package com.ella.music.ui.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import android.app.Activity
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.icu.text.Transliterator
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.sp
import com.ella.music.R
import com.ella.music.data.SettingsManager
import com.ella.music.data.NeteaseKeyInfo
import com.ella.music.data.decodeNeteaseKey
import com.ella.music.data.detailedAudioInfo
import com.ella.music.data.model.AudioInfo
import com.ella.music.data.model.FAVORITES_PLAYLIST_ID
import com.ella.music.data.model.Song
import com.ella.music.data.model.SongTagInfo
import com.ella.music.data.model.UserPlaylist
import com.ella.music.data.model.albumIdentityId
import com.ella.music.data.model.playlistIdentityKey
import com.ella.music.data.neteaseAlbumUrl
import com.ella.music.data.neteaseArtistUrl
import com.ella.music.data.neteaseSongUrl
import com.ella.music.data.splitArtistNames
import com.ella.music.data.tagIdentityKey
import com.ella.music.ui.LibrarySortUiState
import com.ella.music.ui.components.ConfirmDangerDialog
import com.ella.music.ui.components.EllaSearchBar
import com.ella.music.ui.components.ArtistPickerSheet
import com.ella.music.ui.components.DoubleTapScrollOverlay
import com.ella.music.ui.components.FastIndexBar
import com.ella.music.ui.components.LazyListScrollIndicator
import com.ella.music.ui.components.SongItem
import com.ella.music.ui.components.SongMoreActionHost
import com.ella.music.ui.components.TagEditorOption
import com.ella.music.ui.components.TagEditorOptionKind
import com.ella.music.ui.components.buildTagEditorOptions
import com.ella.music.ui.components.ellaPageBackground
import com.ella.music.ui.components.launchTagEditorOption
import com.ella.music.ui.components.openSongSpectrumWithAspectPro
import com.ella.music.ui.components.shareLocalSong
import com.ella.music.viewmodel.MainViewModel
import com.ella.music.viewmodel.PlayerViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.FloatingActionButton
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.SmallTopAppBar
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.basic.Search
import top.yukonga.miuix.kmp.icon.extended.Add
import top.yukonga.miuix.kmp.icon.extended.Delete
import top.yukonga.miuix.kmp.icon.extended.Refresh
import top.yukonga.miuix.kmp.icon.extended.SelectAll
import top.yukonga.miuix.kmp.icon.extended.Sort
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.window.WindowBottomSheet
import kotlinx.coroutines.Job
import java.util.Locale

