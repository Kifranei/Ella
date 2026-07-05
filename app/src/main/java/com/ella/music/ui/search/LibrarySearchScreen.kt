package com.ella.music.ui.search

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.ella.music.R
import com.ella.music.data.SettingsManager
import com.ella.music.data.model.Album
import com.ella.music.data.model.Artist
import com.ella.music.data.model.Song
import com.ella.music.data.model.UserPlaylist
import com.ella.music.data.model.albumIdentityId
import com.ella.music.data.model.matchesFullTagSearch
import com.ella.music.data.tagIdentityKey
import com.ella.music.ui.components.AddToPlaylistSheet
import com.ella.music.ui.components.EllaCenteredLoadingIndicator
import com.ella.music.ui.components.EllaSearchBar
import com.ella.music.ui.components.ConfirmDangerDialog
import com.ella.music.ui.components.EllaMiuixBottomSheet
import com.ella.music.ui.components.EllaMiuixMenuItem
import com.ella.music.ui.components.rememberSongDeleteRequester
import com.ella.music.ui.components.SongItem
import com.ella.music.ui.components.SongMoreActionHost
import com.ella.music.ui.components.SongSelectionActionRow
import com.ella.music.ui.components.createPlaylistOrShowDuplicateToast
import com.ella.music.ui.components.ellaPageBackground
import com.ella.music.ui.components.requestPinnedEllaShortcut
import com.ella.music.ui.components.shareLocalSongs
import com.ella.music.ui.folder.normalizeFolderPath
import com.ella.music.ui.folder.toFolderSettingList
import com.ella.music.ui.navigation.Screen
import com.ella.music.ui.playlist.CreatePlaylistDialog
import com.ella.music.viewmodel.MainViewModel
import com.ella.music.viewmodel.MetadataCategoryItem
import com.ella.music.viewmodel.PlayerViewModel
import java.util.Locale
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.icon.extended.Add
import top.yukonga.miuix.kmp.icon.extended.Close
import top.yukonga.miuix.kmp.icon.extended.Delete
import top.yukonga.miuix.kmp.icon.extended.Play
import top.yukonga.miuix.kmp.icon.extended.Playlist
import top.yukonga.miuix.kmp.icon.extended.Share
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun LibrarySearchScreen(
    mainViewModel: MainViewModel,
    playerViewModel: PlayerViewModel,
    initialFilterType: String? = null,
    initialQuery: String? = null,
    autoFocusSearch: Boolean = false,
    showBackButton: Boolean = true,
    onBack: () -> Unit,
    onNavigateToAlbum: (Long) -> Unit,
    onNavigateToArtist: (String) -> Unit,
    onNavigateToPlaylist: (String) -> Unit,
    onNavigateToMetadataCategory: (String, String) -> Unit,
    onNavigateToPlayer: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val settingsManager = mainViewModel.settingsManager
    val songs by mainViewModel.songs.collectAsState()
    val albums by mainViewModel.albums.collectAsState()
    val playlists by mainViewModel.playlists.collectAsState()
    val libraryCacheLoaded by mainViewModel.libraryCacheLoaded.collectAsState()
    val currentSong by playerViewModel.currentSong.collectAsState()
    val requestDeleteSongs = rememberSongDeleteRequester(mainViewModel)
    val lyricSourceMode by settingsManager.lyricSourceMode.collectAsState(initial = SettingsManager.LYRIC_SOURCE_AUTO)
    val showPlayNextInLists by settingsManager.showPlayNextInLists.collectAsState(initial = false)
    val showAlbumArtists by settingsManager.showAlbumArtists.collectAsState(initial = true)
    val fullTagSearchEnabled by settingsManager.fullTagSearchEnabled.collectAsState(initial = true)
    val scanExcludeFolders by settingsManager.scanExcludeFolders.collectAsState(initial = "")
    val blockedFolders = remember(scanExcludeFolders) { scanExcludeFolders.toFolderSettingList() }
    var query by rememberSaveable(initialQuery) { mutableStateOf(initialQuery.orEmpty()) }
    // Persist the selected filter across navigation (e.g. opening an album/playlist detail and
    // coming back) so the user doesn't get bounced back to the "All" tab. Uses a String-backed
    // saver because SearchFilter is an enum; we store its name and map back on restore.
    var filter by rememberSaveable(initialFilterType, stateSaver = SearchFilterSaver) {
        mutableStateOf(SearchFilter.fromRouteType(initialFilterType))
    }
    var duplicatesOnly by remember { mutableStateOf(false) }
    var actionSong by remember { mutableStateOf<Song?>(null) }
    var actionTarget by remember { mutableStateOf<SearchActionTarget?>(null) }
    var playlistPickerSongs by remember { mutableStateOf<List<Song>?>(null) }
    var createPlaylistSongs by remember { mutableStateOf<List<Song>?>(null) }
    var pendingDeleteSongs by remember { mutableStateOf<List<Song>>(emptyList()) }
    var history by remember { mutableStateOf(loadSearchHistory(context)) }
    var showClearHistoryConfirm by remember { mutableStateOf(false) }
    var selectionMode by remember { mutableStateOf(false) }
    var selectedSongKeys by remember { mutableStateOf<Set<String>>(emptySet()) }
    var rangeAnchorSongKey by remember { mutableStateOf<String?>(null) }
    var rangeTargetSongKey by remember { mutableStateOf<String?>(null) }

    val trimmedQuery = query.trim()
    val songSelectionAvailable = filter in listOf(SearchFilter.Songs, SearchFilter.Lyrics)
    val duplicateSongs = remember(songs) { songs.duplicateTitleAlbumSongs() }
    val duplicatesOnlyActive = duplicatesOnly && filter.supportsDuplicateFilter
    val songSearchSource = remember(songs, duplicateSongs, duplicatesOnlyActive) {
        if (duplicatesOnlyActive) duplicateSongs else songs
    }
    val immediateSongResults = remember(songSearchSource, trimmedQuery, filter, duplicatesOnlyActive) {
        when {
            !filter.acceptsSongResults || filter == SearchFilter.Lyrics -> emptyList()
            trimmedQuery.isBlank() && !duplicatesOnlyActive -> emptyList()
            trimmedQuery.isBlank() -> songSearchSource
                .asSequence()
                .map { SongSearchResult(it, matches = it.directSearchMatches(trimmedQuery)) }
                .toList()
            else -> songSearchSource
                .asSequence()
                .filter { it.matchesFullTagSearch(trimmedQuery) }
                .map { SongSearchResult(it, matches = it.directSearchMatches(trimmedQuery)) }
                .toList()
        }
    }
    val cachedSongResults = remember(context, songs, trimmedQuery, filter, duplicatesOnlyActive, fullTagSearchEnabled) {
        if (duplicatesOnlyActive || !fullTagSearchEnabled) {
            emptyList()
        } else {
            loadCachedSongSearchResults(context, songs, trimmedQuery, filter)
        }
    }
    val songResults by produceState(
        initialValue = cachedSongResults.ifEmpty { immediateSongResults },
        songSearchSource,
        trimmedQuery,
        filter,
        duplicateSongs,
        duplicatesOnlyActive,
        cachedSongResults,
        lyricSourceMode,
        fullTagSearchEnabled
    ) {
        val initialResults = cachedSongResults.ifEmpty { immediateSongResults }
        value = initialResults
        if (!filter.acceptsSongResults || trimmedQuery.isBlank()) {
            return@produceState
        }
        if (!fullTagSearchEnabled && filter == SearchFilter.Lyrics) {
            return@produceState
        }
        if (filter == SearchFilter.Lyrics) {
            val current = mutableListOf<SongSearchResult>()
            for (song in songSearchSource) {
                val snippet = mainViewModel.repository
                    .getLyrics(song, lyricSourceMode)
                    .firstMatchingLyricSnippet(trimmedQuery)
                    ?: continue
                current += SongSearchResult(song = song, lyricSnippet = snippet)
                value = current.toList()
            }
            return@produceState
        }
        if (duplicatesOnlyActive) {
            return@produceState
        }
        val current = initialResults.map { result ->
            if (result.lyricSnippet == null && result.matches.isEmpty()) {
                val tagInfo = if (fullTagSearchEnabled) mainViewModel.getSongTagInfo(result.song) else null
                result.copy(
                    matches = result.song.directSearchMatches(
                        trimmedQuery,
                        tagInfo = tagInfo,
                        includeSnapshotTag = fullTagSearchEnabled
                    )
                )
            } else {
                result
            }
        }.toMutableList()
        if (current != initialResults) value = current.toList()
        val seenKeys = current.map { it.song.searchIdentityKey() }.toMutableSet()
        val remainingSongs = songSearchSource.filter { it.searchIdentityKey() !in seenKeys }
        val snapshotMatches = mainViewModel
            .filterSongsBySearchSnapshot(remainingSongs, trimmedQuery)
            .asSequence()
            .filter { it.searchIdentityKey() !in seenKeys }
            .toList()
        snapshotMatches.forEach { song ->
            val tagInfo = if (fullTagSearchEnabled) mainViewModel.getSongTagInfo(song) else null
            current += SongSearchResult(
                song = song,
                matches = song.directSearchMatches(
                    trimmedQuery,
                    tagInfo = tagInfo,
                    includeSnapshotTag = fullTagSearchEnabled
                )
            )
            seenKeys += song.searchIdentityKey()
        }
        if (snapshotMatches.isNotEmpty()) value = current.toList()
        if (!fullTagSearchEnabled) {
            return@produceState
        }
        for (song in remainingSongs) {
            if (song.searchIdentityKey() in seenKeys) continue
            val snippet = mainViewModel.repository
                .getLyrics(song, lyricSourceMode)
                .firstMatchingLyricSnippet(trimmedQuery)
                ?: continue
            current += SongSearchResult(song = song, lyricSnippet = snippet)
            seenKeys += song.searchIdentityKey()
            value = current.toList()
        }
        saveCachedSongSearchResults(context, trimmedQuery, filter, current)
    }
    val albumResults = remember(albums, trimmedQuery, duplicatesOnlyActive) {
        if (duplicatesOnlyActive || trimmedQuery.isBlank()) emptyList()
        else albums.filter { it.matchesLibrarySearch(trimmedQuery) }
    }
    val artistResults = remember(songs, trimmedQuery, duplicatesOnlyActive, showAlbumArtists) {
        if (duplicatesOnlyActive || trimmedQuery.isBlank()) {
            emptyList()
        } else {
            songs.asSequence()
                .flatMap { song ->
                    // When "show album artists" is on, merge each song's album artist(s) into the
                    // artist tab (deduped per song so a track whose artist == album artist isn't
                    // double-counted). Mirrors ArtistListScreen so search matches the library view.
                    val names = if (showAlbumArtists) {
                        (com.ella.music.data.splitArtistNames(song.artist) +
                            com.ella.music.data.splitArtistNames(song.albumArtist))
                            .distinctBy { it.tagIdentityKey() }
                    } else {
                        com.ella.music.data.splitArtistNames(song.artist)
                    }
                    names.asSequence()
                        .filter { it.isNotBlank() && it.contains(trimmedQuery, ignoreCase = true) }
                        .map { it to song }
                }
                // Group by identity key, not the raw string, so the same person written slightly
                // differently as a track artist vs an album artist (spacing / full-width / case)
                // collapses into ONE artist entry — the same de-dup the "show album artists" merge
                // uses on the artist list — instead of showing them as two separate results.
                .groupBy { it.first.tagIdentityKey() }
                .values
                .map { pairs ->
                    val name = pairs.first().first
                    val artistSongs = pairs.map { it.second }.distinctBy { it.id }
                    ArtistSearchResult(
                        artist = Artist(
                            name = name,
                            songCount = artistSongs.size,
                            albumCount = artistSongs.map { it.album }.distinct().size
                        ),
                        representativeSong = artistSongs.firstOrNull(),
                        participatedAlbumCount = artistSongs.map { it.albumIdentityId() }.distinct().size
                    )
                }
                .sortedBy { it.artist.name.lowercase() }
        }
    }
    val playlistResults = remember(playlists, trimmedQuery, duplicatesOnlyActive) {
        if (duplicatesOnlyActive || trimmedQuery.isBlank()) {
            emptyList()
        } else {
            playlists.filter { playlist ->
                playlist.name.contains(trimmedQuery, ignoreCase = true) ||
                    playlist.songs.any { song ->
                        song.title.contains(trimmedQuery, ignoreCase = true) ||
                            song.artist.contains(trimmedQuery, ignoreCase = true) ||
                            song.album.contains(trimmedQuery, ignoreCase = true)
                    }
            }
        }
    }
    val allCategoryTypes = remember { listOf("folder", "composer", "lyricist", "genre", "year") }
    val allCategoryResultsByType = remember(songs, trimmedQuery, duplicatesOnlyActive) {
        if (duplicatesOnlyActive || trimmedQuery.isBlank()) {
            emptyMap()
        } else {
            allCategoryTypes.associateWith { type ->
                mainViewModel.getMetadataCategoryItems(type)
                    .filter { it.name.contains(trimmedQuery, ignoreCase = true) }
            }
        }
    }
    val categoryResultsByType = remember(filter, allCategoryResultsByType) {
        when (filter) {
            SearchFilter.All -> allCategoryResultsByType
            SearchFilter.Folders -> allCategoryResultsByType.filterKeys { it == "folder" }
            SearchFilter.Composers -> allCategoryResultsByType.filterKeys { it == "composer" }
            SearchFilter.Lyricists -> allCategoryResultsByType.filterKeys { it == "lyricist" }
            SearchFilter.Genres -> allCategoryResultsByType.filterKeys { it == "genre" }
            SearchFilter.Years -> allCategoryResultsByType.filterKeys { it == "year" }
            else -> emptyMap()
        }
    }
    val categoryResultsCount = remember(categoryResultsByType) { categoryResultsByType.values.sumOf { it.size } }
    val visibleAlbumCount = if (filter in listOf(SearchFilter.All, SearchFilter.Albums)) albumResults.size else 0
    val visibleArtistCount = if (filter in listOf(SearchFilter.All, SearchFilter.Artists)) artistResults.size else 0
    val visiblePlaylistCount = if (filter in listOf(SearchFilter.All, SearchFilter.Playlists)) playlistResults.size else 0
    val visibleResultCount = songResults.size + visibleAlbumCount + visibleArtistCount + visiblePlaylistCount + categoryResultsCount

    val songResultGroups = remember(songResults, filter) {
        songResults
            .flatMap { it.toSearchGroupEntries(filter) }
            .groupBy({ it.first }, { it.second })
            .map { it.key to it.value }
    }

    LaunchedEffect(filter, trimmedQuery) {
        selectionMode = false
        selectedSongKeys = emptySet()
        rangeAnchorSongKey = null
        rangeTargetSongKey = null
    }

    val displayedSongIndexByKey = remember(songResults) {
        buildMap {
            songResults.forEachIndexed { index, result -> put(result.song.searchIdentityKey(), index) }
        }
    }
    val rangeSelectionAvailable = remember(
        displayedSongIndexByKey,
        selectedSongKeys,
        rangeAnchorSongKey,
        rangeTargetSongKey
    ) {
        val anchor = rangeAnchorSongKey
        val target = rangeTargetSongKey
        anchor != null &&
            target != null &&
            anchor != target &&
            anchor in selectedSongKeys &&
            target in selectedSongKeys &&
            anchor in displayedSongIndexByKey &&
            target in displayedSongIndexByKey
    }

    fun updateRangeAnchorsForManualSelection(songKey: String, selectedNow: Boolean) {
        if (selectedNow) {
            when {
                rangeAnchorSongKey == null -> rangeAnchorSongKey = songKey
                rangeAnchorSongKey == songKey -> Unit
                else -> rangeTargetSongKey = songKey
            }
        } else {
            if (rangeTargetSongKey == songKey) rangeTargetSongKey = null
            if (rangeAnchorSongKey == songKey) {
                rangeAnchorSongKey = rangeTargetSongKey ?: selectedSongKeys.firstOrNull { it != songKey }
                rangeTargetSongKey = null
            }
        }
    }

    fun toggleSongSelection(song: Song) {
        val key = song.searchIdentityKey()
        val selecting = key !in selectedSongKeys
        selectedSongKeys = if (selecting) {
            selectedSongKeys + key
        } else {
            selectedSongKeys - key
        }
        updateRangeAnchorsForManualSelection(key, selecting)
    }

    fun toggleSelectAllSongResults() {
        val allKeys = songResults.mapTo(mutableSetOf()) { it.song.searchIdentityKey() }
        selectedSongKeys = if (allKeys.isNotEmpty() && allKeys.all { it in selectedSongKeys }) {
            rangeAnchorSongKey = null
            rangeTargetSongKey = null
            emptySet()
        } else {
            rangeAnchorSongKey = songResults.firstOrNull()?.song?.searchIdentityKey()
            rangeTargetSongKey = songResults.lastOrNull()?.song?.searchIdentityKey()
            allKeys
        }
    }

    fun applyRangeSelection() {
        val anchor = rangeAnchorSongKey ?: return
        val target = rangeTargetSongKey ?: return
        val anchorIndex = displayedSongIndexByKey[anchor] ?: return
        val targetIndex = displayedSongIndexByKey[target] ?: return
        if (anchorIndex == targetIndex) return
        val bounds = if (anchorIndex < targetIndex) anchorIndex..targetIndex else targetIndex..anchorIndex
        selectedSongKeys = selectedSongKeys + bounds.map { songResults[it].song.searchIdentityKey() }
        rangeAnchorSongKey = target
        rangeTargetSongKey = null
    }

    fun selectedSearchSongs(): List<Song> =
        songResults
            .map { it.song }
            .distinctBy { it.searchIdentityKey() }
            .filter { it.searchIdentityKey() in selectedSongKeys }

    fun selectedOrToast(): List<Song> {
        val selected = selectedSearchSongs()
        if (selected.isEmpty()) {
            Toast.makeText(context, R.string.library_select_songs_first, Toast.LENGTH_SHORT).show()
        }
        return selected
    }

    fun finishSelectionMode() {
        selectionMode = false
        selectedSongKeys = emptySet()
        rangeAnchorSongKey = null
        rangeTargetSongKey = null
    }

    fun commitSearch(text: String = query) {
        val value = text.trim()
        if (value.isBlank()) return
        history = saveSearchHistory(context, value)
    }

    fun songsForActionTarget(target: SearchActionTarget): List<Song> = when (target) {
        is SearchActionTarget.AlbumTarget -> mainViewModel.getSongsForAlbum(target.album.id)
        is SearchActionTarget.ArtistTarget -> mainViewModel.getSongsForArtist(
            artistName = target.artist.name,
            includeAlbumArtist = showAlbumArtists
        )
        is SearchActionTarget.PlaylistTarget -> mainViewModel.playlistSongs(target.playlist)
        is SearchActionTarget.CategoryTarget -> mainViewModel.getSongsForMetadataCategory(target.type, target.item.name)
    }

    fun shortcutRouteForActionTarget(target: SearchActionTarget): String = when (target) {
        is SearchActionTarget.AlbumTarget -> Screen.AlbumDetail.createRoute(target.album.id)
        is SearchActionTarget.ArtistTarget -> Screen.ArtistDetail.createRoute(target.artist.name)
        is SearchActionTarget.PlaylistTarget -> Screen.PlaylistDetail.createRoute(target.playlist.id)
        is SearchActionTarget.CategoryTarget -> Screen.MetadataCategoryDetail.createRoute(target.type, target.item.name)
    }

    fun shortcutIdForActionTarget(target: SearchActionTarget): String = when (target) {
        is SearchActionTarget.AlbumTarget -> "album_${target.album.id}"
        is SearchActionTarget.ArtistTarget -> "artist_${target.artist.name.tagIdentityKey()}"
        is SearchActionTarget.PlaylistTarget -> "playlist_${target.playlist.id}"
        is SearchActionTarget.CategoryTarget -> "category_${target.type}_${target.item.name.tagIdentityKey()}"
    }

    LaunchedEffect(initialQuery) {
        initialQuery?.trim()?.takeIf { it.isNotBlank() }?.let { value ->
            history = saveSearchHistory(context, value)
        }
    }

    BackHandler {
        if (selectionMode) {
            selectionMode = false
            selectedSongKeys = emptySet()
        } else {
            onBack()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(ellaPageBackground())
            .windowInsetsPadding(WindowInsets.statusBars)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (showBackButton) {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = MiuixIcons.Regular.Back,
                        contentDescription = stringResource(R.string.common_back),
                        tint = MiuixTheme.colorScheme.onSurface,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
            EllaSearchBar(
                query = query,
                onQueryChange = {
                    query = it
                },
                onSearch = { commitSearch() },
                placeholder = stringResource(R.string.library_search_page_placeholder),
                modifier = Modifier.weight(1f),
                // DeepLink 带 keyword 时强制不弹输入法；DeepLink 无 keyword 时强制弹；
                // 普通入口（无 focus 标记且无 keyword）回退用户的"自动弹出输入法"设置。
                autoFocus = when {
                    autoFocusSearch -> true
                    !initialQuery.isNullOrBlank() -> false
                    else -> null
                }
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            SearchFilter.entries.forEach { item ->
                val baseLabel = stringResource(item.labelRes())
                val itemCount = when (item) {
                    SearchFilter.All -> songResults.size + albumResults.size + artistResults.size + playlistResults.size + allCategoryResultsByType.values.sumOf { it.size }
                    SearchFilter.Songs -> if (filter in listOf(SearchFilter.All, SearchFilter.Songs) || duplicatesOnlyActive) songResults.size else 0
                    SearchFilter.Lyrics -> if (filter == SearchFilter.Lyrics) songResults.size else 0
                    SearchFilter.Albums -> albumResults.size
                    SearchFilter.Artists -> artistResults.size
                    SearchFilter.Playlists -> playlistResults.size
                    SearchFilter.Folders -> allCategoryResultsByType["folder"].orEmpty().size
                    SearchFilter.Composers -> allCategoryResultsByType["composer"].orEmpty().size
                    SearchFilter.Lyricists -> allCategoryResultsByType["lyricist"].orEmpty().size
                    SearchFilter.Genres -> allCategoryResultsByType["genre"].orEmpty().size
                    SearchFilter.Years -> allCategoryResultsByType["year"].orEmpty().size
                }
                SearchPill(
                    text = if ((trimmedQuery.isNotBlank() || duplicatesOnlyActive) && itemCount > 0) "$baseLabel ($itemCount)" else baseLabel,
                    selected = filter == item,
                    onClick = {
                        filter = item
                        if (!item.supportsDuplicateFilter) duplicatesOnly = false
                    }
                )
            }
        }

        if (filter.supportsDuplicateFilter) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp)
            ) {
                SearchPill(
                    text = stringResource(R.string.library_search_duplicates),
                    selected = duplicatesOnly,
                    onClick = { duplicatesOnly = !duplicatesOnly }
                )
            }
        }

        if (selectionMode) {
            SongSelectionActionRow(
                selectedCount = selectedSongKeys.size,
                totalCount = songResults.size,
                rangeEnabled = rangeSelectionAvailable,
                allSelected = songResults.isNotEmpty() && songResults.all { it.song.searchIdentityKey() in selectedSongKeys },
                onRangeSelect = ::applyRangeSelection,
                onSelectAll = ::toggleSelectAllSongResults,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 2.dp),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = {
                    val selected = selectedOrToast()
                    if (selected.isNotEmpty()) {
                        playerViewModel.playNext(selected)
                        Toast.makeText(context, R.string.song_more_added_to_play_next, Toast.LENGTH_SHORT).show()
                        finishSelectionMode()
                    }
                }) {
                    Icon(
                        imageVector = MiuixIcons.Regular.Play,
                        contentDescription = stringResource(R.string.song_more_play_next),
                        tint = MiuixTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                }
                IconButton(onClick = {
                    val selected = selectedOrToast()
                    if (selected.isNotEmpty()) {
                        playlistPickerSongs = selected
                    }
                }) {
                    Icon(
                        imageVector = MiuixIcons.Regular.Add,
                        contentDescription = stringResource(R.string.player_add_to_playlist),
                        tint = MiuixTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                }
                IconButton(onClick = {
                    val selected = selectedOrToast()
                    if (selected.isNotEmpty()) {
                        playerViewModel.addToPlaylist(selected)
                        Toast.makeText(context, R.string.song_more_added_to_queue, Toast.LENGTH_SHORT).show()
                        finishSelectionMode()
                    }
                }) {
                    Icon(
                        imageVector = MiuixIcons.Regular.Playlist,
                        contentDescription = stringResource(R.string.common_add_to_queue),
                        tint = MiuixTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                }
                IconButton(onClick = {
                    val selected = selectedOrToast()
                    if (selected.isNotEmpty()) shareLocalSongs(context, selected)
                }) {
                    Icon(
                        imageVector = MiuixIcons.Regular.Share,
                        contentDescription = stringResource(R.string.common_share),
                        tint = MiuixTheme.colorScheme.onSurface,
                        modifier = Modifier.size(24.dp)
                    )
                }
                IconButton(onClick = {
                    val selected = selectedOrToast()
                    if (selected.isNotEmpty()) pendingDeleteSongs = selected
                }) {
                    Icon(
                        imageVector = MiuixIcons.Regular.Delete,
                        contentDescription = stringResource(R.string.common_delete),
                        tint = androidx.compose.ui.graphics.Color(0xFFE5484D),
                        modifier = Modifier.size(24.dp)
                    )
                }
                IconButton(onClick = ::finishSelectionMode) {
                    Icon(
                        imageVector = MiuixIcons.Regular.Close,
                        contentDescription = stringResource(R.string.common_exit_selection),
                        tint = MiuixTheme.colorScheme.onSurface,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        }

        if (songs.isEmpty() && !libraryCacheLoaded) {
            EllaCenteredLoadingIndicator()
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 128.dp)
            ) {
            if (trimmedQuery.isBlank() && !duplicatesOnlyActive) {
                if (history.isNotEmpty()) {
                    item {
                        SearchSectionHeader(
                            text = stringResource(R.string.library_search_history),
                            actionText = stringResource(R.string.library_search_clear_history),
                            onActionClick = {
                                showClearHistoryConfirm = true
                            }
                        )
                    }
                    items(history, key = { it }) { item ->
                        HistoryRow(
                            text = item,
                            onClick = {
                                query = item
                                filter = SearchFilter.All
                                duplicatesOnly = false
                            },
                            onDelete = {
                                history = history - item
                                saveSearchHistory(context, history)
                            }
                        )
                    }
                } else {
                    item { EmptySearchHint(stringResource(R.string.library_search_empty_hint)) }
                }
            } else {
                if (duplicatesOnlyActive) {
                    item { SearchSectionHeader(stringResource(R.string.library_search_duplicates)) }
                }
                if (songResults.isNotEmpty()) {
                    songResultGroups.forEach { (labelRes, entries) ->
                        item {
                            SearchSectionHeader(
                                stringResource(labelRes) + " (${entries.size})"
                            )
                        }
                        items(entries, key = { entry ->
                            val result = entry.result
                            "${result.song.id}:${result.song.path}:${result.lyricSnippet.orEmpty()}:$labelRes:${entry.keySuffix}"
                        }) { entry ->
                            val result = entry.result
                            val selected = result.song.searchIdentityKey() in selectedSongKeys
                            Column {
                                SongItem(
                                    song = result.song,
                                    isCurrent = currentSong?.id == result.song.id,
                                    loadCoverArt = mainViewModel::getCoverArtBitmap,
                                    loadAudioInfo = mainViewModel::getAudioInfo,
                                    showPlayNextInLists = showPlayNextInLists,
                                    selectionMode = selectionMode,
                                    selected = selected,
                                    onPlayNext = {
                                        playerViewModel.playNext(result.song)
                                        Toast.makeText(context, context.getString(R.string.song_more_added_to_play_next), Toast.LENGTH_SHORT).show()
                                    },
                                    onClick = {
                                        if (selectionMode) {
                                            toggleSongSelection(result.song)
                                        } else {
                                            val playbackSongs = songResults.map { it.song }
                                            val index = playbackSongs.indexOfFirst { it.id == result.song.id && it.path == result.song.path }.coerceAtLeast(0)
                                            playerViewModel.setPlaylist(playbackSongs, index)
                                            commitSearch()
                                            onNavigateToPlayer()
                                        }
                                    },
                                    onLongClick = {
                                        if (songSelectionAvailable) {
                                            selectionMode = true
                                            val songKey = result.song.searchIdentityKey()
                                            selectedSongKeys = selectedSongKeys + songKey
                                            updateRangeAnchorsForManualSelection(songKey, selectedNow = true)
                                        } else {
                                            actionSong = result.song
                                        }
                                    },
                                    onMore = { actionSong = result.song }
                                )
                                result.lyricSnippet?.let { snippet ->
                                    LyricSearchMatchLine(snippet = snippet, query = trimmedQuery)
                                } ?: entry.match?.let { match ->
                                    SongSearchMatchLine(match = match, query = trimmedQuery)
                                }
                            }
                        }
                    }
                }
                if (albumResults.isNotEmpty() && filter in listOf(SearchFilter.All, SearchFilter.Albums)) {
                    item { SearchSectionHeader(stringResource(R.string.library_search_albums) + " (${albumResults.size})") }
                    items(albumResults, key = { it.id }) { album ->
                        AlbumResultRow(
                            album = album,
                            coverModel = mainViewModel.getAlbumArtUri(album.artAlbumId),
                            query = trimmedQuery,
                            onClick = {
                                commitSearch()
                                onNavigateToAlbum(album.id)
                            },
                            onLongClick = { actionTarget = SearchActionTarget.AlbumTarget(album) }
                        )
                    }
                }
                if (artistResults.isNotEmpty() && filter in listOf(SearchFilter.All, SearchFilter.Artists)) {
                    item { SearchSectionHeader(stringResource(R.string.library_search_artists) + " (${artistResults.size})") }
                    items(artistResults, key = { it.artist.name }) { result ->
                        ArtistResultRow(
                            result = result,
                            coverModel = result.representativeSong?.coverUrl?.takeIf { it.isNotBlank() }
                                ?: result.representativeSong?.let { mainViewModel.getAlbumArtUri(it.albumId) },
                            query = trimmedQuery,
                            onClick = {
                                commitSearch()
                                onNavigateToArtist(result.artist.name)
                            },
                            onLongClick = { actionTarget = SearchActionTarget.ArtistTarget(result.artist) }
                        )
                    }
                }
                if (playlistResults.isNotEmpty() && filter in listOf(SearchFilter.All, SearchFilter.Playlists)) {
                    item { SearchSectionHeader(stringResource(R.string.library_search_playlists) + " (${playlistResults.size})") }
                    items(playlistResults, key = { it.id }) { playlist ->
                        val playlistSongs = remember(playlist, songs) { mainViewModel.playlistSongs(playlist) }
                        val coverSong = playlistSongs.firstOrNull()
                        PlaylistResultRow(
                            playlist = playlist,
                            coverModel = coverSong?.coverUrl?.takeIf { it.isNotBlank() }
                                ?: coverSong?.albumId?.takeIf { it > 0L }?.let(mainViewModel::getAlbumArtUri),
                            query = trimmedQuery,
                            onClick = {
                                commitSearch()
                                onNavigateToPlaylist(playlist.id)
                            },
                            onLongClick = { actionTarget = SearchActionTarget.PlaylistTarget(playlist) }
                        )
                    }
                }
                categoryResultsByType.forEach { (categoryType, results) ->
                    if (results.isNotEmpty()) {
                        item { SearchSectionHeader(stringResource(categoryType.labelRes()) + " (${results.size})") }
                        items(results, key = { "$categoryType:${it.name}" }) { item ->
                            MetadataCategoryResultRow(
                                item = item,
                                displayName = if (categoryType == "folder") item.name.substringAfterLast('/').ifBlank { item.name } else item.name,
                                coverModel = item.representativeSong?.coverUrl?.takeIf { it.isNotBlank() }
                                    ?: item.coverAlbumIds.firstOrNull()?.let(mainViewModel::getAlbumArtUri),
                                roundCover = categoryType in listOf("composer", "lyricist"),
                                query = trimmedQuery,
                                onClick = {
                                    commitSearch()
                                    onNavigateToMetadataCategory(categoryType, item.name)
                                },
                                onLongClick = { actionTarget = SearchActionTarget.CategoryTarget(categoryType, item) }
                            )
                        }
                    }
                }
                if (
                    songResults.isEmpty() &&
                    visibleResultCount == 0
                ) {
                    item {
                        EmptySearchHint(
                            if (duplicatesOnlyActive) stringResource(R.string.library_search_no_duplicates)
                            else stringResource(R.string.library_search_no_results)
                        )
                    }
                }
            }
            }
        }
    }

    actionTarget?.let { target ->
        EllaMiuixBottomSheet(
            show = true,
            enableNestedScroll = false,
            title = target.title,
            onDismissRequest = { actionTarget = null }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                EllaMiuixMenuItem(
                    text = stringResource(R.string.common_share),
                    onClick = {
                        shareLocalSongs(context, songsForActionTarget(target))
                        actionTarget = null
                    }
                )
                EllaMiuixMenuItem(
                    text = stringResource(R.string.song_more_add_to_playlist),
                    onClick = {
                        playlistPickerSongs = songsForActionTarget(target)
                        actionTarget = null
                    }
                )
                EllaMiuixMenuItem(
                    text = stringResource(R.string.common_add_to_queue),
                    onClick = {
                        playerViewModel.addToPlaylist(songsForActionTarget(target))
                        Toast.makeText(context, context.getString(R.string.song_more_added_to_queue), Toast.LENGTH_SHORT).show()
                        actionTarget = null
                    }
                )
                EllaMiuixMenuItem(
                    text = stringResource(R.string.song_more_play_next),
                    onClick = {
                        playerViewModel.playNext(songsForActionTarget(target))
                        Toast.makeText(context, context.getString(R.string.song_more_added_to_play_next), Toast.LENGTH_SHORT).show()
                        actionTarget = null
                    }
                )
                EllaMiuixMenuItem(
                    text = stringResource(R.string.common_add_desktop_shortcut),
                    onClick = {
                        val ok = requestPinnedEllaShortcut(
                            context = context,
                            id = shortcutIdForActionTarget(target),
                            label = target.title,
                            route = shortcutRouteForActionTarget(target)
                        )
                        Toast.makeText(
                            context,
                            if (ok) context.getString(R.string.playlist_shortcut_requested, target.title) else context.getString(R.string.playlist_shortcut_unsupported),
                            Toast.LENGTH_SHORT
                        ).show()
                        actionTarget = null
                    }
                )
                if (target is SearchActionTarget.CategoryTarget && target.type == "folder") {
                    EllaMiuixMenuItem(
                        text = stringResource(R.string.folder_block_folder),
                        onClick = {
                            val normalizedPath = target.item.name.normalizeFolderPath()
                            scope.launch {
                                val nextBlockedFolders = (blockedFolders + normalizedPath)
                                    .distinctBy { it.normalizeFolderPath().lowercase(Locale.ROOT) }
                                settingsManager.setScanExcludeFolders(nextBlockedFolders.joinToString("；"))
                                mainViewModel.scanMusic()
                            }
                            actionTarget = null
                        }
                    )
                }
            }
        }
    }

    playlistPickerSongs?.let { songsToAdd ->
        EllaMiuixBottomSheet(
            show = true,
            enableNestedScroll = false,
            title = stringResource(R.string.song_more_add_to_playlist_title),
            onDismissRequest = { playlistPickerSongs = null }
        ) {
            AddToPlaylistSheet(
                playlists = playlists,
                songCount = songsToAdd.size,
                onDismiss = { playlistPickerSongs = null },
                onCreatePlaylist = {
                    createPlaylistSongs = songsToAdd
                    playlistPickerSongs = null
                },
                onPlaylistsConfirm = { selectedPlaylists, appendToEnd ->
                    selectedPlaylists.forEach { playlist ->
                        mainViewModel.addSongsToPlaylist(playlist.id, songsToAdd, appendToEnd)
                    }
                    playlistPickerSongs = null
                    Toast.makeText(context, context.getString(R.string.player_added_to_playlists, selectedPlaylists.size), Toast.LENGTH_SHORT).show()
                    finishSelectionMode()
                }
            )
        }
    }

    createPlaylistSongs?.let { songsToAdd ->
        CreatePlaylistDialog(
            onDismiss = { createPlaylistSongs = null },
            onCreate = { playlistName ->
                mainViewModel.createPlaylistOrShowDuplicateToast(context, playlistName) { playlist ->
                    mainViewModel.addSongsToPlaylist(playlist.id, songsToAdd)
                    createPlaylistSongs = null
                    finishSelectionMode()
                }
            }
        )
    }

    SongMoreActionHost(
        actionSong = actionSong,
        mainViewModel = mainViewModel,
        playerViewModel = playerViewModel,
        onDismissAction = { actionSong = null },
        onNavigateToAlbum = onNavigateToAlbum,
        onNavigateToArtist = onNavigateToArtist
    )

    ConfirmDangerDialog(
        show = showClearHistoryConfirm,
        title = stringResource(R.string.library_search_clear_history_title),
        message = stringResource(R.string.library_search_clear_history_message),
        confirmText = stringResource(R.string.common_clear),
        onDismiss = { showClearHistoryConfirm = false },
        onConfirm = {
            history = emptyList()
            saveSearchHistory(context, emptyList())
            showClearHistoryConfirm = false
        }
    )

    ConfirmDangerDialog(
        show = pendingDeleteSongs.isNotEmpty(),
        title = stringResource(R.string.song_more_delete_song_title),
        message = stringResource(R.string.library_delete_selected_message, pendingDeleteSongs.size),
        confirmText = stringResource(R.string.song_more_delete_permanently),
        onDismiss = { pendingDeleteSongs = emptyList() },
        onConfirm = {
            requestDeleteSongs(pendingDeleteSongs)
            pendingDeleteSongs = emptyList()
            finishSelectionMode()
        }
    )
}

