package com.myhealth.domain.model

import kotlin.math.ceil

/**
 * The strength feature's models (PLAN §2.2.7 / §3.12.1 / §3.12.3): the catalog entry [Exercise],
 * which is code rather than a table, plus the three stored models — a named, ordered list of
 * exercises, its rows, and the optional per-set log written when a session is marked done.
 *
 * Nothing stored refers to [Exercise] by reference: [StrengthWorkoutExercise] keeps only the
 * catalog's stable [StrengthWorkoutExercise.exerciseId] string, so a catalog entry can be renamed
 * or re-described in code without a migration.
 */

/**
 * One entry of `ExerciseCatalog` (§3.12.1). [id] is stable and stored
 * (`strength_workout_exercise.exerciseId`); [name] and [cue] are English copy that lives in the
 * domain under the same exemption as the rationale strings — the UI renders them verbatim.
 *
 * [primary] and [secondary] are disjoint by construction (`ex03`) and drive both the body figure
 * (§3.12.2) and `MuscleLoadEngine`'s per-workout share (§3.12.4, primary 1.0 / secondary 0.5).
 */
data class Exercise(
    val id: String,
    val name: String,
    val primary: Set<MuscleGroup>,
    val secondary: Set<MuscleGroup> = emptySet(),
    val equipment: Equipment,
    val pattern: MovementPattern,
    /** Performed one side at a time — a set means one side (lunges, single-leg RDL, side plank). */
    val unilateral: Boolean = false,
    /** Prescribed as a hold in seconds rather than as a rep count (`ex08`). */
    val isTimed: Boolean = false,
    val cue: String,
) {
    /** Every group the exercise touches, primary first — what the body figure highlights. */
    val allMuscles: Set<MuscleGroup> get() = primary + secondary

    val isBodyweightOnly: Boolean get() = equipment == Equipment.BODYWEIGHT
}
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
) {
    /**
     * How long the workout takes, rounded up (§3.12.3):
     * `ceil((Σ sets × (workSec + restSec) + 480) / 60)` with `workSec = seconds ?: reps × 3` and
     * a default rest of [DEFAULT_REST_SEC]. The flat 480 s is warm-up plus changeovers.
     *
     * Computed, never stored — a template edited in code must not leave a stale number in a row.
     */
    val estimatedMinutes: Int
        get() {
            val workingSec = exercises.sumOf { row ->
                val workSec = row.seconds ?: ((row.reps ?: 0) * SEC_PER_REP)
                row.sets.toLong() * (workSec + (row.restSec ?: DEFAULT_REST_SEC)).toLong()
            }
            return ceil((workingSec + OVERHEAD_SEC).toDouble() / 60.0).toInt()
        }

    companion object {
        /** The rest used when a row does not name one (§3.12.3). */
        const val DEFAULT_REST_SEC: Int = 90

        /** A counted rep is modelled as three seconds of work. */
        const val SEC_PER_REP: Int = 3

        /** Warm-up plus equipment changes, added once per workout. */
        const val OVERHEAD_SEC: Int = 480
    }
}

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
