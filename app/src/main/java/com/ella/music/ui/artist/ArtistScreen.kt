package com.ella.music.ui.artist

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
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
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import com.ella.music.data.detailedAudioInfo
import com.ella.music.data.model.AudioInfo
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ella.music.data.model.FAVORITES_PLAYLIST_ID
import com.ella.music.data.model.Album
import com.ella.music.data.model.Song
import com.ella.music.data.model.SongTagInfo
import com.ella.music.data.model.UserPlaylist
import com.ella.music.data.model.albumIdentityId
import com.ella.music.data.model.formatPlaybackDuration
import com.ella.music.data.model.playlistIdentityKey
import com.ella.music.ui.LibrarySortUiState
import com.ella.music.ui.components.AppleStylePlayButton
import com.ella.music.ui.components.DefaultAlbumCover
import com.ella.music.ui.components.DoubleTapScrollOverlay
import com.ella.music.ui.components.LocateCurrentSongFloatingButton
import com.ella.music.ui.components.SafeCoverImage
import com.ella.music.ui.components.ellaPageBackground
import com.ella.music.ui.components.buildTagEditorOptions
import com.ella.music.ui.components.TagEditorOptionKind
import com.ella.music.ui.components.launchTagEditorOption
import com.ella.music.ui.components.openSongSpectrumWithAspectPro
import com.ella.music.ui.components.shareLocalSong
import com.ella.music.ui.components.SongItem
import com.ella.music.ui.components.SongMoreActionHost
import com.ella.music.viewmodel.MainViewModel
import com.ella.music.viewmodel.PlayerViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.basic.ArrowRight
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.icon.extended.MapAlbum
import top.yukonga.miuix.kmp.icon.extended.Sort
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.window.WindowBottomSheet
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

