package com.ella.music.ui.components

import android.graphics.Color
import android.graphics.Typeface
import com.ella.music.data.model.LyricLine
import com.ella.music.data.model.LyricWord
import com.ella.music.data.model.primaryEndMs
import io.github.proify.lyricon.lyric.model.LyricWord as LyriconWord
import io.github.proify.lyricon.lyric.model.RichLyricLine as LyriconRichLyricLine
import io.github.proify.lyricon.lyric.model.Song as LyriconSong
import io.github.proify.lyricon.lyric.view.PlaceholderFormat
import io.github.proify.lyricon.lyric.view.RichLyricLineConfig
import io.github.proify.lyricon.lyric.view.SyllableConfig
import io.github.proify.lyricon.lyric.view.TextConfig
import java.io.File

internal fun loadAndroidTypeface(
    fontPath: String,
    weight: Int,
    italic: Boolean,
    boldFallback: Boolean
): Typeface {
    ScriptFontPaths.decode(fontPath)?.let { paths ->
        return loadScriptAwareTypeface(paths, weight, italic, boldFallback)
    }
    val safeWeight = weight.coerceIn(100, 900)
    val base = if (fontPath.isNotBlank()) {
        runCatching {
            val file = File(fontPath)
            if (file.exists() && file.isFile) Typeface.createFromFile(file) else null
        }.getOrNull()
    } else {
        null
    } ?: if (boldFallback) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
    return Typeface.create(base, safeWeight, italic)
}

internal fun buildLyriconRichLineConfig(
    primaryTextSizePx: Float,
    secondaryTextSizePx: Float,
    primaryTypeface: Typeface,
    secondaryTypeface: Typeface,
    primaryTextColor: Int = Color.WHITE,
    secondaryTextColor: Int = Color.argb(190, 255, 255, 255),
    syllableHighlightColor: Int = primaryTextColor,
    syllableBackgroundColor: Int = Color.argb(88, 255, 255, 255),
    enableAnim: Boolean = false
): RichLyricLineConfig =
    RichLyricLineConfig(
        primary = TextConfig(
            textSize = primaryTextSizePx,
            textColor = intArrayOf(primaryTextColor),
            typeface = primaryTypeface
        ),
        secondary = TextConfig(
            textSize = secondaryTextSizePx,
            textColor = intArrayOf(secondaryTextColor),
            typeface = secondaryTypeface
        ),
        syllable = SyllableConfig(
            highlightColor = intArrayOf(syllableHighlightColor),
            backgroundColor = intArrayOf(syllableBackgroundColor)
        ),
        placeholderFormat = PlaceholderFormat.NAME_ARTIST,
        enableAnim = enableAnim
    )

internal fun List<LyricLine>.toLyriconSong(
    songId: Long,
    songTitle: String,
    songArtist: String
): LyriconSong {
    val lines = mapIndexedNotNull { index, line ->
        val nextLine = getOrNull(index + 1)
        val end = line.primaryEndMs(nextLine = nextLine)
        if (line.text.isBlank() && line.backgroundText.isNullOrBlank()) return@mapIndexedNotNull null
        LyriconRichLyricLine(
            begin = line.timeMs,
            end = end,
            isAlignedRight = line.agent.equals("v2", ignoreCase = true),
            text = line.text.ifBlank { "♪" },
            words = line.displaySmoothPrimaryWords(),
            secondary = line.displaySmoothSecondaryBlockText(),
            secondaryWords = line.displaySmoothSecondaryWords(),
            translation = line.displayTranslationText(),
            roma = line.displayPronunciationText()
        )
    }
    return LyriconSong(
        id = songId.takeIf { it > 0L }?.toString(),
        name = songTitle,
        artist = songArtist,
        lyrics = lines
    ).normalize()
}

internal fun List<LyricWord>.toLyriconWords(): List<LyriconWord> =
    mapNotNull { word ->
        if (word.text.isBlank() || word.endMs <= word.startMs) return@mapNotNull null
        LyriconWord(
            begin = word.startMs,
            end = word.endMs,
            text = word.text
        )
    }

private fun LyricLine.displayPronunciationText(): String? {
    pronunciation?.takeIf { it.isNotBlank() }?.let { return it }
    return romanizationSecondaryCandidate()
}

private fun LyricLine.displayTranslationText(): String? {
    val value = translation?.takeIf { it.isNotBlank() } ?: return null
    if (pronunciation.isNullOrBlank() && isLikelyRomanizationSecondary(text, value)) return null
    return value
}

