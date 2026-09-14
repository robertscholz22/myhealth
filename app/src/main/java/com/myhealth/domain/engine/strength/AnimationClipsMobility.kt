package com.myhealth.domain.engine.strength

import com.myhealth.domain.model.BodyFace

/**
 * The mobility half of the clip set (P18.1): the stretches, the quadruped flow, the joint circles
 * and the foam-roll drill, plus the static `STANDING` fallback. Split out of `AnimationClips` for
 * R10 only — the object above re-exports every value here, and nothing else should name this one.
 *
 * A stretch is authored as *settle → deep*, so the wrap back to the first keyframe reads as the
 * breath out of the position rather than as a rep. `STRETCH` timing gives the deep keyframe a
 * 2.4 s hold.
 */
internal object MobilityClips {

    val HOLD_STRETCH_HIP: AnimationClip = clip(
        "HOLD_STRETCH_HIP", BodyFace.SIDE, STRETCH,
        sidePose(
            trunk = 6f, pelvis = -6f, hip = -66f, knee = 80f, ankle = -8f, hipFar = 40f, kneeFar = 98f,
            ankleFar = 28f, shoulder = -14f, elbow = -28f, dx = 7.1f, dy = 24f, scale = 0.8f,
        ),
        sidePose(
            trunk = 2f, pelvis = -2f, hip = -70f, knee = 84f, ankle = -8f, hipFar = 48f, kneeFar = 110f,
            ankleFar = 28f, shoulder = -14f, elbow = -28f, dx = 7.1f, dy = 26.4f, scale = 0.8f,
        ),
        mirror = true,
    )

    val HOLD_STRETCH_HAMSTRING: AnimationClip = clip(
        "HOLD_STRETCH_HAMSTRING", BodyFace.SIDE, STRETCH,
        sidePose(
            trunk = 40f, pelvis = -40f, hip = -42f, knee = 6f, hipFar = -2f, kneeFar = 10f,
            ankleFar = -8f, shoulder = -42f, elbow = -10f, dx = -32.4f, dy = 2.5f, scale = 0.9f,
        ),
        sidePose(
            trunk = 48f, pelvis = -48f, hip = -48f, knee = 4f, hipFar = -2f, kneeFar = 10f,
            ankleFar = -8f, shoulder = -48f, elbow = -10f, dx = -32.4f, dy = 2.5f, scale = 0.9f,
        ),
        mirror = true,
    )

    val HOLD_STRETCH_CALF: AnimationClip = clip(
        "HOLD_STRETCH_CALF", BodyFace.SIDE, STRETCH,
        sidePose(
            trunk = 8f, pelvis = -8f, hip = -34f, knee = 38f, ankle = -14f, hipFar = 14f, kneeFar = 2f,
            ankleFar = -24f, shoulder = -88f, elbow = -6f, dx = -20.6f, dy = 3.5f, scale = 0.7f,
        ),
        sidePose(
            trunk = 8f, pelvis = -8f, hip = -40f, knee = 44f, ankle = -14f, hipFar = 18f, kneeFar = 2f,
            ankleFar = -30f, shoulder = -92f, elbow = -6f, dx = -20.6f, dy = 4.5f, scale = 0.7f,
        ),
        mirror = true,
    )

    val HOLD_STRETCH_SHOULDER: AnimationClip = clip(
        "HOLD_STRETCH_SHOULDER", BodyFace.FRONT, STRETCH,
        frontPose(
            shoulder = 168f, elbow = -110f, shoulderRight = -14f, elbowRight = -10f, dx = 3.2f,
            dy = 2.7f, scale = 0.75f,
        ),
        frontPose(
            shoulder = 172f, elbow = -125f, shoulderRight = -14f, elbowRight = -10f, dx = 3.2f,
            dy = 2.7f, scale = 0.75f,
        ),
        mirror = true,
    )

