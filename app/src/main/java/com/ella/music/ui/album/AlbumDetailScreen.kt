package com.ella.music.ui.album

import com.ella.music.ui.components.EllaMiuixBottomSheet

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ella.music.R
import com.ella.music.data.audioQualitySummary
import com.ella.music.data.model.Album
import com.ella.music.data.model.FAVORITES_PLAYLIST_ID
import com.ella.music.data.model.AudioInfo
import com.ella.music.data.model.Song
import com.ella.music.data.model.UserPlaylist
import com.ella.music.data.model.formatPlaybackDuration
import com.ella.music.data.model.playlistIdentityKey
import com.ella.music.data.splitArtistNames
import com.ella.music.data.splitGenreNames
import com.ella.music.ui.LibrarySortUiState
import com.ella.music.ui.components.AddToPlaylistSheet
import com.ella.music.ui.components.AppleStylePlayButton
import com.ella.music.ui.components.ArtistPickerSheet
import com.ella.music.ui.components.ArtworkUsage
import com.ella.music.ui.components.ConfirmDangerDialog
import com.ella.music.ui.components.CreatePlaylistAndAddSheet
import com.ella.music.ui.components.createPlaylistOrShowDuplicateToast
import com.ella.music.ui.components.DefaultAlbumCover
import com.ella.music.ui.components.DoubleTapScrollOverlay
import com.ella.music.ui.components.EllaSearchBar
import com.ella.music.ui.components.FastIndexBar
import com.ella.music.ui.components.FloatingSelectionControls
import com.ella.music.ui.components.LibraryFloatingControlsBottomPadding
import com.ella.music.ui.components.LibraryFloatingControlsEndPadding
import com.ella.music.ui.components.LibrarySecondaryFloatingControlsBottomPadding
import com.ella.music.ui.components.LazyListScrollIndicator
import com.ella.music.ui.components.LocateCurrentSongFloatingButton
import com.ella.music.ui.components.SafeCoverImage
import com.ella.music.ui.components.ShuffleAllFloatingButton
import com.ella.music.ui.components.SongMoreActionHost
import com.ella.music.ui.components.SortDropdownItem
import com.ella.music.ui.components.SortDropdownMenu
import com.ella.music.ui.components.ellaPageBackground
import com.ella.music.ui.components.rememberSongArtworkState
import com.ella.music.ui.components.rememberSongDeleteRequester
import com.ella.music.ui.components.toFastIndexSection
import com.ella.music.viewmodel.MainViewModel
import com.ella.music.viewmodel.PlayerViewModel
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.basic.Search
import top.yukonga.miuix.kmp.icon.extended.Add
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.icon.extended.Delete
import top.yukonga.miuix.kmp.icon.extended.Play
import top.yukonga.miuix.kmp.icon.extended.SelectAll
import top.yukonga.miuix.kmp.icon.extended.Sort
import top.yukonga.miuix.kmp.theme.MiuixTheme
import androidx.compose.ui.graphics.Color
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

