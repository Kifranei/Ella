package com.ella.music.ui.online

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ella.music.data.SettingsManager
import com.ella.music.data.LxSourceConfig
import com.ella.music.data.lx.LxOnlineService
import com.ella.music.data.lx.LxOnlineSong
import com.ella.music.ui.components.SongItem
import com.ella.music.ui.components.ellaPageBackground
import com.ella.music.viewmodel.LxOnlineViewModel
import com.ella.music.viewmodel.PlayerViewModel
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.SmallTopAppBar
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun LxOnlineScreen(
    playerViewModel: PlayerViewModel,
    onBack: () -> Unit,
    onNavigateToPlayer: () -> Unit,
    state: LxOnlineViewModel = viewModel()
) {
    BackHandler(onBack = onBack)

    val context = LocalContext.current
    val settingsManager = remember { SettingsManager(context) }
    val service = remember(context) { LxOnlineService(context) }
    val scope = rememberCoroutineScope()

    val loadedSources by settingsManager.lxSources.collectAsState(initial = null)
    val sources = loadedSources.orEmpty()
    val selectedSourceId by settingsManager.selectedLxSourceId.collectAsState(initial = "")
    val selectedSource = remember(sources, selectedSourceId) {
        sources.firstOrNull { it.id == selectedSourceId } ?: sources.firstOrNull()
    }
    val openPlayerOnPlay by settingsManager.openPlayerOnPlay.collectAsState(initial = true)
    LaunchedEffect(loadedSources) {
        if (loadedSources != null && sources.isEmpty() && state.results.isEmpty()) {
            state.importExpanded = true
        }
    }

    fun showToast(text: String) {
        Toast.makeText(context, text, Toast.LENGTH_SHORT).show()
    }

    suspend fun playLazyOnlineQueue(startItem: LxOnlineSong) {
        val visible = state.results.ifEmpty { listOf(startItem) }
        val startIndex = visible.indexOfFirst { it.song.id == startItem.song.id }.coerceAtLeast(0)
        val sourceScript = selectedSource?.script.orEmpty()
        val resolved = service.resolvePlayableSong(startItem, sourceScript)
        val songs = visible.map { it.song }
        val itemById = visible.associateBy { it.song.id }
        playerViewModel.setLazyOnlinePlaylist(
            songs = songs,
            startIndex = startIndex,
            resolvedStartSong = resolved
        ) { song ->
            val target = itemById[song.id] ?: error("队列歌曲已失效")
            service.resolvePlayableSong(target, sourceScript)
        }
        state.message = "已获取 ${songs.size} 首队列歌曲，将在播放到对应歌曲时解析"
    }

    val localSourceLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            state.isBusy = true
            runCatching {
                val script = withContext(Dispatchers.IO) {
                    context.contentResolver.openInputStream(uri)?.use { input ->
                        input.bufferedReader(Charsets.UTF_8).readText()
                    }.orEmpty()
                }
                val (name, normalizedScript) = service.importSourceScript(script)
                settingsManager.setLxSource(uri.toString(), name, normalizedScript)
                state.importUrl = ""
                state.message = "已导入 $name"
                state.importExpanded = false
            }.onFailure {
                state.message = it.localizedMessage ?: "本地导入失败"
                showToast(state.message)
            }
            state.isBusy = false
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(ellaPageBackground())
            .windowInsetsPadding(WindowInsets.statusBars)
    ) {
        SmallTopAppBar(
            title = "LX Music",
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
            }
        )

        Column(modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp)) {
            Spacer(modifier = Modifier.height(8.dp))

            Card(
                modifier = Modifier.padding(vertical = 4.dp),
                onClick = { state.importExpanded = !state.importExpanded }
            ) {
                Column {
                    BasicComponent(
                        title = selectedSource?.name ?: "导入 LX 源",
                        summary = selectedSource?.url ?: "从本地 JS 文件或网络链接导入 Music API 脚本",
                    )
                    AnimatedVisibility(
                        visible = state.importExpanded,
                        enter = expandVertically(),
                        exit = shrinkVertically()
                    ) {
                        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
                            OnlineTextField(
                                value = state.importUrl,
                                onValueChange = { state.importUrl = it },
                                onSearch = {},
                                placeholder = "https://.../source.js"
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Button(
                                    enabled = !state.isBusy,
                                    onClick = {
                                        localSourceLauncher.launch(
                                            arrayOf(
                                                "text/javascript",
                                                "application/javascript",
                                                "application/x-javascript",
                                                "text/*",
                                                "application/octet-stream"
                                            )
                                        )
                                    }
                                ) {
                                    Text(text = "本地 JS")
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                Button(
                                    enabled = !state.isBusy,
                                    onClick = {
                                        scope.launch {
                                            state.isBusy = true
                                            runCatching {
                                                val (name, script) = service.importSource(state.importUrl)
                                                settingsManager.setLxSource(state.importUrl, name, script)
                                                state.importUrl = ""
                                                state.message = "已导入 $name"
                                                state.importExpanded = false
                                            }.onFailure {
                                                state.message = it.localizedMessage ?: "导入失败"
                                                showToast(state.message)
                                            }
                                            state.isBusy = false
                                        }
                                    }
                                ) {
                                    Text(text = if (state.isBusy) "导入中" else "URL 导入")
                                }
                            }
                            if (sources.isNotEmpty()) {
                                Spacer(modifier = Modifier.height(10.dp))
                                Text(
                                    text = "已导入源",
                                    fontSize = 13.sp,
                                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary
                                )
                                sources.forEach { source ->
                                    LxSourceRow(
                                        source = source,
                                        selected = source.id == selectedSource?.id,
                                        enabled = !state.isBusy,
                                        onSelect = {
                                            scope.launch {
                                                settingsManager.selectLxSource(source.id)
                                                state.clearResults("已切换到 ${source.name}")
                                            }
                                        },
                                        onRemove = {
                                            scope.launch {
                                                settingsManager.removeLxSource(source.id)
                                                state.clearResults("已移除 ${source.name}")
                                            }
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }

            OnlineTextField(
                value = state.searchQuery,
                onValueChange = { state.searchQuery = it },
                onSearch = {
                    if (state.searchQuery.isBlank()) return@OnlineTextField
                    if (selectedSource == null) {
                        showToast("请先导入或选择一个源")
                        return@OnlineTextField
                    }
                    scope.launch {
                        state.isBusy = true
                        runCatching {
                            state.results = service.search(state.searchQuery, selectedSource)
                            state.message = if (state.results.isEmpty()) "没有找到相关歌曲" else "找到 ${state.results.size} 首歌曲"
                        }.onFailure {
                            state.message = it.localizedMessage ?: "搜索失败"
                            showToast(state.message)
                        }
                        state.isBusy = false
                    }
                },
                placeholder = "搜索在线歌曲、歌手",
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
            )

            Button(
                enabled = !state.isBusy && state.searchQuery.isNotBlank() && selectedSource != null,
                onClick = {
                    scope.launch {
                        state.isBusy = true
                        runCatching {
                            state.results = service.search(state.searchQuery, selectedSource)
                            state.message = if (state.results.isEmpty()) "没有找到相关歌曲" else "找到 ${state.results.size} 首歌曲"
                        }.onFailure {
                            state.message = it.localizedMessage ?: "搜索失败"
                            showToast(state.message)
                        }
                        state.isBusy = false
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(text = "搜索")
            }

            Text(
                text = if (state.isBusy) "处理中..." else state.message,
                fontSize = 13.sp,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 6.dp)
            )

            if (state.results.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = if (selectedSource == null) "请先导入或选择一个源" else "搜索后点选歌曲即可在线播放",
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary
                    )
                }
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(state.results, key = { it.song.id }) { item ->
                        SongItem(
                            song = item.song,
                            albumArtUri = item.coverUrl.takeIf { it.isNotBlank() }?.let(Uri::parse),
                            onClick = {
                                scope.launch {
                                    state.isBusy = true
                                    runCatching {
                                        playLazyOnlineQueue(item)
                                        if (openPlayerOnPlay) onNavigateToPlayer()
                                    }.onFailure {
                                        state.message = it.localizedMessage ?: "播放失败"
                                        showToast(state.message)
                                    }
                                    state.isBusy = false
                                }
                            },
                            onAddToQueue = {
                                scope.launch {
                                    state.isBusy = true
                                    runCatching {
                                        val playable = service.resolvePlayableSong(item, selectedSource?.script.orEmpty())
                                        playerViewModel.addToPlaylist(playable)
                                        showToast("已加入到播放队列")
                                    }.onFailure {
                                        state.message = it.localizedMessage ?: "加入队列失败"
                                        showToast(state.message)
                                    }
                                    state.isBusy = false
                                }
                            },
                            onDownload = {
                                scope.launch {
                                    state.isBusy = true
                                    runCatching {
                                        val playable = service.resolvePlayableSong(item, selectedSource?.script.orEmpty())
                                        enqueueDownload(context, playable)
                                        showToast("已开始下载到 Music/Ella")
                                    }.onFailure {
                                        state.message = it.localizedMessage ?: "下载失败"
                                        showToast(state.message)
                                    }
                                    state.isBusy = false
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun LxSourceRow(
    source: LxSourceConfig,
    selected: Boolean,
    enabled: Boolean,
    onSelect: () -> Unit,
    onRemove: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = if (selected) "${source.name}（当前）" else source.name,
                fontSize = 14.sp,
                color = MiuixTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = source.url,
                fontSize = 12.sp,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        Button(
            enabled = enabled && !selected,
            onClick = onSelect
        ) {
            Text("使用")
        }
        Spacer(modifier = Modifier.width(8.dp))
        Button(
            enabled = enabled,
            onClick = onRemove
        ) {
            Text("移除")
        }
    }
}

private fun enqueueDownload(context: Context, song: com.ella.music.data.model.Song) {
    val fileName = song.fileName.ifBlank { "${song.title}-${song.artist}.mp3" }.sanitizeFileName()
    val request = DownloadManager.Request(Uri.parse(song.path))
        .setTitle(fileName)
        .setDescription("${song.title} - ${song.artist}")
        .setMimeType(song.mimeType.ifBlank { "audio/*" })
        .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
        .setDestinationInExternalPublicDir(Environment.DIRECTORY_MUSIC, "Ella/$fileName")
        .setAllowedOverMetered(true)
        .setAllowedOverRoaming(true)
    val manager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
    manager.enqueue(request)
}

private fun String.sanitizeFileName(): String {
    return replace(Regex("""[\\/:*?"<>|]"""), "_")
        .replace(Regex("""\s+"""), " ")
        .trim()
        .ifBlank { "Ella Music.mp3" }
}
