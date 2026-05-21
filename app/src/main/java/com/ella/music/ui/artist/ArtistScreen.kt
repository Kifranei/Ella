package com.ella.music.ui.artist

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ella.music.data.model.Album
import com.ella.music.data.model.Song
import com.ella.music.ui.LibrarySortUiState
import com.ella.music.ui.components.AppleStylePlayButton
import com.ella.music.ui.components.DoubleTapScrollOverlay
import com.ella.music.ui.components.LocateCurrentSongFloatingButton
import com.ella.music.ui.components.SafeCoverImage
import com.ella.music.ui.components.ellaPageBackground
import com.ella.music.ui.components.SongItem
import com.ella.music.viewmodel.MainViewModel
import com.ella.music.viewmodel.PlayerViewModel
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.basic.ArrowRight
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.icon.extended.MapAlbum
import top.yukonga.miuix.kmp.icon.extended.Sort
import top.yukonga.miuix.kmp.theme.MiuixTheme
import kotlinx.coroutines.launch
import java.util.Locale

@Composable
fun ArtistScreen(
    artistName: String,
    mainViewModel: MainViewModel,
    playerViewModel: PlayerViewModel,
    onBack: () -> Unit,
    onAlbumClick: (Long) -> Unit,
    onNavigateToPlayer: () -> Unit
) {
    val songs by mainViewModel.songs.collectAsState()
    val albums by mainViewModel.albums.collectAsState()
    val currentSong by playerViewModel.currentSong.collectAsState()
    val locateCurrentSongRequest by playerViewModel.locateCurrentSongRequest.collectAsState()
    val openPlayerOnPlay by mainViewModel.settingsManager.openPlayerOnPlay.collectAsState(initial = true)
    var sortExpanded by remember { mutableStateOf(false) }
    val sortIndex by mainViewModel.settingsManager.artistDetailSongSortIndex.collectAsState(initial = LibrarySortUiState.artistDetailSongSortIndex)
    val sortMode = ArtistDetailSongSortMode.entries.getOrElse(sortIndex) { ArtistDetailSongSortMode.Title }
    val scope = rememberCoroutineScope()
    var selectedTab by remember { mutableStateOf(0) }
    var scrollToTopRequest by remember { mutableStateOf(0) }

    val artistSongs = remember(songs, artistName) {
        mainViewModel.getSongsForArtist(artistName)
    }
    val sortedArtistSongs = remember(artistSongs, sortMode) { artistSongs.sortedForArtistDetail(sortMode) }
    val participatedAlbums = remember(albums, songs, artistName) {
        mainViewModel.getParticipatedAlbumsForArtist(artistName)
    }
    val releaseAlbums = remember(albums, songs, artistName) {
        mainViewModel.getReleaseAlbumsForArtist(artistName)
    }
    val showReleaseAlbums = remember(albums, songs, artistName) {
        mainViewModel.hasAlbumArtistTags() && releaseAlbums.isNotEmpty()
    }
    val tabs = remember(showReleaseAlbums) {
        buildList {
            add(ArtistTab.Songs)
            add(ArtistTab.ParticipatedAlbums)
            if (showReleaseAlbums) add(ArtistTab.ReleaseAlbums)
        }
    }
    val selectedArtistTab = tabs.getOrElse(selectedTab) { ArtistTab.Songs }
    val listState = rememberLazyListState()
    val currentSongItemIndex = remember(sortedArtistSongs, currentSong?.id, selectedArtistTab) {
        if (selectedArtistTab != ArtistTab.Songs) {
            -1
        } else {
            sortedArtistSongs.indexOfFirst { it.id == currentSong?.id }
                .takeIf { it >= 0 }
                ?.plus(3)
                ?: -1
        }
    }

    // 暂时用该歌手第一首歌的专辑封面作为歌手页顶部大图
    val artistCoverUri = artistSongs.firstOrNull()?.albumId
        ?.takeIf { it > 0L }
        ?.let { mainViewModel.getAlbumArtUri(it) }

    BackHandler(enabled = sortExpanded) {
        sortExpanded = false
    }

    LaunchedEffect(scrollToTopRequest) {
        if (scrollToTopRequest > 0) listState.animateScrollToItem(0)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(ellaPageBackground())
    ) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 120.dp)
        ) {
            item {
                ArtistHeader(
                    artistName = artistName,
                    coverUri = artistCoverUri,
                    songCount = sortedArtistSongs.size,
                    albumCount = (participatedAlbums + releaseAlbums).distinctBy { it.id }.size,
                    onPlayAll = {
                        if (sortedArtistSongs.isNotEmpty()) {
                            playerViewModel.setPlaylist(sortedArtistSongs, 0)
                            if (openPlayerOnPlay) onNavigateToPlayer()
                        }
                    }
                )
            }

            item {
                ArtistTabRow(
                    tabs = tabs,
                    selectedTab = selectedArtistTab,
                    onTabSelected = { tab -> selectedTab = tabs.indexOf(tab).coerceAtLeast(0) }
                )
            }

            when (selectedArtistTab) {
                ArtistTab.Songs -> {
                    item {
                        Text(
                            text = "${sortedArtistSongs.size} 首歌曲 · ${sortMode.label}",
                            fontSize = 13.sp,
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                        )
                    }

                    itemsIndexed(sortedArtistSongs) { index, song ->
                        SongItem(
                            song = song,
                            isCurrent = currentSong?.id == song.id,
                            albumArtUri = mainViewModel.getAlbumArtUri(song.albumId),
                            loadCoverArt = mainViewModel::getCoverArtBitmap,
                            loadAudioInfo = mainViewModel::getAudioInfo,
                            onClick = {
                                playerViewModel.setPlaylist(sortedArtistSongs, index)
                                if (openPlayerOnPlay) onNavigateToPlayer()
                            },
                            onAddToQueue = { playerViewModel.addToPlaylist(song) }
                        )
                    }
                }

                ArtistTab.ParticipatedAlbums -> {
                    item { SectionTitle("参与专辑") }
                    items(
                        items = participatedAlbums,
                        key = { it.id }
                    ) { album ->
                        ArtistAlbumRow(
                            album = album,
                            albumArtUri = mainViewModel.getAlbumArtUri(album.artAlbumId),
                            onClick = { onAlbumClick(album.id) }
                        )
                    }
                }

                ArtistTab.ReleaseAlbums -> {
                    item { SectionTitle("发行专辑") }
                    items(
                        items = releaseAlbums,
                        key = { it.id }
                    ) { album ->
                        ArtistAlbumRow(
                            album = album,
                            albumArtUri = mainViewModel.getAlbumArtUri(album.artAlbumId),
                            onClick = { onAlbumClick(album.id) }
                        )
                    }
                }
            }

            if (selectedArtistTab != ArtistTab.Songs && (selectedArtistTab == ArtistTab.ParticipatedAlbums && participatedAlbums.isEmpty() || selectedArtistTab == ArtistTab.ReleaseAlbums && releaseAlbums.isEmpty())) {
                item {
                    Text(
                        text = "暂无专辑",
                        fontSize = 14.sp,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 18.dp)
                    )
                }
            }

            item {
                Spacer(modifier = Modifier.height(24.dp))
            }
        }

        IconButton(
            onClick = onBack,
            modifier = Modifier
                .windowInsetsPadding(WindowInsets.statusBars)
                .padding(start = 8.dp, top = 8.dp)
                .size(48.dp)
                .align(Alignment.TopStart)
        ) {
            Icon(
                imageVector = MiuixIcons.Regular.Back,
                contentDescription = "返回",
                tint = Color.White,
                modifier = Modifier.size(26.dp)
            )
        }

        if (selectedArtistTab == ArtistTab.Songs) {
            IconButton(
                onClick = { sortExpanded = !sortExpanded },
                modifier = Modifier
                    .windowInsetsPadding(WindowInsets.statusBars)
                    .padding(end = 8.dp, top = 8.dp)
                    .size(48.dp)
                    .align(Alignment.TopEnd)
            ) {
                Icon(
                    imageVector = MiuixIcons.Regular.Sort,
                    contentDescription = "排序",
                    tint = Color.White,
                    modifier = Modifier.size(24.dp)
                )
            }
        }

        DoubleTapScrollOverlay(
            onDoubleTap = { scrollToTopRequest++ },
            modifier = Modifier
                .align(Alignment.TopCenter)
                .windowInsetsPadding(WindowInsets.statusBars)
                .fillMaxWidth()
                .height(56.dp),
            startPadding = 64.dp,
            endPadding = 64.dp
        )

        AnimatedVisibility(
            visible = sortExpanded && selectedArtistTab == ArtistTab.Songs,
            enter = expandVertically(),
            exit = shrinkVertically(),
            modifier = Modifier
                .align(Alignment.TopEnd)
                .windowInsetsPadding(WindowInsets.statusBars)
                .padding(top = 60.dp, end = 16.dp)
        ) {
            Column(
                modifier = Modifier
                    .clip(RoundedCornerShape(18.dp))
                    .background(MiuixTheme.colorScheme.surfaceContainer.copy(alpha = 0.94f))
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                ArtistDetailSongSortMode.entries.forEach { mode ->
                    Text(
                        text = mode.label,
                        fontSize = 14.sp,
                        fontWeight = if (sortMode == mode) FontWeight.Bold else FontWeight.Normal,
                        color = if (sortMode == mode) MiuixTheme.colorScheme.primary else MiuixTheme.colorScheme.onSurface,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                LibrarySortUiState.artistDetailSongSortIndex = mode.ordinal
                                scope.launch { mainViewModel.settingsManager.setArtistDetailSongSortIndex(mode.ordinal) }
                                scrollToTopRequest++
                                sortExpanded = false
                            }
                            .padding(vertical = 10.dp)
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
    }
}

private enum class ArtistTab(val label: String) {
    Songs("歌曲"),
    ParticipatedAlbums("参与专辑"),
    ReleaseAlbums("发行专辑")
}

@Composable
private fun ArtistTabRow(
    tabs: List<ArtistTab>,
    selectedTab: ArtistTab,
    onTabSelected: (ArtistTab) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        tabs.forEach { tab ->
            val selected = tab == selectedTab
            Text(
                text = tab.label,
                fontSize = 14.sp,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                color = if (selected) MiuixTheme.colorScheme.onPrimary else MiuixTheme.colorScheme.onSurface,
                modifier = Modifier
                    .clip(RoundedCornerShape(999.dp))
                    .background(
                        if (selected) MiuixTheme.colorScheme.primary
                        else MiuixTheme.colorScheme.surfaceContainer.copy(alpha = 0.72f)
                    )
                    .clickable { onTabSelected(tab) }
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            )
        }
    }
}

