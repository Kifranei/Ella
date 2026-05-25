package com.ella.music.ui.components

import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.Constraints
import com.ella.music.R
import com.ella.music.data.model.Song
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.shadow.Shadow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.abs
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.draw.clipToBounds
import kotlinx.coroutines.isActive

@Composable
fun MiniPlayer(
    song: Song,
    isPlaying: Boolean,
    progress: Float = 0f,
    lyricText: String? = null,
    lyricTranslation: String? = null,
    lyricProgress: Float = 0f,
    coverRotationEnabled: Boolean = true,
    albumArtUri: Uri? = null,
    loadCoverArt: ((Song) -> Bitmap?)? = null,
    backdrop: Backdrop? = null,
    liquidGlass: Boolean = false,
    onClick: () -> Unit,
    onPlayPause: () -> Unit,
    onSkipPrevious: () -> Unit = {},
    onSkipNext: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val preferEmbeddedCover = song.fileName.substringAfterLast('.', song.path.substringAfterLast('.'))
        .lowercase() in setOf("m4a", "mp4", "alac", "flac", "wav", "aiff", "aif")
    val shouldLoadEmbeddedCover = song.coverUrl.isBlank() &&
        loadCoverArt != null &&
        (albumArtUri == null || preferEmbeddedCover)
    val embeddedCover by produceState<Bitmap?>(initialValue = null, song.id, song.dateModified, song.fileSize, shouldLoadEmbeddedCover) {
        value = if (!shouldLoadEmbeddedCover) {
            null
        } else {
            withContext(Dispatchers.IO) {
                runCatching {
                    CoverLoadLimiter.run { loadCoverArt.invoke(song) }
                }.getOrNull()
            }
        }
    }
    val coverModel = song.coverUrl.takeIf { it.isNotBlank() }
        ?: if (preferEmbeddedCover) embeddedCover ?: albumArtUri else albumArtUri ?: embeddedCover
    val shape = RoundedCornerShape(if (liquidGlass) 24.dp else 0.dp)
    val glassBackdrop = if (liquidGlass) backdrop else null
    val useGlassLayout = liquidGlass
    val isLight = MiuixTheme.colorScheme.background.luminance() > 0.5f
    val surfaceContainer = MiuixTheme.colorScheme.surfaceContainer
    val glassSurface = if (isLight) Color(0xFFF8F8FA).copy(alpha = 0.44f) else Color(0xFF111114).copy(alpha = 0.50f)
    val hasTranslation = !lyricTranslation.isNullOrBlank()
    val primaryText = lyricText ?: song.title
    val secondaryText = when {
        lyricText != null && hasTranslation -> lyricTranslation.orEmpty()
        lyricText != null -> "${song.title} - ${song.artist}"
        else -> song.artist
    }
    val textState = MiniPlayerTextState(
        songId = song.id,
        primary = primaryText,
        secondary = secondaryText,
        showingLyric = lyricText != null,
        scrollSecondary = lyricText != null && hasTranslation
    )
    var transitionDirection by remember { mutableIntStateOf(1) }
    var coverRotation by remember(song.id) { mutableFloatStateOf(0f) }
    val interactionSource = remember { MutableInteractionSource() }

    LaunchedEffect(song.id, isPlaying, coverRotationEnabled) {
        if (!coverRotationEnabled) {
            coverRotation = 0f
            return@LaunchedEffect
        }
        if (!isPlaying) return@LaunchedEffect
        var lastFrameNanos = 0L
        while (isActive) {
            withFrameNanos { frameNanos ->
                if (lastFrameNanos != 0L) {
                    val elapsedMs = (frameNanos - lastFrameNanos) / 1_000_000f
                    coverRotation = (coverRotation + elapsedMs * 360f / 20_000f) % 360f
                }
                lastFrameNanos = frameNanos
            }
        }
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = if (useGlassLayout) 16.dp else 0.dp, vertical = if (useGlassLayout) 6.dp else 0.dp)
            .pointerInput(song.id) {
                var dragAmount = 0f
                detectHorizontalDragGestures(
                    onDragStart = { dragAmount = 0f },
                    onHorizontalDrag = { change, amount ->
                        dragAmount += amount
                        change.consume()
                    },
                    onDragEnd = {
                        if (abs(dragAmount) > 96f) {
                            if (dragAmount < 0f) {
                                transitionDirection = 1
                                onSkipNext()
                            } else {
                                transitionDirection = -1
                                onSkipPrevious()
                            }
                        }
                        dragAmount = 0f
                    },
                    onDragCancel = { dragAmount = 0f }
                )
            }
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
            .then(
                if (glassBackdrop != null) {
                    Modifier
                        .clip(shape)
                        .drawBackdrop(
                            backdrop = glassBackdrop,
                            shape = { shape },
                            effects = {
                                blur(42f.dp.toPx())
                            },
                            highlight = { Highlight.Default.copy(alpha = if (isLight) 0.26f else 0.16f) },
                            shadow = { Shadow.Default.copy(color = Color.Black.copy(alpha = if (isLight) 0.12f else 0.30f)) },
                            onDrawSurface = {
                                drawRect(glassSurface)
                            }
                        )
                } else if (useGlassLayout) {
                    Modifier
                        .clip(shape)
                        .background(glassSurface, shape)
                } else {
                    Modifier.background(surfaceContainer)
                }
            )
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(50.dp),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .graphicsLayer { rotationZ = coverRotation }
                    .clip(CircleShape)
                    .background(MiuixTheme.colorScheme.surface),
                contentAlignment = Alignment.Center
            ) {
                if (coverModel != null) {
                    SafeCoverImage(
                        model = coverModel,
                        contentDescription = null,
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape),
                        contentScale = ContentScale.Crop,
                        sizePx = 128
                    )
                } else {
                    DefaultAlbumCover(modifier = Modifier.size(44.dp))
                }
            }
            CircularProgressRing(
                progress = progress,
                color = MiuixTheme.colorScheme.primary,
                trackColor = MiuixTheme.colorScheme.onSurface.copy(alpha = 0.12f),
                modifier = Modifier.size(50.dp)
            )
        }

        Spacer(modifier = Modifier.width(8.dp))

        AnimatedContent(
            targetState = textState,
            transitionSpec = {
                val direction = transitionDirection
                val outOffset = { width: Int -> -direction * width / 3 }
                val inOffset = { width: Int -> direction * width / 3 }
                val enter = slideInHorizontally(
                    animationSpec = tween(450, easing = FastOutSlowInEasing),
                    initialOffsetX = inOffset
                ) + fadeIn(
                    animationSpec = tween(450, easing = FastOutSlowInEasing),
                    initialAlpha = 0.15f
                )
                val exit = slideOutHorizontally(
                    animationSpec = tween(300, easing = FastOutLinearInEasing),
                    targetOffsetX = outOffset
                ) + fadeOut(
                    animationSpec = tween(300, easing = FastOutLinearInEasing),
                    targetAlpha = 0f
                )
                enter togetherWith exit using SizeTransform(clip = false)
            },
            label = "MiniPlayerSongText",
            modifier = Modifier.weight(1f)
        ) { state ->
            Column(modifier = Modifier.fillMaxWidth()) {
                AutoScrollingMiniText(
                    text = state.primary,
                    fontSize = 14,
                    fontWeight = FontWeight.Medium,
                    color = if (state.showingLyric) {
                        MiuixTheme.colorScheme.primary
                    } else {
                        MiuixTheme.colorScheme.onSurface
                    },
                    enabled = state.showingLyric,
                    progress = lyricProgress
                )

                AutoScrollingMiniText(
                    text = state.secondary,
                    fontSize = 12,
                    fontWeight = FontWeight.Normal,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    enabled = state.scrollSecondary,
                    progress = lyricProgress
                )
            }
        }

        IconButton(
            onClick = {
                transitionDirection = -1
                onSkipPrevious()
            },
            modifier = Modifier.size(36.dp)
        ) {
            Icon(
                painter = painterResource(id = R.drawable.ic_skip_previous),
                contentDescription = "上一首",
                tint = MiuixTheme.colorScheme.onSurface,
                modifier = Modifier.size(20.dp)
            )
        }

        IconButton(
            onClick = onPlayPause,
            modifier = Modifier.size(36.dp)
        ) {
            Icon(
                painter = painterResource(id = if (isPlaying) R.drawable.ic_player_pause else R.drawable.ic_player_play),
                contentDescription = if (isPlaying) "暂停" else "播放",
                tint = MiuixTheme.colorScheme.primary,
                modifier = Modifier.size(22.dp)
            )
        }

        IconButton(
            onClick = {
                transitionDirection = 1
                onSkipNext()
            },
            modifier = Modifier.size(36.dp)
        ) {
            Icon(
                painter = painterResource(id = R.drawable.ic_skip_next),
                contentDescription = "下一首",
                tint = MiuixTheme.colorScheme.onSurface,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
private fun CircularProgressRing(
    progress: Float,
    color: Color,
    trackColor: Color,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier.graphicsLayer { rotationZ = -90f }) {
        val strokeWidth = 2.dp.toPx()
        val inset = strokeWidth / 2f
        val arcSize = size.copy(width = size.width - strokeWidth, height = size.height - strokeWidth)
        drawArc(
            color = trackColor,
            startAngle = 0f,
            sweepAngle = 360f,
            useCenter = false,
            topLeft = androidx.compose.ui.geometry.Offset(inset, inset),
            size = arcSize,
            style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
        )
        drawArc(
            color = color,
            startAngle = 0f,
            sweepAngle = 360f * progress.coerceIn(0f, 1f),
            useCenter = false,
            topLeft = androidx.compose.ui.geometry.Offset(inset, inset),
            size = arcSize,
            style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
        )
    }
}

@Composable
private fun AutoScrollingMiniText(
    text: String,
    fontSize: Int,
    fontWeight: FontWeight,
    color: Color,
    enabled: Boolean,
    progress: Float,
    modifier: Modifier = Modifier
) {
    val safeProgress = progress.coerceIn(0f, 1f)
    var autoScrollElapsedMs by remember(text, enabled) { mutableFloatStateOf(0f) }

    LaunchedEffect(text, enabled) {
        autoScrollElapsedMs = 0f
        if (!enabled) return@LaunchedEffect
        var lastFrameNanos = 0L
        while (isActive) {
            withFrameNanos { frameNanos ->
                if (lastFrameNanos != 0L) {
                    autoScrollElapsedMs += (frameNanos - lastFrameNanos) / 1_000_000f
                }
                lastFrameNanos = frameNanos
            }
        }
    }

    Layout(
        content = {
            Text(
                text = text,
                fontSize = fontSize.sp,
                fontWeight = fontWeight,
                color = color,
                maxLines = 1,
                overflow = TextOverflow.Visible
            )
        },
        modifier = modifier
            .fillMaxWidth()
            .clipToBounds()
    ) { measurables, constraints ->
        val placeable = measurables.first().measure(
            constraints.copy(minWidth = 0, maxWidth = Constraints.Infinity)
        )
        val width = if (constraints.hasBoundedWidth) constraints.maxWidth else placeable.width
        val overflowPx = (placeable.width - width).coerceAtLeast(0)
        val scrollProgress = if (enabled && overflowPx > 0) {
            miniMarqueeProgress(
                progress = safeProgress,
                overflowPx = overflowPx.toFloat(),
                viewportPx = width,
                autoScrollElapsedMs = autoScrollElapsedMs
            )
        } else {
            0f
        }
        val offsetPx = overflowPx * scrollProgress

        layout(width, placeable.height) {
            placeable.placeRelativeWithLayer(0, 0) {
                translationX = -offsetPx
            }
        }
    }
}

private fun miniMarqueeProgress(
    progress: Float,
    overflowPx: Float,
    viewportPx: Int,
    autoScrollElapsedMs: Float
): Float {
    val overflowRatio = overflowPx / viewportPx.coerceAtLeast(1).toFloat()
    val startAt = 0.04f
    val endAt = when {
        overflowRatio >= 1.1f -> 0.76f
        overflowRatio >= 0.55f -> 0.82f
        else -> 0.9f
    }
    val lyricDrivenProgress = ((progress - startAt) / (endAt - startAt)).coerceIn(0f, 1f)
    val autoDelayMs = 420f
    val autoScrollSpeedPxPerSecond = (viewportPx * 0.12f).coerceIn(22f, 42f)
    val autoDrivenProgress = (
        ((autoScrollElapsedMs - autoDelayMs).coerceAtLeast(0f) / 1000f) *
            autoScrollSpeedPxPerSecond /
            overflowPx.coerceAtLeast(1f)
        ).coerceIn(0f, 1f)
    return maxOf(lyricDrivenProgress, autoDrivenProgress)
}

private fun Color.luminance(): Float {
    return 0.2126f * red + 0.7152f * green + 0.0722f * blue
}

private data class MiniPlayerTextState(
    val songId: Long,
    val primary: String,
    val secondary: String,
    val showingLyric: Boolean,
    val scrollSecondary: Boolean
)
