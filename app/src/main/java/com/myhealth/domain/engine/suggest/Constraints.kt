package com.myhealth.domain.engine.suggest

import com.myhealth.domain.model.EventType
import com.myhealth.domain.model.Intensity
import com.myhealth.domain.model.RationaleEntry
import com.myhealth.domain.model.RecoveryBand
import com.myhealth.domain.model.SessionType
import com.myhealth.domain.model.SportGroup
import java.time.DayOfWeek
import java.time.LocalDate
import kotlin.math.abs

/** The twelve hard constraints of PLAN §3.5.3, in the plan's order. */
enum class ConstraintId { C1, C2, C3, C4, C5, C6, C7, C8, C9, C10, C11, C12 }

/** A session the engine is considering for one day (§3.5.6 step 4). */
data class Candidate(
    val entry: CatalogEntry,
    val day: Long,
    val minutes: Int = entry.defaultMin,
) {
    val sessionType: SessionType get() = entry.sessionType
    val intensity: Intensity get() = entry.intensity
    val sportGroup: SportGroup get() = entry.sportGroup
    val estTrimp: Double get() = entry.estTrimpFor(minutes)
    val isHard: Boolean get() = entry.isHard
    val isMobility: Boolean get() = sessionType == SessionType.MOBILITY
}

/**
 * Everything the constraints need that is not in the grid: the athlete's state and the events /
 * activities just outside the horizon.
 */
data class ConstraintContext(
    val todayDay: Long,
    val horizonEndDay: Long,
    /** Days carrying a `SOCCER_MATCH` or `RACE`, spanning the horizon **+ 3 days** (C1/C2). */
    val matchOrRaceDays: Set<Long> = emptySet(),
    /** Days whose completed activities' summed TRIMP reached [Constraints.HIGH_LOAD_AU] (C4). */
    val highLoadDays: Set<Long> = emptySet(),
    val recoveryBand: RecoveryBand? = null,
    val acwr: Double? = null,
    /** Sessions/week cap per sport group, from `Profile.preferredSportsJson` (C10). */
    val weeklyCaps: Map<SportGroup, Int> = emptyMap(),
    /** `longRunWeekday` from the same blob; `null` ⇒ C12 allows Sat/Sun (C12). */
    val longRunWeekday: DayOfWeek? = null,
) {
    companion object {
        /** Derives the context from the engine's inputs (§3.5.1). */
        fun of(input: SuggestionInput): ConstraintContext {
            val latest = Periodization.latestLoad(input.recentLoad)
            return ConstraintContext(
                todayDay = input.todayDay,
                horizonEndDay = input.horizonEndDay,
                matchOrRaceDays = input.events
                    .filter { it.type == EventType.SOCCER_MATCH || it.type == EventType.RACE }
                    .map { it.occurrenceDay }
                    .toSet(),
                highLoadDays = input.recentActivities
                    .groupBy { it.day }
                    .filterValues { sessions -> sessions.sumOf { it.trimp ?: 0.0 } >= Constraints.HIGH_LOAD_AU }
                    .keys,
                recoveryBand = input.recovery?.band,
                acwr = latest?.acwr,
                weeklyCaps = SportPreferences.capsOf(input.profile.preferredSportsJson),
                longRunWeekday = SportPreferences.longRunWeekdayOf(input.profile.preferredSportsJson),
            )
        }
    }
}

/**
 * The hard constraints (PLAN §3.5.3). A candidate violating **any** of them is discarded before
 * scoring — this is the injury guard of risk R13, so every rule here fails closed.
 *
 * Evaluation is always against "the grid as it currently stands" (§3.5.6 step 4), so the greedy
 * loop re-runs [violations] for every surviving candidate after each placement.
 *
 * Ambiguity notes (§3.5.3 leaves these open):
 * - "within 48 h before" is read in whole local days: a candidate on the match day itself or on
 *   either of the two days before it is inside the window (test `sug01` fixes this reading).
 * - Candidates have no start time, so C6's "overlapping a locked planned session" is read as
 *   "sharing a day with one" for anything above mobility.
 * - C5 and C10 are counted over every rolling 7-day window of the horizon that contains the
 *   candidate's day; a horizon shorter than a week counts as one window.
 * - A `BLOCKED` day still counts as the rest day C3 asks for — nothing is scheduled on it.
 */
object Constraints {

    /** "Within 48 h before" a match/race ⇒ the event day and the two days before it. */
    const val HARD_WINDOW_DAYS: Long = 2L

