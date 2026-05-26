package com.ella.music.ui.album

import android.content.Context
import android.content.Intent
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ella.music.data.model.Album
import com.ella.music.data.model.Song
import com.ella.music.data.splitArtistNames
import com.ella.music.ui.LibrarySortUiState
import com.ella.music.ui.components.AppleStylePlayButton
import com.ella.music.ui.components.ArtistPickerSheet
import com.ella.music.ui.components.DefaultAlbumCover
import com.ella.music.ui.components.DoubleTapScrollOverlay
import com.ella.music.ui.components.LocateCurrentSongFloatingButton
import com.ella.music.ui.components.SafeCoverImage
import com.ella.music.ui.components.SongItem
import com.ella.music.ui.components.SongMoreActionHost
import com.ella.music.ui.components.ellaPageBackground
import com.ella.music.viewmodel.MainViewModel
import com.ella.music.viewmodel.PlayerViewModel
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.icon.extended.Sort
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.window.WindowBottomSheet
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

@Composable
fun AlbumDetailScreen(
    albumId: Long,
    mainViewModel: MainViewModel,
    playerViewModel: PlayerViewModel,
    onBack: () -> Unit,
    onNavigateToAlbum: (Long) -> Unit = {},
    onNavigateToArtist: (String) -> Unit = {},
    onNavigateToPlayer: () -> Unit
) {
    val albums by mainViewModel.albums.collectAsState()
    val context = LocalContext.current
    val currentSong by playerViewModel.currentSong.collectAsState()
    val locateCurrentSongRequest by playerViewModel.locateCurrentSongRequest.collectAsState()
    val openPlayerOnPlay by mainViewModel.settingsManager.openPlayerOnPlay.collectAsState(initial = true)
    val sortIndex by mainViewModel.settingsManager.albumDetailSongSortIndex.collectAsState(initial = LibrarySortUiState.albumDetailSongSortIndex)
    val sortMode = AlbumDetailSongSortMode.entries.getOrElse(sortIndex) { AlbumDetailSongSortMode.Track }
    val scope = rememberCoroutineScope()
    var sortExpanded by remember { mutableStateOf(false) }
    var actionSong by remember { mutableStateOf<Song?>(null) }
    var albumArtistChoices by remember { mutableStateOf<List<String>>(emptyList()) }
    val album = albums.find { it.id == albumId }
    val albumSongs = mainViewModel.getSongsForAlbum(albumId)
    val sortedAlbumSongs = remember(albumSongs, sortMode) { albumSongs.sortedForAlbumDetail(sortMode) }
    val albumDuration = remember(albumSongs) { albumSongs.sumOf { it.duration } }
    val useDiscSections = sortMode == AlbumDetailSongSortMode.Track && sortedAlbumSongs.any { it.discNumber > 0 }
    val discGroups = remember(sortedAlbumSongs, sortMode) {
        if (sortMode == AlbumDetailSongSortMode.Track) {
            sortedAlbumSongs.groupForDiscSections()
        } else {
            emptyList()
        }
    }
    val albumArtUri = mainViewModel.getAlbumArtUri(album?.artAlbumId ?: albumSongs.firstOrNull()?.albumId ?: 0L)
    val albumCoverModel by produceState<Any?>(initialValue = albumArtUri, albumArtUri, albumSongs) {
        val representative = albumSongs.firstOrNull()
        value = if (representative != null && representative.prefersEmbeddedCoverForHeader()) {
            withContext(Dispatchers.IO) {
                runCatching { mainViewModel.getLargeCoverArtBitmap(representative) }.getOrNull()
            } ?: albumArtUri
        } else {
            albumArtUri
        }
    }
    val neteaseAlbumUrl by produceState<String?>(initialValue = null, albumId, albumSongs) {
        value = mainViewModel.getNeteaseAlbumUrlForAlbum(albumId)
    }
    val albumCopyright by produceState<String>(initialValue = "", albumId, albumSongs) {
        value = withContext(Dispatchers.IO) {
            albumSongs
                .asSequence()
                .map { mainViewModel.getSongTagInfo(it).copyright }
                .map { it.trim() }
                .filter { it.isNotBlank() }
                .distinctBy { it.lowercase(Locale.ROOT) }
                .take(3)
                .joinToString("\n")
        }
    }
    val listState = rememberLazyListState()
    var scrollToTopRequest by remember { mutableStateOf(0) }
    val currentSongItemIndex = remember(sortedAlbumSongs, discGroups, useDiscSections, currentSong?.id) {
        val songIndex = sortedAlbumSongs.indexOfFirst { it.id == currentSong?.id }
        if (songIndex < 0) {
            -1
        } else {
            val discHeaderCount = if (useDiscSections) {
                val song = sortedAlbumSongs[songIndex]
                discGroups.count { group ->
                    group.discNumber <= song.safeDiscNumber()
                }
            } else {
                0
            }
            2 + discHeaderCount + songIndex
        }
    }

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
                AlbumHeader(
                    album = album,
                    albumCoverModel = albumCoverModel,
                    songCount = sortedAlbumSongs.size,
                    duration = albumDuration,
                    hasNeteaseAlbum = !neteaseAlbumUrl.isNullOrBlank(),
                    onNeteaseAlbumClick = { openUrl(context, neteaseAlbumUrl.orEmpty()) },
                    onAlbumArtistClick = {
                        val albumArtist = (album?.albumArtist?.takeIf { it.isNotBlank() } ?: album?.artist)
                            ?.takeIf { it.isNotBlank() && !it.equals("Unknown", ignoreCase = true) }
                            ?: return@AlbumHeader
                        val artists = splitArtistNames(albumArtist).ifEmpty { listOf(albumArtist.trim()) }
                        if (artists.size == 1) {
                            onNavigateToArtist(artists.first())
                        } else {
                            albumArtistChoices = artists
                        }
                    },
                    onPlayAll = {
                        if (sortedAlbumSongs.isNotEmpty()) {
                            playerViewModel.setPlaylist(sortedAlbumSongs, 0)
                            if (openPlayerOnPlay) onNavigateToPlayer()
                        }
                    }
                )
            }

            item {
                Text(
                    text = "${sortedAlbumSongs.size} 首歌曲 · ${sortMode.label}",
                    fontSize = 13.sp,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }

            if (useDiscSections) {
                discGroups.forEach { group ->
                    item(key = "disc-${group.discNumber}") {
                        DiscHeader(group.discNumber)
                    }
                    items(
                        items = group.songs,
                        key = { song -> song.id }
                    ) { song ->
                        val index = sortedAlbumSongs.indexOfFirst { it.id == song.id }
                        AlbumSongRow(
                            song = song,
                            index = index,
                            sortedAlbumSongs = sortedAlbumSongs,
                            albumArtUri = albumArtUri,
                            currentSongId = currentSong?.id,
                            showTrackNumber = true,
                            mainViewModel = mainViewModel,
                            playerViewModel = playerViewModel,
                            openPlayerOnPlay = openPlayerOnPlay,
                            onNavigateToPlayer = onNavigateToPlayer,
                            onMore = { actionSong = song }
                        )
                    }
                }
            } else {
                itemsIndexed(sortedAlbumSongs) { index, song ->
                    AlbumSongRow(
                        song = song,
                        index = index,
                        sortedAlbumSongs = sortedAlbumSongs,
                        albumArtUri = albumArtUri,
                        currentSongId = currentSong?.id,
                        showTrackNumber = sortMode == AlbumDetailSongSortMode.Track,
                        mainViewModel = mainViewModel,
                        playerViewModel = playerViewModel,
                        openPlayerOnPlay = openPlayerOnPlay,
                        onNavigateToPlayer = onNavigateToPlayer,
                        onMore = { actionSong = song }
                    )
                }
            }
            if (albumCopyright.isNotBlank()) {
                item(key = "album-copyright") {
                    AlbumCopyrightFooter(albumCopyright)
                }
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
            visible = sortExpanded,
            enter = expandVertically(),
            exit = shrinkVertically(),
            modifier = Modifier
                .align(Alignment.TopEnd)
                .windowInsetsPadding(WindowInsets.statusBars)
                .padding(top = 60.dp, end = 16.dp)
        ) {
            Column(
                modifier = Modifier
                    .background(MiuixTheme.colorScheme.surfaceContainer.copy(alpha = 0.94f), androidx.compose.foundation.shape.RoundedCornerShape(18.dp))
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                AlbumDetailSongSortMode.entries.forEach { mode ->
                    Text(
                        text = mode.label,
                        fontSize = 14.sp,
                        fontWeight = if (sortMode == mode) FontWeight.Bold else FontWeight.Normal,
                        color = if (sortMode == mode) MiuixTheme.colorScheme.primary else MiuixTheme.colorScheme.onSurface,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                LibrarySortUiState.albumDetailSongSortIndex = mode.ordinal
                                scope.launch { mainViewModel.settingsManager.setAlbumDetailSongSortIndex(mode.ordinal) }
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

        SongMoreActionHost(
            actionSong = actionSong,
            mainViewModel = mainViewModel,
            playerViewModel = playerViewModel,
            onDismissAction = { actionSong = null },
            onNavigateToAlbum = onNavigateToAlbum,
            onNavigateToArtist = onNavigateToArtist
        )

        if (albumArtistChoices.isNotEmpty()) {
            WindowBottomSheet(
                show = true,
                enableNestedScroll = false,
                title = "选择歌手",
                onDismissRequest = { albumArtistChoices = emptyList() }
            ) {
                ArtistPickerSheet(
                    artists = albumArtistChoices,
                    onArtistSelected = { artist ->
                        albumArtistChoices = emptyList()
                        onNavigateToArtist(artist)
                    },
                    onDismiss = { albumArtistChoices = emptyList() }
                )
            }
        }
    }
}

@Composable
private fun AlbumCopyrightFooter(copyright: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 22.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            text = "版权",
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary
        )
        Text(
            text = copyright,
            fontSize = 12.sp,
            lineHeight = 18.sp,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary
        )
    }
}

@Composable
private fun DiscHeader(discNumber: Int) {
    Text(
        text = "Disc $discNumber",
        fontSize = 15.sp,
        fontWeight = FontWeight.Bold,
        color = MiuixTheme.colorScheme.onSurface,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 18.dp, bottom = 6.dp)
    )
}

