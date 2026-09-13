package com.myhealth.ui.training

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.myhealth.R
import com.myhealth.domain.engine.suggest.Periodization
import com.myhealth.domain.model.CalendarDay
import com.myhealth.domain.model.Goal
import com.myhealth.domain.model.GoalStatus
import com.myhealth.domain.model.PlannedSession
import com.myhealth.domain.model.PlannedStatus
import com.myhealth.domain.model.SuggestionBatch
import com.myhealth.domain.model.SuggestionStatus
import com.myhealth.domain.model.TrainingPhase
import com.myhealth.domain.model.TrainingPlan
import com.myhealth.domain.repository.CalendarRepository
import com.myhealth.domain.repository.GoalRepository
import com.myhealth.domain.repository.PlanRepository
import com.myhealth.domain.repository.SettingsRepository
import com.myhealth.domain.repository.StrengthRepository
import com.myhealth.domain.repository.SuggestionRepository
import com.myhealth.domain.util.Outcome
import com.myhealth.ui.strength.SetLogRow
import com.myhealth.ui.strength.toStrengthSetLog
import com.myhealth.ui.common.UiMessage
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.LocalDate

/** Everything outside the displayed week that the header needs. */
private data class TrainingContext(
    val plan: TrainingPlan?,
    val batch: SuggestionBatch?,
    val goals: List<Goal>,
    val matchWithin21Days: Boolean,
    /** POLISH-8: a calendar edit happened under the open `PROPOSED` batch. */
    val suggestionsStale: Boolean = false,
)

/** Transient, VM-owned state: the in-flight generate, its snackbar and its one-shot nav signal. */
private data class TrainingAction(
    val isGenerating: Boolean = false,
    val message: UiMessage? = null,
    val reviewReady: Boolean = false,
    val selectedDay: Long? = null,
)

/**
 * Backs [TrainingScreen] (PLAN §4.2 "Training plan", P6.6).
 *
 * One week is on screen at a time, paged by [showPreviousWeek]/[showNextWeek] around the ISO
 * Monday of today. The week itself comes from the [CalendarDay] aggregate (§2.3) so events,
 * planned sessions, completed activities and `daily_load` all arrive from a single observer.
 *
 * The phase badge prefers the phase of the latest suggestion batch — that is the phase the
 * sessions on screen were actually generated under — and falls back to [Periodization] recomputed
 * live from the active goals, the next three weeks of events and the plan's start day, so the
 * badge is still right before the first batch is ever generated. The weekly target only comes from
 * a batch whose horizon overlaps the displayed week; other weeks show no target rather than a
 * number that was never computed for them.
 */
