package com.teppe21.finances.core.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider

private val DarkColorScheme = darkColorScheme(
    primary = Accent,
    onPrimary = OnAccent,
    primaryContainer = AccentContainer,
    onPrimaryContainer = Accent,
    secondary = Accent,
    onSecondary = OnAccent,
    secondaryContainer = SurfaceHigh,
    onSecondaryContainer = TextPrimary,
    tertiary = TextSecondary,
    background = Background,
    onBackground = TextPrimary,
    surface = Surface,
    onSurface = TextPrimary,
    surfaceVariant = Surface,
    onSurfaceVariant = TextSecondary,
    outline = Hairline,
    outlineVariant = Hairline,
    error = Expense,
    onError = OnAccent
)

// Dark only. System bar icon contrast is set by enableEdgeToEdge in MainActivity.
// Dynamic (wallpaper) colours are intentionally not used: the palette is fixed.
@Composable
fun FinancesTheme(content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalFinancesColors provides DarkFinancesColors) {
        MaterialTheme(
            colorScheme = DarkColorScheme,
            typography = Typography,
            content = content
        )
    }
}
