package com.bangersoul.aivance.core.designsystem.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bangersoul.aivance.core.designsystem.theme.AivanceTheme

/**
 * A minimal sparkline for compact trend visualizations.
 */
@Composable
fun Sparkline(
    values: List<Float>,
    modifier: Modifier = Modifier,
    lineColor: Color? = null,
    fillColor: Color? = null,
    strokeWidth: Float = 3f
) {
    val accent = AivanceTheme.colors.accent
    val duration = AivanceTheme.motion.durationSlow
    val animated = remember { Animatable(0f) }
    LaunchedEffect(values) {
        animated.animateTo(1f, tween(duration))
    }
    val resolvedColor = lineColor ?: accent

    Canvas(
        modifier = modifier
            .height(48.dp)
            .fillMaxWidth()
            .semantics { contentDescription = "Trend chart" }
    ) {
        if (values.isEmpty()) return@Canvas
        val maxV = values.maxOrNull()?.takeIf { it > 0f } ?: 1f
        val minV = values.minOrNull() ?: 0f
        val span = (maxV - minV).takeIf { it > 0f } ?: 1f
        val step = size.width / (values.size - 1).coerceAtLeast(1)

        val points = values.mapIndexed { i, v ->
            Offset(
                x = i * step,
                y = size.height - ((v - minV) / span) * (size.height - 8.dp.toPx()) - 4.dp.toPx()
            )
        }

        val drawUpTo = (animated.value * (values.size - 1)).toInt().coerceIn(0, values.size - 1)
        val pointsToDraw = points.take(drawUpTo + 1)

        if (pointsToDraw.size > 1) {
            if (fillColor != null) {
                val path = Path().apply {
                    moveTo(pointsToDraw.first().x, size.height)
                    pointsToDraw.forEach { lineTo(it.x, it.y) }
                    lineTo(pointsToDraw.last().x, size.height)
                    close()
                }
                drawPath(path, fillColor.copy(alpha = 0.15f))
            }
            for (i in 1 until pointsToDraw.size) {
                drawLine(
                    color = resolvedColor,
                    start = pointsToDraw[i - 1],
                    end = pointsToDraw[i],
                    strokeWidth = strokeWidth,
                    cap = StrokeCap.Round
                )
            }
        }
    }
}

/**
 * Animated horizontal bar chart for funnel / comparison data.
 */
@Composable
fun BarChart(
    data: List<Pair<String, Float>>,
    modifier: Modifier = Modifier,
    barColor: Color? = null,
    valueFormatter: (Float) -> String = { "${(it * 100).toInt()}%" }
) {
    val accent = AivanceTheme.colors.accent
    val duration = AivanceTheme.motion.durationSlow
    val animated = remember { Animatable(0f) }
    LaunchedEffect(data) {
        animated.animateTo(1f, tween(duration))
    }
    val textMeasurer = rememberTextMeasurer()
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
    val trackColor = MaterialTheme.colorScheme.surfaceVariant
    val resolvedColor = barColor ?: accent

    val labelStyle = remember(labelColor) { TextStyle(fontSize = 11.sp, color = labelColor) }
    val valueStyle = remember(resolvedColor) { TextStyle(fontSize = 11.sp, color = resolvedColor) }

    val density = LocalDensity.current
    val labelWidthPx = with(density) { 110.dp.toPx() }
    val rightPadPx = with(density) { 60.dp.toPx() }
    val cornerPx = with(density) { 6.dp.toPx() }
    val valueTextPadPx = with(density) { 8.dp.toPx() }

    // Text measurement is by far the most expensive work this chart does, and it
    // does not vary with the animation frame — measuring inside the draw scope
    // re-laid out every label and value on all ~60 frames of the entry animation.
    // Measure once per data change and reuse the layouts.
    val measuredLabels = remember(data, textMeasurer, labelStyle) {
        data.map { textMeasurer.measure(it.first, labelStyle) }
    }
    val measuredValues = remember(data, textMeasurer, valueStyle) {
        data.map { textMeasurer.measure(valueFormatter(it.second), valueStyle) }
    }

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height((data.size * 32).dp)
            .semantics { contentDescription = "Bar chart" }
    ) {
        val rowHeight = size.height / data.size.coerceAtLeast(1)

        data.forEachIndexed { index, (_, value) ->
            val y = index * rowHeight
            val labelLayout = measuredLabels[index]
            drawText(labelLayout, topLeft = Offset(0f, y + rowHeight / 2 - labelLayout.size.height / 2))

            val maxBarWidth = size.width - labelWidthPx - rightPadPx
            val barWidth = maxBarWidth * value.coerceIn(0f, 1f) * animated.value
            val barStartX = labelWidthPx
            val barY = y + rowHeight * 0.22f
            val barHeight = rowHeight * 0.56f

            drawRoundRect(
                color = trackColor,
                topLeft = Offset(barStartX, barY),
                size = Size(maxBarWidth, barHeight),
                cornerRadius = CornerRadius(cornerPx)
            )
            if (barWidth > 0) {
                drawRoundRect(
                    color = resolvedColor,
                    topLeft = Offset(barStartX, barY),
                    size = Size(barWidth, barHeight),
                    cornerRadius = CornerRadius(cornerPx)
                )
            }
            val valueText = measuredValues[index]
            drawText(
                valueText,
                topLeft = Offset(barStartX + maxBarWidth + valueTextPadPx, y + rowHeight / 2 - valueText.size.height / 2)
            )
        }
    }
}

