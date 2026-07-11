package com.ella.music.ui.player

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlayerDynamicCoverNamingTest {
    @Test
    fun landscapeMusicVideoCandidatesIncludeUnderscoreAndHyphenSuffixes() {
        val candidates = buildLandscapeMusicVideoNameCandidates(listOf("Baby", "Taylor Swift - Lover"))

        assertTrue("Baby_MV" in candidates)
        assertTrue("Baby-MV" in candidates)
        assertTrue("Taylor Swift - Lover_MV" in candidates)
        assertTrue("Taylor Swift - Lover-MV" in candidates)
    }

    @Test
    fun detectsLandscapeMusicVideoFileNameForSongCandidates() {
        val songCandidates = listOf("Baby", "Justin Bieber - Baby")

        assertTrue(isLandscapeMusicVideoFileName("Baby_MV", songCandidates))
        assertTrue(isLandscapeMusicVideoFileName("Baby-MV", songCandidates))
        assertTrue(isLandscapeMusicVideoFileName("Justin Bieber - Baby_MV", songCandidates))
    }

    @Test
    fun ignoresRegularDynamicCoverNames() {
        val songCandidates = listOf("Baby", "Justin Bieber - Baby")

        assertFalse(isLandscapeMusicVideoFileName("Baby", songCandidates))
        assertFalse(isLandscapeMusicVideoFileName("cover", songCandidates))
        assertFalse(isLandscapeMusicVideoFileName("Album-MV", songCandidates))
    }

    @Test
    fun ambientAndMusicVideoCandidateListsAreIndependent() {
        val songCandidates = listOf("Baby", "Justin Bieber - Baby")

        val ambient = playerVideoNameCandidates(songCandidates, musicVideoOnly = false)
        val musicVideos = playerVideoNameCandidates(songCandidates, musicVideoOnly = true)

        assertTrue("Baby" in ambient)
        assertFalse(ambient.any { it.endsWith("MV") })
        assertTrue("Baby_MV" in musicVideos)
        assertTrue("Baby-MV" in musicVideos)
        assertFalse("Baby" in musicVideos)
    }
}
