package com.ella.music.data.parser

import android.text.Html
import com.ella.music.data.model.LyricLine
import com.ella.music.data.model.LyricWord
import java.io.StringReader
import javax.xml.parsers.DocumentBuilderFactory
import org.w3c.dom.Element
import org.w3c.dom.Node
import org.xml.sax.InputSource
import kotlin.math.abs

internal object EllaLyricsParser {
    private val lrcTimePattern = Regex("""\[(\d{1,3}):(\d{2})(?:[.:](\d{1,3}))?]""")
    private val lrcMetaPattern = Regex("""\[(ti|ar|al|by|offset|re|ve):\s*(.*)]""", RegexOption.IGNORE_CASE)
    private val timedWordMarkerPattern = Regex("""<([^>]+)>|\[([^\]]+)]""")
    private val backgroundLinePattern = Regex("""^\[bg:\s*(.*)]$""", RegexOption.IGNORE_CASE)
    private val lyricifySyllablePattern = Regex("""(.*?)\((\d+),(\d+)\)""")
    private val lyricifyAttributePattern = Regex("""^\[(\d+)]""")
    private val timestampOnlyPattern = Regex("""\d+(?::\d+){0,2}(?:[.:]\d+)?""")

    fun parse(content: String): LrcParser.LrcResult {
        parseTtml(content)?.let { return it }
        if (lyricifySyllablePattern.containsMatchIn(content)) {
            parseLyricify(content)?.let { return it }
        }
        return parseLrc(content)
    }

    private fun parseLrc(content: String): LrcParser.LrcResult {
        val lines = mutableListOf<LyricLine>()
        var title: String? = null
        var artist: String? = null
        var album: String? = null
        var offset = 0L

        content.lines().forEach { raw ->
            val line = raw.trim()
            if (line.isBlank()) return@forEach

            lrcMetaPattern.matchEntire(line)?.let { match ->
                when (match.groupValues[1].lowercase()) {
                    "ti" -> title = match.groupValues[2].trim()
                    "ar" -> artist = match.groupValues[2].trim()
                    "al" -> album = match.groupValues[2].trim()
                    "offset" -> offset = match.groupValues[2].trim().toLongOrNull() ?: 0L
                }
                return@forEach
            }

            lines += parseLrcLine(line)
        }

        return LrcParser.LrcResult(
            lyrics = mergeCompanionLines(lines),
            title = title,
            artist = artist,
            album = album,
            offset = offset
        )
    }

    private fun parseLrcLine(line: String): List<LyricLine> {
        backgroundLinePattern.matchEntire(line)?.let { match ->
            val content = match.groupValues[1].trim()
            val words = parseEnhancedWords(content, 0L)
            val text = if (words.isNotEmpty()) words.joinLyricText() else content.cleanLyricText()
            if (text.isBlank() || text.isMusicSymbolOnly()) return emptyList()
            return listOf(
                LyricLine(
                    timeMs = words.firstOrNull()?.startMs ?: 0L,
                    text = "",
                    backgroundText = text,
                    backgroundWords = words,
                    endMs = words.lastOrNull()?.endMs
                )
            )
        }

        val leadingTimes = lrcTimePattern.findAll(line).takeWhile { it.range.first == 0 || line.substring(0, it.range.first).isBlank() }.toList()
        if (leadingTimes.isEmpty()) return emptyList()

        val contentStart = leadingTimes.last().range.last + 1
        val taggedContent = line.substring(contentStart).trim()
        val embeddedBackground = backgroundLinePattern.matchEntire(taggedContent)
        val rawContent = embeddedBackground?.groupValues?.get(1)?.trim() ?: taggedContent
        if (rawContent.isBlank()) return emptyList()

        val voice = Regex("""^(v\d+)\s*:\s*(.*)$""").find(rawContent)
        val agent = voice?.groupValues?.get(1)
        val content = voice?.groupValues?.get(2)?.trim() ?: rawContent

        return leadingTimes.mapNotNull { timeMatch ->
            val start = parseLrcTime(timeMatch.groupValues)
            val words = parseEnhancedWords(content, start)
            val text = if (words.isNotEmpty()) words.joinLyricText() else content.cleanLyricText()
            if (text.isBlank() || text.isMusicSymbolOnly()) return@mapNotNull null
            if (embeddedBackground != null) {
                return@mapNotNull LyricLine(
                    timeMs = words.firstOrNull()?.startMs ?: start,
                    text = "",
                    backgroundText = text,
                    backgroundWords = words.toDisplayWords(text),
                    agent = agent,
                    endMs = words.lastOrNull()?.endMs
                )
            }
            LyricLine(
                timeMs = start,
                text = text,
                words = words.toDisplayWords(text),
                agent = agent,
                endMs = words.lastOrNull()?.endMs
            )
        }
    }