    val QUADRUPED_FLOW: AnimationClip = clip(
        "QUADRUPED_FLOW", BodyFace.SIDE, FLOW,
        sidePose(
            trunk = 86f, pelvis = -4f, hip = -84f, knee = 84f, ankle = 16f, shoulder = -86f, elbow = -4f,
            dx = -10.5f, dy = 14.3f, scale = 0.5f,
        ),
        sidePose(
            trunk = 86f, pelvis = 12f, hip = -94f, knee = 84f, ankle = 16f, shoulder = -86f, elbow = -4f,
            neck = 14f, dx = -10.5f, dy = 14.3f, scale = 0.5f,
        ),
        sidePose(
            trunk = 86f, pelvis = -16f, hip = -72f, knee = 84f, ankle = 16f, shoulder = -86f,
            elbow = -4f, neck = -14f, dx = -10.5f, dy = 14.3f, scale = 0.5f,
        ),
    )

    val ROTATION_THORACIC: AnimationClip = clip(
        "ROTATION_THORACIC", BodyFace.FRONT, STRETCH,
        frontPose(
            trunk = -82f, hip = -68f, knee = 74f, hipRight = -68f, kneeRight = 74f, shoulder = -6f,
            elbow = -4f, shoulderRight = -6f, elbowRight = -4f, dx = 5.7f, dy = -0.7f, scale = 0.5f,
        ),
        frontPose(
            trunk = -82f, hip = -68f, knee = 74f, hipRight = -68f, kneeRight = 74f, shoulder = -6f,
            elbow = -4f, shoulderRight = 94f, elbowRight = -10f, dx = 5.7f, dy = -0.7f, scale = 0.5f,
        ),
        mirror = true,
    )

    val FOAM_ROLL: AnimationClip = clip(
        "FOAM_ROLL", BodyFace.SIDE, STRETCH,
        sidePose(
            trunk = 78f, hip = -6f, knee = 18f, hipFar = -20f, kneeFar = 40f, shoulder = -78f,
            elbow = -70f, dx = -1.9f, dy = 27f, scale = 0.4f,
        ),
        sidePose(
            trunk = 78f, hip = -22f, knee = 42f, hipFar = -20f, kneeFar = 40f, shoulder = -78f,
            elbow = -70f, dx = -1.9f, dy = 27f, scale = 0.4f,
        ),
        mirror = true,
    )

    val SHOULDER_CIRCLE: AnimationClip = clip(
        "SHOULDER_CIRCLE", BodyFace.FRONT, FLOW,
        frontPose(shoulder = 8f, elbow = -8f, dy = 2.8f, scale = 0.45f),
        frontPose(shoulder = 86f, elbow = -8f, dy = 2.8f, scale = 0.45f),
        frontPose(shoulder = 148f, elbow = -8f, dy = 2.8f, scale = 0.45f),
    )

    val WALL_SLIDE: AnimationClip = clip(
        "WALL_SLIDE", BodyFace.FRONT, FLOW,
        frontPose(shoulder = 74f, elbow = 106f, dy = 2.8f, scale = 0.7f),
        frontPose(shoulder = 150f, elbow = 20f, dy = 2.8f, scale = 0.7f),
    )

    val NECK_TURN: AnimationClip = clip(
        "NECK_TURN", BodyFace.FRONT, FLOW,
        frontPose(shoulder = -6f, elbow = 4f, neck = -8f, head = -16f, dy = 2.7f, scale = 0.95f),
        frontPose(shoulder = -6f, elbow = 4f, neck = 8f, head = 16f, dy = 2.7f, scale = 0.95f),
    )

    /**
     * The anatomical figure, not moving: two *identical* keyframes, so it has the same "2 to 4
     * keyframes" shape as every other clip (`an02`) and still renders as a still (`an09`).
     */
    val STANDING: AnimationClip = clip(
        "STANDING", BodyFace.FRONT, HOLD,
        STANDING_POSE,
        STANDING_POSE,
    )
}

private val STANDING_POSE = frontPose(shoulder = -6f, elbow = 4f, dy = -0.4f)
