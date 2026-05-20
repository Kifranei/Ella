package com.ella.music.data.model

data class AudioInfo(
    val format: String,
    val bitRate: Int = 0,
    val sampleRate: Int = 0,
    val bitDepth: Int = 0,
    val channels: Int = 0
)

data class SongTagInfo(
    val title: String = "",
    val artist: String = "",
    val album: String = "",
    val albumArtist: String = "",
    val genre: String = "",
    val year: String = "",
    val track: String = "",
    val comment: String = "",
    val neteaseKey: String = ""
) {
    val displayComment: String
        get() = comment
            .takeIf { it.isNotBlank() }
            ?.takeUnless { it.looksLikeNeteaseKey() || it == neteaseKey }
            .orEmpty()
}

fun String.looksLikeNeteaseKey(): Boolean {
    val normalized = lowercase()
    return "163" in normalized ||
        "netease" in normalized ||
        "cloudmusic" in normalized ||
        "music.163.com" in normalized
}
