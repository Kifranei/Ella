package com.ella.music.data

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import com.ella.music.data.model.FAVORITES_PLAYLIST_ID
import com.ella.music.data.model.Song
import com.ella.music.data.model.UserPlaylist
import com.ella.music.data.model.playlistIdentityKey
import com.ella.music.data.model.toJson
import com.ella.music.data.model.toPlaylistSong
import com.ella.music.data.model.toUserPlaylist
import java.io.File
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

data class PlaylistImportResult(
    val playlist: UserPlaylist?,
    val importedCount: Int,
    val matchedCount: Int,
    val missingCount: Int,
    val duplicateCount: Int
)

data class PlaylistExportResult(
    val exportedCount: Int,
    val skippedCount: Int
)

class PlaylistStore private constructor(context: Context) {
    private val appContext = context.applicationContext
    private val file = File(appContext.filesDir, "ella_playlists.json")
    private val lock = Any()
    private val _playlists = MutableStateFlow(loadPlaylists())

    val playlists: StateFlow<List<UserPlaylist>> = _playlists.asStateFlow()

    fun favoriteSongKeys(): Set<String> =
        playlists.value
            .firstOrNull { it.id == FAVORITES_PLAYLIST_ID }
            ?.songs
            ?.mapTo(mutableSetOf()) { it.key }
            ?: emptySet()

    fun isFavorite(song: Song): Boolean =
        song.playlistIdentityKey() in favoriteSongKeys()

    suspend fun toggleFavorite(song: Song): Boolean = withContext(Dispatchers.IO) {
        val key = song.playlistIdentityKey()
        var added = false
        synchronized(lock) {
            val now = System.currentTimeMillis()
            val next = playlists.value.withPlaylist(FAVORITES_PLAYLIST_ID) { playlist ->
                val exists = playlist.songs.any { it.key == key }
                added = !exists
                val nextSongs = if (exists) {
                    playlist.songs.filterNot { it.key == key }
                } else {
                    listOf(song.toPlaylistSong(now)) + playlist.songs
                }
                playlist.copy(songs = nextSongs, updatedAt = now)
            }
            _playlists.value = next
            saveLocked(next)
        }
        added
    }

    suspend fun createPlaylist(name: String): UserPlaylist? = withContext(Dispatchers.IO) {
        val trimmed = name.trim()
        if (trimmed.isBlank()) return@withContext null
        synchronized(lock) {
            val now = System.currentTimeMillis()
            val playlist = UserPlaylist(
                id = "playlist-${UUID.randomUUID()}",
                name = trimmed,
                createdAt = now,
                updatedAt = now
            )
            val next = playlists.value + playlist
            _playlists.value = next
            saveLocked(next)
            playlist
        }
    }

    suspend fun deletePlaylist(id: String) = withContext(Dispatchers.IO) {
        if (id == FAVORITES_PLAYLIST_ID) return@withContext
        synchronized(lock) {
            val next = playlists.value.filterNot { it.id == id }.ensureFavorites()
            _playlists.value = next
            saveLocked(next)
        }
    }

    suspend fun addSongToPlaylist(playlistId: String, song: Song) = withContext(Dispatchers.IO) {
        val key = song.playlistIdentityKey()
        synchronized(lock) {
            val now = System.currentTimeMillis()
            val next = playlists.value.withPlaylist(playlistId) { playlist ->
                if (playlist.songs.any { it.key == key }) return@withPlaylist playlist
                playlist.copy(
                    songs = listOf(song.toPlaylistSong(now)) + playlist.songs,
                    updatedAt = now
                )
            }
            _playlists.value = next
            saveLocked(next)
        }
    }

    suspend fun addSongsToPlaylist(playlistId: String, songs: Collection<Song>) = withContext(Dispatchers.IO) {
        if (songs.isEmpty()) return@withContext
        synchronized(lock) {
            val now = System.currentTimeMillis()
            val next = playlists.value.withPlaylist(playlistId) { playlist ->
                val existingKeys = playlist.songs.mapTo(mutableSetOf()) { it.key }
                val newSongs = songs
                    .filter { existingKeys.add(it.playlistIdentityKey()) }
                    .map { it.toPlaylistSong(now) }
                if (newSongs.isEmpty()) return@withPlaylist playlist
                playlist.copy(
                    songs = newSongs + playlist.songs,
                    updatedAt = now
                )
            }
            _playlists.value = next
            saveLocked(next)
        }
    }

