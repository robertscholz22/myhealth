package com.myhealth.ui.strength

import com.myhealth.domain.engine.strength.ExerciseCatalog
import com.myhealth.domain.model.Equipment
import com.myhealth.domain.model.Exercise
import com.myhealth.domain.model.MovementPattern
import com.myhealth.domain.model.MuscleGroup
import com.myhealth.domain.model.StrengthWorkout

/**
 * ViewModel state for [ExercisesScreen] (PLAN §4.2 "Exercises", P14.7): a free-text search plus
 * three chip filters (equipment / movement pattern / muscle group — the last also settable by
 * tapping the body figure), all `AND`-ed. The catalog itself is a constant in-memory object
 * ([ExerciseCatalog]), so [items] is recomputed synchronously from the filters — no repository, no
 * loading state.
 */
data class ExercisesUiState(
    val query: String = "",
    val equipment: Equipment? = null,
    val pattern: MovementPattern? = null,
    val muscle: MuscleGroup? = null,
    /** Existing workouts, for the "Add to workout…" picker on a row/detail screen. */
    val workouts: List<StrengthWorkout> = emptyList(),
) {
    /** The catalog's search combined with the three chip filters, all `AND`-ed. */
    val items: List<Exercise>
        get() {
            val bySearch = if (query.isBlank()) ExerciseCatalog.ALL else ExerciseCatalog.search(query)
            return bySearch.filter { exercise ->
                (equipment == null || exercise.equipment == equipment) &&
                    (pattern == null || exercise.pattern == pattern) &&
                    (muscle == null || muscle in exercise.primary || muscle in exercise.secondary)
            }
        }

    val hasActiveFilters: Boolean get() = equipment != null || pattern != null || muscle != null
}

/** "Squat", "Bodyweight", "Horizontal push" — enum-name labels shared across the strength UI. */
fun Equipment.label(): String = com.myhealth.ui.training.trainingLabelOf(name)

fun MovementPattern.label(): String = com.myhealth.ui.training.trainingLabelOf(name)

fun MuscleGroup.label(): String = com.myhealth.ui.training.trainingLabelOf(name)

fun com.myhealth.domain.model.StrengthWorkoutKind.label(): String = com.myhealth.ui.training.trainingLabelOf(name)
