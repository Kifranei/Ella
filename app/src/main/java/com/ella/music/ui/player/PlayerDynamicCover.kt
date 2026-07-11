package com.ella.music.ui.player

import android.content.Context
import android.graphics.Outline
import android.graphics.Color
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Environment
import android.util.Log
import android.view.View
import android.view.ViewOutlineProvider
import androidx.documentfile.provider.DocumentFile
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player as Media3Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.ella.music.data.model.Song
import com.ella.music.data.model.playlistIdentityKey
import com.ella.music.ui.components.SafeCoverImage
import java.io.File
import java.util.concurrent.ConcurrentHashMap

internal enum class DynamicCoverKind {
    Video,
    AnimatedImage
}

internal enum class PlayerVideoRole {
    DynamicCover,
    MusicVideo
}

internal data class DynamicCoverSource(
    val uri: Uri,
    val failureKey: String,
    val kind: DynamicCoverKind = DynamicCoverKind.Video,
    val aspectRatio: Float? = null,
    val preferLandscapeBackground: Boolean = false,
    val playbackOwnerKey: String = "",
    val role: PlayerVideoRole = PlayerVideoRole.DynamicCover
)

internal data class PlayerVideoSources(
    val dynamicCover: DynamicCoverSource? = null,
    val musicVideo: DynamicCoverSource? = null
)

internal fun Song.dynamicCoverResolutionKey(): String =
    listOf(
        playlistIdentityKey(),
        path,
        title,
        artist,
        album,
        dateModified,
        fileSize
    ).joinToString("|")

@Composable
internal fun DynamicCoverVideo(
    source: DynamicCoverSource,
    isPlaying: Boolean,
    onPlaybackError: () -> Unit,
    modifier: Modifier = Modifier,
    cornerRadiusDp: Float = 14f,
    resizeMode: Int = androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FIT
) {
    if (source.kind == DynamicCoverKind.AnimatedImage) {
        SafeCoverImage(
            model = source.uri,
            contentDescription = null,
            modifier = modifier,
            contentScale = if (resizeMode == androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_ZOOM) {
                ContentScale.Crop
            } else {
                ContentScale.Fit
            },
            sizePx = 1200,
            showDefaultPlaceholder = false
        )
        return
    }

    val context = LocalContext.current
    val playbackMemoryKey = remember(source.failureKey, source.playbackOwnerKey) {
        DynamicCoverPlaybackMemory.activate(
            ownerKey = source.playbackOwnerKey.ifBlank { source.failureKey },
            sourceKey = source.failureKey
        )
    }
    val initialPositionMs = remember(playbackMemoryKey) {
        DynamicCoverPlaybackMemory.restore(playbackMemoryKey)
    }

    val exoPlayer = remember(playbackMemoryKey) {
        ExoPlayer.Builder(context).build().apply {
            repeatMode = Media3Player.REPEAT_MODE_ALL
            volume = 0f
            setMediaItem(MediaItem.fromUri(source.uri))
            prepare()
            if (initialPositionMs > 0L) seekTo(initialPositionMs)
        }
    }

    DisposableEffect(exoPlayer) {
        val listener = object : Media3Player.Listener {
            override fun onPlayerError(error: PlaybackException) {
                onPlaybackError()
            }
        }

        exoPlayer.addListener(listener)

        onDispose {
            DynamicCoverPlaybackMemory.save(playbackMemoryKey, exoPlayer.currentPosition)
            exoPlayer.removeListener(listener)
            exoPlayer.release()
        }
    }

    DisposableEffect(isPlaying, exoPlayer) {
        exoPlayer.playWhenReady = isPlaying
        onDispose { }
    }

    AndroidView(
        modifier = modifier,
        factory = { viewContext ->
            PlayerView(viewContext).apply {
                useController = false
                controllerAutoShow = false
                controllerHideOnTouch = false
                this.resizeMode = resizeMode
                setKeepContentOnPlayerReset(true)
                setShutterBackgroundColor(Color.TRANSPARENT)
                setShowBuffering(PlayerView.SHOW_BUFFERING_NEVER)
                findViewById<View>(androidx.media3.ui.R.id.exo_controller)?.visibility = View.GONE
                player = exoPlayer
                clipToOutline = true
                outlineProvider = object : ViewOutlineProvider() {
                    override fun getOutline(view: View, outline: Outline) {
                        val radiusPx = view.resources.displayMetrics.density * cornerRadiusDp
                        outline.setRoundRect(0, 0, view.width, view.height, radiusPx)
                    }
                }
                hideController()
            }
        },
        update = { view ->
            view.useController = false
            view.controllerAutoShow = false
            view.controllerHideOnTouch = false
            view.findViewById<View>(androidx.media3.ui.R.id.exo_controller)?.visibility = View.GONE
            view.player = exoPlayer
            view.resizeMode = resizeMode
            view.setKeepContentOnPlayerReset(true)
            view.setShutterBackgroundColor(Color.TRANSPARENT)
            view.clipToOutline = true
            view.hideController()
            exoPlayer.playWhenReady = isPlaying
        }
    )
}

