package com.ella.music.ui.player

import android.content.Context
import android.app.Activity
import android.app.DownloadManager
import android.Manifest
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.media.MediaRouter2
import android.media.audiofx.Visualizer
import android.content.Intent
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color as AndroidColor
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import android.os.SystemClock
import androidx.compose.runtime.DisposableEffect
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player as Media3Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import android.os.Environment
import android.provider.Settings
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.BaselineShift
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import androidx.media3.common.Player
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import com.ella.music.R
import com.ella.music.data.SettingsManager
import com.ella.music.data.NeteaseKeyInfo
import com.ella.music.data.audioQualitySummary
import com.ella.music.data.decodeNeteaseKey
import com.ella.music.data.neteaseAlbumUrl
import com.ella.music.data.neteaseArtistUrl
import com.ella.music.data.neteaseSongUrl
import com.ella.music.data.splitArtistNames
import com.ella.music.data.tagIdentityKey
import com.ella.music.data.model.AudioInfo
import com.ella.music.data.model.LyricLine
import com.ella.music.data.model.Song
import com.ella.music.data.model.SongTagInfo
import com.ella.music.data.model.albumIdentityId
import com.ella.music.data.model.playlistIdentityKey
import com.ella.music.player.PlaybackAudioSession
import com.ella.music.ui.components.ArtistPickerSheet
import com.ella.music.ui.components.DefaultAlbumCover
import com.ella.music.ui.components.WordLyricView
import com.ella.music.ui.components.SafeCoverImage
import com.ella.music.ui.components.CoverLoadLimiter
import com.ella.music.ui.components.LyricSharePicker
import com.ella.music.ui.components.TagEditorOption
import com.ella.music.ui.components.TagEditorOptionIds
import com.ella.music.ui.components.TagEditorOptionKind
import com.ella.music.ui.components.buildTagEditorOptions
import com.ella.music.ui.components.launchTagEditorOption
import com.ella.music.ui.components.SongInfoSheet
import com.ella.music.ui.components.shareLyricCard
import com.ella.music.ui.components.shareLocalSong
import com.ella.music.viewmodel.PlayerViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.net.URL
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.ln
import kotlin.math.sqrt
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.window.WindowBottomSheet
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.icon.extended.Music
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
private fun rememberThrottledPlayerPosition(
    positionFlow: StateFlow<Long>,
    isPlaying: Boolean,
    anchorKey: Any?,
    intervalMs: Long = 250L
): State<Long> {
    val latestPlaying by rememberUpdatedState(isPlaying)
    return produceState(initialValue = positionFlow.value, positionFlow, anchorKey) {
        var lastUiTickMs = 0L
        var lastLoggedTickMs = 0L
        positionFlow.collect { positionMs ->
            val now = SystemClock.elapsedRealtime()
            val reset = positionMs < value || kotlin.math.abs(positionMs - value) > 1_500L
            val shouldUpdate = reset || !latestPlaying || now - lastUiTickMs >= intervalMs
            if (!shouldUpdate) return@collect

            val previousTickMs = lastUiTickMs
            value = positionMs
            lastUiTickMs = now
            if (latestPlaying && now - lastLoggedTickMs >= 5_000L) {
                val interval = if (previousTickMs > 0L) now - previousTickMs else 0L
                Log.d("PlayerScreenPerf", "PlayerScreen position ui tick interval=${interval}ms")
                lastLoggedTickMs = now
            }
        }
    }
}