    suspend fun importSaltPlayerPlaylist(uri: Uri, librarySongs: List<Song>): PlaylistImportResult =
        importLocalPlaylist(uri, librarySongs)

    suspend fun importLocalPlaylist(uri: Uri, librarySongs: List<Song>): PlaylistImportResult =
        withContext(Dispatchers.IO) {
            val rawEntries = appContext.contentResolver.openInputStream(uri)
                ?.bufferedReader(Charsets.UTF_8)
                ?.useLines { lines ->
                    lines.toList().toPlaylistEntries()
                }
                .orEmpty()

            val seen = mutableSetOf<String>()
            val paths = rawEntries.filter { seen.add(it.normalizedPlaylistPath()) }
            val duplicateCount = rawEntries.size - paths.size
            if (paths.isEmpty()) {
                return@withContext PlaylistImportResult(
                    playlist = null,
                    importedCount = 0,
                    matchedCount = 0,
                    missingCount = 0,
                    duplicateCount = duplicateCount
                )
            }

            val libraryByPath = librarySongs.associateBy { it.path.normalizedPlaylistPath() }
            val libraryByFileName = librarySongs
                .groupBy { it.path.substringAfterLast('/').ifBlank { it.fileName }.normalizedPlaylistPath() }
            var matchedCount = 0
            val now = System.currentTimeMillis()
            val playlistSongs = paths.mapIndexed { index, path ->
                val matched = matchPlaylistEntry(path, librarySongs, libraryByPath, libraryByFileName)
                if (matched != null) {
                    matchedCount += 1
                    matched.toPlaylistSong(now + index)
                } else {
                    placeholderSong(path).toPlaylistSong(now + index)
                }
            }

            synchronized(lock) {
                val playlist = UserPlaylist(
                    id = "playlist-${UUID.randomUUID()}",
                    name = playlistNameFromUri(uri),
                    songs = playlistSongs,
                    createdAt = now,
                    updatedAt = now
                )
                val next = playlists.value + playlist
                _playlists.value = next
                saveLocked(next)
                PlaylistImportResult(
                    playlist = playlist,
                    importedCount = playlistSongs.size,
                    matchedCount = matchedCount,
                    missingCount = playlistSongs.size - matchedCount,
                    duplicateCount = duplicateCount
                )
            }
        }

    private fun List<String>.toPlaylistEntries(): List<String> {
        return map { it.trim().trimStart('\uFEFF') }
            .filter { it.isNotBlank() }
            .mapNotNull { line ->
                when {
                    line.startsWith("#") -> null
                    else -> line.toPlaylistPath()
                }
            }
            .filter { it.isNotBlank() }
    }

    private fun String.toPlaylistPath(): String {
        val text = trim().trim('"', '\'')
        val decoded = Uri.decode(text)
        if (decoded.startsWith("file://", ignoreCase = true)) {
            return Uri.parse(decoded).path.orEmpty().replace('\\', '/')
        }
        return decoded.replace('\\', '/')
    }

    private fun matchPlaylistEntry(
        entry: String,
        librarySongs: List<Song>,
        libraryByPath: Map<String, Song>,
        libraryByFileName: Map<String, List<Song>>
    ): Song? {
        val normalized = entry.normalizedPlaylistPath()
        libraryByPath[normalized]?.let { return it }

        if ('/' in normalized) {
            librarySongs.firstOrNull { song ->
                song.path.normalizedPlaylistPath().endsWith("/$normalized")
            }?.let { return it }
        }

        val fileName = normalized.substringAfterLast('/')
        return libraryByFileName[fileName]
            ?.takeIf { it.size == 1 }
            ?.firstOrNull()
    }

