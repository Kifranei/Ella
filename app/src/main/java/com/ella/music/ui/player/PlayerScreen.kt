package com.ella.music.ui.player

import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import android.net.Uri
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.Player
import com.ella.music.R
import com.ella.music.data.model.Song
import com.ella.music.ui.components.WordLyricView
import com.ella.music.ui.components.SafeCoverImage
import com.ella.music.viewmodel.PlayerViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.max
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.icon.extended.Music
import top.yukonga.miuix.kmp.icon.extended.Pause
import top.yukonga.miuix.kmp.icon.extended.Play
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun PlayerScreen(
    playerViewModel: PlayerViewModel,
    onBack: () -> Unit
) {
    val currentSong by playerViewModel.currentSong.collectAsState()
    val isPlaying by playerViewModel.isPlaying.collectAsState()
    val currentPosition by playerViewModel.currentPosition.collectAsState()
    val duration by playerViewModel.duration.collectAsState()
    val shuffleEnabled by playerViewModel.shuffleEnabled.collectAsState()
    val repeatMode by playerViewModel.repeatMode.collectAsState()
    val lyrics by playerViewModel.lyrics.collectAsState()
    val currentLyricIndex by playerViewModel.currentLyricIndex.collectAsState()
    val showLyrics by playerViewModel.showLyrics.collectAsState()
    val showLyricTranslation by playerViewModel.showLyricTranslation.collectAsState()
    val currentLyricLine = lyrics.getOrNull(currentLyricIndex)

    val song = currentSong
    val embeddedCover by produceState<Bitmap?>(initialValue = null, song?.id) {
        value = withContext(Dispatchers.IO) { song?.let(playerViewModel::getCoverArtBitmap) }
    }
    val palette by produceState(initialValue = PlayerPalette.Default, embeddedCover) {
        value = withContext(Dispatchers.Default) { PlayerPalette.from(embeddedCover) }
    }
    val motionTransition = rememberInfiniteTransition(label = "player_motion")
    val coverMotion by motionTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 4200),
            repeatMode = RepeatMode.Reverse
        ),
        label = "cover_motion"
    )
    val coverScale = if (isPlaying) 1f + coverMotion * 0.018f else 1f
    val coverTranslation = if (isPlaying) -coverMotion * 8f else 0f

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        palette.top,
                        palette.middle,
                        palette.bottom
                    )
                )
            )
    ) {
        PlayerBlurBackground(
            song = song,
            embeddedCover = embeddedCover,
            palette = palette,
            motion = coverMotion,
            isPlaying = isPlaying,
            modifier = Modifier.fillMaxSize()
        )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.statusBars)
            .windowInsetsPadding(WindowInsets.navigationBars),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(12.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.10f))
                    .clickable { onBack() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = MiuixIcons.Regular.Back,
                    contentDescription = "返回",
                    tint = Color.White,
                    modifier = Modifier.size(24.dp)
                )
            }

            Spacer(modifier = Modifier.weight(1f))
            Text(
                text = "正在播放",
                fontSize = 14.sp,
                color = Color.White.copy(alpha = 0.72f)
            )
            Spacer(modifier = Modifier.weight(1f))
            if (showLyrics) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = if (showLyricTranslation) 0.24f else 0.10f))
                        .clickable {
                            playerViewModel.setLyricPageTranslation(!showLyricTranslation)
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "译",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White.copy(alpha = if (showLyricTranslation) 1f else 0.62f)
                    )
                }
            } else {
                Spacer(modifier = Modifier.size(48.dp))
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            contentAlignment = Alignment.Center
        ) {
            AnimatedContent(
                targetState = showLyrics,
                transitionSpec = {
                    (fadeIn() togetherWith fadeOut()).using(SizeTransform(clip = false))
                }
            ) { showLyric ->
                if (showLyric) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .pointerInput(Unit) {
                                var totalDrag = 0f
                                detectDragGestures(
                                    onDragStart = { totalDrag = 0f },
                                    onDrag = { change, dragAmount ->
                                        totalDrag += dragAmount.x
                                        change.consume()
                                    },
                                    onDragEnd = {
                                        if (kotlin.math.abs(totalDrag) > 72f) {
                                            playerViewModel.setShowLyrics(false)
                                        }
                                    }
                                )
                            }
                            .pointerInput(Unit) {
                                detectTapGestures(
                                    onTap = { playerViewModel.setShowLyrics(false) }
                                )
                            }
                    ) {
                        WordLyricView(
                            lyrics = lyrics,
                            currentIndex = currentLyricIndex,
                            currentPositionMs = currentPosition,
                            showTranslation = showLyricTranslation,
                            onLineClick = { line -> playerViewModel.seekTo(line.timeMs) },
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                } else {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth(0.86f)
                            .padding(horizontal = 4.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        AlbumArtView(
                            song = song,
                            embeddedCover = embeddedCover,
                            modifier = Modifier
                                .fillMaxWidth(0.82f)
                                .aspectRatio(1f)
                                .graphicsLayer {
                                    scaleX = coverScale
                                    scaleY = coverScale
                                    translationY = coverTranslation
                                    shadowElevation = if (isPlaying) 18f + coverMotion * 10f else 14f
                                }
                        )
                        if (currentLyricLine != null && currentLyricLine.hasMiniLyric()) {
                            Spacer(modifier = Modifier.height(18.dp))
                            MiniLyricBlock(
                                line = currentLyricLine,
                                showTranslation = showLyricTranslation,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(18.dp))
                                    .clickable { playerViewModel.setShowLyrics(true) }
                                    .padding(horizontal = 8.dp, vertical = 6.dp)
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = song?.title ?: "未在播放",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = song?.artist ?: "",
                fontSize = 14.sp,
                color = Color.White.copy(alpha = 0.66f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 4.dp)
        ) {
            GlowSeekBar(
                value = if (duration > 0) currentPosition.toFloat() / duration.toFloat() else 0f,
                onSeek = { fraction ->
                    playerViewModel.seekTo((fraction * duration).toLong())
                },
                accent = palette.accent,
                modifier = Modifier.fillMaxWidth()
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = formatTime(currentPosition),
                    fontSize = 12.sp,
                    color = Color.White.copy(alpha = 0.58f)
                )
                Text(
                    text = formatTime(duration),
                    fontSize = 12.sp,
                    color = Color.White.copy(alpha = 0.58f)
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = { playerViewModel.toggleShuffle() }) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_shuffle),
                    contentDescription = "随机播放",
                    tint = if (shuffleEnabled) palette.accent else Color.White.copy(alpha = 0.42f),
                    modifier = Modifier.size(22.dp)
                )
            }

            IconButton(onClick = { playerViewModel.skipToPrevious() }) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_skip_previous),
                    contentDescription = "上一首",
                    tint = Color.White,
                    modifier = Modifier.size(32.dp)
                )
            }

            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(CircleShape)
                    .background(palette.accent)
                    .clickable { playerViewModel.togglePlayPause() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (isPlaying) MiuixIcons.Regular.Pause else MiuixIcons.Regular.Play,
                    contentDescription = if (isPlaying) "暂停" else "播放",
                    tint = Color.White,
                    modifier = Modifier.size(30.dp)
                )
            }

            IconButton(onClick = { playerViewModel.skipToNext() }) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_skip_next),
                    contentDescription = "下一首",
                    tint = Color.White,
                    modifier = Modifier.size(32.dp)
                )
            }

            IconButton(onClick = { playerViewModel.toggleRepeat() }) {
                val iconRes = when (repeatMode) {
                    Player.REPEAT_MODE_ONE -> R.drawable.ic_repeat_one
                    else -> R.drawable.ic_repeat
                }
                Icon(
                    painter = painterResource(id = iconRes),
                    contentDescription = "循环模式",
                    tint = if (repeatMode != Player.REPEAT_MODE_OFF) palette.accent else Color.White.copy(alpha = 0.42f),
                    modifier = Modifier.size(22.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
    }
    }
}

