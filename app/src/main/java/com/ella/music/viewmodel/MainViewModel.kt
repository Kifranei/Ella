package com.ella.music.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.ella.music.data.SettingsManager
import com.ella.music.data.PlaybackHistoryEntry
import com.ella.music.data.PlaybackStatsStore
import com.ella.music.data.SongPlaybackStats
import com.ella.music.data.matchesArtistName
import com.ella.music.data.model.Album
import com.ella.music.data.model.Artist
import com.ella.music.data.model.AudioInfo
import com.ella.music.data.model.Song
import com.ella.music.data.model.albumIdentityId
import com.ella.music.data.repository.MusicRepository
import com.ella.music.data.splitArtistNames
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class MainViewModel(application: Application) : AndroidViewModel(application) {

    val repository = MusicRepository(application)
    val settingsManager = SettingsManager(application)
    private val playbackStatsStore = PlaybackStatsStore.getInstance(application)

    val songs: StateFlow<List<Song>> = repository.songs
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val albums: StateFlow<List<Album>> = repository.albums
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val isScanning: StateFlow<Boolean> = repository.isScanning
    val scanProgress: StateFlow<Int> = repository.scanProgress
    val playbackStats: StateFlow<List<SongPlaybackStats>> = playbackStatsStore.stats
    val playbackHistory: StateFlow<List<PlaybackHistoryEntry>> = playbackStatsStore.history
    val dailyListenMs: StateFlow<Map<String, Long>> = playbackStatsStore.dailyListenMs

    private val _selectedTab = MutableStateFlow(0)
    val selectedTab: StateFlow<Int> = _selectedTab.asStateFlow()
    private var scanJob: Job? = null
    private var autoScanRequested = false

    fun selectTab(index: Int) {
        _selectedTab.value = index
    }

    fun scanMusic() {
        if (scanJob?.isActive == true || isScanning.value) return
        scanJob = viewModelScope.launch {
            val minDuration = settingsManager.minDurationSec.first() * 1000L
            repository.scanMusic(
                minDuration,
                settingsManager.scanIncludeFolders.first().toFolderFilterList(),
                settingsManager.scanExcludeFolders.first().toFolderFilterList()
            )
        }
    }

    fun scanMusicIfAutoEnabled() {
        if (autoScanRequested) return
        autoScanRequested = true
        if (scanJob?.isActive == true || isScanning.value) return
        scanJob = viewModelScope.launch {
            if (!settingsManager.autoScan.first()) return@launch
            val minDuration = settingsManager.minDurationSec.first() * 1000L
            repository.scanMusic(
                minDuration,
                settingsManager.scanIncludeFolders.first().toFolderFilterList(),
                settingsManager.scanExcludeFolders.first().toFolderFilterList()
            )
        }
    }

    fun loadCachedLibrary() {
        viewModelScope.launch {
            repository.loadCachedLibrary()
        }
    }

    fun getSongsForAlbum(albumId: Long): List<Song> {
        return repository.getSongsForAlbum(albumId)
    }

    fun getArtists(): List<Artist> {
        val currentSongs = songs.value
        val currentAlbums = albums.value
        val counts = linkedMapOf<String, ArtistAccumulator>()
        val albumIdsByArtist = mutableMapOf<String, MutableSet<Long>>()

        currentSongs.forEach { song ->
            splitArtistNames(song.artist).forEach { rawName ->
                val key = rawName.lowercase()
                val accumulator = counts.getOrPut(key) { ArtistAccumulator(rawName) }
                accumulator.songCount += 1
                albumIdsByArtist.getOrPut(key) { mutableSetOf() } += song.albumIdentityId()
            }
        }

        currentAlbums.forEach { album ->
            splitArtistNames(album.artist).forEach { rawName ->
                val key = rawName.lowercase()
                counts.getOrPut(key) { ArtistAccumulator(rawName) }
                if (album.id > 0L) {
                    albumIdsByArtist.getOrPut(key) { mutableSetOf() } += album.id
                }
            }
        }

        return counts
            .map { (key, accumulator) ->
                Artist(
                    name = accumulator.name,
                    songCount = accumulator.songCount,
                    albumCount = albumIdsByArtist[key]?.size ?: 0
                )
            }
            .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name })
    }

    fun getSongsForArtist(artistName: String): List<Song> {
        return songs.value.filter { it.artist.matchesArtistName(artistName) }
    }

    fun getAlbumsForArtist(artistName: String): List<Album> {
        val artistSongs = getSongsForArtist(artistName)
        val artistAlbumIds = artistSongs.map { it.albumIdentityId() }.toSet()
        return albums.value
            .filter { it.id in artistAlbumIds || it.artist.matchesArtistName(artistName) }
            .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name })
    }

    fun getAlbumArtUri(albumId: Long) = repository.getAlbumArtUri(albumId)

    fun getCoverArtBitmap(song: Song) = repository.getCoverArtBitmap(song, 128)

    fun getReplayGain(song: Song): Float? {
        return repository.getReplayGain(song)
    }

    fun getAudioInfo(song: Song): AudioInfo {
        return repository.getAudioInfo(song)
    }

    fun clearOnlineMetadataCache() {
        repository.clearRemoteMetadataCache()
    }

    fun deleteSongs(songs: Collection<Song>) {
        if (songs.isEmpty()) return
        viewModelScope.launch {
            repository.deleteSongs(songs)
        }
    }

    private fun String.toFolderFilterList(): List<String> {
        return split('\n', ';', '；')
            .map { it.trim() }
            .filter { it.isNotBlank() }
    }
}

private data class ArtistAccumulator(
    val name: String,
    var songCount: Int = 0
)
