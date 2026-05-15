package com.ella.music.data.repository

import android.content.ContentUris
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import android.provider.MediaStore
import android.util.Log
import android.util.LruCache
import com.ella.music.data.SettingsManager
import com.ella.music.data.model.Album
import com.ella.music.data.model.AudioInfo
import com.ella.music.data.model.LyricLine
import com.ella.music.data.model.Song
import com.ella.music.data.model.albumIdentityId
import com.ella.music.data.parser.LrcParser
import com.ella.music.data.scanner.MusicScanner
import com.ella.music.data.webdav.WebDavClient
import com.ella.music.data.webdav.WebDavConfig
import java.io.File
import java.security.MessageDigest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import java.util.concurrent.ConcurrentHashMap

class MusicRepository(private val context: Context) {

    private val scanner = MusicScanner(context)
    private val settingsManager = SettingsManager(context)
    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    private val _songs = MutableStateFlow<List<Song>>(emptyList())
    val songs: StateFlow<List<Song>> = _songs.asStateFlow()

    private val _albums = MutableStateFlow<List<Album>>(emptyList())
    val albums: StateFlow<List<Album>> = _albums.asStateFlow()

    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    private val _scanProgress = MutableStateFlow(0)
    val scanProgress: StateFlow<Int> = _scanProgress.asStateFlow()

    private val lyricsCache = mutableMapOf<String, List<LyricLine>>()
    private val audioInfoCache = mutableMapOf<Long, AudioInfo>()
    private val replayGainCache = mutableMapOf<Long, Float?>()
    private val coverArtCache = object : LruCache<Long, ByteArray>(8 * 1024) {
        override fun sizeOf(key: Long, value: ByteArray): Int = value.size / 1024
    }
    private val coverBitmapCache = object : LruCache<Long, Bitmap>(16 * 1024) {
        override fun sizeOf(key: Long, value: Bitmap): Int = value.byteCount / 1024
    }
    private val coverArtLock = Any()
    private val missingCoverIds = ConcurrentHashMap.newKeySet<Long>()
    private val libraryCacheFile = File(context.filesDir, "music_library_cache.json")
    private val remoteAudioCacheDir = File(context.cacheDir, "webdav_audio")

    suspend fun scanMusic(
        minDurationMs: Long = 0,
        includeFolders: List<String> = emptyList(),
        excludeFolders: List<String> = emptyList()
    ) {
        _isScanning.value = true
        _scanProgress.value = 0
        try {
            _songs.value = scanner.scanAllSongs(minDurationMs, includeFolders, excludeFolders) { count ->
                _scanProgress.value = count
            }
            _albums.value = _songs.value.toAlbums()
            saveLibraryCache(_songs.value, _albums.value)
        } finally {
            _isScanning.value = false
        }
    }

    suspend fun loadCachedLibrary() = withContext(Dispatchers.IO) {
        if (!libraryCacheFile.exists()) return@withContext

        runCatching {
            val root = JSONObject(libraryCacheFile.readText())
            val songs = root.getJSONArray("songs").toSongList()
            _songs.value = songs
            _albums.value = songs.toAlbums()
        }.onFailure {
            Log.w("MusicRepo", "Failed to load music library cache", it)
        }
    }

    suspend fun getLyrics(
        song: Song,
        sourceMode: Int = SettingsManager.LYRIC_SOURCE_AUTO
    ): List<LyricLine> = withContext(Dispatchers.IO) {
        val safeMode = sourceMode.coerceIn(SettingsManager.LYRIC_SOURCE_AUTO, SettingsManager.LYRIC_SOURCE_EMBEDDED)
        val cacheKey = "${song.id}:$safeMode"
        lyricsCache[cacheKey]?.let { return@withContext it }

        Log.d("MusicRepo", "Loading lyrics for: ${song.title} path=${song.path}")

        if (safeMode == SettingsManager.LYRIC_SOURCE_AUTO) {
            fetchOnlineLyrics(song)?.let { onlineLyrics ->
                lyricsCache[cacheKey] = onlineLyrics
                return@withContext onlineLyrics
            }
        }

        val effectivePath = song.effectiveLocalPathForMetadata()
        if (safeMode != SettingsManager.LYRIC_SOURCE_EMBEDDED) {
            loadExternalLyrics(song, effectivePath)?.let { externalLyrics ->
                lyricsCache[cacheKey] = externalLyrics
                return@withContext externalLyrics
            }
        }

        if (safeMode != SettingsManager.LYRIC_SOURCE_EXTERNAL) {
            loadEmbeddedLyrics(song, effectivePath)?.let { embeddedLyrics ->
                lyricsCache[cacheKey] = embeddedLyrics
                return@withContext embeddedLyrics
            }
        }

        Log.d("MusicRepo", "No lyrics found for ${song.title}")
        lyricsCache[cacheKey] = emptyList()
        emptyList()
    }

