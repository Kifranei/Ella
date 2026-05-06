package com.ella.music.player

import android.content.Context
import android.util.Log
import com.ella.music.R
import com.ella.music.data.model.LyricLine
import com.ella.music.data.model.Song
import io.github.proify.lyricon.lyric.model.LyricWord
import io.github.proify.lyricon.lyric.model.RichLyricLine
import io.github.proify.lyricon.provider.LyriconFactory
import io.github.proify.lyricon.provider.LyriconProvider
import io.github.proify.lyricon.provider.ProviderLogo
import io.github.proify.lyricon.provider.service.addConnectionListener

class LyriconBridge(private val context: Context) {

    companion object {
        private const val TAG = "LyriconBridge"
    }

    private var provider: LyriconProvider? = null
    private var enabled = false
    private var displayTranslation = true

    private var lastSongId: String? = null
    private var lastSong: Song? = null
    private var lastLyrics: List<LyricLine> = emptyList()

    fun initialize() {
        if (provider != null) return
        try {
            provider = LyriconFactory.createProvider(
                context = context,
                logo = ProviderLogo.fromDrawable(context, R.drawable.ic_launcher)
            )
            provider?.service?.addConnectionListener {
                onConnected {
                    Log.i(TAG, "Lyricon connected")
                    resendLastSong()
                }
                onReconnected {
                    Log.i(TAG, "Lyricon reconnected")
                    resendLastSong()
                }
                onDisconnected { Log.w(TAG, "Lyricon disconnected") }
                onConnectTimeout { Log.w(TAG, "Lyricon connection timeout") }
            }
            provider?.register()
            Log.i(TAG, "Lyricon provider registered")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize Lyricon", e)
            provider = null
        }
    }

    fun destroy() {
        provider?.destroy()
        provider = null
        lastSongId = null
        lastSong = null
        lastLyrics = emptyList()
    }

    fun setEnabled(enabled: Boolean) {
        this.enabled = enabled
        if (enabled) {
            initialize()
        } else {
            provider?.unregister()
            provider?.destroy()
            provider = null
            lastSongId = null
            lastSong = null
            lastLyrics = emptyList()
        }
    }

    fun isEnabled() = enabled

    fun setDisplayTranslation(display: Boolean) {
        displayTranslation = display
        provider?.player?.setDisplayTranslation(display)
    }

    fun sendSong(song: Song, lyrics: List<LyricLine>) {
        if (!enabled) return
        val p = provider ?: return

        lastSongId = song.id.toString()
        lastSong = song
        lastLyrics = lyrics

        try {
            val richLyrics = lyrics.map { line ->
                val words = line.words.map { word ->
                    LyricWord(
                        text = word.text,
                        begin = word.startMs,
                        end = word.endMs
                    )
                }

                val nextLineTime = lyrics
                    .filter { it.timeMs > line.timeMs }
                    .minByOrNull { it.timeMs }
                    ?.timeMs ?: (song.duration)

                RichLyricLine(
                    begin = line.timeMs,
                    end = nextLineTime,
                    text = line.text,
                    words = words.ifEmpty { null },
                    translation = line.translation
                )
            }

            val lyriconSong = io.github.proify.lyricon.lyric.model.Song(
                id = song.id.toString(),
                name = song.title,
                artist = song.artist,
                duration = song.duration,
                lyrics = richLyrics
            )

            p.player.setSong(lyriconSong)
            p.player.setDisplayTranslation(displayTranslation)
            Log.d(TAG, "Sent song to Lyricon: ${song.title} (${richLyrics.size} lines)")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to send song to Lyricon", e)
        }
    }

    fun sendTranslation(song: Song, lyrics: List<LyricLine>, translationMap: Map<Long, String>) {
        if (!enabled) return
        val p = provider ?: return

        try {
            val richLyrics = lyrics.map { line ->
                val words = line.words.map { word ->
                    LyricWord(
                        text = word.text,
                        begin = word.startMs,
                        end = word.endMs
                    )
                }

                val nextLineTime = lyrics
                    .filter { it.timeMs > line.timeMs }
                    .minByOrNull { it.timeMs }
                    ?.timeMs ?: (song.duration)

                RichLyricLine(
                    begin = line.timeMs,
                    end = nextLineTime,
                    text = line.text,
                    words = words.ifEmpty { null },
                    translation = translationMap[line.timeMs]
                )
            }

            val lyriconSong = io.github.proify.lyricon.lyric.model.Song(
                id = song.id.toString(),
                name = song.title,
                artist = song.artist,
                duration = song.duration,
                lyrics = richLyrics
            )

            p.player.setSong(lyriconSong)
            p.player.setDisplayTranslation(displayTranslation)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to send translation to Lyricon", e)
        }
    }

    fun sendPlaybackState(playing: Boolean) {
        if (!enabled) return
        try {
            provider?.player?.setPlaybackState(playing)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to send playback state", e)
        }
    }

    fun sendPosition(positionMs: Long) {
        if (!enabled) return
        try {
            provider?.player?.setPosition(positionMs)
        } catch (e: Exception) {
            // Silently ignore position update errors
        }
    }

    fun seekTo(positionMs: Long) {
        if (!enabled) return
        try {
            provider?.player?.seekTo(positionMs)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to seek", e)
        }
    }

    fun clearSong() {
        if (!enabled) return
        lastSongId = null
        lastSong = null
        lastLyrics = emptyList()
        try {
            provider?.player?.setSong(null)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to clear song", e)
        }
    }

    private fun resendLastSong() {
        val song = lastSong ?: return
        sendSong(song, lastLyrics)
    }
}
