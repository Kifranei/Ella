package com.ella.music.ui.settings

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.draw.clip
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.ella.music.data.AppLogEntry
import com.ella.music.data.AppLogStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.InputField
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTopAppBar
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.icon.extended.Copy
import top.yukonga.miuix.kmp.icon.extended.Delete
import top.yukonga.miuix.kmp.icon.extended.Share
import top.yukonga.miuix.kmp.preference.WindowDropdownPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.overScrollVertical
import top.yukonga.miuix.kmp.utils.scrollEndHaptic
import top.yukonga.miuix.kmp.window.WindowBottomSheet
import top.yukonga.miuix.kmp.window.WindowDialog
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun LogScreen(
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val scrollBehavior = MiuixScrollBehavior()
    var refreshKey by remember { mutableIntStateOf(0) }
    var query by remember { mutableStateOf("") }
    var selectedLevel by remember { mutableStateOf<EllaLogLevelFilter?>(null) }
    var selectedType by remember { mutableStateOf<EllaLogTypeFilter?>(null) }
    var selectedEntry by remember { mutableStateOf<AppLogEntry?>(null) }
    var showDetailSheet by remember { mutableStateOf(false) }
    var showClearDialog by remember { mutableStateOf(false) }
    var retentionDays by remember { mutableIntStateOf(AppLogStore.retentionDays(context)) }
    val retentionOptions = remember { listOf(1, 3, 7, 14, 30) }

    val entries by produceState(initialValue = emptyList<AppLogEntry>(), refreshKey) {
        value = withContext(Dispatchers.IO) { AppLogStore.read(context) }
    }

    val filteredEntries = remember(entries, selectedLevel, selectedType, query) {
        val keyword = query.trim()
        entries.filter { entry ->
            (selectedLevel == null || selectedLevel?.matches(entry) == true) &&
                (selectedType == null || selectedType?.matches(entry) == true) &&
                (keyword.isBlank() || entry.matchesKeyword(keyword))
        }
    }

    fun showToast(text: String) {
        Toast.makeText(context, text, Toast.LENGTH_SHORT).show()
    }

    fun shareLogs() {
        scope.launch {
            val file = withContext(Dispatchers.IO) { AppLogStore.exportDetailedReport(context, filteredEntries) }
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_SUBJECT, "Ella Music 运行日志")
                putExtra(Intent.EXTRA_TEXT, "Ella Music 运行日志，生成时间：${AppLogStore.formatTime(System.currentTimeMillis())}")
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                clipData = ClipData.newUri(context.contentResolver, "Ella Music 运行日志", uri)
            }
            runCatching {
                context.startActivity(Intent.createChooser(intent, "分享运行日志"))
            }.onFailure {
                showToast("没有可用的分享应用")
            }
        }
    }

    fun copyEntry(entry: AppLogEntry) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("Ella Music 日志", entry.formatForCopy()))
        showToast("日志已复制")
        showDetailSheet = false
    }

    Scaffold(
        topBar = {
            SmallTopAppBar(
                title = "日志分析",
                scrollBehavior = scrollBehavior,
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = MiuixIcons.Regular.Back,
                            contentDescription = "返回"
                        )
                    }
                },
                actions = {
                    IconButton(
                        enabled = filteredEntries.isNotEmpty(),
                        onClick = ::shareLogs
                    ) {
                        Icon(
                            imageVector = MiuixIcons.Regular.Share,
                            contentDescription = "分享日志"
                        )
                    }
                    IconButton(
                        enabled = entries.isNotEmpty(),
                        onClick = { showClearDialog = true }
                    ) {
                        Icon(
                            imageVector = MiuixIcons.Regular.Delete,
                            contentDescription = "清空日志",
                            tint = MiuixTheme.colorScheme.error
                        )
                    }
                }
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .scrollEndHaptic()
                .overScrollVertical(),
            contentPadding = PaddingValues(
                top = paddingValues.calculateTopPadding() + 8.dp,
                bottom = paddingValues.calculateBottomPadding() + 120.dp
            ),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            overscrollEffect = null
        ) {
            item("filters") {
                Card(modifier = Modifier.padding(horizontal = 12.dp)) {
                    WindowDropdownPreference(
                        title = "日志级别",
                        items = listOf("全部") + EllaLogLevelFilter.entries.map { it.label },
                        selectedIndex = selectedLevel?.let { EllaLogLevelFilter.entries.indexOf(it) + 1 } ?: 0,
                        onSelectedIndexChange = { index ->
                            selectedLevel = if (index == 0) null else EllaLogLevelFilter.entries[index - 1]
                        }
                    )
                    WindowDropdownPreference(
                        title = "日志类型",
                        items = listOf("全部") + EllaLogTypeFilter.entries.map { it.label },
                        selectedIndex = selectedType?.let { EllaLogTypeFilter.entries.indexOf(it) + 1 } ?: 0,
                        onSelectedIndexChange = { index ->
                            selectedType = if (index == 0) null else EllaLogTypeFilter.entries[index - 1]
                        }
                    )
                    WindowDropdownPreference(
                        title = "自动保留",
                        items = retentionOptions.map { "$it 天" },
                        selectedIndex = retentionOptions.indexOf(retentionDays).takeIf { it >= 0 } ?: 2,
                        onSelectedIndexChange = { index ->
                            val days = retentionOptions[index]
                            scope.launch {
                                val removed = withContext(Dispatchers.IO) { AppLogStore.setRetentionDays(context, days) }
                                retentionDays = days
                                refreshKey++
                                showToast(if (removed > 0) "已清理 $removed 条旧日志" else "已设为保留 $days 天")
                            }
                        }
                    )
                }
            }

            item("search") {
                InputField(
                    query = query,
                    onQueryChange = { query = it },
                    onSearch = {},
                    expanded = true,
                    onExpandedChange = {},
                    label = "搜索日志、Tag、错误信息",
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp)
                )
            }

            item("summary") {
                Card(modifier = Modifier.padding(horizontal = 12.dp)) {
                    BasicComponent(
                        title = "运行日志",
                        summary = "共 ${entries.size} 条，当前显示 ${filteredEntries.size} 条；错误 ${entries.count { it.level.equals("ERROR", true) }} 条，警告 ${entries.count { it.level.equals("WARNING", true) || it.level.equals("WARN", true) }} 条；自动抓取本进程 logcat、网络失败和关键播放/歌词/扫描日志，最多保留最近 $retentionDays 天和 3000 条记录"
                    )
                }
            }

            if (filteredEntries.isEmpty()) {
                item("empty") {
                    Card(modifier = Modifier.padding(horizontal = 12.dp)) {
                        BasicComponent(title = if (entries.isEmpty()) "暂无日志" else "没有匹配的日志")
                    }
                }
            } else {
                items(
                    items = filteredEntries,
                    key = { entry -> "${entry.time}-${entry.level}-${entry.tag}-${entry.message.hashCode()}" }
                ) { entry ->
                    AppLogItem(
                        entry = entry,
                        onClick = {
                            selectedEntry = entry
                            showDetailSheet = true
                        }
                    )
                }
            }
        }
    }

    AppLogDetailSheet(
        show = showDetailSheet,
        entry = selectedEntry,
        onDismiss = { showDetailSheet = false },
        onDismissFinished = {
            showDetailSheet = false
            selectedEntry = null
        },
        onCopy = ::copyEntry
    )

    WindowDialog(
        show = showClearDialog,
        title = "清空日志",
        onDismissRequest = { showClearDialog = false }
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = "将清空全部 ${entries.size} 条持久化日志，此操作不可恢复。",
                color = MiuixTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.SpaceBetween) {
                TextButton(
                    text = "取消",
                    onClick = { showClearDialog = false },
                    modifier = Modifier.weight(1f)
                )
                Spacer(modifier = Modifier.width(20.dp))
                TextButton(
                    text = "清空",
                    onClick = {
                        scope.launch {
                            withContext(Dispatchers.IO) { AppLogStore.clear(context) }
                            refreshKey++
                            showClearDialog = false
                            showToast("日志已清空")
                        }
                    },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.textButtonColorsPrimary()
                )
            }
        }
    }
}