@Composable
private fun PlayerBlurBackground(
    song: Song?,
    embeddedCover: Bitmap?,
    palette: PlayerPalette,
    motion: Float,
    isPlaying: Boolean,
    modifier: Modifier = Modifier
) {
    val uri = if ((song?.albumId ?: 0L) > 0) {
        Uri.parse("content://media/external/audio/albumart/${song?.albumId}")
    } else null
    val coverModel = embeddedCover ?: uri
    val rotationTransition = rememberInfiniteTransition(label = "cover_background_rotation")
    val rotation by rotationTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 120_000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "cover_background_rotation"
    )
    val movingScale = if (isPlaying) 2.96f + motion * 0.08f else 2.90f
    val movingOffset = if (isPlaying) (motion - 0.5f) * 10f else 0f

    Box(modifier = modifier.background(palette.middle)) {
        if (coverModel != null) {
            SafeCoverImage(
                model = coverModel,
                contentDescription = null,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        scaleX = movingScale
                        scaleY = movingScale
                        rotationZ = rotation
                        translationX = movingOffset
                        translationY = -movingOffset * 0.65f
                        alpha = 0.78f
                    }
                    .blur(72.dp),
                contentScale = ContentScale.Crop,
                sizePx = 512
            )
        }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.linearGradient(
                        colors = listOf(
                            palette.accent.copy(alpha = 0.28f),
                            palette.top.copy(alpha = 0.42f),
                            Color.Black.copy(alpha = 0.34f)
                        ),
                        start = Offset.Zero,
                        end = Offset.Infinite
                    )
                )
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.White.copy(alpha = 0.06f),
                            Color.Transparent,
                            Color.Black.copy(alpha = 0.32f)
                        )
                    )
                )
        )
    }
}

