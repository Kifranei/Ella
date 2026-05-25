package com.ella.music.ui.playlist

import android.widget.Toast
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ella.music.data.model.FIVE_STAR_PLAYLIST_ID
import com.ella.music.data.model.FAVORITES_PLAYLIST_ID
import com.ella.music.data.model.Song
import com.ella.music.data.model.UserPlaylist
import com.ella.music.data.model.playlistIdentityKey
import com.ella.music.data.PlaylistExportFormat
import com.ella.music.data.PlaylistImportMode
import com.ella.music.ui.components.AppleStylePlayButton
import com.ella.music.ui.components.ConfirmDangerDialog
import com.ella.music.ui.components.DefaultAlbumCover
import com.ella.music.ui.components.DoubleTapScrollOverlay
import com.ella.music.ui.components.EllaSearchBar
import com.ella.music.ui.components.LocateCurrentSongFloatingButton
import com.ella.music.ui.components.SafeCoverImage
import com.ella.music.ui.components.SongItem
import com.ella.music.ui.components.SongMoreActionHost
import com.ella.music.ui.components.ellaPageBackground
import com.ella.music.ui.components.requestPinnedEllaShortcut
import com.ella.music.ui.navigation.Screen
import com.ella.music.viewmodel.MainViewModel
import com.ella.music.viewmodel.PlayerViewModel
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import com.ella.music.ui.components.EllaSmallTopAppBar
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.basic.Search
import top.yukonga.miuix.kmp.icon.extended.Add
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.icon.extended.Download
import top.yukonga.miuix.kmp.icon.extended.FavoritesFill
import top.yukonga.miuix.kmp.icon.extended.Playlist
import top.yukonga.miuix.kmp.icon.extended.Share
import top.yukonga.miuix.kmp.icon.extended.Sort
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.window.WindowBottomSheet
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun PlaylistScreen(
    mainViewModel: MainViewModel,
    onBack: () -> Unit,
    onPlaylistClick: (String) -> Unit
) {
    val context = LocalContext.current
    val playlists by mainViewModel.playlists.collectAsState()
    val librarySongs by mainViewModel.songs.collectAsState()
    var showCreateDialog by remember { mutableStateOf(false) }
    var sortExpanded by remember { mutableStateOf(false) }
    var searchExpanded by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    val playlistSortIndex by mainViewModel.settingsManager.playlistListSortIndex.collectAsState(initial = 2)
    val playlistSortMode = PlaylistSortMode.entries.getOrElse(playlistSortIndex) { PlaylistSortMode.UpdatedAt }
    var pendingImportUris by remember { mutableStateOf<List<Uri>>(emptyList()) }
    var showImportModeSheet by remember { mutableStateOf(false) }
    var playlistPendingDelete by remember { mutableStateOf<UserPlaylist?>(null) }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val favorites = playlists.firstOrNull { it.id == FAVORITES_PLAYLIST_ID }
    val customPlaylists = remember(playlists, playlistSortMode) {
        playlists
            .filterNot { it.id == FAVORITES_PLAYLIST_ID }
            .sortedForPlaylistList(playlistSortMode)
    }
    val displayedCustomPlaylists = remember(customPlaylists, searchQuery) {
        val query = searchQuery.trim()
        if (query.isBlank()) customPlaylists else customPlaylists.filter { it.matchesPlaylistSearch(query) }
    }
    val playlistCoverModels = remember(playlists, librarySongs) {
        playlists.associate { playlist ->
            playlist.id to mainViewModel.playlistSongs(playlist).firstOrNull().playlistCoverModel()
        }
    }
    val showFavorites = remember(favorites, searchQuery) {
        favorites != null && (searchQuery.isBlank() || favorites.matchesPlaylistSearch(searchQuery.trim()))
    }
    val showFiveStar = remember(searchQuery) {
        searchQuery.isBlank() || "五星歌曲".contains(searchQuery.trim(), ignoreCase = true)
    }
    val fiveStarSongs by produceState(initialValue = emptyList(), librarySongs) {
        value = mainViewModel.getFiveStarSongs()
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isEmpty()) return@rememberLauncherForActivityResult
        pendingImportUris = uris
        showImportModeSheet = true
    }
    fun importPendingPlaylists(mode: PlaylistImportMode) {
        val uris = pendingImportUris
        if (uris.isEmpty()) return
        showImportModeSheet = false
        pendingImportUris = emptyList()
        mainViewModel.importLocalPlaylists(uris, mode) { result ->
            result
                .onSuccess { importResult ->
                    val message = if (importResult.importedCount == 0) {
                        "没有可导入的歌曲"
                    } else {
                        val missingText = if (importResult.missingCount > 0) "，保留 ${importResult.missingCount} 首未匹配路径" else ""
                        val duplicateText = if (importResult.duplicateCount > 0) "，跳过 ${importResult.duplicateCount} 条重复" else ""
                        val playlistText = if (importResult.importedPlaylists > 1) "${importResult.importedPlaylists} 个歌单，" else ""
                        "已导入 $playlistText${importResult.importedCount} 首，匹配 ${importResult.matchedCount} 首$missingText$duplicateText"
                    }
                    Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                }
                .onFailure {
                    Toast.makeText(context, "导入失败：${it.message.orEmpty()}", Toast.LENGTH_SHORT).show()
                }
        }
    }
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
            .background(ellaPageBackground())
            .windowInsetsPadding(WindowInsets.statusBars)
    ) {
        Box {
            EllaSmallTopAppBar(
                title = "歌单",
                color = ellaPageBackground(),
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = MiuixIcons.Regular.Back,
                            contentDescription = "返回",
                            tint = MiuixTheme.colorScheme.onSurface
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
                    IconButton(onClick = {
                        importLauncher.launch(
                            arrayOf(
                                "audio/x-mpegurl",
                                "audio/mpegurl",
                                "application/vnd.apple.mpegurl",
                                "text/plain",
                                "application/octet-stream",
                                "*/*"
                            )
                        )
                    }) {
                        Icon(
                            imageVector = MiuixIcons.Regular.Download,
                            contentDescription = "导入歌单",
                            tint = MiuixTheme.colorScheme.onSurface,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    IconButton(onClick = { showCreateDialog = true }) {
                        Icon(
                            imageVector = MiuixIcons.Regular.Add,
                            contentDescription = "新建歌单",
                            tint = MiuixTheme.colorScheme.onSurface,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            )
            DoubleTapScrollOverlay(
                onDoubleTap = { scope.launch { listState.animateScrollToItem(0) } },
                onClick = { scope.launch { listState.animateScrollToItem(0) } },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                endPadding = 216.dp
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
                placeholder = "搜索歌单",
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
                PlaylistSortMode.entries.forEach { mode ->
                    Text(
                        text = mode.label,
                        fontSize = 14.sp,
                        fontWeight = if (playlistSortMode == mode) FontWeight.Bold else FontWeight.Normal,
                        color = if (playlistSortMode == mode) MiuixTheme.colorScheme.primary else MiuixTheme.colorScheme.onSurface,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                sortExpanded = false
                                scope.launch { mainViewModel.settingsManager.setPlaylistListSortIndex(mode.ordinal) }
                                scope.launch { listState.animateScrollToItem(0) }
                            }
                            .padding(vertical = 10.dp)
                    )
                }
            }
        }

        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
        ) {
            if (favorites != null && showFavorites) {
                item(key = favorites.id) {
                    PlaylistRow(
                        playlist = favorites,
                        coverModel = playlistCoverModels[favorites.id],
                        accent = true,
                        onClick = { onPlaylistClick(favorites.id) }
                    )
                }
            }

            if (showFiveStar) item(key = FIVE_STAR_PLAYLIST_ID) {
                PlaylistRow(
                    playlist = UserPlaylist(
                        id = FIVE_STAR_PLAYLIST_ID,
                        name = "五星歌曲",
                        createdAt = 0L,
                        updatedAt = 0L
                    ),
                    countOverride = fiveStarSongs.size,
                    durationOverride = fiveStarSongs.sumOf { it.duration },
                    accent = true,
                    onClick = { onPlaylistClick(FIVE_STAR_PLAYLIST_ID) }
                )
            }

            item {
                Text(
                    text = "${displayedCustomPlaylists.size} 个歌单 · ${playlistSortMode.label}排序",
                    fontSize = 13.sp,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 12.dp)
                )
            }

            if (displayedCustomPlaylists.isEmpty()) {
                item {
                    Text(
                        text = if (searchQuery.isBlank()) "还没有自定义歌单" else "没有匹配的歌单",
                        fontSize = 14.sp,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 12.dp)
                    )
                }
            } else {
                items(displayedCustomPlaylists, key = { it.id }) { playlist ->
                    PlaylistRow(
                        playlist = playlist,
                        coverModel = playlistCoverModels[playlist.id],
                        onClick = { onPlaylistClick(playlist.id) },
                        onLongClick = {
                            val route = Screen.PlaylistDetail.createRoute(playlist.id)
                            val created = requestPinnedEllaShortcut(
                                context = context,
                                id = "playlist_${playlist.id}",
                                label = playlist.name,
                                route = route
                            )
                            Toast.makeText(
                                context,
                                if (created) {
                                    "已请求创建「${playlist.name}」快捷方式"
                                } else {
                                    "当前桌面不支持创建快捷方式"
                                },
                                Toast.LENGTH_SHORT
                            ).show()
                        },
                        onDelete = { playlistPendingDelete = playlist }
                    )
                }
            }

            item { Spacer(modifier = Modifier.height(150.dp)) }
        }
    }

    if (showCreateDialog) {
        CreatePlaylistDialog(
            onDismiss = { showCreateDialog = false },
            onCreate = { name ->
                mainViewModel.createPlaylist(name)
                showCreateDialog = false
            }
        )
    }
    if (showImportModeSheet) {
        ImportPlaylistModeSheet(
            count = pendingImportUris.size,
            onDismiss = {
                showImportModeSheet = false
                pendingImportUris = emptyList()
            },
            onModeSelected = ::importPendingPlaylists
        )
    }
    playlistPendingDelete?.let { playlist ->
        ConfirmDangerDialog(
            show = true,
            title = "删除歌单",
            message = "确定要删除歌单「${playlist.name}」吗？歌单内歌曲不会从本地删除。",
            confirmText = "删除",
            onDismiss = { playlistPendingDelete = null },
            onConfirm = {
                mainViewModel.deletePlaylist(playlist.id)
                playlistPendingDelete = null
            }
        )
    }
}

