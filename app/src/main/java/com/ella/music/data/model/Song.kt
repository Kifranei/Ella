package com.ella.music.data.model

data class Song(
    val id: Long,
    val title: String,
    val artist: String,
    val album: String,
    val albumId: Long,
    val duration: Long,
    val path: String,
    val fileName: String,
    val fileSize: Long = 0L,
    val mimeType: String = "",
    val dateAdded: Long = 0L,
    val dateModified: Long = 0L,
    val trackNumber: Int = 0,
    val coverUrl: String = "",
    val onlineSource: String = "",
    val onlineId: String = "",
    val onlineLyrics: String = "",
    val onlineLyricTranslation: String = ""
) {
    val durationText: String
        get() {
            val totalSeconds = duration / 1000
            val minutes = totalSeconds / 60
            val seconds = totalSeconds % 60
            return "%02d:%02d".format(minutes, seconds)
        }
}

fun Song.albumIdentityId(): Long {
    val key = "${album.normalizedAlbumIdentityPart()}|${artist.normalizedAlbumIdentityPart()}"
    var hash = -0x340d631b7bdddcdbL
    key.forEach { char ->
        hash = hash xor char.code.toLong()
        hash *= 0x100000001b3L
    }
    return hash and Long.MAX_VALUE
}

private fun String.normalizedAlbumIdentityPart(): String =
    trim()
        .ifBlank { "unknown" }
        .lowercase()
        .replace(Regex("\\s+"), " ")