    private fun loadExternalLyrics(song: Song, effectivePath: String): List<LyricLine>? {
        val lrcContent = LrcParser.findLrcFile(effectivePath) ?: return null
        val parsed = LrcParser.parse(lrcContent)
        Log.d("MusicRepo", "LRC parsed: ${parsed.lyrics.size} lines for ${song.title}")
        return parsed.lyrics.takeIf { it.isNotEmpty() }
    }

    private fun loadEmbeddedLyrics(song: Song, effectivePath: String): List<LyricLine>? {
        Log.d("MusicRepo", "Trying embedded lyrics for ${song.title}")
        val embedded = scanner.extractEmbeddedLyrics(effectivePath)
        if (!embedded.isNullOrBlank()) {
            Log.d("MusicRepo", "Embedded lyrics found (${embedded.length} chars) for ${song.title}")
            val parsed = LrcParser.parse(embedded)
            if (parsed.lyrics.isNotEmpty()) {
                Log.d("MusicRepo", "Embedded lyrics parsed as LRC: ${parsed.lyrics.size} lines")
                return parsed.lyrics
            }

            Log.d("MusicRepo", "Embedded lyrics not LRC format, using plain text")
            val result = mutableListOf<LyricLine>()
            val lines = embedded.lines()
            var timeOffset = 0L
            for (line in lines) {
                val trimmed = line.trim()
                if (trimmed.isNotEmpty()) {
                    result.add(LyricLine(timeMs = timeOffset, text = trimmed, words = emptyList()))
                    timeOffset += 3000L
                }
            }
            return result.takeIf { it.isNotEmpty() }
        }
        return null
    }

    suspend fun reloadLyrics(song: Song, sourceMode: Int): List<LyricLine> = withContext(Dispatchers.IO) {
        val safeMode = sourceMode.coerceIn(SettingsManager.LYRIC_SOURCE_AUTO, SettingsManager.LYRIC_SOURCE_EMBEDDED)
        lyricsCache.remove("${song.id}:$safeMode")
        getLyrics(song, safeMode)
    }

