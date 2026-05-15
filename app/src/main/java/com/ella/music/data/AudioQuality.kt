package com.ella.music.data

import com.ella.music.data.model.AudioInfo

data class AudioQualitySummary(
    val compactLabel: String,
    val detailLabel: String,
    val listTag: String?,
    val analyticsLabel: String,
    val showMobius: Boolean
)

fun audioQualitySummary(info: AudioInfo): AudioQualitySummary {
    val normalizedFormat = normalizedAudioFormat(info.format)
    val bitDepth = normalizedBitDepth(info)
    val isDolby = info.channels >= 6
    val isMq = bitDepth >= 24 && info.sampleRate >= 192_000
    val isHiRes = bitDepth >= 24 && info.sampleRate >= 48_000
    val isAppleLossless = normalizedFormat.contains("ALAC") ||
        (normalizedFormat == "M4A" && (info.bitRate >= 700_000 || (bitDepth >= 16 && info.bitRate >= 500_000)))
    val isLossless = isAppleLossless || normalizedFormat in setOf("FLAC", "WAV", "APE")
    val isSq = isLossless && info.sampleRate >= 44_100 && bitDepth >= 16
    val isKnownLossy = normalizedFormat in setOf("MP3", "AAC", "M4A", "OGG", "OPUS")
    val compact = when {
        isDolby -> "Dolby Atmos"
        isMq -> "MQ"
        isAppleLossless -> "Apple Lossless"
        isHiRes -> "Hi-Res"
        isSq -> "Lossless"
        info.bitRate >= 319_000 -> "HQ"
        info.bitRate > 0 || isKnownLossy -> "LQ"
        else -> normalizedFormat.ifBlank { "Audio" }
    }
    val tag = when {
        isDolby -> "Dolby"
        isMq -> "MQ"
        isHiRes -> "HR"
        isSq -> "SQ"
        info.bitRate >= 319_000 -> "HQ"
        info.bitRate > 0 || isKnownLossy -> "LQ"
        else -> null
    }
    val analytics = when {
        isDolby -> "Dolby"
        isMq -> "MQ"
        isHiRes -> "Hi-Res"
        isSq -> "无损"
        info.bitRate >= 319_000 -> "HQ"
        info.bitRate > 0 || isKnownLossy -> "LQ"
        else -> "未知"
    }
    return AudioQualitySummary(
        compactLabel = compact,
        detailLabel = detailedAudioInfo(info, bitDepth),
        listTag = tag,
        analyticsLabel = analytics,
        showMobius = isAppleLossless || isSq || isHiRes || isMq
    )
}

fun detailedAudioInfo(info: AudioInfo): String = detailedAudioInfo(info, normalizedBitDepth(info))

fun normalizedBitDepth(info: AudioInfo): Int {
    val format = normalizedAudioFormat(info.format)
    val likelyHiResAlac = format in setOf("ALAC/M4A", "M4A") &&
        info.sampleRate >= 48_000 &&
        info.bitRate >= 1_000_000
    if (info.bitDepth > 0) {
        return if (info.bitDepth < 24 && likelyHiResAlac) 24 else info.bitDepth
    }
    if (format in setOf("FLAC", "ALAC/M4A", "WAV", "APE") ||
        (format == "M4A" && (info.sampleRate >= 88_200 || info.bitRate >= 700_000))
    ) {
        return if (info.sampleRate >= 88_200 || info.bitRate >= 1_600_000) 24 else 16
    }
    return 0
}

fun normalizedAudioFormat(raw: String): String {
    val value = raw.uppercase()
    return when {
        "ALAC" in value -> "ALAC/M4A"
        "FLAC" in value -> "FLAC"
        "M4A" in value || "MP4" in value || "AAC" in value -> "M4A"
        "MP3" in value || "MPEG" in value -> "MP3"
        "WAV" in value || "PCM" in value -> "WAV"
        "OPUS" in value -> "OPUS"
        "OGG" in value -> "OGG"
        else -> value.ifBlank { "AUDIO" }
    }
}

private fun detailedAudioInfo(info: AudioInfo, bitDepth: Int): String {
    val parts = mutableListOf<String>()
    parts += normalizedAudioFormat(info.format).lowercase()
    if (info.sampleRate > 0) parts += if (info.sampleRate % 1000 == 0) {
        "${info.sampleRate / 1000}kHz"
    } else {
        "%.1fkHz".format(info.sampleRate / 1000f)
    }
    if (bitDepth > 0) parts += "${bitDepth}bit"
    if (info.channels > 0) parts += "${info.channels}ch"
    return parts.joinToString("/")
}