/**
 * A funnel chart — decreasing bars for application pipeline.
 */
@Composable
fun FunnelChart(
    stages: List<Pair<String, Int>>,
    modifier: Modifier = Modifier,
    colors: List<Color>? = null
) {
    val maxValue = (stages.maxOfOrNull { it.second } ?: 1).coerceAtLeast(1)
    val duration = AivanceTheme.motion.durationSlow
    val animated = remember { Animatable(0f) }
    LaunchedEffect(stages) {
        animated.animateTo(1f, tween(duration))
    }
    val textMeasurer = rememberTextMeasurer()
    val labelColor = MaterialTheme.colorScheme.onSurface
    val resolvedColors = colors ?: listOf(
        AivanceTheme.colors.accent,
        AivanceTheme.colors.info,
        AivanceTheme.colors.warning,
        AivanceTheme.colors.success
    )

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height((stages.size * 40).dp)
            .semantics { contentDescription = "Application funnel" }
    ) {
        val rowHeight = size.height / stages.size.coerceAtLeast(1)
        val labelStyle = TextStyle(fontSize = 12.sp, color = labelColor)

        stages.forEachIndexed { index, (label, value) ->
            val y = index * rowHeight + 4.dp.toPx()
            val barHeight = rowHeight - 8.dp.toPx()
            val fraction = (value.toFloat() / maxValue) * animated.value
            val barWidth = (size.width - 90.dp.toPx()) * fraction.coerceIn(0f, 1f)
            val color = resolvedColors[index % resolvedColors.size]

            drawRoundRect(
                color = color.copy(alpha = 0.9f),
                topLeft = Offset(0f, y),
                size = Size(barWidth, barHeight),
                cornerRadius = CornerRadius(10.dp.toPx())
            )
            val labelLayout = textMeasurer.measure(label, labelStyle)
            drawText(
                labelLayout,
                topLeft = Offset(barWidth + 10.dp.toPx(), y + barHeight / 2 - labelLayout.size.height / 2)
            )
            val countText = textMeasurer.measure(value.toString(), labelStyle.copy(color = color))
            drawText(
                countText,
                topLeft = Offset(0f, y + barHeight + 2.dp.toPx())
            )
        }
    }
}

/**
 * A simple heat-map grid.
 */
