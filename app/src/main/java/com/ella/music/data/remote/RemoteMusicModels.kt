package com.ella.music.data.remote

import com.ella.music.data.model.Song
import org.json.JSONArray
import org.json.JSONObject

enum class RemoteMusicProvider(val id: String) {
    Lx("lx"),
    Navidrome("navidrome"),
    OpenSubsonic("opensubsonic"),
    Emby("emby");

    companion object {
        fun fromId(id: String): RemoteMusicProvider =
            entries.firstOrNull { it.id == id } ?: Lx
    }
}

val RemoteMusicProvider.isSubsonicLike: Boolean
    get() = this == RemoteMusicProvider.Navidrome || this == RemoteMusicProvider.OpenSubsonic

data class RemoteMusicSourceConfig(
    val provider: RemoteMusicProvider,
    val baseUrl: String,
    val username: String = "",
    val password: String = "",
    val token: String = "",
    val userId: String = "",
    val serverName: String = "",
    val secondaryBaseUrl: String = "",
    val remoteWriteEnabled: Boolean = false,
    val streamMaxBitRate: Int = 0,
    val downloadMaxBitRate: Int = 0,
    val coverArtSize: Int = 512
) {
    val isConfigured: Boolean
        get() = baseUrl.isNotBlank() && when (provider) {
            RemoteMusicProvider.Navidrome,
            RemoteMusicProvider.OpenSubsonic -> username.isNotBlank() && (password.isNotBlank() || token.isNotBlank())
            RemoteMusicProvider.Emby -> token.isNotBlank() && userId.isNotBlank()
            RemoteMusicProvider.Lx -> false
        }
}

/** One saved remote server entry (a named [RemoteMusicSourceConfig]) for multi-address management. */
data class SavedRemoteServer(
    val id: String,
    val name: String,
    val config: RemoteMusicSourceConfig
)

fun List<SavedRemoteServer>.toRemoteServersJson(): String =
    JSONArray().also { array ->
        forEach { server ->
            array.put(
                JSONObject()
                    .put("id", server.id)
                    .put("name", server.name)
                    .put("baseUrl", server.config.baseUrl)
                    .put("username", server.config.username)
                    .put("password", server.config.password)
                    .put("token", server.config.token)
                    .put("userId", server.config.userId)
                    .put("serverName", server.config.serverName)
                    .put("secondaryBaseUrl", server.config.secondaryBaseUrl)
                    .put("remoteWriteEnabled", server.config.remoteWriteEnabled)
                    .put("streamMaxBitRate", server.config.streamMaxBitRate)
                    .put("downloadMaxBitRate", server.config.downloadMaxBitRate)
                    .put("coverArtSize", server.config.coverArtSize)
            )
        }
    }.toString()

fun String.toSavedRemoteServers(provider: RemoteMusicProvider): List<SavedRemoteServer> =
    runCatching {
        val array = JSONArray(this)
        (0 until array.length()).mapNotNull { index ->
            val json = array.optJSONObject(index) ?: return@mapNotNull null
            val id = json.optString("id").trim().ifBlank { return@mapNotNull null }
            SavedRemoteServer(
                id = id,
                name = json.optString("name").trim(),
                config = RemoteMusicSourceConfig(
                    provider = provider,
                    baseUrl = json.optString("baseUrl").trim(),
                    username = json.optString("username").trim(),
                    password = json.optString("password"),
                    token = json.optString("token"),
                    userId = json.optString("userId"),
                    serverName = json.optString("serverName"),
                    secondaryBaseUrl = json.optString("secondaryBaseUrl").trim(),
                    remoteWriteEnabled = json.optBoolean("remoteWriteEnabled", false),
                    streamMaxBitRate = json.optInt("streamMaxBitRate", 0).coerceAtLeast(0),
                    downloadMaxBitRate = json.optInt("downloadMaxBitRate", 0).coerceAtLeast(0),
                    coverArtSize = json.optInt("coverArtSize", 512).coerceIn(64, 2048)
                )
            )
        }
    }.getOrDefault(emptyList())

data class RemoteOnlineSong(
    val song: Song,
    val provider: RemoteMusicProvider,
    val remoteId: String,
    val streamUrl: String = "",
    val coverUrl: String = ""
)