    private fun parseEnhancedWords(content: String, lineStartMs: Long): List<LyricWord> {
        val markers = timedWordMarkerPattern.findAll(content)
            .mapNotNull { match ->
                val time = match.groupValues.getOrNull(1).orEmpty()
                    .ifBlank { match.groupValues.getOrNull(2).orEmpty() }
                    .trim()
                if (!time.isTimestampLike()) return@mapNotNull null
                TimedMarker(match.range.first, match.range.last + 1, time.parseFlexibleTime().toLong())
            }
            .toList()
        if (markers.isEmpty()) return emptyList()

        val words = mutableListOf<LyricWord>()
        var activeStart = lineStartMs
        var textStart = 0

        markers.forEach { marker ->
            val text = content.substring(textStart, marker.startIndex).cleanTimedLyricSegment()
            if (text.isNotBlank()) {
                words += LyricWord(
                    text = text,
                    startMs = activeStart,
                    endMs = marker.timeMs.coerceAtLeast(activeStart + 120L)
                )
            }
            activeStart = marker.timeMs
            textStart = marker.endIndex
        }

        content.substring(textStart).cleanTimedLyricSegment().takeIf { it.isNotBlank() }?.let { tail ->
            words += LyricWord(
                text = tail,
                startMs = activeStart,
                endMs = activeStart + estimateDuration(tail)
            )
        }

        if (words.isEmpty()) return emptyList()

        val firstStart = words.first().startMs
        val relative = firstStart < lineStartMs && abs(firstStart - lineStartMs) > 2_000L
        return if (relative) {
            words.map { it.copy(startMs = it.startMs + lineStartMs, endMs = it.endMs + lineStartMs) }
        } else {
            words
        }
    }

    private data class TimedMarker(
        val startIndex: Int,
        val endIndex: Int,
        val timeMs: Long
    )

    private fun parseLyricify(content: String): LrcParser.LrcResult? {
        val parsed = content.lines()
            .mapNotNull { raw ->
                val line = raw.trim()
                if (line.isBlank() || lrcMetaPattern.matches(line)) return@mapNotNull null

                val attr = lyricifyAttributePattern.find(line)?.groupValues?.getOrNull(1)?.toIntOrNull()
                val real = lyricifyAttributePattern.replace(line, "")
                val words = lyricifySyllablePattern.findAll(real)
                    .mapNotNull { match ->
                        val text = match.groupValues[1]
                        val start = match.groupValues[2].toLongOrNull() ?: return@mapNotNull null
                        val duration = match.groupValues[3].toLongOrNull() ?: return@mapNotNull null
                        LyricWord(text, start, start + duration)
                    }
                    .toList()
                if (words.isEmpty()) return@mapNotNull null

                val isBackground = attr != null && attr !in 0..5
                val agent = if (attr == 2 || attr == 5 || attr == 8) "v2" else "v1"
                val text = words.joinLyricText()

                if (isBackground) {
                    LyricLine(
                        timeMs = words.first().startMs,
                        text = "",
                        backgroundText = text,
                        backgroundWords = words.toDisplayWords(text),
                        agent = agent,
                        isTtml = true,
                        endMs = words.last().endMs
                    )
                } else {
                    LyricLine(
                        timeMs = words.first().startMs,
                        text = text,
                        words = words.toDisplayWords(text),
                        agent = agent,
                        isTtml = true,
                        endMs = words.last().endMs
                    )
                }
            }
        return LrcParser.LrcResult(lyrics = attachBackgroundLines(parsed)).takeIf { it.lyrics.isNotEmpty() }
    }

