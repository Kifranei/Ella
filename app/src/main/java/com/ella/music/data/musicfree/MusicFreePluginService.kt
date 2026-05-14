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
import java.net.URI
import java.util.concurrent.TimeUnit

data class MusicFreeOnlineSong(
    val song: Song,
    val pluginName: String,
    val rawJson: String,
    val coverUrl: String = ""
)

data class MusicFreeImportResult(
    val plugins: List<MusicFreePluginConfig>,
    val skippedCount: Int = 0
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

    suspend fun importPlugins(url: String): MusicFreeImportResult = withContext(Dispatchers.IO) {
        val sourceUrl = url.trim()
        val body = fetchText(sourceUrl)
        parsePluginHub(body, sourceUrl)?.let { entries ->
            val plugins = mutableListOf<MusicFreePluginConfig>()
            var skipped = 0
            entries.forEach { entry ->
                runCatching {
                    val script = fetchText(entry.url)
                    val (detectedName, normalizedScript) = importPluginScript(script)
                    MusicFreePluginConfig(
                        id = "musicfree_${entry.url.hashCode()}",
                        url = entry.url,
                        name = entry.name.ifBlank { detectedName },
                        script = normalizedScript
                    )
                }.onSuccess { plugins += it }
                    .onFailure { skipped += 1 }
            }
            if (plugins.isEmpty()) error("插件仓库里没有可导入的 MusicFree 音乐插件")
            return@withContext MusicFreeImportResult(plugins = plugins, skippedCount = skipped)
        }
        val (name, script) = importPluginScript(body)
        MusicFreeImportResult(
            plugins = listOf(
                MusicFreePluginConfig(
                    id = "musicfree_${sourceUrl.hashCode()}",
                    url = sourceUrl,
                    name = name,
                    script = script
                )
            )
        )
    }

    fun importPluginScript(script: String): Pair<String, String> {
        if (script.length !in 50..9_000_000) error("插件脚本内容异常")
        val name = extractPlatform(script)
        if (name.isBlank()) error("没有识别到 MusicFree 插件 platform")
        val supportsMusic = "search" in script || "getMediaSource" in script || "importMusicItem" in script
        if (!supportsMusic) error("插件未声明搜索或播放能力")
        return name to script
    }

    private fun fetchText(url: String): String {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", USER_AGENT)
            .header("Cache-Control", "no-cache")
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) error("导入失败: HTTP ${response.code}")
            return response.body?.string().orEmpty()
        }
    }

    private fun parsePluginHub(content: String, baseUrl: String): List<PluginHubEntry>? {
        val trimmed = content.trimStart()
        if (!trimmed.startsWith("{") && !trimmed.startsWith("[")) return null
        val array = runCatching {
            if (trimmed.startsWith("[")) JSONArray(trimmed)
            else JSONObject(trimmed).optJSONArray("plugins") ?: return null
        }.getOrNull() ?: return null
        val base = runCatching { URI(baseUrl) }.getOrNull()
        return List(array.length()) { index ->
            val item = array.optJSONObject(index) ?: return@List null
            val rawUrl = item.optString("url").ifBlank { item.optString("srcUrl") }
            if (rawUrl.isBlank()) return@List null
            val resolvedUrl = runCatching { base?.resolve(rawUrl)?.toString() ?: rawUrl }.getOrDefault(rawUrl)
            PluginHubEntry(
                name = item.optString("name").ifBlank { item.optString("platform") },
                url = resolvedUrl
            )
        }.filterNotNull()
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

    private data class PluginHubEntry(
        val name: String,
        val url: String
    )

    companion object {
        private const val USER_AGENT = "Mozilla/5.0 (Linux; Android 15) AppleWebKit/537.36 EllaMusic/1.0"
    }
}
