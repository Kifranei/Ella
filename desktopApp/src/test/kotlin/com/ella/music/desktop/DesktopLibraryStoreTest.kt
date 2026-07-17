package com.ella.music.desktop

import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals

class DesktopLibraryStoreTest {
    @Test
    fun `persists library roots songs and playlists`() {
        val directory = Files.createTempDirectory("halcyon-library")
        val stateFile = directory.resolve("library.json")
        try {
            val expected = DesktopLibraryState(
                libraryRoots = listOf("/music"),
                songs = listOf(DesktopSong(id = "song", path = "/music/song.flac", title = "Song")),
                playlists = listOf(DesktopPlaylist(id = "playlist", name = "Favorites", songIds = listOf("song"))),
                floatingLyricsEnabled = true,
                shuffleEnabled = true,
                repeatMode = DesktopRepeatMode.ONE,
                playbackVolume = 0.65f
            )

            DesktopLibraryStore(stateFile).save(expected)

            assertEquals(expected, DesktopLibraryStore(stateFile).load())
        } finally {
            Files.deleteIfExists(stateFile)
            Files.deleteIfExists(directory)
        }
    }

    @Test
    fun `cycles repeat mode through all supported choices`() {
        assertEquals(DesktopRepeatMode.ALL, DesktopRepeatMode.OFF.next())
        assertEquals(DesktopRepeatMode.ONE, DesktopRepeatMode.ALL.next())
        assertEquals(DesktopRepeatMode.OFF, DesktopRepeatMode.ONE.next())
    }
}
