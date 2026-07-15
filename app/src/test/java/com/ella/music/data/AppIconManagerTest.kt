package com.ella.music.data

import org.junit.Assert.assertEquals
import org.junit.Test

class AppIconManagerTest {
    @Test
    fun `launcher alias stays in source namespace after application id changes`() {
        assertEquals(
            "com.ella.music.DefaultLauncherAlias",
            AppIconManager.launcherAliasClassName(".DefaultLauncherAlias")
        )
    }
}
