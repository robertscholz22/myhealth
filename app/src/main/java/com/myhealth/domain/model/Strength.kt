package com.myhealth.domain.model

/**
 * The stored half of the strength feature (PLAN §2.2.7 / §3.12.3): a named, ordered list of
 * exercises, its rows, and the optional per-set log written when a session is marked done.
 *
 * The `Exercise` catalog entry itself and [StrengthWorkout]'s computed `estimatedMinutes` arrive
 * with the catalog in P14.4 — nothing stored refers to them, because [StrengthWorkoutExercise]
 * keeps only the catalog's stable [StrengthWorkoutExercise.exerciseId] string (the catalog is
 * code, not a table).
 */
data class StrengthWorkout(
    val id: Long,
    val name: String,
    val kind: StrengthWorkoutKind,
    /** Stable id of a built-in (`UPPER_A`, `LOWER_A`, …); unique where not null. */
    val templateId: String? = null,
    /** Seeded from `StrengthTemplates`; a user's editable copy carries `false`. */
    val isBuiltIn: Boolean = false,
    val notes: String? = null,
    /** Always in [StrengthWorkoutExercise.orderIndex] order. */
    val exercises: List<StrengthWorkoutExercise> = emptyList(),
    val createdAtMillis: Long,
    val updatedAtMillis: Long,
)

/**
 * One row of a [StrengthWorkout] (§2.2.7). Exactly one of [reps] / [seconds] is set — a rep count
 * for a counted exercise, a hold for a timed one; the repository validates it, SQL does not.
 */
data class StrengthWorkoutExercise(
    val id: Long,
    val workoutId: Long,
    val orderIndex: Int,
    /** An `ExerciseCatalog` id, e.g. `BARBELL_BACK_SQUAT`. Not a foreign key. */
    val exerciseId: String,
    val sets: Int,
    val reps: Int? = null,
    val seconds: Int? = null,
    val loadKg: Double? = null,
    val isBodyweight: Boolean = false,
    val restSec: Int? = null,
    val note: String? = null,
)

/**
 * One logged set (§2.2.7). Flat by design: the planned session or the completed activity is the
 * header, and both links are soft, so deleting either keeps the fact that the set was done.
 */
data class StrengthSetLog(
    val id: Long,
    val day: Long,
    val plannedSessionId: Long? = null,
    val activityId: Long? = null,
    val exerciseId: String,
    val setIndex: Int,
    val reps: Int? = null,
    val seconds: Int? = null,
    val loadKg: Double? = null,
    val rpe: Int? = null,
    val completedAtMillis: Long,
)
