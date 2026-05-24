package com.ella.music.viewmodel

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.ella.music.data.PlaylistBatchImportResult
import com.ella.music.data.PlaylistExportResult
import com.ella.music.data.PlaylistImportResult
import com.ella.music.data.PlaylistImportMode
import com.ella.music.data.PlaylistStore
import com.ella.music.data.SettingsManager
import com.ella.music.data.PlaybackHistoryEntry
import com.ella.music.data.PlaybackStatsStore
import com.ella.music.data.SongPlaybackStats
import com.ella.music.data.NameSplitConfigStore
import com.ella.music.data.decodeNeteaseKey
import com.ella.music.data.matchesArtistName
import com.ella.music.data.neteaseAlbumUrl
import com.ella.music.data.neteaseArtistUrl
import com.ella.music.data.ai.OpenAiSongInterpretationConfig
import com.ella.music.data.ai.OpenAiSongInterpretationInput
import com.ella.music.data.ai.OpenAiSongInterpreter
import com.ella.music.data.detailedAudioInfo
import com.ella.music.data.model.Album
import com.ella.music.data.model.Artist
import com.ella.music.data.model.AudioInfo
import com.ella.music.data.model.Song
import com.ella.music.data.model.SongTagInfo
import com.ella.music.data.model.UserPlaylist
import com.ella.music.data.model.playlistIdentityKey
import com.ella.music.data.model.toSong
import com.ella.music.data.model.albumIdentityId
import com.ella.music.data.parseNameSplitSetting
import com.ella.music.data.repository.MusicRepository
import com.ella.music.data.splitGenreNames
import com.ella.music.data.splitArtistNames
import com.ella.music.data.tagIdentityKey
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.Job
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainViewModel(application: Application) : AndroidViewModel(application) {

    val repository = MusicRepository(application)
    val settingsManager = SettingsManager(application)
    private val playlistStore = PlaylistStore.getInstance(application)
    private val playbackStatsStore = PlaybackStatsStore.getInstance(application)
    private val openAiSongInterpreter = OpenAiSongInterpreter()

    val songs: StateFlow<List<Song>> = repository.songs
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val albums: StateFlow<List<Album>> = repository.albums
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val isScanning: StateFlow<Boolean> = repository.isScanning
    val scanProgress: StateFlow<Int> = repository.scanProgress
    val playbackStats: StateFlow<List<SongPlaybackStats>> = playbackStatsStore.stats
    val playbackHistory: StateFlow<List<PlaybackHistoryEntry>> = playbackStatsStore.history
    val dailyListenMs: StateFlow<Map<String, Long>> = playbackStatsStore.dailyListenMs
    val playlists: StateFlow<List<UserPlaylist>> = playlistStore.playlists
    private val _libraryCacheLoaded = MutableStateFlow(false)
    val libraryCacheLoaded: StateFlow<Boolean> = _libraryCacheLoaded.asStateFlow()

    private val _selectedTab = MutableStateFlow(0)
    val selectedTab: StateFlow<Int> = _selectedTab.asStateFlow()
    private var scanJob: Job? = null
    private var autoScanRequested = false

    init {
        viewModelScope.launch {
            settingsManager.artistSeparators.collect {
                NameSplitConfigStore.artistCustomSeparators = parseNameSplitSetting(it)
            }
        }
        viewModelScope.launch {
            settingsManager.artistProtectedNames.collect {
                NameSplitConfigStore.artistProtectedNames = parseNameSplitSetting(it)
            }
        }
        viewModelScope.launch {
            settingsManager.genreSeparators.collect {
                NameSplitConfigStore.genreCustomSeparators = parseNameSplitSetting(it)
            }
        }
        viewModelScope.launch {
            settingsManager.genreProtectedNames.collect {
                NameSplitConfigStore.genreProtectedNames = parseNameSplitSetting(it)
            }
        }
        viewModelScope.launch {
            settingsManager.tagIgnoreCase.collect {
                NameSplitConfigStore.tagIgnoreCase = it
            }
        }
    }

    fun selectTab(index: Int) {
        _selectedTab.value = index
    }

    fun scanMusic(fullRescan: Boolean = false, deepRescan: Boolean = fullRescan) {
        if (scanJob?.isActive == true || isScanning.value) return
        scanJob = viewModelScope.launch {
            scanFromCurrentSettings(fullRescan = fullRescan, deepRescan = deepRescan)
        }
    }

    fun fullRescanMusic() {
        scanMusic(fullRescan = true, deepRescan = true)
    }

    fun scanMusicIfAutoEnabled() {
        if (autoScanRequested) return
        autoScanRequested = true
        if (scanJob?.isActive == true || isScanning.value) return
        scanJob = viewModelScope.launch {
            if (!settingsManager.autoScan.first()) return@launch
            scanFromCurrentSettings(fullRescan = false, deepRescan = false)
        }
    }

    private suspend fun scanFromCurrentSettings(fullRescan: Boolean = false, deepRescan: Boolean = fullRescan) {
        val minDuration = settingsManager.minDurationSec.first() * 1000L
        val includeFolders = settingsManager.scanIncludeFolders.first().toFolderFilterList()
        val excludeFolders = settingsManager.scanExcludeFolders.first().toFolderFilterList()
        val useAndroidMediaLibrary = settingsManager.useAndroidMediaLibrary.first()
        val count = repository.scanMusic(
            minDuration,
            if (useAndroidMediaLibrary) emptyList() else includeFolders.ifEmpty { listOf("__ella_no_custom_folder__") },
            excludeFolders,
            fullRescan = fullRescan,
            deepRescan = deepRescan
        )
        if (count == 0 && useAndroidMediaLibrary && includeFolders.isNotEmpty()) {
            repository.scanMusic(
                minDuration,
                includeFolders,
                excludeFolders,
                fullRescan = fullRescan,
                deepRescan = deepRescan
            )
        }
    }

    fun loadCachedLibrary() {
        viewModelScope.launch {
            repository.loadCachedLibrary()
            _libraryCacheLoaded.value = true
        }
    }

    fun getSongsForAlbum(albumId: Long): List<Song> {
        return repository.getSongsForAlbum(albumId)
    }

    fun getArtists(includeAlbumArtists: Boolean = false): List<Artist> {
        val currentSongs = songs.value
        val currentAlbums = albums.value
        val counts = linkedMapOf<String, ArtistAccumulator>()
        val albumIdsByArtist = mutableMapOf<String, MutableSet<Long>>()

        currentSongs.forEach { song ->
            splitArtistNames(song.artist).forEach { rawName ->
                val key = rawName.tagIdentityKey()
                val accumulator = counts.getOrPut(key) { ArtistAccumulator(rawName) }
                accumulator.songCount += 1
                albumIdsByArtist.getOrPut(key) { mutableSetOf() } += song.albumIdentityId()
            }
            if (includeAlbumArtists) {
                splitArtistNames(song.albumArtist).forEach { rawName ->
                    val key = rawName.tagIdentityKey()
                    counts.getOrPut(key) { ArtistAccumulator(rawName) }
                    albumIdsByArtist.getOrPut(key) { mutableSetOf() } += song.albumIdentityId()
                }
            }
        }

        if (includeAlbumArtists) {
            currentAlbums.forEach { album ->
                splitArtistNames(album.albumArtist.ifBlank { album.artist }).forEach { rawName ->
                    val key = rawName.tagIdentityKey()
                    counts.getOrPut(key) { ArtistAccumulator(rawName) }
                    if (album.id > 0L) {
                        albumIdsByArtist.getOrPut(key) { mutableSetOf() } += album.id
                    }
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
        return getParticipatedAlbumsForArtist(artistName)
    }

    fun getParticipatedAlbumsForArtist(artistName: String): List<Album> {
        val artistSongs = getSongsForArtist(artistName)
        val artistAlbumIds = artistSongs.map { it.albumIdentityId() }.toSet()
        return albums.value
            .filter { it.id in artistAlbumIds }
            .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name })
    }

    fun getReleaseAlbumsForArtist(artistName: String): List<Album> {
        return albums.value
            .filter { it.albumArtist.isNotBlank() && it.albumArtist.matchesArtistName(artistName) }
            .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name })
    }

    fun hasAlbumArtistTags(): Boolean {
        return songs.value.any { it.albumArtist.isNotBlank() } || albums.value.any { it.albumArtist.isNotBlank() }
    }

    fun getMetadataCategoryItems(type: String): List<MetadataCategoryItem> {
        val groups = linkedMapOf<String, MutableList<Song>>()
        val displayNames = linkedMapOf<String, String>()
        songs.value.forEach { song ->
            song.metadataCategoryNames(type).forEach { name ->
                val key = name.tagIdentityKey()
                displayNames.putIfAbsent(key, name)
                groups.getOrPut(key) { mutableListOf() } += song
            }
        }
        return groups
            .map { (key, items) ->
                MetadataCategoryItem(
                    name = displayNames[key] ?: key,
                    songCount = items.size,
                    albumCount = items.map { it.albumIdentityId() }.distinct().size,
                    duration = items.sumOf { it.duration },
                    dateModified = items.maxOfOrNull { it.dateModified } ?: 0L,
                    coverAlbumIds = items
                        .mapNotNull { it.albumId.takeIf { albumId -> albumId > 0L } }
                        .distinct()
                        .take(3)
                )
            }
            .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name })
    }

    fun getSongsForMetadataCategory(type: String, name: String): List<Song> {
        val target = name.trim()
        if (target.isBlank()) return emptyList()
        return songs.value
            .filter { song -> song.metadataCategoryNames(type).any { it.equals(target, ignoreCase = NameSplitConfigStore.tagIgnoreCase) } }
            .sortedWith(
                compareBy<Song, String>(String.CASE_INSENSITIVE_ORDER) { it.album }
                    .thenBy { if (it.discNumber > 0) it.discNumber else Int.MAX_VALUE }
                    .thenBy { if (it.trackNumber > 0) it.trackNumber else Int.MAX_VALUE }
                    .thenBy(String.CASE_INSENSITIVE_ORDER) { song -> song.title }
            )
    }

    fun hasMetadataCategory(type: String, name: String): Boolean {
        val target = name.trim()
        if (target.isBlank()) return false
        return songs.value.any { song ->
            song.metadataCategoryNames(type).any { it.equals(target, ignoreCase = NameSplitConfigStore.tagIgnoreCase) }
        }
    }

    suspend fun getNeteaseArtistUrlForArtist(artistName: String): String? = withContext(Dispatchers.IO) {
        val targetNames = splitArtistNames(artistName)
            .ifEmpty { listOf(artistName.trim()) }
            .filter { it.isNotBlank() }
        val targetKeys = targetNames.map { it.tagIdentityKey() }.toSet()
        val matchedArtist = getSongsForArtist(artistName).asSequence()
            .take(80)
            .mapNotNull { song -> decodeNeteaseKey(repository.getSongTagInfo(song).neteaseKey) }
            .flatMap { it.artists.asSequence() }
            .firstOrNull { artist ->
                artist.id.isNotBlank() && artist.name.tagIdentityKey() in targetKeys
            }
            ?: getSongsForArtist(artistName).asSequence()
                .take(80)
                .mapNotNull { song -> decodeNeteaseKey(repository.getSongTagInfo(song).neteaseKey) }
                .flatMap { it.artists.asSequence() }
                .firstOrNull { artist ->
                    artist.id.isNotBlank() && targetNames.any { target ->
                        artist.name.equals(target, ignoreCase = true) ||
                            (artist.name.length >= 3 && target.contains(artist.name, ignoreCase = true)) ||
                            (target.length >= 3 && artist.name.contains(target, ignoreCase = true))
                    }
            }
        matchedArtist?.id?.let(::neteaseArtistUrl)
    }

    suspend fun getNeteaseAlbumUrlForAlbum(albumId: Long): String? = withContext(Dispatchers.IO) {
        getSongsForAlbum(albumId).asSequence()
            .take(40)
            .mapNotNull { song -> decodeNeteaseKey(repository.getSongTagInfo(song).neteaseKey) }
            .firstOrNull { it.albumId.isNotBlank() }
            ?.albumId
            ?.let(::neteaseAlbumUrl)
    }

    fun getAlbumArtUri(albumId: Long) = repository.getAlbumArtUri(albumId)

    fun getCoverArtBitmap(song: Song) = repository.getCoverArtBitmap(song, 128)

    fun getReplayGain(song: Song): Float? {
        return repository.getReplayGain(song)
    }

    fun getAudioInfo(song: Song): AudioInfo {
        return repository.getAudioInfo(song)
    }

    fun getSongTagInfo(song: Song): SongTagInfo {
        return repository.getSongTagInfo(song)
    }

    suspend fun getFiveStarSongs(): List<Song> = withContext(Dispatchers.IO) {
        songs.value.filter { repository.getSongRating(it) >= 5 }
    }

    suspend fun interpretSongWithOpenAi(song: Song): String = withContext(Dispatchers.IO) {
        val lyricSourceMode = settingsManager.lyricSourceMode.first()
        val tagInfo = repository.getSongTagInfo(song)
        val audioInfo = runCatching { repository.getAudioInfo(song) }.getOrNull()
        val lyrics = repository.getLyrics(song, lyricSourceMode)
        openAiSongInterpreter.interpret(
            config = OpenAiSongInterpretationConfig(
                apiKey = settingsManager.openAiApiKey.first(),
                baseUrl = settingsManager.openAiBaseUrl.first(),
                model = settingsManager.openAiModel.first()
            ),
            input = OpenAiSongInterpretationInput(
                song = song,
                tagInfo = tagInfo,
                audioInfo = audioInfo,
                audioInfoText = audioInfo?.let { detailedAudioInfo(it) }.orEmpty(),
                lyrics = lyrics
            )
        )
    }

    fun clearOnlineMetadataCache() {
        repository.clearRemoteMetadataCache()
    }

    fun refreshSongAfterExternalEdit(song: Song, onUpdated: (Song?) -> Unit = {}) {
        viewModelScope.launch {
            onUpdated(repository.refreshSongAfterExternalEdit(song))
        }
    }

    fun playlistSongs(playlist: UserPlaylist): List<Song> {
        val libraryByKey = songs.value.associateBy { it.playlistIdentityKey() }
        return playlist.songs.map { item -> libraryByKey[item.key] ?: item.toSong() }
    }

    fun createPlaylist(name: String, onCreated: (UserPlaylist?) -> Unit = {}) {
        viewModelScope.launch {
            onCreated(playlistStore.createPlaylist(name))
        }
    }

    fun deletePlaylist(id: String) {
        viewModelScope.launch { playlistStore.deletePlaylist(id) }
    }

    fun removeSongFromPlaylist(playlistId: String, songKey: String) {
        viewModelScope.launch { playlistStore.removeSongFromPlaylist(playlistId, songKey) }
    }

    fun addSongsToPlaylist(playlistId: String, songs: Collection<Song>) {
        if (songs.isEmpty()) return
        viewModelScope.launch { playlistStore.addSongsToPlaylist(playlistId, songs) }
    }

    fun importLocalPlaylist(uri: Uri, onResult: (Result<PlaylistImportResult>) -> Unit) {
        viewModelScope.launch {
            val result = runCatching { playlistStore.importLocalPlaylist(uri, songs.value) }
            onResult(result)
        }
    }

    fun importLocalPlaylists(
        uris: List<Uri>,
        mode: PlaylistImportMode = PlaylistImportMode.MergeKeepExisting,
        onResult: (Result<PlaylistBatchImportResult>) -> Unit
    ) {
        viewModelScope.launch {
            val result = runCatching { playlistStore.importLocalPlaylists(uris, songs.value, mode) }
            onResult(result)
        }
    }

    fun exportLocalPlaylist(playlist: UserPlaylist, uri: Uri, onResult: (Result<PlaylistExportResult>) -> Unit) {
        viewModelScope.launch {
            val result = runCatching { playlistStore.exportSaltPlayerPlaylist(playlist, uri) }
            onResult(result)
        }
    }

    fun deleteSongs(songs: Collection<Song>) {
        if (songs.isEmpty()) return
        viewModelScope.launch {
            repository.deleteSongs(songs)
        }
    }

    fun removeSongsFromLibrary(songs: Collection<Song>) {
        if (songs.isEmpty()) return
        viewModelScope.launch {
            repository.removeSongsFromLibrary(songs)
        }
    }

    private fun String.toFolderFilterList(): List<String> {
        return split('\n', ';', '；')
            .map { it.trim() }
            .filter { it.isNotBlank() }
    }
}

data class MetadataCategoryItem(
    val name: String,
    val songCount: Int,
    val albumCount: Int,
    val duration: Long,
    val dateModified: Long = 0L,
    val coverAlbumIds: List<Long> = emptyList()
)

private data class ArtistAccumulator(
    val name: String,
    var songCount: Int = 0
)

private fun Song.metadataCategoryNames(type: String): List<String> {
    return when (type) {
        "genre" -> splitGenreNames(genre)
        "year" -> listOfNotNull(year.extractYear())
        "composer" -> splitArtistNames(composer)
        "lyricist" -> splitArtistNames(lyricist)
        "folder" -> listOfNotNull(parentFolderPath())
        else -> emptyList()
    }
}

private fun String.extractYear(): String? {
    return Regex("""\d{4}""").find(this)?.value
}

private fun Song.parentFolderPath(): String? {
    val normalized = path.replace('\\', '/')
    return normalized.substringBeforeLast('/', missingDelimiterValue = "")
        .trim()
        .ifBlank { null }
}
