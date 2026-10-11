package com.teppe21.finances.core.ui

import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext

/**
 * The app's motion language, built on springs.
 *
 * A spring is the tool for anything a person can touch because it is interruptible and
 * velocity-aware: when the target changes mid-flight the motion continues from where it is, and
 * a velocity handed in (a fling) carries on without a seam. So structure and content move with
 * springs, and only opacity and colour use short fixed-duration easing.
 *
 * - [move]: repositioning, expanding, content size. Critically damped, no overshoot.
 * - [sheet]: things thrown or released with momentum (swipe settle, indicators). A little overshoot.
 * - [slow]: large travel such as screen-level slides and chart geometry.
 * - [press]: direct-manipulation feedback, applied on touch-down.
 * - [short] / [exit]: opacity and colour only (150 ms).
 *
 * Overshoot is only used where the gesture carried momentum; a menu or card that merely appears
 * never bounces. Exits are quicker than entrances, and nothing ever waits before responding to a
 * touch.
 *
 * All Compose animations are scaled by the system animator duration scale, so turning animations
 * off (Developer options, or Accessibility "Remove animations") makes them instant.
 * [rememberReduceMotion] additionally lets a screen choose a simpler transition (fade instead of
 * slide) in that case.
 */
object Motion {
    const val DURATION_SHORT = 150
    const val DURATION_MEDIUM = 250
    const val DURATION_LONG = 350

    /** Apple's "move": damping 1.0, response about 0.4 s. */
    const val MOVE_STIFFNESS = 250f

    /** Apple's "drawer": damping 0.8, response about 0.3 s. */
    const val SHEET_STIFFNESS = 440f
    const val SHEET_DAMPING = 0.8f

    /** Enter and move: quick start, soft landing. */
    val Decelerate = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)

    /** Leave: gentle start, then gone. */
    val Accelerate = CubicBezierEasing(0.3f, 0f, 0.8f, 0.15f)

    /** Changes between two visible states. */
    val Standard = FastOutSlowInEasing

    // Opacity and colour.
    fun <T> short(): FiniteAnimationSpec<T> = tween(DURATION_SHORT, easing = Standard)
    fun <T> exit(): FiniteAnimationSpec<T> = tween(DURATION_SHORT, easing = Accelerate)

    // Structure and position.
    fun <T> move(): FiniteAnimationSpec<T> = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = MOVE_STIFFNESS)
    fun <T> medium(): FiniteAnimationSpec<T> = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = 300f)
    fun <T> slow(): FiniteAnimationSpec<T> = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessLow)
    fun <T> long(): FiniteAnimationSpec<T> = slow()

    /** Momentum: use only after a gesture released with speed. */
    fun <T> sheet(): FiniteAnimationSpec<T> = spring(dampingRatio = SHEET_DAMPING, stiffness = SHEET_STIFFNESS)

    /** Direct-manipulation response, critically damped and stiff so it feels immediate. */
    fun <T> press(): FiniteAnimationSpec<T> = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = 700f)

    /** An element appearing in a column: fades in while the space opens up. */
    fun appear(): EnterTransition =
        fadeIn(short()) + expandVertically(medium(), expandFrom = Alignment.Top)

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

/**
 * Scales content down slightly from the moment the finger touches down (not on release) and springs
 * back, interruptibly, when it lifts or the touch is cancelled. The click itself is never delayed.
 */
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
    return animateFloatAsState(current, Motion.slow(), label = label).value
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
        progress.animateTo(1f, Motion.slow())
    }
    return progress.asState()
}
