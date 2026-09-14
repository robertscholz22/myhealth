package com.myhealth.domain.engine.strength

import com.myhealth.domain.model.BodyFace

/**
 * The exercise animation clips (P18.1) — **pure data**, the way `ExerciseCatalog` is: the poses of
 * every shipped exercise written out as named-joint keyframes in the DSL of `AnimationPoses.kt`,
 * with nothing that knows about Compose, a canvas or a frame clock. `ExerciseAnimations` maps all
 * 87 catalog ids onto them; the renderer (P18.2) eases between the keyframes and wraps back to the
 * first.
 *
 * Every clip is authored in the normalised 100 x 220 box of the body model and stays inside it
 * (`an03`), which is the one constraint that shapes the numbers: the figure is 213 units long, so
 * a pose that lies down or throws the arms wide carries a `scale` below 1 for the *whole* clip
 * (never per keyframe — the figure must not pulse), while `dx` / `dy` vary per keyframe to keep the
 * contact point still. The feet stay planted on a floor that scales with the figure; a hanging clip
 * pins the hands to the bar instead, which is what makes the body rise in `PULL_UP` rather than the
 * hands fall.
 *
 * The strength clips live here; the stretches, flows and rolls live in `AnimationClipsMobility.kt`
 * (R10) and are re-exported at the bottom so every call site has one namespace.
 */
object AnimationClips {

    val SQUAT: AnimationClip = clip(
        "SQUAT", BodyFace.SIDE, LIFT,
        sidePose(
            trunk = 4f, pelvis = -4f, shoulder = -14f, elbow = -10f, dx = -12.9f, dy = 2.4f,
            scale = 0.7f,
        ),
        sidePose(
            trunk = 26f, pelvis = -26f, hip = -66f, knee = 82f, ankle = -16f, shoulder = -106f,
            elbow = -10f, dx = -35.3f, dy = 22.5f, scale = 0.7f,
        ),
    )

    val FRONT_SQUAT: AnimationClip = clip(
        "FRONT_SQUAT", BodyFace.SIDE, LIFT,
        sidePose(
            trunk = 4f, pelvis = -4f, shoulder = -45f, elbow = -155f, dx = 3.8f, dy = 2.2f,
            scale = 0.95f,
        ),
        sidePose(
            trunk = 16f, pelvis = -16f, hip = -60f, knee = 76f, ankle = -16f, shoulder = -57f,
            elbow = -155f, dx = -24.7f, dy = 25.3f, scale = 0.95f,
        ),
    )

    val HINGE: AnimationClip = clip(
        "HINGE", BodyFace.SIDE, LIFT,
        sidePose(shoulder = -8f, elbow = -5f, dx = -33f, dy = 2.2f, scale = 0.9f),
        sidePose(
            trunk = 44f, pelvis = -44f, hip = -4f, knee = 16f, ankle = -10f, shoulder = -42f,
            elbow = -5f, dx = -29.1f, dy = 2.6f, scale = 0.9f,
        ),
    )

    val SINGLE_LEG_HINGE: AnimationClip = clip(
        "SINGLE_LEG_HINGE", BodyFace.SIDE, LIFT,
        sidePose(shoulder = -8f, dx = -5.3f, dy = 2.4f, scale = 0.65f),
        sidePose(
            trunk = 42f, pelvis = -42f, hip = -2f, knee = 12f, ankle = -8f, hipFar = 42f, kneeFar = -8f,
            ankleFar = -18f, shoulder = -40f, dx = -5.3f, dy = 2.5f, scale = 0.65f,
        ),
        mirror = true,
    )

    val LUNGE: AnimationClip = clip(
        "LUNGE", BodyFace.SIDE, LIFT,
        sidePose(trunk = 4f, pelvis = -4f, shoulder = -10f, dx = 6f, dy = 2.3f, scale = 0.8f),
        sidePose(
            trunk = 8f, pelvis = -8f, hip = -64f, knee = 74f, ankle = -8f, hipFar = 16f, kneeFar = 86f,
            ankleFar = 32f, shoulder = -12f, dx = 6f, dy = 23.1f, scale = 0.8f,
        ),
        mirror = true,
    )

