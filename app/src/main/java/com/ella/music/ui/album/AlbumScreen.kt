package com.ella.music.ui.album

import androidx.activity.compose.BackHandler
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ella.music.R
import com.ella.music.data.model.Album
import com.ella.music.ui.LibrarySortUiState
import com.ella.music.ui.components.AlbumCard
import com.ella.music.ui.components.DoubleTapScrollOverlay
import com.ella.music.ui.components.EllaSearchBar
import com.ella.music.ui.components.FastIndexBar
import com.ella.music.ui.components.LazyGridScrollIndicator
import com.ella.music.ui.components.ellaPageBackground
import com.ella.music.ui.components.requestPinnedEllaShortcut
import com.ella.music.ui.navigation.Screen
import com.ella.music.viewmodel.MainViewModel
import com.ella.music.viewmodel.PlayerViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import com.ella.music.ui.components.EllaSmallTopAppBar
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.basic.Search
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.icon.extended.Sort
import top.yukonga.miuix.kmp.theme.MiuixTheme
import android.icu.text.Transliterator
import com.ella.music.data.model.albumIdentityId
import com.ella.music.data.model.formatPlaybackDuration
import java.util.Locale

@Composable
fun AlbumScreen(
    mainViewModel: MainViewModel,
    playerViewModel: PlayerViewModel,
    onBack: () -> Unit,
    onAlbumClick: (Long) -> Unit
) {
    val context = LocalContext.current
    val albums by mainViewModel.albums.collectAsState()
    val songs by mainViewModel.songs.collectAsState()
    var searchQuery by remember { mutableStateOf("") }
    var searchExpanded by remember { mutableStateOf(false) }
    var sortExpanded by remember { mutableStateOf(false) }
    val sortIndex by mainViewModel.settingsManager.albumListSortIndex.collectAsState(initial = LibrarySortUiState.albumListSortIndex)
    val sortMode = AlbumSortMode.entries.getOrElse(sortIndex) { AlbumSortMode.Name }
    val gridColumns by mainViewModel.settingsManager.categoryGridColumns.collectAsState(initial = 2)
    val safeGridColumns = gridColumns.coerceIn(1, 4)
    val scope = rememberCoroutineScope()
    var scrollToTopRequest by remember { mutableStateOf(0) }
    val albumDurations = remember(songs) {
        songs.groupBy { it.albumIdentityId() }.mapValues { (_, albumSongs) -> albumSongs.sumOf { it.duration } }
    }

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
    val sortedAlbums = remember(filteredAlbums, sortMode, albumDurations) {
        when (sortMode) {
            AlbumSortMode.Name -> filteredAlbums.sortedBy { it.name.musicSortKey() }
            AlbumSortMode.Artist -> filteredAlbums.sortedBy { it.artist.musicSortKey() }
            AlbumSortMode.SongCount -> filteredAlbums.sortedByDescending { it.songCount }
            AlbumSortMode.Duration -> filteredAlbums.sortedByDescending { albumDurations[it.id] ?: 0L }
            AlbumSortMode.YearAsc -> filteredAlbums.sortedWith(compareBy<Album> { it.year <= 0 }.thenBy { it.year }.thenBy { it.name.musicSortKey() })
            AlbumSortMode.YearDesc -> filteredAlbums.sortedWith(compareBy<Album> { it.year <= 0 }.thenByDescending { it.year }.thenBy { it.name.musicSortKey() })
        }
    }

    BackHandler(enabled = searchExpanded || sortExpanded) {
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
                title = stringResource(R.string.tab_album),
                color = ellaPageBackground(),
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = MiuixIcons.Regular.Back,
                            contentDescription = stringResource(R.string.common_back),
                            tint = MiuixTheme.colorScheme.onSurface,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { sortExpanded = !sortExpanded }) {
                        Icon(
                            imageVector = MiuixIcons.Regular.Sort,
                            contentDescription = stringResource(R.string.common_sort),
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
            )
            DoubleTapScrollOverlay(
                onDoubleTap = { scrollToTopRequest++ },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                endPadding = 112.dp
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
                AlbumSortMode.entries.forEach { mode ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                LibrarySortUiState.albumListSortIndex = mode.ordinal
                                scope.launch { mainViewModel.settingsManager.setAlbumListSortIndex(mode.ordinal) }
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
                placeholder = stringResource(R.string.album_search_placeholder),
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
                    text = stringResource(R.string.album_empty),
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary
                )
            }
        } else {
            val gridState = rememberLazyGridState()
            var fastScrollJob by remember { mutableStateOf<Job?>(null) }
            LaunchedEffect(sortMode, searchQuery, safeGridColumns) {
                gridState.scrollToItem(0)
            }
            LaunchedEffect(scrollToTopRequest) {
                if (scrollToTopRequest > 0) gridState.animateScrollToItem(0)
            }
            val fastIndexTargets = remember(sortedAlbums) {
                sortedAlbums
                    .mapIndexed { index, album -> album.indexLetter() to index }
                    .distinctBy { it.first }
                    .toMap()
            }

            Box(modifier = Modifier.fillMaxSize()) {
                Column(modifier = Modifier.fillMaxSize()) {
                    Text(
                        text = stringResource(
                            R.string.album_list_summary,
                            sortedAlbums.size,
                            stringResource(sortMode.labelRes)
                        ),
                        fontSize = 13.sp,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )

                    LazyVerticalGrid(
                        columns = GridCells.Fixed(safeGridColumns),
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
                                representativeSong = mainViewModel.getSongsForAlbum(album.id).firstOrNull(),
                                loadCoverArt = mainViewModel::getAlbumCoverArtBitmap,
                                summary = album.summaryForSort(context, sortMode, albumDurations[album.id] ?: 0L),
                                onClick = { onAlbumClick(album.id) },
                                onLongClick = {
                                    val ok = requestPinnedEllaShortcut(
                                        context = context,
                                        id = "album_${album.id}",
                                        label = album.name,
                                        route = Screen.AlbumDetail.createRoute(album.id)
                                    )
                                    Toast.makeText(
                                        context,
                                        if (ok) {
                                            context.getString(R.string.playlist_shortcut_requested, album.name)
                                        } else {
                                            context.getString(R.string.playlist_shortcut_unsupported)
                                        },
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                            )
                        }
                    }
                }

                if (sortMode == AlbumSortMode.Name && sortedAlbums.size > 30) {
                    FastIndexBar(
                        letters = sortedAlbums.map { it.indexLetter() },
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
                } else if (sortedAlbums.size > 30) {
                    LazyGridScrollIndicator(
                        state = gridState,
                        modifier = Modifier
                            .align(Alignment.CenterEnd)
                            .fillMaxHeight()
                    )
                }
            }
        }
    }
}

private enum class AlbumSortMode(val labelRes: Int) {
    Name(R.string.album_sort_name),
    Artist(R.string.album_sort_artist),
    SongCount(R.string.playlist_sort_song_count),
    Duration(R.string.playlist_song_sort_duration),
    YearAsc(R.string.playlist_song_sort_year_asc),
    YearDesc(R.string.playlist_song_sort_year_desc)
}

private fun Album.summaryForSort(context: android.content.Context, sortMode: AlbumSortMode, duration: Long): String {
    val first = if (sortMode == AlbumSortMode.Duration) {
        duration.formatAlbumDuration(context)
    } else {
        context.getString(R.string.song_count, songCount)
    }
    return buildList {
        add(first)
        if (year > 0) add(year.toString())
        val artistText = albumArtist.trim()
        if (artistText.isNotBlank()) add(artistText)
    }.joinToString(" · ")
}

private fun Long.formatAlbumDuration(context: android.content.Context): String {
    return formatPlaybackDuration()
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