@Composable
fun PlaylistDetailScreen(
    playlistId: String,
    mainViewModel: MainViewModel,
    playerViewModel: PlayerViewModel,
    onBack: () -> Unit,
    onNavigateToAlbum: (Long) -> Unit = {},
    onNavigateToArtist: (String) -> Unit = {},
    onNavigateToPlayer: () -> Unit
) {
    val context = LocalContext.current
    val playlists by mainViewModel.playlists.collectAsState()
    val currentSong by playerViewModel.currentSong.collectAsState()
    val locateCurrentSongRequest by playerViewModel.locateCurrentSongRequest.collectAsState()
    val librarySongs by mainViewModel.songs.collectAsState()
    val openPlayerOnPlay by mainViewModel.settingsManager.openPlayerOnPlay.collectAsState(initial = true)
    val isFiveStarPlaylist = playlistId == FIVE_STAR_PLAYLIST_ID
    val storedPlaylist = playlists.firstOrNull { it.id == playlistId }
    val fiveStarSongs by produceState(initialValue = emptyList(), isFiveStarPlaylist, librarySongs) {
        value = if (isFiveStarPlaylist) mainViewModel.getFiveStarSongs() else emptyList()
    }
    val playlist = if (isFiveStarPlaylist) {
        UserPlaylist(
            id = FIVE_STAR_PLAYLIST_ID,
            name = "五星歌曲",
            createdAt = 0L,
            updatedAt = 0L
        )
    } else {
        storedPlaylist
    }
    val songs = remember(playlist, librarySongs, fiveStarSongs, isFiveStarPlaylist) {
        if (isFiveStarPlaylist) fiveStarSongs else playlist?.let(mainViewModel::playlistSongs).orEmpty()
    }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    var actionSong by remember { mutableStateOf<com.ella.music.data.model.Song?>(null) }
    var sortExpanded by remember { mutableStateOf(false) }
    val sortIndex by mainViewModel.settingsManager.playlistDetailSongSortIndex.collectAsState(initial = 2)
    val sortMode = PlaylistSongSortMode.entries.getOrElse(sortIndex) { PlaylistSongSortMode.AddedAt }
    var searchExpanded by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var removeFromPlaylistSong by remember { mutableStateOf<Song?>(null) }
    val sortedSongs = remember(songs, sortMode) { songs.sortedForPlaylistDetail(sortMode) }
    val displayedSongs = remember(sortedSongs, searchQuery) {
        val query = searchQuery.trim()
        if (query.isBlank()) {
            sortedSongs
        } else {
            sortedSongs.filter { song ->
                song.title.contains(query, ignoreCase = true) ||
                    song.artist.contains(query, ignoreCase = true) ||
                    song.album.contains(query, ignoreCase = true) ||
                    song.fileName.contains(query, ignoreCase = true)
            }
        }
    }
    BackHandler(enabled = sortExpanded || searchExpanded) {
        when {
            searchExpanded -> {
                searchExpanded = false
                searchQuery = ""
            }
            sortExpanded -> sortExpanded = false
        }
    }
    val currentSongItemIndex = remember(displayedSongs, currentSong?.playlistIdentityKey()) {
        displayedSongs.indexOfFirst { it.playlistIdentityKey() == currentSong?.playlistIdentityKey() }
            .takeIf { it >= 0 }
            ?.plus(2)
            ?: -1
    }
    val playlistCoverModel = remember(sortedSongs) {
        sortedSongs.firstOrNull()?.let { song ->
            song.coverUrl.takeIf { it.isNotBlank() } ?: mainViewModel.getAlbumArtUri(song.albumId)
        }
    }
    var showExportFormatSheet by remember { mutableStateOf(false) }
    val txtExportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri ->
        val targetPlaylist = playlist
        if (uri == null || targetPlaylist == null) return@rememberLauncherForActivityResult
        mainViewModel.exportLocalPlaylist(targetPlaylist, uri, PlaylistExportFormat.PlainText) { result ->
            result
                .onSuccess { exportResult ->
                    val skippedText = if (exportResult.skippedCount > 0) "，跳过 ${exportResult.skippedCount} 首在线歌曲" else ""
                    Toast.makeText(context, "已导出 ${exportResult.exportedCount} 首$skippedText", Toast.LENGTH_SHORT).show()
                }
                .onFailure {
                    Toast.makeText(context, "导出失败：${it.message.orEmpty()}", Toast.LENGTH_SHORT).show()
                }
        }
    }
    val m3uExportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("audio/x-mpegurl")) { uri ->
        val targetPlaylist = playlist
        if (uri == null || targetPlaylist == null) return@rememberLauncherForActivityResult
        mainViewModel.exportLocalPlaylist(targetPlaylist, uri, PlaylistExportFormat.M3u) { result ->
            result
                .onSuccess { exportResult ->
                    val skippedText = if (exportResult.skippedCount > 0) "，跳过 ${exportResult.skippedCount} 首在线歌曲" else ""
                    Toast.makeText(context, "已导出 ${exportResult.exportedCount} 首$skippedText", Toast.LENGTH_SHORT).show()
                }
                .onFailure {
                    Toast.makeText(context, "导出失败：${it.message.orEmpty()}", Toast.LENGTH_SHORT).show()
                }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(ellaPageBackground())
            .windowInsetsPadding(WindowInsets.statusBars)
    ) {
        Box {
            EllaSmallTopAppBar(
                title = when {
                    playlist == null -> "歌单"
                    isFiveStarPlaylist -> playlist.name
                    listState.firstVisibleItemIndex > 0 -> playlist.name
                    else -> "自定义歌单"
                },
                color = ellaPageBackground(),
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = MiuixIcons.Regular.Back,
                            contentDescription = "返回",
                            tint = MiuixTheme.colorScheme.onSurface
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
                    if (playlist != null && !isFiveStarPlaylist) {
                        IconButton(onClick = { showExportFormatSheet = true }) {
                            Icon(
                                imageVector = MiuixIcons.Regular.Share,
                                contentDescription = "导出歌单",
                                tint = MiuixTheme.colorScheme.onSurface,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                }
            )
            DoubleTapScrollOverlay(
                onDoubleTap = { scope.launch { listState.animateScrollToItem(0) } },
                onClick = { scope.launch { listState.animateScrollToItem(0) } },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                endPadding = 160.dp
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
                PlaylistSongSortMode.entries.forEach { mode ->
                    Text(
                        text = mode.label,
                        fontSize = 14.sp,
                        fontWeight = if (sortMode == mode) FontWeight.Bold else FontWeight.Normal,
                        color = if (sortMode == mode) MiuixTheme.colorScheme.primary else MiuixTheme.colorScheme.onSurface,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                sortExpanded = false
                                scope.launch { mainViewModel.settingsManager.setPlaylistDetailSongSortIndex(mode.ordinal) }
                                scope.launch { listState.animateScrollToItem(0) }
                            }
                            .padding(vertical = 10.dp)
                    )
                }
            }
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
                placeholder = "搜索歌单内歌曲",
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 4.dp)
            )
        }

        if (playlist == null) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("歌单不存在", color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
            }
            return@Column
        }

        Box(modifier = Modifier.fillMaxSize()) {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 150.dp)
            ) {
                item {
                    PlaylistDetailHero(
                        playlist = playlist,
                        coverModel = playlistCoverModel,
                        songCount = sortedSongs.size,
                        duration = sortedSongs.sumOf { it.duration },
                        sortLabel = sortMode.label
                    )
                }

                item {
                    PlaylistPlayAllBar(
                        songCount = displayedSongs.size,
                        sortLabel = sortMode.label,
                        onPlayAll = {
                            if (displayedSongs.isNotEmpty()) {
                                playerViewModel.setPlaylist(displayedSongs, 0)
                                if (openPlayerOnPlay) onNavigateToPlayer()
                            }
                        },
                        onSort = { sortExpanded = !sortExpanded }
                    )
                }

            if (displayedSongs.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 60.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = when {
                                searchQuery.isNotBlank() -> "没有匹配的歌曲"
                                playlist.isFavorites -> "播放页点红心后会收藏到这里"
                                playlist.isFiveStarRating -> "文件标签里评分为五星的歌曲会显示在这里"
                                else -> "这个歌单还没有歌曲"
                            },
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                            fontSize = 14.sp
                        )
                    }
                }
            } else {
                itemsIndexed(displayedSongs, key = { _, song -> song.playlistIdentityKey() }) { index, song ->
                    SongItem(
                        song = song,
                        isCurrent = currentSong?.playlistIdentityKey() == song.playlistIdentityKey(),
                        albumArtUri = mainViewModel.getAlbumArtUri(song.albumId),
                        loadCoverArt = mainViewModel::getCoverArtBitmap,
                        loadAudioInfo = mainViewModel::getAudioInfo,
                        onClick = {
                            playerViewModel.setPlaylist(displayedSongs, index)
                            if (openPlayerOnPlay) onNavigateToPlayer()
                        },
                        onAddToQueue = { playerViewModel.addToPlaylist(song) },
                        onRemove = if (playlist.isFiveStarRating) null else {
                            {
                                removeFromPlaylistSong = song
                            }
                        },
                        onMore = { actionSong = song },
                        leadingLabel = (index + 1).toString(),
                        leadingLabelBeforeCover = true
                    )
                }
            }
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
                onNavigateToAlbum = onNavigateToAlbum,
                onNavigateToArtist = onNavigateToArtist,
                onSongRemovedFromPlaylist = if (playlist.isFiveStarRating) null else {
                    { song -> removeFromPlaylistSong = song }
                }
            )

            removeFromPlaylistSong?.let { song ->
                ConfirmDangerDialog(
                    show = true,
                    title = "从歌单移除",
                    message = "确定要从「${playlist.name}」移除《${song.title.ifBlank { song.fileName.ifBlank { "这首歌" } }}》吗？不会删除本地文件。",
                    confirmText = "移除",
                    onDismiss = { removeFromPlaylistSong = null },
                    onConfirm = {
                        mainViewModel.removeSongFromPlaylist(playlist.id, song.playlistIdentityKey())
                        removeFromPlaylistSong = null
                    }
                )
            }
        }
    }

    if (showExportFormatSheet && playlist != null) {
        ExportPlaylistFormatSheet(
            onDismiss = { showExportFormatSheet = false },
            onFormatSelected = { format ->
                val extension = when (format) {
                    PlaylistExportFormat.PlainText -> "txt"
                    PlaylistExportFormat.M3u -> "m3u"
                }
                showExportFormatSheet = false
                val fileName = "${playlist.name.safePlaylistFileName()}.$extension"
                when (format) {
                    PlaylistExportFormat.PlainText -> txtExportLauncher.launch(fileName)
                    PlaylistExportFormat.M3u -> m3uExportLauncher.launch(fileName)
                }
            }
        )
    }
}