    val SPLIT_SQUAT: AnimationClip = clip(
        "SPLIT_SQUAT", BodyFace.SIDE, LIFT,
        sidePose(
            trunk = 6f, pelvis = -6f, hip = -22f, knee = 24f, ankle = -2f, hipFar = 26f, kneeFar = 36f,
            ankleFar = 18f, shoulder = -10f, dx = 6.2f, dy = 5f, scale = 0.8f,
        ),
        sidePose(
            trunk = 10f, pelvis = -10f, hip = -68f, knee = 78f, ankle = -10f, hipFar = 22f,
            kneeFar = 94f, ankleFar = 32f, shoulder = -12f, dx = 6.2f, dy = 25.7f, scale = 0.8f,
        ),
        mirror = true,
    )

    val STEP_UP: AnimationClip = clip(
        "STEP_UP", BodyFace.SIDE, LIFT,
        sidePose(
            trunk = 8f, pelvis = -8f, hip = -80f, knee = 84f, ankle = -4f, hipFar = 4f, kneeFar = 4f,
            shoulder = -14f, dx = 3.2f, dy = 2.1f, scale = 0.75f,
        ),
        sidePose(
            trunk = 5f, pelvis = -5f, hip = -6f, knee = 4f, hipFar = 28f, kneeFar = 24f, ankleFar = 20f,
            shoulder = -10f, dx = 3.2f, dy = 2.4f, scale = 0.75f,
        ),
        mirror = true,
    )

    val CALF_RAISE: AnimationClip = clip(
        "CALF_RAISE", BodyFace.SIDE, LIFT,
        sidePose(shoulder = -6f, dx = -0.2f, dy = 2.2f, scale = 0.95f),
        sidePose(knee = 2f, ankle = 32f, shoulder = -6f, dx = 1f, dy = -2.7f, scale = 0.95f),
    )

    val JUMP: AnimationClip = clip(
        "JUMP", BodyFace.SIDE, FAST,
        sidePose(
            trunk = 22f, pelvis = -22f, hip = -44f, knee = 56f, ankle = -14f, shoulder = 38f,
            elbow = 18f, dx = -1.6f, dy = 12.4f, scale = 0.75f,
        ),
        sidePose(
            trunk = 3f, pelvis = -3f, hip = -3f, ankle = 26f, shoulder = -140f, elbow = -8f, dx = -1.6f,
            dy = -0.6f, scale = 0.75f,
        ),
    )

    val SWING: AnimationClip = clip(
        "SWING", BodyFace.SIDE, FAST,
        sidePose(
            trunk = 42f, pelvis = -42f, hip = -6f, knee = 20f, ankle = -10f, shoulder = -32f,
            elbow = -6f, dx = -30.8f, dy = 2.6f, scale = 0.9f,
        ),
        sidePose(
            trunk = 2f, pelvis = -2f, shoulder = -84f, elbow = -4f, dx = -34.4f, dy = 2.2f,
            scale = 0.9f,
        ),
    )

    val KNEE_ISOLATION: AnimationClip = clip(
        "KNEE_ISOLATION", BodyFace.SIDE, LIFT,
        sidePose(
            trunk = 5f, pelvis = -5f, hip = -8f, knee = 6f, shoulder = -8f, elbow = -20f, dx = 5.3f,
            dy = 2.3f, scale = 0.95f,
        ),
        sidePose(
            trunk = 5f, pelvis = -5f, hip = -8f, knee = 86f, shoulder = -8f, elbow = -20f, dx = 5.3f,
            dy = 25.7f, scale = 0.95f,
        ),
    )

    val HIP_ABDUCTION: AnimationClip = clip(
        "HIP_ABDUCTION", BodyFace.FRONT, LIFT,
        frontPose(shoulder = -12f, elbow = 6f, dx = -16f, dy = -0.4f),
        frontPose(hipRight = -30f, shoulder = -12f, elbow = 6f, dx = -16f, dy = -0.4f),
        mirror = true,
    )

