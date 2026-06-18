package com.ella.music.data.repository

import android.content.ContentUris
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import android.util.LruCache
import androidx.documentfile.provider.DocumentFile
import com.ella.music.data.exception.WritePermissionRequiredException
import com.ella.music.data.AppLogStore
import com.ella.music.data.AppLogType
import com.ella.music.data.AppNetworkLoggingInterceptor
import com.ella.music.data.LibraryAlbumAggregator
import com.ella.music.data.LibraryNormalizer
import com.ella.music.data.SettingsManager
import com.ella.music.data.isContentAudioSource
import com.ella.music.data.isHttpAudioSource
import com.ella.music.data.isMediaStoreContentAudioSource
import com.ella.music.data.looksLikeNeteaseKeyValue
import com.ella.music.data.model.Album
import com.ella.music.data.model.AudioInfo
import com.ella.music.data.model.LyricLine
import com.ella.music.data.model.Song
import com.ella.music.data.model.SongTagInfo
import com.ella.music.data.model.albumIdentityId
import com.ella.music.data.model.searchableTagValues
import com.ella.music.data.metadata.AudioCoverInfo
import com.ella.music.data.metadata.AudioTagInfo
import com.ella.music.data.metadata.AudioTagRepository
import com.ella.music.data.metadata.LyricoAudioTagReaderWriter
import com.ella.music.data.metadata.WavMetadataReader
import com.ella.music.data.parser.LrcParser
import com.ella.music.data.parser.EllaLyricsParser
import com.ella.music.data.scanner.MediaStoreAudioItem
import com.ella.music.data.scanner.MusicScanner
import com.ella.music.data.webdav.WebDavClient
import com.ella.music.data.webdav.WebDavConfig
import java.io.File
import java.security.MessageDigest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

enum class CoverUsage {
    ListThumbnail,
    AlbumGrid,
    Player,
    Notification,
    ShareCard
}

data class MusicScanSummary(
    val total: Int,
    val added: Int = 0,
    val updated: Int = 0,
    val deleted: Int = 0,
    val failed: Int = 0,
    val fullRescan: Boolean = false
)

private val LOCAL_AUDIO_EXTENSIONS = setOf(
    "mp3",
    "flac",
    "ogg",
    "opus",
    "aac",
    "m4a",
    "mp4",
    "wav",
    "wave",
    "wma",
    "aiff",
    "ape",
    "alac"
)

private val DEFAULT_LOCAL_SCAN_EXCLUDE_FOLDERS = listOf(
    "/storage/emulated/0/Music/Recordings"
)

private const val RECENT_MEDIASTORE_REFRESH_WINDOW_MS = 10 * 60 * 1000L
private const val MAX_RECENT_MEDIASTORE_REFRESH_FILES = 256
private const val MEDIASTORE_REFRESH_TIMEOUT_MS = 8_000L

class MusicRepository(private val context: Context) {
    companion object {
        @Volatile
        private var instance: MusicRepository? = null

        fun getInstance(context: Context): MusicRepository =
            instance ?: synchronized(this) {
                instance ?: MusicRepository(context.applicationContext).also { instance = it }
            }
    }

    data class LyricFormatAvailability(
        val hasTtml: Boolean = false,
        val hasPlain: Boolean = false
    ) {
        val hasBoth: Boolean get() = hasTtml && hasPlain
    }


    private val scanner = MusicScanner(context)
    private val audioTagRepository = AudioTagRepository(
        primary = LyricoAudioTagReaderWriter()
    )
    private val settingsManager = SettingsManager.getInstance(context)
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

    private val _scanSummaryEvents = MutableSharedFlow<MusicScanSummary>(extraBufferCapacity = 1)
    val scanSummaryEvents: SharedFlow<MusicScanSummary> = _scanSummaryEvents.asSharedFlow()

    fun startScanning() {
        _isScanning.value = true
        _scanProgress.value = 0
    }

    fun emitScanSummary(summary: MusicScanSummary) {
        _scanSummaryEvents.tryEmit(summary)
    }

    fun finishScanning() {
        _isScanning.value = false
    }

    private val remoteAudioCacheDir = File(context.cacheDir, "webdav_audio")
    private val remoteMetadataHeaderCacheDir = File(context.cacheDir, "webdav_metadata_headers")
    private val lyricsManager = MusicLyricsManager(context, settingsManager, audioTagRepository, httpClient, remoteAudioCacheDir, remoteMetadataHeaderCacheDir)
    private val coverArtManager = MusicCoverArtManager(context, audioTagRepository, settingsManager, httpClient, remoteAudioCacheDir, remoteMetadataHeaderCacheDir)
    private val snapshotManager = MusicSnapshotManager(
        File(context.filesDir, "library_search_snapshot.json"),
        File(context.filesDir, "library_rating_snapshot.json")
    ) { song -> song.searchableTagValues(getSongTagInfo(song)).joinToString(separator = "\n").lowercase() }

    private val audioInfoCache = ConcurrentHashMap<String, AudioInfo>()
    private val tagInfoCache = ConcurrentHashMap<String, SongTagInfo>()
    private val replayGainCache = ConcurrentHashMap<String, Float>()
    private val replayGainMissingCache = ConcurrentHashMap.newKeySet<String>()
    private val libraryCacheFile = File(context.filesDir, "music_library_cache.json")

    suspend fun scanMusic(
        minDurationMs: Long = 0,
        includeFolders: List<String> = emptyList(),
        excludeFolders: List<String> = emptyList(),
        fullRescan: Boolean = false,
        deepRescan: Boolean = fullRescan
    ): MusicScanSummary {
        val mode = if (includeFolders.isEmpty()) "media_library" else "custom_folders"
        val previousSongs = _songs.value.takeIf { it.isNotEmpty() } ?: readCachedSongs()
        AppLogStore.info(
            context,
            "MusicScanner",
            "Start scan mode=$mode minDuration=${minDurationMs}ms include=${includeFolders.size} exclude=${excludeFolders.size} fullRescan=$fullRescan deepRescan=$deepRescan",
            AppLogType.LIBRARY
        )
        val scanResult = if (fullRescan || deepRescan) {
            val scannedSongs = scanner.scanAllSongs(
                minDurationMs = minDurationMs,
                includeFolders = includeFolders,
                excludeFolders = excludeFolders,
                deepMetadata = true
            ) { count -> _scanProgress.value = count }
                .map { song -> song.withRepositoryTags() }
            LibraryScanResult(
                songs = scannedSongs,
                summary = buildFullScanSummary(previousSongs, scannedSongs, fullRescan = true)
            )
        } else {
            synchronizeLibrary(
                minDurationMs = minDurationMs,
                includeFolders = includeFolders,
                excludeFolders = excludeFolders
            )
        }
        val scannedSongs = scanResult.songs
        val clearedRatingSnapshots = snapshotManager.clearMissingFileSnapshots(scannedSongs.map { it.path }.toSet())
        _songs.value = scannedSongs
        _albums.value = scannedSongs.toAlbums()
        saveLibraryCache(scannedSongs, _albums.value)
        AppLogStore.info(
            context,
            "MusicScanner",
            "Scan finished mode=$mode songs=${scannedSongs.size} albums=${_albums.value.size} added=${scanResult.summary.added} removed=${scanResult.summary.deleted} updated=${scanResult.summary.updated} ratingSnapshotsCleared=$clearedRatingSnapshots",
            AppLogType.LIBRARY
        )
        return scanResult.summary.copy(total = scannedSongs.size)
    }

