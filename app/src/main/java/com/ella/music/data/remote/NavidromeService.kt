package com.ella.music.data.remote

import android.content.Context
import com.ella.music.R
import com.ella.music.data.AppNetworkLoggingInterceptor
import com.ella.music.data.model.Song
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.security.MessageDigest
import java.util.UUID
import java.util.concurrent.TimeUnit
import kotlin.math.absoluteValue

data class SubsonicPlaylistSnapshot(
    val id: String,
    val name: String,
    val songs: List<Song>,
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L
)

data class SubsonicLibraryCollections(
    val favoriteSongs: List<Song>,
    val playlists: List<SubsonicPlaylistSnapshot>
)

class NavidromeService(private val context: Context) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(24, TimeUnit.SECONDS)
        .addInterceptor(AppNetworkLoggingInterceptor("NavidromeNetwork"))
        .build()

    suspend fun test(config: RemoteMusicSourceConfig) = withContext(Dispatchers.IO) {
        request(config, "ping")
    }

    suspend fun search(keyword: String, config: RemoteMusicSourceConfig): List<RemoteOnlineSong> = withContext(Dispatchers.IO) {
        val root = request(config, "search3", mapOf("query" to keyword.trim(), "songCount" to "50"))
        val songs = root.optJSONObject("subsonic-response")
            ?.optJSONObject("searchResult3")
            ?.optJSONArray("song")
            ?: return@withContext emptyList()
        List(songs.length()) { index -> songFromJson(songs.getJSONObject(index), config) }
            .filter { it.remoteId.isNotBlank() }
    }

    suspend fun listSongs(config: RemoteMusicSourceConfig, limit: Int = Int.MAX_VALUE): List<RemoteOnlineSong> = withContext(Dispatchers.IO) {
        val targetCount = normalizeRemoteFetchLimit(limit)
        val songs = mutableListOf<RemoteOnlineSong>()
        val seenSongIds = LinkedHashSet<String>()
        var songOffset = 0
        var searchMayBeTruncated = false

        // Enumerate the whole library by paging songs directly via search3 with an empty query
        // (Navidrome/Subsonic normally returns all songs, 500 per page).
        while (songs.size < targetCount) {
            val pageSize = if (targetCount == Int.MAX_VALUE) {
                REMOTE_LIBRARY_PAGE_SIZE
            } else {
                minOf(REMOTE_LIBRARY_PAGE_SIZE, targetCount - songs.size).coerceAtLeast(1)
            }
            val page = fetchAllSongsPageResult(config, songOffset, pageSize)
            if (page.rawCount == 0) {
                searchMayBeTruncated = songOffset > 0 && songs.size >= REMOTE_LIBRARY_PAGE_SIZE
                break
            }

            val beforeCount = songs.size
            for (song in page.songs) {
                if (song.remoteId.isBlank()) continue
                if (!seenSongIds.add(song.remoteId)) continue
                songs += song
                if (songs.size >= targetCount) return@withContext songs
            }

            if (page.rawCount < pageSize) break
            songOffset += page.rawCount
            if (songs.size == beforeCount) {
                // Some Subsonic-compatible servers ignore songOffset for an empty search3 query.
                searchMayBeTruncated = songs.size >= REMOTE_LIBRARY_PAGE_SIZE
                break
            }
        }

        if (songs.size < targetCount && searchMayBeTruncated) {
            fetchSongsByAlbums(config, targetCount, seenSongIds).forEach { song ->
                songs += song
                if (songs.size >= targetCount) return@withContext songs
            }
        }
        songs
    }

    /** Fetch a single page of the library starting at [offset]. Used for incremental "load more". */
    suspend fun listSongsPage(
        config: RemoteMusicSourceConfig,
        offset: Int,
        count: Int
    ): List<RemoteOnlineSong> = withContext(Dispatchers.IO) {
        fetchAllSongsPage(config, offset.coerceAtLeast(0), count.coerceAtLeast(1))
    }

    /** Reads the server-owned playlists and starred songs through the Subsonic REST API. */
    suspend fun loadCollections(config: RemoteMusicSourceConfig): SubsonicLibraryCollections =
        withContext(Dispatchers.IO) {
            val favoriteSongs = runCatching { fetchStarredSongs(config) }.getOrDefault(emptyList())
            val playlists = runCatching { fetchPlaylists(config) }.getOrDefault(emptyList())
            SubsonicLibraryCollections(favoriteSongs = favoriteSongs, playlists = playlists)
        }

    suspend fun setFavorite(config: RemoteMusicSourceConfig, songId: String, favorite: Boolean) =
        withContext(Dispatchers.IO) {
            require(songId.isNotBlank()) { "Missing remote song id" }
            request(config, if (favorite) "star" else "unstar", mapOf("id" to songId))
        }

    private fun fetchStarredSongs(config: RemoteMusicSourceConfig): List<Song> {
        // OpenSubsonic servers may expose either the newer getStarred2 payload or the legacy
        // getStarred endpoint, so use the latter only as a compatibility fallback.
        val root = runCatching { request(config, "getStarred2") }
            .getOrElse { request(config, "getStarred") }
        val response = root.optJSONObject("subsonic-response")
        val starred = response?.optJSONObject("starred2") ?: response?.optJSONObject("starred")
        val entries = starred?.optJSONArray("song") ?: return emptyList()
        return List(entries.length()) { index ->
            songFromJson(entries.getJSONObject(index), config).song
        }
    }

    private fun fetchPlaylists(config: RemoteMusicSourceConfig): List<SubsonicPlaylistSnapshot> {
        val root = request(config, "getPlaylists")
        val headers = root.optJSONObject("subsonic-response")
            ?.optJSONObject("playlists")
            ?.optJSONArray("playlist")
            ?: return emptyList()
        return List(headers.length()) { index ->
            headers.getJSONObject(index)
        }.mapNotNull { header ->
            val playlistId = header.optString("id").trim()
            if (playlistId.isBlank()) return@mapNotNull null
            val detail = request(config, "getPlaylist", mapOf("id" to playlistId))
                .optJSONObject("subsonic-response")
                ?.optJSONObject("playlist")
                ?: return@mapNotNull null
            val entries = detail.optJSONArray("entry") ?: detail.optJSONArray("song")
            val songs = if (entries == null) {
                emptyList()
            } else {
                List(entries.length()) { entryIndex ->
                    songFromJson(entries.getJSONObject(entryIndex), config).song
                }
            }
            SubsonicPlaylistSnapshot(
                id = playlistId,
                name = detail.optString("name").ifBlank { header.optString("name") }.ifBlank { playlistId },
                songs = songs
            )
        }
    }

    private fun fetchAllSongsPage(
        config: RemoteMusicSourceConfig,
        offset: Int,
        pageSize: Int
    ): List<RemoteOnlineSong> = fetchAllSongsPageResult(config, offset, pageSize).songs

    private fun fetchAllSongsPageResult(
        config: RemoteMusicSourceConfig,
        offset: Int,
        pageSize: Int
    ): NavidromeSongPage {
        val root = request(
            config,
            "search3",
            mapOf(
                "query" to "",
                "artistCount" to "0",
                "albumCount" to "0",
                "songCount" to pageSize.toString(),
                "songOffset" to offset.toString()
            )
        )
        val songs = root.optJSONObject("subsonic-response")
            ?.optJSONObject("searchResult3")
            ?.optJSONArray("song")
            ?: return NavidromeSongPage(emptyList(), rawCount = 0)
        return NavidromeSongPage(
            songs = List(songs.length()) { index -> songFromJson(songs.getJSONObject(index), config) }
                .filter { it.remoteId.isNotBlank() },
            rawCount = songs.length()
        )
    }

    private fun fetchSongsByAlbums(
        config: RemoteMusicSourceConfig,
        targetCount: Int,
        seenSongIds: MutableSet<String>
    ): List<RemoteOnlineSong> {
        val songs = mutableListOf<RemoteOnlineSong>()
        var albumOffset = 0
        while (seenSongIds.size < targetCount) {
            val albumIds = fetchAlbumIdsPage(config, albumOffset, REMOTE_LIBRARY_PAGE_SIZE)
            if (albumIds.isEmpty()) break
            for (albumId in albumIds) {
                val albumSongs = fetchAlbumSongs(config, albumId)
                for (song in albumSongs) {
                    if (song.remoteId.isBlank()) continue
                    if (!seenSongIds.add(song.remoteId)) continue
                    songs += song
                    if (seenSongIds.size >= targetCount) return songs
                }
            }
            if (albumIds.size < REMOTE_LIBRARY_PAGE_SIZE) break
            albumOffset += albumIds.size
        }
        return songs
    }

    private fun fetchAlbumIdsPage(
        config: RemoteMusicSourceConfig,
        offset: Int,
        pageSize: Int
    ): List<String> {
        val root = request(
            config,
            "getAlbumList2",
            mapOf(
                "type" to "alphabeticalByName",
                "size" to pageSize.toString(),
                "offset" to offset.toString()
            )
        )
        val albums = root.optJSONObject("subsonic-response")
            ?.optJSONObject("albumList2")
            ?.optJSONArray("album")
            ?: return emptyList()
        return List(albums.length()) { index -> albums.getJSONObject(index).optString("id") }
            .filter { it.isNotBlank() }
    }

    private fun fetchAlbumSongs(
        config: RemoteMusicSourceConfig,
        albumId: String
    ): List<RemoteOnlineSong> {
        val root = request(config, "getAlbum", mapOf("id" to albumId))
        val songs = root.optJSONObject("subsonic-response")
            ?.optJSONObject("album")
            ?.optJSONArray("song")
            ?: return emptyList()
        return List(songs.length()) { index -> songFromJson(songs.getJSONObject(index), config) }
            .filter { it.remoteId.isNotBlank() }
    }

    fun resolvePlayableSong(item: RemoteOnlineSong): Song =
        item.song.copy(path = item.streamUrl, coverUrl = item.coverUrl, onlineSource = item.provider.id)

    private fun songFromJson(item: JSONObject, config: RemoteMusicSourceConfig): RemoteOnlineSong {
        val id = item.optString("id")
        val title = item.optString("title").ifBlank { context.getString(R.string.common_unknown) }
        val artist = item.optString("artist").ifBlank { context.getString(R.string.player_unknown_artist) }
        val album = item.optString("album").ifBlank {
            if (config.provider == RemoteMusicProvider.OpenSubsonic) "OpenSubsonic" else "Navidrome"
        }
        val durationMs = item.optLong("duration", 0L).coerceAtLeast(0L) * 1000L
        val suffix = item.optString("suffix").ifBlank { "mp3" }
        val stream = endpoint(config, "stream", mapOf("id" to id))
        val cover = item.optString("coverArt").takeIf { it.isNotBlank() }
            ?.let { endpoint(config, "getCoverArt", mapOf("id" to it, "size" to "512")) }
            .orEmpty()
        // Populate album/artist/genre/year/track metadata so remote songs can feed the same
        // artist / album / genre / year library views as local songs.
        val year = item.optInt("year", 0).takeIf { it > 0 }?.toString().orEmpty()
        val genre = item.optString("genre").ifBlank {
            item.optJSONArray("genres")?.optJSONObject(0)?.optString("name").orEmpty()
        }
        return RemoteOnlineSong(
            song = Song(
                id = stableId("${config.provider.id}:$id"),
                title = title,
                artist = artist,
                album = album,
                albumId = 0L,
                duration = durationMs,
                path = stream,
                fileName = "$title.$suffix",
                mimeType = item.optString("contentType"),
                trackNumber = item.optInt("track", 0),
                discNumber = item.optInt("discNumber", 0),
                albumArtist = item.optString("albumArtist")
                    .ifBlank { item.optString("displayAlbumArtist") }
                    .ifBlank { item.optJSONArray("albumArtists")?.optJSONObject(0)?.optString("name").orEmpty() },
                genre = genre,
                year = year,
                coverUrl = cover,
                onlineSource = config.provider.id,
                onlineId = id
            ),
            provider = config.provider,
            remoteId = id,
            streamUrl = stream,
            coverUrl = cover
        )
    }

    private fun request(
        config: RemoteMusicSourceConfig,
        endpoint: String,
        params: Map<String, String> = emptyMap()
    ): JSONObject {
        val url = endpoint(config, endpoint, params)
        val request = Request.Builder().url(url).header("User-Agent", USER_AGENT).build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) error(context.getString(R.string.remote_source_http_error, response.code))
            val root = JSONObject(response.body?.string().orEmpty())
            val subsonic = root.optJSONObject("subsonic-response") ?: error(context.getString(R.string.remote_source_invalid_response))
            if (subsonic.optString("status") == "failed") {
                val message = subsonic.optJSONObject("error")?.optString("message").orEmpty()
                error(message.ifBlank { context.getString(R.string.remote_source_request_failed) })
            }
            return root
        }
    }

    private fun endpoint(config: RemoteMusicSourceConfig, endpoint: String, params: Map<String, String>): String {
        val base = config.baseUrl.trimEnd('/')
        val builder = "$base/rest/$endpoint.view".toHttpUrlOrNull()
            ?.newBuilder()
            ?: error(context.getString(R.string.remote_source_url_invalid))
        val salt = UUID.randomUUID().toString().replace("-", "").take(12)
        val passwordOrToken = config.token.ifBlank { config.password }
        builder
            .addQueryParameter("u", config.username)
            .addQueryParameter("s", salt)
            .addQueryParameter("t", md5(passwordOrToken + salt))
            .addQueryParameter("v", "1.16.1")
            .addQueryParameter("c", "Halcyon")
            .addQueryParameter("f", "json")
        params.forEach { (key, value) -> builder.addQueryParameter(key, value) }
        return builder.build().toString()
    }

    private fun md5(value: String): String =
        MessageDigest.getInstance("MD5")
            .digest(value.toByteArray())
            .joinToString("") { "%02x".format(it) }

    private fun stableId(key: String): Long =
        (key.hashCode().toLong() and Long.MAX_VALUE).takeIf { it != 0L } ?: key.hashCode().toLong().absoluteValue

    private companion object {
        const val USER_AGENT = "Halcyon/1.0 Navidrome"
    }
}

private data class NavidromeSongPage(
    val songs: List<RemoteOnlineSong>,
    val rawCount: Int
)
