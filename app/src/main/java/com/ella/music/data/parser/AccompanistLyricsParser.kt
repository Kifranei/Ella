package com.ella.music.data.parser

import com.ella.music.data.model.LyricLine
import com.ella.music.data.model.LyricWord
import com.mocharealm.accompanist.lyrics.core.model.ISyncedLine
import com.mocharealm.accompanist.lyrics.core.model.karaoke.KaraokeLine
import com.mocharealm.accompanist.lyrics.core.model.synced.SyncedLine
import com.mocharealm.accompanist.lyrics.core.parser.AutoParser

internal object AccompanistLyricsParser {
    private val parser = AutoParser()

    fun parse(content: String): LrcParser.LrcResult? {
        if (!parser.canParse(content)) return null
        val syncedLyrics = runCatching { parser.parse(content) }.getOrNull() ?: return null
        val isTtmlFormat = content.contains("<tt", ignoreCase = true) &&
            content.contains("</tt", ignoreCase = true)
        val lines = syncedLyrics.lines
            .mapNotNull { toLyricLine(it, isTtmlFormat) }
            .filterNot { line ->
                val text = line.text.ifBlank { line.backgroundText.orEmpty() }
                text.isBlank() || EllaLyricsParser.isIgnorableRawLyricLine(text)
            }
            .sortedBy { it.timeMs }
            .mergeSameTimestampCompanions()
        if (lines.isEmpty()) return null
        return LrcParser.LrcResult(
            lyrics = lines,
            title = syncedLyrics.title.takeIf { it.isNotBlank() },
            artist = syncedLyrics.artists
                ?.joinToString("/") { it.name }
                ?.takeIf { it.isNotBlank() }
        )
    }

    private fun toLyricLine(line: ISyncedLine, isTtmlFormat: Boolean): LyricLine? {
        return when (line) {
            is KaraokeLine.MainKaraokeLine -> line.toMainLyricLine(isTtmlFormat)
            is KaraokeLine.AccompanimentKaraokeLine -> line.toBackgroundLyricLine(isTtmlFormat)
            is SyncedLine -> line.toPlainLyricLine()
            else -> null
        }
    }

    private fun KaraokeLine.MainKaraokeLine.toMainLyricLine(isTtmlFormat: Boolean): LyricLine? {
        val text = syllables.joinToString(separator = "") { it.content }.trimMeaningful()
        if (text.isBlank()) return null
        val background = accompanimentLines?.firstOrNull()
        return LyricLine(
            timeMs = start.toLong().coerceAtLeast(0L),
            text = text,
            words = syllables.toLyricWords(),
            translation = translation?.trimMeaningful(),
            pronunciation = phonetic?.trimMeaningful(),
            agent = alignment.toEllaAgent(),
            backgroundText = background?.syllables?.joinToString(separator = "") { it.content }?.trimMeaningful(),
            backgroundWords = background?.syllables.orEmpty().toLyricWords(),
            backgroundTranslation = background?.translation?.trimMeaningful(),
            backgroundStartMs = background?.start?.toLong()?.coerceAtLeast(0L),
            backgroundEndMs = background?.end?.toSafeEndMs(),
            isTtml = isTtmlFormat,
            endMs = end.toSafeEndMs()
        )
    }

    private fun KaraokeLine.AccompanimentKaraokeLine.toBackgroundLyricLine(isTtmlFormat: Boolean): LyricLine? {
        val text = syllables.joinToString(separator = "") { it.content }.trimMeaningful()
        if (text.isBlank()) return null
        return LyricLine(
            timeMs = start.toLong().coerceAtLeast(0L),
            text = "",
            backgroundText = text,
            backgroundWords = syllables.toLyricWords(),
            backgroundTranslation = translation?.trimMeaningful(),
            backgroundStartMs = start.toLong().coerceAtLeast(0L),
            backgroundEndMs = end.toSafeEndMs(),
            agent = alignment.toEllaAgent(),
            isTtml = isTtmlFormat,
            endMs = end.toSafeEndMs()
        )
    }