@Composable
private fun AlbumSongRow(
    song: Song,
    index: Int,
    sortedAlbumSongs: List<Song>,
    albumArtUri: Uri?,
    currentSongId: Long?,
    showTrackNumber: Boolean,
    mainViewModel: MainViewModel,
    playerViewModel: PlayerViewModel,
    openPlayerOnPlay: Boolean,
    onNavigateToPlayer: () -> Unit,
    onMore: () -> Unit
) {
    SongItem(
        song = song,
        isCurrent = currentSongId == song.id,
        albumArtUri = albumArtUri,
        loadCoverArt = mainViewModel::getAlbumCoverArtBitmap,
        loadAudioInfo = mainViewModel::getAudioInfo,
        leadingLabel = if (showTrackNumber) song.displayTrackNumber() else null,
        leadingLabelBeforeCover = showTrackNumber,
        showAlbumInSubtitle = false,
        onClick = {
            val safeIndex = index.coerceAtLeast(0)
            playerViewModel.setPlaylist(sortedAlbumSongs, safeIndex)
            if (openPlayerOnPlay) onNavigateToPlayer()
        },
        onAddToQueue = { playerViewModel.addToPlaylist(song) },
        onMore = onMore
    )
}

@Composable
private fun AlbumHeader(
    album: Album?,
    albumCoverModel: Any?,
    songCount: Int,
    duration: Long,
    hasNeteaseAlbum: Boolean,
    onNeteaseAlbumClick: () -> Unit,
    onAlbumArtistClick: () -> Unit,
    onPlayAll: () -> Unit
) {
    val pageBackground = ellaPageBackground()
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(448.dp)
    ) {
        if (albumCoverModel != null) {
            SafeCoverImage(
                model = albumCoverModel,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
                sizePx = 3000
            )
        } else {
            DefaultAlbumCover(modifier = Modifier.fillMaxSize())
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
                .padding(horizontal = 24.dp, vertical = 38.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = album?.name ?: "未知专辑",
                fontSize = 32.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            val albumArtist = (album?.albumArtist?.takeIf { it.isNotBlank() } ?: album?.artist)
                ?.takeIf { it.isNotBlank() && !it.equals("Unknown", ignoreCase = true) }
            Text(
                text = listOfNotNull(
                    albumArtist,
                    "$songCount 首歌曲",
                    album?.year?.takeIf { it > 0 }?.toString(),
                    duration.formatAlbumDetailDuration()
                ).joinToString(" · "),
                fontSize = 14.sp,
                color = Color.White.copy(alpha = 0.78f),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.clickable(enabled = albumArtist != null, onClick = onAlbumArtistClick)
            )

            Spacer(modifier = Modifier.height(12.dp))

            if (hasNeteaseAlbum) {
                Text(
                    text = "网易云专辑页",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    modifier = Modifier
                        .background(Color.White.copy(alpha = 0.18f), androidx.compose.foundation.shape.RoundedCornerShape(999.dp))
                        .clickable(onClick = onNeteaseAlbumClick)
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                )
            }

            AppleStylePlayButton(
                text = "播放全部",
                onClick = onPlayAll,
                modifier = Modifier
                    .padding(top = 12.dp)
            )
        }
    }
}

