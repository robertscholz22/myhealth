package com.myhealth.domain.engine.strength

import com.myhealth.domain.model.Equipment
import com.myhealth.domain.model.Exercise
import com.myhealth.domain.model.StrengthWorkout
import com.myhealth.domain.model.StrengthWorkoutExercise
import com.myhealth.domain.model.StrengthWorkoutKind

/** The rest a template prescribes for a compound set; the same default the estimate assumes. */
private const val DEFAULT_REST = StrengthWorkout.DEFAULT_REST_SEC

/** The rest for an isolation or a hold — short enough that the core day stays a core day. */
private const val SHORT_REST = 60

/**
 * The six built-in workouts (PLAN §3.12.3): two upper days, two lower days, one full-body day and
 * one core day, each 5–7 exercises deep.
 *
 * They are **code, not a migration**: `StrengthWorkoutSeeder` materialises them into
 * `strength_workout` the first time they are needed, keyed on [StrengthWorkout.templateId], so a
 * template can still be corrected in a later release (the seeder leaves an already-seeded row
 * alone — a user's edits to their copy are theirs).
 *
 * Every id is checked against [ExerciseCatalog] at class-initialisation time, and a held exercise
 * may only be prescribed with [hold] (seconds) and a counted one only with [reps] — which is what
 * `ex08` asserts from the outside.
 */
object StrengthTemplates {

    /** The pinned reference workout (`sw04`): 6 × 3 × (10 reps + 90 s rest) → 44 minutes. */
    val UPPER_A: StrengthWorkout = template("UPPER_A", "Upper A", StrengthWorkoutKind.UPPER) {
        reps("BARBELL_BENCH_PRESS", sets = 3, reps = 10)
        reps("BARBELL_ROW", sets = 3, reps = 10)
        reps("OVERHEAD_PRESS", sets = 3, reps = 10)
        reps("LAT_PULLDOWN", sets = 3, reps = 10)
        reps("BICEPS_CURL", sets = 3, reps = 10)
        reps("TRICEPS_PUSHDOWN", sets = 3, reps = 10)
    }

    val UPPER_B: StrengthWorkout = template("UPPER_B", "Upper B", StrengthWorkoutKind.UPPER) {
        reps("INCLINE_DUMBBELL_PRESS", sets = 3, reps = 10)
        reps("PULL_UP", sets = 3, reps = 8)
        reps("DUMBBELL_ROW", sets = 3, reps = 10)
        reps("LATERAL_RAISE", sets = 3, reps = 15, restSec = 60)
        reps("FACE_PULL", sets = 3, reps = 15, restSec = 60)
        reps("TRICEPS_DIP", sets = 3, reps = 10)
        reps("HAMMER_CURL", sets = 3, reps = 12, restSec = 60)
    }

    val LOWER_A: StrengthWorkout = template("LOWER_A", "Lower A", StrengthWorkoutKind.LOWER) {
        reps("BARBELL_BACK_SQUAT", sets = 4, reps = 8, restSec = 120)
        reps("ROMANIAN_DEADLIFT", sets = 3, reps = 10)
        reps("BULGARIAN_SPLIT_SQUAT", sets = 3, reps = 10)
        reps("LEG_CURL", sets = 3, reps = 12, restSec = 60)
        reps("HIP_THRUST", sets = 3, reps = 12)
        reps("CALF_RAISE", sets = 3, reps = 15, restSec = 60)
    }

    val LOWER_B: StrengthWorkout = template("LOWER_B", "Lower B", StrengthWorkoutKind.LOWER) {
        reps("FRONT_SQUAT", sets = 4, reps = 6, restSec = 120)
        reps("SINGLE_LEG_RDL", sets = 3, reps = 10)
        reps("WALKING_LUNGE", sets = 3, reps = 12)
        reps("LEG_PRESS", sets = 3, reps = 12)
        reps("NORDIC_HAMSTRING_CURL", sets = 3, reps = 6)
        reps("HIP_ADDUCTION", sets = 3, reps = 15, restSec = 60)
        reps("CALF_RAISE", sets = 3, reps = 20, restSec = 60)
    }

