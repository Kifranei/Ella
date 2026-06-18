package com.ella.music.data.repository

import android.util.Log
import com.ella.music.data.model.Song
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import org.json.JSONObject

internal data class RatingSnapshotEntry(
    val rating: Int,
    val dateModified: Long,
    val fileSize: Long,
    val path: String = "",
    val id: Long = 0L,
    val trustedLocalWrite: Boolean = false
)

internal class MusicSnapshotManager(
    private val librarySearchSnapshotFile: File,
    private val libraryRatingSnapshotFile: File,
    private val searchTextBuilder: (Song) -> String
) {
    private val searchTextCache = ConcurrentHashMap<String, String>()
    private val ratingSnapshotCache = ConcurrentHashMap<String, RatingSnapshotEntry>()
    @Volatile private var searchSnapshotLoaded = false
    @Volatile private var searchSnapshotDirty = false
    @Volatile private var ratingSnapshotLoaded = false
    @Volatile private var ratingSnapshotDirty = false

    fun getSongSearchText(song: Song): String {
        ensureSearchSnapshotLoaded()
        val key = song.searchSnapshotKey()
        searchTextCache[key]?.let { return it }
        val text = buildSongSearchText(song)
        searchTextCache[key] = text
        searchSnapshotDirty = true
        return text
    }

    fun getSongRating(song: Song): Int {
        ensureRatingSnapshotLoaded()
        val key = song.searchSnapshotKey()
        val entry = ratingSnapshotCache[key] ?: ratingSnapshotCache.values.firstOrNull { entry ->
            entry.path.isNotBlank() && entry.path == song.path
        }?.also { migrated ->
            ratingSnapshotCache[key] = migrated.copy(id = song.id, path = song.path)
            ratingSnapshotDirty = true
        } ?: return 0
        return if (entry.isFreshFor(song)) entry.rating else 0
    }

    fun updateRatingSnapshot(song: Song, rating: Int, trustedLocalWrite: Boolean = true) {
        ensureRatingSnapshotLoaded()
        ratingSnapshotCache[song.searchSnapshotKey()] = RatingSnapshotEntry(
            rating = rating.coerceIn(0, 5),
            dateModified = song.dateModified,
            fileSize = song.fileSize,
            path = song.path,
            id = song.id,
            trustedLocalWrite = trustedLocalWrite
        )
        ratingSnapshotDirty = true
    }

    internal fun ratingSnapshotEntry(song: Song): RatingSnapshotEntry? {
        ensureRatingSnapshotLoaded()
        return ratingSnapshotCache[song.searchSnapshotKey()]
    }

    fun preloadSearchSnapshot(songs: List<Song>) {
        ensureSearchSnapshotLoaded()
        songs.forEach { song ->
            val key = song.searchSnapshotKey()
            if (!searchTextCache.containsKey(key)) {
                searchTextCache[key] = buildSongSearchText(song)
            }
        }
        searchSnapshotDirty = true
        saveSearchSnapshot()
    }

    fun preloadSongRatings(songs: List<Song>) {
        ensureRatingSnapshotLoaded()
        songs.forEach { song ->
            val key = song.searchSnapshotKey()
            if (!ratingSnapshotCache.containsKey(key)) {
                ratingSnapshotCache[key] = RatingSnapshotEntry(
                    rating = 0,
                    dateModified = song.dateModified,
                    fileSize = song.fileSize,
                    path = song.path,
                    id = song.id,
                    trustedLocalWrite = false
                )
            }
        }
        ratingSnapshotDirty = true
    }

    suspend fun songMatchesSearchSnapshot(song: Song, query: String): Boolean {
        val text = getSongSearchText(song)
        return text.contains(query, ignoreCase = true)
    }

    suspend fun filterSongsBySearchSnapshot(songs: List<Song>, query: String): List<Song> {
        if (query.isBlank()) return songs
        return songs.filter { songMatchesSearchSnapshot(it, query) }
    }

    fun clearCache() {
        ratingSnapshotCache.clear()
        ratingSnapshotLoaded = false
        ratingSnapshotDirty = false
    }

    fun clearLibraryCache() {
        searchTextCache.clear()
        searchSnapshotLoaded = true
        searchSnapshotDirty = false
        if (librarySearchSnapshotFile.exists()) librarySearchSnapshotFile.delete()
        ratingSnapshotCache.clear()
        ratingSnapshotLoaded = true
        ratingSnapshotDirty = false
        if (libraryRatingSnapshotFile.exists()) libraryRatingSnapshotFile.delete()
    }

    fun clearMetadataCache(song: Song) {
        ensureRatingSnapshotLoaded()
        ratingSnapshotCache.keys.removeAll { it == song.searchSnapshotKey() }
        ratingSnapshotDirty = true
        saveRatingSnapshot()
        ensureSearchSnapshotLoaded()
        searchTextCache.keys.removeAll { it == song.searchSnapshotKey() }
        searchSnapshotDirty = true
        saveSearchSnapshot()
    }

    fun clearMissingFileSnapshots(existingPaths: Set<String>): Int {
        ensureRatingSnapshotLoaded()
        val before = ratingSnapshotCache.size
        ratingSnapshotCache.keys.removeAll { key ->
            val entry = ratingSnapshotCache[key] ?: return@removeAll false
            entry.path.isNotBlank() && entry.path !in existingPaths
        }
        val removed = before - ratingSnapshotCache.size
        if (removed > 0) {
            ratingSnapshotDirty = true
            saveRatingSnapshot()
        }
        return removed
    }

    fun saveAll() {
        saveSearchSnapshot()
        saveRatingSnapshot()
    }

    private fun buildSongSearchText(song: Song): String = searchTextBuilder(song)

    private fun ensureSearchSnapshotLoaded() {
        if (searchSnapshotLoaded) return
        synchronized(searchTextCache) {
            if (searchSnapshotLoaded) return
            if (librarySearchSnapshotFile.exists()) {
                runCatching {
                    val root = JSONObject(librarySearchSnapshotFile.readText())
                    root.keys().forEach { key ->
                        val value = root.optString(key)
                        val parts = key.split('|')
                        val stableKey = if (parts.size >= 2) "${parts[0]}|${parts[1]}" else key
                        searchTextCache[stableKey] = value
                    }
                }.onFailure {
                    logSnapshotWarning("Failed to load library search snapshot", it)
                    searchTextCache.clear()
                }
            }
            searchSnapshotLoaded = true
        }
    }

    private fun saveSearchSnapshot() {
        if (!searchSnapshotDirty) return
        runCatching {
            val root = JSONObject()
            searchTextCache.forEach { (key, value) -> root.put(key, value) }
            librarySearchSnapshotFile.writeText(root.toString())
            searchSnapshotDirty = false
        }.onFailure {
            logSnapshotWarning("Failed to save library search snapshot", it)
        }
    }

    private fun ensureRatingSnapshotLoaded() {
        if (ratingSnapshotLoaded) return
        synchronized(ratingSnapshotCache) {
            if (ratingSnapshotLoaded) return
            if (libraryRatingSnapshotFile.exists()) {
                runCatching {
                    val root = JSONObject(libraryRatingSnapshotFile.readText())
                    root.keys().forEach { key ->
                        val value = root.optJSONObject(key) ?: return@forEach
                        ratingSnapshotCache[key] = RatingSnapshotEntry(
                            rating = value.optInt("rating", 0).coerceIn(0, 5),
                            dateModified = value.optLong("dateModified", 0L),
                            fileSize = value.optLong("fileSize", 0L),
                            path = value.optString("path", ""),
                            id = value.optLong("id", 0L),
                            trustedLocalWrite = value.optBoolean("trustedLocalWrite", false)
                        )
                    }
                }.onFailure {
                    logSnapshotWarning("Failed to load library rating snapshot", it)
                    ratingSnapshotCache.clear()
                }
            }
            ratingSnapshotLoaded = true
        }
    }

    private fun saveRatingSnapshot() {
        if (!ratingSnapshotDirty) return
        runCatching {
            val root = JSONObject()
            ratingSnapshotCache.forEach { (key, value) ->
                root.put(key, JSONObject()
                    .put("rating", value.rating)
                    .put("dateModified", value.dateModified)
                    .put("fileSize", value.fileSize)
                    .put("path", value.path)
                    .put("id", value.id)
                    .put("trustedLocalWrite", value.trustedLocalWrite))
            }
            libraryRatingSnapshotFile.writeText(root.toString())
            ratingSnapshotDirty = false
        }.onFailure {
            logSnapshotWarning("Failed to save library rating snapshot", it)
        }
    }

    private fun logSnapshotWarning(message: String, error: Throwable) {
        runCatching { Log.w("MusicRepo", message, error) }
    }

    private fun RatingSnapshotEntry.isFreshFor(song: Song): Boolean {
        if (dateModified == song.dateModified && fileSize == song.fileSize) return true
        val sameIdentity = when {
            path.isNotBlank() && song.path.isNotBlank() -> path == song.path
            id > 0L && song.id > 0L -> id == song.id
            else -> false
        }
        if (!sameIdentity) return false
        if (fileSize == song.fileSize) return true
        return trustedLocalWrite
    }
}
