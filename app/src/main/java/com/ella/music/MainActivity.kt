package com.ella.music

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.DocumentsContract
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.NavGraph.Companion.findStartDestination
import com.ella.music.data.SettingsManager
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import com.ella.music.ui.components.LiquidGlassBottomBar
import com.ella.music.ui.components.LiquidGlassBottomBarItem
import com.ella.music.ui.components.MiniPlayer
import com.ella.music.ui.components.TagEditorEditTracker
import com.ella.music.ui.components.updateEllaDynamicShortcuts
import com.ella.music.ui.navigation.AppNavigation
import com.ella.music.ui.navigation.EXTRA_SHORTCUT_ROUTE
import com.ella.music.ui.navigation.Screen
import com.ella.music.ui.player.PlayerScreen
import com.ella.music.ui.theme.EllaTheme
import com.ella.music.ui.theme.THEME_DARK
import com.ella.music.ui.theme.THEME_FOLLOW_SYSTEM
import com.ella.music.viewmodel.MainViewModel
import com.ella.music.viewmodel.PlayerViewModel
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Music
import top.yukonga.miuix.kmp.icon.extended.Playlist
import top.yukonga.miuix.kmp.icon.extended.Settings
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.window.WindowDialog

class MainActivity : ComponentActivity() {

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            lifecycleScope.launch {
                if (SettingsManager(this@MainActivity).initialScanPromptHandled.first()) {
                    mainViewModel?.scanMusicIfAutoEnabled()
                }
            }
        }
        requestNotificationPermissionIfNeeded()
    }

    private val requestNotificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) {}

    private var mainViewModel: MainViewModel? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isNavigationBarContrastEnforced = false
        }

        setContent {
            val mainVm: MainViewModel = viewModel()
            val playerVm: PlayerViewModel = viewModel()
            mainViewModel = mainVm

            val settingsManager = remember { SettingsManager(this@MainActivity) }
            val themeMode by settingsManager.themeMode.collectAsState(initial = 0)

            val isDark = when (themeMode) {
                THEME_DARK -> true
                THEME_FOLLOW_SYSTEM -> isSystemInDarkTheme()
                else -> false
            }

            val view = LocalView.current
            DisposableEffect(isDark) {
                enableEdgeToEdge(
                    statusBarStyle = SystemBarStyle.auto(
                        Color.TRANSPARENT,
                        Color.TRANSPARENT
                    ) { isDark },
                    navigationBarStyle = SystemBarStyle.auto(
                        Color.TRANSPARENT,
                        Color.TRANSPARENT
                    ) { isDark },
                )

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    window.isNavigationBarContrastEnforced = false
                }

                onDispose {}
            }

            LaunchedEffect(isDark) {
                val window = (view.context as ComponentActivity).window
                WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !isDark
                WindowCompat.getInsetsController(window, view).isAppearanceLightNavigationBars = !isDark
            }

            LaunchedEffect(Unit) {
                val canScanNow = checkAndRequestPermissions()
                mainVm.loadCachedLibrary()
                if (!startupPlaybackHandled) {
                    startupPlaybackHandled = true
                    when (settingsManager.startupPlayMode.first()) {
                        SettingsManager.STARTUP_PLAY_RANDOM -> {
                            val songs = mainVm.songs.first { it.isNotEmpty() }
                            if (playerVm.currentSong.value == null && !playerVm.hasSavedPlaybackQueue()) {
                                val startIndex = songs.indices.random()
                                playerVm.setPlaylist(songs, startIndex)
                            }
                        }
                        SettingsManager.STARTUP_PLAY_RESUME -> {
                            if (playerVm.currentSong.value == null && playerVm.hasSavedPlaybackQueue()) {
                                playerVm.playRestoredQueue()
                            }
                        }
                    }
                }
                if (canScanNow && settingsManager.initialScanPromptHandled.first()) {
                    mainVm.scanMusicIfAutoEnabled()
                }
            }

            EllaTheme(themeMode = themeMode) {
                EllaApp(mainVm, playerVm, isDark)
            }
        }
    }

    private fun checkAndRequestPermissions(): Boolean {
        val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_AUDIO
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }

        return if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
            requestPermissionLauncher.launch(permission)
            false
        } else {
            requestNotificationPermissionIfNeeded()
            true
        }
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        if (
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            requestNotificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    private companion object {
        var startupPlaybackHandled = false
    }
}

