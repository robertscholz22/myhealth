package com.myhealth.domain.engine.suggest

import com.myhealth.domain.model.DailyLoad
import com.myhealth.domain.model.EventOccurrence
import com.myhealth.domain.model.EventType
import com.myhealth.domain.model.Goal
import com.myhealth.domain.model.GoalType
import com.myhealth.domain.model.RecoveryBand
import com.myhealth.domain.model.RecoveryState
import com.myhealth.domain.model.TrainingPhase
import kotlin.math.max
import kotlin.math.min

/**
 * Training phase + weekly load target (PLAN §3.5.2), the two numbers every suggestion hangs off.
 *
 * Stateless and pure: [compute] reads only its [SuggestionInput]. The weekly target is deliberately
 * conservative — the 25 % ramp cap, the ACWR multiplier and the recovery multipliers can only ever
 * *lower* it (risk R13).
 *
 * Ambiguity notes (§3.5.2 does not say):
 * - A race whose `targetDay` is in the past is ignored (it cannot be periodized towards), so the
 *   phase falls back to `IN_SEASON`/`BASE` exactly as if no race goal existed.
 * - `recentLoad.last()` is read as "the row with the largest `day`", so an unsorted list is safe.
 * - P11.2's late-luteal `×0.90` is applied in [compute], after [weeklyTarget], so the §3.5.2
 *   formula itself (and the tests that pin it) is untouched. Like every other multiplier here it
 *   can only lower the budget.
 * - `lastWeekActual` sums the seven days **before** today (`[today-7, today-1]`); today itself is
 *   still being planned and must not shrink its own budget.
 */
object Periodization {

    /** Inclusive upper bounds, in days-to-race, of the phases §3.5.2 tabulates. */
    const val RACE_WEEK_MAX_DAYS: Long = 7L
    const val TAPER_MAX_DAYS: Long = 10L
    const val PEAK_MAX_DAYS: Long = 35L
    const val BUILD_MAX_DAYS: Long = 77L

    /** A soccer match inside this window puts a goal-less athlete in season. */
    const val IN_SEASON_MATCH_WINDOW_DAYS: Long = 21L

    /** Every 4th week of a plan is a down week: `weeksSincePlanStart % 4 == 3`. */
    const val RECOVERY_WEEK_MODULO: Int = 4
    const val RECOVERY_WEEK_REMAINDER: Int = 3

    /** The weekly target may never exceed last week's actual load by more than 25 %. */
    const val MAX_RAMP_FACTOR: Double = 1.25

    /** …but a returning athlete is always allowed at least this much (the ramp cap's floor). */
    const val RAMP_FLOOR_AU: Double = 150.0

    const val ACWR_SUPPRESS_ABOVE: Double = 1.5
    const val ACWR_SUPPRESS_FACTOR: Double = 0.75
    const val FATIGUED_FACTOR: Double = 0.85
    const val STRAINED_FACTOR: Double = 0.60

    const val DAYS_PER_WEEK: Int = 7

    /** Phase → weekly-load factor applied to `ctl * 7` (§3.5.2). */
    fun factorFor(phase: TrainingPhase): Double = when (phase) {
        TrainingPhase.BASE -> 1.05
        TrainingPhase.BUILD -> 1.10
        TrainingPhase.PEAK -> 1.05
        TrainingPhase.TAPER -> 0.60
        TrainingPhase.RACE_WEEK -> 0.45
        TrainingPhase.IN_SEASON -> 1.00
        TrainingPhase.OFF_SEASON -> 0.80
        TrainingPhase.RECOVERY_WEEK -> 0.65
    }

    /** The primary race goal: `RACE_TIME` with a `targetDay` that has not passed, lowest priority. */
    fun primaryRaceGoal(goals: List<Goal>, todayDay: Long): Goal? = goals
        .filter { it.type == GoalType.RACE_TIME && (it.targetDay ?: Long.MIN_VALUE) >= todayDay }
        .minByOrNull { it.priority }

    /** Days from today to the primary race, or `null` when there is none to periodize towards. */
    fun daysToRace(goals: List<Goal>, todayDay: Long): Long? =
        primaryRaceGoal(goals, todayDay)?.targetDay?.minus(todayDay)

    /** True when a `SOCCER_MATCH` occurs in `[today, today + 21]` (§3.5.2's `IN_SEASON` rule). */
    fun matchWithinWindow(
        events: List<EventOccurrence>,
        todayDay: Long,
        windowDays: Long = IN_SEASON_MATCH_WINDOW_DAYS,
    ): Boolean = events.any {
        it.type == EventType.SOCCER_MATCH &&
            it.occurrenceDay >= todayDay &&
            it.occurrenceDay <= todayDay + windowDays
    }

