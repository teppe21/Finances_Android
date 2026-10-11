package com.teppe21.finances.core.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp

// One family: the platform's own (Roboto on most devices), so text matches the system and the user's
// font settings. Hierarchy comes from size, weight and leading together, and type changes shape with
// size: large text is tracked tighter and leaded tighter, small text is tracked slightly open and
// leaded looser. Tracking is in em so it scales with the user's font size; all sizes are sp.
private val Family = FontFamily.Default

val Typography = Typography(
    // Hero figures (the total balance).
    displayLarge = TextStyle(
        fontFamily = Family,
        fontWeight = FontWeight.SemiBold,
        fontSize = 40.sp,
        lineHeight = 44.sp,
        letterSpacing = (-0.025).em
    ),
    displayMedium = TextStyle(
        fontFamily = Family,
        fontWeight = FontWeight.SemiBold,
        fontSize = 32.sp,
        lineHeight = 36.sp,
        letterSpacing = (-0.02).em
    ),
    headlineLarge = TextStyle(
        fontFamily = Family,
        fontWeight = FontWeight.SemiBold,
        fontSize = 28.sp,
        lineHeight = 32.sp,
        letterSpacing = (-0.018).em
    ),
    headlineMedium = TextStyle(
        fontFamily = Family,
        fontWeight = FontWeight.SemiBold,
        fontSize = 24.sp,
        lineHeight = 30.sp,
        letterSpacing = (-0.014).em
    ),
    headlineSmall = TextStyle(
        fontFamily = Family,
        fontWeight = FontWeight.SemiBold,
        fontSize = 22.sp,
        lineHeight = 28.sp,
        letterSpacing = (-0.01).em
    ),
    titleLarge = TextStyle(
        fontFamily = Family,
        fontWeight = FontWeight.SemiBold,
        fontSize = 20.sp,
        lineHeight = 26.sp,
        letterSpacing = (-0.008).em
    ),
    titleMedium = TextStyle(
        fontFamily = Family,
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp,
        lineHeight = 22.sp,
        letterSpacing = (-0.003).em
    ),
    titleSmall = TextStyle(
        fontFamily = Family,
        fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.em
    ),
    bodyLarge = TextStyle(
        fontFamily = Family,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.em
    ),
    bodyMedium = TextStyle(
        fontFamily = Family,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 21.sp,
        letterSpacing = 0.004.em
    ),
    bodySmall = TextStyle(
        fontFamily = Family,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 17.sp,
        letterSpacing = 0.01.em
    ),
    labelLarge = TextStyle(
        fontFamily = Family,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.004.em
    ),
    labelMedium = TextStyle(
        fontFamily = Family,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.012.em
    ),
    labelSmall = TextStyle(
        fontFamily = Family,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 15.sp,
        letterSpacing = 0.02.em
    )
)

/** Apply to monetary text so digits line up (tabular figures) where the font supports it. */
fun TextStyle.tabular(): TextStyle = copy(fontFeatureSettings = "tnum")
