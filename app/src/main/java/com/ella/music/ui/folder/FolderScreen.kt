package com.ella.music.ui.folder

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ella.music.data.model.Song
import com.ella.music.data.webdav.WebDavClient
import com.ella.music.data.webdav.WebDavConfig
import com.ella.music.data.webdav.WebDavItem
import com.ella.music.viewmodel.MainViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.ella.music.viewmodel.PlayerViewModel
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.SmallTopAppBar
import top.yukonga.miuix.kmp.basic.Text
import androidx.compose.ui.window.Dialog
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.basic.ArrowRight
import top.yukonga.miuix.kmp.icon.extended.Folder
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun FolderScreen(
    mainViewModel: MainViewModel,
    playerViewModel: PlayerViewModel,
    onNavigateToPlayer: () -> Unit,
    onFolderClick: (String) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val songs by mainViewModel.songs.collectAsState()
    val savedWebDavUrl by mainViewModel.settingsManager.webDavUrl.collectAsState(initial = "")
    val savedWebDavUser by mainViewModel.settingsManager.webDavUsername.collectAsState(initial = "")
    val savedWebDavPassword by mainViewModel.settingsManager.webDavPassword.collectAsState(initial = "")
    var webDavTreeUri by remember { mutableStateOf<Uri?>(null) }
    var showWebDavDialog by remember { mutableStateOf(false) }
    var webDavUrl by remember { mutableStateOf("") }
    var webDavUser by remember { mutableStateOf("") }
    var webDavPassword by remember { mutableStateOf("") }
    var webDavCurrentUrl by remember { mutableStateOf("") }
    var webDavItems by remember { mutableStateOf<List<WebDavItem>>(emptyList()) }
    var webDavLoading by remember { mutableStateOf(false) }
    var webDavError by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(savedWebDavUrl, savedWebDavUser, savedWebDavPassword) {
        webDavUrl = savedWebDavUrl
        webDavUser = savedWebDavUser
        webDavPassword = savedWebDavPassword
        if (savedWebDavUrl.isNotBlank()) {
            webDavCurrentUrl = savedWebDavUrl
            val config = WebDavConfig(savedWebDavUrl, savedWebDavUser, savedWebDavPassword)
            webDavLoading = true
            webDavError = null
            webDavItems = withContext(Dispatchers.IO) { WebDavClient.list(config, savedWebDavUrl) }
            webDavLoading = false
        }
    }
    val webDavPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri != null) {
            val readOnly = Intent.FLAG_GRANT_READ_URI_PERMISSION
            val readWrite = readOnly or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            runCatching {
                context.contentResolver.takePersistableUriPermission(uri, readWrite)
            }.recoverCatching {
                context.contentResolver.takePersistableUriPermission(uri, readOnly)
            }
            webDavTreeUri = uri
            Toast.makeText(context, "已添加网络音乐目录", Toast.LENGTH_SHORT).show()
        }
    }

    val folderMap = remember(songs) {
        songs.groupBy { song ->
            val path = song.path
            val lastSlash = path.lastIndexOf('/')
            if (lastSlash > 0) path.substring(0, lastSlash) else "/"
        }.toSortedMap()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.statusBars)
    ) {
        SmallTopAppBar(
            title = "文件夹",
            color = MiuixTheme.colorScheme.background
        )

        WebDavEntryCard(
            selectedUri = webDavTreeUri,
            onOpenSettings = { showWebDavDialog = true },
            onOpenDocumentTree = { webDavPicker.launch(null) }
        )

        if (showWebDavDialog) {
            WebDavSettingsDialog(
                url = webDavUrl,
                username = webDavUser,
                password = webDavPassword,
                onUrlChange = { webDavUrl = it },
                onUsernameChange = { webDavUser = it },
                onPasswordChange = { webDavPassword = it },
                onDismiss = { showWebDavDialog = false },
                onTest = {
                    scope.launch {
                        val ok = withContext(Dispatchers.IO) {
                            WebDavClient.test(WebDavConfig(webDavUrl, webDavUser, webDavPassword))
                        }
                        Toast.makeText(
                            context,
                            if (ok) "WebDAV 连接成功" else "WebDAV 连接失败",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                },
                onSave = {
                    scope.launch {
                        mainViewModel.settingsManager.setWebDavConfig(webDavUrl, webDavUser, webDavPassword)
                    }
                    webDavCurrentUrl = webDavUrl
                    showWebDavDialog = false
                    Toast.makeText(context, "WebDAV 配置已保存", Toast.LENGTH_SHORT).show()
                }
            )
        }

        if (folderMap.isEmpty() && savedWebDavUrl.isBlank()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = MiuixIcons.Regular.Folder,
                        contentDescription = null,
                        tint = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        modifier = Modifier.size(48.dp)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "未找到音乐文件",
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary
                    )
                }
            }
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                if (savedWebDavUrl.isNotBlank()) {
                    item {
                        WebDavBrowserCard(
                            currentUrl = webDavCurrentUrl.ifBlank { savedWebDavUrl },
                            loading = webDavLoading,
                            error = webDavError,
                            items = webDavItems,
                            onRefresh = {
                                scope.launch {
                                    val config = WebDavConfig(webDavUrl, webDavUser, webDavPassword)
                                    webDavLoading = true
                                    webDavError = null
                                    webDavItems = withContext(Dispatchers.IO) { WebDavClient.list(config, webDavCurrentUrl.ifBlank { webDavUrl }) }
                                    webDavLoading = false
                                }
                            },
                            onItemClick = { item ->
                                if (item.isDirectory) {
                                    scope.launch {
                                        webDavCurrentUrl = item.url
                                        val config = WebDavConfig(webDavUrl, webDavUser, webDavPassword)
                                        webDavLoading = true
                                        webDavError = null
                                        webDavItems = withContext(Dispatchers.IO) { WebDavClient.list(config, item.url) }
                                        webDavLoading = false
                                    }
                                } else {
                                    val remoteSong = item.toRemoteSong()
                                    playerViewModel.setPlaylist(listOf(remoteSong), 0)
                                    onNavigateToPlayer()
                                }
                            },
                            onAddToQueue = { item ->
                                playerViewModel.addToPlaylist(item.toRemoteSong())
                                Toast.makeText(context, "已加入播放列表", Toast.LENGTH_SHORT).show()
                            }
                        )
                    }
                }
                items(
                    items = folderMap.entries.toList(),
                    key = { it.key }
                ) { (folderPath, folderSongs) ->
                    val folderName = folderPath.substringAfterLast('/')

                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 4.dp),
                        onClick = { onFolderClick(folderPath) }
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Icon(
                                imageVector = MiuixIcons.Regular.Folder,
                                contentDescription = null,
                                tint = MiuixTheme.colorScheme.primary,
                                modifier = Modifier.size(28.dp)
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = folderName.ifEmpty { "根目录" },
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Medium
                                )
                                Text(
                                    text = "${folderSongs.size} 首歌曲",
                                    fontSize = 13.sp,
                                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary
                                )
                            }
                            Icon(
                                imageVector = MiuixIcons.Basic.ArrowRight,
                                contentDescription = null,
                                tint = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun WebDavBrowserCard(
    currentUrl: String,
    loading: Boolean,
    error: String?,
    items: List<WebDavItem>,
    onRefresh: () -> Unit,
    onItemClick: (WebDavItem) -> Unit,
    onAddToQueue: (WebDavItem) -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = "WebDAV 目录", fontSize = 16.sp, fontWeight = FontWeight.Medium)
                    Text(
                        text = currentUrl,
                        fontSize = 12.sp,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary
                    )
                }
                Button(onClick = onRefresh) { Text("刷新") }
            }
            when {
                loading -> Text("正在读取远程目录...", color = MiuixTheme.colorScheme.primary)
                error != null -> Text(error, color = MiuixTheme.colorScheme.primary)
                items.isEmpty() -> Text("远程目录为空或没有可播放音频", color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
                else -> items.forEach { item ->
                    WebDavItemRow(
                        item = item,
                        onClick = { onItemClick(item) },
                        onAddToQueue = { onAddToQueue(item) }
                    )
                }
            }
        }
    }
}

