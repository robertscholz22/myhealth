package com.myhealth.ui.common.body

import com.myhealth.domain.engine.strength.ExerciseCatalog
import com.myhealth.domain.model.Exercise
import com.myhealth.domain.model.MuscleGroup
import com.myhealth.domain.model.StrengthWorkout

/**
 * The body figure's geometry (PLAN §3.12.2, P14.7): front and back silhouettes plus, per
 * [MuscleGroup], one or more closed polygons in a normalised **100 × 220** box. Deliberately kept
 * Compose-free (a plain `Float` point, not `androidx.compose.ui.geometry.Offset`) so it is a plain
 * JVM unit test (`MusclePathsTest`) rather than an instrumented one — the actual drawing lives in
 * [BodyFigure], which scales these polygons to the composable's size.
 *
 * The shapes are simple rounded rectangles, not anatomy: "readability over anatomy" (§3.12.2) —
 * `bf01`…`bf05` pin the *coverage* (every group has a path, every point sits inside the box), not
 * the silhouette's looks (risk K4).
 */
object MusclePaths {

    /** The box every polygon is normalised to. */
    const val WIDTH: Float = 100f
    const val HEIGHT: Float = 220f

    /** One closed shape: a plain vertex list — the canvas connects the last point back to the first. */
    typealias Polygon = List<BodyPoint>

    /** Body outline pieces (head, torso, arms, legs) — not tied to a [MuscleGroup]. */
    val FRONT_OUTLINE: List<Polygon> = listOf(
        rect(40f, 5f, 60f, 26f), // head
        rect(30f, 27f, 70f, 116f), // torso
        rect(12f, 30f, 28f, 106f), // left arm
        rect(72f, 30f, 88f, 106f), // right arm
        rect(32f, 117f, 49f, 210f), // left leg
        rect(51f, 117f, 68f, 210f), // right leg
    )

    val BACK_OUTLINE: List<Polygon> = listOf(
        rect(40f, 5f, 60f, 26f),
        rect(30f, 27f, 70f, 116f),
        rect(12f, 30f, 28f, 106f),
        rect(72f, 30f, 88f, 106f),
        rect(32f, 117f, 49f, 210f),
        rect(51f, 117f, 68f, 210f),
    )

    /** The nine front-visible groups (§2.1: `side == FRONT` or `BOTH`), one or two polygons each. */
    val FRONT: Map<MuscleGroup, List<Polygon>> = mapOf(
        MuscleGroup.CHEST to listOf(rect(36f, 35f, 64f, 55f)),
        MuscleGroup.SHOULDERS_FRONT to listOf(rect(24f, 30f, 34f, 42f), rect(66f, 30f, 76f, 42f)),
        MuscleGroup.BICEPS to listOf(rect(16f, 42f, 27f, 72f), rect(73f, 42f, 84f, 72f)),
        MuscleGroup.FOREARMS to listOf(rect(13f, 74f, 24f, 104f), rect(76f, 74f, 87f, 104f)),
        MuscleGroup.ABS to listOf(rect(42f, 57f, 58f, 92f)),
        MuscleGroup.OBLIQUES to listOf(rect(34f, 57f, 42f, 92f), rect(58f, 57f, 66f, 92f)),
        MuscleGroup.QUADS to listOf(rect(33f, 118f, 48f, 163f), rect(52f, 118f, 67f, 163f)),
        MuscleGroup.ADDUCTORS to listOf(rect(45f, 118f, 50f, 160f), rect(50f, 118f, 55f, 160f)),
        MuscleGroup.CALVES to listOf(rect(35f, 167f, 46f, 208f), rect(54f, 167f, 65f, 208f)),
    )

