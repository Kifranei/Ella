package com.ella.music.data.webdav

import android.text.Html
import android.util.Log
import com.ella.music.data.AppNetworkLoggingInterceptor
import okhttp3.Credentials
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.w3c.dom.Element
import org.xml.sax.InputSource
import java.io.File
import java.io.IOException
import java.io.StringReader
import java.net.URI
import java.net.SocketTimeoutException
import java.net.URLDecoder
import java.net.UnknownHostException
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Locale
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLHandshakeException
import javax.xml.parsers.DocumentBuilderFactory

enum class WebDavAuthMode {
    AUTO,
    BASIC,
    DIGEST
}

data class WebDavConfig(
    val url: String,
    val username: String,
    val password: String,
    val authMode: WebDavAuthMode = WebDavAuthMode.AUTO
) {
    val isConfigured: Boolean get() = url.trim().isNotBlank()
}

data class WebDavItem(
    val name: String,
    val url: String,
    val isDirectory: Boolean,
    val size: Long = 0L,
    val mimeType: String = ""
)

data class WebDavTestResult(
    val ok: Boolean,
    val message: String
)

class WebDavException(message: String) : IOException(message)

object WebDavClient {
    private const val TAG = "WebDavClient"
    private val audioExtensions = setOf("mp3", "m4a", "flac", "wav", "ogg", "opus", "aac", "alac")
    private val listCache = mutableMapOf<String, List<WebDavItem>>()
    private val xmlMediaType = "application/xml; charset=utf-8".toMediaType()
    private val secureRandom = SecureRandom()
    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(12, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .addInterceptor(AppNetworkLoggingInterceptor(TAG))
        .authenticator { _, response ->
            response.request.tag(WebDavConfig::class.java)?.let { config ->
                authenticate(response, config)
            }
        }
        .build()

    fun newAuthenticatedOkHttpClient(configProvider: () -> WebDavConfig): OkHttpClient {
        return OkHttpClient.Builder()
            .connectTimeout(8, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .addInterceptor(AppNetworkLoggingInterceptor(TAG))
            .authenticator { _, response -> authenticate(response, configProvider()) }
            .addInterceptor { chain ->
                val config = configProvider()
                val request = chain.request().newBuilder()
                    .tag(WebDavConfig::class.java, config)
                    .apply { applyPreemptiveBasicAuth(config) }
                    .build()
                chain.proceed(request)
            }
            .build()
    }

    fun isAudioFile(name: String): Boolean {
        val ext = name.substringAfterLast('.', "").lowercase()
        return ext in audioExtensions
    }

    fun test(config: WebDavConfig): Boolean {
        return testDetailed(config).ok
    }

    fun testDetailed(config: WebDavConfig): WebDavTestResult {
        if (!config.isConfigured) {
            return WebDavTestResult(ok = false, message = "请先输入 WebDAV 地址")
        }
        return runCatching {
            val response = executePropfind(normalizeCollectionUrl(config.url), config, depth = "0")
            if (response.code in 200..399) {
                Log.i(TAG, "WebDAV test succeeded: ${config.url.safeLogUrl()} code=${response.code}")
                WebDavTestResult(ok = true, message = "WebDAV 连接成功")
            } else {
                val message = response.toFriendlyMessage()
                Log.w(TAG, "WebDAV test failed: ${config.url.safeLogUrl()} code=${response.code} message=$message")
                WebDavTestResult(ok = false, message = message)
            }
        }.getOrElse { error ->
            Log.e(TAG, "WebDAV test failed", error)
            WebDavTestResult(ok = false, message = error.toFriendlyMessage())
        }
    }

    fun list(config: WebDavConfig, url: String = config.url, forceRefresh: Boolean = false): List<WebDavItem> {
        if (!config.isConfigured) return emptyList()
        val requestUrl = normalizeCollectionUrl(url)
        val cacheKey = "${requestUrl}|${config.username}|${config.authMode}"
        if (!forceRefresh) listCache[cacheKey]?.let { return it }
        val propfind = executePropfind(requestUrl, config, depth = "1")
        val response = propfind.body
        if (propfind.code !in 200..399) {
            val message = propfind.toFriendlyMessage()
            Log.w(TAG, "WebDAV list failed: ${requestUrl.safeLogUrl()} code=${propfind.code} message=$message")
            throw WebDavException(message)
        }
        if (response.isBlank()) return emptyList()

        return parseItems(response, requestUrl)
            .filterNot { normalizeCollectionUrl(it.url) == requestUrl }
            .filter { it.isDirectory || isAudioFile(it.name) }
            .sortedWith(compareByDescending<WebDavItem> { it.isDirectory }.thenBy(String.CASE_INSENSITIVE_ORDER) { it.name })
            .also { listCache[cacheKey] = it }
    }

    fun clearListCache() {
        listCache.clear()
    }

    fun downloadToFile(url: String, config: WebDavConfig, target: File): File {
        val request = Request.Builder()
            .url(normalizeRequestUrl(url))
            .get()
            .tag(WebDavConfig::class.java, config)
            .apply { applyPreemptiveBasicAuth(config) }
            .build()

        httpClient.newCall(request).execute().use { response ->
            if (response.code !in 200..399) {
                throw WebDavException(WebDavResponse(response.code, response.body?.string().orEmpty()).toFriendlyMessage())
            }
            val body = response.body ?: throw WebDavException("WebDAV 文件下载失败")
            target.parentFile?.mkdirs()
            target.outputStream().use { output ->
                body.byteStream().use { input -> input.copyTo(output) }
            }
        }
        return target
    }

    private fun parseItems(xml: String, baseUrl: String): List<WebDavItem> {
        return runCatching {
            val document = DocumentBuilderFactory.newInstance().apply {
                isNamespaceAware = true
                isIgnoringComments = true
            }.newDocumentBuilder().parse(InputSource(StringReader(xml)))
            val responses = document.getElementsByTagNameNS("*", "response")
            (0 until responses.length).mapNotNull { index ->
                val response = responses.item(index) as? Element ?: return@mapNotNull null
                val href = response.firstText("href").ifBlank { return@mapNotNull null }
                val itemUrl = resolveHref(baseUrl, href)
                val decodedPath = URLDecoder.decode(URI(itemUrl).path.substringAfterLast('/'), "UTF-8")
                val name = decodedPath.ifBlank { itemUrl.trimEnd('/').substringAfterLast('/').decodeUrlPart() }
                val resourceType = response.getElementsByTagNameNS("*", "collection")
                val isDirectory = resourceType.length > 0 || itemUrl.endsWith("/")
                val size = response.firstText("getcontentlength").toLongOrNull() ?: 0L
                val mimeType = response.firstText("getcontenttype")
                WebDavItem(
                    name = Html.fromHtml(name, Html.FROM_HTML_MODE_LEGACY).toString(),
                    url = itemUrl,
                    isDirectory = isDirectory,
                    size = size,
                    mimeType = mimeType
                )
            }
        }.getOrDefault(emptyList())
    }

    private fun executePropfind(url: String, config: WebDavConfig, depth: String): WebDavResponse {
        return executePropfind(url, config, depth, useXmlBody = true).let { response ->
            if (response.code == 400) {
                Log.w(TAG, "WebDAV PROPFIND got 400, retrying with empty body: ${normalizeRequestUrl(url).safeLogUrl()}")
                executePropfind(url, config, depth, useXmlBody = false)
            } else {
                response
            }
        }
    }

    private fun executePropfind(url: String, config: WebDavConfig, depth: String, useXmlBody: Boolean): WebDavResponse {
        val requestUrl = normalizeRequestUrl(url)
        Log.i(TAG, "WebDAV PROPFIND depth=$depth body=${if (useXmlBody) "xml" else "empty"} url=${requestUrl.safeLogUrl()}")
        val body = (if (useXmlBody) BASIC_PROPFIND else "").toRequestBody(xmlMediaType)
        val request = Request.Builder()
            .url(requestUrl)
            .method("PROPFIND", body)
            .tag(WebDavConfig::class.java, config)
            .header("Depth", depth)
            .header("Accept", "application/xml, text/xml, */*")
            .header("Content-Type", "application/xml; charset=utf-8")
            .apply { applyPreemptiveBasicAuth(config) }
            .build()

        return httpClient.newCall(request).execute().use { response ->
            Log.i(TAG, "WebDAV PROPFIND response depth=$depth url=${requestUrl.safeLogUrl()} code=${response.code}")
            WebDavResponse(
                code = response.code,
                body = response.body?.string().orEmpty()
            )
        }
    }

    private data class WebDavResponse(val code: Int, val body: String)

    private fun Request.Builder.applyPreemptiveBasicAuth(config: WebDavConfig) {
        if ((config.username.isNotBlank() || config.password.isNotBlank()) && config.authMode != WebDavAuthMode.DIGEST) {
            header("Authorization", Credentials.basic(config.username, config.password, Charsets.UTF_8))
        }
    }

    private fun authenticate(response: Response, config: WebDavConfig): Request? {
        if (config.username.isBlank()) return null
        if (responseCount(response) >= 3) {
            Log.w(TAG, "WebDAV auth retry limit reached: ${response.request.url.toString().safeLogUrl()}")
            return null
        }
        val challenges = parseAuthChallenges(response.headers("WWW-Authenticate"))
        val existingAuth = response.request.header("Authorization")
        val digestHeader = challenges["Digest"]
        val basicHeader = challenges["Basic"]
        return when (config.authMode) {
            WebDavAuthMode.DIGEST -> digestHeader?.let { response.digestRequest(config, it) }
            WebDavAuthMode.BASIC -> basicHeader?.let { response.basicRequest(config) }
            WebDavAuthMode.AUTO -> {
                digestHeader?.let { response.digestRequest(config, it) }
                    ?: if (existingAuth?.startsWith("Basic", ignoreCase = true) != true) {
                        basicHeader?.let { response.basicRequest(config) }
                    } else {
                        null
                    }
            }
        }
    }

    private fun responseCount(response: Response): Int {
        var count = 1
        var prior = response.priorResponse
        while (prior != null) {
            count++
            prior = prior.priorResponse
        }
        return count
    }

    private fun parseAuthChallenges(headers: List<String>): Map<String, String> {
        val result = linkedMapOf<String, String>()
        headers.forEach { header ->
            val value = header.trim()
            when {
                value.startsWith("Digest", ignoreCase = true) -> result["Digest"] = value
                value.startsWith("Basic", ignoreCase = true) -> result["Basic"] = value
                value.contains("Digest", ignoreCase = true) -> {
                    value.substring(value.indexOf("Digest", ignoreCase = true)).let { result["Digest"] = it }
                }
                value.contains("Basic", ignoreCase = true) -> {
                    value.substring(value.indexOf("Basic", ignoreCase = true)).let { result["Basic"] = it }
                }
            }
        }
        return result
    }

    private fun Response.basicRequest(config: WebDavConfig): Request {
        Log.i(TAG, "WebDAV using Basic auth: ${request.url.toString().safeLogUrl()}")
        return request.newBuilder()
            .header("Authorization", Credentials.basic(config.username, config.password, Charsets.UTF_8))
            .build()
    }

    private fun Response.digestRequest(config: WebDavConfig, authHeader: String): Request? {
        return runCatching {
            val realm = authHeader.authParam("realm") ?: return null
            val nonce = authHeader.authParam("nonce") ?: return null
            val opaque = authHeader.authParam("opaque")
            val algorithm = authHeader.authParam("algorithm") ?: "MD5"
            val qop = authHeader.authParam("qop")
                ?.split(',')
                ?.map { it.trim().trim('"') }
                ?.firstOrNull { it.equals("auth", ignoreCase = true) }
            val url = request.url
            val digestUri = url.encodedPath + url.encodedQuery?.let { "?$it" }.orEmpty()
            val method = request.method
            val cnonce = generateCnonce()
            val nc = "00000001"
            val hash = algorithm.substringBefore("-").uppercase(Locale.ROOT)
            val ha1Base = digestHash(hash, "${config.username}:$realm:${config.password}")
            val ha1 = if (algorithm.endsWith("-sess", ignoreCase = true)) {
                digestHash(hash, "$ha1Base:$nonce:$cnonce")
            } else {
                ha1Base
            }
            val ha2 = digestHash(hash, "$method:$digestUri")
            val digestResponse = if (qop != null) {
                digestHash(hash, "$ha1:$nonce:$nc:$cnonce:$qop:$ha2")
            } else {
                digestHash(hash, "$ha1:$nonce:$ha2")
            }
            val authValue = buildString {
                append("Digest ")
                append("""username="${config.username}", """)
                append("""realm="$realm", """)
                append("""nonce="$nonce", """)
                append("""uri="$digestUri", """)
                append("""response="$digestResponse"""")
                append(""", algorithm=$algorithm""")
                if (opaque != null) append(""", opaque="$opaque"""")
                if (qop != null) {
                    append(""", qop=$qop""")
                    append(""", nc=$nc""")
                    append(""", cnonce="$cnonce"""")
                }
            }
            Log.i(TAG, "WebDAV using Digest auth: ${request.url.toString().safeLogUrl()} algorithm=$algorithm")
            request.newBuilder()
                .header("Authorization", authValue)
                .build()
        }.getOrElse { error ->
            Log.w(TAG, "WebDAV Digest auth failed", error)
            null
        }
    }

    private fun String.authParam(name: String): String? {
        val quoted = Regex("""(?i)(?:^|,\s*)${Regex.escape(name)}\s*=\s*"([^"]*)"""").find(this)
        if (quoted != null) return quoted.groupValues[1]
        return Regex("""(?i)(?:^|,\s*)${Regex.escape(name)}\s*=\s*([^,\s]+)""")
            .find(this)
            ?.groupValues
            ?.getOrNull(1)
            ?.trim('"')
    }

    private fun digestHash(algorithm: String, input: String): String {
        val digestAlgorithm = when (algorithm.uppercase(Locale.ROOT)) {
            "SHA-256" -> "SHA-256"
            else -> "MD5"
        }
        return MessageDigest.getInstance(digestAlgorithm)
            .digest(input.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
    }

    private fun generateCnonce(): String {
        val bytes = ByteArray(8)
        secureRandom.nextBytes(bytes)
        return bytes.joinToString("") { "%02x".format(it) }
    }

    private fun WebDavResponse.toFriendlyMessage(): String {
        return when (code) {
            400 -> "HTTP 400：WebDAV 请求格式被服务器拒绝，请确认地址指向 WebDAV 根目录或子目录"
            401 -> "HTTP 401：用户名或密码错误"
            403 -> "HTTP 403：没有访问权限"
            404 -> "HTTP 404：路径不存在，请确认 WebDAV 地址"
            405 -> "HTTP 405：服务器不允许 PROPFIND，请确认填的是 WebDAV 地址"
            in 300..399 -> "HTTP $code：服务器重定向异常，请检查 WebDAV 地址是否需要末尾斜杠"
            else -> {
                val detail = body.lineSequence()
                    .map { it.trim() }
                    .firstOrNull { it.isNotBlank() }
                    ?.take(120)
                    .orEmpty()
                if (detail.isBlank()) "HTTP $code：WebDAV 服务器返回错误" else "HTTP $code：$detail"
            }
        }
    }

    private fun Throwable.toFriendlyMessage(): String {
        val rawMessage = localizedMessage.orEmpty()
        return when (this) {
            is IllegalArgumentException -> rawMessage.ifBlank { "WebDAV 地址格式不正确" }
            is UnknownHostException -> "无法解析主机，请检查地址或网络"
            is SocketTimeoutException -> "连接超时，请检查服务器地址和网络"
            is SSLHandshakeException -> "TLS/证书验证失败，请检查 HTTPS 证书"
            is WebDavException -> rawMessage.ifBlank { "WebDAV 加载失败" }
            is IOException -> {
                if (rawMessage.contains("CLEARTEXT", ignoreCase = true)) {
                    "HTTP 明文连接被系统拦截，请重新安装后再试"
                } else {
                    rawMessage.ifBlank { "网络连接失败" }
                }
            }
            else -> rawMessage.ifBlank { "WebDAV 连接失败" }
        }
    }

    private fun resolveHref(baseUrl: String, href: String): String {
        return runCatching {
            URI(baseUrl).resolve(href).toString()
        }.getOrElse { href }
    }

    private fun normalizeCollectionUrl(url: String): String {
        val trimmed = normalizeRequestUrl(url)
        return if (trimmed.endsWith("/")) trimmed else "$trimmed/"
    }

    private fun normalizeRequestUrl(url: String): String {
        val trimmed = url.trim()
        require(trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
            "WebDAV 地址需要以 http:// 或 https:// 开头"
        }
        return trimmed
    }

    fun displayUrl(url: String): String {
        return runCatching {
            val uri = URI(url)
            val decodedPath = uri.rawPath.orEmpty().decodeUrlPart()
            buildString {
                append(uri.scheme).append("://").append(uri.host ?: "")
                if (uri.port >= 0) append(":").append(uri.port)
                append(decodedPath)
                if (!uri.rawQuery.isNullOrBlank()) append("?").append(uri.rawQuery.decodeUrlPart())
                if (!uri.rawFragment.isNullOrBlank()) append("#").append(uri.rawFragment.decodeUrlPart())
            }
        }.getOrDefault(url.decodeUrlPart())
    }

    private fun String.decodeUrlPart(): String {
        return runCatching { URLDecoder.decode(this, "UTF-8") }.getOrDefault(this)
    }

    private fun String.safeLogUrl(): String {
        return runCatching {
            val uri = URI(this)
            if (uri.userInfo == null) {
                this
            } else {
                URI(uri.scheme, "***", uri.host, uri.port, uri.path, uri.query, uri.fragment).toString()
            }
        }.getOrDefault(this)
    }

    private fun Element.firstText(localName: String): String {
        val nodes = getElementsByTagNameNS("*", localName)
        return (nodes.item(0)?.textContent ?: "").trim()
    }

    private val BASIC_PROPFIND = """
        <?xml version="1.0" encoding="utf-8" ?>
        <D:propfind xmlns:D="DAV:">
          <D:prop>
            <D:resourcetype/>
            <D:getcontentlength/>
            <D:getcontenttype/>
          </D:prop>
        </D:propfind>
    """.trimIndent().trim()
}
