package com.ella.music.ui.navigation

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.ella.music.ui.about.AboutScreen
import com.ella.music.ui.about.UpdateScreen
import com.ella.music.ui.analytics.AnalyticsScreen
import com.ella.music.ui.analytics.LibraryAnalysisScreen
import com.ella.music.ui.analytics.PlaybackHistoryScreen
import com.ella.music.ui.album.AlbumDetailScreen
import com.ella.music.ui.album.AlbumScreen
import com.ella.music.ui.artist.ArtistListScreen
import com.ella.music.ui.artist.ArtistScreen
import com.ella.music.ui.category.MetadataCategoryDetailScreen
import com.ella.music.ui.category.MetadataCategoryScreen
import com.ella.music.ui.folder.FolderDetailScreen
import com.ella.music.ui.folder.FolderScreen
import com.ella.music.ui.folder.ScanSettingsScreen
import com.ella.music.ui.folder.WebDavScreen
import com.ella.music.ui.home.HomeScreen
import com.ella.music.ui.home.LibraryScreen
import com.ella.music.ui.online.LxOnlineScreen
import com.ella.music.ui.online.LxSourceSettingsScreen
import com.ella.music.ui.online.MusicFreePluginSettingsScreen
import com.ella.music.ui.online.MusicFreeOnlineScreen
import com.ella.music.ui.playlist.PlaylistDetailScreen
import com.ella.music.ui.playlist.PlaylistScreen
import com.ella.music.ui.settings.AudioSettingsScreen
import com.ella.music.ui.settings.BackupSettingsScreen
import com.ella.music.ui.settings.LyricFontScreen
import com.ella.music.ui.settings.LogScreen
import com.ella.music.ui.settings.SettingsDetailScreen
import com.ella.music.ui.settings.SettingsScreen
import com.ella.music.viewmodel.MainViewModel
import com.ella.music.viewmodel.PlayerViewModel

sealed class Screen(val route: String) {
    data object Home : Screen("home")
    data object Library : Screen("library")
    data object Album : Screen("album")
    data object Artist : Screen("artist")
    data object AlbumDetail : Screen("album/{albumId}") {
        fun createRoute(albumId: Long) = "album/$albumId"
    }
    data object ArtistDetail : Screen("artist/{artistName}") {
        fun createRoute(artistName: String) = "artist/${java.net.URLEncoder.encode(artistName, "UTF-8")}"
    }
    data object Folder : Screen("folder")
    data object ScanSettings : Screen("scan_settings")
    data object MetadataCategory : Screen("category/{type}") {
        fun createRoute(type: String) = "category/${java.net.URLEncoder.encode(type, "UTF-8")}"
    }
    data object MetadataCategoryDetail : Screen("category/{type}/{name}") {
        fun createRoute(type: String, name: String) =
            "category/${java.net.URLEncoder.encode(type, "UTF-8")}/${java.net.URLEncoder.encode(name, "UTF-8")}"
    }
    data object Playlists : Screen("playlists")
    data object PlaylistDetail : Screen("playlist/{playlistId}") {
        fun createRoute(playlistId: String) = "playlist/${java.net.URLEncoder.encode(playlistId, "UTF-8")}"
    }
    data object WebDav : Screen("webdav")
    data object FolderDetail : Screen("folder/{folderPath}") {
        fun createRoute(folderPath: String) = "folder/${java.net.URLEncoder.encode(folderPath, "UTF-8")}"
    }
    data object LibraryAnalysis : Screen("library_analysis")
    data object Settings : Screen("settings")
    data object SettingsDetail : Screen("settings_detail")
    data object LyricSettings : Screen("lyric_settings")
    data object AudioSettings : Screen("audio_settings")
    data object BackupSettings : Screen("backup_settings")
    data object LyricFont : Screen("lyric_font")
    data object Logs : Screen("logs")
    data object LxOnline : Screen("lx_online")
    data object LxSourceSettings : Screen("lx_source_settings")
    data object MusicFreeOnline : Screen("musicfree_online")
    data object MusicFreePluginSettings : Screen("musicfree_plugin_settings")
    data object Analytics : Screen("analytics")
    data object PlaybackHistory : Screen("playback_history")
    data object About : Screen("about")
    data object Update : Screen("update")
    data object Player : Screen("player")
}

