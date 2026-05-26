package com.ella.music.data.metadata

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaExtractor
import android.media.MediaFormat
import android.os.ParcelFileDescriptor
import android.util.Log
import android.util.LruCache
import com.ella.music.data.model.SongTagInfo
import com.ella.music.data.scanner.MusicScanner
import com.lonx.audiotag.model.AudioPicture
import com.lonx.audiotag.rw.AudioTagReader as LyricoReader
import com.lonx.audiotag.rw.AudioTagWriter as LyricoWriter
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext

data class AudioTagInfo(
    val title: String? = null,
    val artist: String? = null,
    val album: String? = null,
    val albumArtist: String? = null,
    val composer: String? = null,
    val lyricist: String? = null,
    val genre: String? = null,
    val year: String? = null,
    val trackNumber: Int? = null,
    val discNumber: Int? = null,
    val comment: String? = null,
    val lyrics: String? = null,
    val copyright: String? = null,
    val neteaseKey: String? = null,
    val rating: Int? = null,
    val replayGainTrackGain: String? = null,
    val replayGainTrackPeak: String? = null,
    val replayGainAlbumGain: String? = null,
    val replayGainAlbumPeak: String? = null,
    val replayGainReferenceLoudness: String? = null,
    val customTags: Map<String, List<String>> = emptyMap()
)

data class AudioCoverInfo(
    val bytes: ByteArray,
    val mimeType: String? = null
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is AudioCoverInfo) return false
        return bytes.contentEquals(other.bytes) && mimeType == other.mimeType
    }

    override fun hashCode(): Int = 31 * bytes.contentHashCode() + (mimeType?.hashCode() ?: 0)
}

data class AudioQualityInfo(
    val mimeType: String = "",
    val bitRate: Int = 0,
    val sampleRate: Int = 0,
    val bitDepth: Int = 0,
    val channels: Int = 0
)

interface AudioTagReader {
    suspend fun readTags(path: String): AudioTagInfo?
    suspend fun readEmbeddedCover(path: String): AudioCoverInfo?
    suspend fun readEmbeddedLyrics(path: String): String?
}

interface AudioTagWriter {
    suspend fun writeTags(path: String, tags: AudioTagInfo): Result<Unit>
    suspend fun writeEmbeddedCover(path: String, cover: AudioCoverInfo): Result<Unit>
    suspend fun removeEmbeddedCover(path: String): Result<Unit>
}

