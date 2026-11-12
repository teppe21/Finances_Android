package com.teppe21.finances

import androidx.compose.ui.graphics.Color
import com.teppe21.finances.core.theme.Accent
import com.teppe21.finances.core.theme.AccentContainer
import com.teppe21.finances.core.theme.Background
import com.teppe21.finances.core.theme.CategoryColorProvider
import com.teppe21.finances.core.theme.Expense
import com.teppe21.finances.core.theme.Income
import com.teppe21.finances.core.theme.OnAccent
import com.teppe21.finances.core.theme.Surface
import com.teppe21.finances.core.theme.SurfaceHigh
import com.teppe21.finances.core.theme.TextPrimary
import com.teppe21.finances.core.theme.TextSecondary
import com.teppe21.finances.core.theme.Warning
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.pow

/**
 * WCAG contrast of the colours the app actually draws, on the backgrounds it draws them on. Text
 * needs 4.5:1 (amounts are text too); graphics such as category colours need 3:1.
 */
class ThemeContrastTest {

    private fun channel(c: Float): Double = if (c <= 0.03928f) c / 12.92 else ((c + 0.055) / 1.055).pow(2.4)

    private fun luminance(c: Color): Double =
        0.2126 * channel(c.red) + 0.7152 * channel(c.green) + 0.0722 * channel(c.blue)

    private fun contrast(a: Color, b: Color): Double {
        val (hi, lo) = luminance(a).let { la -> luminance(b).let { lb -> maxOf(la, lb) to minOf(la, lb) } }
        return (hi + 0.05) / (lo + 0.05)
    }

    private fun assertReadable(name: String, text: Color, background: Color, minimum: Double = 4.5) {
        val ratio = contrast(text, background)
        assertTrue("$name is $ratio:1, needs $minimum:1", ratio >= minimum)
    }

    @Test
    fun textIsReadableOnItsBackgrounds() {
        for ((bgName, bg) in listOf("background" to Background, "card surface" to Surface, "raised surface" to SurfaceHigh)) {
            assertReadable("primary text on $bgName", TextPrimary, bg)
            assertReadable("secondary text on $bgName", TextSecondary, bg)
            assertReadable("accent on $bgName", Accent, bg)
            assertReadable("income on $bgName", Income, bg)
            assertReadable("expense on $bgName", Expense, bg)
            assertReadable("warning on $bgName", Warning, bg)
        }
        assertReadable("accent on its container", Accent, AccentContainer)
        assertReadable("text on the primary button", OnAccent, Accent)
    }

    @Test
    fun cardsStandApartFromTheBackground() {
        // Cards are flat (hairline outline, no shadow), so their tone is part of what separates them.
        assertTrue(contrast(Background, Surface) > 1.0)
        assertTrue(contrast(Surface, SurfaceHigh) > 1.0)
    }

    @Test
    fun categoryColoursAreVisibleOnTheCardSurface() {
        for (hex in CategoryColorProvider.PRESET_PALETTE) {
            val ratio = contrast(CategoryColorProvider.getColor(null, hex), Surface)
            assertTrue("$hex is $ratio:1 on the card surface, needs 3:1", ratio >= 3.0)
        }
    }
}
