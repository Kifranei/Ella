package com.ella.music.player

/**
 * Full queues are practical for normal libraries. Only pathologically large libraries need a
 * short controller window to avoid MediaSession Binder transaction limits and oversized restore
 * payloads.
 */
internal const val LARGE_LIBRARY_SAFE_MODE_THRESHOLD = 10_000
internal const val LARGE_LIBRARY_SAFE_MODE_QUEUE_SIZE = 1_000
