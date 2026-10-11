package com.teppe21.finances

import com.teppe21.finances.core.ui.GesturePhysics
import com.teppe21.finances.core.ui.GesturePhysics.Release
import com.teppe21.finances.core.ui.animationsDisabled
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class GesturePhysicsTest {

    @Test
    fun `projection follows exponential decay and keeps the sign`() {
        assertEquals(0f, GesturePhysics.project(0f), 0.001f)
        // 1000 px/s at 0.998 comes to rest about 499 px further on.
        assertEquals(499f, GesturePhysics.project(1000f), 0.5f)
        assertEquals(-499f, GesturePhysics.project(-1000f), 0.5f)
        // Faster scroll feel: a lower deceleration rate projects a shorter distance.
        assertTrue(GesturePhysics.project(1000f, 0.99f) < GesturePhysics.project(1000f, 0.998f))
    }

    @Test
    fun `rubber band resists progressively and never exceeds the dimension`() {
        val dimension = 1000f
        assertEquals(0f, GesturePhysics.rubberband(0f, dimension), 0f)
        // Close to the edge it still follows the finger almost 1:1 (scaled by the constant).
        assertEquals(0.55f, GesturePhysics.rubberband(1f, dimension), 0.01f)

        var previous = 0f
        for (overshoot in listOf(50f, 100f, 200f, 400f, 800f, 1600f, 100_000f)) {
            val shown = GesturePhysics.rubberband(overshoot, dimension)
            assertTrue("grows with the finger", shown > previous)
            assertTrue("never beyond the dimension", shown < dimension)
            assertTrue("always less than the finger moved", shown < overshoot)
            previous = shown
        }
        assertEquals(-GesturePhysics.rubberband(300f, dimension), GesturePhysics.rubberband(-300f, dimension), 0.001f)
        assertEquals(0f, GesturePhysics.rubberband(10f, 0f), 0f)
    }

    @Test
    fun `resisted offset is 1 to 1 inside the range and soft outside it`() {
        assertEquals(120f, GesturePhysics.resisted(120f, -500f, 500f, 500f), 0f)
        assertEquals(-120f, GesturePhysics.resisted(-120f, -500f, 500f, 500f), 0f)

        val pastEnd = GesturePhysics.resisted(700f, -500f, 500f, 500f)
        assertTrue(pastEnd > 500f && pastEnd < 700f)
        val pastStart = GesturePhysics.resisted(-700f, -500f, 500f, 500f)
        assertEquals(-pastEnd, pastStart, 0.001f)

        // A direction without an action has a zero range, so any drag that way is resisted from the start.
        val noActionToTheLeft = GesturePhysics.resisted(-100f, 0f, 500f, 500f)
        assertTrue(noActionToTheLeft < 0f && abs(noActionToTheLeft) < 100f)
    }

    @Test
    fun `a slow drag that stops short settles back`() {
        assertEquals(Release.SETTLE, GesturePhysics.release(offset = 200f, velocity = 0f, width = 1000f, canCommitRight = true, canCommitLeft = true))
        assertEquals(Release.SETTLE, GesturePhysics.release(offset = -300f, velocity = 0f, width = 1000f, canCommitRight = true, canCommitLeft = true))
    }

    @Test
    fun `a slow drag past the threshold commits in that direction`() {
        assertEquals(Release.COMMIT_RIGHT, GesturePhysics.release(offset = 450f, velocity = 0f, width = 1000f, canCommitRight = true, canCommitLeft = true))
        assertEquals(Release.COMMIT_LEFT, GesturePhysics.release(offset = -450f, velocity = 0f, width = 1000f, canCommitRight = true, canCommitLeft = true))
    }

    @Test
    fun `a quick flick commits from a short distance because the projection is used`() {
        assertEquals(Release.COMMIT_RIGHT, GesturePhysics.release(offset = 120f, velocity = 1500f, width = 1000f, canCommitRight = true, canCommitLeft = true))
        assertEquals(Release.COMMIT_LEFT, GesturePhysics.release(offset = -120f, velocity = -1500f, width = 1000f, canCommitRight = true, canCommitLeft = true))
    }

    @Test
    fun `a flick back toward the start does not commit even if the position is past the threshold`() {
        assertEquals(Release.SETTLE, GesturePhysics.release(offset = 450f, velocity = -2500f, width = 1000f, canCommitRight = true, canCommitLeft = true))
    }

    @Test
    fun `a direction without an action never commits`() {
        assertEquals(Release.SETTLE, GesturePhysics.release(offset = 600f, velocity = 3000f, width = 1000f, canCommitRight = false, canCommitLeft = true))
        assertEquals(Release.SETTLE, GesturePhysics.release(offset = -600f, velocity = -3000f, width = 1000f, canCommitRight = true, canCommitLeft = false))
    }

    @Test
    fun `an unmeasured row never commits`() {
        assertEquals(Release.SETTLE, GesturePhysics.release(offset = 10f, velocity = 5000f, width = 0f, canCommitRight = true, canCommitLeft = true))
    }

    @Test
    fun `animations count as disabled only at an animator scale of exactly zero`() {
        assertTrue(animationsDisabled(0f))
        assertFalse(animationsDisabled(0.5f))
        assertFalse(animationsDisabled(1f))
    }
}