    /**
     * Scan USB folders via SAF and merge the results into the current library.
     */
    suspend fun scanUsbFolders(
        usbUris: List<android.net.Uri>,
        minDurationMs: Long = 0,
        deepMetadata: Boolean = false
    ): MusicScanSummary {
        if (usbUris.isEmpty()) return MusicScanSummary(total = _songs.value.size)
        val existingSongs = _songs.value
        val existingPaths = existingSongs.map { it.path }.toSet()
        val usbSongs = mutableListOf<Song>()
        for (uri in usbUris) {
            val accessible = scanner.isUsbUriAccessible(uri)
            if (!accessible) {
                AppLogStore.info(
                    context,
                    "MusicScanner",
                    "USB URI not accessible, skipping: $uri",
                    AppLogType.LIBRARY
                )
                continue
            }
            val found = scanner.scanUsbFolder(
                treeUri = uri,
                minDurationMs = minDurationMs,
                deepMetadata = deepMetadata
            ) { count -> _scanProgress.value = count }
            usbSongs.addAll(found.filter { it.path !in existingPaths })
        }
        if (usbSongs.isNotEmpty()) {
            val merged = existingSongs + usbSongs
            _songs.value = merged
            _albums.value = merged.toAlbums()
            saveLibraryCache(merged, _albums.value)
            AppLogStore.info(
                context,
                "MusicScanner",
                "USB scan finished: ${usbSongs.size} new songs from ${usbUris.size} folders, total=${merged.size}",
                AppLogType.LIBRARY
            )
            return MusicScanSummary(total = merged.size, added = usbSongs.size)
        }
        return MusicScanSummary(total = _songs.value.size)
    }

    private suspend fun synchronizeLibrary(
        minDurationMs: Long,
        includeFolders: List<String>,
        excludeFolders: List<String>
    ): LibraryScanResult = withContext(Dispatchers.IO) {
        val cachedSongs = _songs.value.takeIf { it.isNotEmpty() } ?: readCachedSongs()
        val refreshedMediaStoreFiles = refreshRecentlyChangedLocalAudioFiles(cachedSongs, includeFolders, excludeFolders)
        if (refreshedMediaStoreFiles > 0) {
            AppLogStore.info(
                context,
                "MusicScanner",
                "Requested MediaStore refresh for $refreshedMediaStoreFiles recently changed audio files",
                AppLogType.LIBRARY
            )
        }
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
                cachedInfo.dateModified != currentInfo.dateModified ||
                cached.needsMetadataPlaceholderRefresh()

            if (needsUpdate) {
                val scanned = runCatching {
                    scanner.scanAudioItem(
                        item = item,
                        minDurationMs = minDurationMs,
                        deepMetadata = true
                    )?.withRepositoryTags()
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
                    if (cached.hasSameLibraryTags(reused)) reusedCount++ else updatedCount++
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
        LibraryScanResult(
            songs = mergedSongs,
            summary = MusicScanSummary(
                total = mergedSongs.size,
                added = addedCount,
                updated = updatedCount,
                deleted = deletedCount,
                failed = failedCount
            )
        )
    }

    private fun buildFullScanSummary(
        previousSongs: List<Song>,
        scannedSongs: List<Song>,
        fullRescan: Boolean
    ): MusicScanSummary {
        val previousByKey = previousSongs.associateBy { it.librarySyncKey() }
        val scannedByKey = scannedSongs.associateBy { it.librarySyncKey() }
        val added = scannedByKey.keys.count { it !in previousByKey }
        val deleted = previousByKey.keys.count { it !in scannedByKey }
        val updated = scannedByKey.count { (key, song) ->
            val previous = previousByKey[key]
            previous != null && previous.toLibrarySyncInfo() != song.toLibrarySyncInfo()
        }
        return MusicScanSummary(
            total = scannedSongs.size,
            added = added,
            updated = updated,
            deleted = deleted,
            failed = 0,
            fullRescan = fullRescan
        )
    }

    private data class LibraryScanResult(
        val songs: List<Song>,
        val summary: MusicScanSummary
    )

    private suspend fun refreshRecentlyChangedLocalAudioFiles(
        cachedSongs: List<Song>,
        includeFolders: List<String>,
        excludeFolders: List<String>
    ): Int = withContext(Dispatchers.IO) {
        if (cachedSongs.isEmpty()) return@withContext 0

        val knownPaths = cachedSongs
            .asSequence()
            .map { it.path.replace('\\', '/') }
            .filter { it.isNotBlank() && !it.isHttpAudioSource() && !it.isContentAudioSource() }
            .toSet()
        if (knownPaths.isEmpty()) return@withContext 0

        val newestKnownModified = cachedSongs.maxOfOrNull { it.dateModified } ?: 0L
        val recentThreshold = (newestKnownModified - RECENT_MEDIASTORE_REFRESH_WINDOW_MS).coerceAtLeast(0L)
        val excluded = (DEFAULT_LOCAL_SCAN_EXCLUDE_FOLDERS + excludeFolders)
            .mapNotNull { it.normalizedLocalFolderPath() }
        val roots = localMediaRefreshRoots(includeFolders)
            .filter { it.exists() && it.isDirectory }
            .distinctBy { it.absolutePath.lowercase() }

        val candidates = roots
            .asSequence()
            .flatMap { root ->
                runCatching {
                    root.walkTopDown()
                        .onEnter { dir -> dir.toString().isAllowedByLocalFolderFilters(emptyList(), excluded) }
                        .filter { file ->
                            file.isFile &&
                                file.extension.lowercase() in LOCAL_AUDIO_EXTENSIONS &&
                                file.toString().isAllowedByLocalFolderFilters(emptyList(), excluded)
                        }
                        .filter { file ->
                            val path = file.absolutePath.replace('\\', '/')
                            path !in knownPaths || file.lastModified() >= recentThreshold
                        }
                        .toList()
                }.getOrDefault(emptyList()).asSequence()
            }
            .sortedByDescending { it.lastModified() }
            .take(MAX_RECENT_MEDIASTORE_REFRESH_FILES)
            .map { it.absolutePath }
            .toList()

        if (candidates.isEmpty()) return@withContext 0
        scanFilesIntoMediaStore(candidates)
        candidates.size
    }

    private fun localMediaRefreshRoots(includeFolders: List<String>): List<File> {
        val customRoots = includeFolders
            .filterNot { it == "__ella_no_custom_folder__" }
            .map { File(it) }
            .filter { it.exists() && it.isDirectory }
        if (customRoots.isNotEmpty()) return customRoots

        return listOf(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC),
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
            File(Environment.getExternalStorageDirectory(), "Audiobooks"),
            File(Environment.getExternalStorageDirectory(), "Podcasts")
        )
    }

    private suspend fun scanFilesIntoMediaStore(paths: List<String>) {
        if (paths.isEmpty()) return
        withTimeoutOrNull(MEDIASTORE_REFRESH_TIMEOUT_MS) {
            suspendCancellableCoroutine<Unit> { continuation ->
                val remaining = AtomicInteger(paths.size)
                val completed = AtomicBoolean(false)
                fun completeOne() {
                    if (remaining.decrementAndGet() <= 0 && completed.compareAndSet(false, true)) {
                        continuation.resume(Unit)
                    }
                }
                continuation.invokeOnCancellation {
                    completed.set(true)
                }
                MediaScannerConnection.scanFile(context, paths.toTypedArray(), null) { _, _ ->
                    if (!completed.get()) completeOne()
                }
            }
        }
    }

    suspend fun refreshSongAfterExternalEdit(song: Song): Song? = withContext(Dispatchers.IO) {
        if (song.path.isHttpAudioSource()) return@withContext null

        clearMetadataCache(song)
        scanEditedFile(song)
        delay(350)

        val updated = (querySystemSong(song) ?: song.withCurrentFileSnapshot())
            .withRepositoryTags()
            .withCurrentFileSnapshot()
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
            val songs = readLibraryCacheSongs(libraryCacheFile)
            _songs.value = songs
            _albums.value = songs.toAlbums()
        }.onFailure {
            Log.w("MusicRepo", "Failed to load music library cache", it)
        }
    }

