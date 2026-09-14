package com.myhealth.ui.common.body

import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import com.myhealth.domain.model.MuscleGroup
import org.junit.Test
import kotlin.math.abs

/** P15.1's `bm01`…`bm05`, over the jointed body model behind [MusclePaths]. */
class BodyModelTest {

    @Test
    fun bm01_standing_pose_polygons_are_inside_the_box() {
        BodyFace.entries.forEach { face ->
            val polygons = BodySkeleton.outlinePolygons(face, BodyPose.STANDING) +
                BodySkeleton.worldPolygons(face, BodyPose.STANDING).values.flatten()
            assertThat(polygons).isNotEmpty()
            polygons.flatten().forEach { point ->
                assertThat(point.x >= 0f && point.x <= MusclePaths.WIDTH).isTrue()
                assertThat(point.y >= 0f && point.y <= MusclePaths.HEIGHT).isTrue()
            }
            // The figure fills the box: head near the top, soles near the bottom.
            val ys = polygons.flatten().map { it.y }
            assertThat(ys.min()).isLessThan(10f)
            assertThat(ys.max()).isGreaterThan(205f)
        }
    }

    @Test
    fun bm02_rotating_a_forearm_moves_its_hand_but_not_the_torso() {
        val standing = frames(BodySkeleton.FRONT, BodyPose.STANDING)
        val bent = frames(
            BodySkeleton.FRONT,
            BodyPose(mapOf(BodySegmentId.FOREARM_L to -90f)),
        )
        val origin = BodyPoint(0f, 0f)

        // The hand hangs off the rotated forearm, so it swings with it.
        val handStanding = standing.getValue(BodySegmentId.HAND_L).apply(origin)
        val handBent = bent.getValue(BodySegmentId.HAND_L).apply(origin)
        assertThat(distance(handStanding, handBent)).isGreaterThan(20f)

        // Its parent chain above the elbow is untouched.
        listOf(BodySegmentId.TORSO, BodySegmentId.HEAD, BodySegmentId.UPPER_ARM_L, BodySegmentId.THIGH_R)
            .forEach { id ->
                assertThat(distance(standing.getValue(id).apply(origin), bent.getValue(id).apply(origin)))
                    .isLessThan(0.001f)
            }

        // A −90° rotation about the elbow lifts the wrist to the elbow's own height.
        val elbow = standing.getValue(BodySegmentId.FOREARM_L).apply(origin)
        assertThat(abs(handBent.y - elbow.y)).isLessThan(6f)
        // …and swings it across the body (screen y grows downwards, so −90° is anticlockwise).
        assertThat(handBent.x).isGreaterThan(handStanding.x + 20f)
    }

    @Test
    fun bm03_point_in_polygon_hit_test() {
        val square = listOf(
            BodyPoint(0f, 0f), BodyPoint(10f, 0f), BodyPoint(10f, 10f), BodyPoint(0f, 10f),
        )
        assertThat(pointInPolygon(square, 5f, 5f)).isTrue()
        assertThat(pointInPolygon(square, 11f, 5f)).isFalse()

        // A concave "L": the notch is outside even though it is inside the bounding box.
        val ell = listOf(
            BodyPoint(0f, 0f), BodyPoint(4f, 0f), BodyPoint(4f, 6f),
            BodyPoint(10f, 6f), BodyPoint(10f, 10f), BodyPoint(0f, 10f),
        )
        assertThat(pointInPolygon(ell, 2f, 2f)).isTrue()
        assertThat(pointInPolygon(ell, 8f, 2f)).isFalse()

        // On the real figure: the chest is hit inside the torso and missed beside it.
        val chest = MusclePaths.FRONT.getValue(MuscleGroup.CHEST)
        val inside = chest.first().let { p -> BodyPoint(p.map { it.x }.average().toFloat(), p.map { it.y }.average().toFloat()) }
        assertThat(MusclePaths.groupAt(MusclePaths.FRONT, inside.x, inside.y)).isEqualTo(MuscleGroup.CHEST)
        assertThat(MusclePaths.groupAt(MusclePaths.FRONT, 2f, 2f)).isNull()
        // The gap between the arm and the torso is not claimed by anything (P14.7's bounds test was).
        assertThat(MusclePaths.groupAt(MusclePaths.FRONT, 50f, 210f)).isNull()
    }

    @Test
    fun bm04_muscle_regions_lie_inside_their_segment_outline_bounds() {
        val tolerance = 0.5f
        listOf(BodySkeleton.FRONT, BodySkeleton.BACK).forEach { segments ->
            segments.forEach { segment ->
                if (segment.muscles.isEmpty()) return@forEach
                val outline = segment.outline
                val minX = outline.minOf { it.x } - tolerance
                val maxX = outline.maxOf { it.x } + tolerance
                val minY = outline.minOf { it.y } - tolerance
                val maxY = outline.maxOf { it.y } + tolerance
                segment.muscles.forEach { (group, polygons) ->
                    polygons.flatten().forEach { point ->
                        assertWithMessage("${segment.id} / $group point $point")
                            .that(point.x in minX..maxX && point.y in minY..maxY)
                            .isTrue()
                    }
                }
            }
        }
    }

    @Test
    fun bm05_front_and_back_are_mirrored_consistently() {
        val front = BodySkeleton.FRONT.associateBy { it.id }
        val back = BodySkeleton.BACK.associateBy { it.id }
        assertThat(back.keys).isEqualTo(front.keys)
        assertThat(front.keys).containsExactlyElementsIn(BodySegmentId.entries)

        front.forEach { (id, segment) ->
            val other = back.getValue(id)
            assertThat(other.parent).isEqualTo(segment.parent)
            assertThat(other.pivot.y).isWithin(TOL).of(segment.pivot.y)
            val mirroredX = if (segment.parent == null) {
                MusclePaths.WIDTH - segment.pivot.x
            } else {
                -segment.pivot.x
            }
            assertThat(other.pivot.x).isWithin(TOL).of(mirroredX)
            // The bones are the same shape, just flipped: same bounds width, mirrored x extent.
            assertThat(other.outline.size).isEqualTo(segment.outline.size)
            assertThat(other.outline.maxOf { it.x }).isWithin(TOL).of(-segment.outline.minOf { it.x })
            assertThat(other.outline.minOf { it.x }).isWithin(TOL).of(-segment.outline.maxOf { it.x })
        }

        // Both silhouettes therefore occupy the same place in the box.
        val frontBounds = BodySkeleton.outlinePolygons(BodyFace.FRONT).flatten()
        val backBounds = BodySkeleton.outlinePolygons(BodyFace.BACK).flatten()
        assertThat(backBounds.minOf { it.x }).isWithin(TOL).of(frontBounds.minOf { it.x })
        assertThat(backBounds.maxOf { it.x }).isWithin(TOL).of(frontBounds.maxOf { it.x })
        assertThat(backBounds.maxOf { it.y }).isWithin(TOL).of(frontBounds.maxOf { it.y })
    }

    private fun distance(a: BodyPoint, b: BodyPoint): Float {
        val dx = a.x - b.x
        val dy = a.y - b.y
        return kotlin.math.sqrt(dx * dx + dy * dy)
    }

    private companion object {
        const val TOL = 0.001f
    }
}
