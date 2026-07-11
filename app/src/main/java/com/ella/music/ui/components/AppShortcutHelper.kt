package com.ella.music.ui.components

import android.content.Context
import android.content.Intent
import android.content.pm.ShortcutInfo
import android.content.pm.ShortcutManager
import android.graphics.drawable.Icon
import android.os.Build
import com.ella.music.MainActivity
import com.ella.music.R
import com.ella.music.data.SettingsManager
import com.ella.music.ui.navigation.EXTRA_SHORTCUT_ACTION
import com.ella.music.ui.navigation.EXTRA_SHORTCUT_ROUTE
import com.ella.music.ui.navigation.Screen
import com.ella.music.ui.navigation.SHORTCUT_ACTION_PLAY
import com.ella.music.ui.navigation.SHORTCUT_ACTION_SHUFFLE_ALL

private const val SHORTCUT_LIBRARY = "library"
private const val SHORTCUT_SEARCH = "search"
private const val SHORTCUT_PLAY = "play"
private const val SHORTCUT_SHUFFLE_ALL = "shuffle_all"
private const val SHORTCUT_SETTINGS = "settings"

fun updateEllaDynamicShortcuts(
    context: Context,
    libraryLabel: String,
    searchLabel: String,
    shuffleLabel: String
) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N_MR1) return
    val shortcutManager = context.getSystemService(ShortcutManager::class.java) ?: return
    val resolvedLibraryLabel = libraryLabel.localizedShortcutDefault(
        SettingsManager.DEFAULT_SHORTCUT_LIBRARY_LABEL,
        context.getString(R.string.shortcut_library_short)
    )
    val resolvedSearchLabel = searchLabel.localizedShortcutDefault(
        SettingsManager.DEFAULT_SHORTCUT_PLAYLISTS_LABEL,
        context.getString(R.string.shortcut_search_short)
    )
    val resolvedShuffleLabel = shuffleLabel.localizedShortcutDefault(
        SettingsManager.DEFAULT_SHORTCUT_FOLDER_LABEL,
        context.getString(R.string.shortcut_shuffle_all_short)
    )
    val shortcuts = listOf(
        context.buildEllaShortcut(
            id = SHORTCUT_LIBRARY,
            label = resolvedLibraryLabel,
            route = Screen.Library.route,
            iconRes = R.drawable.ic_shortcut_library,
            rank = 0
        ),
        context.buildEllaShortcut(
            id = SHORTCUT_SEARCH,
            label = resolvedSearchLabel,
            route = Screen.LibrarySearch.createRoute(),
            iconRes = R.drawable.ic_shortcut_search,
            rank = 1
        ),
        context.buildEllaActionShortcut(
            id = SHORTCUT_PLAY,
            label = context.getString(R.string.shortcut_play_short),
            shortcutAction = SHORTCUT_ACTION_PLAY,
            iconRes = R.drawable.ic_player_play,
            rank = 2
        ),
        context.buildEllaActionShortcut(
            id = SHORTCUT_SHUFFLE_ALL,
            label = resolvedShuffleLabel,
            shortcutAction = SHORTCUT_ACTION_SHUFFLE_ALL,
            iconRes = R.drawable.ic_shuffle,
            rank = 3
        )
    )
    runCatching { shortcutManager.removeDynamicShortcuts(listOf(SHORTCUT_SETTINGS)) }
    runCatching { shortcutManager.disableShortcuts(listOf(SHORTCUT_SETTINGS), context.getString(R.string.shortcut_playlists_removed)) }
    runCatching { shortcutManager.dynamicShortcuts = shortcuts }
    runCatching { shortcutManager.updateShortcuts(shortcuts) }
}

private fun String.localizedShortcutDefault(chineseDefault: String, localizedDefault: String): String {
    val value = trim()
    return if (value.isBlank() || value == chineseDefault) localizedDefault else value
}

fun requestPinnedEllaShortcut(
    context: Context,
    id: String,
    label: String,
    route: String
): Boolean {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return false
    val shortcutManager = context.getSystemService(ShortcutManager::class.java) ?: return false
    if (!shortcutManager.isRequestPinShortcutSupported) return false
    val intent = Intent(context, MainActivity::class.java).apply {
        action = Intent.ACTION_VIEW
        putExtra(EXTRA_SHORTCUT_ROUTE, route)
    }
    val shortcutLabel = label.shortcutLabelForRoute(route)
    val appName = context.getString(R.string.app_name)
    val shortcut = ShortcutInfo.Builder(context, id.toShortcutId())
        .setShortLabel(shortcutLabel.take(10).ifBlank { appName })
        .setLongLabel(shortcutLabel.ifBlank { appName })
        .setIcon(Icon.createWithResource(context, shortcutIconForRoute(route)))
        .setIntent(intent)
        .build()
    shortcutManager.requestPinShortcut(shortcut, null)
    return true
}