private fun String.safePlaylistFileName(): String =
    replace(Regex("[\\\\/:*?\"<>|]"), "_").trim().ifBlank { "Ella Playlist" }

@Composable
private fun PlaylistDetailHero(
    playlist: UserPlaylist,
    coverModel: Any?,
    songCount: Int,
    duration: Long,
    sortLabel: String
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 170.dp)
            .background(
                Brush.verticalGradient(
                    0f to MiuixTheme.colorScheme.primary.copy(alpha = 0.20f),
                    0.64f to MiuixTheme.colorScheme.primary.copy(alpha = 0.08f),
                    1f to Color.Transparent
                )
            )
            .padding(horizontal = 18.dp, vertical = 18.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(128.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MiuixTheme.colorScheme.surfaceContainer),
                contentAlignment = Alignment.Center
            ) {
                if (coverModel != null) {
                    SafeCoverImage(
                        model = coverModel,
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop,
                        sizePx = 256
                    )
                } else {
                    DefaultAlbumCover(modifier = Modifier.fillMaxSize())
                }
                Text(
                    text = "▶ $songCount",
                    fontSize = 12.sp,
                    color = Color.White,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp)
                        .clip(RoundedCornerShape(999.dp))
                        .background(Color.Black.copy(alpha = 0.34f))
                        .padding(horizontal = 7.dp, vertical = 3.dp)
                )
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = playlist.name,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = MiuixTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "$songCount 首歌曲 · ${duration.formatPlaylistDuration()} · $sortLabel",
                    fontSize = 13.sp,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "编辑信息 >",
                    fontSize = 13.sp,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary.copy(alpha = 0.78f)
                )
            }
        }
    }
}