@Composable
fun LibraryScreen(
    mainViewModel: MainViewModel,
    playerViewModel: PlayerViewModel,
    onNavigateToPlayer: () -> Unit,
    onNavigateToAbout: () -> Unit,
    onNavigateToAlbum: (Long) -> Unit = {},
    onNavigateToArtist: (String) -> Unit = {}
) {
    val songs by mainViewModel.songs.collectAsState()
    val playlists by mainViewModel.playlists.collectAsState()
    val currentSong by playerViewModel.currentSong.collectAsState()
    val favoriteSongKeys by playerViewModel.favoriteSongKeys.collectAsState()
    val locateCurrentSongRequest by playerViewModel.locateCurrentSongRequest.collectAsState()
    val isScanning by mainViewModel.isScanning.collectAsState()
    val scanProgress by mainViewModel.scanProgress.collectAsState()
    val context = LocalContext.current
    val settingsManager = remember(context) { SettingsManager(context) }
    val openPlayerOnPlay by settingsManager.openPlayerOnPlay.collectAsState(initial = true)

    var searchQuery by remember { mutableStateOf("") }
    var searchExpanded by remember { mutableStateOf(false) }
    var sortExpanded by remember { mutableStateOf(false) }
    val sortIndex by settingsManager.librarySongSortIndex.collectAsState(initial = LibrarySortUiState.librarySongSortIndex)
    val sortMode = HomeSortMode.entries.getOrElse(sortIndex) { HomeSortMode.Title }
    var selectionMode by remember { mutableStateOf(false) }
    var selectedIds by remember { mutableStateOf(setOf<Long>()) }
    var actionSong by remember { mutableStateOf<Song?>(null) }
    var artistChoices by remember { mutableStateOf<List<String>>(emptyList()) }
    var playlistPickerSongs by remember { mutableStateOf<List<Song>?>(null) }
    var createPlaylistSongs by remember { mutableStateOf<List<Song>?>(null) }
    var tagEditorSong by remember { mutableStateOf<Song?>(null) }
    var songInfoSheetSong by remember { mutableStateOf<Song?>(null) }
    var aiInterpretationSong by remember { mutableStateOf<Song?>(null) }
    var listCoversEnabled by remember { mutableStateOf(false) }
    var pendingSystemDeleteSongs by remember { mutableStateOf<List<Song>>(emptyList()) }
    var pendingConfirmDeleteSongs by remember { mutableStateOf<List<Song>>(emptyList()) }
    var scrollToTopRequest by remember { mutableStateOf(0) }
    val scope = rememberCoroutineScope()
    fun navigateToArtistOrChoose(artistText: String) {
        val artists = splitArtistNames(artistText)
            .filterNot { it.equals("Unknown", ignoreCase = true) }
            .distinctBy { it.tagIdentityKey() }
        when (artists.size) {
            0 -> Toast.makeText(context, context.getString(R.string.player_no_artist_jump), Toast.LENGTH_SHORT).show()
            1 -> onNavigateToArtist(artists.first())
            else -> artistChoices = artists
        }
    }
    val deleteRequestLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        val songsToDelete = pendingSystemDeleteSongs
        pendingSystemDeleteSongs = emptyList()
        if (result.resultCode == Activity.RESULT_OK && songsToDelete.isNotEmpty()) {
            mainViewModel.removeSongsFromLibrary(songsToDelete)
            Toast.makeText(
                context,
                context.getString(R.string.library_deleted_songs, songsToDelete.size),
                Toast.LENGTH_SHORT
            ).show()
        } else if (songsToDelete.isNotEmpty()) {
            Toast.makeText(context, context.getString(R.string.library_delete_cancelled), Toast.LENGTH_SHORT).show()
        }
    }

    fun requestDeleteSongs(songsToDelete: List<Song>) {
        if (songsToDelete.isEmpty()) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val uris = songsToDelete
                .filter { it.id > 0L }
                .map { song ->
                    ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, song.id)
                }
            if (uris.isNotEmpty()) {
                runCatching {
                    pendingSystemDeleteSongs = songsToDelete
                    val request = MediaStore.createDeleteRequest(context.contentResolver, uris)
                    deleteRequestLauncher.launch(
                        IntentSenderRequest.Builder(request.intentSender).build()
                    )
                }.onFailure {
                    pendingSystemDeleteSongs = emptyList()
                    mainViewModel.deleteSongs(songsToDelete)
                    Toast.makeText(
                        context,
                        context.getString(R.string.library_deleting_songs, songsToDelete.size),
                        Toast.LENGTH_SHORT
                    ).show()
                }
                return
            }
        }
        mainViewModel.deleteSongs(songsToDelete)
        Toast.makeText(
            context,
            context.getString(R.string.library_deleting_songs, songsToDelete.size),
            Toast.LENGTH_SHORT
        ).show()
    }

    LaunchedEffect(Unit) {
        delay(260L)
        listCoversEnabled = true
    }

    val filteredSongs = remember(songs, searchQuery) {
        if (searchQuery.isBlank()) songs
        else songs.filter {
            it.title.contains(searchQuery, ignoreCase = true) ||
                it.artist.contains(searchQuery, ignoreCase = true) ||
                it.album.contains(searchQuery, ignoreCase = true)
        }
    }
    val sortedResult by produceState<HomeSortedSongs?>(
        initialValue = null,
        filteredSongs,
        sortMode
    ) {
        value = withContext(Dispatchers.Default) {
            when (sortMode) {
                HomeSortMode.Title -> filteredSongs.sortedByMusicKey { it.title }
                HomeSortMode.FileName -> filteredSongs.sortedByMusicKey { it.fileName.ifBlank { it.path.substringAfterLast('/') } }
                HomeSortMode.YearAsc -> HomeSortedSongs(filteredSongs.sortedByReleaseDate(ascending = true), emptyMap())
                HomeSortMode.YearDesc -> HomeSortedSongs(filteredSongs.sortedByReleaseDate(ascending = false), emptyMap())
                HomeSortMode.DateAdded -> HomeSortedSongs(filteredSongs.sortedByDescending { it.dateAdded }, emptyMap())
                HomeSortMode.DateAddedAsc -> HomeSortedSongs(filteredSongs.sortedBy { it.dateAdded }, emptyMap())
                HomeSortMode.DateModified -> HomeSortedSongs(filteredSongs.sortedByDescending { it.dateModified }, emptyMap())
                HomeSortMode.DateModifiedAsc -> HomeSortedSongs(filteredSongs.sortedBy { it.dateModified }, emptyMap())
            }
        }
    }
    val sortedSongs = sortedResult?.songs.orEmpty()
    val sortKeysBySongId = sortedResult?.sortKeysBySongId.orEmpty()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(ellaPageBackground())
            .windowInsetsPadding(WindowInsets.statusBars)
    ) {
        Box {
            SmallTopAppBar(
                title = stringResource(R.string.tab_library),
                color = ellaPageBackground(),
                navigationIcon = {
                    if (!selectionMode) {
                        IconButton(
                            onClick = {
                                if (!isScanning) mainViewModel.scanMusic()
                            }
                        ) {
                            Icon(
                                imageVector = MiuixIcons.Regular.Refresh,
                                contentDescription = stringResource(R.string.library_refresh),
                                tint = MiuixTheme.colorScheme.onSurface,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                },
                actions = {
                    if (selectionMode) {
                        IconButton(onClick = {
                            val selectedSongs = sortedSongs.filter { it.id in selectedIds }
                            if (selectedSongs.isEmpty()) {
                                Toast.makeText(context, context.getString(R.string.library_select_songs_first), Toast.LENGTH_SHORT).show()
                            } else {
                                playlistPickerSongs = selectedSongs
                            }
                        }) {
                            Icon(
                                imageVector = MiuixIcons.Regular.Add,
                                contentDescription = stringResource(R.string.category_playlist),
                                tint = MiuixTheme.colorScheme.primary,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        IconButton(onClick = {
                            val selectedSongs = sortedSongs.filter { it.id in selectedIds }
                            if (selectedSongs.isEmpty()) {
                                Toast.makeText(context, context.getString(R.string.library_select_songs_first), Toast.LENGTH_SHORT).show()
                            } else {
                                pendingConfirmDeleteSongs = selectedSongs
                            }
                        }) {
                            Icon(
                                imageVector = MiuixIcons.Regular.Delete,
                                contentDescription = stringResource(R.string.common_delete),
                                tint = Color(0xFFE5484D),
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        IconButton(onClick = {
                            selectedIds = emptySet()
                            selectionMode = false
                        }) {
                            Text(text = stringResource(R.string.common_cancel), fontSize = 13.sp, color = MiuixTheme.colorScheme.onSurface)
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
                                contentDescription = stringResource(R.string.common_select_all),
                                tint = MiuixTheme.colorScheme.onSurface,
                                modifier = Modifier.size(24.dp)
                            )
                        }
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
            )
            DoubleTapScrollOverlay(
                onDoubleTap = { scrollToTopRequest++ },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
            )
        }

        BackHandler(enabled = selectionMode || searchExpanded || sortExpanded) {
            when {
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

        AnimatedVisibility(
            visible = sortExpanded && !selectionMode,
            enter = expandVertically(),
            exit = shrinkVertically()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp)
            ) {
                HomeSortMode.entries.forEach { mode ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                LibrarySortUiState.librarySongSortIndex = mode.ordinal
                                scope.launch { settingsManager.setLibrarySongSortIndex(mode.ordinal) }
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
                placeholder = stringResource(R.string.library_search_placeholder),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 4.dp)
            )
        }

        AnimatedVisibility(
            visible = isScanning && scanProgress > 0,
            enter = expandVertically(),
            exit = shrinkVertically()
        ) {
            Text(
                    text = stringResource(R.string.library_scanning_count, scanProgress),
                fontSize = 13.sp,
                color = MiuixTheme.colorScheme.primary,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )
        }

        if (songs.isEmpty() && !isScanning) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = stringResource(R.string.library_empty_hint),
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary
                )
            }
        } else if (sortedResult == null) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = stringResource(R.string.library_organizing),
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary
                )
            }
        } else {
            val listState = rememberLazyListState()
            var fastScrollJob by remember { mutableStateOf<Job?>(null) }
            var handledLocateRequest by remember { mutableStateOf(locateCurrentSongRequest) }
            val currentSongIndex = remember(sortedSongs, currentSong?.id) {
                sortedSongs.indexOfFirst { it.id == currentSong?.id }
            }
            val showLocateCurrentSongButton by remember(currentSongIndex, selectionMode) {
                derivedStateOf {
                    if (selectionMode || currentSongIndex < 0) return@derivedStateOf false
                    val visibleIndexes = listState.layoutInfo.visibleItemsInfo.map { it.index }
                    if (visibleIndexes.isEmpty()) return@derivedStateOf false
                    visibleIndexes.none { kotlin.math.abs(it - currentSongIndex) <= 2 }
                }
            }

            LaunchedEffect(locateCurrentSongRequest) {
                if (locateCurrentSongRequest <= 0 || locateCurrentSongRequest == handledLocateRequest) return@LaunchedEffect
                handledLocateRequest = locateCurrentSongRequest
                if (currentSongIndex >= 0) listState.animateScrollToItem(currentSongIndex)
            }

            LaunchedEffect(scrollToTopRequest) {
                if (scrollToTopRequest > 0) listState.animateScrollToItem(0)
            }

            val fastIndexTargets = remember(sortedSongs, sortKeysBySongId) {
                sortedSongs
                    .mapIndexed { index, song -> song.indexLetter(sortKeysBySongId[song.id]) to index }
                    .distinctBy { it.first }
                    .toMap()
            }

            Box(modifier = Modifier.fillMaxSize()) {
                Column(modifier = Modifier.fillMaxSize()) {
                    Text(
                        text = if (selectionMode) {
                            stringResource(R.string.library_selected_count, selectedIds.size)
                        } else {
                            stringResource(
                                R.string.library_song_count_sorted,
                                sortedSongs.size,
                                stringResource(sortMode.labelRes)
                            )
                        },
                        fontSize = 13.sp,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )

                    LazyColumn(
                        state = listState,
                        contentPadding = PaddingValues(bottom = 160.dp)
                    ) {
                        items(
                            items = sortedSongs,
                            key = { it.id }
                        ) { song ->
                            val selected = song.id in selectedIds

                            SongItem(
                                song = song,
                                isCurrent = currentSong?.id == song.id,
                                albumArtUri = if (listCoversEnabled) mainViewModel.getAlbumArtUri(song.albumId) else null,
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
                                        selectedIds = if (selected) {
                                            selectedIds - song.id
                                        } else {
                                            selectedIds + song.id
                                        }
                                    } else {
                                        playerViewModel.setPlaylist(sortedSongs, sortedSongs.indexOf(song))
                                        if (openPlayerOnPlay) onNavigateToPlayer()
                                    }
                                },
                                onMore = { actionSong = song }
                            )
                        }
                    }
                }

                if (sortMode == HomeSortMode.Title && sortedSongs.size > 30) {
                    FastIndexBar(
                        letters = sortedSongs.map { it.indexLetter(sortKeysBySongId[it.id]) },
                        modifier = Modifier
                            .align(Alignment.CenterEnd)
                            .fillMaxHeight()
                            .padding(end = 2.dp),
                        onLetterClick = { letter ->
                            val index = fastIndexTargets[letter]
                            if (index != null) {
                                fastScrollJob?.cancel()
                                fastScrollJob = scope.launch {
                                    listState.scrollToItem(index)
                                }
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

                androidx.compose.animation.AnimatedVisibility(
                    visible = showLocateCurrentSongButton,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(end = 22.dp, bottom = 176.dp)
                ) {
                    FloatingActionButton(
                        onClick = { playerViewModel.requestLocateCurrentSong() },
                        minWidth = 46.dp,
                        minHeight = 46.dp,
                        containerColor = MiuixTheme.colorScheme.primary
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_my_location),
                            contentDescription = stringResource(R.string.player_locate_current_song),
                            tint = MiuixTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(21.dp)
                        )
                    }
                }
            }
        }

        SongMoreActionHost(
            actionSong = actionSong,
            mainViewModel = mainViewModel,
            playerViewModel = playerViewModel,
            onDismissAction = { actionSong = null },
            onNavigateToAlbum = onNavigateToAlbum,
            onNavigateToArtist = onNavigateToArtist,
            onDeleteSong = { song -> requestDeleteSongs(listOf(song)) }
        )

        if (artistChoices.isNotEmpty()) {
            WindowBottomSheet(
                show = true,
                enableNestedScroll = false,
                title = stringResource(R.string.song_more_select_artist),
                onDismissRequest = { artistChoices = emptyList() }
            ) {
                ArtistPickerSheet(
                    artists = artistChoices,
                    onArtistSelected = { artist ->
                        artistChoices = emptyList()
                        onNavigateToArtist(artist)
                    },
                    onDismiss = { artistChoices = emptyList() }
                )
            }
        }

        playlistPickerSongs?.let { songsToAdd ->
            WindowBottomSheet(
                show = true,
                enableNestedScroll = false,
                title = stringResource(R.string.song_more_add_to_playlist_title),
                onDismissRequest = { playlistPickerSongs = null }
            ) {
                AddToPlaylistMenu(
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
                        Toast.makeText(
                            context,
                            context.getString(R.string.player_added_to_playlists, selectedPlaylists.size),
                            Toast.LENGTH_SHORT
                        ).show()
                        playlistPickerSongs = null
                        selectedIds = emptySet()
                        selectionMode = false
                    }
                )
            }
        }

        createPlaylistSongs?.let { songsToAdd ->
            CreatePlaylistAndAddSheet(
                songCount = songsToAdd.size,
                onDismiss = { createPlaylistSongs = null },
                onCreate = { name ->
                    mainViewModel.createPlaylist(name) { playlist ->
                        if (playlist != null) {
                            mainViewModel.addSongsToPlaylist(playlist.id, songsToAdd)
                            Toast.makeText(
                                context,
                                context.getString(R.string.player_added_to_playlist_named, playlist.name),
                                Toast.LENGTH_SHORT
                            ).show()
                            selectedIds = emptySet()
                            selectionMode = false
                        }
                    }
                    createPlaylistSongs = null
                }
            )
        }

        ConfirmDangerDialog(
            show = pendingConfirmDeleteSongs.isNotEmpty(),
            title = stringResource(R.string.song_more_delete_song_title),
            message = stringResource(R.string.library_delete_selected_message, pendingConfirmDeleteSongs.size),
            confirmText = stringResource(R.string.song_more_delete_permanently),
            onDismiss = { pendingConfirmDeleteSongs = emptyList() },
            onConfirm = {
                requestDeleteSongs(pendingConfirmDeleteSongs)
                pendingConfirmDeleteSongs = emptyList()
                selectedIds = emptySet()
                selectionMode = false
            }
        )

        tagEditorSong?.let { song ->
            WindowBottomSheet(
                show = true,
                enableNestedScroll = false,
                title = stringResource(R.string.song_more_edit_tags_title),
                onDismissRequest = { tagEditorSong = null }
            ) {
                SongTagEditorMenu(
                    song = song,
                    options = buildTagEditorOptions(context, song).filter { it.kind == TagEditorOptionKind.Metadata },
                    onDismiss = { tagEditorSong = null },
                    onOptionClick = { option ->
                        launchTagEditorOption(context, option)
                        tagEditorSong = null
                    }
                )
            }
        }

        songInfoSheetSong?.let { song ->
            WindowBottomSheet(
                show = true,
                enableNestedScroll = false,
                title = stringResource(R.string.player_song_details),
                onDismissRequest = { songInfoSheetSong = null }
            ) {
                SongInfoMenu(
                    song = song,
                    audioInfoLoader = mainViewModel::getAudioInfo,
                    tagInfoLoader = mainViewModel::getSongTagInfo,
                    onAiInterpret = {
                        songInfoSheetSong = null
                        aiInterpretationSong = song
                    },
                    onDismiss = { songInfoSheetSong = null }
                )
            }
        }

        aiInterpretationSong?.let { song ->
            SongAiInterpretationMenu(
                song = song,
                mainViewModel = mainViewModel,
                onDismiss = { aiInterpretationSong = null }
            )
        }
    }
}