    private fun readCachedSongs(): List<Song> {
        if (!libraryCacheFile.exists()) return emptyList()
        return runCatching {
            readLibraryCacheSongs(libraryCacheFile)
        }.getOrElse {
            Log.w("MusicRepo", "Failed to read music library cache for sync", it)
            emptyList()
        }
    }

    suspend fun getLyrics(
        song: Song,
        sourceMode: Int = SettingsManager.LYRIC_SOURCE_AUTO
    ): List<LyricLine> = lyricsManager.getLyrics(song, sourceMode)

    suspend fun reloadLyrics(song: Song, sourceMode: Int): List<LyricLine> = lyricsManager.reloadLyrics(song, sourceMode)

    suspend fun getLyricFormatAvailability(song: Song): LyricFormatAvailability = lyricsManager.getLyricFormatAvailability(song)

    suspend fun reloadLyricsByFormat(song: Song, preferTtml: Boolean): List<LyricLine> = lyricsManager.reloadLyricsByFormat(song, preferTtml)

    fun getReplayGain(song: Song): Float? {
        val cacheKey = song.metadataCacheKey()
        replayGainCache[cacheKey]?.let { return it }
        if (replayGainMissingCache.contains(cacheKey)) return null
        val gain = scanner.extractReplayGain(song.effectiveLocalPathForMetadata())
        if (gain == null) {
            replayGainCache.remove(cacheKey)
            replayGainMissingCache.add(cacheKey)
        } else {
            replayGainCache[cacheKey] = gain
            replayGainMissingCache.remove(cacheKey)
        }
        return gain
    }