    /** The nine back-visible groups, one or two polygons each. */
    val BACK: Map<MuscleGroup, List<Polygon>> = mapOf(
        MuscleGroup.TRAPS to listOf(rect(38f, 30f, 62f, 50f)),
        MuscleGroup.SHOULDERS_REAR to listOf(rect(24f, 30f, 34f, 42f), rect(66f, 30f, 76f, 42f)),
        MuscleGroup.TRICEPS to listOf(rect(16f, 42f, 27f, 72f), rect(73f, 42f, 84f, 72f)),
        MuscleGroup.LATS to listOf(rect(32f, 50f, 42f, 80f), rect(58f, 50f, 68f, 80f)),
        MuscleGroup.LOWER_BACK to listOf(rect(42f, 80f, 58f, 100f)),
        MuscleGroup.FOREARMS to listOf(rect(13f, 74f, 24f, 104f), rect(76f, 74f, 87f, 104f)),
        MuscleGroup.GLUTES to listOf(rect(33f, 118f, 48f, 140f), rect(52f, 118f, 67f, 140f)),
        MuscleGroup.HAMSTRINGS to listOf(rect(33f, 140f, 48f, 163f), rect(52f, 140f, 67f, 163f)),
        MuscleGroup.CALVES to listOf(rect(35f, 167f, 46f, 208f), rect(54f, 167f, 65f, 208f)),
    )

    /** Every polygon of [group], front and back combined — `bf01`. */
    fun pathsFor(group: MuscleGroup): List<Polygon> = FRONT[group].orEmpty() + BACK[group].orEmpty()

    /**
     * The group whose bounding box contains `(x, y)` in [groups] (a [FRONT] or [BACK] map) —
     * "approximate hit-testing by the group's path bounds is enough" (§4.2 "Exercises"). `null`
     * outside every group's box.
     */
    fun groupAt(groups: Map<MuscleGroup, List<Polygon>>, x: Float, y: Float): MuscleGroup? =
        groups.entries.firstOrNull { (_, polygons) -> polygons.any { it.boundsContain(x, y) } }?.key

    private fun Polygon.boundsContain(x: Float, y: Float): Boolean {
        val minX = minOf { it.x }
        val maxX = maxOf { it.x }
        val minY = minOf { it.y }
        val maxY = maxOf { it.y }
        return x in minX..maxX && y in minY..maxY
    }

    private fun rect(x0: Float, y0: Float, x1: Float, y1: Float): Polygon =
        listOf(BodyPoint(x0, y0), BodyPoint(x1, y0), BodyPoint(x1, y1), BodyPoint(x0, y1))
}

/** A vertex of a [MusclePaths.Polygon], normalised to the 100 × 220 box. */
data class BodyPoint(val x: Float, val y: Float)

/**
 * The intensity [BodyFigure] fills a group with, clamped to `0f..1f` (§3.12.2): `1.0` a primary
 * mover, `0.35` a secondary one — the same 35 % alpha the spec gives "secondary" everywhere else
 * (`bf04`).
 */
fun highlightFor(exercise: Exercise): Map<MuscleGroup, Float> =
    (exercise.primary.associateWith { 1.0f } + exercise.secondary.associateWith { 0.35f })
        .mapValues { it.value.coerceIn(0f, 1f) }

/**
 * The union of every exercise a workout prescribes, one row's [Exercise] resolved off
 * [ExerciseCatalog] at a time — the group's *highest* intensity across the whole workout wins
 * (`bf05`), so an exercise that hits a muscle as a secondary elsewhere does not dim a group another
 * row already hits as its primary. A row naming an id the catalog dropped contributes nothing.
 */
fun highlightFor(workout: StrengthWorkout): Map<MuscleGroup, Float> {
    val result = mutableMapOf<MuscleGroup, Float>()
    workout.exercises.forEach { row ->
        val exercise = ExerciseCatalog.byId(row.exerciseId) ?: return@forEach
        highlightFor(exercise).forEach { (group, value) ->
            result[group] = maxOf(result[group] ?: 0f, value)
        }
    }
    return result.mapValues { it.value.coerceIn(0f, 1f) }
}
