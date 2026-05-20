package com.ella.music.ui.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import android.app.Activity
import android.content.ClipData
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.icu.text.Transliterator
import android.os.Build
import android.provider.MediaStore
import android.widget.Toast
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import androidx.core.content.FileProvider
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ella.music.R
import com.ella.music.data.SettingsManager
import com.ella.music.data.detailedAudioInfo
import com.ella.music.data.model.AudioInfo
import com.ella.music.data.model.FAVORITES_PLAYLIST_ID
import com.ella.music.data.model.Song
import com.ella.music.data.model.SongTagInfo
import com.ella.music.data.model.UserPlaylist
import com.ella.music.data.model.albumIdentityId
import com.ella.music.data.splitArtistNames
import com.ella.music.ui.LibrarySortUiState
import com.ella.music.ui.components.EllaSearchBar
import com.ella.music.ui.components.FastIndexBar
import com.ella.music.ui.components.SongItem
import com.ella.music.ui.components.TagEditorOption
import com.ella.music.ui.components.buildTagEditorOptions
import com.ella.music.ui.components.ellaPageBackground
import com.ella.music.ui.components.launchTagEditorOption
import com.ella.music.viewmodel.MainViewModel
import com.ella.music.viewmodel.PlayerViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.SmallTopAppBar
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.basic.Search
import top.yukonga.miuix.kmp.icon.extended.SelectAll
import top.yukonga.miuix.kmp.icon.extended.Sort
import top.yukonga.miuix.kmp.theme.MiuixTheme
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
    val locateCurrentSongRequest by playerViewModel.locateCurrentSongRequest.collectAsState()
    val isScanning by mainViewModel.isScanning.collectAsState()
    val scanProgress by mainViewModel.scanProgress.collectAsState()
    val context = LocalContext.current
    val settingsManager = remember(context) { SettingsManager(context) }
    val openPlayerOnPlay by settingsManager.openPlayerOnPlay.collectAsState(initial = true)

    var searchQuery by remember { mutableStateOf("") }
    var searchExpanded by remember { mutableStateOf(false) }
    var sortExpanded by remember { mutableStateOf(false) }
    val sortMode = HomeSortMode.entries.getOrElse(LibrarySortUiState.librarySongSortIndex) { HomeSortMode.Title }
    var selectionMode by remember { mutableStateOf(false) }
    var selectedIds by remember { mutableStateOf(setOf<Long>()) }
    var actionSong by remember { mutableStateOf<Song?>(null) }
    var playlistPickerSongs by remember { mutableStateOf<List<Song>?>(null) }
    var tagEditorSong by remember { mutableStateOf<Song?>(null) }
    var songInfoSheetSong by remember { mutableStateOf<Song?>(null) }
    var listCoversEnabled by remember { mutableStateOf(false) }
    var pendingSystemDeleteSongs by remember { mutableStateOf<List<Song>>(emptyList()) }
    val scope = rememberCoroutineScope()
    val deleteRequestLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        val songsToDelete = pendingSystemDeleteSongs
        pendingSystemDeleteSongs = emptyList()
        if (result.resultCode == Activity.RESULT_OK && songsToDelete.isNotEmpty()) {
            mainViewModel.removeSongsFromLibrary(songsToDelete)
            Toast.makeText(context, "已删除 ${songsToDelete.size} 首歌曲", Toast.LENGTH_SHORT).show()
        } else if (songsToDelete.isNotEmpty()) {
            Toast.makeText(context, "已取消删除", Toast.LENGTH_SHORT).show()
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
                    Toast.makeText(context, "正在删除 ${songsToDelete.size} 首歌曲", Toast.LENGTH_SHORT).show()
                }
                return
            }
        }
        mainViewModel.deleteSongs(songsToDelete)
        Toast.makeText(context, "正在删除 ${songsToDelete.size} 首歌曲", Toast.LENGTH_SHORT).show()
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
                HomeSortMode.DateAdded -> HomeSortedSongs(filteredSongs.sortedByDescending { it.dateAdded }, emptyMap())
                HomeSortMode.DateModified -> HomeSortedSongs(filteredSongs.sortedByDescending { it.dateModified }, emptyMap())
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
        SmallTopAppBar(
            title = "音乐库",
            color = ellaPageBackground(),
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
                        Text(text = "添加到歌单", fontSize = 13.sp, color = MiuixTheme.colorScheme.primary)
                    }
                    IconButton(onClick = {
                        val selectedSongs = sortedSongs.filter { it.id in selectedIds }
                        requestDeleteSongs(selectedSongs)
                        selectedIds = emptySet()
                        selectionMode = false
                    }) {
                        Text(text = "删除", fontSize = 13.sp, color = MiuixTheme.colorScheme.primary)
                    }
                    IconButton(onClick = {
                        selectedIds = emptySet()
                        selectionMode = false
                    }) {
                        Text(text = "取消", fontSize = 13.sp, color = MiuixTheme.colorScheme.onSurface)
                    }
                } else {
                    IconButton(onClick = { playerViewModel.requestLocateCurrentSong() }) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_my_location),
                            contentDescription = "定位当前歌曲",
                            tint = MiuixTheme.colorScheme.onSurface,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    IconButton(onClick = { sortExpanded = !sortExpanded }) {
                        Icon(
                            imageVector = MiuixIcons.Regular.Sort,
                            contentDescription = "排序",
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
                            contentDescription = "多选",
                            tint = MiuixTheme.colorScheme.onSurface,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
                IconButton(onClick = { searchExpanded = !searchExpanded }) {
                    Icon(
                        imageVector = MiuixIcons.Basic.Search,
                        contentDescription = "搜索",
                        tint = MiuixTheme.colorScheme.onSurface,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        )

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
                                sortExpanded = false
                            }
                            .padding(vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = mode.label,
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
                placeholder = "搜索歌曲、艺术家或专辑",
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
                text = "正在扫描 ${scanProgress} 首歌曲...",
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
                    text = "未找到歌曲，点击右上角刷新扫描",
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary
                )
            }
        } else if (sortedResult == null) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "正在整理歌曲...",
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary
                )
            }
        } else {
            val listState = rememberLazyListState()
            var fastScrollJob by remember { mutableStateOf<Job?>(null) }
            var handledLocateRequest by remember { mutableStateOf(locateCurrentSongRequest) }

            LaunchedEffect(locateCurrentSongRequest) {
                if (locateCurrentSongRequest <= 0 || locateCurrentSongRequest == handledLocateRequest) return@LaunchedEffect
                handledLocateRequest = locateCurrentSongRequest
                val index = sortedSongs.indexOfFirst { it.id == currentSong?.id }
                if (index >= 0) listState.animateScrollToItem(index)
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
                            "已选择 ${selectedIds.size} 首"
                        } else {
                            "${sortedSongs.size} 首歌曲 · ${sortMode.label}"
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
                }
            }
        }

        actionSong?.let { song ->
            Popup(
                alignment = Alignment.BottomCenter,
                onDismissRequest = { actionSong = null },
                properties = PopupProperties(focusable = true, dismissOnBackPress = true, dismissOnClickOutside = true)
            ) {
                SongActionMenu(
                    song = song,
                    onDismiss = { actionSong = null },
                    onAddToPlaylist = {
                        actionSong = null
                        playlistPickerSongs = listOf(song)
                    },
                    onPlayNext = {
                        actionSong = null
                        playerViewModel.playNext(song)
                        Toast.makeText(context, "已添加到下一首播放", Toast.LENGTH_SHORT).show()
                    },
                    onShare = {
                        actionSong = null
                        shareSong(context, song)
                    },
                    onInfo = {
                        actionSong = null
                        songInfoSheetSong = song
                    },
                    onArtist = {
                        actionSong = null
                        val artist = splitArtistNames(song.artist).firstOrNull().orEmpty()
                        if (artist.isNotBlank() && !artist.equals("Unknown", ignoreCase = true)) {
                            onNavigateToArtist(artist)
                        } else {
                            Toast.makeText(context, "这首歌没有可跳转的歌手信息", Toast.LENGTH_SHORT).show()
                        }
                    },
                    onAlbum = {
                        actionSong = null
                        val albumId = song.albumIdentityId()
                        if (albumId > 0L) {
                            onNavigateToAlbum(albumId)
                        } else {
                            Toast.makeText(context, "这首歌没有可跳转的专辑信息", Toast.LENGTH_SHORT).show()
                        }
                    },
                    onEditTag = {
                        actionSong = null
                        tagEditorSong = song
                    },
                    onDelete = {
                        actionSong = null
                        requestDeleteSongs(listOf(song))
                    }
                )
            }
        }

        playlistPickerSongs?.let { songsToAdd ->
            Popup(
                alignment = Alignment.BottomCenter,
                onDismissRequest = { playlistPickerSongs = null },
                properties = PopupProperties(focusable = true, dismissOnBackPress = true, dismissOnClickOutside = true)
            ) {
                AddToPlaylistMenu(
                    playlists = playlists.filterNot { it.id == FAVORITES_PLAYLIST_ID },
                    songCount = songsToAdd.size,
                    onDismiss = { playlistPickerSongs = null },
                    onPlaylistClick = { playlist ->
                        mainViewModel.addSongsToPlaylist(playlist.id, songsToAdd)
                        Toast.makeText(context, "已添加到 ${playlist.name}", Toast.LENGTH_SHORT).show()
                        playlistPickerSongs = null
                        selectedIds = emptySet()
                        selectionMode = false
                    }
                )
            }
        }

        tagEditorSong?.let { song ->
            Popup(
                alignment = Alignment.BottomCenter,
                onDismissRequest = { tagEditorSong = null },
                properties = PopupProperties(focusable = true, dismissOnBackPress = true, dismissOnClickOutside = true)
            ) {
                SongTagEditorMenu(
                    song = song,
                    options = buildTagEditorOptions(context, song),
                    onDismiss = { tagEditorSong = null },
                    onOptionClick = { option ->
                        launchTagEditorOption(context, option)
                        tagEditorSong = null
                    }
                )
            }
        }

        songInfoSheetSong?.let { song ->
            Popup(
                alignment = Alignment.BottomCenter,
                onDismissRequest = { songInfoSheetSong = null },
                properties = PopupProperties(focusable = true, dismissOnBackPress = true, dismissOnClickOutside = true)
            ) {
                SongInfoMenu(
                    song = song,
                    audioInfoLoader = mainViewModel::getAudioInfo,
                    tagInfoLoader = mainViewModel::getSongTagInfo,
                    onDismiss = { songInfoSheetSong = null }
                )
            }
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
    onInfo: () -> Unit,
    onArtist: () -> Unit,
    onAlbum: () -> Unit,
    onEditTag: () -> Unit,
    onDelete: () -> Unit
) {
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
            text = song.title,
            fontSize = 18.sp,
            fontWeight = FontWeight.ExtraBold,
            color = MiuixTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp)
        )
        LibraryMenuItem("添加到歌单", onAddToPlaylist)
        LibraryMenuItem("下一首播放", onPlayNext)
        LibraryMenuItem("分享", onShare)
        LibraryMenuItem("查看歌曲信息", onInfo)
        LibraryMenuItem("艺术家：${song.artist.ifBlank { "未知艺术家" }}", onArtist)
        LibraryMenuItem("专辑：${song.album.ifBlank { "未知专辑" }}", onAlbum)
        LibraryMenuItem("编辑歌曲标签信息", onEditTag)
        LibraryMenuItem("永久删除", onDelete, danger = true)
        LibraryMenuItem("取消", onDismiss)
    }
}

