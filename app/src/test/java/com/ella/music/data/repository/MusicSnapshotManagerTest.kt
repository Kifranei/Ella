package com.ella.music.data.repository

import com.ella.music.data.model.Song
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Test

class MusicSnapshotManagerTest {
    @Test
    fun ratingSurvivesMtimeChangeWhenPathAndSizeMatch() {
        val manager = snapshotManager()
        val song = song(path = "/music/song.flac", dateModified = 1_000L, fileSize = 200L)

        manager.updateRatingSnapshot(song, 5)

        assertEquals(5, manager.getSongRating(song.copy(dateModified = 2_000L)))
    }

    @Test
    fun trustedLocalWriteSurvivesFileSizeChange() {
        val manager = snapshotManager()
        val song = song(path = "/music/song.flac", dateModified = 1_000L, fileSize = 200L)

        manager.updateRatingSnapshot(song, 4, trustedLocalWrite = true)

        assertEquals(4, manager.getSongRating(song.copy(dateModified = 2_000L, fileSize = 260L)))
    }

    @Test
    fun ratingMigratesWhenMediaStoreIdChangesForSamePath() {
        val manager = snapshotManager()
        val original = song(id = 1L, path = "/music/song.flac", dateModified = 1_000L, fileSize = 200L)
        val restored = original.copy(id = 99L)

        manager.updateRatingSnapshot(original, 5)

        assertEquals(5, manager.getSongRating(restored))
    }

    @Test
    fun untrustedSnapshotDoesNotSurviveFileSizeChange() {
        val manager = snapshotManager()
        val song = song(path = "/music/song.flac", dateModified = 1_000L, fileSize = 200L)

        manager.updateRatingSnapshot(song, 4, trustedLocalWrite = false)

        assertEquals(0, manager.getSongRating(song.copy(dateModified = 2_000L, fileSize = 260L)))
    }

    @Test
    fun updateRatingSnapshotStoresRefreshedFileMetadata() {
        val manager = snapshotManager()
        val refreshed = song(path = "/music/song.flac", dateModified = 9_000L, fileSize = 777L)

        manager.updateRatingSnapshot(refreshed, 5)

        val entry = manager.ratingSnapshotEntry(refreshed)
        assertEquals(9_000L, entry?.dateModified)
        assertEquals(777L, entry?.fileSize)
        assertEquals("/music/song.flac", entry?.path)
    }

    @Test
    fun clearMetadataCacheOnlyClearsExactSongKey() {
        val manager = snapshotManager()
        val first = song(id = 1L, path = "/music/one.flac")
        val second = song(id = 10L, path = "/music/ten.flac")
        manager.updateRatingSnapshot(first, 5)
        manager.updateRatingSnapshot(second, 4)

        manager.clearMetadataCache(first)

        assertEquals(0, manager.getSongRating(first))
        assertEquals(4, manager.getSongRating(second))
    }

    @Test
    fun clearMissingFileSnapshotsRemovesDeletedPathOnly() {
        val manager = snapshotManager()
        val deleted = song(id = 1L, path = "/music/deleted.flac")
        val existing = song(id = 2L, path = "/music/existing.flac")
        manager.updateRatingSnapshot(deleted, 5)
        manager.updateRatingSnapshot(existing, 4)

        val removed = manager.clearMissingFileSnapshots(setOf(existing.path))

        assertEquals(1, removed)
        assertEquals(0, manager.getSongRating(deleted))
        assertEquals(4, manager.getSongRating(existing))
    }

    private fun snapshotManager(
        searchFile: File = tempFile("search"),
        ratingFile: File = tempFile("rating")
    ): MusicSnapshotManager = MusicSnapshotManager(
        librarySearchSnapshotFile = searchFile,
        libraryRatingSnapshotFile = ratingFile,
        searchTextBuilder = { it.title.lowercase() }
    )

    private fun tempFile(prefix: String): File =
        kotlin.io.path.createTempFile(prefix = prefix, suffix = ".json").toFile().apply {
            deleteOnExit()
            delete()
        }

    private fun song(
        id: Long = 1L,
        path: String,
        dateModified: Long = 1_000L,
        fileSize: Long = 200L
    ): Song = Song(
        id = id,
        title = "Song $id",
        artist = "Artist",
        album = "Album",
        albumId = 1L,
        duration = 180_000L,
        path = path,
        fileName = path.substringAfterLast('/'),
        fileSize = fileSize,
        dateModified = dateModified
    )
}
