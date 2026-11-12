package com.teppe21.finances

import com.teppe21.finances.core.ui.bandIndexAt
import com.teppe21.finances.core.ui.lineIndexAt
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ChartIndexTest {
    @Test fun lineEdgesAndMiddle() {
        assertEquals(0, lineIndexAt(0f, 100f, 5))
        assertEquals(4, lineIndexAt(100f, 100f, 5))
        assertEquals(2, lineIndexAt(50f, 100f, 5))
        assertEquals(1, lineIndexAt(37f, 100f, 5)) // 1.48 rounds down to the nearer point
        assertEquals(2, lineIndexAt(38f, 100f, 5))
    }

    @Test fun lineOutOfBoundsClamps() {
        assertEquals(0, lineIndexAt(-30f, 100f, 5))
        assertEquals(4, lineIndexAt(500f, 100f, 5))
    }

    @Test fun lineSingleAndEmpty() {
        assertEquals(0, lineIndexAt(70f, 100f, 1))
        assertNull(lineIndexAt(10f, 100f, 0))
        assertNull(lineIndexAt(10f, 0f, 3))
    }

    @Test fun bandEdgesAndOutOfBounds() {
        assertEquals(0, bandIndexAt(0f, 120f, 6))
        assertEquals(0, bandIndexAt(19.9f, 120f, 6))
        assertEquals(1, bandIndexAt(20f, 120f, 6))
        assertEquals(5, bandIndexAt(120f, 120f, 6))
        assertEquals(0, bandIndexAt(-5f, 120f, 6))
        assertEquals(5, bandIndexAt(999f, 120f, 6))
    }

    @Test fun bandSingleAndEmpty() {
        assertEquals(0, bandIndexAt(80f, 100f, 1))
        assertNull(bandIndexAt(10f, 100f, 0))
    }
}