@Composable
private fun SongInfoMenu(
    song: Song,
    audioInfoLoader: (Song) -> AudioInfo,
    tagInfoLoader: (Song) -> SongTagInfo,
    onDismiss: () -> Unit
) {
    val audioInfo by produceState<AudioInfo?>(initialValue = null, song.id, song.dateModified, song.fileSize) {
        value = withContext(Dispatchers.IO) { audioInfoLoader(song) }
    }
    val tagInfo by produceState<SongTagInfo?>(initialValue = null, song.id, song.dateModified, song.fileSize) {
        value = withContext(Dispatchers.IO) { tagInfoLoader(song) }
    }

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
            text = "歌曲信息",
            fontSize = 18.sp,
            fontWeight = FontWeight.ExtraBold,
            color = MiuixTheme.colorScheme.onSurface,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp)
        )
        SongInfoRow("标题", tagInfo?.title?.ifBlank { song.title } ?: song.title)
        SongInfoRow("艺术家", tagInfo?.artist?.ifBlank { song.artist } ?: song.artist)
        SongInfoRow("专辑", tagInfo?.album?.ifBlank { song.album } ?: song.album)
        SongInfoRow("专辑艺术家", tagInfo?.albumArtist.orEmpty())
        SongInfoRow("注释", tagInfo?.comment.orEmpty())
        SongInfoRow("163 key", tagInfo?.neteaseKey.orEmpty())
        SongInfoRow("格式", audioInfo?.let { detailedAudioInfo(it) }.orEmpty())
        SongInfoRow("时长", song.durationText)
        SongInfoRow("大小", formatLibraryFileSize(song.fileSize))
        SongInfoRow("文件名", song.fileName.ifBlank { song.path.substringAfterLast('/') })
        SongInfoRow("路径", song.path)
        LibraryMenuItem("关闭", onDismiss)
    }
}

