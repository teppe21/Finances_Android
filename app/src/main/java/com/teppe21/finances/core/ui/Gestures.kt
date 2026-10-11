package com.teppe21.finances.core.ui

import com.teppe21.finances.core.theme.Radii

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import kotlin.math.abs

/** Pure gesture maths, kept free of Compose so it can be unit tested. */
object GesturePhysics {

    /** Scroll-like deceleration: how far a release at [velocity] (px/s) would carry on. */
    const val NORMAL_DECELERATION = 0.998f

    /**
     * Where a motion released at [velocity] (px/s) would come to rest, as a distance in px. The
     * exponential-decay form used by scroll views, not `v^2 / 2a`: a small fast flick projects a long
     * way, which is what makes a throw feel like a throw.
     */
    fun project(velocity: Float, decelerationRate: Float = NORMAL_DECELERATION): Float =
        (velocity / 1000f) * decelerationRate / (1f - decelerationRate)

    /**
     * Progressive resistance past an edge: the further beyond the bound, the less the element follows.
     * The result grows with [overshoot] but never exceeds [dimension]; its sign follows [overshoot].
     */
    fun rubberband(overshoot: Float, dimension: Float, constant: Float = 0.55f): Float {
        if (overshoot == 0f || dimension <= 0f) return 0f
        val distance = abs(overshoot)
        val resisted = (distance * dimension * constant) / (dimension + constant * distance)
        return if (overshoot < 0f) -resisted else resisted
    }

    /** The offset to show for a raw finger offset: 1:1 inside [min]..[max], resisted outside. */
    fun resisted(raw: Float, min: Float, max: Float, dimension: Float): Float = when {
        raw > max -> max + rubberband(raw - max, dimension)
        raw < min -> min + rubberband(raw - min, dimension)
        else -> raw
    }

    enum class Release { COMMIT_RIGHT, COMMIT_LEFT, SETTLE }

    /**
     * Decides what a release does. The decision uses the *projected* resting position, so a quick
     * flick commits even if the finger had not travelled far, and a slow drag that stops short settles
     * back. A direction that has no action never commits.
     */
    fun release(
        offset: Float,
        velocity: Float,
        width: Float,
        canCommitRight: Boolean,
        canCommitLeft: Boolean,
        commitFraction: Float = COMMIT_FRACTION
    ): Release {
        if (width <= 0f) return Release.SETTLE
        val projected = offset + project(velocity)
        val threshold = width * commitFraction
        return when {
            canCommitRight && offset > 0f && projected >= threshold -> Release.COMMIT_RIGHT
            canCommitLeft && offset < 0f && projected <= -threshold -> Release.COMMIT_LEFT
            else -> Release.SETTLE
        }
    }

    /** Fraction of the width a projected release must reach to commit. */
    const val COMMIT_FRACTION = 0.4f
}

/** An action revealed by swiping a row. A non-gesture path must always exist as well (a button). */
class SwipeAction(
    val label: String,
    val icon: ImageVector,
    val container: Color,
    val content: Color,
    val onCommit: () -> Unit
)

/**
 * A row that can be swiped to reveal and commit an action.
 *
 * - The content follows the finger 1:1 from the touch slop onward, keeping the offset at which it was
 *   grabbed; a direction without an action resists like a rubber band instead of stopping.
 * - On release the motion continues at the finger's velocity into a spring, and the outcome is chosen
 *   from the momentum-projected position.
 * - It is always interruptible: touching down mid-flight stops the animation where it is and the drag
 *   carries on from there.
 * - A light tick marks the point at which releasing will commit.
 * - Screen readers get the same actions as custom accessibility actions.
 *
 * [onCommit] of the chosen action runs after the content has left; the caller is expected to remove or
 * replace the row.
 */