@Composable
private fun SongActionMenu(
    song: Song,
    onDismiss: () -> Unit,
    onAddToPlaylist: () -> Unit,
    onPlayNext: () -> Unit,
    onShare: () -> Unit,
    onSpectrum: () -> Unit,
    onInfo: () -> Unit,
    onAiInterpret: () -> Unit,
    onArtist: () -> Unit,
    onAlbum: () -> Unit,
    onEditTag: () -> Unit,
    onDelete: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
            .background(MiuixTheme.colorScheme.background.copy(alpha = 0.98f))
            .verticalScroll(rememberScrollState())
            .navigationBarsPadding()
            .padding(horizontal = 18.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        SheetHandle()
        Text(
            text = song.title,
            fontSize = 18.sp,
            fontWeight = FontWeight.ExtraBold,
            color = MiuixTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp)
        )
        LibraryMenuItem(stringResource(R.string.song_more_add_to_playlist), onAddToPlaylist)
        LibraryMenuItem(stringResource(R.string.song_more_play_next), onPlayNext)
        LibraryMenuItem(stringResource(R.string.common_share), onShare)
        LibraryMenuItem(stringResource(R.string.song_more_view_spectrum), onSpectrum)
        LibraryMenuItem(stringResource(R.string.song_more_ai_title), onAiInterpret)
        LibraryMenuItem(stringResource(R.string.song_more_view_song_info), onInfo)
        LibraryMenuItem(
            stringResource(
                R.string.song_more_artist_entry,
                song.artist.ifBlank { stringResource(R.string.player_unknown_artist) }
            ),
            onArtist
        )
        LibraryMenuItem(
            stringResource(
                R.string.song_more_album_entry,
                song.album.ifBlank { stringResource(R.string.player_unknown_album) }
            ),
            onAlbum
        )
        LibraryMenuItem(stringResource(R.string.song_more_edit_tags_title), onEditTag)
        LibraryMenuItem(stringResource(R.string.song_more_delete_permanently), onDelete, danger = true)
        LibraryMenuItem(stringResource(R.string.common_cancel), onDismiss)
    }
}

