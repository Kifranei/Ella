package com.ella.music.ui.album

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
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
import android.os.SystemClock
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ella.music.data.model.Album
import com.ella.music.ui.LibrarySortUiState
import com.ella.music.ui.components.AlbumCard
import com.ella.music.ui.components.EllaSearchBar
import com.ella.music.ui.components.ellaPageBackground
import com.ella.music.viewmodel.MainViewModel
import com.ella.music.viewmodel.PlayerViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.SmallTopAppBar
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.basic.Search
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.icon.extended.Sort
import top.yukonga.miuix.kmp.theme.MiuixTheme
import kotlin.math.floor
import android.icu.text.Transliterator
import java.util.Locale

@Composable
fun AlbumScreen(
    mainViewModel: MainViewModel,
    playerViewModel: PlayerViewModel,
    onBack: () -> Unit,
    onAlbumClick: (Long) -> Unit
) {
    val albums by mainViewModel.albums.collectAsState()
    var searchQuery by remember { mutableStateOf("") }
    var searchExpanded by remember { mutableStateOf(false) }
    var sortExpanded by remember { mutableStateOf(false) }
    val sortMode = AlbumSortMode.entries.getOrElse(LibrarySortUiState.albumListSortIndex) { AlbumSortMode.Name }
    val scope = rememberCoroutineScope()

    val filteredAlbums = remember(albums, searchQuery) {
        if (searchQuery.isBlank()) {
            albums
        } else {
            albums.filter {
                it.name.contains(searchQuery, ignoreCase = true) ||
                    it.artist.contains(searchQuery, ignoreCase = true)
            }
        }
    }
    val sortedAlbums = remember(filteredAlbums, sortMode) {
        when (sortMode) {
            AlbumSortMode.Name -> filteredAlbums.sortedBy { it.name.musicSortKey() }
            AlbumSortMode.Artist -> filteredAlbums.sortedBy { it.artist.musicSortKey() }
            AlbumSortMode.SongCount -> filteredAlbums.sortedByDescending { it.songCount }
            AlbumSortMode.Year -> filteredAlbums.sortedByDescending { it.year }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(ellaPageBackground())
            .windowInsetsPadding(WindowInsets.statusBars)
    ) {
        SmallTopAppBar(
            title = "专辑",
            color = ellaPageBackground(),
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
            visible = sortExpanded,
            enter = expandVertically(),
            exit = shrinkVertically()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp)
            ) {
                AlbumSortMode.entries.forEach { mode ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                LibrarySortUiState.albumListSortIndex = mode.ordinal
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
                placeholder = "搜索专辑或艺术家",
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 4.dp)
            )
        }

        if (albums.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "未找到专辑",
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary
                )
            }
        } else {
            val gridState = rememberLazyGridState()
            var fastScrollJob by remember { mutableStateOf<Job?>(null) }
            val fastIndexTargets = remember(sortedAlbums) {
                sortedAlbums
                    .mapIndexed { index, album -> album.indexLetter() to index }
                    .distinctBy { it.first }
                    .toMap()
            }

            Box(modifier = Modifier.fillMaxSize()) {
                Column(modifier = Modifier.fillMaxSize()) {
                    Text(
                        text = "${sortedAlbums.size} 张专辑 · ${sortMode.label}",
                        fontSize = 13.sp,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )

                    LazyVerticalGrid(
                        columns = GridCells.Fixed(2),
                        state = gridState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(bottom = 160.dp)
                    ) {
                        items(
                            items = sortedAlbums,
                            key = { it.id }
                        ) { album ->
                            AlbumCard(
                                album = album,
                                albumArtUri = mainViewModel.getAlbumArtUri(album.artAlbumId),
                                onClick = { onAlbumClick(album.id) }
                            )
                        }
                    }
                }

                if (sortMode == AlbumSortMode.Name && sortedAlbums.size > 30) {
                    AlbumFastIndexBar(
                        albums = sortedAlbums,
                        modifier = Modifier
                            .align(Alignment.CenterEnd)
                            .fillMaxHeight()
                            .padding(end = 2.dp),
                        onLetterClick = { letter ->
                            val index = fastIndexTargets[letter]
                            if (index != null) {
                                fastScrollJob?.cancel()
                                fastScrollJob = scope.launch { gridState.scrollToItem(index) }
                            }
                        }
                    )
                }
            }
        }
    }
}

private enum class AlbumSortMode(val label: String) {
    Name("专辑名称"),
    Artist("艺术家"),
    SongCount("歌曲数量"),
    Year("年份")
}

@Composable
private fun AlbumFastIndexBar(
    albums: List<Album>,
    onLetterClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val letters = remember(albums) {
        albums.map { it.indexLetter() }.distinct()
    }
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

private fun Album.indexLetter(): String {
    val first = name.musicSortKey().firstOrNull()?.uppercaseChar()
    return if (first != null && first in 'A'..'Z') first.toString() else "#"
}

private fun String.musicSortKey(): String {
    val text = trim()
    if (text.isBlank()) return ""
    if (text.isAsciiSortable()) return text.lowercase(Locale.ROOT)

    AlbumSortKeyCache[text]?.let { return it }

    val latin = runCatching {
        AlbumSortTransliterator.value.transliterate(text)
    }.getOrDefault(text)

    return latin.lowercase(Locale.ROOT).also {
        AlbumSortKeyCache[text] = it
    }
}

private fun String.isAsciiSortable(): Boolean {
    return all { it.code in 0x20..0x7E }
}

private object AlbumSortTransliterator {
    val value: Transliterator by lazy {
        Transliterator.getInstance("Any-Latin; Latin-ASCII; NFD; [:Nonspacing Mark:] Remove; NFC")
    }
}

private object AlbumSortKeyCache {
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
