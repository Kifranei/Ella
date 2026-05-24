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
import com.ella.music.data.model.SongTagInfo
import com.ella.music.data.looksLikeNeteaseKeyValue
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

data class MediaStoreAudioItem(
    val id: Long,
    val title: String,
    val artist: String,
    val album: String,
    val albumId: Long,
    val duration: Long,
    val path: String,
    val fileName: String,
    val fileSize: Long,
    val mimeType: String,
    val dateAdded: Long,
    val dateModified: Long,
    val trackNumber: Int,
    val discNumber: Int
)

class MusicScanner(private val context: Context) {

    companion object {
        private const val TAG = "MusicScanner"
    }

    suspend fun enumerateAudioFiles(
        includeFolders: List<String> = emptyList(),
        excludeFolders: List<String> = emptyList()
    ): List<MediaStoreAudioItem> = withContext(Dispatchers.IO) {
        val items = mutableListOf<MediaStoreAudioItem>()
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
        val sortOrder = "${MediaStore.Audio.Media.TITLE} ASC"

        context.contentResolver.query(
            collection, projection, null, null, sortOrder
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
                val path = cursor.getString(dataCol).orEmpty()
                if (path.isEmpty()) continue
                if (!path.isAllowedByFolderFilters(normalizedIncludeFolders, normalizedExcludeFolders)) continue
                val file = File(path)
                if (!file.exists()) continue

                val rawTrackNumber = cursor.getInt(trackCol)
                items += MediaStoreAudioItem(
                    id = cursor.getLong(idCol),
                    title = cursor.getString(titleCol).orEmpty(),
                    artist = cursor.getString(artistCol).orEmpty(),
                    album = cursor.getString(albumCol).orEmpty(),
                    albumId = cursor.getLong(albumIdCol),
                    duration = cursor.getLong(durationCol),
                    path = path,
                    fileName = cursor.getString(nameCol).orEmpty(),
                    fileSize = cursor.getLong(sizeCol),
                    mimeType = cursor.getString(mimeCol).orEmpty(),
                    dateAdded = cursor.getLong(dateAddedCol) * 1000L,
                    dateModified = cursor.getLong(dateModifiedCol) * 1000L,
                    trackNumber = rawTrackNumber.normalizedTrackNumber(),
                    discNumber = rawTrackNumber.normalizedDiscNumber()
                )
            }
        }
        items
    }

    suspend fun scanAudioItem(
        item: MediaStoreAudioItem,
        minDurationMs: Long = 0,
        deepMetadata: Boolean = false
    ): Song? = withContext(Dispatchers.IO) {
        var title = item.title
        var artist = item.artist
        var album = item.album
        var albumArtist = ""
        var genre = ""
        var year = ""
        var composer = ""
        var lyricist = ""
        var duration = item.duration
        var discNumber = item.discNumber
        val file = File(item.path)
        if (!file.exists()) return@withContext null

        val shouldDeepRead = deepMetadata ||
            isMissingTag(title, file.name) ||
            isMissingTag(artist) ||
            isMissingTag(album) ||
            duration <= 0

        val audioFile = if (shouldDeepRead) readAudioFile(file) else null
        val tag = audioFile?.safeTag(file)

        if (tag != null) {
            if (isMissingTag(title, file.name)) title = tag.safeFirst(file, FieldKey.TITLE)
            if (isMissingTag(artist)) artist = tag.safeFirst(file, FieldKey.ARTIST)
            if (isMissingTag(album)) album = tag.safeFirst(file, FieldKey.ALBUM)
            albumArtist = tag.safeFirst(file, FieldKey.ALBUM_ARTIST)
            genre = tag.safeFirst(file, FieldKey.GENRE)
            year = tag.safeFirst(file, FieldKey.YEAR).normalizeYear()
            composer = tag.safeFirst(file, FieldKey.COMPOSER)
            lyricist = firstNonBlank(
                tag.safeFirst(file, "LYRICIST"),
                tag.safeFirst(file, "TEXT"),
                tag.safeFirst(file, "WRITER")
            ).orEmpty()
            discNumber = discNumber.takeIf { it > 0 } ?: firstNonBlank(
                tag.safeFirst(file, "DISCNUMBER"),
                tag.safeFirst(file, "DISC"),
                tag.safeFirst(file, "TPOS")
            ).orEmpty().normalizedDiscNumberFromTag()
            if (duration <= 0) duration = (audioFile.audioHeader?.trackLength ?: 0) * 1000L
        }

        if (shouldReadTagsWithTagLib(file, title, artist, album, duration, deepMetadata)) {
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
                    if (albumArtist.isBlank()) {
                        albumArtist = props.firstValue("ALBUMARTIST", "ALBUM ARTIST", "ALBUM_ARTIST", "WM/ALBUMARTIST", "TPE2")
                    }
                    if (genre.isBlank()) {
                        genre = props.firstValue("GENRE", "TCON")
                    }
                    if (year.isBlank()) {
                        year = props.firstValue("DATE", "YEAR", "TYER", "TDRC").normalizeYear()
                    }
                    if (composer.isBlank()) {
                        composer = props.firstValue("COMPOSER", "TCOM", "WM/COMPOSER")
                    }
                    if (lyricist.isBlank()) {
                        lyricist = props.firstValue("LYRICIST", "TEXT", "WRITER", "AUTHOR", "WM/WRITER")
                    }
                    if (discNumber <= 0) {
                        discNumber = props.firstValue("DISCNUMBER", "DISC", "TPOS").normalizedDiscNumberFromTag()
                    }
                    if (duration <= 0) {
                        duration = ((audioProps?.length ?: 0) * 1000L)
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "TagLib metadata extraction failed for ${item.path}", e)
            }
        }

        if (shouldDeepRead || deepMetadata) file.readWavInfoTags()?.let { wavInfo ->
            if (isMissingTag(title, file.name)) title = wavInfo.title.orEmpty()
            if (isMissingTag(artist)) artist = wavInfo.artist.orEmpty()
            if (isMissingTag(album)) album = wavInfo.album.orEmpty()
            if (albumArtist.isBlank()) albumArtist = wavInfo.albumArtist.orEmpty()
            if (genre.isBlank()) genre = wavInfo.genre.orEmpty()
            if (year.isBlank()) year = wavInfo.year.orEmpty().normalizeYear()
            if (composer.isBlank()) composer = wavInfo.composer.orEmpty()
            if (lyricist.isBlank()) lyricist = wavInfo.lyricist.orEmpty()
        }

        if (shouldDeepRead && (isMissingTag(title, file.name) || isMissingTag(artist) || isMissingTag(album) || duration <= 0)) {
            try {
                val retriever = MediaMetadataRetriever()
                retriever.setDataSource(item.path)
                if (isMissingTag(title, file.name)) title = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE) ?: ""
                if (isMissingTag(artist)) artist = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST) ?: ""
                if (isMissingTag(album)) album = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM) ?: ""
                if (duration <= 0) duration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
                retriever.release()
            } catch (e: Exception) {
                Log.w(TAG, "Metadata extraction failed for ${item.path}", e)
            }
        }

        if (isMissingTag(title, file.name)) title = item.fileName.substringBeforeLast('.')
        if (isMissingTag(artist)) artist = "Unknown"
        if (isMissingTag(album)) album = "Unknown"

        if (duration <= 0 || duration < minDurationMs) return@withContext null

        Song(
            id = item.id,
            title = title,
            artist = artist,
            album = album,
            albumId = item.albumId,
            duration = duration,
            path = item.path,
            fileName = item.fileName,
            fileSize = item.fileSize,
            mimeType = item.mimeType,
            dateAdded = item.dateAdded,
            dateModified = item.dateModified,
            trackNumber = item.trackNumber,
            discNumber = discNumber,
            albumArtist = albumArtist,
            genre = genre,
            year = year,
            composer = composer,
            lyricist = lyricist
        )
    }

    suspend fun scanAllSongs(
        minDurationMs: Long = 0,
        includeFolders: List<String> = emptyList(),
        excludeFolders: List<String> = emptyList(),
        deepMetadata: Boolean = false,
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
        val selection: String? = null
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
                var albumArtist = ""
                var genre = ""
                var year = ""
                var composer = ""
                var lyricist = ""
                val albumId = cursor.getLong(albumIdCol)
                var duration = cursor.getLong(durationCol)
                val path = cursor.getString(dataCol) ?: ""
                val fileName = cursor.getString(nameCol) ?: ""
                val size = cursor.getLong(sizeCol)
                val mime = cursor.getString(mimeCol) ?: ""
                val dateAdded = cursor.getLong(dateAddedCol) * 1000L
                val dateModified = cursor.getLong(dateModifiedCol) * 1000L
                val rawTrackNumber = cursor.getInt(trackCol)
                val trackNumber = rawTrackNumber.normalizedTrackNumber()
                var discNumber = rawTrackNumber.normalizedDiscNumber()

                if (path.isEmpty()) continue
                if (!path.isAllowedByFolderFilters(normalizedIncludeFolders, normalizedExcludeFolders)) continue
                val file = File(path)
                if (!file.exists()) continue

                val shouldDeepRead = deepMetadata ||
                    isMissingTag(title, file.name) ||
                    isMissingTag(artist) ||
                    isMissingTag(album) ||
                    duration <= 0

                val audioFile = if (shouldDeepRead) readAudioFile(file) else null
                val tag = audioFile?.safeTag(file)

                if (tag != null) {
                    if (isMissingTag(title, file.name)) title = tag.safeFirst(file, FieldKey.TITLE)
                    if (isMissingTag(artist)) artist = tag.safeFirst(file, FieldKey.ARTIST)
                    if (isMissingTag(album)) album = tag.safeFirst(file, FieldKey.ALBUM)
                    albumArtist = tag.safeFirst(file, FieldKey.ALBUM_ARTIST)
                    genre = tag.safeFirst(file, FieldKey.GENRE)
                    year = tag.safeFirst(file, FieldKey.YEAR).normalizeYear()
                    composer = tag.safeFirst(file, FieldKey.COMPOSER)
                    lyricist = firstNonBlank(
                        tag.safeFirst(file, "LYRICIST"),
                        tag.safeFirst(file, "TEXT"),
                        tag.safeFirst(file, "WRITER")
                    ).orEmpty()
                    discNumber = discNumber.takeIf { it > 0 } ?: firstNonBlank(
                        tag.safeFirst(file, "DISCNUMBER"),
                        tag.safeFirst(file, "DISC"),
                        tag.safeFirst(file, "TPOS")
                    ).orEmpty().normalizedDiscNumberFromTag()
                    if (duration <= 0) duration = (audioFile.audioHeader?.trackLength ?: 0) * 1000L
                }

                if (shouldReadTagsWithTagLib(file, title, artist, album, duration, deepMetadata)) {
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
                            if (albumArtist.isBlank()) {
                                albumArtist = props.firstValue("ALBUMARTIST", "ALBUM ARTIST", "ALBUM_ARTIST", "WM/ALBUMARTIST", "TPE2")
                            }
                            if (genre.isBlank()) {
                                genre = props.firstValue("GENRE", "TCON")
                            }
                            if (year.isBlank()) {
                                year = props.firstValue("DATE", "YEAR", "TYER", "TDRC").normalizeYear()
                            }
                            if (composer.isBlank()) {
                                composer = props.firstValue("COMPOSER", "TCOM", "WM/COMPOSER")
                            }
                            if (lyricist.isBlank()) {
                                lyricist = props.firstValue("LYRICIST", "TEXT", "WRITER", "AUTHOR", "WM/WRITER")
                            }
                            if (discNumber <= 0) {
                                discNumber = props.firstValue("DISCNUMBER", "DISC", "TPOS").normalizedDiscNumberFromTag()
                            }
                            if (duration <= 0) {
                                duration = ((audioProps?.length ?: 0) * 1000L)
                            }
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "TagLib metadata extraction failed for $path", e)
                    }
                }

                if (shouldDeepRead || deepMetadata) file.readWavInfoTags()?.let { wavInfo ->
                    if (isMissingTag(title, file.name)) title = wavInfo.title.orEmpty()
                    if (isMissingTag(artist)) artist = wavInfo.artist.orEmpty()
                    if (isMissingTag(album)) album = wavInfo.album.orEmpty()
                    if (albumArtist.isBlank()) albumArtist = wavInfo.albumArtist.orEmpty()
                    if (genre.isBlank()) genre = wavInfo.genre.orEmpty()
                    if (year.isBlank()) year = wavInfo.year.orEmpty().normalizeYear()
                    if (composer.isBlank()) composer = wavInfo.composer.orEmpty()
                    if (lyricist.isBlank()) lyricist = wavInfo.lyricist.orEmpty()
                }

                if (shouldDeepRead && (isMissingTag(title, file.name) || isMissingTag(artist) || isMissingTag(album) || duration <= 0)) {
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
                    songs.add(
                        Song(
                            id = id,
                            title = title,
                            artist = artist,
                            album = album,
                            albumId = albumId,
                            duration = duration,
                            path = path,
                            fileName = fileName,
                            fileSize = size,
                            mimeType = mime,
                            dateAdded = dateAdded,
                            dateModified = dateModified,
                            trackNumber = trackNumber,
                            discNumber = discNumber,
                            albumArtist = albumArtist,
                            genre = genre,
                            year = year,
                            composer = composer,
                            lyricist = lyricist
                        )
                    )
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

    fun extractSongTagInfo(path: String): SongTagInfo {
        val file = File(path)
        if (!file.exists() || !file.isFile) return SongTagInfo()

        val jaudioValues = runCatching {
            readAudioFile(file)?.safeTag(file)?.let { tag ->
                mapOf(
                    "title" to tag.safeFirst(file, FieldKey.TITLE),
                    "artist" to tag.safeFirst(file, FieldKey.ARTIST),
                    "album" to tag.safeFirst(file, FieldKey.ALBUM),
                    "albumArtist" to tag.safeFirst(file, FieldKey.ALBUM_ARTIST),
                    "genre" to tag.safeFirst(file, FieldKey.GENRE),
                    "year" to tag.safeFirst(file, FieldKey.YEAR),
                    "composer" to tag.safeFirst(file, FieldKey.COMPOSER),
                    "lyricist" to firstNonBlank(
                        tag.safeFirst(file, "LYRICIST"),
                        tag.safeFirst(file, "TEXT"),
                        tag.safeFirst(file, "WRITER")
                    ).orEmpty(),
                    "track" to tag.safeFirst(file, FieldKey.TRACK),
                    "comment" to tag.safeFirst(file, FieldKey.COMMENT),
                    "neteaseKey" to firstNonBlank(
                        tag.safeFirst(file, "163KEY"),
                        tag.safeFirst(file, "163 KEY"),
                        tag.safeFirst(file, "NETEASEKEY"),
                        tag.safeFirst(file, "NETEASE_KEY"),
                        tag.safeFirst(file, "NETEASE_CLOUD_MUSIC_KEY"),
                        tag.safeFirst(file, "CLOUDMUSICKEY"),
                        tag.safeFirst(file, "CLOUDMUSIC_KEY"),
                        tag.safeFirst(file, "MUSIC163KEY")
                    ).orEmpty(),
                    "copyright" to firstNonBlank(
                        tag.safeFirst(file, "COPYRIGHT"),
                        tag.safeFirst(file, "COPYRIGHTMESSAGE"),
                        tag.safeFirst(file, "TCOP"),
                        tag.safeFirst(file, "\u00a9cpy")
                    ).orEmpty(),
                    "rating" to listOf(
                        tag.safeFirst(file, "RATING"),
                        tag.safeFirst(file, "RATE"),
                        tag.safeFirst(file, "POPM"),
                        tag.safeFirst(file, "POPULARIMETER"),
                        tag.safeFirst(file, "WM/RATING"),
                        tag.safeFirst(file, "WM/POPULARITY")
                    )
                        .filter { it.isNotBlank() }
                        .joinToString(";")
                )
            }.orEmpty()
        }.getOrElse {
            Log.d(TAG, "jaudiotagger details unavailable for ${file.path}", it)
            emptyMap()
        }

        val tagLibValues: Map<String, List<String>> = runCatching {
            ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { fd ->
                TagLib.getMetadata(fd.dup().detachFd(), false)
                    ?.propertyMap
                    .orEmpty()
                    .mapValues { (_, values) -> values.toList() }
            }
        }.getOrElse {
            Log.d(TAG, "TagLib details unavailable for ${file.path}", it)
            emptyMap()
        }

        return SongTagInfo(
            title = jaudioValues["title"].orEmpty().ifBlank { tagLibValues.firstTagValue("TITLE") },
            artist = jaudioValues["artist"].orEmpty().ifBlank { tagLibValues.firstTagValue("ARTIST") },
            album = jaudioValues["album"].orEmpty().ifBlank { tagLibValues.firstTagValue("ALBUM") },
            albumArtist = jaudioValues["albumArtist"].orEmpty().ifBlank {
                tagLibValues.firstTagValue("ALBUMARTIST", "ALBUM ARTIST", "ALBUM_ARTIST")
            },
            genre = jaudioValues["genre"].orEmpty().ifBlank { tagLibValues.firstTagValue("GENRE") },
            year = jaudioValues["year"].orEmpty().ifBlank { tagLibValues.firstTagValue("DATE", "YEAR") },
            composer = jaudioValues["composer"].orEmpty().ifBlank {
                tagLibValues.firstTagValue("COMPOSER", "TCOM", "WM/COMPOSER")
            },
            lyricist = jaudioValues["lyricist"].orEmpty().ifBlank {
                tagLibValues.firstTagValue("LYRICIST", "TEXT", "WRITER", "AUTHOR", "WM/WRITER")
            },
            track = jaudioValues["track"].orEmpty().ifBlank { tagLibValues.firstTagValue("TRACKNUMBER", "TRACK") },
            comment = jaudioValues["comment"].orEmpty().ifBlank {
                tagLibValues.firstTagValue("COMMENT", "DESCRIPTION", "SUBTITLE")
            }.cleanTagText(),
            copyright = jaudioValues["copyright"].orEmpty().ifBlank {
                tagLibValues.firstTagValue(
                    "COPYRIGHT",
                    "COPYRIGHTMESSAGE",
                    "COPYRIGHT MESSAGE",
                    "TCOP",
                    "\u00a9cpy",
                    "©cpy",
                    "WCOP"
                )
            }.cleanTagText(),
            neteaseKey = jaudioValues["neteaseKey"].orEmpty()
                .takeIf { it.looksLikeNeteaseKeyValue() }
                .orEmpty()
                .ifBlank { jaudioValues["comment"].orEmpty().extractPrefixedNeteaseCommentKey() }
                .ifBlank { tagLibValues.findNeteaseKey() }
                .ifBlank { tagLibValues.findPrefixedNeteaseCommentKey() }
                .cleanTagText(),
            rating = ratingStarsFromTagValues(
                jaudioValues["rating"],
                tagLibValues.allTagValues(
                    "RATING",
                    "RATE",
                    "POPM",
                    "POPULARIMETER",
                    "POPULARITY",
                    "WM/RATING",
                    "WM/POPULARITY",
                    "RATING WMP",
                    "FMPS_RATING"
                )
            )
        )
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
        if (normalized.looksLikeMojibake()) return true
        return fileName != null && normalized == fileName.substringBeforeLast('.')
    }

    private fun shouldReadTagsWithTagLib(
        file: File,
        title: String,
        artist: String,
        album: String,
        duration: Long,
        deepMetadata: Boolean
    ): Boolean {
        return deepMetadata ||
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

    private fun ratingStarsFromTagValues(vararg values: String?): Int {
        return values
            .flatMap { value -> value.orEmpty().split(';', '\n') }
            .mapNotNull { it.parseRatingStars() }
            .maxOrNull()
            ?.coerceIn(0, 5)
            ?: 0
    }

    private fun String.parseRatingStars(): Int? {
        val text = cleanTagText()
        if (text.isBlank()) return null

        val filledStars = text.count { it == '★' || it == '⭐' }
        if (filledStars > 0) return filledStars.coerceIn(0, 5)

        val numeric = Regex("""([0-9]+(?:\.[0-9]+)?)""")
            .find(text)
            ?.groupValues
            ?.getOrNull(1)
            ?.toFloatOrNull()
            ?: return null

        return when {
            numeric <= 0f -> 0
            numeric <= 1f -> kotlin.math.round(numeric * 5f).toInt()
            numeric <= 5f -> kotlin.math.round(numeric).toInt()
            numeric <= 100f -> kotlin.math.round(numeric / 20f).toInt()
            numeric <= 255f -> kotlin.math.round(numeric / 255f * 5f).toInt()
            else -> null
        }?.coerceIn(0, 5)
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
        val album: String? = null,
        val albumArtist: String? = null,
        val genre: String? = null,
        val year: String? = null,
        val composer: String? = null,
        val lyricist: String? = null
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
                                album = values.firstInfoValue("IPRD", "IALB", "ALBUM", "PRODUCT"),
                                albumArtist = values.firstInfoValue("ALBUMARTIST", "ALBUM ARTIST", "IARTIST"),
                                genre = values.firstInfoValue("IGNR", "GENRE"),
                                year = values.firstInfoValue("ICRD", "YEAR", "DATE"),
                                composer = values.firstInfoValue("IMUS", "COMPOSER", "TCOM"),
                                lyricist = values.firstInfoValue("IWRI", "LYRICIST", "WRITER", "TEXT")
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

    private fun Map<String, List<String>>.firstTagValue(vararg keys: String): String {
        val normalizedKeys = keys.map { it.normalizedPropertyKey() }.toSet()
        for (key in keys) {
            get(key)?.firstNotBlank()?.let { return it.cleanTagText() }
        }
        for ((key, values) in this) {
            if (key.normalizedPropertyKey() in normalizedKeys) {
                values.firstNotBlank()?.let { return it.cleanTagText() }
            }
        }
        return ""
    }

    private fun Map<String, List<String>>.allTagValues(vararg keys: String): String {
        val normalizedKeys = keys.map { it.normalizedPropertyKey() }.toSet()
        val values = linkedSetOf<String>()
        for (key in keys) {
            get(key)
                ?.map { it.cleanTagText() }
                ?.filter { it.isNotBlank() }
                ?.forEach(values::add)
        }
        for ((key, propertyValues) in this) {
            if (key.normalizedPropertyKey() in normalizedKeys) {
                propertyValues
                    .map { it.cleanTagText() }
                    .filter { it.isNotBlank() }
                    .forEach(values::add)
            }
        }
        return values.joinToString(";")
    }

    private fun Map<String, List<String>>.findNeteaseKey(): String {
        for ((key, values) in this) {
            if (!key.isNeteaseKeyPropertyName()) continue
            values.asSequence()
                .map { it.cleanTagText() }
                .firstOrNull { it.looksLikeNeteaseKeyValue() }
                ?.let { return it }
        }
        return ""
    }

    private fun Map<String, List<String>>.findPrefixedNeteaseCommentKey(): String {
        for ((key, values) in this) {
            if (key.normalizedPropertyKey() != "comment") continue
            values.asSequence()
                .map { it.extractPrefixedNeteaseCommentKey() }
                .firstOrNull { it.isNotBlank() }
                ?.let { return it }
        }
        return ""
    }

    private fun String.extractPrefixedNeteaseCommentKey(): String {
        val text = cleanTagText()
        return text.takeIf {
            neteaseCommentPrefixRegex.containsMatchIn(it) &&
                it.looksLikeNeteaseKeyValue()
        }.orEmpty()
    }

    private fun String.isNeteaseKeyPropertyName(): Boolean {
        val normalized = normalizedPropertyKey()
        return normalized in explicitNeteaseKeyProperties ||
            normalized.startsWith("163KEY") ||
            normalized.startsWith("NETEASEKEY") ||
            normalized.startsWith("CLOUDMUSICKEY")
    }

    private val explicitNeteaseKeyProperties = setOf(
        "163KEY",
        "NETEASEKEY",
        "NETEASECLOUDMUSICKEY",
        "CLOUDMUSICKEY",
        "MUSIC163KEY"
    )

    private val neteaseCommentPrefixRegex = Regex(
        """^\s*163\s+key\s*\(\s*don't\s+modify\s*\)\s*:""",
        RegexOption.IGNORE_CASE
    )

    private fun List<String>.firstNotBlank(): String? =
        firstOrNull { it.trim().isNotBlank() }?.trim()

    private fun String.cleanTagText(): String =
        trim('\uFEFF', '\u0000', ' ', '\t', '\r', '\n')
            .replace(Regex("""\s+"""), " ")

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

    private fun String.normalizeYear(): String =
        Regex("""\d{4}""").find(this)?.value ?: trim()

    private fun String.looksLikeMojibake(): Boolean {
        val text = trim()
        if (text.isBlank()) return false
        if ('\uFFFD' in text || "锟斤拷" in text || "�" in text) return true
        return Regex("""(?:锟|斤|拷){3,}""").containsMatchIn(text)
    }

    private fun Int.normalizedTrackNumber(): Int =
        if (this > 1000) this % 1000 else this

    private fun Int.normalizedDiscNumber(): Int =
        if (this >= 1000) this / 1000 else 0

    private fun String.normalizedDiscNumberFromTag(): Int =
        substringBefore('/').trim().toIntOrNull() ?: 0

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