@Composable
private fun AppLogItem(
    entry: AppLogEntry,
    onClick: () -> Unit
) {
    Card(modifier = Modifier.padding(horizontal = 12.dp)) {
        BasicComponent(
            insideMargin = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            onClick = onClick
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    SeverityBadge(entry.level)
                    Text(
                        text = entry.detectType().label,
                        fontWeight = FontWeight.Bold,
                        color = MiuixTheme.colorScheme.onSurface
                    )
                }
                Text(
                    text = formatTimeOnly(entry.time),
                    style = MiuixTheme.textStyles.body2,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    maxLines = 1
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = entry.message,
                style = MiuixTheme.textStyles.body2,
                color = MiuixTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = entry.tag,
                style = MiuixTheme.textStyles.body2,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun SeverityBadge(level: String) {
    val normalized = level.uppercase(Locale.ROOT)
    val background = when (normalized) {
        "ERROR", "CRASH" -> MiuixTheme.colorScheme.error
        "WARN", "WARNING" -> MiuixTheme.colorScheme.tertiaryContainer
        "DEBUG" -> MiuixTheme.colorScheme.secondaryContainer
        else -> MiuixTheme.colorScheme.primary
    }
    val content = when (normalized) {
        "ERROR", "CRASH" -> MiuixTheme.colorScheme.onError
        "INFO" -> MiuixTheme.colorScheme.onPrimary
        else -> MiuixTheme.colorScheme.onSurface
    }
    Text(
        text = if (normalized == "WARN") "WARNING" else normalized,
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(background)
            .padding(horizontal = 7.dp, vertical = 2.dp),
        color = content,
        fontWeight = FontWeight.Bold,
        maxLines = 1,
        fontSize = 12.sp
    )
}

@Composable
private fun AppLogDetailSheet(
    show: Boolean,
    entry: AppLogEntry?,
    onDismiss: () -> Unit,
    onDismissFinished: () -> Unit,
    onCopy: (AppLogEntry) -> Unit
) {
    WindowBottomSheet(
        show = show,
        enableNestedScroll = false,
        title = "日志详情",
        endAction = {
            entry?.let {
                IconButton(onClick = { onCopy(it) }) {
                    Icon(
                        imageVector = MiuixIcons.Regular.Copy,
                        contentDescription = "复制日志"
                    )
                }
            }
        },
        onDismissRequest = onDismiss,
        onDismissFinished = onDismissFinished
    ) {
        entry ?: return@WindowBottomSheet
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 32.dp)
                .verticalScroll(rememberScrollState())
        ) {
            DetailRow(label = "时间", value = AppLogStore.formatTime(entry.time))
            DetailRow(label = "级别", value = entry.level)
            DetailRow(label = "类型", value = entry.detectType().label)
            DetailRow(label = "Tag", value = entry.tag)
            entry.relatedId?.takeIf { it.isNotBlank() }?.let {
                DetailRow(label = "关联对象", value = it)
            }
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "消息",
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                fontWeight = FontWeight.Bold
            )
            SelectionContainer {
                Text(
                    text = entry.message,
                    modifier = Modifier.padding(top = 4.dp),
                    color = MiuixTheme.colorScheme.onSurface
                )
            }
            entry.throwable?.takeIf { it.isNotBlank() }?.let { detail ->
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "详情",
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    fontWeight = FontWeight.Bold
                )
                SelectionContainer {
                    Text(
                        text = detail,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp),
                        color = MiuixTheme.colorScheme.onSurface,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        }
    }
}

@Composable
private fun DetailRow(
    label: String,
    value: String
) {
    Column(modifier = Modifier.padding(bottom = 8.dp)) {
        Text(
            text = label,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            fontWeight = FontWeight.Bold
        )
        SelectionContainer {
            Text(
                text = value,
                color = MiuixTheme.colorScheme.onSurface,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

private enum class EllaLogLevelFilter(val label: String, private val aliases: Set<String>) {
    DEBUG("调试", setOf("DEBUG")),
    INFO("信息", setOf("INFO")),
    WARNING("警告", setOf("WARN", "WARNING")),
    ERROR("错误", setOf("ERROR")),
    CRASH("闪退", setOf("CRASH"));

    fun matches(entry: AppLogEntry): Boolean = entry.level.uppercase(Locale.ROOT) in aliases
}

private enum class EllaLogTypeFilter(val label: String, val storageNames: Set<String>, val keywords: Set<String>) {
    APP("应用", setOf("APP"), emptySet()),
    PLAYBACK("播放", setOf("PLAYBACK"), setOf("player", "playback", "play", "exo", "media", "audio", "decoder", "queue", "播放", "播放器", "解码", "队列")),
    LYRICS("歌词", setOf("LYRICS"), setOf("lyric", "lyrics", "ticker", "superlyric", "lyricon", "flyme", "samsung", "bluetooth", "词幕", "歌词")),
    LIBRARY("音乐库", setOf("LIBRARY"), setOf("scan", "scanner", "library", "folder", "album", "artist", "cover", "音乐库", "扫描", "文件夹", "专辑", "艺术家", "封面")),
    METADATA("元数据", setOf("METADATA"), setOf("tag", "metadata", "jaudiotagger", "taglib", "wav", "alac", "元数据", "标签")),
    ONLINE("在线", setOf("ONLINE"), setOf("lx", "download", "api", "在线", "下载")),
    NETWORK("网络", setOf("NETWORK"), setOf("network", "http", "okhttp", "webdav", "request", "response", "网络")),
    DATABASE("数据", setOf("DATABASE"), setOf("database", "db", "room", "dao", "backup", "restore", "playlist", "stats", "数据库", "备份", "恢复")),
    CRASH("崩溃", setOf("CRASH"), setOf("crash", "exception", "fatal", "崩溃", "闪退"));

    fun matches(entry: AppLogEntry): Boolean = entry.detectType() == this
}

private fun AppLogEntry.detectType(): EllaLogTypeFilter {
    if (level.equals("CRASH", ignoreCase = true)) return EllaLogTypeFilter.CRASH
    EllaLogTypeFilter.entries.firstOrNull { filter ->
        type.uppercase(Locale.ROOT) in filter.storageNames
    }?.let { return it }
    val haystack = "$tag $message ${throwable.orEmpty()}".lowercase(Locale.ROOT)
    return EllaLogTypeFilter.entries
        .asSequence()
        .filter { it != EllaLogTypeFilter.APP }
        .firstOrNull { type -> type.keywords.any { it.lowercase(Locale.ROOT) in haystack } }
        ?: EllaLogTypeFilter.APP
}

private fun AppLogEntry.matchesKeyword(keyword: String): Boolean {
    return level.contains(keyword, ignoreCase = true) ||
        tag.contains(keyword, ignoreCase = true) ||
        message.contains(keyword, ignoreCase = true) ||
        throwable.orEmpty().contains(keyword, ignoreCase = true) ||
        AppLogStore.formatTime(time).contains(keyword, ignoreCase = true) ||
        detectType().label.contains(keyword, ignoreCase = true)
}

private fun AppLogEntry.formatForCopy(): String = buildString {
    appendLine("${AppLogStore.formatTime(time)} [$level/${detectType().label}] $tag")
    appendLine(message)
    throwable?.takeIf { it.isNotBlank() }?.let {
        appendLine()
        appendLine(it)
    }
}

private fun formatTimeOnly(timestamp: Long): String =
    SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(timestamp))