    private fun parseTtml(content: String): LrcParser.LrcResult? {
        if (!content.contains("<tt", ignoreCase = true)) return null
        return runCatching {
            val document = DocumentBuilderFactory.newInstance().apply {
                isNamespaceAware = false
                isIgnoringComments = true
                isCoalescing = true
            }.newDocumentBuilder().parse(InputSource(StringReader(content.preformatTtml())))

            val agentAlignment = parseAgentAlignment(document.documentElement)
            val translations = parseTimedTextMap(document.documentElement, "translation")
            val transliterations = parseTransliterations(document.documentElement)
            val paragraphs = document.getElementsByTagName("p")

            val lines = (0 until paragraphs.length).mapNotNull { index ->
                val p = paragraphs.item(index) as? Element ?: return@mapNotNull null
                val start = p.attr("begin").parseTtmlTime() ?: return@mapNotNull null
                val end = p.attr("end").parseTtmlTime()
                val key = p.attr("itunes:key").ifBlank { p.attr("key") }
                val agent = p.attr("ttm:agent").ifBlank { p.attr("agent") }
                val words = mutableListOf<LyricWord>()
                val text = collectTtmlMainText(p, words, end).cleanLyricText()
                val inlineTranslation = p.childrenElements()
                    .firstOrNull { it.hasRole("x-translation") && !it.hasRole("x-bg") }
                    ?.textContent
                    ?.cleanLyricText()
                val bg = p.childrenElements()
                    .firstOrNull { it.hasRole("x-bg") }
                    ?.parseTtmlBackground(end, translations[key])
                val linePronunciation = p.childrenElements()
                    .firstOrNull { it.hasRole("x-roman") }
                    ?.textContent
                    ?.cleanLyricText()
                val transliteration = transliterations[key]
                val pronunciationWords = transliteration?.words
                    ?.alignPronunciationWords(words, text)
                    .orEmpty()
                val pronunciation = linePronunciation
                    ?: transliteration?.text?.takeUsefulText()
                    ?: pronunciationWords.joinLyricText().takeIf { it.isNotBlank() }

                if (text.isBlank() && bg == null) return@mapNotNull null

                LyricLine(
                    timeMs = start,
                    text = text,
                    words = words.toDisplayWords(text),
                    translation = inlineTranslation?.takeUsefulText() ?: translations[key]?.splitAppleTranslation()?.first,
                    pronunciation = pronunciation?.takeUsefulText(),
                    pronunciationWords = pronunciationWords.toDisplayWords(pronunciation.orEmpty()),
                    agent = agentAlignment[agent] ?: agent,
                    backgroundText = bg?.text,
                    backgroundWords = bg?.words.orEmpty().toDisplayWords(bg?.text.orEmpty()),
                    backgroundTranslation = bg?.translation,
                    isTtml = true,
                    endMs = end
                )
            }

            LrcParser.LrcResult(lyrics = lines.sortedBy { it.timeMs })
        }.getOrNull()?.takeIf { it.lyrics.isNotEmpty() }
    }

    private fun parseAgentAlignment(root: Element): Map<String, String> {
        val agents = root.allElements()
            .filter { it.localTagName() == "agent" }
            .mapNotNull { it.attr("xml:id").ifBlank { it.attr("id") }.takeIf(String::isNotBlank) }
        return agents.mapIndexed { index, id -> id to if (index == 1) "v2" else "v1" }.toMap()
    }

    private fun parseTimedTextMap(root: Element, tag: String): Map<String, String> {
        val result = mutableMapOf<String, String>()
        root.allElements()
            .filter { it.localTagName() == tag }
            .flatMap { it.childrenElements() }
            .filter { it.localTagName() == "text" }
            .forEach { text ->
                val key = text.attr("for").ifBlank { return@forEach }
                val value = text.textContent.cleanLyricText()
                if (value.isNotBlank()) result[key] = value
            }
        return result
    }

