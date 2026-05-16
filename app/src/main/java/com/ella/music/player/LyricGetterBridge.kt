package com.ella.music.player

import android.content.Context
import android.util.Log
import cn.lyric.getter.api.API
import cn.lyric.getter.api.data.ExtraData
import com.ella.music.data.model.LyricLine

class LyricGetterBridge(context: Context) {
    private val api = API()
    private val packageName = context.packageName
    private var enabled = false
    private var lastPayload: String? = null

    fun setEnabled(enabled: Boolean) {
        this.enabled = enabled
        lastPayload = null
        if (!enabled) clearLyric()
    }

    fun isEnabled(): Boolean = enabled

    fun sendLyric(line: LyricLine?, force: Boolean = false) {
        if (!enabled) return
        val text = line.originalTextForLyricGetter() ?: return
        val payload = "${line?.timeMs}:$text"
        if (!force && payload == lastPayload) return
        lastPayload = payload

        runCatching {
            api.sendLyric(
                text,
                ExtraData().apply {
                    this.packageName = this@LyricGetterBridge.packageName
                    base64Icon = ""
                    useOwnMusicController = false
                    delay = line?.displayDurationMs()?.coerceIn(0, Int.MAX_VALUE.toLong())?.toInt() ?: 0
                }
            )
        }.onFailure {
            Log.w(TAG, "Failed to send Lyric Getter lyric", it)
            lastPayload = null
        }
    }

    fun clearLyric() {
        lastPayload = null
        runCatching { api.clearLyric() }
            .onFailure { Log.w(TAG, "Failed to clear Lyric Getter lyric", it) }
    }

    private fun LyricLine?.originalTextForLyricGetter(): String? {
        val text = this?.text?.trim().orEmpty().ifBlank { this?.backgroundText?.trim().orEmpty() }
        return text.takeIf { it.isNotBlank() && !it.isMusicSymbolOnly() }
    }

    private fun LyricLine.displayDurationMs(): Long? {
        val end = endMs ?: words.maxOfOrNull { it.endMs }
        return end?.minus(timeMs)?.takeIf { it > 0 }
    }

    private fun String.isMusicSymbolOnly(): Boolean =
        all { char ->
            char.isWhitespace() ||
                char in setOf('♪', '♫', '♬', '♩', '♭', '♯', '♮') ||
                Character.UnicodeBlock.of(char) == Character.UnicodeBlock.MUSICAL_SYMBOLS
        }

    private companion object {
        const val TAG = "LyricGetterBridge"
    }
}
