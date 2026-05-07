package com.ella.music.ui.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ella.music.data.model.Song
import com.ella.music.ui.components.SongItem
import com.ella.music.viewmodel.MainViewModel
import com.ella.music.viewmodel.PlayerViewModel
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.InputField
import top.yukonga.miuix.kmp.basic.PullToRefresh
import top.yukonga.miuix.kmp.basic.SearchBar
import top.yukonga.miuix.kmp.basic.SmallTopAppBar
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.basic.Search
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun HomeScreen(
    mainViewModel: MainViewModel,
    playerViewModel: PlayerViewModel,
    onNavigateToPlayer: () -> Unit,
    onNavigateToAbout: () -> Unit
) {
    val songs by mainViewModel.songs.collectAsState()
    val currentSong by playerViewModel.currentSong.collectAsState()
    val isScanning by mainViewModel.isScanning.collectAsState()

    var searchQuery by remember { mutableStateOf("") }
    var searchExpanded by remember { mutableStateOf(false) }
    var sortExpanded by remember { mutableStateOf(false) }
    var sortMode by remember { mutableStateOf(HomeSortMode.Title) }
    var selectionMode by remember { mutableStateOf(false) }
    var selectedIds by remember { mutableStateOf(setOf<Long>()) }
    val scope = rememberCoroutineScope()

    val filteredSongs = remember(songs, searchQuery) {
        if (searchQuery.isBlank()) songs
        else songs.filter {
            it.title.contains(searchQuery, ignoreCase = true) ||
                it.artist.contains(searchQuery, ignoreCase = true) ||
                it.album.contains(searchQuery, ignoreCase = true)
        }
    }
    val sortedSongs = remember(filteredSongs, sortMode) {
        when (sortMode) {
            HomeSortMode.Title -> filteredSongs.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.title })
            HomeSortMode.FileName -> filteredSongs.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.fileName.ifBlank { it.path.substringAfterLast('/') } })
            HomeSortMode.DateAdded -> filteredSongs.sortedByDescending { it.dateAdded }
            HomeSortMode.DateModified -> filteredSongs.sortedByDescending { it.dateModified }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.statusBars)
    ) {
        SmallTopAppBar(
            title = "Ella Music",
            color = MiuixTheme.colorScheme.background,
            actions = {
                if (selectionMode) {
                    IconButton(onClick = {
                        val selectedSongs = sortedSongs.filter { it.id in selectedIds }
                        mainViewModel.deleteSongs(selectedSongs)
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
                    IconButton(onClick = { sortExpanded = !sortExpanded }) {
                        Text(text = "排序", fontSize = 13.sp, color = MiuixTheme.colorScheme.onSurface)
                    }
                    IconButton(onClick = {
                        selectionMode = true
                        selectedIds = emptySet()
                    }) {
                        Text(text = "多选", fontSize = 13.sp, color = MiuixTheme.colorScheme.onSurface)
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
                                sortMode = mode
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
            SearchBar(
                inputField = {
                    InputField(
                        query = searchQuery,
                        onQueryChange = { searchQuery = it },
                        onSearch = { searchExpanded = false },
                        expanded = searchExpanded,
                        onExpandedChange = { searchExpanded = it },
                        label = "搜索歌曲、艺术家或专辑"
                    )
                },
                expanded = searchExpanded,
                onExpandedChange = { searchExpanded = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 4.dp)
            ) {}
        }

        PullToRefresh(
            isRefreshing = isScanning,
            onRefresh = {
                scope.launch {
                    mainViewModel.scanMusic()
                }
            },
        ) {
            if (songs.isEmpty() && !isScanning) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "未找到歌曲，下拉刷新扫描",
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary
                    )
                }
            } else {
                val listState = rememberLazyListState()

                Column(modifier = Modifier.fillMaxSize()) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        Column(modifier = Modifier.fillMaxSize()) {
                            Text(
                                text = if (selectionMode) "已选择 ${selectedIds.size} 首" else "${sortedSongs.size} 首歌曲 · ${sortMode.label}",
                                fontSize = 13.sp,
                                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                            )

                            LazyColumn(state = listState) {
                                items(
                                    items = sortedSongs,
                                    key = { it.id }
                                ) { song ->
                                    val selected = song.id in selectedIds
                                    SongItem(
                                        song = song,
                                        isCurrent = currentSong?.id == song.id,
                                        albumArtUri = mainViewModel.getAlbumArtUri(song.albumId),
                                        loadCoverArt = mainViewModel::getCoverArtBitmap,
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
                                                playerViewModel.setPlaylist(sortedSongs, sortedSongs.indexOf(song))
                                                onNavigateToPlayer()
                                            }
                                        }
                                    )
                                }
                            }
                        }

                        if (sortMode == HomeSortMode.Title && sortedSongs.size > 30) {
                            FastIndexBar(
                                songs = sortedSongs,
                                modifier = Modifier
                                    .align(Alignment.CenterEnd)
                                    .fillMaxHeight()
                                    .padding(end = 2.dp),
                                onLetterClick = { letter ->
                                    val index = sortedSongs.indexOfFirst { it.indexLetter() == letter }
                                    if (index >= 0) {
                                        scope.launch { listState.animateScrollToItem(index) }
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

private enum class HomeSortMode(val label: String) {
    Title("歌曲名称"),
    FileName("文件名"),
    DateAdded("添加时间"),
    DateModified("修改时间")
}

@Composable
private fun FastIndexBar(
    songs: List<Song>,
    onLetterClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val letters = remember(songs) {
        songs.map { it.indexLetter() }.distinct()
    }
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center
    ) {
        letters.forEach { letter ->
            Text(
                text = letter,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = MiuixTheme.colorScheme.primary,
                modifier = Modifier
                    .clickable { onLetterClick(letter) }
                    .padding(horizontal = 8.dp, vertical = 1.dp)
            )
        }
    }
}

private fun Song.indexLetter(): String {
    val first = title.trim().firstOrNull()?.uppercaseChar()
    return if (first != null && first in 'A'..'Z') first.toString() else "#"
}