    private fun parseTransliterations(root: Element): Map<String, TtmlPronunciation> {
        val result = mutableMapOf<String, TtmlPronunciation>()
        root.allElements()
            .filter { it.localTagName() == "transliteration" }
            .flatMap { it.childrenElements() }
            .filter { it.localTagName() == "text" }
            .forEach { text ->
                val key = text.attr("for").ifBlank { return@forEach }
                val words = text.childrenElements()
                    .filter { it.localTagName() == "span" }
                    .mapNotNull { span ->
                        val value = span.textContent
                            .removeBackgroundParentheses()
                            .cleanLyricText()
                            .takeIf { it.isNotBlank() } ?: return@mapNotNull null
                        val start = span.attr("begin").parseTtmlTime()
                        val end = span.attr("end").parseTtmlTime()
                        LyricWord(
                            text = value,
                            startMs = start ?: 0L,
                            endMs = end ?: start?.plus(estimateDuration(value)) ?: 0L
                        )
                    }
                val plainText = text.textContent
                    .removeBackgroundParentheses()
                    .cleanLyricText()
                    .takeUsefulText()
                if (!plainText.isNullOrBlank() || words.isNotEmpty()) {
                    result[key] = TtmlPronunciation(
                        text = plainText.orEmpty(),
                        words = words
                    )
                }
            }
        return result
    }

    private fun collectTtmlMainText(element: Element, words: MutableList<LyricWord>, fallbackEnd: Long?): String {
        val builder = StringBuilder()
        element.childNodes.toNodeList().forEach { node ->
            when (node.nodeType) {
                Node.TEXT_NODE -> builder.append(node.nodeValue.orEmpty().withoutFormattingWhitespace())
                Node.ELEMENT_NODE -> {
                    val child = node as? Element ?: return@forEach
                    if (child.hasRole("x-translation") || child.hasRole("x-bg") || child.hasRole("x-roman")) {
                        return@forEach
                    }
                    val nested = collectTtmlMainText(child, words, fallbackEnd)
                    val begin = child.attr("begin").parseTtmlTime()
                    if (begin != null && nested.isNotBlank()) {
                        words += LyricWord(
                            text = nested,
                            startMs = begin,
                            endMs = child.attr("end").parseTtmlTime()
                                ?: fallbackEnd
                                ?: begin + estimateDuration(nested)
                        )
                    }
                    builder.append(nested)
                }
            }
        }
        return builder.toString()
    }

    private fun Element.parseTtmlBackground(fallbackEnd: Long?, fallbackTranslation: String?): TtmlBackground {
        val words = mutableListOf<LyricWord>()
        val translation = childrenElements()
            .firstOrNull { it.hasRole("x-translation") }
            ?.textContent
            ?.cleanLyricText()
            ?.takeUsefulText()
            ?: fallbackTranslation?.splitAppleTranslation()?.second
        val text = collectTtmlMainText(this, words, fallbackEnd)
            .removeBackgroundParentheses()
            .cleanLyricText()
        return TtmlBackground(
            text = text,
            words = words.map { it.copy(text = it.text.removeBackgroundParentheses()) }.filter { it.text.isNotBlank() },
            translation = translation
        )
    }

    private fun mergeCompanionLines(lines: List<LyricLine>): List<LyricLine> {
        val merged = lines
            .sortedBy { it.timeMs }
            .groupBy { it.timeMs }
            .values
            .map { group ->
                if (group.size == 1) return@map group.first()
                val hasRomanizedCompanion = group.size >= 3 && group.any { it.text.isPronunciationLine() }
                val primary = if (hasRomanizedCompanion) {
                    group.firstOrNull { it.text.cleanLyricText().hasCjk() && it.text.isUsefulMainText() }
                } else {
                    null
                } ?: group.firstOrNull { it.text.isUsefulMainText() } ?: group.first()
                val primaryText = primary.text.cleanLyricText()
                val pronunciation = group
                    .takeIf { it.size >= 3 && primaryText.hasCjk() }
                    ?.firstOrNull { it !== primary && it.text.isPronunciationLine() }
                val translationCandidates = group
                    .asSequence()
                    .filter { it !== primary && it !== pronunciation }
                    .map { it.text.cleanLyricText() }
                    .filter { it.isUsefulMainText() && it != primaryText }
                    .toList()
                val translation = translationCandidates
                    .firstOrNull { primaryText.hasCjk() && it.hasCjk() }
                    ?: translationCandidates.firstOrNull()
                primary.copy(
                    translation = primary.translation ?: translation,
                    pronunciation = primary.pronunciation ?: pronunciation?.text?.cleanLyricText(),
                    pronunciationWords = primary.pronunciationWords.ifEmpty { pronunciation?.words.orEmpty() },
                    endMs = primary.endMs ?: group.mapNotNull { it.endMs }.maxOrNull()
                )
            }
        return attachBackgroundLines(merged)
    }