@Composable
private fun ArtistHeader(
    artistName: String,
    coverUri: Uri?,
    songCount: Int,
    albumCount: Int,
    onPlayAll: () -> Unit
) {
    val headerTextColor = Color.White
    val headerSubTextColor = Color.White.copy(alpha = 0.78f)
    val pageBackground = ellaPageBackground()
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(468.dp)
    ) {
        if (coverUri != null) {
            SafeCoverImage(
                model = coverUri,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
                sizePx = 3000
            )
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MiuixTheme.colorScheme.surfaceContainer)
            )
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colorStops = arrayOf(
                            0.00f to Color.Black.copy(alpha = 0.05f),
                            0.42f to Color.Black.copy(alpha = 0.16f),
                            0.74f to pageBackground.copy(alpha = 0.78f),
                            1.00f to pageBackground
                        )
                    )
                )
        )

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 46.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = artistName.ifBlank { "未知歌手" },
                fontSize = 34.sp,
                fontWeight = FontWeight.Bold,
                color = headerTextColor
            )

            Text(
                text = "$albumCount 张专辑 · $songCount 首歌曲",
                fontSize = 14.sp,
                color = headerSubTextColor
            )

            AppleStylePlayButton(
                text = "播放全部",
                onClick = onPlayAll,
                modifier = Modifier
                    .padding(top = 12.dp)
            )
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        fontSize = 18.sp,
        fontWeight = FontWeight.Bold,
        color = MiuixTheme.colorScheme.onBackground.copy(alpha = 0.88f),
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
    )
}