@Composable
fun ArtistScreen(
    artistName: String,
    mainViewModel: MainViewModel,
    playerViewModel: PlayerViewModel,
    onBack: () -> Unit,
    onAlbumClick: (Long) -> Unit,
    onArtistClick: (String) -> Unit = {},
    onMetadataCategoryClick: (String, String) -> Unit = { _, _ -> },
    onNavigateToPlayer: () -> Unit
) {
    val context = LocalContext.current
    val songs by mainViewModel.songs.collectAsState()
    val albums by mainViewModel.albums.collectAsState()
    val playlists by mainViewModel.playlists.collectAsState()
    val currentSong by playerViewModel.currentSong.collectAsState()
    val favoriteSongKeys by playerViewModel.favoriteSongKeys.collectAsState()
    val locateCurrentSongRequest by playerViewModel.locateCurrentSongRequest.collectAsState()
    val openPlayerOnPlay by mainViewModel.settingsManager.openPlayerOnPlay.collectAsState(initial = true)
    val showAlbumArtists by mainViewModel.settingsManager.showAlbumArtists.collectAsState(initial = false)
    var sortExpanded by remember { mutableStateOf(false) }
    val sortIndex by mainViewModel.settingsManager.artistDetailSongSortIndex.collectAsState(initial = LibrarySortUiState.artistDetailSongSortIndex)
    val sortMode = ArtistDetailSongSortMode.entries.getOrElse(sortIndex) { ArtistDetailSongSortMode.Title }
    var albumSortMode by remember { mutableStateOf(ArtistDetailAlbumSortMode.YearAsc) }
    val scope = rememberCoroutineScope()
    var selectedTabTarget by rememberSaveable(artistName) { mutableStateOf(ArtistTab.Songs) }
    var scrollToTopRequest by remember { mutableStateOf(0) }
    var actionSong by remember { mutableStateOf<Song?>(null) }
    var playlistPickerSong by remember { mutableStateOf<Song?>(null) }
    var createPlaylistSong by remember { mutableStateOf<Song?>(null) }
    var tagEditorSong by remember { mutableStateOf<Song?>(null) }
    var songInfoSheetSong by remember { mutableStateOf<Song?>(null) }
    var aiInterpretationSong by remember { mutableStateOf<Song?>(null) }

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
    val showReleaseAlbums = remember(albums, songs, artistName, showAlbumArtists) {
        showAlbumArtists && mainViewModel.hasAlbumArtistTags() && releaseAlbums.isNotEmpty()
    }
    val albumDurations = remember(songs) {
        songs.groupBy { it.albumIdentityId() }.mapValues { (_, albumSongs) -> albumSongs.sumOf { it.duration } }
    }
    val sortedParticipatedAlbums = remember(participatedAlbums, albumSortMode, albumDurations) {
        participatedAlbums.sortedForArtistAlbumDetail(albumSortMode, albumDurations)
    }
    val sortedReleaseAlbums = remember(releaseAlbums, albumSortMode, albumDurations) {
        releaseAlbums.sortedForArtistAlbumDetail(albumSortMode, albumDurations)
    }
    val hasComposerCategory = remember(songs, artistName) {
        mainViewModel.hasMetadataCategory("composer", artistName)
    }
    val hasLyricistCategory = remember(songs, artistName) {
        mainViewModel.hasMetadataCategory("lyricist", artistName)
    }
    val neteaseArtistUrl by produceState<String?>(initialValue = null, artistName, songs) {
        value = mainViewModel.getNeteaseArtistUrlForArtist(artistName)
    }
    val tabs = remember(showReleaseAlbums) {
        buildList {
            add(ArtistTab.Songs)
            add(ArtistTab.ParticipatedAlbums)
            if (showReleaseAlbums) add(ArtistTab.ReleaseAlbums)
        }
    }
    val selectedArtistTab = selectedTabTarget.takeIf { it in tabs } ?: ArtistTab.Songs
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

            if (hasComposerCategory || hasLyricistCategory || !neteaseArtistUrl.isNullOrBlank()) {
                item {
                    ArtistJumpActions(
                        hasComposerCategory = hasComposerCategory,
                        hasLyricistCategory = hasLyricistCategory,
                        hasNeteaseArtist = !neteaseArtistUrl.isNullOrBlank(),
                        onComposerClick = { onMetadataCategoryClick("composer", artistName) },
                        onLyricistClick = { onMetadataCategoryClick("lyricist", artistName) },
                        onNeteaseClick = { openUrl(context, neteaseArtistUrl.orEmpty()) }
                    )
                }
            }

            item {
                ArtistTabRow(
                    tabs = tabs,
                    selectedTab = selectedArtistTab,
                    onTabSelected = { tab -> selectedTabTarget = tab }
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
                            isFavorite = song.playlistIdentityKey() in favoriteSongKeys,
                            loadSongRating = mainViewModel::getSongRating,
                            onClick = {
                                playerViewModel.setPlaylist(sortedArtistSongs, index)
                                if (openPlayerOnPlay) onNavigateToPlayer()
                            },
                            onAddToQueue = { playerViewModel.addToPlaylist(song) },
                            onMore = { actionSong = song }
                        )
                    }
                }

                ArtistTab.ParticipatedAlbums -> {
                    item {
                        Text(
                            text = "${sortedParticipatedAlbums.size} 个参与专辑 · ${albumSortMode.label}",
                            fontSize = 13.sp,
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                        )
                    }
                    items(
                        items = sortedParticipatedAlbums,
                        key = { it.id }
                    ) { album ->
                        ArtistAlbumRow(
                            album = album,
                            duration = albumDurations[album.id] ?: 0L,
                            albumArtUri = mainViewModel.getAlbumArtUri(album.artAlbumId),
                            onClick = { onAlbumClick(album.id) }
                        )
                    }
                }

                ArtistTab.ReleaseAlbums -> {
                    item {
                        Text(
                            text = "${sortedReleaseAlbums.size} 个发行专辑 · ${albumSortMode.label}",
                            fontSize = 13.sp,
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                        )
                    }
                    items(
                        items = sortedReleaseAlbums,
                        key = { it.id }
                    ) { album ->
                        ArtistAlbumRow(
                            album = album,
                            duration = albumDurations[album.id] ?: 0L,
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
                    .clip(RoundedCornerShape(18.dp))
                    .background(MiuixTheme.colorScheme.surfaceContainer.copy(alpha = 0.94f))
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                if (selectedArtistTab == ArtistTab.Songs) {
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
                } else {
                    ArtistDetailAlbumSortMode.entries.forEach { mode ->
                        Text(
                            text = mode.label,
                            fontSize = 14.sp,
                            fontWeight = if (albumSortMode == mode) FontWeight.Bold else FontWeight.Normal,
                            color = if (albumSortMode == mode) MiuixTheme.colorScheme.primary else MiuixTheme.colorScheme.onSurface,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    albumSortMode = mode
                                    scrollToTopRequest++
                                    sortExpanded = false
                                }
                                .padding(vertical = 10.dp)
                        )
                    }
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
            onNavigateToAlbum = onAlbumClick,
            onNavigateToArtist = onArtistClick
        )

        playlistPickerSong?.let { song ->
            WindowBottomSheet(
                show = true,
                enableNestedScroll = false,
                title = "添加到歌单",
                onDismissRequest = { playlistPickerSong = null }
            ) {
                ArtistAddToPlaylistMenu(
                    playlists = playlists
                        .filterNot { it.id == FAVORITES_PLAYLIST_ID }
                        .sortedByDescending { it.createdAt },
                    onDismiss = { playlistPickerSong = null },
                    onCreatePlaylist = {
                        createPlaylistSong = song
                        playlistPickerSong = null
                    },
                    onPlaylistsConfirm = { selectedPlaylists ->
                        selectedPlaylists.forEach { playlist ->
                            mainViewModel.addSongsToPlaylist(playlist.id, listOf(song))
                        }
                        Toast.makeText(context, "已添加到 ${selectedPlaylists.size} 个歌单", Toast.LENGTH_SHORT).show()
                        playlistPickerSong = null
                    }
                )
            }
        }

        createPlaylistSong?.let { song ->
            ArtistCreatePlaylistSheet(
                onDismiss = { createPlaylistSong = null },
                onCreate = { name ->
                    mainViewModel.createPlaylist(name) { playlist ->
                        if (playlist != null) {
                            mainViewModel.addSongsToPlaylist(playlist.id, listOf(song))
                        }
                    }
                    createPlaylistSong = null
                }
            )
        }

        tagEditorSong?.let { song ->
            WindowBottomSheet(
                show = true,
                enableNestedScroll = false,
                title = "编辑歌曲标签信息",
                onDismissRequest = { tagEditorSong = null }
            ) {
                ArtistTagEditorMenu(
                    song = song,
                    onDismiss = { tagEditorSong = null },
                    onOptionClick = { option ->
                        launchTagEditorOption(context, option)
                        tagEditorSong = null
                    }
                )
            }
        }

        songInfoSheetSong?.let { song ->
            WindowBottomSheet(
                show = true,
                enableNestedScroll = false,
                title = "歌曲信息",
                onDismissRequest = { songInfoSheetSong = null }
            ) {
                ArtistSongInfoMenu(
                    song = song,
                    mainViewModel = mainViewModel,
                    onAiInterpret = {
                        songInfoSheetSong = null
                        aiInterpretationSong = song
                    },
                    onDismiss = { songInfoSheetSong = null }
                )
            }
        }

        aiInterpretationSong?.let { song ->
            WindowBottomSheet(
                show = true,
                enableNestedScroll = false,
                title = "AI 解读歌曲",
                onDismissRequest = { aiInterpretationSong = null }
            ) {
                ArtistAiInterpretationMenu(
                    song = song,
                    mainViewModel = mainViewModel,
                    onDismiss = { aiInterpretationSong = null }
                )
            }
        }
    }
}