private fun openUrl(context: Context, url: String) {
    if (url.isBlank()) return
    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
}

private fun Long.formatAlbumDetailDuration(): String {
    if (this <= 0L) return "00:00"
    val totalMinutes = this / 60_000L
    val hours = totalMinutes / 60L
    val minutes = totalMinutes % 60L
    return if (hours > 0) "${hours}小时${minutes}分" else "${minutes}分钟"
}

private enum class AlbumDetailSongSortMode(val label: String) {
    Track("曲目顺序"),
    Title("歌曲名称"),
    FileName("文件名"),
    Duration("歌曲时长"),
    DateAdded("添加时间"),
    DateAddedAsc("添加时间升序"),
    DateModified("修改时间"),
    DateModifiedAsc("修改时间升序")
}

private data class AlbumDiscGroup(
    val discNumber: Int,
    val songs: List<Song>
)

private fun List<Song>.groupForDiscSections(): List<AlbumDiscGroup> =
    groupBy { it.safeDiscNumber() }
        .toSortedMap()
        .map { (discNumber, songs) -> AlbumDiscGroup(discNumber, songs) }

private fun Song.safeDiscNumber(): Int =
    if (discNumber > 0) discNumber else 1

private fun Song.displayTrackNumber(): String =
    trackNumber.takeIf { it > 0 }?.toString().orEmpty()

