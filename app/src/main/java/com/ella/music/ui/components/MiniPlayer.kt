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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.Constraints
import com.ella.music.R
import com.ella.music.data.BottomBarGlassEffect
import com.ella.music.data.model.Song
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.drawBackdrop
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
    glassEffect: BottomBarGlassEffect = BottomBarGlassEffect.Blur,
    onClick: () -> Unit,
    onPlayPause: () -> Unit,
    onSkipPrevious: () -> Unit = {},
    onSkipNext: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val coverModel = rememberMiniPlayerCoverModel(song, albumArtUri, loadCoverArt)
    val shape = RoundedCornerShape(if (liquidGlass) 32.dp else 0.dp)
    val glassBackdrop = if (liquidGlass) backdrop else null
    val useGlassLayout = liquidGlass
    val isLight = MiuixTheme.colorScheme.background.simpleLuminance() > 0.5f
    val surfaceContainer = MiuixTheme.colorScheme.surfaceContainer
    val glassSurface = bottomBarGlassContainerColor(
        isLight = isLight,
        glassEffect = glassEffect,
        lightAlpha = 0.44f,
        darkAlpha = 0.50f,
        lightLiquidAlpha = 0.34f,
        darkLiquidAlpha = 0.38f
    )
    val textState = rememberMiniPlayerTextState(song, lyricText, lyricTranslation)
    var transitionDirection by remember { mutableIntStateOf(1) }
    val interactionSource = remember { MutableInteractionSource() }

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
                                applyBottomBarGlassEffect(
                                    glassEffect = glassEffect,
                                    blurRadius = 42f,
                                    liquidBlurRadius = 12f
                                )
                            },
                            highlight = {
                                Highlight.Default.copy(
                                    alpha = when (glassEffect) {
                                        BottomBarGlassEffect.Blur -> if (isLight) 0.26f else 0.16f
                                        BottomBarGlassEffect.LiquidGlass -> if (isLight) 0.36f else 0.24f
                                    }
                                )
                            },
                            shadow = {
                                Shadow.Default.copy(
                                    color = Color.Black.copy(
                                        alpha = when (glassEffect) {
                                            BottomBarGlassEffect.Blur -> if (isLight) 0.12f else 0.30f
                                            BottomBarGlassEffect.LiquidGlass -> if (isLight) 0.18f else 0.40f
                                        }
                                    )
                                )
                            },
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
        MiniPlayerCoverProgress(
            coverModel = coverModel,
            isPlaying = isPlaying,
            progress = progress,
            coverRotationEnabled = coverRotationEnabled,
            coverSize = 44.dp,
            ringSize = 50.dp
        )

        Spacer(modifier = Modifier.width(8.dp))

        MiniPlayerAnimatedText(
            textState = textState,
            transitionDirection = transitionDirection,
            lyricProgress = lyricProgress,
            modifier = Modifier.weight(1f)
        )

        IconButton(
            onClick = {
                transitionDirection = -1
                onSkipPrevious()
            },
            modifier = Modifier.size(36.dp)
        ) {
            Icon(
                painter = painterResource(id = R.drawable.ic_skip_previous),
                contentDescription = stringResource(R.string.common_previous),
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
                contentDescription = if (isPlaying) stringResource(R.string.common_pause) else stringResource(R.string.common_play),
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
                contentDescription = stringResource(R.string.common_next),
                tint = MiuixTheme.colorScheme.onSurface,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
fun CompactMiniPlayer(
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
    glassEffect: BottomBarGlassEffect = BottomBarGlassEffect.Blur,
    onClick: () -> Unit,
    onPlayPause: () -> Unit,
    onSkipNext: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val coverModel = rememberMiniPlayerCoverModel(song, albumArtUri, loadCoverArt)
    val textState = rememberMiniPlayerTextState(song, lyricText, lyricTranslation)
    var transitionDirection by remember { mutableIntStateOf(1) }

    GlassPill(
        backdrop = backdrop,
        modifier = modifier.height(64.dp),
        shape = RoundedCornerShape(32.dp),
        glassEffect = glassEffect
    ) {
        Row(
            modifier = Modifier
                .height(64.dp)
                .padding(start = 12.dp, end = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier
                    .weight(1f)
                    .height(64.dp)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onClick
                    ),
                verticalAlignment = Alignment.CenterVertically
            ) {
                MiniPlayerCoverProgress(
                    coverModel = coverModel,
                    isPlaying = isPlaying,
                    progress = progress,
                    coverRotationEnabled = coverRotationEnabled,
                    coverSize = 38.dp,
                    ringSize = 44.dp
                )
                Spacer(modifier = Modifier.width(10.dp))
                MiniPlayerAnimatedText(
                    textState = textState,
                    transitionDirection = transitionDirection,
                    lyricProgress = lyricProgress,
                    modifier = Modifier.weight(1f),
                    primaryFontSize = 14,
                    primaryFontWeight = FontWeight.SemiBold,
                    secondaryFontSize = 12
                )
            }
            IconButton(
                onClick = onPlayPause,
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    painter = painterResource(id = if (isPlaying) R.drawable.ic_player_pause else R.drawable.ic_player_play),
                    contentDescription = if (isPlaying) stringResource(R.string.common_pause) else stringResource(R.string.common_play),
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
                    contentDescription = stringResource(R.string.common_next),
                    tint = MiuixTheme.colorScheme.onSurface,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

@Composable
private fun MiniPlayerAnimatedText(
    textState: MiniPlayerTextState,
    transitionDirection: Int,
    lyricProgress: Float,
    modifier: Modifier = Modifier,
    primaryFontSize: Int = 14,
    primaryFontWeight: FontWeight = FontWeight.Medium,
    secondaryFontSize: Int = 12
) {
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
            modifier = modifier
        ) { state ->
            Column(modifier = Modifier.fillMaxWidth()) {
                AutoScrollingMiniText(
                    text = state.primary,
                    fontSize = primaryFontSize,
                    fontWeight = primaryFontWeight,
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
                    fontSize = secondaryFontSize,
                    fontWeight = FontWeight.Normal,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    enabled = state.scrollSecondary,
                    progress = lyricProgress
                )
            }
        }
}

@Composable
private fun MiniPlayerCoverProgress(
    coverModel: Any?,
    isPlaying: Boolean,
    progress: Float,
    coverRotationEnabled: Boolean,
    coverSize: Dp,
    ringSize: Dp,
    modifier: Modifier = Modifier
) {
    var coverRotation by remember(coverModel) { mutableFloatStateOf(0f) }

    LaunchedEffect(coverModel, isPlaying, coverRotationEnabled) {
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

    Box(
        modifier = modifier.size(ringSize),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(coverSize)
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
                        .size(coverSize)
                        .clip(CircleShape),
                    contentScale = ContentScale.Crop,
                    sizePx = 128
                )
            } else {
                DefaultAlbumCover(modifier = Modifier.size(coverSize))
            }
        }
        CircularProgressRing(
            progress = progress,
            color = MiuixTheme.colorScheme.primary,
            trackColor = MiuixTheme.colorScheme.onSurface.copy(alpha = 0.12f),
            modifier = Modifier.size(ringSize)
        )
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
private fun rememberMiniPlayerCoverModel(
    song: Song,
    albumArtUri: Uri?,
    loadCoverArt: ((Song) -> Bitmap?)?
): Any? {
    val preferEmbeddedCover = song.fileName.substringAfterLast('.', song.path.substringAfterLast('.'))
        .lowercase() in setOf("m4a", "mp4", "alac", "flac", "wav", "aiff", "aif")
    val shouldLoadEmbeddedCover = song.coverUrl.isBlank() &&
        loadCoverArt != null &&
        (albumArtUri == null || preferEmbeddedCover)
    val embeddedCover by produceState<Bitmap?>(
        initialValue = null,
        song.id,
        song.dateModified,
        song.fileSize,
        shouldLoadEmbeddedCover
    ) {
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
    return song.coverUrl.takeIf { it.isNotBlank() }
        ?: if (preferEmbeddedCover) embeddedCover ?: albumArtUri else albumArtUri ?: embeddedCover
}

private fun rememberMiniPlayerTextState(
    song: Song,
    lyricText: String?,
    lyricTranslation: String?
): MiniPlayerTextState {
    val hasTranslation = !lyricTranslation.isNullOrBlank()
    return MiniPlayerTextState(
        songId = song.id,
        primary = lyricText ?: song.title,
        secondary = when {
            lyricText != null && hasTranslation -> lyricTranslation.orEmpty()
            lyricText != null -> "${song.title} - ${song.artist}"
            else -> song.artist
        },
        showingLyric = lyricText != null,
        scrollSecondary = lyricText != null && hasTranslation
    )
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

private data class MiniPlayerTextState(
    val songId: Long,
    val primary: String,
    val secondary: String,
    val showingLyric: Boolean,
    val scrollSecondary: Boolean
)
