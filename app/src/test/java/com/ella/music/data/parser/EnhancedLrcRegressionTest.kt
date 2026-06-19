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
}