@Composable
private fun ArtistJumpActions(
    hasComposerCategory: Boolean,
    hasLyricistCategory: Boolean,
    hasNeteaseArtist: Boolean,
    onComposerClick: () -> Unit,
    onLyricistClick: () -> Unit,
    onNeteaseClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (hasComposerCategory) {
            ArtistJumpChip("作曲家页", onComposerClick)
        }
        if (hasLyricistCategory) {
            ArtistJumpChip("作词家页", onLyricistClick)
        }
        if (hasNeteaseArtist) {
            ArtistJumpChip("网易云歌手页", onNeteaseClick)
        }
    }
}

@Composable
private fun ArtistJumpChip(text: String, onClick: () -> Unit) {
    Text(
        text = text,
        fontSize = 13.sp,
        fontWeight = FontWeight.Bold,
        color = MiuixTheme.colorScheme.primary,
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(MiuixTheme.colorScheme.primary.copy(alpha = 0.12f))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp)
    )
}

private fun openUrl(context: Context, url: String) {
    if (url.isBlank()) return
    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
}

private enum class ArtistTab(val label: String) {
    Songs("歌曲"),
    ParticipatedAlbums("参与专辑"),
    ReleaseAlbums("发行专辑")
}

@Composable
private fun ArtistSongActionMenu(
    song: Song,
    onDismiss: () -> Unit,
    onAddToPlaylist: () -> Unit,
    onPlayNext: () -> Unit,
    onShare: () -> Unit,
    onSpectrum: () -> Unit,
    onInfo: () -> Unit,
    onAiInterpret: () -> Unit,
    onArtist: () -> Unit,
    onAlbum: () -> Unit,
    onEditTag: () -> Unit,
    onDelete: () -> Unit
) {
    ArtistSheetColumn {
        ArtistSheetHandle()
        Text(
            text = song.title,
            fontSize = 18.sp,
            fontWeight = FontWeight.ExtraBold,
            color = MiuixTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp)
        )
        ArtistMenuItem("添加到歌单", onAddToPlaylist)
        ArtistMenuItem("下一首播放", onPlayNext)
        ArtistMenuItem("分享", onShare)
        ArtistMenuItem("查看频谱", onSpectrum)
        ArtistMenuItem("AI 解读歌曲", onAiInterpret)
        ArtistMenuItem("查看歌曲信息", onInfo)
        ArtistMenuItem("艺术家：${song.artist.ifBlank { "未知艺术家" }}", onArtist)
        ArtistMenuItem("专辑：${song.album.ifBlank { "未知专辑" }}", onAlbum)
        ArtistMenuItem("编辑歌曲标签信息", onEditTag)
        ArtistMenuItem("永久删除", onDelete, danger = true)
        ArtistMenuItem("取消", onDismiss)
    }
}