@Composable
fun EllaApp(
    mainViewModel: MainViewModel,
    playerViewModel: PlayerViewModel,
    isDarkTheme: Boolean
) {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route
    val view = LocalView.current
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val settingsManager = remember { SettingsManager(context) }
    val scope = rememberCoroutineScope()
    var showPlayerOverlay by remember { mutableStateOf(false) }
    var playerDismissProgress by remember { mutableFloatStateOf(0f) }
    var playerOverlayOpenToken by remember { mutableIntStateOf(0) }
    val isPlayerVisible = showPlayerOverlay || currentRoute == Screen.Player.route
    val libraryCacheLoaded by mainViewModel.libraryCacheLoaded.collectAsState()
    val initialScanPromptHandled by settingsManager.initialScanPromptHandled.collectAsState(initial = true)
    val shortcutLibraryLabel by settingsManager.shortcutLibraryLabel.collectAsState(initial = SettingsManager.DEFAULT_SHORTCUT_LIBRARY_LABEL)
    val shortcutPlaylistsLabel by settingsManager.shortcutPlaylistsLabel.collectAsState(initial = SettingsManager.DEFAULT_SHORTCUT_PLAYLISTS_LABEL)
    val shortcutFolderLabel by settingsManager.shortcutFolderLabel.collectAsState(initial = SettingsManager.DEFAULT_SHORTCUT_FOLDER_LABEL)
    val isScanning by mainViewModel.isScanning.collectAsState()
    var showInitialScanPrompt by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        val activity = context as? Activity
        val shortcutRoute = activity?.intent?.getStringExtra(EXTRA_SHORTCUT_ROUTE).orEmpty()
        if (shortcutRoute.isNotBlank()) {
            runCatching {
                navController.navigate(shortcutRoute) {
                    launchSingleTop = true
                    restoreState = true
                }
            }
            activity?.intent?.removeExtra(EXTRA_SHORTCUT_ROUTE)
        }
    }

    LaunchedEffect(shortcutLibraryLabel, shortcutPlaylistsLabel, shortcutFolderLabel) {
        updateEllaDynamicShortcuts(
            context = context,
            libraryLabel = shortcutLibraryLabel,
            playlistsLabel = shortcutPlaylistsLabel,
            folderLabel = shortcutFolderLabel
        )
    }

    val initialScanFolderPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val readOnly = Intent.FLAG_GRANT_READ_URI_PERMISSION
        val readWrite = readOnly or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        runCatching {
            context.contentResolver.takePersistableUriPermission(uri, readWrite)
        }.recoverCatching {
            context.contentResolver.takePersistableUriPermission(uri, readOnly)
        }
        val folderPath = uri.toPrimaryStoragePath()
        if (folderPath == null) {
            Toast.makeText(context, "暂不支持该系统目录路径", Toast.LENGTH_SHORT).show()
        } else {
            scope.launch {
                settingsManager.setUseAndroidMediaLibrary(false)
                settingsManager.setScanIncludeFolders(folderPath)
                settingsManager.setAutoScan(true)
                mainViewModel.scanMusic()
            }
            Toast.makeText(context, "已添加扫描文件夹", Toast.LENGTH_SHORT).show()
        }
    }

    LaunchedEffect(isPlayerVisible, isDarkTheme) {
        val window = (view.context as ComponentActivity).window
        window.statusBarColor = Color.TRANSPARENT
        window.navigationBarColor = Color.TRANSPARENT
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isNavigationBarContrastEnforced = false
        }
        WindowCompat.getInsetsController(window, view).apply {
            isAppearanceLightStatusBars = if (isPlayerVisible) false else !isDarkTheme
            isAppearanceLightNavigationBars = if (isPlayerVisible) false else !isDarkTheme
        }
    }

    val bottomBarScreens = listOf(
        Screen.Home.route,
        Screen.Library.route,
        Screen.Settings.route
    )
    val showBottomBar = currentRoute in bottomBarScreens

    val currentSong by playerViewModel.currentSong.collectAsState()
    val isPlaying by playerViewModel.isPlaying.collectAsState()
    val librarySongs by mainViewModel.songs.collectAsState()

    LaunchedEffect(libraryCacheLoaded, initialScanPromptHandled, isScanning, librarySongs) {
        if (!libraryCacheLoaded || initialScanPromptHandled) return@LaunchedEffect
        if (librarySongs.isNotEmpty()) {
            settingsManager.setInitialScanPromptHandled(true)
            mainViewModel.scanMusicIfAutoEnabled()
        } else if (!isScanning) {
            showInitialScanPrompt = true
        }
    }

    DisposableEffect(lifecycleOwner, mainViewModel, playerViewModel) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                TagEditorEditTracker.consume()?.let { editedSong ->
                    mainViewModel.refreshSongAfterExternalEdit(editedSong) { updatedSong ->
                        playerViewModel.refreshCurrentSongAfterExternalEdit(updatedSong)
                    }
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }
    val currentPosition by playerViewModel.currentPosition.collectAsState()
    val duration by playerViewModel.duration.collectAsState()
    val lyrics by playerViewModel.lyrics.collectAsState()
    val currentLyricIndex by playerViewModel.currentLyricIndex.collectAsState()
    val miniPlayerShowTranslation by settingsManager.miniPlayerLyricTranslation.collectAsState(initial = true)
    val miniPlayerCoverRotation by settingsManager.miniPlayerCoverRotation.collectAsState(initial = true)
    val miniPlayerLyricsEnabled by settingsManager.miniPlayerLyricsEnabled.collectAsState(initial = true)

    val currentLyricLine = lyrics.getOrNull(currentLyricIndex)
    val miniPlayerLyricText = if (isPlaying && miniPlayerLyricsEnabled) {
        currentLyricLine?.text?.takeIf { it.isNotBlank() && !it.isMusicSymbolOnly() }
    } else {
        null
    }
    val miniPlayerLyricTranslation = if (isPlaying && miniPlayerLyricsEnabled && miniPlayerShowTranslation) {
        currentLyricLine?.translation?.takeIf { it.isNotBlank() }
    } else {
        null
    }

    val nextLyricLine = lyrics.getOrNull(currentLyricIndex + 1)

    val miniPlayerLyricProgress = if (isPlaying && currentLyricLine != null) {
        val start = currentLyricLine.timeMs
        val end = currentLyricLine.endMs
            ?: nextLyricLine?.timeMs
            ?: (start + 5_000L)

        ((currentPosition - start).toFloat() / (end - start).coerceAtLeast(1L).toFloat())
            .coerceIn(0f, 1f)
    } else {
        0f
    }

    val showMiniPlayer = currentSong != null &&
        currentRoute != Screen.Player.route &&
        !showPlayerOverlay

    val backdrop = rememberLayerBackdrop()
    val useGlass = true
    val tabs = listOf(
        Triple(Screen.Home.route, "首页", MiuixIcons.Regular.Music),
        Triple(Screen.Library.route, "音乐库", MiuixIcons.Regular.Playlist),
        Triple(Screen.Settings.route, "设置", MiuixIcons.Regular.Settings),
    )

    val contentModifier = Modifier
        .fillMaxSize()
        .background(MiuixTheme.colorScheme.background)
        .layerBackdrop(backdrop)
    val previousContentBlur = if (
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
        showPlayerOverlay
    ) {
        7.dp * playerDismissProgress.coerceIn(0f, 1f)
    } else {
        0.dp
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MiuixTheme.colorScheme.background)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .then(
                    if (previousContentBlur.value > 0f) {
                        Modifier.blur(radius = previousContentBlur)
                    } else {
                        Modifier
                    }
                )
        ) {
            AppNavigation(
                navController = navController,
                mainViewModel = mainViewModel,
                playerViewModel = playerViewModel,
                modifier = contentModifier,
                onNavigateToPlayer = {
                    playerDismissProgress = 0f
                    playerOverlayOpenToken++
                    showPlayerOverlay = true
                }
            )
            FloatingBottomControls(
                showMiniPlayer = showMiniPlayer,
                showBottomBar = showBottomBar,
                currentSong = currentSong,
                isPlaying = isPlaying,
                coverRotationEnabled = miniPlayerCoverRotation,
                currentPosition = currentPosition,
                duration = duration,
                lyricText = miniPlayerLyricText,
                lyricTranslation = miniPlayerLyricTranslation,
                tabs = tabs,
                currentRoute = currentRoute,
                backdrop = backdrop,
                lyricProgress = miniPlayerLyricProgress,
                mainViewModel = mainViewModel,
                playerViewModel = playerViewModel,
                onNavigate = { route ->
                    if (currentRoute != route) {
                        navController.navigate(route) {
                            popUpTo(navController.graph.findStartDestination().id) {
                                saveState = false
                            }
                            launchSingleTop = true
                            restoreState = false
                        }
                    }
                },
                onNavigatePlayer = {
                    playerDismissProgress = 0f
                    playerOverlayOpenToken++
                    showPlayerOverlay = true
                },
                modifier = Modifier.align(androidx.compose.ui.Alignment.BottomCenter)
            )
        }
        AnimatedVisibility(
            visible = showPlayerOverlay,
            enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.fillMaxSize()
        ) {
            PlayerScreen(
                playerViewModel = playerViewModel,
                onBack = {
                    playerViewModel.setShowLyrics(false)
                    showPlayerOverlay = false
                    playerDismissProgress = 0f
                },
                onNavigateToAlbum = { albumId ->
                    playerViewModel.setShowLyrics(false)
                    showPlayerOverlay = false
                    playerDismissProgress = 0f
                    navController.navigate(Screen.AlbumDetail.createRoute(albumId))
                },
                onNavigateToArtist = { artistName ->
                    playerViewModel.setShowLyrics(false)
                    showPlayerOverlay = false
                    playerDismissProgress = 0f
                    navController.navigate(Screen.ArtistDetail.createRoute(artistName))
                },
                onNavigateToMetadataCategory = { type, name ->
                    playerViewModel.setShowLyrics(false)
                    showPlayerOverlay = false
                    playerDismissProgress = 0f
                    navController.navigate(Screen.MetadataCategoryDetail.createRoute(type, name))
                },
                onDismissProgressChange = { progress ->
                    playerDismissProgress = progress
                },
                openToken = playerOverlayOpenToken
            )
        }

        InitialScanPromptDialog(
            show = showInitialScanPrompt,
            onDismiss = {
                showInitialScanPrompt = false
                scope.launch {
                    settingsManager.setInitialScanPromptHandled(true)
                    settingsManager.setAutoScan(false)
                }
            },
            onCustomFolderScan = {
                showInitialScanPrompt = false
                scope.launch {
                    settingsManager.setInitialScanPromptHandled(true)
                    settingsManager.setUseAndroidMediaLibrary(false)
                    settingsManager.setAutoScan(false)
                }
                initialScanFolderPicker.launch(null)
            },
            onMediaLibraryScan = {
                showInitialScanPrompt = false
                scope.launch {
                    settingsManager.setInitialScanPromptHandled(true)
                    settingsManager.setUseAndroidMediaLibrary(true)
                    settingsManager.setAutoScan(true)
                    mainViewModel.scanMusic()
                }
            }
        )
    }
}

