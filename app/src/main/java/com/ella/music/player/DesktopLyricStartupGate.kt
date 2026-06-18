package com.ella.music.player

internal class DesktopLyricStartupGate {
    var settingsLoaded: Boolean = false
        private set

    private var pendingShow = false

    fun shouldDeferShow(): Boolean {
        if (settingsLoaded) return false
        pendingShow = true
        return true
    }

    fun markSettingsLoaded(): Boolean {
        settingsLoaded = true
        return pendingShow.also { pendingShow = false }
    }

    fun clearPendingShow() {
        pendingShow = false
    }
}
