package com.ella.music.ui.folder

import android.content.Intent
import android.provider.DocumentsContract
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ella.music.data.model.Song
import com.ella.music.ui.LibrarySortUiState
import com.ella.music.data.webdav.WebDavClient
import com.ella.music.data.webdav.WebDavItem
import com.ella.music.ui.components.DoubleTapScrollOverlay
import com.ella.music.ui.components.ellaPageBackground
import com.ella.music.viewmodel.MainViewModel
import kotlinx.coroutines.launch
import com.ella.music.viewmodel.PlayerViewModel
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.SmallTopAppBar
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextField
import androidx.compose.ui.window.Dialog
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.basic.ArrowRight
import top.yukonga.miuix.kmp.icon.extended.Add
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.icon.extended.Close
import top.yukonga.miuix.kmp.icon.extended.Folder
import top.yukonga.miuix.kmp.icon.extended.Play
import top.yukonga.miuix.kmp.icon.extended.Refresh
import top.yukonga.miuix.kmp.icon.extended.Settings
import top.yukonga.miuix.kmp.icon.extended.Sort
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.window.WindowBottomSheet
import java.util.Locale

@Composable
@OptIn(ExperimentalFoundationApi::class)
fun FolderScreen(
    mainViewModel: MainViewModel,
    playerViewModel: PlayerViewModel,
    onBack: () -> Unit,
    onNavigateToPlayer: () -> Unit,
    onNavigateToLibraryAnalysis: () -> Unit,
    onNavigateToScanSettings: () -> Unit,
    onFolderClick: (String) -> Unit
) {
    val scope = rememberCoroutineScope()
    val songs by mainViewModel.songs.collectAsState()
    val isScanning by mainViewModel.isScanning.collectAsState()
    val scanProgress by mainViewModel.scanProgress.collectAsState()
    val scanExcludeFolders by mainViewModel.settingsManager.scanExcludeFolders.collectAsState(initial = "")
    val blockedFolders = remember(scanExcludeFolders) { scanExcludeFolders.toFolderSettingList() }
    val folderSortIndex by mainViewModel.settingsManager.folderListSortIndex.collectAsState(initial = LibrarySortUiState.folderListSortIndex)
    val folderSortMode = FolderListSortMode.entries.getOrElse(folderSortIndex) { FolderListSortMode.Name }
    var folderToBlock by remember { mutableStateOf<String?>(null) }
    var sortExpanded by remember { mutableStateOf(false) }
    var scrollToTopRequest by remember { mutableStateOf(0) }

    val rootFolderPath = remember(songs) { songs.commonFolderRoot() }
    val rootDirectSongs = remember(songs, rootFolderPath) { songs.directSongsInFolder(rootFolderPath) }
    val rootChildFolders = remember(songs, rootFolderPath) { songs.childFoldersOf(rootFolderPath) }

    BackHandler(enabled = sortExpanded || folderToBlock != null) {
        when {
            folderToBlock != null -> folderToBlock = null
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
            SmallTopAppBar(
                title = "文件夹",
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
                    IconButton(
                        onClick = {
                            if (!isScanning) mainViewModel.scanMusic()
                        }
                    ) {
                        Icon(
                            imageVector = MiuixIcons.Regular.Refresh,
                            contentDescription = "全量扫描",
                            tint = MiuixTheme.colorScheme.onSurface,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    IconButton(onClick = onNavigateToScanSettings) {
                        Icon(
                            imageVector = MiuixIcons.Regular.Settings,
                            contentDescription = "扫描设置",
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
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp)
            ) {
                FolderListSortMode.entries.forEach { mode ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .combinedClickable(
                                onClick = {
                                    LibrarySortUiState.folderListSortIndex = mode.ordinal
                                    scope.launch { mainViewModel.settingsManager.setFolderListSortIndex(mode.ordinal) }
                                    scrollToTopRequest++
                                    sortExpanded = false
                                }
                            )
                            .padding(vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = mode.label,
                            fontSize = 14.sp,
                            fontWeight = if (folderSortMode == mode) FontWeight.Bold else FontWeight.Normal,
                            color = if (folderSortMode == mode) MiuixTheme.colorScheme.primary else MiuixTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }

        if (isScanning) {
            ScanStatusCard(scanProgress = scanProgress)
        }

        LibraryAnalysisEntryCard(onClick = onNavigateToLibraryAnalysis)

        folderToBlock?.let { folderPath ->
            FolderBlockDialog(
                folderPath = folderPath,
                onDismiss = { folderToBlock = null },
                onBlock = {
                    scope.launch {
                        mainViewModel.settingsManager.setScanExcludeFolders(
                            (blockedFolders + folderPath).distinct().joinToString("；")
                        )
                        mainViewModel.scanMusic()
                    }
                    folderToBlock = null
                }
            )
        }

        if (songs.isEmpty()) {
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
                        text = if (blockedFolders.isNotEmpty()) {
                            "未找到音乐文件，可能已被屏蔽规则排除"
                        } else {
                            "未找到音乐文件"
                        },
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary
                    )
                }
            }
        } else {
            val folders = remember(rootChildFolders, rootDirectSongs, rootFolderPath, folderSortMode) {
                val entries = buildList {
                    if (rootDirectSongs.isNotEmpty()) {
                        add(
                            FolderTreeEntry(
                                path = rootFolderPath,
                                name = rootFolderPath.substringAfterLast('/').ifBlank { "根目录" },
                                songCount = rootDirectSongs.size,
                                duration = rootDirectSongs.sumOf { it.duration }
                            )
                        )
                    }
                    addAll(rootChildFolders)
                }
                entries.sortedForFolderList(folderSortMode)
            }
            val listState = rememberLazyListState()
            LaunchedEffect(scrollToTopRequest) {
                if (scrollToTopRequest > 0) listState.animateScrollToItem(0)
            }
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 160.dp)
            ) {
                items(
                    items = folders,
                    key = { it.path }
                ) { folder ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 4.dp)
                            .combinedClickable(
                                onClick = { onFolderClick(folder.path) },
                                onLongClick = { folderToBlock = folder.path }
                            )
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
                                    text = folder.name,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = MiuixTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "${folder.songCount} 首歌曲 · ${folder.duration.formatFolderDuration()}",
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
fun ScanSettingsScreen(
    mainViewModel: MainViewModel,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val isScanning by mainViewModel.isScanning.collectAsState()
    val scanProgress by mainViewModel.scanProgress.collectAsState()
    val scanIncludeFolders by mainViewModel.settingsManager.scanIncludeFolders.collectAsState(initial = "")
    val scanExcludeFolders by mainViewModel.settingsManager.scanExcludeFolders.collectAsState(initial = "")
    val useAndroidMediaLibrary by mainViewModel.settingsManager.useAndroidMediaLibrary.collectAsState(initial = true)
    val savedFolders = remember(scanIncludeFolders) { scanIncludeFolders.toFolderSettingList() }
    val blockedFolders = remember(scanExcludeFolders) { scanExcludeFolders.toFolderSettingList() }
    var showBlockedDialog by remember { mutableStateOf(false) }

    val folderPicker = rememberLauncherForActivityResult(
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
            val folderPath = uri.toPrimaryStoragePath()
            if (folderPath == null) {
                Toast.makeText(context, "暂不支持该系统目录路径", Toast.LENGTH_SHORT).show()
            } else {
                scope.launch {
                    mainViewModel.settingsManager.setUseAndroidMediaLibrary(false)
                    mainViewModel.settingsManager.setScanIncludeFolders(
                        (savedFolders + folderPath).distinct().joinToString("；")
                    )
                    mainViewModel.scanMusic()
                }
                Toast.makeText(context, "已添加扫描文件夹", Toast.LENGTH_SHORT).show()
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
            title = "扫描设置",
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
                IconButton(onClick = { if (!isScanning) mainViewModel.scanMusic() }) {
                    Icon(
                        imageVector = MiuixIcons.Regular.Refresh,
                        contentDescription = "全量扫描",
                        tint = MiuixTheme.colorScheme.onSurface,
                        modifier = Modifier.size(24.dp)
                    )
                }
                IconButton(onClick = { folderPicker.launch(null) }) {
                    Icon(
                        imageVector = MiuixIcons.Regular.Add,
                        contentDescription = "添加自定义目录",
                        tint = MiuixTheme.colorScheme.onSurface,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        )

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 120.dp)
        ) {
            if (isScanning) {
                item { ScanStatusCard(scanProgress = scanProgress) }
            }

            item {
                MediaSourceModeCard(
                    useAndroidMediaLibrary = useAndroidMediaLibrary,
                    customFolderCount = savedFolders.size,
                    onUseAndroidMediaLibraryChange = { enabled ->
                        scope.launch {
                            mainViewModel.settingsManager.setUseAndroidMediaLibrary(enabled)
                            mainViewModel.scanMusic()
                        }
                    }
                )
            }

            item {
                SavedScanFoldersCard(
                    folders = savedFolders,
                    onRemove = { folderPath ->
                        scope.launch {
                            mainViewModel.settingsManager.setScanIncludeFolders(
                                savedFolders.filterNot { it == folderPath }.joinToString("；")
                            )
                            mainViewModel.scanMusic()
                        }
                    },
                    onScan = {
                        if (!isScanning) mainViewModel.scanMusic()
                    }
                )
            }

            item {
                BlockedFoldersEntryCard(
                    count = blockedFolders.size,
                    onClick = { showBlockedDialog = true }
                )
            }
        }

        if (showBlockedDialog) {
            BlockedFoldersDialog(
                folders = blockedFolders,
                onDismiss = { showBlockedDialog = false },
                onRemove = { folderPath ->
                    scope.launch {
                        mainViewModel.settingsManager.setScanExcludeFolders(
                            blockedFolders.filterNot { it == folderPath }.joinToString("；")
                        )
                        mainViewModel.scanMusic()
                    }
                },
                onClear = {
                    scope.launch {
                        mainViewModel.settingsManager.setScanExcludeFolders("")
                        mainViewModel.scanMusic()
                    }
                    showBlockedDialog = false
                }
            )
        }
    }
}

@Composable
private fun ScanStatusCard(scanProgress: Int) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp)
    ) {
        Text(
            text = if (scanProgress > 0) "正在扫描 ${scanProgress} 首歌曲..." else "正在扫描音乐库...",
            fontSize = 14.sp,
            color = MiuixTheme.colorScheme.primary,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
        )
    }
}

@Composable
private fun MediaSourceModeCard(
    useAndroidMediaLibrary: Boolean,
    customFolderCount: Int,
    onUseAndroidMediaLibraryChange: (Boolean) -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp)
    ) {
        Column(modifier = Modifier.padding(vertical = 4.dp)) {
            SwitchPreference(
                title = "使用 Android 媒体库",
                summary = if (useAndroidMediaLibrary) {
                    "扫描系统媒体库中的所有音乐"
                } else {
                    "仅扫描已添加的 $customFolderCount 个自定义文件夹"
                },
                checked = useAndroidMediaLibrary,
                onCheckedChange = onUseAndroidMediaLibraryChange
            )
        }
    }
}

