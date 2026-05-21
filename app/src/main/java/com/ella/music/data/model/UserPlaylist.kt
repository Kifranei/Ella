package com.ella.music.data.model

import org.json.JSONArray
import org.json.JSONObject

const val FAVORITES_PLAYLIST_ID = "favorites"
const val FIVE_STAR_PLAYLIST_ID = "five_star_rating"

data class UserPlaylist(
    val id: String,
    val name: String,
    val songs: List<PlaylistSong> = emptyList(),
    val createdAt: Long,
    val updatedAt: Long
) {
    val isFavorites: Boolean get() = id == FAVORITES_PLAYLIST_ID
    val isFiveStarRating: Boolean get() = id == FIVE_STAR_PLAYLIST_ID
}

data class PlaylistSong(
    val key: String,
    val id: Long,
    val title: String,
    val artist: String,
    val album: String,
    val albumId: Long,
    val duration: Long,
    val path: String,
    val fileName: String,
    val fileSize: Long,
    val mimeType: String,
    val dateAdded: Long,
    val dateModified: Long,
    val trackNumber: Int,
    val discNumber: Int = 0,
    val albumArtist: String = "",
    val genre: String = "",
    val year: String = "",
    val composer: String = "",
    val lyricist: String = "",
    val coverUrl: String,
    val onlineSource: String,
    val onlineId: String,
    val onlineLyrics: String,
    val onlineLyricTranslation: String,
    val addedAt: Long
)

fun Song.playlistIdentityKey(): String = when {
    onlineSource.isNotBlank() || onlineId.isNotBlank() -> {
        listOf("online", onlineSource, onlineId, path, title, artist).joinToString("|")
    }
    path.isNotBlank() -> "path|${path.trim().lowercase()}"
    else -> listOf("media", id, title, artist, album, duration).joinToString("|")
}

fun Song.toPlaylistSong(addedAt: Long = System.currentTimeMillis()): PlaylistSong =
    PlaylistSong(
        key = playlistIdentityKey(),
        id = id,
        title = title,
        artist = artist,
        album = album,
        albumId = albumId,
        duration = duration,
        path = path,
        fileName = fileName,
        fileSize = fileSize,
        mimeType = mimeType,
        dateAdded = dateAdded,
        dateModified = dateModified,
        trackNumber = trackNumber,
        discNumber = discNumber,
        albumArtist = albumArtist,
        genre = genre,
        year = year,
        composer = composer,
        lyricist = lyricist,
        coverUrl = coverUrl,
        onlineSource = onlineSource,
        onlineId = onlineId,
        onlineLyrics = onlineLyrics,
        onlineLyricTranslation = onlineLyricTranslation,
        addedAt = addedAt
    )

fun PlaylistSong.toSong(): Song =
    Song(
        id = id,
        title = title,
        artist = artist,
        album = album,
        albumId = albumId,
        duration = duration,
        path = path,
        fileName = fileName,
        fileSize = fileSize,
        mimeType = mimeType,
        dateAdded = dateAdded,
        dateModified = dateModified,
        trackNumber = trackNumber,
        discNumber = discNumber,
        albumArtist = albumArtist,
        genre = genre,
        year = year,
        composer = composer,
        lyricist = lyricist,
        coverUrl = coverUrl,
        onlineSource = onlineSource,
        onlineId = onlineId,
        onlineLyrics = onlineLyrics,
        onlineLyricTranslation = onlineLyricTranslation
    )

fun UserPlaylist.toJson(): JSONObject =
    JSONObject()
        .put("id", id)
        .put("name", name)
        .put("createdAt", createdAt)
        .put("updatedAt", updatedAt)
        .put("songs", JSONArray().also { array ->
            songs.forEach { array.put(it.toJson()) }
        })

fun JSONObject.toUserPlaylist(): UserPlaylist =
    UserPlaylist(
        id = optString("id"),
        name = optString("name"),
        createdAt = optLong("createdAt"),
        updatedAt = optLong("updatedAt"),
        songs = optJSONArray("songs").toPlaylistSongs()
    )

fun PlaylistSong.toJson(): JSONObject =
    JSONObject()
        .put("key", key)
        .put("id", id)
        .put("title", title)
        .put("artist", artist)
        .put("album", album)
        .put("albumId", albumId)
        .put("duration", duration)
        .put("path", path)
        .put("fileName", fileName)
        .put("fileSize", fileSize)
        .put("mimeType", mimeType)
        .put("dateAdded", dateAdded)
        .put("dateModified", dateModified)
        .put("trackNumber", trackNumber)
        .put("discNumber", discNumber)
        .put("albumArtist", albumArtist)
        .put("genre", genre)
        .put("year", year)
        .put("composer", composer)
        .put("lyricist", lyricist)
        .put("coverUrl", coverUrl)
        .put("onlineSource", onlineSource)
        .put("onlineId", onlineId)
        .put("onlineLyrics", onlineLyrics)
        .put("onlineLyricTranslation", onlineLyricTranslation)
        .put("addedAt", addedAt)

fun JSONObject.toPlaylistSong(): PlaylistSong =
    PlaylistSong(
        key = optString("key"),
        id = optLong("id"),
        title = optString("title"),
        artist = optString("artist"),
        album = optString("album"),
        albumId = optLong("albumId"),
        duration = optLong("duration"),
        path = optString("path"),
        fileName = optString("fileName"),
        fileSize = optLong("fileSize"),
        mimeType = optString("mimeType"),
        dateAdded = optLong("dateAdded"),
        dateModified = optLong("dateModified"),
        trackNumber = optInt("trackNumber"),
        discNumber = optInt("discNumber"),
        albumArtist = optString("albumArtist"),
        genre = optString("genre"),
        year = optString("year"),
        composer = optString("composer"),
        lyricist = optString("lyricist"),
        coverUrl = optString("coverUrl"),
        onlineSource = optString("onlineSource"),
        onlineId = optString("onlineId"),
        onlineLyrics = optString("onlineLyrics"),
        onlineLyricTranslation = optString("onlineLyricTranslation"),
        addedAt = optLong("addedAt")
    )

private fun JSONArray?.toPlaylistSongs(): List<PlaylistSong> {
    if (this == null) return emptyList()
    return List(length()) { index -> optJSONObject(index)?.toPlaylistSong() }
        .filterNotNull()
        .filter { it.key.isNotBlank() && it.title.isNotBlank() }
}
