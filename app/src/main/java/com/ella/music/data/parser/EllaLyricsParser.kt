package com.ella.music.data.parser

import android.text.Html
import com.ella.music.data.model.LyricLine
import com.ella.music.data.model.LyricWord
import com.ella.music.data.model.shiftedBy
import java.io.StringReader
import javax.xml.parsers.DocumentBuilderFactory
import org.w3c.dom.Element
import org.w3c.dom.Node
import org.xml.sax.InputSource
import kotlin.math.abs

internal object EllaLyricsParser {
    private val lrcTimePattern = Regex("""\[(\d{1,3}):(\d{1,2})(?:[.:](\d{1,6}))?]""")
    private val lrcMetaPattern = Regex("""\[(ti|ar|al|by|offset|re|ve):\s*(.*)]""", RegexOption.IGNORE_CASE)
    private val lrcGenericMetaPattern = Regex("""^\[[A-Za-z][A-Za-z0-9 _\-]*:[^\]]*]$""")
    private val timedWordMarkerPattern = Regex("""<([^>]+)>|\[([^\]]+)]""")
    private val backgroundLinePattern = Regex("""^\[bg:\s*(.*)]$""", RegexOption.IGNORE_CASE)
    private val lyricifySyllablePattern = Regex("""(.*?)\((\d+),(\d+)\)""")
    private val lyricifyAttributePattern = Regex("""^\[(\d+)]""")
    private val timestampOnlyPattern = Regex("""\d+(?::\d{1,2}){1,2}(?:[.:]\d{1,6})?""")
    private val unknownTtmlAgentIdPattern = Regex("""v\d+|agent\d+""", RegexOption.IGNORE_CASE)

    fun parse(content: String, ignoreHeaderTags: Boolean = false): LrcParser.LrcResult {
        parseTtml(content)?.let { return it }
        if (lyricifySyllablePattern.containsMatchIn(content)) {
            parseLyricify(content)?.let { return it }
        }
        return parseLrc(content, ignoreHeaderTags)
    }

    private fun parseLrc(content: String, ignoreHeaderTags: Boolean = false): LrcParser.LrcResult {
        val lines = mutableListOf<LyricLine>()
        var title: String? = null
        var artist: String? = null
        var album: String? = null
        var offset = 0L
        var companionTargetIndexes = emptyList<Int>()

        content.lines().forEach { raw ->
            val line = raw.trim()
            if (line.isBlank()) {
                companionTargetIndexes = emptyList()
                return@forEach
            }

            lrcMetaPattern.matchEntire(line)?.let { match ->
                when (match.groupValues[1].lowercase()) {
                    "ti" -> title = match.groupValues[2].trim()
                    "ar" -> artist = match.groupValues[2].trim()
                    "al" -> album = match.groupValues[2].trim()
                    "offset" -> offset = match.groupValues[2].trim().toLongOrNull() ?: 0L
                }
                companionTargetIndexes = emptyList()
                return@forEach
            }
            parseTimestampOnlyLine(line)?.let { endMs ->
                lines.applyLineEnd(companionTargetIndexes, endMs)
                companionTargetIndexes = emptyList()
                return@forEach
            }
            val withoutTimes = lrcTimePattern.replace(line, "").trim()
            val hasTimestamps = withoutTimes != line.trim()
            if (hasTimestamps && isPlaceholderOnlyLine(withoutTimes)) {
                val endMs = parseLrcTime(lrcTimePattern.findAll(line).last().groupValues)
                if (endMs > 0L) {
                    lines.applyLineEnd(companionTargetIndexes, endMs)
                }
                companionTargetIndexes = emptyList()
                return@forEach
            }
            if (hasTimestamps && withoutTimes.isBlank()) {
                companionTargetIndexes = emptyList()
                return@forEach
            }
            if (lrcGenericMetaPattern.matches(line)) {
                companionTargetIndexes = emptyList()
                return@forEach
            }
            if (ignoreHeaderTags && isHeaderTagLine(line)) {
                companionTargetIndexes = emptyList()
                return@forEach
            }

            val parsed = parseLrcLine(line)
            if (parsed.isNotEmpty()) {
                val firstIndex = lines.size
                lines += parsed
                companionTargetIndexes = (firstIndex until lines.size).toList()
            } else if (!lines.appendUntimedTranslation(companionTargetIndexes, line)) {
                companionTargetIndexes = emptyList()
            }
        }

        return LrcParser.LrcResult(
            lyrics = mergeCompanionLines(lines)
                .shiftedBy(-offset),
            title = title,
            artist = artist,
            album = album,
            offset = offset
        )
    }

    private fun parseTimestampOnlyLine(line: String): Long? {
        val times = lrcTimePattern.findAll(line).toList()
        if (times.isEmpty()) return null
        var cursor = 0
        times.forEach { match ->
            if (line.substring(cursor, match.range.first).isNotBlank()) return null
            cursor = match.range.last + 1
        }
        if (line.substring(cursor).isNotBlank()) return null
        return parseLrcTime(times.last().groupValues)
    }

