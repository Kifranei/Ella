package com.ella.music.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun ellaPageBackground(): Color {
    val isDark = MiuixTheme.colorScheme.background.luminance() < 0.5f
    return if (isDark) Color(0xFF101014) else Color(0xFFF4F4F7)
}