@Composable
private fun ArtistAddToPlaylistMenu(
    playlists: List<UserPlaylist>,
    onDismiss: () -> Unit,
    onCreatePlaylist: () -> Unit,
    onPlaylistsConfirm: (List<UserPlaylist>) -> Unit
) {
    var selectedPlaylistIds by remember(playlists) { mutableStateOf(emptySet<String>()) }
    val selectedPlaylists = playlists.filter { it.id in selectedPlaylistIds }
    ArtistSheetColumn {
        ArtistSheetHandle()
        Text(
            text = "添加到歌单",
            fontSize = 18.sp,
            fontWeight = FontWeight.ExtraBold,
            color = MiuixTheme.colorScheme.onSurface,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp)
        )
        ArtistMenuItem("新建歌单", onCreatePlaylist)
        if (playlists.isEmpty()) {
            Text(
                text = "暂无自定义歌单",
                fontSize = 14.sp,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 18.dp)
            )
        } else {
            playlists.forEach { playlist ->
                val selected = playlist.id in selectedPlaylistIds
                ArtistMenuItem("${if (selected) "✓ " else ""}${playlist.name} · ${playlist.songs.size} 首", onClick = {
                    selectedPlaylistIds = if (selected) {
                        selectedPlaylistIds - playlist.id
                    } else {
                        selectedPlaylistIds + playlist.id
                    }
                })
            }
        }
        if (playlists.isNotEmpty()) {
            ArtistMenuItem("完成（${selectedPlaylistIds.size}）", onClick = {
                if (selectedPlaylists.isNotEmpty()) {
                    onPlaylistsConfirm(selectedPlaylists)
                }
            })
        }
        ArtistMenuItem("取消", onDismiss)
    }
}

@Composable
private fun ArtistCreatePlaylistSheet(
    onDismiss: () -> Unit,
    onCreate: (String) -> Unit
) {
    var name by remember { mutableStateOf("") }
    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current
    LaunchedEffect(Unit) {
        delay(220L)
        focusRequester.requestFocus()
        keyboardController?.show()
    }
    WindowBottomSheet(
        show = true,
        title = "新建歌单",
        onDismissRequest = onDismiss
    ) {
        Column(
            modifier = Modifier.padding(bottom = 18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            TextField(
                value = name,
                onValueChange = { name = it },
                label = "歌单名称",
                useLabelAsPlaceholder = true,
                singleLine = true,
                insideMargin = DpSize(12.dp, 10.dp),
                backgroundColor = MiuixTheme.colorScheme.surfaceContainer,
                cornerRadius = 12.dp,
                textStyle = TextStyle(
                    color = MiuixTheme.colorScheme.onSurface,
                    fontSize = 15.sp
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focusRequester)
            )
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                Button(onClick = onDismiss) { Text("取消") }
                Spacer(modifier = Modifier.width(8.dp))
                Button(onClick = { onCreate(name) }) { Text("创建") }
            }
        }
    }
}

