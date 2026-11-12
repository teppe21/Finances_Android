package com.teppe21.finances

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import com.teppe21.finances.core.theme.Accent
import com.teppe21.finances.core.theme.Surface
import com.teppe21.finances.core.theme.TextSecondary
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.pow

/**
 * The tab bar is an opaque `Surface` with a hairline outline, so its labels are checked against
 * that fill and, for the selected tab, against the 12 % accent pill. Labels are text: 4.5:1.
 */
class GlassContrastTest {

    private fun channel(c: Float): Double = if (c <= 0.03928f) c / 12.92 else ((c + 0.055) / 1.055).pow(2.4)
    private fun luminance(c: Color) = 0.2126 * channel(c.red) + 0.7152 * channel(c.green) + 0.0722 * channel(c.blue)
    private fun contrast(a: Color, b: Color): Double {
        val la = luminance(a)
        val lb = luminance(b)
        return (maxOf(la, lb) + 0.05) / (minOf(la, lb) + 0.05)
    }

    @Test
    fun unselectedTabLabelsAreReadableOnTheBar() {
        val ratio = contrast(TextSecondary, Surface)
        assertTrue("unselected label is $ratio:1", ratio >= 4.5)
    }

    @Test
    fun selectedTabLabelsAreReadableOnTheAccentPill() {
        val pill = Accent.copy(alpha = 0.12f).compositeOver(Surface)
        val ratio = contrast(Accent, pill)
        assertTrue("selected label is $ratio:1", ratio >= 4.5)
    }
}