class AudioTagRepository(
    private val primary: AudioTagReader,
    private val legacy: AudioTagReader,
    private val writer: AudioTagWriter? = primary as? AudioTagWriter
) {
    private val coverDataCache = object : LruCache<String, AudioCoverInfo>(8 * 1024) {
        override fun sizeOf(key: String, value: AudioCoverInfo): Int = value.bytes.size / 1024
    }
    private val coverBitmapCache = object : LruCache<String, Bitmap>(16 * 1024) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount / 1024
    }
    private val lyricsCache = LruCache<String, String>(64)
    private val tagsCache = LruCache<String, AudioTagInfo>(64)
    private val qualityCache = LruCache<String, AudioQualityInfo>(64)

    suspend fun readTags(path: String): AudioTagInfo? = withContext(Dispatchers.IO) {
        val key = cacheKey(path) ?: return@withContext null
        tagsCache.get(key)?.let { return@withContext it }
        val tags = readWithFallback("tags", path) { it.readTags(path) }?.takeIf { it.hasUsefulTagData() }
        tags?.also { tagsCache.put(key, it) }
    }

    suspend fun readEmbeddedCover(path: String): AudioCoverInfo? = withContext(Dispatchers.IO) {
        val key = cacheKey(path) ?: return@withContext null
        coverDataCache.get(key)?.let { return@withContext it }
        val cover = readWithFallback("cover", path) { it.readEmbeddedCover(path) }?.takeIf { it.bytes.isNotEmpty() }
        cover?.also { coverDataCache.put(key, it) }
    }

    suspend fun readEmbeddedLyrics(path: String): String? = withContext(Dispatchers.IO) {
        val key = cacheKey(path) ?: return@withContext null
        lyricsCache.get(key)?.let { return@withContext it }
        val lyrics = readWithFallback("lyrics", path) { it.readEmbeddedLyrics(path) }?.takeIf { it.isNotBlank() }
        lyrics?.also { lyricsCache.put(key, it) }
    }

    suspend fun writeTags(path: String, tags: AudioTagInfo): Result<Unit> =
        writer?.writeTags(path, tags)?.onSuccess { clear(path) } ?: Result.failure(UnsupportedOperationException("No audio tag writer"))

    suspend fun writeEmbeddedCover(path: String, cover: AudioCoverInfo): Result<Unit> =
        writer?.writeEmbeddedCover(path, cover)?.onSuccess { clear(path) } ?: Result.failure(UnsupportedOperationException("No audio tag writer"))

    suspend fun removeEmbeddedCover(path: String): Result<Unit> =
        writer?.removeEmbeddedCover(path)?.onSuccess { clear(path) } ?: Result.failure(UnsupportedOperationException("No audio tag writer"))

    fun readTagsBlocking(path: String): AudioTagInfo? = runBlocking(Dispatchers.IO) { readTags(path) }

    fun readEmbeddedCoverDataBlocking(path: String): ByteArray? =
        runBlocking(Dispatchers.IO) { readEmbeddedCover(path)?.bytes }

    fun readEmbeddedCoverBitmapBlocking(path: String, maxSize: Int = 512): Bitmap? {
        val key = cacheKey(path)?.let { "$it:${maxSize.coerceIn(64, 3000)}" } ?: return null
        coverBitmapCache.get(key)?.let { return it }
        val data = readEmbeddedCoverDataBlocking(path) ?: return null
        return decodeCoverBitmap(data, maxSize)?.also { coverBitmapCache.put(key, it) }
    }

    fun readEmbeddedLyricsBlocking(path: String): String? = runBlocking(Dispatchers.IO) { readEmbeddedLyrics(path) }

    fun readQualityInfoBlocking(path: String): AudioQualityInfo? {
        val key = cacheKey(path) ?: return null
        qualityCache.get(key)?.let { return it }
        return runCatching {
            val extractor = MediaExtractor()
            try {
                extractor.setDataSource(path)
                var audioFormat: MediaFormat? = null
                for (index in 0 until extractor.trackCount) {
                    val format = extractor.getTrackFormat(index)
                    val mime = format.getString(MediaFormat.KEY_MIME).orEmpty()
                    if (mime.startsWith("audio/")) {
                        audioFormat = format
                        break
                    }
                }
                val format = audioFormat ?: return null
                AudioQualityInfo(
                    mimeType = format.getString(MediaFormat.KEY_MIME).orEmpty(),
                    bitRate = format.getIntOrZero(MediaFormat.KEY_BIT_RATE),
                    sampleRate = format.getIntOrZero(MediaFormat.KEY_SAMPLE_RATE),
                    bitDepth = format.getIntOrZero("bits-per-sample"),
                    channels = format.getIntOrZero(MediaFormat.KEY_CHANNEL_COUNT)
                )
            } finally {
                extractor.release()
            }
        }.onFailure {
            Log.w(TAG, "Failed to read quality info for $path", it)
        }.getOrNull()?.also { qualityCache.put(key, it) }
    }

    fun clear(path: String) {
        val key = cacheKey(path) ?: return
        coverDataCache.remove(key)
        lyricsCache.remove(key)
        tagsCache.remove(key)
        qualityCache.remove(key)
        for (bitmapKey in coverBitmapCache.snapshot().keys) {
            if (bitmapKey.startsWith("$key:")) coverBitmapCache.remove(bitmapKey)
        }
    }

    private suspend fun <T> readWithFallback(
        label: String,
        path: String,
        block: suspend (AudioTagReader) -> T?
    ): T? {
        val primaryResult = runCatching { block(primary) }
            .onFailure { Log.d(TAG, "lyrico-audiotag $label failed for $path", it) }
            .getOrNull()
        if (primaryResult != null) return primaryResult
        return runCatching { block(legacy) }
            .onFailure { Log.d(TAG, "legacy $label failed for $path", it) }
            .getOrNull()
    }

    private fun cacheKey(path: String): String? {
        if (path.isBlank() || path.startsWith("http://") || path.startsWith("https://")) return null
        val file = File(path)
        if (!file.exists() || !file.isFile) return null
        return "${file.absolutePath}:${file.lastModified()}:${file.length()}"
    }

    private fun decodeCoverBitmap(data: ByteArray, maxSize: Int): Bitmap? {
        val targetSize = maxSize.coerceIn(64, 3000)
        return runCatching {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(data, 0, data.size, bounds)
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
            var sampleSize = 1
            while ((bounds.outWidth / sampleSize) > targetSize || (bounds.outHeight / sampleSize) > targetSize) {
                sampleSize *= 2
            }
            BitmapFactory.decodeByteArray(
                data,
                0,
                data.size,
                BitmapFactory.Options().apply {
                    inSampleSize = sampleSize.coerceAtLeast(1)
                    inPreferredConfig = Bitmap.Config.RGB_565
                }
            )
        }.getOrNull()
    }

    private fun AudioTagInfo.hasUsefulTagData(): Boolean =
        listOf(title, artist, album, albumArtist, composer, lyricist, genre, year, comment, lyrics, copyright, neteaseKey)
            .any { !it.isNullOrBlank() } ||
            trackNumber != null ||
            discNumber != null ||
            rating != null ||
            customTags.isNotEmpty()

    private fun MediaFormat?.getIntOrZero(key: String): Int =
        runCatching { if (this != null && containsKey(key)) getInteger(key) else 0 }.getOrDefault(0)

    companion object {
        private const val TAG = "AudioTagRepository"
    }
}

