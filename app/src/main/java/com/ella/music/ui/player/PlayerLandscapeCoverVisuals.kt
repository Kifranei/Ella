package com.ella.music.ui.player

import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.ella.music.data.model.Song
import com.ella.music.data.model.playlistIdentityKey
import com.ella.music.ui.components.DefaultAlbumCover
import com.ella.music.ui.components.SafeCoverImage
import kotlin.math.abs

@Composable
internal fun LandscapeCoverModeBackground(
    palette: PlayerPalette,
    dynamicCoverSource: DynamicCoverSource? = null,
    embeddedCover: Bitmap? = null,
    paletteBitmap: Bitmap? = null,
    currentPosition: Long,
    isPlaying: Boolean,
    flowEffectMode: Int,
    dynamicFlowEnabled: Boolean,
    visualizerEnabled: Boolean,
    visualizerOpacity: Float = 1f,
    customBackgroundUri: String,
    customBackgroundOpacity: Float = 1f,
    customBackgroundDim: Float = 0.26f,
    beautifulLyricsBackground: Boolean = false,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier.background(palette.middle)) {
        if (dynamicCoverSource?.preferLandscapeBackground == true) {
            DynamicCoverVideo(
                source = dynamicCoverSource,
                isPlaying = isPlaying,
                onPlaybackError = {},
                modifier = Modifier.fillMaxSize(),
                cornerRadiusDp = 0f,
                resizeMode = androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_ZOOM
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.24f))
            )
        } else if (customBackgroundUri.isNotBlank()) {
            PlayerCustomBackground(
                uri = customBackgroundUri,
                imageAlpha = customBackgroundOpacity,
                dimAlpha = customBackgroundDim,
                modifier = Modifier.fillMaxSize()
            )
        } else if (beautifulLyricsBackground) {
            BeautifulLyricsDynamicBackground(
                palette = palette,
                coverBitmap = embeddedCover ?: paletteBitmap,
                positionMs = currentPosition,
                isPlaying = isPlaying,
                animate = true,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            val flowAlpha = if (visualizerEnabled) visualizerOpacity.coerceIn(0f, 1f) else 1f
            AppleCoverFlowBackground(
                coverBitmap = embeddedCover ?: paletteBitmap,
                backgroundColor = palette.middle,
                isDark = !palette.isLight,
                isPlaying = isPlaying,
                animate = dynamicFlowEnabled && !visualizerEnabled,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer { alpha = flowAlpha }
            )
        }
    }
}