private fun Song.prefersEmbeddedCoverForHeader(): Boolean {
    val extension = fileName.substringAfterLast('.', path.substringAfterLast('.')).lowercase()
    return extension in setOf("m4a", "mp4", "alac", "flac", "wav", "wave", "aiff", "aif")
}

private fun List<Song>.sortedForAlbumDetail(mode: AlbumDetailSongSortMode): List<Song> {
    return when (mode) {
        AlbumDetailSongSortMode.Track -> sortedWith(
            compareBy<Song> { it.discNumber <= 0 && it.trackNumber <= 0 }
                .thenBy { if (it.discNumber > 0) it.discNumber else Int.MAX_VALUE }
                .thenBy { if (it.trackNumber > 0) it.trackNumber else Int.MAX_VALUE }
                .thenBy { it.title.lowercase(Locale.ROOT) }
                .thenBy { it.id }
        )
        AlbumDetailSongSortMode.Title -> sortedBy { it.title.lowercase(Locale.ROOT) }
        AlbumDetailSongSortMode.FileName -> sortedBy { it.fileName.ifBlank { it.path.substringAfterLast('/') }.lowercase(Locale.ROOT) }
        AlbumDetailSongSortMode.Duration -> sortedByDescending { it.duration }
        AlbumDetailSongSortMode.DateAdded -> sortedByDescending { it.dateAdded }
        AlbumDetailSongSortMode.DateAddedAsc -> sortedBy { it.dateAdded }
        AlbumDetailSongSortMode.DateModified -> sortedByDescending { it.dateModified }
        AlbumDetailSongSortMode.DateModifiedAsc -> sortedBy { it.dateModified }
    }
}
