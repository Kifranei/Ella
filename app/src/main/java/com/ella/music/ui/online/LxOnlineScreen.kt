package com.ella.music.ui.online

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ella.music.data.SettingsManager
import com.ella.music.data.lx.LxOnlineService
import com.ella.music.data.lx.LxOnlineSong
import com.ella.music.ui.components.SongItem
import com.ella.music.viewmodel.PlayerViewModel
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.InputField
import top.yukonga.miuix.kmp.basic.SearchBar
import top.yukonga.miuix.kmp.basic.SmallTopAppBar
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun LxOnlineScreen(
    playerViewModel: PlayerViewModel,
    onBack: () -> Unit,
    onNavigateToPlayer: () -> Unit
) {
    val context = LocalContext.current
    val settingsManager = remember { SettingsManager(context) }
    val service = remember { LxOnlineService() }
    val scope = rememberCoroutineScope()

    val sourceName by settingsManager.lxSourceName.collectAsState(initial = "")
    val sourceUrl by settingsManager.lxSourceUrl.collectAsState(initial = "")
    val sourceScript by settingsManager.lxSourceScript.collectAsState(initial = "")
    var importUrl by remember(sourceUrl) { mutableStateOf(sourceUrl) }
    var searchQuery by remember { mutableStateOf("") }
    var importExpanded by remember { mutableStateOf(sourceName.isBlank()) }
    var isBusy by remember { mutableStateOf(false) }
    var results by remember { mutableStateOf<List<LxOnlineSong>>(emptyList()) }
    var message by remember { mutableStateOf("导入落雪源后可搜索在线歌曲") }

    fun showToast(text: String) {
        Toast.makeText(context, text, Toast.LENGTH_SHORT).show()
    }

    val localSourceLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            isBusy = true
            runCatching {
                val script = withContext(Dispatchers.IO) {
                    context.contentResolver.openInputStream(uri)?.use { input ->
                        input.bufferedReader(Charsets.UTF_8).readText()
                    }.orEmpty()
                }
                val (name, normalizedScript) = service.importSourceScript(script)
                settingsManager.setLxSource(uri.toString(), name, normalizedScript)
                importUrl = uri.toString()
                message = "已导入 $name"
                importExpanded = false
            }.onFailure {
                message = it.localizedMessage ?: "本地导入失败"
                showToast(message)
            }
            isBusy = false
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.statusBars)
    ) {
        SmallTopAppBar(
            title = "落雪源在线音乐",
            color = MiuixTheme.colorScheme.background,
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = MiuixIcons.Regular.Back,
                        contentDescription = "返回",
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        )

        Column(modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp)) {
            Spacer(modifier = Modifier.height(8.dp))

            Card(
                modifier = Modifier.padding(vertical = 4.dp),
                onClick = { importExpanded = !importExpanded }
            ) {
                Column {
                    BasicComponent(
                        title = if (sourceName.isBlank()) "导入落雪源" else sourceName,
                        summary = if (sourceUrl.isBlank()) "从本地 JS 文件或网络链接导入 Music API 脚本" else sourceUrl,
                    )
                    AnimatedVisibility(
                        visible = importExpanded,
                        enter = expandVertically(),
                        exit = shrinkVertically()
                    ) {
                        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
                            InputField(
                                query = importUrl,
                                onQueryChange = { importUrl = it },
                                onSearch = {},
                                expanded = true,
                                onExpandedChange = {},
                                label = "https://.../source.js"
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Button(
                                    enabled = !isBusy,
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
                                    enabled = !isBusy,
                                    onClick = {
                                        scope.launch {
                                            isBusy = true
                                            runCatching {
                                                val (name, script) = service.importSource(importUrl)
                                                settingsManager.setLxSource(importUrl, name, script)
                                                message = "已导入 $name"
                                                importExpanded = false
                                            }.onFailure {
                                                message = it.localizedMessage ?: "导入失败"
                                                showToast(message)
                                            }
                                            isBusy = false
                                        }
                                    }
                                ) {
                                    Text(text = if (isBusy) "导入中" else "URL 导入")
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                Button(
                                    enabled = sourceName.isNotBlank() && !isBusy,
                                    onClick = {
                                        scope.launch {
                                            settingsManager.clearLxSource()
                                            importUrl = ""
                                            message = "已移除落雪源"
                                        }
                                    }
                                ) {
                                    Text(text = "移除")
                                }
                            }
                        }
                    }
                }
            }

            SearchBar(
                inputField = {
                    InputField(
                        query = searchQuery,
                        onQueryChange = { searchQuery = it },
                        onSearch = {
                            if (searchQuery.isBlank()) return@InputField
                            scope.launch {
                                isBusy = true
                                runCatching {
                                    results = service.search(searchQuery)
                                    message = if (results.isEmpty()) "没有找到相关歌曲" else "找到 ${results.size} 首歌曲"
                                }.onFailure {
                                    message = it.localizedMessage ?: "搜索失败"
                                    showToast(message)
                                }
                                isBusy = false
                            }
                        },
                        expanded = true,
                        onExpandedChange = {},
                        label = "搜索在线歌曲、歌手"
                    )
                },
                expanded = true,
                onExpandedChange = {},
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
            ) {}

            Button(
                enabled = !isBusy && searchQuery.isNotBlank(),
                onClick = {
                    scope.launch {
                        isBusy = true
                        runCatching {
                            results = service.search(searchQuery)
                            message = if (results.isEmpty()) "没有找到相关歌曲" else "找到 ${results.size} 首歌曲"
                        }.onFailure {
                            message = it.localizedMessage ?: "搜索失败"
                            showToast(message)
                        }
                        isBusy = false
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(text = "搜索")
            }

            Text(
                text = if (isBusy) "处理中..." else message,
                fontSize = 13.sp,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 6.dp)
            )

            if (results.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = "搜索后点选歌曲即可在线播放",
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary
                    )
                }
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(results, key = { it.song.id }) { item ->
                        SongItem(
                            song = item.song,
                            albumArtUri = item.coverUrl.takeIf { it.isNotBlank() }?.let(Uri::parse),
                            onClick = {
                                scope.launch {
                                    isBusy = true
                                    runCatching {
                                        val playable = service.resolvePlayableSong(item, sourceScript)
                                        playerViewModel.setPlaylist(listOf(playable), 0)
                                        onNavigateToPlayer()
                                    }.onFailure {
                                        message = it.localizedMessage ?: "播放失败"
                                        showToast(message)
                                    }
                                    isBusy = false
                                }
                            },
                            onAddToQueue = {
                                scope.launch {
                                    isBusy = true
                                    runCatching {
                                        playerViewModel.addToPlaylist(service.resolvePlayableSong(item, sourceScript))
                                        showToast("已加入播放队列")
                                    }.onFailure {
                                        message = it.localizedMessage ?: "加入队列失败"
                                        showToast(message)
                                    }
                                    isBusy = false
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}
