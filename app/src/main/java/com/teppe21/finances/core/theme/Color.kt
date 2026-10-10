package com.teppe21.finances.core.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

// Warm editorial palette. Every colour used by the app comes from here via
// MaterialTheme.colorScheme or MaterialTheme.finances; screens do not define their own.

// Light
val Paper = Color(0xFFFAF7F2)          // background
val PaperSurface = Color(0xFFF3EEE5)   // surfaces / rows, one step below the background
val Ink = Color(0xFF2B2B2B)            // main text
val InkSecondary = Color(0xFF6B645C)   // secondary text (5.4:1 on Paper)
val Hairline = Color(0xFFE2DACD)       // borders and dividers
val Terracotta = Color(0xFFC15F3C)     // brand accent: indicators, selected states (not text)
val TerracottaDeep = Color(0xFFA94E2E) // filled primary actions; white on it is 5.5:1 (white on Terracotta is 4.2:1)
val IncomeGreen = Color(0xFF2E7D4F)
val ExpenseRed = Color(0xFFB03A2E)
val WarningOchre = Color(0xFF8A5A00)
val TransferSlate = Color(0xFF4F5D75)
val MarketUp = Color(0xFF2B6F77)       // market movement, deliberately not the income/expense colours
val MarketDown = Color(0xFF7A4A74)

// Dark (separately chosen, not an inversion)
val NightPaper = Color(0xFF1C1A17)
val NightSurface = Color(0xFF26231F)
val NightInk = Color(0xFFEDE6DB)
val NightInkSecondary = Color(0xFFB5AA9A)
val NightHairline = Color(0xFF3A3630)
val NightTerracotta = Color(0xFFE08A66)
val NightOnTerracotta = Color(0xFF2B1A12)
val NightIncome = Color(0xFF6CC794)
val NightExpense = Color(0xFFEE8478)
val NightWarning = Color(0xFFE0A93B)
val NightTransfer = Color(0xFF9BAAC4)
val NightMarketUp = Color(0xFF6FC3CC)
val NightMarketDown = Color(0xFFC796C0)

/** Semantic colours that Material 3's ColorScheme has no slot for. */
@Immutable
data class FinancesColors(
    val income: Color,
    val expense: Color,
    val success: Color,
    val warning: Color,
    val transfer: Color,
    val accent: Color,
    val marketUp: Color,
    val marketDown: Color
)

val LightFinancesColors = FinancesColors(
    income = IncomeGreen,
    expense = ExpenseRed,
    success = IncomeGreen,
    warning = WarningOchre,
    transfer = TransferSlate,
    accent = Terracotta,
    marketUp = MarketUp,
    marketDown = MarketDown
)

val DarkFinancesColors = FinancesColors(
    income = NightIncome,
    expense = NightExpense,
    success = NightIncome,
    warning = NightWarning,
    transfer = NightTransfer,
    accent = NightTerracotta,
    marketUp = NightMarketUp,
    marketDown = NightMarketDown
)

val LocalFinancesColors = staticCompositionLocalOf { LightFinancesColors }

val MaterialTheme.finances: FinancesColors
    @Composable
    @ReadOnlyComposable
    get() = LocalFinancesColors.current
