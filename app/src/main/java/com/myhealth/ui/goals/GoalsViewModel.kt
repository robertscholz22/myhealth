package com.myhealth.ui.goals

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.myhealth.R
import com.myhealth.domain.model.GoalStatus
import com.myhealth.domain.repository.ActivityRepository
import com.myhealth.domain.repository.BodyRepository
import com.myhealth.domain.repository.GoalRepository
import com.myhealth.domain.repository.RunningBestRepository
import com.myhealth.domain.util.Outcome
import com.myhealth.ui.common.UiMessage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.LocalDate

/** The windows the progress engine needs: 4 weeks of sessions, a year of weights. */
private const val ACTIVITY_WINDOW_DAYS = 28L
private const val WEIGHT_WINDOW_DAYS = 365L

/**
 * Backs [GoalsScreen] (PLAN §4.2 "Goals", P6.1): the goal list with the progress
 * [com.myhealth.domain.engine.goal.GoalProgress] computes for each one, plus the status actions.
 */
class GoalsViewModel(
    private val goalRepo: GoalRepository,
    private val runningBestRepo: RunningBestRepository,
    private val bodyRepo: BodyRepository,
    private val activityRepo: ActivityRepository,
    private val clock: Clock,
) : ViewModel() {

    private val message = MutableStateFlow<UiMessage?>(null)

    private fun today(): LocalDate = LocalDate.now(clock)

    val state: StateFlow<GoalsUiState> = combine(
        goalRepo.observeAll(),
        runningBestRepo.observeBestPerDistance(),
        bodyRepo.observeRange(today().toEpochDay() - WEIGHT_WINDOW_DAYS, today().toEpochDay()),
        activityRepo.observeRange(today().toEpochDay() - ACTIVITY_WINDOW_DAYS, today().toEpochDay()),
        message,
    ) { goals, bests, weights, activities, msg ->
        val rows = goalRows(goals, bests, weights, activities, today())
        GoalsUiState(
            isLoading = false,
            active = rows.activeOnly().sortedForDisplay(),
            archived = rows.archivedOnly().sortedForDisplay(),
            message = msg,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), GoalsUiState())

    fun makePrimary(id: Long) = run(id, UiMessage.of(R.string.goal_message_primary_updated)) { goalRepo.setPrimary(id) }

    fun markAchieved(id: Long) =
        run(id, UiMessage.of(R.string.goal_message_achieved)) { goalRepo.setStatus(id, GoalStatus.ACHIEVED) }

    fun markAbandoned(id: Long) =
        run(id, UiMessage.of(R.string.goal_message_abandoned)) { goalRepo.setStatus(id, GoalStatus.ABANDONED) }

    fun reactivate(id: Long) =
        run(id, UiMessage.of(R.string.goal_message_reactivated)) { goalRepo.setStatus(id, GoalStatus.ACTIVE) }

    fun delete(id: Long) = run(id, UiMessage.of(R.string.goal_message_deleted)) { goalRepo.delete(id) }

    fun consumeMessage() {
        message.value = null
    }

    private fun run(id: Long, success: UiMessage, block: suspend () -> Outcome<*>) {
        viewModelScope.launch {
            message.value = when (block()) {
                is Outcome.Ok -> success
                is Outcome.Err -> UiMessage.of(R.string.goal_message_update_failed, id)
            }
        }
    }
}
