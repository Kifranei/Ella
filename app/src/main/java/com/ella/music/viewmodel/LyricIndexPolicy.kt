package com.ella.music.viewmodel

import com.ella.music.data.model.LyricLine
import com.ella.music.data.model.primaryEndMs

internal const val LEADING_ZERO_LYRIC_SUPPRESSION_MS = 750L

internal data class LyricIndexResult(
    val index: Int,
    val suppressedLeadingZero: Boolean
)

internal fun currentLyricIndexAt(
    positionMs: Long,
    lyrics: List<LyricLine>,
    suppressLeadingZero: Boolean
): LyricIndexResult {
    if (lyrics.isEmpty()) return LyricIndexResult(index = -1, suppressedLeadingZero = false)

    var index = -1
    for (i in lyrics.indices.reversed()) {
        val line = lyrics[i]
        if (!line.hasVisibleLyricText()) continue
        val nextLine = lyrics.getOrNull(i + 1)
        val endMs = line.primaryEndMs(nextLine = nextLine)
        if (positionMs >= line.timeMs && positionMs < endMs) {
            index = i
            break
        }
    }

    // No line's [start, end) window contains the position — an instrumental gap, an empty /
    // placeholder-only line (which we skip), or past the end of a short or single-line lyric.
    // Stay on the most recently started visible line instead of snapping back to the top (-1),
    // which otherwise makes the mini lyric flash to the first line and then jump back.
    if (index == -1) {
        for (i in lyrics.indices.reversed()) {
            val line = lyrics[i]
            if (!line.hasVisibleLyricText()) continue
            if (positionMs >= line.timeMs) {
                index = i
                break
            }
        }
    }

    val shouldSuppressLeadingZero = suppressLeadingZero &&
        lyrics.getOrNull(index)?.timeMs == 0L &&
        positionMs in 0L until LEADING_ZERO_LYRIC_SUPPRESSION_MS

    return LyricIndexResult(
        index = if (shouldSuppressLeadingZero) -1 else index,
        suppressedLeadingZero = shouldSuppressLeadingZero
    )
}

private fun LyricLine.hasVisibleLyricText(): Boolean {
    val textFields = listOf(text, translation, pronunciation, backgroundText, backgroundTranslation)
    if (textFields.any { value -> value.hasDisplayableLyricText() }) return true
    return words.any { it.text.hasDisplayableLyricText() } ||
        pronunciationWords.any { it.text.hasDisplayableLyricText() } ||
        backgroundWords.any { it.text.hasDisplayableLyricText() }
}

private fun String?.hasDisplayableLyricText(): Boolean {
    if (isNullOrBlank()) return false
    return !trim().isTimestampOnlyLyricText()
}

private fun String.isTimestampOnlyLyricText(): Boolean =
    replace(',', '.').let { text ->
        timestampOnlyLyricTextRegex.matches(text)
    }

private val timestampOnlyLyricTextRegex = Regex(
    """^\s*(?:\[\d{1,2}:\d{2}(?:[.:]\d{1,3})?]|\[\d{1,2}:\d{2}]|<\d{1,2}:\d{2}(?:[.:]\d{1,3})?>)(?:\s*(?:\[\d{1,2}:\d{2}(?:[.:]\d{1,3})?]|\[\d{1,2}:\d{2}]|<\d{1,2}:\d{2}(?:[.:]\d{1,3})?>))*\s*$"""
)
