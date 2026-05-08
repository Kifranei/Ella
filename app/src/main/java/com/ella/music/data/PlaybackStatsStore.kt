package com.ella.music.data

import android.content.Context
import android.util.Log
import com.ella.music.data.model.Song
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

data class SongPlaybackStats(
    val songId: Long,
    val title: String,
    val artist: String,
    val album: String,
    val playCount: Int,
    val listenedMs: Long,
    val lastPlayedAt: Long
)

class PlaybackStatsStore private constructor(context: Context) {
    private val statsFile = File(context.applicationContext.filesDir, "playback_stats.json")
    private val _stats = MutableStateFlow<List<SongPlaybackStats>>(emptyList())
    val stats: StateFlow<List<SongPlaybackStats>> = _stats.asStateFlow()

    init {
        load()
    }

    suspend fun recordPlay(song: Song) = update(song) { current ->
        current.copy(
            playCount = current.playCount + 1,
            lastPlayedAt = System.currentTimeMillis()
        )
    }

    suspend fun addListenTime(song: Song, listenedMs: Long) {
        if (listenedMs <= 0) return
        update(song) { current ->
            current.copy(
                listenedMs = current.listenedMs + listenedMs,
                lastPlayedAt = System.currentTimeMillis()
            )
        }
    }

    private suspend fun update(
        song: Song,
        transform: (SongPlaybackStats) -> SongPlaybackStats
    ) = withContext(Dispatchers.IO) {
        val current = _stats.value.associateBy { it.songId }.toMutableMap()
        val existing = current[song.id] ?: SongPlaybackStats(
            songId = song.id,
            title = song.title,
            artist = song.artist,
            album = song.album,
            playCount = 0,
            listenedMs = 0L,
            lastPlayedAt = 0L
        )
        current[song.id] = transform(
            existing.copy(
                title = song.title,
                artist = song.artist,
                album = song.album
            )
        )
        val sorted = current.values.sortedByDescending { it.lastPlayedAt }
        _stats.value = sorted
        save(sorted)
    }

    private fun load() {
        if (!statsFile.exists()) return
        runCatching {
            val array = JSONArray(statsFile.readText())
            _stats.value = List(array.length()) { index ->
                val item = array.getJSONObject(index)
                SongPlaybackStats(
                    songId = item.getLong("songId"),
                    title = item.optString("title"),
                    artist = item.optString("artist"),
                    album = item.optString("album"),
                    playCount = item.optInt("playCount"),
                    listenedMs = item.optLong("listenedMs"),
                    lastPlayedAt = item.optLong("lastPlayedAt")
                )
            }
        }.onFailure {
            Log.w("PlaybackStatsStore", "Failed to load playback stats", it)
        }
    }

    private fun save(stats: List<SongPlaybackStats>) {
        runCatching {
            val array = JSONArray()
            stats.forEach { stat ->
                array.put(
                    JSONObject()
                        .put("songId", stat.songId)
                        .put("title", stat.title)
                        .put("artist", stat.artist)
                        .put("album", stat.album)
                        .put("playCount", stat.playCount)
                        .put("listenedMs", stat.listenedMs)
                        .put("lastPlayedAt", stat.lastPlayedAt)
                )
            }
            statsFile.writeText(array.toString())
        }.onFailure {
            Log.w("PlaybackStatsStore", "Failed to save playback stats", it)
        }
    }

    companion object {
        @Volatile
        private var instance: PlaybackStatsStore? = null

        fun getInstance(context: Context): PlaybackStatsStore {
            return instance ?: synchronized(this) {
                instance ?: PlaybackStatsStore(context.applicationContext).also { instance = it }
            }
        }
    }
}
