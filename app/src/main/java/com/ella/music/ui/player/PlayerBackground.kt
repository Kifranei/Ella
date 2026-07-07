package com.ella.music.ui.player

import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

@Composable
internal fun ImmersiveCoverBackground(
    palette: PlayerPalette,
    flowEffectMode: Int,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            palette.top.copy(alpha = 0.64f),
                            palette.middle.copy(alpha = 0.58f),
                            palette.bottom.copy(alpha = 0.72f)
                        )
                    )
                )
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.linearGradient(
                        colors = listOf(
                            palette.accent.copy(alpha = 0.20f),
                            Color.Transparent,
                            Color.Black.copy(alpha = 0.18f)
                        ),
                        start = Offset.Zero,
                        end = Offset.Infinite
                    )
                )
        )
    }
}

@Composable
internal fun SharedPlayerPageBackground(
    song: com.ella.music.data.model.Song?,
    embeddedCover: Bitmap?,
    paletteBitmap: Bitmap?,
    palette: PlayerPalette,
    currentPositionMs: Long,
    isPlaying: Boolean,
    playerBackgroundEnabled: Boolean,
    playerBackgroundUri: String,
    playerBackgroundOpacity: Float,
    playerBackgroundDim: Float,
    beautifulLyricsBackground: Boolean,
    dynamicFlowEnabled: Boolean = false,
    useBlurBackground: Boolean,
    modifier: Modifier = Modifier
) {
    val useCustomPlayerBackground = playerBackgroundEnabled && playerBackgroundUri.isNotBlank() && !useBlurBackground
    when {
        useCustomPlayerBackground -> PlayerCustomBackground(
            uri = playerBackgroundUri,
            imageAlpha = playerBackgroundOpacity,
            dimAlpha = playerBackgroundDim,
            modifier = modifier
        )
        beautifulLyricsBackground -> BeautifulLyricsDynamicBackground(
            palette = palette,
            coverBitmap = embeddedCover ?: paletteBitmap,
            positionMs = currentPositionMs,
            isPlaying = isPlaying,
            modifier = modifier
        )
        useBlurBackground -> PlayerBlurBackground(
            song = song,
            embeddedCover = embeddedCover,
            palette = palette,
            motion = 0.42f,
            isPlaying = isPlaying,
            modifier = modifier
        )
        (embeddedCover ?: paletteBitmap) != null -> AppleCoverFlowBackground(
            coverBitmap = embeddedCover ?: paletteBitmap,
            backgroundColor = palette.middle,
            isDark = !palette.isLight,
            isPlaying = isPlaying,
            animate = dynamicFlowEnabled,
            modifier = modifier
        )
        else -> Box(
            modifier = modifier.background(
                Brush.verticalGradient(
                    colors = listOf(
                        palette.top,
                        palette.middle,
                        palette.bottom
                    )
                )
            )
        )
    }
}
