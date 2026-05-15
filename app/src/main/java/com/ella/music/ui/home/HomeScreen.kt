package com.ella.music.ui.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import android.icu.text.Transliterator
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.changedToUpIgnoreConsumed
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import android.os.SystemClock
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ella.music.R
import com.ella.music.data.SettingsManager
import com.ella.music.data.model.Song
import com.ella.music.ui.LibrarySortUiState
import com.ella.music.ui.components.EllaSearchBar
import com.ella.music.ui.components.SongItem
import com.ella.music.ui.components.ellaPageBackground
import com.ella.music.viewmodel.MainViewModel
import com.ella.music.viewmodel.PlayerViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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
import kotlin.math.floor
import java.util.Locale

@Composable
fun LibraryScreen(
    mainViewModel: MainViewModel,
    playerViewModel: PlayerViewModel,
    onNavigateToPlayer: () -> Unit,
    onNavigateToAbout: () -> Unit
) {
    val songs by mainViewModel.songs.collectAsState()
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
    var listCoversEnabled by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

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
                                onAddToQueue = {
                                    playerViewModel.addToPlaylist(song)
                                }
                            )
                        }
                    }
                }

                if (sortMode == HomeSortMode.Title && sortedSongs.size > 30) {
                    FastIndexBar(
                        songs = sortedSongs,
                        sortKeysBySongId = sortKeysBySongId,
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
    sortKeysBySongId: Map<Long, String>,
    onLetterClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val letters = remember(songs, sortKeysBySongId) {
        songs.map { it.indexLetter(sortKeysBySongId[it.id]) }.distinct()
    }
    var heightPx by remember { mutableStateOf(1) }
    var lastSelectedLetter by remember { mutableStateOf<String?>(null) }
    var lastDispatchTimeMs by remember { mutableStateOf(0L) }

    fun selectAt(y: Float, force: Boolean = false) {
        if (letters.isEmpty()) return
        val now = SystemClock.uptimeMillis()
        if (!force && now - lastDispatchTimeMs < 80L) return
        val index = floor((y.coerceIn(0f, heightPx.toFloat() - 1f) / heightPx) * letters.size)
            .toInt()
            .coerceIn(0, letters.lastIndex)
        val letter = letters[index]
        if (letter != lastSelectedLetter) {
            lastSelectedLetter = letter
            lastDispatchTimeMs = now
            onLetterClick(letter)
        }
    }

    Column(
        modifier = modifier
            .onSizeChanged { heightPx = it.height.coerceAtLeast(1) }
            .pointerInput(letters, heightPx) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    selectAt(down.position.y)
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull() ?: break
                        if (change.changedToUpIgnoreConsumed()) break
                        if (change.pressed) {
                            selectAt(change.position.y)
                            change.consume()
                        }
                    }
                    lastSelectedLetter = null
                }
            },
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
                    .clickable {
                        lastSelectedLetter = letter
                        lastDispatchTimeMs = SystemClock.uptimeMillis()
                        onLetterClick(letter)
                    }
                    .padding(horizontal = 8.dp, vertical = 1.dp)
            )
        }
    }
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
