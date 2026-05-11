package com.ella.music.data.lx

import android.content.Context
import com.ella.music.data.model.Song
import com.ella.music.data.LxSourceConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

data class LxOnlineSong(
    val song: Song,
    val source: String,
    val songmid: String,
    val quality: String,
    val coverUrl: String = ""
)

class LxOnlineService(private val context: Context) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    suspend fun importSource(url: String): Pair<String, String> = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(url.trim())
            .header("User-Agent", USER_AGENT)
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) error("导入失败: HTTP ${response.code}")
            val script = response.body?.string().orEmpty()
            importSourceScript(script)
        }
    }

    fun importSourceScript(script: String): Pair<String, String> {
        if (script.length !in 50..9_000_000) error("源脚本内容异常")
        val name = extractSourceName(script)
        LxUserApiRuntime(context, client).use { runtime ->
            runtime.load(script, script.hashCode().toString(), name, "")
                ?: error("源初始化失败")
        }
        return name to script
    }

    suspend fun search(
        keyword: String,
        sourceConfig: LxSourceConfig?,
        page: Int = 1
    ): List<LxOnlineSong> = withContext(Dispatchers.IO) {
        if (sourceConfig == null) error("请先选择一个 LX 源")
        val encoded = URLEncoder.encode(keyword.trim(), "UTF-8")
        val url = "http://search.kuwo.cn/r.s?client=kt&all=$encoded&pn=${(page - 1).coerceAtLeast(0)}&rn=30" +
            "&uid=794762570&ver=kwplayer_ar_9.2.2.1&vipver=1&show_copyright_off=1&newver=1" +
            "&ft=music&cluster=0&strategy=2012&encoding=utf8&rformat=json&vermerge=1&mobi=1&issubtitle=1"
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", USER_AGENT)
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) error("搜索失败: HTTP ${response.code}")
            val body = response.body?.string().orEmpty()
            val root = JSONObject(body)
            val list = root.optJSONArray("abslist") ?: return@withContext emptyList()
            List(list.length()) { index ->
                val item = list.getJSONObject(index)
                val mid = item.optString("MUSICRID").removePrefix("MUSIC_").ifBlank { item.optString("DC_TARGETID") }
                val title = decodeHtml(item.optString("SONGNAME"))
                val artist = decodeHtml(item.optString("ARTIST")).replace("&", "、")
                val album = decodeHtml(item.optString("ALBUM")).ifBlank { "在线音乐" }
                val durationMs = item.optLong("DURATION", 0L) * 1000L
                val id = "lx_kw_$mid".hashCode().toLong()
                val coverUrl = buildKuwoCoverUrl(item.optString("web_albumpic_short"))
                LxOnlineSong(
                    song = Song(
                        id = id,
                        title = title,
                        artist = artist,
                        album = album,
                        albumId = 0L,
                        duration = durationMs,
                        path = "",
                        fileName = "$title-$artist.mp3",
                        mimeType = "audio/mpeg",
                        coverUrl = coverUrl,
                        onlineSource = "kw",
                        onlineId = mid
                    ),
                    source = "kw",
                    songmid = mid,
                    quality = pickQuality(item.optString("N_MINFO")),
                    coverUrl = coverUrl
                )
            }.filter { it.songmid.isNotBlank() && it.song.title.isNotBlank() }
        }
    }

    suspend fun resolvePlayableSong(item: LxOnlineSong, sourceScript: String = ""): Song = withContext(Dispatchers.IO) {
        runCatching { resolveByImportedSource(item, sourceScript) }
            .getOrNull()
            ?.let { playableUrl ->
            val extension = playableUrl.substringBefore('?')
                .substringAfterLast('.', missingDelimiterValue = "")
                .takeIf { it.length in 2..5 }
                ?: if (item.quality.startsWith("flac")) "flac" else "mp3"
            return@withContext item.song.copy(path = playableUrl, fileName = "${item.song.title}.$extension")
        }

        val format = when (item.quality) {
            "flac", "flac24bit" -> "flac"
            else -> "mp3"
        }
        val url = "http://antiserver.kuwo.cn/anti.s?type=convert_url&rid=MUSIC_${item.songmid}&format=$format&response=url"
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", USER_AGENT)
            .build()
        val playableUrl = client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) error("解析播放地址失败: HTTP ${response.code}")
            response.body?.string()?.trim().orEmpty()
        }
        if (!playableUrl.startsWith("http")) error("解析播放地址失败")
        item.song.copy(path = playableUrl, fileName = "${item.song.title}.${if (format == "flac") "flac" else "mp3"}")
    }

    private fun resolveByImportedSource(item: LxOnlineSong, sourceScript: String): String? {
        if (sourceScript.isNotBlank()) {
            runCatching {
                return LxUserApiRuntime(context, client).use { runtime ->
                    runtime.requestMusicUrl(item, sourceScript, extractSourceName(sourceScript))
                }
            }.onFailure { quickJsError ->
                val config = extractRenderApiConfig(sourceScript)
                if (config == null) throw quickJsError
            }
        }
        val config = extractRenderApiConfig(sourceScript) ?: return null
        val quality = config.bestQuality(item.source, item.quality)
        val url = "${config.apiUrl}/url/${item.source}/${item.songmid}/$quality"
        val request = Request.Builder()
            .url(url)
            .header("Content-Type", "application/json")
            .header("User-Agent", "lx-music-mobile/1.0.0")
            .header("X-Request-Key", config.apiKey)
            .build()
        return client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) error("源解析失败: HTTP ${response.code}")
            val body = response.body?.string().orEmpty()
            val root = JSONObject(body)
            when (root.optInt("code", -1)) {
                0 -> root.optString("url").takeIf { it.startsWith("http") } ?: error("源没有返回播放地址")
                1 -> error("源解析失败: IP 被限制")
                2 -> error("源解析失败: 获取播放地址失败")
                4 -> error("源解析失败: 服务内部错误")
                5 -> error("源解析失败: 请求过于频繁")
                6 -> error("源解析失败: 参数错误")
                else -> error(root.optString("msg").ifBlank { "源解析失败" })
            }
        }
    }

    private fun extractSourceName(script: String): String {
        val currentInfoName = Regex("""name\s*:\s*['"]([^'"]+)['"]""").find(script)?.groupValues?.getOrNull(1)
        val commentName = Regex("""@name\s+(.+)""").find(script)?.groupValues?.getOrNull(1)?.trim()
        return currentInfoName ?: commentName ?: "LX源"
    }

    private fun pickQuality(raw: String): String {
        return when {
            "bitrate:4000" in raw -> "flac24bit"
            "bitrate:2000" in raw -> "flac"
            "bitrate:320" in raw -> "320k"
            else -> "128k"
        }
    }

    private fun buildKuwoCoverUrl(path: String): String {
        val normalized = path.trim()
        return when {
            normalized.startsWith("http://") || normalized.startsWith("https://") -> normalized
            normalized.isNotBlank() -> {
                val highResPath = normalized.replace(Regex("""^\d+/"""), "500/")
                "https://img1.kuwo.cn/star/albumcover/$highResPath"
            }
            else -> ""
        }
    }

    private fun extractRenderApiConfig(script: String): RenderApiConfig? {
        if (script.isBlank() || "/url/" !in script || "X-Request-Key" !in script) return null
        val apiUrl = Regex("""API_URL\s*=\s*['"]([^'"]+)['"]""").find(script)
            ?.groupValues
            ?.getOrNull(1)
            ?.trimEnd('/')
            ?: return null
        val apiKey = Regex("""API_KEY\s*=\s*['"]([^'"]+)['"]""").find(script)
            ?.groupValues
            ?.getOrNull(1)
            ?.replace(Regex("[\r\n\t]"), "")
            ?.takeIf { it.isNotBlank() }
            ?: return null
        val qualitys = mutableMapOf<String, List<String>>()
        Regex("""(\w+)\s*:\s*\[([^\]]+)]""").findAll(script.substringAfter("MUSIC_QUALITY", script)).forEach { match ->
            val source = match.groupValues[1]
            val values = Regex("""['"]([^'"]+)['"]""").findAll(match.groupValues[2]).map { it.groupValues[1] }.toList()
            if (values.isNotEmpty()) qualitys[source] = values
        }
        return RenderApiConfig(apiUrl = apiUrl, apiKey = apiKey, qualitys = qualitys)
    }

    private data class RenderApiConfig(
        val apiUrl: String,
        val apiKey: String,
        val qualitys: Map<String, List<String>>
    ) {
        fun bestQuality(source: String, requested: String): String {
            val available = qualitys[source].orEmpty()
            if (available.isEmpty() || requested in available) return requested
            return when {
                "flac24bit" in available -> "flac24bit"
                "flac" in available -> "flac"
                "320k" in available -> "320k"
                "128k" in available -> "128k"
                else -> available.last()
            }
        }
    }

    private fun decodeHtml(value: String): String = value
        .replace("&amp;", "&")
        .replace("&nbsp;", " ")
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&quot;", "\"")
        .trim()

    companion object {
        private const val USER_AGENT = "Mozilla/5.0 (Linux; Android 15) AppleWebKit/537.36 EllaMusic/1.0"
    }
}
