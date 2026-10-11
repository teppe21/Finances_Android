package com.teppe21.finances

import androidx.compose.ui.unit.dp
import com.teppe21.finances.core.ui.QuickReturnState
import com.teppe21.finances.feature.analytics.donutCentreWidth
import com.teppe21.finances.feature.analytics.donutHoleDiameter
import com.teppe21.finances.feature.analytics.donutRingSize
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The Transactions header that leaves on scroll-down and returns on scroll-up, and the donut sizing rules. */
class QuickReturnAndDonutLayoutTest {

    private fun state() = QuickReturnState(thresholdPx = 24f)

    @Test
    fun theHeaderIsShownAtFirst() {
        assertTrue(state().visible)
    }

    @Test
    fun scrollingDownPastTheThresholdHidesItAndScrollingUpBringsItBack() {
        val s = state()
        repeat(5) { s.onScroll(-4f, atTop = false, pinned = false) }
        assertTrue("20 px is under the threshold", s.visible)
        s.onScroll(-8f, atTop = false, pinned = false)
        assertFalse(s.visible)
        repeat(5) { s.onScroll(4f, atTop = false, pinned = false) }
        assertFalse("20 px back is under the threshold", s.visible)
        s.onScroll(8f, atTop = false, pinned = false)
        assertTrue(s.visible)
    }

    @Test
    fun aChangeOfDirectionRestartsTheCount() {
        val s = state()
        s.onScroll(-20f, atTop = false, pinned = false)
        s.onScroll(10f, atTop = false, pinned = false)
        s.onScroll(-20f, atTop = false, pinned = false)
        assertTrue("-20, +10 restarts the count, so -20 is still under 24", s.visible)
    }

    @Test
    fun atTheTopOfTheListItIsAlwaysShown() {
        val s = state()
        s.onScroll(-100f, atTop = false, pinned = false)
        assertFalse(s.visible)
        s.onScroll(-1f, atTop = true, pinned = false)
        assertTrue(s.visible)
    }

    @Test
    fun aPinnedHeaderNeverLeaves() {
        val s = state()
        repeat(10) { s.onScroll(-50f, atTop = false, pinned = true) }
        assertTrue(s.visible)
    }

    @Test
    fun noMovementChangesNothing() {
        val s = state()
        s.onScroll(-100f, atTop = false, pinned = false)
        s.onScroll(0f, atTop = false, pinned = false)
        assertFalse(s.visible)
    }

    // ---- donut sizing ----------------------------------------------------------------------------

    @Test
    fun theRingKeepsItsSizeAtNormalScaleAndGrowsWithTheFontUpToWhatACardHolds() {
        assertEquals(220f, donutRingSize(1.0f).value, 0.01f)
        assertEquals(220f, donutRingSize(0.85f).value, 0.01f)
        assertTrue(donutRingSize(1.5f) > donutRingSize(1.3f))
        assertEquals(280f, donutRingSize(2.0f).value, 0.01f)
        assertEquals(280f, donutRingSize(3.0f).value, 0.01f)
        // A card at 360 dp has 296 dp inside it.
        assertTrue(donutRingSize(3.0f) <= 296.dp)
    }

    @Test
    fun theHoleGrowsWithTheRingAndTheCentreColumnIsNarrowerThanTheHole() {
        var last = 0f
        for (scale in listOf(1.0f, 1.15f, 1.3f, 1.5f, 1.8f, 2.0f)) {
            val ring = donutRingSize(scale)
            val hole = donutHoleDiameter(ring)
            assertTrue("hole at $scale", hole.value > last)
            assertTrue(donutCentreWidth(ring) < hole)
            last = hole.value
        }
        // At normal scale the hole is the 147 dp the layout always had.
        assertEquals(147.2f, donutHoleDiameter(donutRingSize(1.0f)).value, 0.1f)
    }
}
