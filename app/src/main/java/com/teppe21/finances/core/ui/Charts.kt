package com.teppe21.finances.core.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.teppe21.finances.core.theme.Spacing
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.abs
import kotlin.math.roundToInt

/** Index of the nearest of [count] points spread edge to edge over [width]; null when there is nothing to pick. */
fun lineIndexAt(x: Float, width: Float, count: Int): Int? = when {
    count <= 0 || width <= 0f -> null
    count == 1 -> 0
    else -> (x / width * (count - 1)).roundToInt().coerceIn(0, count - 1)
}

/** Index of the band containing [x] when [width] is split into [count] equal bands; null when there is nothing to pick. */
fun bandIndexAt(x: Float, width: Float, count: Int): Int? =
    if (count <= 0 || width <= 0f) null else (x / width * count).toInt().coerceIn(0, count - 1)

/**
 * Press-and-drag selection inside a scrolling page. The parent scroll container consumes a vertical drag
 * in the Main pass after this modifier has seen it, so consumption is checked in the Final pass. A gesture
 * is claimed (and its changes consumed, so the page stops scrolling) once horizontal travel passes touch
 * slop and exceeds vertical travel, or - with [selectOnHold] - after a press held without moving. [onSelect]
 * is only called for a claimed gesture; with [selectOnTap] an unclaimed down+up within slop selects at the
 * touch point. [onRelease] always runs when the gesture ends, also on cancellation. The callbacks are
 * captured once per [key]: restarting on every new lambda would cancel the gesture being tracked.
 */
private fun Modifier.dragSelect(
    key: Any,
    selectOnHold: Boolean,
    selectOnTap: Boolean,
    indexAt: (x: Float, width: Float) -> Int?,
    onSelect: (Int?) -> Unit,
    onRelease: () -> Unit
): Modifier = pointerInput(key) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        val slop = viewConfiguration.touchSlop
        val width = size.width.toFloat()
        var claimed = false
        try {
            var exceededSlop = false
            while (true) {
                // Without movement, a press held for the long-press timeout claims the gesture.
                val event = if (selectOnHold && !claimed && !exceededSlop) {
                    withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) { awaitPointerEvent(PointerEventPass.Final) }
                } else {
                    awaitPointerEvent(PointerEventPass.Final)
                }
                if (event == null) {
                    claimed = true
                    onSelect(indexAt(down.position.x, width))
                    continue
                }
                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                if (!claimed && change.isConsumed) break
                val distance = (change.position - down.position).getDistance()
                if (!change.pressed) {
                    if (!claimed && selectOnTap && distance <= slop) onSelect(indexAt(change.position.x, width))
                    break
                }
                if (distance > slop) exceededSlop = true
                if (!claimed) {
                    val dx = abs(change.position.x - down.position.x)
                    val dy = abs(change.position.y - down.position.y)
                    claimed = dx > slop && dx > dy
                }
                if (claimed) {
                    change.consume()
                    onSelect(indexAt(change.position.x, width))
                }
            }
        } finally {
            onRelease()
        }
    }
}

/** 0..1 draw-in progress, always 1 when the user turned animations off. Read it inside a draw block. */
@Composable
private fun rememberDrawIn(key: Any?): () -> Float {
    val reveal = rememberReveal(key)
    val reduce = rememberReduceMotion()
    return { if (reduce) 1f else reveal.value }
}

/**
 * Line with a soft area fill. While a finger is down it marks the nearest point and reports its index
 * through [onInspect]; the index goes back to null on release. Zero points draws an empty baseline and
 * one point a flat line. Geometry only: callers show the exact values.
 */