    /** C4's "an activity with TRIMP ≥ 200". */
    const val HIGH_LOAD_AU: Double = 200.0

    /** C5: at most two hard sessions per rolling week. */
    const val MAX_HARD_PER_WEEK: Int = 2

    /** C11 spacing, in days (72 h ⇒ 3 days). */
    const val HARD_RUN_SPACING_DAYS: Long = 3L
    const val STRENGTH_LOWER_SPACING_DAYS: Long = 3L
    const val LONG_RUN_SPACING_DAYS: Long = 5L

    /** C9: no hard work at all above this ACWR. */
    const val ACWR_NO_HARD_ABOVE: Double = 1.5

    /** C4 / C8: the only sessions allowed the day after a match, race or 200 AU day. */
    val RECOVERY_ONLY_TYPES: Set<SessionType> =
        setOf(SessionType.RECOVERY_RUN, SessionType.MOBILITY, SessionType.REST)

    val WEEKEND: Set<DayOfWeek> = setOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY)

    /**
     * Every constraint [candidate] violates on [day], evaluated against [grid] and [ctx].
     * An empty list means the candidate is admissible.
     */
    fun violations(
        candidate: Candidate,
        day: Long,
        grid: SuggestionGrid,
        ctx: ConstraintContext,
    ): List<ConstraintId> {
        val broken = mutableListOf<ConstraintId>()
        val plan = grid.dayAt(day)
        if (c1Violated(candidate, day, ctx)) broken += ConstraintId.C1
        if (c2Violated(candidate, day, ctx)) broken += ConstraintId.C2
        if (c3Violated(candidate, day, grid)) broken += ConstraintId.C3
        if (c4Violated(candidate, day, grid, ctx)) broken += ConstraintId.C4
        if (c5Violated(candidate, day, grid)) broken += ConstraintId.C5
        if (plan != null && c6Violated(candidate, plan)) broken += ConstraintId.C6
        if (plan != null && c7Violated(candidate, plan)) broken += ConstraintId.C7
        if (c8Violated(candidate, day, ctx)) broken += ConstraintId.C8
        if (c9Violated(candidate, ctx)) broken += ConstraintId.C9
        if (c10Violated(candidate, day, grid, ctx)) broken += ConstraintId.C10
        if (c11Violated(candidate, day, grid)) broken += ConstraintId.C11
        if (c12Violated(candidate, day, ctx)) broken += ConstraintId.C12
        return broken
    }

    /** C1 — no `HIGH`/`MAX` within 48 h before a match or race. */
    private fun c1Violated(candidate: Candidate, day: Long, ctx: ConstraintContext): Boolean =
        candidate.isHard && ctx.matchOrRaceDays.any { it - day in 0..HARD_WINDOW_DAYS }

    /** C2 — no lower-body / full-body strength within 48 h before a match or race. */
    private fun c2Violated(candidate: Candidate, day: Long, ctx: ConstraintContext): Boolean {
        val heavyLegs = candidate.sessionType == SessionType.STRENGTH_LOWER ||
            candidate.sessionType == SessionType.STRENGTH_FULL
        return heavyLegs && ctx.matchOrRaceDays.any { it - day in 0..HARD_WINDOW_DAYS }
    }

    /** C3 — every rolling 7-day window of the horizon keeps at least one rest day. */
    private fun c3Violated(candidate: Candidate, day: Long, grid: SuggestionGrid): Boolean {
        if (candidate.isMobility) return false
        val after = grid.place(day, candidate.asPlacedItem())
        return after.rollingWindows().any { window ->
            after.days.none { it.day in window && it.isRestDay }
        }
    }

    /** C4 — the day after a match, race or a ≥ 200 AU day is recovery-only. */
    private fun c4Violated(
        candidate: Candidate,
        day: Long,
        grid: SuggestionGrid,
        ctx: ConstraintContext,
    ): Boolean {
        val previous = day - 1
        val hadKeyEvent = previous in ctx.matchOrRaceDays ||
            grid.itemsOn(previous).any { it.isKeyEvent }
        val hadBigLoad = previous in ctx.highLoadDays ||
            grid.itemsOn(previous).sumOf { it.estTrimp } >= HIGH_LOAD_AU
        if (!hadKeyEvent && !hadBigLoad) return false
        return candidate.sessionType !in RECOVERY_ONLY_TYPES
    }

    /** C5 — at most two hard sessions per rolling week; a match or race counts as one. */
    private fun c5Violated(candidate: Candidate, day: Long, grid: SuggestionGrid): Boolean {
        if (!candidate.isHard) return false
        return grid.rollingWindows()
            .filter { day in it }
            .any { window ->
                val existing = grid.days
                    .filter { it.day in window }
                    .sumOf { plan -> plan.sessions.count { it.isHard } }
                existing + 1 > MAX_HARD_PER_WEEK
            }
    }

    /** C6 — nothing on a `BLOCKED` day, nothing overlapping a locked planned session. */
    private fun c6Violated(candidate: Candidate, plan: DayPlan): Boolean {
        if (plan.isBlocked) return true
        val locked = plan.items.filter { it.origin == ItemOrigin.LOCKED_PLANNED }
        if (locked.isEmpty()) return false
        return if (candidate.isMobility) locked.any { it.isMobility } else true
    }

    /** C7 — at most one non-mobility session per day; mobility may be the second. */
    private fun c7Violated(candidate: Candidate, plan: DayPlan): Boolean =
        if (candidate.isMobility) {
            plan.sessions.any { it.isMobility }
        } else {
            plan.nonMobilitySessions.isNotEmpty()
        }

    /** C8 — `FATIGUED` today is recovery-only; `STRAINED` today is rest. */
    private fun c8Violated(candidate: Candidate, day: Long, ctx: ConstraintContext): Boolean {
        if (day != ctx.todayDay) return false
        return when (ctx.recoveryBand) {
            RecoveryBand.FATIGUED -> candidate.sessionType !in RECOVERY_ONLY_TYPES
            RecoveryBand.STRAINED -> true
            else -> false
        }
    }

    /** C9 — ACWR above 1.5 suppresses every hard candidate in the horizon. */
    private fun c9Violated(candidate: Candidate, ctx: ConstraintContext): Boolean =
        candidate.isHard && (ctx.acwr ?: 0.0) > ACWR_NO_HARD_ABOVE

    /** C10 — per-sport weekly caps; fixed calendar sessions count towards the cap. */
    private fun c10Violated(
        candidate: Candidate,
        day: Long,
        grid: SuggestionGrid,
        ctx: ConstraintContext,
    ): Boolean {
        val cap = ctx.weeklyCaps[candidate.sportGroup] ?: return false
        return grid.rollingWindows()
            .filter { day in it }
            .any { window ->
                val existing = grid.days
                    .filter { it.day in window }
                    .sumOf { plan -> plan.sessions.count { it.sportGroup == candidate.sportGroup } }
                existing + 1 > cap
            }
    }

    /** C11 — minimum spacing between repeats of the same hard work. */
    private fun c11Violated(candidate: Candidate, day: Long, grid: SuggestionGrid): Boolean {
        val spacing = when {
            candidate.sessionType == SessionType.LONG_RUN -> LONG_RUN_SPACING_DAYS
            candidate.sessionType == SessionType.STRENGTH_LOWER -> STRENGTH_LOWER_SPACING_DAYS
            candidate.sportGroup == SportGroup.RUN && candidate.isHard -> HARD_RUN_SPACING_DAYS
            else -> return false
        }
        return grid.entries().any { (otherDay, item) ->
            otherDay != day && matchesSpacingKind(candidate, item) && abs(otherDay - day) < spacing
        }
    }

    private fun matchesSpacingKind(candidate: Candidate, item: GridItem): Boolean = when {
        candidate.sessionType == SessionType.LONG_RUN -> item.sessionType == SessionType.LONG_RUN
        candidate.sessionType == SessionType.STRENGTH_LOWER ->
            item.sessionType == SessionType.STRENGTH_LOWER
        else -> item.sportGroup == SportGroup.RUN && item.isHard
    }

    /** C12 — long runs only on the configured weekday, or on Sat/Sun when unset. */
    private fun c12Violated(candidate: Candidate, day: Long, ctx: ConstraintContext): Boolean {
        if (candidate.sessionType != SessionType.LONG_RUN) return false
        val weekday = LocalDate.ofEpochDay(day).dayOfWeek
        val allowed = ctx.longRunWeekday?.let { setOf(it) } ?: WEEKEND
        return weekday !in allowed
    }
}

/** The grid item a candidate becomes once placed (§3.5.6 step 6). */
fun Candidate.asPlacedItem(
    score: Double = 0.0,
    rationale: List<RationaleEntry> = emptyList(),
): GridItem = GridItem(
    origin = ItemOrigin.SUGGESTED,
    sessionType = entry.sessionType,
    sportType = entry.sportType,
    intensity = entry.intensity,
    minutes = minutes,
    estTrimp = estTrimp,
    score = score,
    rationale = rationale,
)
