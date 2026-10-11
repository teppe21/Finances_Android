package com.teppe21.finances.core.ui

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * A short fade in [color] over the top edge of a list while content is scrolled under it, so
 * rows dissolve into the fixed header above instead of ending in a hard cut. Read in the draw
 * phase only: scrolling redraws the edge without recomposing the list.
 */
fun Modifier.topScrollEdge(state: LazyListState, color: Color, height: Dp = 16.dp): Modifier = drawWithContent {
    drawContent()
    val scrolled = state.firstVisibleItemIndex > 0 || state.firstVisibleItemScrollOffset > 0
    if (scrolled) {
        val h = height.toPx()
        drawRect(Brush.verticalGradient(listOf(color, Color.Transparent), endY = h), size = Size(size.width, h))
    }
}