private enum class FolderListSortMode(val label: String) {
    Name("文件夹名"),
    SongCount("歌曲数"),
    Duration("歌曲时长")
}

private fun List<FolderTreeEntry>.sortedForFolderList(
    mode: FolderListSortMode
): List<FolderTreeEntry> {
    return when (mode) {
        FolderListSortMode.Name -> sortedBy { it.name.lowercase(Locale.ROOT) }
        FolderListSortMode.SongCount -> sortedByDescending { it.songCount }
        FolderListSortMode.Duration -> sortedByDescending { it.duration }
    }
}

private fun Long.formatFolderDuration(): String {
    val totalMinutes = this / 60_000L
    val hours = totalMinutes / 60L
    val minutes = totalMinutes % 60L
    return if (hours > 0) "${hours}小时${minutes}分钟" else "${minutes}分钟"
}

@Composable
private fun LibraryAnalysisEntryCard(onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp),
        onClick = onClick
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "歌曲库分析",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = MiuixTheme.colorScheme.onSurface
                )
                Text(
                    text = "总览、音频格式和音质统计",
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

@Composable
private fun SavedScanFoldersCard(
    folders: List<String>,
    onRemove: (String) -> Unit,
    onScan: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "本地扫描目录",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium,
                        color = MiuixTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "${folders.size} 个目录，右上角加号可继续添加",
                        fontSize = 13.sp,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary
                    )
                }
                IconButton(onClick = onScan) {
                    Icon(
                        imageVector = MiuixIcons.Regular.Refresh,
                        contentDescription = "全量扫描",
                        tint = MiuixTheme.colorScheme.onSurface,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
            folders.forEach { folder ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        imageVector = MiuixIcons.Regular.Folder,
                        contentDescription = null,
                        tint = MiuixTheme.colorScheme.primary,
                        modifier = Modifier.size(22.dp)
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = folder.substringAfterLast('/').ifBlank { folder },
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            color = MiuixTheme.colorScheme.onSurface
                        )
                        Text(
                            text = folder,
                            fontSize = 12.sp,
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary
                        )
                    }
                    IconButton(onClick = { onRemove(folder) }) {
                        Icon(
                            imageVector = MiuixIcons.Regular.Close,
                            contentDescription = "移除",
                            tint = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
internal fun WebDavBrowserCard(
    currentUrl: String,
    canGoParent: Boolean,
    loading: Boolean,
    error: String?,
    items: List<WebDavItem>,
    onRefresh: () -> Unit,
    onGoParent: () -> Unit,
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
                    Text(
                        text = "WebDAV 目录",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium,
                        color = MiuixTheme.colorScheme.onSurface
                    )
                    Text(
                        text = currentUrl,
                        fontSize = 12.sp,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary
                    )
                }
                if (canGoParent) {
                    Button(onClick = onGoParent) { Text("上级") }
                }
                IconButton(onClick = onRefresh) {
                    Icon(
                        imageVector = MiuixIcons.Regular.Refresh,
                        contentDescription = "刷新",
                        tint = MiuixTheme.colorScheme.onSurface
                    )
                }
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
internal fun WebDavItemRow(
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
                Text(
                    text = item.name,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = MiuixTheme.colorScheme.onSurface
                )
                Text(
                    text = if (item.isDirectory) "目录" else item.mimeType.ifBlank { "远程音频" },
                    fontSize = 12.sp,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary
                )
            }
            IconButton(onClick = onClick) {
                Icon(
                    imageVector = if (item.isDirectory) MiuixIcons.Basic.ArrowRight else MiuixIcons.Regular.Play,
                    contentDescription = if (item.isDirectory) "打开" else "播放",
                    tint = MiuixTheme.colorScheme.onSurface
                )
            }
        }
        if (!item.isDirectory) {
            IconButton(onClick = onAddToQueue) {
                Icon(
                    imageVector = MiuixIcons.Regular.Add,
                    contentDescription = "加入播放列表",
                    tint = MiuixTheme.colorScheme.onSurface
                )
            }
        }
    }
}

