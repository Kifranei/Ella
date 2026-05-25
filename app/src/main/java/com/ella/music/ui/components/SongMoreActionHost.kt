package com.ella.music.ui.components

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ella.music.data.decodeNeteaseKey
import com.ella.music.data.detailedAudioInfo
import com.ella.music.data.model.AudioInfo
import com.ella.music.data.model.FAVORITES_PLAYLIST_ID
import com.ella.music.data.model.Song
import com.ella.music.data.model.SongTagInfo
import com.ella.music.data.model.UserPlaylist
import com.ella.music.data.model.albumIdentityId
import com.ella.music.data.neteaseAlbumUrl
import com.ella.music.data.neteaseArtistUrl
import com.ella.music.data.neteaseSongUrl
import com.ella.music.data.splitArtistNames
import com.ella.music.data.tagIdentityKey
import com.ella.music.viewmodel.MainViewModel
import com.ella.music.viewmodel.PlayerViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.window.WindowBottomSheet
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import com.ella.music.data.NeteaseArtist

@Composable
fun SongMoreActionHost(
    actionSong: Song?,
    mainViewModel: MainViewModel,
    playerViewModel: PlayerViewModel,
    onDismissAction: () -> Unit,
    onNavigateToAlbum: (Long) -> Unit = {},
    onNavigateToArtist: (String) -> Unit = {},
    onSongRemovedFromPlaylist: ((Song) -> Unit)? = null,
    deleteFromLibrary: Boolean = true,
    showDelete: Boolean = true,
    showLocalFileActions: Boolean = true,
    resolveSongForAction: (suspend (Song) -> Song)? = null,
    onDeleteSong: ((Song) -> Unit)? = null
) {
    val context = LocalContext.current
    val playlists by mainViewModel.playlists.collectAsState(initial = emptyList())
    val metadataEditorId by mainViewModel.settingsManager.metadataEditorId.collectAsState(initial = TagEditorOptionIds.ASK_EACH_TIME)
    val scope = rememberCoroutineScope()
    var playlistSong by remember { mutableStateOf<Song?>(null) }
    var createPlaylistSong by remember { mutableStateOf<Song?>(null) }
    var tagEditorSong by remember { mutableStateOf<Song?>(null) }
    var infoSong by remember { mutableStateOf<Song?>(null) }
    var aiSong by remember { mutableStateOf<Song?>(null) }
    var artistChoices by remember { mutableStateOf<List<String>>(emptyList()) }
    var dangerConfirmTitle by remember { mutableStateOf("") }
    var dangerConfirmMessage by remember { mutableStateOf("") }
    var dangerConfirmText by remember { mutableStateOf("删除") }
    var dangerConfirmAction by remember { mutableStateOf<(() -> Unit)?>(null) }

    fun closeAction() = onDismissAction()

    fun requestDangerConfirm(
        title: String,
        message: String,
        confirmText: String = "删除",
        action: () -> Unit
    ) {
        dangerConfirmTitle = title
        dangerConfirmMessage = message
        dangerConfirmText = confirmText
        dangerConfirmAction = action
    }

    fun runResolvedSongAction(
        sourceSong: Song,
        failureMessage: String,
        action: (Song) -> Unit
    ) {
        scope.launch {
            runCatching {
                resolveSongForAction?.invoke(sourceSong) ?: sourceSong
            }.onSuccess { resolvedSong ->
                action(resolvedSong)
            }.onFailure { error ->
                Toast.makeText(context, error.localizedMessage ?: failureMessage, Toast.LENGTH_SHORT).show()
            }
        }
    }

    actionSong?.let { song ->
        WindowBottomSheet(
            show = true,
            enableNestedScroll = false,
            title = song.title.ifBlank { "歌曲操作" },
            onDismissRequest = ::closeAction
        ) {
            SongMoreActionSheet(
                song = song,
                onDismiss = ::closeAction,
                onAddToPlaylist = {
                    closeAction()
                    runResolvedSongAction(song, "添加到歌单失败") { resolvedSong ->
                        playlistSong = resolvedSong
                    }
                },
                onPlayNext = {
                    closeAction()
                    runResolvedSongAction(song, "添加到下一首播放失败") { resolvedSong ->
                        playerViewModel.playNext(resolvedSong)
                        Toast.makeText(context, "已添加到下一首播放", Toast.LENGTH_SHORT).show()
                    }
                },
                onShare = {
                    closeAction()
                    runResolvedSongAction(song, "分享失败") { resolvedSong ->
                        shareLocalSong(context, resolvedSong)
                    }
                },
                onSpectrum = {
                    openSongSpectrumWithAspectPro(context, song)
                    closeAction()
                },
                onInfo = {
                    infoSong = song
                    closeAction()
                },
                onAiInterpret = {
                    aiSong = song
                    closeAction()
                },
                onArtist = {
                    val artists = splitArtistNames(song.artist)
                        .filterNot { it.equals("Unknown", ignoreCase = true) }
                        .distinctBy { it.tagIdentityKey() }
                    when (artists.size) {
                        0 -> Toast.makeText(context, "这首歌没有可跳转的歌手信息", Toast.LENGTH_SHORT).show()
                        1 -> onNavigateToArtist(artists.first())
                        else -> artistChoices = artists
                    }
                    closeAction()
                },
                onAlbum = {
                    val albumId = song.albumIdentityId()
                    if (albumId > 0L) {
                        onNavigateToAlbum(albumId)
                    } else {
                        Toast.makeText(context, "这首歌没有可跳转的专辑信息", Toast.LENGTH_SHORT).show()
                    }
                    closeAction()
                },
                onEditTag = if (showLocalFileActions) {
                    {
                        tagEditorSong = song
                        closeAction()
                    }
                } else null,
                onRemoveFromPlaylist = onSongRemovedFromPlaylist?.let {
                    {
                        closeAction()
                        requestDangerConfirm(
                            title = "从歌单移除",
                            message = "确定要从当前歌单移除《${song.title.ifBlank { song.fileName.ifBlank { "这首歌" } }}》吗？不会删除本地文件。",
                            confirmText = "移除"
                        ) {
                            it(song)
                        }
                    }
                },
                onDelete = if (showDelete) {
                    {
                        closeAction()
                        requestDangerConfirm(
                            title = if (deleteFromLibrary) "永久删除歌曲" else "从音乐库移除",
                            message = if (deleteFromLibrary) {
                                "确定要永久删除《${song.title.ifBlank { song.fileName.ifBlank { "这首歌" } }}》吗？此操作可能会删除本地音频文件。"
                            } else {
                                "确定要从音乐库移除《${song.title.ifBlank { song.fileName.ifBlank { "这首歌" } }}》吗？本地文件不会被删除。"
                            },
                            confirmText = if (deleteFromLibrary) "永久删除" else "移除"
                        ) {
                            if (onDeleteSong != null) {
                                onDeleteSong(song)
                            } else if (deleteFromLibrary) {
                                mainViewModel.deleteSongs(listOf(song))
                            } else {
                                mainViewModel.removeSongsFromLibrary(listOf(song))
                            }
                        }
                    }
                } else null,
                showSpectrum = showLocalFileActions
            )
        }
    }

    ConfirmDangerDialog(
        show = dangerConfirmAction != null,
        title = dangerConfirmTitle,
        message = dangerConfirmMessage,
        confirmText = dangerConfirmText,
        onDismiss = { dangerConfirmAction = null },
        onConfirm = {
            val action = dangerConfirmAction
            dangerConfirmAction = null
            action?.invoke()
        }
    )

    if (artistChoices.isNotEmpty()) {
        WindowBottomSheet(
            show = true,
            enableNestedScroll = false,
            title = "选择歌手",
            onDismissRequest = { artistChoices = emptyList() }
        ) {
            ArtistPickerContent(
                artists = artistChoices,
                onArtistSelected = { artist ->
                    artistChoices = emptyList()
                    onNavigateToArtist(artist)
                },
                onDismiss = { artistChoices = emptyList() }
            )
        }
    }

    playlistSong?.let { song ->
        WindowBottomSheet(
            show = true,
            enableNestedScroll = false,
            title = "添加到歌单",
            onDismissRequest = { playlistSong = null }
        ) {
            AddToPlaylistSheet(
                playlists = playlists
                    .filterNot { it.id == FAVORITES_PLAYLIST_ID }
                    .sortedByDescending { it.createdAt },
                onDismiss = { playlistSong = null },
                onCreatePlaylist = {
                    createPlaylistSong = song
                    playlistSong = null
                },
                onPlaylistsConfirm = { selectedPlaylists ->
                    selectedPlaylists.forEach { playlist ->
                        mainViewModel.addSongsToPlaylist(playlist.id, listOf(song))
                    }
                    Toast.makeText(context, "已添加到 ${selectedPlaylists.size} 个歌单", Toast.LENGTH_SHORT).show()
                    playlistSong = null
                }
            )
        }
    }

    createPlaylistSong?.let { song ->
        CreatePlaylistAndAddSheet(
            onDismiss = { createPlaylistSong = null },
            onCreate = { name ->
                mainViewModel.createPlaylist(name) { playlist ->
                    if (playlist != null) {
                        mainViewModel.addSongsToPlaylist(playlist.id, listOf(song))
                        Toast.makeText(context, "已添加到 ${playlist.name}", Toast.LENGTH_SHORT).show()
                    }
                }
                createPlaylistSong = null
            }
        )
    }

    tagEditorSong?.let { song ->
        val tagOptions = remember(song.id, song.path, song.mimeType) {
            buildTagEditorOptions(context, song)
                .filter { it.kind == TagEditorOptionKind.Metadata }
        }
        val preferredOption = remember(tagOptions, metadataEditorId) {
            tagOptions.firstOrNull { it.id == metadataEditorId }
        }
        LaunchedEffect(song.id, metadataEditorId, preferredOption) {
            if (metadataEditorId.isNotBlank() && preferredOption != null) {
                launchTagEditorOption(context, preferredOption)
                tagEditorSong = null
            }
        }
        WindowBottomSheet(
            show = true,
            enableNestedScroll = false,
            title = "编辑歌曲标签信息",
            onDismissRequest = { tagEditorSong = null }
        ) {
            SongTagEditorSheet(
                song = song,
                options = tagOptions,
                onDismiss = { tagEditorSong = null },
                onOptionClick = { option ->
                    launchTagEditorOption(context, option)
                    tagEditorSong = null
                }
            )
        }
    }

    infoSong?.let { song ->
        WindowBottomSheet(
            show = true,
            enableNestedScroll = false,
            title = "歌曲信息",
            onDismissRequest = { infoSong = null }
        ) {
            SongInfoSheet(
                song = song,
                audioInfoLoader = mainViewModel::getAudioInfo,
                tagInfoLoader = mainViewModel::getSongTagInfo,
                onDismiss = { infoSong = null }
            )
        }
    }

    aiSong?.let { song ->
        WindowBottomSheet(
            show = true,
            enableNestedScroll = false,
            title = "AI 解读歌曲",
            onDismissRequest = { aiSong = null }
        ) {
            SongAiInterpretationSheet(
                song = song,
                mainViewModel = mainViewModel,
                onDismiss = { aiSong = null }
            )
        }
    }
}

