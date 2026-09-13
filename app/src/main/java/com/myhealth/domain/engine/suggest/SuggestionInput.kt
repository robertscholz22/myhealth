package com.myhealth.domain.engine.suggest

import com.myhealth.domain.model.ActivitySummary
import com.myhealth.domain.model.CycleStatus
import com.myhealth.domain.model.DailyLoad
import com.myhealth.domain.model.EventOccurrence
import com.myhealth.domain.model.Goal
import com.myhealth.domain.model.PlannedSession
import com.myhealth.domain.model.Profile
import com.myhealth.domain.model.RecoveryState
import java.time.LocalDate

/**
 * Everything the training-suggestion engine reads (PLAN §3.5.1). Nothing else: the engine is a
 * pure function of this object, which is what makes [SuggestionEngine]'s `inputsHash` meaningful.
 *
 * Two documented extensions of the §3.5.1 field list:
 * - [planStartDay] — §3.5.2's `RECOVERY_WEEK` override counts weeks "since plan start", which
 *   §3.5.1 does not carry. `null` (no active plan) simply disables the override.
 * - [horizonDays] keeps its §3.5.1 default of 7; the settings key `suggestionHorizonDays` feeds it.
 * - [cycleStatusByDay] — added by P11.2; empty unless the user tracks their cycle.
 */
data class SuggestionInput(
    val today: LocalDate,
    val horizonDays: Int = 7,
    val profile: Profile,
    /** `ACTIVE` goals, sorted by `priority` ascending (1 = primary). */
    val goals: List<Goal> = emptyList(),
    /** Expanded occurrences covering the horizon **+ 3 days** (C1/C2 look ahead). */
    val events: List<EventOccurrence> = emptyList(),
    /** Only `locked == true` sessions; the suggester may not move or overlap them (C6). */
    val lockedPlanned: List<PlannedSession> = emptyList(),
    /** The last 42 days of `daily_load`, ascending by day. */
    val recentLoad: List<DailyLoad> = emptyList(),
    val recovery: RecoveryState? = null,
    /** The last 14 days of activities — C4's "TRIMP ≥ 200 yesterday" rule and the sport caps. */
    val recentActivities: List<ActivitySummary> = emptyList(),
    val planStartDay: Long? = null,
    /**
     * P11.2: where each horizon day sits in the menstrual cycle. Empty whenever cycle tracking is
     * off, which is exactly what makes the four `CYCLE_*` rules inert for everybody else.
     */
    val cycleStatusByDay: Map<Long, CycleStatus> = emptyMap(),
) {
    val todayDay: Long get() = today.toEpochDay()

    /** Exclusive end of the horizon: suggestions live in `[todayDay, horizonEndDay)`. */
    val horizonEndDay: Long get() = todayDay + horizonDays

    /** Every day the grid spans, ascending. */
    val horizonDaysList: List<Long> get() = (todayDay until horizonEndDay).toList()
}
