package com.ella.music

import android.Manifest
import android.graphics.Color
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
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
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.LayoutDirection
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.NavGraph.Companion.findStartDestination
import com.ella.music.data.AppLogStore
import com.ella.music.data.SettingsManager
import kotlinx.coroutines.flow.first
import com.ella.music.ui.components.LiquidGlassBottomBar
import com.ella.music.ui.components.LiquidGlassBottomBarItem
import com.ella.music.ui.components.MiniPlayer
import com.ella.music.ui.navigation.AppNavigation
import com.ella.music.ui.navigation.Screen
import com.ella.music.ui.theme.EllaTheme
import com.ella.music.ui.theme.THEME_DARK
import com.ella.music.ui.theme.THEME_FOLLOW_SYSTEM
import com.ella.music.viewmodel.MainViewModel
import com.ella.music.viewmodel.PlayerViewModel
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Music
import top.yukonga.miuix.kmp.icon.extended.Playlist
import top.yukonga.miuix.kmp.icon.extended.Settings
import top.yukonga.miuix.kmp.theme.MiuixTheme

class MainActivity : ComponentActivity() {

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            mainViewModel?.scanMusicIfAutoEnabled()
        }
        requestVideoPermissionIfNeeded()
    }

    private val requestVideoPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) {
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
                when (settingsManager.startupPlayMode.first()) {
                    SettingsManager.STARTUP_PLAY_RANDOM -> {
                        val songs = mainVm.songs.first { it.isNotEmpty() }
                        if (playerVm.currentSong.value == null) {
                            val startIndex = songs.indices.random()
                            playerVm.setPlaylist(songs, startIndex)
                        }
                    }
                    SettingsManager.STARTUP_PLAY_RESUME -> {
                        playerVm.playRestoredQueue()
                    }
                }
                if (canScanNow) mainVm.scanMusicIfAutoEnabled()
            }

            EllaTheme(themeMode = themeMode) {
                EllaApp(mainVm, playerVm, isDark)
            }
        }
    }

    private fun requestVideoPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            requestNotificationPermissionIfNeeded()
            return
        }

        if (
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.READ_MEDIA_VIDEO
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            requestVideoPermissionLauncher.launch(Manifest.permission.READ_MEDIA_VIDEO)
        } else {
            requestNotificationPermissionIfNeeded()
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
            requestVideoPermissionIfNeeded()
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
    val isPlayerRoute = currentRoute == Screen.Player.route

    LaunchedEffect(isPlayerRoute, isDarkTheme) {
        val window = (view.context as ComponentActivity).window
        window.statusBarColor = Color.TRANSPARENT
        window.navigationBarColor = Color.TRANSPARENT
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isNavigationBarContrastEnforced = false
        }
        WindowCompat.getInsetsController(window, view).apply {
            isAppearanceLightStatusBars = if (isPlayerRoute) false else !isDarkTheme
            isAppearanceLightNavigationBars = if (isPlayerRoute) false else !isDarkTheme
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
    val currentPosition by playerViewModel.currentPosition.collectAsState()
    val duration by playerViewModel.duration.collectAsState()
    val lyrics by playerViewModel.lyrics.collectAsState()
    val currentLyricIndex by playerViewModel.currentLyricIndex.collectAsState()

    val currentLyricLine = lyrics.getOrNull(currentLyricIndex)
    val miniPlayerLyricText = if (isPlaying) {
        currentLyricLine?.text?.takeIf { it.isNotBlank() && !it.isMusicSymbolOnly() }
    } else {
        null
    }
    val miniPlayerLyricTranslation = if (isPlaying) {
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

    val showMiniPlayer = currentSong != null && currentRoute != Screen.Player.route

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

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MiuixTheme.colorScheme.background)
    ) {
        AppNavigation(
            navController = navController,
            mainViewModel = mainViewModel,
            playerViewModel = playerViewModel,
            modifier = contentModifier
        )
        FloatingBottomControls(
            showMiniPlayer = showMiniPlayer,
            showBottomBar = showBottomBar,
            currentSong = currentSong,
            isPlaying = isPlaying,
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
                AppLogStore.debug(context, "BottomNav", "click route=$route current=$currentRoute")
                if (currentRoute != route) {
                    navController.navigate(route) {
                        popUpTo(navController.graph.findStartDestination().id) {
                            saveState = false
                        }
                        launchSingleTop = true
                        restoreState = false
                    }
                    AppLogStore.debug(context, "BottomNav", "navigate route=$route")
                } else {
                    AppLogStore.debug(context, "BottomNav", "ignored same route=$route")
                }
            },
            onNavigatePlayer = { navController.navigate(Screen.Player.route) },
            modifier = Modifier.align(androidx.compose.ui.Alignment.BottomCenter)
        )
    }
}

@Composable
private fun FloatingBottomControls(
    showMiniPlayer: Boolean,
    showBottomBar: Boolean,
    currentSong: com.ella.music.data.model.Song?,
    isPlaying: Boolean,
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