@Composable
fun PlayerScreen(
    playerViewModel: PlayerViewModel,
    onBack: () -> Unit,
    onNavigateToAlbum: (Long) -> Unit = {},
    onNavigateToArtist: (String) -> Unit = {},
    onNavigateToMetadataCategory: (String, String) -> Unit = { _, _ -> },
    onDismissProgressChange: (Float) -> Unit = {},
    openToken: Int = 0
) {
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    val view = LocalView.current
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val settingsManager = remember { SettingsManager(context) }
    val lyricFontPath by settingsManager.lyricFontPath.collectAsState(initial = "")
    val lyricFontWeightValue by settingsManager.lyricFontWeight.collectAsState(initial = 800)
    val lyricFontScaleValue by settingsManager.lyricFontScale.collectAsState(initial = 100)
    val lyricPerspectiveEffect by settingsManager.lyricPerspectiveEffect.collectAsState(initial = false)
    val lyricSourceMode by settingsManager.lyricSourceMode.collectAsState(initial = SettingsManager.LYRIC_SOURCE_AUTO)
    val lyricFontFamily = remember(lyricFontPath, lyricFontWeightValue) {
        lyricFontPath.toPlayerLyricFontFamily(lyricFontWeightValue)
    }
    val lyricFontWeight = remember(lyricFontWeightValue) { FontWeight(lyricFontWeightValue.coerceIn(100, 900)) }
    val lyricFontScale = remember(lyricFontScaleValue) { lyricFontScaleValue.coerceIn(75, 130) / 100f }
    val currentSong by playerViewModel.currentSong.collectAsState()
    val isPlaying by playerViewModel.isPlaying.collectAsState()
    val currentPositionState = rememberThrottledPlayerPosition(
        positionFlow = playerViewModel.currentPosition,
        isPlaying = isPlaying,
        anchorKey = currentSong?.id
    )
    val currentPositionProvider = remember(currentPositionState) { { currentPositionState.value } }
    val duration by playerViewModel.duration.collectAsState()
    val shuffleEnabled by playerViewModel.shuffleEnabled.collectAsState()
    val repeatMode by playerViewModel.repeatMode.collectAsState()
    val playbackSpeed by playerViewModel.playbackSpeed.collectAsState()
    val playbackPitch by playerViewModel.playbackPitch.collectAsState()
    val audioSessionId by PlaybackAudioSession.audioSessionId.collectAsState()
    val audioVisualizerEnabled by settingsManager.audioVisualizerEnabled.collectAsState(initial = false)
    val dynamicCoverEnabled by settingsManager.dynamicCoverEnabled.collectAsState(initial = false)
    val immersiveAlbumCover by settingsManager.playerImmersiveCover.collectAsState(initial = true)
    val playerDynamicFlowEnabled by settingsManager.playerDynamicFlowEnabled.collectAsState(initial = false)
    val lyricShareCustomInfo by settingsManager.lyricShareCustomInfo.collectAsState(initial = "")
    val metadataEditorId by settingsManager.metadataEditorId.collectAsState(initial = TagEditorOptionIds.ASK_EACH_TIME)
    val lyricTimingEditorId by settingsManager.lyricTimingEditorId.collectAsState(initial = TagEditorOptionIds.ASK_EACH_TIME)
    val sleepTimerCustomMinutes by settingsManager.sleepTimerCustomMinutes.collectAsState(initial = 45)
    val sleepTimerStopAfterCurrent by settingsManager.sleepTimerStopAfterCurrent.collectAsState(initial = false)
    val playlist by playerViewModel.playlist.collectAsState()
    val lyrics by playerViewModel.lyrics.collectAsState()
    val currentLyricIndex by playerViewModel.currentLyricIndex.collectAsState()
    val showLyrics by playerViewModel.showLyrics.collectAsState()
    val showLyricTranslation by playerViewModel.showLyricTranslation.collectAsState()
    val showLyricPronunciation by playerViewModel.showLyricPronunciation.collectAsState()
    val lyricPageKeepScreenOn by settingsManager.lyricPageKeepScreenOn.collectAsState(initial = false)
    val favoriteSongKeys by playerViewModel.favoriteSongKeys.collectAsState()
    val sleepTimerEndRealtimeMs by playerViewModel.sleepTimerEndRealtimeMs.collectAsState()
    val stopAfterCurrentEnabled by playerViewModel.stopAfterCurrentEnabled.collectAsState()
    val currentLyricLine = lyrics.getOrNull(currentLyricIndex)
    val miniLyricLine = currentLyricLine
        ?.takeIf { it.hasMiniLyric() }
        ?: lyrics.firstOrNull { it.hasMiniLyric() }
    var menuExpanded by remember { mutableStateOf(false) }
    var songInfoExpanded by remember { mutableStateOf(false) }
    var queueExpanded by remember { mutableStateOf(false) }
    var artistChoices by remember { mutableStateOf<List<String>>(emptyList()) }
    var landscapeExpanded by rememberSaveable { mutableStateOf(false) }
    var dynamicCoverFailedPath by remember { mutableStateOf<String?>(null) }
    var hasVisualizerPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        )
    }
    val effectiveAudioVisualizerEnabled = immersiveAlbumCover &&
        audioVisualizerEnabled &&
        hasVisualizerPermission &&
        isPlaying &&
        !showLyrics &&
        !landscapeExpanded
    val visualizerPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasVisualizerPermission = granted
        scope.launch {
            settingsManager.setAudioVisualizerEnabled(granted)
        }
        if (!granted) Toast.makeText(context, context.getString(R.string.player_need_record_audio_permission), Toast.LENGTH_SHORT).show()
    }
    fun setAudioVisualizerEnabled(enabled: Boolean) {
        if (enabled && !immersiveAlbumCover) {
            Toast.makeText(context, context.getString(R.string.player_visualizer_immersive_only), Toast.LENGTH_SHORT).show()
            return
        }
        if (enabled && !hasVisualizerPermission) {
            visualizerPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        } else {
            scope.launch {
                settingsManager.setAudioVisualizerEnabled(enabled)
            }
        }
    }
    val dragDismissOffset = remember { Animatable(0f) }
    var dismissingPlayer by remember { mutableStateOf(false) }
    val topDragLimitPx = with(density) { 132.dp.toPx() }
    val dismissThresholdPx = with(density) { 240.dp.toPx() }
    val dismissVelocityThresholdPx = with(density) { 1250.dp.toPx() }
    val dismissTargetPx = remember(view.height) {
        view.height.takeIf { it > 0 }?.toFloat() ?: with(density) { 760.dp.toPx() }
    }
    LaunchedEffect(landscapeExpanded) {
        setPlayerSystemBars(context.findActivity(), view)
    }
    DisposableEffect(view, showLyrics, lyricPageKeepScreenOn) {
        val previousKeepScreenOn = view.keepScreenOn
        view.keepScreenOn = previousKeepScreenOn || (showLyrics && lyricPageKeepScreenOn)
        onDispose {
            view.keepScreenOn = previousKeepScreenOn
        }
    }
    val dismissProgress = (dragDismissOffset.value / dismissThresholdPx).coerceIn(0f, 1f)
    val dismissInProgress = dismissProgress > 0.001f || dismissingPlayer
    val dragCornerRadius = 30.dp * dismissProgress
    LaunchedEffect(openToken) {
        dismissingPlayer = false
        dragDismissOffset.snapTo(0f)
        onDismissProgressChange(0f)
    }
    SideEffect {
        onDismissProgressChange(dismissProgress)
    }
    DisposableEffect(Unit) {
        onDispose { onDismissProgressChange(0f) }
    }
    fun dismissWithPlayerMotion() {
        if (dismissingPlayer) return
        scope.launch {
            if (dismissingPlayer) return@launch
            dismissingPlayer = true
            dragDismissOffset.stop()
            dragDismissOffset.animateTo(
                targetValue = dismissTargetPx,
                animationSpec = tween(durationMillis = 260, easing = LinearOutSlowInEasing)
            )
            playerViewModel.setShowLyrics(false)
            onBack()
        }
    }

    val song = currentSong
    val isCurrentSongFavorite = song?.playlistIdentityKey()?.let { it in favoriteSongKeys } == true
    val embeddedCover by produceState<Bitmap?>(initialValue = null, song?.id, song?.dateModified, song?.fileSize) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                CoverLoadLimiter.run { song?.takeIf { it.coverUrl.isBlank() }?.let(playerViewModel::getCoverArtBitmap) }
            }.getOrNull()
        }
    }
    val paletteBitmap by produceState<Bitmap?>(initialValue = null, song?.id, song?.albumId, song?.coverUrl, song?.dateModified, song?.fileSize, embeddedCover) {
        value = withContext(Dispatchers.IO) {
            embeddedCover ?: song?.let { loadPaletteCoverBitmap(context, it) }
        }
    }
    val palette by produceState(initialValue = PlayerPalette.Default, paletteBitmap) {
        value = withContext(Dispatchers.Default) { PlayerPalette.from(paletteBitmap) }
    }
    val audioInfo by produceState<AudioInfo?>(initialValue = null, song?.id, song?.dateModified, song?.fileSize) {
        value = withContext(Dispatchers.IO) { song?.let(playerViewModel::getAudioInfo) }
    }
    val tagInfo by produceState<SongTagInfo?>(initialValue = null, song?.id, song?.dateModified, song?.fileSize) {
        value = withContext(Dispatchers.IO) { song?.let(playerViewModel::getSongTagInfo) }
    }
    val songAnnotation = tagInfo?.displayComment.orEmpty()
    var lyricShareInitialLine by remember { mutableStateOf<LyricLine?>(null) }
    fun openLyricSharePicker(line: LyricLine) {
        lyricShareInitialLine = line
    }
    fun shareSelectedLyrics(lines: List<LyricLine>) {
        shareLyricCard(
            context = context,
            song = song,
            lines = lines,
            cover = embeddedCover ?: paletteBitmap,
            backgroundColors = listOf(
                palette.top.toArgb(),
                palette.middle.toArgb(),
                palette.bottom.toArgb()
            ),
            annotation = songAnnotation,
            customInfo = lyricShareCustomInfo
        )
        lyricShareInitialLine = null
    }
    val neteaseInfo = remember(tagInfo?.neteaseKey) { decodeNeteaseKey(tagInfo?.neteaseKey.orEmpty()) }
    fun navigateToArtistOrChoose(artistText: String) {
        val artists = splitArtistNames(artistText)
            .filterNot { it.equals("Unknown", ignoreCase = true) }
            .distinctBy { it.tagIdentityKey() }
        when (artists.size) {
            0 -> Toast.makeText(context, context.getString(R.string.player_no_artist_jump), Toast.LENGTH_SHORT).show()
            1 -> onNavigateToArtist(artists.first())
            else -> artistChoices = artists
        }
    }
    fun openNetease(url: String?) {
        if (url.isNullOrBlank()) {
            Toast.makeText(context, context.getString(R.string.player_no_netease_jump), Toast.LENGTH_SHORT).show()
        } else {
            uriHandler.openUri(url)
        }
    }
    val playerPagerState = rememberPagerState(
        initialPage = PLAYER_PAGE_COVER,
        pageCount = { PLAYER_PAGE_COUNT }
    )
    LaunchedEffect(showLyrics) {
        if (immersiveAlbumCover) return@LaunchedEffect
        val target = if (showLyrics) PLAYER_PAGE_LYRICS else PLAYER_PAGE_COVER
        if (showLyrics && playerPagerState.currentPage != target && !playerPagerState.isScrollInProgress) {
            playerPagerState.animateScrollToPage(target)
        } else if (!showLyrics && playerPagerState.currentPage == PLAYER_PAGE_LYRICS && !playerPagerState.isScrollInProgress) {
            playerPagerState.animateScrollToPage(target)
        }
    }
    LaunchedEffect(playerPagerState.currentPage) {
        if (immersiveAlbumCover) return@LaunchedEffect
        val lyricPageVisible = playerPagerState.currentPage == PLAYER_PAGE_LYRICS
        if (showLyrics != lyricPageVisible) {
            playerViewModel.setShowLyrics(lyricPageVisible)
        }
    }
    LaunchedEffect(immersiveAlbumCover) {
        if (immersiveAlbumCover && playerPagerState.currentPage != PLAYER_PAGE_COVER) {
            playerViewModel.setShowLyrics(false)
            playerPagerState.scrollToPage(PLAYER_PAGE_COVER)
        }
    }
    BackHandler { dismissWithPlayerMotion() }

    @Composable
    fun CoverPageContent(
        onShowLyrics: () -> Unit,
        modifier: Modifier = Modifier
    ) {
        var actionMenuInitialPage by remember { mutableStateOf(PlayerActionSheetPage.Main) }
        CoverPlayerPage(
            context = context,
            song = song,
            embeddedCover = embeddedCover,
            annotation = songAnnotation,
            dynamicCoverFailedPath = dynamicCoverFailedPath,
            dynamicCoverEnabled = dynamicCoverEnabled,
            immersiveAlbumCover = immersiveAlbumCover,
            isPlaying = isPlaying,
            currentPositionProvider = currentPositionProvider,
            duration = duration,
            shuffleEnabled = shuffleEnabled,
            repeatMode = repeatMode,
            audioInfo = audioInfo,
            palette = palette,
            flowEffectMode = SettingsManager.PLAYER_FLOW_EFFECT_DARK,
            dynamicFlowEnabled = playerDynamicFlowEnabled,
            lyrics = lyrics,
            currentLyricIndex = currentLyricIndex,
            miniLyricLine = miniLyricLine,
            showTranslation = showLyricTranslation,
            showPronunciation = showLyricPronunciation,
            fontFamily = lyricFontFamily,
            fontWeight = lyricFontWeight,
            menuExpanded = menuExpanded,
            queueExpanded = queueExpanded,
            playlist = playlist,
            sleepTimerEndRealtimeMs = sleepTimerEndRealtimeMs,
            stopAfterCurrentEnabled = stopAfterCurrentEnabled,
            sleepTimerCustomMinutes = sleepTimerCustomMinutes,
            sleepTimerStopAfterCurrent = sleepTimerStopAfterCurrent,
            onDynamicCoverFailed = { dynamicCoverFailedPath = it },
            onToggleMenu = {
                actionMenuInitialPage = PlayerActionSheetPage.Main
                menuExpanded = !menuExpanded
            },
            onToggleFavorite = { playerViewModel.toggleCurrentSongFavorite() },
            onDismissMenu = { menuExpanded = false },
            onToggleQueue = { queueExpanded = !queueExpanded },
            onDismissQueue = { queueExpanded = false },
            onShowLyrics = onShowLyrics,
            onLyricLineClick = { line -> playerViewModel.seekTo(line.timeMs) },
            onLyricLineLongClick = ::openLyricSharePicker,
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
                val albumId = song?.albumIdentityId() ?: 0L
                if (albumId > 0L) onNavigateToAlbum(albumId)
                else Toast.makeText(context, context.getString(R.string.player_no_album_jump), Toast.LENGTH_SHORT).show()
            },
            onArtist = {
                menuExpanded = false
                navigateToArtistOrChoose(song?.artist.orEmpty())
            },
            onDownload = {
                menuExpanded = false
                val current = song
                if (current != null) {
                    enqueuePlayerDownload(context, current)
                    Toast.makeText(context, context.getString(R.string.player_download_started), Toast.LENGTH_SHORT).show()
                }
            },
            onLandscape = {
                menuExpanded = false
                landscapeExpanded = true
            },
            onSongInfo = {
                menuExpanded = false
                songInfoExpanded = true
            },
            onShareSong = {
                val current = song
                if (current != null) shareLocalSong(context, current)
                else Toast.makeText(context, context.getString(R.string.player_no_share_song), Toast.LENGTH_SHORT).show()
            },
            onOpenTimer = {
                actionMenuInitialPage = PlayerActionSheetPage.Timer
                menuExpanded = true
            },
            onOpenMetadataEditor = {
                val metadataOptions = song
                    ?.let { buildTagEditorOptions(context, it) }
                    .orEmpty()
                    .filter { it.kind == TagEditorOptionKind.Metadata }
                val preferredOption = metadataEditorId
                    .takeIf { it.isNotBlank() }
                    ?.let { id -> metadataOptions.firstOrNull { it.id == id } }
                if (preferredOption != null) {
                    launchTagEditorOption(context, preferredOption)
                    menuExpanded = false
                } else {
                    actionMenuInitialPage = PlayerActionSheetPage.MetadataEditor
                    menuExpanded = true
                }
            },
            onStopAfterCurrent = {
                scope.launch { settingsManager.setSleepTimerStopAfterCurrent(it) }
                playerViewModel.setStopAfterCurrentEnabled(it)
                Toast.makeText(
                    context,
                    if (it) context.getString(R.string.player_pause_after_current_on) else context.getString(R.string.player_pause_after_current_off),
                    Toast.LENGTH_SHORT
                ).show()
            },
            onTimer = { minutes ->
                scope.launch { settingsManager.setSleepTimerCustomMinutes(minutes) }
                if (sleepTimerStopAfterCurrent) {
                    playerViewModel.setStopAfterCurrentEnabled(true)
                }
                playerViewModel.startSleepTimer(minutes)
                Toast.makeText(context, context.getString(R.string.player_sleep_timer_minutes, minutes), Toast.LENGTH_SHORT).show()
            },
            onCustomTimerMinutes = { minutes ->
                scope.launch { settingsManager.setSleepTimerCustomMinutes(minutes) }
            },
            onCancelTimer = {
                playerViewModel.cancelSleepTimer()
                Toast.makeText(context, context.getString(R.string.player_sleep_timer_cancelled), Toast.LENGTH_SHORT).show()
            },
            onSpeed = { playerViewModel.setPlaybackSpeed(it) },
            onPitch = { playerViewModel.setPlaybackPitch(it) },
            playbackSpeed = playbackSpeed,
            playbackPitch = playbackPitch,
            isFavorite = isCurrentSongFavorite,
            audioSessionId = audioSessionId,
            visualizerEnabled = audioVisualizerEnabled,
            metadataEditorId = metadataEditorId,
            lyricTimingEditorId = lyricTimingEditorId,
            onVisualizerEnabled = ::setAudioVisualizerEnabled,
            actionMenuInitialPage = actionMenuInitialPage,
            modifier = modifier
        )
    }

    @Composable
    fun LyricsPageContent(
        onDismissLyrics: () -> Unit,
        enableSwipeDismiss: Boolean,
        modifier: Modifier = Modifier
    ) {
        LyricsPlayerPage(
            song = song,
            embeddedCover = embeddedCover,
            annotation = songAnnotation,
            lyrics = lyrics,
            currentLyricIndex = currentLyricIndex,
            currentPositionProvider = currentPositionProvider,
            showTranslation = showLyricTranslation,
            showPronunciation = showLyricPronunciation,
            keepScreenOn = lyricPageKeepScreenOn,
            lyricSourceMode = lyricSourceMode,
            fontFamily = lyricFontFamily,
            fontWeight = lyricFontWeight,
            fontScale = lyricFontScale,
            perspectiveEffect = lyricPerspectiveEffect,
            palette = palette,
            flowEffectMode = SettingsManager.PLAYER_FLOW_EFFECT_DARK,
            isPlaying = isPlaying,
            isFavorite = isCurrentSongFavorite,
            audioSessionId = audioSessionId,
            visualizerEnabled = effectiveAudioVisualizerEnabled,
            onLineClick = { line -> playerViewModel.seekTo(line.timeMs) },
            onLineDoubleClick = { playerViewModel.togglePlayPause() },
            onLineLongClick = ::openLyricSharePicker,
            onDismissLyrics = onDismissLyrics,
            onTogglePronunciation = {
                playerViewModel.setLyricPagePronunciation(!showLyricPronunciation)
            },
            onToggleTranslation = {
                playerViewModel.setLyricPageTranslation(!showLyricTranslation)
            },
            onToggleKeepScreenOn = {
                scope.launch { settingsManager.setLyricPageKeepScreenOn(!lyricPageKeepScreenOn) }
            },
            onToggleFavorite = { playerViewModel.toggleCurrentSongFavorite() },
            onFontScale = { scale ->
                scope.launch { settingsManager.setLyricFontScale((scale * 100f).toInt()) }
            },
            onLyricSourceMode = { mode ->
                playerViewModel.setLyricSourceMode(mode)
            },
            onArtist = {
                navigateToArtistOrChoose(song?.artist.orEmpty())
            },
            enableSwipeDismiss = enableSwipeDismiss,
            useBlurBackground = immersiveAlbumCover,
            modifier = modifier
        )
    }

    @Composable
    fun DetailPageContent(modifier: Modifier = Modifier) {
        PlayerDetailPage(
            song = song,
            tagInfo = tagInfo,
            neteaseInfo = neteaseInfo,
            onAlbum = {
                val albumId = song?.albumIdentityId() ?: 0L
                if (albumId > 0L) onNavigateToAlbum(albumId)
                else Toast.makeText(context, context.getString(R.string.player_no_album_jump), Toast.LENGTH_SHORT).show()
            },
            onComposer = { name -> onNavigateToMetadataCategory("composer", name) },
            onLyricist = { name -> onNavigateToMetadataCategory("lyricist", name) },
            onNeteaseSong = { openNetease(neteaseInfo?.musicId?.takeIf { it.isNotBlank() }?.let(::neteaseSongUrl)) },
            onNeteaseArtist = { id -> openNetease(neteaseArtistUrl(id)) },
            onNeteaseAlbum = { openNetease(neteaseInfo?.albumId?.takeIf { it.isNotBlank() }?.let(::neteaseAlbumUrl)) },
            modifier = modifier
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Transparent)
            .pointerInput(showLyrics, dismissingPlayer, dismissTargetPx, dismissThresholdPx) {
                var closeGesture = false
                var gestureOffset = 0f
                val velocityTracker = VelocityTracker()
                detectDragGestures(
                    onDragStart = { offset ->
                        closeGesture = !dismissingPlayer && offset.y <= topDragLimitPx
                        gestureOffset = dragDismissOffset.value
                        velocityTracker.resetTracking()
                        velocityTracker.addPosition(SystemClock.uptimeMillis(), offset)
                        if (closeGesture) {
                            scope.launch { dragDismissOffset.stop() }
                        }
                    },
                    onDrag = { change, dragAmount ->
                        if (!closeGesture) return@detectDragGestures
                        gestureOffset = (gestureOffset + if (dragAmount.y > 0f) {
                            dragAmount.y
                        } else {
                            dragAmount.y * 0.36f
                        }).coerceIn(0f, dismissTargetPx)
                        velocityTracker.addPosition(change.uptimeMillis, change.position)
                        scope.launch { dragDismissOffset.snapTo(gestureOffset) }
                        if (gestureOffset > 0f) change.consume()
                    },
                    onDragCancel = {
                        closeGesture = false
                        scope.launch {
                            dragDismissOffset.animateTo(
                                targetValue = 0f,
                                animationSpec = spring(
                                    dampingRatio = Spring.DampingRatioNoBouncy,
                                    stiffness = Spring.StiffnessMediumLow
                                )
                            )
                        }
                    },
                    onDragEnd = {
                        if (!closeGesture) return@detectDragGestures
                        closeGesture = false
                        val velocityY = velocityTracker.calculateVelocity().y
                        scope.launch {
                            if (gestureOffset >= dismissThresholdPx || velocityY >= dismissVelocityThresholdPx) {
                                if (!dismissingPlayer) {
                                    dismissingPlayer = true
                                    dragDismissOffset.animateTo(
                                        targetValue = dismissTargetPx,
                                        animationSpec = tween(durationMillis = 260, easing = LinearOutSlowInEasing)
                                    )
                                    playerViewModel.setShowLyrics(false)
                                    onBack()
                                }
                            } else {
                                dragDismissOffset.animateTo(
                                    targetValue = 0f,
                                    animationSpec = spring(
                                        dampingRatio = Spring.DampingRatioNoBouncy,
                                        stiffness = Spring.StiffnessMediumLow
                                    )
                                )
                            }
                        }
                    }
                )
            }
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    translationY = dragDismissOffset.value
                    scaleX = 1f
                    scaleY = 1f
                    transformOrigin = TransformOrigin(0.5f, 0f)
                    alpha = 1f
                }
                .clip(
                    RoundedCornerShape(
                        topStart = dragCornerRadius,
                        topEnd = dragCornerRadius
                    )
                )
        ) {
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
            if (immersiveAlbumCover) {
                ImmersiveCoverBackground(
                    palette = palette,
                    flowEffectMode = SettingsManager.PLAYER_FLOW_EFFECT_DARK,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                PlayerFlowBackground(
                    palette = palette,
                    flowEffectMode = SettingsManager.PLAYER_FLOW_EFFECT_DARK,
                    animate = playerDynamicFlowEnabled && !dismissInProgress && !isPlaying,
                    modifier = Modifier.fillMaxSize()
                )
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                colorStops = arrayOf(
                                    0.0f to Color.Black.copy(alpha = 0.08f),
                                    0.42f to Color.Black.copy(alpha = 0.18f),
                                    1.0f to Color.Black.copy(alpha = 0.34f)
                                )
                            )
                        )
                )
            }

            if (immersiveAlbumCover) {
                if (showLyrics) {
                    LyricsPageContent(
                        onDismissLyrics = { playerViewModel.setShowLyrics(false) },
                        enableSwipeDismiss = true,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    CoverPageContent(
                        onShowLyrics = { playerViewModel.setShowLyrics(true) },
                        modifier = Modifier.fillMaxSize()
                    )
                }
            } else {
                HorizontalPager(
                    state = playerPagerState,
                    modifier = Modifier.fillMaxSize(),
                    userScrollEnabled = !dismissingPlayer,
                    beyondViewportPageCount = 1
                ) { page ->
                    when (page) {
                        PLAYER_PAGE_COVER -> CoverPageContent(
                            onShowLyrics = {
                                scope.launch { playerPagerState.animateScrollToPage(PLAYER_PAGE_LYRICS) }
                            },
                            modifier = Modifier.fillMaxSize()
                        )
                        PLAYER_PAGE_LYRICS -> LyricsPageContent(
                            onDismissLyrics = {
                                scope.launch { playerPagerState.animateScrollToPage(PLAYER_PAGE_COVER) }
                            },
                            enableSwipeDismiss = false,
                            modifier = Modifier.fillMaxSize()
                        )
                        PLAYER_PAGE_DETAILS -> DetailPageContent(modifier = Modifier.fillMaxSize())
                    }
                }
            }

            if (artistChoices.isNotEmpty()) {
                Popup(
                    alignment = Alignment.BottomCenter,
                    onDismissRequest = { artistChoices = emptyList() },
                    properties = PopupProperties(focusable = true, dismissOnBackPress = true, dismissOnClickOutside = true)
                ) {
                    ArtistPickerSheet(
                        artists = artistChoices,
                        onArtistSelected = { artist ->
                            artistChoices = emptyList()
                            onNavigateToArtist(artist)
                        },
                        onDismiss = { artistChoices = emptyList() }
                    )
                }
            }

            if (songInfoExpanded && song != null) {
                WindowBottomSheet(
                    show = true,
                    enableNestedScroll = false,
                title = stringResource(R.string.player_song_info),
                    onDismissRequest = { songInfoExpanded = false }
                ) {
                    SongInfoSheet(
                        song = song,
                        audioInfoLoader = playerViewModel::getAudioInfo,
                        tagInfoLoader = playerViewModel::getSongTagInfo,
                        onDismiss = { songInfoExpanded = false }
                    )
                }
            }

            if (landscapeExpanded) {
                LandscapeLyricsOverlay(
                    song = song,
                    embeddedCover = embeddedCover,
                    annotation = songAnnotation,
                    lyrics = lyrics,
                    currentLyricIndex = currentLyricIndex,
                    currentPositionProvider = currentPositionProvider,
                    duration = duration,
                    shuffleEnabled = shuffleEnabled,
                    repeatMode = repeatMode,
                    showTranslation = showLyricTranslation,
                    showPronunciation = showLyricPronunciation,
                    fontFamily = lyricFontFamily,
                    fontWeight = lyricFontWeight,
                    palette = palette,
                    flowEffectMode = SettingsManager.PLAYER_FLOW_EFFECT_DARK,
                    isPlaying = isPlaying,
                    audioSessionId = audioSessionId,
                    visualizerEnabled = effectiveAudioVisualizerEnabled,
                    onLineClick = { line -> playerViewModel.seekTo(line.timeMs) },
                    onLineLongClick = ::openLyricSharePicker,
                    onSeek = { progress ->
                        if (duration > 0L) playerViewModel.seekTo((duration * progress).toLong())
                    },
                    onCyclePlaybackMode = { playerViewModel.cyclePlaybackMode() },
                    onPrevious = { playerViewModel.skipToPrevious() },
                    onPlayPause = { playerViewModel.togglePlayPause() },
                    onNext = { playerViewModel.skipToNext() },
                    onDismiss = { landscapeExpanded = false },
                    modifier = Modifier.fillMaxSize()
                )
            }
        }

        lyricShareInitialLine?.let { initialLine ->
            LyricSharePicker(
                song = song,
                lyrics = lyrics,
                initialLine = initialLine,
                cover = embeddedCover ?: paletteBitmap,
                backgroundColors = listOf(palette.top, palette.middle, palette.bottom),
                annotation = songAnnotation,
                customInfo = lyricShareCustomInfo,
                onDismiss = { lyricShareInitialLine = null },
                onShare = ::shareSelectedLyrics
            )
        }
    }
}

