package com.ella.music.data.repository

import android.content.Context
import android.media.MediaFormat
import android.net.Uri
import com.ella.music.data.LibraryNormalizer
import com.ella.music.data.SettingsManager
import com.ella.music.data.isContentAudioSource
import com.ella.music.data.isHttpAudioSource
import com.ella.music.data.looksLikeNeteaseKeyValue
import com.ella.music.data.model.Song
import com.ella.music.data.metadata.AudioTagInfo
import com.ella.music.data.webdav.WebDavClient
import com.ella.music.data.webdav.WebDavConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import java.io.File
import java.security.MessageDigest

internal fun String.sha256(): String {
    val digest = MessageDigest.getInstance("SHA-256").digest(toByteArray(Charsets.UTF_8))
    return digest.joinToString("") { "%02x".format(it) }
}

internal fun Song.metadataCachePrefix(): String {
    val source = when {
        path.isNotBlank() -> "path:$path"
        onlineSource.isNotBlank() || onlineId.isNotBlank() -> "online:$onlineSource:$onlineId:$path"
        else -> "media:$id:$title:$artist:$album:$duration"
    }
    return source.sha256()
}

internal fun Song.metadataCacheKey(): String =
    "${metadataCachePrefix()}:$dateModified:$fileSize"

internal fun Song.coverCacheKey(): String {
    val source = when {
        path.isNotBlank() -> path
        onlineSource.isNotBlank() || onlineId.isNotBlank() -> "$onlineSource:$onlineId"
        else -> "$id:$title:$artist:$album"
    }
    return source.sha256()
}

internal fun Song.coverDataCacheKey(): String =
    "${coverCacheKey()}:$dateModified:$fileSize"

internal fun Song.searchSnapshotKey(): String =
    "${id}|${path.sha256()}"

internal fun Song.isWebDavRemoteSong(): Boolean =
    path.isHttpAudioSource() && onlineSource.isBlank()

internal fun Song.webDavCacheExtension(): String =
    fileName.substringAfterLast('.', path.substringBefore('?').substringBefore('#').substringAfterLast('.', "audio"))
        .ifBlank { "audio" }

internal fun Song.isLikelyWavAudio(): Boolean =
    webDavCacheExtension().lowercase() in setOf("wav", "wave") ||
        mimeType.contains("wav", ignoreCase = true) ||
        mimeType.contains("wave", ignoreCase = true)

internal fun Song.webDavFullCacheFile(cacheDir: File): File =
    File(cacheDir, "${path.sha256()}.${webDavCacheExtension()}")

internal fun Song.webDavHeaderCacheFile(cacheDir: File): File =
    File(cacheDir, "${path.sha256()}.${webDavCacheExtension()}")

internal suspend fun Song.effectiveLocalPathForMetadata(
    context: Context,
    settingsManager: SettingsManager,
    httpClient: OkHttpClient,
    remoteAudioCacheDir: File,
    remoteMetadataHeaderCacheDir: File,
    allowFullDownload: Boolean = false
): String = withContext(Dispatchers.IO) {
    if (path.isContentAudioSource()) return@withContext path
    if (!isWebDavRemoteSong()) return@withContext path
    val fullCache = webDavFullCacheFile(remoteAudioCacheDir)
    if (fullCache.exists() && fullCache.length() > 0L) return@withContext fullCache.absolutePath
    val headerCache = webDavHeaderCacheFile(remoteMetadataHeaderCacheDir)
    if (headerCache.exists() && headerCache.length() > 0L) return@withContext headerCache.absolutePath
    val config = loadWebDavConfig(settingsManager) ?: return@withContext path
    downloadWebDavMetadataHeader(this@effectiveLocalPathForMetadata, config, remoteMetadataHeaderCacheDir)?.let { return@withContext it.absolutePath }
    if (!allowFullDownload) return@withContext path
    return@withContext runCatching {
        WebDavClient.downloadToFile(path, config, fullCache).absolutePath
    }.getOrElse {
        android.util.Log.w("MusicRepo", "Failed to cache remote metadata file for $path", it)
        path
    }
}

internal fun Song.effectiveLocalPathForMetadataBlocking(
    settingsManager: SettingsManager,
    httpClient: OkHttpClient,
    remoteAudioCacheDir: File,
    remoteMetadataHeaderCacheDir: File,
    allowFullDownload: Boolean = false
): String {
    if (path.isContentAudioSource()) return path
    if (!isWebDavRemoteSong()) return path
    val fullCache = webDavFullCacheFile(remoteAudioCacheDir)
    if (fullCache.exists() && fullCache.length() > 0L) return fullCache.absolutePath
    val headerCache = webDavHeaderCacheFile(remoteMetadataHeaderCacheDir)
    if (headerCache.exists() && headerCache.length() > 0L) return headerCache.absolutePath
    val config = runBlocking(Dispatchers.IO) { loadWebDavConfig(settingsManager) } ?: return path
    downloadWebDavMetadataHeader(this, config, remoteMetadataHeaderCacheDir)?.let { return it.absolutePath }
    if (!allowFullDownload) return path
    return runCatching {
        WebDavClient.downloadToFile(path, config, fullCache).absolutePath
    }.getOrElse {
        android.util.Log.w("MusicRepo", "Failed to cache remote metadata file for $path", it)
        path
    }
}

