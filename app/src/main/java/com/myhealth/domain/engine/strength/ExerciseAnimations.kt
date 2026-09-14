package com.myhealth.domain.engine.strength

import com.myhealth.domain.engine.strength.AnimationClips as A
import com.myhealth.domain.model.Exercise
import com.myhealth.domain.model.MovementPattern

/**
 * Which [AnimationClip] each shipped exercise plays (P18.1).
 *
 * The table is **explicit and exhaustive**: every one of the 87 catalog ids (54 strength +
 * 33 mobility) names a clip, and `an01` fails the build if the catalog grows an entry that is not
 * listed here. That is deliberate — a silent pattern-based fallback would quietly give a new
 * exercise the wrong animation, and the point of P18 is that a shipped exercise always shows the
 * movement rather than a generic one.
 *
 * [defaultFor] exists for the *other* direction: a user's own exercise, which has a movement
 * pattern but no id the catalog knows. It picks the archetype of that pattern. An id that names no
 * exercise at all has no pattern either, so [clipOrStanding] falls back to the static standing
 * figure (`an09`) — what the owner accepted custom entries would show.
 *
 * Several exercises deliberately share a clip: an incline press and a flat bench press are the same
 * schematic movement on a jointed stick figure, and so are a chin-up and a pull-up. The clip's
 * `mirror` flag, not the table, is what says a one-sided drill can be shown on the other side.
 */
object ExerciseAnimations {

    /** The clip for this catalog id, or `null` if the id is not a shipped exercise. */
    fun clipFor(exerciseId: String): AnimationClip? = BY_ID[exerciseId]

    /** The clip for [exercise] — the table, or the archetype of its movement pattern. */
    fun clipFor(exercise: Exercise): AnimationClip =
        BY_ID[exercise.id] ?: defaultFor(exercise.pattern)

    /** The table, the catalog's pattern archetype, or — for an id nothing knows — [A.STANDING]. */
    fun clipOrStanding(exerciseId: String): AnimationClip =
        BY_ID[exerciseId] ?: ExerciseCatalog.byId(exerciseId)?.let { defaultFor(it.pattern) }
            ?: A.STANDING

    /** The archetype clip of a movement pattern — the fallback for a user's own exercise. */
    fun defaultFor(pattern: MovementPattern): AnimationClip = when (pattern) {
        MovementPattern.SQUAT -> A.SQUAT
        MovementPattern.HINGE -> A.HINGE
        MovementPattern.LUNGE -> A.LUNGE
        MovementPattern.HORIZONTAL_PUSH -> A.PUSH_UP
        MovementPattern.VERTICAL_PUSH -> A.OVERHEAD_PRESS
        MovementPattern.HORIZONTAL_PULL -> A.ROW_BENT
        MovementPattern.VERTICAL_PULL -> A.PULL_UP
        MovementPattern.CARRY -> A.CARRY
        MovementPattern.CORE -> A.PLANK
        MovementPattern.ISOLATION -> A.CURL
        MovementPattern.PLYOMETRIC -> A.JUMP
        MovementPattern.MOBILITY -> A.QUADRUPED_FLOW
    }

    private val BY_ID: Map<String, AnimationClip> = mapOf(
        // ---- upper body (22) -------------------------------------------------------------------
        "BARBELL_BENCH_PRESS" to A.BENCH_PRESS,
        "INCLINE_DUMBBELL_PRESS" to A.BENCH_PRESS,
        "PUSH_UP" to A.PUSH_UP,
        "OVERHEAD_PRESS" to A.OVERHEAD_PRESS,
        "TRICEPS_DIP" to A.DIP,
        "BARBELL_ROW" to A.ROW_BENT,
        "DUMBBELL_ROW" to A.ROW_BENT,
        "SEATED_CABLE_ROW" to A.ROW_SEATED,
        "INVERTED_ROW" to A.ROW_SEATED,
        "FACE_PULL" to A.REAR_DELT,
        "BAND_PULL_APART" to A.REAR_DELT,
        "PULL_UP" to A.PULL_UP,
        "CHIN_UP" to A.PULL_UP,
        "LAT_PULLDOWN" to A.PULLDOWN,
        "LATERAL_RAISE" to A.LATERAL_RAISE,
        "REAR_DELT_FLY" to A.REAR_DELT,
        "BICEPS_CURL" to A.CURL,
        "HAMMER_CURL" to A.CURL,
        "TRICEPS_PUSHDOWN" to A.TRICEPS_EXTENSION,
        "SKULL_CRUSHER" to A.TRICEPS_EXTENSION,
        "FARMERS_CARRY" to A.CARRY,
        "DEAD_HANG" to A.HANG,

        // ---- lower body (22) -------------------------------------------------------------------
        "BARBELL_BACK_SQUAT" to A.SQUAT,
        "FRONT_SQUAT" to A.FRONT_SQUAT,
        "GOBLET_SQUAT" to A.FRONT_SQUAT,
        "LEG_PRESS" to A.SQUAT,
        "WALL_SIT" to A.SQUAT,
        "CONVENTIONAL_DEADLIFT" to A.HINGE,
        "ROMANIAN_DEADLIFT" to A.HINGE,
        "SINGLE_LEG_RDL" to A.SINGLE_LEG_HINGE,
        "HIP_THRUST" to A.HIP_THRUST,
        "GLUTE_BRIDGE" to A.HIP_THRUST,
        "KETTLEBELL_SWING" to A.SWING,
        "WALKING_LUNGE" to A.LUNGE,
        "REVERSE_LUNGE" to A.LUNGE,
        "BULGARIAN_SPLIT_SQUAT" to A.SPLIT_SQUAT,
        "STEP_UP" to A.STEP_UP,
        "LEG_CURL" to A.KNEE_ISOLATION,
        "LEG_EXTENSION" to A.KNEE_ISOLATION,
        "NORDIC_HAMSTRING_CURL" to A.KNEE_ISOLATION,
        "HIP_ABDUCTION" to A.HIP_ABDUCTION,
        "HIP_ADDUCTION" to A.HIP_ABDUCTION,
        "CALF_RAISE" to A.CALF_RAISE,
        "BOX_JUMP" to A.JUMP,

        // ---- trunk (10) ------------------------------------------------------------------------
        "PLANK" to A.PLANK,
        "SIDE_PLANK" to A.SIDE_PLANK,
        "HOLLOW_HOLD" to A.DEAD_BUG,
        "COPENHAGEN_PLANK" to A.SIDE_PLANK,
        "DEAD_BUG" to A.DEAD_BUG,
        "BIRD_DOG" to A.QUADRUPED_FLOW,
        "HANGING_LEG_RAISE" to A.LEG_RAISE,
        "RUSSIAN_TWIST" to A.TWIST,
        "PALLOF_PRESS" to A.TWIST,
        "BACK_EXTENSION" to A.BACK_EXTENSION,
    ) + MOBILITY_CLIP_IDS