@Composable
private fun PlaylistPlayAllBar(
    songCount: Int,
    sortLabel: String,
    onPlayAll: () -> Unit,
    onSort: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 18.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(MiuixTheme.colorScheme.primary)
                .clickable(onClick = onPlayAll),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = MiuixIcons.Regular.Playlist,
                contentDescription = "播放全部",
                tint = Color.White,
                modifier = Modifier.size(20.dp)
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = "播放全部",
            fontSize = 18.sp,
            fontWeight = FontWeight.ExtraBold,
            color = MiuixTheme.colorScheme.onSurface,
            modifier = Modifier.clickable(onClick = onPlayAll)
        )
        Text(
            text = "（$songCount）",
            fontSize = 14.sp,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            modifier = Modifier.padding(start = 4.dp)
        )
        Spacer(modifier = Modifier.weight(1f))
        Text(
            text = sortLabel,
            fontSize = 12.sp,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .clip(RoundedCornerShape(999.dp))
                .clickable(onClick = onSort)
                .background(MiuixTheme.colorScheme.surfaceContainer.copy(alpha = 0.70f))
                .padding(horizontal = 10.dp, vertical = 7.dp)
        )
    }
}

private enum class PlaylistSortMode(val label: String) {
    Custom("自定义"),
    CustomDesc("自定义倒序"),
    UpdatedAt("最近更新"),
    CreatedAt("创建时间倒序"),
    CreatedAtAsc("创建时间"),
    Name("名称"),
    SongCount("歌曲数"),
    Duration("歌曲时长")
}