class TrainingViewModel(
    private val planRepo: PlanRepository,
    private val suggestionRepo: SuggestionRepository,
    private val calendarRepo: CalendarRepository,
    private val goalRepo: GoalRepository,
    private val settingsRepo: SettingsRepository,
    private val strengthRepo: StrengthRepository,
    private val clock: Clock,
) : ViewModel() {

    private val weekOffset = MutableStateFlow(0)
    private val action = MutableStateFlow(TrainingAction())

    private fun todayDay(): Long = LocalDate.now(clock).toEpochDay()

    @OptIn(ExperimentalCoroutinesApi::class)
    private val week: Flow<Pair<TrainingWeek, List<com.myhealth.domain.model.DailyLoad>>> =
        weekOffset.flatMapLatest { offset ->
            val start = mondayOf(todayDay()) + offset * DAYS_PER_WEEK
            calendarRepo.observeRange(start, start + DAYS_PER_WEEK - 1).map { days ->
                TrainingWeek(
                    startDay = start,
                    offset = offset,
                    days = trainingDayRows(start, todayDay(), days),
                ) to weekLoads(start, days)
            }
        }

    private val context: Flow<TrainingContext> = combine(
        planRepo.observeActivePlan(),
        suggestionRepo.observeLatestBatch(),
        goalRepo.observeByStatus(GoalStatus.ACTIVE),
        calendarRepo.observeOccurrences(todayDay(), todayDay() + PHASE_EVENT_WINDOW_DAYS),
        suggestionRepo.observeStale(),
    ) { plan, batch, goals, events, stale ->
        TrainingContext(
            plan = plan,
            batch = batch,
            goals = goals.sortedBy { it.priority },
            matchWithin21Days = Periodization.matchWithinWindow(events, todayDay()),
            suggestionsStale = stale,
        )
    }

    val state: StateFlow<TrainingUiState> = combine(
        week,
        context,
        action,
        strengthRepo.observeAll(),
    ) { weekAndLoads, ctx, act, workouts ->
        val (currentWeek, loads) = weekAndLoads
        val planned = currentWeek.days.flatMap { it.planned }
        TrainingUiState(
            isLoading = false,
            today = todayDay(),
            week = currentWeek,
            planName = ctx.plan?.name,
            phase = phaseOf(ctx),
            loads = weeklyLoadSums(planned, targetFor(currentWeek, ctx.batch), loads),
            selectedDay = act.selectedDay ?: defaultSelectedDay(currentWeek),
            isGenerating = act.isGenerating,
            suggestionsStale = ctx.suggestionsStale,
            message = act.message,
            reviewReady = act.reviewReady,
            workoutsById = workouts.associateBy { it.id },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), TrainingUiState())

    // ---- week paging -----------------------------------------------------------------------

    fun showPreviousWeek() = shiftWeek(-1)

    fun showNextWeek() = shiftWeek(1)

    fun showCurrentWeek() {
        weekOffset.value = 0
        action.update { it.copy(selectedDay = null) }
    }

    fun selectDay(day: Long) = action.update { it.copy(selectedDay = day) }

    private fun shiftWeek(delta: Int) {
        weekOffset.value = (weekOffset.value + delta).coerceIn(-MAX_WEEK_OFFSET, MAX_WEEK_OFFSET)
        action.update { it.copy(selectedDay = null) }
    }

    // ---- actions ---------------------------------------------------------------------------

    /** Runs the suggester over `settings.suggestionHorizonDays` and opens the review on success. */
    fun generateSuggestions() {
        if (action.value.isGenerating) return
        viewModelScope.launch {
            action.update { it.copy(isGenerating = true, message = null) }
            val horizon = settingsRepo.settings.first().suggestionHorizonDays
            action.update {
                when (suggestionRepo.generate(horizon)) {
                    is Outcome.Ok -> it.copy(isGenerating = false, reviewReady = true)
                    is Outcome.Err -> it.copy(
                        isGenerating = false,
                        message = UiMessage.of(R.string.training_generate_error),
                    )
                }
            }
        }
    }

    fun setLocked(sessionId: Long, locked: Boolean) = run(
        if (locked) UiMessage.of(R.string.training_session_locked) else UiMessage.of(R.string.training_session_unlocked),
    ) { planRepo.setSessionLocked(sessionId, locked) }

    fun markDone(sessionId: Long) = run(UiMessage.of(R.string.training_session_marked_done)) {
        planRepo.setSessionStatus(sessionId, PlannedStatus.COMPLETED)
    }

    /**
     * "Mark done" on a `STRENGTH_*` session with a workout, once the [SetLogSheet] rows are
     * confirmed (P14.7): writes the set logs — [rows] already excludes anything skipped — then
     * marks the session done exactly like [markDone].
     */
    fun completeStrengthSession(session: PlannedSession, rows: List<SetLogRow>) {
        viewModelScope.launch {
            if (rows.isNotEmpty()) {
                val now = clock.millis()
                strengthRepo.insertSetLogs(rows.map { it.toStrengthSetLog(session.day, session.id, now) })
            }
            planRepo.setSessionStatus(session.id, PlannedStatus.COMPLETED)
            action.update { it.copy(message = UiMessage.of(R.string.training_session_marked_done)) }
        }
    }

    fun skip(sessionId: Long) = run(UiMessage.of(R.string.training_session_skipped)) {
        planRepo.setSessionStatus(sessionId, PlannedStatus.SKIPPED)
    }

    fun reopen(sessionId: Long) = run(UiMessage.of(R.string.training_session_reopened)) {
        planRepo.setSessionStatus(sessionId, PlannedStatus.PLANNED)
    }

    fun delete(sessionId: Long) = run(UiMessage.of(R.string.training_session_deleted)) {
        planRepo.deleteSession(sessionId)
    }

    fun consumeMessage() = action.update { it.copy(message = null) }

    fun consumeReviewReady() = action.update { it.copy(reviewReady = false) }

    private fun run(success: UiMessage, block: suspend () -> Outcome<*>) {
        viewModelScope.launch {
            val message = when (block()) {
                is Outcome.Ok -> success
                is Outcome.Err -> UiMessage.of(R.string.training_update_error)
            }
            action.update { it.copy(message = message) }
        }
    }

    // ---- header ----------------------------------------------------------------------------

    /** The batch's phase when there is one, else §3.5.2 recomputed from goals/events/plan start. */
    private fun phaseOf(ctx: TrainingContext): TrainingPhase {
        val batch = ctx.batch
        if (batch != null && batch.status != SuggestionStatus.SUPERSEDED) return batch.phase
        return Periodization.phase(
            daysToRace = Periodization.daysToRace(ctx.goals, todayDay()),
            matchWithin21Days = ctx.matchWithin21Days,
            weeksSincePlanStart = Periodization.weeksSincePlanStart(todayDay(), ctx.plan?.startDay),
        )
    }

    /** A batch's weekly budget only describes the week its horizon covers. */
    private fun targetFor(week: TrainingWeek, batch: SuggestionBatch?): Double {
        if (batch == null) return 0.0
        val weekEnd = week.startDay + DAYS_PER_WEEK
        val overlaps = batch.horizonStartDay < weekEnd && batch.horizonEndDay > week.startDay
        return if (overlaps) batch.weeklyLoadTarget else 0.0
    }

    private fun defaultSelectedDay(week: TrainingWeek): Long {
        val today = todayDay()
        return if (today in week.startDay until week.startDay + DAYS_PER_WEEK) today else week.startDay
    }

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L

        /** How far the fallback phase computation looks for a match (§3.5.2's 21-day window). */
        const val PHASE_EVENT_WINDOW_DAYS = 21L

        /** The board pages a year either way; further than that is a different plan. */
        const val MAX_WEEK_OFFSET = 52
    }
}