    /** Whole weeks between the plan's start and today; `null` when there is no plan. */
    fun weeksSincePlanStart(todayDay: Long, planStartDay: Long?): Int? {
        if (planStartDay == null) return null
        val elapsed = todayDay - planStartDay
        if (elapsed < 0) return null
        return (elapsed / DAYS_PER_WEEK).toInt()
    }

    /**
     * The §3.5.2 phase table, including the `RECOVERY_WEEK` override (which never overrides a
     * `TAPER` or `RACE_WEEK` — the race outranks the plan's rhythm).
     */
    fun phase(daysToRace: Long?, matchWithin21Days: Boolean, weeksSincePlanStart: Int?): TrainingPhase {
        val base = when {
            daysToRace == null && matchWithin21Days -> TrainingPhase.IN_SEASON
            daysToRace == null -> TrainingPhase.BASE
            daysToRace <= RACE_WEEK_MAX_DAYS -> TrainingPhase.RACE_WEEK
            daysToRace <= TAPER_MAX_DAYS -> TrainingPhase.TAPER
            daysToRace <= PEAK_MAX_DAYS -> TrainingPhase.PEAK
            daysToRace <= BUILD_MAX_DAYS -> TrainingPhase.BUILD
            else -> TrainingPhase.BASE
        }
        val isDownWeek = weeksSincePlanStart != null &&
            weeksSincePlanStart % RECOVERY_WEEK_MODULO == RECOVERY_WEEK_REMAINDER
        val protected = base == TrainingPhase.TAPER || base == TrainingPhase.RACE_WEEK
        return if (isDownWeek && !protected) TrainingPhase.RECOVERY_WEEK else base
    }

    /**
     * The weekly AU budget of §3.5.2, in the order the plan writes it: phase factor, then the
     * 25 % ramp cap (floored at 150 AU), then the ACWR and recovery multipliers, then `max(_, 0)`.
     */
    fun weeklyTarget(
        phase: TrainingPhase,
        ctl: Double,
        lastWeekActual: Double,
        acwr: Double?,
        band: RecoveryBand?,
    ): Double {
        var target = ctl * DAYS_PER_WEEK * factorFor(phase)
        target = min(target, max(lastWeekActual * MAX_RAMP_FACTOR, RAMP_FLOOR_AU))
        if (acwr != null && acwr > ACWR_SUPPRESS_ABOVE) target *= ACWR_SUPPRESS_FACTOR
        if (band == RecoveryBand.FATIGUED) target *= FATIGUED_FACTOR
        if (band == RecoveryBand.STRAINED) target *= STRAINED_FACTOR
        return max(target, 0.0)
    }

    /** Sum of `trimp` over the seven days before [todayDay]. */
    fun lastWeekActual(recentLoad: List<DailyLoad>, todayDay: Long): Double = recentLoad
        .filter { it.day in (todayDay - DAYS_PER_WEEK) until todayDay }
        .sumOf { it.trimp }

    /** The most recent `daily_load` row, by day — §3.5.2's `recentLoad.last()`. */
    fun latestLoad(recentLoad: List<DailyLoad>): DailyLoad? = recentLoad.maxByOrNull { it.day }

    /** Phase + budget for one [SuggestionInput]; step 3 of the §3.5.6 algorithm. */
    fun compute(input: SuggestionInput): PeriodizationResult {
        val todayDay = input.todayDay
        val latest = latestLoad(input.recentLoad)
        val ctl = latest?.ctl ?: 0.0
        val acwr = latest?.acwr
        val lastWeek = lastWeekActual(input.recentLoad, todayDay)
        val daysToRace = daysToRace(input.goals, todayDay)
        val phase = phase(
            daysToRace = daysToRace,
            matchWithin21Days = matchWithinWindow(input.events, todayDay),
            weeksSincePlanStart = weeksSincePlanStart(todayDay, input.planStartDay),
        )
        val cycleFactor = CycleRules.weeklyTargetFactor(input.cycleStatusByDay, input.horizonDays)
        return PeriodizationResult(
            phase = phase,
            weeklyTarget = weeklyTarget(phase, ctl, lastWeek, acwr, input.recovery.bandOf()) * cycleFactor,
            daysToRace = daysToRace,
            ctl = ctl,
            lastWeekActual = lastWeek,
            acwr = acwr,
            band = input.recovery.bandOf(),
        )
    }

    private fun RecoveryState?.bandOf(): RecoveryBand? = this?.band
}

/** [Periodization.compute]'s output — the budget plus every input that shaped it (for rationale). */
data class PeriodizationResult(
    val phase: TrainingPhase,
    val weeklyTarget: Double,
    val daysToRace: Long?,
    val ctl: Double,
    val lastWeekActual: Double,
    val acwr: Double?,
    val band: RecoveryBand?,
)
