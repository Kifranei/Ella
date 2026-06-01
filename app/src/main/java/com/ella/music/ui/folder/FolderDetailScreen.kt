package com.ella.music.ui.folder

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ella.music.R
import com.ella.music.data.model.FAVORITES_PLAYLIST_ID
import com.ella.music.data.model.Song
import com.ella.music.data.model.UserPlaylist
import com.ella.music.data.model.playlistIdentityKey
import com.ella.music.ui.LibrarySortUiState
import com.ella.music.ui.components.ConfirmDangerDialog
import com.ella.music.ui.components.DoubleTapScrollOverlay
import com.ella.music.ui.components.EllaSearchBar
import com.ella.music.ui.components.FastIndexBar
import com.ella.music.ui.components.FolderOutlineIcon
import com.ella.music.ui.components.LazyListScrollIndicator
import com.ella.music.ui.components.LocateCurrentSongFloatingButton
import com.ella.music.ui.components.SongItem
import com.ella.music.ui.components.SongMoreActionHost
import com.ella.music.ui.components.ellaPageBackground
import com.ella.music.viewmodel.MainViewModel
import com.ella.music.viewmodel.PlayerViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.basic.ArrowRight
import top.yukonga.miuix.kmp.icon.basic.Search
import top.yukonga.miuix.kmp.icon.extended.Add
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.icon.extended.Delete
import top.yukonga.miuix.kmp.icon.extended.SelectAll
import top.yukonga.miuix.kmp.icon.extended.Sort
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.window.WindowBottomSheet
import android.icu.text.Transliterator
import java.util.Locale

