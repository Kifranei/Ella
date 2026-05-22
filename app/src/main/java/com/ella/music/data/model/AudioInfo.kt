package com.ella.music.data.model

import com.ella.music.data.decodeNeteaseKey

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
    val composer: String = "",
    val lyricist: String = "",
    val track: String = "",
    val comment: String = "",
    val copyright: String = "",
    val neteaseKey: String = "",
    val rating: Int = 0
) {
    val displayComment: String
        get() = comment
            .cleanDisplayComment()
            .takeIf { it.isNotBlank() }
            ?.takeUnless { it.looksLikeNeteaseKey() || it == neteaseKey.cleanDisplayComment() || it.looksLikeSourceGarbageComment() }
            ?: decodeNeteaseKey(neteaseKey)?.comment?.cleanDisplayComment()?.takeIf { it.isNotBlank() }
            .orEmpty()
}

fun String.looksLikeNeteaseKey(): Boolean {
    val normalized = lowercase()
    return "163" in normalized ||
        "netease" in normalized ||
        "cloudmusic" in normalized ||
        "music.163.com" in normalized
}

private fun String.cleanDisplayComment(): String {
    val text = trim()
        .trim('"', '\'', ' ', '\t', '\r', '\n')
        .replace(Regex("""\s+"""), " ")
    return text.unwrapWholeCommentMarks()
}

private fun String.unwrapWholeCommentMarks(): String {
    val pairs = listOf('《' to '》', '「' to '」', '『' to '』', '<' to '>')
    val first = firstOrNull() ?: return this
    val last = lastOrNull() ?: return this
    val pair = pairs.firstOrNull { it.first == first && it.second == last } ?: return this
    val inner = substring(1, length - 1).trim()
    return inner.takeIf { it.isNotBlank() && pair.first !in it && pair.second !in it } ?: this
}

private fun String.looksLikeSourceGarbageComment(): Boolean {
    val normalized = lowercase()
        .replace(Regex("""[\s_\-:：/\\|,.，。;；()\[\]{}<>《》「」『』]+"""), "")
    if (normalized in setOf("kuwo", "酷我", "kw", "lx")) return true
    return normalized.startsWith("kuwo") && normalized.length <= 12
}