    val CARRY: AnimationClip = clip(
        "CARRY", BodyFace.SIDE, FLOW,
        sidePose(hip = -14f, hipFar = 12f, kneeFar = 8f, shoulder = -4f, elbow = -3f, dx = 0.4f, dy = -0.8f),
        sidePose(
            hip = 12f, knee = 8f, hipFar = -14f, kneeFar = 0f, shoulder = -4f, elbow = -3f, dx = 0.4f,
            dy = -1.3f,
        ),
        mirror = true,
    )

    val BENCH_PRESS: AnimationClip = clip(
        "BENCH_PRESS", BodyFace.SIDE, LIFT,
        sidePose(
            trunk = -90f, hip = -80f, knee = 86f, ankle = -6f, shoulder = -90f, elbow = -2f, dx = 9.4f,
            dy = 18.4f, scale = 0.5f,
        ),
        sidePose(
            trunk = -90f, hip = -80f, knee = 86f, ankle = -6f, shoulder = -60f, elbow = -60f, dx = 9.4f,
            dy = 18.4f, scale = 0.5f,
        ),
    )

    val PUSH_UP: AnimationClip = clip(
        "PUSH_UP", BodyFace.SIDE, LIFT,
        sidePose(trunk = 90f, hip = 5f, shoulder = -90f, dx = 0.3f, dy = 10.2f, scale = 0.4f),
        sidePose(trunk = 90f, hip = 5f, shoulder = -46f, elbow = -44f, dx = 0.3f, dy = 14.6f, scale = 0.4f),
    )

    val OVERHEAD_PRESS: AnimationClip = clip(
        "OVERHEAD_PRESS", BodyFace.FRONT, LIFT,
        frontPose(shoulder = 40f, elbow = 140f, dy = 2.8f, scale = 0.7f),
        frontPose(shoulder = 170f, elbow = -10f, dy = 2.8f, scale = 0.7f),
    )

    val DIP: AnimationClip = clip(
        "DIP", BodyFace.SIDE, LIFT,
        sidePose(hip = -20f, knee = 46f, ankle = 18f, shoulder = 10f, elbow = -10f, dx = 5.6f, dy = -0.2f),
        sidePose(hip = -20f, knee = 46f, ankle = 18f, shoulder = 40f, elbow = -40f, dx = 5.6f, dy = -0.2f),
    )

    val ROW_BENT: AnimationClip = clip(
        "ROW_BENT", BodyFace.SIDE, LIFT,
        sidePose(
            trunk = 44f, pelvis = -44f, hip = -6f, knee = 20f, ankle = -10f, shoulder = -44f,
            elbow = -4f, dx = -31.8f, dy = 2.6f, scale = 0.95f,
        ),
        sidePose(
            trunk = 44f, pelvis = -44f, hip = -6f, knee = 20f, ankle = -10f, shoulder = 30f,
            elbow = -74f, dx = -31.8f, dy = 2.6f, scale = 0.95f,
        ),
    )

    val ROW_SEATED: AnimationClip = clip(
        "ROW_SEATED", BodyFace.SIDE, LIFT,
        sidePose(
            trunk = 8f, pelvis = -8f, hip = -80f, knee = 70f, ankle = -10f, shoulder = -92f, elbow = -6f,
            dx = -26.4f, dy = 13.5f, scale = 0.75f,
        ),
        sidePose(
            trunk = -4f, pelvis = 4f, hip = -80f, knee = 70f, ankle = -10f, shoulder = 20f,
            elbow = -110f, dx = -26.4f, dy = 13.5f, scale = 0.75f,
        ),
    )

    val PULL_UP: AnimationClip = clip(
        "PULL_UP", BodyFace.FRONT, LIFT,
        frontPose(hip = 5f, knee = -16f, shoulder = 165f, elbow = -6f, dx = 8.4f, dy = 31.3f, scale = 0.7f),
        frontPose(hip = 5f, knee = -16f, shoulder = 40f, elbow = 135f, dx = -1f, dy = -14.6f, scale = 0.7f),
    )

    val PULLDOWN: AnimationClip = clip(
        "PULLDOWN", BodyFace.FRONT, LIFT,
        frontPose(shoulder = 165f, elbow = -6f, dy = 2.8f, scale = 0.7f),
        frontPose(shoulder = 60f, elbow = 115f, dy = 2.8f, scale = 0.7f),
    )