@Composable
internal fun LandscapeCoverStack(
    currentSong: Song?,
    embeddedCover: Bitmap?,
    dynamicCoverSource: DynamicCoverSource?,
    isPlaying: Boolean,
    coverItems: List<Pair<Int, Song>>,
    onDynamicCoverFailed: (String) -> Unit,
    coverWidthFraction: Float = 0.30f,
    onCenterCoverClick: (() -> Unit)? = null,
    centerOverlay: (@Composable BoxScope.() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val visibleItems = remember(coverItems) {
        coverItems.sortedByDescending { abs(it.first) }
    }
    BoxWithConstraints(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        val coverSize = minOf(maxHeight * 0.84f, maxWidth * coverWidthFraction).coerceAtLeast(118.dp)
        val maxDistance = visibleItems.maxOfOrNull { abs(it.first) } ?: 0
        val outerScale = (1f - maxDistance * 0.13f).coerceAtLeast(0.58f)
        val horizontalStep = if (maxDistance > 0) {
            (((maxWidth - coverSize * outerScale) / 2f) - 4.dp)
                .coerceAtLeast(126.dp) / maxDistance.toFloat()
        } else {
            126.dp
        }
        visibleItems.forEach { (offsetIndex, itemSong) ->
            val distance = abs(offsetIndex)
            val isCenter = offsetIndex == 0
            val xOffset = horizontalStep * offsetIndex.toFloat()
            val scale = (1f - distance * 0.13f).coerceAtLeast(0.58f)
            val cardAlpha = (1f - distance * 0.14f).coerceAtLeast(0.34f)
            val rotation = -offsetIndex * 13f
            val coverModifier = Modifier
                .size(coverSize)
                .offset(x = xOffset, y = (distance * 8).dp)
                .zIndex(10f - distance)
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    alpha = cardAlpha
                    rotationY = rotation
                    cameraDistance = 18f * density
                }

            Box(
                modifier = if (isCenter && onCenterCoverClick != null) {
                    coverModifier.playerNoIndicationClick(onCenterCoverClick)
                } else {
                    coverModifier
                },
                contentAlignment = Alignment.Center
            ) {
                val isCurrentSongItem = itemSong.playlistIdentityKey() == currentSong?.playlistIdentityKey()
                LandscapeCoverReflection(
                    song = itemSong,
                    embeddedCover = embeddedCover.takeIf { isCenter && isCurrentSongItem },
                    cornerRadius = if (isCenter) 14.dp else 10.dp,
                    alpha = if (isCenter) 0.34f else 0.18f
                )
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .clip(RoundedCornerShape(if (isCenter) 14.dp else 10.dp))
                    .background(Color.White.copy(alpha = 0.10f)),
                contentAlignment = Alignment.Center
            ) {
                val foregroundDynamicCoverSource = dynamicCoverSource
                    ?.takeUnless { it.preferLandscapeBackground }
                if (isCenter && foregroundDynamicCoverSource != null) {
                    DynamicCoverVideo(
                        source = foregroundDynamicCoverSource,
                        isPlaying = isPlaying,
                        onPlaybackError = { onDynamicCoverFailed(foregroundDynamicCoverSource.failureKey) },
                        modifier = Modifier.fillMaxSize(),
                        cornerRadiusDp = if (isCenter) 14f else 10f
                    )
                } else {
                        LandscapeStackCoverImage(
                            song = itemSong,
                            embeddedCover = embeddedCover.takeIf { isCenter && isCurrentSongItem },
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                    if (!isCenter) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color.Black.copy(alpha = 0.16f + distance * 0.05f))
                        )
                    }
                    if (isCenter && centerOverlay != null) {
                        centerOverlay()
                    }
                }
            }
        }
    }
}

@Composable
internal fun BoxScope.LandscapeCoverReflection(
    song: Song,
    embeddedCover: Bitmap?,
    cornerRadius: androidx.compose.ui.unit.Dp,
    alpha: Float
) {
    Box(
        modifier = Modifier
            .matchParentSize()
            .align(Alignment.BottomCenter)
            .graphicsLayer {
                scaleY = -0.34f
                transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0.5f, 1f)
                this.alpha = alpha
            }
            .clip(RoundedCornerShape(cornerRadius))
    ) {
        LandscapeStackCoverImage(
            song = song,
            embeddedCover = embeddedCover,
            modifier = Modifier.fillMaxSize()
        )
        Box(
            modifier = Modifier
                .matchParentSize()
                .background(
                    Brush.verticalGradient(
                        0f to Color.Black,
                        0.52f to Color.Black.copy(alpha = 0.36f),
                        1f to Color.Transparent
                    )
                )
        )
    }
}

@Composable
internal fun LandscapeStackCoverImage(
    song: Song,
    embeddedCover: Bitmap?,
    modifier: Modifier = Modifier
) {
    val uri = if (song.albumId > 0L) {
        Uri.parse("content://media/external/audio/albumart/${song.albumId}")
    } else {
        null
    }
    val coverModel = embeddedCover ?: song.coverUrl.takeIf { it.isNotBlank() } ?: uri
    if (coverModel != null) {
        SafeCoverImage(
            model = coverModel,
            contentDescription = null,
            modifier = modifier,
            contentScale = ContentScale.Fit,
            sizePx = 512,
            showDefaultPlaceholder = false
        )
    } else {
        DefaultAlbumCover(modifier = modifier)
    }
}
