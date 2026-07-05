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
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ella.music.ui.LibrarySortUiState
import com.ella.music.ui.settings.findComponentActivity
import androidx.lifecycle.lifecycleScope
import com.ella.music.ui.components.DoubleTapScrollOverlay
import com.ella.music.ui.components.DirectionalSortField
import com.ella.music.ui.components.EllaSearchBar
import com.ella.music.ui.components.EllaCenteredLoadingIndicator
import com.ella.music.ui.components.LazyListScrollIndicator
import com.ella.music.ui.components.SortDropdownItem
import com.ella.music.ui.components.SortDropdownMenu
import com.ella.music.ui.components.directionalSortDropdownItems
import com.ella.music.ui.components.ellaPageBackground
import com.ella.music.ui.components.wallpaperContentOverlayColor
import com.ella.music.ui.listmodel.SortDirection
import com.ella.music.viewmodel.MainViewModel
import kotlinx.coroutines.launch
import com.ella.music.viewmodel.PlayerViewModel
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import com.ella.music.ui.components.EllaSmallTopAppBar
import top.yukonga.miuix.kmp.basic.Text
import com.ella.music.R
import com.ella.music.data.model.albumIdentityId
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.basic.Search
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.icon.extended.Folder
import top.yukonga.miuix.kmp.icon.extended.Settings
import top.yukonga.miuix.kmp.icon.extended.Sort
import top.yukonga.miuix.kmp.theme.MiuixTheme
import java.util.Locale

@Composable
fun FolderScreen(
    mainViewModel: MainViewModel,
    playerViewModel: PlayerViewModel,
    showBackButton: Boolean = true,
    onBack: () -> Unit,
    onNavigateToPlayer: () -> Unit,
    onNavigateToLibraryAnalysis: () -> Unit,
    onNavigateToScanSettings: () -> Unit,
    onFolderClick: (String) -> Unit
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val saveScope = context.findComponentActivity()?.lifecycleScope ?: scope
    val songs by mainViewModel.songs.collectAsState()
    val libraryCacheLoaded by mainViewModel.libraryCacheLoaded.collectAsState()
    val isScanning by mainViewModel.isScanning.collectAsState()
    val scanProgress by mainViewModel.scanProgress.collectAsState()
    val scanExcludeFolders by mainViewModel.settingsManager.scanExcludeFolders.collectAsState(initial = "")
    val blockedFolders = remember(scanExcludeFolders) { scanExcludeFolders.toFolderSettingList() }
    val folderSortIndex by mainViewModel.settingsManager.folderListSortIndex.collectAsState(initial = LibrarySortUiState.folderListSortIndex)
    val folderSortMode = FolderListSortMode.entries.getOrElse(folderSortIndex) { FolderListSortMode.Name }
    LaunchedEffect(folderSortIndex) {
        LibrarySortUiState.folderListSortIndex = folderSortIndex
    }
    var folderToBlock by remember { mutableStateOf<String?>(null) }
    var sortExpanded by remember { mutableStateOf(false) }
    var searchExpanded by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var scrollToTopRequest by remember { mutableStateOf(0) }

    val rootFolderPath = remember(songs) { songs.commonFolderRoot() }
    val rootSongs = songs
    val rootChildFolders = remember(songs, rootFolderPath) { songs.childFoldersOf(context, rootFolderPath) }

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

    val pageBackground = ellaPageBackground()
    val overlayColor = wallpaperContentOverlayColor()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(pageBackground)
    ) {
        if (overlayColor.alpha > 0f) {
            Box(modifier = Modifier.fillMaxSize().background(overlayColor))
        }
        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.statusBars)
        ) {
        Box {
            EllaSmallTopAppBar(
                title = stringResource(R.string.tab_folder),
                color = ellaPageBackground(),
                navigationIcon = {
                    if (showBackButton) {
                        IconButton(onClick = onBack) {
                            Icon(
                                imageVector = MiuixIcons.Regular.Back,
                                contentDescription = stringResource(R.string.common_back),
                                tint = MiuixTheme.colorScheme.onSurface,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                },
                actions = {
                    IconButton(onClick = onNavigateToScanSettings) {
                        Icon(
                            imageVector = MiuixIcons.Regular.Settings,
                            contentDescription = stringResource(R.string.folder_scan_settings),
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
                    SortDropdownMenu(
                        items = directionalSortDropdownItems(
                            fields = FolderListSortField.entries.map { field ->
                                DirectionalSortField(
                                    field = field,
                                    text = stringResource(field.labelRes),
                                    defaultDirection = when (field) {
                                        FolderListSortField.Name -> SortDirection.Ascending
                                        FolderListSortField.DateModified,
                                        FolderListSortField.SongCount,
                                        FolderListSortField.AlbumCount,
                                        FolderListSortField.Duration -> SortDirection.Descending
                                    },
                                    supportsAscending = field == FolderListSortField.Name || field == FolderListSortField.DateModified,
                                    supportsDescending = true
                                )
                            },
                            selectedField = folderSortMode.sortField(),
                            selectedDirection = if (folderSortMode.isDescending()) SortDirection.Descending else SortDirection.Ascending,
                            ascendingSummary = stringResource(R.string.common_sort_ascending),
                            descendingSummary = stringResource(R.string.common_sort_descending)
                        ) { field, direction ->
                            val mode = field.toMode(direction == SortDirection.Descending)
                            LibrarySortUiState.folderListSortIndex = mode.ordinal
                            saveScope.launch { mainViewModel.settingsManager.setFolderListSortIndex(mode.ordinal) }
                        }
                    )
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
                                    saveScope.launch { mainViewModel.settingsManager.setFolderListSortIndex(mode.ordinal) }
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

        if (songs.isEmpty() && !libraryCacheLoaded) {
            EllaCenteredLoadingIndicator()
        } else if (songs.isEmpty()) {
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
            Box(modifier = Modifier.fillMaxSize()) {
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
                if (folders.size > 30) {
                    LazyListScrollIndicator(
                        state = listState,
                        modifier = Modifier
                            .align(Alignment.CenterEnd)
                            .fillMaxHeight()
                    )
                }
            }
        }
        }
    }
}