@Composable
fun AppNavigation(
    navController: NavHostController,
    mainViewModel: MainViewModel,
    playerViewModel: PlayerViewModel,
    modifier: Modifier = Modifier,
    onNavigateToPlayer: () -> Unit = {}
) {
    NavHost(
        navController = navController,
        startDestination = Screen.Home.route,
        modifier = modifier,
        enterTransition = {
            fadeIn(animationSpec = tween(300)) + slideIntoContainer(
                AnimatedContentTransitionScope.SlideDirection.Start, tween(300)
            )
        },
        exitTransition = { fadeOut(animationSpec = tween(300)) },
        popEnterTransition = { fadeIn(animationSpec = tween(300)) },
        popExitTransition = {
            fadeOut(animationSpec = tween(300)) + slideOutOfContainer(
                AnimatedContentTransitionScope.SlideDirection.End, tween(300)
            )
        }
    ) {
        composable(Screen.Home.route) {
            HomeScreen(
                mainViewModel = mainViewModel,
                playerViewModel = playerViewModel,
                onNavigateToLibrary = { navController.navigate(Screen.Library.route) },
                onNavigateToArtist = { navController.navigate(Screen.Artist.route) },
                onNavigateToAlbum = { navController.navigate(Screen.Album.route) },
                onNavigateToFolder = { navController.navigate(Screen.Folder.route) },
                onNavigateToPlaylists = { navController.navigate(Screen.Playlists.route) },
                onNavigateToLxOnline = { navController.navigate(Screen.LxOnline.route) },
                onNavigateToMusicFreeOnline = { navController.navigate(Screen.MusicFreeOnline.route) },
                onNavigateToWebDav = { navController.navigate(Screen.WebDav.route) },
                onNavigateToAnalytics = { navController.navigate(Screen.Analytics.route) },
                onNavigateToMetadataCategory = { type -> navController.navigate(Screen.MetadataCategory.createRoute(type)) },
                onNavigateToPlayer = onNavigateToPlayer
            )
        }

        composable(Screen.Library.route) {
            LibraryScreen(
                mainViewModel = mainViewModel,
                playerViewModel = playerViewModel,
                onNavigateToPlayer = onNavigateToPlayer,
                onNavigateToAbout = { navController.navigate(Screen.About.route) },
                onNavigateToAlbum = { albumId -> navController.navigate(Screen.AlbumDetail.createRoute(albumId)) },
                onNavigateToArtist = { artistName -> navController.navigate(Screen.ArtistDetail.createRoute(artistName)) }
            )
        }

        composable(Screen.Album.route) {
            AlbumScreen(
                mainViewModel = mainViewModel,
                playerViewModel = playerViewModel,
                onBack = { navController.popBackStack() },
                onAlbumClick = { albumId ->
                    navController.navigate(Screen.AlbumDetail.createRoute(albumId))
                }
            )
        }

        composable(Screen.Artist.route) {
            ArtistListScreen(
                mainViewModel = mainViewModel,
                onBack = { navController.popBackStack() },
                onArtistClick = { artistName ->
                    navController.navigate(Screen.ArtistDetail.createRoute(artistName))
                }
            )
        }

        composable(
            route = Screen.AlbumDetail.route,
            arguments = listOf(navArgument("albumId") { type = NavType.LongType })
        ) { backStackEntry ->
            val albumId = backStackEntry.arguments?.getLong("albumId") ?: 0L
            AlbumDetailScreen(
                albumId = albumId,
                mainViewModel = mainViewModel,
                playerViewModel = playerViewModel,
                onBack = { navController.popBackStack() },
                onNavigateToPlayer = onNavigateToPlayer
            )
        }

        composable(Screen.Folder.route) {
            FolderScreen(
                mainViewModel = mainViewModel,
                playerViewModel = playerViewModel,
                onBack = { navController.popBackStack() },
                onNavigateToPlayer = onNavigateToPlayer,
                onNavigateToLibraryAnalysis = { navController.navigate(Screen.LibraryAnalysis.route) },
                onNavigateToScanSettings = { navController.navigate(Screen.ScanSettings.route) },
                onFolderClick = { folderPath ->
                    navController.navigate(Screen.FolderDetail.createRoute(folderPath))
                }
            )
        }

        composable(Screen.ScanSettings.route) {
            ScanSettingsScreen(
                mainViewModel = mainViewModel,
                onBack = { navController.popBackStack() }
            )
        }

        composable(
            route = Screen.MetadataCategory.route,
            arguments = listOf(navArgument("type") { type = NavType.StringType })
        ) { backStackEntry ->
            val type = java.net.URLDecoder.decode(
                backStackEntry.arguments?.getString("type").orEmpty(),
                "UTF-8"
            )
            MetadataCategoryScreen(
                type = type,
                mainViewModel = mainViewModel,
                onBack = { navController.popBackStack() },
                onCategoryClick = { name ->
                    navController.navigate(Screen.MetadataCategoryDetail.createRoute(type, name))
                }
            )
        }

        composable(
            route = Screen.MetadataCategoryDetail.route,
            arguments = listOf(
                navArgument("type") { type = NavType.StringType },
                navArgument("name") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val type = java.net.URLDecoder.decode(
                backStackEntry.arguments?.getString("type").orEmpty(),
                "UTF-8"
            )
            val name = java.net.URLDecoder.decode(
                backStackEntry.arguments?.getString("name").orEmpty(),
                "UTF-8"
            )
            MetadataCategoryDetailScreen(
                type = type,
                name = name,
                mainViewModel = mainViewModel,
                playerViewModel = playerViewModel,
                onBack = { navController.popBackStack() },
                onNavigateToPlayer = onNavigateToPlayer
            )
        }

        composable(Screen.Playlists.route) {
            PlaylistScreen(
                mainViewModel = mainViewModel,
                onBack = { navController.popBackStack() },
                onPlaylistClick = { playlistId ->
                    navController.navigate(Screen.PlaylistDetail.createRoute(playlistId))
                }
            )
        }

        composable(
            route = Screen.PlaylistDetail.route,
            arguments = listOf(navArgument("playlistId") { type = NavType.StringType })
        ) { backStackEntry ->
            val playlistId = java.net.URLDecoder.decode(
                backStackEntry.arguments?.getString("playlistId").orEmpty(),
                "UTF-8"
            )
            PlaylistDetailScreen(
                playlistId = playlistId,
                mainViewModel = mainViewModel,
                playerViewModel = playerViewModel,
                onBack = { navController.popBackStack() },
                onNavigateToPlayer = onNavigateToPlayer
            )
        }

        composable(Screen.WebDav.route) {
            WebDavScreen(
                mainViewModel = mainViewModel,
                playerViewModel = playerViewModel,
                onBack = { navController.popBackStack() },
                onNavigateToPlayer = onNavigateToPlayer
            )
        }

        composable(
            route = Screen.ArtistDetail.route,
            arguments = listOf(navArgument("artistName") { type = NavType.StringType })
        ) { backStackEntry ->
            val artistName = java.net.URLDecoder.decode(
                backStackEntry.arguments?.getString("artistName") ?: "",
                "UTF-8"
            )
            ArtistScreen(
                artistName = artistName,
                mainViewModel = mainViewModel,
                playerViewModel = playerViewModel,
                onBack = { navController.popBackStack() },
                onAlbumClick = { albumId -> navController.navigate(Screen.AlbumDetail.createRoute(albumId)) },
                onNavigateToPlayer = onNavigateToPlayer
            )
        }

        composable(
            route = Screen.FolderDetail.route,
            arguments = listOf(navArgument("folderPath") { type = NavType.StringType })
        ) { backStackEntry ->
            val folderPath = java.net.URLDecoder.decode(
                backStackEntry.arguments?.getString("folderPath") ?: "",
                "UTF-8"
            )
            FolderDetailScreen(
                folderPath = folderPath,
                mainViewModel = mainViewModel,
                playerViewModel = playerViewModel,
                onBack = { navController.popBackStack() },
                onFolderClick = { childFolderPath ->
                    navController.navigate(Screen.FolderDetail.createRoute(childFolderPath))
                },
                onNavigateToPlayer = onNavigateToPlayer
            )
        }

        composable(Screen.Settings.route) {
            SettingsScreen(
                onNavigateToAbout = { navController.navigate(Screen.About.route) },
                onNavigateToSettingsDetail = { navController.navigate(Screen.SettingsDetail.route) },
                onNavigateToLyricSettings = { navController.navigate(Screen.LyricSettings.route) },
                onNavigateToAudioSettings = { navController.navigate(Screen.AudioSettings.route) },
                onNavigateToBackupSettings = { navController.navigate(Screen.BackupSettings.route) },
                onNavigateToLogs = { navController.navigate(Screen.Logs.route) },
                mainViewModel = mainViewModel,
                playerViewModel = playerViewModel
            )
        }

        composable(Screen.AudioSettings.route) {
            AudioSettingsScreen(
                onBack = { navController.popBackStack() },
                playerViewModel = playerViewModel
            )
        }

        composable(Screen.BackupSettings.route) {
            BackupSettingsScreen(
                onBack = { navController.popBackStack() }
            )
        }

        composable(Screen.SettingsDetail.route) {
            SettingsDetailScreen(
                onBack = { navController.popBackStack() },
                onNavigateToLyricFont = { navController.navigate(Screen.LyricFont.route) }
            )
        }

        composable(Screen.LyricSettings.route) {
            SettingsDetailScreen(
                onBack = { navController.popBackStack() },
                onNavigateToLyricFont = { navController.navigate(Screen.LyricFont.route) },
                playerViewModel = playerViewModel,
                showOnlyLyrics = true
            )
        }

        composable(Screen.LyricFont.route) {
            LyricFontScreen(
                onBack = { navController.popBackStack() }
            )
        }

        composable(Screen.Logs.route) {
            LogScreen(
                onBack = { navController.popBackStack() }
            )
        }

        composable(Screen.LxOnline.route) {
            LxOnlineScreen(
                playerViewModel = playerViewModel,
                onBack = { navController.popBackStack() },
                onNavigateToPlayer = onNavigateToPlayer,
                onNavigateToSourceSettings = { navController.navigate(Screen.LxSourceSettings.route) }
            )
        }

        composable(Screen.LxSourceSettings.route) {
            LxSourceSettingsScreen(
                onBack = { navController.popBackStack() }
            )
        }

        composable(Screen.MusicFreeOnline.route) {
            MusicFreeOnlineScreen(
                playerViewModel = playerViewModel,
                onBack = { navController.popBackStack() },
                onNavigateToPlayer = onNavigateToPlayer,
                onNavigateToPluginSettings = { navController.navigate(Screen.MusicFreePluginSettings.route) }
            )
        }

        composable(Screen.MusicFreePluginSettings.route) {
            MusicFreePluginSettingsScreen(
                onBack = { navController.popBackStack() }
            )
        }

        composable(Screen.Analytics.route) {
            AnalyticsScreen(
                mainViewModel = mainViewModel,
                onBack = { navController.popBackStack() },
                onNavigateToHistory = { navController.navigate(Screen.PlaybackHistory.route) }
            )
        }

        composable(Screen.PlaybackHistory.route) {
            PlaybackHistoryScreen(
                mainViewModel = mainViewModel,
                onBack = { navController.popBackStack() }
            )
        }

        composable(Screen.LibraryAnalysis.route) {
            LibraryAnalysisScreen(
                mainViewModel = mainViewModel,
                onBack = { navController.popBackStack() }
            )
        }

        composable(Screen.About.route) {
            AboutScreen(
                onBack = { navController.popBackStack() },
                onNavigateToUpdate = { navController.navigate(Screen.Update.route) }
            )
        }

        composable(Screen.Update.route) {
            UpdateScreen(
                onBack = { navController.popBackStack() }
            )
        }

    }
}
