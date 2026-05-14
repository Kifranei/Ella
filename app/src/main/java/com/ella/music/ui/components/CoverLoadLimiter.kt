package com.ella.music.ui.components

import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

object CoverLoadLimiter {
    private val semaphore = Semaphore(2)

    suspend fun <T> run(block: suspend () -> T): T = semaphore.withPermit { block() }
}
