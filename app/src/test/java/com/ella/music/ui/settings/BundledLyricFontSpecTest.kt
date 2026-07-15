package com.ella.music.ui.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BundledLyricFontSpecTest {
    @Test
    fun interIsAvailableAsABundledLyricFont() {
        val inter = BUNDLED_FONT_SPECS.single { it.displayName == "Inter Bold" }

        assertEquals("Inter-Bold.ttf", inter.fileName)
        assertEquals("fonts/Inter-Bold.ttf", inter.assetPath)
    }

    @Test
    fun bundledFontDestinationsAreUnique() {
        assertEquals(BUNDLED_FONT_SPECS.size, BUNDLED_FONT_SPECS.map { it.fileName }.distinct().size)
        assertTrue(BUNDLED_FONT_SPECS.all { it.assetPath.startsWith("fonts/") })
    }
}