@Composable
private fun CoverPlayerPage(
    context: Context,
    song: Song?,
    embeddedCover: Bitmap?,
    annotation: String,
    dynamicCoverFailedPath: String?,
    dynamicCoverEnabled: Boolean,
    immersiveAlbumCover: Boolean,
    isPlaying: Boolean,
    currentPositionProvider: () -> Long,
    duration: Long,
    shuffleEnabled: Boolean,
    repeatMode: Int,
    audioInfo: AudioInfo?,
    palette: PlayerPalette,
    flowEffectMode: Int,
    dynamicFlowEnabled: Boolean,
    lyrics: List<com.ella.music.data.model.LyricLine>,
    currentLyricIndex: Int,
    miniLyricLine: com.ella.music.data.model.LyricLine?,
    showTranslation: Boolean,
    showPronunciation: Boolean,
    fontFamily: FontFamily?,
    fontWeight: FontWeight,
    menuExpanded: Boolean,
    queueExpanded: Boolean,
    playlist: List<Song>,
    sleepTimerEndRealtimeMs: Long?,
    stopAfterCurrentEnabled: Boolean,
    sleepTimerCustomMinutes: Int,
    sleepTimerStopAfterCurrent: Boolean,
    playbackSpeed: Float,
    playbackPitch: Float,
    isFavorite: Boolean,
    audioSessionId: Int,
    visualizerEnabled: Boolean,
    metadataEditorId: String,
    lyricTimingEditorId: String,
    onVisualizerEnabled: (Boolean) -> Unit,
    onDynamicCoverFailed: (String) -> Unit,
    onToggleMenu: () -> Unit,
    onToggleFavorite: () -> Unit,
    onDismissMenu: () -> Unit,
    onToggleQueue: () -> Unit,
    onDismissQueue: () -> Unit,
    onShowLyrics: () -> Unit,
    onLyricLineClick: (com.ella.music.data.model.LyricLine) -> Unit,
    onLyricLineLongClick: (com.ella.music.data.model.LyricLine) -> Unit,
    onSeek: (Float) -> Unit,
    onCyclePlaybackMode: () -> Unit,
    onPrevious: () -> Unit,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onQueueSongClick: (Int) -> Unit,
    onClearQueue: () -> Unit,
    onAlbum: () -> Unit,
    onArtist: () -> Unit,
    onDownload: () -> Unit,
    onLandscape: () -> Unit,
    onSongInfo: () -> Unit,
    onShareSong: () -> Unit,
    onOpenTimer: () -> Unit,
    onOpenMetadataEditor: () -> Unit,
    onStopAfterCurrent: (Boolean) -> Unit,
    onTimer: (Int) -> Unit,
    onCustomTimerMinutes: (Int) -> Unit,
    onCancelTimer: () -> Unit,
    onSpeed: (Float) -> Unit,
    onPitch: (Float) -> Unit,
    actionMenuInitialPage: PlayerActionSheetPage,
    modifier: Modifier = Modifier
) {
    val bluetoothDeviceName = rememberBluetoothOutputName()
    val dynamicCoverFile = if (dynamicCoverEnabled) {
        song
            ?.dynamicCoverVideoFile(context)
            ?.takeUnless { it.absolutePath == dynamicCoverFailedPath }
    } else {
        null
    }

    BoxWithConstraints(modifier = modifier) {
        val useWidePlayer = maxWidth > maxHeight && maxWidth >= 700.dp
        if (useWidePlayer) {
            LandscapeCoverPlayerPage(
                song = song,
                embeddedCover = embeddedCover,
                annotation = annotation,
                dynamicCoverFile = dynamicCoverFile,
                isPlaying = isPlaying,
                currentPositionProvider = currentPositionProvider,
                duration = duration,
                shuffleEnabled = shuffleEnabled,
                repeatMode = repeatMode,
                audioInfo = audioInfo,
                palette = palette,
                flowEffectMode = flowEffectMode,
                dynamicFlowEnabled = dynamicFlowEnabled,
                lyrics = lyrics,
                currentLyricIndex = currentLyricIndex,
                showTranslation = showTranslation,
                showPronunciation = showPronunciation,
                fontFamily = fontFamily,
                fontWeight = fontWeight,
                queueExpanded = queueExpanded,
                playlist = playlist,
                audioSessionId = audioSessionId,
                visualizerEnabled = visualizerEnabled,
                onDynamicCoverFailed = onDynamicCoverFailed,
                onToggleMenu = onToggleMenu,
                onToggleQueue = onToggleQueue,
                onDismissQueue = onDismissQueue,
                onShowLyrics = onShowLyrics,
                onLyricLineClick = onLyricLineClick,
                onLyricLineLongClick = onLyricLineLongClick,
                onSeek = onSeek,
                onCyclePlaybackMode = onCyclePlaybackMode,
                onPrevious = onPrevious,
                onPlayPause = onPlayPause,
                onNext = onNext,
                onQueueSongClick = onQueueSongClick,
                onClearQueue = onClearQueue,
                onLineClick = onShowLyrics,
                onArtist = onArtist,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                if (immersiveAlbumCover) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(1f),
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
                            .background(playerContentSurfaceBrush(palette, flowEffectMode))
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
                            PlayerSongMetaText(
                                song = song,
                                annotation = annotation,
                                titleFontSize = 22.sp,
                                artistFontSize = 14.sp,
                                artistAlpha = 0.54f,
                                onArtistClick = onArtist,
                                modifier = Modifier
                                    .weight(1f)
                                    .widthIn(max = 230.dp)
                            )
                            Spacer(modifier = Modifier.width(20.dp))
                            PlayerHeaderAction(
                                kind = PlayerHeaderActionKind.Favorite,
                                selected = isFavorite,
                                onClick = onToggleFavorite
                            )
                            PlayerHeaderAction(kind = PlayerHeaderActionKind.More, onClick = onToggleMenu)
                        }

                        if (miniLyricLine != null) {
                            Spacer(modifier = Modifier.height(8.dp))
                            MiniLyricsPreview(
                                lyrics = lyrics,
                                currentIndex = currentLyricIndex,
                                showTranslation = showTranslation,
                                showPronunciation = showPronunciation,
                                currentPositionProvider = currentPositionProvider,
                                isPlaying = isPlaying,
                                fontFamily = fontFamily,
                                fontWeight = fontWeight,
                                onLineClick = { onShowLyrics() },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(miniLyricsPreviewHeight(miniLyricLine, showTranslation, showPronunciation))
                                    .padding(vertical = 2.dp)
                            )
                        }

                        Spacer(modifier = Modifier.weight(1f))
                        PlayerProgressBlock(
                            currentPositionProvider = currentPositionProvider,
                            duration = duration,
                            audioInfo = audioInfo,
                            bluetoothDeviceName = bluetoothDeviceName,
                            palette = palette,
                            onSeek = onSeek
                        )
                        Spacer(modifier = Modifier.height(12.dp))
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
                            onClearQueue = onClearQueue,
                            modifier = Modifier.height(76.dp)
                        )
                        PositionedAudioVisualizer(
                            enabled = visualizerEnabled,
                            audioSessionId = audioSessionId,
                            isPlaying = isPlaying,
                            currentPositionProvider = currentPositionProvider,
                            accent = Color.White.copy(alpha = 0.86f),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(30.dp)
                        )
                    }
                } else {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .windowInsetsPadding(WindowInsets.statusBars)
                            .windowInsetsPadding(WindowInsets.navigationBars)
                            .padding(horizontal = 28.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Spacer(modifier = Modifier.height(22.dp))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(1f)
                                .clip(RoundedCornerShape(24.dp)),
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
                                    cornerRadius = 24.dp,
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            PlayerSongMetaText(
                                song = song,
                                annotation = annotation,
                                titleFontSize = 23.sp,
                                artistFontSize = 14.sp,
                                artistAlpha = 0.62f,
                                onArtistClick = onArtist,
                                modifier = Modifier.weight(1f)
                            )
                            Spacer(modifier = Modifier.width(18.dp))
                            PlayerHeaderAction(
                                kind = PlayerHeaderActionKind.Favorite,
                                selected = isFavorite,
                                onClick = onToggleFavorite
                            )
                        }

                        if (miniLyricLine != null) {
                            Spacer(modifier = Modifier.height(10.dp))
                            MiniLyricsPreview(
                                lyrics = lyrics,
                                currentIndex = currentLyricIndex,
                                showTranslation = showTranslation,
                                showPronunciation = showPronunciation,
                                currentPositionProvider = currentPositionProvider,
                                isPlaying = isPlaying,
                                fontFamily = fontFamily,
                                fontWeight = fontWeight,
                                onLineClick = { onShowLyrics() },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(miniLyricsPreviewHeight(miniLyricLine, showTranslation, showPronunciation))
                                    .padding(vertical = 2.dp)
                            )
                        } else {
                            Spacer(modifier = Modifier.height(12.dp))
                        }

                        Spacer(modifier = Modifier.weight(1f))
                        PlayerQuickActionRow(
                            onSongInfo = onSongInfo,
                            onShareSong = onShareSong,
                            onTimer = onOpenTimer,
                            onEditMetadata = onOpenMetadataEditor,
                            onMore = onToggleMenu,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        PlayerProgressBlock(
                            currentPositionProvider = currentPositionProvider,
                            duration = duration,
                            audioInfo = audioInfo,
                            bluetoothDeviceName = bluetoothDeviceName,
                            palette = palette,
                            onSeek = onSeek
                        )
                        Spacer(modifier = Modifier.height(12.dp))
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
                            onClearQueue = onClearQueue,
                            modifier = Modifier.height(92.dp)
                        )
                        Spacer(modifier = Modifier.height(20.dp))
                    }
                }
            }
        }

        if (menuExpanded) {
            WindowBottomSheet(
                show = true,
                enableNestedScroll = false,
                title = stringResource(R.string.player_more_actions),
                onDismissRequest = onDismissMenu
            ) {
                PlayerActionMenu(
                    modifier = Modifier.fillMaxWidth(),
                    song = song,
                    speed = playbackSpeed,
                    pitch = playbackPitch,
                    visualizerEnabled = visualizerEnabled,
                    visualizerAvailable = immersiveAlbumCover,
                    metadataEditorId = metadataEditorId,
                    lyricTimingEditorId = lyricTimingEditorId,
                    sleepTimerEndRealtimeMs = sleepTimerEndRealtimeMs,
                    stopAfterCurrentEnabled = stopAfterCurrentEnabled,
                    sleepTimerCustomMinutes = sleepTimerCustomMinutes,
                    sleepTimerStopAfterCurrent = sleepTimerStopAfterCurrent,
                    onClose = onDismissMenu,
                    onAlbum = onAlbum,
                    onArtist = onArtist,
                    onDownload = onDownload,
                    onLandscape = onLandscape,
                    onSongInfo = onSongInfo,
                    onStopAfterCurrent = onStopAfterCurrent,
                    onTimer = onTimer,
                    onCustomTimerMinutes = onCustomTimerMinutes,
                    onCancelTimer = onCancelTimer,
                    onSpeed = onSpeed,
                    onPitch = onPitch,
                    onVisualizerEnabled = onVisualizerEnabled,
                    initialPage = actionMenuInitialPage
                )
            }
        }
    }
}

@Composable
private fun LandscapeCoverPlayerPage(
    song: Song?,
    embeddedCover: Bitmap?,
    annotation: String,
    dynamicCoverFile: File?,
    isPlaying: Boolean,
    currentPositionProvider: () -> Long,
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
    fontWeight: FontWeight,
    queueExpanded: Boolean,
    playlist: List<Song>,
    audioSessionId: Int,
    visualizerEnabled: Boolean,
    flowEffectMode: Int,
    dynamicFlowEnabled: Boolean,
    onDynamicCoverFailed: (String) -> Unit,
    onToggleMenu: () -> Unit,
    onToggleQueue: () -> Unit,
    onDismissQueue: () -> Unit,
    onShowLyrics: () -> Unit,
    onLyricLineClick: (com.ella.music.data.model.LyricLine) -> Unit,
    onLyricLineLongClick: (com.ella.music.data.model.LyricLine) -> Unit,
    onSeek: (Float) -> Unit,
    onCyclePlaybackMode: () -> Unit,
    onPrevious: () -> Unit,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onQueueSongClick: (Int) -> Unit,
    onClearQueue: () -> Unit,
    onLineClick: () -> Unit,
    onArtist: () -> Unit,
    modifier: Modifier = Modifier
) {
    val bluetoothDeviceName = rememberBluetoothOutputName()
    Box(modifier = modifier.background(palette.middle)) {
        PlayerFlowBackground(
            palette = palette,
            flowEffectMode = flowEffectMode,
            animate = dynamicFlowEnabled && !isPlaying && !visualizerEnabled,
            modifier = Modifier.fillMaxSize()
        )
        if (dynamicFlowEnabled) {
            FluidLyricBackground(
                palette = palette,
                isPlaying = isPlaying,
                flowEffectMode = flowEffectMode,
                animate = dynamicFlowEnabled && !isPlaying && !visualizerEnabled,
                modifier = Modifier.fillMaxSize()
            )
        }
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
                        .fillMaxHeight(0.82f)
                        .aspectRatio(1f)
                        .clip(RoundedCornerShape(14.dp)),
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
                    PlayerSongMetaText(
                        song = song,
                        annotation = annotation,
                        titleFontSize = 24.sp,
                        artistFontSize = 14.sp,
                        artistAlpha = 0.56f,
                        onArtistClick = onArtist,
                        modifier = Modifier.weight(1f)
                    )
                    PlayerHeaderAction(kind = PlayerHeaderActionKind.More, onClick = onToggleMenu)
                }
                Spacer(modifier = Modifier.height(8.dp))
                WordLyricView(
                    lyrics = lyrics,
                    currentIndex = currentLyricIndex,
                    currentPositionMs = currentPositionProvider(),
                    isPlaying = isPlaying,
                    showTranslation = showTranslation,
                    showPronunciation = showPronunciation,
                    fontScale = 0.74f,
                    fontFamily = fontFamily,
                    fontWeight = fontWeight,
                    topSpacer = 24.dp,
                    bottomSpacer = 72.dp,
                    horizontalPadding = 6.dp,
                    onLineClick = onLyricLineClick,
                    onLineLongClick = onLyricLineLongClick,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                )
                PlayerProgressBlock(
                    currentPositionProvider = currentPositionProvider,
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
        PositionedAudioVisualizer(
            enabled = visualizerEnabled,
            audioSessionId = audioSessionId,
            isPlaying = isPlaying,
            currentPositionProvider = currentPositionProvider,
            accent = Color.White.copy(alpha = 0.72f),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .fillMaxWidth()
                .height(68.dp)
        )
    }
}