    private fun SyncedLine.toPlainLyricLine(): LyricLine? {
        val text = content.trimMeaningful()
        if (text.isBlank()) return null
        return LyricLine(
            timeMs = start.toLong().coerceAtLeast(0L),
            text = text,
            translation = translation?.trimMeaningful(),
            endMs = end.toSafeEndMs()
        )
    }

    private fun List<com.mocharealm.accompanist.lyrics.core.model.karaoke.KaraokeSyllable>.toLyricWords(): List<LyricWord> =
        mapIndexedNotNull { index, syllable ->
            val text = syllable.content
                .let { if (index == lastIndex) it.trimEnd() else it }
                .takeIf { it.isNotBlank() } ?: return@mapIndexedNotNull null
            val startMs = syllable.start.toLong().coerceAtLeast(0L)
            val endMs = syllable.end.toLong().coerceAtLeast(startMs + 1L)
            LyricWord(text = text, startMs = startMs, endMs = endMs)
        }

    private fun Int.toSafeEndMs(): Long? =
        takeIf { it in 0 until Int.MAX_VALUE }?.toLong()

    private fun String.trimMeaningful(): String =
        trim().replace(Regex("""[ \t\r\n]+"""), " ")

    private fun com.mocharealm.accompanist.lyrics.core.model.karaoke.KaraokeAlignment.toEllaAgent(): String? =
        when (name.lowercase()) {
            "start" -> "v1"
            "end" -> "v2"
            else -> null
        }

    private fun List<LyricLine>.mergeSameTimestampCompanions(): List<LyricLine> {
        return groupBy { it.timeMs }
            .values
            .flatMap { group ->
                if (group.size == 1) return@flatMap group
                val primary = group.firstOrNull { it.words.isNotEmpty() && it.text.isUsefulMainText() }
                    ?: group.firstOrNull { it.text.isUsefulMainText() }
                    ?: group.first()
                val primaryText = primary.text.trimMeaningful()
                val primaryTranslationAsPronunciation = primary.translation
                    ?.takeIf { primaryText.hasCjk() && it.isPronunciationFor(primaryText) }
                    ?.trimMeaningful()
                val companions = group.filter { it !== primary }
                val pronunciation = companions
                    .firstOrNull { primaryText.hasCjk() && it.text.isPronunciationFor(primaryText) }
                    ?.text
                    ?.trimMeaningful()
                val translation = companions
                    .asSequence()
                    .filter { it.text.isUsefulMainText() }
                    .map { it.text.trimMeaningful() }
                    .filter { it != primaryText && it != pronunciation }
                    .distinct()
                    .joinToString("\n")
                    .takeIf { it.isNotBlank() }
                listOf(
                    primary.copy(
                        translation = primary.translation
                            ?.takeUnless { it == primaryTranslationAsPronunciation }
                            .mergeText(translation),
                        pronunciation = primary.pronunciation ?: pronunciation ?: primaryTranslationAsPronunciation,
                        endMs = group.mapNotNull { it.endMs }.maxOrNull() ?: primary.endMs
                    )
                )
            }
            .sortedBy { it.timeMs }
    }

    private fun String?.mergeText(extra: String?): String? =
        listOfNotNull(this?.takeIf { it.isNotBlank() }, extra?.takeIf { it.isNotBlank() })
            .distinct()
            .joinToString("\n")
            .takeIf { it.isNotBlank() }

    private fun String.isUsefulMainText(): Boolean =
        trimMeaningful().isNotBlank() && !EllaLyricsParser.isPlaceholderOnlyLine(this)

    private fun String.hasCjk(): Boolean =
        any { char ->
            val block = Character.UnicodeBlock.of(char)
            block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS ||
                block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_A ||
                block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_B ||
                block == Character.UnicodeBlock.HIRAGANA ||
                block == Character.UnicodeBlock.KATAKANA ||
                block == Character.UnicodeBlock.HANGUL_SYLLABLES
        }

    private fun String.isPronunciationFor(primaryText: String): Boolean {
        val text = trimMeaningful()
        if (text.isBlank() || text.hasCjk()) return false
        return primaryText.hasCjk() && text.any { it.isLetter() }
    }
}