@Composable
private fun SongMoreActionSheet(
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
    onEditTag: (() -> Unit)?,
    onRemoveFromPlaylist: (() -> Unit)?,
    onDelete: (() -> Unit)?,
    showSpectrum: Boolean
) {
    SongSheetColumn {
        SongMenuItem("添加到歌单", onAddToPlaylist)
        SongMenuItem("下一首播放", onPlayNext)
        SongMenuItem("分享", onShare)
        if (showSpectrum) {
            SongMenuItem("查看频谱", onSpectrum)
        }
        SongMenuItem("AI 解读歌曲", onAiInterpret)
        SongMenuItem("查看歌曲信息", onInfo)
        SongMenuItem("艺术家：${song.artist.ifBlank { "未知艺术家" }}", onArtist)
        SongMenuItem("专辑：${song.album.ifBlank { "未知专辑" }}", onAlbum)
        if (onEditTag != null) {
            SongMenuItem("编辑歌曲标签信息", onEditTag)
        }
        if (onRemoveFromPlaylist != null) {
            SongMenuItem("从歌单移除", onRemoveFromPlaylist, danger = true)
        }
        if (onDelete != null) {
            SongMenuItem("永久删除", onDelete, danger = true)
        }
        SongMenuItem("取消", onDismiss)
    }
}

