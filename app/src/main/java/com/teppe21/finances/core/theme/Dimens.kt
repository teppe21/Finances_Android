package com.teppe21.finances.core.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp

/**
 * Spacing on an 8 dp grid, with 4 dp half steps for tight pairs (an icon and its label, a
 * title and its subtitle). Screens use these instead of their own numbers.
 */
object Spacing {
    /** Between a line of text and the quieter line under it. */
    val xxs = 2.dp
    val xs = 4.dp
    val s = 8.dp
    val m = 12.dp
    /** Screen side gutter, card padding, gap between a label and its value. */
    val l = 16.dp
    /** Between sections. */
    val xl = 24.dp
    val xxl = 32.dp
}

/** One radius family: small controls, rows inside cards, cards, sheets. */
object Radii {
    val small = RoundedCornerShape(6.dp)
    val control = RoundedCornerShape(10.dp)
    val row = RoundedCornerShape(12.dp)
    val card = RoundedCornerShape(14.dp)
    val sheet = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)
}

object Sizes {
    /** Minimum touch target and minimum height of a one-line row. */
    val touch = 48.dp
    /** Minimum height of a two-line list row (title and subtitle). */
    val row = 56.dp
    val leadingIcon = 36.dp
    /** The narrowest a name may get before an amount moves under it instead of squeezing it. */
    val minLabelWidth = 96.dp
}
