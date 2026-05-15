package com.ella.music.ui.artist

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.changedToUpIgnoreConsumed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ella.music.data.model.Artist
import com.ella.music.data.model.Song
import com.ella.music.data.splitArtistNames
import com.ella.music.ui.LibrarySortUiState
import com.ella.music.ui.components.EllaSearchBar
import com.ella.music.ui.components.SafeCoverImage
import com.ella.music.ui.components.ellaPageBackground
import com.ella.music.viewmodel.MainViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import android.os.SystemClock
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.SmallTopAppBar
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.basic.Search
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.icon.extended.Music
import top.yukonga.miuix.kmp.icon.extended.Sort
import top.yukonga.miuix.kmp.theme.MiuixTheme
import kotlin.math.floor

@Composable
fun ArtistListScreen(
    mainViewModel: MainViewModel,
    onBack: () -> Unit,
    onArtistClick: (String) -> Unit
) {
    val songs by mainViewModel.songs.collectAsState()
    val albums by mainViewModel.albums.collectAsState()
    var searchQuery by remember { mutableStateOf("") }
    var searchExpanded by remember { mutableStateOf(false) }
    var sortExpanded by remember { mutableStateOf(false) }
    val sortMode = ArtistSortMode.entries.getOrElse(LibrarySortUiState.artistListSortIndex) { ArtistSortMode.Name }
    val scope = androidx.compose.runtime.rememberCoroutineScope()

    val artists = remember(songs, albums) { mainViewModel.getArtists() }
    val representativeSongsByArtist = remember(songs) {
        buildMap {
            songs.forEach { song ->
                splitArtistNames(song.artist).forEach { artistName ->
                    putIfAbsent(artistName, song)
                }
            }
        }
    }
    val filteredArtists = remember(artists, searchQuery, sortMode) {
        val filtered = if (searchQuery.isBlank()) {
            artists
        } else {
            artists.filter { it.name.contains(searchQuery, ignoreCase = true) }
        }
        when (sortMode) {
            ArtistSortMode.Name -> filtered.sortedBy { it.name.lowercase() }
            ArtistSortMode.SongCount -> filtered.sortedByDescending { it.songCount }
            ArtistSortMode.AlbumCount -> filtered.sortedByDescending { it.albumCount }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(ellaPageBackground())
            .windowInsetsPadding(WindowInsets.statusBars)
    ) {
        SmallTopAppBar(
            title = "艺术家",
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
            Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
                ArtistSortMode.entries.forEach { mode ->
                    Text(
                        text = mode.label,
                        fontSize = 14.sp,
                        fontWeight = if (sortMode == mode) FontWeight.Bold else FontWeight.Normal,
                        color = if (sortMode == mode) MiuixTheme.colorScheme.primary else MiuixTheme.colorScheme.onSurface,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                LibrarySortUiState.artistListSortIndex = mode.ordinal
                                sortExpanded = false
                            }
                            .padding(vertical = 10.dp)
                    )
                }
            }
        }

        if (searchExpanded) {
            EllaSearchBar(
                query = searchQuery,
                onQueryChange = { searchQuery = it },
                onSearch = { searchExpanded = false },
                placeholder = "搜索艺术家",
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 4.dp)
            )
        }

        if (filteredArtists.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(text = "未找到艺术家", color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
            }
        } else {
            val listState = rememberLazyListState()
            var fastScrollJob by remember { mutableStateOf<Job?>(null) }
            val fastIndexTargets = remember(filteredArtists) {
                filteredArtists
                    .mapIndexed { index, artist -> artist.indexLetter() to index + 1 }
                    .distinctBy { it.first }
                    .toMap()
            }
            Box(modifier = Modifier.fillMaxSize()) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 160.dp)
                ) {
                    item {
                        Text(
                            text = "${filteredArtists.size} 位艺术家 · ${sortMode.label}",
                            fontSize = 13.sp,
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                        )
                    }
                    items(filteredArtists, key = { it.name }) { artist ->
                        ArtistRow(
                            artist = artist,
                            representativeSong = representativeSongsByArtist[artist.name],
                            mainViewModel = mainViewModel,
                            onClick = { onArtistClick(artist.name) }
                        )
                    }
                }

                if (sortMode == ArtistSortMode.Name && filteredArtists.size > 30) {
                    ArtistFastIndexBar(
                        artists = filteredArtists,
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

@Composable
private fun ArtistFastIndexBar(
    artists: List<Artist>,
    onLetterClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val letters = remember(artists) { artists.map { it.indexLetter() }.distinct() }
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

private fun Artist.indexLetter(): String {
    val first = name.trim().firstOrNull()?.uppercaseChar()
    return if (first != null && first in 'A'..'Z') first.toString() else "#"
}

@Composable
private fun ArtistRow(
    artist: Artist,
    representativeSong: Song?,
    mainViewModel: MainViewModel,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(76.dp)
            .clickable(onClick = onClick)
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
            val albumArtUri = representativeSong?.let { mainViewModel.getAlbumArtUri(it.albumId) }
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
                text = artist.name.ifBlank { "未知歌手" },
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                color = MiuixTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = "${artist.songCount} 首歌曲 · ${artist.albumCount} 张专辑",
                fontSize = 12.sp,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

private enum class ArtistSortMode(val label: String) {
    Name("名称"),
    SongCount("歌曲数"),
    AlbumCount("专辑数")
}