private fun LyricLine.displaySmoothSecondaryBlockText(): String? {
    val background = displayBackgroundText() ?: return null
    val translation = displayBackgroundTranslationText()
    return if (translation.isNullOrBlank()) {
        background
    } else {
        "$background$SMOOTH_SECONDARY_TRANSLATION_SEPARATOR$translation"
    }
}

private fun LyricLine.displaySmoothPrimaryWords(): List<LyriconWord>? =
    words
        .withLineSpacing(text)
        .toLyriconWords()
        .ifEmpty { null }

private fun LyricLine.displaySmoothSecondaryWords(): List<LyriconWord>? {
    return backgroundWords
        .withLineSpacing(backgroundText.orEmpty()) { text ->
            text.normalizeBackgroundWordText()
        }
        .toLyriconWords()
        .ifEmpty { null }
}

private fun LyricLine.displayBackgroundText(): String? =
    backgroundText
        ?.normalizeBackgroundAsideText()
        ?.takeIf { it.isNotBlank() }

private fun LyricLine.displayBackgroundTranslationText(): String? {
    val value = backgroundTranslation?.takeIf { it.isNotBlank() } ?: return null
    val primary = displayBackgroundText() ?: text
    if (pronunciation.isNullOrBlank() && isLikelyRomanizationSecondary(primary, value)) return null
    return value
}

private fun LyricLine.romanizationSecondaryCandidate(): String? {
    translation?.takeIf { isLikelyRomanizationSecondary(text, it) }?.let { return it }
    val primary = displayBackgroundText() ?: text
    return backgroundTranslation?.takeIf { isLikelyRomanizationSecondary(primary, it) }
}

private fun isLikelyRomanizationSecondary(primary: String?, candidate: String?): Boolean {
    val primaryText = primary?.takeIf { it.isNotBlank() } ?: return false
    val secondary = candidate?.trim()?.takeIf { it.isNotBlank() } ?: return false
    if (!primaryText.hasCjkKanaOrHangul()) return false
    if (!secondary.any { it.isLatinLetter() }) return false
    if (secondary.hasCjkKanaOrHangul()) return false
    val useful = secondary.filterNot { it.isWhitespace() }
    if (useful.isEmpty()) return false
    val romanChars = useful.count { it.isLatinLetter() || it in "-'.`·・" }
    return romanChars.toFloat() / useful.length >= 0.82f
}

private fun String.hasCjkKanaOrHangul(): Boolean = any { char ->
    when (Character.UnicodeBlock.of(char)) {
        Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS,
        Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_A,
        Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_B,
        Character.UnicodeBlock.CJK_COMPATIBILITY_IDEOGRAPHS,
        Character.UnicodeBlock.HIRAGANA,
        Character.UnicodeBlock.KATAKANA,
        Character.UnicodeBlock.HANGUL_SYLLABLES,
        Character.UnicodeBlock.HANGUL_JAMO,
        Character.UnicodeBlock.HANGUL_COMPATIBILITY_JAMO -> true
        else -> false
    }
}

private fun Char.isLatinLetter(): Boolean = this in 'A'..'Z' || this in 'a'..'z'

private fun String.normalizeBackgroundAsideText(): String =
    trim()
        .replace(Regex("""^[（(]+\s*"""), "")
        .replace(Regex("""\s*[）)]+$"""), "")
        .replace(Regex("""[ \t\r\n]+"""), " ")
        .trim()

private fun String.normalizeBackgroundWordText(): String {
    val trailingWhitespace = takeLastWhile { it.isWhitespace() }
        .replace(Regex("""[ \t\r\n]+"""), " ")
    val core = trim()
        .replace(Regex("""^[（(]+\s*"""), "")
        .replace(Regex("""\s*[）)]+$"""), "")
        .replace(Regex("""[ \t\r\n]+"""), " ")
        .trim()
    return if (core.isBlank()) "" else core + trailingWhitespace
}

private fun List<LyricWord>.withLineSpacing(
    lineText: String,
    normalizeText: (String) -> String = { it }
): List<LyricWord> {
    if (isEmpty()) return this
    if (lineText.isBlank() || !lineText.any { it.isWhitespace() }) {
        return map { word -> word.copy(text = normalizeText(word.text)) }
    }

    val result = mutableListOf<LyricWord>()
    var cursor = 0

    forEachIndexed { index, word ->
        val start = lineText.indexOf(word.text, startIndex = cursor)
        if (start < 0) {
            result += word.copy(text = normalizeText(word.text))
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
        result += word.copy(text = normalizeText(word.text + suffix))
        cursor = end + suffix.length
    }

    return result
}

private const val SMOOTH_SECONDARY_TRANSLATION_SEPARATOR = "\u000B"