@Composable
fun LineChart(
    values: List<Float>,
    color: Color,
    contentDescription: String,
    modifier: Modifier = Modifier,
    guides: Boolean = true,
    onInspect: (Int?) -> Unit = {}
) {
    var selected by remember { mutableStateOf<Int?>(null) }
    val drawIn = rememberDrawIn(values)
    val guideColor = MaterialTheme.colorScheme.outline
    val markerColor = MaterialTheme.colorScheme.onSurfaceVariant
    val ringColor = MaterialTheme.colorScheme.surface
    Canvas(
        modifier
            .semantics { this.contentDescription = contentDescription }
            .dragSelect(
                key = values.size,
                selectOnHold = true,
                selectOnTap = false,
                indexAt = { x, w -> lineIndexAt(x, w, values.size) },
                onSelect = { selected = it; onInspect(it) },
                onRelease = { selected = null; onInspect(null) }
            )
    ) {
        val pad = 6.dp.toPx()
        val w = size.width
        val top = pad
        val bottom = size.height - pad
        val lo = values.minOrNull() ?: 0f
        val hi = values.maxOrNull() ?: 0f
        fun y(v: Float) = if (hi > lo) bottom - (v - lo) / (hi - lo) * (bottom - top) else (top + bottom) / 2
        fun x(i: Int) = if (values.size <= 1) w / 2 else i * w / (values.size - 1)

        if (values.isEmpty()) {
            drawLine(guideColor, Offset(0f, bottom), Offset(w, bottom), 1.dp.toPx())
            return@Canvas
        }
        if (guides && hi > lo) {
            drawLine(guideColor, Offset(0f, top), Offset(w, top), 1.dp.toPx())
            drawLine(guideColor, Offset(0f, bottom), Offset(w, bottom), 1.dp.toPx())
        }
        val line = Path()
        if (values.size == 1) {
            line.moveTo(0f, y(values[0])); line.lineTo(w, y(values[0]))
        } else {
            values.forEachIndexed { i, v -> if (i == 0) line.moveTo(x(0), y(v)) else line.lineTo(x(i), y(v)) }
        }
        val area = Path().apply {
            addPath(line)
            lineTo(if (values.size == 1) w else x(values.size - 1), size.height)
            lineTo(0f, size.height)
            close()
        }
        clipRect(right = w * drawIn()) {
            drawPath(area, Brush.verticalGradient(listOf(color.copy(alpha = 0.28f), Color.Transparent), startY = top, endY = size.height))
            drawPath(line, color, style = Stroke(2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
        selected?.takeIf { it < values.size }?.let { i ->
            drawLine(markerColor, Offset(x(i), 0f), Offset(x(i), size.height), 1.dp.toPx())
            drawCircle(ringColor, 6.dp.toPx(), Offset(x(i), y(values[i])))
            drawCircle(color, 4.dp.toPx(), Offset(x(i), y(values[i])))
        }
    }
}

/**
 * Paired bars per period with [labels] beneath. Tapping or horizontally dragging selects a period ([selectedIndex],
 * reported through [onSelect] and kept after release); the other periods dim.
 */
@Composable
fun GroupedBarChart(
    labels: List<String>,
    first: List<Float>,
    second: List<Float>,
    firstColor: Color,
    secondColor: Color,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    contentDescription: String,
    modifier: Modifier = Modifier
) {
    val drawIn = rememberDrawIn(first to second)
    val baseline = MaterialTheme.colorScheme.outline
    Column(modifier.semantics { this.contentDescription = contentDescription }) {
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(120.dp)
                .dragSelect(
                    key = labels.size,
                    selectOnHold = false,
                    selectOnTap = true,
                    indexAt = { x, w -> bandIndexAt(x, w, labels.size) },
                    onSelect = { if (it != null) onSelect(it) },
                    onRelease = {}
                )
        ) {
            val peak = maxOf(first.maxOrNull() ?: 0f, second.maxOrNull() ?: 0f).coerceAtLeast(1f)
            val band = size.width / labels.size.coerceAtLeast(1)
            val barWidth = minOf(band * 0.3f, 16.dp.toPx())
            val gap = 3.dp.toPx()
            val radius = minOf(4.dp.toPx(), barWidth / 2)
            val floor = size.height - 1.dp.toPx()
            val usable = floor - 4.dp.toPx()
            drawLine(baseline, Offset(0f, floor), Offset(size.width, floor), 1.dp.toPx())
            labels.indices.forEach { i ->
                val alpha = if (i == selectedIndex) 1f else 0.35f
                val left = band * i + (band - 2 * barWidth - gap) / 2
                listOf(first to firstColor, second to secondColor).forEachIndexed { n, (series, c) ->
                    val h = usable * (series.getOrElse(i) { 0f } / peak) * drawIn()
                    if (h >= 1f) clipRect(bottom = floor) {
                        // Drawn taller than visible so only the top corners are rounded.
                        drawRoundRect(c.copy(alpha = alpha), Offset(left + n * (barWidth + gap), floor - h), Size(barWidth, h + radius), CornerRadius(radius))
                    }
                }
            }
        }
        Spacer(Modifier.height(Spacing.xs))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
            labels.forEachIndexed { i, label ->
                Text(
                    label,
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = if (i == selectedIndex) FontWeight.Bold else null,
                    color = if (i == selectedIndex) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