    init {
        // The catalog is the contract; a missing or stale id here would animate the wrong movement.
        val ids = ExerciseCatalog.ALL.map { it.id }.toSet()
        check(BY_ID.keys == ids) {
            "ExerciseAnimations is out of step with ExerciseCatalog: " +
                "missing ${ids - BY_ID.keys}, stale ${BY_ID.keys - ids}"
        }
    }
}

/** The 33 `MOB_` drills (P17), kept out of the map literal above so both stay inside R10. */
private val MOBILITY_CLIP_IDS: Map<String, AnimationClip> = mapOf(
    // hips, knees and ankles
    "MOB_COUCH_STRETCH" to A.HOLD_STRETCH_HIP,
    "MOB_PIGEON" to A.HOLD_STRETCH_HIP,
    "MOB_HIP_SWITCH_90_90" to A.HOLD_STRETCH_HIP,
    "MOB_STANDING_HAMSTRING_STRETCH" to A.HOLD_STRETCH_HAMSTRING,
    "MOB_WALL_CALF_STRETCH" to A.HOLD_STRETCH_CALF,
    "MOB_ANKLE_ROCKS" to A.HOLD_STRETCH_CALF,
    "MOB_DEEP_SQUAT_HOLD" to A.SQUAT,
    "MOB_ADDUCTOR_ROCK_BACK" to A.QUADRUPED_FLOW,
    "MOB_FIGURE_FOUR_GLUTE" to A.HOLD_STRETCH_HIP,
    "MOB_STANDING_QUAD_STRETCH" to A.HOLD_STRETCH_HIP,
    "MOB_WORLDS_GREATEST_STRETCH" to A.HOLD_STRETCH_HIP,
    "MOB_LEG_SWINGS" to A.HOLD_STRETCH_HAMSTRING,

    // spine and trunk
    "MOB_CAT_COW" to A.QUADRUPED_FLOW,
    "MOB_THREAD_THE_NEEDLE" to A.QUADRUPED_FLOW,
    "MOB_THORACIC_ROTATION" to A.ROTATION_THORACIC,
    "MOB_CHILDS_POSE" to A.QUADRUPED_FLOW,
    "MOB_DOWNWARD_DOG" to A.QUADRUPED_FLOW,
    "MOB_COBRA" to A.BACK_EXTENSION,

    // shoulders, arms and neck
    "MOB_SHOULDER_CARS" to A.SHOULDER_CIRCLE,
    "MOB_WALL_SLIDES" to A.WALL_SLIDE,
    "MOB_DOORWAY_PEC_STRETCH" to A.HOLD_STRETCH_SHOULDER,
    "MOB_BAND_PULL_APART_SLOW" to A.REAR_DELT,
    "MOB_DOORWAY_LAT_STRETCH" to A.HOLD_STRETCH_SHOULDER,
    "MOB_OVERHEAD_TRICEPS_STRETCH" to A.HOLD_STRETCH_SHOULDER,
    "MOB_WALL_BICEPS_STRETCH" to A.HOLD_STRETCH_SHOULDER,
    "MOB_WRIST_CIRCLES" to A.SHOULDER_CIRCLE,
    "MOB_NECK_ROTATIONS" to A.NECK_TURN,

    // foam roller
    "MOB_FOAM_ROLL_QUADS" to A.FOAM_ROLL,
    "MOB_FOAM_ROLL_HAMSTRINGS" to A.FOAM_ROLL,
    "MOB_FOAM_ROLL_CALVES" to A.FOAM_ROLL,
    "MOB_FOAM_ROLL_THORACIC" to A.FOAM_ROLL,
    "MOB_FOAM_ROLL_LATS" to A.FOAM_ROLL,
    "MOB_FOAM_ROLL_GLUTES" to A.FOAM_ROLL,
)