    private fun fetchOnlineLyrics(song: Song): List<LyricLine>? {
        if (song.onlineSource.startsWith("musicfree:")) {
            parseMusicFreeOnlineLyrics(song)?.let { return it }
            return null
        }
        if (song.onlineSource != "kw" || song.onlineId.isBlank()) return null
        val request = Request.Builder()
            .url("https://www.kuwo.cn/newh5/singles/songinfoandlrc?musicId=${song.onlineId}")
            .header("User-Agent", "Mozilla/5.0 (Linux; Android 15) AppleWebKit/537.36 EllaMusic/1.0")
            .build()
        return runCatching {
            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@use null
                val root = JSONObject(response.body?.string().orEmpty())
                val list = root.optJSONObject("data")?.optJSONArray("lrclist") ?: return@use null
                val rawLines = List(list.length()) { index ->
                    val item = list.getJSONObject(index)
                    val timeMs = ((item.optString("time").toDoubleOrNull() ?: 0.0) * 1000).toLong()
                    LyricLine(
                        timeMs = timeMs,
                        text = item.optString("lineLyric").trim()
                    )
                }.filter { it.text.isNotBlank() }
                rawLines.takeIf { it.isNotEmpty() }
            }
        }.getOrElse {
            Log.w("MusicRepo", "Failed to fetch online lyrics for ${song.title}", it)
            null
        }
    }

    private fun parseMusicFreeOnlineLyrics(song: Song): List<LyricLine>? {
        val rawLyrics = song.onlineLyrics.trim()
        val rawTranslation = song.onlineLyricTranslation.trim()
        if (rawLyrics.isBlank() && rawTranslation.isBlank()) return null

        val content = listOf(rawLyrics, rawTranslation)
            .filter { it.isNotBlank() }
            .joinToString("\n")
        val parsed = LrcParser.parse(content).lyrics
        if (parsed.isNotEmpty()) {
            Log.d("MusicRepo", "MusicFree online lyrics parsed: ${parsed.size} lines for ${song.title}")
            return parsed
        }

        val plainLines = content.lines()
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .mapIndexed { index, line ->
                LyricLine(timeMs = index * 3000L, text = line)
            }
        return plainLines.takeIf { it.isNotEmpty() }
    }

    fun getReplayGain(song: Song): Float? {
        replayGainCache[song.id]?.let { return it }
        val gain = scanner.extractReplayGain(song.effectiveLocalPathForMetadata())
        replayGainCache[song.id] = gain
        return gain
    }

    fun getAudioInfo(song: Song): AudioInfo {
        audioInfoCache[song.id]?.let { return it }
        val info = runCatching {
            val extractor = MediaExtractor()
            try {
                extractor.setDataSource(song.effectiveLocalPathForMetadata())
                var audioFormat: MediaFormat? = null
                for (index in 0 until extractor.trackCount) {
                    val format = extractor.getTrackFormat(index)
                    val mime = format.getString(MediaFormat.KEY_MIME).orEmpty()
                    if (mime.startsWith("audio/")) {
                        audioFormat = format
                        break
                    }
                }

                val format = audioFormat
                val formatLabel = song.audioFormatLabel(format?.getString(MediaFormat.KEY_MIME))
                val extractedBitRate = format?.getIntOrZero(MediaFormat.KEY_BIT_RATE) ?: 0
                val bitRate = extractedBitRate.takeIf { it > 0 } ?: song.estimatedBitRate()
                AudioInfo(
                    format = formatLabel,
                    bitRate = bitRate,
                    sampleRate = format?.getIntOrZero(MediaFormat.KEY_SAMPLE_RATE) ?: 0,
                    bitDepth = format?.getIntOrZero("bits-per-sample") ?: 0,
                    channels = format?.getIntOrZero(MediaFormat.KEY_CHANNEL_COUNT) ?: 0
                )
            } finally {
                extractor.release()
            }
        }.getOrElse {
            Log.w("MusicRepo", "Failed to read audio info for ${song.path}", it)
            AudioInfo(format = song.audioFormatLabel(null))
        }
        audioInfoCache[song.id] = info
        return info
    }

    private fun Song.estimatedBitRate(): Int {
        if (fileSize <= 0L || duration <= 0L) return 0
        return ((fileSize * 8_000L) / duration).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
    }

    fun getCoverArt(song: Song): ByteArray? {
        if (missingCoverIds.contains(song.id)) return null
        synchronized(coverArtLock) {
            coverArtCache.get(song.id)?.let { return it }
            val art = runCatching {
                scanner.extractCoverArt(song.effectiveLocalPathForMetadata())
            }.getOrElse { error ->
                if (error is OutOfMemoryError) {
                    coverArtCache.evictAll()
                    coverBitmapCache.evictAll()
                }
                Log.w("MusicRepo", "Failed to extract cover art for ${song.path}", error)
                null
            }
            if (art != null) {
                coverArtCache.put(song.id, art)
            } else {
                missingCoverIds += song.id
            }
            return art
        }
    }

    fun getCoverArtBitmap(song: Song, maxSize: Int = 512): Bitmap? {
        val targetSize = maxSize.coerceIn(64, 3000)
        val cacheKey = (song.id shl 16) xor targetSize.toLong()
        coverBitmapCache.get(cacheKey)?.let { return it }
        return synchronized(coverArtLock) {
            coverBitmapCache.get(cacheKey)?.let { return it }
            val data = getCoverArt(song) ?: return null
            runCatching {
            val bounds = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            BitmapFactory.decodeByteArray(data, 0, data.size, bounds)
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

            var sampleSize = 1
            while ((bounds.outWidth / sampleSize) > targetSize || (bounds.outHeight / sampleSize) > targetSize) {
                sampleSize *= 2
            }

            val options = BitmapFactory.Options().apply {
                inSampleSize = sampleSize.coerceAtLeast(1)
                inPreferredConfig = Bitmap.Config.RGB_565
            }
            BitmapFactory.decodeByteArray(data, 0, data.size, options)
                ?.also { coverBitmapCache.put(cacheKey, it) }
            }.getOrElse { error ->
            if (error is OutOfMemoryError) {
                coverArtCache.evictAll()
                coverBitmapCache.evictAll()
            }
            Log.w("MusicRepo", "Failed to decode cover bitmap for ${song.path}", error)
            null
            }
        }
    }

    fun getAlbumArtUri(albumId: Long): Uri? {
        if (albumId <= 0L) return null
        return ContentUris.withAppendedId(
            Uri.parse("content://media/external/audio/albumart"),
            albumId
        )
    }

    fun getSongsForAlbum(albumId: Long): List<Song> {
        return _songs.value
            .filter { it.albumIdentityId() == albumId }
            .sortedWith(
                compareBy<Song> { it.trackNumber <= 0 }
                    .thenBy { it.trackNumber }
                    .thenBy { it.title.lowercase() }
                    .thenBy { it.id }
            )
    }

    suspend fun deleteSongs(songs: Collection<Song>): Int = withContext(Dispatchers.IO) {
        var deleted = 0
        songs.forEach { song ->
            val deletedFromStore = runCatching {
                val uri = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, song.id)
                context.contentResolver.delete(uri, null, null) > 0
            }.getOrDefault(false)

            val deletedFromFile = if (!deletedFromStore) {
                runCatching {
                    val file = File(song.path)
                    file.exists() && file.delete()
                }.getOrDefault(false)
            } else {
                true
            }

            if (deletedFromFile) deleted++
        }

        if (deleted > 0) {
            val deletedIds = songs.map { it.id }.toSet()
            _songs.value = _songs.value.filterNot { it.id in deletedIds }
            _albums.value = _songs.value.toAlbums()
            saveLibraryCache(_songs.value, _albums.value)
        }
        deleted
    }

    fun clearCache() {
        lyricsCache.clear()
        audioInfoCache.clear()
        replayGainCache.clear()
        coverArtCache.evictAll()
        missingCoverIds.clear()
        coverBitmapCache.evictAll()
    }

    fun clearRemoteMetadataCache() {
        clearCache()
        runCatching {
            if (remoteAudioCacheDir.exists()) {
                remoteAudioCacheDir.deleteRecursively()
            }
        }.onFailure {
            Log.w("MusicRepo", "Failed to clear online metadata cache", it)
        }
    }

    private fun Song.effectiveLocalPathForMetadata(): String {
        if (!path.startsWith("http://") && !path.startsWith("https://")) return path
        val target = File(remoteAudioCacheDir, "${path.sha256()}.${fileName.substringAfterLast('.', "audio")}")
        if (target.exists() && target.length() > 0L) return target.absolutePath
        return runCatching {
            val config = runBlocking(Dispatchers.IO) {
                WebDavConfig(
                    url = settingsManager.webDavUrl.first(),
                    username = settingsManager.webDavUsername.first(),
                    password = settingsManager.webDavPassword.first()
                )
            }
            WebDavClient.downloadToFile(path, config, target).absolutePath
        }.getOrElse {
            Log.w("MusicRepo", "Failed to cache remote metadata file for $path", it)
            path
        }
    }

    private fun String.sha256(): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }

    private fun MediaFormat.getIntOrZero(key: String): Int {
        return if (containsKey(key)) runCatching { getInteger(key) }.getOrDefault(0) else 0
    }

    private fun Song.audioFormatLabel(mime: String?): String {
        val source = (mime ?: mimeType).lowercase()
        val extension = fileName.substringAfterLast('.', path.substringAfterLast('.')).lowercase()
        return when {
            "flac" in source || extension == "flac" -> "FLAC"
            "mpeg" in source || "mp3" in source || extension == "mp3" -> "MP3"
            "wav" in source || extension == "wav" -> "WAV"
            "aac" in source || extension == "aac" -> "AAC"
            "alac" in source -> "ALAC/M4A"
            "mp4" in source || "m4a" in source || extension == "m4a" || extension == "mp4" -> "M4A"
            "ogg" in source || extension == "ogg" -> "OGG"
            "opus" in source || extension == "opus" -> "OPUS"
            extension.isNotBlank() -> extension.uppercase()
            else -> "Audio"
        }
    }

    private suspend fun saveLibraryCache(songs: List<Song>, albums: List<Album>) = withContext(Dispatchers.IO) {
        runCatching {
            val root = JSONObject()
                .put("version", 1)
                .put("songs", songsToJsonArray(songs))
                .put("albums", albumsToJsonArray(albums))
            libraryCacheFile.writeText(root.toString())
        }.onFailure {
            Log.w("MusicRepo", "Failed to save music library cache", it)
        }
    }

    private fun List<Song>.toAlbums(): List<Album> {
        return groupBy { it.albumIdentityId() }
            .map { (albumIdentityId, albumSongs) ->
                val first = albumSongs.first()
                Album(
                    id = albumIdentityId,
                    name = first.album,
                    artist = first.artist,
                    songCount = albumSongs.size,
                    artAlbumId = first.albumId
                )
            }
            .sortedWith(
                compareBy<Album> { it.name.lowercase() }
                    .thenBy { it.artist.lowercase() }
                    .thenBy { it.id }
            )
    }

    private fun songsToJsonArray(songs: List<Song>): JSONArray {
        val array = JSONArray()
        songs.forEach { song ->
            array.put(
                JSONObject()
                    .put("id", song.id)
                    .put("title", song.title)
                    .put("artist", song.artist)
                    .put("album", song.album)
                    .put("albumId", song.albumId)
                    .put("duration", song.duration)
                    .put("path", song.path)
                    .put("fileName", song.fileName)
                    .put("fileSize", song.fileSize)
                    .put("mimeType", song.mimeType)
                    .put("dateAdded", song.dateAdded)
                    .put("dateModified", song.dateModified)
                    .put("trackNumber", song.trackNumber)
                    .put("coverUrl", song.coverUrl)
                    .put("onlineSource", song.onlineSource)
                    .put("onlineId", song.onlineId)
                    .put("onlineLyrics", song.onlineLyrics)
                    .put("onlineLyricTranslation", song.onlineLyricTranslation)
            )
        }
        return array
    }

    private fun albumsToJsonArray(albums: List<Album>): JSONArray {
        val array = JSONArray()
        albums.forEach { album ->
            array.put(
                JSONObject()
                    .put("id", album.id)
                    .put("name", album.name)
                    .put("artist", album.artist)
                    .put("songCount", album.songCount)
                    .put("year", album.year)
                    .put("artAlbumId", album.artAlbumId)
            )
        }
        return array
    }

    private fun JSONArray.toSongList(): List<Song> {
        return List(length()) { index ->
            val item = getJSONObject(index)
            Song(
                id = item.getLong("id"),
                title = item.optString("title"),
                artist = item.optString("artist"),
                album = item.optString("album"),
                albumId = item.optLong("albumId"),
                duration = item.optLong("duration"),
                path = item.optString("path"),
                fileName = item.optString("fileName"),
                fileSize = item.optLong("fileSize"),
                mimeType = item.optString("mimeType"),
                dateAdded = item.optLong("dateAdded"),
                dateModified = item.optLong("dateModified"),
                trackNumber = item.optInt("trackNumber"),
                coverUrl = item.optString("coverUrl"),
                onlineSource = item.optString("onlineSource"),
                onlineId = item.optString("onlineId"),
                onlineLyrics = item.optString("onlineLyrics"),
                onlineLyricTranslation = item.optString("onlineLyricTranslation")
            )
        }
    }

    private fun JSONArray.toAlbumList(): List<Album> {
        return List(length()) { index ->
            val item = getJSONObject(index)
            Album(
                id = item.getLong("id"),
                name = item.optString("name"),
                artist = item.optString("artist"),
                songCount = item.optInt("songCount"),
                year = item.optInt("year"),
                artAlbumId = item.optLong("artAlbumId", item.optLong("id"))
            )
        }
    }
}
