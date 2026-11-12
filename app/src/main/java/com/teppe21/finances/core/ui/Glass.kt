package com.teppe21.finances.core.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp

/** Flat chrome (the tab bar): an opaque surface with a hairline outline, no shadow or transparency. */
@Composable
fun Modifier.glassChrome(shape: Shape): Modifier = this
    .background(MaterialTheme.colorScheme.surface, shape)
    .border(1.dp, MaterialTheme.colorScheme.outline, shape)
    .clip(shape)
