package com.ella.music.data

import com.ella.music.data.model.Album
import com.ella.music.data.model.Song
import com.ella.music.data.model.albumIdentityId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class LibraryAlbumAggregatorTest {
    @Test
    fun albumIdentityRespectsTagIgnoreCaseWhenAlbumArtistIsMissing() {
        val previous = NameSplitConfigStore.tagIgnoreCase
        val upper = song(id = 1, album = "CLOSER", albumArtist = "")
        val title = song(id = 2, album = "Closer", albumArtist = "")
        try {
            NameSplitConfigStore.tagIgnoreCase = false
            assertNotEquals(upper.albumIdentityId(), title.albumIdentityId())

            NameSplitConfigStore.tagIgnoreCase = true
            assertEquals(upper.albumIdentityId(), title.albumIdentityId())
        } finally {
            NameSplitConfigStore.tagIgnoreCase = previous
        }
    }

    @Test
    fun literalUnknownAlbumNameIsPreserved() {
        val albums = LibraryAlbumAggregator.toAlbums(
            listOf(
                song(id = 1, album = "unknown", albumArtist = "ReoNa"),
                song(id = 2, album = "Unknown Album", albumArtist = "Unknown Artist")
            )
        )

        assertEquals(2, albums.size)
        val byName = albums.associateBy { it.name }
        assertEquals("ReoNa", byName.getValue("unknown").artist)
        assertEquals(1, byName.getValue("unknown").songCount)
        assertEquals("", byName.getValue("Unknown Album").artist)
        assertEquals(1, byName.getValue("Unknown Album").songCount)
    }

    @Test
    fun blankAlbumFallsBackToUnknownAlbum() {
        val albums = LibraryAlbumAggregator.toAlbums(listOf(song(album = "")))

        assertEquals("Unknown Album", albums.first().name)
    }

    @Test
    fun albumDurationsAreGroupedByIdentity() {
        val firstAlbumSong = song(id = 1, album = "First Album", albumId = 21L, duration = 1000L)
        val secondAlbumSong = song(id = 3, album = "Second Album", albumId = 22L, duration = 500L)
        val durations = LibraryAlbumAggregator.durationsByAlbumIdentity(
            listOf(
                firstAlbumSong,
                song(id = 2, album = "First Album", albumId = 21L, duration = 2500L),
                secondAlbumSong
            )
        )

        assertEquals(3500L, durations[firstAlbumSong.albumIdentityId()])
        assertEquals(500L, durations[secondAlbumSong.albumIdentityId()])
    }

    @Test
    fun albumKeepsEarliestCompleteReleaseDate() {
        val albums = LibraryAlbumAggregator.toAlbums(
            listOf(
                song(id = 1, year = "2005-01-28"),
                song(id = 2, year = "2005-02-03")
            )
        )

        assertEquals("2005-01-28", albums.single().year)
    }

    @Test
    fun metadataAlbumsReuseLibraryAlbumWhenAvailable() {
        val song = song(album = "Library Album", albumArtist = "Library Artist")
        val libraryAlbum = Album(
            id = song.albumIdentityId(),
            name = "Library Album",
            artist = "Library Artist",
            songCount = 9,
            year = "2024",
            artAlbumId = 210L,
            albumArtist = "Library Artist"
        )

        val albums = LibraryAlbumAggregator.toAlbumsForSongs(
            songs = listOf(song),
            libraryAlbums = listOf(libraryAlbum)
        )

        assertEquals(libraryAlbum, albums.first())
    }

    private fun song(
        id: Long = 1,
        album: String = "Album",
        albumArtist: String = "Artist",
        year: String = "",
        albumId: Long = 11L,
        duration: Long = 1000L
    ): Song = Song(
        id = id,
        title = "Song $id",
        artist = "Artist",
        album = album,
        albumId = albumId,
        duration = duration,
        path = "/music/song$id.flac",
        fileName = "song$id.flac",
        albumArtist = albumArtist,
        year = year
    )
}
