package com.ella.music.data.webdav

import android.text.Html
import org.w3c.dom.Element
import org.xml.sax.InputSource
import java.io.StringReader
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import java.net.URLDecoder
import java.util.Base64
import javax.xml.parsers.DocumentBuilderFactory

data class WebDavConfig(
    val url: String,
    val username: String,
    val password: String
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

object WebDavClient {
    private val audioExtensions = setOf("mp3", "m4a", "flac", "wav", "ogg", "opus", "aac", "alac")

    fun isAudioFile(name: String): Boolean {
        val ext = name.substringAfterLast('.', "").lowercase()
        return ext in audioExtensions
    }

    fun test(config: WebDavConfig): Boolean {
        if (!config.isConfigured) return false
        val code = openConnection(config.url, config).run {
            requestMethod = "PROPFIND"
            setRequestProperty("Depth", "0")
            doOutput = true
            outputStream.use { it.write(BASIC_PROPFIND.toByteArray(Charsets.UTF_8)) }
            responseCode.also { disconnect() }
        }
        return code in 200..399 || code == 401
    }

    fun list(config: WebDavConfig, url: String = config.url): List<WebDavItem> {
        if (!config.isConfigured) return emptyList()
        val connection = openConnection(url, config).apply {
            requestMethod = "PROPFIND"
            setRequestProperty("Depth", "1")
            doOutput = true
            outputStream.use { it.write(BASIC_PROPFIND.toByteArray(Charsets.UTF_8)) }
        }
        val response = runCatching {
            if (connection.responseCode in 200..399) {
                connection.inputStream.bufferedReader().use { it.readText() }
            } else {
                ""
            }
        }.getOrDefault("")
        connection.disconnect()
        if (response.isBlank()) return emptyList()

        return parseItems(response, normalizeCollectionUrl(url))
            .filterNot { normalizeCollectionUrl(it.url) == normalizeCollectionUrl(url) }
            .filter { it.isDirectory || isAudioFile(it.name) }
            .sortedWith(compareByDescending<WebDavItem> { it.isDirectory }.thenBy(String.CASE_INSENSITIVE_ORDER) { it.name })
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
                val name = decodedPath.ifBlank { itemUrl.trimEnd('/').substringAfterLast('/') }
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

    private fun openConnection(url: String, config: WebDavConfig): HttpURLConnection {
        return (URL(url.trim()).openConnection() as HttpURLConnection).apply {
            connectTimeout = 8_000
            readTimeout = 12_000
            setRequestProperty("Content-Type", "application/xml; charset=utf-8")
            if (config.username.isNotBlank() || config.password.isNotBlank()) {
                val token = Base64.getEncoder()
                    .encodeToString("${config.username}:${config.password}".toByteArray(Charsets.UTF_8))
                setRequestProperty("Authorization", "Basic $token")
            }
        }
    }

    private fun resolveHref(baseUrl: String, href: String): String {
        return runCatching {
            URI(baseUrl).resolve(href).toString()
        }.getOrElse { href }
    }

    private fun normalizeCollectionUrl(url: String): String {
        val trimmed = url.trim()
        return if (trimmed.endsWith("/")) trimmed else "$trimmed/"
    }

    private fun Element.firstText(localName: String): String {
        val nodes = getElementsByTagNameNS("*", localName)
        return (nodes.item(0)?.textContent ?: "").trim()
    }

    private const val BASIC_PROPFIND = """
        <?xml version="1.0" encoding="utf-8" ?>
        <D:propfind xmlns:D="DAV:">
          <D:prop>
            <D:resourcetype/>
            <D:getcontentlength/>
            <D:getcontenttype/>
          </D:prop>
        </D:propfind>
    """
}