private enum class ArtistDetailSongSortMode(val label: String) {
    Title("歌曲名称"),
    AlbumTrack("专辑曲序"),
    FileName("文件名"),
    Duration("歌曲时长"),
    DateAdded("添加时间"),
    DateAddedAsc("添加时间升序"),
    DateModified("修改时间"),
    DateModifiedAsc("修改时间升序")
}

private fun List<Song>.sortedForArtistDetail(mode: ArtistDetailSongSortMode): List<Song> {
    return when (mode) {
        ArtistDetailSongSortMode.Title -> sortedBy { it.title.lowercase(Locale.ROOT) }
        ArtistDetailSongSortMode.AlbumTrack -> sortedWith(
            compareBy<Song> { it.album.lowercase(Locale.ROOT) }
                .thenBy { if (it.discNumber > 0) it.discNumber else Int.MAX_VALUE }
                .thenBy { if (it.trackNumber > 0) it.trackNumber else Int.MAX_VALUE }
                .thenBy { it.title.lowercase(Locale.ROOT) }
        )
        ArtistDetailSongSortMode.FileName -> sortedBy { it.fileName.ifBlank { it.path.substringAfterLast('/') }.lowercase(Locale.ROOT) }
        ArtistDetailSongSortMode.Duration -> sortedByDescending { it.duration }
        ArtistDetailSongSortMode.DateAdded -> sortedByDescending { it.dateAdded }
        ArtistDetailSongSortMode.DateAddedAsc -> sortedBy { it.dateAdded }
        ArtistDetailSongSortMode.DateModified -> sortedByDescending { it.dateModified }
        ArtistDetailSongSortMode.DateModifiedAsc -> sortedBy { it.dateModified }
    }
}

@Composable
private fun ArtistAlbumRow(
    album: Album,
    albumArtUri: Uri?,
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
                Icon(
                    imageVector = MiuixIcons.Regular.MapAlbum,
                    contentDescription = null,
                    tint = MiuixTheme.colorScheme.primary,
                    modifier = Modifier.size(28.dp)
                )
            }
        }

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = album.name,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                color = MiuixTheme.colorScheme.onSurface
            )
            Text(
                text = "${album.songCount} 首歌曲",
                fontSize = 12.sp,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary
            )
        }

        Icon(
            imageVector = MiuixIcons.Basic.ArrowRight,
            contentDescription = null,
            tint = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            modifier = Modifier.size(18.dp)
        )
    }
}