internal fun Song.dynamicCoverSource(
    context: Context,
    includeExternalFiles: Boolean = true,
    customRootPaths: List<String> = emptyList()
): DynamicCoverSource? {
    val resolvedSource = if (includeExternalFiles) {
        dynamicCoverVideoFile(
            context = context,
            customRootPaths = customRootPaths,
            includeExternalFiles = includeExternalFiles,
            musicVideoOnly = false
        )?.let { file ->
            file.toDynamicCoverSource(
                context = context,
                role = PlayerVideoRole.DynamicCover
            )
        } ?: dynamicCoverDocumentSource(
            context = context,
            customRootPaths = customRootPaths,
            musicVideoOnly = false
        ) ?: legacyEmbeddedAnimatedImageSource(context)
            ?: embeddedDynamicVideoSource(context)
    } else {
        legacyEmbeddedAnimatedImageSource(context) ?: embeddedDynamicVideoSource(context)
    }
    return resolvedSource?.copy(playbackOwnerKey = dynamicCoverResolutionKey())
}

internal fun Song.musicVideoSource(
    context: Context,
    customRootPaths: List<String> = emptyList()
): DynamicCoverSource? {
    val resolvedSource = dynamicCoverVideoFile(
        context = context,
        customRootPaths = customRootPaths,
        includeExternalFiles = true,
        musicVideoOnly = true
    )?.toDynamicCoverSource(
        context = context,
        role = PlayerVideoRole.MusicVideo
    ) ?: dynamicCoverDocumentSource(
        context = context,
        customRootPaths = customRootPaths,
        musicVideoOnly = true
    )
    return resolvedSource?.copy(playbackOwnerKey = dynamicCoverResolutionKey())
}

private fun Song.embeddedDynamicVideoSource(context: Context): DynamicCoverSource? {
    val mediaUri = dynamicCoverMediaUri() ?: return null
    if (!hasPlayableEmbeddedVideoTrack(context, mediaUri)) return null
    return DynamicCoverSource(
        uri = mediaUri,
        failureKey = "embedded-video:$path:${dateModified}:${fileSize}",
        aspectRatio = context.readDynamicCoverAspectRatio(mediaUri)
    )
}

private fun Song.legacyEmbeddedAnimatedImageSource(context: Context): DynamicCoverSource? {
    val mediaUri = dynamicCoverMediaUri() ?: return null
    val picture = runCatching {
        MediaMetadataRetriever().useCompat { retriever ->
            if (mediaUri.scheme.equals("content", ignoreCase = true)) {
                retriever.setDataSource(context, mediaUri)
            } else {
                retriever.setDataSource(mediaUri.path.orEmpty())
            }
            retriever.embeddedPicture
        }
    }.getOrNull()?.takeIf { it.isNotEmpty() } ?: return null

    val format = picture.legacyAnimatedPictureFormat() ?: return null
    val cacheFile = File(context.cacheDir, "dynamic_covers/${path.hashCode()}_${dateModified}_${fileSize}.${format.extension}")
    return runCatching {
        cacheFile.parentFile?.mkdirs()
        if (!cacheFile.exists() || cacheFile.length() != picture.size.toLong()) {
            cacheFile.writeBytes(picture)
        }
        DynamicCoverSource(
            uri = Uri.fromFile(cacheFile),
            failureKey = "embedded-image:${cacheFile.absolutePath}:${cacheFile.length()}",
            kind = DynamicCoverKind.AnimatedImage
        )
    }.getOrNull()
}

private data class LegacyAnimatedPictureFormat(val extension: String)