@Composable
private fun LyricsPlayerPage(
    song: Song?,
    embeddedCover: Bitmap?,
    annotation: String,
    lyrics: List<com.ella.music.data.model.LyricLine>,
    currentLyricIndex: Int,
    showTranslation: Boolean,
    showPronunciation: Boolean,
    keepScreenOn: Boolean,
    lyricSourceMode: Int,
    fontFamily: FontFamily?,
    fontWeight: FontWeight,
    fontScale: Float,
    perspectiveEffect: Boolean,
    palette: PlayerPalette,
    flowEffectMode: Int,
    currentPositionProvider: () -> Long,
    isPlaying: Boolean,
    isFavorite: Boolean,
    audioSessionId: Int,
    visualizerEnabled: Boolean,
    onLineClick: (com.ella.music.data.model.LyricLine) -> Unit,
    onLineDoubleClick: () -> Unit,
    onLineLongClick: (com.ella.music.data.model.LyricLine) -> Unit,
    onDismissLyrics: () -> Unit,
    onTogglePronunciation: () -> Unit,
    onToggleTranslation: () -> Unit,
    onToggleKeepScreenOn: () -> Unit,
    onToggleFavorite: () -> Unit,
    onFontScale: (Float) -> Unit,
    onLyricSourceMode: (Int) -> Unit,
    onArtist: () -> Unit,
    enableSwipeDismiss: Boolean,
    useBlurBackground: Boolean,
    modifier: Modifier = Modifier
) {
    var lyricMenuExpanded by remember { mutableStateOf(false) }
    var dismissDragX by remember { mutableFloatStateOf(0f) }

    val lyricBackgroundMotion = 0.42f

    val swipeDismissModifier = if (enableSwipeDismiss) {
        Modifier.pointerInput(onDismissLyrics) {
            detectDragGestures(
                onDragStart = { dismissDragX = 0f },
                onDrag = { change, dragAmount ->
                    if (abs(dragAmount.x) > abs(dragAmount.y)) {
                        dismissDragX += dragAmount.x
                        change.consume()
                    }
                },
                onDragCancel = { dismissDragX = 0f },
                onDragEnd = {
                    if (dismissDragX > 96.dp.toPx()) {
                        onDismissLyrics()
                    }
                    dismissDragX = 0f
                }
            )
        }
    } else {
        Modifier
    }

    Box(modifier = modifier.then(swipeDismissModifier)) {
        if (useBlurBackground) {
            PlayerBlurBackground(
                song = song,
                embeddedCover = embeddedCover,
                palette = palette,
                motion = lyricBackgroundMotion,
                isPlaying = isPlaying,
                modifier = Modifier.fillMaxSize()
            )
        }
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
                        .size(56.dp)
                        .clickable(onClick = onDismissLyrics)
                )
                Spacer(modifier = Modifier.width(12.dp))
                PlayerSongMetaText(
                    song = song,
                    annotation = annotation,
                    titleFontSize = 22.sp,
                    artistFontSize = 14.sp,
                    artistAlpha = 0.72f,
                    onArtistClick = onArtist,
                    modifier = Modifier
                        .weight(1f)
                        .widthIn(max = 230.dp)
                )
                Spacer(modifier = Modifier.width(20.dp))
                PlayerHeaderAction(
                    kind = PlayerHeaderActionKind.Favorite,
                    selected = isFavorite,
                    onClick = onToggleFavorite
                )
                PlayerHeaderAction(kind = PlayerHeaderActionKind.More, onClick = { lyricMenuExpanded = true })
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                WordLyricView(
                    lyrics = lyrics,
                    currentIndex = currentLyricIndex,
                    currentPositionMs = currentPositionProvider(),
                    isPlaying = isPlaying,
                    showTranslation = showTranslation,
                    showPronunciation = showPronunciation,
                    fontScale = fontScale,
                    fontFamily = fontFamily,
                    fontWeight = fontWeight,
                    topSpacer = 64.dp,
                    bottomSpacer = if (visualizerEnabled) 150.dp else 166.dp,
                    horizontalPadding = 0.dp,
                    lineHorizontalPadding = 0.dp,
                    perspectiveEffect = perspectiveEffect,
                    onLineClick = onLineClick,
                    onLineDoubleClick = onLineDoubleClick,
                    onLineLongClick = onLineLongClick,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }

        PositionedAudioVisualizer(
            enabled = visualizerEnabled,
            audioSessionId = audioSessionId,
            isPlaying = isPlaying,
            currentPositionProvider = currentPositionProvider,
            accent = Color.White.copy(alpha = 0.86f),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .fillMaxWidth()
                .height(42.dp)
        )

        if (lyricMenuExpanded) {
            WindowBottomSheet(
                show = true,
                enableNestedScroll = false,
                title = stringResource(R.string.player_lyrics_display),
                onDismissRequest = { lyricMenuExpanded = false }
            ) {
                LyricActionMenu(
                    showPronunciation = showPronunciation,
                    showTranslation = showTranslation,
                    keepScreenOn = keepScreenOn,
                    lyricSourceMode = lyricSourceMode,
                    fontScale = fontScale,
                    onTogglePronunciation = {
                        lyricMenuExpanded = false
                        onTogglePronunciation()
                    },
                    onToggleTranslation = {
                        lyricMenuExpanded = false
                        onToggleTranslation()
                    },
                    onToggleKeepScreenOn = {
                        lyricMenuExpanded = false
                        onToggleKeepScreenOn()
                    },
                    onLyricSourceMode = { mode ->
                        lyricMenuExpanded = false
                        onLyricSourceMode(mode)
                    },
                    onFontScale = onFontScale,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

@Composable
private fun PlayerDetailPage(
    song: Song?,
    tagInfo: SongTagInfo?,
    neteaseInfo: NeteaseKeyInfo?,
    onAlbum: () -> Unit,
    onComposer: (String) -> Unit,
    onLyricist: (String) -> Unit,
    onNeteaseSong: () -> Unit,
    onNeteaseArtist: (String) -> Unit,
    onNeteaseAlbum: () -> Unit,
    modifier: Modifier = Modifier
) {
    val composerNames = remember(tagInfo?.composer, song?.composer) {
        splitArtistNames(tagInfo?.composer?.ifBlank { song?.composer.orEmpty() }.orEmpty())
    }
    val lyricistNames = remember(tagInfo?.lyricist, song?.lyricist) {
        splitArtistNames(tagInfo?.lyricist?.ifBlank { song?.lyricist.orEmpty() }.orEmpty())
    }
    var showNeteaseArtistPicker by remember(neteaseInfo) { mutableStateOf(false) }
    val neteaseArtists = remember(neteaseInfo) {
        neteaseInfo?.artists.orEmpty().filter { it.id.isNotBlank() }
    }

    if (showNeteaseArtistPicker && neteaseArtists.isNotEmpty()) {
        WindowBottomSheet(
            show = true,
            onDismissRequest = { showNeteaseArtistPicker = false }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 22.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "选择网易云歌手",
                    color = MiuixTheme.colorScheme.onSurface,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.ExtraBold,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp)
                )
                neteaseArtists.forEach { artist ->
                    PlayerDetailArtistPickerRow(
                        title = artist.name.ifBlank { "ID ${artist.id}" },
                        onClick = {
                            showNeteaseArtistPicker = false
                            onNeteaseArtist(artist.id)
                        }
                    )
                }
            }
        }
    }

    LazyColumn(
        modifier = modifier
            .windowInsetsPadding(WindowInsets.statusBars)
            .windowInsetsPadding(WindowInsets.navigationBars),
        contentPadding = PaddingValues(horizontal = 28.dp, vertical = 36.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Text(
                text = "歌曲详情",
                color = Color.White,
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(14.dp))
            PlayerDetailInfoLine("歌曲", song?.title.orEmpty().ifBlank { "未知歌曲" })
            PlayerDetailInfoLine("歌手", song?.artist.orEmpty().ifBlank { "未知歌手" })
            PlayerDetailInfoLine("专辑", song?.album.orEmpty().ifBlank { "未知专辑" })
            tagInfo?.displayComment?.takeIf { it.isNotBlank() }?.let {
                PlayerDetailInfoLine("注释", it)
            }
            Spacer(modifier = Modifier.height(18.dp))
        }

        item {
            PlayerDetailActionRow(
                title = "专辑",
                summary = song?.album.orEmpty().ifBlank { "无专辑信息" },
                enabled = (song?.albumIdentityId() ?: 0L) > 0L,
                onClick = onAlbum
            )
        }

        composerNames.forEach { composer ->
            item(key = "composer_$composer") {
                PlayerDetailActionRow(
                    title = "作曲家",
                    summary = composer,
                    enabled = composer.isNotBlank(),
                    onClick = { onComposer(composer) }
                )
            }
        }

        lyricistNames.forEach { lyricist ->
            item(key = "lyricist_$lyricist") {
                PlayerDetailActionRow(
                    title = "作词家",
                    summary = lyricist,
                    enabled = lyricist.isNotBlank(),
                    onClick = { onLyricist(lyricist) }
                )
            }
        }

        if (neteaseInfo?.hasDecodedContent == true) {
            item {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "网易云",
                    color = Color.White.copy(alpha = 0.72f),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
            if (neteaseInfo.musicId.isNotBlank()) {
                item {
                    PlayerDetailActionRow(
                        title = "网易云歌曲页",
                        summary = neteaseInfo.musicName.ifBlank { neteaseInfo.musicId },
                        onClick = onNeteaseSong
                    )
                }
            }
            neteaseInfo.artists
                .joinToString(" / ") { it.name.ifBlank { it.id } }
                .takeIf { it.isNotBlank() }
                ?.let { artistSummary ->
                    item(key = "netease_artists") {
                        PlayerDetailActionRow(
                            title = "网易云歌手页",
                            summary = artistSummary,
                            enabled = neteaseArtists.isNotEmpty(),
                            onClick = {
                                if (neteaseArtists.size == 1) {
                                    onNeteaseArtist(neteaseArtists.first().id)
                                } else {
                                    showNeteaseArtistPicker = true
                                }
                            }
                        )
                    }
                }
            if (neteaseInfo.albumId.isNotBlank()) {
                item {
                    PlayerDetailActionRow(
                        title = "网易云专辑页",
                        summary = neteaseInfo.albumName.ifBlank { neteaseInfo.albumId },
                        onClick = onNeteaseAlbum
                    )
                }
            }
        }
    }
}

@Composable
private fun PlayerDetailInfoLine(label: String, value: String) {
    if (value.isBlank()) return
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = label,
            color = Color.White.copy(alpha = 0.44f),
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium
        )
        Text(
            text = value,
            color = Color.White.copy(alpha = 0.88f),
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun PlayerDetailActionRow(
    title: String,
    summary: String,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(Color.White.copy(alpha = if (enabled) 0.11f else 0.055f))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = Color.White.copy(alpha = if (enabled) 0.92f else 0.42f),
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = summary.ifBlank { "无可用信息" },
                color = Color.White.copy(alpha = if (enabled) 0.58f else 0.30f),
                fontSize = 12.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
        Text(
            text = "›",
            color = Color.White.copy(alpha = if (enabled) 0.72f else 0.24f),
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun PlayerDetailArtistPickerRow(
    title: String,
    onClick: () -> Unit
) {
    Text(
        text = title,
        color = MiuixTheme.colorScheme.onSurface,
        fontSize = 15.sp,
        fontWeight = FontWeight.Medium,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .background(MiuixTheme.colorScheme.surfaceContainer.copy(alpha = 0.55f))
            .padding(horizontal = 16.dp, vertical = 14.dp)
    )
}

@Composable
private fun LandscapeLyricsOverlay(
    song: Song?,
    embeddedCover: Bitmap?,
    annotation: String,
    lyrics: List<com.ella.music.data.model.LyricLine>,
    currentLyricIndex: Int,
    currentPositionProvider: () -> Long,
    duration: Long,
    shuffleEnabled: Boolean,
    repeatMode: Int,
    showTranslation: Boolean,
    showPronunciation: Boolean,
    fontFamily: FontFamily?,
    fontWeight: FontWeight,
    palette: PlayerPalette,
    flowEffectMode: Int,
    isPlaying: Boolean,
    audioSessionId: Int,
    visualizerEnabled: Boolean,
    onLineClick: (com.ella.music.data.model.LyricLine) -> Unit,
    onLineLongClick: (com.ella.music.data.model.LyricLine) -> Unit,
    onSeek: (Float) -> Unit,
    onCyclePlaybackMode: () -> Unit,
    onPrevious: () -> Unit,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val activity = LocalContext.current.findActivity()
    val view = LocalView.current
    DisposableEffect(activity) {
        val oldOrientation = activity?.requestedOrientation
        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        setPlayerSystemBars(activity, view)
        onDispose {
            if (oldOrientation != null) {
                activity.requestedOrientation = oldOrientation
            }
            setPlayerSystemBars(activity, view)
            view.post { setPlayerSystemBars(activity, view) }
        }
    }
    BackHandler(onBack = onDismiss)
    Box(modifier = modifier.background(palette.middle)) {
        FluidLyricBackground(
            palette = palette,
            isPlaying = isPlaying,
            flowEffectMode = flowEffectMode,
            modifier = Modifier.fillMaxSize()
        )
        Row(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.statusBars)
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(start = 34.dp, end = 78.dp, top = 22.dp, bottom = 28.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .weight(0.56f)
                    .widthIn(max = 360.dp),
                contentAlignment = Alignment.Center
            ) {
                AlbumArtView(
                    song = song,
                    embeddedCover = embeddedCover,
                    modifier = Modifier
                        .fillMaxHeight(0.72f)
                        .aspectRatio(1f)
                        .clip(RoundedCornerShape(16.dp))
                )
            }
            Spacer(modifier = Modifier.width(34.dp))
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .weight(1.44f)
            ) {
                Column(modifier = Modifier.fillMaxSize()) {
                    LandscapeSongTitle(
                        song = song,
                        annotation = annotation,
                        modifier = Modifier.fillMaxWidth()
                    )
                    LandscapeLyricShowcase(
                        lyrics = lyrics,
                        currentIndex = currentLyricIndex,
                        currentPositionProvider = currentPositionProvider,
                        showTranslation = showTranslation,
                        showPronunciation = showPronunciation,
                        fontFamily = fontFamily,
                        fontWeight = fontWeight,
                        onLineClick = onLineClick,
                        onLineLongClick = onLineLongClick,
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                    )
                    LandscapeProgressRow(
                        currentPositionProvider = currentPositionProvider,
                        duration = duration,
                        palette = palette,
                        onSeek = onSeek
                    )
                    LandscapeTransportControls(
                        isPlaying = isPlaying,
                        shuffleEnabled = shuffleEnabled,
                        repeatMode = repeatMode,
                        palette = palette,
                        onCyclePlaybackMode = onCyclePlaybackMode,
                        onPrevious = onPrevious,
                        onPlayPause = onPlayPause,
                        onNext = onNext
                    )
                }
            }
        }
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .windowInsetsPadding(WindowInsets.statusBars)
                .padding(top = 26.dp, end = 28.dp)
                .size(56.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.14f))
                .clickable(onClick = onDismiss),
            contentAlignment = Alignment.Center
        ) {
            CloseIcon(
                color = Color.White.copy(alpha = 0.92f),
                modifier = Modifier.size(26.dp)
            )
        }
    }

}

@Composable
private fun LandscapeSongTitle(
    song: Song?,
    annotation: String,
    modifier: Modifier = Modifier
) {
    PlayerSongMetaText(
        song = song,
        annotation = annotation,
        titleFontSize = 28.sp,
        artistFontSize = 16.sp,
        artistAlpha = 0.50f,
        fallbackTitle = "Ella Music",
        modifier = modifier.padding(end = 16.dp)
    )
}

@Composable
private fun PlayerSongMetaText(
    song: Song?,
    annotation: String,
    titleFontSize: TextUnit,
    artistFontSize: TextUnit,
    artistAlpha: Float,
    modifier: Modifier = Modifier,
    fallbackTitle: String = "未在播放",
    onArtistClick: (() -> Unit)? = null
) {
    val artist = song?.artist.orEmpty()
    val artistModifier = if (onArtistClick != null && artist.isNotBlank()) {
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .clickable(onClick = onArtistClick)
    } else {
        Modifier.fillMaxWidth()
    }
    Column(modifier = modifier) {
        PlayerSongTitleText(
            text = song?.title ?: fallbackTitle,
            fontSize = titleFontSize,
            fontWeight = FontWeight.ExtraBold,
            color = Color.White.copy(alpha = 0.96f),
            modifier = Modifier.fillMaxWidth()
        )
        if (annotation.isNotBlank()) {
            PlayerMarqueeText(
                text = annotation,
                fontSize = (artistFontSize.value * 0.82f).sp,
                fontWeight = FontWeight.Bold,
                color = Color.White.copy(alpha = (artistAlpha + 0.16f).coerceAtMost(0.82f)),
                modifier = Modifier.fillMaxWidth()
            )
        }
        PlayerMarqueeText(
            text = artist,
            fontSize = artistFontSize,
            fontWeight = FontWeight.Bold,
            color = Color.White.copy(alpha = artistAlpha),
            modifier = artistModifier
        )
    }
}

@Composable
private fun PlayerSongTitleText(
    text: String,
    fontSize: TextUnit,
    fontWeight: FontWeight,
    color: Color,
    modifier: Modifier = Modifier
) {
    Text(
        text = text,
        fontSize = fontSize,
        fontWeight = fontWeight,
        color = color,
        maxLines = 1,
        softWrap = false,
        overflow = TextOverflow.Clip,
        modifier = modifier.basicMarquee(iterations = Int.MAX_VALUE)
    )
}

@Composable
private fun PlayerMarqueeText(
    text: String,
    fontSize: TextUnit,
    fontWeight: FontWeight,
    color: Color,
    modifier: Modifier = Modifier
) {
    LyriconStyleMarqueeText(
        text = AnnotatedString(text),
        style = TextStyle(
            fontSize = fontSize,
            fontWeight = fontWeight,
            color = color
        ),
        enabled = true,
        textAlign = TextAlign.Start,
        modifier = modifier
    )
}

@Composable
private fun LyriconStyleMarqueeText(
    text: AnnotatedString,
    style: TextStyle,
    enabled: Boolean,
    textAlign: TextAlign,
    modifier: Modifier = Modifier,
    ghostSpacing: androidx.compose.ui.unit.Dp = 70.dp,
    speedDpPerSecond: Float = 40f,
    initialDelayMs: Long = 300L,
    loopDelayMs: Long = 700L
) {
    val density = LocalDensity.current
    val spacingPx = with(density) { ghostSpacing.toPx() }
    val speedPxPerMs = with(density) { speedDpPerSecond.dp.toPx() } / 1000f
    var elapsedMs by remember(text, enabled) { mutableFloatStateOf(0f) }

    LaunchedEffect(text, enabled) {
        elapsedMs = 0f
        if (!enabled) return@LaunchedEffect
        var lastFrameNanos = 0L
        while (isActive) {
            withFrameNanos { frameNanos ->
                if (lastFrameNanos != 0L) {
                    elapsedMs += (frameNanos - lastFrameNanos) / 1_000_000f
                }
                lastFrameNanos = frameNanos
            }
        }
    }

    Layout(
        content = {
            BasicText(
                text = text,
                style = style.copy(textAlign = TextAlign.Start),
                maxLines = 1,
                overflow = TextOverflow.Visible
            )
            BasicText(
                text = text,
                style = style.copy(textAlign = TextAlign.Start),
                maxLines = 1,
                overflow = TextOverflow.Visible
            )
        },
        modifier = modifier
            .fillMaxWidth()
            .clipToBounds()
    ) { measurables, constraints ->
        val textConstraints = constraints.copy(minWidth = 0, maxWidth = Constraints.Infinity)
        val primary = measurables[0].measure(textConstraints)
        val ghost = measurables[1].measure(textConstraints)
        val width = if (constraints.hasBoundedWidth) constraints.maxWidth else primary.width
        val height = primary.height
        val overflowPx = (primary.width - width).coerceAtLeast(0)
        val shouldScroll = enabled && overflowPx > 0
        val x = if (shouldScroll) {
            -lyriconMarqueeOffsetPx(
                elapsedMs = elapsedMs,
                textWidthPx = primary.width.toFloat(),
                spacingPx = spacingPx,
                speedPxPerMs = speedPxPerMs,
                initialDelayMs = initialDelayMs,
                loopDelayMs = loopDelayMs
            )
        } else {
            when (textAlign) {
                TextAlign.Center -> ((width - primary.width) / 2).coerceAtLeast(0).toFloat()
                TextAlign.End, TextAlign.Right -> (width - primary.width).coerceAtLeast(0).toFloat()
                else -> 0f
            }
        }
        val unit = primary.width + spacingPx

        layout(width, height) {
            primary.placeRelativeWithLayer(0, 0) {
                translationX = x
            }
            if (shouldScroll) {
                ghost.placeRelativeWithLayer(0, 0) {
                    translationX = x + unit
                }
            }
        }
    }
}

private fun lyriconMarqueeOffsetPx(
    elapsedMs: Float,
    textWidthPx: Float,
    spacingPx: Float,
    speedPxPerMs: Float,
    initialDelayMs: Long,
    loopDelayMs: Long
): Float {
    val activeMs = (elapsedMs - initialDelayMs).coerceAtLeast(0f)
    if (activeMs <= 0f) return 0f
    val unit = textWidthPx + spacingPx
    val travelMs = unit / speedPxPerMs.coerceAtLeast(0.001f)
    val cycleMs = travelMs + loopDelayMs
    val cycleTime = activeMs % cycleMs
    if (cycleTime >= travelMs) return 0f
    return (cycleTime * speedPxPerMs).coerceIn(0f, unit)
}

@Composable
private fun LandscapeLyricShowcase(
    lyrics: List<com.ella.music.data.model.LyricLine>,
    currentIndex: Int,
    currentPositionProvider: () -> Long,
    showTranslation: Boolean,
    showPronunciation: Boolean,
    fontFamily: FontFamily?,
    fontWeight: FontWeight,
    onLineClick: (com.ella.music.data.model.LyricLine) -> Unit,
    onLineLongClick: (com.ella.music.data.model.LyricLine) -> Unit,
    modifier: Modifier = Modifier
) {
    if (lyrics.isEmpty()) {
        Box(
            modifier = modifier.padding(top = 14.dp, bottom = 10.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            LandscapeLyricLine(
                line = null,
                currentPositionProvider = currentPositionProvider,
                showTranslation = showTranslation,
                showPronunciation = showPronunciation,
                fontFamily = fontFamily,
                fontWeight = fontWeight,
                primary = true,
                alpha = 0.9f,
                scale = 1f,
                onLineClick = onLineClick,
                onLineLongClick = onLineLongClick
            )
        }
        return
    }

    val safeIndex = currentIndex.coerceIn(0, lyrics.lastIndex)
    val listState = rememberLazyListState()
    LaunchedEffect(safeIndex, lyrics.size) {
        listState.animateScrollToItem((safeIndex - 1).coerceAtLeast(0))
    }

    LazyColumn(
        state = listState,
        modifier = modifier.padding(top = 8.dp, bottom = 4.dp),
        contentPadding = PaddingValues(top = 12.dp, bottom = 22.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        itemsIndexed(
            items = lyrics,
            key = { index, line -> "${line.timeMs}_$index" }
        ) { index, line ->
            val distance = abs(index - safeIndex)
            LandscapeLyricLine(
                line = line,
                currentPositionProvider = currentPositionProvider,
                showTranslation = showTranslation,
                showPronunciation = showPronunciation,
                fontFamily = fontFamily,
                fontWeight = fontWeight,
                primary = index == safeIndex,
                alpha = when (distance) {
                    0 -> 0.98f
                    1 -> 0.42f
                    2 -> 0.24f
                    else -> 0.14f
                },
                scale = when (distance) {
                    0 -> 1f
                    1 -> 0.86f
                    2 -> 0.78f
                    else -> 0.72f
                },
                onLineClick = onLineClick,
                onLineLongClick = onLineLongClick
            )
        }
    }
}

@Composable
private fun LandscapeLyricLine(
    line: com.ella.music.data.model.LyricLine?,
    currentPositionProvider: () -> Long,
    showTranslation: Boolean,
    showPronunciation: Boolean,
    fontFamily: FontFamily?,
    fontWeight: FontWeight,
    primary: Boolean,
    alpha: Float,
    scale: Float,
    onLineClick: (com.ella.music.data.model.LyricLine) -> Unit,
    onLineLongClick: (com.ella.music.data.model.LyricLine) -> Unit
) {
    val currentPositionMs = currentPositionProvider()
    if (line == null) {
        Text(
            text = "暂无歌词",
            fontSize = 26.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White.copy(alpha = alpha),
            fontFamily = fontFamily
        )
        return
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .pointerInput(line) {
                detectTapGestures(
                    onTap = { onLineClick(line) },
                    onLongPress = { onLineLongClick(line) }
                )
            },
        horizontalAlignment = Alignment.Start
    ) {
        val pronunciation = line.pronunciation.orEmpty()
        val translation = line.translation.orEmpty()
        if (showPronunciation && pronunciation.isNotBlank()) {
            Text(
                text = pronunciation,
                fontSize = (14 * scale).sp,
                fontWeight = FontWeight.Bold,
                color = Color.White.copy(alpha = alpha * 0.58f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                fontFamily = fontFamily
            )
        }
        Text(
            text = line.text.ifBlank { "♪" },
            fontSize = (if (primary) 26 else 22).sp * scale,
            lineHeight = (if (primary) 31 else 27).sp * scale,
            fontWeight = if (primary) FontWeight.ExtraBold else FontWeight.Bold,
            color = Color.White.copy(alpha = alpha),
            maxLines = if (primary) 3 else 2,
            overflow = TextOverflow.Ellipsis,
            fontFamily = fontFamily
        )
        if (showTranslation && translation.isNotBlank()) {
            Text(
                text = translation,
                fontSize = (if (primary) 17 else 14).sp * scale,
                lineHeight = (if (primary) 22 else 19).sp * scale,
                fontWeight = if (primary) FontWeight.Bold else FontWeight.SemiBold,
                color = Color.White.copy(alpha = if (primary) 0.74f else alpha * 0.72f),
                maxLines = if (primary) 2 else 1,
                overflow = TextOverflow.Ellipsis,
                fontFamily = fontFamily
            )
        }
    }
}

@Composable
private fun LandscapeProgressRow(
    currentPositionProvider: () -> Long,
    duration: Long,
    palette: PlayerPalette,
    onSeek: (Float) -> Unit
) {
    val currentPosition = currentPositionProvider()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = formatTime(currentPosition),
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White.copy(alpha = 0.72f)
        )
        GlowSeekBar(
            value = if (duration > 0) currentPosition.toFloat() / duration.toFloat() else 0f,
            onSeek = onSeek,
            accent = palette.accent,
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 12.dp)
        )
        Text(
            text = "-${formatTime((duration - currentPosition).coerceAtLeast(0L))}",
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White.copy(alpha = 0.72f)
        )
    }
}

@Composable
private fun LandscapeTransportControls(
    isPlaying: Boolean,
    shuffleEnabled: Boolean,
    repeatMode: Int,
    palette: PlayerPalette,
    onCyclePlaybackMode: () -> Unit,
    onPrevious: () -> Unit,
    onPlayPause: () -> Unit,
    onNext: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(58.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        PlayerTransportIconButton(onClick = onCyclePlaybackMode) {
            PlaybackModeIcon(shuffleEnabled = shuffleEnabled, repeatMode = repeatMode, accent = palette.accent)
        }
        PlayerTransportIconButton(onClick = onPrevious) {
            Icon(
                painter = painterResource(id = R.drawable.ic_skip_previous),
                contentDescription = stringResource(R.string.common_previous),
                tint = Color.White.copy(alpha = 0.92f),
                modifier = Modifier.size(28.dp)
            )
        }
        Box(
            modifier = Modifier
                .size(54.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.15f))
                .playerNoIndicationClick(onPlayPause),
            contentAlignment = Alignment.Center
        ) {
            CenteredPlayPauseGlyph(
                isPlaying = isPlaying,
                tint = Color.White.copy(alpha = 0.96f),
                modifier = Modifier.size(34.dp)
            )
        }
        PlayerTransportIconButton(onClick = onNext) {
            Icon(
                painter = painterResource(id = R.drawable.ic_skip_next),
                contentDescription = stringResource(R.string.common_next),
                tint = Color.White.copy(alpha = 0.92f),
                modifier = Modifier.size(28.dp)
            )
        }
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
            DefaultAlbumCover(modifier = Modifier.fillMaxSize())
        }
    }
}

