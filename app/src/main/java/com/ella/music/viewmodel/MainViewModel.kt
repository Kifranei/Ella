package com.ella.music.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.ella.music.data.SettingsManager
import com.ella.music.data.PlaybackStatsStore
import com.ella.music.data.SongPlaybackStats
import com.ella.music.data.matchesArtistName
import com.ella.music.data.model.Album
import com.ella.music.data.model.Artist
import com.ella.music.data.model.AudioInfo
import com.ella.music.data.model.Song
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

    private val _selectedTab = MutableStateFlow(0)
    val selectedTab: StateFlow<Int> = _selectedTab.asStateFlow()
    private var scanJob: Job? = null

    fun selectTab(index: Int) {
        _selectedTab.value = index
    }

    fun scanMusic() {
        if (scanJob?.isActive == true || isScanning.value) return
        scanJob = viewModelScope.launch {
            val minDuration = settingsManager.minDurationSec.first() * 1000L
            repository.scanMusic(minDuration)
        }
    }

    fun scanMusicIfAutoEnabled() {
        if (scanJob?.isActive == true || isScanning.value) return
        scanJob = viewModelScope.launch {
            if (!settingsManager.autoScan.first()) return@launch
            val minDuration = settingsManager.minDurationSec.first() * 1000L
            repository.scanMusic(minDuration)
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
        return currentSongs
            .flatMap { splitArtistNames(it.artist) }
            .distinctBy { it.lowercase() }
            .map { artist ->
                Artist(
                    name = artist,
                    songCount = currentSongs.count { it.artist.matchesArtistName(artist) },
                    albumCount = currentAlbums.count { album ->
                        album.artist.matchesArtistName(artist) ||
                            currentSongs.any { it.albumId == album.id && it.artist.matchesArtistName(artist) }
                    }
                )
            }
            .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name })
    }

    fun getSongsForArtist(artistName: String): List<Song> {
        return songs.value.filter { it.artist.matchesArtistName(artistName) }
    }

    fun getAlbumsForArtist(artistName: String): List<Album> {
        val artistSongs = getSongsForArtist(artistName)
        val artistAlbumIds = artistSongs.map { it.albumId }.toSet()
        return albums.value
            .filter { it.id in artistAlbumIds || it.artist.matchesArtistName(artistName) }
            .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name })
    }

    fun getAlbumArtUri(albumId: Long) = repository.getAlbumArtUri(albumId)

    fun getCoverArtBitmap(song: Song) = repository.getCoverArtBitmap(song)

    fun getReplayGain(song: Song): Float? {
        return repository.getReplayGain(song)
    }

    fun getAudioInfo(song: Song): AudioInfo {
        return repository.getAudioInfo(song)
    }

    fun deleteSongs(songs: Collection<Song>) {
        if (songs.isEmpty()) return
        viewModelScope.launch {
            repository.deleteSongs(songs)
        }
    }
}