private fun ByteArray.legacyAnimatedPictureFormat(): LegacyAnimatedPictureFormat? {
    return when {
        startsWithAscii("GIF8") -> LegacyAnimatedPictureFormat("gif")
        else -> null
    }
}

private fun ByteArray.startsWithBytes(vararg bytes: Int): Boolean =
    size >= bytes.size && bytes.indices.all { (this[it].toInt() and 0xFF) == bytes[it] }

private fun ByteArray.startsWithAscii(prefix: String): Boolean =
    size >= prefix.length && prefix.indices.all { this[it].toInt().toChar() == prefix[it] }

private inline fun <T> MediaMetadataRetriever.useCompat(block: (MediaMetadataRetriever) -> T): T {
    try {
        return block(this)
    } finally {
        release()
    }
}

private fun Song.dynamicCoverMediaUri(): Uri? {
    val trimmedPath = path.trim()
    if (trimmedPath.isBlank() || trimmedPath.startsWith("http://") || trimmedPath.startsWith("https://")) return null
    return if (trimmedPath.startsWith("content://", ignoreCase = true)) {
        Uri.parse(trimmedPath)
    } else {
        File(trimmedPath)
            .takeIf { it.exists() && it.isFile && it.length() > 0L }
            ?.let(Uri::fromFile)
    }
}

private fun Song.hasPlayableEmbeddedVideoTrack(context: Context, uri: Uri): Boolean {
    return runCatching {
        val extractor = MediaExtractor()
        try {
            if (uri.scheme.equals("content", ignoreCase = true)) {
                extractor.setDataSource(context, uri, null)
            } else {
                extractor.setDataSource(uri.path.orEmpty())
            }
            (0 until extractor.trackCount).any { index ->
                val format = extractor.getTrackFormat(index)
                val mime = format.getString(MediaFormat.KEY_MIME).orEmpty().lowercase()
                mime.startsWith("video/") &&
                    mime != "video/mjpeg" &&
                    !mime.startsWith("image/")
            }
        } finally {
            extractor.release()
        }
    }.getOrElse { error ->
        Log.d("PlayerScreen", "Embedded dynamic cover video unavailable for ${title.ifBlank { fileName }}", error)
        false
    }
}