@Composable
private fun AddToPlaylistSheet(
    playlists: List<UserPlaylist>,
    onDismiss: () -> Unit,
    onCreatePlaylist: () -> Unit,
    onPlaylistsConfirm: (List<UserPlaylist>) -> Unit
) {
    var selectedIds by remember(playlists) { mutableStateOf(emptySet<String>()) }
    val selectedPlaylists = playlists.filter { it.id in selectedIds }
    SongSheetColumn {
        SongMenuItem("新建歌单", onCreatePlaylist)
        if (playlists.isEmpty()) {
            Text(
                text = "暂无自定义歌单",
                fontSize = 14.sp,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 18.dp)
            )
        } else {
            playlists.forEach { playlist ->
                val selected = playlist.id in selectedIds
                SongMenuItem(
                    "${if (selected) "✓ " else ""}${playlist.name} · ${playlist.songs.size} 首",
                    onClick = {
                        selectedIds = if (selected) {
                            selectedIds - playlist.id
                        } else {
                            selectedIds + playlist.id
                        }
                    }
                )
            }
        }
        if (playlists.isNotEmpty()) {
            SongMenuItem(
                "完成（${selectedIds.size}）",
                onClick = {
                    if (selectedPlaylists.isNotEmpty()) {
                        onPlaylistsConfirm(selectedPlaylists)
                    }
                }
            )
        }
        SongMenuItem("取消", onDismiss)
    }
}

