package com.ella.music.data.parser

import org.junit.Assert.assertEquals
import org.junit.Test

class EllaLyricsParserTest {
    @Test
    fun placeholderOnlyTimedLinesAreIgnored() {
        val result = LrcParser.parse(
            """
            [00:00.539]花篝り (篝火) - 滴草由实 (しずくさ ゆみ)
            [00:00.539]//
            [00:04.097]词：滴草由実
            [00:04.097]//
            [00:05.785]曲：大野愛果
            [00:05.785]//
            """.trimIndent()
        )

        assertEquals(
            listOf("花篝り (篝火) - 滴草由实 (しずくさ ゆみ)", "词：滴草由実", "曲：大野愛果"),
            result.lyrics.map { it.text }
        )
        assertEquals(listOf(539L, 4_097L, 5_785L), result.lyrics.map { it.timeMs })
    }

    @Test
    fun translationHeaderAndPlaceholderBlockAreIgnored() {
        val result = LrcParser.parse(
            """
            [04:16.712](I need your love)
            [trans:]
            [00:00.724]//
            [00:07.960]//
            [00:10.204]//
            """.trimIndent()
        )

        assertEquals(listOf("(I need your love)"), result.lyrics.map { it.text })
        assertEquals(listOf(256_712L), result.lyrics.map { it.timeMs })
    }

    @Test
    fun untimedLinesDoNotAttachToPreviousLyricLine() {
        val result = LrcParser.parse(
            """
            [00:01.00]第一句
            [trans:]
            无时间戳翻译
            [00:03.00]第二句
            """.trimIndent()
        )

        assertEquals(listOf("第一句", "第二句"), result.lyrics.map { it.text })
        assertEquals(listOf(null, null), result.lyrics.map { it.translation })
    }

    @Test
    fun synchronizedCreditAndCopyrightLinesArePreserved() {
        val result = LrcParser.parse(
            """
            [00:01.00]QQ音乐享有本翻译作品的著作权
            [00:02.00]作词：Someone
            [00:03.00]正常歌词
            """.trimIndent()
        )

        assertEquals(
            listOf("QQ音乐享有本翻译作品的著作权", "作词：Someone", "正常歌词"),
            result.lyrics.map { it.text }
        )
        assertEquals(listOf(1_000L, 2_000L, 3_000L), result.lyrics.map { it.timeMs })
    }

    @Test
    fun sameTimestampWordLineAndBlankPronunciationAttachTranslation() {
        val result = LrcParser.parse(
            """
            [00:41.373] <00:41.373>wake <00:41.949>me <00:42.502>up <00:43.040>
            [00:41.373]
            [00:41.373]叫醒我
            """.trimIndent()
        )

        assertEquals(1, result.lyrics.size)
        assertEquals("wake me up", result.lyrics.single().text)
        assertEquals("叫醒我", result.lyrics.single().translation)
        assertEquals(listOf("wake ", "me ", "up"), result.lyrics.single().words.map { it.text })
    }

    @Test
    fun sameTimestampWordLineRomanizationAndTranslationAreMerged() {
        val result = LrcParser.parse(
            """
            [00:21.853] <00:21.853>覚<00:22.261>醒 <00:22.719>READY <00:23.379>OK <00:23.935>
            [00:21.853]ka ku se i READY OK
            [00:21.853]该觉醒了 Ready，ok？
            """.trimIndent()
        )

        assertEquals(1, result.lyrics.size)
        assertEquals("覚醒 READY OK", result.lyrics.single().text)
        assertEquals("ka ku se i READY OK", result.lyrics.single().pronunciation)
        assertEquals("该觉醒了 Ready，ok？", result.lyrics.single().translation)
    }
}