private fun Song.dynamicCoverVideoFile(
    context: Context,
    customRootPaths: List<String>,
    includeExternalFiles: Boolean,
    musicVideoOnly: Boolean
): File? {
    val songFile = path
        .takeUnless { it.startsWith("http://") || it.startsWith("https://") }
        ?.let { File(it) }

    val songFolder = songFile?.parentFile

    val albumName = album.ifBlank {
        songFolder?.name.orEmpty()
    }.ifBlank {
        "Unknown"
    }

    val albumKey = albumName.toSafeDynamicCoverName()

    val artistAlbumKey = listOf(artist, albumName)
        .filter { it.isNotBlank() }
        .joinToString(" - ")
        .toSafeDynamicCoverName()

    val artistSongName = listOf(artist, title)
        .filter { it.isNotBlank() }
        .joinToString(" - ")
    val artistSongCompactName = listOf(artist, title)
        .filter { it.isNotBlank() }
        .joinToString("-")
    val songNameCandidates = listOf(
        songFile?.nameWithoutExtension.orEmpty(),
        title,
        artistSongCompactName,
        artistSongName
    )
        .filter { it.isNotBlank() }
        .distinct()
    val safeSongNameCandidates = songNameCandidates
        .map { it.toSafeDynamicCoverName() }
        .filter { it.isNotBlank() }
        .distinct()
    val songCandidates = (songNameCandidates + safeSongNameCandidates).distinct()
    val musicVideoSongCandidates = buildLandscapeMusicVideoNameCandidates(songCandidates)
    val selectedSongCandidates = playerVideoNameCandidates(songCandidates, musicVideoOnly)
    val albumNameCandidates = listOf(
        albumName,
        albumKey,
        listOf(artist, albumName).filter { it.isNotBlank() }.joinToString(" - "),
        artistAlbumKey
    )
        .filter { it.isNotBlank() }
        .distinct()

    val folderCandidates = songFolder
        ?.takeIf { it.exists() && it.isDirectory }
        ?.let { folder ->
            if (musicVideoOnly) {
                musicVideoSongCandidates.map { File(folder, "$it.mp4") }
            } else {
                songCandidates.map { File(folder, "$it.mp4") } + listOf(
                    File(folder, "cover.mp4"),
                    File(folder, "${folder.name}.mp4"),
                    File(folder, "$albumName.mp4"),
                    File(folder, "$albumKey.mp4"),
                    File(folder, "$artistAlbumKey.mp4")
                )
            }
        }
        .orEmpty()

    val roots = dynamicCoverRootDirectories(
        context = context,
        customRootPaths = customRootPaths,
        includeExternalFiles = includeExternalFiles
    )

    val libraryCandidates = roots.flatMap { root ->
        buildList {
            if (!musicVideoOnly) add(File(root, "cover.mp4"))
            addAll(selectedSongCandidates.map { name ->
                File(root, "$name.mp4")
            })
            if (!musicVideoOnly) {
                addAll(albumNameCandidates.map { name -> File(root, "$name.mp4") })
            }
            listOf("Song", "song").forEach { songDir ->
                addAll(selectedSongCandidates.map { name ->
                    File(root, "$songDir/$name.mp4")
                })
            }
            if (!musicVideoOnly) {
                listOf("Album", "album").forEach { albumDir ->
                    addAll(albumNameCandidates.map { name -> File(root, "$albumDir/$name.mp4") })
                }
            }
        }
    }

    val candidates = folderCandidates + libraryCandidates

    candidates.firstOrNull { it.exists() && it.isFile && it.length() > 0L }?.let { return it }

    val fuzzySongTokens = selectedSongCandidates
        .mapTo(mutableSetOf()) { it.toDynamicCoverMatchToken() }
    val fuzzyAlbumTokens = albumNameCandidates
        .mapTo(mutableSetOf()) { it.toDynamicCoverMatchToken() }
    val fuzzySearchDirs = buildList {
        songFolder?.takeIf { it.exists() && it.isDirectory }?.let(::add)
        roots.forEach { root ->
            root.takeIf { it.exists() && it.isDirectory }?.let(::add)
            File(root, "Song").takeIf { it.exists() && it.isDirectory }?.let(::add)
            File(root, "song").takeIf { it.exists() && it.isDirectory }?.let(::add)
            File(root, "Album").takeIf { it.exists() && it.isDirectory }?.let(::add)
            File(root, "album").takeIf { it.exists() && it.isDirectory }?.let(::add)
        }
    }.distinctBy { it.absolutePath.lowercase() }

    return fuzzySearchDirs.firstNotNullOfOrNull { dir ->
        dir.listFiles { file ->
            file.isFile &&
                file.extension.equals("mp4", ignoreCase = true) &&
                file.length() > 0L &&
                file.nameWithoutExtension.toDynamicCoverMatchToken().let { token ->
                    token in fuzzySongTokens || (!musicVideoOnly && token in fuzzyAlbumTokens)
                }
        }?.firstOrNull()
    }
}

internal fun dynamicCoverRootDirectories(
    context: Context,
    customRootPaths: List<String>,
    includeExternalFiles: Boolean = true
): List<File> {
    val customRoots = customRootPaths
        .map { it.trim() }
        .filter { it.isNotBlank() }
        .filterNot { it.startsWith("content://", ignoreCase = true) }
        .map(::File)

    val publicMovieDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES)
    val defaultRoots = listOf(
        File(publicMovieDir, "Halcyon/DynamicCovers"),
        File(publicMovieDir, "Ella/DynamicCovers")
    )
    val appRoots = if (includeExternalFiles) {
        listOf(
            File(
                context.getExternalFilesDir(Environment.DIRECTORY_MOVIES),
                "DynamicCovers"
            )
        )
    } else {
        emptyList()
    }

    return (customRoots + defaultRoots + appRoots)
        .map { it.absoluteFile }
        .distinctBy { it.path.lowercase() }
}

