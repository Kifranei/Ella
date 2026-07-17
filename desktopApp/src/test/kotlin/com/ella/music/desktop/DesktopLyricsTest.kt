package com.ella.music.desktop

import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class DesktopLyricsTest {
    @Test
    fun `parses multiple LRC timestamps and translations`() {
        val lyricFile = Files.createTempFile("halcyon-lyrics", ".lrc")
        try {
            Files.writeString(lyricFile, "[00:01.20][00:03.45]Hello|你好" + System.lineSeparator())

            val lines = DesktopLyrics.load(lyricFile.toString())

            assertEquals(listOf(1_200L, 3_450L), lines.map(DesktopLyricLine::timeMs))
            assertEquals("Hello", lines.first().text)
            assertEquals("你好", lines.first().translation)
            assertEquals("Hello", DesktopLyrics.lineAt(lines, 3_500L)?.text)
        } finally {
            Files.deleteIfExists(lyricFile)
        }
    }

    @Test
    fun `parses TTML clock and seconds timestamps`() {
        val lyricFile = Files.createTempFile("halcyon-lyrics", ".ttml")
        try {
            Files.writeString(
                lyricFile,
                "<tt><body><p begin=\"00:01:02.500\">First <span>line</span></p><p begin=\"3.25s\">Second</p></body></tt>"
            )

            val lines = DesktopLyrics.load(lyricFile.toString())

            assertEquals(2, lines.size)
            assertEquals(3_250L, lines.first().timeMs)
            assertEquals("Second", lines.first().text)
            assertNotNull(lines.singleOrNull { it.text == "First line" })
        } finally {
            Files.deleteIfExists(lyricFile)
        }
    }
}