class LyricoAudioTagReaderWriter : AudioTagReader, AudioTagWriter {
    override suspend fun readTags(path: String): AudioTagInfo? = withPfd(path, ParcelFileDescriptor.MODE_READ_ONLY) { pfd ->
        val data = LyricoReader.read(pfd, readPictures = false)
        val raw = data.rawProperties.orEmpty().mapValues { (_, values) -> values.toList() }
        AudioTagInfo(
            title = data.title,
            artist = data.artist,
            album = data.album,
            albumArtist = data.albumArtist,
            composer = data.composer,
            lyricist = data.lyricist,
            genre = data.genre,
            year = data.date,
            trackNumber = data.trackNumber?.substringBefore('/')?.toIntOrNull(),
            discNumber = data.discNumber,
            comment = data.comment,
            lyrics = data.lyrics?.ifBlank { null } ?: raw.bestLyrics(),
            copyright = data.copyright,
            neteaseKey = raw.firstTagValue(
                "163KEY",
                "163 KEY",
                "NETEASEKEY",
                "NETEASE_KEY",
                "NETEASE_CLOUD_MUSIC_KEY",
                "CLOUDMUSICKEY",
                "CLOUDMUSIC_KEY",
                "MUSIC163KEY"
            ),
            rating = data.rating,
            replayGainTrackGain = data.replayGainTrackGain,
            replayGainTrackPeak = data.replayGainTrackPeak,
            replayGainAlbumGain = data.replayGainAlbumGain,
            replayGainAlbumPeak = data.replayGainAlbumPeak,
            replayGainReferenceLoudness = data.replayGainReferenceLoudness,
            customTags = raw
        )
    }

    override suspend fun readEmbeddedCover(path: String): AudioCoverInfo? =
        withPfd(path, ParcelFileDescriptor.MODE_READ_ONLY) { pfd ->
            val data = LyricoReader.read(pfd, readPictures = true)
            val picture = data.pictures.firstOrNull { it.pictureType.equals("Front Cover", ignoreCase = true) }
                ?: data.pictures.firstOrNull()
            picture?.let { AudioCoverInfo(bytes = it.data, mimeType = it.mimeType) }
        }

    override suspend fun readEmbeddedLyrics(path: String): String? =
        readTags(path)?.lyrics?.takeIf { it.isNotBlank() }

    override suspend fun writeTags(path: String, tags: AudioTagInfo): Result<Unit> = runCatching {
        val updates = tags.toWritableMap()
        if (updates.isEmpty()) return@runCatching
        val ok = withPfd(path, ParcelFileDescriptor.MODE_READ_WRITE) { pfd ->
            LyricoWriter.writeTags(pfd, updates, preserveOldTags = true)
        } ?: false
        check(ok) { "lyrico-audiotag writeTags returned false" }
    }

    override suspend fun writeEmbeddedCover(path: String, cover: AudioCoverInfo): Result<Unit> = runCatching {
        val picture = AudioPicture(
            data = cover.bytes,
            mimeType = cover.mimeType ?: "image/jpeg",
            description = "",
            pictureType = "Front Cover"
        )
        val ok = withPfd(path, ParcelFileDescriptor.MODE_READ_WRITE) { pfd ->
            LyricoWriter.writePictures(pfd, listOf(picture))
        } ?: false
        check(ok) { "lyrico-audiotag writeEmbeddedCover returned false" }
    }

    override suspend fun removeEmbeddedCover(path: String): Result<Unit> = runCatching {
        val ok = withPfd(path, ParcelFileDescriptor.MODE_READ_WRITE) { pfd ->
            LyricoWriter.writePictures(pfd, emptyList())
        } ?: false
        check(ok) { "lyrico-audiotag removeEmbeddedCover returned false" }
    }

    private suspend fun <T> withPfd(path: String, mode: Int, block: suspend (ParcelFileDescriptor) -> T): T? =
        withContext(Dispatchers.IO) {
            val file = File(path)
            if (!file.exists() || !file.isFile) return@withContext null
            ParcelFileDescriptor.open(file, mode).use { pfd -> block(pfd) }
        }