@Composable
private fun SmallCover(song: Song?, embeddedCover: Bitmap?, modifier: Modifier = Modifier) {
    AlbumArtView(
        song = song,
        embeddedCover = embeddedCover,
        cornerRadius = 12.dp,
        modifier = modifier.clip(RoundedCornerShape(12.dp))
    )
}

private enum class PlayerHeaderActionKind {
    Favorite,
    More
}

@Composable
private fun Modifier.playerNoIndicationClick(onClick: () -> Unit): Modifier =
    clickable(
        interactionSource = remember { MutableInteractionSource() },
        indication = null,
        onClick = onClick
    )

@Composable
private fun PlayerQuickActionRow(
    onSongInfo: () -> Unit,
    onShareSong: () -> Unit,
    onTimer: () -> Unit,
    onEditMetadata: () -> Unit,
    onMore: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        PlayerQuickAction("信息", PlayerQuickActionKind.Info, onSongInfo)
        PlayerQuickAction("分享", PlayerQuickActionKind.Share, onShareSong)
        PlayerQuickAction("定时", PlayerQuickActionKind.Timer, onTimer)
        PlayerQuickAction("编辑", PlayerQuickActionKind.Edit, onEditMetadata)
        PlayerQuickAction("更多", PlayerQuickActionKind.More, onMore)
    }
}

private enum class PlayerQuickActionKind {
    Info,
    Share,
    Timer,
    Edit,
    More
}

@Composable
private fun PlayerQuickAction(
    label: String,
    kind: PlayerQuickActionKind,
    onClick: () -> Unit
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(42.dp)
            .playerNoIndicationClick(onClick)
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center
        ) {
            QuickActionIcon(
                kind = kind,
                color = Color.White.copy(alpha = 0.9f),
                modifier = Modifier.size(19.dp)
            )
        }
    }
}

@Composable
private fun QuickActionIcon(
    kind: PlayerQuickActionKind,
    color: Color,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        val stroke = size.minDimension * 0.10f
        val cx = size.width / 2f
        val cy = size.height / 2f
        when (kind) {
            PlayerQuickActionKind.Info -> {
                drawCircle(color = color, radius = size.minDimension * 0.42f, style = androidx.compose.ui.graphics.drawscope.Stroke(stroke, cap = StrokeCap.Round))
                drawLine(color, Offset(cx, size.height * 0.46f), Offset(cx, size.height * 0.70f), stroke, cap = StrokeCap.Round)
                drawCircle(color = color, radius = stroke * 0.68f, center = Offset(cx, size.height * 0.30f))
            }
            PlayerQuickActionKind.Share -> {
                val a = Offset(size.width * 0.26f, size.height * 0.58f)
                val b = Offset(size.width * 0.68f, size.height * 0.30f)
                val c = Offset(size.width * 0.70f, size.height * 0.74f)
                drawLine(color, a, b, stroke, cap = StrokeCap.Round)
                drawLine(color, a, c, stroke, cap = StrokeCap.Round)
                listOf(a, b, c).forEach { drawCircle(color = color, radius = stroke * 1.35f, center = it) }
            }
            PlayerQuickActionKind.Timer -> {
                drawCircle(color = color, radius = size.minDimension * 0.40f, style = androidx.compose.ui.graphics.drawscope.Stroke(stroke, cap = StrokeCap.Round))
                drawLine(color, Offset(cx, cy), Offset(cx, size.height * 0.30f), stroke, cap = StrokeCap.Round)
                drawLine(color, Offset(cx, cy), Offset(size.width * 0.66f, size.height * 0.58f), stroke, cap = StrokeCap.Round)
            }
            PlayerQuickActionKind.Edit -> {
                drawLine(color, Offset(size.width * 0.28f, size.height * 0.72f), Offset(size.width * 0.72f, size.height * 0.28f), stroke * 1.3f, cap = StrokeCap.Round)
                drawLine(color, Offset(size.width * 0.22f, size.height * 0.78f), Offset(size.width * 0.40f, size.height * 0.72f), stroke, cap = StrokeCap.Round)
            }
            PlayerQuickActionKind.More -> {
                listOf(0.25f, 0.5f, 0.75f).forEach { x ->
                    drawCircle(color = color, radius = stroke * 0.95f, center = Offset(size.width * x, cy))
                }
            }
        }
    }
}

@Composable
private fun PlayerHeaderAction(
    kind: PlayerHeaderActionKind,
    selected: Boolean = false,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(42.dp)
            .clip(CircleShape)
            .playerNoIndicationClick(onClick),
        contentAlignment = Alignment.Center
    ) {
        when (kind) {
            PlayerHeaderActionKind.Favorite -> HeartIcon(
                color = if (selected) Color(0xFFFF4D6D) else Color.White.copy(alpha = 0.92f),
                filled = selected,
                modifier = Modifier.size(25.dp)
            )
            PlayerHeaderActionKind.More -> MoreIcon(
                color = Color.White.copy(alpha = 0.90f),
                modifier = Modifier.size(24.dp)
            )
        }
    }
}