@Composable
private fun InitialScanPromptDialog(
    show: Boolean,
    onDismiss: () -> Unit,
    onCustomFolderScan: () -> Unit,
    onMediaLibraryScan: () -> Unit
) {
    WindowDialog(
        show = show,
        title = "无歌曲",
        onDismissRequest = onDismiss
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = "当前没有歌曲，是否现在扫描歌曲？",
                color = MiuixTheme.colorScheme.onSurface,
                fontSize = 15.sp,
                lineHeight = 22.sp
            )
            Spacer(modifier = Modifier.height(18.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(modifier = Modifier.weight(1f)) {
                    TextButton(
                        text = "取消",
                        onClick = onDismiss,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Box(modifier = Modifier.weight(1f)) {
                    TextButton(
                        text = "自定义",
                        onClick = onCustomFolderScan,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Box(modifier = Modifier.weight(1f)) {
                    TextButton(
                        text = "确定",
                        onClick = onMediaLibraryScan,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun FloatingBottomControls(
    showMiniPlayer: Boolean,
    showBottomBar: Boolean,
    currentSong: com.ella.music.data.model.Song?,
    isPlaying: Boolean,
    coverRotationEnabled: Boolean,
    currentPosition: Long,
    duration: Long,
    lyricText: String?,
    lyricTranslation: String?,
    lyricProgress: Float,
    tabs: List<Triple<String, String, androidx.compose.ui.graphics.vector.ImageVector>>,
    currentRoute: String?,
    backdrop: com.kyant.backdrop.Backdrop?,
    mainViewModel: MainViewModel,
    playerViewModel: PlayerViewModel,
    onNavigate: (String) -> Unit,
    onNavigatePlayer: () -> Unit,
    modifier: Modifier = Modifier,
    useGlass: Boolean = true
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .then(if (useGlass) Modifier.navigationBarsPadding() else Modifier)
    ) {
        AnimatedVisibility(
            visible = showMiniPlayer,
            enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { it }) + fadeOut()
        ) {
            currentSong?.let { song ->
                MiniPlayer(
                    song = song,
                    isPlaying = isPlaying,
                    progress = if (duration > 0L) currentPosition.toFloat() / duration.toFloat() else 0f,
                    coverRotationEnabled = coverRotationEnabled,
                    lyricText = lyricText,
                    lyricTranslation = lyricTranslation,
                    albumArtUri = mainViewModel.getAlbumArtUri(song.albumId),
                    loadCoverArt = mainViewModel::getCoverArtBitmap,
                    backdrop = if (useGlass) backdrop else null,
                    liquidGlass = useGlass,
                    onClick = onNavigatePlayer,
                    onPlayPause = { playerViewModel.togglePlayPause() },
                    onSkipNext = { playerViewModel.skipToNext() },
                    onSkipPrevious = { playerViewModel.skipToPrevious() },
                    lyricProgress = lyricProgress,
                )
            }
        }

        AnimatedVisibility(visible = showBottomBar) {
            if (useGlass) {
                LiquidGlassBottomBar(
                    backdrop = backdrop,
                    isBlurEnabled = true
                ) {
                    tabs.forEach { (route, label, icon) ->
                        LiquidGlassBottomBarItem(
                            selected = currentRoute == route,
                            onClick = { onNavigate(route) },
                            backdrop = backdrop,
                            isBlurEnabled = true,
                            icon = {
                                Icon(
                                    imageVector = icon,
                                    contentDescription = label,
                                    tint = if (currentRoute == route) MiuixTheme.colorScheme.primary
                                    else MiuixTheme.colorScheme.onSurface,
                                    modifier = Modifier
                                )
                            },
                            label = {
                                Text(
                                    text = label,
                                    fontSize = 11.sp,
                                    color = if (currentRoute == route) MiuixTheme.colorScheme.primary
                                    else MiuixTheme.colorScheme.onSurface
                                )
                            }
                        )
                    }
                }
            }
        }
    }
}
private fun String.isMusicSymbolOnly(): Boolean {
    val content = trim()
    if (content.isBlank()) return true

    return content.all { char ->
        char.isWhitespace() ||
                char in setOf('♪', '♫', '♬', '♩', '♭', '♯', '♮') ||
                Character.UnicodeBlock.of(char) == Character.UnicodeBlock.MUSICAL_SYMBOLS
    }
}

private fun Uri.toPrimaryStoragePath(): String? {
    val documentId = runCatching { DocumentsContract.getTreeDocumentId(this) }.getOrNull() ?: return null
    val parts = documentId.split(':', limit = 2)
    val volume = parts.firstOrNull().orEmpty()
    val path = parts.getOrNull(1).orEmpty().trim('/')
    return when {
        volume.equals("primary", ignoreCase = true) && path.isBlank() -> "/storage/emulated/0"
        volume.equals("primary", ignoreCase = true) -> "/storage/emulated/0/$path"
        else -> null
    }
}
