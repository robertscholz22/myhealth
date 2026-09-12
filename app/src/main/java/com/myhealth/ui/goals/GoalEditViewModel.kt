package com.myhealth.ui.goals

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.myhealth.domain.model.EventType
import com.myhealth.domain.model.GoalStatus
import com.myhealth.domain.repository.CalendarRepository
import com.myhealth.domain.repository.GoalRepository
import com.myhealth.domain.util.Outcome
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.LocalDate

/** How far ahead the editor looks for a `RACE` event to link a race goal to. */
private const val RACE_LOOKAHEAD_DAYS = 400L

/** One linkable race in the calendar (§4.2 "Goal edit": "link event"). */
data class RaceOption(val eventId: Long, val title: String, val day: Long)

/** ViewModel state for [GoalEditScreen]. */
data class GoalEditUiState(
    val isLoading: Boolean = true,
    val isNew: Boolean = true,
    val draft: GoalDraft = GoalDraft(),
    val errors: Map<GoalField, String> = emptyMap(),
    val races: List<RaceOption> = emptyList(),
    val isSaving: Boolean = false,
    val saveError: String? = null,
    val loadError: String? = null,
    val pendingDelete: Boolean = false,
    /** One-shot: the screen pops back once either flips to `true`. */
    val saved: Boolean = false,
    val deleted: Boolean = false,
)

/**
 * Backs [GoalEditScreen] (PLAN §4.2 "Goal edit", P6.1). `id == -1` creates a new goal.
 * Saving a draft with "primary goal" on relies on [GoalRepository.upsert] to demote the others.
 */
class GoalEditViewModel(
    private val id: Long,
    private val goalRepo: GoalRepository,
    private val calendarRepo: CalendarRepository,
    private val clock: Clock,
) : ViewModel() {

    private val _state = MutableStateFlow(GoalEditUiState(isLoading = id != -1L, isNew = id == -1L))
    val state: StateFlow<GoalEditUiState> = _state.asStateFlow()

    init {
        load()
        loadRaces()
    }

    private fun load() {
        if (id == -1L) {
            _state.update { it.copy(isLoading = false) }
            return
        }
        viewModelScope.launch {
            val goal = goalRepo.getById(id)
            if (goal == null) {
                _state.update { it.copy(isLoading = false, loadError = "Goal not found.") }
            } else {
                _state.update { it.copy(isLoading = false, draft = goalDraftOf(goal)) }
            }
        }
    }

    private fun loadRaces() {
        viewModelScope.launch {
            val today = LocalDate.now(clock).toEpochDay()
            runCatching {
                calendarRepo.observeOccurrences(today, today + RACE_LOOKAHEAD_DAYS)
            }.getOrNull()?.collect { occurrences ->
                val races = occurrences
                    .filter { it.type == EventType.RACE }
                    .sortedBy { it.occurrenceDay }
                    .map { RaceOption(it.eventId, it.effectiveTitle, it.occurrenceDay) }
                _state.update { it.copy(races = races) }
            }
        }
    }

    fun update(transform: (GoalDraft) -> GoalDraft) {
        _state.update { current ->
            val draft = transform(current.draft)
            current.copy(draft = draft, errors = if (current.errors.isEmpty()) emptyMap() else validateGoal(draft))
        }
    }

    /** Linking a race event copies its day into the goal, which is what periodization reads. */
    fun linkRace(option: RaceOption?) = update { draft ->
        draft.copy(
            linkedEventId = option?.eventId,
            targetDay = option?.let { LocalDate.ofEpochDay(it.day) } ?: draft.targetDay,
        )
    }

    fun setStatus(status: GoalStatus) {
        update { it.copy(status = status) }
        save()
    }

    fun save() {
        val draft = _state.value.draft
        val errors = validateGoal(draft)
        if (errors.isNotEmpty()) {
            _state.update { it.copy(errors = errors) }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(isSaving = true, saveError = null) }
            when (goalRepo.upsert(draft.toGoal(clock))) {
                is Outcome.Ok -> _state.update { it.copy(isSaving = false, saved = true) }
                is Outcome.Err -> _state.update {
                    it.copy(isSaving = false, saveError = "Could not save the goal. Please try again.")
                }
            }
        }
    }

    fun requestDelete() = _state.update { it.copy(pendingDelete = true) }

    fun cancelDelete() = _state.update { it.copy(pendingDelete = false) }

    fun confirmDelete() {
        _state.update { it.copy(pendingDelete = false) }
        viewModelScope.launch {
            when (goalRepo.delete(id)) {
                is Outcome.Ok -> _state.update { it.copy(deleted = true) }
                is Outcome.Err -> _state.update { it.copy(saveError = "Could not delete the goal.") }
            }
        }
    }
}
