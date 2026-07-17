package com.ella.music.desktop

import kotlinx.serialization.Serializable
import java.util.Locale

/**
 * Desktop's persisted representation deliberately uses file paths instead of Android content URIs.
 * Keeping it independent from the Android [Song] type lets both clients evolve without corrupting
 * each other's library databases.
 */
@Serializable
data class DesktopSong(
    val id: String,
    val path: String,
    val title: String,
    val artist: String = "Unknown artist",
    val album: String = "Unknown album",
    val albumArtist: String = "",
    val durationMs: Long = 0L,
    val fileSize: Long = 0L,
    val format: String = "",
    val trackNumber: Int = 0,
    val discNumber: Int = 0,
    val genre: String = "",
    val year: String = "",
    val composer: String = "",
    val lyricist: String = "",
    val coverPath: String? = null,
    val lyricPath: String? = null
) {
    val durationText: String
        get() = durationMs.formatDuration()

    val displayArtist: String
        get() = artist.ifBlank { "Unknown artist" }

    val displayAlbum: String
        get() = album.ifBlank { "Unknown album" }
}

@Serializable
data class DesktopPlaylist(
    val id: String,
    val name: String,
    val songIds: List<String> = emptyList()
)

@Serializable
data class DesktopLibraryState(
    val schemaVersion: Int = 1,
    val libraryRoots: List<String> = emptyList(),
    val songs: List<DesktopSong> = emptyList(),
    val playlists: List<DesktopPlaylist> = emptyList(),
    val floatingLyricsEnabled: Boolean = false,
    // Preserve the desktop player's old continuous-queue behavior while making it configurable.
    val shuffleEnabled: Boolean = false,
    val repeatMode: DesktopRepeatMode = DesktopRepeatMode.ALL,
    val playbackVolume: Float = 1f
)

@Serializable
enum class DesktopRepeatMode(val label: String) {
    OFF("Off"),
    ALL("All"),
    ONE("One");

    fun next(): DesktopRepeatMode = when (this) {
        OFF -> ALL
        ALL -> ONE
        ONE -> OFF
    }
}

enum class DesktopSection(val label: String) {
    LIBRARY("Library"),
    ALBUMS("Albums"),
    ARTISTS("Artists"),
    GENRES("Genres"),
    PLAYLISTS("Playlists"),
    SETTINGS("Settings")
}

data class DesktopPlaybackState(
    val song: DesktopSong? = null,
    val isPlaying: Boolean = false,
    val positionMs: Long = 0L,
    val error: String? = null
)

data class DesktopLyricLine(
    val timeMs: Long,
    val text: String,
    val translation: String? = null
)

fun Long.formatDuration(): String {
    if (this <= 0L) return "--:--"
    val totalSeconds = this / 1_000L
    val hours = totalSeconds / 3_600L
    val minutes = (totalSeconds % 3_600L) / 60L
    val seconds = totalSeconds % 60L
    return if (hours > 0L) {
        String.format(Locale.ROOT, "%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format(Locale.ROOT, "%d:%02d", minutes, seconds)
    }
}