private fun Song.dynamicCoverDocumentSource(
    context: Context,
    customRootPaths: List<String>,
    musicVideoOnly: Boolean
): DynamicCoverSource? {
    val albumName = album.ifBlank { "Unknown" }
    val artistAlbumKey = listOf(artist, albumName)
        .filter { it.isNotBlank() }
        .joinToString(" - ")
        .toSafeDynamicCoverName()
    val artistSongName = listOf(artist, title)
        .filter { it.isNotBlank() }
        .joinToString(" - ")
    val artistSongCompactName = listOf(artist, title)
        .filter { it.isNotBlank() }
        .joinToString("-")
    val songNameCandidates = listOf(
        File(path).nameWithoutExtension,
        title,
        artistSongCompactName,
        artistSongName
    )
        .filter { it.isNotBlank() }
        .distinct()
    val safeSongNameCandidates = songNameCandidates
        .map { it.toSafeDynamicCoverName() }
        .filter { it.isNotBlank() }
        .distinct()
    val albumNameCandidates = listOf(
        albumName,
        albumName.toSafeDynamicCoverName(),
        listOf(artist, albumName).filter { it.isNotBlank() }.joinToString(" - "),
        artistAlbumKey
    )
        .filter { it.isNotBlank() }
        .distinct()
    val songCandidates = (songNameCandidates + safeSongNameCandidates).distinct()
    val selectedSongCandidates = playerVideoNameCandidates(songCandidates, musicVideoOnly)
    val fuzzySongTokens = selectedSongCandidates.mapTo(mutableSetOf()) { it.toDynamicCoverMatchToken() }
    val fuzzyAlbumTokens = albumNameCandidates.mapTo(mutableSetOf()) { it.toDynamicCoverMatchToken() }

    return customRootPaths
        .asSequence()
        .map(String::trim)
        .filter { it.startsWith("content://", ignoreCase = true) }
        .mapNotNull { rawUri ->
            val root = runCatching {
                DocumentFile.fromTreeUri(context, Uri.parse(rawUri))
            }.getOrNull() ?: return@mapNotNull null

            val searchRoots = listOfNotNull(
                root,
                root.findChildDirectoryIgnoreCase("Song"),
                root.findChildDirectoryIgnoreCase("Album")
            )

            searchRoots.firstNotNullOfOrNull { directory ->
                val exactNames = buildList {
                    if (!musicVideoOnly) add("cover.mp4")
                    addAll(selectedSongCandidates.map { "$it.mp4" })
                    if (!musicVideoOnly) addAll(albumNameCandidates.map { "$it.mp4" })
                }
                exactNames.firstNotNullOfOrNull { name ->
                    directory.findChildFileIgnoreCase(name)?.toDynamicCoverSource(
                        context,
                        rawUri,
                        if (musicVideoOnly) PlayerVideoRole.MusicVideo else PlayerVideoRole.DynamicCover
                    )
                } ?: directory.listFiles().firstOrNull { file ->
                    file.isFile &&
                        file.length() > 0L &&
                        file.name.orEmpty().substringAfterLast('.', "").equals("mp4", ignoreCase = true) &&
                        file.name.orEmpty().substringBeforeLast('.').toDynamicCoverMatchToken().let { token ->
                            token in fuzzySongTokens || (!musicVideoOnly && token in fuzzyAlbumTokens)
                        }
                }?.toDynamicCoverSource(
                    context,
                    rawUri,
                    if (musicVideoOnly) PlayerVideoRole.MusicVideo else PlayerVideoRole.DynamicCover
                )
            }
        }
        .firstOrNull()
}

/** Keeps an ambient video's loop position while Compose swaps player pages. */
internal object DynamicCoverPlaybackMemory {
    private val positions = ConcurrentHashMap<String, Long>()
    private var activeOwnerKey: String? = null

    @Synchronized
    fun activate(ownerKey: String, sourceKey: String): String {
        if (activeOwnerKey != ownerKey) {
            positions.clear()
            activeOwnerKey = ownerKey
        }
        return "$ownerKey|$sourceKey"
    }

    fun restore(key: String): Long = positions[key]?.coerceAtLeast(0L) ?: 0L

    fun save(key: String, positionMs: Long) {
        if (positionMs > 0L) positions[key] = positionMs
    }

    /**
     * Explicitly clears all remembered playback positions.
     *
     * Called when the active song changes (next/previous) to guarantee MV/dynamic-cover
     * videos restart from the beginning instead of resuming a stale position. This is a
     * safety net alongside [activate] — the activate-based clear can be defeated by Compose
     * lifecycle timing (onDispose save racing ahead of activate clear).
     */
    @Synchronized
    fun clearAll() {
        positions.clear()
        activeOwnerKey = null
    }
}

private fun DocumentFile.findChildDirectoryIgnoreCase(name: String): DocumentFile? =
    listFiles().firstOrNull { it.isDirectory && it.name.equals(name, ignoreCase = true) }