@Composable
private fun ArtistTagEditorMenu(
    song: Song,
    onDismiss: () -> Unit,
    onOptionClick: (com.ella.music.ui.components.TagEditorOption) -> Unit
) {
    val context = LocalContext.current
    val options = remember(song) {
        buildTagEditorOptions(context, song).filter { it.kind == TagEditorOptionKind.Metadata }
    }
    ArtistSheetColumn {
        ArtistSheetHandle()
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
        options.forEach { option ->
            ArtistMenuItem(option.label, onClick = { onOptionClick(option) })
        }
        ArtistMenuItem("取消", onDismiss)
    }
}

@Composable
private fun ArtistSongInfoMenu(
    song: Song,
    mainViewModel: MainViewModel,
    onAiInterpret: () -> Unit,
    onDismiss: () -> Unit
) {
    val audioInfo by produceState<AudioInfo?>(initialValue = null, song.id, song.dateModified, song.fileSize) {
        value = withContext(Dispatchers.IO) { mainViewModel.getAudioInfo(song) }
    }
    val tagInfo by produceState<SongTagInfo?>(initialValue = null, song.id, song.dateModified, song.fileSize) {
        value = withContext(Dispatchers.IO) { mainViewModel.getSongTagInfo(song) }
    }
    ArtistSheetColumn {
        ArtistSheetHandle()
        Text(
            text = "歌曲信息",
            fontSize = 18.sp,
            fontWeight = FontWeight.ExtraBold,
            color = MiuixTheme.colorScheme.onSurface,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp)
        )
        ArtistMenuItem("AI 解读歌曲", onAiInterpret)
        ArtistInfoRow("标题", tagInfo?.title?.ifBlank { song.title } ?: song.title)
        ArtistInfoRow("艺术家", tagInfo?.artist?.ifBlank { song.artist } ?: song.artist)
        ArtistInfoRow("专辑", tagInfo?.album?.ifBlank { song.album } ?: song.album)
        ArtistInfoRow("专辑艺术家", tagInfo?.albumArtist?.ifBlank { song.albumArtist }.orEmpty())
        ArtistInfoRow("注释", tagInfo?.displayComment.orEmpty())
        ArtistInfoRow("音频", audioInfo?.let { detailedAudioInfo(it) }.orEmpty())
        ArtistInfoRow("路径", song.path)
    }
}

@Composable
private fun ArtistAiInterpretationMenu(
    song: Song,
    mainViewModel: MainViewModel,
    onDismiss: () -> Unit
) {
    val result by produceState<Result<String>?>(initialValue = null, song.id) {
        value = runCatching { mainViewModel.interpretSongWithOpenAi(song) }
    }
    ArtistSheetColumn {
        ArtistSheetHandle()
        Text(
            text = "AI 解读歌曲",
            fontSize = 18.sp,
            fontWeight = FontWeight.ExtraBold,
            color = MiuixTheme.colorScheme.onSurface,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp)
        )
        Text(
            text = when {
                result == null -> "正在读取歌曲信息和歌词..."
                result?.isSuccess == true -> result?.getOrNull().orEmpty()
                else -> result?.exceptionOrNull()?.message ?: "AI 解读失败"
            },
            fontSize = 14.sp,
            lineHeight = 22.sp,
            color = MiuixTheme.colorScheme.onSurface,
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(MiuixTheme.colorScheme.surfaceContainer.copy(alpha = 0.72f))
                .padding(horizontal = 16.dp, vertical = 14.dp)
        )
        ArtistMenuItem("关闭", onDismiss)
    }
}

@Composable
private fun ArtistSheetColumn(content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
            .background(MiuixTheme.colorScheme.background.copy(alpha = 0.98f))
            .verticalScroll(rememberScrollState())
            .navigationBarsPadding()
            .padding(horizontal = 18.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
        content = content
    )
}

@Composable
private fun ArtistSheetHandle() {
    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .width(52.dp)
                .height(5.dp)
                .clip(RoundedCornerShape(999.dp))
                .background(MiuixTheme.colorScheme.onSurfaceVariantSummary.copy(alpha = 0.28f))
        )
    }
}

@Composable
private fun ArtistMenuItem(
    text: String,
    onClick: () -> Unit,
    danger: Boolean = false
) {
    Text(
        text = text,
        fontSize = 16.sp,
        fontWeight = FontWeight.Bold,
        color = if (danger) Color(0xFFE5484D) else MiuixTheme.colorScheme.onSurface,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 13.dp)
    )
}

