package com.ella.music.data.scanner

import android.content.ContentUris
import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.MediaStore
import android.util.Log
import com.ella.music.data.model.Album
import com.ella.music.data.model.Song
import com.ella.music.data.parser.LrcParser
import com.kyant.taglib.TagLib
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jaudiotagger.audio.AudioFile
import org.jaudiotagger.audio.AudioFileIO
import org.jaudiotagger.tag.FieldKey
import java.io.File
import java.io.RandomAccessFile
import java.nio.charset.Charset
import java.nio.charset.StandardCharsets

class MusicScanner(private val context: Context) {

    companion object {
        private const val TAG = "MusicScanner"
    }

    suspend fun scanAllSongs(
        minDurationMs: Long = 0,
        includeFolders: List<String> = emptyList(),
        excludeFolders: List<String> = emptyList(),
        onProgress: ((Int) -> Unit)? = null
    ): List<Song> = withContext(Dispatchers.IO) {
        val songs = mutableListOf<Song>()
        val normalizedIncludeFolders = includeFolders.mapNotNull { it.normalizedFolderPath() }
        val normalizedExcludeFolders = excludeFolders.mapNotNull { it.normalizedFolderPath() }
        val collection = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
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
        val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0"
        val sortOrder = "${MediaStore.Audio.Media.TITLE} ASC"

        context.contentResolver.query(
            collection, projection, selection, null, sortOrder
        )?.use { cursor ->
            val idCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val titleCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
            val artistCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
            val albumCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
            val albumIdCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)
            val durationCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
            val dataCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)
            val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DISPLAY_NAME)
            val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.SIZE)
            val mimeCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.MIME_TYPE)
            val dateAddedCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_ADDED)
            val dateModifiedCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_MODIFIED)
            val trackCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TRACK)

            while (cursor.moveToNext()) {
                val id = cursor.getLong(idCol)
                var title = cursor.getString(titleCol) ?: ""
                var artist = cursor.getString(artistCol) ?: ""
                var album = cursor.getString(albumCol) ?: ""
                val albumId = cursor.getLong(albumIdCol)
                var duration = cursor.getLong(durationCol)
                val path = cursor.getString(dataCol) ?: ""
                val fileName = cursor.getString(nameCol) ?: ""
                val size = cursor.getLong(sizeCol)
                val mime = cursor.getString(mimeCol) ?: ""
                val dateAdded = cursor.getLong(dateAddedCol) * 1000L
                val dateModified = cursor.getLong(dateModifiedCol) * 1000L
                val trackNumber = cursor.getInt(trackCol).normalizedTrackNumber()

                if (path.isEmpty()) continue
                if (!path.isAllowedByFolderFilters(normalizedIncludeFolders, normalizedExcludeFolders)) continue
                val file = File(path)
                if (!file.exists()) continue

                val audioFile = readAudioFile(file)
                val tag = audioFile?.safeTag(file)

                if (tag != null) {
                    if (isMissingTag(title, file.name)) title = tag.safeFirst(file, FieldKey.TITLE)
                    if (isMissingTag(artist)) artist = tag.safeFirst(file, FieldKey.ARTIST)
                    if (isMissingTag(album)) album = tag.safeFirst(file, FieldKey.ALBUM)
                    if (duration <= 0) duration = (audioFile.audioHeader?.trackLength ?: 0) * 1000L
                }

                if (shouldReadTagsWithTagLib(file, title, artist, album, duration)) {
                    try {
                        ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { fd ->
                            val audioProps = TagLib.getAudioProperties(fd.dup().detachFd())
                            val metadata = TagLib.getMetadata(fd.dup().detachFd(), false)
                            val props = metadata?.propertyMap

                            if (isMissingTag(title, file.name)) {
                                title = props.firstValue("TITLE", "INAM", "NAME", "TIT2")
                            }
                            if (isMissingTag(artist)) {
                                artist = props.firstValue("ARTIST", "ALBUMARTIST", "ALBUM ARTIST", "IART", "PERFORMER", "TPE1")
                            }
                            if (isMissingTag(album)) {
                                album = props.firstValue("ALBUM", "IPRD", "PRODUCT", "WM/ALBUMTITLE", "TALB")
                            }
                            if (duration <= 0) {
                                duration = ((audioProps?.length ?: 0) * 1000L)
                            }
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "TagLib metadata extraction failed for $path", e)
                    }
                }

                file.readWavInfoTags()?.let { wavInfo ->
                    if (isMissingTag(title, file.name)) title = wavInfo.title.orEmpty()
                    if (isMissingTag(artist)) artist = wavInfo.artist.orEmpty()
                    if (isMissingTag(album)) album = wavInfo.album.orEmpty()
                }

                if (isMissingTag(title, file.name) || isMissingTag(artist) || isMissingTag(album) || duration <= 0) {
                    try {
                        val retriever = MediaMetadataRetriever()
                        retriever.setDataSource(path)
                        if (isMissingTag(title, file.name)) title = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE) ?: ""
                        if (isMissingTag(artist)) artist = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST) ?: ""
                        if (isMissingTag(album)) album = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM) ?: ""
                        if (duration <= 0) duration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
                        retriever.release()
                    } catch (e: Exception) {
                        Log.w(TAG, "Metadata extraction failed for $path", e)
                    }
                }

                if (isMissingTag(title, file.name)) title = fileName.substringBeforeLast('.')
                if (isMissingTag(artist)) artist = "Unknown"
                if (isMissingTag(album)) album = "Unknown"

                if (duration > 0 && duration >= minDurationMs) {
                    songs.add(Song(id, title, artist, album, albumId, duration, path, fileName, size, mime, dateAdded, dateModified, trackNumber))
                    onProgress?.invoke(songs.size)
                }
            }
        }
        songs
    }

    suspend fun scanAlbums(): List<Album> = withContext(Dispatchers.IO) {
        val albums = mutableListOf<Album>()
        val collection = MediaStore.Audio.Albums.EXTERNAL_CONTENT_URI
        val projection = arrayOf(
            MediaStore.Audio.Albums._ID,
            MediaStore.Audio.Albums.ALBUM,
            MediaStore.Audio.Albums.ARTIST,
            MediaStore.Audio.Albums.NUMBER_OF_SONGS,
            MediaStore.Audio.Albums.FIRST_YEAR
        )
        context.contentResolver.query(collection, projection, null, null, "${MediaStore.Audio.Albums.ALBUM} ASC")?.use { cursor ->
            while (cursor.moveToNext()) {
                albums.add(Album(
                    cursor.getLong(0),
                    cursor.getString(1) ?: "Unknown",
                    cursor.getString(2) ?: "Unknown",
                    cursor.getInt(3),
                    cursor.getInt(4)
                ))
            }
        }
        albums
    }

    fun extractEmbeddedLyrics(path: String): String? {
        val file = File(path)
        if (!file.exists()) return null

        val audioFileLyrics = readAudioFile(file)
            ?.safeTag(file)
            ?.safeFirst(file, FieldKey.LYRICS)
            ?.takeIf { it.isUsableSynchronizedLyrics() }
        if (!audioFileLyrics.isNullOrBlank()) {
            Log.d(TAG, "Found jaudiotagger lyrics (${audioFileLyrics.length} chars) for ${file.name}")
            return audioFileLyrics
        }

        runCatching {
            val tagLibLyrics = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { fd ->
                selectBestLyrics(
                    collectMetadataValues(
                        fd,
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
                )
            }
            if (!tagLibLyrics.isNullOrBlank()) {
                Log.d(TAG, "Found TagLib lyrics (${tagLibLyrics.length} chars) for ${file.name}")
                return tagLibLyrics
            }
        }.onFailure {
            Log.w(TAG, "TagLib lyrics extraction failed for $path", it)
        }

        return runCatching {
            MediaMetadataRetriever().useCompat { retriever ->
                retriever.setDataSource(path)
                val lyrics = retriever.extractMetadata(1000)
                if (!lyrics.isNullOrBlank()) {
                    Log.d(TAG, "Found retriever lyrics (${lyrics.length} chars) for ${file.name}")
                    lyrics
                } else null
            }
        }.onFailure {
            Log.w(TAG, "Retriever lyrics extraction failed for $path", it)
        }.getOrNull()
    }

    fun extractCoverArt(path: String): ByteArray? {
        val file = File(path)
        if (!file.exists()) return null

        val audioFileArt = readAudioFile(file)
            ?.safeTag(file)
            ?.safeFirstArtworkData(file)
        if (audioFileArt != null) return audioFileArt

        runCatching {
            val tagLibArt = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { fd ->
                val pictures = TagLib.getPictures(fd.dup().detachFd())
                val frontCover = pictures.firstOrNull { it.pictureType == "Front Cover" } ?: pictures.firstOrNull()
                frontCover?.data
            }
            if (tagLibArt != null) return tagLibArt
        }.onFailure {
            Log.w(TAG, "TagLib cover art extraction failed for $path", it)
        }

        return runCatching {
            MediaMetadataRetriever().useCompat { retriever ->
                retriever.setDataSource(path)
                retriever.embeddedPicture
            }
        }.onFailure {
            Log.w(TAG, "Retriever cover art extraction failed for $path", it)
        }.getOrNull()
    }

    fun extractReplayGain(path: String): Float? {
        return try {
            val file = File(path)
            if (!file.exists()) return null
            readAudioFile(file)
                ?.safeTag(file)
                ?.let { tag ->
                    firstNonBlank(
                        tag.safeFirst(file, "REPLAYGAIN_TRACK_GAIN"),
                        tag.safeFirst(file, "R128_TRACK_GAIN")
                    )
                }
                ?.parseReplayGain()
                ?.let { return it }

            val gainStr = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { fd ->
                firstMetadataValue(fd, "REPLAYGAIN_TRACK_GAIN", "R128_TRACK_GAIN")
            }
            gainStr?.parseReplayGain()
        } catch (e: Exception) {
            Log.w(TAG, "ReplayGain extraction failed for $path", e)
            null
        }
    }

    fun getAlbumArtUri(albumId: Long): Uri =
        ContentUris.withAppendedId(Uri.parse("content://media/external/audio/albumart"), albumId)

    private fun isMissingTag(value: String?, fileName: String? = null): Boolean {
        val normalized = value?.trim().orEmpty()
        if (normalized.isBlank()) return true
        if (normalized.equals("<unknown>", ignoreCase = true)) return true
        if (normalized.equals("unknown", ignoreCase = true)) return true
        if (normalized.equals("unknown artist", ignoreCase = true)) return true
        if (normalized.equals("unknown album", ignoreCase = true)) return true
        return fileName != null && normalized == fileName.substringBeforeLast('.')
    }

    private fun shouldReadTagsWithTagLib(
        file: File,
        title: String,
        artist: String,
        album: String,
        duration: Long
    ): Boolean {
        val extension = file.extension.lowercase()
        val preferTagLib = extension in setOf("wav", "wave", "aif", "aiff", "flac", "ogg", "opus", "m4a", "mp4")
        return preferTagLib ||
            isMissingTag(title, file.name) ||
            isMissingTag(artist) ||
            isMissingTag(album) ||
            duration <= 0
    }

    private fun readAudioFile(file: File): AudioFile? {
        return try {
            AudioFileIO.read(file)
        } catch (e: Exception) {
            Log.d(TAG, "jaudiotagger read failed for ${file.path}", e)
            null
        }
    }

    private fun AudioFile.safeTag(file: File) = runCatching {
        tagOrCreateDefault
    }.onFailure {
        Log.d(TAG, "jaudiotagger tag read failed for ${file.path}", it)
    }.getOrNull()

    private fun org.jaudiotagger.tag.Tag.safeFirst(file: File, key: FieldKey): String {
        return runCatching {
            getFirst(key).orEmpty()
        }.onFailure {
            Log.d(TAG, "jaudiotagger field $key unavailable for ${file.path}", it)
        }.getOrDefault("")
    }

    private fun org.jaudiotagger.tag.Tag.safeFirst(file: File, key: String): String {
        return runCatching {
            getFirst(key).orEmpty()
        }.onFailure {
            Log.d(TAG, "jaudiotagger field $key unavailable for ${file.path}", it)
        }.getOrDefault("")
    }

    private fun org.jaudiotagger.tag.Tag.safeFirstArtworkData(file: File): ByteArray? {
        return runCatching {
            firstArtwork?.binaryData
        }.onFailure {
            Log.d(TAG, "jaudiotagger artwork unavailable for ${file.path}", it)
        }.getOrNull()
    }

    private fun firstNonBlank(vararg values: String?): String? =
        values.firstOrNull { !it.isNullOrBlank() }

    private fun String.parseReplayGain(): Float? {
        return Regex("([+-]?[0-9]+(?:\\.[0-9]+)?)")
            .find(this)
            ?.groupValues
            ?.getOrNull(1)
            ?.toFloatOrNull()
    }

    private fun String.isUsableSynchronizedLyrics(): Boolean {
        if (isBlank()) return false
        return LrcParser.parse(this).lyrics.any { !it.text.isMusicSymbolOnly() }
    }

    private fun Map<String, Array<String>>?.firstValue(vararg keys: String): String {
        if (this == null) return ""

        for (key in keys) {
            val value = this[key]?.firstOrNull()?.trim()
            if (!value.isNullOrBlank()) return value
        }

        val normalizedKeys = keys.map { it.normalizedPropertyKey() }.toSet()
        for ((propertyKey, values) in this) {
            if (propertyKey.normalizedPropertyKey() in normalizedKeys) {
                val value = values.firstOrNull()?.trim()
                if (!value.isNullOrBlank()) return value
            }
        }
        return ""
    }

    private data class WavInfoTags(
        val title: String? = null,
        val artist: String? = null,
        val album: String? = null
    )

    private fun File.readWavInfoTags(): WavInfoTags? {
        val extension = extension.lowercase()
        if (extension !in setOf("wav", "wave")) return null

        return runCatching {
            RandomAccessFile(this, "r").use { input ->
                if (input.length() < 12L) return@use null
                val riff = input.readFourCc()
                input.readUnsignedIntLe()
                val wave = input.readFourCc()
                if (riff !in setOf("RIFF", "RF64") || wave != "WAVE") return@use null

                val values = linkedMapOf<String, String>()
                while (input.filePointer + 8L <= input.length()) {
                    val chunkId = input.readFourCc()
                    val chunkSize = input.readUnsignedIntLe()
                    val chunkStart = input.filePointer
                    val chunkEnd = (chunkStart + chunkSize).coerceAtMost(input.length())

                    if (chunkId == "LIST" && chunkSize >= 4L) {
                        val listType = input.readFourCc()
                        if (listType == "INFO") {
                            while (input.filePointer + 8L <= chunkEnd) {
                                val key = input.readFourCc()
                                val valueSize = input.readUnsignedIntLe()
                                val valueEnd = (input.filePointer + valueSize).coerceAtMost(chunkEnd)
                                val valueLength = (valueEnd - input.filePointer).toInt().coerceAtLeast(0)
                                val bytes = ByteArray(valueLength)
                                input.readFully(bytes)
                                bytes.decodeInfoText().takeIf { it.isNotBlank() }?.let { values[key] = it }
                                val paddedEnd = valueEnd + (valueSize and 1L)
                                input.seek(paddedEnd.coerceAtMost(chunkEnd))
                            }
                            return@use WavInfoTags(
                                title = values.firstInfoValue("INAM", "TITL", "TITLE", "NAME"),
                                artist = values.firstInfoValue("IART", "ARTIST", "ALBUMARTIST", "ALBUM ARTIST", "PERFORMER"),
                                album = values.firstInfoValue("IPRD", "IALB", "ALBUM", "PRODUCT")
                            )
                        }
                    }

                    input.seek((chunkEnd + (chunkSize and 1L)).coerceAtMost(input.length()))
                }
                null
            }
        }.onFailure {
            Log.d(TAG, "WAV INFO metadata extraction failed for ${path}", it)
        }.getOrNull()
    }

    private fun RandomAccessFile.readFourCc(): String {
        val bytes = ByteArray(4)
        readFully(bytes)
        return String(bytes, StandardCharsets.US_ASCII)
    }

    private fun RandomAccessFile.readUnsignedIntLe(): Long {
        val b0 = read()
        val b1 = read()
        val b2 = read()
        val b3 = read()
        if (b0 < 0 || b1 < 0 || b2 < 0 || b3 < 0) return 0L
        return (b0.toLong() and 0xFF) or
            ((b1.toLong() and 0xFF) shl 8) or
            ((b2.toLong() and 0xFF) shl 16) or
            ((b3.toLong() and 0xFF) shl 24)
    }

    private fun ByteArray.decodeInfoText(): String {
        val trimmed = dropLastWhile { it == 0.toByte() || it == 0x20.toByte() }.toByteArray()
        if (trimmed.isEmpty()) return ""
        val text = when {
            trimmed.size >= 2 && trimmed[0] == 0xFF.toByte() && trimmed[1] == 0xFE.toByte() ->
                String(trimmed, StandardCharsets.UTF_16LE)
            trimmed.size >= 2 && trimmed[0] == 0xFE.toByte() && trimmed[1] == 0xFF.toByte() ->
                String(trimmed, StandardCharsets.UTF_16BE)
            trimmed.size >= 4 && trimmed.count { it == 0.toByte() } > trimmed.size / 4 ->
                String(trimmed, StandardCharsets.UTF_16LE)
            else -> {
                val utf8 = String(trimmed, StandardCharsets.UTF_8)
                if ('\uFFFD' in utf8) String(trimmed, Charset.forName("GB18030")) else utf8
            }
        }
        return text.trim('\uFEFF', '\u0000', ' ', '\t', '\r', '\n')
    }

    private fun Map<String, String>.firstInfoValue(vararg keys: String): String? {
        for (key in keys) {
            get(key)?.takeIf { it.isNotBlank() }?.let { return it }
        }
        val normalizedKeys = keys.map { it.normalizedPropertyKey() }.toSet()
        for ((key, value) in this) {
            if (key.normalizedPropertyKey() in normalizedKeys && value.isNotBlank()) return value
        }
        return null
    }

    private fun firstMetadataValue(fd: ParcelFileDescriptor, vararg keys: String): String? {
        for (key in keys) {
            val value = TagLib.getMetadataPropertyValues(fd.dup().detachFd(), key)
                ?.firstOrNull()
                ?.trim()
            if (!value.isNullOrBlank()) return value
        }

        val props = TagLib.getMetadata(fd.dup().detachFd(), false)?.propertyMap
        return props.firstValue(*keys).ifBlank { null }
    }

    private fun collectMetadataValues(fd: ParcelFileDescriptor, vararg keys: String): List<String> {
        val values = linkedSetOf<String>()
        for (key in keys) {
            TagLib.getMetadataPropertyValues(fd.dup().detachFd(), key)
                ?.map { it.trim() }
                ?.filter { it.isNotBlank() }
                ?.forEach { values.add(it) }
        }

        val props = TagLib.getMetadata(fd.dup().detachFd(), false)?.propertyMap
        for (key in keys) {
            props?.get(key)
                ?.map { it.trim() }
                ?.filter { it.isNotBlank() }
                ?.forEach { values.add(it) }
        }

        val normalizedKeys = keys.map { it.normalizedPropertyKey() }.toSet()
        props?.forEach { (propertyKey, propertyValues) ->
            if (propertyKey.normalizedPropertyKey() in normalizedKeys) {
                propertyValues
                    .map { it.trim() }
                    .filter { it.isNotBlank() }
                    .forEach { values.add(it) }
            }
        }
        return values.toList()
    }

    private fun selectBestLyrics(candidates: List<String>): String? {
        return candidates
            .map { it to scoreLyrics(it) }
            .filter { (_, score) -> score > 0 }
            .maxByOrNull { (_, score) -> score }
            ?.first
    }

    private fun scoreLyrics(candidate: String): Int {
        val parsed = LrcParser.parse(candidate).lyrics
        val usefulLines = parsed.count { !it.text.isMusicSymbolOnly() }
        if (usefulLines == 0) return 0

        val timedScore = usefulLines * 10
        val translationBonus = parsed.count { !it.translation.isNullOrBlank() }
        val plainTextPenalty = if (parsed.isEmpty() && candidate.lines().size > 1) -5 else 0
        return timedScore + translationBonus + plainTextPenalty
    }

    private fun String.isMusicSymbolOnly(): Boolean {
        val content = trim()
        if (content.isBlank()) return true
        return content.all { char ->
            char.isWhitespace() ||
                char in setOf('♪', '♫', '♬', '♩', '♭', '♯', '♮') ||
                Character.UnicodeBlock.of(char) == Character.UnicodeBlock.MUSICAL_SYMBOLS
        }
    }

    private fun String.normalizedPropertyKey(): String =
        lowercase().replace(" ", "").replace("_", "")

    private fun Int.normalizedTrackNumber(): Int =
        if (this > 1000) this % 1000 else this

    private fun String.normalizedFolderPath(): String? {
        val normalized = trim().replace('\\', '/').trimEnd('/')
        return normalized.takeIf { it.isNotBlank() }?.lowercase()
    }

    private fun String.isAllowedByFolderFilters(
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

    private inline fun <T> MediaMetadataRetriever.useCompat(block: (MediaMetadataRetriever) -> T): T {
        return try {
            block(this)
        } finally {
            release()
        }
    }
}
