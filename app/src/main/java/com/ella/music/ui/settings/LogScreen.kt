package com.ella.music.ui.settings

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.core.content.FileProvider
import androidx.compose.foundation.background
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.ui.graphics.SolidColor
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ella.music.data.AppLogEntry
import com.ella.music.data.AppLogStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.ListPopupColumn
import top.yukonga.miuix.kmp.basic.PopupPositionProvider
import top.yukonga.miuix.kmp.basic.SmallTopAppBar
import top.yukonga.miuix.kmp.basic.SpinnerDefaults
import top.yukonga.miuix.kmp.basic.SpinnerEntry
import top.yukonga.miuix.kmp.basic.SpinnerItemImpl
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.theme.LocalDismissState
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.window.WindowListPopup

@Composable
fun LogScreen(
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val isDark = MiuixTheme.colorScheme.background.luminance() < 0.5f
    val pageBackground = if (isDark) Color(0xFF101014) else Color(0xFFF4F4F7)
    var refreshKey by remember { mutableIntStateOf(0) }
    var query by remember { mutableStateOf("") }
    var retentionMenuExpanded by remember { mutableStateOf(false) }
    var retentionDays by remember { mutableIntStateOf(AppLogStore.retentionDays(context)) }
    val retentionOptions = remember { listOf(1, 3, 7, 14, 30) }
    val retentionEntries = remember { retentionOptions.map { SpinnerEntry(title = "保留最近 $it 天") } }
    val selectedRetentionIndex = retentionOptions.indexOf(retentionDays).takeIf { it >= 0 } ?: 2
    val entries by produceState(initialValue = emptyList<AppLogEntry>(), refreshKey) {
        value = withContext(Dispatchers.IO) { AppLogStore.read(context) }
    }
    val filteredEntries = remember(entries, query) {
        val keyword = query.trim()
        if (keyword.isBlank()) {
            entries
        } else {
            entries.filter { entry ->
                entry.level.contains(keyword, ignoreCase = true) ||
                    entry.tag.contains(keyword, ignoreCase = true) ||
                    entry.message.contains(keyword, ignoreCase = true) ||
                    entry.throwable.orEmpty().contains(keyword, ignoreCase = true) ||
                    AppLogStore.formatTime(entry.time).contains(keyword, ignoreCase = true)
            }
        }
    }
    val reportText by produceState(initialValue = "正在生成详细运行日志...", entries) {
        value = withContext(Dispatchers.IO) { AppLogStore.buildDetailedReport(context, entries) }
    }

    fun showToast(text: String) {
        Toast.makeText(context, text, Toast.LENGTH_SHORT).show()
    }

    fun copyLogs() {
        scope.launch {
            val text = withContext(Dispatchers.IO) { AppLogStore.buildDetailedReport(context, entries) }
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.setPrimaryClip(ClipData.newPlainText("Ella Music 运行日志", text))
            showToast("详细日志已复制")
        }
    }

    fun shareLogs() {
        scope.launch {
            val file = withContext(Dispatchers.IO) { AppLogStore.exportDetailedReport(context, entries) }
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

    fun clearOlderLogs(days: Int) {
        retentionMenuExpanded = false
        scope.launch {
            val removed = withContext(Dispatchers.IO) { AppLogStore.setRetentionDays(context, days) }
            retentionDays = days
            refreshKey++
            showToast(if (removed > 0) "已设为保留 $days 天，并清理 $removed 条旧日志" else "已设为保留 $days 天")
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(pageBackground)
            .windowInsetsPadding(WindowInsets.statusBars)
    ) {
        SmallTopAppBar(
            title = "日志",
            color = pageBackground,
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
                RetentionDropdownAction(
                    expanded = retentionMenuExpanded,
                    selectedIndex = selectedRetentionIndex,
                    entries = retentionEntries,
                    onExpandedChange = { retentionMenuExpanded = it },
                    onSelected = { index -> clearOlderLogs(retentionOptions[index]) }
                )
            }
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp)
        ) {
            Text(
                text = "记录详细日志、警告和闪退",
                fontSize = 14.sp,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary
            )
            Text(
                text = "自动保留最近 $retentionDays 天，最多 800 条；导出时会附带当前 logcat 尾部",
                fontSize = 12.sp,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary.copy(alpha = 0.72f)
            )
            Spacer(modifier = Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Button(
                    onClick = ::copyLogs
                ) {
                    Text("复制")
                }
                Spacer(modifier = Modifier.width(8.dp))
                Button(
                    onClick = ::shareLogs
                ) {
                    Text("发送")
                }
                Spacer(modifier = Modifier.width(8.dp))
                Button(
                    onClick = {
                        scope.launch {
                            withContext(Dispatchers.IO) { AppLogStore.clear(context) }
                            refreshKey++
                        }
                    }
                ) {
                    Text("清空")
                }
            }
        }

        BasicTextField(
            value = query,
            onValueChange = { query = it },
            singleLine = true,
            textStyle = TextStyle(
                color = MiuixTheme.colorScheme.onSurface,
                fontSize = 14.sp
            ),
            cursorBrush = SolidColor(MiuixTheme.colorScheme.primary),
            decorationBox = { innerTextField ->
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp)
                        .background(MiuixTheme.colorScheme.surfaceContainer, androidx.compose.foundation.shape.RoundedCornerShape(14.dp))
                        .padding(horizontal = 14.dp, vertical = 10.dp)
                ) {
                    if (query.isBlank()) {
                        Text(
                            text = "搜索日志、Tag、错误信息",
                            fontSize = 14.sp,
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary
                        )
                    }
                    innerTextField()
                }
            }
        )

        if (filteredEntries.isEmpty()) {
            Spacer(modifier = Modifier.height(90.dp))
            Text(
                text = if (entries.isEmpty()) "暂无日志" else "没有匹配的日志",
                fontSize = 15.sp,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            )
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
            ) {
                item {
                    RuntimeSummaryCard(
                        entries = entries,
                        filteredCount = filteredEntries.size,
                        reportText = reportText,
                        isDark = isDark
                    )
                }
                items(filteredEntries) { entry ->
                    LogEntryCard(entry = entry, isDark = isDark)
                }
                item { Spacer(modifier = Modifier.height(120.dp)) }
            }
        }
    }
}

