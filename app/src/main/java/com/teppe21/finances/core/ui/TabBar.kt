package com.teppe21.finances.core.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp

class TabBarItem(val label: String, val icon: ImageVector)

/**
 * Floating tab bar: a rounded surface set in from the screen edges, with a selection pill that slides
 * between tabs on a spring (so a quick second tap redirects it mid-flight instead of waiting).
 *
 * Touch targets are 56 dp tall, the pressed state shows on touch-down, and each tab is exposed to
 * accessibility as a selectable tab. The system navigation bar inset is applied here.
 */
@Composable
fun FloatingTabBar(
    items: List<TabBarItem>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        // Floating glass chrome (tinted, lightly translucent, no blur): see Glass.kt.
        Box(Modifier.glassChrome(RoundedCornerShape(30.dp))) {
            BoxWithConstraints(Modifier.padding(6.dp).widthIn(max = 440.dp).fillMaxWidth()) {
                val itemWidth = maxWidth / items.size
                val indicatorX by animateDpAsState(itemWidth * selectedIndex, Motion.sheet(), label = "tabIndicator")

                Box(
                    Modifier
                        .offset { IntOffset(indicatorX.roundToPx(), 0) }
                        .width(itemWidth)
                        .height(TAB_HEIGHT)
                        .clip(RoundedCornerShape(24.dp))
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f))
                )
                Row(Modifier.selectableGroup(), horizontalArrangement = Arrangement.spacedBy(0.dp)) {
                    items.forEachIndexed { index, item ->
                        TabBarButton(
                            item = item,
                            selected = index == selectedIndex,
                            onClick = { onSelect(index) },
                            modifier = Modifier.width(itemWidth)
                        )
                    }
                }
            }
        }
    }
}

private val TAB_HEIGHT = 56.dp

@Composable
private fun TabBarButton(item: TabBarItem, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val source = remember { MutableInteractionSource() }
    val tint by animateColorAsState(
        if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        Motion.short(), label = "tabTint"
    )
    // The label is small text: on the accent pill the accent colour is under 4.5:1 in the light theme, so a
    // selected label uses the main text colour and the icon, pill and weight carry the selection.
    val labelColor by animateColorAsState(
        if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
        Motion.short(), label = "tabLabel"
    )
    Column(
        modifier
            .height(TAB_HEIGHT)
            .pressScale(source, pressedScale = 0.94f)
            .clip(RoundedCornerShape(24.dp))
            .selectable(
                selected = selected,
                interactionSource = source,
                indication = LocalIndication.current,
                role = Role.Tab,
                onClick = onClick
            ),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(item.icon, contentDescription = null, tint = tint, modifier = Modifier.size(22.dp))
        Spacer(Modifier.height(2.dp))
        // The tab width is fixed, so a long label ("Transactions", "Tranzakciók") shrinks at large
        // font scales instead of being cut off.
        ShrinkToFitText(
            item.label,
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium),
            color = labelColor,
            modifier = Modifier.fillMaxWidth()
        )
    }
}