    val HANG: AnimationClip = clip(
        "HANG", BodyFace.SIDE, HOLD,
        sidePose(shoulder = -170f, elbow = -2f, dx = -1.6f, dy = 31.3f, scale = 0.7f),
        sidePose(hip = 4f, shoulder = -174f, elbow = -2f, dx = 1.9f, dy = 31.6f, scale = 0.7f),
    )

    val LEG_RAISE: AnimationClip = clip(
        "LEG_RAISE", BodyFace.SIDE, LIFT,
        sidePose(hip = 5f, knee = -5f, shoulder = -170f, elbow = -2f, dx = -26.4f, dy = 31.3f, scale = 0.7f),
        sidePose(
            hip = -92f, knee = 8f, shoulder = -170f, elbow = -2f, dx = -26.4f, dy = 31.3f,
            scale = 0.7f,
        ),
    )

    val LATERAL_RAISE: AnimationClip = clip(
        "LATERAL_RAISE", BodyFace.FRONT, LIFT,
        frontPose(shoulder = 6f, elbow = -10f, dy = 2.8f, scale = 0.45f),
        frontPose(shoulder = 90f, elbow = -6f, dy = 2.8f, scale = 0.45f),
    )

    val REAR_DELT: AnimationClip = clip(
        "REAR_DELT", BodyFace.BACK, LIFT,
        frontPose(shoulder = -14f, elbow = -38f, dy = 2.8f, scale = 0.45f),
        frontPose(shoulder = 76f, elbow = -28f, dy = 2.8f, scale = 0.45f),
    )

    val CURL: AnimationClip = clip(
        "CURL", BodyFace.SIDE, LIFT,
        sidePose(shoulder = -8f, elbow = -6f, dx = -15.8f, dy = -0.9f),
        sidePose(shoulder = -14f, elbow = -124f, dx = -15.8f, dy = -0.9f),
    )

    val TRICEPS_EXTENSION: AnimationClip = clip(
        "TRICEPS_EXTENSION", BodyFace.SIDE, LIFT,
        sidePose(shoulder = -160f, elbow = -114f, dx = 4.8f, dy = 2.4f, scale = 0.7f),
        sidePose(shoulder = -166f, elbow = -6f, dx = 4.8f, dy = 2.4f, scale = 0.7f),
    )

    val PLANK: AnimationClip = clip(
        "PLANK", BodyFace.SIDE, HOLD,
        sidePose(trunk = 90f, hip = 4f, shoulder = -88f, elbow = -80f, dx = -2f, dy = 23.8f, scale = 0.4f),
        sidePose(trunk = 90f, hip = 4f, shoulder = -92f, elbow = -84f, dx = -2f, dy = 26.1f, scale = 0.4f),
    )

    val SIDE_PLANK: AnimationClip = clip(
        "SIDE_PLANK", BodyFace.FRONT, HOLD,
        frontPose(
            trunk = -70f, hip = 2f, shoulder = -86f, elbow = -4f, shoulderRight = 28f, elbowRight = 0f,
            dx = -0.7f, dy = 7.3f, scale = 0.45f,
        ),
        frontPose(
            trunk = -70f, hip = 2f, shoulder = -90f, elbow = -4f, shoulderRight = 32f, elbowRight = 0f,
            dx = -0.7f, dy = 7.3f, scale = 0.45f,
        ),
        mirror = true,
    )

    val DEAD_BUG: AnimationClip = clip(
        "DEAD_BUG", BodyFace.SIDE, LIFT,
        sidePose(
            trunk = -90f, hip = -84f, knee = 86f, shoulder = -90f, elbow = -4f, dx = 0.1f, dy = 16.7f,
            scale = 0.45f,
        ),
        sidePose(
            trunk = -90f, hip = -12f, knee = 12f, hipFar = -84f, kneeFar = 86f, shoulder = -90f,
            elbow = -4f, shoulderFar = -45f, dx = 0.1f, dy = 16.7f, scale = 0.45f,
        ),
        mirror = true,
    )

