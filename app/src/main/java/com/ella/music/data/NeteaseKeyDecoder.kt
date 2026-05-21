package com.ella.music.data

import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject
import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec

data class NeteaseKeyInfo(
    val raw: String,
    val decodedJson: String = "",
    val musicId: String = "",
    val musicName: String = "",
    val albumId: String = "",
    val albumName: String = "",
    val artists: List<NeteaseArtist> = emptyList(),
    val format: String = "",
    val bitrate: String = "",
    val comment: String = ""
) {
    val hasDecodedContent: Boolean
        get() = decodedJson.isNotBlank() ||
            musicId.isNotBlank() ||
            albumId.isNotBlank() ||
            artists.any { it.id.isNotBlank() } ||
            comment.isNotBlank()
}

data class NeteaseArtist(
    val id: String,
    val name: String
)

fun decodeNeteaseKey(value: String): NeteaseKeyInfo? {
    val raw = value.trim().takeIf { it.isNotBlank() } ?: return null
    val normalized = raw.extractNeteaseKeyPayload()

    normalized.extractNeteaseSongIdFromPlainText()?.let { id ->
        return NeteaseKeyInfo(raw = raw, musicId = id)
    }

    val decodedJson = when {
        normalized.trimStart().startsWith("{") -> normalized
        else -> decryptNeteaseKeyPayload(normalized).orEmpty()
    }.trim()

    if (decodedJson.isBlank()) return NeteaseKeyInfo(raw = raw)

    return runCatching {
        val json = JSONObject(decodedJson)
        NeteaseKeyInfo(
            raw = raw,
            decodedJson = decodedJson,
            musicId = json.optId("musicId", "songId", "id"),
            musicName = json.optStringCompat("musicName", "songName", "name"),
            albumId = json.optId("albumId"),
            albumName = json.optStringCompat("album", "albumName"),
            artists = json.optArtists(),
            format = json.optStringCompat("format", "fileFormat"),
            bitrate = json.optId("bitrate", "bitRate"),
            comment = json.optStringCompat("comment", "description", "desc", "remark", "note", "subtitle", "subTitle")
        )
    }.getOrElse {
        NeteaseKeyInfo(raw = raw, decodedJson = decodedJson)
    }
}

fun neteaseSongUrl(id: String): String = "https://music.163.com/#/song?id=$id"

fun neteaseAlbumUrl(id: String): String = "https://music.163.com/#/album?id=$id"

fun neteaseArtistUrl(id: String): String = "https://music.163.com/#/artist?id=$id"

private fun decryptNeteaseKeyPayload(payload: String): String? {
    return runCatching {
        val encrypted = Base64.decode(payload, Base64.DEFAULT)
        val cipher = Cipher.getInstance("AES/ECB/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(NETEASE_KEY_AES_KEY.toByteArray(Charsets.UTF_8), "AES"))
        cipher.doFinal(encrypted)
            .stripPkcs7Padding()
            .toString(Charsets.UTF_8)
            .trim('\uFEFF', '\u0000', ' ', '\t', '\r', '\n')
            .jsonObjectSlice()
    }.getOrNull()
}

private fun String.extractNeteaseKeyPayload(): String {
    val text = trim('\uFEFF', '\u0000', ' ', '\t', '\r', '\n')
    val prefixIndex = text.indexOf("163 key", ignoreCase = true)
    if (prefixIndex >= 0) {
        val colonIndex = text.indexOf(':', startIndex = prefixIndex)
        if (colonIndex >= 0 && colonIndex + 1 < text.length) {
            return text.substring(colonIndex + 1).trim()
        }
    }
    return text
}

private fun String.extractNeteaseSongIdFromPlainText(): String? {
    if (matches(Regex("""\d{4,}"""))) return this
    return Regex("""(?:music\.163\.com/(?:#/)?song\?id=|songid[:=]\s*|song_id[:=]\s*|id=)(\d{4,})""", RegexOption.IGNORE_CASE)
        .find(this)
        ?.groupValues
        ?.getOrNull(1)
}

private fun ByteArray.stripPkcs7Padding(): ByteArray {
    if (isEmpty()) return this
    val padding = last().toInt() and 0xff
    if (padding !in 1..16 || padding > size) return this
    val valid = takeLast(padding).all { (it.toInt() and 0xff) == padding }
    return if (valid) copyOf(size - padding) else this
}

private fun String.jsonObjectSlice(): String {
    val start = indexOf('{')
    val end = lastIndexOf('}')
    return if (start >= 0 && end >= start) substring(start, end + 1) else this
}

private fun JSONObject.optId(vararg names: String): String {
    for (name in names) {
        if (!has(name) || isNull(name)) continue
        val value = opt(name)
        val text = when (value) {
            is Number -> value.toLong().toString()
            else -> value?.toString().orEmpty()
        }.trim()
        if (text.isNotBlank() && text != "0") return text
    }
    return ""
}

private fun JSONObject.optStringCompat(vararg names: String): String {
    for (name in names) {
        val text = optString(name).trim()
        if (text.isNotBlank() && text != "null") return text
    }
    return ""
}

private fun JSONObject.optArtists(): List<NeteaseArtist> {
    val array = optJSONArray("artist")
        ?: optJSONArray("artists")
        ?: optJSONArray("artistsInfo")
        ?: return emptyList()
    return buildList {
        for (index in 0 until array.length()) {
            parseArtist(array, index)?.let(::add)
        }
    }
}

private fun parseArtist(array: JSONArray, index: Int): NeteaseArtist? {
    val item = array.opt(index) ?: return null
    return when (item) {
        is JSONArray -> NeteaseArtist(
            name = item.optString(0).trim(),
            id = item.opt(1)?.toString().orEmpty().takeUnless { it == "0" }.orEmpty()
        )
        is JSONObject -> NeteaseArtist(
            name = item.optStringCompat("name", "artistName"),
            id = item.optId("id", "artistId")
        )
        else -> null
    }?.takeIf { it.name.isNotBlank() || it.id.isNotBlank() }
}

private const val NETEASE_KEY_AES_KEY = "#14ljk_!\\]&0U<'("
