package com.myhealth.ui.common.body

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.myhealth.domain.model.MuscleGroup
import com.myhealth.ui.theme.MyHealthTheme

/** The hairline outline stroke every silhouette and muscle shape is drawn with. */
private val OUTLINE_WIDTH = 1.dp

/**
 * The body figure (PLAN §3.12.2, P14.7): a front silhouette and a back silhouette side by side,
 * each muscle group filled by [highlight]'s intensity for that group — `1.0` reads as
 * `colorScheme.primary`, `0.0` as `colorScheme.surfaceVariant`, and everything between as a linear
 * blend of the two (so `0.35`, the spec's "secondary" value, reads the same as `primary` at 35 %
 * alpha over the card background). Pure `Canvas`, no images, no new dependency.
 *
 * The same composable backs the exercise/workout figures (primary/secondary intensities) and the
 * Load screen's muscle heat map (P14.8, a continuous `load / ref` clamped to `0..1`) — [highlight]
 * does not care which produced it.
 *
 * [onFrontTap]/[onBackTap] turn the figure into the Exercises screen's muscle filter (§4.2): a tap
 * is hit-tested against each group's polygon **bounding box** ([MusclePaths.groupAt]) — "readable,
 * not anatomically precise" is explicitly good enough here (§4.2 "Exercises").
 */
@Composable
fun BodyFigure(
    highlight: Map<MuscleGroup, Float>,
    modifier: Modifier = Modifier,
    onFrontTap: ((MuscleGroup) -> Unit)? = null,
    onBackTap: ((MuscleGroup) -> Unit)? = null,
) {
    Row(modifier = modifier) {
        BodyView(
            groups = MusclePaths.FRONT,
            outline = MusclePaths.FRONT_OUTLINE,
            highlight = highlight,
            onTap = onFrontTap,
            modifier = Modifier.aspectRatio(MusclePaths.WIDTH / MusclePaths.HEIGHT),
        )
        BodyView(
            groups = MusclePaths.BACK,
            outline = MusclePaths.BACK_OUTLINE,
            highlight = highlight,
            onTap = onBackTap,
            modifier = Modifier.aspectRatio(MusclePaths.WIDTH / MusclePaths.HEIGHT),
        )
    }
}

@Composable
private fun BodyView(
    groups: Map<MuscleGroup, List<MusclePaths.Polygon>>,
    outline: List<MusclePaths.Polygon>,
    highlight: Map<MuscleGroup, Float>,
    onTap: ((MuscleGroup) -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val unused = MaterialTheme.colorScheme.surfaceVariant
    val filled = MaterialTheme.colorScheme.primary
    val outlineColor = MaterialTheme.colorScheme.outline

    val tapModifier = if (onTap == null) {
        Modifier
    } else {
        Modifier.pointerInput(groups) {
            detectTapGestures { offset ->
                val x = offset.x / size.width * MusclePaths.WIDTH
                val y = offset.y / size.height * MusclePaths.HEIGHT
                MusclePaths.groupAt(groups, x, y)?.let(onTap)
            }
        }
    }

    Canvas(modifier = modifier.then(tapModifier)) {
        val scaleX = size.width / MusclePaths.WIDTH
        val scaleY = size.height / MusclePaths.HEIGHT

        fun toPath(polygon: MusclePaths.Polygon): Path = Path().apply {
            polygon.forEachIndexed { index, point ->
                val offset = Offset(point.x * scaleX, point.y * scaleY)
                if (index == 0) moveTo(offset.x, offset.y) else lineTo(offset.x, offset.y)
            }
            close()
        }

        outline.forEach { polygon ->
            val path = toPath(polygon)
            drawPath(path, color = unused)
            drawPath(path, color = outlineColor, style = Stroke(width = OUTLINE_WIDTH.toPx()))
        }
        groups.forEach { (group, polygons) ->
            val intensity = (highlight[group] ?: 0f).coerceIn(0f, 1f)
            val color = colorFor(intensity, unused, filled)
            polygons.forEach { polygon ->
                val path = toPath(polygon)
                drawPath(path, color = color)
                drawPath(path, color = outlineColor, style = Stroke(width = OUTLINE_WIDTH.toPx()))
            }
        }
    }
}

/** `intensity` linearly blended from [unused] (0) to [filled] (1) — §3.12.2's fill rule. */
private fun colorFor(intensity: Float, unused: Color, filled: Color): Color = lerp(unused, filled, intensity)

@Preview(showBackground = true)
@Composable
private fun BodyFigurePreview() {
    MyHealthTheme(dynamicColor = false) {
        BodyFigure(
            highlight = mapOf(
                MuscleGroup.CHEST to 1.0f,
                MuscleGroup.TRICEPS to 0.35f,
                MuscleGroup.SHOULDERS_FRONT to 0.35f,
                MuscleGroup.QUADS to 0.6f,
            ),
        )
    }
}