@Composable
private fun ArtistPickerContent(
    artists: List<String>,
    onArtistSelected: (String) -> Unit,
    onDismiss: () -> Unit
) {
    SongSheetColumn {
        artists.distinctBy { it.tagIdentityKey() }.forEach { artist ->
            BasicComponent(
                title = artist,
                onClick = { onArtistSelected(artist) }
            )
        }
        BasicComponent(
            title = "取消",
            onClick = onDismiss
        )
    }
}

@Composable
private fun CreatePlaylistAndAddSheet(
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
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            TextField(
                value = name,
                onValueChange = { name = it },
                label = "歌单名称",
                useLabelAsPlaceholder = true,
                singleLine = true,
                insideMargin = DpSize(12.dp, 10.dp),
                backgroundColor = MiuixTheme.colorScheme.surfaceContainer,
                cornerRadius = 12.dp,
                textStyle = TextStyle(color = MiuixTheme.colorScheme.onSurface, fontSize = 15.sp),
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
private fun SongTagEditorSheet(
    song: Song,
    options: List<TagEditorOption>,
    onDismiss: () -> Unit,
    onOptionClick: (TagEditorOption) -> Unit
) {
    SongSheetColumn {
        Text(
            text = song.title,
            fontSize = 13.sp,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 8.dp)
        )
        options.forEach { option -> SongMenuItem(option.label, onClick = { onOptionClick(option) }) }
        SongMenuItem("取消", onDismiss)
    }
}

@Composable
fun SongInfoSheet(
    song: Song,
    audioInfoLoader: (Song) -> AudioInfo,
    tagInfoLoader: (Song) -> SongTagInfo,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var showNeteaseKeyInfo by remember(song.id) { mutableStateOf(false) }
    var showNeteaseArtistPicker by remember(song.id) { mutableStateOf(false) }
    val audioInfo by produceState<AudioInfo?>(initialValue = null, song.id, song.dateModified, song.fileSize) {
        value = withContext(Dispatchers.IO) { audioInfoLoader(song) }
    }
    val tagInfo by produceState<SongTagInfo?>(initialValue = null, song.id, song.dateModified, song.fileSize) {
        value = withContext(Dispatchers.IO) { tagInfoLoader(song) }
    }
    val neteaseInfo = remember(tagInfo?.neteaseKey) { decodeNeteaseKey(tagInfo?.neteaseKey.orEmpty()) }
    val neteaseArtists = remember(neteaseInfo) {
        neteaseInfo?.artists.orEmpty().filter { it.id.isNotBlank() }
    }

    if (showNeteaseArtistPicker && neteaseArtists.isNotEmpty()) {
        SongSheetColumn {
            Text(
                text = "选择网易云歌手",
                fontSize = 18.sp,
                fontWeight = FontWeight.ExtraBold,
                color = MiuixTheme.colorScheme.onSurface,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp)
            )
            neteaseArtists.forEach { artist ->
                SongMenuItem(artist.name.ifBlank { "ID ${artist.id}" }, onClick = {
                    openUrl(context, neteaseArtistUrl(artist.id))
                })
            }
            SongMenuItem("返回 163 key", onClick = { showNeteaseArtistPicker = false })
        }
        return
    }

    if (showNeteaseKeyInfo && neteaseInfo != null) {
        SongSheetColumn {
            Text(
                text = "163 key",
                fontSize = 18.sp,
                fontWeight = FontWeight.ExtraBold,
                color = MiuixTheme.colorScheme.onSurface,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp)
            )
            neteaseInfo.musicName.takeIf { it.isNotBlank() }?.let { SongInfoRow("歌曲", it) }
            neteaseInfo.aliases
                .joinToString(" / ")
                .takeIf { it.isNotBlank() }
                ?.let { SongInfoRow("别名", it) }
            neteaseInfo.artists
                .joinToString(" / ") { it.name.ifBlank { it.id } }
                .takeIf { it.isNotBlank() }
                ?.let { SongInfoRow("歌手", it) }
            neteaseInfo.albumName.takeIf { it.isNotBlank() }?.let { SongInfoRow("专辑", it) }
            neteaseInfo.comment.takeIf { it.isNotBlank() }?.let { SongInfoRow("注释", it) }
            neteaseInfo.musicId.takeIf { it.isNotBlank() }?.let { id ->
                SongMenuItem("网易云歌曲页", onClick = { openUrl(context, neteaseSongUrl(id)) })
            }
            if (neteaseArtists.isNotEmpty()) {
                SongMenuItem(
                    title = "网易云歌手页",
                    onClick = {
                        if (neteaseArtists.size == 1) {
                            openUrl(context, neteaseArtistUrl(neteaseArtists.first().id))
                        } else {
                            showNeteaseArtistPicker = true
                        }
                    }
                )
            }
            neteaseInfo.albumId.takeIf { it.isNotBlank() }?.let { id ->
                SongMenuItem("网易云专辑页", onClick = { openUrl(context, neteaseAlbumUrl(id)) })
            }
            SongInfoRow("原始 163 key", neteaseInfo.raw)
            SongMenuItem("返回", onClick = { showNeteaseKeyInfo = false })
        }
        return
    }

    SongSheetColumn {
        SongInfoRow("标题", tagInfo?.title?.ifBlank { song.title } ?: song.title)
        SongInfoRow("艺术家", tagInfo?.artist?.ifBlank { song.artist } ?: song.artist)
        SongInfoRow("专辑", tagInfo?.album?.ifBlank { song.album } ?: song.album)
        SongInfoRow("专辑艺术家", tagInfo?.albumArtist?.ifBlank { song.albumArtist }.orEmpty())
        SongInfoRow("流派", tagInfo?.genre?.ifBlank { song.genre }.orEmpty())
        SongInfoRow("年份", tagInfo?.year?.ifBlank { song.year }.orEmpty())
        SongInfoRow("作曲家", tagInfo?.composer?.ifBlank { song.composer }.orEmpty())
        SongInfoRow("作词家", tagInfo?.lyricist?.ifBlank { song.lyricist }.orEmpty())
        SongInfoRow("注释", tagInfo?.displayComment.orEmpty())
        if (!tagInfo?.neteaseKey.isNullOrBlank()) {
            SongInfoActionRow(
                label = "163 key",
                value = neteaseInfo?.musicName?.ifBlank { null }
                    ?: neteaseInfo?.musicId?.takeIf { it.isNotBlank() }?.let { "网易云歌曲 ID：$it" }
                    ?: "点击查看网易云关联信息",
                onClick = { showNeteaseKeyInfo = true }
            )
        }
        SongInfoRow("格式", audioInfo?.let { detailedAudioInfo(it) }.orEmpty())
        SongInfoRow("时长", song.durationText)
        SongInfoRow("大小", formatFileSize(song.fileSize))
        SongInfoRow("修改时间", song.dateModified.formatSongDateTime())
        SongInfoRow("添加时间", song.dateAdded.formatSongDateTime())
        SongInfoRow("文件名", song.fileName.ifBlank { song.path.substringAfterLast('/') })
        SongInfoRow("路径", song.path)
    }
}

