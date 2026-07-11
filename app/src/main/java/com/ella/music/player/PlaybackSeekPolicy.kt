package com.ella.music.player

/**
 * Resolves a user-selected playback position without making the end of the seek bar unreachable.
 * Media3 accepts the exact duration as an end-of-item seek, so a 100% tap or drag must not be
 * silently moved back to the last millisecond.
 */
internal fun playbackSeekTarget(positionMs: Long, durationMs: Long): Long =
    if (durationMs > 0L) {
        positionMs.coerceIn(0L, durationMs)
    } else {
        positionMs.coerceAtLeast(0L)
    }