private fun List<UserPlaylist>.sortedForPlaylistList(mode: PlaylistSortMode): List<UserPlaylist> {
    return when (mode) {
        PlaylistSortMode.Custom -> sortedByDescending { it.createdAt }
        PlaylistSortMode.CustomDesc -> sortedBy { it.createdAt }
        PlaylistSortMode.UpdatedAt -> sortedByDescending { it.updatedAt }
        PlaylistSortMode.CreatedAt -> sortedByDescending { it.createdAt }
        PlaylistSortMode.CreatedAtAsc -> sortedBy { it.createdAt }
        PlaylistSortMode.Name -> sortedBy { it.name.lowercase() }
        PlaylistSortMode.SongCount -> sortedByDescending { it.songs.size }
        PlaylistSortMode.Duration -> sortedByDescending { playlist -> playlist.songs.sumOf { it.duration } }
    }
}

private fun UserPlaylist.matchesPlaylistSearch(query: String): Boolean {
    if (query.isBlank()) return true
    return name.contains(query, ignoreCase = true) ||
        songs.any { song ->
            song.title.contains(query, ignoreCase = true) ||
                song.artist.contains(query, ignoreCase = true) ||
                song.album.contains(query, ignoreCase = true)
        }
}

private enum class PlaylistSongSortMode(val label: String) {
    Custom("自定义"),
    CustomDesc("自定义倒序"),
    AddedAt("加入时间"),
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

private fun List<Song>.sortedForPlaylistDetail(mode: PlaylistSongSortMode): List<Song> {
    return when (mode) {
        PlaylistSongSortMode.Custom -> this
        PlaylistSongSortMode.CustomDesc -> asReversed()
        PlaylistSongSortMode.AddedAt -> this
        PlaylistSongSortMode.Title -> sortedBy { it.title.lowercase() }
        PlaylistSongSortMode.FileName -> sortedBy { song ->
            song.fileName.ifBlank { song.path.substringAfterLast('/') }.lowercase()
        }
        PlaylistSongSortMode.Duration -> sortedByDescending { it.duration }
        PlaylistSongSortMode.YearAsc -> sortedByReleaseDate(ascending = true)
        PlaylistSongSortMode.YearDesc -> sortedByReleaseDate(ascending = false)
        PlaylistSongSortMode.DateAdded -> sortedByDescending { it.dateAdded }
        PlaylistSongSortMode.DateAddedAsc -> sortedBy { it.dateAdded }
        PlaylistSongSortMode.DateModified -> sortedByDescending { it.dateModified }
        PlaylistSongSortMode.DateModifiedAsc -> sortedBy { it.dateModified }
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
            .thenBy { it.album.lowercase() }
            .thenBy { if (it.discNumber > 0) it.discNumber else Int.MAX_VALUE }
            .thenBy { if (it.trackNumber > 0) it.trackNumber else Int.MAX_VALUE }
            .thenBy { it.title.lowercase() }
    )
}

private fun Song.releaseYearOrNull(): Int? =
    Regex("""\d{4}""").find(year)?.value?.toIntOrNull()

private fun Long.formatPlaylistDuration(): String {
    if (this <= 0L) return "00:00"
    val totalMinutes = this / 60_000L
    val hours = totalMinutes / 60L
    val minutes = totalMinutes % 60L
    return if (hours > 0) "${hours}小时${minutes}分" else "${minutes}分钟"
}

private fun Song?.playlistCoverModel(): Any? {
    val song = this ?: return null
    return song.coverUrl.takeIf { it.isNotBlank() }
        ?: song.albumId.takeIf { it > 0L }?.let { Uri.parse("content://media/external/audio/albumart/$it") }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PlaylistRow(
    playlist: UserPlaylist,
    coverModel: Any? = null,
    countOverride: Int? = null,
    durationOverride: Long? = null,
    accent: Boolean = false,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    onDelete: (() -> Unit)? = null
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 10.dp)
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            ),
        cornerRadius = 16.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(
                        color = if (accent) MiuixTheme.colorScheme.primary.copy(alpha = 0.18f)
                        else MiuixTheme.colorScheme.surfaceContainer,
                    ),
                contentAlignment = Alignment.Center
            ) {
                if (coverModel != null && !playlist.isFavorites && !playlist.isFiveStarRating) {
                    SafeCoverImage(
                        model = coverModel,
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop,
                        sizePx = 160
                    )
                } else {
                    Icon(
                        imageVector = when {
                            playlist.isFavorites -> MiuixIcons.Regular.FavoritesFill
                            playlist.isFiveStarRating -> FiveStarPlaylistIcon
                            else -> MiuixIcons.Regular.Playlist
                        },
                        contentDescription = null,
                        tint = if (accent) MiuixTheme.colorScheme.primary else MiuixTheme.colorScheme.onSurface,
                        modifier = Modifier.size(25.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = playlist.name,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    color = MiuixTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${countOverride ?: playlist.songs.size} 首歌曲 · ${(durationOverride ?: playlist.songs.sumOf { it.duration }).formatPlaylistDuration()}",
                    fontSize = 13.sp,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary
                )
            }
            if (onDelete != null) {
                Text(
                    text = "删除",
                    fontSize = 13.sp,
                    color = Color(0xFFE5484D),
                    modifier = Modifier
                        .clickable(onClick = onDelete)
                        .padding(8.dp)
                )
            }
        }
    }
}