@Composable
private fun HeartIcon(
    color: Color,
    filled: Boolean,
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
        if (filled) {
            drawPath(path, color)
        } else {
            drawPath(
                path = path,
                color = color,
                style = androidx.compose.ui.graphics.drawscope.Stroke(
                    width = size.minDimension * 0.09f,
                    cap = StrokeCap.Round
                )
            )
        }
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
    currentPositionProvider: () -> Long,
    duration: Long,
    audioInfo: AudioInfo?,
    bluetoothDeviceName: String?,
    palette: PlayerPalette,
    onSeek: (Float) -> Unit
) {
    val context = LocalContext.current
    val currentPosition = currentPositionProvider()
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

    Toast.makeText(context, context.getString(R.string.player_media_output_unsupported), Toast.LENGTH_SHORT).show()
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
    onClearQueue: () -> Unit,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        PlayerTransportIconButton(onClick = onCyclePlaybackMode) {
            PlaybackModeIcon(shuffleEnabled = shuffleEnabled, repeatMode = repeatMode, accent = palette.accent)
        }
        PlayerTransportIconButton(onClick = onPrevious) {
            Icon(
                painter = painterResource(id = R.drawable.ic_skip_previous),
                contentDescription = stringResource(R.string.common_previous),
                tint = Color.White.copy(alpha = 0.92f),
                modifier = Modifier.size(38.dp)
            )
        }
        Box(
            modifier = Modifier
                .size(64.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.18f))
                .playerNoIndicationClick(onPlayPause),
            contentAlignment = Alignment.Center
        ) {
            CenteredPlayPauseGlyph(
                isPlaying = isPlaying,
                tint = Color.White.copy(alpha = 0.96f),
                modifier = Modifier.size(if (isPlaying) 38.dp else 40.dp)
            )
        }
        PlayerTransportIconButton(onClick = onNext) {
            Icon(
                painter = painterResource(id = R.drawable.ic_skip_next),
                contentDescription = stringResource(R.string.common_next),
                tint = Color.White.copy(alpha = 0.92f),
                modifier = Modifier.size(38.dp)
            )
        }
        Box(contentAlignment = Alignment.Center) {
            PlayerTransportIconButton(onClick = onToggleQueue) {
                QueueListIcon(
                    color = Color.White.copy(alpha = 0.58f),
                    modifier = Modifier.size(28.dp)
                )
            }
            if (queueExpanded) {
                WindowBottomSheet(
                    show = true,
                    enableNestedScroll = false,
                    title = "当前播放列表",
                    onDismissRequest = onDismissQueue
                ) {
                    PlayerQueueMenu(
                        playlist = playlist,
                        currentSongId = currentSongId,
                        onSongClick = onQueueSongClick,
                        onClearQueue = onClearQueue,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
}

@Composable
private fun PlayerTransportIconButton(
    onClick: () -> Unit,
    content: @Composable BoxScope.() -> Unit
) {
    Box(
        modifier = Modifier
            .size(52.dp)
            .playerNoIndicationClick(onClick),
        contentAlignment = Alignment.Center,
        content = content
    )
}

@Composable
private fun CenteredPlayPauseGlyph(
    isPlaying: Boolean,
    tint: Color,
    modifier: Modifier = Modifier
) {
    Icon(
        painter = painterResource(id = if (isPlaying) R.drawable.ic_player_pause else R.drawable.ic_player_play),
        contentDescription = if (isPlaying) stringResource(R.string.common_pause) else stringResource(R.string.common_play),
        tint = tint,
        modifier = modifier
    )
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
private fun QueueListIcon(
    color: Color,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        val stroke = 3.dp.toPx()
        val startX = size.width * 0.22f
        val endX = size.width * 0.78f
        listOf(0.30f, 0.50f, 0.70f).forEach { yFraction ->
            drawLine(
                color = color,
                start = Offset(startX, size.height * yFraction),
                end = Offset(endX, size.height * yFraction),
                strokeWidth = stroke,
                cap = StrokeCap.Round
            )
        }
    }
}

@Composable
private fun LyricActionMenu(
    showPronunciation: Boolean,
    showTranslation: Boolean,
    keepScreenOn: Boolean,
    lyricSourceMode: Int,
    fontScale: Float,
    onTogglePronunciation: () -> Unit,
    onToggleTranslation: () -> Unit,
    onToggleKeepScreenOn: () -> Unit,
    onLyricSourceMode: (Int) -> Unit,
    onFontScale: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .navigationBarsPadding()
            .padding(horizontal = 18.dp, vertical = 10.dp)
    ) {
        PlayerActionMenuItem(
            text = if (showPronunciation) "隐藏注音" else "显示注音",
            onClick = onTogglePronunciation
        )
        PlayerActionMenuItem(
            text = if (showTranslation) "隐藏翻译" else "显示翻译",
            onClick = onToggleTranslation
        )
        PlayerActionMenuItem(
            text = if (keepScreenOn) "关闭歌词页常亮" else "开启歌词页常亮",
            onClick = onToggleKeepScreenOn
        )
        Spacer(modifier = Modifier.height(10.dp))
        Text(
            text = "歌词字号",
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
        )
        DottedValueSlider(
            value = fontScale.coerceIn(0.75f, 1.30f),
            valueRange = 0.75f..1.30f,
            steps = 11,
            label = "${(fontScale.coerceIn(0.75f, 1.30f) * 100f).toInt()}%",
            onValueChange = onFontScale,
            modifier = Modifier
                .fillMaxWidth()
                .height(82.dp)
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "歌词来源",
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
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
            .background(
                if (selected) MiuixTheme.colorScheme.primary.copy(alpha = 0.16f)
                else MiuixTheme.colorScheme.surfaceContainer.copy(alpha = 0.72f)
            )
            .clickable(onClick = onClick)
            .padding(vertical = 11.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = if (selected) "✓ $text" else text,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            color = if (selected) MiuixTheme.colorScheme.primary else MiuixTheme.colorScheme.onSurfaceVariantSummary
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
private fun PlayerFlowBackground(
    palette: PlayerPalette,
    flowEffectMode: Int,
    animate: Boolean = true,
    modifier: Modifier = Modifier
) {
    LaunchedEffect(animate) {
        Log.d("PlayerScreenPerf", "flow background ${if (animate) "animated" else "static"}")
    }
    val sweepDrift = if (animate) {
        val transition = rememberInfiniteTransition(label = "player_flow_background")
        val value by transition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 46_000, easing = LinearEasing),
                repeatMode = RepeatMode.Restart
            ),
            label = "player_flow_background_drift"
        )
        value
    } else {
        0f
    }

    Canvas(modifier = modifier.background(palette.middle)) {
        val w = size.width
        val h = size.height
        val maxSide = max(w, h)
        val baseTop = palette.top.boosted().lighten(0.18f)
        val baseMid = palette.middle.boosted().lighten(0.12f)
        val baseBottom = palette.bottom.boosted().lighten(0.08f)

        drawRect(
            brush = Brush.verticalGradient(
                colors = listOf(
                    baseTop.copy(alpha = 0.96f),
                    baseMid.copy(alpha = 0.98f),
                    baseBottom.copy(alpha = 1f)
                )
            )
        )

        drawRect(
            brush = Brush.linearGradient(
                colors = listOf(
                    Color.White.copy(alpha = 0.10f),
                    palette.accent.lighten(0.20f).copy(alpha = 0.08f),
                    Color.Transparent
                ),
                start = Offset(0f, 0f),
                end = Offset(w, h * 0.72f)
            )
        )
        drawRect(
            brush = Brush.verticalGradient(
                colorStops = arrayOf(
                    0.0f to Color.White.copy(alpha = 0.06f),
                    0.48f to Color.Transparent,
                    1.0f to Color.Black.copy(alpha = 0.28f)
                )
            )
        )

        val sweepStart = Offset((-0.36f + sweepDrift * 1.72f) * w, -0.08f * h)
        val sweepEnd = Offset((0.12f + sweepDrift * 1.72f) * w, 1.08f * h)
        drawRect(
            brush = Brush.linearGradient(
                colorStops = arrayOf(
                    0.0f to Color.Transparent,
                    0.42f to Color.Transparent,
                    0.50f to Color.White.copy(alpha = 0.08f),
                    0.58f to Color.Transparent,
                    1.0f to Color.Transparent
                ),
                start = sweepStart,
                end = sweepEnd
            )
        )
        drawRect(
            brush = Brush.verticalGradient(
                colors = listOf(
                    Color.White.copy(alpha = 0.08f),
                    Color.Transparent
                )
            )
        )
        drawRect(
            brush = Brush.verticalGradient(
                colors = listOf(
                    Color.Transparent,
                    Color.Black.copy(alpha = 0.24f)
                )
            )
        )
    }
}

private fun playerContentSurfaceBrush(
    palette: PlayerPalette,
    flowEffectMode: Int
): Brush {
    return Brush.verticalGradient(
        colorStops = arrayOf(
            0.0f to palette.middle.copy(alpha = 0.70f),
            0.16f to palette.middle.copy(alpha = 0.82f),
            1.0f to palette.middle.copy(alpha = 0.90f)
        )
    )
}

private fun loadPaletteCoverBitmap(context: Context, song: Song): Bitmap? {
    return runCatching {
        when {
            song.coverUrl.isNotBlank() -> URL(song.coverUrl).openStream().use { input ->
                BitmapFactory.decodeStream(input)
            }
            song.albumId > 0L -> context.contentResolver
                .openInputStream(Uri.parse("content://media/external/audio/albumart/${song.albumId}"))
                ?.use { input -> BitmapFactory.decodeStream(input) }
            else -> null
        }?.scaledForPalette()
    }.getOrNull()
}

private fun Bitmap.scaledForPalette(): Bitmap {
    val longest = max(width, height)
    if (longest <= 480) return this
    val scale = 480f / longest.toFloat()
    return Bitmap.createScaledBitmap(
        this,
        (width * scale).toInt().coerceAtLeast(1),
        (height * scale).toInt().coerceAtLeast(1),
        true
    )
}

@Composable
private fun FluidLyricBackground(
    palette: PlayerPalette,
    isPlaying: Boolean,
    flowEffectMode: Int = SettingsManager.PLAYER_FLOW_EFFECT_DARK,
    animate: Boolean = false,
    modifier: Modifier = Modifier
) {
    LaunchedEffect(animate) {
        Log.d("PlayerScreenPerf", "flow background ${if (animate) "animated" else "static"}")
    }
    val drift = if (animate) {
        val transition = rememberInfiniteTransition(label = "fluid_lyric_background")
        val value by transition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 18_000, easing = LinearEasing),
                repeatMode = RepeatMode.Restart
            ),
            label = "fluid_lyric_background_drift"
        )
        value
    } else {
        0.36f
    }
    val pulse = if (animate && isPlaying) {
        val transition = rememberInfiniteTransition(label = "fluid_lyric_background_pulse")
        val value by transition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 1_800, easing = LinearEasing),
                repeatMode = RepeatMode.Restart
            ),
            label = "fluid_lyric_background_pulse"
        )
        0.5f + 0.5f * kotlin.math.sin(value * kotlin.math.PI.toFloat() * 2f)
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
    val movingScale = 2.90f
    val movingOffset = 0f
    LaunchedEffect(coverModel, isPlaying) {
        Log.d("PlayerScreenPerf", "blur background static")
    }

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
                        translationX = movingOffset
                        translationY = -movingOffset * 0.65f
                        alpha = 0.78f
                    }
                    .blur(48.dp),
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
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val currentIndex = remember(playlist, currentSongId) {
        playlist.indexOfFirst { it.id == currentSongId }
    }
    LaunchedEffect(currentIndex) {
        if (currentIndex >= 0) {
            listState.scrollToItem(currentIndex)
        }
    }

    Column(
        modifier = modifier
            .navigationBarsPadding()
            .padding(horizontal = 18.dp, vertical = 8.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Spacer(modifier = Modifier.weight(1f))
            if (playlist.isNotEmpty()) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .playerNoIndicationClick {
                        if (currentIndex >= 0) {
                            scope.launch { listState.animateScrollToItem(currentIndex) }
                        }
                    },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_my_location),
                        contentDescription = "定位当前歌曲",
                        tint = MiuixTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .playerNoIndicationClick(onClearQueue),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_delete),
                        contentDescription = "清空播放列表",
                        tint = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
        if (playlist.isEmpty()) {
            Text(
                text = "暂无歌曲",
                fontSize = 13.sp,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 18.dp)
            )
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier.heightIn(max = 420.dp)
            ) {
                itemsIndexed(playlist, key = { _, item -> item.id }) { index, item ->
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(
                                if (item.id == currentSongId) MiuixTheme.colorScheme.primary.copy(alpha = 0.12f)
                                else Color.Transparent
                            )
                            .clickable { onSongClick(index) }
                            .padding(horizontal = 14.dp, vertical = 10.dp)
                    ) {
                        Text(
                            text = item.title,
                            fontSize = 13.sp,
                            fontWeight = if (item.id == currentSongId) FontWeight.Bold else FontWeight.Medium,
                            color = if (item.id == currentSongId) MiuixTheme.colorScheme.primary else MiuixTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = item.artist,
                            fontSize = 11.sp,
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

private fun miniLyricsPreviewHeight(
    line: LyricLine?,
    showTranslation: Boolean,
    showPronunciation: Boolean
) = when (line?.miniVisiblePartCount(showTranslation, showPronunciation) ?: 1) {
    0, 1 -> 154.dp
    2 -> 184.dp
    3 -> 220.dp
    else -> 252.dp
}

@Composable
private fun MiniLyricsPreview(
    lyrics: List<com.ella.music.data.model.LyricLine>,
    currentIndex: Int,
    showTranslation: Boolean,
    showPronunciation: Boolean,
    currentPositionProvider: () -> Long,
    isPlaying: Boolean,
    fontFamily: FontFamily? = null,
    fontWeight: FontWeight = FontWeight.ExtraBold,
    onLineClick: (com.ella.music.data.model.LyricLine) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val currentPositionMs = currentPositionProvider()
    val safeIndex = currentIndex.takeIf { it in lyrics.indices }
        ?: lyrics.indexOfFirst { it.hasMiniLyric() }.takeIf { it >= 0 }
        ?: return
    val usesBilingualPreview = showTranslation && lyrics.any {
        !it.translation.isNullOrBlank() || !it.backgroundTranslation.isNullOrBlank()
    }
    val sideLineCount = if (usesBilingualPreview) 1 else 2
    val previousIndices = lyrics.indices
        .asSequence()
        .filter { it < safeIndex && lyrics[it].hasMiniLyric() }
        .toList()
        .takeLast(sideLineCount)
    val nextIndices = lyrics.indices
        .asSequence()
        .filter { it > safeIndex && lyrics[it].hasMiniLyric() }
        .take(sideLineCount)
        .toList()
    val previewItems = previousIndices + safeIndex + nextIndices
    val smoothPositionMs = rememberSmoothMiniLyricPosition(
        currentPositionMs = currentPositionMs,
        isPlaying = isPlaying,
        anchorKey = lyrics.getOrNull(safeIndex)?.miniLyricRenderKey() ?: safeIndex
    )

    val maxVisiblePartCount = previewItems.maxOfOrNull { index ->
        lyrics[index].miniVisiblePartCount(
            showTranslation = showTranslation,
            showPronunciation = showPronunciation
        )
    } ?: 1

    val miniLineSpacing = when {
        maxVisiblePartCount <= 2 -> 5.dp
        maxVisiblePartCount == 3 -> 7.dp
        else -> 9.dp
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .graphicsLayer {
                compositingStrategy = CompositingStrategy.Offscreen
            }
            .drawWithCache {
                val fade = Brush.verticalGradient(
                    0f to Color.Transparent,
                    0.10f to Color.Black,
                    0.90f to Color.Black,
                    1f to Color.Transparent
                )
                onDrawWithContent {
                    drawContent()
                    drawRect(fade, blendMode = BlendMode.DstIn)
                }
            },
        verticalArrangement = Arrangement.spacedBy(
            space = miniLineSpacing,
            alignment = Alignment.CenterVertically
        )
    ) {
        previewItems.forEach { index ->
            val line = lyrics[index]
            val isActive = index == safeIndex

            val distance = kotlin.math.abs(index - safeIndex)
            val alpha by animateFloatAsState(
                targetValue = when {
                    distance == 0 -> 0.86f
                    distance == 1 -> 0.66f
                    else -> 0.42f
                },
                animationSpec = tween(durationMillis = 220, easing = LinearOutSlowInEasing),
                label = "mini_lyric_alpha"
            )
            val scale by animateFloatAsState(
                targetValue = 1f,
                animationSpec = tween(durationMillis = 240, easing = LinearOutSlowInEasing),
                label = "mini_lyric_scale"
            )

            MiniLyricBlock(
                line = line,
                showTranslation = showTranslation,
                showPronunciation = showPronunciation,
                currentPositionMs = smoothPositionMs,
                active = isActive,
                isPlaying = isPlaying,
                fontFamily = fontFamily,
                fontWeight = fontWeight,
                modifier = Modifier
                    .fillMaxWidth()
                    .graphicsLayer {
                        this.alpha = alpha
                        scaleX = scale
                        scaleY = scale
                    }
                    .clickable { onLineClick(line) }
                    .padding(vertical = 1.dp)
            )
        }
    }
}

@Composable
private fun MiniLyricBlock(
    line: com.ella.music.data.model.LyricLine,
    showTranslation: Boolean,
    showPronunciation: Boolean,
    currentPositionMs: Long = 0L,
    active: Boolean = true,
    isPlaying: Boolean = true,
    fontFamily: FontFamily? = null,
    fontWeight: FontWeight = FontWeight.ExtraBold,
    modifier: Modifier = Modifier
) {
    val main = line.text.takeIf { it.isNotBlank() && !it.isMusicSymbolOnly() }
    val pronunciation = line.pronunciation?.takeIf { showPronunciation && it.isNotBlank() }
    val background = line.backgroundText?.takeIf { it.isNotBlank() && !it.isMusicSymbolOnly() }
    val translation = line.translation?.takeIf { showTranslation && it.isNotBlank() }
    val backgroundTranslation = line.backgroundTranslation?.takeIf { showTranslation && it.isNotBlank() }
    val backgroundAfterMain = line.isMiniBackgroundAfterMain()
    val visiblePartCount = line.miniVisiblePartCount(
        showTranslation = showTranslation,
        showPronunciation = showPronunciation
    )
    val denseTtmlLayout = visiblePartCount >= 4
    val longest = listOfNotNull(
        pronunciation,
        main,
        translation,
        background,
        backgroundTranslation
    ).maxOfOrNull { it.length } ?: 0
    val baseMainSize = when {
        longest > 72 -> 11.sp
        longest > 54 -> 12.sp
        longest > 38 -> 13.sp
        longest > 26 -> 14.sp
        else -> 16.sp
    }
    val mainSize = if (denseTtmlLayout) {
        baseMainSize.value.coerceAtMost(13f).sp
    } else {
        baseMainSize
    }
    val baseSecondarySize = when {
        longest > 72 -> 9.sp
        longest > 54 -> 10.sp
        else -> 12.sp
    }
    val secondarySize = if (denseTtmlLayout) {
        baseSecondarySize.value.coerceAtMost(10f).sp
    } else {
        baseSecondarySize
    }
    val pronunciationSize = if (denseTtmlLayout) 9.sp else if (secondarySize.value <= 10f) 9.sp else 11.sp
    val activeMainSize = if (denseTtmlLayout) mainSize else (mainSize.value + 1f).sp
    val backgroundSize = if (denseTtmlLayout) 10.sp else if (mainSize.value <= 12f) 10.sp else 13.sp
    val backgroundTranslationSize = if (denseTtmlLayout) 9.sp else if (secondarySize.value <= 10f) 9.sp else 11.sp

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(1.dp, Alignment.CenterVertically),
        horizontalAlignment = line.previewHorizontalAlignment()
    ) {
        if (pronunciation != null) {
            if (active && line.pronunciationWords.isNotEmpty()) {
                MiniWordText(
                    text = pronunciation,
                    words = line.pronunciationWords,
                    currentPositionMs = currentPositionMs,
                    isPlaying = isPlaying,
                    fontSize = pronunciationSize,
                    fontFamily = fontFamily,
                    fontWeight = fontWeight.softenedPlayerLyricWeight(),
                    textAlign = line.previewTextAlign(),
                    pendingAlpha = 0.42f,
                    sungAlpha = 0.62f,
                    currentAlpha = 0.82f
                )
            } else {
                MiniPlainLyricText(
                    text = pronunciation,
                    fontSize = pronunciationSize,
                    fontFamily = fontFamily,
                    fontWeight = fontWeight.softenedPlayerLyricWeight(),
                    color = Color.White.copy(alpha = if (active) 0.48f else 0.34f),
                    textAlign = line.previewTextAlign(),
                    active = active,
                    isPlaying = isPlaying,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
        if (!backgroundAfterMain) {
            MiniBackgroundLyricBlock(
                line = line,
                background = background,
                backgroundTranslation = backgroundTranslation,
                currentPositionMs = currentPositionMs,
                active = active,
                isPlaying = isPlaying,
                fontFamily = fontFamily,
                fontWeight = fontWeight,
                backgroundSize = backgroundSize,
                backgroundTranslationSize = backgroundTranslationSize
            )
        }
        if (main != null) {
            if (active && line.words.isNotEmpty()) {
                MiniWordText(
                    text = main,
                    words = line.words,
                    currentPositionMs = currentPositionMs,
                    isPlaying = isPlaying,
                    fontSize = activeMainSize,
                    fontFamily = fontFamily,
                    fontWeight = fontWeight,
                    textAlign = line.previewTextAlign(),
                    maxLines = 3
                )
            } else {
                MiniPlainLyricText(
                    text = main,
                    fontSize = if (active) activeMainSize else mainSize,
                    fontFamily = fontFamily,
                    fontWeight = if (active) fontWeight else fontWeight.softenedPlayerLyricWeight(),
                    color = Color.White.copy(alpha = if (active) 0.90f else 0.70f),
                    textAlign = line.previewTextAlign(),
                    active = active,
                    isPlaying = isPlaying,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
        if (translation != null) {
            MiniPlainLyricText(
                text = translation,
                fontSize = secondarySize,
                fontFamily = fontFamily,
                fontWeight = fontWeight.softenedPlayerLyricWeight(),
                color = Color.White.copy(alpha = if (active) 0.58f else 0.38f),
                textAlign = line.previewTextAlign(),
                active = active,
                isPlaying = isPlaying,
                modifier = Modifier.fillMaxWidth()
            )
        }
        if (backgroundAfterMain) {
            MiniBackgroundLyricBlock(
                line = line,
                background = background,
                backgroundTranslation = backgroundTranslation,
                currentPositionMs = currentPositionMs,
                active = active,
                isPlaying = isPlaying,
                fontFamily = fontFamily,
                fontWeight = fontWeight,
                backgroundSize = backgroundSize,
                backgroundTranslationSize = backgroundTranslationSize
            )
        }
    }
}

@Composable
private fun MiniBackgroundLyricBlock(
    line: com.ella.music.data.model.LyricLine,
    background: String?,
    backgroundTranslation: String?,
    currentPositionMs: Long,
    active: Boolean,
    isPlaying: Boolean,
    fontFamily: FontFamily?,
    fontWeight: FontWeight,
    backgroundSize: TextUnit,
    backgroundTranslationSize: TextUnit
) {
    AnimatedVisibility(
        visible = line.isMiniBackgroundVisibleAt(currentPositionMs),
        enter = fadeIn(animationSpec = tween(durationMillis = 180, easing = LinearOutSlowInEasing)),
        exit = fadeOut(animationSpec = tween(durationMillis = 260, easing = LinearOutSlowInEasing))
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            if (background != null) {
                if (active && line.backgroundWords.isNotEmpty()) {
                    MiniWordText(
                        text = background,
                        words = line.backgroundWords,
                        currentPositionMs = currentPositionMs,
                        isPlaying = isPlaying,
                        fontSize = backgroundSize,
                        fontFamily = fontFamily,
                        fontWeight = fontWeight.softenedPlayerLyricWeight(),
                        textAlign = line.previewBackgroundTextAlign(),
                        pendingAlpha = 0.36f,
                        sungAlpha = 0.58f,
                        currentAlpha = 0.78f
                    )
                } else {
                    MiniPlainLyricText(
                        text = background,
                        fontSize = backgroundSize,
                        fontFamily = fontFamily,
                        fontWeight = fontWeight.softenedPlayerLyricWeight(),
                        color = Color.White.copy(alpha = if (active) 0.68f else 0.44f),
                        textAlign = line.previewBackgroundTextAlign(),
                        active = active,
                        isPlaying = isPlaying,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
            if (backgroundTranslation != null) {
                MiniPlainLyricText(
                    text = backgroundTranslation,
                    fontSize = backgroundTranslationSize,
                    fontFamily = fontFamily,
                    fontWeight = fontWeight.softenedPlayerLyricWeight(),
                    color = Color.White.copy(alpha = 0.48f),
                    textAlign = line.previewBackgroundTextAlign(),
                    active = active,
                    isPlaying = isPlaying,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

@Composable
private fun MiniPlainLyricText(
    text: String,
    fontSize: TextUnit,
    fontFamily: FontFamily?,
    fontWeight: FontWeight,
    color: Color,
    textAlign: TextAlign,
    active: Boolean,
    isPlaying: Boolean,
    modifier: Modifier = Modifier
) {
    LyriconStyleMarqueeText(
        text = AnnotatedString(text),
        style = TextStyle(
            fontSize = fontSize,
            lineHeight = (fontSize.value * 1.38f).sp,
            fontFamily = fontFamily,
            fontWeight = fontWeight,
            color = color
        ),
        enabled = active && isPlaying,
        textAlign = textAlign,
        modifier = modifier
    )
}

@Composable
private fun MiniWordText(
    text: String,
    words: List<com.ella.music.data.model.LyricWord>,
    currentPositionMs: Long,
    isPlaying: Boolean,
    fontSize: TextUnit,
    fontFamily: FontFamily?,
    fontWeight: FontWeight,
    textAlign: TextAlign,
    modifier: Modifier = Modifier,
    maxLines: Int = 2,
    pendingAlpha: Float = 0.52f,
    sungAlpha: Float = 0.72f,
    currentAlpha: Float = 1f
) {
    val annotated = remember(words, currentPositionMs, pendingAlpha, sungAlpha, currentAlpha) {
        buildAnnotatedString {
            words.forEach { word ->
                appendMiniTimedWord(
                    text = word.text,
                    startMs = word.startMs,
                    endMs = word.endMs,
                    currentPositionMs = currentPositionMs,
                    pendingColor = Color.White.copy(alpha = pendingAlpha),
                    sungColor = Color.White.copy(alpha = sungAlpha),
                    currentColor = Color.White.copy(alpha = currentAlpha),
                    fontWeight = fontWeight,
                    inactiveWeight = fontWeight.softenedPlayerLyricWeight()
                )
            }
            if (length == 0) append(text)
        }
    }
    LyriconStyleMarqueeText(
        text = annotated,
        style = TextStyle(
            fontSize = fontSize,
            lineHeight = (fontSize.value * 1.38f).sp,
            fontFamily = fontFamily,
            fontWeight = fontWeight,
            textAlign = textAlign
        ),
        enabled = isPlaying,
        textAlign = textAlign,
        modifier = modifier.fillMaxWidth()
    )
}

private fun androidx.compose.ui.text.AnnotatedString.Builder.appendMiniTimedWord(
    text: String,
    startMs: Long,
    endMs: Long,
    currentPositionMs: Long,
    pendingColor: Color,
    sungColor: Color,
    currentColor: Color,
    fontWeight: FontWeight,
    inactiveWeight: FontWeight
) {
    if (text.isEmpty()) return
    val durationMs = (endMs - startMs).coerceAtLeast(1L)
    val isCurrent = currentPositionMs in startMs until endMs
    val isSung = currentPositionMs >= endMs
    when {
        isCurrent -> {
            val progress = ((currentPositionMs - startMs).toFloat() / durationMs).coerceIn(0f, 1f)
            val useSweep = text.trim().length > 2
            appendMiniStyledText(
                value = text,
                color = currentColor,
                fontWeight = fontWeight,
                brush = if (useSweep) {
                    miniLyricSweepBrush(
                        progress = progress,
                        activeColor = currentColor,
                        pendingColor = pendingColor
                    )
                } else {
                    null
                }
            )
        }
        isSung -> appendMiniStyledText(text, sungColor, inactiveWeight)
        else -> appendMiniStyledText(text, pendingColor, inactiveWeight)
    }
}

private fun androidx.compose.ui.text.AnnotatedString.Builder.appendMiniStyledText(
    value: String,
    color: Color,
    fontWeight: FontWeight,
    brush: Brush? = null,
    shadow: Shadow? = null,
    baselineShift: BaselineShift? = null
) {
    if (value.isEmpty()) return
    val style = if (brush != null) {
        SpanStyle(
            brush = brush,
            fontWeight = fontWeight,
            shadow = shadow,
            baselineShift = baselineShift
        )
    } else {
        SpanStyle(
            color = color,
            fontWeight = fontWeight,
            shadow = shadow,
            baselineShift = baselineShift
        )
    }
    pushStyle(
        style
    )
    append(value)
    pop()
}

private fun miniLyricSweepBrush(
    progress: Float,
    activeColor: Color,
    pendingColor: Color
): Brush {
    val edge = progress.coerceIn(0.002f, 0.998f)
    val featherStart = (edge - MINI_LYRIC_SWEEP_FEATHER_FRACTION).coerceAtLeast(0f)
    return Brush.horizontalGradient(
        colorStops = arrayOf(
            0f to activeColor,
            featherStart to activeColor,
            edge to pendingColor,
            1f to pendingColor
        )
    )
}

@Composable
private fun rememberSmoothMiniLyricPosition(
    currentPositionMs: Long,
    isPlaying: Boolean,
    anchorKey: Any?
): Long {
    var renderedPositionMs by remember(anchorKey) { mutableLongStateOf(currentPositionMs) }
    var anchorPositionMs by remember(anchorKey) { mutableLongStateOf(currentPositionMs) }
    var anchorFrameNanos by remember(anchorKey) { mutableLongStateOf(0L) }

    LaunchedEffect(anchorKey, currentPositionMs) {
        withFrameNanos { frameNanos ->
            if (currentPositionMs < renderedPositionMs || currentPositionMs - renderedPositionMs > 900L) {
                renderedPositionMs = currentPositionMs
            }
            anchorPositionMs = currentPositionMs
            anchorFrameNanos = frameNanos
            if (!isPlaying) renderedPositionMs = currentPositionMs
        }
    }

    LaunchedEffect(anchorKey, isPlaying) {
        if (!isPlaying) {
            renderedPositionMs = currentPositionMs
            return@LaunchedEffect
        }
        while (isActive) {
            withFrameNanos { frameNanos ->
                if (anchorFrameNanos <= 0L) return@withFrameNanos
                val elapsedMs = ((frameNanos - anchorFrameNanos) / 1_000_000L).coerceAtLeast(0L)
                val predicted = anchorPositionMs + elapsedMs
                if (predicted >= renderedPositionMs) {
                    renderedPositionMs = predicted
                }
            }
        }
    }

    return renderedPositionMs
}

private fun com.ella.music.data.model.LyricLine.miniLyricRenderKey(): String =
    "$timeMs|$endMs|$text|$backgroundText"

private const val PLAYER_PAGE_DETAILS = 0
private const val PLAYER_PAGE_COVER = 1
private const val PLAYER_PAGE_LYRICS = 2
private const val PLAYER_PAGE_COUNT = 3

private const val MINI_LYRIC_SWEEP_FEATHER_FRACTION = 0.04f

@Composable
private fun PlayerActionMenu(
    song: Song?,
    speed: Float,
    pitch: Float,
    visualizerEnabled: Boolean,
    visualizerAvailable: Boolean,
    metadataEditorId: String,
    lyricTimingEditorId: String,
    sleepTimerEndRealtimeMs: Long?,
    stopAfterCurrentEnabled: Boolean,
    sleepTimerCustomMinutes: Int,
    sleepTimerStopAfterCurrent: Boolean,
    onClose: () -> Unit,
    onAlbum: () -> Unit,
    onArtist: () -> Unit,
    onDownload: () -> Unit,
    onLandscape: () -> Unit,
    onSongInfo: () -> Unit,
    onStopAfterCurrent: (Boolean) -> Unit,
    onTimer: (Int) -> Unit,
    onCustomTimerMinutes: (Int) -> Unit,
    onCancelTimer: () -> Unit,
    onSpeed: (Float) -> Unit,
    onPitch: (Float) -> Unit,
    onVisualizerEnabled: (Boolean) -> Unit,
    initialPage: PlayerActionSheetPage = PlayerActionSheetPage.Main,
    modifier: Modifier = Modifier
) {
    var page by remember(initialPage) { mutableStateOf(initialPage) }
    val context = LocalContext.current
    val metadataOptions = remember(song?.id, song?.path, song?.mimeType) {
        song?.let { buildTagEditorOptions(context, it) }
            .orEmpty()
            .filter { it.kind == TagEditorOptionKind.Metadata }
    }
    val lyricTimingOptions = remember(song?.id, song?.path, song?.mimeType) {
        song?.let { buildTagEditorOptions(context, it) }
            .orEmpty()
            .filter { it.kind == TagEditorOptionKind.LyricTiming }
    }

    fun openEditorPage(
        kind: TagEditorOptionKind,
        preferredId: String
    ) {
        val options = if (kind == TagEditorOptionKind.Metadata) metadataOptions else lyricTimingOptions
        val preferredOption = preferredId
            .takeIf { it.isNotBlank() }
            ?.let { id -> options.firstOrNull { it.id == id } }
        if (preferredOption != null) {
            launchTagEditorOption(context, preferredOption)
            onClose()
        } else {
            page = if (kind == TagEditorOptionKind.Metadata) {
                PlayerActionSheetPage.MetadataEditor
            } else {
                PlayerActionSheetPage.LyricTimingEditor
            }
        }
    }

    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .navigationBarsPadding()
            .padding(horizontal = 18.dp, vertical = 10.dp)
    ) {
        when (page) {
            PlayerActionSheetPage.Main -> {
                PlayerActionMenuItem(stringResource(R.string.player_landscape_lyrics), onLandscape)
                PlayerActionMenuItem(stringResource(R.string.player_view_album), onAlbum)
                PlayerActionMenuItem(stringResource(R.string.player_view_artist), onArtist)
                PlayerActionMenuItem(stringResource(R.string.player_song_info), onSongInfo)
                PlayerActionMenuItem(stringResource(R.string.player_edit_metadata), { openEditorPage(TagEditorOptionKind.Metadata, metadataEditorId) })
                PlayerActionMenuItem(stringResource(R.string.player_lyric_timing), { openEditorPage(TagEditorOptionKind.LyricTiming, lyricTimingEditorId) })
                if (song?.onlineSource == "kw" && song.path.startsWith("http")) {
                    PlayerActionMenuItem(stringResource(R.string.player_download_lx_song), onDownload)
                }
                PlayerActionMenuItem(stringResource(R.string.player_sleep_timer), { page = PlayerActionSheetPage.Timer })
                PlayerActionMenuItem(stringResource(R.string.player_speed_pitch), { page = PlayerActionSheetPage.Speed })
                if (visualizerAvailable) {
                    PlayerActionMenuItem(stringResource(R.string.player_visualizer_settings), { page = PlayerActionSheetPage.Visualizer })
                }
            }
            PlayerActionSheetPage.Timer -> {
                TimerSheetContent(
                    onBack = { page = PlayerActionSheetPage.Main },
                    sleepTimerEndRealtimeMs = sleepTimerEndRealtimeMs,
                    stopAfterCurrentEnabled = stopAfterCurrentEnabled,
                    sleepTimerCustomMinutes = sleepTimerCustomMinutes,
                    sleepTimerStopAfterCurrent = sleepTimerStopAfterCurrent,
                    onStopAfterCurrent = onStopAfterCurrent,
                    onTimer = onTimer,
                    onCustomTimerMinutes = onCustomTimerMinutes,
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
            PlayerActionSheetPage.Visualizer -> {
                VisualizerSheetContent(
                    enabled = visualizerEnabled,
                    onBack = { page = PlayerActionSheetPage.Main },
                    onEnabledChange = onVisualizerEnabled
                )
            }
            PlayerActionSheetPage.MetadataEditor -> {
                TagEditorSheetContent(
                    song = song,
                    title = "选择元数据编辑器",
                    kind = TagEditorOptionKind.Metadata,
                    onBack = { page = PlayerActionSheetPage.Main },
                    onClose = onClose
                )
            }
            PlayerActionSheetPage.LyricTimingEditor -> {
                TagEditorSheetContent(
                    song = song,
                    title = "选择歌词打轴工具",
                    kind = TagEditorOptionKind.LyricTiming,
                    onBack = { page = PlayerActionSheetPage.Main },
                    onClose = onClose
                )
            }
        }
    }
}

private enum class PlayerActionSheetPage {
    Main,
    Timer,
    Speed,
    Visualizer,
    MetadataEditor,
    LyricTimingEditor
}

@Composable
private fun TimerSheetContent(
    onBack: () -> Unit,
    sleepTimerEndRealtimeMs: Long?,
    stopAfterCurrentEnabled: Boolean,
    sleepTimerCustomMinutes: Int,
    sleepTimerStopAfterCurrent: Boolean,
    onStopAfterCurrent: (Boolean) -> Unit,
    onTimer: (Int) -> Unit,
    onCustomTimerMinutes: (Int) -> Unit,
    onCancelTimer: () -> Unit
) {
    var customMinutes by remember(sleepTimerCustomMinutes) {
        mutableFloatStateOf(sleepTimerCustomMinutes.coerceIn(5, 120).toFloat())
    }
    var nowRealtimeMs by remember { mutableLongStateOf(SystemClock.elapsedRealtime()) }
    val remainingMs = sleepTimerEndRealtimeMs
        ?.minus(nowRealtimeMs)
        ?.coerceAtLeast(0L)
    val timerActive = remainingMs != null && remainingMs > 0L

    LaunchedEffect(sleepTimerEndRealtimeMs) {
        while (sleepTimerEndRealtimeMs != null) {
            nowRealtimeMs = SystemClock.elapsedRealtime()
            delay(1000L)
        }
    }

    HalfSheetTitle(title = "定时关闭", onBack = onBack)
    Spacer(modifier = Modifier.height(18.dp))

    if (timerActive) {
        TimerStatusCard(
            title = "定时播放中",
            subtitle = "剩余 ${formatTimerRemaining(remainingMs)} 后暂停"
        )
        Spacer(modifier = Modifier.height(12.dp))
    } else {
        listOf(10, 15, 20, 30, 40, 60).chunked(3).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                row.forEach { minutes ->
                    HalfSheetPill(
                        text = "$minutes 分钟",
                        onClick = { onTimer(minutes) },
                        modifier = Modifier.weight(1f)
                    )
                }
                repeat(3 - row.size) { Spacer(modifier = Modifier.weight(1f)) }
            }
            Spacer(modifier = Modifier.height(12.dp))
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "自定义时长",
            fontSize = 16.sp,
            fontWeight = FontWeight.ExtraBold,
            color = MiuixTheme.colorScheme.onSurface
        )
        DottedValueSlider(
            value = customMinutes,
            valueRange = 5f..120f,
            steps = 23,
            label = "${customMinutes.toInt()} 分钟",
            onValueChange = {
                customMinutes = it
                onCustomTimerMinutes(it.toInt().coerceIn(5, 120))
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(92.dp)
        )
        HalfSheetPill(
            text = "开始计时: ${customMinutes.toInt()} 分钟",
            selected = true,
            onClick = { onTimer(customMinutes.toInt().coerceAtLeast(1)) },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(10.dp))
    }

    StopAfterCurrentRow(
        checked = stopAfterCurrentEnabled || sleepTimerStopAfterCurrent,
        onCheckedChange = onStopAfterCurrent
    )
    if (timerActive) {
        Spacer(modifier = Modifier.height(8.dp))
        PlayerActionMenuItem("取消定时播放", onCancelTimer)
    }
}

@Composable
private fun TimerStatusCard(
    title: String,
    subtitle: String
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MiuixTheme.colorScheme.surfaceContainer.copy(alpha = 0.72f))
            .padding(horizontal = 16.dp, vertical = 14.dp)
    ) {
        Text(
            text = title,
            fontSize = 16.sp,
            fontWeight = FontWeight.ExtraBold,
            color = MiuixTheme.colorScheme.onSurface
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = subtitle,
            fontSize = 13.sp,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary
        )
    }
}

@Composable
private fun StopAfterCurrentRow(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(MiuixTheme.colorScheme.surfaceContainer.copy(alpha = 0.72f))
            .clickable { onCheckedChange(!checked) }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(22.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(
                    if (checked) MiuixTheme.colorScheme.primary
                    else MiuixTheme.colorScheme.onSurfaceVariantSummary.copy(alpha = 0.18f)
                ),
            contentAlignment = Alignment.Center
        ) {
            if (checked) {
                Text(
                    text = "✓",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = MiuixTheme.colorScheme.onPrimary
                )
            }
        }
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = "播放完当前歌曲后暂停",
            fontSize = 14.sp,
            color = MiuixTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f)
        )
    }
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
    SpeedPitchHeader(title = "变速播放")
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
    SpeedPitchHeader(title = "变调播放")
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
private fun SpeedPitchHeader(title: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            fontSize = 16.sp,
            fontWeight = FontWeight.ExtraBold,
            color = MiuixTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun VisualizerSheetContent(
    enabled: Boolean,
    onBack: () -> Unit,
    onEnabledChange: (Boolean) -> Unit
) {
    HalfSheetTitle(title = "可视化设置", onBack = onBack)
    Spacer(modifier = Modifier.height(22.dp))
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(MiuixTheme.colorScheme.surfaceContainer.copy(alpha = 0.72f))
            .clickable { onEnabledChange(!enabled) }
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "音乐可视化(Visualizer)",
            fontSize = 16.sp,
            fontWeight = FontWeight.ExtraBold,
            color = MiuixTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f)
        )
        Box(
            modifier = Modifier
                .width(56.dp)
                .height(32.dp)
                .clip(RoundedCornerShape(99.dp))
                .background(
                    if (enabled) MiuixTheme.colorScheme.primary
                    else MiuixTheme.colorScheme.onSurfaceVariantSummary.copy(alpha = 0.26f)
                )
                .padding(4.dp),
            contentAlignment = if (enabled) Alignment.CenterEnd else Alignment.CenterStart
        ) {
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .clip(CircleShape)
                    .background(MiuixTheme.colorScheme.background)
            )
        }
    }
    Spacer(modifier = Modifier.height(20.dp))
    Text(
        text = "开启时会请求录音权限，用来读取当前播放音频的频谱。",
        fontSize = 13.sp,
        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
        modifier = Modifier.padding(horizontal = 4.dp)
    )
}

@Composable
private fun TagEditorSheetContent(
    song: Song?,
    title: String,
    kind: TagEditorOptionKind,
    onBack: () -> Unit,
    onClose: () -> Unit
) {
    val context = LocalContext.current
    val options = remember(song?.id, song?.path, song?.mimeType, kind) {
        song?.let { buildTagEditorOptions(context, it) }
            .orEmpty()
            .filter { it.kind == kind }
    }

    HalfSheetTitle(title = title, onBack = onBack)
    Spacer(modifier = Modifier.height(18.dp))

    if (song == null) {
        TagEditorEmptyState("当前没有正在播放的歌曲")
        return
    }

    if (song.path.startsWith("http://") || song.path.startsWith("https://")) {
        TagEditorEmptyState("在线 / WebDAV 歌曲暂不支持外部编辑")
        return
    }

    if (options.isEmpty()) {
        TagEditorEmptyState(
            if (kind == TagEditorOptionKind.Metadata) {
                "未找到 Lyrico、LunaBeat 或音乐标签，请先安装后再试"
            } else {
                "未找到 LunaBeat 歌词打轴，请先安装后再试"
            }
        )
        return
    }

    options.forEach { option ->
        TagEditorOptionRow(
            option = option,
            onClick = {
                launchTagEditorOption(context, option)
                onClose()
            }
        )
        Spacer(modifier = Modifier.height(10.dp))
    }

    Text(
        text = "会把当前歌曲路径传给所选应用，并在返回后刷新这首歌。",
        fontSize = 13.sp,
        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
        modifier = Modifier.padding(horizontal = 4.dp, vertical = 6.dp)
    )
}

@Composable
private fun TagEditorOptionRow(
    option: TagEditorOption,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MiuixTheme.colorScheme.surfaceContainer.copy(alpha = 0.72f))
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(42.dp)
                .clip(CircleShape)
                .background(MiuixTheme.colorScheme.primary.copy(alpha = 0.14f)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = option.label.first().toString(),
                fontSize = 18.sp,
                fontWeight = FontWeight.ExtraBold,
                color = MiuixTheme.colorScheme.primary
            )
        }
        Spacer(modifier = Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = option.label,
                fontSize = 16.sp,
                fontWeight = FontWeight.ExtraBold,
                color = MiuixTheme.colorScheme.onSurface
            )
            Text(
                text = option.summary,
                fontSize = 13.sp,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun TagEditorEmptyState(text: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(MiuixTheme.colorScheme.surfaceContainer.copy(alpha = 0.72f))
            .padding(horizontal = 18.dp, vertical = 28.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun HalfSheetTitle(title: String, onBack: () -> Unit) {
    Box(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = "‹",
            fontSize = 30.sp,
            fontWeight = FontWeight.Bold,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
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
            color = MiuixTheme.colorScheme.onSurface,
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
            .background(
                if (selected) MiuixTheme.colorScheme.primary.copy(alpha = 0.16f)
                else MiuixTheme.colorScheme.surfaceContainer.copy(alpha = 0.72f)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 14.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            fontSize = 16.sp,
            fontWeight = FontWeight.ExtraBold,
            color = if (selected) MiuixTheme.colorScheme.primary else MiuixTheme.colorScheme.onSurface,
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
    val activeDotColor = MiuixTheme.colorScheme.primary.copy(alpha = 0.72f)
    val inactiveDotColor = MiuixTheme.colorScheme.onSurfaceVariantSummary.copy(alpha = 0.28f)
    val activeLineColor = MiuixTheme.colorScheme.primary.copy(alpha = 0.88f)
    val activeKnobColor = MiuixTheme.colorScheme.primary.copy(alpha = 0.92f)

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

    BoxWithConstraints(modifier = modifier) {
        val knobOffset = (maxWidth - 46.dp) * fraction
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
                    color = if (dotFraction <= fraction) activeDotColor else inactiveDotColor,
                    radius = if (index % 5 == 0) 4.2f else 3.2f,
                    center = Offset(x = gap * index, y = centerY)
                )
            }
            val knobX = size.width * fraction
            drawLine(
                color = activeLineColor,
                start = Offset(knobX, centerY - 36f),
                end = Offset(knobX, centerY + 36f),
                strokeWidth = 6f,
                cap = StrokeCap.Round
            )
            drawCircle(
                color = activeKnobColor,
                radius = 24f,
                center = Offset(knobX, centerY - 54f)
            )
        }
        label?.let {
            Text(
                text = it,
                fontSize = 13.sp,
                fontWeight = FontWeight.ExtraBold,
                color = MiuixTheme.colorScheme.onPrimary,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(start = knobOffset)
                    .padding(top = 2.dp)
                    .clip(RoundedCornerShape(999.dp))
                    .background(MiuixTheme.colorScheme.primary)
                    .padding(horizontal = 8.dp, vertical = 3.dp)
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
        fontWeight = FontWeight.Bold,
        color = MiuixTheme.colorScheme.onSurface,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 13.dp)
    )
}

@Composable
private fun PositionedAudioVisualizer(
    enabled: Boolean,
    audioSessionId: Int,
    isPlaying: Boolean,
    currentPositionProvider: () -> Long,
    accent: Color,
    modifier: Modifier = Modifier
) {
    AudioVisualizer(
        enabled = enabled,
        audioSessionId = audioSessionId,
        isPlaying = isPlaying,
        positionMs = currentPositionProvider(),
        accent = accent,
        modifier = modifier
    )
}

@Composable
private fun AudioVisualizer(
    enabled: Boolean,
    audioSessionId: Int,
    isPlaying: Boolean,
    positionMs: Long,
    accent: Color,
    modifier: Modifier = Modifier
) {
    if (!enabled) return
    var fftData by remember { mutableStateOf<ByteArray?>(null) }
    var levels by remember { mutableStateOf<List<Float>>(emptyList()) }
    var visualizerFailed by remember { mutableStateOf(false) }
    val playingState by rememberUpdatedState(isPlaying)

    LaunchedEffect(enabled, audioSessionId) {
        fftData = null
        levels = emptyList()
        visualizerFailed = false
        if (!enabled || audioSessionId <= 0) return@LaunchedEffect
        val visualizer = runCatching {
            Visualizer(audioSessionId).apply {
                captureSize = Visualizer.getCaptureSizeRange()[1].coerceAtMost(512)
                scalingMode = Visualizer.SCALING_MODE_NORMALIZED
                this.enabled = true
            }
        }.onFailure { visualizerFailed = true }.getOrNull() ?: return@LaunchedEffect

        Log.d("PlayerScreenPerf", "visualizer start")
        val buffer = ByteArray(visualizer.captureSize)
        try {
            while (isActive) {
                if (playingState) {
                    if (visualizer.getFft(buffer) == Visualizer.SUCCESS) {
                        fftData = buffer.copyOf()
                    }
                } else {
                    fftData = null
                    delay(120L)
                    continue
                }
                delay(66L)
            }
        } finally {
            Log.d("PlayerScreenPerf", "visualizer stop")
            runCatching { visualizer.enabled = false }
            visualizer.release()
        }
    }

    LaunchedEffect(fftData, enabled, visualizerFailed) {
        val fft = fftData
        levels = if (enabled && !visualizerFailed && fft != null && fft.size > 8) {
            mapFftToLogBars(fft, levels, barCount = 84)
        } else {
            emptyList()
        }
    }

    Canvas(modifier = modifier.graphicsLayer { alpha = if (isPlaying) 1f else 0.42f }) {
        val barCount = 84
        val horizontalPadding = size.width * 0.065f
        val usableWidth = (size.width - horizontalPadding * 2f).coerceAtLeast(1f)
        val gap = usableWidth / barCount
        val barWidth = (gap * 0.34f).coerceIn(2.dp.toPx(), 3.8.dp.toPx())
        val minHeight = 2.5.dp.toPx()
        val visualHeight = min(size.height * 0.34f, 18.dp.toPx())
        val centerY = size.height - 11.dp.toPx()
        val glowWidth = (barWidth * 2.7f).coerceAtLeast(5.dp.toPx())
        val halfCount = (barCount - 1) / 2f
        for (index in 0 until barCount) {
            val x = horizontalPadding + gap * index + gap / 2f
            val normalized = (levels.getOrNull(index) ?: 0.06f).coerceIn(0.04f, 1f)
            val distance = abs(index - halfCount) / halfCount
            val edgeFade = (1f - distance * distance * 0.48f).coerceIn(0.48f, 1f)
            val height = (minHeight + visualHeight * normalized).coerceAtMost(visualHeight + minHeight)
            val top = centerY - height / 2f
            val alpha = (0.18f + normalized * 0.54f) * edgeFade

            drawRoundRect(
                color = accent.copy(alpha = alpha * 0.18f),
                topLeft = Offset(x - glowWidth / 2f, top - 1.5.dp.toPx()),
                size = Size(glowWidth, height + 3.dp.toPx()),
                cornerRadius = CornerRadius(glowWidth, glowWidth)
            )
            drawRoundRect(
                color = accent.copy(alpha = alpha),
                topLeft = Offset(x - barWidth / 2f, top),
                size = Size(barWidth, height),
                cornerRadius = CornerRadius(barWidth, barWidth)
            )
        }
    }
}

private fun mapFftToLogBars(
    fft: ByteArray,
    previous: List<Float>,
    barCount: Int
): List<Float> {
    val binCount = fft.size / 2
    if (binCount <= 2) return List(barCount) { 0.06f }

    return List(barCount) { index ->
        val startRatio = index.toFloat() / barCount
        val endRatio = (index + 1f) / barCount
        val startBin = (1f + (binCount - 2) * startRatio * startRatio)
            .toInt()
            .coerceIn(1, binCount - 1)
        val endBin = (1f + (binCount - 2) * endRatio * endRatio)
            .toInt()
            .coerceIn(startBin, binCount - 1)

        var peak = 0f
        for (bin in startBin..endBin) {
            val real = fft[bin * 2].toFloat()
            val imag = fft[bin * 2 + 1].toFloat()
            peak = max(peak, sqrt(real * real + imag * imag))
        }

        val db = 20f * (ln(peak.coerceAtLeast(1f)) / ln(10f))
        val normalized = ((db - 16f) / 36f).coerceIn(0f, 1f)
        val shaped = 0.06f + sqrt(normalized) * 0.94f
        val old = previous.getOrNull(index) ?: 0.06f
        if (shaped > old) {
            old * 0.42f + shaped * 0.58f
        } else {
            old * 0.84f + shaped * 0.16f
        }
    }
}

@Composable
private fun AlbumArtView(
    song: Song?,
    embeddedCover: Bitmap?,
    cornerRadius: androidx.compose.ui.unit.Dp = 20.dp,
    modifier: Modifier = Modifier
) {
    val uri = if ((song?.albumId ?: 0L) > 0) {
        Uri.parse("content://media/external/audio/albumart/${song?.albumId}")
    } else null
    val coverModel = embeddedCover ?: song?.coverUrl?.takeIf { it.isNotBlank() } ?: uri

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(cornerRadius))
            .background(MiuixTheme.colorScheme.surfaceContainer),
        contentAlignment = Alignment.Center
    ) {
        if (coverModel != null) {
            SafeCoverImage(
                model = coverModel,
                contentDescription = null,
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(cornerRadius)),
                contentScale = ContentScale.Crop,
                sizePx = 768
            )
        } else {
            DefaultAlbumCover(modifier = Modifier.fillMaxSize())
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
                useController = false
                controllerAutoShow = false
                controllerHideOnTouch = false
                resizeMode = androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                setShowBuffering(PlayerView.SHOW_BUFFERING_NEVER)
                findViewById<View>(androidx.media3.ui.R.id.exo_controller)?.visibility = View.GONE
                player = exoPlayer
                hideController()
            }
        },
        update = { view ->
            view.useController = false
            view.controllerAutoShow = false
            view.controllerHideOnTouch = false
            view.findViewById<View>(androidx.media3.ui.R.id.exo_controller)?.visibility = View.GONE
            view.player = exoPlayer
            view.hideController()
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
    var draggingProgress by remember { mutableStateOf<Float?>(null) }
    val displayProgress = draggingProgress ?: safeProgress

    fun progressAt(width: Float, x: Float): Float {
        return (x / width.coerceAtLeast(1f)).coerceIn(0f, 1f)
    }

    Box(
        modifier = modifier.height(30.dp)
    ) {
        AndroidView(
            factory = { context ->
                SuperIslandGlowProgressBar(context).apply {
                    shaderMode = SuperIslandGlowProgressBar.ShaderMode.HIGH_END
                    trackHeightPx = resources.displayMetrics.density * 4.5f
                    trackHorizontalPaddingPx = 0f
                    headGlowAlpha = 1f
                    trackColor = AndroidColor.argb(48, 255, 255, 255)
                }
            },
            update = { view ->
                if (view.progressFraction != displayProgress) {
                    view.progressFraction = displayProgress
                }
                val progressColor = accent.copy(alpha = 0.82f).toArgb()
                if (view.fallbackProgressColor != progressColor) {
                    view.fallbackProgressColor = progressColor
                }
            },
            modifier = Modifier.fillMaxSize()
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectTapGestures { offset ->
                        onSeek(progressAt(size.width.toFloat(), offset.x))
                    }
                }
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDragStart = { offset ->
                            draggingProgress = progressAt(size.width.toFloat(), offset.x)
                        },
                        onDragEnd = {
                            draggingProgress?.let(onSeek)
                            draggingProgress = null
                        },
                        onDragCancel = {
                            draggingProgress = null
                        },
                        onDrag = { change, _ ->
                            draggingProgress = progressAt(size.width.toFloat(), change.position.x)
                        }
                    )
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

private fun com.ella.music.data.model.LyricLine.miniVisiblePartCount(
    showTranslation: Boolean,
    showPronunciation: Boolean
): Int {
    var count = 0
    if (showPronunciation && !pronunciation.isNullOrBlank()) count++
    if (text.isNotBlank() && !text.isMusicSymbolOnly()) count++
    if (showTranslation && !translation.isNullOrBlank()) count++
    if (!backgroundText.isNullOrBlank() && !backgroundText.isMusicSymbolOnly()) count++
    if (showTranslation && !backgroundTranslation.isNullOrBlank()) count++
    return count
}

private fun com.ella.music.data.model.LyricLine.isMiniBackgroundAfterMain(): Boolean {
    val start = backgroundStartMs ?: backgroundWords.minOfOrNull { it.startMs }
    val mainStart = words.minOfOrNull { it.startMs } ?: timeMs
    return start == null || start > mainStart
}

private fun com.ella.music.data.model.LyricLine.isMiniBackgroundVisibleAt(positionMs: Long): Boolean {
    val start = backgroundStartMs ?: backgroundWords.minOfOrNull { it.startMs } ?: return true
    val end = backgroundEndMs ?: backgroundWords.maxOfOrNull { it.endMs } ?: endMs
    return positionMs >= start && (end == null || positionMs <= end)
}

private fun com.ella.music.data.model.LyricLine.previewTextAlign(): TextAlign {
    if (agent.isNullOrBlank()) {
        return TextAlign.Start
    }
    return if (agent.equals("v2", ignoreCase = true)) TextAlign.End else TextAlign.Start
}

private fun com.ella.music.data.model.LyricLine.previewBackgroundTextAlign(): TextAlign {
    return when {
        agent.equals("v2", ignoreCase = true) -> TextAlign.End
        else -> TextAlign.Start
    }
}

private fun com.ella.music.data.model.LyricLine.previewHorizontalAlignment(): Alignment.Horizontal {
    return when (previewTextAlign()) {
        TextAlign.End -> Alignment.End
        else -> Alignment.Start
    }
}

private fun FontWeight.softenedPlayerLyricWeight(): FontWeight {
    return FontWeight((weight - 200).coerceIn(100, 900))
}

private fun adaptiveTitleFontSize(text: String, maxSize: TextUnit): TextUnit {
    val scale = when {
        text.length > 72 -> 0.54f
        text.length > 58 -> 0.62f
        text.length > 44 -> 0.70f
        text.length > 32 -> 0.80f
        text.length > 24 -> 0.90f
        else -> 1f
    }
    return (maxSize.value * scale).sp
}

private fun String.toPlayerLyricFontFamily(weight: Int): FontFamily? {
    if (isBlank()) return null
    val file = File(this)
    if (!file.exists() || !file.canRead()) return null
    return runCatching {
        val baseTypeface = Typeface.createFromFile(file)
        val weightedTypeface = Typeface.create(baseTypeface, weight.coerceIn(100, 900), false)
        FontFamily(weightedTypeface)
    }.getOrNull()
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

private fun Color.lighten(amount: Float): Color = Color(
    red = red + (1f - red) * amount,
    green = green + (1f - green) * amount,
    blue = blue + (1f - blue) * amount,
    alpha = alpha
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

private fun setPlayerSystemBars(activity: Activity?, view: View) {
    val window = activity?.window ?: return
    window.statusBarColor = android.graphics.Color.TRANSPARENT
    window.navigationBarColor = android.graphics.Color.TRANSPARENT
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        window.isNavigationBarContrastEnforced = false
    }
    WindowCompat.getInsetsController(window, view).apply {
        isAppearanceLightStatusBars = false
        isAppearanceLightNavigationBars = false
    }
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

private fun formatTimerRemaining(ms: Long): String {
    val totalSeconds = (ms / 1000).coerceAtLeast(0L)
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        "%d:%02d:%02d".format(hours, minutes, seconds)
    } else {
        "%02d:%02d".format(minutes, seconds)
    }
}

private fun com.ella.music.data.AudioQualitySummary.playerCompactText(): String {
    return when {
        compactLabel == "MQ" -> "∞ Master"
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

    val songNameCandidates = listOf(
        songFile?.nameWithoutExtension.orEmpty(),
        title,
        songKey,
        listOf(artist, title).filter { it.isNotBlank() }.joinToString("-"),
        listOf(artist, title).filter { it.isNotBlank() }.joinToString(" -")
    )
        .filter { it.isNotBlank() }
        .map { it.toSafeDynamicCoverName() }
        .filter { it.isNotBlank() }
        .distinct()

    val folderCandidates = songFolder
        ?.takeIf { it.exists() && it.isDirectory }
        ?.let { folder ->
            songNameCandidates.map { File(folder, "$it.mp4") } + listOf(
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

    candidates.firstOrNull { it.exists() && it.isFile && it.length() > 0L }?.let { return it }

    val fuzzySongTokens = songNameCandidates.mapTo(mutableSetOf()) { it.toDynamicCoverMatchToken() }
    return songFolder
        ?.takeIf { it.exists() && it.isDirectory }
        ?.listFiles { file ->
            file.isFile &&
                file.extension.equals("mp4", ignoreCase = true) &&
                file.length() > 0L &&
                file.nameWithoutExtension.toDynamicCoverMatchToken() in fuzzySongTokens
        }
        ?.firstOrNull()
}

private fun String.toSafeDynamicCoverName(): String {
    return trim()
        .replace("""[\\/:*?"<>|]""".toRegex(), "_")
        .replace("\\s+".toRegex(), " ")
        .ifBlank { "Unknown" }
}

private fun String.toDynamicCoverMatchToken(): String =
    lowercase()
        .replace(Regex("""[\s_\-–—]+"""), "")
        .replace(Regex("""[\\/:*?"<>|.,，。'’`~!！()\[\]{}]+"""), "")