    private fun String?.mergeLyricCompanionText(text: String?): String? =
        listOfNotNull(this?.takeIf { it.isNotBlank() }, text?.takeIf { it.isNotBlank() })
            .distinct()
            .joinToString("\n")
            .takeIf { it.isNotBlank() }

    private fun MutableList<LyricLine>.applyLineEnd(indexes: List<Int>, endMs: Long) {
        indexes.forEach { index ->
            val line = getOrNull(index) ?: return@forEach
            if (endMs > line.timeMs) {
                this[index] = line.copy(endMs = line.endMs ?: endMs)
            }
        }
    }

    private fun MutableList<LyricLine>.appendUntimedTranslation(indexes: List<Int>, rawLine: String): Boolean {
        if (indexes.isEmpty()) return false
        val (_, content) = rawLine.extractLrcAgent()
        val text = content.cleanLyricSecondaryText()
        if (text.isIgnorableLyricText()) return false
        indexes.forEach { index ->
            val line = getOrNull(index) ?: return@forEach
            this[index] = line.copy(
                translation = line.translation.mergeLyricCompanionText(text)
            )
        }
        return true
    }

    private fun parseLrcLine(line: String): List<LyricLine> {
        backgroundLinePattern.matchEntire(line)?.let { match ->
            val content = match.groupValues[1].trim()
            val words = parseEnhancedWords(content, 0L)
            val text = if (words.isNotEmpty()) words.joinLyricText() else content.cleanLyricText()
            if (text.isIgnorableLyricText()) return emptyList()
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

        val leadingTimes = line.leadingLrcTimeMatches()
        if (leadingTimes.isEmpty()) return emptyList()

        val contentStart = leadingTimes.last().range.last + 1
        val taggedContent = line.substring(contentStart).trim()
        val embeddedBackground = backgroundLinePattern.matchEntire(taggedContent)
        val rawContent = embeddedBackground?.groupValues?.get(1)?.trim() ?: taggedContent
        if (rawContent.isBlank()) return emptyList()

        val (agent, content) = rawContent.extractLrcAgent().let { (agent, content) ->
            if (agent != null) {
                agent to content
            } else {
                content.extractEnhancedLrcAgent()
            }
        }

        return leadingTimes.mapNotNull { timeMatch ->
            val start = parseLrcTime(timeMatch.groupValues)
            val words = parseEnhancedWords(content, start)
            val text = if (words.isNotEmpty()) words.joinLyricText() else content.cleanLyricText()
            if (text.isIgnorableLyricText()) return@mapNotNull null
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

    private fun String.extractLrcAgent(): Pair<String?, String> {
        Regex("""^(v[12])\s*[:：]\s*(.*)$""", RegexOption.IGNORE_CASE)
            .matchEntire(this)
            ?.let { match ->
                return match.groupValues[1].lowercase() to match.groupValues[2].trim()
            }
        Regex("""^\[(v[12])]\s*(.*)$""", RegexOption.IGNORE_CASE)
            .matchEntire(this)
            ?.let { match ->
                return match.groupValues[1].lowercase() to match.groupValues[2].trim()
            }
        return null to this
    }

    private fun String.extractEnhancedLrcAgent(): Pair<String?, String> {
        val match = Regex(
            """^(\s*(?:<\d{1,3}:\d{2}(?:\.\d{1,3})?>|\[\d{1,3}:\d{2}(?:\.\d{1,3})?])\s*)(v[12])\s*[:：]\s*""",
            RegexOption.IGNORE_CASE
        ).find(this) ?: return null to this
        return match.groupValues[2].lowercase() to match.groupValues[1] + substring(match.range.last + 1)
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
            if (marker.timeMs < activeStart) return@forEach
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
                if (text.isIgnorableLyricText()) return@mapNotNull null

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
                trySetFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
                trySetFeature("http://xml.org/sax/features/external-general-entities", false)
                trySetFeature("http://xml.org/sax/features/external-parameter-entities", false)
            }.newDocumentBuilder().parse(InputSource(StringReader(content.preformatTtml())))

            val root = document.documentElement
            val metadata = parseTtmlMetadata(root)
            val agentInfo = parseAgentInfo(root)
            val translations = parseTimedTextMap(root, "translations", "translation")
            val transliterations = parseTransliterations(root)
            val forceLineTiming = root.attr("itunes:timing")
                .ifBlank { root.attr("timing") }
                .equals("Line", ignoreCase = true)
            val paragraphs = root.allElements()
                .filter { it.localTagName() == "p" }

            val lines = paragraphs.mapNotNull { p ->
                val start = p.attr("begin").parseTtmlTime() ?: return@mapNotNull null
                val end = p.attr("end").parseTtmlTime()
                val key = p.attr("itunes:key").ifBlank { p.attr("key") }
                val rawAgent = p.attr("ttm:agent").ifBlank { p.attr("agent") }
                val agentIds = rawAgent.toTtmlAgentIds()
                val displayAgentName = agentIds.resolveTtmlAgentNames(agentInfo)
                val words = mutableListOf<LyricWord>()
                val rubyPronunciationWords = mutableListOf<LyricWord>()
                val text = collectTtmlMainText(p, words, end, rubyPronunciationWords).cleanLyricText()
                val displayText = text.takeUnless { it.isIgnorableLyricText() }.orEmpty()
                val displayWords = words.toTtmlDisplayWords(
                    lineText = displayText,
                    lineStart = start,
                    lineEnd = end,
                    forceLineTiming = forceLineTiming
                )
                val inlineTranslation = p.childrenElements()
                    .firstOrNull { it.hasRole("x-translation") && !it.hasRole("x-bg") }
                    ?.textContent
                    ?.cleanLyricSecondaryText()
                val bg = p.childrenElements()
                    .firstOrNull { it.hasRole("x-bg") }
                    ?.parseTtmlBackground(end, translations[key])
                val linePronunciation = p.childrenElements()
                    .firstOrNull { it.hasAnyRole("x-roman", "x-romanization") }
                    ?.textContent
                    ?.cleanLyricSecondaryText()
                val transliteration = transliterations[key]
                val pronunciationWords = when {
                    transliteration?.words?.isNotEmpty() == true -> transliteration.words.alignPronunciationWords(displayWords, text)
                    rubyPronunciationWords.isNotEmpty() -> rubyPronunciationWords
                    else -> emptyList()
                }
                val pronunciation = linePronunciation
                    ?: transliteration?.text?.takeUsefulSecondaryText()
                    ?: rubyPronunciationWords.joinLyricText().takeIf { it.isNotBlank() }
                    ?: pronunciationWords.joinLyricText().takeIf { it.isNotBlank() }

                if (displayText.isBlank() && bg == null) return@mapNotNull null

                LyricLine(
                    timeMs = start,
                    text = displayText,
                    words = displayWords,
                    translation = inlineTranslation?.takeUsefulSecondaryText() ?: translations[key]?.splitAppleTranslation()?.first,
                    pronunciation = pronunciation?.takeUsefulSecondaryText(),
                    pronunciationWords = pronunciationWords.toDisplayWords(pronunciation.orEmpty()),
                    agent = agentIds.firstOrNull() ?: rawAgent.takeIf(String::isNotBlank),
                    agentName = displayAgentName,
                    backgroundText = bg?.text,
                    backgroundWords = bg?.words.orEmpty().toDisplayWords(bg?.text.orEmpty()),
                    backgroundTranslation = bg?.translation,
                    backgroundStartMs = bg?.startMs,
                    backgroundEndMs = bg?.endMs,
                    isTtml = true,
                    endMs = end
                )
            }

            LrcParser.LrcResult(
                lyrics = assignTtmlAgentSides(lines.sortedBy { it.timeMs }, agentInfo),
                title = metadata.title,
                artist = metadata.artist,
                album = metadata.album
            )
        }.getOrNull()?.takeIf { it.lyrics.isNotEmpty() }
    }

    private data class TtmlMetadata(
        val title: String? = null,
        val artist: String? = null,
        val album: String? = null
    )

    private fun parseTtmlMetadata(root: Element): TtmlMetadata {
        var title: String? = null
        var artist: String? = null
        var album: String? = null

        root.allElements().forEach { element ->
            when (element.localTagName()) {
                "title" -> if (title == null) title = element.textContent.takeUsefulText()
                "meta" -> {
                    val key = element.attr("key").trim()
                    val value = element.attr("value")
                        .ifBlank { element.textContent.orEmpty() }
                        .takeUsefulText()
                        ?: return@forEach
                    when (key) {
                        "musicName" -> title = title ?: value
                        "artists" -> artist = artist ?: value
                        "album" -> album = album ?: value
                    }
                }
            }
        }

        return TtmlMetadata(title = title, artist = artist, album = album)
    }

    private data class TtmlAgentInfo(
        /** ttm:agent type: person / organization / group / other (lower-cased; may be blank). */
        val type: String,
        val name: String?
    )

    private fun DocumentBuilderFactory.trySetFeature(name: String, value: Boolean) {
        runCatching { setFeature(name, value) }
    }

    private fun parseAgentInfo(root: Element): Map<String, TtmlAgentInfo> {
        return root.allElements()
            .filter { it.localTagName() == "agent" }
            .mapNotNull { agent ->
                val id = agent.attr("xml:id").ifBlank { agent.attr("id") }.takeIf(String::isNotBlank)
                    ?: return@mapNotNull null
                id to TtmlAgentInfo(
                    type = agent.attr("type").lowercase(),
                    name = agent.displayName()
                )
            }
            .toMap()
    }

    private fun String.toTtmlAgentIds(): List<String> =
        split(Regex("""[\s,;]+"""))
            .map { it.trim().trimStart('#') }
            .filter { it.isNotBlank() }
            .distinct()

    private fun List<String>.resolveTtmlAgentNames(agentInfo: Map<String, TtmlAgentInfo>): String? {
        val names = mapNotNull { id ->
            agentInfo[id]?.name
                ?: id.takeUnless { unknownTtmlAgentIdPattern.matches(it) }
        }
            .map { it.trim() }
            .filter { it.isNotBlank() && !it.isMusicSymbolOnly() }
            .distinct()
        return names.takeIf { it.isNotEmpty() }?.joinToString("/")
    }

    /**
     * Assign each TTML line a display side (left = "v1", right = "v2") from its agent type, per the
     * AMLL convention:
     *  - group  -> always left
     *  - other  -> always right
     *  - person / organization (or untyped) -> the first one is left, then every switch to a
     *    different solo/organization agent flips to the opposite of the previous one; repeats of the
     *    same agent keep the current side. Group/other lines don't disturb that running side.
     * Lines must already be in playback (time) order.
     */
    private fun assignTtmlAgentSides(
        lines: List<LyricLine>,
        agentInfo: Map<String, TtmlAgentInfo>
    ): List<LyricLine> {
        var prevSoloId: String? = null
        var prevSoloSide: String? = null
        return lines.map { line ->
            val id = line.agent
            val side = when (agentInfo[id]?.type) {
                "group" -> "v1"
                "other" -> "v2"
                else -> {
                    val resolved = when {
                        prevSoloSide == null -> "v1"
                        id == prevSoloId -> prevSoloSide
                        else -> if (prevSoloSide == "v1") "v2" else "v1"
                    }
                    prevSoloId = id
                    prevSoloSide = resolved
                    resolved
                }
            }
            line.copy(agent = side)
        }
    }

    private fun parseTimedTextMap(root: Element, containerTag: String, itemTag: String): Map<String, String> {
        val result = mutableMapOf<String, String>()
        root.allElements()
            .filter { it.localTagName() == containerTag }
            .flatMap { it.childrenElements() }
            .filter { it.localTagName() == itemTag }
            .flatMap { it.childrenElements() }
            .filter { it.localTagName() == "text" }
            .plus(
                root.allElements()
                    .filter { it.localTagName() == itemTag }
                    .flatMap { it.childrenElements() }
                    .filter { it.localTagName() == "text" }
            )
            .forEach { text ->
                val key = text.attr("for").ifBlank { return@forEach }
                val value = text.textContent.cleanLyricSecondaryText()
                if (value.isNotBlank()) result.putIfAbsent(key, value)
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
                    .cleanLyricSecondaryText()
                    .takeUsefulSecondaryText()
                if (!plainText.isNullOrBlank() || words.isNotEmpty()) {
                    result[key] = TtmlPronunciation(
                        text = plainText.orEmpty(),
                        words = words
                    )
                }
            }
        return result
    }

    private fun collectTtmlMainText(
        element: Element,
        words: MutableList<LyricWord>,
        fallbackEnd: Long?,
        pronunciationWords: MutableList<LyricWord> = mutableListOf()
    ): String {
        val builder = StringBuilder()
        element.childNodes.toNodeList().forEach { node ->
            when (node.nodeType) {
                Node.TEXT_NODE -> builder.append(node.nodeValue.orEmpty().withoutFormattingWhitespace())
                Node.ELEMENT_NODE -> {
                    val child = node as? Element ?: return@forEach
                    if (child.hasRole("x-translation") || child.hasRole("x-bg") || child.hasAnyRole("x-roman", "x-romanization")) {
                        return@forEach
                    }
                    when (child.rubyMode()) {
                        "container" -> {
                            val ruby = child.parseRubyTtml(fallbackEnd)
                            if (ruby.text.isNotBlank()) {
                                builder.append(ruby.text)
                                words += ruby.words
                                pronunciationWords += ruby.pronunciationWords
                            }
                            return@forEach
                        }
                        "textContainer", "text" -> {
                            pronunciationWords += child.collectRubyPronunciationWords(fallbackEnd)
                            return@forEach
                        }
                    }
                    val wordCountBefore = words.size
                    val nested = collectTtmlMainText(child, words, fallbackEnd, pronunciationWords)
                    val nestedAddedTimedWords = words.size > wordCountBefore
                    val begin = child.attr("begin").parseTtmlTime()
                    if (begin != null && nested.isNotBlank() && !nestedAddedTimedWords) {
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

    private data class TtmlRuby(
        val text: String,
        val words: List<LyricWord>,
        val pronunciationWords: List<LyricWord>
    )

    private fun Element.parseRubyTtml(fallbackEnd: Long?): TtmlRuby {
        val pronunciationWords = collectRubyPronunciationWords(fallbackEnd)
        val baseText = childrenElements()
            .filter { it.rubyMode() == "base" }
            .joinToString("") { it.textContent.orEmpty() }
            .cleanLyricText()
            .ifBlank {
                childNodes.toNodeList()
                    .mapNotNull { node ->
                        when (node.nodeType) {
                            Node.TEXT_NODE -> node.nodeValue.orEmpty().withoutFormattingWhitespace()
                            Node.ELEMENT_NODE -> {
                                val child = node as? Element ?: return@mapNotNull null
                                child.takeUnless { it.rubyMode() in setOf("textContainer", "text") }
                                    ?.textContent
                                    .orEmpty()
                            }
                            else -> null
                        }
                    }
                    .joinToString("")
                    .cleanLyricText()
            }
        if (baseText.isBlank()) return TtmlRuby("", emptyList(), pronunciationWords)

        val begin = attr("begin").parseTtmlTime() ?: pronunciationWords.minOfOrNull { it.startMs }
        val end = attr("end").parseTtmlTime()
            ?: pronunciationWords.maxOfOrNull { it.endMs }
            ?: fallbackEnd
            ?: begin?.plus(estimateDuration(baseText))
        val words = if (begin != null && end != null) {
            listOf(LyricWord(baseText, begin, end))
        } else {
            emptyList()
        }
        return TtmlRuby(baseText, words, pronunciationWords)
    }

    private fun Element.collectRubyPronunciationWords(fallbackEnd: Long?): List<LyricWord> {
        val result = mutableListOf<LyricWord>()
        fun visit(element: Element) {
            val mode = element.rubyMode()
            if (mode == "text") {
                val value = element.textContent.cleanLyricText()
                val begin = element.attr("begin").parseTtmlTime()
                if (value.isNotBlank() && begin != null) {
                    result += LyricWord(
                        text = value,
                        startMs = begin,
                        endMs = element.attr("end").parseTtmlTime()
                            ?: fallbackEnd
                            ?: begin + estimateDuration(value)
                    )
                }
            }
            element.childrenElements().forEach(::visit)
        }
        visit(this)
        return result
    }

    private fun Element.parseTtmlBackground(fallbackEnd: Long?, fallbackTranslation: String?): TtmlBackground {
        val words = mutableListOf<LyricWord>()
        val translation = childrenElements()
            .firstOrNull { it.hasRole("x-translation") }
            ?.textContent
            ?.cleanLyricSecondaryText()
            ?.takeUsefulSecondaryText()
            ?: fallbackTranslation?.splitAppleTranslation()?.second
        val text = collectTtmlMainText(this, words, fallbackEnd)
            .removeBackgroundParentheses()
            .cleanLyricText()
        val cleanedWords = words
            .map { it.copy(text = it.text.removeBackgroundParentheses()) }
            .filter { it.text.isNotBlank() }
        // If the collected text has no spaces but we have multiple words, the spans were
        // likely adjacent without inter-span whitespace. Rebuild the display text by
        // joining the individual word texts with spaces so it renders correctly.
        val displayText = if (cleanedWords.size > 1 && text.isNotBlank() && !text.hasCjk() && !text.contains(' ')) {
            cleanedWords.joinToString(" ") { it.text.cleanLyricText() }.cleanLyricText()
        } else {
            text
        }
        val bgStart = attr("begin").parseTtmlTime() ?: cleanedWords.minOfOrNull { it.startMs }
        val bgEnd = attr("end").parseTtmlTime() ?: cleanedWords.maxOfOrNull { it.endMs } ?: fallbackEnd
        // When x-bg has no inner timed spans but has overall begin/end timing,
        // create estimated per-word timing so x-bg animates per-word like v1/v2.
        val effectiveWords = if (cleanedWords.isEmpty() && displayText.isNotBlank() && bgStart != null && bgEnd != null) {
            displayText.estimateTtmlBackgroundWords(bgStart, bgEnd)
        } else {
            cleanedWords
        }
        return TtmlBackground(
            text = displayText,
            words = effectiveWords,
            translation = translation,
            startMs = bgStart,
            endMs = bgEnd
        )
    }

    private fun String.estimateTtmlBackgroundWords(startMs: Long, endMs: Long): List<LyricWord> {
        val cleaned = cleanLyricText()
        if (cleaned.isBlank()) return emptyList()
        val duration = (endMs - startMs).coerceAtLeast(cleaned.length * 120L)
        // For CJK text, split per character; for Latin text, split per word
        val segments = if (cleaned.hasCjk()) {
            cleaned.chunked(1)
        } else {
            Regex("""\S+\s*""").findAll(cleaned).map { it.value }.toList()
        }
        if (segments.isEmpty()) return emptyList()
        val totalWeight = segments.sumOf { estimateWordWeight(it) }.coerceAtLeast(1.0)
        var cursorMs = startMs
        return segments.mapNotNull { segment ->
            val weight = estimateWordWeight(segment)
            val segDuration = (duration * weight / totalWeight).toLong().coerceAtLeast(120L)
            val segStart = cursorMs
            val segEnd = cursorMs + segDuration
            cursorMs = segEnd
            val displayText = segment.trim()
            if (displayText.isNotBlank()) {
                LyricWord(text = segment, startMs = segStart, endMs = segEnd)
            } else null
        }.filter { it.text.isNotBlank() }
    }

    private fun estimateWordWeight(text: String): Double =
        text.cleanLyricText().let { cleaned ->
            if (cleaned.hasCjk()) cleaned.length.toDouble()
            else cleaned.split(Regex("""\s+""")).filter { it.isNotBlank() }.size.toDouble().coerceAtLeast(1.0)
        }

    private fun String.leadingLrcTimeMatches(): List<MatchResult> {
        val result = mutableListOf<MatchResult>()
        var cursor = 0
        lrcTimePattern.findAll(this).forEach { match ->
            if (substring(cursor, match.range.first).isNotBlank()) return result
            result += match
            cursor = match.range.last + 1
        }
        return result
    }

    private fun mergeCompanionLines(lines: List<LyricLine>): List<LyricLine> {
        val merged = lines
            .sortedBy { it.timeMs }
            .groupBy { it.timeMs }
            .values
            .flatMap { group ->
                if (group.size == 1) return@flatMap listOf(group.first())
                if (group.shouldKeepIndependentDuetLines()) {
                    return@flatMap group.sortedBy { it.agentSortOrder() }
                }
                val hasRomanizedCompanion = group.size >= 3 && group.any { it.text.isPronunciationLine() }
                val primary = if (hasRomanizedCompanion) {
                    group.firstOrNull { it.text.cleanLyricText().hasCjk() && it.text.isUsefulMainText() }
                } else {
                    val cjkCandidates = group.filter { it.text.cleanLyricText().hasCjk() && it.text.isUsefulMainText() }
                    if (cjkCandidates.size >= 2) {
                        cjkCandidates.firstOrNull {
                            val t = it.text.cleanLyricText()
                            t.hasJapaneseKana() && !t.isLyricCreditLine()
                        } ?: cjkCandidates.firstOrNull { it.text.cleanLyricText().hasJapaneseKana() }
                    } else null
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
                val preferredTranslation = translationCandidates
                    .firstOrNull { primaryText.hasCjk() && it.hasCjk() }
                    ?: translationCandidates.firstOrNull()
                val translation = (listOfNotNull(preferredTranslation) + translationCandidates)
                    .distinct()
                    .joinToString("\n")
                    .takeIf { it.isNotBlank() }
                listOf(
                    primary.copy(
                        translation = primary.translation.mergeLyricCompanionText(translation),
                        pronunciation = primary.pronunciation ?: pronunciation?.text?.cleanLyricText(),
                        pronunciationWords = primary.pronunciationWords.ifEmpty { pronunciation?.words.orEmpty() },
                        endMs = primary.endMs ?: group.mapNotNull { it.endMs }.maxOrNull()
                    )
                )
            }
        val timeMerged = attachBackgroundLines(merged)
        return mergeNearbyCompanionLines(timeMerged)
    }

    private fun mergeNearbyCompanionLines(lines: List<LyricLine>): List<LyricLine> {
        if (lines.size < 2) return lines
        val result = mutableListOf<LyricLine>()
        var i = 0
        while (i < lines.size) {
            val current = lines[i]
            if (i + 1 < lines.size) {
                val next = lines[i + 1]
                val timeGap = next.timeMs - current.timeMs
                if (timeGap in 0..500 && next.endMs != null && next.endMs!! >= current.timeMs) {
                    val currentText = current.text.cleanLyricText()
                    val nextText = next.text.cleanLyricText()
                    val nextIsCjk = nextText.hasCjk()
                    val currentIsCjk = currentText.hasCjk()
                    val currentIsCredit = currentText.isLyricCreditLine()
                    val nextIsCredit = nextText.isLyricCreditLine()
                    // Credit lines only merge with other credit lines (e.g. "Lyrics by:" + "Composed by:")
                    // Non-credit English lines should pair with CJK translations, not other English lines
                    if (currentIsCredit && nextIsCredit) {
                        val mergedTranslation = listOfNotNull(current.translation, nextText)
                            .filter { it.isNotBlank() }
                            .distinct()
                            .joinToString("\n")
                            .takeIf { it.isNotBlank() }
                        result.add(current.copy(
                            translation = current.translation.mergeLyricCompanionText(mergedTranslation),
                            endMs = current.endMs ?: next.endMs
                        ))
                        i += 2
                        continue
                    }
                    // Pair non-CJK (English) with CJK (translation): e.g. "I'm at a payphone" + "我在电话亭里"
                    // But only if neither is a credit line
                    if (!currentIsCredit && !nextIsCredit && !currentIsCjk && nextIsCjk
                        && !currentText.contains(nextText) && !nextText.contains(currentText)) {
                        val mergedTranslation = listOfNotNull(current.translation, nextText)
                            .filter { it.isNotBlank() }
                            .distinct()
                            .joinToString("\n")
                            .takeIf { it.isNotBlank() }
                        result.add(current.copy(
                            translation = current.translation.mergeLyricCompanionText(mergedTranslation),
                            endMs = current.endMs ?: next.endMs
                        ))
                        i += 2
                        continue
                    }
                }
            }
            result.add(current)
            i++
        }
        return result
    }

    private fun List<LyricLine>.shouldKeepIndependentDuetLines(): Boolean =
        mapNotNull { line ->
            line.agent
                ?.trim()
                ?.takeIf { it.isNotBlank() && line.text.isUsefulMainText() }
        }.distinct().size >= 2

    private fun LyricLine.agentSortOrder(): Int =
        when (agent?.trim()?.lowercase()) {
            "v1" -> 0
            "v2" -> 1
            else -> 2
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
                        backgroundStartMs = target.backgroundStartMs ?: line.backgroundStartMs ?: line.timeMs,
                        backgroundEndMs = target.backgroundEndMs ?: line.backgroundEndMs ?: line.endMs,
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
        val translation: String?,
        val startMs: Long?,
        val endMs: Long?
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
        // Move trailing whitespace (spaces) from inside spans to between spans,
        // and normalize inter-span whitespace (including XML indentation with newlines)
        // to a single space. cleanLyricText() removes unwanted CJK-CJK spaces later.
        replace(Regex("""[ \t]+</span>\s*<span"""), "</span> <span")
            .replace(Regex("""</span>\s+<span"""), "</span> <span")
            .replace(",</span><span", ",</span> <span")

    private fun String.cleanLyricText(): String =
        decodeHtmlCompat()
            .replace(Regex("""[ \t\r\n]+"""), " ")
            .replace(Regex("""(?<=[\u4e00-\u9fff\u3040-\u30ff\uac00-\ud7af]) (?=[\u4e00-\u9fff\u3040-\u30ff\uac00-\ud7af])"""), "")
            .trim()

    private fun String.cleanLyricSecondaryText(): String =
        decodeHtmlCompat()
            .replace(Regex("""[ \t\r\n]+"""), " ")
            .trim()

    fun isPlaceholderOnlyLine(line: String): Boolean =
        lrcTimePattern.replace(line.cleanLyricText(), "")
            .replace(Regex("""\s+"""), "")
            .let { it == "//" || it == "／／" }

    fun isIgnorableRawLyricLine(line: String): Boolean {
        val trimmed = line.trim()
        if (trimmed.isBlank()) return true
        if (lrcGenericMetaPattern.matches(trimmed)) return true
        val withoutTimes = lrcTimePattern.replace(trimmed, "").trim()
        if (isPlaceholderOnlyLine(trimmed)) return true
        return withoutTimes != trimmed && (
            withoutTimes.isBlank() ||
                isPlaceholderOnlyLine(withoutTimes)
            )
    }

    fun isHeaderTagLine(line: String): Boolean {
        val trimmed = line.trim()
        val withoutTimes = lrcTimePattern.replace(trimmed, "").trim()
        val content = withoutTimes.takeIf { it.isNotBlank() } ?: trimmed
        return content.startsWith("[kana:", ignoreCase = true) ||
            content.startsWith("[trans:", ignoreCase = true) ||
            content.startsWith("[roma:", ignoreCase = true)
    }

    private fun String.cleanTimedLyricText(): String =
        replace(timedWordMarkerPattern) { match ->
            val time = match.groupValues.getOrNull(1).orEmpty()
                .ifBlank { match.groupValues.getOrNull(2).orEmpty() }
                .trim()
            if (time.isTimestampLike()) "" else match.value
        }.cleanLyricText()

    private fun String.cleanTimedLyricSegment(): String =
        replace(timedWordMarkerPattern) { match ->
                val time = match.groupValues.getOrNull(1).orEmpty()
                    .ifBlank { match.groupValues.getOrNull(2).orEmpty() }
                    .trim()
                if (time.isTimestampLike()) "" else match.value
            }
            .decodeHtmlCompat()
            .replace(Regex("""[ \t\r\n]+"""), " ")

    private fun String.takeUsefulText(): String? =
        cleanLyricText().takeIf { !it.isIgnorableLyricText() }

    private fun String.takeUsefulSecondaryText(): String? =
        cleanLyricSecondaryText().takeIf { !it.isIgnorableLyricText() }

    private fun String.isIgnorableLyricText(): Boolean =
        isBlank() || isMusicSymbolOnly() || isPlaceholderOnlyLine(this) || lrcGenericMetaPattern.matches(cleanLyricText())

    private fun String.splitAppleTranslation(): Pair<String?, String?> {
        val text = cleanLyricSecondaryText()
        if (!text.endsWith('）')) return text.takeUsefulSecondaryText() to null
        val start = text.lastIndexOf('（')
        if (start < 0) return text.takeUsefulSecondaryText() to null
        val main = text.substring(0, start).takeUsefulSecondaryText()
        val bg = text.substring(start + 1, text.length - 1).takeUsefulSecondaryText()
        return main to bg
    }

    private fun String.removeBackgroundParentheses(): String =
        cleanLyricText()
            .replace(Regex("""^[（(]+\s*"""), "")
            .replace(Regex("""\s*[）)]+$"""), "")
            .replace(Regex("""(?<=\s)[（(]+"""), "")
            .replace(Regex("""[）)]+(?=\s|$)"""), "")
            .cleanLyricText()

    private fun String.withoutFormattingWhitespace(): String =
        if (isBlank()) {
            // Preserve at least one space from whitespace-only text nodes that contain
            // regular space characters — these may represent inter-word gaps between spans.
            // Pure newlines/tabs (no spaces) are formatting indentation only.
            if (any { it == ' ' || it == '\u00A0' }) " " else ""
        } else {
            this
        }

    private fun String.decodeHtmlCompat(): String =
        runCatching { Html.fromHtml(this, Html.FROM_HTML_MODE_LEGACY).toString() }
            .getOrElse { this }

    private fun List<LyricWord>.joinLyricText(): String {
        val raw = joinToString("") { it.text }.cleanLyricText()
        if (raw.isBlank() || raw.hasCjk() || raw.contains(' ')) return raw
        return map { it.text.cleanLyricText() }.filter { it.isNotBlank() }.joinToString(" ")
    }

    private fun List<LyricWord>.toDisplayWords(lineText: String): List<LyricWord> {
        if (isEmpty() || lineText.isBlank()) return this
        val normalized = lineText.cleanLyricText()
        if (normalized.hasCjk()) return withSpacing(normalized)
        // If the line text has no spaces but we have multiple words, the text was likely
        // concatenated from TTML spans without inter-span whitespace (e.g. x-bg spans that
        // are directly adjacent). Don't try token matching — it would collapse all words
        // into a single blob. Return the individual words directly; they already have
        // proper per-word text and timing.
        if (!normalized.contains(' ') && size > 1) return this
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

    private fun List<LyricWord>.toTtmlDisplayWords(
        lineText: String,
        lineStart: Long,
        lineEnd: Long?,
        forceLineTiming: Boolean
    ): List<LyricWord> {
        if (lineText.isBlank()) return emptyList()
        if (forceLineTiming) return emptyList()
        val displayWords = toDisplayWords(lineText)
        val onlyWord = displayWords.singleOrNull() ?: return displayWords
        val sameText = onlyWord.text.cleanLyricText() == lineText.cleanLyricText()
        val sameStart = abs(onlyWord.startMs - lineStart) <= 25L
        val sameEnd = lineEnd == null || abs(onlyWord.endMs - lineEnd) <= 25L
        return if (sameText && sameStart && sameEnd) emptyList() else displayWords
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

    private fun String.hasJapaneseKana(): Boolean =
        any {
            val block = Character.UnicodeBlock.of(it)
            block == Character.UnicodeBlock.HIRAGANA || block == Character.UnicodeBlock.KATAKANA
        }

    private val creditPrefixPattern = Regex(
        "^(作词|作曲|编曲|原唱|翻唱|制作|演唱|录音|混音|监制|企划|出品|填词|歌手|歌|曲|词|Lyrics|Music|Arrangement|Compose[rd]?|Vocal|Mix|Produce[rd]?)[：:]",
        RegexOption.IGNORE_CASE
    )

    private fun String.isLyricCreditLine(): Boolean =
        creditPrefixPattern.containsMatchIn(trim())

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

    private fun Element.displayName(): String? {
        val attrName = attr("name")
            .ifBlank { attr("ttm:name") }
            .ifBlank { attr("xml:name") }
        val textName = textContent
            .orEmpty()
            .replace(Regex("""[ \t\r\n]+"""), " ")
            .trim()
        return attrName
            .ifBlank { textName }
            .takeIf { it.isNotBlank() && !it.isMusicSymbolOnly() }
    }

    private fun Element.hasRole(role: String): Boolean =
        attr("role") == role || attr("ttm:role") == role

    private fun Element.hasAnyRole(vararg roles: String): Boolean =
        roles.any { hasRole(it) }

    private fun Element.rubyMode(): String =
        attr("tts:ruby").ifBlank { attr("ruby") }

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
