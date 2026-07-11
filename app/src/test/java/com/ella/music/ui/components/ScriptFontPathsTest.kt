package com.ella.music.ui.components

import org.junit.Assert.assertEquals
import org.junit.Test

class ScriptFontPathsTest {
    @Test
    fun roundTripsUnicodeAndSeparatorCharacters() {
        val paths = ScriptFontPaths(
            western = "/fonts/Inter.Variable.ttf",
            cjk = "/字体/思源 黑体.ttc"
        )

        assertEquals(paths, ScriptFontPaths.decode(paths.encode()))
    }

    @Test
    fun legacySinglePathIsNotMisreadAsScriptSelection() {
        assertEquals(null, ScriptFontPaths.decode("/fonts/legacy.ttf"))
    }
}