@Composable
private fun SongInfoMenu(
    song: Song,
    audioInfoLoader: (Song) -> AudioInfo,
    tagInfoLoader: (Song) -> SongTagInfo,
    onAiInterpret: () -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var showNeteaseKeyInfo by remember(song.id) { mutableStateOf(false) }
    val audioInfo by produceState<AudioInfo?>(initialValue = null, song.id, song.dateModified, song.fileSize) {
        value = withContext(Dispatchers.IO) { audioInfoLoader(song) }
    }
    val tagInfo by produceState<SongTagInfo?>(initialValue = null, song.id, song.dateModified, song.fileSize) {
        value = withContext(Dispatchers.IO) { tagInfoLoader(song) }
    }
    val neteaseInfo = remember(tagInfo?.neteaseKey) { decodeNeteaseKey(tagInfo?.neteaseKey.orEmpty()) }

    if (showNeteaseKeyInfo && neteaseInfo != null) {
        NeteaseKeyInfoMenu(
            info = neteaseInfo,
            onOpenUrl = { url -> openNeteaseUrl(context, url) },
            onBack = { showNeteaseKeyInfo = false },
            onDismiss = onDismiss
        )
        return
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
            .background(MiuixTheme.colorScheme.background.copy(alpha = 0.98f))
            .verticalScroll(rememberScrollState())
            .navigationBarsPadding()
            .padding(horizontal = 18.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        SheetHandle()
        Text(
            text = stringResource(R.string.player_song_details),
            fontSize = 18.sp,
            fontWeight = FontWeight.ExtraBold,
            color = MiuixTheme.colorScheme.onSurface,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp)
        )
        LibraryMenuItem(stringResource(R.string.song_more_ai_title), onAiInterpret)
        SongInfoRow(stringResource(R.string.player_detail_song), tagInfo?.title?.ifBlank { song.title } ?: song.title)
        SongInfoRow(stringResource(R.string.player_detail_artist), tagInfo?.artist?.ifBlank { song.artist } ?: song.artist)
        SongInfoRow(stringResource(R.string.player_detail_album), tagInfo?.album?.ifBlank { song.album } ?: song.album)
        SongInfoRow(stringResource(R.string.song_more_detail_album_artist), tagInfo?.albumArtist.orEmpty())
        SongInfoRow(stringResource(R.string.song_more_detail_genre), tagInfo?.genre?.ifBlank { song.genre }.orEmpty())
        SongInfoRow(stringResource(R.string.song_more_detail_year), tagInfo?.year?.ifBlank { song.year }.orEmpty())
        SongInfoRow(stringResource(R.string.player_detail_composer), tagInfo?.composer?.ifBlank { song.composer }.orEmpty())
        SongInfoRow(stringResource(R.string.player_detail_lyricist), tagInfo?.lyricist?.ifBlank { song.lyricist }.orEmpty())
        SongInfoRow(stringResource(R.string.player_detail_comment), tagInfo?.displayComment.orEmpty())
        if (!tagInfo?.neteaseKey.isNullOrBlank()) {
            SongInfoActionRow(
                label = stringResource(R.string.song_more_netease_key),
                value = neteaseInfo?.musicName?.ifBlank { null }
                    ?: neteaseInfo?.musicId?.takeIf { it.isNotBlank() }?.let {
                        stringResource(R.string.song_more_netease_song_id, it)
                    }
                    ?: stringResource(R.string.song_more_view_netease_info),
                onClick = { showNeteaseKeyInfo = true }
            )
        }
        SongInfoRow(stringResource(R.string.song_more_detail_format), audioInfo?.let { detailedAudioInfo(it) }.orEmpty())
        SongInfoRow(stringResource(R.string.song_more_detail_duration), song.durationText)
        SongInfoRow(stringResource(R.string.song_more_detail_size), formatLibraryFileSize(song.fileSize))
        SongInfoRow(stringResource(R.string.song_more_detail_file_name), song.fileName.ifBlank { song.path.substringAfterLast('/') })
        SongInfoRow(stringResource(R.string.song_more_detail_path), song.path)
    }
}

