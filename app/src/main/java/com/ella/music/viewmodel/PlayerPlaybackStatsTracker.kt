package com.ella.music.viewmodel

import com.ella.music.data.PlaybackStatsStore
import com.ella.music.data.model.Song

internal class PlayerPlaybackStatsTracker(
    private val playbackStatsStore: PlaybackStatsStore,
    private val minPlaybackStatsListenMs: Long = 20_000L,
    private val onPlayCounted: suspend (Song) -> Unit = {}
) {
    private var statsSongId: Long? = null
    private var statsSong: Song? = null
    private var playCountedSongId: Long? = null
    private var pendingListenMs = 0L
    private var lastStatsTickMs = 0L

    suspend fun update(
        nowMs: Long,
        song: Song?,
        isPlaying: Boolean
    ) {
        val songId = song?.id

        if (songId != statsSongId) {
            flush()
            statsSongId = songId
            statsSong = song
            playCountedSongId = null
            lastStatsTickMs = nowMs
            return
        }

        if (song != null && isPlaying) {
            if (lastStatsTickMs > 0L) {
                pendingListenMs += (nowMs - lastStatsTickMs).coerceIn(0L, 1500L)
            }
            if (playCountedSongId != song.id && pendingListenMs >= minPlaybackStatsListenMs) {
                playbackStatsStore.recordPlay(song)
                onPlayCounted(song)
                playCountedSongId = song.id
            }
            if (playCountedSongId == song.id && pendingListenMs >= 5000L) {
                playbackStatsStore.addListenTime(song, pendingListenMs)
                pendingListenMs = 0L
            }
        } else {
            flush()
        }
        lastStatsTickMs = nowMs
    }

    fun takePendingFlush(): PlayerPlaybackStatsPendingFlush? {
        val song = statsSong
        val listenedMs = pendingListenMs
        pendingListenMs = 0L
        return if (song != null && playCountedSongId == song.id && listenedMs > 0L) {
            PlayerPlaybackStatsPendingFlush(song, listenedMs)
        } else {
            null
        }
    }

    private suspend fun flush() {
        val song = statsSong
        if (song != null && playCountedSongId == song.id && pendingListenMs > 0L) {
            playbackStatsStore.addListenTime(song, pendingListenMs)
        }
        pendingListenMs = 0L
    }
}

internal data class PlayerPlaybackStatsPendingFlush(
    val song: Song,
    val listenedMs: Long
)
