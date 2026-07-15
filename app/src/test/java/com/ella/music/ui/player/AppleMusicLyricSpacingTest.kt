package com.ella.music.ui.player

import com.ella.music.data.model.LyricWord
import org.junit.Assert.assertEquals
import org.junit.Test

class AppleMusicLyricSpacingTest {
    @Test
    fun leadingWordSpacesMoveToPreviousWordForFlushWrappedRows() {
        val words = listOf(
            LyricWord("It's", 0L, 400L),
            LyricWord(" been", 400L, 800L),
            LyricWord(" a", 800L, 1_000L),
            LyricWord(" long", 1_000L, 1_500L)
        )

        assertEquals(
            listOf("It's ", "been ", "a ", "long"),
            words.moveLeadingSpacesToPreviousWord().map { it.text }
        )
    }
}
