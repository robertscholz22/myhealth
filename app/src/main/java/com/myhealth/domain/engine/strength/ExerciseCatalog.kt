package com.myhealth.domain.engine.strength

import com.myhealth.domain.model.Equipment
import com.myhealth.domain.model.Exercise
import com.myhealth.domain.model.MovementPattern
import com.myhealth.domain.model.MuscleGroup

/**
 * The exercise catalog (PLAN §3.12.1) — a Kotlin object, deliberately not a JSON asset: `domain/`
 * is Android-free and cannot open `assets/`, and a compile-time-checked `Set<MuscleGroup>` is
 * worth more than a stringly-typed blob that would need a loader, a parser and its own tests.
 *
 * The entries live in three files for R10 ([UPPER_BODY_EXERCISES], [LOWER_BODY_EXERCISES],
 * [CORE_EXERCISES]); this object is the only place that joins them and the only API the rest of
 * the app uses.
 *
 * **Ids are permanent.** `strength_workout_exercise.exerciseId` stores them, so an entry may be
 * renamed or re-cued in code, but never re-identified — and an id that disappears would orphan a
 * saved workout row, which is why [byId] returns `null` rather than throwing.
 */
object ExerciseCatalog {

    /** Every entry, upper body → lower body → trunk, in the order the three files declare them. */
    val ALL: List<Exercise> = UPPER_BODY_EXERCISES + LOWER_BODY_EXERCISES + CORE_EXERCISES

    private val byIdMap: Map<String, Exercise> = ALL.associateBy { it.id }

    init {
        // The catalog is data typed by hand; a duplicated id would silently shadow an entry and
        // make a stored workout row point at the wrong exercise.
        check(byIdMap.size == ALL.size) { "ExerciseCatalog has duplicate ids" }
    }

    /** The entry with this stored id, or `null` if a saved row names one the catalog dropped. */
    fun byId(id: String): Exercise? = byIdMap[id]

    /**
     * Free-text search over the exercise name **and** the names of the muscle groups it trains
     * (`ex09`: "squat" and "quads" both find the back squat). Case-insensitive, whitespace- and
     * underscore-insensitive (`lower back` matches `LOWER_BACK`).
     *
     * Name matches come first, then muscle-only matches; within each half the catalog's own order
     * is kept, so the result is deterministic. A blank query returns [ALL].
     */
    fun search(query: String): List<Exercise> {
        val needle = query.trim().lowercase()
        if (needle.isEmpty()) return ALL
        val byName = ALL.filter { it.name.lowercase().contains(needle) }
        val byMuscle = ALL.filter { it !in byName && it.matchesMuscleName(needle) }
        return byName + byMuscle
    }

    /**
     * The three filters of the exercise list screen, `AND`-ed; every `null` argument is "any".
     * [muscle] matches a primary **or** a secondary group — the screen's chip says "trains", not
     * "targets".
     */
    fun filter(
        equipment: Equipment? = null,
        pattern: MovementPattern? = null,
        muscle: MuscleGroup? = null,
    ): List<Exercise> = ALL.filter { exercise ->
        (equipment == null || exercise.equipment == equipment) &&
            (pattern == null || exercise.pattern == pattern) &&
            (muscle == null || muscle in exercise.primary || muscle in exercise.secondary)
    }

    /** Every entry that trains [muscle] as its primary — what the body figure's tap opens. */
    fun byPrimary(muscle: MuscleGroup): List<Exercise> = ALL.filter { muscle in it.primary }

    private fun Exercise.matchesMuscleName(needle: String): Boolean =
        allMuscles.any { group -> group.searchName.contains(needle) }

    /** `LOWER_BACK` → `"lower back"`, so a user types what they see, not what the enum is called. */
    private val MuscleGroup.searchName: String
        get() = name.lowercase().replace('_', ' ')
}