@Composable
private fun SongAiInterpretationMenu(
    song: Song,
    mainViewModel: MainViewModel,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val settingsManager = remember(context) { SettingsManager(context) }
    val openAiApiKey by settingsManager.openAiApiKey.collectAsState(initial = "")
    val missingApiKeyText = stringResource(R.string.library_ai_missing_api_key)
    val aiFailedText = stringResource(R.string.song_more_ai_failed)
    var requestKey by remember(song.id) { mutableStateOf(0) }
    var isLoading by remember(song.id) { mutableStateOf(false) }
    var resultText by remember(song.id) { mutableStateOf("") }
    var errorText by remember(song.id) { mutableStateOf<String?>(null) }

    LaunchedEffect(song.id, requestKey, openAiApiKey) {
        if (openAiApiKey.isBlank()) {
            isLoading = false
            resultText = ""
            errorText = missingApiKeyText
            return@LaunchedEffect
        }
        isLoading = true
        errorText = null
        resultText = ""
        runCatching {
            mainViewModel.interpretSongWithOpenAi(song)
        }.onSuccess {
            resultText = it
        }.onFailure {
            errorText = it.message ?: aiFailedText
        }
        isLoading = false
    }

    WindowBottomSheet(
        show = true,
        title = stringResource(R.string.song_more_ai_title),
        onDismissRequest = onDismiss
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.78f)
                .verticalScroll(rememberScrollState())
                .padding(bottom = 18.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = song.title,
                fontSize = 13.sp,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 4.dp)
            )

            when {
                isLoading -> {
                    SongInfoRow(
                        stringResource(R.string.library_status_label),
                        stringResource(R.string.library_ai_loading)
                    )
                }
                errorText != null -> {
                    SongInfoRow(stringResource(R.string.library_status_label), errorText.orEmpty())
                    LibraryMenuItem(stringResource(R.string.library_retry), onClick = { requestKey++ })
                }
                resultText.isNotBlank() -> {
                    Text(
                        text = resultText,
                        fontSize = 14.sp,
                        lineHeight = 22.sp,
                        color = MiuixTheme.colorScheme.onSurface,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(MiuixTheme.colorScheme.surfaceContainer.copy(alpha = 0.72f))
                            .padding(horizontal = 16.dp, vertical = 14.dp)
                    )
                    LibraryMenuItem(stringResource(R.string.library_reinterpret), onClick = { requestKey++ })
                }
            }

            LibraryMenuItem(stringResource(R.string.common_close), onDismiss)
        }
    }
}

