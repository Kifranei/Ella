package com.ella.music.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import top.yukonga.miuix.kmp.basic.InfiniteProgressIndicator

@Composable
fun EllaLoadingIndicator(
    modifier: Modifier = Modifier
) {
    InfiniteProgressIndicator(modifier = modifier)
}

@Composable
fun EllaCenteredLoadingIndicator(
    modifier: Modifier = Modifier.fillMaxSize()
) {
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        EllaLoadingIndicator()
    }
}
