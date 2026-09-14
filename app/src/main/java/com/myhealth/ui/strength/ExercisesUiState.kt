package com.myhealth.ui.strength

import com.myhealth.domain.engine.strength.ExerciseCatalog
import com.myhealth.domain.model.Equipment
import com.myhealth.domain.model.Exercise
import com.myhealth.domain.model.MovementPattern
import com.myhealth.domain.model.MuscleGroup
import com.myhealth.domain.model.StrengthWorkout

/**
 * ViewModel state for [ExercisesScreen] (PLAN §4.2 "Exercises", P14.7/P16.2): a free-text search
 * plus three chip filters (equipment / movement pattern / muscle group — the last also settable by
 * tapping the body figure), all `AND`-ed, and the "Only my equipment" switch (P16.2). The catalog
 * itself is a constant in-memory object ([ExerciseCatalog]), so [items] is recomputed synchronously
 * from the filters — no repository, no loading state.
 */
data class ExercisesUiState(
    val query: String = "",
    val equipment: Equipment? = null,
    val pattern: MovementPattern? = null,
    val muscle: MuscleGroup? = null,
    /** Existing workouts, for the "Add to workout…" picker on a row/detail screen. */
    val workouts: List<StrengthWorkout> = emptyList(),
    /** `profile.availableEquipmentJson`, decoded (P16.2). `null` means everything, and hides the
     * "Only my equipment" switch — there is nothing to filter down to. */
    val myEquipment: Set<Equipment>? = null,
    /** The "Only my equipment" switch (§P16 "My equipment"): on by default, but only ever narrows
     * the list when [myEquipment] is non-null. */
    val onlyMyEquipment: Boolean = true,
) {
    /** The catalog's search combined with the three chip filters and the equipment switch, all
     * `AND`-ed (`eqmy01`). */
    val items: List<Exercise>
        get() {
            val bySearch = if (query.isBlank()) ExerciseCatalog.ALL else ExerciseCatalog.search(query)
            val home = myEquipment
            return bySearch.filter { exercise ->
                (equipment == null || exercise.equipment == equipment) &&
                    (pattern == null || exercise.pattern == pattern) &&
                    (muscle == null || muscle in exercise.primary || muscle in exercise.secondary) &&
                    (!onlyMyEquipment || home == null || exercise.equipment in home)
            }
        }

    val hasActiveFilters: Boolean get() = equipment != null || pattern != null || muscle != null

    /** Whether the switch has anything to do — hidden in the UI when the profile never narrowed
     * "my equipment" (§P16.2: "visible when the set is narrower than all"). */
    val showOnlyMyEquipmentSwitch: Boolean get() = myEquipment != null
}

/** "Squat", "Bodyweight", "Horizontal push" — enum-name labels shared across the strength UI. */
fun Equipment.label(): String = com.myhealth.ui.training.trainingLabelOf(name)

fun MovementPattern.label(): String = com.myhealth.ui.training.trainingLabelOf(name)

fun MuscleGroup.label(): String = com.myhealth.ui.training.trainingLabelOf(name)

fun com.myhealth.domain.model.StrengthWorkoutKind.label(): String = com.myhealth.ui.training.trainingLabelOf(name)

/** "Too easy", "Easy", "Hard", "Too hard" — the set-log sheet's segmented buttons and the
 * Progression card's last-feedback line (P16.2). */
fun com.myhealth.domain.model.Feedback.label(): String = com.myhealth.ui.training.trainingLabelOf(name)