private val FiveStarPlaylistIcon: ImageVector by lazy {
    ImageVector.Builder(
        name = "FiveStarPlaylist",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).apply {
        path(fill = SolidColor(Color.Black)) {
            moveTo(12f, 2.4f)
            lineTo(14.96f, 8.38f)
            lineTo(21.56f, 9.34f)
            lineTo(16.78f, 13.99f)
            lineTo(17.91f, 20.56f)
            lineTo(12f, 17.46f)
            lineTo(6.09f, 20.56f)
            lineTo(7.22f, 13.99f)
            lineTo(2.44f, 9.34f)
            lineTo(9.04f, 8.38f)
            close()
        }
    }.build()
}

@Composable
private fun CreatePlaylistDialog(
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
        title = "新建歌单",
        onDismissRequest = onDismiss
    ) {
        Column(
            modifier = Modifier.padding(bottom = 18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            TextField(
                value = name,
                onValueChange = { name = it },
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
                Spacer(modifier = Modifier.width(8.dp))
                Button(onClick = { onCreate(name) }) { Text("创建") }
            }
        }
    }
}

@Composable
private fun ImportPlaylistModeSheet(
    count: Int,
    onDismiss: () -> Unit,
    onModeSelected: (PlaylistImportMode) -> Unit
) {
    WindowBottomSheet(
        show = true,
        title = "导入歌单",
        onDismissRequest = onDismiss
    ) {
        Column(
            modifier = Modifier.padding(bottom = 18.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "已选择 $count 个歌单文件",
                fontSize = 13.sp,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
            )
            ImportModeItem(
                title = "替换所有歌单",
                summary = "清空现有自定义歌单后重新导入",
                onClick = { onModeSelected(PlaylistImportMode.ReplaceAll) }
            )
            ImportModeItem(
                title = "合并并替换同名歌单",
                summary = "保留其它歌单，同名歌单使用导入内容覆盖",
                onClick = { onModeSelected(PlaylistImportMode.MergeReplaceExisting) }
            )
            ImportModeItem(
                title = "合并并保留同名歌单",
                summary = "同名歌单只追加不存在的歌曲",
                onClick = { onModeSelected(PlaylistImportMode.MergeKeepExisting) }
            )
            Button(onClick = onDismiss, modifier = Modifier.align(Alignment.End)) {
                Text("取消")
            }
        }
    }
}

