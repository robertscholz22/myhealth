package com.myhealth.domain.engine.suggest

import com.myhealth.domain.engine.load.TrimpDefaults
import com.myhealth.domain.model.Intensity
import com.myhealth.domain.model.SessionType
import com.myhealth.domain.model.SuggestedSession
import com.myhealth.domain.model.SuggestionBatch
import com.myhealth.domain.model.SuggestionStatus
import com.myhealth.domain.model.TrainingPhase
import java.time.Clock
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min

/** One run of the suggester: the unsaved batch, its sessions, and the numbers behind them. */
data class SuggestionResult(
    /** Unsaved (`id = 0`): the repository (P6.5) assigns ids when it persists the batch. */
    val batch: SuggestionBatch,
    val sessions: List<SuggestedSession>,
    val phase: TrainingPhase,
    val weeklyTarget: Double,
    val inputsHash: String,
)

/**
 * The training-suggestion engine (PLAN §3.5.6): a deterministic, offline greedy planner.
 *
 * The nine steps of §3.5.6 map onto this class as: [SuggestionGrid.seed] (1), [fixedLoad]/
 * `remainingBudget` (2), [Periodization.compute] (3), [candidatesFor] + [Constraints.violations]
 * (4), [Scorer.score] + `candidateOrder` (5), the `while` loop in [generate] (6), [enforceRestDay]
 * / [downgradeBeforeKeyEvent] / [addMobilityToRestDays] (7a–c), [Rationale.forSession] (8) and
 * [SuggestionInputsHash] (9).
 *
 * Determinism: no randomness, no clock reads except [SuggestionBatch.generatedAtMillis], every
 * ordering is total (score desc, day asc, sessionType ordinal asc), and the whole input is hashed.
 *
 * Ambiguity note on §3.5.4's `durationScale = clamp(remainingBudget / Σ(remaining planned
 * defaults), 0.7, 1.3)`: the plan does not say how many sessions are still "planned" at the moment
 * of a placement (the greedy loop does not know its own future). This implementation reads it as
 * "the budget still affordable in minutes of this session type, divided by the default minutes of
 * the sessions that would fill the remaining free days": the affordable minutes are
 * `remainingBudget / (0.30 * rpe)`, and the expected remaining count is
 * `min(freeRestDays, ceil(affordableMinutes / defaultMin))`. The ratio is 1.0 when the budget
 * matches the days available, shrinks (to 0.7) when the week is tight and stretches (to 1.3) when
 * few days are left — which is what keeps `sug16`'s "within 15 % of target" true.
 */
class SuggestionEngine(private val clock: Clock) {

    /** Step 5's total order; the two tie-breakers make the output byte-identical across runs. */
    private val candidateOrder: Comparator<Pair<Candidate, ScoreBreakdown>> =
        compareByDescending<Pair<Candidate, ScoreBreakdown>> { it.second.total }
            .thenBy { it.first.day }
            .thenBy { it.first.sessionType.ordinal }

    fun generate(input: SuggestionInput): SuggestionResult {
        val periodization = Periodization.compute(input)
        val ctx = ConstraintContext.of(input)
        val bike = BikeContext.of(input)
        var grid = SuggestionGrid.seed(input)
        var remaining = max(periodization.weeklyTarget - grid.fixedLoad, 0.0)
        val stopFloor = MIN_BUDGET_FRACTION * periodization.weeklyTarget

        var iterations = 0
        while (iterations < MAX_ITERATIONS && remaining > 0.0 && remaining >= stopFloor) {
            iterations++
            val best = bestCandidate(input, periodization, grid, ctx, bike, remaining) ?: break
            val (candidate, breakdown) = best
            if (breakdown.total < MIN_SCORE) break
            val rationale = Rationale.forSession(
                candidate = candidate,
                ctx = rationaleContext(input, periodization, grid, ctx, bike, remaining, candidate),
            )
            grid = grid.place(candidate.day, candidate.asPlacedItem(breakdown.total, rationale))
            remaining = max(remaining - candidate.estTrimp, 0.0)
        }

        grid = enforceRestDay(grid)
        grid = downgradeBeforeKeyEvent(grid, ctx)
        if (input.profile.mobilityOnRestDays) {
            grid = addMobilityToRestDays(grid, periodization.phase, periodization.isStarterWeek)
        }
        return resultOf(input, periodization, grid)
    }

    // ---- steps 4 + 5 -----------------------------------------------------------------------------

