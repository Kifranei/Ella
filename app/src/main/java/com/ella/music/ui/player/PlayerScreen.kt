package com.ella.music.ui.player

import android.content.ContentUris
import android.content.Context
import android.app.Activity
import android.app.DownloadManager
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.media.MediaRouter2
import android.content.Intent
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import androidx.compose.runtime.DisposableEffect
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player as Media3Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import android.os.Environment
import android.provider.Settings
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.animateFloat
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
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import androidx.media3.common.Player
import com.ella.music.R
import com.ella.music.data.SettingsManager
import com.ella.music.data.audioQualitySummary
import com.ella.music.data.splitArtistNames
import com.ella.music.data.model.AudioInfo
import com.ella.music.data.model.Song
import com.ella.music.ui.components.LyricView
import com.ella.music.ui.components.WordLyricView
import com.ella.music.ui.components.SafeCoverImage
import com.ella.music.viewmodel.PlayerViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
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
import android.content.ClipData

@Composable
fun PlayerScreen(
    playerViewModel: PlayerViewModel,
    onBack: () -> Unit,
    onNavigateToAlbum: (Long) -> Unit = {},
    onNavigateToArtist: (String) -> Unit = {}
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val settingsManager = remember { SettingsManager(context) }
    val lyricFontPath by settingsManager.lyricFontPath.collectAsState(initial = "")
    val lyricSourceMode by settingsManager.lyricSourceMode.collectAsState(initial = SettingsManager.LYRIC_SOURCE_AUTO)
    val lyricFontFamily = remember(lyricFontPath) { lyricFontPath.toPlayerLyricFontFamily() }
    val currentSong by playerViewModel.currentSong.collectAsState()
    val isPlaying by playerViewModel.isPlaying.collectAsState()
    val currentPosition by playerViewModel.currentPosition.collectAsState()
    val duration by playerViewModel.duration.collectAsState()
    val shuffleEnabled by playerViewModel.shuffleEnabled.collectAsState()
    val repeatMode by playerViewModel.repeatMode.collectAsState()
    val playbackSpeed by playerViewModel.playbackSpeed.collectAsState()
    val playbackPitch by playerViewModel.playbackPitch.collectAsState()
    val playlist by playerViewModel.playlist.collectAsState()
    val lyrics by playerViewModel.lyrics.collectAsState()
    val currentLyricIndex by playerViewModel.currentLyricIndex.collectAsState()
    val showLyrics by playerViewModel.showLyrics.collectAsState()
    val showLyricTranslation by playerViewModel.showLyricTranslation.collectAsState()
    val showLyricPronunciation by playerViewModel.showLyricPronunciation.collectAsState()
    val currentLyricLine = lyrics.getOrNull(currentLyricIndex)
    val miniLyricLine = currentLyricLine
        ?.takeIf { it.hasMiniLyric() }
        ?: lyrics.firstOrNull { it.hasMiniLyric() }
    var menuExpanded by remember { mutableStateOf(false) }
    var queueExpanded by remember { mutableStateOf(false) }
    var landscapeExpanded by rememberSaveable { mutableStateOf(false) }
    var dynamicCoverFailedPath by remember { mutableStateOf<String?>(null) }
    var dragDismissOffset by remember { mutableFloatStateOf(0f) }
    val animatedDismissOffset by animateFloatAsState(
        targetValue = dragDismissOffset,
        animationSpec = tween(durationMillis = 160, easing = FastOutSlowInEasing),
        label = "player_dismiss_offset"
    )
    val topDragLimitPx = with(density) { 132.dp.toPx() }
    val dismissThresholdPx = with(density) { 112.dp.toPx() }
    val dragCornerRadius by animateDpAsState(
        targetValue = if (dragDismissOffset > 1f) 28.dp else 0.dp,
        animationSpec = tween(durationMillis = 160, easing = FastOutSlowInEasing),
        label = "player_drag_corner_radius"
    )

    val song = currentSong
    val embeddedCover by produceState<Bitmap?>(initialValue = null, song?.id) {
        value = if (song?.coverUrl?.isNotBlank() == true) {
            null
        } else {
            withContext(Dispatchers.IO) { song?.let(playerViewModel::getCoverArtBitmap) }
        }
    }
    val palette by produceState(initialValue = PlayerPalette.Default, embeddedCover) {
        value = withContext(Dispatchers.Default) { PlayerPalette.from(embeddedCover) }
    }
    val audioInfo by produceState<AudioInfo?>(initialValue = null, song?.id) {
        value = withContext(Dispatchers.IO) { song?.let(playerViewModel::getAudioInfo) }
    }
    BackHandler { onBack() }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer {
                translationY = animatedDismissOffset
                alpha = (1f - animatedDismissOffset / (dismissThresholdPx * 2.8f))
                    .coerceIn(0.72f, 1f)
            }
            .clip(
                RoundedCornerShape(
                    topStart = dragCornerRadius,
                    topEnd = dragCornerRadius
                )
            )
            .pointerInput(showLyrics) {
                var closeGesture = false
                detectDragGestures(
                    onDragStart = { offset ->
                        closeGesture = offset.y <= topDragLimitPx
                    },
                    onDrag = { change, dragAmount ->
                        if (closeGesture) {
                            val nextOffset = (dragDismissOffset + dragAmount.y).coerceAtLeast(0f)
                            dragDismissOffset = nextOffset
                            if (nextOffset > 0f) change.consume()
                        }
                    },
                    onDragCancel = {
                        closeGesture = false
                        dragDismissOffset = 0f
                    },
                    onDragEnd = {
                        closeGesture = false
                        if (dragDismissOffset >= dismissThresholdPx) {
                            onBack()
                        } else {
                            dragDismissOffset = 0f
                        }
                    }
                )
            }
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
        ImmersiveCoverBackground(palette = palette, modifier = Modifier.fillMaxSize())

        AnimatedContent(
            targetState = showLyrics,
            transitionSpec = {
                (fadeIn(animationSpec = tween(220)) togetherWith fadeOut(animationSpec = tween(180)))
                    .using(SizeTransform(clip = true))
            },
            modifier = Modifier.fillMaxSize()
        ) { showLyric ->
            if (showLyric) {
                LyricsPlayerPage(
                    song = song,
                    embeddedCover = embeddedCover,
                    lyrics = lyrics,
                    currentLyricIndex = currentLyricIndex,
                    currentPosition = currentPosition,
                    showTranslation = showLyricTranslation,
                    showPronunciation = showLyricPronunciation,
                    lyricSourceMode = lyricSourceMode,
                    fontFamily = lyricFontFamily,
                    palette = palette,
                    currentPositionMs = currentPosition,
                    isPlaying = isPlaying,
                    onLineClick = { line -> playerViewModel.seekTo(line.timeMs) },
                    onDismissLyrics = { playerViewModel.setShowLyrics(false) },
                    onTogglePronunciation = {
                        playerViewModel.setLyricPagePronunciation(!showLyricPronunciation)
                    },
                    onToggleTranslation = {
                        playerViewModel.setLyricPageTranslation(!showLyricTranslation)
                    },
                    onLyricSourceMode = { mode ->
                        playerViewModel.setLyricSourceMode(mode)
                    },
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                CoverPlayerPage(
                    context = context,
                    song = song,
                    embeddedCover = embeddedCover,
                    dynamicCoverFailedPath = dynamicCoverFailedPath,
                    isPlaying = isPlaying,
                    currentPosition = currentPosition,
                    duration = duration,
                    shuffleEnabled = shuffleEnabled,
                    repeatMode = repeatMode,
                    audioInfo = audioInfo,
                    palette = palette,
                    lyrics = lyrics,
                    currentLyricIndex = currentLyricIndex,
                    miniLyricLine = miniLyricLine,
                    showTranslation = showLyricTranslation,
                    showPronunciation = showLyricPronunciation,
                    fontFamily = lyricFontFamily,
                    menuExpanded = menuExpanded,
                    queueExpanded = queueExpanded,
                    playlist = playlist,
                    onDynamicCoverFailed = { dynamicCoverFailedPath = it },
                    onToggleMenu = { menuExpanded = !menuExpanded },
                    onDismissMenu = { menuExpanded = false },
                    onToggleQueue = { queueExpanded = !queueExpanded },
                    onDismissQueue = { queueExpanded = false },
                    onShowLyrics = { playerViewModel.setShowLyrics(true) },
                    onSeek = { fraction -> playerViewModel.seekTo((fraction * duration).toLong()) },
                    onCyclePlaybackMode = { playerViewModel.cyclePlaybackMode() },
                    onPrevious = { playerViewModel.skipToPrevious() },
                    onPlayPause = { playerViewModel.togglePlayPause() },
                    onNext = { playerViewModel.skipToNext() },
                    onQueueSongClick = { index ->
                        queueExpanded = false
                        playerViewModel.playQueueIndex(index)
                    },
                    onClearQueue = {
                        queueExpanded = false
                        playerViewModel.clearPlaylist()
                    },
                    onAlbum = {
                        menuExpanded = false
                        val albumId = song?.albumId ?: 0L
                        if (albumId > 0L) onNavigateToAlbum(albumId)
                        else Toast.makeText(context, "这首歌没有可跳转的专辑信息", Toast.LENGTH_SHORT).show()
                    },
                    onArtist = {
                        menuExpanded = false
                        val artist = splitArtistNames(song?.artist.orEmpty()).firstOrNull().orEmpty()
                        if (artist.isNotBlank() && !artist.equals("Unknown", ignoreCase = true)) {
                            onNavigateToArtist(artist)
                        } else {
                            Toast.makeText(context, "这首歌没有可跳转的歌手信息", Toast.LENGTH_SHORT).show()
                        }
                    },
                    onEditTags = {
                        menuExpanded = false
                        val current = song
                        if (current != null) openExternalTagEditor(context, current)
                    },
                    onDownload = {
                        menuExpanded = false
                        val current = song
                        if (current != null) {
                            enqueuePlayerDownload(context, current)
                            Toast.makeText(context, "已开始下载到 Music/Ella", Toast.LENGTH_SHORT).show()
                        }
                    },
                    onLandscape = {
                        menuExpanded = false
                        landscapeExpanded = true
                    },
                    onStopAfterCurrent = {
                        menuExpanded = false
                        playerViewModel.stopAfterCurrentSong()
                        Toast.makeText(context, "当前歌曲播放完后暂停", Toast.LENGTH_SHORT).show()
                    },
                    onTimer = { minutes ->
                        menuExpanded = false
                        playerViewModel.startSleepTimer(minutes)
                        Toast.makeText(context, "${minutes} 分钟后暂停播放", Toast.LENGTH_SHORT).show()
                    },
                    onCancelTimer = {
                        menuExpanded = false
                        playerViewModel.cancelSleepTimer()
                        Toast.makeText(context, "已取消定时播放", Toast.LENGTH_SHORT).show()
                    },
                    onSpeed = {
                        playerViewModel.setPlaybackSpeed(it)
                    },
                    onPitch = {
                        playerViewModel.setPlaybackPitch(it)
                    },
                    playbackSpeed = playbackSpeed,
                    playbackPitch = playbackPitch,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }

        if (landscapeExpanded) {
            LandscapeLyricsOverlay(
                song = song,
                embeddedCover = embeddedCover,
                lyrics = lyrics,
                currentLyricIndex = currentLyricIndex,
                currentPosition = currentPosition,
                showTranslation = showLyricTranslation,
                showPronunciation = showLyricPronunciation,
                fontFamily = lyricFontFamily,
                palette = palette,
                isPlaying = isPlaying,
                onLineClick = { line -> playerViewModel.seekTo(line.timeMs) },
                onDismiss = { landscapeExpanded = false },
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

@Composable
private fun CoverPlayerPage(
    context: Context,
    song: Song?,
    embeddedCover: Bitmap?,
    dynamicCoverFailedPath: String?,
    isPlaying: Boolean,
    currentPosition: Long,
    duration: Long,
    shuffleEnabled: Boolean,
    repeatMode: Int,
    audioInfo: AudioInfo?,
    palette: PlayerPalette,
    lyrics: List<com.ella.music.data.model.LyricLine>,
    currentLyricIndex: Int,
    miniLyricLine: com.ella.music.data.model.LyricLine?,
    showTranslation: Boolean,
    showPronunciation: Boolean,
    fontFamily: FontFamily?,
    menuExpanded: Boolean,
    queueExpanded: Boolean,
    playlist: List<Song>,
    playbackSpeed: Float,
    playbackPitch: Float,
    onDynamicCoverFailed: (String) -> Unit,
    onToggleMenu: () -> Unit,
    onDismissMenu: () -> Unit,
    onToggleQueue: () -> Unit,
    onDismissQueue: () -> Unit,
    onShowLyrics: () -> Unit,
    onSeek: (Float) -> Unit,
    onCyclePlaybackMode: () -> Unit,
    onPrevious: () -> Unit,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onQueueSongClick: (Int) -> Unit,
    onClearQueue: () -> Unit,
    onAlbum: () -> Unit,
    onArtist: () -> Unit,
    onEditTags: () -> Unit,
    onDownload: () -> Unit,
    onLandscape: () -> Unit,
    onStopAfterCurrent: () -> Unit,
    onTimer: (Int) -> Unit,
    onCancelTimer: () -> Unit,
    onSpeed: (Float) -> Unit,
    onPitch: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    val bluetoothDeviceName = rememberBluetoothOutputName()
    val dynamicCoverFile = song
        ?.dynamicCoverVideoFile(context)
        ?.takeUnless { it.absolutePath == dynamicCoverFailedPath }

    BoxWithConstraints(modifier = modifier) {
        val useWidePlayer = maxWidth > maxHeight && maxWidth >= 700.dp
        if (useWidePlayer) {
            LandscapeCoverPlayerPage(
                song = song,
                embeddedCover = embeddedCover,
                dynamicCoverFile = dynamicCoverFile,
                isPlaying = isPlaying,
                currentPosition = currentPosition,
                duration = duration,
                shuffleEnabled = shuffleEnabled,
                repeatMode = repeatMode,
                audioInfo = audioInfo,
                palette = palette,
                lyrics = lyrics,
                currentLyricIndex = currentLyricIndex,
                showTranslation = showTranslation,
                showPronunciation = showPronunciation,
                fontFamily = fontFamily,
                queueExpanded = queueExpanded,
                playlist = playlist,
                onDynamicCoverFailed = onDynamicCoverFailed,
                onToggleMenu = onToggleMenu,
                onToggleQueue = onToggleQueue,
                onDismissQueue = onDismissQueue,
                onShowLyrics = onShowLyrics,
                onSeek = onSeek,
                onCyclePlaybackMode = onCyclePlaybackMode,
                onPrevious = onPrevious,
                onPlayPause = onPlayPause,
                onNext = onNext,
                onQueueSongClick = onQueueSongClick,
                onClearQueue = onClearQueue,
                onLineClick = onShowLyrics,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .clickable(onClick = onShowLyrics),
                contentAlignment = Alignment.Center
            ) {
                if (dynamicCoverFile != null) {
                    DynamicCoverVideo(
                        file = dynamicCoverFile,
                        isPlaying = isPlaying,
                        onPlaybackError = { onDynamicCoverFailed(dynamicCoverFile.absolutePath) },
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    FullBleedCover(song = song, embeddedCover = embeddedCover, modifier = Modifier.fillMaxSize())
                }
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    Color.White.copy(alpha = 0.18f),
                                    Color.White.copy(alpha = 0.06f),
                                    Color.White.copy(alpha = 0.16f)
                                )
                            )
                        )
                )
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .height(220.dp)
                        .background(
                            Brush.verticalGradient(
                                colorStops = arrayOf(
                                    0.0f to Color.Transparent,
                                    0.48f to palette.middle.copy(alpha = 0.42f),
                                    0.78f to palette.middle.copy(alpha = 0.86f),
                                    1.0f to palette.middle
                                )
                            )
                        )
                )
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .background(
                        Brush.verticalGradient(
                            colorStops = arrayOf(
                                0.0f to palette.middle.copy(alpha = 0.86f),
                                0.16f to palette.middle,
                                1.0f to palette.middle
                            )
                        )
                    )
                    .windowInsetsPadding(WindowInsets.navigationBars)
                    .padding(horizontal = 28.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = song?.title ?: "未在播放",
                            fontSize = 28.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color.White.copy(alpha = 0.96f),
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = song?.artist.orEmpty(),
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White.copy(alpha = 0.54f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    PlayerHeaderAction(kind = PlayerHeaderActionKind.Favorite, onClick = {})
                    PlayerHeaderAction(kind = PlayerHeaderActionKind.More, onClick = onToggleMenu)
                }

                if (miniLyricLine != null) {
                    Spacer(modifier = Modifier.height(14.dp))
                    MiniLyricsPreview(
                        lyrics = lyrics,
                        currentIndex = currentLyricIndex,
                        showTranslation = showTranslation,
                        showPronunciation = showPronunciation,
                        fontFamily = fontFamily,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 96.dp, max = 150.dp)
                            .clickable(onClick = onShowLyrics)
                            .padding(vertical = 6.dp)
                    )
                }

                Spacer(modifier = Modifier.weight(1f))
                PlayerProgressBlock(
                    currentPosition = currentPosition,
                    duration = duration,
                    audioInfo = audioInfo,
                    bluetoothDeviceName = bluetoothDeviceName,
                    palette = palette,
                    onSeek = onSeek
                )
                Spacer(modifier = Modifier.height(18.dp))
                PlayerTransportControls(
                    isPlaying = isPlaying,
                    shuffleEnabled = shuffleEnabled,
                    repeatMode = repeatMode,
                    palette = palette,
                    queueExpanded = queueExpanded,
                    playlist = playlist,
                    currentSongId = song?.id,
                    onCyclePlaybackMode = onCyclePlaybackMode,
                    onPrevious = onPrevious,
                    onPlayPause = onPlayPause,
                    onNext = onNext,
                    onToggleQueue = onToggleQueue,
                    onDismissQueue = onDismissQueue,
                    onQueueSongClick = onQueueSongClick,
                    onClearQueue = onClearQueue
                )
                SimpleAudioVisualizer(
                    isPlaying = isPlaying,
                    positionMs = currentPosition,
                    accent = Color.White.copy(alpha = 0.86f),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(42.dp)
                )
            }
            }
        }

        if (menuExpanded) {
            Popup(
                alignment = Alignment.BottomCenter,
                onDismissRequest = onDismissMenu,
                properties = PopupProperties(focusable = true, dismissOnBackPress = true, dismissOnClickOutside = true)
            ) {
                PlayerActionMenu(
                    modifier = Modifier.fillMaxWidth(),
                    song = song,
                    speed = playbackSpeed,
                    pitch = playbackPitch,
                    onAlbum = onAlbum,
                    onArtist = onArtist,
                    onEditTags = onEditTags,
                    onDownload = onDownload,
                    onLandscape = onLandscape,
                    onStopAfterCurrent = onStopAfterCurrent,
                    onTimer = onTimer,
                    onCancelTimer = onCancelTimer,
                    onSpeed = onSpeed,
                    onPitch = onPitch
                )
            }
        }
    }
}

@Composable
private fun LandscapeCoverPlayerPage(
    song: Song?,
    embeddedCover: Bitmap?,
    dynamicCoverFile: File?,
    isPlaying: Boolean,
    currentPosition: Long,
    duration: Long,
    shuffleEnabled: Boolean,
    repeatMode: Int,
    audioInfo: AudioInfo?,
    palette: PlayerPalette,
    lyrics: List<com.ella.music.data.model.LyricLine>,
    currentLyricIndex: Int,
    showTranslation: Boolean,
    showPronunciation: Boolean,
    fontFamily: FontFamily?,
    queueExpanded: Boolean,
    playlist: List<Song>,
    onDynamicCoverFailed: (String) -> Unit,
    onToggleMenu: () -> Unit,
    onToggleQueue: () -> Unit,
    onDismissQueue: () -> Unit,
    onShowLyrics: () -> Unit,
    onSeek: (Float) -> Unit,
    onCyclePlaybackMode: () -> Unit,
    onPrevious: () -> Unit,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onQueueSongClick: (Int) -> Unit,
    onClearQueue: () -> Unit,
    onLineClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val bluetoothDeviceName = rememberBluetoothOutputName()
    Box(modifier = modifier.background(palette.middle)) {
        FluidLyricBackground(
            palette = palette,
            positionMs = currentPosition,
            isPlaying = isPlaying,
            modifier = Modifier.fillMaxSize()
        )
        Row(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.statusBars)
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(horizontal = 32.dp, vertical = 22.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .weight(0.62f),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight(0.70f)
                        .aspectRatio(1f)
                        .clip(RoundedCornerShape(14.dp))
                        .clickable(onClick = onShowLyrics),
                    contentAlignment = Alignment.Center
                ) {
                    if (dynamicCoverFile != null) {
                        DynamicCoverVideo(
                            file = dynamicCoverFile,
                            isPlaying = isPlaying,
                            onPlaybackError = { onDynamicCoverFailed(dynamicCoverFile.absolutePath) },
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        AlbumArtView(
                            song = song,
                            embeddedCover = embeddedCover,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.width(28.dp))
            Column(
                modifier = Modifier
                    .fillMaxHeight()
                    .weight(1.38f),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = song?.title ?: "未在播放",
                            fontSize = 24.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color.White.copy(alpha = 0.96f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = song?.artist.orEmpty(),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White.copy(alpha = 0.56f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    PlayerHeaderAction(kind = PlayerHeaderActionKind.More, onClick = onToggleMenu)
                }
                Spacer(modifier = Modifier.height(8.dp))
                LyricView(
                    lyrics = lyrics,
                    currentIndex = currentLyricIndex,
                    showTranslation = showTranslation,
                    showPronunciation = showPronunciation,
                    fontFamily = fontFamily,
                    usePlayerColors = true,
                    topSpacer = 18.dp,
                    bottomSpacer = 64.dp,
                    onLineClick = { onLineClick() },
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                )
                PlayerProgressBlock(
                    currentPosition = currentPosition,
                    duration = duration,
                    audioInfo = audioInfo,
                    bluetoothDeviceName = bluetoothDeviceName,
                    palette = palette,
                    onSeek = onSeek
                )
                Spacer(modifier = Modifier.height(8.dp))
                PlayerTransportControls(
                    isPlaying = isPlaying,
                    shuffleEnabled = shuffleEnabled,
                    repeatMode = repeatMode,
                    palette = palette,
                    queueExpanded = queueExpanded,
                    playlist = playlist,
                    currentSongId = song?.id,
                    onCyclePlaybackMode = onCyclePlaybackMode,
                    onPrevious = onPrevious,
                    onPlayPause = onPlayPause,
                    onNext = onNext,
                    onToggleQueue = onToggleQueue,
                    onDismissQueue = onDismissQueue,
                    onQueueSongClick = onQueueSongClick,
                    onClearQueue = onClearQueue
                )
            }
        }
        SimpleAudioVisualizer(
            isPlaying = isPlaying,
            positionMs = currentPosition,
            accent = Color.White.copy(alpha = 0.72f),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(38.dp)
        )
    }
}

@Composable
private fun LyricsPlayerPage(
    song: Song?,
    embeddedCover: Bitmap?,
    lyrics: List<com.ella.music.data.model.LyricLine>,
    currentLyricIndex: Int,
    currentPosition: Long,
    showTranslation: Boolean,
    showPronunciation: Boolean,
    lyricSourceMode: Int,
    fontFamily: FontFamily?,
    palette: PlayerPalette,
    currentPositionMs: Long,
    isPlaying: Boolean,
    onLineClick: (com.ella.music.data.model.LyricLine) -> Unit,
    onDismissLyrics: () -> Unit,
    onTogglePronunciation: () -> Unit,
    onToggleTranslation: () -> Unit,
    onLyricSourceMode: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    var lyricMenuExpanded by remember { mutableStateOf(false) }

    Box(modifier = modifier.background(palette.middle)) {
        FluidLyricBackground(
            palette = palette,
            positionMs = currentPositionMs,
            isPlaying = isPlaying,
            modifier = Modifier.fillMaxSize()
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.statusBars)
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(horizontal = 28.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 28.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                SmallCover(
                    song = song,
                    embeddedCover = embeddedCover,
                    modifier = Modifier
                        .size(66.dp)
                        .clickable(onClick = onDismissLyrics)
                )
                Spacer(modifier = Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = song?.title ?: "未在播放",
                        fontSize = 28.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color.White.copy(alpha = 0.96f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = song?.artist.orEmpty(),
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White.copy(alpha = 0.72f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                PlayerHeaderAction(kind = PlayerHeaderActionKind.Favorite, onClick = {})
                PlayerHeaderAction(kind = PlayerHeaderActionKind.More, onClick = { lyricMenuExpanded = true })
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .pointerInput(Unit) {
                        var totalDrag = 0f
                        detectDragGestures(
                            onDragStart = { totalDrag = 0f },
                            onDrag = { change, dragAmount ->
                                totalDrag += dragAmount.x
                                change.consume()
                            },
                            onDragEnd = {
                                if (kotlin.math.abs(totalDrag) > 72f) onDismissLyrics()
                            }
                        )
                    }
            ) {
                LyricView(
                    lyrics = lyrics,
                    currentIndex = currentLyricIndex,
                    showTranslation = showTranslation,
                    showPronunciation = showPronunciation,
                    fontFamily = fontFamily,
                    usePlayerColors = true,
                    topSpacer = 112.dp,
                    bottomSpacer = 220.dp,
                    onLineClick = onLineClick,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }

        SimpleAudioVisualizer(
            isPlaying = isPlaying,
            positionMs = currentPositionMs,
            accent = Color.White.copy(alpha = 0.86f),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(58.dp)
        )

        if (lyricMenuExpanded) {
            Popup(
                alignment = Alignment.BottomCenter,
                onDismissRequest = { lyricMenuExpanded = false },
                properties = PopupProperties(focusable = true, dismissOnBackPress = true, dismissOnClickOutside = true)
            ) {
                LyricActionMenu(
                    showPronunciation = showPronunciation,
                    showTranslation = showTranslation,
                    lyricSourceMode = lyricSourceMode,
                    onTogglePronunciation = {
                        lyricMenuExpanded = false
                        onTogglePronunciation()
                    },
                    onToggleTranslation = {
                        lyricMenuExpanded = false
                        onToggleTranslation()
                    },
                    onLyricSourceMode = { mode ->
                        lyricMenuExpanded = false
                        onLyricSourceMode(mode)
                    },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

@Composable
private fun LandscapeLyricsOverlay(
    song: Song?,
    embeddedCover: Bitmap?,
    lyrics: List<com.ella.music.data.model.LyricLine>,
    currentLyricIndex: Int,
    currentPosition: Long,
    showTranslation: Boolean,
    showPronunciation: Boolean,
    fontFamily: FontFamily?,
    palette: PlayerPalette,
    isPlaying: Boolean,
    onLineClick: (com.ella.music.data.model.LyricLine) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val activity = LocalContext.current.findActivity()
    DisposableEffect(activity) {
        val oldOrientation = activity?.requestedOrientation
        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        onDispose {
            if (oldOrientation != null) {
                activity.requestedOrientation = oldOrientation
            }
        }
    }
    BackHandler(onBack = onDismiss)
    Box(modifier = modifier.background(palette.middle)) {
        FluidLyricBackground(
            palette = palette,
            positionMs = currentPosition,
            isPlaying = isPlaying,
            modifier = Modifier.fillMaxSize()
        )
        Row(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.statusBars)
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(horizontal = 30.dp, vertical = 20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .weight(0.48f)
                    .widthIn(max = 292.dp),
                contentAlignment = Alignment.Center
            ) {
                AlbumArtView(
                    song = song,
                    embeddedCover = embeddedCover,
                    modifier = Modifier
                        .fillMaxHeight(0.68f)
                        .aspectRatio(1f)
                        .clip(RoundedCornerShape(12.dp))
                )
            }
            Spacer(modifier = Modifier.width(24.dp))
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .weight(1.52f)
            ) {
                Column(modifier = Modifier.fillMaxSize()) {
                    Text(
                        text = song?.title ?: "Ella Music",
                        fontSize = 22.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color.White.copy(alpha = 0.96f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = song?.artist.orEmpty(),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White.copy(alpha = 0.48f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    LyricView(
                        lyrics = lyrics,
                        currentIndex = currentLyricIndex,
                        showTranslation = showTranslation,
                        showPronunciation = showPronunciation,
                        fontFamily = fontFamily,
                        usePlayerColors = true,
                        topSpacer = 36.dp,
                        bottomSpacer = 96.dp,
                        onLineClick = onLineClick,
                        modifier = Modifier.fillMaxSize()
                    )
                }
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .size(42.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.12f))
                        .clickable(onClick = onDismiss),
                    contentAlignment = Alignment.Center
                ) {
                    CloseIcon(
                        color = Color.White.copy(alpha = 0.92f),
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
        SimpleAudioVisualizer(
            isPlaying = isPlaying,
            positionMs = currentPosition,
            accent = Color.White.copy(alpha = 0.82f),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(54.dp)
        )
    }

}

@Composable
private fun FullBleedCover(
    song: Song?,
    embeddedCover: Bitmap?,
    modifier: Modifier = Modifier
) {
    val uri = if ((song?.albumId ?: 0L) > 0) Uri.parse("content://media/external/audio/albumart/${song?.albumId}") else null
    val coverModel = embeddedCover ?: song?.coverUrl?.takeIf { it.isNotBlank() } ?: uri
    Box(modifier = modifier.background(Color.Black), contentAlignment = Alignment.Center) {
        if (coverModel != null) {
            SafeCoverImage(
                model = coverModel,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit,
                sizePx = 768
            )
        } else {
            Icon(
                imageVector = MiuixIcons.Regular.Music,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.48f),
                modifier = Modifier.size(72.dp)
            )
        }
    }
}

@Composable
private fun SmallCover(song: Song?, embeddedCover: Bitmap?, modifier: Modifier = Modifier) {
    AlbumArtView(
        song = song,
        embeddedCover = embeddedCover,
        modifier = modifier.clip(RoundedCornerShape(6.dp))
    )
}

private enum class PlayerHeaderActionKind {
    Favorite,
    More
}

@Composable
private fun PlayerHeaderAction(kind: PlayerHeaderActionKind, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(52.dp)
            .clip(CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        when (kind) {
            PlayerHeaderActionKind.Favorite -> HeartIcon(
                color = Color.White.copy(alpha = 0.92f),
                modifier = Modifier.size(32.dp)
            )
            PlayerHeaderActionKind.More -> MoreIcon(
                color = Color.White.copy(alpha = 0.90f),
                modifier = Modifier.size(30.dp)
            )
        }
    }
}

@Composable
private fun HeartIcon(
    color: Color,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val path = Path().apply {
            moveTo(w * 0.50f, h * 0.86f)
            cubicTo(w * 0.18f, h * 0.60f, w * 0.04f, h * 0.42f, w * 0.10f, h * 0.24f)
            cubicTo(w * 0.17f, h * 0.04f, w * 0.39f, h * 0.05f, w * 0.50f, h * 0.25f)
            cubicTo(w * 0.61f, h * 0.05f, w * 0.83f, h * 0.04f, w * 0.90f, h * 0.24f)
            cubicTo(w * 0.96f, h * 0.42f, w * 0.82f, h * 0.60f, w * 0.50f, h * 0.86f)
            close()
        }
        drawPath(path, color)
    }
}

@Composable
private fun MoreIcon(
    color: Color,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        val radius = size.minDimension * 0.09f
        val centerX = size.width / 2f
        listOf(0.25f, 0.50f, 0.75f).forEach { y ->
            drawCircle(color = color, radius = radius, center = Offset(centerX, size.height * y))
        }
    }
}

@Composable
private fun CloseIcon(
    color: Color,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        val stroke = size.minDimension * 0.12f
        drawLine(
            color = color,
            start = Offset(size.width * 0.22f, size.height * 0.22f),
            end = Offset(size.width * 0.78f, size.height * 0.78f),
            strokeWidth = stroke
        )
        drawLine(
            color = color,
            start = Offset(size.width * 0.78f, size.height * 0.22f),
            end = Offset(size.width * 0.22f, size.height * 0.78f),
            strokeWidth = stroke
        )
    }
}

@Composable
private fun PlayerProgressBlock(
    currentPosition: Long,
    duration: Long,
    audioInfo: AudioInfo?,
    bluetoothDeviceName: String?,
    palette: PlayerPalette,
    onSeek: (Float) -> Unit
) {
    val context = LocalContext.current
    var infoMode by remember(audioInfo, bluetoothDeviceName) { mutableStateOf(0) }
    val infoLabels = remember(audioInfo, bluetoothDeviceName) {
        buildList {
            audioInfo?.let {
                val quality = audioQualitySummary(it)
                add(quality.playerCompactText())
                quality.detailLabel.takeIf { text -> text.isNotBlank() }?.let(::add)
            }
            bluetoothDeviceName?.takeIf { it.isNotBlank() }?.let(::add)
        }.distinct()
    }
    Column(modifier = Modifier.fillMaxWidth()) {
        GlowSeekBar(
            value = if (duration > 0) currentPosition.toFloat() / duration.toFloat() else 0f,
            onSeek = onSeek,
            accent = palette.accent,
            modifier = Modifier.fillMaxWidth()
        )
        Box(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = formatTime(currentPosition),
                fontSize = 14.sp,
                color = Color.White.copy(alpha = 0.56f),
                modifier = Modifier.align(Alignment.CenterStart)
            )
            if (infoLabels.isNotEmpty()) {
                val infoText = infoLabels[infoMode % infoLabels.size]
                Text(
                    text = infoText,
                    fontSize = 12.sp,
                    color = Color.White.copy(alpha = 0.62f),
                    modifier = Modifier
                        .align(Alignment.Center)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color.White.copy(alpha = 0.10f))
                        .pointerInput(infoLabels, bluetoothDeviceName) {
                            detectTapGestures(
                                onTap = {
                                    if (infoLabels.size > 1) infoMode = (infoMode + 1) % infoLabels.size
                                },
                                onLongPress = {
                                    if (!bluetoothDeviceName.isNullOrBlank()) {
                                        openSystemOutputSwitcher(context)
                                    }
                                }
                            )
                        }
                        .padding(horizontal = 10.dp, vertical = 3.dp)
                )
            }
            Text(
                text = "-${formatTime((duration - currentPosition).coerceAtLeast(0L))}",
                fontSize = 14.sp,
                color = Color.White.copy(alpha = 0.56f),
                modifier = Modifier.align(Alignment.CenterEnd)
            )
        }
    }
}

private fun openSystemOutputSwitcher(context: Context) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
        val shown = runCatching {
            MediaRouter2.getInstance(context).showSystemOutputSwitcher()
        }.getOrDefault(false)
        if (shown) return
    }

    Toast.makeText(context, "当前系统不支持直接打开媒体输出面板，已跳转到蓝牙设置", Toast.LENGTH_SHORT).show()
    runCatching {
        context.startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }.onFailure {
        context.startActivity(Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}

@Composable
private fun PlayerTransportControls(
    isPlaying: Boolean,
    shuffleEnabled: Boolean,
    repeatMode: Int,
    palette: PlayerPalette,
    queueExpanded: Boolean,
    playlist: List<Song>,
    currentSongId: Long?,
    onCyclePlaybackMode: () -> Unit,
    onPrevious: () -> Unit,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onToggleQueue: () -> Unit,
    onDismissQueue: () -> Unit,
    onQueueSongClick: (Int) -> Unit,
    onClearQueue: () -> Unit
) {
    val density = LocalDensity.current
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onCyclePlaybackMode) {
            PlaybackModeIcon(shuffleEnabled = shuffleEnabled, repeatMode = repeatMode, accent = palette.accent)
        }
        IconButton(onClick = onPrevious) {
            Icon(
                painter = painterResource(id = R.drawable.ic_skip_previous),
                contentDescription = "上一首",
                tint = Color.White.copy(alpha = 0.92f),
                modifier = Modifier.size(38.dp)
            )
        }
        Box(
            modifier = Modifier
                .size(72.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.16f))
                .clickable(onClick = onPlayPause),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = if (isPlaying) MiuixIcons.Regular.Pause else MiuixIcons.Regular.Play,
                contentDescription = if (isPlaying) "暂停" else "播放",
                tint = Color.White.copy(alpha = 0.96f),
                modifier = Modifier.size(44.dp)
            )
        }
        IconButton(onClick = onNext) {
            Icon(
                painter = painterResource(id = R.drawable.ic_skip_next),
                contentDescription = "下一首",
                tint = Color.White.copy(alpha = 0.92f),
                modifier = Modifier.size(38.dp)
            )
        }
        Box(contentAlignment = Alignment.Center) {
            IconButton(onClick = onToggleQueue) {
                Text(
                    text = "≡♪",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color.White.copy(alpha = 0.52f)
                )
            }
            if (queueExpanded) {
                Popup(
                    alignment = Alignment.BottomEnd,
                    offset = with(density) { IntOffset(x = (-12).dp.roundToPx(), y = (-76).dp.roundToPx()) },
                    onDismissRequest = onDismissQueue,
                    properties = PopupProperties(focusable = true, dismissOnBackPress = true, dismissOnClickOutside = true)
                ) {
                    PlayerQueueMenu(
                        playlist = playlist,
                        currentSongId = currentSongId,
                        onSongClick = onQueueSongClick,
                        onClearQueue = onClearQueue,
                        modifier = Modifier.width(280.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun LyricToggleButton(
    text: String,
    active: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(48.dp)
            .clip(CircleShape)
            .background(Color.White.copy(alpha = if (active) 0.24f else 0.10f))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White.copy(alpha = if (active) 1f else 0.62f)
        )
    }
}

@Composable
private fun LyricActionMenu(
    showPronunciation: Boolean,
    showTranslation: Boolean,
    lyricSourceMode: Int,
    onTogglePronunciation: () -> Unit,
    onToggleTranslation: () -> Unit,
    onLyricSourceMode: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
            .background(Color.Black.copy(alpha = 0.86f))
            .navigationBarsPadding()
            .padding(horizontal = 18.dp, vertical = 16.dp)
    ) {
        Box(
            modifier = Modifier
                .width(42.dp)
                .height(4.dp)
                .clip(RoundedCornerShape(99.dp))
                .background(Color.White.copy(alpha = 0.24f))
                .align(Alignment.CenterHorizontally)
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = "歌词显示",
            fontSize = 18.sp,
            fontWeight = FontWeight.ExtraBold,
            color = Color.White.copy(alpha = 0.94f),
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp)
        )
        PlayerActionMenuItem(
            text = if (showPronunciation) "隐藏注音" else "显示注音",
            onClick = onTogglePronunciation
        )
        PlayerActionMenuItem(
            text = if (showTranslation) "隐藏翻译" else "显示翻译",
            onClick = onToggleTranslation
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "歌词来源",
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White.copy(alpha = 0.74f),
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            LyricSourceChip(
                text = "自动",
                selected = lyricSourceMode == SettingsManager.LYRIC_SOURCE_AUTO,
                onClick = { onLyricSourceMode(SettingsManager.LYRIC_SOURCE_AUTO) },
                modifier = Modifier.weight(1f)
            )
            LyricSourceChip(
                text = "外置",
                selected = lyricSourceMode == SettingsManager.LYRIC_SOURCE_EXTERNAL,
                onClick = { onLyricSourceMode(SettingsManager.LYRIC_SOURCE_EXTERNAL) },
                modifier = Modifier.weight(1f)
            )
            LyricSourceChip(
                text = "内嵌",
                selected = lyricSourceMode == SettingsManager.LYRIC_SOURCE_EMBEDDED,
                onClick = { onLyricSourceMode(SettingsManager.LYRIC_SOURCE_EMBEDDED) },
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun LyricSourceChip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(15.dp))
            .background(Color.White.copy(alpha = if (selected) 0.22f else 0.07f))
            .clickable(onClick = onClick)
            .padding(vertical = 11.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = if (selected) "✓ $text" else text,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White.copy(alpha = if (selected) 0.96f else 0.70f)
        )
    }
}

@Composable
private fun PlaybackModeIcon(
    shuffleEnabled: Boolean,
    repeatMode: Int,
    accent: Color
) {
    val active = shuffleEnabled || repeatMode != Player.REPEAT_MODE_OFF
    val iconRes = when {
        shuffleEnabled -> R.drawable.ic_shuffle
        repeatMode == Player.REPEAT_MODE_ONE -> R.drawable.ic_repeat_one
        else -> R.drawable.ic_repeat
    }
    val label = when {
        shuffleEnabled -> "随机播放"
        repeatMode == Player.REPEAT_MODE_ONE -> "单曲循环"
        repeatMode == Player.REPEAT_MODE_ALL -> "列表循环"
        else -> "顺序播放"
    }
    Box(
        modifier = Modifier
            .size(28.dp)
            .clip(CircleShape)
            .background((if (active) accent else Color.White).copy(alpha = if (active) 0.28f else 0.10f)),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            painter = painterResource(id = iconRes),
            contentDescription = label,
            tint = if (active) Color.White else Color.White.copy(alpha = 0.52f),
            modifier = Modifier.size(20.dp)
        )
    }
}

@Composable
private fun ImmersiveCoverBackground(
    palette: PlayerPalette,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier.background(palette.middle)) {
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
private fun FluidLyricBackground(
    palette: PlayerPalette,
    positionMs: Long,
    isPlaying: Boolean,
    modifier: Modifier = Modifier
) {
    val transition = rememberInfiniteTransition(label = "fluid_lyric_background")
    val drift by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 18_000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "fluid_lyric_background_drift"
    )
    val pulse = if (isPlaying) {
        0.5f + 0.5f * kotlin.math.sin(positionMs / 900.0).toFloat()
    } else {
        0.28f
    }

    Canvas(modifier = modifier.background(palette.middle)) {
        drawRect(
            brush = Brush.verticalGradient(
                colors = listOf(
                    palette.top.copy(alpha = 0.98f),
                    palette.middle.copy(alpha = 0.98f),
                    palette.bottom.copy(alpha = 1f)
                )
            )
        )
        val w = size.width
        val h = size.height
        val t = drift * kotlin.math.PI.toFloat() * 2f
        val centers = listOf(
            Offset((0.18f + 0.04f * kotlin.math.sin(t)) * w, (0.24f + 0.08f * kotlin.math.cos(t * 0.7f)) * h),
            Offset((0.82f + 0.05f * kotlin.math.cos(t * 0.8f)) * w, (0.20f + 0.06f * kotlin.math.sin(t)) * h),
            Offset((0.48f + 0.08f * kotlin.math.sin(t * 0.55f)) * w, (0.62f + 0.05f * kotlin.math.cos(t * 0.9f)) * h),
            Offset((0.72f + 0.06f * kotlin.math.sin(t * 0.95f)) * w, (0.86f + 0.04f * kotlin.math.cos(t * 0.6f)) * h)
        )
        val colors = listOf(
            palette.accent.copy(alpha = 0.22f + pulse * 0.05f),
            Color.White.copy(alpha = 0.10f),
            palette.top.copy(alpha = 0.20f),
            Color.Black.copy(alpha = 0.20f)
        )
        centers.forEachIndexed { index, center ->
            val radius = minOf(w, h) * (0.34f + index * 0.055f)
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(colors[index], Color.Transparent),
                    center = center,
                    radius = radius
                ),
                radius = radius,
                center = center
            )
        }
        drawRect(
            brush = Brush.verticalGradient(
                colors = listOf(
                    Color.Black.copy(alpha = 0.10f),
                    Color.Transparent,
                    Color.Black.copy(alpha = 0.42f)
                )
            )
        )
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
    val coverModel = embeddedCover ?: song?.coverUrl?.takeIf { it.isNotBlank() } ?: uri
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
                sizePx = 1200
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
private fun PlayerQueueMenu(
    playlist: List<Song>,
    currentSongId: Long?,
    onSongClick: (Int) -> Unit,
    onClearQueue: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .background(Color.Black.copy(alpha = 0.56f))
            .padding(vertical = 8.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "当前播放列表",
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White.copy(alpha = 0.92f)
            )
            Spacer(modifier = Modifier.weight(1f))
            if (playlist.isNotEmpty()) {
                Text(
                    text = "清空",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color.White.copy(alpha = 0.62f),
                    modifier = Modifier
                        .clip(RoundedCornerShape(999.dp))
                        .clickable(onClick = onClearQueue)
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
        }
        if (playlist.isEmpty()) {
            Text(
                text = "暂无歌曲",
                fontSize = 13.sp,
                color = Color.White.copy(alpha = 0.54f),
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp)
            )
        } else {
            LazyColumn(modifier = Modifier.heightIn(max = 320.dp)) {
                itemsIndexed(playlist, key = { _, item -> item.id }) { index, item ->
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSongClick(index) }
                            .padding(horizontal = 14.dp, vertical = 8.dp)
                    ) {
                        Text(
                            text = item.title,
                            fontSize = 13.sp,
                            fontWeight = if (item.id == currentSongId) FontWeight.Bold else FontWeight.Medium,
                            color = if (item.id == currentSongId) Color.White else Color.White.copy(alpha = 0.82f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = item.artist,
                            fontSize = 11.sp,
                            color = Color.White.copy(alpha = 0.48f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MiniLyricsPreview(
    lyrics: List<com.ella.music.data.model.LyricLine>,
    currentIndex: Int,
    showTranslation: Boolean,
    showPronunciation: Boolean,
    fontFamily: FontFamily? = null,
    modifier: Modifier = Modifier
) {
    val safeIndex = currentIndex.takeIf { it in lyrics.indices }
        ?: lyrics.indexOfFirst { it.hasMiniLyric() }.takeIf { it >= 0 }
        ?: return
    val previewLines = (-1..2)
        .mapNotNull { offset -> lyrics.getOrNull(safeIndex + offset)?.let { (safeIndex + offset) to it } }
        .filter { (_, line) -> line.hasMiniLyric() }

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(8.dp),
        horizontalAlignment = Alignment.Start
    ) {
        previewLines.forEach { (index, line) ->
            val isActive = index == safeIndex
            val main = line.text.takeIf { it.isNotBlank() && !it.isMusicSymbolOnly() }
            val pronunciation = line.pronunciation?.takeIf { showPronunciation && it.isNotBlank() }
            val translation = line.translation?.takeIf { showTranslation && it.isNotBlank() }
            val textAlign = line.previewTextAlign()
            val alpha = if (isActive) 0.92f else 0.34f

            Column(modifier = Modifier.fillMaxWidth()) {
                if (pronunciation != null && isActive) {
                    Text(
                        text = pronunciation,
                        fontSize = 12.sp,
                        fontFamily = fontFamily,
                        color = Color.White.copy(alpha = 0.48f),
                        textAlign = textAlign,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                if (main != null) {
                    Text(
                        text = main,
                        fontSize = if (isActive) 18.sp else 15.sp,
                        fontFamily = fontFamily,
                        fontWeight = if (isActive) FontWeight.ExtraBold else FontWeight.Bold,
                        color = Color.White.copy(alpha = alpha),
                        textAlign = textAlign,
                        maxLines = if (isActive) 2 else 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                if (translation != null) {
                    Text(
                        text = translation,
                        fontSize = if (isActive) 14.sp else 12.sp,
                        fontFamily = fontFamily,
                        fontWeight = if (isActive) FontWeight.Bold else FontWeight.Medium,
                        color = Color.White.copy(alpha = if (isActive) 0.64f else 0.28f),
                        textAlign = textAlign,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
}

@Composable
private fun MiniLyricBlock(
    line: com.ella.music.data.model.LyricLine,
    showTranslation: Boolean,
    showPronunciation: Boolean,
    fontFamily: FontFamily? = null,
    modifier: Modifier = Modifier
) {
    val longest = listOfNotNull(
        line.pronunciation,
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
        horizontalAlignment = line.previewHorizontalAlignment()
    ) {
        val main = line.text.takeIf { it.isNotBlank() && !it.isMusicSymbolOnly() }
        val pronunciation = line.pronunciation?.takeIf { showPronunciation && it.isNotBlank() }
        val background = line.backgroundText?.takeIf { it.isNotBlank() && !it.isMusicSymbolOnly() }
        val translation = line.translation?.takeIf { showTranslation && it.isNotBlank() }
        val backgroundTranslation = line.backgroundTranslation?.takeIf { showTranslation && it.isNotBlank() }

        if (pronunciation != null) {
            Text(
                text = pronunciation,
                fontSize = if (secondarySize.value <= 10f) 9.sp else 11.sp,
                fontFamily = fontFamily,
                color = Color.White.copy(alpha = 0.48f),
                textAlign = line.previewTextAlign(),
                maxLines = 2,
                modifier = Modifier.fillMaxWidth()
            )
        }
        if (main != null) {
            Text(
                text = main,
                fontSize = mainSize,
                fontFamily = fontFamily,
                fontWeight = FontWeight.SemiBold,
                color = Color.White.copy(alpha = 0.88f),
                textAlign = line.previewTextAlign(),
                maxLines = 3,
                modifier = Modifier.fillMaxWidth()
            )
        }
        if (translation != null) {
            Text(
                text = translation,
                fontSize = secondarySize,
                fontFamily = fontFamily,
                color = Color.White.copy(alpha = 0.58f),
                textAlign = line.previewTextAlign(),
                maxLines = 2,
                modifier = Modifier.fillMaxWidth()
            )
        }
        if (background != null) {
            Text(
                text = background,
                fontSize = if (mainSize.value <= 12f) 10.sp else 13.sp,
                fontFamily = fontFamily,
                fontWeight = FontWeight.Medium,
                color = Color.White.copy(alpha = 0.68f),
                textAlign = line.previewBackgroundTextAlign(),
                maxLines = 2,
                modifier = Modifier.fillMaxWidth()
            )
        }
        if (backgroundTranslation != null) {
            Text(
                text = backgroundTranslation,
                fontSize = if (secondarySize.value <= 10f) 9.sp else 11.sp,
                fontFamily = fontFamily,
                color = Color.White.copy(alpha = 0.48f),
                textAlign = line.previewBackgroundTextAlign(),
                maxLines = 2,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun PlayerActionMenu(
    song: Song?,
    speed: Float,
    pitch: Float,
    onAlbum: () -> Unit,
    onArtist: () -> Unit,
    onEditTags: () -> Unit,
    onDownload: () -> Unit,
    onLandscape: () -> Unit,
    onStopAfterCurrent: () -> Unit,
    onTimer: (Int) -> Unit,
    onCancelTimer: () -> Unit,
    onSpeed: (Float) -> Unit,
    onPitch: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    var page by remember { mutableStateOf(PlayerActionSheetPage.Main) }
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
            .background(Color.Black.copy(alpha = 0.74f))
            .navigationBarsPadding()
            .padding(horizontal = 18.dp, vertical = 16.dp)
    ) {
        Box(
            modifier = Modifier
                .width(42.dp)
                .height(4.dp)
                .clip(RoundedCornerShape(99.dp))
                .background(Color.White.copy(alpha = 0.24f))
                .align(Alignment.CenterHorizontally)
        )
        Spacer(modifier = Modifier.height(12.dp))
        when (page) {
            PlayerActionSheetPage.Main -> {
                Text(
                    text = "更多操作",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color.White.copy(alpha = 0.94f),
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp)
                )
                PlayerActionMenuItem("横屏歌词", onLandscape)
                PlayerActionMenuItem("查看专辑页", onAlbum)
                PlayerActionMenuItem("查看歌手页", onArtist)
                PlayerActionMenuItem("外部编辑标签", onEditTags)
                if (song?.onlineSource == "kw" && song.path.startsWith("http")) {
                    PlayerActionMenuItem("下载 LX 歌曲", onDownload)
                }
                PlayerActionMenuItem("播放完当前歌曲后暂停", onStopAfterCurrent)
                PlayerActionMenuItem("定时关闭", { page = PlayerActionSheetPage.Timer })
                PlayerActionMenuItem("变速变调", { page = PlayerActionSheetPage.Speed })
            }
            PlayerActionSheetPage.Timer -> {
                TimerSheetContent(
                    onBack = { page = PlayerActionSheetPage.Main },
                    onTimer = onTimer,
                    onCancelTimer = onCancelTimer
                )
            }
            PlayerActionSheetPage.Speed -> {
                SpeedPitchSheetContent(
                    speed = speed,
                    pitch = pitch,
                    onBack = { page = PlayerActionSheetPage.Main },
                    onSpeed = onSpeed,
                    onPitch = onPitch
                )
            }
        }
    }
}

private enum class PlayerActionSheetPage {
    Main,
    Timer,
    Speed
}

@Composable
private fun TimerSheetContent(
    onBack: () -> Unit,
    onTimer: (Int) -> Unit,
    onCancelTimer: () -> Unit
) {
    var customMinutes by remember { mutableFloatStateOf(45f) }
    HalfSheetTitle(title = "定时关闭", onBack = onBack)
    Spacer(modifier = Modifier.height(18.dp))
    listOf(10, 15, 20, 30, 40, 60).chunked(4).forEach { row ->
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            row.forEach { minutes ->
                HalfSheetPill(
                    text = "$minutes 分钟",
                    onClick = { onTimer(minutes) },
                    modifier = Modifier.weight(1f)
                )
            }
            repeat(4 - row.size) { Spacer(modifier = Modifier.weight(1f)) }
        }
        Spacer(modifier = Modifier.height(12.dp))
    }
    Spacer(modifier = Modifier.height(8.dp))
    Text(
        text = "自定义时长",
        fontSize = 16.sp,
        fontWeight = FontWeight.ExtraBold,
        color = Color.White.copy(alpha = 0.90f)
    )
    DottedValueSlider(
        value = customMinutes,
        valueRange = 5f..120f,
        steps = 23,
        onValueChange = { customMinutes = it },
        modifier = Modifier
            .fillMaxWidth()
            .height(92.dp)
    )
    HalfSheetPill(
        text = "开始计时:${customMinutes.toInt()} 分钟",
        selected = true,
        onClick = { onTimer(customMinutes.toInt().coerceAtLeast(1)) },
        modifier = Modifier.fillMaxWidth()
    )
    Spacer(modifier = Modifier.height(8.dp))
    PlayerActionMenuItem("取消定时播放", onCancelTimer)
}

@Composable
private fun SpeedPitchSheetContent(
    speed: Float,
    pitch: Float,
    onBack: () -> Unit,
    onSpeed: (Float) -> Unit,
    onPitch: (Float) -> Unit
) {
    HalfSheetTitle(title = "变速变调", onBack = onBack)
    Spacer(modifier = Modifier.height(22.dp))
    Text(
        text = "变速播放",
        fontSize = 16.sp,
        fontWeight = FontWeight.ExtraBold,
        color = Color.White.copy(alpha = 0.90f)
    )
    DottedValueSlider(
        value = speed,
        valueRange = 0.5f..2f,
        steps = 30,
        label = speed.formatPlaybackStep(),
        onValueChange = onSpeed,
        modifier = Modifier
            .fillMaxWidth()
            .height(100.dp)
    )
    Text(
        text = "变调播放",
        fontSize = 16.sp,
        fontWeight = FontWeight.ExtraBold,
        color = Color.White.copy(alpha = 0.90f)
    )
    DottedValueSlider(
        value = pitch,
        valueRange = 0.5f..2f,
        steps = 30,
        label = pitch.formatPlaybackStep(),
        onValueChange = onPitch,
        modifier = Modifier
            .fillMaxWidth()
            .height(100.dp)
    )
}

@Composable
private fun HalfSheetTitle(title: String, onBack: () -> Unit) {
    Box(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = "‹",
            fontSize = 30.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White.copy(alpha = 0.70f),
            modifier = Modifier
                .align(Alignment.CenterStart)
                .clip(CircleShape)
                .clickable(onClick = onBack)
                .padding(horizontal = 12.dp, vertical = 2.dp)
        )
        Text(
            text = title,
            fontSize = 22.sp,
            fontWeight = FontWeight.ExtraBold,
            color = Color.White.copy(alpha = 0.94f),
            modifier = Modifier.align(Alignment.Center)
        )
    }
}

@Composable
private fun HalfSheetPill(
    text: String,
    selected: Boolean = false,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(if (selected) Color.White.copy(alpha = 0.82f) else Color.White.copy(alpha = 0.10f))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 14.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            fontSize = 16.sp,
            fontWeight = FontWeight.ExtraBold,
            color = if (selected) Color.Black.copy(alpha = 0.78f) else Color.White.copy(alpha = 0.84f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun DottedValueSlider(
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    steps: Int,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null
) {
    val safeValue = value.coerceIn(valueRange.start, valueRange.endInclusive)
    val fraction = ((safeValue - valueRange.start) / (valueRange.endInclusive - valueRange.start)).coerceIn(0f, 1f)

    fun update(width: Float, x: Float) {
        val raw = valueRange.start + (x / width.coerceAtLeast(1f)).coerceIn(0f, 1f) *
            (valueRange.endInclusive - valueRange.start)
        val stepped = if (steps > 0) {
            val stepSize = (valueRange.endInclusive - valueRange.start) / steps
            (kotlin.math.round(raw / stepSize) * stepSize).coerceIn(valueRange.start, valueRange.endInclusive)
        } else {
            raw
        }
        onValueChange(stepped)
    }

    Box(modifier = modifier) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(valueRange, steps) {
                    detectTapGestures { offset -> update(size.width.toFloat(), offset.x) }
                }
                .pointerInput(valueRange, steps) {
                    detectDragGestures { change, _ -> update(size.width.toFloat(), change.position.x) }
                }
        ) {
            val centerY = size.height * 0.60f
            val dotCount = 44
            val gap = size.width / (dotCount - 1).coerceAtLeast(1)
            for (index in 0 until dotCount) {
                val dotFraction = index.toFloat() / (dotCount - 1).coerceAtLeast(1)
                drawCircle(
                    color = Color.White.copy(alpha = if (dotFraction <= fraction) 0.74f else 0.34f),
                    radius = if (index % 5 == 0) 4.2f else 3.2f,
                    center = Offset(x = gap * index, y = centerY)
                )
            }
            val knobX = size.width * fraction
            drawLine(
                color = Color.White.copy(alpha = 0.86f),
                start = Offset(knobX, centerY - 36f),
                end = Offset(knobX, centerY + 36f),
                strokeWidth = 6f,
                cap = StrokeCap.Round
            )
            drawCircle(
                color = Color.White.copy(alpha = 0.92f),
                radius = 24f,
                center = Offset(knobX, centerY - 54f)
            )
        }
        label?.let {
            Text(
                text = it,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = Color.Black.copy(alpha = 0.78f),
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 5.dp)
            )
        }
    }
}

@Composable
private fun PlayerActionMenuItem(
    text: String,
    onClick: () -> Unit
) {
    Text(
        text = text,
        fontSize = 14.sp,
        color = Color.White.copy(alpha = 0.92f),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(Color.White.copy(alpha = 0.06f))
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp)
    )
}

@Composable
private fun SimpleAudioVisualizer(
    isPlaying: Boolean,
    positionMs: Long,
    accent: Color,
    modifier: Modifier = Modifier
) {
    val transition = rememberInfiniteTransition(label = "audio_visualizer")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = if (isPlaying) 920 else 2600, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "audio_visualizer_phase"
    )
    val playbackPhase = if (isPlaying) phase else 0.18f
    Canvas(modifier = modifier.graphicsLayer { alpha = if (isPlaying) 1f else 0.42f }) {
        val barCount = 58
        val bottom = size.height - 2.dp.toPx()
        val gap = size.width / barCount
        val barWidth = (gap * 0.36f).coerceIn(2.dp.toPx(), 5.dp.toPx())
        for (index in 0 until barCount) {
            val x = gap * index + gap / 2f
            val seed = index * 0.37f + positionMs / 780f + playbackPhase * 6.28f
            val wave = (
                kotlin.math.sin(seed) * 0.42f +
                    kotlin.math.sin(seed * 0.47f + index * 0.19f) * 0.34f +
                    kotlin.math.sin(seed * 1.71f) * 0.24f
                ).coerceIn(-1f, 1f)
            val normalized = if (isPlaying) (0.30f + kotlin.math.abs(wave) * 0.70f) else 0.18f
            val height = size.height * normalized * if (index < 6 || index > barCount - 7) 0.58f else 1f
            drawRoundRect(
                color = accent.copy(alpha = 0.20f + normalized * 0.56f),
                topLeft = Offset(x - barWidth / 2f, bottom - height),
                size = Size(barWidth, height),
                cornerRadius = CornerRadius(barWidth, barWidth)
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
    val coverModel = embeddedCover ?: song?.coverUrl?.takeIf { it.isNotBlank() } ?: uri

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
private fun DynamicCoverVideo(
    file: File,
    isPlaying: Boolean,
    onPlaybackError: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    val exoPlayer = remember(file.absolutePath) {
        ExoPlayer.Builder(context).build().apply {
            repeatMode = Media3Player.REPEAT_MODE_ALL
            volume = 0f
            setMediaItem(MediaItem.fromUri(Uri.fromFile(file)))
            prepare()
        }
    }

    DisposableEffect(exoPlayer) {
        val listener = object : Media3Player.Listener {
            override fun onPlayerError(error: PlaybackException) {
                onPlaybackError()
            }
        }

        exoPlayer.addListener(listener)

        onDispose {
            exoPlayer.removeListener(listener)
            exoPlayer.release()
        }
    }

    DisposableEffect(isPlaying, exoPlayer) {
        exoPlayer.playWhenReady = isPlaying
        onDispose { }
    }

    AndroidView(
        modifier = modifier,
        factory = { viewContext ->
            PlayerView(viewContext).apply {
                player = exoPlayer
                useController = false
                resizeMode = androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                setShowBuffering(PlayerView.SHOW_BUFFERING_NEVER)
            }
        },
        update = { view ->
            view.player = exoPlayer
            exoPlayer.playWhenReady = isPlaying
        }
    )
}

@Composable
private fun GlowSeekBar(
    value: Float,
    onSeek: (Float) -> Unit,
    accent: Color,
    modifier: Modifier = Modifier
) {
    val safeProgress = value.coerceIn(0f, 1f)

    fun seek(width: Float, x: Float) {
        onSeek((x / width.coerceAtLeast(1f)).coerceIn(0f, 1f))
    }

    Box(
        modifier = modifier.height(38.dp)
    ) {
        AndroidView(
            factory = { context ->
                SuperIslandGlowProgressBar(context).apply {
                    shaderMode = SuperIslandGlowProgressBar.ShaderMode.HIGH_END
                    trackHeightPx = resources.displayMetrics.density * 6f
                    trackHorizontalPaddingPx = 0f
                    headGlowAlpha = 1f
                    trackColor = AndroidColor.argb(48, 255, 255, 255)
                }
            },
            update = { view ->
                view.progressFraction = safeProgress
                view.fallbackProgressColor = accent.copy(alpha = 0.82f).toArgb()
            },
            modifier = Modifier.fillMaxSize()
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectTapGestures { offset -> seek(size.width.toFloat(), offset.x) }
                }
                .pointerInput(Unit) {
                    detectDragGestures { change, _ ->
                        seek(size.width.toFloat(), change.position.x)
                    }
                }
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
    return !pronunciation.isNullOrBlank() ||
        text.takeIf { it.isNotBlank() && !it.isMusicSymbolOnly() } != null ||
        !translation.isNullOrBlank() ||
        backgroundText?.takeIf { it.isNotBlank() && !it.isMusicSymbolOnly() } != null ||
        !backgroundTranslation.isNullOrBlank()
}

private fun com.ella.music.data.model.LyricLine.previewTextAlign(): TextAlign {
    if (!isTtml) return TextAlign.Start
    if (agent.isNullOrBlank()) {
        return if (!backgroundText.isNullOrBlank() || backgroundWords.isNotEmpty()) TextAlign.Start else TextAlign.Center
    }
    return if (agent.equals("v2", ignoreCase = true)) TextAlign.End else TextAlign.Start
}

private fun com.ella.music.data.model.LyricLine.previewBackgroundTextAlign(): TextAlign {
    if (!isTtml) return TextAlign.Start
    return when (previewTextAlign()) {
        TextAlign.End -> TextAlign.Start
        TextAlign.Start -> TextAlign.End
        else -> TextAlign.Center
    }
}

private fun com.ella.music.data.model.LyricLine.previewHorizontalAlignment(): Alignment.Horizontal {
    return when (previewTextAlign()) {
        TextAlign.End -> Alignment.End
        TextAlign.Center -> Alignment.CenterHorizontally
        else -> Alignment.Start
    }
}

private fun String.toPlayerLyricFontFamily(): FontFamily? {
    if (isBlank()) return null
    val file = File(this)
    if (!file.exists() || !file.canRead()) return null
    return runCatching { FontFamily(Typeface.createFromFile(file)) }.getOrNull()
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

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

@Composable
private fun rememberBluetoothOutputName(): String? {
    val context = LocalContext.current
    return remember {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
        audioManager
            ?.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
            ?.firstOrNull { device ->
                device.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP ||
                    device.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO ||
                    device.type == AudioDeviceInfo.TYPE_BLE_HEADSET ||
                    device.type == AudioDeviceInfo.TYPE_BLE_SPEAKER ||
                    device.type == AudioDeviceInfo.TYPE_BLE_BROADCAST
            }
            ?.productName
            ?.toString()
            ?.takeIf { it.isNotBlank() }
    }
}

private fun formatTime(ms: Long): String {
    if (ms <= 0) return "00:00"
    val totalSeconds = ms / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%02d:%02d".format(minutes, seconds)
}

private fun com.ella.music.data.AudioQualitySummary.playerCompactText(): String {
    return when {
        compactLabel == "Dolby Atmos" -> "ᴰᴰ Dolby Atmos"
        showMobius -> "∞ $compactLabel"
        else -> compactLabel
    }
}

private fun Float.nextPlaybackStep(): Float {
    val next = ((this * 4).toInt() + 1) / 4f
    return if (next > 2f) 0.5f else next.coerceIn(0.5f, 2f)
}

private fun Float.formatPlaybackStep(): String = "%.2f".format(this.coerceIn(0.5f, 2f))

private fun enqueuePlayerDownload(context: Context, song: Song) {
    val fileName = song.fileName.ifBlank { "${song.title}-${song.artist}.mp3" }
        .replace(Regex("""[\\/:*?"<>|]"""), "_")
        .replace(Regex("""\s+"""), " ")
        .trim()
        .ifBlank { "Ella Music.mp3" }
    val request = DownloadManager.Request(Uri.parse(song.path))
        .setTitle(fileName)
        .setDescription("${song.title} - ${song.artist}")
        .setMimeType(song.mimeType.ifBlank { "audio/*" })
        .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
        .setDestinationInExternalPublicDir(Environment.DIRECTORY_MUSIC, "Ella/$fileName")
        .setAllowedOverMetered(true)
        .setAllowedOverRoaming(true)
    val manager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
    manager.enqueue(request)
}

private fun openExternalTagEditor(context: Context, song: Song) {
    if (song.path.startsWith("http://") || song.path.startsWith("https://")) {
        Toast.makeText(context, "在线 / WebDAV 歌曲暂不支持外部编辑标签", Toast.LENGTH_SHORT).show()
        return
    }

    if (song.id <= 0L) {
        Toast.makeText(context, "无法获取歌曲媒体库 Uri", Toast.LENGTH_SHORT).show()
        return
    }

    val uri = ContentUris.withAppendedId(
        MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
        song.id
    )

    val mimeType = song.mimeType
        .takeIf { it.startsWith("audio/") }
        ?: "audio/*"

    val intent = Intent("com.lonx.lyrico.action.EDIT_TAG").apply {
        setDataAndType(uri, mimeType)
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        clipData = ClipData.newUri(context.contentResolver, "audio", uri)
    }

    runCatching {
        context.startActivity(intent)
    }.onFailure {
        Toast.makeText(context, "未找到支持编辑标签的应用，请先安装 Lyrico", Toast.LENGTH_SHORT).show()
    }
}

private fun Song.dynamicCoverVideoFile(context: Context): File? {
    val songFile = path
        .takeUnless { it.startsWith("http://") || it.startsWith("https://") }
        ?.let { File(it) }

    val songFolder = songFile?.parentFile

    val albumName = album.ifBlank {
        songFolder?.name.orEmpty()
    }.ifBlank {
        "Unknown"
    }

    val albumKey = albumName.toSafeDynamicCoverName()

    val artistAlbumKey = listOf(artist, albumName)
        .filter { it.isNotBlank() }
        .joinToString(" - ")
        .toSafeDynamicCoverName()

    val songKey = listOf(artist, title)
        .filter { it.isNotBlank() }
        .joinToString(" - ")
        .toSafeDynamicCoverName()

    val folderCandidates = songFolder
        ?.takeIf { it.exists() && it.isDirectory }
        ?.let { folder ->
            listOf(
                File(folder, "cover.mp4"),               // 专辑内文件夹统一视频
                File(folder, "${folder.name}.mp4"),      // 例s: Music/÷(Deluxe)/÷(Deluxe).mp4
                File(folder, "$albumName.mp4"),          // 按专辑 tag
                File(folder, "$albumKey.mp4"),           // 清洗后的专辑名
                File(folder, "$artistAlbumKey.mp4")      // 歌手 - 专辑
            )
        }
        .orEmpty()

    val publicDir = File(
        Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES),
        "Ella/DynamicCovers"
    )

    val appDir = File(
        context.getExternalFilesDir(Environment.DIRECTORY_MOVIES),
        "DynamicCovers"
    )

    val roots = listOf(publicDir, appDir)

    val libraryCandidates = roots.flatMap { root ->
        listOf(
            File(root, "Song/$songKey.mp4"),
            File(root, "Album/$albumKey.mp4"),
            File(root, "Album/$artistAlbumKey.mp4"),
            File(root, "cover.mp4")
        )
    }

    val candidates = folderCandidates + libraryCandidates

    return candidates.firstOrNull { it.exists() && it.isFile && it.length() > 0L }
}

private fun String.toSafeDynamicCoverName(): String {
    return trim()
        .replace("""[\\/:*?"<>|]""".toRegex(), "_")
        .replace("\\s+".toRegex(), " ")
        .ifBlank { "Unknown" }
}
