package com.ella.music.player

import com.ella.music.data.model.Song
import kotlin.random.Random

internal data class ShuffleQueuePlan(
    val queue: List<Song>,
    val currentIndex: Int
)

internal fun buildShuffleQueueKeepingCurrent(
    sourceOrder: List<Song>,
    current: Song,
    seed: Long
): ShuffleQueuePlan? {
    if (sourceOrder.size <= 1) return null
    val currentIndex = sourceOrder.indexOfFirst { it.isSamePlaybackIdentity(current) }
        .takeIf { it >= 0 }
        ?: return null
    val currentSong = sourceOrder[currentIndex]
    val shuffled = sourceOrder
        .filterIndexed { index, _ -> index != currentIndex }
        .shuffled(Random(seed))
    return ShuffleQueuePlan(
        queue = listOf(currentSong) + shuffled,
        currentIndex = 0
    )
}

internal fun shouldDeferShuffleReorder(
    enableShuffle: Boolean,
    previousShuffle: Boolean,
    queueSize: Int,
    hasVirtualQueue: Boolean
): Boolean {
    return enableShuffle &&
        !previousShuffle &&
        queueSize > 1 &&
        !hasVirtualQueue
}
