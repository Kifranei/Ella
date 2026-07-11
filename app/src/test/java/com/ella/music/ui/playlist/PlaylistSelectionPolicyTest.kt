package com.ella.music.ui.playlist

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaylistSelectionPolicyTest {
    @Test
    fun repeatedLongPressDoesNotDeselectSelectedPlaylist() {
        assertFalse(shouldSelectPlaylistOnLongPress(selectionMode = true, alreadySelected = true))
    }

    @Test
    fun longPressStillSelectsAnUnselectedPlaylist() {
        assertTrue(shouldSelectPlaylistOnLongPress(selectionMode = true, alreadySelected = false))
        assertTrue(shouldSelectPlaylistOnLongPress(selectionMode = false, alreadySelected = false))
    }
}