private fun String.toShortcutId(): String =
    "halcyon_${replace(Regex("[^A-Za-z0-9_.-]"), "_").take(80)}"

private fun Context.buildEllaShortcut(
    id: String,
    label: String,
    route: String,
    iconRes: Int,
    rank: Int
): ShortcutInfo {
    val appName = getString(R.string.app_name)
    return ShortcutInfo.Builder(this, id)
        .setShortLabel(label.take(10).ifBlank { appName })
        .setLongLabel(label.ifBlank { appName })
        .setIcon(Icon.createWithResource(this, iconRes))
        .setIntent(
            Intent(this, MainActivity::class.java).apply {
                action = Intent.ACTION_VIEW
                putExtra(EXTRA_SHORTCUT_ROUTE, route)
            }
        )
        .setRank(rank)
        .build()
}

private fun Context.buildEllaActionShortcut(
    id: String,
    label: String,
    shortcutAction: String,
    iconRes: Int,
    rank: Int
): ShortcutInfo {
    val appName = getString(R.string.app_name)
    return ShortcutInfo.Builder(this, id)
        .setShortLabel(label.take(10).ifBlank { appName })
        .setLongLabel(label.ifBlank { appName })
        .setIcon(Icon.createWithResource(this, iconRes))
        .setIntent(
            Intent(this, MainActivity::class.java).apply {
                action = Intent.ACTION_VIEW
                putExtra(EXTRA_SHORTCUT_ACTION, shortcutAction)
            }
        )
        .setRank(rank)
        .build()
}

private fun shortcutIconForRoute(route: String): Int = when (route) {
    Screen.Library.route -> R.drawable.ic_shortcut_library
    Screen.Playlists.createRoute() -> R.drawable.ic_shortcut_playlist
    Screen.Folder.createRoute() -> R.drawable.ic_shortcut_folder
    Screen.Album.createRoute() -> R.drawable.ic_shortcut_album
    Screen.Artist.createRoute() -> R.drawable.ic_shortcut_artist
    "category/folder" -> R.drawable.ic_shortcut_folder
    "category/genre" -> R.drawable.ic_shortcut_tag
    "category/year" -> R.drawable.ic_shortcut_calendar
    "category/composer" -> R.drawable.ic_shortcut_composer
    "category/lyricist" -> R.drawable.ic_shortcut_lyricist
    else -> shortcutIconForRoutePrefix(route)
}

private fun shortcutIconForRoutePrefix(route: String): Int = when {
    route == Screen.Album.baseRoute || route.startsWith("${Screen.Album.baseRoute}?") -> R.drawable.ic_shortcut_album
    route == Screen.Artist.baseRoute || route.startsWith("${Screen.Artist.baseRoute}?") -> R.drawable.ic_shortcut_artist
    route == Screen.Folder.baseRoute || route.startsWith("${Screen.Folder.baseRoute}?") -> R.drawable.ic_shortcut_folder
    route == Screen.Playlists.baseRoute || route.startsWith("${Screen.Playlists.baseRoute}?") -> R.drawable.ic_shortcut_playlist
    route.startsWith("album/") -> R.drawable.ic_shortcut_album
    route.startsWith("artist/") -> R.drawable.ic_shortcut_artist
    route.startsWith("folder/") -> R.drawable.ic_shortcut_folder_hierarchy
    route.startsWith("playlist/") -> R.drawable.ic_shortcut_playlist
    route.startsWith("category/folder/") -> R.drawable.ic_shortcut_folder
    route.startsWith("category/genre/") -> R.drawable.ic_shortcut_tag
    route.startsWith("category/year/") -> R.drawable.ic_shortcut_calendar
    route.startsWith("category/composer/") -> R.drawable.ic_shortcut_composer
    route.startsWith("category/lyricist/") -> R.drawable.ic_shortcut_lyricist
    else -> R.drawable.ic_music_note
}

private fun String.shortcutLabelForRoute(route: String): String {
    return if (route.startsWith("folder/") || route.startsWith("category/folder/")) {
        trim()
            .trimEnd('/', '\\')
            .substringAfterLast('/')
            .substringAfterLast('\\')
            .ifBlank { this }
    } else {
        this
    }
}