@Composable
private fun SongAiInterpretationSheet(
    song: Song,
    mainViewModel: MainViewModel,
    onDismiss: () -> Unit
) {
    val result by produceState<Result<String>?>(initialValue = null, song.id) {
        value = runCatching { mainViewModel.interpretSongWithOpenAi(song) }
    }
    SongSheetColumn {
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
        SongMenuItem("关闭", onDismiss)
    }
}

@Composable
private fun SongSheetColumn(content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 18.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
        content = content
    )
}

@Composable
private fun SongMenuItem(
    title: String,
    onClick: () -> Unit,
    danger: Boolean = false
) {
    Text(
        text = title,
        fontSize = 15.sp,
        fontWeight = FontWeight.Medium,
        color = if (danger) Color(0xFFE5484D) else MiuixTheme.colorScheme.onSurface,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 4.dp, vertical = 14.dp)
    )
}

@Composable
private fun SongInfoRow(label: String, value: String) {
    if (value.isBlank()) return
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MiuixTheme.colorScheme.surfaceContainer.copy(alpha = 0.38f))
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Text(
            text = label,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary
        )
        Text(
            text = value,
            fontSize = 14.sp,
            lineHeight = 20.sp,
            color = MiuixTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun SongInfoActionRow(label: String, value: String, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MiuixTheme.colorScheme.primary.copy(alpha = 0.18f))
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Text(
            text = label,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = MiuixTheme.colorScheme.primary
        )
        Text(
            text = value,
            fontSize = 14.sp,
            lineHeight = 20.sp,
            color = MiuixTheme.colorScheme.onSurface
        )
    }
}

private fun openUrl(context: Context, url: String) {
    if (url.isBlank()) return
    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
}

private fun formatFileSize(bytes: Long): String {
    if (bytes <= 0L) return ""
    val kb = bytes / 1024.0
    val mb = kb / 1024.0
    val gb = mb / 1024.0
    return when {
        gb >= 1 -> String.format(Locale.ROOT, "%.2f GB", gb)
        mb >= 1 -> String.format(Locale.ROOT, "%.2f MB", mb)
        else -> String.format(Locale.ROOT, "%.0f KB", kb)
    }
}

private fun Long.formatSongDateTime(): String {
    if (this <= 0L) return ""
    val millis = if (this < 10_000_000_000L) this * 1000L else this
    return SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date(millis))
}
