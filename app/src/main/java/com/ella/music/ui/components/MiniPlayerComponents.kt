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
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ella.music.data.model.Song
import kotlinx.coroutines.isActive
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
internal fun MiniPlayerAnimatedText(
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
                highlightWithProgress = state.showingLyric,
                progress = lyricProgress
            )

            AutoScrollingMiniText(
                text = state.secondary,
                fontSize = secondaryFontSize,
                fontWeight = FontWeight.Normal,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                enabled = state.scrollSecondary,
                highlightWithProgress = state.highlightSecondaryWithProgress,
                progress = lyricProgress
            )
        }
    }
}

@Composable
internal fun MiniPlayerCoverProgress(
    coverState: MiniPlayerCoverState,
    isPlaying: Boolean,
    progress: Float,
    coverRotationEnabled: Boolean,
    coverSize: Dp,
    ringSize: Dp,
    modifier: Modifier = Modifier
) {
    val coverModel = coverState.model
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
                    sizePx = 128,
                    showDefaultPlaceholder = false
                )
            } else if (coverState.showDefaultCover) {
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
internal fun rememberMiniPlayerCoverModel(
    song: Song,
    albumArtUri: Uri?,
    loadCoverArt: ((Song) -> Bitmap?)?
): MiniPlayerCoverState {
    val coverState = rememberSongArtworkState(
        song = song,
        albumArtUri = albumArtUri,
        loadCoverArt = loadCoverArt,
        usage = ArtworkUsage.MiniPlayer
    )
    return MiniPlayerCoverState(
        model = coverState.model,
        showDefaultCover = coverState.showDefaultCover
    )
}

internal data class MiniPlayerCoverState(
    val model: Any?,
    val showDefaultCover: Boolean
)

internal fun rememberMiniPlayerTextState(
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
        scrollSecondary = lyricText != null && hasTranslation,
        highlightSecondaryWithProgress = false
    )
}

@Composable
private fun AutoScrollingMiniText(
    text: String,
    fontSize: Int,
    fontWeight: FontWeight,
    color: Color,
    enabled: Boolean,
    highlightWithProgress: Boolean,
    progress: Float,
    modifier: Modifier = Modifier
) {
    val safeProgress = progress.coerceIn(0f, 1f)
    val textMeasurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val highlightedColor = MiuixTheme.colorScheme.onSurface
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

    val textStyle = TextStyle(
        fontSize = fontSize.sp,
        fontWeight = fontWeight
    )
    val textLayout = remember(text, textStyle, textMeasurer) {
        textMeasurer.measure(
            text = AnnotatedString(text),
            style = textStyle,
            maxLines = 1,
            softWrap = false
        )
    }
    val canvasHeight = with(density) { textLayout.size.height.toDp() }
    val horizontalPaddingPx = with(density) { 2.dp.toPx() }
    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(canvasHeight)
            .clipToBounds()
    ) {
        val viewportPx = size.width.toInt().coerceAtLeast(1)
        val textWidth = textLayout.size.width.toFloat()
        val overflowPx = (textWidth - size.width).coerceAtLeast(0f)
        val scrollProgress = if (enabled && overflowPx > 0f) {
            miniMarqueeProgress(
                progress = safeProgress,
                overflowPx = overflowPx,
                viewportPx = viewportPx,
                autoScrollElapsedMs = autoScrollElapsedMs
            )
        } else {
            0f
        }
        val offsetPx = overflowPx * scrollProgress
        val highlightRight = (textWidth * safeProgress - offsetPx + horizontalPaddingPx)
            .coerceIn(0f, size.width)
        val topLeft = Offset(-offsetPx, 0f)

        drawText(
            textLayoutResult = textLayout,
            color = color,
            topLeft = topLeft
        )
        if (enabled && highlightWithProgress && highlightRight > 0f) {
            // Draw a solid sung section, then blend it into the dim copy.  The previous
            // rectangular clip made every word boundary snap hard in the mini player.
            // Once the last word is complete, do not leave the trailing feather visible:
            // its transparent edge would otherwise keep the final glyphs permanently dim.
            val featherWidth = if (safeProgress >= 0.995f) 0f else {
                with(density) { 18.dp.toPx() }.coerceAtMost(highlightRight)
            }
            val solidHighlightRight = (highlightRight - featherWidth).coerceAtLeast(0f)
            clipRect(
                left = 0f,
                top = 0f,
                right = solidHighlightRight,
                bottom = size.height
            ) {
                drawText(
                    textLayoutResult = textLayout,
                    color = highlightedColor,
                    topLeft = topLeft
                )
            }
            if (featherWidth > 0f) {
                clipRect(
                    left = solidHighlightRight,
                    top = 0f,
                    right = highlightRight,
                    bottom = size.height
                ) {
                    drawText(
                        textLayoutResult = textLayout,
                        brush = Brush.horizontalGradient(
                            colorStops = arrayOf(
                                0f to highlightedColor,
                                1f to Color.Transparent
                            ),
                            startX = solidHighlightRight,
                            endX = highlightRight
                        ),
                        topLeft = topLeft
                    )
                }
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

internal data class MiniPlayerTextState(
    val songId: Long,
    val primary: String,
    val secondary: String,
    val showingLyric: Boolean,
    val scrollSecondary: Boolean,
    val highlightSecondaryWithProgress: Boolean
)

@Composable
internal fun QueueListIcon(
    color: Color,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        val stroke = 2.5.dp.toPx()
        val startX = size.width * 0.22f
        val endX = size.width * 0.78f
        listOf(0.30f, 0.50f, 0.70f).forEach { yFraction ->
            drawLine(
                color = color,
                start = androidx.compose.ui.geometry.Offset(startX, size.height * yFraction),
                end = androidx.compose.ui.geometry.Offset(endX, size.height * yFraction),
                strokeWidth = stroke,
                cap = StrokeCap.Round
            )
        }
    }
}