@Composable
fun HeatMapGrid(
    values: List<List<Float>>,
    modifier: Modifier = Modifier,
    cellSize: Int = 12,
    lowColor: Color? = null,
    highColor: Color? = null
) {
    val duration = AivanceTheme.motion.durationSlow
    val animated = remember { Animatable(0f) }
    LaunchedEffect(values) {
        animated.animateTo(1f, tween(duration))
    }
    val resolvedLow = lowColor ?: MaterialTheme.colorScheme.surfaceVariant
    val resolvedHigh = highColor ?: AivanceTheme.colors.accent

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height((values.size * (cellSize + 4)).dp)
            .semantics { contentDescription = "Activity heat map" }
    ) {
        val cell = cellSize.dp.toPx()
        val gap = 4.dp.toPx()
        values.forEachIndexed { row, cols ->
            cols.forEachIndexed { col, v ->
                val color = lerpColor(resolvedLow, resolvedHigh, v.coerceIn(0f, 1f))
                drawRoundRect(
                    color = color.copy(alpha = 0.35f + 0.65f * animated.value),
                    topLeft = Offset(col * (cell + gap), row * (cell + gap)),
                    size = Size(cell, cell),
                    cornerRadius = CornerRadius(3.dp.toPx())
                )
            }
        }
    }
}

private fun lerpColor(start: Color, end: Color, t: Float): Color = Color(
    red = start.red + (end.red - start.red) * t,
    green = start.green + (end.green - start.green) * t,
    blue = start.blue + (end.blue - start.blue) * t,
    alpha = start.alpha + (end.alpha - start.alpha) * t
)

/**
 * A large animated line chart with area fill and data points.
 */
@Composable
fun LineChart(
    values: List<Float>,
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
            .semantics { contentDescription = "Line chart" }
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

/**
 * A multi-segment pie/donut chart with an animated sweep. Each segment is
 * drawn as an arc proportional to its value; the [centerText] (if any) is
 * rendered in the middle. Zero-valued segments are simply not drawn.
 */
@Composable
fun PieChart(
    segments: List<PieSegment>,
    modifier: Modifier = Modifier,
    size: Int = 150,
    centerText: String? = null,
    strokeWidth: Int = 24
) {
    val total = segments.map { it.value }.sum().coerceAtLeast(1f)
    val duration = AivanceTheme.motion.durationSlow
    val animated = remember { Animatable(0f) }
    LaunchedEffect(segments) {
        animated.animateTo(1f, tween(duration))
    }

    Box(modifier = modifier.size(size.dp), contentAlignment = Alignment.Center) {
        Canvas(
            modifier = Modifier
                .size(size.dp)
                .semantics { contentDescription = "Career breakdown pie chart" }
        ) {
            val stroke = Stroke(width = strokeWidth.dp.toPx(), cap = StrokeCap.Butt)
            var startAngle = -90f
            segments.forEach { segment ->
                val segmentFraction = segment.value / total
                val sweep = 360f * segmentFraction * animated.value
                if (sweep > 0.5f) {
                    drawArc(
                        color = segment.color,
                        startAngle = startAngle,
                        sweepAngle = sweep,
                        useCenter = false,
                        style = stroke
                    )
                }
                startAngle += 360f * segmentFraction
            }
        }
        centerText?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
        }
    }
}

/** A single slice of a [PieChart]. */
data class PieSegment(
    val label: String,
    val value: Float,
    val color: Color
)

/**
 * A donut chart with animated sweep.
 */
@Composable
fun DonutChart(
    fraction: Float,
    modifier: Modifier = Modifier,
    size: Int = 140,
    color: Color? = null,
    trackColor: Color? = null,
    centerText: String? = null
) {
    val accent = AivanceTheme.colors.accent
    val duration = AivanceTheme.motion.durationSlow
    val animated = remember { Animatable(0f) }
    LaunchedEffect(fraction) {
        animated.animateTo(fraction.coerceIn(0f, 1f), tween(duration))
    }
    val resolvedColor = color ?: accent
    val resolvedTrack = trackColor ?: MaterialTheme.colorScheme.surfaceVariant

    Box(modifier = modifier.size(size.dp), contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.size(size.dp)) {
            val stroke = Stroke(width = 12.dp.toPx(), cap = StrokeCap.Round)
            drawArc(
                color = resolvedTrack,
                startAngle = -90f,
                sweepAngle = 360f,
                useCenter = false,
                style = stroke
            )
            drawArc(
                color = resolvedColor,
                startAngle = -90f,
                sweepAngle = 360f * animated.value,
                useCenter = false,
                style = stroke
            )
        }
        centerText?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}