@Composable
private fun NeteaseKeyInfoMenu(
    info: NeteaseKeyInfo,
    onOpenUrl: (String) -> Unit,
    onBack: () -> Unit,
    onDismiss: () -> Unit
) {
    var showArtistPicker by remember(info) { mutableStateOf(false) }
    val neteaseArtists = remember(info) { info.artists.filter { it.id.isNotBlank() } }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .fillMaxHeight(0.88f)
            .clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
            .background(MiuixTheme.colorScheme.background.copy(alpha = 0.98f))
            .verticalScroll(rememberScrollState())
            .navigationBarsPadding()
            .padding(horizontal = 18.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        SheetHandle()
        Text(
            text = if (showArtistPicker) stringResource(R.string.player_choose_netease_artist) else stringResource(R.string.song_more_netease_key),
            fontSize = 18.sp,
            fontWeight = FontWeight.ExtraBold,
            color = MiuixTheme.colorScheme.onSurface,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp)
        )
        if (showArtistPicker) {
            neteaseArtists.forEach { artist ->
                LibraryMenuItem(
                    text = artist.name.ifBlank { "ID ${artist.id}" },
                    onClick = { onOpenUrl(neteaseArtistUrl(artist.id)) }
                )
            }
            LibraryMenuItem(stringResource(R.string.song_more_back_to_netease_key), onClick = { showArtistPicker = false })
            return@Column
        }
        if (!info.hasDecodedContent) {
            SongInfoRow(stringResource(R.string.library_status_label), stringResource(R.string.library_netease_info_unavailable))
        }
        if (info.musicId.isNotBlank()) {
            SongInfoActionRow(
                label = stringResource(R.string.player_netease_song_page),
                value = listOf(info.musicName, "ID ${info.musicId}").filter { it.isNotBlank() }.joinToString(" · "),
                onClick = { onOpenUrl(neteaseSongUrl(info.musicId)) }
            )
        }
        info.aliases
            .joinToString(" / ")
            .takeIf { it.isNotBlank() }
            ?.let { SongInfoRow(stringResource(R.string.song_more_alias), it) }
        if (info.albumId.isNotBlank()) {
            SongInfoActionRow(
                label = stringResource(R.string.player_netease_album_page),
                value = listOf(info.albumName, "ID ${info.albumId}").filter { it.isNotBlank() }.joinToString(" · "),
                onClick = { onOpenUrl(neteaseAlbumUrl(info.albumId)) }
            )
        }
        val artistSummary = info.artists
            .joinToString(" / ") { it.name.ifBlank { it.id } }
            .takeIf { it.isNotBlank() }
        if (neteaseArtists.isNotEmpty()) {
            SongInfoActionRow(
                label = stringResource(R.string.player_netease_artist_page),
                value = artistSummary.orEmpty(),
                onClick = {
                    if (neteaseArtists.size == 1) {
                        onOpenUrl(neteaseArtistUrl(neteaseArtists.first().id))
                    } else {
                        showArtistPicker = true
                    }
                }
            )
        } else {
            artistSummary?.let { SongInfoRow(stringResource(R.string.player_netease_artist_page), it) }
        }
        SongInfoRow(stringResource(R.string.player_detail_comment), info.comment)
        SongInfoRow(stringResource(R.string.song_more_raw_netease_key), info.raw)
        SongInfoRow(stringResource(R.string.library_decoded_json), info.decodedJson)
        LibraryMenuItem(stringResource(R.string.library_back_to_song_info), onBack)
    }
}