    val FULL_A: StrengthWorkout = template("FULL_A", "Full body A", StrengthWorkoutKind.FULL) {
        reps("GOBLET_SQUAT", sets = 3, reps = 12)
        reps("PUSH_UP", sets = 3, reps = 12)
        reps("ROMANIAN_DEADLIFT", sets = 3, reps = 10)
        reps("INVERTED_ROW", sets = 3, reps = 10)
        reps("OVERHEAD_PRESS", sets = 3, reps = 10)
        hold("PLANK", sets = 3, seconds = 45)
    }

    val CORE_A: StrengthWorkout = template("CORE_A", "Core A", StrengthWorkoutKind.CORE) {
        hold("PLANK", sets = 3, seconds = 60)
        hold("SIDE_PLANK", sets = 3, seconds = 45)
        hold("HOLLOW_HOLD", sets = 3, seconds = 30)
        reps("DEAD_BUG", sets = 3, reps = 10, restSec = 60)
        reps("BIRD_DOG", sets = 3, reps = 10, restSec = 60)
        hold("COPENHAGEN_PLANK", sets = 3, seconds = 30)
        reps("BACK_EXTENSION", sets = 3, reps = 12, restSec = 60)
    }

    /** All six, in the order the Workouts screen lists them and the seeder writes them. */
    val ALL: List<StrengthWorkout> = listOf(UPPER_A, UPPER_B, LOWER_A, LOWER_B, FULL_A, CORE_A)

    private val byTemplateId: Map<String, StrengthWorkout> = ALL.associateBy { it.templateId!! }

    /** The built-in with this `templateId` (`UPPER_A`, …), or `null` — suggestions name them. */
    fun byId(templateId: String): StrengthWorkout? = byTemplateId[templateId]

    /** The built-ins of one kind, for the alternating choice of §3.12.5. */
    fun ofKind(kind: StrengthWorkoutKind): List<StrengthWorkout> = ALL.filter { it.kind == kind }

    private fun template(
        templateId: String,
        name: String,
        kind: StrengthWorkoutKind,
        rows: TemplateBuilder.() -> Unit,
    ): StrengthWorkout = StrengthWorkout(
        id = 0L,
        name = name,
        kind = kind,
        templateId = templateId,
        isBuiltIn = true,
        exercises = TemplateBuilder().apply(rows).rows,
        createdAtMillis = 0L,
        updatedAtMillis = 0L,
    )

    /**
     * Builds the ordered rows of one template. `orderIndex` is the position in the list — the
     * repository renumbers from the list position too, so the two can never disagree.
     */
    private class TemplateBuilder {
        val rows = mutableListOf<StrengthWorkoutExercise>()

        /** A counted exercise. Rejects a held one, so `ex08` cannot be broken by an edit here. */
        fun reps(exerciseId: String, sets: Int, reps: Int, restSec: Int = DEFAULT_REST) {
            val exercise = requireCatalog(exerciseId)
            require(!exercise.isTimed) { "${exercise.id} is timed — prescribe it with hold()" }
            add(exercise, sets, reps = reps, seconds = null, restSec = restSec)
        }

        /** A held exercise: `seconds`, never `reps`. */
        fun hold(exerciseId: String, sets: Int, seconds: Int, restSec: Int = SHORT_REST) {
            val exercise = requireCatalog(exerciseId)
            require(exercise.isTimed) { "${exercise.id} is counted — prescribe it with reps()" }
            add(exercise, sets, reps = null, seconds = seconds, restSec = restSec)
        }

        private fun add(exercise: Exercise, sets: Int, reps: Int?, seconds: Int?, restSec: Int) {
            rows += StrengthWorkoutExercise(
                id = 0L,
                workoutId = 0L,
                orderIndex = rows.size,
                exerciseId = exercise.id,
                sets = sets,
                reps = reps,
                seconds = seconds,
                isBodyweight = exercise.equipment == Equipment.BODYWEIGHT,
                restSec = restSec,
            )
        }

        private fun requireCatalog(exerciseId: String): Exercise =
            requireNotNull(ExerciseCatalog.byId(exerciseId)) {
                "Template names an exercise the catalog does not have: $exerciseId"
            }
    }
}
