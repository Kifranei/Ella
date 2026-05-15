package com.ella.music.ui.folder

import android.os.SystemClock
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
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
import androidx.compose.ui.input.pointer.changedToUpIgnoreConsumed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ella.music.data.model.Song
import com.ella.music.ui.LibrarySortUiState
import com.ella.music.ui.components.EllaSearchBar
import com.ella.music.ui.components.SongItem
import com.ella.music.ui.components.ellaPageBackground
import com.ella.music.viewmodel.MainViewModel
import com.ella.music.viewmodel.PlayerViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.basic.Search
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.icon.extended.Folder
import top.yukonga.miuix.kmp.icon.extended.Sort
import top.yukonga.miuix.kmp.theme.MiuixTheme
import kotlin.math.floor
import android.icu.text.Transliterator
import java.util.Locale

@Composable
fun FolderDetailScreen(
    folderPath: String,
    mainViewModel: MainViewModel,
    playerViewModel: PlayerViewModel,
    onBack: () -> Unit,
    onNavigateToPlayer: () -> Unit
) {
    val songs by mainViewModel.songs.collectAsState()
    val currentSong by playerViewModel.currentSong.collectAsState()
    val openPlayerOnPlay by mainViewModel.settingsManager.openPlayerOnPlay.collectAsState(initial = true)
    val scope = rememberCoroutineScope()
    var searchQuery by remember { mutableStateOf("") }
    var searchExpanded by remember { mutableStateOf(false) }
    var sortExpanded by remember { mutableStateOf(false) }
    val sortMode = FolderSongSortMode.entries.getOrElse(LibrarySortUiState.folderDetailSongSortIndex) { FolderSongSortMode.Title }

    val folderSongs = remember(songs, folderPath) {
        songs.filter { it.path.startsWith(folderPath, ignoreCase = true) }
    }
    val filteredSongs = remember(folderSongs, searchQuery) {
        if (searchQuery.isBlank()) folderSongs
        else folderSongs.filter {
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
            FolderSongSortMode.DateAdded -> filteredSongs.sortedByDescending { it.dateAdded }
            FolderSongSortMode.DateModified -> filteredSongs.sortedByDescending { it.dateModified }
        }
    }

    val folderName = folderPath.substringAfterLast('/')

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(ellaPageBackground())
            .windowInsetsPadding(WindowInsets.statusBars)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = MiuixIcons.Regular.Back,
                    contentDescription = "返回",
                    tint = MiuixTheme.colorScheme.onSurface,
                    modifier = Modifier.size(24.dp)
                )
            }
            Spacer(modifier = Modifier.width(4.dp))
            Icon(
                imageVector = MiuixIcons.Regular.Folder,
                contentDescription = null,
                tint = MiuixTheme.colorScheme.primary,
                modifier = Modifier.size(22.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Column {
                Text(
                    text = folderName.ifEmpty { "根目录" },
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = MiuixTheme.colorScheme.onSurface
                )
                Text(
                    text = "${folderSongs.size} 首歌曲",
                    fontSize = 12.sp,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary
                )
            }
            Spacer(modifier = Modifier.weight(1f))
            IconButton(onClick = { sortExpanded = !sortExpanded }) {
                Icon(
                    imageVector = MiuixIcons.Regular.Sort,
                    contentDescription = "排序",
                    tint = MiuixTheme.colorScheme.onSurface,
                    modifier = Modifier.size(24.dp)
                )
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
                placeholder = "搜索歌曲、艺术家、专辑或文件名",
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 4.dp)
            )
        }

        if (folderSongs.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "该文件夹没有音乐文件",
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary
                )
            }
        } else {
            val listState = rememberLazyListState()
            var fastScrollJob by remember { mutableStateOf<Job?>(null) }
            val fastIndexTargets = remember(sortedSongs) {
                sortedSongs
                    .mapIndexed { index, song -> song.indexLetter() to index }
                    .distinctBy { it.first }
                    .toMap()
            }
            Box(modifier = Modifier.fillMaxSize()) {
                Column(modifier = Modifier.fillMaxSize()) {
                    Text(
                        text = "${sortedSongs.size} 首歌曲 · ${sortMode.label}",
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
                            SongItem(
                                song = song,
                                isCurrent = currentSong?.id == song.id,
                                albumArtUri = mainViewModel.getAlbumArtUri(song.albumId),
                                loadCoverArt = mainViewModel::getCoverArtBitmap,
                                loadAudioInfo = mainViewModel::getAudioInfo,
                                onClick = {
                                    playerViewModel.setPlaylist(sortedSongs, index)
                                    if (openPlayerOnPlay) onNavigateToPlayer()
                                },
                                onAddToQueue = { playerViewModel.addToPlaylist(song) }
                            )
                        }
                    }
                }
                if (sortMode == FolderSongSortMode.Title && sortedSongs.size > 30) {
                    FolderFastIndexBar(
                        songs = sortedSongs,
                        modifier = Modifier
                            .align(Alignment.CenterEnd)
                            .fillMaxHeight()
                            .padding(end = 2.dp),
                        onLetterClick = { letter ->
                            val index = fastIndexTargets[letter]
                            if (index != null) {
                                fastScrollJob?.cancel()
                                fastScrollJob = scope.launch { listState.scrollToItem(index) }
                            }
                        }
                    )
                }
            }
        }
    }
}

private enum class FolderSongSortMode(val label: String) {
    Title("歌曲名称"),
    FileName("文件名"),
    DateAdded("添加时间"),
    DateModified("修改时间")
}

@Composable
private fun FolderFastIndexBar(
    songs: List<Song>,
    onLetterClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val letters = remember(songs) { songs.map { it.indexLetter() }.distinct() }
    var heightPx by remember { mutableStateOf(1) }
    var lastSelectedLetter by remember { mutableStateOf<String?>(null) }
    var lastDispatchTimeMs by remember { mutableStateOf(0L) }

    fun selectAt(y: Float) {
        if (letters.isEmpty()) return
        val now = SystemClock.uptimeMillis()
        if (now - lastDispatchTimeMs < 80L) return
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
        verticalArrangement = Arrangement.Center
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
