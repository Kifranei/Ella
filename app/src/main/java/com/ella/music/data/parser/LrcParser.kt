package com.ella.music.data.parser

import android.os.ParcelFileDescriptor
import android.text.Html
import com.ella.music.data.model.LyricLine
import com.ella.music.data.model.LyricWord
import java.io.File
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction
import java.io.StringReader
import javax.xml.parsers.DocumentBuilderFactory
import org.w3c.dom.Element
import org.w3c.dom.Node
import org.xml.sax.InputSource

object LrcParser {

    private val timePattern = Regex("""\[(\d{1,3}):(\d{2})(?:[.:](\d{1,3}))?]""")
    private val retroLinePattern = Regex("""((?:\[.*?])+)(.*)""")
    private val wordTimePattern = Regex("""<(\d{1,3}):(\d{2})(?:[.:](\d{1,3}))?>""")
    private val metaDataPattern = Regex("""\[(ti|ar|al|by|offset|re|ve):\s*(.*)]""", RegexOption.IGNORE_CASE)
    private val ttmlParagraphPattern = Regex("""<p\b([^>]*)>(.*?)</p>""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
    private val ttmlSpanPattern = Regex("""<span\b([^>]*)>(.*?)</span>""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
    private val ttmlTranslationPattern = Regex("""<span\b(?=[^>]*ttm:role="x-translation")[^>]*>(.*?)</span>""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
    private val ttmlBackgroundPattern = Regex("""<span\b(?=[^>]*ttm:role="x-bg")[^>]*>.*?</span>""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
    private val xmlTagPattern = Regex("""<[^>]+>""")
    private val beginAttributePattern = Regex("\\bbegin=\"([^\"]+)\"", RegexOption.IGNORE_CASE)
    private val endAttributePattern = Regex("\\bend=\"([^\"]+)\"", RegexOption.IGNORE_CASE)

    data class LrcResult(
        val lyrics: List<LyricLine>,
        val title: String? = null,
        val artist: String? = null,
        val album: String? = null,
        val offset: Long = 0L
    )

    fun parse(lrcContent: String): LrcResult {
        parseTtml(lrcContent)?.let { return it }

        val lines = lrcContent.lines()
        val lyrics = mutableListOf<LyricLine>()
        var title: String? = null
        var artist: String? = null
        var album: String? = null
        var offset = 0L

        for (line in lines) {
            val trimmed = line.trim()
            if (trimmed.isEmpty()) continue

            val metaMatch = metaDataPattern.find(trimmed)
            if (metaMatch != null) {
                when (metaMatch.groupValues[1].lowercase()) {
                    "ti" -> title = metaMatch.groupValues[2].trim()
                    "ar" -> artist = metaMatch.groupValues[2].trim()
                    "al" -> album = metaMatch.groupValues[2].trim()
                    "offset" -> offset = metaMatch.groupValues[2].trim().toLongOrNull() ?: 0L
                }
                continue
            }

            parseInlineTimedLine(trimmed)?.let {
                lyrics.add(it)
                continue
            }

            val lineMatch = retroLinePattern.matchEntire(trimmed) ?: continue
            val times = lineMatch.groupValues[1]
            val text = lineMatch.groupValues[2].trim()
            if (text.isMusicSymbolOnly()) continue

            val timeMatches = timePattern.findAll(times).toList()
            if (timeMatches.isEmpty()) continue

            val hasWordTiming = wordTimePattern.containsMatchIn(text)

            for (timeMatch in timeMatches) {
                val timeMs = parseTime(timeMatch.groupValues)

                if (hasWordTiming) {
                    val words = parseEnhancedWords(text, timeMs)
                    val fullText = words.joinToString("") { it.text }
                    lyrics.add(LyricLine(timeMs, fullText, words))
                } else {
                    lyrics.add(LyricLine(timeMs, text.trim()))
                }
            }
        }

        return LrcResult(
            lyrics = mergeSameTimestampLines(lyrics),
            title = title,
            artist = artist,
            album = album,
            offset = offset
        )
    }

    private fun parseTtml(content: String): LrcResult? {
        if (!content.trimStart().startsWith("<tt", ignoreCase = true)) return null
        parseTtmlDom(content)?.let { return it }

        val lyrics = ttmlParagraphPattern.findAll(content)
            .mapNotNull { paragraph ->
                val attributes = paragraph.groupValues[1]
                val body = paragraph.groupValues[2]
                val begin = beginAttributePattern.find(attributes)?.groupValues?.getOrNull(1)?.parseTtmlTime()
                    ?: return@mapNotNull null
                val end = endAttributePattern.find(attributes)?.groupValues?.getOrNull(1)?.parseTtmlTime()
                val agent = Regex("""\b(?:ttm:)?agent="([^"]+)"""", RegexOption.IGNORE_CASE)
                    .find(attributes)
                    ?.groupValues
                    ?.getOrNull(1)

                val translation = ttmlTranslationPattern
                    .find(body)
                    ?.groupValues
                    ?.getOrNull(1)
                    ?.stripXmlText()
                    ?.takeIf { it.isNotBlank() && !it.isMusicSymbolOnly() }

                val mainBody = body
                    .replace(ttmlTranslationPattern, "")
                    .replace(ttmlBackgroundPattern, "")
                val background = ttmlBackgroundPattern
                    .find(body)
                    ?.value
                    ?.parseTtmlBackground()
                val text = mainBody.stripXmlText()
                if (text.isMusicSymbolOnly()) return@mapNotNull null

                val words = ttmlSpanPattern.findAll(mainBody)
                    .mapNotNull { span ->
                        val spanAttributes = span.groupValues[1]
                        if (spanAttributes.contains("ttm:role=", ignoreCase = true)) return@mapNotNull null
                        val wordBegin = beginAttributePattern.find(spanAttributes)?.groupValues?.getOrNull(1)?.parseTtmlTime()
                            ?: return@mapNotNull null
                        val wordEnd = endAttributePattern.find(spanAttributes)?.groupValues?.getOrNull(1)?.parseTtmlTime()
                            ?: end
                            ?: (wordBegin + estimateWordDuration(span.groupValues[2].stripXmlText()))
                        val wordText = span.groupValues[2].stripXmlText()
                        if (wordText.isBlank()) null else LyricWord(wordText, wordBegin, wordEnd)
                    }
                    .toList()

                LyricLine(
                    timeMs = begin,
                    text = text,
                    words = words.withTextSpacing(text),
                    translation = translation,
                    agent = agent,
                    backgroundText = background?.text,
                    backgroundTranslation = background?.translation
                )
            }
            .sortedBy { it.timeMs }
            .toList()

        return LrcResult(lyrics = lyrics)
    }

    private fun parseTtmlDom(content: String): LrcResult? {
        return try {
            val document = DocumentBuilderFactory.newInstance().apply {
                isNamespaceAware = false
                isIgnoringComments = true
                isCoalescing = true
            }.newDocumentBuilder().parse(InputSource(StringReader(content)))

            val paragraphs = document.getElementsByTagName("p")
            val lyrics = (0 until paragraphs.length).mapNotNull { index ->
                val paragraph = paragraphs.item(index) as? Element ?: return@mapNotNull null
                val begin = paragraph.getAttribute("begin").parseTtmlTime() ?: return@mapNotNull null
                val end = paragraph.getAttribute("end").parseTtmlTime()
                val words = mutableListOf<LyricWord>()
                val translations = mutableListOf<String>()
                val backgrounds = mutableListOf<TtmlBackground>()
                val agent = paragraph.getAttribute("ttm:agent")
                    .ifBlank { paragraph.getAttribute("agent") }
                    .ifBlank { null }
                val text = collectTtmlText(paragraph, words, translations, backgrounds, end)
                    .replace(Regex("""[ \t\r\n]+"""), " ")
                    .trim()
                if (text.isMusicSymbolOnly()) return@mapNotNull null

                LyricLine(
                    timeMs = begin,
                    text = text,
                    words = words.withTextSpacing(text),
                    translation = translations.firstOrNull { it.isNotBlank() && !it.isMusicSymbolOnly() },
                    agent = agent,
                    backgroundText = backgrounds.firstOrNull { it.text.isNotBlank() && !it.text.isMusicSymbolOnly() }?.text,
                    backgroundTranslation = backgrounds.firstOrNull { !it.translation.isNullOrBlank() }?.translation
                )
            }.sortedBy { it.timeMs }

            LrcResult(lyrics = lyrics).takeIf { lyrics.isNotEmpty() }
        } catch (_: Exception) {
            null
        }
    }

    private fun collectTtmlText(
        node: Node,
        words: MutableList<LyricWord>,
        translations: MutableList<String>,
        backgrounds: MutableList<TtmlBackground>,
        lineEndMs: Long?
    ): String {
        val builder = StringBuilder()
        val children = node.childNodes
        for (index in 0 until children.length) {
            val child = children.item(index)
            when (child.nodeType) {
                Node.TEXT_NODE -> builder.append(child.nodeValue)
                Node.ELEMENT_NODE -> {
                    val element = child as? Element ?: continue
                    val role = element.getAttribute("ttm:role").ifBlank { element.getAttribute("role") }
                    when (role) {
                        "x-translation" -> translations += element.textContent.cleanTtmlText()
                        "x-bg" -> backgrounds += collectTtmlBackground(element)
                        else -> {
                            val text = collectTtmlText(element, words, translations, backgrounds, lineEndMs)
                            val wordBegin = element.getAttribute("begin").parseTtmlTime()
                            if (wordBegin != null && text.isNotBlank()) {
                                val wordEnd = element.getAttribute("end").parseTtmlTime()
                                    ?: lineEndMs
                                    ?: (wordBegin + estimateWordDuration(text))
                                words += LyricWord(text, wordBegin, wordEnd)
                            }
                            builder.append(text)
                        }
                    }
                }
            }
        }
        return builder.toString()
    }

    private data class TtmlBackground(
        val text: String,
        val translation: String? = null
    )

    private fun collectTtmlBackground(element: Element): TtmlBackground {
        val translations = mutableListOf<String>()
        val textBuilder = StringBuilder()
        val children = element.childNodes
        for (index in 0 until children.length) {
            val child = children.item(index)
            when (child.nodeType) {
                Node.TEXT_NODE -> textBuilder.append(child.nodeValue)
                Node.ELEMENT_NODE -> {
                    val childElement = child as? Element ?: continue
                    val role = childElement.getAttribute("ttm:role").ifBlank { childElement.getAttribute("role") }
                    if (role == "x-translation") {
                        translations += childElement.textContent.cleanTtmlText()
                    } else {
                        textBuilder.append(childElement.textContent)
                    }
                }
            }
        }
        return TtmlBackground(
            text = textBuilder.toString().cleanTtmlText(),
            translation = translations.firstOrNull { it.isNotBlank() && !it.isMusicSymbolOnly() }
        )
    }

    private fun String.parseTtmlBackground(): TtmlBackground {
        val translation = ttmlTranslationPattern
            .find(this)
            ?.groupValues
            ?.getOrNull(1)
            ?.stripXmlText()
            ?.takeIf { it.isNotBlank() && !it.isMusicSymbolOnly() }
        val text = replace(ttmlTranslationPattern, "")
            .stripXmlText()
            .takeIf { it.isNotBlank() && !it.isMusicSymbolOnly() }
            .orEmpty()
        return TtmlBackground(text, translation)
    }

    private fun parseInlineTimedLine(line: String): LyricLine? {
        val matches = timePattern.findAll(line).toList()
        if (matches.size < 2) return null

        val timedSegments = matches.mapIndexedNotNull { index, match ->
            val start = match.range.last + 1
            val end = matches.getOrNull(index + 1)?.range?.first ?: line.length
            val text = line.substring(start, end)
            if (text.isBlank()) null else TimedSegment(parseTime(match.groupValues), text, index)
        }
        if (timedSegments.isEmpty()) return null

        val first = timedSegments.first()
        val wordSegments = if (first.text.hasCjk() && timedSegments.getOrNull(1)?.timeMs == first.timeMs) {
            timedSegments.drop(1)
        } else {
            timedSegments
        }.filter { !it.text.isMusicSymbolOnly() }

        if (wordSegments.isEmpty()) return null

        val hasWordTiming = wordSegments.size > 1 && wordSegments.any { !it.text.hasCjk() }
        if (!hasWordTiming) {
            return LyricLine(first.timeMs, first.text.trim())
        }

        val words = wordSegments.mapIndexed { index, segment ->
            val next = wordSegments.getOrNull(index + 1)
            val nextRawTime = matches
                .getOrNull(segment.matchIndex + 1)
                ?.groupValues
                ?.let(::parseTime)
                ?.takeIf { it > segment.timeMs }
            LyricWord(
                text = segment.text,
                startMs = segment.timeMs,
                endMs = next?.timeMs ?: nextRawTime ?: (segment.timeMs + estimateWordDuration(segment.text))
            )
        }
        val text = words.joinToString("") { it.text }.trim()
        if (text.isMusicSymbolOnly()) return null

        val translation = first.text
            .takeIf { first.text.hasCjk() && timedSegments.getOrNull(1)?.timeMs == first.timeMs }
            ?.trim()
            ?.takeIf { it.isNotBlank() && !it.isMusicSymbolOnly() }

        return LyricLine(wordSegments.first().timeMs, text, words, translation)
    }

    private data class TimedSegment(
        val timeMs: Long,
        val text: String,
        val matchIndex: Int
    )

    private fun mergeSameTimestampLines(lines: List<LyricLine>): List<LyricLine> {
        return lines
            .sortedBy { it.timeMs }
            .groupBy { it.timeMs }
            .values
            .map { sameTimeLines ->
                if (sameTimeLines.size == 1) return@map sameTimeLines.first()

                val primary = sameTimeLines.firstOrNull { it.words.isNotEmpty() && !it.text.hasCjk() }
                    ?: sameTimeLines.firstOrNull { it.words.isNotEmpty() }
                    ?: sameTimeLines.firstOrNull { !it.text.hasCjk() && !it.text.isMusicSymbolOnly() }
                    ?: sameTimeLines.firstOrNull { !it.text.isMusicSymbolOnly() }
                    ?: sameTimeLines.first()
                val translation = sameTimeLines
                    .asSequence()
                    .filter { it !== primary }
                    .map { it.text.trim() }
                    .firstOrNull { it.isNotBlank() && !it.isMusicSymbolOnly() && it != primary.text.trim() }

                primary.copy(translation = primary.translation ?: translation)
            }
    }

    private fun parseEnhancedWords(text: String, lineStartMs: Long): List<LyricWord> {
        val words = mutableListOf<LyricWord>()
        val matches = wordTimePattern.findAll(text).toList()

        for (i in matches.indices) {
            val current = matches[i]
            val next = matches.getOrNull(i + 1)
            val wordText = text
                .substring(current.range.last + 1, next?.range?.first ?: text.length)
                .replace(wordTimePattern, "")
            if (wordText.isNotEmpty()) {
                val startMs = parseTime(current.groupValues)
                val endMs = if (next != null) {
                    parseTime(next.groupValues)
                } else {
                    startMs + estimateWordDuration(wordText)
                }
                words.add(LyricWord(wordText, startMs, endMs))
            }
        }

        if (words.isEmpty() && text.isNotBlank()) {
            words.add(LyricWord(text, lineStartMs, lineStartMs + 1000))
        }

        return words
    }

    private fun List<LyricWord>.withTextSpacing(lineText: String): List<LyricWord> {
        if (isEmpty() || lineText.isBlank()) return this

        val result = mutableListOf<LyricWord>()
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
            val suffix = if (nextStart > end) lineText.substring(end, nextStart) else ""
            result += word.copy(text = word.text + suffix)
            cursor = end + suffix.length
        }

        return result
    }

    private fun estimateWordDuration(text: String): Long {
        return (text.length * 150L).coerceIn(200L, 2000L)
    }

    private fun parseTime(groups: List<String>): Long {
        val minutes = groups[1].toLongOrNull() ?: 0L
        val seconds = groups[2].toLongOrNull() ?: 0L
        val millisStr = groups.getOrNull(3).orEmpty()
        val millis = when (millisStr.length) {
            1 -> (millisStr.toLongOrNull() ?: 0L) * 100
            2 -> (millisStr.toLongOrNull() ?: 0L) * 10
            3 -> millisStr.toLongOrNull() ?: 0L
            else -> 0L
        }
        return minutes * 60_000 + seconds * 1000 + millis
    }

    private fun String.parseTtmlTime(): Long? {
        val value = trim()
        if (value.isBlank()) return null

        if (value.endsWith("ms", ignoreCase = true)) {
            return value.dropLast(2).toDoubleOrNull()?.toLong()
        }
        if (value.endsWith("s", ignoreCase = true)) {
            return ((value.dropLast(1).toDoubleOrNull() ?: return null) * 1000).toLong()
        }

        val parts = value.split(":")
        return when (parts.size) {
            1 -> ((parts[0].toDoubleOrNull() ?: return null) * 1000).toLong()
            2 -> {
                val minutes = parts[0].toLongOrNull() ?: return null
                val seconds = parts[1].toDoubleOrNull() ?: return null
                (minutes * 60_000 + seconds * 1000).toLong()
            }
            3 -> {
                val hours = parts[0].toLongOrNull() ?: return null
                val minutes = parts[1].toLongOrNull() ?: return null
                val seconds = parts[2].toDoubleOrNull() ?: return null
                (hours * 3_600_000 + minutes * 60_000 + seconds * 1000).toLong()
            }
            else -> null
        }
    }

    private fun String.stripXmlText(): String {
        val withoutTags = replace(xmlTagPattern, "")
        return Html.fromHtml(withoutTags, Html.FROM_HTML_MODE_LEGACY)
            .toString()
            .cleanTtmlText()
    }

    private fun String.cleanTtmlText(): String =
        replace(Regex("""[ \t\r\n]+"""), " ").trim()

    private fun String.hasCjk(): Boolean =
        any { char ->
            Character.UnicodeBlock.of(char) in setOf(
                Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS,
                Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_A,
                Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_B,
                Character.UnicodeBlock.CJK_COMPATIBILITY_IDEOGRAPHS,
                Character.UnicodeBlock.HIRAGANA,
                Character.UnicodeBlock.KATAKANA,
                Character.UnicodeBlock.HANGUL_SYLLABLES
            )
        }

    private fun String.isMusicSymbolOnly(): Boolean {
        val content = trim()
        if (content.isBlank()) return true
        return content.all { char ->
            char.isWhitespace() ||
                char in setOf('♪', '♫', '♬', '♩', '♭', '♯', '♮') ||
                Character.UnicodeBlock.of(char) == Character.UnicodeBlock.MUSICAL_SYMBOLS
        }
    }

    fun findLrcFile(songPath: String): String? {
        val baseName = songPath.substringBeforeLast('.')
        val candidates = listOf("$baseName.lrc", "${baseName}.LRC")
        for (candidate in candidates) {
            readViaFd(candidate)?.let { return it }
        }

        val parentDir = File(songPath).parentFile
        if (parentDir != null) {
            val songName = File(songPath).nameWithoutExtension
            try {
                parentDir.listFiles()?.find {
                    it.extension.equals("lrc", ignoreCase = true) &&
                        it.nameWithoutExtension.contains(songName, ignoreCase = true)
                }?.let { readViaFd(it.absolutePath)?.let { text -> return text } }
            } catch (_: Exception) {}
        }

        return null
    }

    private fun readViaFd(path: String): String? {
        return try {
            val file = File(path)
            if (!file.exists()) return null
            ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { pfd ->
                FileInputStream(pfd.fileDescriptor).use { fis ->
                    val bytes = fis.readBytes()
                    if (bytes.isEmpty()) return null
                    readTextWithFallback(bytes)
                }
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun readTextWithFallback(bytes: ByteArray): String {
        val charsets = listOf("UTF-8", "GB18030", "UTF-16LE", "UTF-16BE")
        for (charsetName in charsets) {
            val charset = Charset.forName(charsetName)
            try {
                val decoder = charset.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                return decoder.decode(ByteBuffer.wrap(bytes)).toString()
            } catch (_: CharacterCodingException) {
            }
        }
        return String(bytes, Charsets.UTF_8)
    }
}
