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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ella.music.data.model.Song
import com.ella.music.ui.LibrarySortUiState
import com.ella.music.data.webdav.WebDavClient
import com.ella.music.data.webdav.WebDavItem
import com.ella.music.ui.components.DoubleTapScrollOverlay
import com.ella.music.ui.components.EllaSearchBar
import com.ella.music.ui.components.FolderOutlineIcon
import com.ella.music.ui.components.ConfirmDangerDialog
import com.ella.music.ui.components.ellaPageBackground
import com.ella.music.viewmodel.MainViewModel
import kotlinx.coroutines.launch
import com.ella.music.viewmodel.PlayerViewModel
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import com.ella.music.ui.components.EllaSmallTopAppBar
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextField
import androidx.compose.ui.window.Dialog
import com.ella.music.R
import com.ella.music.data.model.albumIdentityId
import com.ella.music.data.model.formatPlaybackDuration
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.basic.ArrowRight
import top.yukonga.miuix.kmp.icon.basic.Check
import top.yukonga.miuix.kmp.icon.basic.Search
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
import java.text.SimpleDateFormat
import java.util.Date
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
    val context = LocalContext.current
    val songs by mainViewModel.songs.collectAsState()
    val isScanning by mainViewModel.isScanning.collectAsState()
    val scanProgress by mainViewModel.scanProgress.collectAsState()
    val scanExcludeFolders by mainViewModel.settingsManager.scanExcludeFolders.collectAsState(initial = "")
    val blockedFolders = remember(scanExcludeFolders) { scanExcludeFolders.toFolderSettingList() }
    val folderSortIndex by mainViewModel.settingsManager.folderListSortIndex.collectAsState(initial = LibrarySortUiState.folderListSortIndex)
    val folderSortMode = FolderListSortMode.entries.getOrElse(folderSortIndex) { FolderListSortMode.Name }
    var folderToBlock by remember { mutableStateOf<String?>(null) }
    var sortExpanded by remember { mutableStateOf(false) }
    var searchExpanded by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var scrollToTopRequest by remember { mutableStateOf(0) }

    val rootFolderPath = remember(songs) { songs.commonFolderRoot() }
    val rootSongs = remember(songs, rootFolderPath) { songs.recursiveSongsInFolder(rootFolderPath) }
    val rootChildFolders = remember(songs, rootFolderPath) { songs.childFoldersOf(rootFolderPath) }

    BackHandler(enabled = sortExpanded || searchExpanded || folderToBlock != null) {
        when {
            folderToBlock != null -> folderToBlock = null
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
                title = stringResource(R.string.tab_folder),
                color = ellaPageBackground(),
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = MiuixIcons.Regular.Back,
                            contentDescription = stringResource(R.string.common_back),
                            tint = MiuixTheme.colorScheme.onSurface,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { sortExpanded = !sortExpanded }) {
                        Icon(
                            imageVector = MiuixIcons.Regular.Sort,
                            contentDescription = stringResource(R.string.common_sort),
                            tint = MiuixTheme.colorScheme.onSurface,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    IconButton(onClick = {
                        searchExpanded = !searchExpanded
                        if (!searchExpanded) searchQuery = ""
                    }) {
                        Icon(
                            imageVector = MiuixIcons.Basic.Search,
                            contentDescription = stringResource(R.string.common_search),
                            tint = MiuixTheme.colorScheme.onSurface,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    IconButton(onClick = onNavigateToScanSettings) {
                        Icon(
                            imageVector = MiuixIcons.Regular.Settings,
                            contentDescription = stringResource(R.string.folder_scan_settings),
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
                    .height(56.dp),
                endPadding = 160.dp
            )
        }

        AnimatedVisibility(
            visible = searchExpanded,
            enter = expandVertically(),
            exit = shrinkVertically()
        ) {
            EllaSearchBar(
                query = searchQuery,
                onQueryChange = { searchQuery = it },
                onSearch = { searchExpanded = false },
                placeholder = stringResource(R.string.folder_search_placeholder),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 4.dp)
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
                            text = stringResource(mode.labelRes),
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
                            stringResource(R.string.folder_empty_blocked_hint)
                        } else {
                            stringResource(R.string.folder_empty)
                        },
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary
                    )
                }
            }
        } else {
            val folders = remember(rootChildFolders, rootSongs, rootFolderPath, folderSortMode, searchQuery) {
                val entries = buildList {
                    if (rootSongs.isNotEmpty()) {
                        add(
                            FolderTreeEntry(
                                path = rootFolderPath,
                                name = rootFolderPath.substringAfterLast('/').ifBlank { context.getString(R.string.folder_root) },
                                songCount = rootSongs.size,
                                albumCount = rootSongs.map { it.albumIdentityId() }.distinct().size,
                                duration = rootSongs.sumOf { it.duration },
                                dateModified = rootSongs.maxOfOrNull { it.dateModified } ?: 0L
                            )
                        )
                    }
                    addAll(rootChildFolders)
                }
                val query = searchQuery.trim()
                val pinnedRoot = rootFolderPath.takeIf { rootSongs.isNotEmpty() }
                entries
                    .sortedForFolderList(folderSortMode, pinnedPath = pinnedRoot)
                    .let { sorted ->
                        if (query.isBlank()) sorted else sorted.filter { folder ->
                            folder.name.contains(query, ignoreCase = true) ||
                                folder.path.contains(query, ignoreCase = true)
                        }
                    }
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
                    FolderListRow(
                        folder = folder,
                        sortMode = folderSortMode,
                        onClick = { onFolderClick(folder.path) },
                        onLongClick = { folderToBlock = folder.path }
                    )
                }
            }
        }
    }
}