@Composable
fun FolderDetailScreen(
    folderPath: String,
    mainViewModel: MainViewModel,
    playerViewModel: PlayerViewModel,
    onBack: () -> Unit,
    onNavigateToAlbum: (Long) -> Unit = {},
    onNavigateToArtist: (String) -> Unit = {},
    onFolderClick: (String) -> Unit,
    onNavigateToPlayer: () -> Unit
) {
    val context = LocalContext.current
    val songs by mainViewModel.songs.collectAsState()
    val playlists by mainViewModel.playlists.collectAsState()
    val currentSong by playerViewModel.currentSong.collectAsState()
    val favoriteSongKeys by playerViewModel.favoriteSongKeys.collectAsState()
    val locateCurrentSongRequest by playerViewModel.locateCurrentSongRequest.collectAsState()
    val openPlayerOnPlay by mainViewModel.settingsManager.openPlayerOnPlay.collectAsState(initial = true)
    val scanExcludeFolders by mainViewModel.settingsManager.scanExcludeFolders.collectAsState(initial = "")
    val blockedFolders = remember(scanExcludeFolders) { scanExcludeFolders.toFolderSettingList() }
    val scope = rememberCoroutineScope()
    var searchQuery by remember { mutableStateOf("") }
    var searchExpanded by remember { mutableStateOf(false) }
    var sortExpanded by remember { mutableStateOf(false) }
    var actionSong by remember { mutableStateOf<Song?>(null) }
    var selectionMode by remember { mutableStateOf(false) }
    var selectedIds by remember { mutableStateOf(setOf<Long>()) }
    var playlistPickerSongs by remember { mutableStateOf<List<Song>?>(null) }
    var createPlaylistSongs by remember { mutableStateOf<List<Song>?>(null) }
    var pendingDeleteSongs by remember { mutableStateOf<List<Song>>(emptyList()) }
    var folderToBlock by remember { mutableStateOf<String?>(null) }
    val sortIndex by mainViewModel.settingsManager.folderDetailSongSortIndex.collectAsState(initial = LibrarySortUiState.folderDetailSongSortIndex)
    val sortMode = FolderSongSortMode.entries.getOrElse(sortIndex) { FolderSongSortMode.Title }
    val normalizedFolderPath = remember(folderPath) { folderPath.normalizeFolderPath() }
    var scrollToTopRequest by remember { mutableStateOf(0) }

    val childFolders = remember(songs, normalizedFolderPath) {
        songs.childFoldersOf(normalizedFolderPath).sortedBy { it.name.lowercase(Locale.ROOT) }
    }
    val directSongs = remember(songs, normalizedFolderPath) {
        songs.directSongsInFolder(normalizedFolderPath)
    }
    val recursiveSongs = remember(songs, normalizedFolderPath) {
        songs.recursiveSongsInFolder(normalizedFolderPath)
    }
    val filteredSongs = remember(directSongs, recursiveSongs, searchQuery) {
        val sourceSongs = if (searchQuery.isBlank()) directSongs else recursiveSongs
        if (searchQuery.isBlank()) sourceSongs
        else sourceSongs.filter {
            it.title.contains(searchQuery, ignoreCase = true) ||
                it.artist.contains(searchQuery, ignoreCase = true) ||
                it.album.contains(searchQuery, ignoreCase = true) ||
                it.fileName.contains(searchQuery, ignoreCase = true)
        }
    }
    val sortedSongs = remember(filteredSongs, sortMode) {
        when (sortMode) {
            FolderSongSortMode.Title -> filteredSongs.sortedBy { it.title.musicSortKey() }
            FolderSongSortMode.FileName -> filteredSongs.sortedBy {
                it.fileName.ifBlank { it.path.substringAfterLast('/') }.musicSortKey()
            }
            FolderSongSortMode.Duration -> filteredSongs.sortedByDescending { it.duration }
            FolderSongSortMode.YearAsc -> filteredSongs.sortedByReleaseDate(ascending = true)
            FolderSongSortMode.YearDesc -> filteredSongs.sortedByReleaseDate(ascending = false)
            FolderSongSortMode.DateAdded -> filteredSongs.sortedByDescending { it.dateAdded }
            FolderSongSortMode.DateAddedAsc -> filteredSongs.sortedBy { it.dateAdded }
            FolderSongSortMode.DateModified -> filteredSongs.sortedByDescending { it.dateModified }
            FolderSongSortMode.DateModifiedAsc -> filteredSongs.sortedBy { it.dateModified }
        }
    }

    val folderName = normalizedFolderPath.substringAfterLast('/')

    BackHandler(enabled = selectionMode || searchExpanded || sortExpanded || folderToBlock != null) {
        when {
            folderToBlock != null -> folderToBlock = null
            selectionMode -> {
                selectedIds = emptySet()
                selectionMode = false
            }
            searchExpanded -> {
                searchExpanded = false
                searchQuery = ""
            }
            sortExpanded -> sortExpanded = false
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(ellaPageBackground())
            .windowInsetsPadding(WindowInsets.statusBars)
    ) {
        Box {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = {
                        if (selectionMode) {
                            selectedIds = emptySet()
                            selectionMode = false
                        } else {
                            onBack()
                        }
                    }
                ) {
                    Icon(
                        imageVector = MiuixIcons.Regular.Back,
                        contentDescription = if (selectionMode) stringResource(R.string.common_exit_selection) else stringResource(R.string.common_back),
                        tint = MiuixTheme.colorScheme.onSurface,
                        modifier = Modifier.size(24.dp)
                    )
                }
                Spacer(modifier = Modifier.width(4.dp))
                if (!selectionMode) {
                    FolderOutlineIcon(
                        tint = MiuixTheme.colorScheme.primary,
                        modifier = Modifier.size(26.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (selectionMode) {
                            stringResource(R.string.library_selected_count, selectedIds.size)
                        } else {
                            folderName.ifEmpty { stringResource(R.string.folder_root) }
                        },
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = MiuixTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (!selectionMode) {
                        Text(
                            text = stringResource(R.string.folder_detail_header_summary, childFolders.size, recursiveSongs.size),
                            fontSize = 12.sp,
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary
                        )
                    }
                }
                if (selectionMode) {
                    IconButton(
                        onClick = {
                                val selectedSongs = sortedSongs.filter { it.id in selectedIds }
                                if (selectedSongs.isEmpty()) {
                                    Toast.makeText(context, R.string.library_select_songs_first, Toast.LENGTH_SHORT).show()
                                } else {
                                    playlistPickerSongs = selectedSongs
                                }
                        }
                    ) {
                        Icon(
                            imageVector = MiuixIcons.Regular.Add,
                            contentDescription = stringResource(R.string.player_add_to_playlist),
                            tint = MiuixTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    IconButton(
                        onClick = {
                                val selectedSongs = sortedSongs.filter { it.id in selectedIds }
                                if (selectedSongs.isNotEmpty()) {
                                    pendingDeleteSongs = selectedSongs
                                } else {
                                    Toast.makeText(context, R.string.library_select_songs_first, Toast.LENGTH_SHORT).show()
                                }
                        }
                    ) {
                        Icon(
                            imageVector = MiuixIcons.Regular.Delete,
                            contentDescription = stringResource(R.string.common_delete),
                            tint = Color(0xFFE5484D),
                            modifier = Modifier.size(24.dp)
                        )
                    }
                } else {
                    IconButton(onClick = { sortExpanded = !sortExpanded }) {
                        Icon(
                            imageVector = MiuixIcons.Regular.Sort,
                            contentDescription = stringResource(R.string.common_sort),
                            tint = MiuixTheme.colorScheme.onSurface,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    IconButton(onClick = {
                        selectionMode = true
                        selectedIds = emptySet()
                    }) {
                        Icon(
                            imageVector = MiuixIcons.Regular.SelectAll,
                            contentDescription = stringResource(R.string.common_multi_select),
                            tint = MiuixTheme.colorScheme.onSurface,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    IconButton(onClick = { searchExpanded = !searchExpanded }) {
                        Icon(
                            imageVector = MiuixIcons.Basic.Search,
                            contentDescription = stringResource(R.string.common_search),
                            tint = MiuixTheme.colorScheme.onSurface,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            }
            DoubleTapScrollOverlay(
                onDoubleTap = { scrollToTopRequest++ },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                startPadding = 64.dp,
                endPadding = 104.dp
            )
        }

        AnimatedVisibility(
            visible = sortExpanded,
            enter = expandVertically(),
            exit = shrinkVertically()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp)
            ) {
                FolderSongSortMode.entries.forEach { mode ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                LibrarySortUiState.folderDetailSongSortIndex = mode.ordinal
                                scope.launch { mainViewModel.settingsManager.setFolderDetailSongSortIndex(mode.ordinal) }
                                scrollToTopRequest++
                                sortExpanded = false
                            }
                            .padding(vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = stringResource(mode.labelRes),
                            fontSize = 14.sp,
                            fontWeight = if (sortMode == mode) FontWeight.Bold else FontWeight.Normal,
                            color = if (sortMode == mode) MiuixTheme.colorScheme.primary else MiuixTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }

        if (searchExpanded) {
            EllaSearchBar(
                query = searchQuery,
                onQueryChange = { searchQuery = it },
                onSearch = { searchExpanded = false },
                placeholder = stringResource(R.string.folder_detail_search_placeholder),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 4.dp)
            )
        }

        if (childFolders.isEmpty() && sortedSongs.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = stringResource(R.string.folder_detail_empty),
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary
                )
            }
        } else {
            val listState = rememberLazyListState()
            var fastScrollJob by remember { mutableStateOf<Job?>(null) }
            LaunchedEffect(scrollToTopRequest) {
                if (scrollToTopRequest > 0) listState.animateScrollToItem(0)
            }
            val currentSongItemIndex = remember(sortedSongs, currentSong?.id, selectionMode) {
                if (selectionMode) return@remember -1
                sortedSongs.indexOfFirst { it.id == currentSong?.id }
                    .takeIf { it >= 0 }
                    ?.plus(1)
                    ?: -1
            }
            val fastIndexTargets = remember(sortedSongs) {
                sortedSongs
                    .mapIndexed { index, song -> song.indexLetter() to index }
                    .distinctBy { it.first }
                    .toMap()
            }
            Box(modifier = Modifier.fillMaxSize()) {
                Column(modifier = Modifier.fillMaxSize()) {
                    Text(
                        text = if (searchQuery.isBlank()) {
                            stringResource(R.string.folder_detail_current_summary, childFolders.size, sortedSongs.size)
                        } else {
                            stringResource(R.string.folder_detail_search_summary, sortedSongs.size)
                        },
                        fontSize = 13.sp,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(bottom = 120.dp)
                    ) {
                        itemsIndexed(
                            items = sortedSongs,
                            key = { _, song -> song.id }
                        ) { index, song ->
                            val selected = song.id in selectedIds
                            SongItem(
                                song = song,
                                isCurrent = currentSong?.id == song.id,
                                albumArtUri = mainViewModel.getAlbumArtUri(song.albumId),
                                loadCoverArt = mainViewModel::getCoverArtBitmap,
                                loadAudioInfo = mainViewModel::getAudioInfo,
                                isFavorite = song.playlistIdentityKey() in favoriteSongKeys,
                                loadSongRating = mainViewModel::getSongRating,
                                selectionMode = selectionMode,
                                selected = selected,
                                onLongClick = {
                                    selectionMode = true
                                    selectedIds = selectedIds + song.id
                                },
                                onClick = {
                                    if (selectionMode) {
                                        selectedIds = if (selected) selectedIds - song.id else selectedIds + song.id
                                    } else {
                                        playerViewModel.setPlaylist(sortedSongs, index)
                                        if (openPlayerOnPlay) onNavigateToPlayer()
                                    }
                                },
                                onAddToQueue = { playerViewModel.addToPlaylist(song) },
                                onMore = { actionSong = song }
                            )
                        }
                        if (searchQuery.isBlank()) {
                            items(childFolders, key = { it.path }) { folder ->
                                ChildFolderRow(
                                    folder = folder,
                                    onClick = { onFolderClick(folder.path) },
                                    onLongClick = { folderToBlock = folder.path }
                                )
                            }
                        }
                    }
                }
                if (sortMode == FolderSongSortMode.Title && sortedSongs.size > 30) {
                    FastIndexBar(
                        letters = sortedSongs.map { it.indexLetter() },
                        modifier = Modifier
                            .align(Alignment.CenterEnd)
                            .fillMaxHeight()
                            .padding(end = 2.dp),
                        onLetterClick = { letter ->
                            val index = fastIndexTargets[letter]
                            if (index != null) {
                                fastScrollJob?.cancel()
                                fastScrollJob = scope.launch { listState.scrollToItem(index + 1) }
                            }
                        }
                    )
                } else if (sortedSongs.size > 30) {
                    LazyListScrollIndicator(
                        state = listState,
                        modifier = Modifier
                            .align(Alignment.CenterEnd)
                            .fillMaxHeight()
                    )
                }
                LocateCurrentSongFloatingButton(
                    listState = listState,
                    currentItemIndex = currentSongItemIndex,
                    locateRequest = locateCurrentSongRequest,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(end = 22.dp, bottom = 118.dp)
                )
            }
        }

        folderToBlock?.let { folderPath ->
            FolderBlockDialog(
                folderPath = folderPath,
                onDismiss = { folderToBlock = null },
                onBlock = {
                    scope.launch {
                        mainViewModel.settingsManager.setScanExcludeFolders(
                            (blockedFolders + folderPath.normalizeFolderPath()).distinct().joinToString("；")
                        )
                        mainViewModel.scanMusic()
                    }
                    folderToBlock = null
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

        playlistPickerSongs?.let { songsToAdd ->
            WindowBottomSheet(
                show = true,
                enableNestedScroll = false,
                title = stringResource(R.string.song_more_add_to_playlist_title),
                onDismissRequest = { playlistPickerSongs = null }
            ) {
                AddSelectedSongsToPlaylistSheet(
                    playlists = playlists
                        .filterNot { it.id == FAVORITES_PLAYLIST_ID }
                        .sortedByDescending { it.createdAt },
                    songCount = songsToAdd.size,
                    onDismiss = { playlistPickerSongs = null },
                    onCreatePlaylist = {
                        createPlaylistSongs = songsToAdd
                        playlistPickerSongs = null
                    },
                    onPlaylistsConfirm = { selectedPlaylists ->
                        selectedPlaylists.forEach { playlist ->
                            mainViewModel.addSongsToPlaylist(playlist.id, songsToAdd)
                        }
                        Toast.makeText(context, context.getString(R.string.player_added_to_playlists, selectedPlaylists.size), Toast.LENGTH_SHORT).show()
                        playlistPickerSongs = null
                        selectedIds = emptySet()
                        selectionMode = false
                    }
                )
            }
        }

        createPlaylistSongs?.let { songsToAdd ->
            CreatePlaylistAndAddSelectedSheet(
                songCount = songsToAdd.size,
                onDismiss = { createPlaylistSongs = null },
                onCreate = { name ->
                    mainViewModel.createPlaylist(name) { playlist ->
                        if (playlist != null) {
                            mainViewModel.addSongsToPlaylist(playlist.id, songsToAdd)
                            Toast.makeText(context, context.getString(R.string.player_added_to_playlist_named, playlist.name), Toast.LENGTH_SHORT).show()
                            selectedIds = emptySet()
                            selectionMode = false
                        }
                    }
                    createPlaylistSongs = null
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
                mainViewModel.deleteSongs(pendingDeleteSongs)
                pendingDeleteSongs = emptyList()
                selectedIds = emptySet()
                selectionMode = false
            }
        )
    }
}

@Composable
private fun AddSelectedSongsToPlaylistSheet(
    playlists: List<UserPlaylist>,
    songCount: Int,
    onDismiss: () -> Unit,
    onCreatePlaylist: () -> Unit,
    onPlaylistsConfirm: (List<UserPlaylist>) -> Unit
) {
    var selectedPlaylistIds by remember(playlists) { mutableStateOf(emptySet<String>()) }
    val selectedPlaylists = playlists.filter { it.id in selectedPlaylistIds }
    Column(
        modifier = Modifier.padding(bottom = 18.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            text = stringResource(R.string.library_selected_count, songCount),
            fontSize = 13.sp,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
        )
        FolderSheetItem(stringResource(R.string.playlist_create_title), onCreatePlaylist)
        if (playlists.isEmpty()) {
            Text(
                text = stringResource(R.string.song_more_no_custom_playlists),
                fontSize = 14.sp,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 18.dp)
            )
        } else {
            playlists.forEach { playlist ->
                val selected = playlist.id in selectedPlaylistIds
                FolderSheetItem(
                    stringResource(
                        R.string.song_more_playlist_item_summary,
                        if (selected) "✓ " else "",
                        playlist.name,
                        playlist.songs.size
                    )
                ) {
                    selectedPlaylistIds = if (selected) {
                        selectedPlaylistIds - playlist.id
                    } else {
                        selectedPlaylistIds + playlist.id
                    }
                }
            }
        }
        if (playlists.isNotEmpty()) {
            FolderSheetItem(stringResource(R.string.song_more_done_selected, selectedPlaylistIds.size)) {
                if (selectedPlaylists.isNotEmpty()) {
                    onPlaylistsConfirm(selectedPlaylists)
                }
            }
        }
        FolderSheetItem(stringResource(R.string.common_cancel), onDismiss)
    }
}

@Composable
private fun CreatePlaylistAndAddSelectedSheet(
    songCount: Int,
    onDismiss: () -> Unit,
    onCreate: (String) -> Unit
) {
    var name by remember { mutableStateOf("") }
    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current
    LaunchedEffect(Unit) {
        delay(220L)
        focusRequester.requestFocus()
        keyboardController?.show()
    }
    WindowBottomSheet(
        show = true,
        title = stringResource(R.string.playlist_create_title),
        onDismissRequest = onDismiss
    ) {
        Column(
            modifier = Modifier.padding(bottom = 18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(
                text = stringResource(R.string.library_create_playlist_add_count, songCount),
                fontSize = 13.sp,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary
            )
            TextField(
                value = name,
                onValueChange = { name = it },
                label = stringResource(R.string.playlist_name_label),
                useLabelAsPlaceholder = true,
                singleLine = true,
                insideMargin = DpSize(12.dp, 10.dp),
                backgroundColor = MiuixTheme.colorScheme.surfaceContainer,
                cornerRadius = 12.dp,
                textStyle = TextStyle(
                    color = MiuixTheme.colorScheme.onSurface,
                    fontSize = 15.sp
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focusRequester)
            )
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                Button(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) }
                Spacer(modifier = Modifier.width(8.dp))
                Button(onClick = { onCreate(name) }) { Text(stringResource(R.string.common_create)) }
            }
        }
    }
}

@Composable
private fun FolderSheetItem(
    text: String,
    onClick: () -> Unit
) {
    Text(
        text = text,
        fontSize = 14.sp,
        color = MiuixTheme.colorScheme.onSurface,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 12.dp)
    )
}

private enum class FolderSongSortMode(val labelRes: Int) {
    Title(R.string.playlist_song_sort_title),
    FileName(R.string.playlist_song_sort_file_name),
    Duration(R.string.playlist_song_sort_duration),
    DateAdded(R.string.playlist_song_sort_date_added),
    DateAddedAsc(R.string.playlist_song_sort_date_added_asc),
    DateModified(R.string.playlist_song_sort_date_modified),
    DateModifiedAsc(R.string.playlist_song_sort_date_modified_asc),
    YearAsc(R.string.playlist_song_sort_year_asc),
    YearDesc(R.string.playlist_song_sort_year_desc)
}

private fun List<Song>.sortedByReleaseDate(ascending: Boolean): List<Song> {
    val comparator = if (ascending) {
        compareBy<Song> { it.releaseYearOrNull() == null }
            .thenBy { it.releaseYearOrNull() ?: Int.MAX_VALUE }
    } else {
        compareBy<Song> { it.releaseYearOrNull() == null }
            .thenByDescending { it.releaseYearOrNull() ?: Int.MIN_VALUE }
    }
    return sortedWith(
        comparator
            .thenBy { it.album.lowercase(Locale.ROOT) }
            .thenBy { if (it.discNumber > 0) it.discNumber else Int.MAX_VALUE }
            .thenBy { if (it.trackNumber > 0) it.trackNumber else Int.MAX_VALUE }
            .thenBy { it.title.lowercase(Locale.ROOT) }
    )
}

private fun Song.releaseYearOrNull(): Int? =
    Regex("""\d{4}""").find(year)?.value?.toIntOrNull()

@Composable
private fun ChildFolderRow(
    folder: FolderTreeEntry,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(MiuixTheme.colorScheme.surfaceContainer)
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
            .padding(horizontal = 18.dp, vertical = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        FolderOutlineIcon(
            tint = MiuixTheme.colorScheme.primary,
            modifier = Modifier.size(42.dp)
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = folder.name,
                fontSize = 17.sp,
                lineHeight = 22.sp,
                fontWeight = FontWeight.SemiBold,
                color = MiuixTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.folder_child_summary, folder.songCount, folder.path),
                fontSize = 13.sp,
                lineHeight = 17.sp,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
        Icon(
            imageVector = MiuixIcons.Basic.ArrowRight,
            contentDescription = null,
            tint = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            modifier = Modifier.size(22.dp)
        )
    }
}

private fun Song.indexLetter(): String {
    val first = title.musicSortKey().firstOrNull()?.uppercaseChar()
    return if (first != null && first in 'A'..'Z') first.toString() else "#"
}

private fun String.musicSortKey(): String {
    val text = trim()
    if (text.isBlank()) return ""
    if (text.isAsciiSortable()) return text.lowercase(Locale.ROOT)

    FolderSortKeyCache[text]?.let { return it }

    val latin = runCatching {
        FolderSortTransliterator.value.transliterate(text)
    }.getOrDefault(text)

    return latin.lowercase(Locale.ROOT).also {
        FolderSortKeyCache[text] = it
    }
}

private fun String.isAsciiSortable(): Boolean {
    return all { it.code in 0x20..0x7E }
}

private object FolderSortTransliterator {
    val value: Transliterator by lazy {
        Transliterator.getInstance("Any-Latin; Latin-ASCII; NFD; [:Nonspacing Mark:] Remove; NFC")
    }
}

private object FolderSortKeyCache {
    private const val MaxSize = 4096

    private val values = object : LinkedHashMap<String, String>(MaxSize, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, String>?): Boolean {
            return size > MaxSize
        }
    }

    operator fun get(key: String): String? = synchronized(values) { values[key] }

    operator fun set(key: String, value: String) {
        synchronized(values) { values[key] = value }
    }
}