@Composable
private fun MiniLyricBlock(
    line: com.ella.music.data.model.LyricLine,
    showTranslation: Boolean,
    modifier: Modifier = Modifier
) {
    val longest = listOfNotNull(
        line.text,
        line.translation,
        line.backgroundText,
        line.backgroundTranslation
    ).maxOfOrNull { it.length } ?: 0
    val mainSize = when {
        longest > 72 -> 11.sp
        longest > 54 -> 12.sp
        longest > 38 -> 13.sp
        longest > 26 -> 14.sp
        else -> 16.sp
    }
    val secondarySize = when {
        longest > 72 -> 9.sp
        longest > 54 -> 10.sp
        else -> 12.sp
    }

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        val main = line.text.takeIf { it.isNotBlank() && !it.isMusicSymbolOnly() }
        val background = line.backgroundText?.takeIf { it.isNotBlank() && !it.isMusicSymbolOnly() }
        val translation = line.translation?.takeIf { showTranslation && it.isNotBlank() }
        val backgroundTranslation = line.backgroundTranslation?.takeIf { showTranslation && it.isNotBlank() }

        if (main != null) {
            Text(
                text = main,
                fontSize = mainSize,
                fontWeight = FontWeight.SemiBold,
                color = Color.White.copy(alpha = 0.88f),
                textAlign = TextAlign.Center,
                maxLines = 3,
                modifier = Modifier.fillMaxWidth()
            )
        }
        if (translation != null) {
            Text(
                text = translation,
                fontSize = secondarySize,
                color = Color.White.copy(alpha = 0.58f),
                textAlign = TextAlign.Center,
                maxLines = 2,
                modifier = Modifier.fillMaxWidth()
            )
        }
        if (background != null) {
            Text(
                text = background,
                fontSize = if (mainSize.value <= 12f) 10.sp else 13.sp,
                fontWeight = FontWeight.Medium,
                color = Color.White.copy(alpha = 0.68f),
                textAlign = TextAlign.Center,
                maxLines = 2,
                modifier = Modifier.fillMaxWidth()
            )
        }
        if (backgroundTranslation != null) {
            Text(
                text = backgroundTranslation,
                fontSize = if (secondarySize.value <= 10f) 9.sp else 11.sp,
                color = Color.White.copy(alpha = 0.48f),
                textAlign = TextAlign.Center,
                maxLines = 2,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun AlbumArtView(
    song: Song?,
    embeddedCover: Bitmap?,
    modifier: Modifier = Modifier
) {
    val uri = if ((song?.albumId ?: 0L) > 0) {
        Uri.parse("content://media/external/audio/albumart/${song?.albumId}")
    } else null
    val coverModel = embeddedCover ?: uri

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .background(MiuixTheme.colorScheme.surfaceContainer),
        contentAlignment = Alignment.Center
    ) {
        if (coverModel != null) {
            SafeCoverImage(
                model = coverModel,
                contentDescription = null,
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(20.dp)),
                contentScale = ContentScale.Crop,
                sizePx = 768
            )
        } else {
            Icon(
                imageVector = MiuixIcons.Regular.Music,
                contentDescription = null,
                tint = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                modifier = Modifier.size(64.dp)
            )
        }
    }
}

@Composable
private fun GlowSeekBar(
    value: Float,
    onSeek: (Float) -> Unit,
    accent: Color,
    modifier: Modifier = Modifier
) {
    val safeProgress = value.coerceIn(0f, 1f)
    val shownProgress by animateFloatAsState(
        targetValue = safeProgress,
        animationSpec = tween(durationMillis = 280, easing = FastOutSlowInEasing),
        label = "player_progress"
    )
    val glowTransition = rememberInfiniteTransition(label = "player_progress_glow")
    val glowPulse by glowTransition.animateFloat(
        initialValue = 0.45f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(900, easing = LinearOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glow_pulse"
    )

    fun seek(width: Float, x: Float) {
        onSeek((x / width.coerceAtLeast(1f)).coerceIn(0f, 1f))
    }

    Canvas(
        modifier = modifier
            .height(18.dp)
            .pointerInput(Unit) {
                detectTapGestures { offset -> seek(size.width.toFloat(), offset.x) }
            }
            .pointerInput(Unit) {
                detectDragGestures { change, _ ->
                    seek(size.width.toFloat(), change.position.x)
                }
            }
    ) {
        val trackHeight = 4.dp.toPx()
        val centerY = size.height / 2f
        val radius = trackHeight / 2f
        val progressWidth = size.width * shownProgress
        val visibleProgressWidth = progressWidth.coerceAtLeast(if (shownProgress > 0f) trackHeight else 0f)

        drawRoundRect(
            color = Color.White.copy(alpha = 0.16f),
            topLeft = Offset(0f, centerY - trackHeight / 2f),
            size = Size(size.width, trackHeight),
            cornerRadius = CornerRadius(radius, radius)
        )

        if (visibleProgressWidth <= 0f) return@Canvas

        drawRoundRect(
            brush = Brush.horizontalGradient(
                colors = listOf(
                    accent.copy(alpha = 0.75f),
                    accent,
                    Color.White.copy(alpha = 0.95f)
                ),
                startX = 0f,
                endX = max(visibleProgressWidth, 1f)
            ),
            topLeft = Offset(0f, centerY - trackHeight / 2f),
            size = Size(visibleProgressWidth, trackHeight),
            cornerRadius = CornerRadius(radius, radius)
        )

        val glowRadius = trackHeight * 3.2f
        val headX = visibleProgressWidth.coerceIn(0f, size.width)
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    accent.copy(alpha = 0.75f * glowPulse),
                    accent.copy(alpha = 0.25f * glowPulse),
                    Color.Transparent
                ),
                center = Offset(headX, centerY),
                radius = glowRadius
            ),
            radius = glowRadius,
            center = Offset(headX, centerY),
            blendMode = BlendMode.Screen
        )

        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    Color.White.copy(alpha = 0.80f),
                    Color.White.copy(alpha = 0.18f),
                    Color.Transparent
                ),
                center = Offset(headX, centerY),
                radius = glowRadius * 0.82f
            ),
            radius = glowRadius * 0.82f,
            center = Offset(headX, centerY),
            blendMode = BlendMode.Screen
        )
    }
}