    private fun attachBackgroundLines(lines: List<LyricLine>): List<LyricLine> {
        val result = mutableListOf<LyricLine>()
        lines.sortedBy { it.timeMs }.forEach { line ->
            if (line.text.isBlank() && !line.backgroundText.isNullOrBlank()) {
                val targetIndex = result.indexOfLast { abs(it.timeMs - line.timeMs) <= 350L }
                if (targetIndex >= 0) {
                    val target = result[targetIndex]
                    result[targetIndex] = target.copy(
                        backgroundText = target.backgroundText ?: line.backgroundText,
                        backgroundWords = target.backgroundWords.ifEmpty { line.backgroundWords },
                        backgroundTranslation = target.backgroundTranslation
                            ?: line.backgroundTranslation
                            ?: line.backgroundText.takeIf {
                                !target.backgroundText.isNullOrBlank() && it != target.backgroundText
                            },
                        endMs = listOfNotNull(target.endMs, line.endMs).maxOrNull()
                    )
                } else {
                    result += line
                }
            } else {
                result += line
            }
        }
        return result
    }

    private data class TtmlBackground(
        val text: String,
        val words: List<LyricWord>,
        val translation: String?
    )

    private data class TtmlPronunciation(
        val text: String,
        val words: List<LyricWord>
    )

    private fun List<LyricWord>.alignPronunciationWords(
        mainWords: List<LyricWord>,
        mainText: String
    ): List<LyricWord> {
        if (isEmpty() || mainWords.isEmpty()) return emptyList()

        if (size == mainWords.size) {
            return mainWords.mapIndexed { index, word -> word.copy(text = this[index].text) }
        }

        val byOverlap = mainWords.mapNotNull { word ->
            val match = maxByOrNull { ruby ->
                val overlap = minOf(word.endMs, ruby.endMs) - maxOf(word.startMs, ruby.startMs)
                overlap.coerceAtLeast(0L)
            }?.takeIf { ruby ->
                minOf(word.endMs, ruby.endMs) > maxOf(word.startMs, ruby.startMs)
            }
            match?.let { word.copy(text = it.text) }
        }
        if (byOverlap.size == mainWords.size) return byOverlap

        val cjkWordIndices = mainWords
            .mapIndexedNotNull { index, word -> index.takeIf { word.text.hasCjk() } }
        if (cjkWordIndices.size == size) {
            val result = MutableList(mainWords.size) { index -> mainWords[index].copy(text = "") }
            cjkWordIndices.forEachIndexed { rubyIndex, wordIndex ->
                result[wordIndex] = mainWords[wordIndex].copy(text = this[rubyIndex].text)
            }
            return result.filter { it.text.isNotBlank() }
        }

        val cjkCharCount = mainText.count { it.isCjkChar() }
        if (cjkCharCount == size && mainWords.size == 1) {
            val word = mainWords.first()
            val duration = (word.endMs - word.startMs).coerceAtLeast(size * 120L)
            return mapIndexed { index, ruby ->
                val start = word.startMs + duration * index / size
                val end = word.startMs + duration * (index + 1) / size
                LyricWord(ruby.text, start, end)
            }
        }

        return emptyList()
    }

    private fun parseLrcTime(groups: List<String>): Long {
        val minutes = groups[1].toLongOrNull() ?: 0L
        val seconds = groups[2].toLongOrNull() ?: 0L
        val millisRaw = groups.getOrNull(3).orEmpty()
        val millis = when (millisRaw.length) {
            0 -> 0L
            1 -> millisRaw.toLongOrNull()?.times(100) ?: 0L
            2 -> millisRaw.toLongOrNull()?.times(10) ?: 0L
            else -> millisRaw.take(3).toLongOrNull() ?: 0L
        }
        return minutes * 60_000 + seconds * 1000 + millis
    }

