package com.ella.music.data.musicfree

import android.content.Context
import com.ella.music.data.MusicFreePluginConfig
import com.ella.music.data.model.Song
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

data class MusicFreeOnlineSong(
    val song: Song,
    val pluginName: String,
    val rawJson: String,
    val coverUrl: String = ""
)

class MusicFreePluginService(private val context: Context? = null) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    suspend fun importPlugin(url: String): Pair<String, String> = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(url.trim())
            .header("User-Agent", USER_AGENT)
            .header("Cache-Control", "no-cache")
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) error("导入失败: HTTP ${response.code}")
            importPluginScript(response.body?.string().orEmpty())
        }
    }

    fun importPluginScript(script: String): Pair<String, String> {
        if (script.length !in 50..9_000_000) error("插件脚本内容异常")
        val name = extractPlatform(script)
        if (name.isBlank()) error("没有识别到 MusicFree 插件 platform")
        val supportsMusic = "search" in script || "getMediaSource" in script || "importMusicItem" in script
        if (!supportsMusic) error("插件未声明搜索或播放能力")
        return name to script
    }

    suspend fun search(
        keyword: String,
        plugin: MusicFreePluginConfig?,
        page: Int = 1
    ): List<MusicFreeOnlineSong> = withContext(Dispatchers.IO) {
        if (plugin == null) error("请先选择一个 MusicFree 插件")
        if (context == null) error("MusicFree 运行环境未初始化")
        val rawItems = MusicFreePluginRuntime(context, client).use { runtime ->
            runtime.search(plugin.script, keyword.trim(), page.coerceAtLeast(1))
        }
        rawItems.toJsonObjects().mapNotNull { item -> item.toOnlineSong(plugin.name) }
    }

    suspend fun resolvePlayableSong(item: MusicFreeOnlineSong, plugin: MusicFreePluginConfig?): Song = withContext(Dispatchers.IO) {
        if (plugin == null) error("请先选择一个 MusicFree 插件")
        if (item.song.path.startsWith("http://") || item.song.path.startsWith("https://")) return@withContext item.song
        if (context == null) error("MusicFree 运行环境未初始化")
        val mediaSource = MusicFreePluginRuntime(context, client).use { runtime ->
            runtime.getMediaSource(plugin.script, item.rawJson, bestQuality(item.rawJson))
        }
        val url = mediaSource.optString("url").takeIf { it.startsWith("http://") || it.startsWith("https://") }
            ?: error("插件没有返回播放地址")
        val ext = url.substringBefore('?')
            .substringAfterLast('.', "")
            .takeIf { it.length in 2..5 }
            ?: "mp3"
        item.song.copy(
            path = url,
            fileName = "${item.song.title}.$ext",
            mimeType = mimeTypeFromExtension(ext)
        )
    }

    private fun extractPlatform(script: String): String {
        val patterns = listOf(
            Regex("""platform\s*:\s*['"]([^'"]+)['"]"""),
            Regex("""platform\s*=\s*['"]([^'"]+)['"]"""),
            Regex("""['"]platform['"]\s*:\s*['"]([^'"]+)['"]""")
        )
        return patterns.firstNotNullOfOrNull { regex ->
            regex.find(script)?.groupValues?.getOrNull(1)
        }.orEmpty().trim()
    }

    private fun JSONObject.toOnlineSong(pluginName: String): MusicFreeOnlineSong? {
        val title = optString("title").ifBlank { optString("name") }.trim()
        if (title.isBlank()) return null
        val artist = optString("artist").ifBlank { optString("singer") }.ifBlank { "未知歌手" }.trim()
        val album = optString("album").ifBlank { "在线音乐" }.trim()
        val platform = optString("platform").ifBlank { pluginName }
        val onlineId = optString("id").ifBlank { "$platform:$title:$artist" }
        val artwork = optString("artwork").ifBlank { optString("cover") }
        val url = optString("url")
        val durationMs = when {
            optLong("duration", 0L) in 1L..86_400L -> optLong("duration") * 1000L
            else -> optLong("duration", 0L)
        }
        val id = "musicfree_${platform}_$onlineId".hashCode().toLong()
        return MusicFreeOnlineSong(
            song = Song(
                id = id,
                title = title,
                artist = artist,
                album = album,
                albumId = 0L,
                duration = durationMs,
                path = url,
                fileName = "$title-$artist.mp3",
                mimeType = "audio/mpeg",
                coverUrl = artwork,
                onlineSource = "musicfree:$platform",
                onlineId = onlineId
            ),
            pluginName = platform,
            rawJson = toString(),
            coverUrl = artwork
        )
    }

    private fun bestQuality(rawJson: String): String {
        val item = runCatching { JSONObject(rawJson) }.getOrNull() ?: return "standard"
        val qualities = item.optJSONObject("qualities") ?: return "standard"
        return when {
            qualities.has("super") -> "super"
            qualities.has("high") -> "high"
            qualities.has("standard") -> "standard"
            qualities.has("low") -> "low"
            else -> "standard"
        }
    }

    private fun mimeTypeFromExtension(ext: String): String {
        return when (ext.lowercase()) {
            "flac" -> "audio/flac"
            "m4a" -> "audio/mp4"
            "ogg", "opus" -> "audio/ogg"
            "wav" -> "audio/wav"
            else -> "audio/mpeg"
        }
    }

    private fun JSONArray.toJsonObjects(): List<JSONObject> {
        return List(length()) { index -> optJSONObject(index) }.filterNotNull()
    }

    companion object {
        private const val USER_AGENT = "Mozilla/5.0 (Linux; Android 15) AppleWebKit/537.36 EllaMusic/1.0"
    }
}
