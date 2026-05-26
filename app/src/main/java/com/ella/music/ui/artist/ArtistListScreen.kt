package com.ella.music.ui.artist

import androidx.activity.compose.BackHandler
import android.graphics.Bitmap
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.background
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ella.music.data.model.Artist
import com.ella.music.data.model.Song
import com.ella.music.data.splitArtistNames
import com.ella.music.data.tagIdentityKey
import com.ella.music.ui.LibrarySortUiState
import com.ella.music.ui.components.DoubleTapScrollOverlay
import com.ella.music.ui.components.EllaSearchBar
import com.ella.music.ui.components.FastIndexBar
import com.ella.music.ui.components.LazyListScrollIndicator
import com.ella.music.ui.components.SafeCoverImage
import com.ella.music.ui.components.ellaPageBackground
import com.ella.music.ui.components.requestPinnedEllaShortcut
import com.ella.music.ui.navigation.Screen
import com.ella.music.viewmodel.MainViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import com.ella.music.ui.components.EllaSmallTopAppBar
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.basic.Search
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.icon.extended.Music
import top.yukonga.miuix.kmp.icon.extended.Sort
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
@OptIn(ExperimentalFoundationApi::class)
fun ArtistListScreen(
    mainViewModel: MainViewModel,
    onBack: () -> Unit,
    onArtistClick: (String) -> Unit
) {
    val context = LocalContext.current
    val songs by mainViewModel.songs.collectAsState()
    val albums by mainViewModel.albums.collectAsState()
    var searchQuery by remember { mutableStateOf("") }
    var searchExpanded by remember { mutableStateOf(false) }
    var sortExpanded by remember { mutableStateOf(false) }
    val sortIndex by mainViewModel.settingsManager.artistListSortIndex.collectAsState(initial = LibrarySortUiState.artistListSortIndex)
    val showAlbumArtists by mainViewModel.settingsManager.showAlbumArtists.collectAsState(initial = false)
    val tagIgnoreCase by mainViewModel.settingsManager.tagIgnoreCase.collectAsState(initial = false)
    val sortMode = ArtistSortMode.entries.getOrElse(sortIndex) { ArtistSortMode.Name }
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    var scrollToTopRequest by remember { mutableStateOf(0) }

    val artists = remember(songs, albums, showAlbumArtists, tagIgnoreCase) { mainViewModel.getArtists(showAlbumArtists) }
    val representativeSongsByArtist = remember(songs, showAlbumArtists, tagIgnoreCase) {
        buildMap {
            songs.forEach { song ->
                val names = if (showAlbumArtists) {
                    splitArtistNames(song.artist) + splitArtistNames(song.albumArtist)
                } else {
                    splitArtistNames(song.artist)
                }
                names.forEach { artistName ->
                    putIfAbsent(artistName.tagIdentityKey(), song)
                }
            }
        }
    }
    val artistDurations = remember(songs, tagIgnoreCase) {
        buildMap {
            songs.forEach { song ->
                splitArtistNames(song.artist).forEach { artistName ->
                    val key = artistName.tagIdentityKey()
                    put(key, (get(key) ?: 0L) + song.duration)
                }
            }
        }
    }
    val releaseAlbumCounts = remember(albums, showAlbumArtists, tagIgnoreCase) {
        buildMap {
            if (!showAlbumArtists) return@buildMap
            albums.forEach { album ->
                splitArtistNames(album.albumArtist.ifBlank { album.artist }).forEach { artistName ->
                    val key = artistName.tagIdentityKey()
                    put(key, (get(key) ?: 0) + 1)
                }
            }
        }
    }
    val filteredArtists = remember(artists, searchQuery, sortMode, artistDurations, releaseAlbumCounts) {
        val filtered = if (searchQuery.isBlank()) {
            artists
        } else {
            artists.filter { it.name.contains(searchQuery, ignoreCase = true) }
        }
        when (sortMode) {
            ArtistSortMode.Name -> filtered.sortedBy { it.name.lowercase() }
            ArtistSortMode.SongCount -> filtered.sortedByDescending { it.songCount }
            ArtistSortMode.AlbumCount -> filtered.sortedByDescending { it.albumCount }
            ArtistSortMode.ReleaseAlbumCount -> filtered.sortedByDescending { releaseAlbumCounts[it.name.tagIdentityKey()] ?: 0 }
            ArtistSortMode.Duration -> filtered.sortedByDescending { artistDurations[it.name.tagIdentityKey()] ?: 0L }
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
            DoubleTapScrollOverlay(
                onDoubleTap = { scrollToTopRequest++ },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
            )
        }

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
                                scope.launch { mainViewModel.settingsManager.setArtistListSortIndex(mode.ordinal) }
                                scrollToTopRequest++
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
            LaunchedEffect(scrollToTopRequest) {
                if (scrollToTopRequest > 0) listState.animateScrollToItem(0)
            }
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
                        val artistKey = artist.name.tagIdentityKey()
                        ArtistRow(
                            artist = artist,
                            representativeSong = representativeSongsByArtist[artistKey],
                            mainViewModel = mainViewModel,
                            summary = artist.summaryForSort(
                                sortMode = sortMode,
                                duration = artistDurations[artistKey] ?: 0L,
                                releaseAlbumCount = releaseAlbumCounts[artistKey] ?: 0
                            ),
                            onClick = { onArtistClick(artist.name) },
                            onLongClick = {
                                val ok = requestPinnedEllaShortcut(
                                    context = context,
                                    id = "artist_${artist.name}",
                                    label = artist.name,
                                    route = Screen.ArtistDetail.createRoute(artist.name)
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

                if (sortMode == ArtistSortMode.Name && filteredArtists.size > 30) {
                    FastIndexBar(
                        letters = filteredArtists.map { it.indexLetter() },
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
                } else if (filteredArtists.size > 30) {
                    LazyListScrollIndicator(
                        state = listState,
                        modifier = Modifier
                            .align(Alignment.CenterEnd)
                            .fillMaxHeight()
                    )
                }
            }
        }
    }
}

private fun Artist.indexLetter(): String {
    val first = name.trim().firstOrNull()?.uppercaseChar()
    return if (first != null && first in 'A'..'Z') first.toString() else "#"
}

@Composable
@OptIn(ExperimentalFoundationApi::class)
private fun ArtistRow(
    artist: Artist,
    representativeSong: Song?,
    mainViewModel: MainViewModel,
    summary: String,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    val embeddedCover by produceState<Bitmap?>(
        initialValue = null,
        representativeSong?.id,
        representativeSong?.dateModified,
        representativeSong?.fileSize
    ) {
        val song = representativeSong
        value = if (song != null && song.prefersEmbeddedArtistCover()) {
            withContext(Dispatchers.IO) {
                runCatching { mainViewModel.getAlbumCoverArtBitmap(song) }.getOrNull()
            }
        } else {
            null
        }
    }
    val coverModel: Any? = representativeSong?.coverUrl?.takeIf { it.isNotBlank() }
        ?: embeddedCover
        ?: representativeSong?.let { mainViewModel.getAlbumArtUri(it.albumId) }

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
            if (coverModel != null) {
                SafeCoverImage(
                    model = coverModel,
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
                text = summary,
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
    AlbumCount("参与专辑数"),
    ReleaseAlbumCount("发行专辑数"),
    Duration("歌曲时长")
}

private fun Artist.summaryForSort(
    sortMode: ArtistSortMode,
    duration: Long,
    releaseAlbumCount: Int
): String {
    return when (sortMode) {
        ArtistSortMode.Duration -> "${duration.formatArtistDuration()} · ${albumCount} 张参与专辑"
        ArtistSortMode.ReleaseAlbumCount -> "$songCount 首歌曲 · $releaseAlbumCount 张发行专辑"
        else -> "$songCount 首歌曲 · $albumCount 张参与专辑"
    }
}

private fun Song.prefersEmbeddedArtistCover(): Boolean {
    val extension = fileName.substringAfterLast('.', path.substringAfterLast('.')).lowercase()
    return extension in setOf("m4a", "mp4", "alac", "flac", "wav", "wave", "aiff", "aif")
}

private fun Long.formatArtistDuration(): String {
    if (this <= 0L) return "00:00"
    val totalMinutes = this / 60_000L
    val hours = totalMinutes / 60L
    val minutes = totalMinutes % 60L
    return if (hours > 0) "${hours}小时${minutes}分" else "${minutes}分钟"
}
