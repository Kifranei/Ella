package com.ella.music.ui.playlist

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ella.music.data.model.FAVORITES_PLAYLIST_ID
import com.ella.music.data.model.UserPlaylist
import com.ella.music.data.model.playlistIdentityKey
import com.ella.music.ui.components.AppleStylePlayButton
import com.ella.music.ui.components.SongItem
import com.ella.music.ui.components.ellaPageBackground
import com.ella.music.viewmodel.MainViewModel
import com.ella.music.viewmodel.PlayerViewModel
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.SmallTopAppBar
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Add
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.icon.extended.Download
import top.yukonga.miuix.kmp.icon.extended.Share
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.window.WindowBottomSheet

@Composable
fun PlaylistScreen(
    mainViewModel: MainViewModel,
    onBack: () -> Unit,
    onPlaylistClick: (String) -> Unit
) {
    val context = LocalContext.current
    val playlists by mainViewModel.playlists.collectAsState()
    var showCreateDialog by remember { mutableStateOf(false) }
    val favorites = playlists.firstOrNull { it.id == FAVORITES_PLAYLIST_ID }
    val customPlaylists = playlists.filterNot { it.id == FAVORITES_PLAYLIST_ID }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        mainViewModel.importLocalPlaylist(uri) { result ->
            result
                .onSuccess { importResult ->
                    val message = if (importResult.importedCount == 0) {
                        "没有可导入的歌曲"
                    } else {
                        val missingText = if (importResult.missingCount > 0) "，保留 ${importResult.missingCount} 首未匹配路径" else ""
                        val duplicateText = if (importResult.duplicateCount > 0) "，跳过 ${importResult.duplicateCount} 条重复" else ""
                        "已导入 ${importResult.importedCount} 首，匹配 ${importResult.matchedCount} 首$missingText$duplicateText"
                    }
                    Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                }
                .onFailure {
                    Toast.makeText(context, "导入失败：${it.message.orEmpty()}", Toast.LENGTH_SHORT).show()
                }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(ellaPageBackground())
            .windowInsetsPadding(WindowInsets.statusBars)
    ) {
        SmallTopAppBar(
            title = "歌单",
            color = ellaPageBackground(),
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = MiuixIcons.Regular.Back,
                        contentDescription = "返回",
                        tint = MiuixTheme.colorScheme.onSurface
                    )
                }
            },
            actions = {
                IconButton(onClick = { importLauncher.launch(arrayOf("text/plain", "application/octet-stream", "*/*")) }) {
                    Icon(
                        imageVector = MiuixIcons.Regular.Download,
                        contentDescription = "导入歌单",
                        tint = MiuixTheme.colorScheme.onSurface,
                        modifier = Modifier.size(24.dp)
                    )
                }
                IconButton(onClick = { showCreateDialog = true }) {
                    Icon(
                        imageVector = MiuixIcons.Regular.Add,
                        contentDescription = "新建歌单",
                        tint = MiuixTheme.colorScheme.onSurface,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        )

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
        ) {
            if (favorites != null) {
                item(key = favorites.id) {
                    PlaylistRow(
                        playlist = favorites,
                        accent = true,
                        onClick = { onPlaylistClick(favorites.id) }
                    )
                }
            }

            item {
                Text(
                    text = "自定义歌单",
                    fontSize = 13.sp,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 12.dp)
                )
            }

            if (customPlaylists.isEmpty()) {
                item {
                    Text(
                        text = "还没有自定义歌单",
                        fontSize = 14.sp,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 12.dp)
                    )
                }
            } else {
                items(customPlaylists, key = { it.id }) { playlist ->
                    PlaylistRow(
                        playlist = playlist,
                        onClick = { onPlaylistClick(playlist.id) },
                        onDelete = { mainViewModel.deletePlaylist(playlist.id) }
                    )
                }
            }

            item { Spacer(modifier = Modifier.height(150.dp)) }
        }
    }

    if (showCreateDialog) {
        CreatePlaylistDialog(
            onDismiss = { showCreateDialog = false },
            onCreate = { name ->
                mainViewModel.createPlaylist(name)
                showCreateDialog = false
            }
        )
    }
}