private fun DocumentFile.findChildFileIgnoreCase(name: String): DocumentFile? =
    listFiles().firstOrNull { it.isFile && it.length() > 0L && it.name.equals(name, ignoreCase = true) }

private fun File.toDynamicCoverSource(
    context: Context,
    role: PlayerVideoRole
): DynamicCoverSource {
    val uri = Uri.fromFile(this)
    return DynamicCoverSource(
        uri = uri,
        failureKey = "file:${absolutePath}:${lastModified()}:${length()}",
        aspectRatio = context.readDynamicCoverAspectRatio(uri),
        preferLandscapeBackground = role == PlayerVideoRole.MusicVideo,
        role = role
    )
}

private fun DocumentFile.toDynamicCoverSource(
    context: Context,
    rootUri: String,
    role: PlayerVideoRole
): DynamicCoverSource =
    DynamicCoverSource(
        uri = uri,
        failureKey = "tree:$rootUri:${uri}:${length()}",
        aspectRatio = context.readDynamicCoverAspectRatio(uri),
        preferLandscapeBackground = role == PlayerVideoRole.MusicVideo,
        role = role
    )

private fun Context.readDynamicCoverAspectRatio(uri: Uri): Float? =
    runCatching {
        MediaMetadataRetriever().useCompat { retriever ->
            if (uri.scheme.equals("content", ignoreCase = true)) {
                retriever.setDataSource(this, uri)
            } else {
                retriever.setDataSource(uri.path.orEmpty())
            }
            val width = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)
                ?.toIntOrNull()
                ?: return@useCompat null
            val height = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)
                ?.toIntOrNull()
                ?: return@useCompat null
            if (width <= 0 || height <= 0) return@useCompat null
            val rotation = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)
                ?.toIntOrNull()
                ?: 0
            val rotated = rotation == 90 || rotation == 270
            val displayWidth = if (rotated) height else width
            val displayHeight = if (rotated) width else height
            displayWidth.toFloat() / displayHeight.toFloat()
        }
    }.getOrNull()

private fun String.toSafeDynamicCoverName(): String {
    return trim()
        .replace("""[\\/:*?"<>|]""".toRegex(), "_")
        .replace("\\s+".toRegex(), " ")
        .ifBlank { "Unknown" }
}

private val LANDSCAPE_MUSIC_VIDEO_SUFFIX_REGEX =
    Regex("""(?:[\s_\-–—]+mv)$""", RegexOption.IGNORE_CASE)

internal fun buildLandscapeMusicVideoNameCandidates(baseNames: Collection<String>): List<String> =
    baseNames
        .map(String::trim)
        .filter { it.isNotBlank() }
        .flatMap { name -> listOf("${name}_MV", "${name}-MV") }
        .distinct()

internal fun playerVideoNameCandidates(
    songCandidates: Collection<String>,
    musicVideoOnly: Boolean
): List<String> = if (musicVideoOnly) {
    buildLandscapeMusicVideoNameCandidates(songCandidates)
} else {
    songCandidates.map(String::trim).filter(String::isNotBlank).distinct()
}

internal fun isLandscapeMusicVideoFileName(
    nameWithoutExtension: String,
    songCandidates: Collection<String>
): Boolean {
    if (!nameWithoutExtension.hasLandscapeMusicVideoSuffix()) return false
    val baseToken = nameWithoutExtension.removeLandscapeMusicVideoSuffix().toDynamicCoverMatchToken()
    val songTokens = songCandidates.mapTo(mutableSetOf()) { it.toDynamicCoverMatchToken() }
    return baseToken.isNotBlank() && baseToken in songTokens
}

private fun String.hasLandscapeMusicVideoSuffix(): Boolean =
    LANDSCAPE_MUSIC_VIDEO_SUFFIX_REGEX.containsMatchIn(trim())

private fun String.removeLandscapeMusicVideoSuffix(): String =
    trim().replace(LANDSCAPE_MUSIC_VIDEO_SUFFIX_REGEX, "")

private fun String.toDynamicCoverMatchToken(): String =
    lowercase()
        .replace(Regex("""[\s_\-–—]+"""), "")
        .replace(Regex("""[\\/:*?"<>|.,，。'’`~!！()\[\]{}]+"""), "")