    private fun String.parseFlexibleTime(): Int {
        val value = trim().replace(',', '.')
        if (value.isBlank()) return 0

        if (value.endsWith("ms", ignoreCase = true)) return value.dropLast(2).toDoubleOrNull()?.toInt() ?: 0
        if (value.endsWith("s", ignoreCase = true)) return ((value.dropLast(1).toDoubleOrNull() ?: 0.0) * 1000).toInt()

        val parts = value.split(":")
        fun secondsMs(part: String): Int {
            val pieces = part.split(".")
            val seconds = pieces.getOrNull(0)?.toIntOrNull()?.times(1000) ?: 0
            val msRaw = pieces.getOrNull(1).orEmpty()
            val ms = when (msRaw.length) {
                0 -> 0
                1 -> msRaw.toIntOrNull()?.times(100) ?: 0
                2 -> msRaw.toIntOrNull()?.times(10) ?: 0
                else -> msRaw.take(3).toIntOrNull() ?: 0
            }
            return seconds + ms
        }
        return when (parts.size) {
            1 -> secondsMs(parts[0])
            2 -> (parts[0].toIntOrNull() ?: 0) * 60_000 + secondsMs(parts[1])
            3 -> (parts[0].toIntOrNull() ?: 0) * 3_600_000 + (parts[1].toIntOrNull() ?: 0) * 60_000 + secondsMs(parts[2])
            else -> 0
        }
    }

    private fun String.parseTtmlTime(): Long? {
        if (isBlank()) return null
        return trim().parseFlexibleTime().toLong()
    }

    private fun String.isTimestampLike(): Boolean = timestampOnlyPattern.matches(trim().replace(',', '.'))

    private fun String.preformatTtml(): String =
        replace(" </span><span", "</span> <span")
            .replace(",</span><span", ",</span> <span")

    private fun String.cleanLyricText(): String =
        Html.fromHtml(this, Html.FROM_HTML_MODE_LEGACY)
            .toString()
            .replace(Regex("""[ \t\r\n]+"""), " ")
            .trim()

    private fun String.cleanTimedLyricText(): String =
        replace(timedWordMarkerPattern) { match ->
            val time = match.groupValues.getOrNull(1).orEmpty()
                .ifBlank { match.groupValues.getOrNull(2).orEmpty() }
                .trim()
            if (time.isTimestampLike()) "" else match.value
        }.cleanLyricText()

    private fun String.cleanTimedLyricSegment(): String =
        Html.fromHtml(
            replace(timedWordMarkerPattern) { match ->
                val time = match.groupValues.getOrNull(1).orEmpty()
                    .ifBlank { match.groupValues.getOrNull(2).orEmpty() }
                    .trim()
                if (time.isTimestampLike()) "" else match.value
            },
            Html.FROM_HTML_MODE_LEGACY
        )
            .toString()
            .replace(Regex("""[ \t\r\n]+"""), " ")

    private fun String.takeUsefulText(): String? =
        cleanLyricText().takeIf { it.isNotBlank() && !it.isMusicSymbolOnly() }

    private fun String.splitAppleTranslation(): Pair<String?, String?> {
        val text = cleanLyricText()
        if (!text.endsWith('）')) return text.takeUsefulText() to null
        val start = text.lastIndexOf('（')
        if (start < 0) return text.takeUsefulText() to null
        val main = text.substring(0, start).takeUsefulText()
        val bg = text.substring(start + 1, text.length - 1).takeUsefulText()
        return main to bg
    }

    private fun String.removeBackgroundParentheses(): String =
        replace(Regex("""[()（）]"""), "").cleanLyricText()

    private fun String.withoutFormattingWhitespace(): String =
        if (isBlank() && any { it == '\n' || it == '\r' || it == '\t' }) "" else this

    private fun List<LyricWord>.joinLyricText(): String {
        val raw = joinToString("") { it.text }.cleanLyricText()
        if (raw.isBlank() || raw.hasCjk() || raw.contains(' ')) return raw
        return map { it.text.cleanLyricText() }.filter { it.isNotBlank() }.joinToString(" ")
    }