@Composable
private fun FolderListRow(
    folder: FolderTreeEntry,
    sortMode: FolderListSortMode,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    val context = LocalContext.current
    Row(
        modifier = Modifier
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(MiuixTheme.colorScheme.surfaceContainer)
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
            .padding(horizontal = 18.dp, vertical = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        FolderOutlineIcon(
            tint = MiuixTheme.colorScheme.primary,
            modifier = Modifier.size(42.dp)
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = folder.name,
                fontSize = 17.sp,
                lineHeight = 22.sp,
                fontWeight = FontWeight.SemiBold,
                color = MiuixTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "${folder.summaryFor(context, sortMode)} · ${folder.path}",
                fontSize = 13.sp,
                lineHeight = 17.sp,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
        Icon(
            imageVector = MiuixIcons.Basic.ArrowRight,
            contentDescription = null,
            tint = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            modifier = Modifier.size(22.dp)
        )
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
    val blockedFolderKeys = remember(blockedFolders) {
        blockedFolders.map { it.normalizeFolderPath().lowercase(Locale.ROOT) }.toSet()
    }
    var showBlockedDialog by remember { mutableStateOf(false) }
    var pendingRemoveScanFolder by remember { mutableStateOf<String?>(null) }

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
                Toast.makeText(context, R.string.unsupported_system_folder_path, Toast.LENGTH_SHORT).show()
            } else {
                scope.launch {
                    mainViewModel.settingsManager.setUseAndroidMediaLibrary(false)
                    mainViewModel.settingsManager.setScanIncludeFolders(
                        (savedFolders + folderPath).distinct().joinToString("；")
                    )
                    mainViewModel.scanMusic()
                }
                Toast.makeText(context, R.string.scan_folder_added, Toast.LENGTH_SHORT).show()
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(ellaPageBackground())
            .windowInsetsPadding(WindowInsets.statusBars)
    ) {
        EllaSmallTopAppBar(
            title = stringResource(R.string.folder_scan_settings),
            color = ellaPageBackground(),
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = MiuixIcons.Regular.Back,
                        contentDescription = stringResource(R.string.common_back),
                        tint = MiuixTheme.colorScheme.onSurface,
                        modifier = Modifier.size(24.dp)
                    )
                }
            },
            actions = {
                IconButton(onClick = { if (!isScanning) mainViewModel.scanMusic() }) {
                    Icon(
                        imageVector = MiuixIcons.Regular.Refresh,
                        contentDescription = stringResource(R.string.folder_full_scan),
                        tint = MiuixTheme.colorScheme.onSurface,
                        modifier = Modifier.size(24.dp)
                    )
                }
                IconButton(onClick = { folderPicker.launch(null) }) {
                    Icon(
                        imageVector = MiuixIcons.Regular.Add,
                        contentDescription = stringResource(R.string.folder_add_custom_directory),
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
                    hiddenFolders = blockedFolderKeys,
                    onVisibilityChange = { folderPath, visible ->
                        scope.launch {
                            val normalizedPath = folderPath.normalizeFolderPath()
                            val nextBlockedFolders = if (visible) {
                                blockedFolders.filterNot {
                                    it.normalizeFolderPath().equals(normalizedPath, ignoreCase = true)
                                }
                            } else {
                                (blockedFolders + normalizedPath).distinctBy {
                                    it.normalizeFolderPath().lowercase(Locale.ROOT)
                                }
                            }
                            mainViewModel.settingsManager.setScanExcludeFolders(nextBlockedFolders.joinToString("；"))
                            mainViewModel.scanMusic()
                        }
                    },
                    onRemove = { folderPath ->
                        pendingRemoveScanFolder = folderPath
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

        pendingRemoveScanFolder?.let { folderPath ->
            ConfirmDangerDialog(
                show = true,
                title = stringResource(R.string.folder_remove_scan_folder_title),
                message = stringResource(R.string.folder_remove_scan_folder_message, folderPath),
                confirmText = stringResource(R.string.common_remove),
                onDismiss = { pendingRemoveScanFolder = null },
                onConfirm = {
                    scope.launch {
                        val normalizedPath = folderPath.normalizeFolderPath()
                        mainViewModel.settingsManager.setScanIncludeFolders(
                            savedFolders.filterNot {
                                it.normalizeFolderPath().equals(normalizedPath, ignoreCase = true)
                            }.joinToString("；")
                        )
                        mainViewModel.settingsManager.setScanExcludeFolders(
                            blockedFolders.filterNot {
                                it.normalizeFolderPath().equals(normalizedPath, ignoreCase = true)
                            }.joinToString("；")
                        )
                        mainViewModel.scanMusic()
                    }
                    pendingRemoveScanFolder = null
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
            text = if (scanProgress > 0) {
                stringResource(R.string.library_scanning_count, scanProgress)
            } else {
                stringResource(R.string.folder_scanning_library)
            },
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
                title = stringResource(R.string.folder_use_android_media_library),
                summary = if (useAndroidMediaLibrary) {
                    stringResource(R.string.folder_scan_android_media_summary)
                } else {
                    stringResource(R.string.folder_scan_custom_folders_summary, customFolderCount)
                },
                checked = useAndroidMediaLibrary,
                onCheckedChange = onUseAndroidMediaLibraryChange
            )
        }
    }
}

private enum class FolderListSortMode(val labelRes: Int) {
    Name(R.string.playlist_sort_name),
    SongCount(R.string.playlist_sort_song_count),
    AlbumCount(R.string.folder_sort_album_count),
    Duration(R.string.playlist_song_sort_duration),
    DateModified(R.string.playlist_song_sort_date_modified),
    DateModifiedAsc(R.string.playlist_song_sort_date_modified_asc)
}

private fun List<FolderTreeEntry>.sortedForFolderList(
    mode: FolderListSortMode,
    pinnedPath: String? = null
): List<FolderTreeEntry> {
    val sorted = when (mode) {
        FolderListSortMode.Name -> sortedBy { it.name.lowercase(Locale.ROOT) }
        FolderListSortMode.SongCount -> sortedWith(compareByDescending<FolderTreeEntry> { it.songCount }.thenBy { it.name.lowercase(Locale.ROOT) })
        FolderListSortMode.Duration -> sortedWith(compareByDescending<FolderTreeEntry> { it.duration }.thenBy { it.name.lowercase(Locale.ROOT) })
        FolderListSortMode.AlbumCount -> sortedWith(compareByDescending<FolderTreeEntry> { it.albumCount }.thenBy { it.name.lowercase(Locale.ROOT) })
        FolderListSortMode.DateModified -> sortedWith(compareByDescending<FolderTreeEntry> { it.dateModified }.thenBy { it.name.lowercase(Locale.ROOT) })
        FolderListSortMode.DateModifiedAsc -> sortedWith(compareBy<FolderTreeEntry> { it.dateModified }.thenBy { it.name.lowercase(Locale.ROOT) })
    }
    if (pinnedPath.isNullOrBlank()) return sorted
    val pinned = sorted.firstOrNull { it.path.equals(pinnedPath, ignoreCase = true) } ?: return sorted
    return listOf(pinned) + sorted.filterNot { it.path.equals(pinnedPath, ignoreCase = true) }
}

private fun FolderTreeEntry.summaryFor(context: android.content.Context, mode: FolderListSortMode): String {
    return when (mode) {
        FolderListSortMode.Duration -> duration.formatFolderDuration(context)
        FolderListSortMode.AlbumCount -> context.getString(R.string.album_count, albumCount)
        FolderListSortMode.DateModified,
        FolderListSortMode.DateModifiedAsc -> dateModified.formatFolderDateTime(context)
        else -> context.getString(R.string.song_count, songCount)
    }
}

private fun Long.formatFolderDuration(context: android.content.Context): String {
    return formatPlaybackDuration()
}

private fun Long.formatFolderDateTime(context: android.content.Context): String {
    if (this <= 0L) return context.getString(R.string.folder_unknown_modified_time)
    val millis = if (this < 10_000_000_000L) this * 1000L else this
    return SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date(millis))
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
                    text = stringResource(R.string.analytics_library_analysis),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = MiuixTheme.colorScheme.onSurface
                )
                Text(
                    text = stringResource(R.string.folder_library_analysis_summary),
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
    hiddenFolders: Set<String>,
    onVisibilityChange: (String, Boolean) -> Unit,
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
                        text = stringResource(R.string.folder_local_scan_directories),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium,
                        color = MiuixTheme.colorScheme.onSurface
                    )
                    Text(
                        text = stringResource(R.string.folder_local_scan_directories_summary, folders.size),
                        fontSize = 13.sp,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary
                    )
                }
                IconButton(onClick = onScan) {
                    Icon(
                        imageVector = MiuixIcons.Regular.Refresh,
                        contentDescription = stringResource(R.string.folder_full_scan),
                        tint = MiuixTheme.colorScheme.onSurface,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
            folders.forEach { folder ->
                val isVisible = folder.normalizeFolderPath().lowercase(Locale.ROOT) !in hiddenFolders
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    FolderVisibilityCheckbox(
                        checked = isVisible,
                        onCheckedChange = { onVisibilityChange(folder, it) }
                    )
                    FolderOutlineIcon(
                        tint = if (isVisible) {
                            MiuixTheme.colorScheme.primary
                        } else {
                            MiuixTheme.colorScheme.onSurfaceVariantSummary
                        },
                        modifier = Modifier.size(24.dp)
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = folder.substringAfterLast('/').ifBlank { folder },
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            color = if (isVisible) {
                                MiuixTheme.colorScheme.onSurface
                            } else {
                                MiuixTheme.colorScheme.onSurfaceVariantSummary
                            }
                        )
                        Text(
                            text = folder,
                            fontSize = 12.sp,
                            lineHeight = 16.sp,
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    IconButton(onClick = { onRemove(folder) }) {
                        Icon(
                            imageVector = MiuixIcons.Regular.Close,
                            contentDescription = stringResource(R.string.common_remove),
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
@OptIn(ExperimentalFoundationApi::class)
private fun FolderVisibilityCheckbox(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Box(
        modifier = Modifier
            .size(26.dp)
            .clip(RoundedCornerShape(7.dp))
            .background(
                if (checked) {
                    MiuixTheme.colorScheme.primary
                } else {
                    MiuixTheme.colorScheme.surfaceContainer
                }
            )
            .combinedClickable(onClick = { onCheckedChange(!checked) }),
        contentAlignment = Alignment.Center
    ) {
        if (checked) {
            Icon(
                imageVector = MiuixIcons.Basic.Check,
                contentDescription = stringResource(R.string.folder_show_folder),
                tint = MiuixTheme.colorScheme.onPrimary,
                modifier = Modifier.size(18.dp)
            )
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
                        text = stringResource(R.string.webdav_directory),
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
                    Button(onClick = onGoParent) { Text(stringResource(R.string.folder_parent)) }
                }
                IconButton(onClick = onRefresh) {
                    Icon(
                        imageVector = MiuixIcons.Regular.Refresh,
                        contentDescription = stringResource(R.string.library_refresh),
                        tint = MiuixTheme.colorScheme.onSurface
                    )
                }
            }
            when {
                loading -> Text(stringResource(R.string.webdav_loading_directory), color = MiuixTheme.colorScheme.primary)
                error != null -> Text(error, color = MiuixTheme.colorScheme.primary)
                items.isEmpty() -> Text(stringResource(R.string.webdav_empty_directory), color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
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
                    text = if (item.isDirectory) stringResource(R.string.webdav_item_directory) else item.mimeType.ifBlank { stringResource(R.string.webdav_remote_audio) },
                    fontSize = 12.sp,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary
                )
            }
            IconButton(onClick = onClick) {
                Icon(
                    imageVector = if (item.isDirectory) MiuixIcons.Basic.ArrowRight else MiuixIcons.Regular.Play,
                    contentDescription = if (item.isDirectory) stringResource(R.string.common_open) else stringResource(R.string.common_play),
                    tint = MiuixTheme.colorScheme.onSurface
                )
            }
        }
        if (!item.isDirectory) {
            IconButton(onClick = onAddToQueue) {
                Icon(
                    imageVector = MiuixIcons.Regular.Add,
                    contentDescription = stringResource(R.string.common_add_to_queue),
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
                    text = stringResource(R.string.folder_blocked_folders),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                    color = MiuixTheme.colorScheme.onSurface
                )
                Text(
                    text = stringResource(R.string.folder_blocked_folders_summary, count),
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
internal fun FolderBlockDialog(
    folderPath: String,
    onDismiss: () -> Unit,
    onBlock: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = stringResource(R.string.folder_block_folder),
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = MiuixTheme.colorScheme.onSurface
                )
                Text(text = folderPath, fontSize = 13.sp, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    Button(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(onClick = onBlock) { Text(stringResource(R.string.folder_block)) }
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
                    text = stringResource(R.string.folder_blocked_folders),
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
                        Button(onClick = { onRemove(folder) }) { Text(stringResource(R.string.common_remove)) }
                    }
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    Button(onClick = onClear) { Text(stringResource(R.string.common_clear)) }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(onClick = onDismiss) { Text(stringResource(R.string.common_done)) }
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
        title = stringResource(R.string.webdav_library_title),
        onDismissRequest = onDismiss
    ) {
        Column(
            modifier = Modifier.padding(bottom = 18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            WebDavTextField(stringResource(R.string.webdav_url), url, onUrlChange)
            WebDavTextField(stringResource(R.string.webdav_username), username, onUsernameChange)
            WebDavTextField(stringResource(R.string.webdav_password), password, onPasswordChange)
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
                Button(onClick = onClear) { Text(stringResource(R.string.common_remove)) }
                Spacer(modifier = Modifier.weight(1f))
                Button(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) }
                Spacer(modifier = Modifier.width(8.dp))
                Button(onClick = onTest) { Text(stringResource(R.string.common_test)) }
                Spacer(modifier = Modifier.width(8.dp))
                Button(onClick = onSave) { Text(stringResource(R.string.common_save)) }
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

internal fun String.toFolderSettingList(): List<String> =
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
