package com.teppe21.finances.feature.analytics

/** One ring segment in degrees; 0 is the top of the ring, angles grow clockwise. */
data class DonutSegment(val start: Float, val sweep: Float) {
    val end: Float get() = start + sweep
    fun contains(angle: Float): Boolean = angle >= start && angle < end
}

/**
 * Ring geometry for a donut chart, in the order given (callers keep that order stable).
 *
 * Segments are drawn with flat ends and separated by [gapDegrees], so they never overlap; the
 * old round caps bulged into the neighbouring segments and made small categories look bigger.
 * A segment smaller than [minSweepDegrees] is drawn at that size so it stays visible; the extra
 * is taken from the larger segments in proportion to their size, so the ring still closes and
 * the difference stays a fraction of a degree. The exact shares are printed in the legend.
 */
fun donutSegments(values: List<Long>, gapDegrees: Float = 2f, minSweepDegrees: Float = 2f): List<DonutSegment> {
    val positive = values.map { it.coerceAtLeast(0L) }
    val total = positive.sum()
    if (total <= 0L || positive.isEmpty()) return emptyList()
    if (positive.count { it > 0 } == 1) {
        // A single category is a whole ring, no gap.
        var start = 0f
        return positive.map { v -> DonutSegment(start, if (v > 0) 360f else 0f).also { start += it.sweep } }
    }

    val drawn = positive.count { it > 0 }
    val available = 360f - drawn * gapDegrees
    val raw = positive.map { if (it > 0) available * it / total else 0f }
    val small = raw.withIndex().filter { (_, s) -> s > 0f && s < minSweepDegrees }.map { it.index }.toSet()
    val deficit = small.sumOf { (minSweepDegrees - raw[it]).toDouble() }.toFloat()
    val largeTotal = raw.withIndex().filter { (i, s) -> s > 0f && i !in small }.sumOf { it.value.toDouble() }.toFloat()
    val sweeps = raw.mapIndexed { i, s ->
        when {
            s <= 0f -> 0f
            i in small -> minSweepDegrees
            largeTotal > 0f -> s - deficit * (s / largeTotal)
            else -> s
        }
    }

    var cursor = 0f
    return sweeps.map { sweep ->
        if (sweep <= 0f) {
            DonutSegment(cursor, 0f)
        } else {
            val segment = DonutSegment(cursor + gapDegrees / 2f, sweep)
            cursor += sweep + gapDegrees
            segment
        }
    }
}

/** Index of the segment under [angle] (degrees from the top, clockwise), or null in a gap. */
fun List<DonutSegment>.indexAt(angle: Float): Int? {
    val a = ((angle % 360f) + 360f) % 360f
    return indexOfFirst { it.sweep > 0f && it.contains(a) }.takeIf { it >= 0 }
}
