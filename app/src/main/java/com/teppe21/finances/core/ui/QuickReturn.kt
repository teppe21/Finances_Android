package com.teppe21.finances.core.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.IntOffset
import kotlin.math.roundToInt
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp

const val QUICK_RETURN_HEADER_TAG = "quickReturnHeader"

/**
 * Decides whether a "quick return" header is shown: it is always there at the top of the list (or
 * while [onScroll] is told it is pinned, for example while the search field has focus), leaves when
 * the list is scrolled down by more than [thresholdPx] and comes back as soon as the list is scrolled
 * back up by that much. The threshold keeps a tremor of the finger from flickering it.
 */
@Stable
class QuickReturnState(private val thresholdPx: Float) {
    var visible by mutableStateOf(true)
        private set
    private var travelled = 0f

    /** [deltaY] is the scroll offered to the list: negative when the content moves up (scrolling down). */
    fun onScroll(deltaY: Float, atTop: Boolean, pinned: Boolean) {
        if (pinned || atTop) {
            visible = true
            travelled = 0f
            return
        }
        if (deltaY == 0f) return
        if (travelled != 0f && (travelled < 0f) != (deltaY < 0f)) travelled = 0f
        travelled += deltaY
        if (travelled <= -thresholdPx) visible = false else if (travelled >= thresholdPx) visible = true
    }
}

/**
 * A list with a header (search, filters) that stays out of the way: it sits over the top of the list,
 * leaves when the user scrolls down and returns on the first scroll back up, so it is one gesture away
 * without taking part of the screen all the time. [content] receives the padding that keeps the first
 * item below the header.
 */
@Composable
fun QuickReturnScaffold(
    listState: LazyListState,
    modifier: Modifier = Modifier,
    pinned: Boolean = false,
    header: @Composable ColumnScope.() -> Unit,
    content: @Composable (contentPadding: PaddingValues) -> Unit
) {
    val density = LocalDensity.current
    val state = remember(density) { QuickReturnState(thresholdPx = with(density) { 24.dp.toPx() }) }
    val pinnedNow by rememberUpdatedState(pinned)
    val connection = remember(listState, state) {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                val atTop = listState.firstVisibleItemIndex == 0 && listState.firstVisibleItemScrollOffset == 0
                state.onScroll(available.y, atTop, pinnedNow)
                return Offset.Zero
            }
        }
    }
    var headerHeightPx by remember { mutableFloatStateOf(0f) }
    val shown = state.visible || pinned
    val offset by animateFloatAsState(if (shown) 0f else -headerHeightPx, Motion.move(), label = "quickReturn")

    Box(modifier.fillMaxSize().clipToBounds().nestedScroll(connection)) {
        content(PaddingValues(top = with(density) { headerHeightPx.toDp() }))
        Column(
            Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                // A placement offset, not a graphics layer: the semantics bounds (what TalkBack outlines and
                // what tests read) must move with the header, or a hidden header would still claim the top of the list.
                .offset { IntOffset(0, offset.roundToInt()) }
                .testTag(QUICK_RETURN_HEADER_TAG)
                .onSizeChanged { headerHeightPx = it.height.toFloat() }
                .background(MaterialTheme.colorScheme.background),
            content = header
        )
    }
}