@Composable
private fun SongInfoRow(label: String, value: String) {
    if (value.isBlank()) return
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
            maxLines = if (label == "路径") 3 else 2,
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
    onPlaylistClick: (UserPlaylist) -> Unit
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
            text = "添加 $songCount 首到歌单",
            fontSize = 18.sp,
            fontWeight = FontWeight.ExtraBold,
            color = MiuixTheme.colorScheme.onSurface,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp)
        )
        if (playlists.isEmpty()) {
            Text(
                text = "暂无自定义歌单，请先在歌单页创建",
                fontSize = 14.sp,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 18.dp)
            )
        } else {
            playlists.forEach { playlist ->
                LibraryMenuItem(
                    text = "${playlist.name} · ${playlist.songs.size} 首",
                    onClick = { onPlaylistClick(playlist) }
                )
            }
        }
        LibraryMenuItem("取消", onDismiss)
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
            text = "编辑歌曲标签信息",
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
                text = "未找到 Lyrico、LunaBeat 或音乐标签，请先安装后再试",
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
        LibraryMenuItem("取消", onDismiss)
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

private fun shareSong(context: Context, song: Song) {
    if (song.path.startsWith("http://") || song.path.startsWith("https://")) {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, "${song.title} - ${song.artist}\n${song.path}")
        }
        runCatching {
            context.startActivity(Intent.createChooser(intent, "分享歌曲"))
        }.onFailure {
            Toast.makeText(context, "没有可用的分享应用", Toast.LENGTH_SHORT).show()
        }
        return
    }

    val uri = runCatching {
        val file = File(song.path)
        if (file.exists() && file.isFile) {
            FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        } else {
            ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, song.id)
        }
    }.getOrElse {
        ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, song.id)
    }

    val intent = Intent(Intent.ACTION_SEND).apply {
        type = song.mimeType.takeIf { it.startsWith("audio/") } ?: "audio/*"
        putExtra(Intent.EXTRA_TITLE, "${song.title} - ${song.artist}")
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        clipData = ClipData.newUri(context.contentResolver, song.title, uri)
    }
    runCatching {
        context.startActivity(Intent.createChooser(intent, "分享歌曲"))
    }.onFailure {
        Toast.makeText(context, "没有可用的分享应用", Toast.LENGTH_SHORT).show()
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

private enum class HomeSortMode(val label: String) {
    Title("歌曲名称"),
    FileName("文件名"),
    DateAdded("添加时间"),
    DateModified("修改时间")
}

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
