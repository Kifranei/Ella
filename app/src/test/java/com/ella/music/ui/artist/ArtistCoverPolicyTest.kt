package com.ella.music.ui.artist

import com.ella.music.data.NameSplitConfigStore
import com.ella.music.data.model.Song
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class ArtistCoverPolicyTest {
    @Before
    fun setUp() {
        NameSplitConfigStore.artistCustomSeparators = listOf("/")
        NameSplitConfigStore.artistProtectedNames = emptyList()
        NameSplitConfigStore.tagIgnoreCase = false
    }

    @After
    fun tearDown() {
        NameSplitConfigStore.artistCustomSeparators = emptyList()
        NameSplitConfigStore.artistProtectedNames = emptyList()
        NameSplitConfigStore.tagIgnoreCase = false
    }

    @Test
    fun soleAlbumArtistWinsOverEverySongArtistCandidate() {
        val collaboration = song(id = 1, artist = "Target / Guest")
        val soleSongArtist = song(id = 2, artist = "Target")
        val soleAlbumArtist = song(id = 3, artist = "Singer", albumArtist = "Target")

        assertEquals(
            soleAlbumArtist,
            selectArtistCoverSong(listOf(collaboration, soleSongArtist, soleAlbumArtist), "Target")
        )
    }

    @Test
    fun soleSongArtistWinsOverCollaboration() {
        val collaboration = song(id = 1, artist = "Target / Guest")
        val soleSongArtist = song(id = 2, artist = "Target")

        assertEquals(
            soleSongArtist,
            selectArtistCoverSong(listOf(collaboration, soleSongArtist), "Target")
        )
    }

    @Test
    fun collaborativeAlbumArtistWinsOverCollaborativeSongArtist() {
        val songCollaboration = song(id = 1, artist = "Target / Guest")
        val albumCollaboration = song(id = 2, artist = "Singer", albumArtist = "Target / Guest")

        assertEquals(
            albumCollaboration,
            selectArtistCoverSong(listOf(songCollaboration, albumCollaboration), "Target")
        )
    }

    @Test
    fun collaborativeSongArtistRemainsTheLastFallback() {
        val collaboration = song(id = 1, artist = "Target / Guest")

        assertEquals(collaboration, selectArtistCoverSong(listOf(collaboration), "Target"))
    }

    private fun song(
        id: Long,
        artist: String,
        albumArtist: String = ""
    ) = Song(
        id = id,
        title = "Song $id",
        artist = artist,
        album = "Album $id",
        albumId = id,
        duration = 1L,
        path = "/music/$id.mp3",
        fileName = "$id.mp3",
        albumArtist = albumArtist
    )
}
