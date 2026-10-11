package com.teppe21.finances.core.ui

import android.app.UiModeManager
import android.content.Context
import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat

/**
 * Numbers behind the "glass" chrome, kept apart from the drawing so a unit test can hold them to
 * the contrast bar (see `GlassContrastTest`).
 */
object Glass {
    /**
     * Opacity of the tinted fill. Most of what is behind the bar is hidden, so the labels on it
     * keep their normal contrast whatever scrolls past; the rest lets a hint of it through.
     */
    const val FILL_ALPHA = 0.94f

    /** White highlight along the top of the bar, light and dark theme. */
    const val SHEEN_ALPHA_LIGHT = 0.45f
    const val SHEEN_ALPHA_DARK = 0.07f

    /** UiModeManager contrast level (0 standard, 0.5 medium, 1 high) from which the bar goes opaque. */
    const val OPAQUE_FROM_CONTRAST = 0.5f

    fun sheenAlpha(dark: Boolean): Float = if (dark) SHEEN_ALPHA_DARK else SHEEN_ALPHA_LIGHT
}

/**
 * A frosted-glass look for floating chrome: a tinted, slightly translucent fill, a soft highlight
 * along the top edge, a light-catching hairline and a shadow. It is deliberately **not** a
 * backdrop blur (Compose 1.6 cannot blur what is behind a node without a library or a render-node
 * workaround that redraws the whole screen); the contrast therefore never depends on the content
 * underneath.
 *
 * When the user has raised the system contrast (Android 14+), the fill is opaque and the sheen
 * is dropped, so the bar is a plain high-contrast surface.
 */
@Composable
fun Modifier.glassChrome(shape: Shape, shadowElevation: Dp = 6.dp): Modifier {
    val scheme = MaterialTheme.colorScheme
    val dark = scheme.background.luminance() < 0.5f
    val opaque = rememberSystemContrastRaised()
    val fill = if (opaque) scheme.surface else scheme.surface.copy(alpha = Glass.FILL_ALPHA)
    val outline = scheme.outline
    val sheen = remember(dark) {
        Brush.verticalGradient(listOf(Color.White.copy(alpha = Glass.sheenAlpha(dark)), Color.Transparent))
    }
    val edge = remember(dark, outline, opaque) {
        if (opaque) Brush.linearGradient(listOf(outline, outline))
        else Brush.linearGradient(listOf(Color.White.copy(alpha = if (dark) 0.18f else 0.9f), outline.copy(alpha = 0.55f)))
    }
    return this
        .shadow(shadowElevation, shape, clip = false)
        .background(fill, shape)
        .border(1.dp, edge, shape)
        .clip(shape)
        .then(if (opaque) Modifier else Modifier.drawBehind { drawRect(sheen) })
}

/** True when the system contrast setting (Android 14+) is above standard; always false before that. */
@Composable
fun rememberSystemContrastRaised(): Boolean {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) return false
    val context = LocalContext.current
    var raised by remember { mutableStateOf(contrastRaised(context)) }
    DisposableEffect(context) {
        val manager = context.getSystemService(Context.UI_MODE_SERVICE) as? UiModeManager
        val listener = UiModeManager.ContrastChangeListener { raised = it >= Glass.OPAQUE_FROM_CONTRAST }
        manager?.addContrastChangeListener(ContextCompat.getMainExecutor(context), listener)
        onDispose { manager?.removeContrastChangeListener(listener) }
    }
    return raised
}

private fun contrastRaised(context: Context): Boolean {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) return false
    val manager = context.getSystemService(Context.UI_MODE_SERVICE) as? UiModeManager ?: return false
    return manager.contrast >= Glass.OPAQUE_FROM_CONTRAST
}
