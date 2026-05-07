package com.ella.music.ui.player

import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.Player
import coil3.compose.AsyncImage
import com.ella.music.R
import com.ella.music.data.model.Song
import com.ella.music.ui.components.WordLyricView
import com.ella.music.viewmodel.PlayerViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Slider
import top.yukonga.miuix.kmp.basic.Switch
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

    val song = currentSong

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MiuixTheme.colorScheme.surface)
            .windowInsetsPadding(WindowInsets.statusBars)
            .windowInsetsPadding(WindowInsets.navigationBars),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(12.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = MiuixIcons.Regular.Back,
                    contentDescription = "返回",
                    tint = MiuixTheme.colorScheme.onSurface,
                    modifier = Modifier.size(24.dp)
                )
            }
            Spacer(modifier = Modifier.weight(1f))
            Text(
                text = "正在播放",
                fontSize = 14.sp,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary
            )
            Spacer(modifier = Modifier.weight(1f))
            Spacer(modifier = Modifier.size(48.dp))
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .clickable { playerViewModel.toggleLyrics() },
            contentAlignment = Alignment.Center
        ) {
            AnimatedContent(
                targetState = showLyrics,
                transitionSpec = { fadeIn() togetherWith fadeOut() }
            ) { showLyric ->
                if (showLyric) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        WordLyricView(
                            lyrics = lyrics,
                            currentIndex = currentLyricIndex,
                            currentPositionMs = currentPosition,
                            showTranslation = showLyricTranslation,
                            modifier = Modifier.fillMaxSize()
                        )
                        Row(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(top = 8.dp, end = 18.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "翻译",
                                fontSize = 12.sp,
                                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                modifier = Modifier.padding(end = 6.dp)
                            )
                            Switch(
                                checked = showLyricTranslation,
                                onCheckedChange = playerViewModel::setLyricPageTranslation
                            )
                        }
                    }
                } else {
                    AlbumArtView(
                        song = song,
                        loadCoverArt = playerViewModel::getCoverArtBitmap,
                        modifier = Modifier
                            .fillMaxWidth(0.7f)
                            .aspectRatio(1f)
                    )
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
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = song?.artist ?: "",
                fontSize = 14.sp,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        Column(modifier = Modifier.padding(horizontal = 24.dp)) {
            Slider(
                value = if (duration > 0) currentPosition.toFloat() / duration.toFloat() else 0f,
                onValueChange = { fraction ->
                    playerViewModel.seekTo((fraction * duration).toLong())
                },
                modifier = Modifier.fillMaxWidth()
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = formatTime(currentPosition),
                    fontSize = 12.sp,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary
                )
                Text(
                    text = formatTime(duration),
                    fontSize = 12.sp,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary
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
                    tint = if (shuffleEnabled) MiuixTheme.colorScheme.primary
                    else MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    modifier = Modifier.size(22.dp)
                )
            }

            IconButton(onClick = { playerViewModel.skipToPrevious() }) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_skip_previous),
                    contentDescription = "上一首",
                    tint = MiuixTheme.colorScheme.onSurface,
                    modifier = Modifier.size(32.dp)
                )
            }

            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(CircleShape)
                    .background(MiuixTheme.colorScheme.primary)
                    .clickable { playerViewModel.togglePlayPause() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (isPlaying) MiuixIcons.Regular.Pause else MiuixIcons.Regular.Play,
                    contentDescription = if (isPlaying) "暂停" else "播放",
                    tint = MiuixTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(30.dp)
                )
            }

            IconButton(onClick = { playerViewModel.skipToNext() }) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_skip_next),
                    contentDescription = "下一首",
                    tint = MiuixTheme.colorScheme.onSurface,
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
                    tint = if (repeatMode != Player.REPEAT_MODE_OFF) MiuixTheme.colorScheme.primary
                    else MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    modifier = Modifier.size(22.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
private fun AlbumArtView(
    song: Song?,
    loadCoverArt: (Song) -> Bitmap?,
    modifier: Modifier = Modifier
) {
    val embeddedCover by produceState<Bitmap?>(initialValue = null, song?.id) {
        value = withContext(Dispatchers.IO) { song?.let(loadCoverArt) }
    }

    val uri = if ((song?.albumId ?: 0L) > 0) {
        Uri.parse("content://media/external/audio/albumart/${song?.albumId}")
    } else null
    val coverModel = embeddedCover ?: if (song == null) uri else null

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .background(MiuixTheme.colorScheme.surfaceContainer),
        contentAlignment = Alignment.Center
    ) {
        if (coverModel != null) {
            AsyncImage(
                model = coverModel,
                contentDescription = null,
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(20.dp)),
                contentScale = ContentScale.Crop
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

private fun formatTime(ms: Long): String {
    if (ms <= 0) return "00:00"
    val totalSeconds = ms / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%02d:%02d".format(minutes, seconds)
}