private data class PlayerPalette(
    val top: Color,
    val middle: Color,
    val bottom: Color,
    val accent: Color
) {
    companion object {
        val Default = PlayerPalette(
            top = Color(0xFF171717),
            middle = Color(0xFF0B0B0D),
            bottom = Color.Black,
            accent = Color(0xFF2F7DFF)
        )

        fun from(bitmap: Bitmap?): PlayerPalette {
            if (bitmap == null || bitmap.width <= 0 || bitmap.height <= 0) return Default
            val sampleStep = (minOf(bitmap.width, bitmap.height) / 36).coerceAtLeast(1)
            var red = 0L
            var green = 0L
            var blue = 0L
            var count = 0L

            var y = 0
            while (y < bitmap.height) {
                var x = 0
                while (x < bitmap.width) {
                    val pixel = bitmap.getPixel(x, y)
                    val alpha = AndroidColor.alpha(pixel)
                    if (alpha > 24) {
                        red += AndroidColor.red(pixel)
                        green += AndroidColor.green(pixel)
                        blue += AndroidColor.blue(pixel)
                        count++
                    }
                    x += sampleStep
                }
                y += sampleStep
            }
            if (count == 0L) return Default

            val r = (red / count).toInt()
            val g = (green / count).toInt()
            val b = (blue / count).toInt()
            val accent = Color(r, g, b).boosted()
            return PlayerPalette(
                top = accent.darken(0.58f),
                middle = accent.darken(0.78f),
                bottom = Color.Black,
                accent = accent
            )
        }
    }
}

private fun com.ella.music.data.model.LyricLine.hasMiniLyric(): Boolean {
    return text.takeIf { it.isNotBlank() && !it.isMusicSymbolOnly() } != null ||
        !translation.isNullOrBlank() ||
        backgroundText?.takeIf { it.isNotBlank() && !it.isMusicSymbolOnly() } != null ||
        !backgroundTranslation.isNullOrBlank()
}

private fun String.isMusicSymbolOnly(): Boolean {
    val cleaned = trim()
    if (cleaned.isEmpty()) return true
    return cleaned.all { char ->
        char.isWhitespace() ||
            char in setOf('♪', '♫', '♬', '♩', '♭', '♮', '♯', '☆', '★', '·', '.', '。', '…')
    }
}

private fun Color.darken(amount: Float): Color = Color(
    red = red * (1f - amount),
    green = green * (1f - amount),
    blue = blue * (1f - amount),
    alpha = 1f
)

private fun Color.boosted(): Color {
    val max = maxOf(red, green, blue).coerceAtLeast(0.01f)
    val scale = (0.86f / max).coerceIn(1f, 2.4f)
    return Color(
        red = (red * scale).coerceAtMost(1f),
        green = (green * scale).coerceAtMost(1f),
        blue = (blue * scale).coerceAtMost(1f),
        alpha = 1f
    )
}

private fun formatTime(ms: Long): String {
    if (ms <= 0) return "00:00"
    val totalSeconds = ms / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%02d:%02d".format(minutes, seconds)
}
