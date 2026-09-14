package com.myhealth.ui.common.body

import com.myhealth.domain.model.MuscleGroup
import kotlin.math.cos
import kotlin.math.sin

/**
 * The body figure's **jointed segment model** (P15.1). Where P14.7 drew a fixed list of rectangles,
 * the figure is now a little skeleton: sixteen [BodySegment]s hanging off the torso, each carrying
 * a rounded outline and its muscle regions in its *own* local frame, and a [BodyPose] that rotates
 * segments about their joints. Composing the parent chain ([worldPolygons] / [outlinePolygons])
 * produces exactly what the drawing code has always consumed — closed polygons in the normalised
 * **100 × 220** box of [MusclePaths] — so nothing downstream had to change.
 *
 * The pose exists for the *next* step (P15.2, exercise animations: keyframe poses per movement
 * pattern, interpolated by a Compose transition). Today every caller passes [BodyPose.STANDING].
 *
 * Pure Kotlin: no Compose types, no `android.*`. `BodyModelTest` (`bm01`…`bm05`) is a plain JVM test.
 */

/** The sixteen parts the figure is built from; `_L` / `_R` are the *subject's* left and right. */
enum class BodySegmentId {
    HEAD,
    NECK,
    TORSO,
    PELVIS,
    UPPER_ARM_L,
    UPPER_ARM_R,
    FOREARM_L,
    FOREARM_R,
    HAND_L,
    HAND_R,
    THIGH_L,
    THIGH_R,
    SHANK_L,
    SHANK_R,
    FOOT_L,
    FOOT_R,
}

/** Which silhouette a skeleton draws — the muscle regions differ, the bones do not. */
enum class BodyFace { FRONT, BACK }

/**
 * One rigid part of the figure.
 *
 * @param pivot the joint's position **in the parent's local frame** (in the 100 × 220 box itself
 *   when [parent] is `null`, i.e. for the root [BodySegmentId.TORSO]).
 * @param outline the part's silhouette **in the segment's own local frame**, pivot at the origin.
 * @param muscles the muscle regions drawn on this part, in the same local frame as [outline].
 */
data class BodySegment(
    val id: BodySegmentId,
    val parent: BodySegmentId?,
    val pivot: BodyPoint,
    val outline: MusclePaths.Polygon,
    val muscles: Map<MuscleGroup, List<MusclePaths.Polygon>> = emptyMap(),
)

/**
 * A rotation in **degrees** per segment, about that segment's own [BodySegment.pivot]; positive is
 * clockwise on screen (y grows downwards). Rotations compose down the chain, so bending the elbow
 * carries the hand with it and leaves the torso alone (`bm02`).
 */
data class BodyPose(val angles: Map<BodySegmentId, Float> = emptyMap()) {

    fun angleOf(id: BodySegmentId): Float = angles[id] ?: 0f

    /** Every joint at zero: the anatomical standing figure [MusclePaths] is derived from. */
    companion object {
        val STANDING: BodyPose = BodyPose()
    }
}

/** The front and back skeletons. Mirror images share their data — see `BodyGeometry`. */
object BodySkeleton {

    val FRONT: List<BodySegment> = BodyGeometry.segments(BodyMuscles.FRONT)

    val BACK: List<BodySegment> = BodyGeometry.mirrored(BodyGeometry.segments(BodyMuscles.BACK))

    fun segmentsOf(face: BodyFace): List<BodySegment> = if (face == BodyFace.FRONT) FRONT else BACK
}

/**
 * Every muscle region of [face] in world space (the 100 × 220 box) at [pose], merged across the
 * segments that carry the group — e.g. `FOREARMS` collects one polygon from each forearm.
 */
fun BodySkeleton.worldPolygons(
    face: BodyFace,
    pose: BodyPose = BodyPose.STANDING,
): Map<MuscleGroup, List<MusclePaths.Polygon>> {
    val segments = segmentsOf(face)
    val frames = frames(segments, pose)
    val result = LinkedHashMap<MuscleGroup, MutableList<MusclePaths.Polygon>>()
    segments.forEach { segment ->
        val frame = frames.getValue(segment.id)
        segment.muscles.forEach { (group, polygons) ->
            val target = result.getOrPut(group) { mutableListOf() }
            polygons.forEach { target += frame.apply(it) }
        }
    }
    return result
}

/** Every silhouette piece of [face] in world space at [pose] — head, limbs, torso, hands, feet. */
fun BodySkeleton.outlinePolygons(
    face: BodyFace,
    pose: BodyPose = BodyPose.STANDING,
): List<MusclePaths.Polygon> {
    val segments = segmentsOf(face)
    val frames = frames(segments, pose)
    return segments.map { frames.getValue(it.id).apply(it.outline) }
}

/** `true` when ([x], [y]) is inside [polygon] — the ray-casting test `groupAt` hit-tests with. */
internal fun pointInPolygon(polygon: MusclePaths.Polygon, x: Float, y: Float): Boolean {
    if (polygon.size < 3) return false
    var inside = false
    var j = polygon.lastIndex
    for (i in polygon.indices) {
        val a = polygon[i]
        val b = polygon[j]
        if ((a.y > y) != (b.y > y)) {
            val cut = (b.x - a.x) * (y - a.y) / (b.y - a.y) + a.x
            if (x < cut) inside = !inside
        }
        j = i
    }
    return inside
}

/**
 * A rigid transform (rotation + translation) from a segment's local frame to the box. Kept as the
 * four numbers rather than a matrix class so the model owes nothing to Compose or `android.graphics`.
 */
internal data class BodyFrame(val cos: Float, val sin: Float, val tx: Float, val ty: Float) {

    fun apply(point: BodyPoint): BodyPoint =
        BodyPoint(cos * point.x - sin * point.y + tx, sin * point.x + cos * point.y + ty)

    fun apply(polygon: MusclePaths.Polygon): MusclePaths.Polygon = polygon.map { apply(it) }

    /** `this ∘ child`: [child]'s frame expressed in this frame's parent. */
    fun then(child: BodyFrame): BodyFrame = BodyFrame(
        cos = cos * child.cos - sin * child.sin,
        sin = sin * child.cos + cos * child.sin,
        tx = cos * child.tx - sin * child.ty + tx,
        ty = sin * child.tx + cos * child.ty + ty,
    )

    companion object {
        val IDENTITY = BodyFrame(1f, 0f, 0f, 0f)

        fun of(pivot: BodyPoint, degrees: Float): BodyFrame {
            val rad = degrees * DEG_TO_RAD
            return BodyFrame(cos(rad), sin(rad), pivot.x, pivot.y)
        }
    }
}

/** Resolves every segment's local-to-box transform by walking the parent chain once, with memoing. */
internal fun frames(segments: List<BodySegment>, pose: BodyPose): Map<BodySegmentId, BodyFrame> {
    val byId = segments.associateBy { it.id }
    val resolved = HashMap<BodySegmentId, BodyFrame>(segments.size)
    fun resolve(id: BodySegmentId): BodyFrame = resolved.getOrPut(id) {
        val segment = byId.getValue(id)
        val local = BodyFrame.of(segment.pivot, pose.angleOf(id))
        val parent = segment.parent?.let { resolve(it) } ?: BodyFrame.IDENTITY
        parent.then(local)
    }
    segments.forEach { resolve(it.id) }
    return resolved
}

private const val DEG_TO_RAD = (Math.PI / 180.0).toFloat()
