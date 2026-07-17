package com.ella.music.desktop

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.awt.Desktop
import java.nio.file.Path
import java.util.UUID
import java.util.concurrent.Executors
import javax.swing.JFileChooser
import javax.swing.SwingUtilities

/** State holder for the desktop client. Android services are deliberately replaced at this edge. */
class DesktopController(
    private val store: DesktopLibraryStore = DesktopLibraryStore(),
    private val scanner: DesktopLibraryScanner = DesktopLibraryScanner()
) : AutoCloseable {
    private val worker = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "halcyon-library").apply { isDaemon = true }
    }
    private val audio = FfmpegAudioEngine(
        onState = { isPlaying, position -> onUi { updatePlayback(isPlaying = isPlaying, positionMs = position) } },
        onCompleted = { onUi(::next) },
        onError = { message -> onUi { updatePlayback(error = message, isPlaying = false) } }
    )

    var library by mutableStateOf(store.load())
        private set
    var section by mutableStateOf(DesktopSection.LIBRARY)
    var searchQuery by mutableStateOf("")
    var playback by mutableStateOf(DesktopPlaybackState())
        private set
    var lyrics by mutableStateOf(emptyList<DesktopLyricLine>())
        private set
    var isScanning by mutableStateOf(false)
        private set
    var scanMessage by mutableStateOf<String?>(null)
        private set
    var selectedPlaylistId by mutableStateOf<String?>(null)
    var activeQueue by mutableStateOf(emptyList<String>())
        private set

    init {
        if (library.libraryRoots.isNotEmpty()) rescan()
    }

    val visibleSongs: List<DesktopSong>
        get() {
            val source = selectedPlaylistId?.let { id ->
                library.playlists.firstOrNull { it.id == id }?.songIds
                    ?.mapNotNull { songId -> library.songs.firstOrNull { it.id == songId } }
                    .orEmpty()
            } ?: library.songs
            val query = searchQuery.trim()
            if (query.isBlank()) return source
            return source.filter { song ->
                listOf(song.title, song.artist, song.album, song.genre, song.path)
                    .any { it.contains(query, ignoreCase = true) }
            }
        }

    fun chooseAndAddLibraryRoot() {
        val picker = JFileChooser().apply {
            fileSelectionMode = JFileChooser.DIRECTORIES_ONLY
            isMultiSelectionEnabled = false
            dialogTitle = "Choose a music folder"
        }
        if (picker.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) {
            addLibraryRoot(picker.selectedFile.toPath().toAbsolutePath().normalize().toString())
        }
    }

    fun addLibraryRoot(path: String) {
        if (path in library.libraryRoots) return
        library = library.copy(libraryRoots = library.libraryRoots + path)
        persist()
        rescan()
    }

    fun removeLibraryRoot(path: String) {
        library = library.copy(libraryRoots = library.libraryRoots.filterNot { it == path })
        persist()
        rescan()
    }

    fun rescan() {
        val roots = library.libraryRoots
        if (roots.isEmpty()) {
            library = library.copy(songs = emptyList())
            persist()
            scanMessage = "Add a music folder to start your library."
            return
        }
        isScanning = true
        scanMessage = "Scanning music library…"
        worker.execute {
            runCatching {
                scanner.scan(roots) { scanned, accepted ->
                    onUi { scanMessage = "Scanned $scanned files • found $accepted tracks" }
                }
            }.onSuccess { scannedSongs ->
                onUi {
                    library = library.copy(songs = scannedSongs)
                    persist()
                    isScanning = false
                    scanMessage = "Found ${scannedSongs.size} tracks"
                    playback.song?.let { playing ->
                        library.songs.firstOrNull { it.id == playing.id }?.let(::selectSongMetadata)
                    }
                }
            }.onFailure { error ->
                onUi {
                    isScanning = false
                    scanMessage = "Scan failed: ${error.message ?: error.javaClass.simpleName}"
                }
            }
        }
    }

    fun play(song: DesktopSong, queue: List<DesktopSong> = visibleSongs) {
        activeQueue = queue.map(DesktopSong::id).ifEmpty { listOf(song.id) }
        selectSongMetadata(song)
        playback = DesktopPlaybackState(song = song, isPlaying = true, positionMs = 0L)
        audio.play(song)
    }

    fun togglePlayback() {
        val current = playback.song
        when {
            current == null -> visibleSongs.firstOrNull()?.let(::play)
            playback.isPlaying -> audio.pause()
            else -> audio.resume()
        }
    }

    fun seekTo(positionMs: Long) {
        if (playback.song == null) return
        audio.seekTo(positionMs)
        updatePlayback(positionMs = positionMs)
    }

    fun next() = advance(1)

    fun previous() = advance(-1)

    fun createPlaylist(name: String) {
        val sanitized = name.trim()
        if (sanitized.isBlank() || library.playlists.any { it.name.equals(sanitized, ignoreCase = true) }) return
        val playlist = DesktopPlaylist(id = UUID.randomUUID().toString(), name = sanitized)
        library = library.copy(playlists = library.playlists + playlist)
        selectedPlaylistId = playlist.id
        persist()
    }

    fun addSongToPlaylist(song: DesktopSong, playlistId: String) {
        val playlists = library.playlists.map { playlist ->
            if (playlist.id == playlistId && song.id !in playlist.songIds) {
                playlist.copy(songIds = playlist.songIds + song.id)
            } else {
                playlist
            }
        }
        library = library.copy(playlists = playlists)
        persist()
    }

    fun removeSongFromPlaylist(song: DesktopSong, playlistId: String) {
        library = library.copy(playlists = library.playlists.map { playlist ->
            if (playlist.id == playlistId) playlist.copy(songIds = playlist.songIds - song.id) else playlist
        })
        persist()
    }

    fun setFloatingLyrics(enabled: Boolean) {
        library = library.copy(floatingLyricsEnabled = enabled)
        persist()
    }

    fun revealCurrentSong() {
        val parent = playback.song?.path?.let(Path::of)?.parent ?: return
        runCatching { if (Desktop.isDesktopSupported()) Desktop.getDesktop().open(parent.toFile()) }
    }

    private fun advance(delta: Int) {
        val queue = activeQueue.ifEmpty { library.songs.map(DesktopSong::id) }
        if (queue.isEmpty()) return
        val currentIndex = playback.song?.id?.let(queue::indexOf)?.takeIf { it >= 0 } ?: if (delta > 0) -1 else 0
        val nextId = queue[(currentIndex + delta).floorMod(queue.size)]
        library.songs.firstOrNull { it.id == nextId }?.let { nextSong ->
            val songs = queue.mapNotNull { id -> library.songs.firstOrNull { it.id == id } }
            play(nextSong, songs)
        }
    }

    private fun Int.floorMod(modulus: Int): Int = ((this % modulus) + modulus) % modulus

    private fun selectSongMetadata(song: DesktopSong) {
        lyrics = DesktopLyrics.load(song.lyricPath)
    }

    private fun updatePlayback(
        isPlaying: Boolean = playback.isPlaying,
        positionMs: Long = playback.positionMs,
        error: String? = playback.error
    ) {
        playback = playback.copy(isPlaying = isPlaying, positionMs = positionMs, error = error)
    }

    private fun persist() = runCatching { store.save(library) }

    private fun onUi(action: () -> Unit) {
        if (SwingUtilities.isEventDispatchThread()) action() else SwingUtilities.invokeLater(action)
    }

    override fun close() {
        audio.close()
        worker.shutdownNow()
    }
}