    private fun List<LyricWord>.toDisplayWords(lineText: String): List<LyricWord> {
        if (isEmpty() || lineText.isBlank()) return this
        val normalized = lineText.cleanLyricText()
        if (normalized.hasCjk()) return withSpacing(normalized)
        val tokens = Regex("""\S+\s*""").findAll(normalized).map { it.value }.toList()
        if (tokens.isEmpty()) return withSpacing(normalized)
        val result = mutableListOf<LyricWord>()
        var index = 0
        tokens.forEach { token ->
            if (index >= size) return@forEach
            val startIndex = index
            val target = token.trim()
            val builder = StringBuilder()
            var endMs = this[index].endMs
            while (index < size && builder.length < target.length) {
                builder.append(this[index].text.trimTimedWordToken())
                endMs = this[index].endMs
                index++
            }
            if (builder.toString() == target) {
                result += this[startIndex].copy(text = token, endMs = endMs)
            }
        }
        val resultText = result.joinToString("") { it.text }.cleanLyricText()
        return if (result.isNotEmpty() && resultText == normalized) {
            result
        } else {
            withSpacing(normalized)
        }
    }

    private fun List<LyricWord>.withSpacing(lineText: String): List<LyricWord> {
        var cursor = 0
        return mapIndexed { index, word ->
            val start = lineText.indexOf(word.text, cursor)
            if (start < 0) return@mapIndexed word
            val end = start + word.text.length
            val next = getOrNull(index + 1)?.text
            val nextStart = if (next != null) lineText.indexOf(next, end) else -1
            val suffix = when {
                nextStart > end -> lineText.substring(end, nextStart)
                next == null && end < lineText.length -> lineText.substring(end)
                else -> ""
            }
            cursor = end + suffix.length
            word.copy(text = word.text + suffix)
        }
    }

    private fun String.trimTimedWordToken(): String = trim {
        it.isWhitespace() || it == '\u00A0' || it == '\u200B' || it == '\u2060'
    }

    private fun estimateDuration(text: String): Long =
        (text.cleanLyricText().length * 150L).coerceIn(180L, 2_200L)

    private fun String.isUsefulMainText(): Boolean = isNotBlank() && !isMusicSymbolOnly()

    private fun String.isMusicSymbolOnly(): Boolean {
        val content = trim()
        if (content.isBlank()) return true
        return content.all { char ->
            char.isWhitespace() ||
                char in setOf('♪', '♫', '♬', '♩', '♭', '♯', '♮', '☆', '★', '·', '.', '。', '…') ||
                Character.UnicodeBlock.of(char) == Character.UnicodeBlock.MUSICAL_SYMBOLS
        }
    }

    private fun String.hasCjk(): Boolean =
        any { it.isCjkChar() }

    private fun Char.isCjkChar(): Boolean =
        Character.UnicodeBlock.of(this) in setOf(
            Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS,
            Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_A,
            Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_B,
            Character.UnicodeBlock.CJK_COMPATIBILITY_IDEOGRAPHS,
            Character.UnicodeBlock.HIRAGANA,
            Character.UnicodeBlock.KATAKANA,
            Character.UnicodeBlock.HANGUL_SYLLABLES
        )

    private fun String.isPronunciationLine(): Boolean {
        val text = cleanLyricText()
        if (text.isBlank() || text.hasCjk() || text.isMusicSymbolOnly()) return false
        val letters = text.count { it.isLetter() }
        return letters >= 2 && text.all {
            it.isLetter() ||
                it.isWhitespace() ||
                it in "-'`.:,;!?/()[]{}" ||
                it in setOf('‘', '’', '“', '”', 'ʼ', '・', '·')
        }
    }

    private fun Element.attr(name: String): String {
        getAttribute(name).takeIf { it.isNotBlank() }?.let { return it }
        attributes ?: return ""
        for (index in 0 until attributes.length) {
            val item = attributes.item(index)
            if (item.nodeName == name || item.nodeName.substringAfter(':') == name.substringAfter(':')) {
                return item.nodeValue.orEmpty()
            }
        }
        return ""
    }

    private fun Element.hasRole(role: String): Boolean =
        attr("role") == role || attr("ttm:role") == role

    private fun Element.localTagName(): String = tagName.substringAfter(':')

    private fun Element.childrenElements(): List<Element> =
        childNodes.toNodeList().mapNotNull { it as? Element }

    private fun Element.allElements(): List<Element> {
        val result = mutableListOf<Element>()
        fun visit(element: Element) {
            result += element
            element.childrenElements().forEach(::visit)
        }
        visit(this)
        return result
    }

    private fun org.w3c.dom.NodeList.toNodeList(): List<Node> =
        List(length) { item(it) }
}