    val TWIST: AnimationClip = clip(
        "TWIST", BodyFace.FRONT, LIFT,
        frontPose(
            torso = -14f, hip = -24f, knee = 26f, shoulder = -40f, elbow = -60f, shoulderRight = -80f,
            elbowRight = -40f, dx = 1.4f, dy = 4f, scale = 0.45f,
        ),
        frontPose(
            torso = 14f, hip = -24f, knee = 26f, shoulder = 80f, elbow = -40f, shoulderRight = 40f,
            elbowRight = -60f, dx = 1.4f, dy = 4f, scale = 0.45f,
        ),
        mirror = true,
    )

    val BACK_EXTENSION: AnimationClip = clip(
        "BACK_EXTENSION", BodyFace.SIDE, LIFT,
        sidePose(trunk = 96f, hip = -4f, shoulder = -82f, elbow = -94f, dx = -2.4f, dy = 22f, scale = 0.4f),
        sidePose(
            trunk = 72f, hip = -4f, shoulder = -82f, elbow = -94f, dx = -2.4f, dy = 24.9f,
            scale = 0.4f,
        ),
    )

    val HIP_THRUST: AnimationClip = clip(
        "HIP_THRUST", BodyFace.SIDE, LIFT,
        sidePose(
            trunk = -90f, hip = -88f, knee = 88f, ankle = -4f, shoulder = 4f, elbow = -6f, dx = 5.6f,
            dy = 10.7f, scale = 0.5f,
        ),
        sidePose(
            trunk = -62f, pelvis = -26f, hip = -62f, knee = 88f, ankle = -4f, shoulder = 4f, elbow = -6f,
            dx = 5.6f, dy = 10.7f, scale = 0.5f,
        ),
    )

    // ---- re-exported from AnimationClipsMobility.kt (R10: one object, two files) --------------

    val HOLD_STRETCH_HIP: AnimationClip = MobilityClips.HOLD_STRETCH_HIP
    val HOLD_STRETCH_HAMSTRING: AnimationClip = MobilityClips.HOLD_STRETCH_HAMSTRING
    val HOLD_STRETCH_CALF: AnimationClip = MobilityClips.HOLD_STRETCH_CALF
    val HOLD_STRETCH_SHOULDER: AnimationClip = MobilityClips.HOLD_STRETCH_SHOULDER
    val QUADRUPED_FLOW: AnimationClip = MobilityClips.QUADRUPED_FLOW
    val ROTATION_THORACIC: AnimationClip = MobilityClips.ROTATION_THORACIC
    val FOAM_ROLL: AnimationClip = MobilityClips.FOAM_ROLL
    val SHOULDER_CIRCLE: AnimationClip = MobilityClips.SHOULDER_CIRCLE
    val WALL_SLIDE: AnimationClip = MobilityClips.WALL_SLIDE
    val NECK_TURN: AnimationClip = MobilityClips.NECK_TURN

    /** The fallback every unknown id lands on: the anatomical figure, not moving (`an09`). */
    val STANDING: AnimationClip = MobilityClips.STANDING

    /** Every clip, in declaration order — what `an02` / `an03` iterate. */
    val ALL: List<AnimationClip> = listOf(
        SQUAT, FRONT_SQUAT, HINGE, SINGLE_LEG_HINGE, LUNGE, SPLIT_SQUAT, STEP_UP, CALF_RAISE,
        JUMP, SWING, KNEE_ISOLATION, HIP_ABDUCTION, CARRY, BENCH_PRESS, PUSH_UP, OVERHEAD_PRESS,
        DIP, ROW_BENT, ROW_SEATED, PULL_UP, PULLDOWN, HANG, LEG_RAISE, LATERAL_RAISE, REAR_DELT,
        CURL, TRICEPS_EXTENSION, PLANK, SIDE_PLANK, DEAD_BUG, TWIST, BACK_EXTENSION, HIP_THRUST,
        HOLD_STRETCH_HIP, HOLD_STRETCH_HAMSTRING, HOLD_STRETCH_CALF, HOLD_STRETCH_SHOULDER,
        QUADRUPED_FLOW, ROTATION_THORACIC, FOAM_ROLL, SHOULDER_CIRCLE, WALL_SLIDE, NECK_TURN,
        STANDING,
    )

    init {
        check(ALL.map { it.id }.toSet().size == ALL.size) { "AnimationClips has duplicate ids" }
    }
}