@Composable
private fun BlockedFoldersEntryCard(
    count: Int,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp),
        onClick = onClick
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
                    text = "已屏蔽文件夹",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                    color = MiuixTheme.colorScheme.onSurface
                )
                Text(
                    text = "$count 个文件夹已被排除，点击管理或取消屏蔽",
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

@Composable
private fun FolderBlockDialog(
    folderPath: String,
    onDismiss: () -> Unit,
    onBlock: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = "屏蔽文件夹",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = MiuixTheme.colorScheme.onSurface
                )
                Text(text = folderPath, fontSize = 13.sp, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    Button(onClick = onDismiss) { Text("取消") }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(onClick = onBlock) { Text("屏蔽") }
                }
            }
        }
    }
}

@Composable
private fun BlockedFoldersDialog(
    folders: List<String>,
    onDismiss: () -> Unit,
    onRemove: (String) -> Unit,
    onClear: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = "已屏蔽的文件夹",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = MiuixTheme.colorScheme.onSurface
                )
                folders.forEach { folder ->
                    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = folder,
                            fontSize = 13.sp,
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                            modifier = Modifier.weight(1f)
                        )
                        Button(onClick = { onRemove(folder) }) { Text("移除") }
                    }
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    Button(onClick = onClear) { Text("清空") }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(onClick = onDismiss) { Text("完成") }
                }
            }
        }
    }
}

