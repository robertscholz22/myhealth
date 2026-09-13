package com.myhealth.ui.strength

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.myhealth.R
import com.myhealth.domain.model.MuscleGroup
import com.myhealth.domain.model.StrengthWorkoutKind
import com.myhealth.domain.repository.StrengthRepository
import com.myhealth.domain.util.Outcome
import com.myhealth.ui.common.UiMessage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Clock

/** ViewModel state for [WorkoutEditScreen] (PLAN §4.2 "Workout edit", P14.7). */
data class WorkoutEditUiState(
    val isLoading: Boolean = true,
    val isNew: Boolean = true,
    val draft: WorkoutEditDraft = WorkoutEditDraft(),
    val validation: WorkoutValidation = WorkoutValidation(),
    val isSaving: Boolean = false,
    val saveError: UiMessage? = null,
    val loadError: UiMessage? = null,
    val saved: Boolean = false,
) {
    val highlight: Map<MuscleGroup, Float> get() = draft.highlight()
}

/**
 * Backs [WorkoutEditScreen] (§4.2 "Workout edit", P14.7). `id == -1` creates a new, empty workout;
 * any other id loads it — including a built-in, which becomes an editable copy on save (`isBuiltIn`
 * stays whatever it already is; the repository does not special-case it beyond that).
 */
class WorkoutEditViewModel(
    private val id: Long,
    private val strengthRepo: StrengthRepository,
    private val clock: Clock,
) : ViewModel() {

    private val _state = MutableStateFlow(WorkoutEditUiState(isLoading = id != NEW_ID, isNew = id == NEW_ID))
    val state: StateFlow<WorkoutEditUiState> = _state.asStateFlow()

    init {
        if (id != NEW_ID) load()
    }

    private fun load() {
        viewModelScope.launch {
            val workout = strengthRepo.getById(id)
            if (workout == null) {
                _state.update { it.copy(isLoading = false, loadError = UiMessage.of(R.string.workout_edit_load_error)) }
            } else {
                _state.update { it.copy(isLoading = false, draft = workoutEditDraftOf(workout)) }
            }
        }
    }

    fun update(transform: (WorkoutEditDraft) -> WorkoutEditDraft) {
        _state.update { current ->
            val draft = transform(current.draft)
            current.copy(draft = draft, validation = validateWorkoutDraft(draft))
        }
    }

    fun setName(name: String) = update { it.copy(name = name) }

    fun setKind(kind: StrengthWorkoutKind) = update { it.copy(kind = kind) }

    fun setNotes(notes: String) = update { it.copy(notes = notes) }

    fun addExercise(exerciseId: String) = update { it.addExercise(exerciseId) }

    fun removeExercise(index: Int) = update { it.removeExercise(index) }

    fun moveUp(index: Int) = update { it.moveExercise(index, index - 1) }

    fun moveDown(index: Int) = update { it.moveExercise(index, index + 1) }

    fun updateRow(index: Int, transform: (WorkoutExerciseDraft) -> WorkoutExerciseDraft) = update { draft ->
        val rows = draft.exercises.toMutableList()
        if (index in rows.indices) rows[index] = transform(rows[index])
        draft.copy(exercises = rows)
    }

    fun save() {
        val draft = _state.value.draft
        val validation = validateWorkoutDraft(draft)
        if (!validation.isValid) {
            _state.update { it.copy(validation = validation) }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(isSaving = true, saveError = null) }
            when (strengthRepo.upsertWorkout(draft.toStrengthWorkout(clock))) {
                is Outcome.Ok -> _state.update { it.copy(isSaving = false, saved = true) }
                is Outcome.Err -> _state.update {
                    it.copy(isSaving = false, saveError = UiMessage.of(R.string.workout_edit_save_error))
                }
            }
        }
    }

    private companion object {
        /** `WorkoutEditRoute`'s "no id" sentinel (§4.1). */
        const val NEW_ID = -1L
    }
}
