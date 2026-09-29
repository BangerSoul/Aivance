package com.bangersoul.aivance.core.designsystem.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.bangersoul.aivance.core.designsystem.theme.AivanceTheme

/**
 * A large animated line chart with area fill and data points.
 *
 * The accessible name is **supplied by the caller** as [contentDescription]: this
 * module ships no string resources, so a hardcoded label here could never be
 * translated and the chart would be announced identically on every screen. The
 * owning screen knows what the chart plots and passes its own localized string.
 */
@Composable
fun LineChart(
    values: List<Float>,
    contentDescription: String,
    modifier: Modifier = Modifier,
    lineColor: Color? = null,
    showPoints: Boolean = true,
    minValue: Float = 0f
) {
    val accent = AivanceTheme.colors.accent
    val duration = AivanceTheme.motion.durationEmphasis
    val animated = remember { Animatable(0f) }
    LaunchedEffect(values) {
        animated.animateTo(1f, tween(duration))
    }
    val resolvedColor = lineColor ?: accent

    // Scale bounds do not depend on the canvas size, so derive them once per data
    // change rather than re-scanning the list on every animation frame.
    val minV = remember(values, minValue) { minOf(minValue, values.minOrNull() ?: 0f) }
    val span = remember(values, minValue) {
        ((values.maxOrNull() ?: 1f).coerceAtLeast(1f) - minV).coerceAtLeast(1f)
    }

    // Point geometry needs the canvas size, which the draw scope only learns
    // during layout. Track it via onSizeChanged and build the Offsets in
    // remember() so the draw pass reuses one list instead of allocating a fresh
    // List<Offset> on every frame of the entry animation.
    var canvasSize by remember { mutableStateOf(IntSize.Zero) }
    val density = LocalDensity.current
    val points = remember(values, minV, span, canvasSize, density) {
        if (values.isEmpty() || canvasSize == IntSize.Zero) {
            emptyList()
        } else {
            val topPad = with(density) { 16.dp.toPx() }
            val bottomPad = with(density) { 8.dp.toPx() }
            val step = canvasSize.width.toFloat() / (values.size - 1).coerceAtLeast(1)
            val usable = canvasSize.height - topPad - bottomPad
            values.mapIndexed { i, v ->
                Offset(
                    x = i * step,
                    y = topPad + (1f - (v - minV) / span) * usable
                )
            }
        }
    }

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(160.dp)
            .onSizeChanged { canvasSize = it }
            .semantics { this.contentDescription = contentDescription }
    ) {
        if (points.isEmpty()) return@Canvas
        val bottomPad = 8.dp.toPx()

        val drawCount = (animated.value * (values.size - 1)).toInt().coerceIn(0, values.size - 1) + 1
        val visible = points.take(drawCount)

        if (visible.size > 1) {
            val path = Path().apply {
                moveTo(visible.first().x, size.height - bottomPad)
                visible.forEach { lineTo(it.x, it.y) }
                lineTo(visible.last().x, size.height - bottomPad)
                close()
            }
            drawPath(path, resolvedColor.copy(alpha = 0.12f))

            for (i in 1 until visible.size) {
                drawLine(
                    color = resolvedColor,
                    start = visible[i - 1],
                    end = visible[i],
                    strokeWidth = 3f,
                    cap = StrokeCap.Round
                )
            }
            if (showPoints) {
                visible.forEach { p ->
                    drawCircle(resolvedColor, radius = 4f, center = p)
                }
            }
        }
    }
}