@Composable
internal fun WebDavSettingsDialog(
    url: String,
    username: String,
    password: String,
    onUrlChange: (String) -> Unit,
    onUsernameChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    testStatus: String?,
    onDismiss: () -> Unit,
    onTest: () -> Unit,
    onSave: () -> Unit,
    onClear: () -> Unit
) {
    WindowBottomSheet(
        show = true,
        title = "WebDAV 音乐库",
        onDismissRequest = onDismiss
    ) {
        Column(
            modifier = Modifier.padding(bottom = 18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            WebDavTextField("地址", url, onUrlChange)
            WebDavTextField("用户名", username, onUsernameChange)
            WebDavTextField("密码", password, onPasswordChange)
            if (!testStatus.isNullOrBlank()) {
                Text(
                    text = testStatus,
                    fontSize = 13.sp,
                    color = MiuixTheme.colorScheme.primary
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                Button(onClick = onClear) { Text("移除") }
                Spacer(modifier = Modifier.weight(1f))
                Button(onClick = onDismiss) { Text("取消") }
                Spacer(modifier = Modifier.width(8.dp))
                Button(onClick = onTest) { Text("测试") }
                Spacer(modifier = Modifier.width(8.dp))
                Button(onClick = onSave) { Text("保存") }
            }
        }
    }
}

@Composable
internal fun WebDavTextField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit
) {
    TextField(
        value = value,
        onValueChange = onValueChange,
        label = label,
        useLabelAsPlaceholder = true,
        singleLine = true,
        insideMargin = DpSize(12.dp, 10.dp),
        backgroundColor = MiuixTheme.colorScheme.surfaceContainer,
        cornerRadius = 12.dp,
        textStyle = TextStyle(
            color = MiuixTheme.colorScheme.onBackground,
            fontSize = 14.sp
        ),
        modifier = Modifier.fillMaxWidth()
    )
}

internal fun WebDavItem.toRemoteSong(): Song {
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

private fun String.toFolderSettingList(): List<String> =
    split('；', ';')
        .map { it.trim() }
        .filter { it.isNotBlank() }

private fun Uri.toPrimaryStoragePath(): String? {
    val documentId = runCatching { DocumentsContract.getTreeDocumentId(this) }.getOrNull() ?: return null
    val parts = documentId.split(':', limit = 2)
    val volume = parts.firstOrNull().orEmpty()
    val path = parts.getOrNull(1).orEmpty().trim('/')
    return when {
        volume.equals("primary", ignoreCase = true) && path.isBlank() -> "/storage/emulated/0"
        volume.equals("primary", ignoreCase = true) -> "/storage/emulated/0/$path"
        else -> null
    }
}
