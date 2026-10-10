package com.teppe21.finances.core.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val DarkColorScheme = darkColorScheme(
    primary = NightTerracotta,
    onPrimary = NightOnTerracotta,
    primaryContainer = NightSurface,
    onPrimaryContainer = NightTerracotta,
    secondary = NightTerracotta,
    onSecondary = NightOnTerracotta,
    secondaryContainer = NightSurface,
    onSecondaryContainer = NightInk,
    tertiary = NightTransfer,
    background = NightPaper,
    onBackground = NightInk,
    surface = NightSurface,
    onSurface = NightInk,
    surfaceVariant = NightSurface,
    onSurfaceVariant = NightInkSecondary,
    outline = NightHairline,
    outlineVariant = NightHairline,
    error = NightExpense,
    onError = NightOnTerracotta
)

private val LightColorScheme = lightColorScheme(
    primary = TerracottaDeep,
    onPrimary = Color.White,
    primaryContainer = PaperSurface,
    onPrimaryContainer = TerracottaDeep,
    secondary = TerracottaDeep,
    onSecondary = Color.White,
    secondaryContainer = PaperSurface,
    onSecondaryContainer = Ink,
    tertiary = TransferSlate,
    background = Paper,
    onBackground = Ink,
    surface = PaperSurface,
    onSurface = Ink,
    surfaceVariant = PaperSurface,
    onSurfaceVariant = InkSecondary,
    outline = Hairline,
    outlineVariant = Hairline,
    error = ExpenseRed,
    onError = Color.White
)

@Composable
fun FinancesTheme(
    darkTheme: Boolean = true,
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    val view = LocalView.current

    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window
            if (window != null) {
                window.statusBarColor = colorScheme.background.toArgb()
                window.navigationBarColor = colorScheme.background.toArgb()
                val controller = WindowCompat.getInsetsController(window, view)
                controller.isAppearanceLightStatusBars = !darkTheme
                controller.isAppearanceLightNavigationBars = !darkTheme
            }
        }
    }

    // Dynamic (wallpaper) colours are intentionally not used: the palette is fixed.
    CompositionLocalProvider(
        LocalFinancesColors provides if (darkTheme) DarkFinancesColors else LightFinancesColors
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            content = content
        )
    }
}
