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
    private var lastSentSignature: String? = null

    fun initialize() {
        if (provider != null) return
        try {
            provider = LyriconFactory.createProvider(
                context = context,
                logo = ProviderLogo
                    .fromDrawable(context, R.drawable.ic_flyme_ticker, width = 96, height = 96)
                    .copy(colorful = false)
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
        lastSentSignature = null
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
            lastSentSignature = null
        }
    }

    fun isEnabled() = enabled

    fun setDisplayTranslation(display: Boolean) {
        displayTranslation = display
        provider?.player?.setDisplayTranslation(display)
        resendLastSong()
    }

    fun sendSong(song: Song, lyrics: List<LyricLine>, force: Boolean = false) {
        if (!enabled) return
        val p = provider ?: return

        lastSongId = song.id.toString()
        lastSong = song
        lastLyrics = lyrics
        val signature = song.lyriconSignature(lyrics)
        if (!force && signature == lastSentSignature) {
            p.player.setDisplayTranslation(displayTranslation)
            Log.d(TAG, "Skipped duplicate Lyricon song: ${song.title} (${lyrics.size} lines)")
            return
        }

        try {
            val richLyrics = lyrics.map { line ->
                val words = line.words.withLineSpacing(line.text).map { word ->
                    LyricWord(
                        text = word.text,
                        begin = word.startMs,
                        end = word.endMs
                    )
                }
                val backgroundWords = line.backgroundWords.withLineSpacing(line.backgroundText.orEmpty()).map { word ->
                    LyricWord(
                        text = word.text,
                        begin = word.startMs,
                        end = word.endMs
                    )
                }

                val nextLineTime = line.endMs
                    ?: lyrics
                        .filter { it.timeMs > line.timeMs }
                        .minByOrNull { it.timeMs }
                        ?.timeMs
                    ?: song.duration

                RichLyricLine(
                    begin = line.timeMs,
                    end = nextLineTime,
                    isAlignedRight = line.agent.equals("v2", ignoreCase = true),
                    text = line.text,
                    words = words.ifEmpty { null },
                    secondary = line.backgroundText,
                    secondaryWords = backgroundWords.ifEmpty { null },
                    translation = line.translationForLyricon()
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
            lastSentSignature = signature
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
                val words = line.words.withLineSpacing(line.text).map { word ->
                    LyricWord(
                        text = word.text,
                        begin = word.startMs,
                        end = word.endMs
                    )
                }
                val backgroundWords = line.backgroundWords.withLineSpacing(line.backgroundText.orEmpty()).map { word ->
                    LyricWord(
                        text = word.text,
                        begin = word.startMs,
                        end = word.endMs
                    )
                }

                val nextLineTime = line.endMs
                    ?: lyrics
                        .filter { it.timeMs > line.timeMs }
                        .minByOrNull { it.timeMs }
                        ?.timeMs
                    ?: song.duration

                RichLyricLine(
                    begin = line.timeMs,
                    end = nextLineTime,
                    isAlignedRight = line.agent.equals("v2", ignoreCase = true),
                    text = line.text,
                    words = words.ifEmpty { null },
                    secondary = line.backgroundText,
                    secondaryWords = backgroundWords.ifEmpty { null },
                    translation = if (displayTranslation) {
                        translationMap[line.timeMs] ?: line.backgroundTranslation
                    } else {
                        null
                    }
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
        lastSentSignature = null
        try {
            provider?.player?.setSong(null)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to clear song", e)
        }
    }

    private fun resendLastSong() {
        val song = lastSong ?: return
        sendSong(song, lastLyrics, force = true)
    }

    private fun Song.lyriconSignature(lyrics: List<LyricLine>): String {
        var lyricHash = 17
        lyrics.forEach { line ->
            lyricHash = 31 * lyricHash + line.timeMs.hashCode()
            lyricHash = 31 * lyricHash + line.endMs.hashCode()
            lyricHash = 31 * lyricHash + line.text.hashCode()
            lyricHash = 31 * lyricHash + line.translation.hashCode()
            lyricHash = 31 * lyricHash + line.backgroundText.hashCode()
            lyricHash = 31 * lyricHash + line.backgroundTranslation.hashCode()
            line.words.forEach { word ->
                lyricHash = 31 * lyricHash + word.text.hashCode()
                lyricHash = 31 * lyricHash + word.startMs.hashCode()
                lyricHash = 31 * lyricHash + word.endMs.hashCode()
            }
            line.backgroundWords.forEach { word ->
                lyricHash = 31 * lyricHash + word.text.hashCode()
                lyricHash = 31 * lyricHash + word.startMs.hashCode()
                lyricHash = 31 * lyricHash + word.endMs.hashCode()
            }
        }
        return listOf(
            id,
            title,
            artist,
            duration,
            path,
            lyrics.size,
            lyricHash,
            displayTranslation
        ).joinToString("|")
    }

    private fun LyricLine.translationForLyricon(): String? {
        if (!displayTranslation) return null
        return translation ?: backgroundTranslation
    }

    private fun List<com.ella.music.data.model.LyricWord>.withLineSpacing(
        lineText: String
    ): List<com.ella.music.data.model.LyricWord> {
        if (isEmpty() || lineText.isBlank() || !lineText.any { it.isWhitespace() }) return this

        val result = mutableListOf<com.ella.music.data.model.LyricWord>()
        var cursor = 0

        forEachIndexed { index, word ->
            val start = lineText.indexOf(word.text, startIndex = cursor)
            if (start < 0) {
                result += word
                return@forEachIndexed
            }

            val end = start + word.text.length
            val nextText = getOrNull(index + 1)?.text
            val nextStart = if (nextText != null) lineText.indexOf(nextText, startIndex = end) else -1
            val suffix = when {
                nextStart > end -> lineText.substring(end, nextStart)
                nextText == null && end < lineText.length -> lineText.substring(end)
                else -> ""
            }
            result += word.copy(text = word.text + suffix)
            cursor = end + suffix.length
        }

        return result.takeIf { it.size == size } ?: this
    }
}