@Composable
fun PlaylistDetailScreen(
    playlistId: String,
    mainViewModel: MainViewModel,
    playerViewModel: PlayerViewModel,
    onBack: () -> Unit,
    onNavigateToPlayer: () -> Unit
) {
    val context = LocalContext.current
    val playlists by mainViewModel.playlists.collectAsState()
    val currentSong by playerViewModel.currentSong.collectAsState()
    val librarySongs by mainViewModel.songs.collectAsState()
    val openPlayerOnPlay by mainViewModel.settingsManager.openPlayerOnPlay.collectAsState(initial = true)
    val playlist = playlists.firstOrNull { it.id == playlistId }
    val songs = remember(playlist, librarySongs) {
        playlist?.let(mainViewModel::playlistSongs).orEmpty()
    }
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri ->
        val targetPlaylist = playlist
        if (uri == null || targetPlaylist == null) return@rememberLauncherForActivityResult
        mainViewModel.exportLocalPlaylist(targetPlaylist, uri) { result ->
            result
                .onSuccess { exportResult ->
                    val skippedText = if (exportResult.skippedCount > 0) "，跳过 ${exportResult.skippedCount} 首在线歌曲" else ""
                    Toast.makeText(context, "已导出 ${exportResult.exportedCount} 首$skippedText", Toast.LENGTH_SHORT).show()
                }
                .onFailure {
                    Toast.makeText(context, "导出失败：${it.message.orEmpty()}", Toast.LENGTH_SHORT).show()
                }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(ellaPageBackground())
            .windowInsetsPadding(WindowInsets.statusBars)
    ) {
        SmallTopAppBar(
            title = playlist?.name ?: "歌单",
            color = ellaPageBackground(),
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = MiuixIcons.Regular.Back,
                        contentDescription = "返回",
                        tint = MiuixTheme.colorScheme.onSurface
                    )
                }
            },
            actions = {
                if (playlist != null) {
                    IconButton(onClick = { exportLauncher.launch("${playlist.name.safePlaylistFileName()}.txt") }) {
                        Icon(
                            imageVector = MiuixIcons.Regular.Share,
                            contentDescription = "导出歌单",
                            tint = MiuixTheme.colorScheme.onSurface,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            }
        )

        if (playlist == null) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("歌单不存在", color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
            }
            return@Column
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 150.dp)
        ) {
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 18.dp, vertical = 18.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = playlist.name,
                        fontSize = 30.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = MiuixTheme.colorScheme.onSurface,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "${songs.size} 首歌曲",
                        fontSize = 14.sp,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary
                    )
                    AppleStylePlayButton(
                        text = "播放全部",
                        onClick = {
                            if (songs.isNotEmpty()) {
                                playerViewModel.setPlaylist(songs, 0)
                                if (openPlayerOnPlay) onNavigateToPlayer()
                            }
                        }
                    )
                }
            }

            if (songs.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 60.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (playlist.isFavorites) "播放页点红心后会收藏到这里" else "这个歌单还没有歌曲",
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                            fontSize = 14.sp
                        )
                    }
                }
            } else {
                itemsIndexed(songs, key = { _, song -> song.playlistIdentityKey() }) { index, song ->
                    SongItem(
                        song = song,
                        isCurrent = currentSong?.playlistIdentityKey() == song.playlistIdentityKey(),
                        albumArtUri = mainViewModel.getAlbumArtUri(song.albumId),
                        loadCoverArt = mainViewModel::getCoverArtBitmap,
                        loadAudioInfo = mainViewModel::getAudioInfo,
                        onClick = {
                            playerViewModel.setPlaylist(songs, index)
                            if (openPlayerOnPlay) onNavigateToPlayer()
                        },
                        onAddToQueue = { playerViewModel.addToPlaylist(song) },
                        onRemove = {
                            mainViewModel.removeSongFromPlaylist(playlist.id, song.playlistIdentityKey())
                        }
                    )
                }
            }
        }
    }
}

private fun String.safePlaylistFileName(): String =
    replace(Regex("[\\\\/:*?\"<>|]"), "_").trim().ifBlank { "Ella Playlist" }

@Composable
private fun PlaylistRow(
    playlist: UserPlaylist,
    accent: Boolean = false,
    onClick: () -> Unit,
    onDelete: (() -> Unit)? = null
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 10.dp),
        cornerRadius = 16.dp,
        onClick = onClick
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .background(
                        color = if (accent) MiuixTheme.colorScheme.primary.copy(alpha = 0.18f)
                        else MiuixTheme.colorScheme.surfaceContainer,
                        shape = RoundedCornerShape(14.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (playlist.isFavorites) "♥" else "♪",
                    fontSize = 24.sp,
                    color = if (accent) MiuixTheme.colorScheme.primary else MiuixTheme.colorScheme.onSurface
                )
            }
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = playlist.name,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    color = MiuixTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${playlist.songs.size} 首歌曲",
                    fontSize = 13.sp,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary
                )
            }
            if (onDelete != null) {
                Text(
                    text = "删除",
                    fontSize = 13.sp,
                    color = Color(0xFFE5484D),
                    modifier = Modifier
                        .clickable(onClick = onDelete)
                        .padding(8.dp)
                )
            }
        }
    }
}

@Composable
private fun CreatePlaylistDialog(
    onDismiss: () -> Unit,
    onCreate: (String) -> Unit
) {
    var name by remember { mutableStateOf("") }
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
                modifier = Modifier.fillMaxWidth()
            )
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                Button(onClick = onDismiss) { Text("取消") }
                Spacer(modifier = Modifier.width(8.dp))
                Button(onClick = { onCreate(name) }) { Text("创建") }
            }
        }
    }
}