@Composable
fun SwipeActionBox(
    modifier: Modifier = Modifier,
    swipeRight: SwipeAction? = null,
    swipeLeft: SwipeAction? = null,
    shape: Shape = Radii.card,
    content: @Composable () -> Unit
) {
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current
    val offset = remember { Animatable(0f) }
    var width by remember { mutableFloatStateOf(0f) }
    val tracker = remember { VelocityTracker() }
    val right by rememberUpdatedState(swipeRight)
    val left by rememberUpdatedState(swipeLeft)
    var rawOffset by remember { mutableFloatStateOf(0f) }
    var armed by remember { mutableStateOf(false) }

    Box(
        modifier
            .clip(shape)
            .onSizeChanged { width = it.width.toFloat() }
            .semantics {
                customActions = listOfNotNull(
                    swipeRight?.let { action -> CustomAccessibilityAction(action.label) { action.onCommit(); true } },
                    swipeLeft?.let { action -> CustomAccessibilityAction(action.label) { action.onCommit(); true } }
                )
            }
    ) {
        SwipeBackground(right, Alignment.CenterStart) { (offset.value / (width * GesturePhysics.COMMIT_FRACTION)).coerceIn(0f, 1f) }
        SwipeBackground(left, Alignment.CenterEnd) { (-offset.value / (width * GesturePhysics.COMMIT_FRACTION)).coerceIn(0f, 1f) }

        Box(
            Modifier
                // Pointer input sits outside the translation so positions (and velocity) stay stable.
                .pointerInput(Unit) {
                    detectHorizontalDragGestures(
                        onDragStart = {
                            tracker.resetTracking()
                            // Grab a moving row exactly where it is, then continue from there.
                            scope.launch { offset.stop() }
                            rawOffset = offset.value
                        },
                        onHorizontalDrag = { change, delta ->
                            change.consume()
                            tracker.addPosition(change.uptimeMillis, change.position)
                            rawOffset += delta
                            val max = if (right != null) width else 0f
                            val min = if (left != null) -width else 0f
                            val shown = GesturePhysics.resisted(rawOffset, min, max, width)
                            scope.launch { offset.snapTo(shown) }
                            val nowArmed = abs(shown) >= width * GesturePhysics.COMMIT_FRACTION &&
                                (if (shown > 0f) right != null else left != null)
                            if (nowArmed != armed) {
                                armed = nowArmed
                                if (nowArmed) haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            }
                        },
                        onDragEnd = {
                            armed = false
                            val velocity = tracker.calculateVelocity().x
                            scope.launch {
                                when (GesturePhysics.release(offset.value, velocity, width, right != null, left != null)) {
                                    GesturePhysics.Release.COMMIT_RIGHT -> {
                                        offset.animateTo(width, Motion.sheet(), initialVelocity = velocity)
                                        right?.onCommit?.invoke()
                                    }
                                    GesturePhysics.Release.COMMIT_LEFT -> {
                                        offset.animateTo(-width, Motion.sheet(), initialVelocity = velocity)
                                        left?.onCommit?.invoke()
                                    }
                                    GesturePhysics.Release.SETTLE ->
                                        offset.animateTo(0f, Motion.sheet(), initialVelocity = velocity)
                                }
                            }
                        },
                        onDragCancel = {
                            armed = false
                            scope.launch { offset.animateTo(0f, Motion.sheet()) }
                        }
                    )
                }
                .graphicsLayer { translationX = offset.value }
        ) {
            content()
        }
    }
}

@Composable
private fun BoxScope.SwipeBackground(
    action: SwipeAction?,
    alignment: Alignment,
    reveal: () -> Float
) {
    if (action == null) return
    Box(
        Modifier
            .matchParentSize()
            .graphicsLayer { alpha = reveal() }
            // Decorative while swiping: the same actions are exposed once, as custom accessibility actions.
            .clearAndSetSemantics { }
            .background(action.container)
            .padding(horizontal = 20.dp),
        contentAlignment = alignment
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(action.icon, contentDescription = null, tint = action.content, modifier = Modifier.size(22.dp))
            Text(
                action.label,
                color = action.content,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.width(0.dp))
        }
    }
}
