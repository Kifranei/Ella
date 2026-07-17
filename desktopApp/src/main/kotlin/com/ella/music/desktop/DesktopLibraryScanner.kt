package com.ella.music.desktop

import org.jaudiotagger.audio.AudioFileIO
import org.jaudiotagger.tag.FieldKey
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import java.util.Locale
import kotlin.io.path.extension
import kotlin.io.path.fileSize
import kotlin.io.path.isRegularFile
import kotlin.io.path.name

/**
 * Uses the file system rather than MediaStore. jaudiotagger supplies the same useful baseline
 * metadata on Windows and Linux for the formats it supports; unreadable tags fall back to names.
 */
class DesktopLibraryScanner(
    private val artworkDirectory: Path = desktopDataDirectory().resolve("artwork")
) {
    fun scan(roots: List<String>, onProgress: (scanned: Int, accepted: Int) -> Unit = { _, _ -> }): List<DesktopSong> {
        val deduplicatedRoots = roots
            .map(Path::of)
            .filter(Files::isDirectory)
            .distinct()
        val songs = mutableListOf<DesktopSong>()
        var scanned = 0

        deduplicatedRoots.forEach { root ->
            Files.walk(root).use { paths ->
                paths.filter { it.isRegularFile() }.forEach { path ->
                    scanned += 1
                    if (path.extension.lowercase(Locale.ROOT) in AUDIO_EXTENSIONS) {
                        readSong(path)?.let(songs::add)
                    }
                    if (scanned % 64 == 0) onProgress(scanned, songs.size)
                }
            }
        }
        onProgress(scanned, songs.size)
        return songs.sortedWith(
            compareBy<DesktopSong> { it.displayArtist.lowercase(Locale.ROOT) }
                .thenBy { it.displayAlbum.lowercase(Locale.ROOT) }
                .thenBy { it.trackNumber }
                .thenBy { it.title.lowercase(Locale.ROOT) }
        )
    }

    private fun readSong(path: Path): DesktopSong? {
        val fileName = path.fileName.toString()
        val fallback = fallbackMetadata(path)
        val audioFile = runCatching { AudioFileIO.read(path.toFile()) }.getOrNull()
        val tag = audioFile?.tag
        fun text(field: FieldKey): String = runCatching { tag?.getFirst(field).orEmpty().trim() }.getOrDefault("")
        fun number(field: FieldKey): Int = text(field).substringBefore('/').toIntOrNull() ?: 0

        val title = text(FieldKey.TITLE).ifBlank { fallback.title }
        val artist = text(FieldKey.ARTIST).ifBlank { fallback.artist }
        val album = text(FieldKey.ALBUM).ifBlank { path.parent?.fileName?.toString().orEmpty() }.ifBlank { "Unknown album" }
        val id = stableId(path)
        val coverPath = extractArtwork(id, tag?.firstArtwork?.binaryData, tag?.firstArtwork?.mimeType)
            ?: findFolderArtwork(path.parent)

        return DesktopSong(
            id = id,
            path = path.toAbsolutePath().normalize().toString(),
            title = title,
            artist = artist,
            album = album,
            albumArtist = text(FieldKey.ALBUM_ARTIST),
            durationMs = (audioFile?.audioHeader?.trackLength ?: 0).toLong() * 1_000L,
            fileSize = runCatching { path.fileSize() }.getOrDefault(0L),
            format = path.extension.uppercase(Locale.ROOT),
            trackNumber = number(FieldKey.TRACK),
            discNumber = number(FieldKey.DISC_NO),
            genre = text(FieldKey.GENRE),
            year = text(FieldKey.YEAR),
            composer = text(FieldKey.COMPOSER),
            lyricist = text(FieldKey.LYRICIST),
            coverPath = coverPath?.toString(),
            lyricPath = findSidecarLyric(path)?.toString()
        )
    }

    private fun fallbackMetadata(path: Path): FallbackMetadata {
        val stem = path.fileName.toString().substringBeforeLast('.', path.fileName.toString()).trim()
        val parts = stem.split(" - ", limit = 2)
        return if (parts.size == 2 && parts[0].isNotBlank() && parts[1].isNotBlank()) {
            FallbackMetadata(title = parts[1], artist = parts[0])
        } else {
            FallbackMetadata(title = stem.ifBlank { path.name }, artist = "Unknown artist")
        }
    }

    private fun extractArtwork(id: String, binaryData: ByteArray?, mimeType: String?): Path? {
        val data = binaryData ?: return null
        if (data.isEmpty()) return null
        val extension = if (mimeType.orEmpty().contains("png", ignoreCase = true)) "png" else "jpg"
        val destination = artworkDirectory.resolve("$id.$extension")
        return runCatching {
            Files.createDirectories(artworkDirectory)
            if (!Files.exists(destination) || Files.size(destination) != data.size.toLong()) {
                Files.write(destination, data)
            }
            destination
        }.getOrNull()
    }

    private fun findFolderArtwork(directory: Path?): Path? {
        if (directory == null || !Files.isDirectory(directory)) return null
        return runCatching {
            Files.list(directory).use { files ->
                files.iterator().asSequence().firstOrNull {
                    it.isRegularFile() && it.fileName.toString().substringBeforeLast('.', "")
                        .lowercase(Locale.ROOT) in FOLDER_ARTWORK_NAMES &&
                        it.extension.lowercase(Locale.ROOT) in IMAGE_EXTENSIONS
                }
            }
        }.getOrNull()
    }

    private fun findSidecarLyric(audioPath: Path): Path? {
        val parent = audioPath.parent ?: return null
        val stem = audioPath.fileName.toString().substringBeforeLast('.', audioPath.fileName.toString())
        return runCatching {
            Files.list(parent).use { files ->
                files.iterator().asSequence().firstOrNull { candidate ->
                    candidate.isRegularFile() &&
                        candidate.fileName.toString().substringBeforeLast('.', "").equals(stem, ignoreCase = true) &&
                        candidate.extension.lowercase(Locale.ROOT) in LYRIC_EXTENSIONS
                }
            }
        }.getOrNull()
    }

    private fun stableId(path: Path): String {
        val bytes = MessageDigest.getInstance("SHA-256")
            .digest(path.toAbsolutePath().normalize().toString().toByteArray())
        return bytes.joinToString("") { "%02x".format(it.toInt() and 0xFF) }.take(24)
    }

    private data class FallbackMetadata(val title: String, val artist: String)

    private companion object {
        val AUDIO_EXTENSIONS = setOf("mp3", "flac", "m4a", "aac", "alac", "wav", "aiff", "aif", "ogg", "opus", "wma", "ape")
        val LYRIC_EXTENSIONS = setOf("lrc", "elrc", "ttml")
        val IMAGE_EXTENSIONS = setOf("jpg", "jpeg", "png", "webp")
        val FOLDER_ARTWORK_NAMES = setOf("cover", "folder", "front", "albumart", "album art")
    }
}