@Composable
fun AlbumDetailScreen(
    albumId: Long,
    mainViewModel: MainViewModel,
    playerViewModel: PlayerViewModel,
    onBack: () -> Unit,
    onNavigateToAlbum: (Long) -> Unit = {},
    onNavigateToArtist: (String) -> Unit = {},
    onNavigateToMetadataCategory: (String, String) -> Unit = { _, _ -> },
    onNavigateToPlayer: () -> Unit
) {
    val albums by mainViewModel.albums.collectAsState()
    val playlists by mainViewModel.playlists.collectAsState()
    val context = LocalContext.current
    val currentSong by playerViewModel.currentSong.collectAsState()
    val favoriteSongKeys by playerViewModel.favoriteSongKeys.collectAsState()
    val locateCurrentSongRequest by playerViewModel.locateCurrentSongRequest.collectAsState()
    val ratingRevision by mainViewModel.ratingRevision.collectAsState()
    val openPlayerOnPlay by mainViewModel.settingsManager.openPlayerOnPlay.collectAsState(initial = false)
    val sortIndex by mainViewModel.settingsManager.albumDetailSongSortIndex.collectAsState(initial = LibrarySortUiState.albumDetailSongSortIndex)
    val showPlayNextInLists by mainViewModel.settingsManager.showPlayNextInLists.collectAsState(initial = false)
    val sortMode = AlbumDetailSongSortMode.entries.getOrElse(sortIndex) { AlbumDetailSongSortMode.Track }
    val scope = rememberCoroutineScope()
    var sortExpanded by remember { mutableStateOf(false) }
    var searchExpanded by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var selectionMode by remember { mutableStateOf(false) }
    var selectedIds by remember { mutableStateOf(setOf<Long>()) }
    var rangeAnchorId by remember { mutableStateOf<Long?>(null) }
    var rangeTargetId by remember { mutableStateOf<Long?>(null) }
    var actionSong by remember { mutableStateOf<Song?>(null) }
    var playlistPickerSongs by remember { mutableStateOf<List<Song>?>(null) }
    var createPlaylistSongs by remember { mutableStateOf<List<Song>?>(null) }
    var pendingDeleteSongs by remember { mutableStateOf<List<Song>>(emptyList()) }
    var albumArtistChoices by remember { mutableStateOf<List<String>>(emptyList()) }
    val requestDeleteSongs = rememberSongDeleteRequester(mainViewModel)
    val album = albums.find { it.id == albumId }
    val albumSongs = mainViewModel.getSongsForAlbum(albumId)
    val filteredAlbumSongs = remember(albumSongs, searchQuery) {
        val query = searchQuery.trim()
        if (query.isBlank()) {
            albumSongs
        } else {
            albumSongs.filter { song ->
                song.title.contains(query, ignoreCase = true) ||
                    song.artist.contains(query, ignoreCase = true) ||
                    song.album.contains(query, ignoreCase = true) ||
                    song.fileName.contains(query, ignoreCase = true)
            }
        }
    }
    val sortedAlbumSongs = remember(filteredAlbumSongs, sortMode) { filteredAlbumSongs.sortedForAlbumDetail(sortMode) }
    val sortedAlbumSongIndexById = remember(sortedAlbumSongs) {
        buildMap {
            sortedAlbumSongs.forEachIndexed { index, song -> put(song.id, index) }
        }
    }
    val albumDuration = remember(albumSongs) { albumSongs.sumOf { it.duration } }
    val useDiscSections = sortMode == AlbumDetailSongSortMode.Track && sortedAlbumSongs.any { it.discNumber > 0 }
    val discGroups = remember(sortedAlbumSongs, sortMode) {
        if (sortMode == AlbumDetailSongSortMode.Track) {
            sortedAlbumSongs.groupForDiscSections()
        } else {
            emptyList()
        }
    }
    val albumArtUri = mainViewModel.getAlbumArtUri(album?.artAlbumId ?: albumSongs.firstOrNull()?.albumId ?: 0L)
    val albumCoverState = rememberSongArtworkState(
        song = albumSongs.firstOrNull(),
        albumArtUri = albumArtUri,
        loadCoverArt = mainViewModel::getLargeCoverArtBitmap,
        usage = ArtworkUsage.ArtistImage,
        showDefaultWhenMissing = false
    )
    val albumCoverModel = albumCoverState.model
    val neteaseAlbumUrl by produceState<String?>(initialValue = null, albumId, albumSongs) {
        value = mainViewModel.getNeteaseAlbumUrlForAlbum(albumId)
    }
    val albumCopyright by produceState<String>(initialValue = "", albumId, albumSongs) {
        value = withContext(Dispatchers.IO) {
            albumSongs
                .asSequence()
                .map { mainViewModel.getSongTagInfo(it).copyright }
                .map { it.trim() }
                .filter { it.isNotBlank() }
                .distinctBy { it.lowercase(Locale.ROOT) }
                .take(3)
                .joinToString("\n")
        }
    }
    val albumRecordedYear by produceState<String?>(initialValue = null, albumId, albumSongs) {
        value = withContext(Dispatchers.IO) {
            albumSongs
                .asSequence()
                .mapNotNull { song -> mainViewModel.getFullAudioTagInfo(song)?.recordedDateYear() }
                .firstOrNull()
        }
    }
    val albumGenres = remember(albumSongs) {
        albumSongs
            .flatMap { splitGenreNames(it.genre) }
            .distinctBy { it.lowercase(Locale.ROOT) }
    }
    val participatingArtists = remember(albumSongs) {
        albumSongs
            .flatMap { splitArtistNames(it.artist) }
            .distinctBy { it.lowercase(Locale.ROOT) }
    }
    val participatingComposers = remember(albumSongs) {
        albumSongs
            .flatMap { splitArtistNames(it.composer) }
            .distinctBy { it.lowercase(Locale.ROOT) }
    }
    val participatingLyricists = remember(albumSongs) {
        albumSongs
            .flatMap { splitArtistNames(it.lyricist) }
            .distinctBy { it.lowercase(Locale.ROOT) }
    }
    val listState = rememberLazyListState()
    val albumSongHeaderCount = 2
    val showSongSideIndex = !selectionMode &&
        sortMode == AlbumDetailSongSortMode.Title &&
        sortedAlbumSongs.size > 30
    val songFastIndexData = remember(showSongSideIndex, sortedAlbumSongs) {
        if (!showSongSideIndex) {
            emptyList()
        } else {
            sortedAlbumSongs
                .mapIndexed { index, song -> song.title.toFastIndexSection() to (index + albumSongHeaderCount) }
                .distinctBy { it.first }
        }
    }
    val showScrollIndicator = sortedAlbumSongs.size > 30 && !showSongSideIndex
    var scrollToTopRequest by remember { mutableStateOf(0) }
    fun finishSelectionMode() {
        selectionMode = false
        selectedIds = emptySet()
        rangeAnchorId = null
        rangeTargetId = null
    }
    fun updateRangeAnchorsForManualSelection(songId: Long, selectedNow: Boolean) {
        if (selectedNow) {
            when {
                rangeAnchorId == null -> rangeAnchorId = songId
                rangeAnchorId == songId -> Unit
                else -> rangeTargetId = songId
            }
        } else {
            if (rangeTargetId == songId) rangeTargetId = null
            if (rangeAnchorId == songId) {
                rangeAnchorId = rangeTargetId ?: selectedIds.firstOrNull { it != songId }
                rangeTargetId = null
            }
        }
    }
    fun toggleSelection(song: Song) {
        val selecting = song.id !in selectedIds
        val next = if (selecting) selectedIds + song.id else selectedIds - song.id
        selectedIds = next
        updateRangeAnchorsForManualSelection(song.id, selecting)
        if (next.isEmpty()) selectionMode = false
    }
    fun selectedSongs(): List<Song> = sortedAlbumSongs.filter { it.id in selectedIds }
    val selectedVisibleCount = remember(selectedIds, sortedAlbumSongs) {
        sortedAlbumSongs.count { it.id in selectedIds }
    }
    val rangeSelectionAvailable = remember(sortedAlbumSongIndexById, selectedIds, rangeAnchorId, rangeTargetId) {
        val anchor = rangeAnchorId
        val target = rangeTargetId
        anchor != null &&
            target != null &&
            anchor != target &&
            anchor in selectedIds &&
            target in selectedIds &&
            anchor in sortedAlbumSongIndexById &&
            target in sortedAlbumSongIndexById
    }
    fun applyRangeSelection() {
        val anchor = rangeAnchorId ?: return
        val target = rangeTargetId ?: return
        val anchorIndex = sortedAlbumSongIndexById[anchor] ?: return
        val targetIndex = sortedAlbumSongIndexById[target] ?: return
        if (anchorIndex == targetIndex) return
        val bounds = if (anchorIndex < targetIndex) anchorIndex..targetIndex else targetIndex..anchorIndex
        selectedIds = selectedIds + bounds.map { sortedAlbumSongs[it].id }
        rangeAnchorId = target
        rangeTargetId = null
    }
    fun toggleSelectAllVisibleSongs() {
        if (sortedAlbumSongs.isEmpty()) return
        val ids = sortedAlbumSongs.mapTo(mutableSetOf()) { it.id }
        if (ids.all { it in selectedIds }) {
            selectedIds = selectedIds - ids
            rangeAnchorId = null
            rangeTargetId = null
        } else {
            selectedIds = selectedIds + ids
            rangeAnchorId = sortedAlbumSongs.firstOrNull()?.id
            rangeTargetId = sortedAlbumSongs.lastOrNull()?.id
        }
        selectionMode = true
    }

    val currentSongItemIndex = remember(sortedAlbumSongs, discGroups, useDiscSections, currentSong?.id, selectionMode) {
        if (selectionMode) return@remember -1
        val songIndex = currentSong?.id?.let { sortedAlbumSongIndexById[it] } ?: -1
        if (songIndex < 0) {
            -1
        } else {
            val discHeaderCount = if (useDiscSections) {
                val song = sortedAlbumSongs[songIndex]
                discGroups.count { group ->
                    group.discNumber <= song.safeDiscNumber()
                }
            } else {
                0
            }
            2 + discHeaderCount + songIndex
        }
    }

    BackHandler(enabled = selectionMode || sortExpanded || searchExpanded) {
        when {
            selectionMode -> finishSelectionMode()
            searchExpanded -> {
                searchExpanded = false
                searchQuery = ""
            }
            else -> sortExpanded = false
        }
    }

    LaunchedEffect(scrollToTopRequest) {
        if (scrollToTopRequest > 0) listState.animateScrollToItem(0)
    }
    LaunchedEffect(selectionMode, sortedAlbumSongs) {
        if (!selectionMode) return@LaunchedEffect
        val visibleIds = sortedAlbumSongs.mapTo(mutableSetOf()) { it.id }
        selectedIds = selectedIds.filterTo(mutableSetOf()) { it in visibleIds }
        if (rangeAnchorId !in visibleIds) rangeAnchorId = selectedIds.firstOrNull()
        if (rangeTargetId !in visibleIds) rangeTargetId = null
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(ellaPageBackground())
    ) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 120.dp)
        ) {
            item {
                AlbumHeader(
                    album = album,
                    albumCoverModel = albumCoverModel,
                    songCount = sortedAlbumSongs.size,
                    duration = albumDuration,
                    hasNeteaseAlbum = !neteaseAlbumUrl.isNullOrBlank(),
                    onNeteaseAlbumClick = { openUrl(context, neteaseAlbumUrl.orEmpty()) },
                    onAlbumArtistClick = {
                        val albumArtist = album?.albumArtist?.takeIf { it.isNotBlank() }
                            ?: return@AlbumHeader
                        val artists = splitArtistNames(albumArtist).ifEmpty { listOf(albumArtist.trim()) }
                        if (artists.size == 1) {
                            onNavigateToArtist(artists.first())
                        } else {
                            albumArtistChoices = artists
                        }
                    },
                    onReleaseYearClick = {
                        album?.yearInt?.takeIf { it > 0 }?.let { year ->
                            onNavigateToMetadataCategory("year", year.toString())
                        }
                    },
                    onPlayAll = {
                        if (sortedAlbumSongs.isNotEmpty()) {
                            playerViewModel.setPlaylist(sortedAlbumSongs, 0)
                            if (openPlayerOnPlay) onNavigateToPlayer()
                        }
                    }
                )
            }

            item {
                Text(
                    text = stringResource(
                        R.string.album_sort_summary,
                        sortedAlbumSongs.size,
                        albumDuration.formatPlaybackDuration(),
                        stringResource(sortMode.labelRes)
                    ),
                    fontSize = 13.sp,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }

            if (useDiscSections) {
                discGroups.forEach { group ->
                    item(key = "disc-${group.discNumber}") {
                        DiscHeader(group)
                    }
                    items(
                        items = group.songs,
                        key = { song -> song.id }
                    ) { song ->
                        val index = sortedAlbumSongIndexById[song.id] ?: -1
                        AlbumSongRow(
                            song = song,
                            index = index,
                            sortedAlbumSongs = sortedAlbumSongs,
                            currentSongId = currentSong?.id,
                            isFavorite = song.playlistIdentityKey() in favoriteSongKeys,
                            showTrackNumber = true,
                            mainViewModel = mainViewModel,
                            ratingRevision = ratingRevision,
                            playerViewModel = playerViewModel,
                            openPlayerOnPlay = openPlayerOnPlay,
                            onNavigateToPlayer = onNavigateToPlayer,
                            selectionMode = selectionMode,
                            selected = song.id in selectedIds,
                            onLongClick = {
                                selectionMode = true
                                selectedIds = selectedIds + song.id
                                updateRangeAnchorsForManualSelection(song.id, selectedNow = true)
                            },
                            onSelectionClick = { toggleSelection(song) },
                            onMore = { actionSong = song },
                            showPlayNextInLists = showPlayNextInLists
                        )
                    }
                }
            } else {
                itemsIndexed(sortedAlbumSongs) { index, song ->
                    AlbumSongRow(
                        song = song,
                        index = index,
                        sortedAlbumSongs = sortedAlbumSongs,
                        currentSongId = currentSong?.id,
                        isFavorite = song.playlistIdentityKey() in favoriteSongKeys,
                        showTrackNumber = sortMode == AlbumDetailSongSortMode.Track,
                        mainViewModel = mainViewModel,
                        ratingRevision = ratingRevision,
                        playerViewModel = playerViewModel,
                        openPlayerOnPlay = openPlayerOnPlay,
                        onNavigateToPlayer = onNavigateToPlayer,
                        selectionMode = selectionMode,
                        selected = song.id in selectedIds,
                        onLongClick = {
                            selectionMode = true
                            selectedIds = selectedIds + song.id
                            updateRangeAnchorsForManualSelection(song.id, selectedNow = true)
                        },
                        onSelectionClick = { toggleSelection(song) },
                        onMore = { actionSong = song },
                        showPlayNextInLists = showPlayNextInLists
                    )
                }
            }
            if (
                albumCopyright.isNotBlank() ||
                albumGenres.isNotEmpty() ||
                participatingArtists.isNotEmpty() ||
                participatingComposers.isNotEmpty() ||
                participatingLyricists.isNotEmpty() ||
                albumRecordedYear != null
            ) {
                item(key = "album-extra-info") {
                    AlbumCopyrightFooter(
                        copyright = albumCopyright,
                        genres = albumGenres,
                        artists = participatingArtists,
                        composers = participatingComposers,
                        lyricists = participatingLyricists,
                        year = albumRecordedYear,
                        onGenreClick = { genre -> onNavigateToMetadataCategory("genre", genre) },
                        onArtistClick = onNavigateToArtist,
                        onComposerClick = { composer -> onNavigateToMetadataCategory("composer", composer) },
                        onLyricistClick = { lyricist -> onNavigateToMetadataCategory("lyricist", lyricist) },
                        onYearClick = { year -> onNavigateToMetadataCategory("year", year) }
                    )
                }
            }
        }

        if (showSongSideIndex && songFastIndexData.isNotEmpty()) {
            FastIndexBar(
                letters = songFastIndexData.map { it.first },
                onLetterClick = { letter ->
                    songFastIndexData.firstOrNull { it.first == letter }?.second?.let { itemIndex ->
                        scope.launch { listState.scrollToItem(itemIndex) }
                    }
                },
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(top = 80.dp, bottom = 118.dp)
            )
        } else if (showScrollIndicator) {
            LazyListScrollIndicator(
                state = listState,
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(top = 80.dp, bottom = 118.dp)
            )
        }

        IconButton(
            onClick = { if (selectionMode) finishSelectionMode() else onBack() },
            modifier = Modifier
                .windowInsetsPadding(WindowInsets.statusBars)
                .padding(start = 8.dp, top = 8.dp)
                .size(48.dp)
                .align(Alignment.TopStart)
        ) {
            Icon(
                imageVector = MiuixIcons.Regular.Back,
                contentDescription = stringResource(R.string.common_back),
                tint = MiuixTheme.colorScheme.onSurface,
                modifier = Modifier.size(26.dp)
            )
        }

        IconButton(
            onClick = {
                if (selectionMode) {
                    val selected = selectedSongs()
                    if (selected.isNotEmpty()) playlistPickerSongs = selected
                } else {
                    selectionMode = true
                    selectedIds = emptySet()
                    rangeAnchorId = null
                    rangeTargetId = null
                }
            },
            modifier = Modifier
                .windowInsetsPadding(WindowInsets.statusBars)
                .padding(end = 104.dp, top = 8.dp)
                .size(48.dp)
                .align(Alignment.TopEnd)
        ) {
            Icon(
                imageVector = if (selectionMode) MiuixIcons.Regular.Add else MiuixIcons.Regular.SelectAll,
                contentDescription = if (selectionMode) stringResource(R.string.player_add_to_playlist) else stringResource(R.string.common_multi_select),
                tint = MiuixTheme.colorScheme.onSurface,
                modifier = Modifier.size(24.dp)
            )
        }

        if (selectionMode) {
            IconButton(
                onClick = {
                    val selected = selectedSongs()
                    if (selected.isNotEmpty()) {
                        playerViewModel.playNext(selected)
                        finishSelectionMode()
                    }
                },
                modifier = Modifier
                    .windowInsetsPadding(WindowInsets.statusBars)
                    .padding(end = 56.dp, top = 8.dp)
                    .size(48.dp)
                    .align(Alignment.TopEnd)
            ) {
                Icon(
                    imageVector = MiuixIcons.Regular.Play,
                    contentDescription = stringResource(R.string.song_more_play_next),
                    tint = MiuixTheme.colorScheme.onSurface,
                    modifier = Modifier.size(24.dp)
                )
            }
            IconButton(
                onClick = {
                    val selected = selectedSongs()
                    if (selected.isNotEmpty()) pendingDeleteSongs = selected
                },
                modifier = Modifier
                    .windowInsetsPadding(WindowInsets.statusBars)
                    .padding(end = 8.dp, top = 8.dp)
                    .size(48.dp)
                    .align(Alignment.TopEnd)
            ) {
                Icon(
                    imageVector = MiuixIcons.Regular.Delete,
                    contentDescription = stringResource(R.string.common_delete),
                    tint = Color(0xFFE5484D),
                    modifier = Modifier.size(24.dp)
                )
            }
        }

        if (!selectionMode) {
            IconButton(
                onClick = {
                    searchExpanded = !searchExpanded
                    if (!searchExpanded) searchQuery = ""
                },
                modifier = Modifier
                    .windowInsetsPadding(WindowInsets.statusBars)
                    .padding(end = 56.dp, top = 8.dp)
                    .size(48.dp)
                    .align(Alignment.TopEnd)
            ) {
                Icon(
                    imageVector = MiuixIcons.Basic.Search,
                    contentDescription = stringResource(R.string.common_search),
                    tint = MiuixTheme.colorScheme.onSurface,
                    modifier = Modifier.size(24.dp)
                )
            }
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .windowInsetsPadding(WindowInsets.statusBars)
                    .padding(end = 8.dp, top = 8.dp)
                    .size(48.dp)
                    .align(Alignment.TopEnd)
            ) {
                SortDropdownMenu(
                    items = AlbumDetailSongSortMode.entries.map { mode ->
                        SortDropdownItem(
                            text = stringResource(mode.labelRes),
                            selected = sortMode == mode,
                            onClick = {
                                LibrarySortUiState.albumDetailSongSortIndex = mode.ordinal
                                scope.launch { mainViewModel.settingsManager.setAlbumDetailSongSortIndex(mode.ordinal) }
                                scrollToTopRequest++
                            }
                        )
                    }
                )
            }
        }

        AnimatedVisibility(
            visible = searchExpanded,
            enter = expandVertically(),
            exit = shrinkVertically(),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .windowInsetsPadding(WindowInsets.statusBars)
                .padding(top = 60.dp)
        ) {
            EllaSearchBar(
                query = searchQuery,
                onQueryChange = { searchQuery = it },
                onSearch = { searchExpanded = false },
                placeholder = stringResource(R.string.folder_detail_search_placeholder),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp)
            )
        }

        DoubleTapScrollOverlay(
            onDoubleTap = { scrollToTopRequest++ },
            modifier = Modifier
                .align(Alignment.TopCenter)
                .windowInsetsPadding(WindowInsets.statusBars)
                .fillMaxWidth()
                .height(56.dp),
            startPadding = 64.dp,
            endPadding = 160.dp
        )

        if (selectionMode) {
            Text(
                text = stringResource(R.string.library_selected_fraction, selectedIds.size, sortedAlbumSongs.size),
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = MiuixTheme.colorScheme.onSurface,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .windowInsetsPadding(WindowInsets.statusBars)
                    .padding(top = 22.dp)
            )
        }

        AnimatedVisibility(
            visible = sortExpanded,
            enter = expandVertically(),
            exit = shrinkVertically(),
            modifier = Modifier
                .align(Alignment.TopEnd)
                .windowInsetsPadding(WindowInsets.statusBars)
                .padding(top = 60.dp, end = 16.dp)
        ) {
            Column(
                modifier = Modifier
                    .background(MiuixTheme.colorScheme.surfaceContainer.copy(alpha = 0.94f), androidx.compose.foundation.shape.RoundedCornerShape(18.dp))
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                AlbumDetailSongSortMode.entries.forEach { mode ->
                    Text(
                        text = stringResource(mode.labelRes),
                        fontSize = 14.sp,
                        fontWeight = if (sortMode == mode) FontWeight.Bold else FontWeight.Normal,
                        color = if (sortMode == mode) MiuixTheme.colorScheme.primary else MiuixTheme.colorScheme.onSurface,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                LibrarySortUiState.albumDetailSongSortIndex = mode.ordinal
                                scope.launch { mainViewModel.settingsManager.setAlbumDetailSongSortIndex(mode.ordinal) }
                                scrollToTopRequest++
                                sortExpanded = false
                            }
                            .padding(vertical = 10.dp)
                    )
                }
            }
        }

        ShuffleAllFloatingButton(
            visible = !selectionMode && sortedAlbumSongs.isNotEmpty(),
            onClick = {
                playerViewModel.setPlaylist(sortedAlbumSongs.shuffled(), 0)
                if (openPlayerOnPlay) onNavigateToPlayer()
            },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = LibraryFloatingControlsEndPadding, bottom = LibrarySecondaryFloatingControlsBottomPadding)
        )
        LocateCurrentSongFloatingButton(
            listState = listState,
            currentItemIndex = currentSongItemIndex,
            locateRequest = locateCurrentSongRequest,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = LibraryFloatingControlsEndPadding, bottom = LibraryFloatingControlsBottomPadding)
        )
        FloatingSelectionControls(
            visible = selectionMode && sortedAlbumSongs.isNotEmpty(),
            rangeEnabled = rangeSelectionAvailable,
            allSelected = sortedAlbumSongs.isNotEmpty() && selectedVisibleCount == sortedAlbumSongs.size,
            onRangeSelect = ::applyRangeSelection,
            onSelectAll = ::toggleSelectAllVisibleSongs,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = LibraryFloatingControlsEndPadding, bottom = LibraryFloatingControlsBottomPadding)
        )

        SongMoreActionHost(
            actionSong = actionSong,
            mainViewModel = mainViewModel,
            playerViewModel = playerViewModel,
            onDismissAction = { actionSong = null },
            onNavigateToAlbum = onNavigateToAlbum,
            onNavigateToArtist = onNavigateToArtist
        )

        if (albumArtistChoices.isNotEmpty()) {
            EllaMiuixBottomSheet(
                show = true,
                enableNestedScroll = false,
                title = stringResource(R.string.common_select_artist),
                onDismissRequest = { albumArtistChoices = emptyList() }
            ) {
                ArtistPickerSheet(
                    artists = albumArtistChoices,
                    onArtistSelected = { artist ->
                        albumArtistChoices = emptyList()
                        onNavigateToArtist(artist)
                    },
                    onDismiss = { albumArtistChoices = emptyList() }
                )
            }
        }

        playlistPickerSongs?.let { songsToAdd ->
            EllaMiuixBottomSheet(
                show = true,
                enableNestedScroll = false,
                title = stringResource(R.string.player_add_to_playlist),
                onDismissRequest = { playlistPickerSongs = null }
            ) {
                AddToPlaylistSheet(
                    playlists = playlists.sortedWith(
                        compareByDescending<UserPlaylist> { it.id == FAVORITES_PLAYLIST_ID }
                            .thenByDescending { it.createdAt }
                    ),
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
                        finishSelectionMode()
                    }
                )
            }
        }

        createPlaylistSongs?.let { songsToAdd ->
            CreatePlaylistAndAddSheet(
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
        ConfirmDangerDialog(
            show = pendingDeleteSongs.isNotEmpty(),
            title = stringResource(R.string.song_more_delete_song_title),
            message = stringResource(R.string.library_delete_selected_message, pendingDeleteSongs.size),
            confirmText = stringResource(R.string.song_more_delete_permanently),
            onDismiss = { pendingDeleteSongs = emptyList() },
            onConfirm = {
                val songsToDelete = pendingDeleteSongs
                pendingDeleteSongs = emptyList()
                requestDeleteSongs(songsToDelete)
                finishSelectionMode()
            }
        )
    }
}

private fun com.ella.music.data.metadata.AudioTagInfo.recordedDateYear(): String? =
    (customTags.firstRecordedDateValue() ?: year?.trim())
        ?.let { Regex("""\d{4}""").find(it)?.value }

private fun Map<String, List<String>>.firstRecordedDateValue(): String? {
    val targets = setOf(
        "RECORDED DATE",
        "RECORDEDDATE",
        "RECORDING DATE",
        "RECORDINGDATE",
        "DATE RECORDED",
        "RECORDEDTIME",
        "DATE",
        "TDRC"
    ).mapTo(mutableSetOf()) { it.normalizedTagName() }
    return entries
        .firstOrNull { (key, values) ->
            key.normalizedTagName() in targets && values.any { it.isNotBlank() }
        }
        ?.value
        ?.firstOrNull { it.isNotBlank() }
        ?.trim()
}

private fun String.normalizedTagName(): String =
    uppercase(Locale.ROOT).filter { it.isLetterOrDigit() }