@Composable
private fun ExportPlaylistFormatSheet(
    onDismiss: () -> Unit,
    onFormatSelected: (PlaylistExportFormat) -> Unit
) {
    WindowBottomSheet(
        show = true,
        title = "导出歌单",
        onDismissRequest = onDismiss
    ) {
        Column(
            modifier = Modifier.padding(bottom = 18.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            ImportModeItem(
                title = "导出为 TXT",
                summary = "逐行导出本地歌曲路径",
                onClick = { onFormatSelected(PlaylistExportFormat.PlainText) }
            )
            ImportModeItem(
                title = "导出为 M3U",
                summary = "导出 #EXTM3U 播放列表，包含歌曲信息和本地路径",
                onClick = { onFormatSelected(PlaylistExportFormat.M3u) }
            )
            Button(onClick = onDismiss, modifier = Modifier.align(Alignment.End)) {
                Text("取消")
            }
        }
    }
}

@Composable
private fun ImportModeItem(
    title: String,
    summary: String,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        cornerRadius = 14.dp,
        onClick = onClick
    ) {
        Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
            Text(
                text = title,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = MiuixTheme.colorScheme.onSurface
            )
            Text(
                text = summary,
                fontSize = 12.sp,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                modifier = Modifier.padding(top = 3.dp)
            )
        }
    }
}