    private fun AudioTagInfo.toWritableMap(): Map<String, String> = buildMap {
        title?.let { put("TITLE", it) }
        artist?.let { put("ARTIST", it) }
        album?.let { put("ALBUM", it) }
        albumArtist?.let { put("ALBUMARTIST", it) }
        composer?.let { put("COMPOSER", it) }
        lyricist?.let { put("LYRICIST", it) }
        genre?.let { put("GENRE", it) }
        year?.let { put("DATE", it) }
        trackNumber?.let { put("TRACKNUMBER", it.toString()) }
        discNumber?.let { put("DISCNUMBER", it.toString()) }
        comment?.let { put("COMMENT", it) }
        lyrics?.let { put("LYRICS", it) }
        customTags.forEach { (key, values) ->
            if (key.isNotBlank() && values.isNotEmpty()) put(key, values.joinToString("; "))
        }
    }
}

class LegacyAudioTagReader(
    private val scanner: MusicScanner
) : AudioTagReader {
    override suspend fun readTags(path: String): AudioTagInfo? = withContext(Dispatchers.IO) {
        scanner.extractSongTagInfo(path).toAudioTagInfo().takeIf { it.hasLegacyTagData() }
    }

    override suspend fun readEmbeddedCover(path: String): AudioCoverInfo? = withContext(Dispatchers.IO) {
        scanner.extractCoverArt(path)?.let { AudioCoverInfo(bytes = it, mimeType = sniffImageMimeType(it)) }
    }

    override suspend fun readEmbeddedLyrics(path: String): String? = withContext(Dispatchers.IO) {
        scanner.extractEmbeddedLyrics(path)
    }

    private fun SongTagInfo.toAudioTagInfo(): AudioTagInfo =
        AudioTagInfo(
            title = title.takeIf { it.isNotBlank() },
            artist = artist.takeIf { it.isNotBlank() },
            album = album.takeIf { it.isNotBlank() },
            albumArtist = albumArtist.takeIf { it.isNotBlank() },
            composer = composer.takeIf { it.isNotBlank() },
            lyricist = lyricist.takeIf { it.isNotBlank() },
            genre = genre.takeIf { it.isNotBlank() },
            year = year.takeIf { it.isNotBlank() },
            trackNumber = track.substringBefore('/').toIntOrNull(),
            comment = comment.takeIf { it.isNotBlank() },
            copyright = copyright.takeIf { it.isNotBlank() },
            neteaseKey = neteaseKey.takeIf { it.isNotBlank() },
            rating = rating.takeIf { it > 0 }
        )

    private fun AudioTagInfo.hasLegacyTagData(): Boolean =
        listOf(title, artist, album, albumArtist, composer, lyricist, genre, year, comment, copyright, neteaseKey)
            .any { !it.isNullOrBlank() } || trackNumber != null || rating != null
}

private fun Map<String, List<String>>.bestLyrics(): String? =
    firstTagValue(
        "SYNCEDLYRICS",
        "UNSYNCEDLYRICS",
        "UNSYNCED LYRICS",
        "LYRICS",
        "USLT",
        "SYLT",
        "©lyr",
        "\u00a9lyr",
        "LYRIC"
    )

private fun Map<String, List<String>>.firstTagValue(vararg keys: String): String? {
    keys.forEach { requested ->
        val value = entries.firstOrNull { (key, _) -> key.equals(requested, ignoreCase = true) }
            ?.value
            ?.firstOrNull { it.isNotBlank() }
            ?.trim()
        if (!value.isNullOrBlank()) return value
    }
    return null
}

private fun sniffImageMimeType(bytes: ByteArray): String? = when {
    bytes.size >= 3 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xD8.toByte() && bytes[2] == 0xFF.toByte() -> "image/jpeg"
    bytes.size >= 8 &&
        bytes[0] == 0x89.toByte() &&
        bytes[1] == 0x50.toByte() &&
        bytes[2] == 0x4E.toByte() &&
        bytes[3] == 0x47.toByte() -> "image/png"
    bytes.size >= 12 &&
        bytes[0] == 0x52.toByte() &&
        bytes[1] == 0x49.toByte() &&
        bytes[2] == 0x46.toByte() &&
        bytes[3] == 0x46.toByte() &&
        bytes[8] == 0x57.toByte() &&
        bytes[9] == 0x45.toByte() &&
        bytes[10] == 0x42.toByte() &&
        bytes[11] == 0x50.toByte() -> "image/webp"
    else -> null
}
