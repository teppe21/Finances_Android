package com.teppe21.finances

import androidx.compose.ui.graphics.Color
import com.teppe21.finances.core.theme.ExpenseRed
import com.teppe21.finances.core.theme.IncomeGreen
import com.teppe21.finances.core.theme.Ink
import com.teppe21.finances.core.theme.InkSecondary
import com.teppe21.finances.core.theme.MarketDown
import com.teppe21.finances.core.theme.MarketUp
import com.teppe21.finances.core.theme.NightExpense
import com.teppe21.finances.core.theme.NightIncome
import com.teppe21.finances.core.theme.NightInk
import com.teppe21.finances.core.theme.NightInkSecondary
import com.teppe21.finances.core.theme.NightMarketDown
import com.teppe21.finances.core.theme.NightMarketUp
import com.teppe21.finances.core.theme.NightOnTerracotta
import com.teppe21.finances.core.theme.NightPaper
import com.teppe21.finances.core.theme.NightSurface
import com.teppe21.finances.core.theme.NightTerracotta
import com.teppe21.finances.core.theme.NightTransfer
import com.teppe21.finances.core.theme.NightWarning
import com.teppe21.finances.core.theme.Paper
import com.teppe21.finances.core.theme.PaperSurface
import com.teppe21.finances.core.theme.TerracottaDeep
import com.teppe21.finances.core.theme.TransferSlate
import com.teppe21.finances.core.theme.WarningOchre
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.pow

/**
 * WCAG contrast of the text colours the app actually draws, on the backgrounds it draws them on, in
 * both themes. Text needs 4.5:1. Amounts (income, expense, transfer) are text too, so they are
 * held to the same bar on the card surface, in light and in dark.
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
    fun lightThemeTextIsReadableOnItsBackgrounds() {
        for ((bgName, bg) in listOf("paper" to Paper, "card surface" to PaperSurface)) {
            assertReadable("ink on $bgName", Ink, bg)
            assertReadable("secondary ink on $bgName", InkSecondary, bg)
            assertReadable("income on $bgName", IncomeGreen, bg)
            assertReadable("expense on $bgName", ExpenseRed, bg)
            assertReadable("transfer on $bgName", TransferSlate, bg)
            assertReadable("warning on $bgName", WarningOchre, bg)
            assertReadable("primary on $bgName", TerracottaDeep, bg)
            assertReadable("market up on $bgName", MarketUp, bg)
            assertReadable("market down on $bgName", MarketDown, bg)
        }
        assertReadable("white on the primary button", Color.White, TerracottaDeep)
    }

    @Test
    fun darkThemeTextIsReadableOnItsBackgrounds() {
        for ((bgName, bg) in listOf("night paper" to NightPaper, "night card surface" to NightSurface)) {
            assertReadable("ink on $bgName", NightInk, bg)
            assertReadable("secondary ink on $bgName", NightInkSecondary, bg)
            assertReadable("income on $bgName", NightIncome, bg)
            assertReadable("expense on $bgName", NightExpense, bg)
            assertReadable("transfer on $bgName", NightTransfer, bg)
            assertReadable("warning on $bgName", NightWarning, bg)
            assertReadable("primary on $bgName", NightTerracotta, bg)
            assertReadable("market up on $bgName", NightMarketUp, bg)
            assertReadable("market down on $bgName", NightMarketDown, bg)
        }
        assertReadable("dark text on the primary button", NightOnTerracotta, NightTerracotta)
    }

    @Test
    fun cardsStandApartFromTheBackgroundInBothThemes() {
        // Cards are flat (no shadow), so their tone is all that separates them: not identical.
        assertTrue(contrast(Paper, PaperSurface) > 1.0)
        assertTrue(contrast(NightPaper, NightSurface) > 1.0)
    }
}
