package com.ella.music.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.basic.InfiniteProgressIndicator

@Composable
fun EllaLoadingIndicator(
    modifier: Modifier = Modifier
) {
    InfiniteProgressIndicator(
        modifier = modifier,
        size = 24.dp,
        strokeWidth = 3.dp,
        orbitingDotSize = 5.dp
    )
}
