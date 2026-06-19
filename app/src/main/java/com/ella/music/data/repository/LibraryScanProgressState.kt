package com.ella.music.data.repository

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

internal class LibraryScanProgressState {
    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    private val _scanProgress = MutableStateFlow(0)
    val scanProgress: StateFlow<Int> = _scanProgress.asStateFlow()

    fun start() {
        _scanProgress.value = 0
        _isScanning.value = true
    }

    fun update(count: Int) {
        _scanProgress.value = count.coerceAtLeast(0)
    }

    fun finish() {
        _scanProgress.value = 0
        _isScanning.value = false
    }
}
