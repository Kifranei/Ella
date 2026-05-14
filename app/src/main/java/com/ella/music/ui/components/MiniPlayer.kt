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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Music
import top.yukonga.miuix.kmp.icon.extended.Pause
import top.yukonga.miuix.kmp.icon.extended.Play
import top.yukonga.miuix.kmp.theme.MiuixTheme
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.draw.clipToBounds

@Composable
fun MiniPlayer(
    song: Song,
    isPlaying: Boolean,
    progress: Float = 0f,
    lyricText: String? = null,
    lyricTranslation: String? = null,
    lyricProgress: Float = 0f,
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
    val shouldLoadEmbeddedCover = song.coverUrl.isBlank() && albumArtUri == null && loadCoverArt != null
    val embeddedCover by produceState<Bitmap?>(initialValue = null, song.id, shouldLoadEmbeddedCover) {
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
    val coverModel = song.coverUrl.takeIf { it.isNotBlank() } ?: albumArtUri ?: embeddedCover
    val shape = RoundedCornerShape(if (liquidGlass) 24.dp else 0.dp)
    val glassBackdrop = if (liquidGlass) backdrop else null
    val useGlassLayout = liquidGlass
    val isLight = MiuixTheme.colorScheme.background.luminance() > 0.5f
    val surfaceContainer = MiuixTheme.colorScheme.surfaceContainer
    val glassSurface = if (isLight) Color(0xFFF8F8FA).copy(alpha = 0.48f) else Color(0xFF111114).copy(alpha = 0.58f)
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
                                blur(30f.dp.toPx())
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
                    Icon(
                        imageVector = MiuixIcons.Regular.Music,
                        contentDescription = null,
                        tint = MiuixTheme.colorScheme.primary,
                        modifier = Modifier.size(22.dp)
                    )
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
                imageVector = if (isPlaying) MiuixIcons.Regular.Pause else MiuixIcons.Regular.Play,
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
    val scrollState = rememberScrollState()
    val safeProgress = progress.coerceIn(0f, 1f)

    LaunchedEffect(text, enabled) {
        scrollState.scrollTo(0)
    }

    LaunchedEffect(text, enabled, safeProgress, scrollState.maxValue) {
        if (enabled && scrollState.maxValue > 0) {
            scrollState.scrollTo((scrollState.maxValue * safeProgress).toInt())
        } else {
            scrollState.scrollTo(0)
        }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clipToBounds()
    ) {
        Row(
            modifier = Modifier.horizontalScroll(
                state = scrollState,
                enabled = false
            )
        ) {
            Text(
                text = text,
                fontSize = fontSize.sp,
                fontWeight = fontWeight,
                color = color,
                maxLines = 1,
                overflow = TextOverflow.Visible
            )
        }
    }
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
