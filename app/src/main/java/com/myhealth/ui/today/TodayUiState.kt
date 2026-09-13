package com.myhealth.ui.today

import com.myhealth.domain.engine.calendar.LinkProposal
import com.myhealth.domain.model.ActivitySummary
import com.myhealth.domain.model.BodyMeasurement
import com.myhealth.domain.model.CycleStatus
import com.myhealth.domain.model.DailyHealthSummary
import com.myhealth.domain.model.DailyLoad
import com.myhealth.domain.model.MacroTotals
import com.myhealth.domain.model.NutritionTarget
import com.myhealth.domain.model.PlannedSession
import com.myhealth.domain.model.SleepRecord
import com.myhealth.domain.model.SuggestedSession
import com.myhealth.ui.body.weightDeltaToGoalKg
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs

/** ViewModel state for [TodayScreen] (PLAN §4.2 Today (Home), P2.10/P3.7). */
data class TodayUiState(
    val isLoading: Boolean = true,
    /** Epoch day of "today" — the day the activities card links to (P3.5 day detail). */
    val day: Long = 0L,
    val activities: List<ActivitySummary> = emptyList(),
    val latestWeight: BodyMeasurement? = null,
    val goalWeightKg: Double? = null,
    val healthSummary: DailyHealthSummary? = null,
    val sleep: SleepRecord? = null,
    val lastSyncSuccessAtMillis: Long? = null,
    val lastSyncError: String? = null,
    val isSyncing: Boolean = false,
    /** Undismissed auto-link proposals from the last week, `confidence >= PROPOSE_THRESHOLD`
     * (P3.7) — the "Suggested links" card shows when this is non-empty. */
    val linkSuggestions: List<LinkProposal> = emptyList(),
    /** Today's `nutrition_target_snapshot`, or `null` before the first computation (P4.12). */
    val target: NutritionTarget? = null,
    /** Sum of today's logged meals (§3.7). */
    val intake: MacroTotals = MacroTotals.ZERO,
    /** The most recently cached `daily_load` row (P5.8) — its `recoveryScore`/`band`/`confidence`
     * back the recovery card, its `atl`/`ctl`/`acwr` the load card. */
    val latestLoad: DailyLoad? = null,
    /** Σ TRIMP over the last 7 days (inclusive of today), for the load card. */
    val weeklyTrimp: Double = 0.0,
    /** Today's `planned_session` rows — section 2 of §4.2 Today (P6.6). */
    val plannedToday: List<PlannedSession> = emptyList(),
    /** …or, when nothing is planned, the best suggestion for today from the open batch (P6.7). */
    val suggestedToday: SuggestedSession? = null,
    /** POLISH-8: the open `PROPOSED` batch predates a calendar change and should be regenerated. */
    val suggestionsStale: Boolean = false,
    /** P11.3: whether the cycle card should show at all — `settings.cycleTrackingEnabled` or
     * `profile.sex == FEMALE` (see `CycleRepository.isTrackingEnabled`). */
    val cycleTrackingEnabled: Boolean = false,
    /** Today's cycle status, or `null` before a first period start is logged. */
    val cycleStatus: CycleStatus? = null,
) {
    val weightChipText: String? get() = weightChipLabel(latestWeight?.weightKg, goalWeightKg)

    /** The flag the recovery card leads with, if any (P5.8). */
    val topRecoveryFlag: String? get() = latestLoad?.flags?.firstOrNull()

    /** True when the plan card has something concrete to show rather than an invitation. */
    val hasPlanToday: Boolean get() = plannedToday.isNotEmpty() || suggestedToday != null
}

/** A stable per-proposal key (there is no `LinkProposal.id`) for VM-only dismissal tracking. */
fun linkProposalKey(proposal: LinkProposal): String =
    "${proposal.eventOccurrence.eventId}|${proposal.eventOccurrence.occurrenceDay}|${proposal.activity.id}"

private val LAST_SYNCED_TIME_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

/**
 * "Last synced 14:05" / "Not yet synced" for the sync-status banner. Pure — unit-tested in
 * `TodayUiStateTest`.
 */
fun lastSyncedLabel(lastSuccessAtMillis: Long?, zone: ZoneId): String {
    if (lastSuccessAtMillis == null) return "Not yet synced"
    val time = Instant.ofEpochMilli(lastSuccessAtMillis).atZone(zone).format(LAST_SYNCED_TIME_FORMAT)
    return "Last synced $time"
}

/**
 * Body chip text: `"78.4 kg"`, plus a signed delta to the goal weight when both are known
 * (`"78.4 kg (+3.4 kg to goal)"`), or `null` when there is no weight logged yet. Pure —
 * unit-tested. Reuses [weightDeltaToGoalKg] (`ui/body`) so the two screens agree on the sign.
 */
fun weightChipLabel(latestWeightKg: Double?, goalWeightKg: Double?): String? {
    if (latestWeightKg == null) return null
    val base = "%.1f kg".format(Locale.US, latestWeightKg)
    val delta = weightDeltaToGoalKg(latestWeightKg, goalWeightKg) ?: return base
    if (abs(delta) < 0.05) return "$base (at goal)"
    val sign = if (delta > 0) "+" else "-"
    val magnitude = "%.1f".format(Locale.US, abs(delta))
    return "$base ($sign$magnitude kg to goal)"
}
