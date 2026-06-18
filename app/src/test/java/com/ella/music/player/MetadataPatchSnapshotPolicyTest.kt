package com.ella.music.player

import com.ella.music.data.model.Song
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MetadataPatchSnapshotPolicyTest {
    @Test
    fun metadataPatchForCurrentSongIsDisplayOnly() {
        val song = song(id = 1L, path = "/music/current.flac")

        assertTrue(
            isDisplayOnlyMetadataPatchSnapshot(
                isMetadataOnlyPatch = true,
                snapshotSong = song.copy(title = "Lyric display title"),
                currentSong = song
            )
        )
    }

    @Test
    fun metadataPatchForDifferentSongStillUpdatesCurrentSong() {
        assertFalse(
            isDisplayOnlyMetadataPatchSnapshot(
                isMetadataOnlyPatch = true,
                snapshotSong = song(id = 2L, path = "/music/next.flac"),
                currentSong = song(id = 1L, path = "/music/current.flac")
            )
        )
    }

    @Test
    fun unmarkedSnapshotStillUsesNormalSongRefreshPath() {
        val song = song(id = 1L, path = "/music/current.flac")

        assertFalse(
            isDisplayOnlyMetadataPatchSnapshot(
                isMetadataOnlyPatch = false,
                snapshotSong = song,
                currentSong = song
            )
        )
    }

    private fun song(id: Long, path: String): Song = Song(
        id = id,
        title = "Song $id",
        artist = "Artist",
        album = "Album",
        albumId = 1L,
        duration = 180_000L,
        path = path,
        fileName = path.substringAfterLast('/')
    )
}
