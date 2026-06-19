package com.ella.music.data.parser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class EnhancedLrcRegressionTest {
    @Test
    fun squareBracketWordTimingKeepsFirstWordAndPairsTranslationAsPlainLyrics() {
        val result = LrcParser.parse(
            """
            [00:37.133]I'm [00:37.397]walking [00:37.845]fast[00:38.333]
            [00:37.133]快步穿梭
            """.trimIndent()
        )

        assertEquals(1, result.lyrics.size)
        val line = result.lyrics.single()
        assertEquals(37_133L, line.timeMs)
        assertEquals("I'm walking fast", line.text)
        assertEquals("快步穿梭", line.translation)
        assertEquals(listOf("I'm ", "walking ", "fast"), line.words.map { it.text })
        assertFalse(line.isTtml)
    }

    @Test
    fun leadingTextKeepsRelativeInlineWordTimingRelativeToLineStart() {
        val result = LrcParser.parse(
            "[00:10.000]Lead <00:00.500>word<00:01.000>end"
        )

        val line = result.lyrics.single()
        assertEquals(10_000L, line.timeMs)
        assertEquals("Lead wordend", line.text)
        assertEquals(listOf("Lead ", "word", "end"), line.words.map { it.text })
        assertEquals(listOf(10_000L, 10_500L, 11_000L), line.words.map { it.startMs })
    }
}