    suspend fun exportSaltPlayerPlaylist(playlist: UserPlaylist, uri: Uri): PlaylistExportResult =
        withContext(Dispatchers.IO) {
            val paths = playlist.songs
                .map { it.path.trim() }
                .filter { it.isNotBlank() && !it.startsWith("http://") && !it.startsWith("https://") }
                .distinctBy { it.normalizedPlaylistPath() }
            val content = paths.joinToString(separator = "\n", postfix = if (paths.isNotEmpty()) "\n" else "")
            appContext.contentResolver.openOutputStream(uri, "wt")?.use { output ->
                output.write(content.toByteArray(Charsets.UTF_8))
            } ?: error("无法打开导出文件")
            PlaylistExportResult(
                exportedCount = paths.size,
                skippedCount = playlist.songs.size - paths.size
            )
        }

    suspend fun removeSongFromPlaylist(playlistId: String, songKey: String) = withContext(Dispatchers.IO) {
        synchronized(lock) {
            val now = System.currentTimeMillis()
            val next = playlists.value.withPlaylist(playlistId) { playlist ->
                playlist.copy(
                    songs = playlist.songs.filterNot { it.key == songKey },
                    updatedAt = now
                )
            }
            _playlists.value = next
            saveLocked(next)
        }
    }

    private fun loadPlaylists(): List<UserPlaylist> {
        if (!file.exists()) return emptyList<UserPlaylist>().ensureFavorites()
        return runCatching {
            val root = JSONObject(file.readText())
            val items = root.optJSONArray("playlists") ?: JSONArray()
            List(items.length()) { index -> items.optJSONObject(index)?.toUserPlaylist() }
                .filterNotNull()
                .filter { it.id.isNotBlank() && it.name.isNotBlank() }
                .ensureFavorites()
        }.getOrElse {
            Log.w(TAG, "Failed to load playlists", it)
            emptyList<UserPlaylist>().ensureFavorites()
        }
    }

    private fun List<UserPlaylist>.ensureFavorites(): List<UserPlaylist> {
        if (any { it.id == FAVORITES_PLAYLIST_ID }) return this
        val now = System.currentTimeMillis()
        return listOf(
            UserPlaylist(
                id = FAVORITES_PLAYLIST_ID,
                name = "我喜欢的音乐",
                createdAt = now,
                updatedAt = now
            )
        ) + this
    }

    private fun List<UserPlaylist>.withPlaylist(
        playlistId: String,
        transform: (UserPlaylist) -> UserPlaylist
    ): List<UserPlaylist> {
        val ensured = ensureFavorites()
        return ensured.map { playlist ->
            if (playlist.id == playlistId) transform(playlist) else playlist
        }
    }

    private fun saveLocked(playlists: List<UserPlaylist>) {
        runCatching {
            val root = JSONObject()
                .put("version", 1)
                .put("playlists", JSONArray().also { array ->
                    playlists.ensureFavorites().forEach { array.put(it.toJson()) }
                })
            file.writeText(root.toString())
        }.onFailure {
            Log.w(TAG, "Failed to save playlists", it)
        }
    }

    private fun playlistNameFromUri(uri: Uri): String {
        val displayName = runCatching {
            appContext.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                ?.use { cursor ->
                    if (cursor.moveToFirst()) cursor.getString(0) else null
                }
        }.getOrNull()
            ?.substringBeforeLast('.')
            ?.trim()
            .orEmpty()
        return displayName.ifBlank { "导入歌单" }
    }

    private fun placeholderSong(path: String): Song {
        val fileName = path.substringAfterLast('/').ifBlank { path }
        val title = fileName.substringBeforeLast('.').ifBlank { fileName }
        return Song(
            id = 0L,
            title = title,
            artist = "Unknown",
            album = "Unknown",
            albumId = 0L,
            duration = 0L,
            path = path,
            fileName = fileName
        )
    }

    private fun String.normalizedPlaylistPath(): String =
        trim().replace('\\', '/').lowercase()

    companion object {
        private const val TAG = "PlaylistStore"

        @Volatile
        private var instance: PlaylistStore? = null

        fun getInstance(context: Context): PlaylistStore =
            instance ?: synchronized(this) {
                instance ?: PlaylistStore(context).also { instance = it }
            }
    }
}
