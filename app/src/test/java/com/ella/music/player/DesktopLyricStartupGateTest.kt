package com.ella.music.player

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DesktopLyricStartupGateTest {
    @Test
    fun showBeforeSettingsLoadIsDeferredAndReplayedOnce() {
        val gate = DesktopLyricStartupGate()

        assertTrue(gate.shouldDeferShow())
        assertTrue(gate.markSettingsLoaded())
        assertFalse(gate.markSettingsLoaded())
    }

    @Test
    fun showAfterSettingsLoadIsHandledImmediately() {
        val gate = DesktopLyricStartupGate()

        assertFalse(gate.markSettingsLoaded())
        assertFalse(gate.shouldDeferShow())
    }

    @Test
    fun clearingPendingShowPreventsReplayAfterSettingsLoad() {
        val gate = DesktopLyricStartupGate()

        assertTrue(gate.shouldDeferShow())
        gate.clearPendingShow()

        assertFalse(gate.markSettingsLoaded())
    }
}