    private fun bestCandidate(
        input: SuggestionInput,
        periodization: PeriodizationResult,
        grid: SuggestionGrid,
        ctx: ConstraintContext,
        bike: BikeContext,
        remaining: Double,
    ): Pair<Candidate, ScoreBreakdown>? {
        val scoring = Scorer.contextOf(input, periodization, remaining)
        return grid.days
            .flatMap { plan -> candidatesFor(plan.day, periodization.phase, grid, remaining, bike) }
            .filter { Constraints.violations(it, it.day, grid, ctx).isEmpty() }
            .map { it to Scorer.score(it, grid, scoring) }
            .minWithOrNull(candidateOrder)
    }

    /**
     * Every catalog session the day could take, at the duration the budget and phase imply.
     *
     * [bike] is where P12.3 enters: it drops the four cycling rows for an athlete who does not
     * ride, drops `TRAINER_SESSION` without a trainer, and moves outdoor rides onto the trainer in
     * the indoor season — all before a single constraint or score is evaluated.
     */
    internal fun candidatesFor(
        day: Long,
        phase: TrainingPhase,
        grid: SuggestionGrid,
        remaining: Double,
        bike: BikeContext = BikeContext.NONE,
    ): List<Candidate> = SessionCatalog.suggestableFor(bike.enabled)
        .mapNotNull { entry -> BikeRules.entryFor(entry, day, bike) }
        .map { entry ->
            Candidate(entry = entry, day = day, minutes = minutesFor(entry, phase, grid, remaining))
        }

    /** §3.5.4's duration scaling — see the class KDoc for how `Σ(remaining defaults)` is read. */
    internal fun minutesFor(
        entry: CatalogEntry,
        phase: TrainingPhase,
        grid: SuggestionGrid,
        remaining: Double,
    ): Int {
        val baseMin = max(
            MIN_SESSION_MINUTES,
            TrimpDefaults.roundHalfUp(entry.defaultMin * Scorer.durationFactor(phase, entry.sessionType)),
        )
        val perMinute = TrimpDefaults.RPE_TO_TRIMP * entry.rpe
        if (perMinute <= 0.0) return baseMin
        val affordableMinutes = remaining / perMinute
        val freeDays = max(1, grid.days.count { it.isRestDay && !it.isBlocked })
        val expected = min(freeDays, max(1, ceil(affordableMinutes / baseMin).toInt()))
        val scale = SessionCatalog.durationScale(affordableMinutes, (expected * baseMin).toDouble())
        return SessionCatalog.scaledMinutes(baseMin, scale)
    }

    // ---- step 7: post-passes ---------------------------------------------------------------------

    /** 7a — drop the lowest-scoring placement in any window that lost its rest day. */
    internal fun enforceRestDay(grid: SuggestionGrid): SuggestionGrid {
        var current = grid
        repeat(MAX_ITERATIONS) {
            val offending = current.rollingWindows()
                .firstOrNull { window -> current.days.none { it.day in window && it.isRestDay } }
                ?: return current
            val victim = current.suggested()
                .filter { it.first in offending && !it.second.isMobility }
                .minByOrNull { it.second.score }
                ?: return current
            current = current.remove(victim.first, victim.second)
        }
        return current
    }

    /** 7b — anything above `LOW` on the eve of a match or race becomes an easy run, or goes. */
    internal fun downgradeBeforeKeyEvent(grid: SuggestionGrid, ctx: ConstraintContext): SuggestionGrid {
        var current = grid
        val easy = SessionCatalog.entryFor(SessionType.EASY_RUN) ?: return current
        grid.days.forEach { plan ->
            val next = plan.day + 1
            val keyTomorrow = next in ctx.matchOrRaceDays || grid.itemsOn(next).any { it.isKeyEvent }
            if (!keyTomorrow) return@forEach
            val label = if (next in ctx.matchOrRaceDays) "a match or race" else "a match"
            plan.items
                .filter { it.origin == ItemOrigin.SUGGESTED && it.intensity.ordinal > Intensity.LOW.ordinal }
                .forEach { item ->
                    current = current.remove(plan.day, item)
                    val replacement = Candidate(easy, plan.day)
                    if (Constraints.violations(replacement, plan.day, current, ctx).isEmpty()) {
                        current = current.place(
                            plan.day,
                            replacement.asPlacedItem(
                                score = item.score,
                                rationale = item.rationale + Rationale.downgradeEntry(label),
                            ),
                        )
                    }
                }
        }
        return current
    }

