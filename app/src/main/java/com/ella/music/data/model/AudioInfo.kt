package com.ella.music.data.model

data class AudioInfo(
    val format: String,
    val bitRate: Int = 0,
    val sampleRate: Int = 0,
    val bitDepth: Int = 0,
    val channels: Int = 0
)
