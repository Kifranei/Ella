package com.ella.music.player

import org.junit.Assert.assertEquals
import org.junit.Test

class DesktopStatusBarLyricPolicyTest {
    @Test
    fun mergedSecondaryUsesExactlyOneSpace() {
        assertEquals(
            "Original Translation",
            mergeDesktopStatusBarLyric("Original ", " Translation", mergeSecondary = true)
        )
    }

    @Test
    fun disabledMergeLeavesMainTextUntouched() {
        assertEquals(
            "Original ",
            mergeDesktopStatusBarLyric("Original ", " Translation", mergeSecondary = false)
        )
    }
}
