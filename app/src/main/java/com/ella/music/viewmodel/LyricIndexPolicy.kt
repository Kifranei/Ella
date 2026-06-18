package com.ella.music.viewmodel

import com.ella.music.data.model.LyricLine

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
        if (positionMs >= lyrics[i].timeMs && lyrics[i].hasVisibleLyricText()) {
            index = i
            break
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
