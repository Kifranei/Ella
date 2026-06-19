package com.ella.music.data.repository

import java.util.concurrent.CancellationException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LibraryScanProgressStateTest {
    @Test
    fun normalScanCompletionEndsScanningState() {
        val state = LibraryScanProgressState()

        state.start()
        state.update(42)
        state.finish()

        assertFalse(state.isScanning.value)
        assertEquals(0, state.scanProgress.value)
    }

    @Test
    fun scanExceptionStillEndsScanningState() {
        val state = LibraryScanProgressState()

        runCatching {
            state.start()
            state.update(10)
            error("scan failed")
        }.onFailure {
            state.finish()
        }

        assertFalse(state.isScanning.value)
        assertEquals(0, state.scanProgress.value)
    }

    @Test
    fun scanCancellationStillEndsScanningState() {
        val state = LibraryScanProgressState()

        runCatching {
            state.start()
            state.update(7)
            throw CancellationException("cancel scan")
        }.onFailure {
            state.finish()
        }

        assertFalse(state.isScanning.value)
        assertEquals(0, state.scanProgress.value)
    }

    @Test
    fun emptyLibraryScanCompletionEndsScanningState() {
        val state = LibraryScanProgressState()

        state.start()
        state.update(0)
        state.finish()

        assertFalse(state.isScanning.value)
        assertEquals(0, state.scanProgress.value)
    }

    @Test
    fun customFolderFallbackCompletionEndsScanningState() {
        val state = LibraryScanProgressState()

        state.start()
        state.update(0)
        assertTrue(state.isScanning.value)
        state.update(3)
        state.update(12)
        state.finish()

        assertFalse(state.isScanning.value)
        assertEquals(0, state.scanProgress.value)
    }
}
