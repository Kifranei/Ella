package com.ella.music.data.repository

import com.ella.music.data.metadata.AudioTagInfo
import org.junit.Assert.assertEquals
import org.junit.Test

class MusicLyricsSelectionTest {
    @Test
    fun richerLyricsTagWinsOverUnsyncedLyricsTag() {
        val rich = "[00:01.000]Hello\n[00:01.000]你好"
        val plain = "[00:01.00]Hello"
        val tags = AudioTagInfo(
            lyrics = plain,
            customTags = linkedMapOf(
                "UNSYNCEDLYRICS" to listOf(plain),
                "LYRICS" to listOf(rich)
            )
        )

        assertEquals(rich, tags.embeddedLyricsContent(preferTtml = false))
    }

    @Test
    fun richerLyricsTagAlsoWinsForTtml() {
        val rich = "<tt><body><p begin=\"1s\">Hello</p><p begin=\"1s\">你好</p></body></tt>"
        val plain = "<tt><body><p begin=\"1s\">Hello</p></body></tt>"
        val tags = AudioTagInfo(
            customTags = linkedMapOf(
                "UNSYNCEDLYRICS" to listOf(plain),
                "LYRICS" to listOf(rich)
            )
        )

        assertEquals(rich, tags.embeddedLyricsContent(preferTtml = true))
    }
}