@Composable
private fun ArtistInfoRow(label: String, value: String) {
    if (value.isBlank()) return
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 6.dp)
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
            maxLines = if (label == "路径" || label == "音频") 4 else 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 2.dp)
        )
    }
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
                            0.00f to Color.Black.copy(alpha = 0.12f),
                            0.42f to Color.Black.copy(alpha = 0.28f),
                            0.72f to Color.Black.copy(alpha = 0.58f),
                            0.88f to pageBackground.copy(alpha = 0.82f),
                            1.00f to pageBackground
                        )
                    )
                )
        )

        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 42.dp),
            horizontalAlignment = Alignment.Start,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = artistName.ifBlank { "未知歌手" },
                fontSize = 36.sp,
                fontWeight = FontWeight.ExtraBold,
                color = headerTextColor,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            Text(
                text = "$albumCount 张专辑 · $songCount 首歌曲",
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                color = headerSubTextColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
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
    DateModifiedAsc("修改时间升序"),
    YearAsc("发行时间正序"),
    YearDesc("发行时间倒序")
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
        ArtistDetailSongSortMode.YearAsc -> sortedByReleaseDate(ascending = true)
        ArtistDetailSongSortMode.YearDesc -> sortedByReleaseDate(ascending = false)
        ArtistDetailSongSortMode.DateAdded -> sortedByDescending { it.dateAdded }
        ArtistDetailSongSortMode.DateAddedAsc -> sortedBy { it.dateAdded }
        ArtistDetailSongSortMode.DateModified -> sortedByDescending { it.dateModified }
        ArtistDetailSongSortMode.DateModifiedAsc -> sortedBy { it.dateModified }
    }
}

private enum class ArtistDetailAlbumSortMode(val label: String) {
    YearAsc("发行时间正序"),
    YearDesc("发行时间倒序"),
    SongCount("歌曲数"),
    Duration("歌曲时长"),
    Name("专辑名称")
}

private fun List<Album>.sortedForArtistAlbumDetail(
    mode: ArtistDetailAlbumSortMode,
    durations: Map<Long, Long>
): List<Album> {
    return when (mode) {
        ArtistDetailAlbumSortMode.YearAsc -> sortedWith(compareBy<Album> { it.year <= 0 }.thenBy { it.year }.thenBy { it.name.lowercase(Locale.ROOT) })
        ArtistDetailAlbumSortMode.YearDesc -> sortedWith(compareBy<Album> { it.year <= 0 }.thenByDescending { it.year }.thenBy { it.name.lowercase(Locale.ROOT) })
        ArtistDetailAlbumSortMode.SongCount -> sortedByDescending { it.songCount }
        ArtistDetailAlbumSortMode.Duration -> sortedByDescending { durations[it.id] ?: 0L }
        ArtistDetailAlbumSortMode.Name -> sortedBy { it.name.lowercase(Locale.ROOT) }
    }
}

private fun List<Song>.sortedByReleaseDate(ascending: Boolean): List<Song> {
    val comparator = if (ascending) {
        compareBy<Song> { it.releaseYearOrNull() == null }
            .thenBy { it.releaseYearOrNull() ?: Int.MAX_VALUE }
    } else {
        compareBy<Song> { it.releaseYearOrNull() == null }
            .thenByDescending { it.releaseYearOrNull() ?: Int.MIN_VALUE }
    }
    return sortedWith(
        comparator
            .thenBy { it.album.lowercase(Locale.ROOT) }
            .thenBy { if (it.discNumber > 0) it.discNumber else Int.MAX_VALUE }
            .thenBy { if (it.trackNumber > 0) it.trackNumber else Int.MAX_VALUE }
            .thenBy { it.title.lowercase(Locale.ROOT) }
    )
}

private fun Song.releaseYearOrNull(): Int? =
    Regex("""\d{4}""").find(year)?.value?.toIntOrNull()

@Composable
private fun ArtistAlbumRow(
    album: Album,
    duration: Long,
    albumArtUri: Uri?,
    onClick: () -> Unit
) {
    val summary = buildList {
        add("${album.songCount} 首歌曲")
        if (album.year > 0) add("${album.year}年")
        add(duration.formatArtistDetailDuration())
    }.joinToString(" · ")

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
                DefaultAlbumCover(modifier = Modifier.fillMaxSize())
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
                text = summary,
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

private fun Long.formatArtistDetailDuration(): String {
    return formatPlaybackDuration()
}
