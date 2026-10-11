package com.teppe21.finances.core.ui

import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext

/**
 * The app's motion language: short, decelerating, never decorative.
 *
 * Timing conventions
 * - [DURATION_SHORT] (150 ms): fades, small state changes, press feedback, error text.
 * - [DURATION_MEDIUM] (250 ms): content swapping, expand/collapse, list items entering or leaving.
 * - [DURATION_LONG] (350 ms): screen-level moves and chart geometry.
 * Exits are faster than entrances. Nothing ever waits before responding to a touch.
 *
 * All Compose animations are scaled by the system "Animator duration scale", so turning
 * animations off in Developer options or Accessibility ("Remove animations") makes them
 * instant. [rememberReduceMotion] additionally lets a screen pick a simpler transition
 * (fade instead of slide) in that case.
 */
object Motion {
    const val DURATION_SHORT = 150
    const val DURATION_MEDIUM = 250
    const val DURATION_LONG = 350

    /** Enter and move: quick start, soft landing. */
    val Decelerate = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)

    /** Leave: gentle start, then gone. */
    val Accelerate = CubicBezierEasing(0.3f, 0f, 0.8f, 0.15f)

    /** Changes between two visible states. */
    val Standard = FastOutSlowInEasing

    fun <T> short(): FiniteAnimationSpec<T> = tween(DURATION_SHORT, easing = Standard)
    fun <T> medium(): FiniteAnimationSpec<T> = tween(DURATION_MEDIUM, easing = Decelerate)
    fun <T> long(): FiniteAnimationSpec<T> = tween(DURATION_LONG, easing = Decelerate)
    fun <T> exit(): FiniteAnimationSpec<T> = tween(DURATION_SHORT, easing = Accelerate)

    /** Press feedback and other direct-manipulation responses: spring, no bounce. */
    fun <T> press(): FiniteAnimationSpec<T> = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMedium)

    /** An element appearing in a column: fades in while the space opens up. */
    fun appear(): EnterTransition =
        fadeIn(medium()) + expandVertically(medium(), expandFrom = Alignment.Top)

    /** The reverse of [appear], slightly faster. */
    fun disappear(): ExitTransition =
        fadeOut(exit()) + shrinkVertically(medium(), shrinkTowards = Alignment.Top)

    /** For overlays and small affordances that should not move anything else. */
    fun fadeInShort(): EnterTransition = fadeIn(short())
    fun fadeOutShort(): ExitTransition = fadeOut(exit())
}

/** True when the user has turned system animations off (animator duration scale 0). */
fun animationsDisabled(scale: Float): Boolean = scale == 0f

@Composable
fun rememberReduceMotion(): Boolean {
    val context = LocalContext.current
    var reduce by remember { mutableStateOf(animationsDisabled(readAnimatorScale(context))) }
    DisposableEffect(context) {
        val resolver = context.contentResolver
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                reduce = animationsDisabled(readAnimatorScale(context))
            }
        }
        resolver.registerContentObserver(Settings.Global.getUriFor(Settings.Global.ANIMATOR_DURATION_SCALE), false, observer)
        onDispose { resolver.unregisterContentObserver(observer) }
    }
    return reduce
}

private fun readAnimatorScale(context: android.content.Context): Float =
    Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)

/** Scales content down slightly while it is pressed. Direct feedback; the click itself is not delayed. */
fun Modifier.pressScale(interactionSource: InteractionSource, pressedScale: Float = 0.97f): Modifier = composed {
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) pressedScale else 1f, Motion.press(), label = "pressScale")
    graphicsLayer {
        scaleX = scale
        scaleY = scale
    }
}

/** [clickable] with the standard ripple plus [pressScale]. */
fun Modifier.pressClickable(enabled: Boolean = true, onClick: () -> Unit): Modifier = composed {
    val source = remember { MutableInteractionSource() }
    pressScale(source).clickable(
        interactionSource = source,
        indication = LocalIndication.current,
        enabled = enabled,
        onClick = onClick
    )
}

/**
 * A 0..1 fraction (bar height, progress) that grows from zero when it first appears and moves
 * smoothly when the target changes. Only geometry is animated; the figures shown next to a bar
 * are always the exact current values.
 */
@Composable
fun animatedFraction(target: Float, label: String = "fraction"): Float {
    var current by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(target) { current = target }
    return animateFloatAsState(current, Motion.long(), label = label).value
}

/**
 * 0 -> 1 whenever [key] changes (first appearance, new period, new data set). Meant for drawing
 * a chart in: read it inside a draw block so only drawing, not composition, is invalidated.
 */
@Composable
fun rememberReveal(key: Any?): State<Float> {
    val progress = remember { Animatable(0f) }
    LaunchedEffect(key) {
        progress.snapTo(0f)
        progress.animateTo(1f, Motion.long())
    }
    return progress.asState()
}
