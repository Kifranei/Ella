package com.ella.music.viewmodel

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.ella.music.data.PlaylistBatchImportResult
import com.ella.music.data.PlaylistBatchExportResult
import com.ella.music.data.PlaylistExportResult
import com.ella.music.data.PlaylistExportFormat
import com.ella.music.data.PlaylistImportResult
import com.ella.music.data.PlaylistImportMode
import com.ella.music.data.PlaylistStore
import com.ella.music.data.SettingsManager
import com.ella.music.data.PlaybackHistoryEntry
import com.ella.music.data.PlaybackStatsStore
import com.ella.music.data.SongPlaybackStats
import com.ella.music.data.matchesArtistName
import com.ella.music.data.model.Album
import com.ella.music.data.model.Artist
import com.ella.music.data.model.AudioInfo
import com.ella.music.data.model.Song
import com.ella.music.data.model.SongTagInfo
import com.ella.music.data.metadata.AudioTagInfo
import com.ella.music.data.metadata.AudioCoverInfo
import com.ella.music.data.model.UserPlaylist
import com.ella.music.data.model.albumIdentityId
import com.ella.music.data.repository.CoverUsage
import com.ella.music.data.repository.MusicScanSummary
import com.ella.music.data.repository.MusicRepository
import com.ella.music.data.tagIdentityKey
import com.ella.music.ui.analytics.prewarmLibraryAnalysisCache
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.Job
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainViewModel(application: Application) : AndroidViewModel(application) {

    val repository = MusicRepository.getInstance(application)
    val settingsManager = SettingsManager.getInstance(application)
    private val playlistStore = PlaylistStore.getInstance(application)
    private val playbackStatsStore = PlaybackStatsStore.getInstance(application)
    private val aiCoordinator = MainViewModelAiCoordinator(getApplication(), settingsManager, repository)
    private val neteaseLinkResolver = MainNeteaseLinkResolver(
        repository = repository,
        songsForArtist = ::getSongsForArtist,
        songsForAlbum = ::getSongsForAlbum
    )

    val songs: StateFlow<List<Song>> = repository.songs
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    private val playlistCoordinator = MainViewModelPlaylistCoordinator(
        playlistStore = playlistStore,
        settingsManager = settingsManager,
        scope = viewModelScope,
        currentSongs = { songs.value }
    )

    val albums: StateFlow<List<Album>> = repository.albums
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val isScanning: StateFlow<Boolean> = repository.isScanning
    val scanProgress: StateFlow<Int> = repository.scanProgress
    val scanSummaryEvents = repository.scanSummaryEvents
    val playbackStats: StateFlow<List<SongPlaybackStats>> = playbackStatsStore.stats
    val playbackHistory: StateFlow<List<PlaybackHistoryEntry>> = playbackStatsStore.history
    val dailyListenMs: StateFlow<Map<String, Long>> = playbackStatsStore.dailyListenMs
    val playlists: StateFlow<List<UserPlaylist>> = playlistStore.playlists
    private val _libraryCacheLoaded = MutableStateFlow(false)
    val libraryCacheLoaded: StateFlow<Boolean> = _libraryCacheLoaded.asStateFlow()
    private val _ratingRevision = MutableStateFlow(0)
    val ratingRevision: StateFlow<Int> = _ratingRevision.asStateFlow()

    private val _selectedTab = MutableStateFlow(0)
    val selectedTab: StateFlow<Int> = _selectedTab.asStateFlow()
    private var scanJob: Job? = null
    private var autoScanRequested = false

    init {
        viewModelScope.launchNameSplitConfigObservers(settingsManager)
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
        repository.startScanning()
        val completedSummary = try {
            val minDuration = settingsManager.minDurationSec.first() * 1000L
            val includeFolders = settingsManager.scanIncludeFolders.first().toFolderFilterList()
            val excludeFolders = settingsManager.scanExcludeFolders.first().toFolderFilterList()
            val useAndroidMediaLibrary = settingsManager.useAndroidMediaLibrary.first()
            var summary = repository.scanMusic(
                minDuration,
                if (useAndroidMediaLibrary) emptyList() else includeFolders.ifEmpty { listOf("__ella_no_custom_folder__") },
                excludeFolders,
                fullRescan = fullRescan,
                deepRescan = deepRescan
            )
            if (summary.total == 0 && useAndroidMediaLibrary && includeFolders.isNotEmpty()) {
                summary = repository.scanMusic(
                    minDuration,
                    includeFolders,
                    excludeFolders,
                    fullRescan = fullRescan,
                    deepRescan = deepRescan
                )
            }
            val usbFolderUris = settingsManager.usbFolderUris.first()
                .split('\n')
                .map { it.trim() }
                .filter { it.isNotBlank() }
            if (usbFolderUris.isNotEmpty()) {
                val uris = usbFolderUris.mapNotNull { runCatching { Uri.parse(it) }.getOrNull() }
                repository.scanUsbFolders(
                    usbUris = uris,
                    minDurationMs = minDuration,
                    deepMetadata = deepRescan
                )
            }
            summary
        } finally {
            repository.finishScanning()
        }
        repository.emitScanSummary(completedSummary)
        preloadLibrarySearchSnapshot()
        withContext(Dispatchers.IO) {
            prewarmLibraryAnalysisCache(getApplication(), songs.value, this@MainViewModel)
        }
    }

    fun loadCachedLibrary() {
        viewModelScope.launch {
            repository.loadCachedLibrary()
            _libraryCacheLoaded.value = true
            preloadLibrarySearchSnapshot()
        }
    }

    fun preloadLibrarySearchSnapshot() {
        val currentSongs = songs.value
        if (currentSongs.isEmpty()) return
        viewModelScope.launch(Dispatchers.IO) {
            // Defer the per-file tag warm-up so the cold-start library open and its
            // cover decoding aren't starved by this disk I/O. Search still works before
            // this completes: the snapshot is built lazily per song on demand.
            delay(2000)
            repository.preloadSongRatings(currentSongs)
            repository.preloadLibrarySearchSnapshot(currentSongs)
        }
    }

    fun getSongsForAlbum(albumId: Long): List<Song> {
        return repository.getSongsForAlbum(albumId)
    }

    fun getArtists(includeAlbumArtists: Boolean = false): List<Artist> {
        return buildArtists(
            songs = songs.value,
            albums = albums.value,
            includeAlbumArtists = includeAlbumArtists
        )
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
        return buildMetadataCategoryItems(songs.value, type)
    }

    fun getMetadataCategoryCount(type: String): Int {
        return countMetadataCategories(songs.value, type)
    }

    fun getMetadataCategoryCounts(types: Collection<String>): Map<String, Int> {
        return countMetadataCategories(songs.value, types)
    }

    fun getSongsForMetadataCategory(type: String, name: String): List<Song> {
        return filterSongsForMetadataCategory(songs.value, type, name)
    }

    fun hasMetadataCategory(type: String, name: String): Boolean {
        return containsMetadataCategory(songs.value, type, name)
    }

    suspend fun getNeteaseArtistUrlForArtist(artistName: String): String? =
        neteaseLinkResolver.artistUrlForArtist(artistName)

    suspend fun getNeteaseAlbumUrlForAlbum(albumId: Long): String? =
        neteaseLinkResolver.albumUrlForAlbum(albumId)

    fun getAlbumArtUri(albumId: Long) = repository.getAlbumArtUri(albumId)

    fun getCoverArtBitmap(song: Song) = repository.getCoverArtBitmap(song, 128, CoverUsage.ListThumbnail)

    fun getCoverArtBitmap(song: Song, maxSize: Int) = repository.getCoverArtBitmap(song, maxSize, CoverUsage.ListThumbnail)

    fun getAlbumCoverArtBitmap(song: Song) = repository.getCoverArtBitmap(song, 512, CoverUsage.AlbumGrid)

    fun getLargeCoverArtBitmap(song: Song) = repository.getCoverArtBitmap(song, 1200, CoverUsage.Player)

    fun getMetadataEditorCoverArtBitmap(song: Song) = repository.getCoverArtBitmap(song, 1600, CoverUsage.Player)

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

    fun getSongRating(song: Song): Int = repository.getSongRating(song)

    suspend fun writeSongRating(song: Song, rating: Int): Result<Song?> {
        val result = repository.writeSongRating(song, rating)
        if (result.isSuccess) {
            _ratingRevision.value += 1
        }
        return result
    }

    suspend fun writeSongCustomTag(song: Song, key: String, value: String): Result<Song?> =
        repository.writeSongCustomTag(song, key, value)

    suspend fun writeSongMetadata(song: Song, tags: AudioTagInfo): Result<Song?> {
        val result = repository.writeSongMetadata(song, tags)
        if (result.isSuccess && tags.rating != null) {
            _ratingRevision.value += 1
        }
        return result
    }

    suspend fun writeSongEmbeddedCover(song: Song, cover: AudioCoverInfo?): Result<Song?> =
        repository.writeSongEmbeddedCover(song, cover)

    fun getFullAudioTagInfo(song: Song): AudioTagInfo? =
        repository.getFullAudioTagInfo(song)

    suspend fun interpretSongWithOpenAi(song: Song): String =
        aiCoordinator.interpretSong(song)

    suspend fun recommendPlaylistWithOpenAi(maxItems: Int = 30): AiPlaylistRecommendationResult =
        aiCoordinator.recommendPlaylist(
            librarySongs = songs.value,
            playbackStats = playbackStats.value,
            playbackHistory = playbackHistory.value,
            maxItems = maxItems
        )

    suspend fun chatWithOpenAiLibraryAssistant(
        message: String,
        conversationHistory: List<Pair<String, String>> = emptyList(),
        maxPlayableItems: Int = 30
    ): AiLibraryChatResult =
        aiCoordinator.chatWithLibrary(
            librarySongs = songs.value,
            playbackStats = playbackStats.value,
            playbackHistory = playbackHistory.value,
            message = message,
            conversationHistory = conversationHistory,
            maxPlayableItems = maxPlayableItems
        )

    fun clearOnlineMetadataCache() {
        repository.clearRemoteMetadataCache()
    }

    suspend fun prefetchWebDavMetadataHeaders(songs: List<Song>, maxItems: Int = 80) {
        repository.prefetchWebDavMetadataHeaders(songs, maxItems)
    }

    suspend fun resolveSongForPlayback(song: Song): Song =
        repository.resolveSongForPlayback(song)

    suspend fun songMatchesSearchSnapshot(song: Song, query: String): Boolean =
        repository.songMatchesSearchSnapshot(song, query)

    suspend fun filterSongsBySearchSnapshot(songs: List<Song>, query: String): List<Song> =
        repository.filterSongsBySearchSnapshot(songs, query)

    fun clearLibrarySnapshotCache() {
        viewModelScope.launch {
            repository.clearLibrarySnapshotCache()
        }
    }

    fun refreshSongAfterExternalEdit(song: Song, onUpdated: (Song?) -> Unit = {}) {
        viewModelScope.launch {
            onUpdated(repository.refreshSongAfterExternalEdit(song))
        }
    }

    fun playlistSongs(playlist: UserPlaylist): List<Song> {
        return playlistCoordinator.playlistSongs(playlist)
    }

    fun createPlaylist(name: String, onCreated: (UserPlaylist?) -> Unit = {}) {
        playlistCoordinator.createPlaylist(name, onCreated)
    }

    fun renamePlaylist(id: String, newName: String, onRenamed: (Boolean) -> Unit = {}) {
        playlistCoordinator.renamePlaylist(id, newName, onRenamed)
    }

    fun deletePlaylist(id: String) {
        playlistCoordinator.deletePlaylist(id)
    }

    fun deletePlaylists(ids: Set<String>) {
        playlistCoordinator.deletePlaylists(ids)
    }

    fun removeSongFromPlaylist(playlistId: String, songKey: String) {
        playlistCoordinator.removeSongFromPlaylist(playlistId, songKey)
    }

    fun removeSongsFromPlaylist(playlistId: String, songKeys: Set<String>) {
        playlistCoordinator.removeSongsFromPlaylist(playlistId, songKeys)
    }

    fun addSongsToPlaylist(playlistId: String, songs: Collection<Song>, appendToEnd: Boolean = false) {
        playlistCoordinator.addSongsToPlaylist(playlistId, songs, appendToEnd)
    }

    fun reorderPlaylistSongs(playlistId: String, orderedKeys: List<String>) {
        playlistCoordinator.reorderPlaylistSongs(playlistId, orderedKeys)
    }

    fun reorderPlaylists(orderedIds: List<String>) {
        playlistCoordinator.reorderPlaylists(orderedIds)
    }

    fun importLocalPlaylist(uri: Uri, onResult: (Result<PlaylistImportResult>) -> Unit) {
        playlistCoordinator.importLocalPlaylist(uri, onResult)
    }

    fun importLocalPlaylists(
        uris: List<Uri>,
        mode: PlaylistImportMode = PlaylistImportMode.MergeKeepExisting,
        onResult: (Result<PlaylistBatchImportResult>) -> Unit
    ) {
        playlistCoordinator.importLocalPlaylists(uris, mode, onResult)
    }

    fun scanLocalPlaylistFiles(
        onResult: (Result<PlaylistBatchImportResult>) -> Unit = {}
    ) {
        playlistCoordinator.scanLocalPlaylistFiles(onResult)
    }

    fun exportLocalPlaylist(
        playlist: UserPlaylist,
        uri: Uri,
        format: PlaylistExportFormat = PlaylistExportFormat.PlainText,
        onResult: (Result<PlaylistExportResult>) -> Unit
    ) {
        playlistCoordinator.exportLocalPlaylist(playlist, uri, format, onResult)
    }

    fun exportLocalPlaylists(
        playlists: List<UserPlaylist>,
        treeUri: Uri,
        format: PlaylistExportFormat = PlaylistExportFormat.PlainText,
        onResult: (Result<PlaylistBatchExportResult>) -> Unit
    ) {
        playlistCoordinator.exportLocalPlaylists(playlists, treeUri, format, onResult)
    }

    fun deleteSongs(songs: Collection<Song>) {
        if (songs.isEmpty()) return
        viewModelScope.launch {
            runCatching { repository.deleteSongs(songs) }
        }
    }

    suspend fun deleteSongsResult(songs: Collection<Song>): Result<Int> {
        if (songs.isEmpty()) return Result.success(0)
        return runCatching { repository.deleteSongs(songs) }
    }

    fun removeSongsFromLibrary(songs: Collection<Song>) {
        if (songs.isEmpty()) return
        viewModelScope.launch {
            repository.removeSongsFromLibrary(songs)
        }
    }

}