@Composable
private fun WebDavItemRow(
    item: WebDavItem,
    onClick: () -> Unit,
    onAddToQueue: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MiuixTheme.colorScheme.surfaceContainer)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            modifier = Modifier
                .weight(1f)
                .clip(RoundedCornerShape(10.dp))
                .background(MiuixTheme.colorScheme.surfaceContainer)
                .padding(0.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(
                imageVector = MiuixIcons.Regular.Folder,
                contentDescription = null,
                tint = MiuixTheme.colorScheme.primary,
                modifier = Modifier.size(22.dp)
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MiuixTheme.colorScheme.surfaceContainer)
            ) {
                Text(text = item.name, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                Text(
                    text = if (item.isDirectory) "目录" else item.mimeType.ifBlank { "远程音频" },
                    fontSize = 12.sp,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary
                )
            }
            Button(onClick = onClick) { Text(if (item.isDirectory) "打开" else "播放") }
        }
        if (!item.isDirectory) {
            Button(onClick = onAddToQueue) { Text("+") }
        }
    }
}

@Composable
private fun WebDavEntryCard(
    selectedUri: Uri?,
    onOpenSettings: () -> Unit,
    onOpenDocumentTree: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp),
        onClick = onOpenSettings
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                imageVector = MiuixIcons.Regular.Folder,
                contentDescription = null,
                tint = MiuixTheme.colorScheme.primary,
                modifier = Modifier.size(28.dp)
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "WebDAV 音乐库",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = if (selectedUri != null) {
                        "已选择网络目录，后续接入索引和缓存"
                    } else {
                        "配置 WebDAV，或选择 rclone/RCX/rcloud sync 网络目录"
                    },
                    fontSize = 13.sp,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary
                )
            }
            Icon(
                imageVector = MiuixIcons.Basic.ArrowRight,
                contentDescription = null,
                tint = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                modifier = Modifier.size(20.dp)
            )
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 56.dp, end = 16.dp, bottom = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(onClick = onOpenSettings) {
                Text("手动配置")
            }
            Button(onClick = onOpenDocumentTree) {
                Text("系统目录")
            }
        }
    }
}