    /** 7c — `profile.mobilityOnRestDays`: every rest day gets mobility; a rest day stays a rest day. */
    internal fun addMobilityToRestDays(
        grid: SuggestionGrid,
        phase: TrainingPhase,
        isStarterWeek: Boolean = false,
    ): SuggestionGrid {
        val entry = SessionCatalog.entryFor(SessionType.MOBILITY) ?: return grid
        var current = grid
        grid.days.forEach { plan ->
            val alreadyThere = plan.items.any { it.isMobility }
            if (!plan.isRestDay || plan.isBlocked || alreadyThere) return@forEach
            current = current.place(
                plan.day,
                Candidate(entry, plan.day).asPlacedItem(
                    score = MOBILITY_SCORE,
                    rationale = Rationale.forMobility(phase, isStarterWeek),
                ),
            )
        }
        return current
    }

    // ---- steps 8 + 9 ------------------------------------------------------------------------------

    private fun rationaleContext(
        input: SuggestionInput,
        periodization: PeriodizationResult,
        grid: SuggestionGrid,
        ctx: ConstraintContext,
        bike: BikeContext,
        remaining: Double,
        candidate: Candidate,
    ): RationaleContext {
        val nextKeyEvent = ctx.matchOrRaceDays
            .filter { it > candidate.day && it - candidate.day <= Constraints.HARD_WINDOW_DAYS }
            .minOrNull()
        val cap = ctx.weeklyCaps[candidate.sportGroup]
        return RationaleContext(
            phase = periodization.phase,
            weeklyTarget = periodization.weeklyTarget,
            remainingBudget = remaining,
            recoveryScore = input.recovery?.score,
            recoveryBand = periodization.band,
            hoursToKeyEvent = nextKeyEvent?.let { (it - candidate.day) * HOURS_PER_DAY },
            primaryGoalTitle = input.goals.minByOrNull { it.priority }?.title,
            sportCap = cap,
            sportUsed = if (cap == null) 0 else {
                Scorer.sessionsThisWeekForSport(candidate.sportGroup, candidate.day, grid)
            },
            cycleStatus = input.cycleStatusByDay[candidate.day],
            bikeGoal = BikeRules.primaryBikeGoal(input.goals),
            bikeIndoorSeason = bike.trainerAvailable && BikeRules.isIndoorSeason(candidate.day),
            isStarterWeek = periodization.isStarterWeek,
        )
    }

    private fun resultOf(
        input: SuggestionInput,
        periodization: PeriodizationResult,
        grid: SuggestionGrid,
    ): SuggestionResult {
        val sessions = grid.suggested()
            .sortedWith(compareBy({ it.first }, { it.second.sessionType.ordinal }))
            .map { (day, item) ->
                SuggestedSession(
                    id = 0L,
                    batchId = 0L,
                    day = day,
                    sportType = item.sportType,
                    sessionType = item.sessionType,
                    intensity = item.intensity,
                    targetDurationMin = item.minutes,
                    targetDistanceMeters = null,
                    estimatedTrimp = item.estTrimp,
                    score = item.score,
                    rationale = item.rationale,
                    status = SuggestionStatus.PROPOSED,
                )
            }
        val hash = SuggestionInputsHash.of(input)
        return SuggestionResult(
            batch = SuggestionBatch(
                id = 0L,
                generatedAtMillis = clock.millis(),
                horizonStartDay = input.todayDay,
                horizonEndDay = input.horizonEndDay,
                phase = periodization.phase,
                weeklyLoadTarget = periodization.weeklyTarget,
                inputsHash = hash,
                status = SuggestionStatus.PROPOSED,
            ),
            sessions = sessions,
            phase = periodization.phase,
            weeklyTarget = periodization.weeklyTarget,
            inputsHash = hash,
        )
    }

    companion object {
        /** §3.5.6 step 6: a candidate below this score is not worth placing. */
        const val MIN_SCORE: Double = 0.35

        /** …and the loop stops once less than 10 % of the weekly budget is left. */
        const val MIN_BUDGET_FRACTION: Double = 0.10

        const val MAX_ITERATIONS: Int = 20

        /** Post-pass 7c sessions are not scored candidates; they carry this nominal score. */
        const val MOBILITY_SCORE: Double = 0.0

        const val MIN_SESSION_MINUTES: Int = 10

        private const val HOURS_PER_DAY: Long = 24L
    }
}