private fun SearchFilter.labelRes(): Int = when (this) {
    SearchFilter.All -> R.string.library_search_all
    SearchFilter.Songs -> R.string.library_search_songs
    SearchFilter.Artists -> R.string.library_search_artists
    SearchFilter.Albums -> R.string.library_search_albums
    SearchFilter.Playlists -> R.string.library_search_playlists
    SearchFilter.Folders -> R.string.library_search_folders
    SearchFilter.Composers -> R.string.library_search_composers
    SearchFilter.Lyricists -> R.string.library_search_lyricists
    SearchFilter.Lyrics -> R.string.library_search_lyrics
    SearchFilter.Genres -> R.string.library_search_genres
    SearchFilter.Years -> R.string.library_search_years
}

private fun String.labelRes(): Int = when (this) {
    "folder" -> R.string.library_search_folders
    "composer" -> R.string.library_search_composers
    "lyricist" -> R.string.library_search_lyricists
    "genre" -> R.string.library_search_genres
    "year" -> R.string.library_search_years
    else -> R.string.library_search_all
}

private sealed interface SearchActionTarget {
    val title: String

    data class AlbumTarget(val album: Album) : SearchActionTarget {
        override val title: String = album.name
    }

    data class ArtistTarget(val artist: Artist) : SearchActionTarget {
        override val title: String = artist.name
    }

    data class PlaylistTarget(val playlist: UserPlaylist) : SearchActionTarget {
        override val title: String = playlist.name
    }

    data class CategoryTarget(val type: String, val item: MetadataCategoryItem) : SearchActionTarget {
        override val title: String = item.name.substringAfterLast('/').ifBlank { item.name }
    }
}
