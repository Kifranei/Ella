package com.ella.music.ui.category

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import android.widget.Toast
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ella.music.data.model.Album
import com.ella.music.data.model.FAVORITES_PLAYLIST_ID
import com.ella.music.data.model.Song
import com.ella.music.data.model.UserPlaylist
import com.ella.music.data.model.albumIdentityId
import com.ella.music.viewmodel.MainViewModel
import com.ella.music.viewmodel.MetadataCategoryItem
import com.ella.music.viewmodel.PlayerViewModel
import com.ella.music.ui.components.ConfirmDangerDialog
import com.ella.music.ui.components.DoubleTapScrollOverlay
import com.ella.music.ui.components.EllaSearchBar
import com.ella.music.ui.components.FolderOutlineIcon
import com.ella.music.ui.components.LocateCurrentSongFloatingButton
import com.ella.music.ui.components.DefaultAlbumCover
import com.ella.music.ui.components.SafeCoverImage
import com.ella.music.ui.components.SongItem
import com.ella.music.ui.components.SongMoreActionHost
import com.ella.music.ui.components.ellaPageBackground
import com.ella.music.ui.components.requestPinnedEllaShortcut
import com.ella.music.ui.navigation.Screen
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import com.ella.music.ui.components.EllaSmallTopAppBar
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.basic.Search
import top.yukonga.miuix.kmp.icon.extended.Add
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.icon.extended.Delete
import top.yukonga.miuix.kmp.icon.extended.Music
import top.yukonga.miuix.kmp.icon.extended.SelectAll
import top.yukonga.miuix.kmp.icon.extended.Sort
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.window.WindowBottomSheet
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
@OptIn(ExperimentalFoundationApi::class)
fun MetadataCategoryScreen(
    type: String,
    mainViewModel: MainViewModel,
    onBack: () -> Unit,
    onCategoryClick: (String) -> Unit
) {
    val context = LocalContext.current
    val songs by mainViewModel.songs.collectAsState()
    val items = remember(type, songs) { mainViewModel.getMetadataCategoryItems(type) }
    var sortExpanded by remember { mutableStateOf(false) }
    val sortIndexFlow = remember(type) { mainViewModel.settingsManager.metadataCategorySortIndex(type) }
    val sortIndex by sortIndexFlow.collectAsState(initial = 0)
    val availableSortModes = remember(type) { MetadataCategorySortMode.entries.filter { it.availableFor(type) } }
    val sortMode = availableSortModes.getOrElse(sortIndex) { MetadataCategorySortMode.Name }
    val sortedItems = remember(items, sortMode) { items.sortedForCategory(sortMode) }
    var searchExpanded by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    val displayedItems = remember(sortedItems, searchQuery, type) {
        val query = searchQuery.trim()
        if (query.isBlank()) {
            sortedItems
        } else {
            sortedItems.filter { it.matchesCategorySearch(query, type) }
        }
    }
    val gridColumns by mainViewModel.settingsManager.categoryGridColumns.collectAsState(initial = 2)
    val safeGridColumns = if (type.usesSingleColumnCategory()) 1 else gridColumns.coerceIn(1, 4)
    val pageBackground = ellaPageBackground()
    val gridState = rememberLazyGridState()
    val scope = rememberCoroutineScope()
    BackHandler(enabled = sortExpanded || searchExpanded) {
        when {
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
            .background(pageBackground)
            .windowInsetsPadding(WindowInsets.statusBars)
    ) {
        Box {
            EllaSmallTopAppBar(
                title = type.categoryTitle(),
                color = pageBackground,
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = MiuixIcons.Regular.Back,
                            contentDescription = "返回",
                            tint = MiuixTheme.colorScheme.onSurface,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { sortExpanded = !sortExpanded }) {
                        Icon(
                            imageVector = MiuixIcons.Regular.Sort,
                            contentDescription = "排序",
                            tint = MiuixTheme.colorScheme.onSurface,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    IconButton(onClick = {
                        searchExpanded = !searchExpanded
                        if (!searchExpanded) searchQuery = ""
                    }) {
                        Icon(
                            imageVector = MiuixIcons.Basic.Search,
                            contentDescription = "搜索",
                            tint = MiuixTheme.colorScheme.onSurface,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            )
            DoubleTapScrollOverlay(
                onDoubleTap = { scope.launch { gridState.animateScrollToItem(0) } },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                endPadding = 112.dp
            )
        }

        AnimatedVisibility(
            visible = searchExpanded,
            enter = expandVertically(),
            exit = shrinkVertically()
        ) {
            EllaSearchBar(
                query = searchQuery,
                onQueryChange = { searchQuery = it },
                onSearch = { searchExpanded = false },
                placeholder = "搜索${type.categoryTitle()}",
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 4.dp)
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
                availableSortModes.forEach { mode ->
                    Text(
                        text = mode.displayLabel(type),
                        fontSize = 14.sp,
                        fontWeight = if (sortMode == mode) FontWeight.Bold else FontWeight.Normal,
                        color = if (sortMode == mode) MiuixTheme.colorScheme.primary else MiuixTheme.colorScheme.onSurface,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                sortExpanded = false
                                scope.launch { mainViewModel.settingsManager.setMetadataCategorySortIndex(type, availableSortModes.indexOf(mode)) }
                                scope.launch { gridState.animateScrollToItem(0) }
                            }
                            .padding(vertical = 10.dp)
                    )
                }
            }
        }

        if (displayedItems.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = if (searchQuery.isBlank()) "暂无${type.categoryTitle()}信息，刷新音乐库后再看看" else "没有匹配的${type.categoryTitle()}",
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    fontSize = 14.sp
                )
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(safeGridColumns),
                state = gridState,
                contentPadding = PaddingValues(bottom = 120.dp)
            ) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Text(
                        text = "${type.categoryCountSummary(displayedItems.size)} · ${sortMode.displayLabel(type)}",
                        fontSize = 13.sp,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                }
                items(displayedItems, key = { it.name }) { item ->
                    MetadataCategoryCard(
                        type = type,
                        item = item,
                        sortMode = sortMode,
                        albumArtUri = item.coverAlbumIds.firstOrNull()?.let(mainViewModel::getAlbumArtUri),
                        onClick = { onCategoryClick(item.name) },
                        onLongClick = {
                            val ok = requestPinnedEllaShortcut(
                                context = context,
                                id = "category_${type}_${item.name}",
                                label = item.name,
                                route = Screen.MetadataCategoryDetail.createRoute(type, item.name)
                            )
                            Toast.makeText(
                                context,
                                if (ok) "已请求添加桌面快捷方式" else "当前桌面不支持固定快捷方式",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun MetadataCategoryDetailScreen(
    type: String,
    name: String,
    mainViewModel: MainViewModel,
    playerViewModel: PlayerViewModel,
    onBack: () -> Unit,
    onAlbumClick: (Long) -> Unit = {},
    onArtistClick: (String) -> Unit = {},
    onNavigateToPlayer: () -> Unit
) {
    val context = LocalContext.current
    val librarySongs by mainViewModel.songs.collectAsState()
    val libraryAlbums by mainViewModel.albums.collectAsState()
    val playlists by mainViewModel.playlists.collectAsState()
    val currentSong by playerViewModel.currentSong.collectAsState()
    val locateCurrentSongRequest by playerViewModel.locateCurrentSongRequest.collectAsState()
    val openPlayerOnPlay by mainViewModel.settingsManager.openPlayerOnPlay.collectAsState(initial = true)
    val songs = remember(type, name, librarySongs) { mainViewModel.getSongsForMetadataCategory(type, name) }
    var sortExpanded by remember { mutableStateOf(false) }
    val detailSongSortIndexFlow = remember(type) { mainViewModel.settingsManager.metadataCategoryDetailSongSortIndex(type) }
    val detailAlbumSortIndexFlow = remember(type) { mainViewModel.settingsManager.metadataCategoryDetailAlbumSortIndex(type) }
    val sortIndex by detailSongSortIndexFlow.collectAsState(initial = 0)
    val albumSortIndex by detailAlbumSortIndexFlow.collectAsState(initial = 0)
    val sortMode = MetadataDetailSongSortMode.entries.getOrElse(sortIndex) { MetadataDetailSongSortMode.AlbumTrack }
    val albumSortMode = MetadataDetailAlbumSortMode.entries.getOrElse(albumSortIndex) { MetadataDetailAlbumSortMode.YearAsc }
    var selectedTab by rememberSaveable(type, name) { mutableStateOf(MetadataDetailTab.Songs) }
    var actionSong by remember { mutableStateOf<com.ella.music.data.model.Song?>(null) }
    var selectionMode by remember { mutableStateOf(false) }
    var selectedIds by remember { mutableStateOf(setOf<Long>()) }
    var playlistPickerSongs by remember { mutableStateOf<List<Song>?>(null) }
    var createPlaylistSongs by remember { mutableStateOf<List<Song>?>(null) }
    var pendingDeleteSongs by remember { mutableStateOf<List<Song>>(emptyList()) }
    val sortedSongs = remember(songs, sortMode) { songs.sortedForMetadataDetail(sortMode) }
    val showAlbumTab = type == "genre" || type == "year" || type == "composer" || type == "lyricist"
    val detailAlbums = remember(songs, libraryAlbums) {
        songs.toMetadataAlbums(libraryAlbums)
    }
    val albumDurations = remember(songs) {
        songs.groupBy { it.albumIdentityId() }.mapValues { (_, albumSongs) -> albumSongs.sumOf { it.duration } }
    }
    val sortedAlbums = remember(detailAlbums, albumSortMode, albumDurations) {
        detailAlbums.sortedForMetadataAlbumDetail(albumSortMode, albumDurations)
    }
    val hasSameNameArtist = remember(type, name, librarySongs) {
        (type == "composer" || type == "lyricist") && mainViewModel.getSongsForArtist(name).isNotEmpty()
    }
    val pageBackground = ellaPageBackground()
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val currentSongItemIndex = remember(sortedSongs, currentSong?.id, selectedTab, selectionMode) {
        if (selectedTab != MetadataDetailTab.Songs || selectionMode) return@remember -1
        sortedSongs.indexOfFirst { it.id == currentSong?.id }
            .takeIf { it >= 0 }
            ?.plus(if (showAlbumTab) 2 else 1)
            ?: -1
    }
    BackHandler(enabled = selectionMode || sortExpanded) {
        when {
            selectionMode -> {
                selectedIds = emptySet()
                selectionMode = false
            }
            sortExpanded -> sortExpanded = false
        }
    }
    LaunchedEffect(selectedTab) {
        if (selectedTab != MetadataDetailTab.Songs && selectionMode) {
            selectedIds = emptySet()
            selectionMode = false
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(pageBackground)
            .windowInsetsPadding(WindowInsets.statusBars)
    ) {
        Box {
            EllaSmallTopAppBar(
                title = name.ifBlank { type.categoryTitle() },
                color = pageBackground,
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = MiuixIcons.Regular.Back,
                            contentDescription = "返回",
                            tint = MiuixTheme.colorScheme.onSurface,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                },
                actions = {
                    if (selectionMode) {
                        IconButton(onClick = {
                            val selectedSongs = sortedSongs.filter { it.id in selectedIds }
                            if (selectedSongs.isEmpty()) {
                                Toast.makeText(context, "请先选择歌曲", Toast.LENGTH_SHORT).show()
                            } else {
                                playlistPickerSongs = selectedSongs
                            }
                        }) {
                            Icon(
                                imageVector = MiuixIcons.Regular.Add,
                                contentDescription = "添加到歌单",
                                tint = MiuixTheme.colorScheme.primary,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        IconButton(onClick = {
                            val selectedSongs = sortedSongs.filter { it.id in selectedIds }
                            if (selectedSongs.isEmpty()) {
                                Toast.makeText(context, "请先选择歌曲", Toast.LENGTH_SHORT).show()
                            } else {
                                pendingDeleteSongs = selectedSongs
                            }
                        }) {
                            Icon(
                                imageVector = MiuixIcons.Regular.Delete,
                                contentDescription = "删除",
                                tint = Color(0xFFE5484D),
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    } else {
                        if (selectedTab == MetadataDetailTab.Songs) {
                            IconButton(onClick = {
                                selectionMode = true
                                selectedIds = sortedSongs.map { it.id }.toSet()
                            }) {
                                Icon(
                                    imageVector = MiuixIcons.Regular.SelectAll,
                                    contentDescription = "多选",
                                    tint = MiuixTheme.colorScheme.onSurface,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }
                        IconButton(onClick = { sortExpanded = !sortExpanded }) {
                            Icon(
                                imageVector = MiuixIcons.Regular.Sort,
                                contentDescription = "排序",
                                tint = MiuixTheme.colorScheme.onSurface,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                }
            )
            DoubleTapScrollOverlay(
                onDoubleTap = { scope.launch { listState.animateScrollToItem(0) } },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                endPadding = 128.dp
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
                if (selectedTab == MetadataDetailTab.Albums) {
                    MetadataDetailAlbumSortMode.entries.forEach { mode ->
                        Text(
                            text = mode.label,
                            fontSize = 14.sp,
                            fontWeight = if (albumSortMode == mode) FontWeight.Bold else FontWeight.Normal,
                            color = if (albumSortMode == mode) MiuixTheme.colorScheme.primary else MiuixTheme.colorScheme.onSurface,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    sortExpanded = false
                                    scope.launch { mainViewModel.settingsManager.setMetadataCategoryDetailAlbumSortIndex(type, mode.ordinal) }
                                    scope.launch { listState.animateScrollToItem(0) }
                                }
                                .padding(vertical = 10.dp)
                        )
                    }
                } else {
                    MetadataDetailSongSortMode.entries.forEach { mode ->
                    Text(
                        text = mode.label,
                        fontSize = 14.sp,
                        fontWeight = if (sortMode == mode) FontWeight.Bold else FontWeight.Normal,
                        color = if (sortMode == mode) MiuixTheme.colorScheme.primary else MiuixTheme.colorScheme.onSurface,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                sortExpanded = false
                                scope.launch { mainViewModel.settingsManager.setMetadataCategoryDetailSongSortIndex(type, mode.ordinal) }
                                scope.launch { listState.animateScrollToItem(0) }
                            }
                            .padding(vertical = 10.dp)
                    )
                    }
                }
            }
        }

        Box(modifier = Modifier.fillMaxSize()) {
            LazyColumn(
                state = listState,
                contentPadding = PaddingValues(bottom = 120.dp)
            ) {
                item {
                    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                        Text(
                            text = if (selectionMode) {
                                "已选择 ${selectedIds.size} 首"
                            } else if (selectedTab == MetadataDetailTab.Albums) {
                                "${sortedAlbums.size} 张专辑 · ${type.categoryTitle()} · ${albumSortMode.label}"
                            } else {
                                "${sortedSongs.size} 首歌曲 · ${type.categoryTitle()} · ${sortMode.label}"
                            },
                            fontSize = 13.sp,
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary
                        )
                        if (showAlbumTab) {
                            Row(
                                modifier = Modifier.padding(top = 10.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                MetadataDetailTab.entries.forEach { tab ->
                                    Text(
                                        text = tab.label,
                                        fontSize = 13.sp,
                                        fontWeight = if (selectedTab == tab) FontWeight.Bold else FontWeight.Normal,
                                        color = if (selectedTab == tab) MiuixTheme.colorScheme.primary else MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(999.dp))
                                            .background(
                                                if (selectedTab == tab) MiuixTheme.colorScheme.primary.copy(alpha = 0.12f)
                                                else Color.Transparent
                                            )
                                            .clickable { selectedTab = tab }
                                            .padding(horizontal = 12.dp, vertical = 7.dp)
                                    )
                                }
                            }
                        }
                        if (hasSameNameArtist) {
                            Text(
                                text = "艺术家页",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = MiuixTheme.colorScheme.primary,
                                modifier = Modifier
                                    .padding(top = 8.dp)
                                    .clip(RoundedCornerShape(999.dp))
                                    .background(MiuixTheme.colorScheme.primary.copy(alpha = 0.12f))
                                    .clickable { onArtistClick(name) }
                                    .padding(horizontal = 12.dp, vertical = 8.dp)
                            )
                        }
                    }
                }
                if (selectedTab == MetadataDetailTab.Albums) {
                    items(sortedAlbums, key = { it.id }) { album ->
                        MetadataAlbumRow(
                            album = album,
                            duration = albumDurations[album.id] ?: 0L,
                            albumArtUri = mainViewModel.getAlbumArtUri(album.artAlbumId),
                            onClick = { onAlbumClick(album.id) }
                        )
                    }
                } else {
                    itemsIndexed(sortedSongs, key = { _, song -> song.id }) { index, song ->
                        val selected = song.id in selectedIds
                        SongItem(
                            song = song,
                            isCurrent = currentSong?.id == song.id,
                            albumArtUri = mainViewModel.getAlbumArtUri(song.albumId),
                            loadCoverArt = mainViewModel::getCoverArtBitmap,
                            loadAudioInfo = mainViewModel::getAudioInfo,
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
                }
                item { Spacer(modifier = Modifier.height(24.dp)) }
            }

            LocateCurrentSongFloatingButton(
                listState = listState,
                currentItemIndex = currentSongItemIndex,
                locateRequest = locateCurrentSongRequest,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 22.dp, bottom = 118.dp)
            )

            SongMoreActionHost(
                actionSong = actionSong,
                mainViewModel = mainViewModel,
                playerViewModel = playerViewModel,
                onDismissAction = { actionSong = null },
                onNavigateToAlbum = onAlbumClick,
                onNavigateToArtist = onArtistClick
            )

            playlistPickerSongs?.let { songsToAdd ->
                WindowBottomSheet(
                    show = true,
                    enableNestedScroll = false,
                    title = "添加到歌单",
                    onDismissRequest = { playlistPickerSongs = null }
                ) {
                    CategoryAddSelectedSongsToPlaylistSheet(
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
                            Toast.makeText(context, "已添加到 ${selectedPlaylists.size} 个歌单", Toast.LENGTH_SHORT).show()
                            playlistPickerSongs = null
                            selectedIds = emptySet()
                            selectionMode = false
                        }
                    )
                }
            }

            createPlaylistSongs?.let { songsToAdd ->
                CategoryCreatePlaylistAndAddSelectedSheet(
                    songCount = songsToAdd.size,
                    onDismiss = { createPlaylistSongs = null },
                    onCreate = { playlistName ->
                        mainViewModel.createPlaylist(playlistName) { playlist ->
                            if (playlist != null) {
                                mainViewModel.addSongsToPlaylist(playlist.id, songsToAdd)
                                Toast.makeText(context, "已添加到 ${playlist.name}", Toast.LENGTH_SHORT).show()
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
                title = "永久删除歌曲",
                message = "确定要永久删除选中的 ${pendingDeleteSongs.size} 首歌曲吗？此操作可能会删除本地音频文件。",
                confirmText = "永久删除",
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
}

@Composable
private fun CategoryAddSelectedSongsToPlaylistSheet(
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
            text = "已选择 $songCount 首歌曲",
            fontSize = 13.sp,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
        )
        CategorySheetItem("新建歌单", onCreatePlaylist)
        if (playlists.isEmpty()) {
            Text(
                text = "暂无自定义歌单",
                fontSize = 14.sp,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 18.dp)
            )
        } else {
            playlists.forEach { playlist ->
                val selected = playlist.id in selectedPlaylistIds
                CategorySheetItem("${if (selected) "✓ " else ""}${playlist.name} · ${playlist.songs.size} 首") {
                    selectedPlaylistIds = if (selected) {
                        selectedPlaylistIds - playlist.id
                    } else {
                        selectedPlaylistIds + playlist.id
                    }
                }
            }
        }
        if (playlists.isNotEmpty()) {
            CategorySheetItem("完成（${selectedPlaylistIds.size}）") {
                if (selectedPlaylists.isNotEmpty()) {
                    onPlaylistsConfirm(selectedPlaylists)
                }
            }
        }
        CategorySheetItem("取消", onDismiss)
    }
}

@Composable
private fun CategoryCreatePlaylistAndAddSelectedSheet(
    songCount: Int,
    onDismiss: () -> Unit,
    onCreate: (String) -> Unit
) {
    var playlistName by remember { mutableStateOf("") }
    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current
    LaunchedEffect(Unit) {
        delay(220L)
        focusRequester.requestFocus()
        keyboardController?.show()
    }
    WindowBottomSheet(
        show = true,
        title = "新建歌单",
        onDismissRequest = onDismiss
    ) {
        Column(
            modifier = Modifier.padding(bottom = 18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(
                text = "将添加 $songCount 首歌曲",
                fontSize = 13.sp,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary
            )
            TextField(
                value = playlistName,
                onValueChange = { playlistName = it },
                label = "歌单名称",
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
                Button(onClick = onDismiss) { Text("取消") }
                Spacer(modifier = Modifier.size(8.dp))
                Button(onClick = { onCreate(playlistName) }) { Text("创建") }
            }
        }
    }
}

@Composable
private fun CategorySheetItem(
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

@Composable
@OptIn(ExperimentalFoundationApi::class)
private fun MetadataCategoryCard(
    type: String,
    item: MetadataCategoryItem,
    sortMode: MetadataCategorySortMode,
    albumArtUri: android.net.Uri?,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    when (type) {
        "folder" -> {
            FolderCategoryRow(
                item = item,
                sortMode = sortMode,
                albumArtUri = albumArtUri,
                onClick = onClick,
                onLongClick = onLongClick
            )
            return
        }
        "composer", "lyricist" -> {
            PersonCategoryRow(
                item = item,
                sortMode = sortMode,
                albumArtUri = albumArtUri,
                onClick = onClick,
                onLongClick = onLongClick
            )
            return
        }
    }

    val cardColor = remember(item.name) { item.name.categoryCardColor() }
    val hasCover = albumArtUri != null
    val isGenreCard = type == "genre"
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(if (isGenreCard) 112.dp else 116.dp)
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
            .padding(horizontal = 8.dp, vertical = 8.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(
                Brush.linearGradient(
                    colors = listOf(
                        cardColor,
                        cardColor.darkenCategoryColor(0.78f)
                    )
                )
            )
    ) {
        Box(
            modifier = Modifier
                .matchParentSize()
                .background(Color.Black.copy(alpha = 0.08f))
        )
        if (albumArtUri != null) {
            Box(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 2.dp)
                    .size(if (isGenreCard) 54.dp else 78.dp)
                    .graphicsLayer {
                        rotationZ = 13f
                        translationX = if (isGenreCard) 9.dp.toPx() else 16.dp.toPx()
                        translationY = if (isGenreCard) 3.dp.toPx() else 6.dp.toPx()
                    }
                    .clip(RoundedCornerShape(10.dp)),
                contentAlignment = Alignment.Center
            ) {
                SafeCoverImage(
                    model = albumArtUri,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    sizePx = 220
                )
            }
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(
                        Brush.horizontalGradient(
                            colors = listOf(Color.Transparent, cardColor.copy(alpha = 0.16f), Color.Black.copy(alpha = 0.16f))
                        )
                    )
            )
        }
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(
                    start = 14.dp,
                    top = if (isGenreCard) 12.dp else 13.dp,
                    end = if (hasCover) {
                        if (isGenreCard) 54.dp else 72.dp
                    } else {
                        14.dp
                    },
                    bottom = 12.dp
                ),
            verticalArrangement = if (isGenreCard) Arrangement.Center else Arrangement.SpaceBetween
        ) {
            Text(
                text = item.name,
                fontSize = if (isGenreCard) 13.sp else 16.sp,
                lineHeight = if (isGenreCard) 17.sp else 20.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth()
            )
            Text(
                text = "${item.songCount} 首歌曲",
                fontSize = if (isGenreCard) 10.sp else 12.sp,
                lineHeight = if (isGenreCard) 13.sp else 15.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color.White.copy(alpha = 0.78f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
@OptIn(ExperimentalFoundationApi::class)
private fun FolderCategoryRow(
    item: MetadataCategoryItem,
    sortMode: MetadataCategorySortMode,
    albumArtUri: android.net.Uri?,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
            .padding(horizontal = 18.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(MiuixTheme.colorScheme.surfaceContainer),
            contentAlignment = Alignment.Center
        ) {
            if (albumArtUri != null) {
                SafeCoverImage(
                    model = albumArtUri,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    sizePx = 160
                )
            } else {
                FolderOutlineIcon(
                    tint = MiuixTheme.colorScheme.primary,
                    modifier = Modifier.size(34.dp)
                )
            }
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = item.name.substringAfterLast('/').ifBlank { item.name.ifBlank { "根目录" } },
                fontSize = 17.sp,
                lineHeight = 22.sp,
                fontWeight = FontWeight.SemiBold,
                color = MiuixTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "${item.folderSortSummary(sortMode)} · ${item.name}",
                fontSize = 13.sp,
                lineHeight = 17.sp,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
@OptIn(ExperimentalFoundationApi::class)
private fun PersonCategoryRow(
    item: MetadataCategoryItem,
    sortMode: MetadataCategorySortMode,
    albumArtUri: android.net.Uri?,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(76.dp)
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(54.dp)
                .clip(CircleShape)
                .background(MiuixTheme.colorScheme.surfaceContainer),
            contentAlignment = Alignment.Center
        ) {
            if (albumArtUri != null) {
                SafeCoverImage(
                    model = albumArtUri,
                    contentDescription = null,
                    modifier = Modifier
                        .size(54.dp)
                        .clip(CircleShape),
                    contentScale = ContentScale.Crop,
                    sizePx = 128
                )
            } else {
                Icon(
                    imageVector = MiuixIcons.Regular.Music,
                    contentDescription = null,
                    tint = MiuixTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
            }
        }
        Spacer(modifier = Modifier.size(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = item.name.ifBlank { "未知" },
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                color = MiuixTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = item.personSortSummary(sortMode),
                fontSize = 12.sp,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

private fun String.categoryCardColor(): Color {
    val palette = listOf(
        Color(0xFF141414),
        Color(0xFF825A58),
        Color(0xFFA92E4A),
        Color(0xFF626262),
        Color(0xFF352B28),
        Color(0xFF416B8D),
        Color(0xFF28295F),
        Color(0xFF9B463D),
        Color(0xFF6C4E86),
        Color(0xFF2A1024),
        Color(0xFFA88E24),
        Color(0xFF542231),
        Color(0xFF5EA91A)
    )
    val index = (lowercase(Locale.ROOT).hashCode() and Int.MAX_VALUE) % palette.size
    return palette[index]
}

private fun Color.darkenCategoryColor(factor: Float): Color {
    return Color(
        red = red * factor,
        green = green * factor,
        blue = blue * factor,
        alpha = alpha
    )
}

private enum class MetadataCategorySortMode(val label: String) {
    Name("名称"),
    NameDesc("年份倒序"),
    SongCount("歌曲数"),
    AlbumCount("专辑数"),
    Duration("歌曲时长"),
    DateModified("修改时间"),
    DateModifiedAsc("修改时间升序")
}

private fun MetadataCategorySortMode.availableFor(type: String): Boolean {
    return when (this) {
        MetadataCategorySortMode.NameDesc -> type == "year"
        MetadataCategorySortMode.DateModified,
        MetadataCategorySortMode.DateModifiedAsc -> type == "folder"
        else -> true
    }
}

private fun MetadataCategorySortMode.displayLabel(type: String): String {
    return when {
        type == "year" && this == MetadataCategorySortMode.Name -> "年份正序"
        (type == "composer" || type == "lyricist") && this == MetadataCategorySortMode.AlbumCount -> "参与专辑数"
        else -> label
    }
}

private fun List<MetadataCategoryItem>.sortedForCategory(mode: MetadataCategorySortMode): List<MetadataCategoryItem> {
    return when (mode) {
        MetadataCategorySortMode.Name -> sortedBy { it.name.lowercase(Locale.ROOT) }
        MetadataCategorySortMode.NameDesc -> sortedByDescending { it.name.toIntOrNull() ?: Int.MIN_VALUE }
        MetadataCategorySortMode.SongCount -> sortedByDescending { it.songCount }
        MetadataCategorySortMode.AlbumCount -> sortedByDescending { it.albumCount }
        MetadataCategorySortMode.Duration -> sortedByDescending { it.duration }
        MetadataCategorySortMode.DateModified -> sortedByDescending { it.dateModified }
        MetadataCategorySortMode.DateModifiedAsc -> sortedBy { it.dateModified }
    }
}

private fun MetadataCategoryItem.matchesCategorySearch(query: String, type: String): Boolean {
    return name.contains(query, ignoreCase = true) ||
        (type == "folder" && name.substringAfterLast('/').contains(query, ignoreCase = true))
}

private fun MetadataCategoryItem.folderSortSummary(sortMode: MetadataCategorySortMode): String {
    return when (sortMode) {
        MetadataCategorySortMode.AlbumCount -> "${albumCount} 张专辑"
        MetadataCategorySortMode.Duration -> duration.formatDuration()
        MetadataCategorySortMode.DateModified,
        MetadataCategorySortMode.DateModifiedAsc -> dateModified.formatDateTimeText()
        else -> "${songCount} 首歌曲"
    }
}

private fun MetadataCategoryItem.personSortSummary(sortMode: MetadataCategorySortMode): String {
    return when (sortMode) {
        MetadataCategorySortMode.Duration -> "${duration.formatDuration()} · ${albumCount} 张参与专辑"
        else -> "${songCount} 首歌曲 · ${albumCount} 张参与专辑"
    }
}

private enum class MetadataDetailSongSortMode(val label: String) {
    AlbumTrack("专辑曲序"),
    Title("歌曲名称"),
    FileName("文件名"),
    Duration("歌曲时长"),
    YearAsc("发行时间正序"),
    YearDesc("发行时间倒序"),
    DateAdded("添加时间"),
    DateAddedAsc("添加时间升序"),
    DateModified("修改时间"),
    DateModifiedAsc("修改时间升序")
}

private fun List<com.ella.music.data.model.Song>.sortedForMetadataDetail(
    mode: MetadataDetailSongSortMode
): List<com.ella.music.data.model.Song> {
    return when (mode) {
        MetadataDetailSongSortMode.AlbumTrack -> sortedWith(
            compareBy<com.ella.music.data.model.Song> { it.album.lowercase(Locale.ROOT) }
                .thenBy { if (it.discNumber > 0) it.discNumber else Int.MAX_VALUE }
                .thenBy { if (it.trackNumber > 0) it.trackNumber else Int.MAX_VALUE }
                .thenBy { it.title.lowercase(Locale.ROOT) }
        )
        MetadataDetailSongSortMode.Title -> sortedBy { it.title.lowercase(Locale.ROOT) }
        MetadataDetailSongSortMode.FileName -> sortedBy { song ->
            song.fileName.ifBlank { song.path.substringAfterLast('/') }.lowercase(Locale.ROOT)
        }
        MetadataDetailSongSortMode.Duration -> sortedByDescending { it.duration }
        MetadataDetailSongSortMode.YearAsc -> sortedByReleaseDate(ascending = true)
        MetadataDetailSongSortMode.YearDesc -> sortedByReleaseDate(ascending = false)
        MetadataDetailSongSortMode.DateAdded -> sortedByDescending { it.dateAdded }
        MetadataDetailSongSortMode.DateAddedAsc -> sortedBy { it.dateAdded }
        MetadataDetailSongSortMode.DateModified -> sortedByDescending { it.dateModified }
        MetadataDetailSongSortMode.DateModifiedAsc -> sortedBy { it.dateModified }
    }
}

private enum class MetadataDetailTab(val label: String) {
    Songs("歌曲"),
    Albums("专辑")
}

private enum class MetadataDetailAlbumSortMode(val label: String) {
    YearAsc("发行时间正序"),
    YearDesc("发行时间倒序"),
    SongCount("歌曲数"),
    Duration("歌曲时长"),
    Name("专辑名称")
}

private fun List<Album>.sortedForMetadataAlbumDetail(
    mode: MetadataDetailAlbumSortMode,
    durations: Map<Long, Long>
): List<Album> {
    return when (mode) {
        MetadataDetailAlbumSortMode.YearAsc -> sortedWith(compareBy<Album> { it.year <= 0 }.thenBy { it.year }.thenBy { it.name.lowercase(Locale.ROOT) })
        MetadataDetailAlbumSortMode.YearDesc -> sortedWith(compareBy<Album> { it.year <= 0 }.thenByDescending { it.year }.thenBy { it.name.lowercase(Locale.ROOT) })
        MetadataDetailAlbumSortMode.SongCount -> sortedByDescending { it.songCount }
        MetadataDetailAlbumSortMode.Duration -> sortedByDescending { durations[it.id] ?: 0L }
        MetadataDetailAlbumSortMode.Name -> sortedBy { it.name.lowercase(Locale.ROOT) }
    }
}

private fun List<Song>.toMetadataAlbums(libraryAlbums: List<Album>): List<Album> {
    val albumById = libraryAlbums.associateBy { it.id }
    return groupBy { it.albumIdentityId() }
        .map { (albumId, albumSongs) ->
            albumById[albumId] ?: Album(
                id = albumId,
                name = albumSongs.firstOrNull()?.album.orEmpty().ifBlank { "未知专辑" },
                artist = albumSongs.firstOrNull()?.artist.orEmpty(),
                songCount = albumSongs.size,
                year = albumSongs.mapNotNull { it.releaseYearOrNull() }.minOrNull() ?: 0,
                artAlbumId = albumSongs.firstOrNull()?.albumId ?: albumId,
                albumArtist = albumSongs.firstOrNull()?.albumArtist.orEmpty()
            )
        }
}

@Composable
private fun MetadataAlbumRow(
    album: Album,
    duration: Long,
    albumArtUri: android.net.Uri?,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            modifier = Modifier
                .size(52.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(MiuixTheme.colorScheme.surfaceContainer),
            contentAlignment = Alignment.Center
        ) {
            if (albumArtUri != null) {
                SafeCoverImage(
                    model = albumArtUri,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                    sizePx = 256
                )
            } else {
                DefaultAlbumCover(modifier = Modifier.fillMaxSize())
            }
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = album.name,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                color = MiuixTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = "${album.songCount} 首歌曲 · ${duration.formatDuration()}",
                fontSize = 12.sp,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
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

private fun String.categoryTitle(): String {
    return when (this) {
        "genre" -> "流派"
        "year" -> "年份"
        "composer" -> "作曲家"
        "lyricist" -> "作词家"
        "folder" -> "文件夹"
        else -> "分类"
    }
}

private fun String.categoryCountSummary(count: Int): String {
    return when (this) {
        "genre" -> "$count 种流派"
        "composer" -> "$count 位作曲家"
        "lyricist" -> "$count 位作词家"
        "folder" -> "$count 个文件夹"
        "year" -> "$count 个年份"
        else -> "$count 个分类"
    }
}

private fun String.usesSingleColumnCategory(): Boolean {
    return this == "composer" || this == "lyricist" || this == "folder"
}

private fun Long.formatDuration(): String {
    if (this <= 0L) return "00:00"
    val totalSeconds = this / 1000L
    val hours = totalSeconds / 3600L
    val minutes = (totalSeconds % 3600L) / 60L
    return if (hours > 0) "${hours}小时${minutes}分" else "${minutes}分钟"
}

private fun Long.formatDateText(): String {
    if (this <= 0L) return "未知修改时间"
    val millis = if (this < 10_000_000_000L) this * 1000L else this
    return SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date(millis))
}

private fun Long.formatDateTimeText(): String {
    if (this <= 0L) return "未知修改时间"
    val millis = if (this < 10_000_000_000L) this * 1000L else this
    return SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date(millis))
}