@Composable
private fun WebDavSettingsDialog(
    url: String,
    username: String,
    password: String,
    onUrlChange: (String) -> Unit,
    onUsernameChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onDismiss: () -> Unit,
    onTest: () -> Unit,
    onSave: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(text = "WebDAV 音乐库", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                WebDavTextField("地址", url, onUrlChange)
                WebDavTextField("用户名", username, onUsernameChange)
                WebDavTextField("密码", password, onPasswordChange)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    Button(onClick = onDismiss) { Text("取消") }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(onClick = onTest) { Text("测试") }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(onClick = onSave) { Text("保存") }
                }
            }
        }
    }
}

@Composable
private fun WebDavTextField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(text = label, fontSize = 12.sp, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            textStyle = androidx.compose.ui.text.TextStyle(
                color = MiuixTheme.colorScheme.onBackground,
                fontSize = 14.sp
            ),
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(MiuixTheme.colorScheme.surfaceContainer)
                .padding(horizontal = 12.dp, vertical = 10.dp)
        )
    }
}

private fun WebDavItem.toRemoteSong(): Song {
    val title = name.substringBeforeLast('.', name)
    return Song(
        id = -url.hashCode().toLong().coerceAtLeast(1L),
        title = title,
        artist = "WebDAV",
        album = "WebDAV",
        albumId = 0L,
        duration = 0L,
        path = url,
        fileName = name,
        fileSize = size,
        mimeType = mimeType
    )
}
