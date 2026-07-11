package com.ella.music.player

import org.junit.Assert.assertEquals
import org.junit.Test

class PlaybackSeekPolicyTest {
    @Test
    fun exactEndRemainsReachable() {
        assertEquals(180_000L, playbackSeekTarget(180_000L, 180_000L))
    }

    @Test
    fun positionPastEndIsClampedToDuration() {
        assertEquals(180_000L, playbackSeekTarget(200_000L, 180_000L))
    }

    @Test
    fun unknownDurationKeepsNonNegativePosition() {
        assertEquals(42_000L, playbackSeekTarget(42_000L, -1L))
        assertEquals(0L, playbackSeekTarget(-1L, -1L))
    }

    @Test
    fun progressUsesLivePlayerDurationInsteadOfStaleUiDuration() {
        assertEquals(
            240_000L,
            playbackSeekTargetForProgress(1f, playerDurationMs = 240_000L, fallbackDurationMs = 180_000L)
        )
    }

    @Test
    fun progressFallsBackWhenPlayerDurationIsNotReady() {
        assertEquals(
            90_000L,
            playbackSeekTargetForProgress(0.5f, playerDurationMs = -1L, fallbackDurationMs = 180_000L)
        )
    }

    @Test
    fun progressRoundsInsteadOfTruncating() {
        assertEquals(2L, playbackSeekTargetForProgress(0.5f, playerDurationMs = 3L, fallbackDurationMs = 0L))
    }
}
