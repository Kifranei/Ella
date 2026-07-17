package com.ella.music.desktop

import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.extension

/** Common LRC/ELRC reader plus a deliberately small TTML fallback for desktop sidecar lyrics. */
object DesktopLyrics {
    private val lrcTimestamp = Regex("\\[(\\d{1,3}):(\\d{2})(?:[.:](\\d{1,3}))?]")
    private val ttmlLine = Regex("<p[^>]*?(?:begin|start)=\\\"([^\\\"]+)\\\"[^>]*>(.*?)</p>", RegexOption.IGNORE_CASE)
    private val markup = Regex("<[^>]+>")

    fun load(pathString: String?): List<DesktopLyricLine> {
        val path = pathString?.let(Path::of) ?: return emptyList()
        if (!Files.isRegularFile(path)) return emptyList()
        val content = runCatching { Files.readString(path) }.getOrNull() ?: return emptyList()
        return if (path.extension.equals("ttml", ignoreCase = true)) parseTtml(content) else parseLrc(content)
    }

    fun lineAt(lines: List<DesktopLyricLine>, positionMs: Long): DesktopLyricLine? =
        lines.lastOrNull { it.timeMs <= positionMs }

    private fun parseLrc(content: String): List<DesktopLyricLine> = buildList {
        content.lineSequence().forEach { rawLine ->
            val matches = lrcTimestamp.findAll(rawLine).toList()
            if (matches.isEmpty()) return@forEach
            val text = rawLine.substring(matches.last().range.last + 1).trim()
            if (text.isBlank()) return@forEach
            val (main, translation) = text.split("|", limit = 2).let { it.first() to it.getOrNull(1) }
            matches.forEach { match ->
                val minutes = match.groupValues[1].toLongOrNull() ?: return@forEach
                val seconds = match.groupValues[2].toLongOrNull() ?: return@forEach
                val fractionText = match.groupValues[3]
                val fraction = when (fractionText.length) {
                    1 -> fractionText.toLongOrNull()?.times(100L) ?: 0L
                    2 -> fractionText.toLongOrNull()?.times(10L) ?: 0L
                    else -> fractionText.take(3).toLongOrNull() ?: 0L
                }
                add(DesktopLyricLine((minutes * 60L + seconds) * 1_000L + fraction, main.trim(), translation?.trim()))
            }
        }
    }.distinctBy { it.timeMs to it.text }.sortedBy { it.timeMs }

    private fun parseTtml(content: String): List<DesktopLyricLine> = ttmlLine.findAll(content)
        .mapNotNull { match ->
            parseTtmlTime(match.groupValues[1])?.let { time ->
                DesktopLyricLine(time, match.groupValues[2].replace(markup, "").trim())
            }
        }
        .filter { it.text.isNotBlank() }
        .sortedBy { it.timeMs }
        .toList()

    private fun parseTtmlTime(raw: String): Long? {
        val normalized = raw.trim()
        if (normalized.endsWith("ms")) return normalized.removeSuffix("ms").toDoubleOrNull()?.toLong()
        if (normalized.endsWith("s")) return normalized.removeSuffix("s").toDoubleOrNull()?.times(1_000L)?.toLong()
        val parts = normalized.split(':')
        if (parts.size !in 2..3) return null
        val seconds = parts.last().toDoubleOrNull() ?: return null
        val minutes = parts[parts.size - 2].toLongOrNull() ?: return null
        val hours = if (parts.size == 3) parts[0].toLongOrNull() ?: return null else 0L
        return hours * 3_600_000L + minutes * 60_000L + (seconds * 1_000L).toLong()
    }
}