@Composable
private fun RetentionDropdownAction(
    expanded: Boolean,
    selectedIndex: Int,
    entries: List<SpinnerEntry>,
    onExpandedChange: (Boolean) -> Unit,
    onSelected: (Int) -> Unit
) {
    IconButton(onClick = { onExpandedChange(!expanded) }) {
        Text(
            text = entries.getOrNull(selectedIndex)?.title?.removePrefix("保留最近 ") ?: "7 天",
            fontSize = 13.sp,
            color = MiuixTheme.colorScheme.primary
        )
        WindowListPopup(
            show = expanded,
            alignment = PopupPositionProvider.Align.End,
            onDismissRequest = { onExpandedChange(false) },
            onDismissFinished = {}
        ) {
            val dismiss = LocalDismissState.current
            ListPopupColumn {
                entries.forEachIndexed { index, entry ->
                    key(index) {
                        SpinnerItemImpl(
                            entry = entry,
                            entryCount = entries.size,
                            isSelected = selectedIndex == index,
                            index = index,
                            spinnerColors = SpinnerDefaults.spinnerColors(),
                            onSelectedIndexChange = { selected ->
                                onSelected(selected)
                                dismiss?.invoke()
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun RuntimeSummaryCard(
    entries: List<AppLogEntry>,
    filteredCount: Int,
    reportText: String,
    isDark: Boolean
) {
    val cardColor = if (isDark) Color(0xFF1D1D21) else Color.White
    val crashCount = entries.count { it.level == "CRASH" }
    val errorCount = entries.count { it.level == "ERROR" }
    val warnCount = entries.count { it.level == "WARN" }
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 10.dp),
        cornerRadius = 14.dp,
        insideMargin = PaddingValues(14.dp),
        colors = CardDefaults.defaultColors(color = cardColor)
    ) {
        Column {
            Text(
                text = "详细运行日志",
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = MiuixTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "持久化 ${entries.size} 条，当前显示 $filteredCount 条，WARN $warnCount / ERROR $errorCount / CRASH $crashCount",
                fontSize = 12.sp,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = reportText.lineSequence().take(9).joinToString("\n"),
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary
            )
        }
    }
}

@Composable
private fun LogEntryCard(
    entry: AppLogEntry,
    isDark: Boolean
) {
    val cardColor = if (isDark) Color(0xFF1D1D21) else Color.White
    val levelColor = if (entry.level == "CRASH") Color(0xFFFF6B6B) else MiuixTheme.colorScheme.primary
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 10.dp),
        cornerRadius = 14.dp,
        insideMargin = PaddingValues(14.dp),
        colors = CardDefaults.defaultColors(color = cardColor)
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = entry.level,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = levelColor
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = entry.tag,
                    fontSize = 13.sp,
                    color = MiuixTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.weight(1f))
                Text(
                    text = AppLogStore.formatTime(entry.time),
                    fontSize = 11.sp,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = entry.message,
                fontSize = 13.sp,
                color = MiuixTheme.colorScheme.onSurface
            )
            entry.throwable?.let { stack ->
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = stack,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary
                )
            }
        }
    }
}
