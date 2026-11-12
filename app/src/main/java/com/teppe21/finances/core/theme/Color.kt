package com.teppe21.finances.core.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

// Dark-only, single-accent palette. Every colour used by the app comes from here via
// MaterialTheme.colorScheme or MaterialTheme.finances; screens do not define their own.
// Green and red mean money in / out and market up / down, and nothing else.

val Background = Color(0xFF0B0D10)
val Surface = Color(0xFF14171B)        // cards, rows, bars: one step above the background
val SurfaceHigh = Color(0xFF1C2026)    // selected chips, banners
val Hairline = Color(0xFF282D34)       // borders and dividers
val TextPrimary = Color(0xFFF2F4F7)
val TextSecondary = Color(0xFF9AA3AF)
val Accent = Color(0xFF4A8FFF)         // the one accent; 6.2:1 on Background, 5.7:1 on Surface
val OnAccent = Background              // 6.2:1 on Accent (white would be 3.0:1)
val AccentContainer = Color(0xFF14284A)
val Income = Color(0xFF3DD68C)
val Expense = Color(0xFFFF6B6B)
val Warning = Color(0xFFE0A93B)

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

val DarkFinancesColors = FinancesColors(
    income = Income,
    expense = Expense,
    success = Accent,
    warning = Warning,
    transfer = TextSecondary,
    accent = Accent,
    marketUp = Income,
    marketDown = Expense
)

val LocalFinancesColors = staticCompositionLocalOf { DarkFinancesColors }

val MaterialTheme.finances: FinancesColors
    @Composable
    @ReadOnlyComposable
    get() = LocalFinancesColors.current