internal suspend fun loadWebDavConfig(settingsManager: SettingsManager): WebDavConfig? {
    val url = settingsManager.webDavUrl.first().trim()
    if (url.isBlank()) return null
    return WebDavConfig(
        url = url,
        username = settingsManager.webDavUsername.first(),
        password = settingsManager.webDavPassword.first()
    )
}

internal fun downloadWebDavMetadataHeader(song: Song, config: WebDavConfig, cacheDir: File): File? {
    val target = song.webDavHeaderCacheFile(cacheDir)
    if (target.exists() && target.length() > 0L) return target
    return WebDavClient.downloadHeaderToFile(song.path, config, target)
}

internal fun AudioTagInfo.embeddedLyricsContent(preferTtml: Boolean): String? {
    val names = if (preferTtml) {
        listOf("TTML LYRICS", "TTML LYRIC", "TTMLLYRICS", "TTMLLYRIC", "TTML",
            "SYNCEDLYRICS", "LYRICS", "UNSYNCEDLYRICS", "UNSYNCED LYRICS", "USLT", "SYLT", "LYRIC")
    } else {
        listOf("SYNCEDLYRICS", "LYRICS", "UNSYNCEDLYRICS", "UNSYNCED LYRICS", "USLT", "SYLT", "LYRIC")
    }
    names.forEach { target ->
        customTags.firstMatchingTagValue(target)?.takeIf { it.looksLikeTtmlLyrics() == preferTtml }?.let { return it }
    }
    return lyrics?.takeIf { it.isNotBlank() && (preferTtml == it.looksLikeTtmlLyrics()) }
}

internal fun Map<String, List<String>>.firstMatchingTagValue(target: String): String? {
    val normalizedTarget = target.normalizedTagName()
    return entries.firstOrNull { (key, values) ->
        key.normalizedTagName() == normalizedTarget && values.any { it.isNotBlank() }
    }?.value?.firstOrNull { it.isNotBlank() }
}

internal fun String.normalizedTagName(): String =
    uppercase().filter { it.isLetterOrDigit() }

internal fun String.looksLikeTtmlLyrics(): Boolean =
    contains("<tt", ignoreCase = true) && contains("</tt", ignoreCase = true)

internal fun findExternalLyricContentByFormat(songPath: String, preferTtml: Boolean): String? {
    val extensions = if (preferTtml) listOf("ttml") else listOf("lrc", "elrc")
    val baseName = songPath.substringBeforeLast('.')
    extensions.forEach { ext ->
        readTextIfExists("$baseName.$ext")?.let { return it }
    }
    val parentDir = File(songPath).parentFile ?: return null
    val songName = File(songPath).nameWithoutExtension
    return runCatching {
        parentDir.listFiles()
            ?.filter { file -> extensions.any { file.extension.equals(it, ignoreCase = true) } }
            ?.sortedWith(compareBy<File> { extensions.indexOf(it.extension.lowercase()) }.thenBy { it.name })
            ?.firstOrNull { it.nameWithoutExtension.contains(songName, ignoreCase = true) }
            ?.let { readTextIfExists(it.absolutePath) }
    }.getOrNull()
}

internal fun readTextIfExists(path: String): String? =
    runCatching {
        val file = File(path)
        if (!file.exists()) return null
        file.readText()
    }.getOrNull()

internal fun String.extractYearInt(): Int? =
    Regex("""\d{4}""").find(this)?.value?.toIntOrNull()

internal fun String?.usableTagText(): String =
    LibraryNormalizer.cleanedTagText(this)

internal fun String?.usableArtistText(): String =
    LibraryNormalizer.cleanedArtistText(this)

internal fun String?.usableAlbumText(): String =
    LibraryNormalizer.cleanedAlbumText(this)

internal fun String?.isUsableTagText(): Boolean =
    usableTagText().isNotBlank()

internal fun String?.isUsableArtistText(): Boolean =
    usableArtistText().isNotBlank()

internal fun String?.isUsableAlbumText(): Boolean =
    usableAlbumText().isNotBlank()

internal fun String.looksLikeLastFolderName(path: String): Boolean =
    LibraryNormalizer.looksLikeLastFolderName(this, path)

