package com.ella.music.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.ella.music.data.SettingsManager
import com.ella.music.data.model.Album
import com.ella.music.data.model.Song
import com.ella.music.data.repository.MusicRepository
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

    val songs: StateFlow<List<Song>> = repository.songs
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val albums: StateFlow<List<Album>> = repository.albums
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val isScanning: StateFlow<Boolean> = repository.isScanning

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

    fun loadCachedLibrary() {
        viewModelScope.launch {
            repository.loadCachedLibrary()
        }
    }

    fun getSongsForAlbum(albumId: Long): List<Song> {
        return repository.getSongsForAlbum(albumId)
    }

    fun getAlbumArtUri(albumId: Long) = repository.getAlbumArtUri(albumId)

    fun getCoverArtBitmap(song: Song) = repository.getCoverArtBitmap(song)

    fun getReplayGain(song: Song): Float? {
        return repository.getReplayGain(song)
    }
}
