package com.ella.music.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun DoubleTapScrollOverlay(
    onDoubleTap: () -> Unit,
    modifier: Modifier = Modifier,
    startPadding: Dp = 56.dp,
    endPadding: Dp = 104.dp
) {
    Box(
        modifier = modifier
            .padding(start = startPadding, end = endPadding)
            .combinedClickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = {},
                onDoubleClick = onDoubleTap
            )
    )
}
