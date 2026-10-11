package com.teppe21.finances.core.ui

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp

/**
 * One line of text that steps down in size until it fits the width it is given, instead of being
 * clipped. It follows the user's font scale like any other text, but never goes below
 * [floorSp] (what that size is at the default font scale), so a small label never becomes
 * unreadable; at the floor an ellipsis is the last resort.
 *
 * Used where the width is fixed by the layout and cannot grow with the text: the tab bar labels
 * and the figure in the middle of the donut chart.
 */
@Composable
fun ShrinkToFitText(
    text: String,
    style: TextStyle,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    floorSp: Float = 11f,
    textAlign: TextAlign = TextAlign.Center
) {
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    BoxWithConstraints(
        modifier,
        contentAlignment = when (textAlign) {
            TextAlign.Start, TextAlign.Left -> Alignment.CenterStart
            TextAlign.End, TextAlign.Right -> Alignment.CenterEnd
            else -> Alignment.Center
        }
    ) {
        val maxWidth = constraints.maxWidth
        // The floor is given at font scale 1.0; below it the text is only as small as the style says.
        val floor = minOf(style.fontSize.value, floorSp / density.fontScale)
        val fitted = remember(text, style, maxWidth, density) {
            var size = style.fontSize.value
            while (size > floor && measurer.measure(text, style.copy(fontSize = size.sp), maxLines = 1, softWrap = false).size.width > maxWidth) {
                size = maxOf(floor, size * 0.94f)
            }
            size
        }
        Text(
            text,
            style = style.copy(fontSize = fitted.sp),
            color = color,
            textAlign = textAlign,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis
        )
    }
}
