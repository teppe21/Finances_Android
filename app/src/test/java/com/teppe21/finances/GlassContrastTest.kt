package com.teppe21.finances

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import com.teppe21.finances.core.theme.Ink
import com.teppe21.finances.core.theme.NightInk
import com.teppe21.finances.core.theme.NightInkSecondary
import com.teppe21.finances.core.theme.Paper
import com.teppe21.finances.core.theme.NightPaper
import com.teppe21.finances.core.theme.NightSurface
import com.teppe21.finances.core.theme.NightTerracotta
import com.teppe21.finances.core.theme.PaperSurface
import com.teppe21.finances.core.theme.InkSecondary
import com.teppe21.finances.core.theme.TerracottaDeep
import com.teppe21.finances.core.ui.Glass
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.pow

/**
 * The floating tab bar is a translucent "glass" fill. Whatever scrolls behind it is, at worst,
 * pure black or pure white (text or a bright card edge), so the labels are checked against the bar
 * composited over both extremes, with the highlight at its strongest. The selected tab also sits on
 * its 12 % primary pill. Labels are text and need 4.5:1.
 */
class GlassContrastTest {

    private fun channel(c: Float): Double = if (c <= 0.03928f) c / 12.92 else ((c + 0.055) / 1.055).pow(2.4)
    private fun luminance(c: Color) = 0.2126 * channel(c.red) + 0.7152 * channel(c.green) + 0.0722 * channel(c.blue)
    private fun contrast(a: Color, b: Color): Double {
        val la = luminance(a)
        val lb = luminance(b)
        return (maxOf(la, lb) + 0.05) / (minOf(la, lb) + 0.05)
    }

    /** The bar's fill as it is drawn over [backdrop], with the top highlight at full strength. */
    private fun bar(surface: Color, backdrop: Color, dark: Boolean): Color {
        val tinted = surface.copy(alpha = Glass.FILL_ALPHA).compositeOver(backdrop)
        return Color.White.copy(alpha = Glass.sheenAlpha(dark)).compositeOver(tinted)
    }

    /** Labels (text, 4.5:1) and the selected icon (graphic, 3:1) over the bar on every extreme backdrop. */
    private fun check(
        name: String, surface: Color, page: Color, unselectedLabel: Color, selectedLabel: Color,
        accent: Color, dark: Boolean
    ) {
        for ((backdropName, backdrop) in listOf("black" to Color.Black, "white" to Color.White, "page" to page)) {
            val fill = bar(surface, backdrop, dark)
            val pill = accent.copy(alpha = 0.12f).compositeOver(fill)
            val a = contrast(unselectedLabel, fill)
            val b = contrast(selectedLabel, pill)
            val c = contrast(accent, pill)
            assertTrue("$name unselected label over $backdropName is $a:1", a >= 4.5)
            assertTrue("$name selected label over $backdropName is $b:1", b >= 4.5)
            assertTrue("$name selected icon over $backdropName is $c:1", c >= 3.0)
        }
    }

    @Test
    fun lightGlassKeepsTabLabelsReadable() =
        check("light", PaperSurface, Paper, InkSecondary, Ink, TerracottaDeep, dark = false)

    @Test
    fun darkGlassKeepsTabLabelsReadable() =
        check("dark", NightSurface, NightPaper, NightInkSecondary, NightInk, NightTerracotta, dark = true)

    @Test
    fun theAccentColourAloneWouldNotHaveBeenEnoughForTheSelectedLabel() {
        // Why the selected label uses the main text colour: terracotta on its own pill is under 4.5:1
        // in the light theme, even on a fully opaque bar.
        val pill = TerracottaDeep.copy(alpha = 0.12f).compositeOver(PaperSurface)
        assertTrue(contrast(TerracottaDeep, pill) < 4.5)
    }

    @Test
    fun theFillIsMostlyOpaqueAndTheHighlightStaysSubtle() {
        assertTrue(Glass.FILL_ALPHA >= 0.9f && Glass.FILL_ALPHA < 1f)
        assertTrue(Glass.sheenAlpha(dark = false) <= 0.5f)
        assertTrue(Glass.sheenAlpha(dark = true) <= 0.1f)
        assertEquals(Glass.SHEEN_ALPHA_DARK, Glass.sheenAlpha(dark = true), 0f)
    }
}
