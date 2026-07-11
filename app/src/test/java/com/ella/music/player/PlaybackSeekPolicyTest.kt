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
}