    fun getAudioInfo(song: Song): AudioInfo {
        val cacheKey = song.metadataCacheKey()
        audioInfoCache[cacheKey]?.let { return it }
        val replayGainDb = getReplayGain(song)
        val metadataPath = song.effectiveLocalPathForMetadata()
        val wavMetadata = WavMetadataReader.read(metadataPath)
        audioTagRepository.readQualityInfoBlocking(metadataPath)?.let { quality ->
            val info = AudioInfo(
                format = song.audioFormatLabel(quality.mimeType),
                bitRate = quality.bitRate.takeIf { it > 0 }
                    ?: wavMetadata?.bitRate?.takeIf { it > 0 }
                    ?: song.estimatedBitRate(),
                sampleRate = quality.sampleRate.takeIf { it > 0 } ?: wavMetadata?.sampleRate ?: 0,
                bitDepth = quality.bitDepth.takeIf { it > 0 } ?: wavMetadata?.bitDepth ?: 0,
                channels = quality.channels.takeIf { it > 0 } ?: wavMetadata?.channels ?: 0,
                replayGainDb = replayGainDb
            )
            audioInfoCache[cacheKey] = info
            return info
        }
        wavMetadata?.takeIf { it.hasQuality }?.let { quality ->
            val info = AudioInfo(
                format = song.audioFormatLabel("audio/wav"),
                bitRate = quality.bitRate.takeIf { it > 0 } ?: song.estimatedBitRate(),
                sampleRate = quality.sampleRate,
                bitDepth = quality.bitDepth,
                channels = quality.channels,
                replayGainDb = replayGainDb
            )
            audioInfoCache[cacheKey] = info
            return info
        }
        val info = runCatching {
            val extractor = MediaExtractor()
            try {
                extractor.setDataSource(metadataPath)
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
                    sampleRate = (format?.getIntOrZero(MediaFormat.KEY_SAMPLE_RATE) ?: 0)
                        .takeIf { it > 0 } ?: wavMetadata?.sampleRate ?: 0,
                    bitDepth = (format?.getIntOrZero("bits-per-sample") ?: 0)
                        .takeIf { it > 0 } ?: wavMetadata?.bitDepth ?: 0,
                    channels = (format?.getIntOrZero(MediaFormat.KEY_CHANNEL_COUNT) ?: 0)
                        .takeIf { it > 0 } ?: wavMetadata?.channels ?: 0,
                    replayGainDb = replayGainDb
                )
            } finally {
                extractor.release()
            }
        }.getOrElse {
            Log.w("MusicRepo", "Failed to read audio info for ${song.path}", it)
            AudioInfo(format = song.audioFormatLabel(null), replayGainDb = replayGainDb)
        }
        audioInfoCache[cacheKey] = info
        return info
    }

    fun getSongTagInfo(song: Song): SongTagInfo {
        val cacheKey = song.metadataCacheKey()
        tagInfoCache[cacheKey]?.let { return it }
        val info = runCatching {
            audioTagRepository.readTagsBlocking(song.effectiveLocalPathForMetadata())?.toSongTagInfo() ?: SongTagInfo()
        }.getOrElse {
            Log.w("MusicRepo", "Failed to read tag info for ${song.path}", it)
            SongTagInfo()
        }
        tagInfoCache[cacheKey] = info
        return info
    }

    suspend fun songMatchesSearchSnapshot(song: Song, query: String): Boolean =
        snapshotManager.songMatchesSearchSnapshot(song, query)

    suspend fun filterSongsBySearchSnapshot(songs: List<Song>, query: String): List<Song> =
        snapshotManager.filterSongsBySearchSnapshot(songs, query)

    suspend fun getSongSearchText(song: Song): String =
        snapshotManager.getSongSearchText(song)

    suspend fun preloadLibrarySearchSnapshot(songs: List<Song>) =
        snapshotManager.preloadSearchSnapshot(songs)

    suspend fun clearLibrarySnapshotCache() = withContext(Dispatchers.IO) {
        snapshotManager.clearLibraryCache()
    }

    fun getSongRating(song: Song): Int =
        snapshotManager.getSongRating(song)

    suspend fun preloadSongRatings(songs: List<Song>) =
        snapshotManager.preloadSongRatings(songs)

    suspend fun writeSongRating(song: Song, rating: Int): Result<Song?> = withContext(Dispatchers.IO) {
        val safeRating = rating.coerceIn(0, 5)
        val result = try {
            writeSongTags(
                song,
                AudioTagInfo(rating = safeRating)
            )
        } catch (e: SecurityException) {
            val sender = createWritePermissionIntentSender(song)
                ?: return@withContext Result.failure(e)
            return@withContext Result.failure(WritePermissionRequiredException(sender))
        }
        result.writePermissionRequestIfNeeded(song)?.let { return@withContext it }
        result.map {
            val immediate = updateSongAfterLocalTagWrite(song)
            snapshotManager.updateRatingSnapshot(immediate, safeRating)
            val refreshed = refreshSongAfterExternalEdit(immediate) ?: immediate
            snapshotManager.updateRatingSnapshot(refreshed, safeRating)
            snapshotManager.saveAll()
            refreshed
        }
    }

    suspend fun writeSongCustomTag(song: Song, key: String, value: String): Result<Song?> = withContext(Dispatchers.IO) {
        val tagKey = key.trim()
        if (tagKey.isBlank()) {
            return@withContext Result.failure(IllegalArgumentException("Tag name is blank"))
        }
        val result = try {
            writeSongTags(
                song,
                AudioTagInfo(customTags = mapOf(tagKey to listOf(value)))
            )
        } catch (e: SecurityException) {
            val sender = createWritePermissionIntentSender(song)
                ?: return@withContext Result.failure(e)
            return@withContext Result.failure(WritePermissionRequiredException(sender))
        }
        result.writePermissionRequestIfNeeded(song)?.let { return@withContext it }
        result.map {
            val immediate = updateSongAfterLocalTagWrite(song)
            refreshSongAfterExternalEdit(immediate) ?: immediate
        }
    }

    suspend fun writeSongMetadata(song: Song, tags: AudioTagInfo): Result<Song?> = withContext(Dispatchers.IO) {
        val result = try {
            writeSongTags(song, tags)
        } catch (e: SecurityException) {
            val sender = createWritePermissionIntentSender(song)
                ?: return@withContext Result.failure(e)
            return@withContext Result.failure(WritePermissionRequiredException(sender))
        }
        result.writePermissionRequestIfNeeded(song)?.let { return@withContext it }
        result.map {
            val immediate = updateSongAfterLocalTagWrite(song)
            val refreshed = refreshSongAfterExternalEdit(immediate) ?: immediate
            tags.rating?.let { rating ->
                snapshotManager.updateRatingSnapshot(refreshed, rating.coerceIn(0, 5))
                snapshotManager.saveAll()
            }
            refreshed
        }
    }

    suspend fun writeSongEmbeddedCover(song: Song, cover: AudioCoverInfo?): Result<Song?> = withContext(Dispatchers.IO) {
        val result = try {
            writeSongCover(song, cover)
        } catch (e: SecurityException) {
            val sender = createWritePermissionIntentSender(song)
                ?: return@withContext Result.failure(e)
            return@withContext Result.failure(WritePermissionRequiredException(sender))
        }
        result.writePermissionRequestIfNeeded(song)?.let { return@withContext it }
        result.map {
            val immediate = updateSongAfterLocalTagWrite(song)
            refreshSongAfterExternalEdit(immediate) ?: immediate
        }
    }

    private suspend fun updateSongAfterLocalTagWrite(song: Song): Song = withContext(Dispatchers.IO) {
        clearMetadataCache(song)
        val updated = song.withCurrentFileSnapshot()
            .withRepositoryTags()
            .withCurrentFileSnapshot()
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

    private fun Song.withCurrentFileSnapshot(): Song {
        if (path.isHttpAudioSource()) return this
        val file = File(path)
        if (!file.exists()) return copy(dateModified = System.currentTimeMillis())
        return copy(
            fileSize = file.length().takeIf { it > 0L } ?: fileSize,
            dateModified = file.lastModified().takeIf { it > 0L } ?: System.currentTimeMillis()
        )
    }

    private suspend fun writeSongTags(song: Song, tags: AudioTagInfo): Result<Unit> {
        if (song.isWebDavRemoteSong()) {
            return Result.failure(IllegalArgumentException("Online / WebDAV songs are not supported for tag editing"))
        }
        val path = song.effectiveLocalPathForMetadata()
        val writableUri = song.writableAudioUri()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && writableUri != null) {
            val uriResult = runCatching {
                val pfd = context.contentResolver.openFileDescriptor(writableUri, "rw")
                    ?: error("Unable to open audio file for editing")
                pfd.use { descriptor ->
                    audioTagRepository.writeTags(descriptor, tags).getOrThrow()
                }
            }
            if (uriResult.isSuccess) {
                audioTagRepository.clear(path)
                return Result.success(Unit)
            }

            val error = uriResult.exceptionOrNull()
            if (error is SecurityException || error?.isWritePermissionError() == true) {
                return Result.failure(error)
            }
            Log.w("MusicRepo", "MediaStore tag write failed for ${song.path}, falling back to file path", error)
        }
        return audioTagRepository.writeTags(path, tags)
    }

    private suspend fun writeSongCover(song: Song, cover: AudioCoverInfo?): Result<Unit> {
        if (song.isWebDavRemoteSong()) {
            return Result.failure(IllegalArgumentException("Online / WebDAV songs are not supported for cover editing"))
        }
        val path = song.effectiveLocalPathForMetadata()
        val writableUri = song.writableAudioUri()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && writableUri != null) {
            val uriResult = runCatching {
                val pfd = context.contentResolver.openFileDescriptor(writableUri, "rw")
                    ?: error("Unable to open audio file for cover editing")
                pfd.use { descriptor ->
                    if (cover == null) {
                        audioTagRepository.removeEmbeddedCover(descriptor, path).getOrThrow()
                    } else {
                        audioTagRepository.writeEmbeddedCover(descriptor, path, cover).getOrThrow()
                    }
                }
            }
            if (uriResult.isSuccess) {
                clearMetadataCache(song)
                return Result.success(Unit)
            }

            val error = uriResult.exceptionOrNull()
            if (error is SecurityException || error?.isWritePermissionError() == true) {
                return Result.failure(error)
            }
            Log.w("MusicRepo", "MediaStore cover write failed for ${song.path}, falling back to file path", error)
        }
        return if (cover == null) {
            audioTagRepository.removeEmbeddedCover(path)
        } else {
            audioTagRepository.writeEmbeddedCover(path, cover)
        }
    }

    fun getFullAudioTagInfo(song: Song): AudioTagInfo? {
        return runCatching {
            audioTagRepository.readTagsBlocking(song.effectiveLocalPathForMetadata())
        }.getOrNull()
    }

    private fun createWritePermissionIntentSender(song: Song): android.content.IntentSender? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return null
        val uri = song.writableAudioUri() ?: return null
        return runCatching {
            MediaStore.createWriteRequest(context.contentResolver, listOf(uri)).intentSender
        }.getOrNull()
    }

    private fun Song.writableAudioUri(): Uri? {
        if (path.isContentAudioSource()) return Uri.parse(path)
        if (id > 0L) return ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id)
        return null
    }

    private fun Result<Unit>.writePermissionRequestIfNeeded(song: Song): Result<Song?>? {
        val error = exceptionOrNull() ?: return null
        if (!error.isWritePermissionError()) return null
        val sender = createWritePermissionIntentSender(song) ?: return null
        return Result.failure(WritePermissionRequiredException(sender))
    }

    private fun Throwable.isWritePermissionError(): Boolean {
        var current: Throwable? = this
        while (current != null) {
            if (current is SecurityException) return true
            val message = current.message.orEmpty()
            if (
                message.contains("permission", ignoreCase = true) ||
                message.contains("denied", ignoreCase = true) ||
                message.contains("EACCES", ignoreCase = true) ||
                message.contains("EPERM", ignoreCase = true) ||
                message.contains("Operation not permitted", ignoreCase = true)
            ) {
                return true
            }
            current = current.cause
        }
        return false
    }

    private fun Song.estimatedBitRate(): Int {
        if (fileSize <= 0L || duration <= 0L) return 0
        return ((fileSize * 8_000L) / duration).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
    }

    fun getCoverArt(song: Song): ByteArray? = coverArtManager.getCoverArt(song)

    fun getCoverArtBitmap(
        song: Song,
        maxSize: Int = 512,
        usage: CoverUsage = CoverUsage.ListThumbnail
    ): Bitmap? = coverArtManager.getCoverArtBitmap(song, maxSize, usage)

    fun getAlbumArtUri(albumId: Long): Uri? = coverArtManager.getAlbumArtUri(albumId)

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
        val deletedSongs = mutableListOf<Song>()
        val mediaStoreUrisNeedingPermission = mutableListOf<Uri>()

        songs.forEach { song ->
            if (tryDeleteSongDirect(song)) {
                deleted++
                deletedSongs += song
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                song.mediaStoreDeleteUriOrNull()?.let { mediaStoreUrisNeedingPermission += it }
            }
        }

        if (deletedSongs.isNotEmpty()) {
            removeDeletedSongsFromState(deletedSongs)
        }

        if (mediaStoreUrisNeedingPermission.isNotEmpty()) {
            val request = MediaStore.createDeleteRequest(context.contentResolver, mediaStoreUrisNeedingPermission.distinct())
            throw WritePermissionRequiredException(request.intentSender)
        }

        deleted
    }

    private fun tryDeleteSongDirect(song: Song): Boolean {
        if (song.onlineSource.isNotBlank()) return false
        val path = song.path.trim()
        if (path.isContentAudioSource()) {
            val uri = Uri.parse(path)
            val documentDeleted = runCatching {
                DocumentFile.fromSingleUri(context, uri)?.delete() == true
            }.getOrDefault(false)
            if (documentDeleted) return true
            return runCatching { context.contentResolver.delete(uri, null, null) > 0 }.getOrDefault(false)
        }

        val fileDeleted = runCatching {
            val file = File(path)
            file.exists() && file.delete()
        }.getOrDefault(false)
        if (fileDeleted) return true

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R && song.id > 0L) {
            return runCatching {
                val uri = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, song.id)
                context.contentResolver.delete(uri, null, null) > 0
            }.getOrDefault(false)
        }

        return false
    }

    private fun Song.mediaStoreDeleteUriOrNull(): Uri? {
        if (onlineSource.isNotBlank() || id <= 0L) return null
        if (path.isContentAudioSource() && !path.isMediaStoreContentAudioSource()) {
            return null
        }
        return ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id)
    }

    private suspend fun removeDeletedSongsFromState(deletedSongs: Collection<Song>) {
        val deletedKeys = deletedSongs.map { it.deleteIdentityKey() }.toSet()
        val deletedIds = deletedSongs.map { it.id }.filter { it > 0L }.toSet()
        _songs.value = _songs.value.filterNot { song ->
            (song.id > 0L && song.id in deletedIds) || song.deleteIdentityKey() in deletedKeys
        }
        _albums.value = _songs.value.toAlbums()
        saveLibraryCache(_songs.value, _albums.value)
    }

    private fun Song.deleteIdentityKey(): String = "$id|$path"

    suspend fun removeSongsFromLibrary(songs: Collection<Song>): Unit = withContext(Dispatchers.IO) {
        if (songs.isEmpty()) return@withContext
        removeDeletedSongsFromState(songs)
        Unit
    }

    fun clearCache() {
        lyricsManager.clearCache()
        coverArtManager.clearCache()
        snapshotManager.clearCache()
        audioInfoCache.clear()
        tagInfoCache.clear()
        replayGainCache.clear()
        replayGainMissingCache.clear()
    }

    fun clearMetadataCache(song: Song) {
        lyricsManager.clearMetadataCache(song)
        coverArtManager.clearMetadataCache(song)
        snapshotManager.clearMetadataCache(song)
        val metadataPrefix = "${song.metadataCachePrefix()}:"
        audioInfoCache.removeKeysMatching { it.startsWith(metadataPrefix) }
        tagInfoCache.removeKeysMatching { it.startsWith(metadataPrefix) || it.startsWith("${song.id}:") }
        replayGainCache.removeKeysMatching { it.startsWith(metadataPrefix) }
        replayGainMissingCache.removeIf { it.startsWith(metadataPrefix) }
        audioTagRepository.clear(song.effectiveLocalPathForMetadataBlocking(settingsManager, httpClient, remoteAudioCacheDir, remoteMetadataHeaderCacheDir))
        if (song.isWebDavRemoteSong()) {
            song.webDavHeaderCacheFile(remoteMetadataHeaderCacheDir).delete()
            song.webDavFullCacheFile(remoteAudioCacheDir).delete()
        }
    }

    fun clearRemoteMetadataCache() {
        clearCache()
        runCatching {
            if (remoteAudioCacheDir.exists()) {
                remoteAudioCacheDir.deleteRecursively()
            }
            if (remoteMetadataHeaderCacheDir.exists()) {
                remoteMetadataHeaderCacheDir.deleteRecursively()
            }
        }.onFailure {
            Log.w("MusicRepo", "Failed to clear online metadata cache", it)
        }
    }

    suspend fun resolveSongForPlayback(song: Song): Song = withContext(Dispatchers.IO) {
        runCatching {
            song.withRepositoryTags(allowFullDownload = song.isWebDavRemoteSong() && song.isLikelyWavAudio())
        }.getOrElse { error ->
            Log.w("MusicRepo", "Failed to resolve playback song for ${song.path}", error)
            song
        }
    }

    suspend fun prefetchWebDavMetadataHeaders(songs: List<Song>, maxItems: Int = 80) = supervisorScope {
        val targets = songs
            .asSequence()
            .filter { it.isWebDavRemoteSong() }
            .distinctBy { it.path }
            .take(maxItems.coerceIn(1, 100))
            .toList()
        if (targets.isEmpty()) return@supervisorScope
        val config = loadWebDavConfig() ?: return@supervisorScope
        val semaphore = Semaphore(3)
        targets.forEach { song ->
            launch(Dispatchers.IO) {
                runCatching {
                    semaphore.withPermit {
                        val headerFile = song.webDavHeaderCacheFile()
                        if (headerFile.exists() && headerFile.length() > 0L) {
                            Log.d("MusicRepo", "WebDAV header prefetch hit cache url=${song.path.webDavSafeLogUrl()}")
                            return@withPermit
                        }
                        Log.d("MusicRepo", "WebDAV header prefetch start url=${song.path.webDavSafeLogUrl()}")
                        val cached = downloadWebDavMetadataHeader(song, config)
                        if (cached != null) {
                            Log.d("MusicRepo", "WebDAV header prefetch success url=${song.path.webDavSafeLogUrl()} bytes=${headerFile.length()}")
                        } else {
                            Log.d("MusicRepo", "WebDAV header prefetch skipped url=${song.path.webDavSafeLogUrl()}")
                        }
                    }
                }.onFailure { error ->
                    AppLogStore.warn(
                        context,
                        "MusicRepoWebDav",
                        "WebDAV header prefetch failed url=${song.path.webDavSafeLogUrl()}",
                        error,
                        AppLogType.NETWORK
                    )
                }
            }
        }
    }

    private fun Song.effectiveLocalPathForMetadata(allowFullDownload: Boolean = false): String {
        if (path.isContentAudioSource()) return path
        if (!isWebDavRemoteSong()) return path
        val fullCache = webDavFullCacheFile()
        if (fullCache.exists() && fullCache.length() > 0L) return fullCache.absolutePath
        val headerCache = webDavHeaderCacheFile()
        if (headerCache.exists() && headerCache.length() > 0L) return headerCache.absolutePath
        val config = runBlocking(Dispatchers.IO) { loadWebDavConfig() } ?: return path
        downloadWebDavMetadataHeader(this, config)?.let { return it.absolutePath }
        if (!allowFullDownload) return path
        return runCatching {
            WebDavClient.downloadToFile(path, config, fullCache).absolutePath
        }.getOrElse {
            Log.w("MusicRepo", "Failed to cache remote metadata file for $path", it)
            path
        }
    }

    private fun String.sha256(): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }

    private suspend fun loadWebDavConfig(): WebDavConfig? {
        val url = settingsManager.webDavUrl.first().trim()
        if (url.isBlank()) return null
        return WebDavConfig(
            url = url,
            username = settingsManager.webDavUsername.first(),
            password = settingsManager.webDavPassword.first()
        )
    }

    private fun Song.isWebDavRemoteSong(): Boolean =
        path.isHttpAudioSource() &&
            onlineSource.isBlank()

    private fun Song.webDavCacheExtension(): String =
        fileName.substringAfterLast('.', path.substringBefore('?').substringBefore('#').substringAfterLast('.', "audio"))
            .ifBlank { "audio" }

    private fun Song.isLikelyWavAudio(): Boolean =
        webDavCacheExtension().lowercase() in setOf("wav", "wave") ||
            mimeType.contains("wav", ignoreCase = true) ||
            mimeType.contains("wave", ignoreCase = true)

    private fun Song.webDavFullCacheFile(): File =
        File(remoteAudioCacheDir, "${path.sha256()}.${webDavCacheExtension()}")

    private fun Song.webDavHeaderCacheFile(): File =
        File(remoteMetadataHeaderCacheDir, "${path.sha256()}.${webDavCacheExtension()}")

    private fun downloadWebDavMetadataHeader(song: Song, config: WebDavConfig): File? {
        val target = song.webDavHeaderCacheFile()
        if (target.exists() && target.length() > 0L) return target
        return WebDavClient.downloadHeaderToFile(song.path, config, target)
    }

    private fun String.webDavSafeLogUrl(): String =
        runCatching {
            val uri = java.net.URI(this)
            if (uri.userInfo == null) {
                this
            } else {
                java.net.URI(uri.scheme, "***", uri.host, uri.port, uri.path, uri.query, uri.fragment).toString()
            }
        }.getOrDefault(this)

    private fun MediaFormat.getIntOrZero(key: String): Int {
        return if (containsKey(key)) runCatching { getInteger(key) }.getOrDefault(0) else 0
    }

    private fun Song.audioFormatLabel(mime: String?): String {
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

    private suspend fun saveLibraryCache(songs: List<Song>, albums: List<Album>) = withContext(Dispatchers.IO) {
        runCatching {
            val root = JSONObject()
                .put("version", 1)
                .put("songs", songsToLibraryCacheJsonArray(songs))
                .put("albums", albumsToLibraryCacheJsonArray(albums))
            libraryCacheFile.writeText(root.toString())
        }.onFailure {
            Log.w("MusicRepo", "Failed to save music library cache", it)
        }
    }

    private fun List<Song>.toAlbums(): List<Album> {
        return LibraryAlbumAggregator.toAlbums(this)
    }

    private fun Song.withRepositoryTags(allowFullDownload: Boolean = false): Song {
        val metadataPath = effectiveLocalPathForMetadata(allowFullDownload)
        val tagInfo = runCatching {
            audioTagRepository.readTagsBlocking(metadataPath)
        }.getOrElse { error ->
            Log.w("MusicRepo", "Failed to refresh library tags for $path", error)
            null
        }
        val wavMetadata = runCatching { WavMetadataReader.read(metadataPath) }
            .getOrNull()

        val mergedArtist = tagInfo?.artist.takeIf { it.isUsableArtistText() }
            ?: wavMetadata?.artist.takeIf { it.isUsableArtistText() }
            ?: artist.takeIf { it.isUsableArtistText() }
            ?: "Unknown Artist"
        val mergedAlbum = tagInfo?.album.takeIf { it.isUsableAlbumText() }
            ?: wavMetadata?.album.takeIf { it.isUsableAlbumText() }
            ?: album.takeIf { it.isUsableAlbumText() && !it.looksLikeLastFolderName(path) }
            ?: "Unknown Album"
        val mergedAlbumArtist = tagInfo?.albumArtist.takeIf { it.isUsableArtistText() }
            ?: wavMetadata?.albumArtist.takeIf { it.isUsableArtistText() }
            ?: albumArtist.takeIf { it.isUsableArtistText() }
            ?: ""

        return copy(
            title = tagInfo?.title.takeIf { it.isUsableTagText() }
                ?: wavMetadata?.title.takeIf { it.isUsableTagText() }
                ?: title.takeIf { it.isUsableTagText() }
                ?: fileName.substringBeforeLast('.').ifBlank { path.substringAfterLast('/') },
            artist = mergedArtist,
            album = mergedAlbum,
            albumArtist = mergedAlbumArtist,
            genre = tagInfo?.genre.takeIf { it.isUsableTagText() } ?: wavMetadata?.genre.takeIf { it.isUsableTagText() } ?: genre,
            year = tagInfo?.year.takeIf { it.isUsableTagText() } ?: wavMetadata?.year.takeIf { it.isUsableTagText() } ?: year,
            composer = tagInfo?.composer.takeIf { it.isUsableTagText() } ?: wavMetadata?.composer.takeIf { it.isUsableTagText() } ?: composer,
            lyricist = tagInfo?.lyricist.takeIf { it.isUsableTagText() } ?: wavMetadata?.lyricist.takeIf { it.isUsableTagText() } ?: lyricist,
            trackNumber = tagInfo?.trackNumber ?: wavMetadata?.trackNumber ?: trackNumber,
            discNumber = tagInfo?.discNumber ?: wavMetadata?.discNumber ?: discNumber
        ).withFinalLibraryFallbacks()
    }

    private fun Song.withFinalLibraryFallbacks(): Song {
        val fallbackArtist = artist.takeIf { it.isUsableArtistText() } ?: "Unknown Artist"
        val fallbackAlbum = album.takeIf { it.isUsableAlbumText() && !it.looksLikeLastFolderName(path) }
            ?: "Unknown Album"
        return copy(
            title = title.takeIf { it.isUsableTagText() } ?: fileName.substringBeforeLast('.').ifBlank { path.substringAfterLast('/') },
            artist = fallbackArtist,
            album = fallbackAlbum,
            albumArtist = albumArtist.takeIf { it.isUsableArtistText() }.orEmpty()
        )
    }

    private fun Song.hasSameLibraryTags(other: Song): Boolean =
        title == other.title &&
            artist == other.artist &&
            album == other.album &&
            albumArtist == other.albumArtist &&
            genre == other.genre &&
            year == other.year &&
            composer == other.composer &&
            lyricist == other.lyricist &&
            trackNumber == other.trackNumber &&
            discNumber == other.discNumber

    private fun Song.needsMetadataPlaceholderRefresh(): Boolean =
        LibraryNormalizer.isGeneratedUnknownArtistPlaceholder(artist) ||
            LibraryNormalizer.isGeneratedUnknownAlbumPlaceholder(album) ||
            (album.isUsableAlbumText() && album.looksLikeLastFolderName(path))

    private suspend fun scanEditedFile(song: Song) = suspendCoroutine<Unit> { continuation ->
        val path = song.path.takeIf { it.isNotBlank() }
        if (path == null || path.isContentAudioSource()) {
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
                audioTagRepository.readTagsBlocking(song.effectiveLocalPathForMetadata())?.toSongTagInfo()
            }.getOrNull() ?: SongTagInfo()
            val wavInfo = runCatching { WavMetadataReader.read(song.effectiveLocalPathForMetadata()) }
                .getOrNull()
            val path = cursor.getString(6).orEmpty().ifBlank { song.path }
            val file = File(path)
            val fileSize = file.length().takeIf { file.exists() && it > 0L }
                ?: cursor.getLong(8)
            val dateModified = file.lastModified().takeIf { file.exists() && it > 0L }
                ?: (cursor.getLong(11) * 1000L).takeIf { it > 0L }
                ?: song.dateModified
            Song(
                id = cursor.getLong(0),
                title = tagInfo.title.usableTagText().ifBlank {
                    wavInfo?.title.usableTagText().ifBlank {
                        cursor.getString(1)?.usableTagText().orEmpty().ifBlank { song.title }
                    }
                },
                artist = tagInfo.artist.usableArtistText().ifBlank {
                    wavInfo?.artist.usableArtistText().ifBlank {
                        cursor.getString(2)?.usableArtistText().orEmpty().ifBlank { song.artist }
                    }
                },
                album = tagInfo.album.usableAlbumText().ifBlank {
                    wavInfo?.album.usableAlbumText().ifBlank {
                        cursor.getString(3)?.usableAlbumText().orEmpty().ifBlank { song.album }
                    }
                },
                albumId = cursor.getLong(4),
                duration = cursor.getLong(5).takeIf { it > 0L } ?: song.duration,
                path = path,
                fileName = cursor.getString(7).orEmpty().ifBlank { song.fileName },
                fileSize = fileSize,
                mimeType = cursor.getString(9).orEmpty().ifBlank { song.mimeType },
                dateAdded = cursor.getLong(10) * 1000L,
                dateModified = dateModified,
                trackNumber = tagInfo.track.takeIf { it.isNotBlank() }?.toIntOrNull()
                    ?: wavInfo?.trackNumber
                    ?: cursor.getInt(12).let { if (it > 1000) it % 1000 else it },
                discNumber = wavInfo?.discNumber
                    ?: cursor.getInt(12).let { if (it >= 1000) it / 1000 else song.discNumber },
                albumArtist = tagInfo.albumArtist.usableArtistText().ifBlank {
                    wavInfo?.albumArtist.usableArtistText().ifBlank { song.albumArtist }
                },
                genre = tagInfo.genre.ifBlank { wavInfo?.genre.orEmpty().ifBlank { song.genre } },
                year = tagInfo.year.ifBlank { wavInfo?.year.orEmpty().ifBlank { song.year } },
                composer = tagInfo.composer.ifBlank { wavInfo?.composer.orEmpty().ifBlank { song.composer } },
                lyricist = tagInfo.lyricist.ifBlank { wavInfo?.lyricist.orEmpty().ifBlank { song.lyricist } },
                coverUrl = song.coverUrl,
                onlineSource = song.onlineSource,
                onlineId = song.onlineId,
                onlineLyrics = song.onlineLyrics,
                onlineLyricTranslation = song.onlineLyricTranslation
            ).withFinalLibraryFallbacks()
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

    private fun Song.coverDataCacheKey(): String =
        "${coverCacheKey()}:$dateModified:$fileSize"

    private fun Song.metadataCachePrefix(): String {
        val source = when {
            path.isNotBlank() -> "path:$path"
            onlineSource.isNotBlank() || onlineId.isNotBlank() -> "online:$onlineSource:$onlineId:$path"
            else -> "media:$id:$title:$artist:$album:$duration"
        }
        return source.sha256()
    }

    private fun Song.metadataCacheKey(): String =
        "${metadataCachePrefix()}:$dateModified:$fileSize"

    private fun Song.searchSnapshotKey(): String =
        "${id}|${path.sha256()}"

    private fun AudioTagInfo.embeddedLyricsContent(preferTtml: Boolean): String? {
        val names = if (preferTtml) {
            listOf(
                "TTML LYRICS",
                "TTML LYRIC",
                "TTMLLYRICS",
                "TTMLLYRIC",
                "TTML",
                "SYNCEDLYRICS",
                "UNSYNCEDLYRICS",
                "UNSYNCED LYRICS",
                "LYRICS",
                "USLT",
                "SYLT",
                "LYRIC"
            )
        } else {
            listOf("SYNCEDLYRICS", "UNSYNCEDLYRICS", "UNSYNCED LYRICS", "LYRICS", "USLT", "SYLT", "LYRIC")
        }
        names.forEach { target ->
            customTags.firstMatchingTagValue(target)?.takeIf { it.looksLikeTtmlLyrics() == preferTtml }?.let { return it }
        }
        return lyrics?.takeIf { it.isNotBlank() && (preferTtml == it.looksLikeTtmlLyrics()) }
    }

    private fun Map<String, List<String>>.firstMatchingTagValue(target: String): String? {
        val normalizedTarget = target.normalizedTagName()
        return entries.firstOrNull { (key, values) ->
            key.normalizedTagName() == normalizedTarget && values.any { it.isNotBlank() }
        }?.value?.firstOrNull { it.isNotBlank() }
    }

    private fun String.normalizedTagName(): String =
        uppercase().filter { it.isLetterOrDigit() }

    private fun String.looksLikeTtmlLyrics(): Boolean =
        contains("<tt", ignoreCase = true) && contains("</tt", ignoreCase = true)

    private fun findExternalLyricContentByFormat(songPath: String, preferTtml: Boolean): String? {
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

    private fun readTextIfExists(path: String): String? =
        runCatching {
            val file = File(path)
            if (!file.exists()) return null
            file.readText()
        }.getOrNull()

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
        return LibraryNormalizer.cleanedTagText(this)
    }

    private fun String?.usableArtistText(): String {
        return LibraryNormalizer.cleanedArtistText(this)
    }

    private fun String?.usableAlbumText(): String {
        return LibraryNormalizer.cleanedAlbumText(this)
    }

    private fun String?.isUsableTagText(): Boolean =
        usableTagText().isNotBlank()

    private fun String?.isUsableArtistText(): Boolean =
        usableArtistText().isNotBlank()

    private fun String?.isUsableAlbumText(): Boolean {
        return usableAlbumText().isNotBlank()
    }

    private fun String.looksLikeLastFolderName(path: String): Boolean {
        return LibraryNormalizer.looksLikeLastFolderName(this, path)
    }

    private fun AudioTagInfo.toSongTagInfo(): SongTagInfo =
        SongTagInfo(
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

    private fun Map<String, List<String>>.flattenForSearch(): String =
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

    private fun Int?.normalizeTagRatingToStars(): Int {
        val raw = this ?: return 0
        return when {
            raw <= 0 -> 0
            raw <= 5 -> raw
            raw <= 100 -> kotlin.math.round(raw / 20f).toInt()
            raw <= 255 -> kotlin.math.round(raw / 255f * 5f).toInt()
            else -> 0
        }.coerceIn(0, 5)
    }

    private fun String.isIgnoredSearchTagKey(): Boolean {
        val normalized = trim().lowercase()
        return normalized in setOf(
            "apic",
            "covr",
            "picture",
            "metadata_block_picture",
            "unsyncedlyrics",
            "uslt",
            "lyrics",
            "lyric",
            "syncedlyrics",
            "replaygain_track_gain",
            "replaygain_track_peak",
            "replaygain_album_gain",
            "replaygain_album_peak",
            "replaygain_reference_loudness"
        )
    }

    private fun String.normalizedLocalFolderPath(): String? {
        val normalized = trim().replace('\\', '/').trimEnd('/').lowercase()
        return normalized.takeIf { it.isNotBlank() }
    }

    private fun String.isAllowedByLocalFolderFilters(
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

    private data class LibrarySyncInfo(
        val key: String,
        val path: String,
        val fileSize: Long,
        val dateModified: Long
    )
}
