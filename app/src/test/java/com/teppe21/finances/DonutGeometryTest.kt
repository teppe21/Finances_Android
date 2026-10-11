package com.teppe21.finances

import com.teppe21.finances.feature.analytics.DonutSegment
import com.teppe21.finances.feature.analytics.donutSegments
import com.teppe21.finances.feature.analytics.indexAt
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The ring geometry behind the Analytics donut: flat segments, gaps, a minimum sweep, a stable order. */
class DonutGeometryTest {

    private val eps = 0.01f

    @Test
    fun nothingToDrawGivesNoSegments() {
        assertTrue(donutSegments(emptyList()).isEmpty())
        assertTrue(donutSegments(listOf(0L, 0L)).isEmpty())
        assertTrue(donutSegments(listOf(-5L)).isEmpty())
    }

    @Test
    fun aSingleCategoryIsAWholeRingWithoutAGap() {
        val segments = donutSegments(listOf(0L, 42_000L, 0L))
        assertEquals(DonutSegment(0f, 0f), segments[0])
        assertEquals(360f, segments[1].sweep, eps)
        assertEquals(0f, segments[1].start, eps)
        assertEquals(0f, segments[2].sweep, eps)
    }

    @Test
    fun segmentsKeepTheOrderTheyAreGivenAndNeverOverlap() {
        val segments = donutSegments(listOf(500L, 300L, 150L, 50L))
        val drawn = segments.filter { it.sweep > 0f }
        assertEquals(4, drawn.size)
        drawn.zipWithNext().forEach { (a, b) ->
            assertTrue("segments must not overlap: $a, $b", b.start >= a.end)
            assertEquals("the gap between neighbours", 2f, b.start - a.end, eps)
        }
        assertTrue("the ring must not run past a full turn", drawn.last().end <= 360f + eps)
    }

    @Test
    fun theRingClosesWithTheGapsTakenOutOfTheSweeps() {
        val segments = donutSegments(listOf(7L, 3L, 2L, 1L), gapDegrees = 2f)
        val swept = segments.sumOf { it.sweep.toDouble() }.toFloat()
        // 4 segments, 4 gaps of 2 degrees: the sweeps share the other 352 degrees.
        assertEquals(352f, swept, 0.1f)
        // The first segment starts half a gap in, so the last gap straddles the top of the ring.
        assertEquals(1f, segments.first().start, eps)
        assertEquals(359f, segments.last().end, 0.1f)
    }

    @Test
    fun sweepsAreProportionalWhenNothingIsSmall() {
        val segments = donutSegments(listOf(600L, 300L, 100L))
        assertEquals(segments[0].sweep / segments[1].sweep, 2f, 0.01f)
        assertEquals(segments[1].sweep / segments[2].sweep, 3f, 0.01f)
    }

    @Test
    fun aTinyShareStaysVisibleAtTheMinimumSweepAndTheRingStillCloses() {
        // 1 in 100,000 would be a fraction of a degree.
        val segments = donutSegments(listOf(99_999L, 1L), gapDegrees = 2f, minSweepDegrees = 2f)
        assertEquals(2f, segments[1].sweep, eps)
        assertEquals(354f, segments[0].sweep, 0.01f)
        assertEquals(360f, segments.sumOf { it.sweep.toDouble() }.toFloat() + 2 * 2f, 0.1f)
    }

    @Test
    fun zeroValuedCategoriesAreSkippedWithoutBreakingTheOthers() {
        val segments = donutSegments(listOf(100L, 0L, 100L))
        assertEquals(0f, segments[1].sweep, eps)
        assertEquals(segments[0].sweep, segments[2].sweep, eps)
        assertTrue(segments[2].start >= segments[0].end)
    }

    @Test
    fun theOrderOfTheInputIsTheOrderOfTheRing() {
        val a = donutSegments(listOf(10L, 30L, 20L))
        assertTrue(a[0].start < a[1].start && a[1].start < a[2].start)
    }

    @Test
    fun aTapIsMappedToTheSegmentUnderIt() {
        val segments = donutSegments(listOf(500L, 500L))
        assertEquals(0, segments.indexAt(90f))
        assertEquals(1, segments.indexAt(270f))
    }

    @Test
    fun aTapInAGapOrOnNothingHitsNoSegment() {
        val segments = donutSegments(listOf(500L, 500L), gapDegrees = 2f)
        // The gaps are centred on 0/360 and on 180 degrees.
        assertNull(segments.indexAt(180f))
        assertNull(segments.indexAt(0f))
        assertNull(donutSegments(emptyList()).indexAt(10f))
    }

    @Test
    fun anglesOutsideZeroToThreeSixtyWrapAround() {
        val segments = donutSegments(listOf(500L, 500L))
        assertEquals(segments.indexAt(90f), segments.indexAt(450f))
        assertEquals(segments.indexAt(270f), segments.indexAt(-90f))
    }
}
