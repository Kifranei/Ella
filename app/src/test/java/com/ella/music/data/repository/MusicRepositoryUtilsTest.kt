package com.ella.music.data.repository

import com.ella.music.data.model.Song
import org.junit.Assert.assertEquals
import org.junit.Test

class MusicRepositoryUtilsTest {
    @Test
    fun ac4AjocM4aIsNotMisidentifiedAsAac() {
        val song = song(
            fileName = "07. The Chain (Dolby Atmos AC-4 A-JOC).m4a",
            album = "Rumours (Dolby Atmos AC-4 A-JOC)"
        )

        assertEquals(
            "AC4 A-JOC",
            song.audioFormatLabel(
                mime = "audio/ac4",
                bitRate = 448_000,
                sampleRate = 48_000,
                estimatedBitRate = 448_000
            )
        )
    }

    @Test
    fun ac4ImmersiveStereoM4aKeepsItsVariant() {
        val song = song(
            fileName = "07. The Chain (Dolby Atmos Immersive Stereo).m4a",
            album = "Rumours (Dolby Atmos Immersive Stereo)"
        )

        assertEquals(
            "AC4 Immersive Stereo",
            song.audioFormatLabel(
                mime = "audio/ac4",
                bitRate = 256_000,
                sampleRate = 48_000,
                estimatedBitRate = 256_000
            )
        )
    }

    @Test
    fun alacM4aDoesNotFallBackToMp3FromParentFolderName() {
        val song = Song(
            id = 8L,
            title = "Test",
            artist = "Artist",
            album = "Album",
            albumId = 1L,
            duration = 180_000L,
            path = "C:/Music/mp3-cache/Test.m4a",
            fileName = "Test.m4a",
            mimeType = "audio/mp4"
        )

        assertEquals(
            "ALAC",
            song.audioFormatLabel(
                mime = "audio/mp4a-latm",
                bitRate = 520_000,
                sampleRate = 44_100,
                bitDepth = 16,
                channels = 2,
                estimatedBitRate = 520_000
            )
        )
    }

    @Test
    fun alacM4aUsesLosslessHintsWhenMimeIsGeneric() {
        val song = song(
            fileName = "Lossless.m4a",
            album = "Album"
        )

        assertEquals(
            "ALAC",
            song.audioFormatLabel(
                mime = "audio/mp4a-latm",
                bitRate = 480_000,
                sampleRate = 44_100,
                channels = 2,
                estimatedBitRate = 480_000
            )
        )
    }

    @Test
    fun alacM4aLosslessHintsOverrideWrongMpegMime() {
        val song = song(
            fileName = "Lossless.m4a",
            album = "Album"
        )

        assertEquals(
            "ALAC",
            song.audioFormatLabel(
                mime = "audio/mpeg",
                bitRate = 520_000,
                sampleRate = 44_100,
                bitDepth = 16,
                channels = 2,
                estimatedBitRate = 520_000
            )
        )
    }

    private fun song(fileName: String, album: String): Song = Song(
        id = 7L,
        title = "The Chain",
        artist = "Fleetwood Mac",
        album = album,
        albumId = 1L,
        duration = 267_000L,
        path = "C:/$fileName",
        fileName = fileName,
        mimeType = "audio/mp4"
    )
}