@Composable
private fun SongInfoActionRow(label: String, value: String, onClick: () -> Unit) {
    if (value.isBlank()) return
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .background(MiuixTheme.colorScheme.primary.copy(alpha = 0.12f))
            .padding(horizontal = 16.dp, vertical = 10.dp)
    ) {
        Text(
            text = label,
            fontSize = 12.sp,
            color = MiuixTheme.colorScheme.primary
        )
        Text(
            text = value,
            fontSize = 14.sp,
            color = MiuixTheme.colorScheme.onSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 2.dp)
        )
    }
}

private fun openNeteaseUrl(context: Context, url: String) {
    runCatching {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }.onFailure {
        Toast.makeText(context, context.getString(R.string.library_open_netease_failed), Toast.LENGTH_SHORT).show()
    }
}

@Composable
private fun SongInfoRow(label: String, value: String) {
    if (value.isBlank()) return
    val pathLabel = stringResource(R.string.song_more_detail_path)
    val rawNeteaseKeyLabel = stringResource(R.string.song_more_raw_netease_key)
    val decodedJsonLabel = stringResource(R.string.library_decoded_json)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(MiuixTheme.colorScheme.surfaceContainer.copy(alpha = 0.62f))
            .padding(horizontal = 16.dp, vertical = 10.dp)
    ) {
        Text(
            text = label,
            fontSize = 12.sp,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary
        )
        Text(
            text = value,
            fontSize = 14.sp,
            color = MiuixTheme.colorScheme.onSurface,
            maxLines = when (label) {
                pathLabel -> 3
                rawNeteaseKeyLabel, decodedJsonLabel -> 6
                else -> 2
            },
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 2.dp)
        )
    }
}

@Composable
private fun AddToPlaylistMenu(
    playlists: List<UserPlaylist>,
    songCount: Int,
    onDismiss: () -> Unit,
    onCreatePlaylist: () -> Unit,
    onPlaylistsConfirm: (List<UserPlaylist>) -> Unit
) {
    var selectedPlaylistIds by remember(playlists) { mutableStateOf(emptySet<String>()) }
    val selectedPlaylists = playlists.filter { it.id in selectedPlaylistIds }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
            .background(MiuixTheme.colorScheme.background.copy(alpha = 0.98f))
            .navigationBarsPadding()
            .padding(horizontal = 18.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        SheetHandle()
        Text(
            text = stringResource(R.string.library_add_to_playlist_count, songCount),
            fontSize = 18.sp,
            fontWeight = FontWeight.ExtraBold,
            color = MiuixTheme.colorScheme.onSurface,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp)
        )
        LibraryMenuItem(stringResource(R.string.song_more_create_playlist), onCreatePlaylist)
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
                LibraryMenuItem(
                    text = stringResource(
                        R.string.song_more_playlist_item_summary,
                        if (selected) "\u2713 " else "",
                        playlist.name,
                        playlist.songs.size
                    ),
                    onClick = {
                        selectedPlaylistIds = if (selected) {
                            selectedPlaylistIds - playlist.id
                        } else {
                            selectedPlaylistIds + playlist.id
                        }
                    }
                )
            }
        }
        if (playlists.isNotEmpty()) {
            LibraryMenuItem(
                text = stringResource(R.string.song_more_done_selected, selectedPlaylistIds.size),
                onClick = {
                    if (selectedPlaylists.isNotEmpty()) {
                        onPlaylistsConfirm(selectedPlaylists)
                    }
                }
            )
        }
        LibraryMenuItem(stringResource(R.string.common_cancel), onDismiss)
    }
}