internal fun AudioTagInfo.toSongTagInfo(): com.ella.music.data.model.SongTagInfo =
    com.ella.music.data.model.SongTagInfo(
        title = title.orEmpty(),
        artist = artist.orEmpty(),
        album = album.orEmpty(),
        albumArtist = albumArtist.orEmpty(),
        genre = genre.orEmpty(),
        year = year.orEmpty(),
        composer = composer.orEmpty(),
        lyricist = lyricist.orEmpty(),
        track = trackNumber?.toString().orEmpty(),
        comment = comment.orEmpty(),
        copyright = copyright.orEmpty(),
        neteaseKey = neteaseKey.orEmpty(),
        rating = rating.normalizeTagRatingToStars(),
        customTagText = customTags.flattenForSearch()
    )

internal fun Map<String, List<String>>.flattenForSearch(): String =
    entries.asSequence()
        .filterNot { (key, _) -> key.isIgnoredSearchTagKey() }
        .flatMap { (key, values) ->
            sequence {
                yield(key)
                values.forEach { value ->
                    val text = value.trim()
                    if (text.isNotBlank() && !text.looksLikeNeteaseKeyValue()) yield(text)
                }
            }
        }
        .distinct()
        .take(80)
        .joinToString(" ")

internal fun Int?.normalizeTagRatingToStars(): Int {
    val raw = this ?: return 0
    return when {
        raw <= 0 -> 0
        raw <= 5 -> raw
        raw <= 100 -> kotlin.math.round(raw / 20f).toInt()
        raw <= 255 -> kotlin.math.round(raw / 255f * 5f).toInt()
        else -> 0
    }.coerceIn(0, 5)
}

internal fun String.isIgnoredSearchTagKey(): Boolean {
    val normalized = trim().lowercase()
    return normalized in setOf(
        "apic", "covr", "picture", "metadata_block_picture",
        "unsyncedlyrics", "uslt", "lyrics", "lyric", "syncedlyrics",
        "replaygain_track_gain", "replaygain_track_peak",
        "replaygain_album_gain", "replaygain_album_peak",
        "replaygain_reference_loudness"
    )
}

internal fun String.normalizedLocalFolderPath(): String? {
    val normalized = trim().replace('\\', '/').trimEnd('/').lowercase()
    return normalized.takeIf { it.isNotBlank() }
}

internal fun String.isAllowedByLocalFolderFilters(
    includeFolders: List<String>,
    excludeFolders: List<String>
): Boolean {
    val normalizedPath = replace('\\', '/').lowercase()
    val included = includeFolders.isEmpty() || includeFolders.any { folder ->
        normalizedPath == folder || normalizedPath.startsWith("$folder/")
    }
    if (!included) return false
    return excludeFolders.none { folder ->
        normalizedPath == folder || normalizedPath.startsWith("$folder/")
    }
}

internal fun Song.audioFormatLabel(mime: String?, estimatedBitRate: () -> Int): String {
    val source = (mime ?: mimeType).lowercase()
    val extensionSource = fileName.takeIf { it.substringAfterLast('.', "").isNotBlank() }
        ?: path.substringBefore('?').substringBefore('#')
    val extension = extensionSource.substringAfterLast('.', "").lowercase()
    return when {
        "flac" in source || extension == "flac" -> "FLAC"
        "mpeg" in source || "mp3" in source || extension == "mp3" -> "MP3"
        "wav" in source || extension == "wav" -> "WAV"
        "eac3" in source || "e-ac-3" in source || "ec-3" in source || extension == "ec3" || extension == "eac3" -> "EC3"
        "ac3" in source || "ac-3" in source || extension == "ac3" -> "AC3"
        "aac" in source || extension == "aac" -> "AAC"
        "alac" in source || "audio/alac" in source -> "ALAC"
        extension == "m4a" && estimatedBitRate() >= 700_000 -> "ALAC"
        extension == "m4a" -> "AAC"
        "mp4" in source || "m4a" in source || extension == "m4a" || extension == "mp4" -> "M4A"
        "ogg" in source || extension == "ogg" -> "OGG"
        "opus" in source || extension == "opus" -> "OPUS"
        extension.isNotBlank() -> extension.uppercase()
        else -> "Audio"
    }
}

internal fun MediaFormat.getIntOrZero(key: String): Int {
    return if (containsKey(key)) runCatching { getInteger(key) }.getOrDefault(0) else 0
}

internal fun String.webDavSafeLogUrl(): String =
    runCatching {
        val uri = java.net.URI(this)
        if (uri.userInfo == null) this
        else java.net.URI(uri.scheme, "***", uri.host, uri.port, uri.path, uri.query, uri.fragment).toString()
    }.getOrDefault(this)

internal fun <K, V> java.util.concurrent.ConcurrentHashMap<K, V>.removeKeysMatching(predicate: (K) -> Boolean) {
    keys.toList().forEach { key ->
        if (predicate(key)) remove(key)
    }
}
