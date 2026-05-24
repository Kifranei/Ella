package com.ella.music.data.repository

import android.content.ContentUris
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaScannerConnection
import android.net.Uri
import android.provider.MediaStore
import android.util.Log
import android.util.LruCache
import com.ella.music.data.AppLogStore
import com.ella.music.data.AppLogType
import com.ella.music.data.AppNetworkLoggingInterceptor
import com.ella.music.data.SettingsManager
import com.ella.music.data.model.Album
import com.ella.music.data.model.AudioInfo
import com.ella.music.data.model.LyricLine
import com.ella.music.data.model.Song
import com.ella.music.data.model.SongTagInfo
import com.ella.music.data.model.albumIdentityId
import com.ella.music.data.parser.LrcParser
import com.ella.music.data.scanner.MediaStoreAudioItem
import com.ella.music.data.scanner.MusicScanner
import com.ella.music.data.webdav.WebDavClient
import com.ella.music.data.webdav.WebDavConfig
import java.io.File
import java.security.MessageDigest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine
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
        .addInterceptor(AppNetworkLoggingInterceptor("MusicRepoNetwork"))
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
    private val tagInfoCache = mutableMapOf<String, SongTagInfo>()
    private val replayGainCache = mutableMapOf<Long, Float?>()
    private val coverArtCache = object : LruCache<String, ByteArray>(8 * 1024) {
        override fun sizeOf(key: String, value: ByteArray): Int = value.size / 1024
    }
    private val coverBitmapCache = object : LruCache<String, Bitmap>(16 * 1024) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount / 1024
    }
    private val coverArtLock = Any()
    private val missingCoverKeys = ConcurrentHashMap.newKeySet<String>()
    private val libraryCacheFile = File(context.filesDir, "music_library_cache.json")
    private val remoteAudioCacheDir = File(context.cacheDir, "webdav_audio")

    suspend fun scanMusic(
        minDurationMs: Long = 0,
        includeFolders: List<String> = emptyList(),
        excludeFolders: List<String> = emptyList(),
        fullRescan: Boolean = false,
        deepRescan: Boolean = fullRescan
    ): Int {
        _isScanning.value = true
        _scanProgress.value = 0
        try {
            val mode = if (includeFolders.isEmpty()) "media_library" else "custom_folders"
            AppLogStore.info(
                context,
                "MusicScanner",
                "Start scan mode=$mode minDuration=${minDurationMs}ms include=${includeFolders.size} exclude=${excludeFolders.size} fullRescan=$fullRescan deepRescan=$deepRescan",
                AppLogType.LIBRARY
            )
            val scannedSongs = if (fullRescan || deepRescan) {
                scanner.scanAllSongs(
                    minDurationMs = minDurationMs,
                    includeFolders = includeFolders,
                    excludeFolders = excludeFolders,
                    deepMetadata = true
                ) { count -> _scanProgress.value = count }
            } else {
                synchronizeLibrary(
                    minDurationMs = minDurationMs,
                    includeFolders = includeFolders,
                    excludeFolders = excludeFolders
                )
            }
            _songs.value = scannedSongs
            _albums.value = scannedSongs.toAlbums()
            saveLibraryCache(scannedSongs, _albums.value)
            AppLogStore.info(
                context,
                "MusicScanner",
                "Scan finished mode=$mode songs=${scannedSongs.size} albums=${_albums.value.size}",
                AppLogType.LIBRARY
            )
            return scannedSongs.size
        } catch (error: Throwable) {
            AppLogStore.error(
                context,
                "MusicScanner",
                "Scan failed: ${error.message ?: error.javaClass.name}",
                error,
                AppLogType.LIBRARY
            )
            throw error
        } finally {
            _isScanning.value = false
        }
    }

    private suspend fun synchronizeLibrary(
        minDurationMs: Long,
        includeFolders: List<String>,
        excludeFolders: List<String>
    ): List<Song> = withContext(Dispatchers.IO) {
        val cachedSongs = _songs.value.takeIf { it.isNotEmpty() } ?: readCachedSongs()
        val cachedBySyncKey = cachedSongs.associateBy { it.librarySyncKey() }
        val cachedByPath = cachedSongs.associateBy { it.path }
        val currentItems = scanner.enumerateAudioFiles(
            includeFolders = includeFolders,
            excludeFolders = excludeFolders
        )
        val currentKeys = currentItems.map { it.librarySyncKey() }.toSet()
        val currentPaths = currentItems.map { it.path }.toSet()
        val mergedSongs = ArrayList<Song>(currentItems.size)
        var addedCount = 0
        var updatedCount = 0
        var reusedCount = 0
        var failedCount = 0

        currentItems.forEachIndexed { index, item ->
            val cached = cachedBySyncKey[item.librarySyncKey()] ?: cachedByPath[item.path]
            val mediaStoreSaysTooShort = item.duration > 0L && item.duration < minDurationMs
            if (mediaStoreSaysTooShort) {
                _scanProgress.value = index + 1
                return@forEachIndexed
            }

            val currentInfo = item.toLibrarySyncInfo()
            val cachedInfo = cached?.toLibrarySyncInfo()
            val needsUpdate = cachedInfo == null ||
                cachedInfo.key != currentInfo.key ||
                cachedInfo.path != currentInfo.path ||
                cachedInfo.fileSize != currentInfo.fileSize ||
                cachedInfo.dateModified != currentInfo.dateModified

            if (needsUpdate) {
                val scanned = runCatching {
                    scanner.scanAudioItem(
                        item = item,
                        minDurationMs = minDurationMs,
                        deepMetadata = true
                    )
                }.onFailure { error ->
                    failedCount++
                    AppLogStore.warn(
                        context,
                        "MusicScanner",
                        "Incremental item failed path=${item.path}: ${error.message ?: error.javaClass.name}",
                        type = AppLogType.LIBRARY
                    )
                }.getOrNull()

                if (scanned != null) {
                    cached?.let(::clearMetadataCache)
                    clearMetadataCache(scanned)
                    mergedSongs += scanned
                    if (cached == null) addedCount++ else updatedCount++
                } else if (cached != null) {
                    mergedSongs += cached
                }
            } else {
                val reused = cached.copy(
                    albumId = item.albumId,
                    fileName = item.fileName.ifBlank { cached.fileName },
                    mimeType = item.mimeType.ifBlank { cached.mimeType },
                    dateAdded = item.dateAdded.takeIf { it > 0L } ?: cached.dateAdded,
                    trackNumber = item.trackNumber.takeIf { it > 0 } ?: cached.trackNumber,
                    discNumber = item.discNumber.takeIf { it > 0 } ?: cached.discNumber
                )
                if (reused.duration >= minDurationMs) {
                    mergedSongs += reused
                    reusedCount++
                }
            }
            _scanProgress.value = index + 1
        }

        val deletedSongs = cachedSongs.filter { song ->
            song.librarySyncKey() !in currentKeys && song.path !in currentPaths
        }
        deletedSongs.forEach(::clearMetadataCache)
        val deletedCount = deletedSongs.size

        AppLogStore.info(
            context,
            "MusicScanner",
            "Incremental scan finished total=${currentItems.size} added=$addedCount updated=$updatedCount reused=$reusedCount deleted=$deletedCount failed=$failedCount",
            AppLogType.LIBRARY
        )
        Log.d(
            "MusicScanner",
            "Incremental scan finished total=${currentItems.size} added=$addedCount updated=$updatedCount reused=$reusedCount deleted=$deletedCount failed=$failedCount"
        )
        mergedSongs
    }

    suspend fun refreshSongAfterExternalEdit(song: Song): Song? = withContext(Dispatchers.IO) {
        if (song.path.startsWith("http://") || song.path.startsWith("https://")) return@withContext null

        clearMetadataCache(song)
        scanEditedFile(song)
        delay(350)

        val updated = querySystemSong(song) ?: song
        clearMetadataCache(updated)

        val currentSongs = _songs.value
        if (currentSongs.isNotEmpty()) {
            val nextSongs = currentSongs.map { existing ->
                if (existing.id == song.id || existing.path == song.path) updated else existing
            }
            _songs.value = nextSongs
            _albums.value = nextSongs.toAlbums()
            saveLibraryCache(nextSongs, _albums.value)
        }
        updated
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

    private fun readCachedSongs(): List<Song> {
        if (!libraryCacheFile.exists()) return emptyList()
        return runCatching {
            JSONObject(libraryCacheFile.readText()).getJSONArray("songs").toSongList()
        }.getOrElse {
            Log.w("MusicRepo", "Failed to read music library cache for sync", it)
            emptyList()
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

    fun getSongTagInfo(song: Song): SongTagInfo {
        val cacheKey = "${song.id}:${song.dateModified}:${song.fileSize}"
        tagInfoCache[cacheKey]?.let { return it }
        val info = runCatching {
            scanner.extractSongTagInfo(song.effectiveLocalPathForMetadata())
        }.getOrElse {
            Log.w("MusicRepo", "Failed to read tag info for ${song.path}", it)
            SongTagInfo()
        }
        tagInfoCache[cacheKey] = info
        return info
    }

    fun getSongRating(song: Song): Int {
        return getSongTagInfo(song).rating
    }

    private fun Song.estimatedBitRate(): Int {
        if (fileSize <= 0L || duration <= 0L) return 0
        return ((fileSize * 8_000L) / duration).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
    }

    fun getCoverArt(song: Song): ByteArray? {
        val cacheKey = song.coverCacheKey()
        if (missingCoverKeys.contains(cacheKey)) return null
        synchronized(coverArtLock) {
            coverArtCache.get(cacheKey)?.let { return it }
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
                coverArtCache.put(cacheKey, art)
            } else {
                missingCoverKeys += cacheKey
            }
            return art
        }
    }

    fun getCoverArtBitmap(song: Song, maxSize: Int = 512): Bitmap? {
        val targetSize = maxSize.coerceIn(64, 3000)
        val cacheKey = "${song.coverCacheKey()}:$targetSize"
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
                compareBy<Song> { it.discNumber <= 0 && it.trackNumber <= 0 }
                    .thenBy { if (it.discNumber > 0) it.discNumber else Int.MAX_VALUE }
                    .thenBy { if (it.trackNumber > 0) it.trackNumber else Int.MAX_VALUE }
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

    suspend fun removeSongsFromLibrary(songs: Collection<Song>): Unit = withContext(Dispatchers.IO) {
        if (songs.isEmpty()) return@withContext
        val deletedIds = songs.map { it.id }.toSet()
        _songs.value = _songs.value.filterNot { it.id in deletedIds }
        _albums.value = _songs.value.toAlbums()
        saveLibraryCache(_songs.value, _albums.value)
        Unit
    }

    fun clearCache() {
        lyricsCache.clear()
        audioInfoCache.clear()
        tagInfoCache.clear()
        replayGainCache.clear()
        coverArtCache.evictAll()
        missingCoverKeys.clear()
        coverBitmapCache.evictAll()
    }

    fun clearMetadataCache(song: Song) {
        lyricsCache.keys.removeAll { it.startsWith("${song.id}:") }
        audioInfoCache.remove(song.id)
        tagInfoCache.keys.removeAll { it.startsWith("${song.id}:") }
        replayGainCache.remove(song.id)
        missingCoverKeys.remove(song.coverCacheKey())
        coverArtCache.remove(song.coverCacheKey())
        val keyPrefix = "${song.coverCacheKey()}:"
        val bitmapKeys = mutableListOf<String>()
        synchronized(coverArtLock) {
            for (key in coverBitmapCache.snapshot().keys) {
                if (key.startsWith(keyPrefix)) bitmapKeys += key
            }
            bitmapKeys.forEach(coverBitmapCache::remove)
        }
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
                    artist = first.albumArtist.ifBlank { first.artist },
                    songCount = albumSongs.size,
                    year = albumSongs.mapNotNull { it.year.extractYearInt() }.minOrNull() ?: 0,
                    artAlbumId = first.albumId,
                    albumArtist = first.albumArtist
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
                    .put("discNumber", song.discNumber)
                    .put("albumArtist", song.albumArtist)
                    .put("genre", song.genre)
                    .put("year", song.year)
                    .put("composer", song.composer)
                    .put("lyricist", song.lyricist)
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
                    .put("albumArtist", album.albumArtist)
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
                discNumber = item.optInt("discNumber"),
                albumArtist = item.optString("albumArtist"),
                genre = item.optString("genre"),
                year = item.optString("year"),
                composer = item.optString("composer"),
                lyricist = item.optString("lyricist"),
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
                artAlbumId = item.optLong("artAlbumId", item.optLong("id")),
                albumArtist = item.optString("albumArtist")
            )
        }
    }

    private suspend fun scanEditedFile(song: Song) = suspendCoroutine<Unit> { continuation ->
        val path = song.path.takeIf { it.isNotBlank() }
        if (path == null || path.startsWith("content://", ignoreCase = true)) {
            continuation.resume(Unit)
            return@suspendCoroutine
        }
        val mimeTypes = song.mimeType.takeIf { it.isNotBlank() }?.let { arrayOf(it) }
        MediaScannerConnection.scanFile(
            context,
            arrayOf(path),
            mimeTypes,
        ) { _, _ ->
            continuation.resume(Unit)
        }
    }

    private fun querySystemSong(song: Song): Song? {
        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.ALBUM_ID,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.DATA,
            MediaStore.Audio.Media.DISPLAY_NAME,
            MediaStore.Audio.Media.SIZE,
            MediaStore.Audio.Media.MIME_TYPE,
            MediaStore.Audio.Media.DATE_ADDED,
            MediaStore.Audio.Media.DATE_MODIFIED,
            MediaStore.Audio.Media.TRACK
        )
        val uri = if (song.id > 0L) {
            ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, song.id)
        } else {
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        }
        val selection = if (song.id > 0L) null else "${MediaStore.Audio.Media.DATA} = ?"
        val selectionArgs = if (song.id > 0L) null else arrayOf(song.path)

        return context.contentResolver.query(uri, projection, selection, selectionArgs, null)?.use { cursor ->
            if (!cursor.moveToFirst()) return@use null
            val tagInfo = runCatching {
                scanner.extractSongTagInfo(song.effectiveLocalPathForMetadata())
            }.getOrDefault(SongTagInfo())
            Song(
                id = cursor.getLong(0),
                title = tagInfo.title.usableTagText().ifBlank {
                    cursor.getString(1)?.usableTagText().orEmpty().ifBlank { song.title }
                },
                artist = tagInfo.artist.usableTagText().ifBlank {
                    cursor.getString(2)?.usableTagText().orEmpty().ifBlank { song.artist }
                },
                album = tagInfo.album.usableTagText().ifBlank {
                    cursor.getString(3)?.usableTagText().orEmpty().ifBlank { song.album }
                },
                albumId = cursor.getLong(4),
                duration = cursor.getLong(5).takeIf { it > 0L } ?: song.duration,
                path = cursor.getString(6).orEmpty().ifBlank { song.path },
                fileName = cursor.getString(7).orEmpty().ifBlank { song.fileName },
                fileSize = cursor.getLong(8),
                mimeType = cursor.getString(9).orEmpty().ifBlank { song.mimeType },
                dateAdded = cursor.getLong(10) * 1000L,
                dateModified = cursor.getLong(11) * 1000L,
                trackNumber = cursor.getInt(12).let { if (it > 1000) it % 1000 else it },
                discNumber = cursor.getInt(12).let { if (it >= 1000) it / 1000 else song.discNumber },
                albumArtist = tagInfo.albumArtist.ifBlank { song.albumArtist },
                genre = tagInfo.genre.ifBlank { song.genre },
                year = tagInfo.year.ifBlank { song.year },
                composer = tagInfo.composer.ifBlank { song.composer },
                lyricist = tagInfo.lyricist.ifBlank { song.lyricist },
                coverUrl = song.coverUrl,
                onlineSource = song.onlineSource,
                onlineId = song.onlineId,
                onlineLyrics = song.onlineLyrics,
                onlineLyricTranslation = song.onlineLyricTranslation
            )
        }
    }

    private fun Song.coverCacheKey(): String {
        val source = when {
            path.isNotBlank() -> path
            onlineSource.isNotBlank() || onlineId.isNotBlank() -> "$onlineSource:$onlineId"
            else -> "$id:$title:$artist:$album"
        }
        return source.sha256()
    }

    private fun Song.librarySyncKey(): String =
        if (id > 0L) {
            ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id).toString()
        } else {
            path
        }

    private fun MediaStoreAudioItem.librarySyncKey(): String =
        if (id > 0L) {
            ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id).toString()
        } else {
            path
        }

    private fun Song.toLibrarySyncInfo(): LibrarySyncInfo =
        LibrarySyncInfo(
            key = librarySyncKey(),
            path = path,
            fileSize = fileSize,
            dateModified = dateModified
        )

    private fun MediaStoreAudioItem.toLibrarySyncInfo(): LibrarySyncInfo =
        LibrarySyncInfo(
            key = librarySyncKey(),
            path = path,
            fileSize = fileSize,
            dateModified = dateModified
        )

    private fun String.extractYearInt(): Int? =
        Regex("""\d{4}""").find(this)?.value?.toIntOrNull()

    private fun String?.usableTagText(): String {
        val text = this?.trim().orEmpty()
        if (text.isBlank() || text == "<unknown>") return ""
        if ('\uFFFD' in text || "锟斤拷" in text || Regex("""(?:锟|斤|拷){3,}""").containsMatchIn(text)) return ""
        return text
    }

    private data class LibrarySyncInfo(
        val key: String,
        val path: String,
        val fileSize: Long,
        val dateModified: Long
    )
}