@Composable
private fun CreatePlaylistAndAddSheet(
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
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                modifier = Modifier.padding(horizontal = 4.dp)
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
private fun SongTagEditorMenu(
    song: Song,
    options: List<TagEditorOption>,
    onDismiss: () -> Unit,
    onOptionClick: (TagEditorOption) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
            .background(MiuixTheme.colorScheme.background.copy(alpha = 0.98f))
            .navigationBarsPadding()
            .padding(horizontal = 18.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        SheetHandle()
        Text(
            text = stringResource(R.string.song_more_edit_tags_title),
            fontSize = 18.sp,
            fontWeight = FontWeight.ExtraBold,
            color = MiuixTheme.colorScheme.onSurface,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp)
        )
        Text(
            text = song.title,
            fontSize = 13.sp,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 8.dp)
        )
        if (options.isEmpty()) {
            Text(
                text = stringResource(R.string.player_no_metadata_editor_found),
                fontSize = 14.sp,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 18.dp)
            )
        } else {
            options.forEach { option ->
                LibraryMenuItem(
                    text = option.label,
                    subtitle = option.summary,
                    onClick = { onOptionClick(option) }
                )
            }
        }
        LibraryMenuItem(stringResource(R.string.common_cancel), onDismiss)
    }
}

@Composable
private fun SheetHandle() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .width(42.dp)
                .height(4.dp)
                .clip(RoundedCornerShape(99.dp))
                .background(MiuixTheme.colorScheme.onSurface.copy(alpha = 0.18f))
        )
    }
}

@Composable
private fun LibraryMenuItem(
    text: String,
    onClick: () -> Unit,
    subtitle: String? = null,
    danger: Boolean = false
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(MiuixTheme.colorScheme.surfaceContainer.copy(alpha = 0.78f))
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Text(
            text = text,
            fontSize = 14.sp,
            color = if (danger) Color(0xFFE5484D) else MiuixTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        if (subtitle != null) {
            Text(
                text = subtitle,
                fontSize = 12.sp,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 2.dp)
            )
        }
    }
}

private fun formatLibraryFileSize(bytes: Long): String {
    if (bytes <= 0L) return ""
    val mb = bytes / 1024.0 / 1024.0
    return if (mb >= 1024.0) {
        "%.2f GB".format(mb / 1024.0)
    } else {
        "%.1f MB".format(mb)
    }
}

private enum class HomeSortMode(val labelRes: Int) {
    Title(R.string.playlist_song_sort_title),
    FileName(R.string.playlist_song_sort_file_name),
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

private fun Song.indexLetter(sortKey: String? = null): String {
    val first = (sortKey ?: title.musicSortKey()).firstOrNull()?.uppercaseChar()
    return if (first != null && first in 'A'..'Z') first.toString() else "#"
}

private fun String.musicSortKey(): String {
    val text = trim()
    if (text.isBlank()) return ""
    if (text.isAsciiSortable()) return text.lowercase(Locale.ROOT)
    MusicSortKeyCache[text]?.let { return it }
    val latin = runCatching { MusicSortTransliterator.value.transliterate(text) }.getOrDefault(text)
    return latin.lowercase(Locale.ROOT).also { MusicSortKeyCache[text] = it }
}

private inline fun List<Song>.sortedByMusicKey(crossinline selector: (Song) -> String): HomeSortedSongs {
    val entries = map { song ->
        val raw = selector(song)
        SongSortEntry(
            song = song,
            sortKey = raw.musicSortKey(),
            fallback = raw
        )
    }.sortedWith(
        compareBy<SongSortEntry> { it.sortKey }
            .thenBy { it.fallback }
    )
    return HomeSortedSongs(
        songs = entries.map { it.song },
        sortKeysBySongId = entries.associate { it.song.id to it.sortKey }
    )
}

private data class HomeSortedSongs(
    val songs: List<Song>,
    val sortKeysBySongId: Map<Long, String>
)

private data class SongSortEntry(
    val song: Song,
    val sortKey: String,
    val fallback: String
)

private fun String.isAsciiSortable(): Boolean {
    return all { it.code in 0x20..0x7E }
}

private object MusicSortTransliterator {
    val value: Transliterator by lazy {
        Transliterator.getInstance("Any-Latin; Latin-ASCII; NFD; [:Nonspacing Mark:] Remove; NFC")
    }
}

private object MusicSortKeyCache {
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
