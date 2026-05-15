package com.ella.music.ui.analytics

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ella.music.data.PlaybackHistoryEntry
import com.ella.music.data.SongPlaybackStats
import com.ella.music.data.audioQualitySummary
import com.ella.music.data.model.AudioInfo
import com.ella.music.data.model.Song
import com.ella.music.ui.components.ellaPageBackground
import com.ella.music.viewmodel.MainViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.theme.MiuixTheme
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

@Composable
fun AnalyticsScreen(
    mainViewModel: MainViewModel,
    onBack: () -> Unit
) {
    val songs by mainViewModel.songs.collectAsState()
    val playbackStats by mainViewModel.playbackStats.collectAsState()
    val playbackHistory by mainViewModel.playbackHistory.collectAsState()
    val dailyListenMs by mainViewModel.dailyListenMs.collectAsState()
    val analysis by produceState<LibraryAnalysis?>(initialValue = null, songs) {
        value = if (songs.isEmpty()) LibraryAnalysis(emptyList(), emptyList(), 0, 0L)
        else withContext(Dispatchers.IO) { buildLibraryAnalysis(songs, mainViewModel) }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(ellaPageBackground())
            .windowInsetsPadding(WindowInsets.statusBars)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = MiuixIcons.Regular.Back,
                    contentDescription = "返回",
                    tint = MiuixTheme.colorScheme.onBackground,
                    modifier = Modifier.size(24.dp)
                )
            }
            Text(
                text = "歌曲库分析",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = MiuixTheme.colorScheme.onBackground,
                modifier = Modifier.padding(start = 8.dp)
            )
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 12.dp,
                end = 12.dp,
                top = 8.dp,
                bottom = 32.dp
            ),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                SummaryCard(
                    songs = songs,
                    playbackStats = playbackStats
                )
            }

            item {
                ListenHeatmapCard(dailyListenMs = dailyListenMs)
            }

            item {
                HistoryCard(history = playbackHistory.take(20))
            }

            item {
                DonutChartCard(
                    title = "音频格式统计",
                    loadingText = "正在分析音频格式...",
                    buckets = analysis?.formatBuckets,
                    total = analysis?.totalCount ?: 0,
                    totalSizeBytes = analysis?.totalSizeBytes ?: 0L,
                    palette = formatPalette
                )
            }

            item {
                DonutChartCard(
                    title = "音频音质统计",
                    loadingText = "正在分析音频参数...",
                    buckets = analysis?.qualityBuckets,
                    total = analysis?.totalCount ?: 0,
                    totalSizeBytes = analysis?.totalSizeBytes ?: 0L,
                    palette = qualityPalette
                )
            }

            item {
                RankingCard(
                    title = "听歌时长排行",
                    emptyText = "开始播放后会自动累计听歌时长",
                    stats = playbackStats
                        .filter { it.listenedMs > 0L }
                        .sortedByDescending { it.listenedMs }
                        .take(10),
                    valueText = { formatListenDuration(it.listenedMs) }
                )
            }

            item {
                RankingCard(
                    title = "播放次数排行",
                    emptyText = "开始播放后会自动累计播放次数",
                    stats = playbackStats
                        .filter { it.playCount > 0 }
                        .sortedByDescending { it.playCount }
                        .take(10),
                    valueText = { "${it.playCount} 次" }
                )
            }
        }
    }
}

@Composable
private fun ListenHeatmapCard(dailyListenMs: Map<String, Long>) {
    val days = remember(dailyListenMs) { recentDateKeys(56) }
    val maxMs = days.maxOfOrNull { dailyListenMs[it] ?: 0L }?.coerceAtLeast(1L) ?: 1L
    val todayListenMs = days.lastOrNull()?.let { dailyListenMs[it] } ?: 0L
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "听歌排行热力图",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = MiuixTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "近 8 周每日听歌时长",
                fontSize = 12.sp,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary
            )
            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                days.chunked(7).forEach { week ->
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        week.forEach { date ->
                            val listenedMs = dailyListenMs[date] ?: 0L
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(13.dp)
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(heatmapColor(listenedMs, maxMs))
                            )
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "少",
                    fontSize = 11.sp,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary
                )
                Spacer(modifier = Modifier.width(6.dp))
                listOf(0.16f, 0.36f, 0.58f, 0.82f).forEach { level ->
                    Box(
                        modifier = Modifier
                            .size(13.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(heatmapColor((maxMs * level).toLong(), maxMs))
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                }
                Text(
                    text = "多",
                    fontSize = 11.sp,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary
                )
                Spacer(modifier = Modifier.weight(1f))
                Text(
                    text = "今天 ${formatListenDuration(todayListenMs)}",
                    fontSize = 11.sp,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary
                )
            }
        }
    }
}

@Composable
private fun HistoryCard(history: List<PlaybackHistoryEntry>) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "听歌历史记录",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = MiuixTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(10.dp))
            if (history.isEmpty()) {
                Text(
                    text = "播放歌曲后会记录最近听过的内容",
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary
                )
            } else {
                history.forEach { entry ->
                    HistoryRow(entry = entry)
                }
            }
        }
    }
}

@Composable
private fun HistoryRow(entry: PlaybackHistoryEntry) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = entry.title,
                fontSize = 14.sp,
                color = MiuixTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = entry.artist,
                fontSize = 12.sp,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Text(
            text = formatHistoryTime(entry.playedAt),
            fontSize = 12.sp,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            textAlign = TextAlign.End,
            modifier = Modifier.widthIn(min = 72.dp)
        )
    }
}

@Composable
private fun SummaryCard(
    songs: List<Song>,
    playbackStats: List<SongPlaybackStats>
) {
    val listenedMs = playbackStats.sumOf { it.listenedMs }
    val playCount = playbackStats.sumOf { it.playCount }
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "总览",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = MiuixTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(10.dp))
            StatLine("曲库歌曲", "${songs.size} 首")
            StatLine("曲库体积", formatFileSize(songs.sumOf { it.fileSize }))
            StatLine("累计播放", "$playCount 次")
            StatLine("累计听歌", formatListenDuration(listenedMs))
        }
    }
}

@Composable
private fun DonutChartCard(
    title: String,
    loadingText: String,
    buckets: List<AnalysisBucket>?,
    total: Int,
    totalSizeBytes: Long,
    palette: List<Color>
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = title,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = MiuixTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(12.dp))
            when {
                buckets == null -> Text(
                    text = loadingText,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary
                )
                total == 0 -> Text(
                    text = "暂无歌曲",
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary
                )
                else -> {
                    DonutChart(
                        buckets = buckets,
                        total = total,
                        colors = palette,
                        modifier = Modifier
                            .align(Alignment.CenterHorizontally)
                            .size(220.dp)
                    )
                    Spacer(modifier = Modifier.height(14.dp))
                    buckets.forEachIndexed { index, bucket ->
                        BucketLegendRow(
                            bucket = bucket,
                            total = total,
                            color = palette[index % palette.size]
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    StatLine("ALL", "$total 首 · ${formatFileSize(totalSizeBytes)}")
                }
            }
        }
    }
}

@Composable
private fun DonutChart(
    buckets: List<AnalysisBucket>,
    total: Int,
    colors: List<Color>,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        val strokeWidth = 34.dp.toPx()
        val diameter = size.minDimension - strokeWidth
        val topLeft = androidx.compose.ui.geometry.Offset(
            x = (size.width - diameter) / 2f,
            y = (size.height - diameter) / 2f
        )
        val chartSize = androidx.compose.ui.geometry.Size(diameter, diameter)

        drawArc(
            color = Color.White.copy(alpha = 0.10f),
            startAngle = -90f,
            sweepAngle = 360f,
            useCenter = false,
            topLeft = topLeft,
            size = chartSize,
            style = Stroke(width = strokeWidth, cap = StrokeCap.Butt)
        )

        var startAngle = -90f
        buckets.forEachIndexed { index, bucket ->
            val sweep = (bucket.count.toFloat() / total.toFloat()) * 360f
            drawArc(
                color = colors[index % colors.size],
                startAngle = startAngle,
                sweepAngle = sweep,
                useCenter = false,
                topLeft = topLeft,
                size = chartSize,
                style = Stroke(width = strokeWidth, cap = StrokeCap.Butt)
            )
            startAngle += sweep
        }
    }
}

@Composable
private fun BucketLegendRow(
    bucket: AnalysisBucket,
    total: Int,
    color: Color
) {
    val percent = if (total > 0) bucket.count * 100f / total.toFloat() else 0f
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .clip(CircleShape)
                .background(color)
        )
        Spacer(modifier = Modifier.width(10.dp))
        Text(
            text = bucket.label,
            fontSize = 13.sp,
            color = MiuixTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = "${bucket.count} 首 · ${formatPercent(percent)} · ${formatFileSize(bucket.sizeBytes)}",
            fontSize = 12.sp,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary
        )
    }
}

@Composable
private fun RankingCard(
    title: String,
    emptyText: String,
    stats: List<SongPlaybackStats>,
    valueText: (SongPlaybackStats) -> String
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = title,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = MiuixTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(10.dp))
            if (stats.isEmpty()) {
                Text(
                    text = emptyText,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary
                )
            } else {
                stats.forEachIndexed { index, stat ->
                    RankingRow(index = index + 1, stat = stat, value = valueText(stat))
                }
            }
        }
    }
}

@Composable
private fun RankingRow(index: Int, stat: SongPlaybackStats, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = index.toString(),
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            color = MiuixTheme.colorScheme.primary,
            modifier = Modifier.width(28.dp)
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stat.title,
                fontSize = 14.sp,
                color = MiuixTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = stat.artist,
                fontSize = 12.sp,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Text(
            text = value,
            fontSize = 12.sp,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary
        )
    }
}

@Composable
private fun StatLine(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = value,
            fontWeight = FontWeight.Medium,
            color = MiuixTheme.colorScheme.onSurface
        )
    }
}

private data class LibraryAnalysis(
    val formatBuckets: List<AnalysisBucket>,
    val qualityBuckets: List<AnalysisBucket>,
    val totalCount: Int,
    val totalSizeBytes: Long
)

private data class AnalysisBucket(
    val label: String,
    val count: Int,
    val sizeBytes: Long
)

private data class SongWithInfo(
    val song: Song,
    val info: AudioInfo
)

private fun buildLibraryAnalysis(
    songs: List<Song>,
    mainViewModel: MainViewModel
): LibraryAnalysis {
    val rows = songs.map { song -> SongWithInfo(song, mainViewModel.getAudioInfo(song)) }
    return LibraryAnalysis(
        formatBuckets = rows.toBuckets { formatLabel(it.song, it.info) },
        qualityBuckets = rows.toBuckets { qualityLabel(it.song, it.info) }
            .sortedWith(compareBy<AnalysisBucket> { qualityOrder.indexOf(it.label).let { index -> if (index < 0) Int.MAX_VALUE else index } }
                .thenByDescending { it.count }),
        totalCount = songs.size,
        totalSizeBytes = songs.sumOf { it.fileSize }
    )
}

private fun List<SongWithInfo>.toBuckets(labelOf: (SongWithInfo) -> String): List<AnalysisBucket> {
    return groupBy(labelOf)
        .map { (label, rows) ->
            AnalysisBucket(
                label = label,
                count = rows.size,
                sizeBytes = rows.sumOf { it.song.fileSize }
            )
        }
        .sortedByDescending { it.count }
}

private fun formatLabel(song: Song, info: AudioInfo): String {
    val extension = song.fileExtension()
    return when {
        extension == "mp3" -> "MP3"
        extension == "m4a" || extension == "mp4" -> "M4A"
        extension == "flac" -> "FLAC"
        extension == "wav" || extension == "wave" -> "WAV"
        extension == "ogg" -> "OGG"
        extension == "opus" -> "OPUS"
        extension == "aac" -> "AAC"
        info.format.equals("ALAC/M4A", ignoreCase = true) -> "M4A"
        info.format.isNotBlank() -> info.format.uppercase()
        else -> "OTHER"
    }
}

private fun qualityLabel(song: Song, info: AudioInfo): String {
    val label = audioQualitySummary(info).analyticsLabel
    return if (label == "未知" && !song.mimeType.contains("audio", ignoreCase = true)) "其他" else label
}

private fun Song.fileExtension(): String {
    val source = fileName.ifBlank { path.substringAfterLast('/') }
    return source.substringAfterLast('.', missingDelimiterValue = "").lowercase()
}

private fun formatListenDuration(ms: Long): String {
    val totalMinutes = (ms / 60_000).coerceAtLeast(0)
    val hours = totalMinutes / 60
    val minutes = totalMinutes % 60
    return when {
        hours > 0 -> "${hours}小时${minutes}分"
        minutes > 0 -> "${minutes}分钟"
        else -> "不足 1 分钟"
    }
}

private fun formatFileSize(bytes: Long): String {
    if (bytes <= 0L) return "0 B"
    val mb = bytes / 1024.0 / 1024.0
    return if (mb >= 1024.0) {
        "%.1f GB".format(mb / 1024.0)
    } else {
        "%.1f MB".format(mb)
    }
}

private fun formatPercent(percent: Float): String {
    return if (percent < 1f && percent > 0f) "<1%" else "${percent.toInt()}%"
}

private fun recentDateKeys(days: Int): List<String> {
    val calendar = Calendar.getInstance()
    calendar.add(Calendar.DAY_OF_YEAR, -(days - 1))
    return List(days) {
        val key = "%04d-%02d-%02d".format(
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH) + 1,
            calendar.get(Calendar.DAY_OF_MONTH)
        )
        calendar.add(Calendar.DAY_OF_YEAR, 1)
        key
    }
}

private fun heatmapColor(listenedMs: Long, maxMs: Long): Color {
    if (listenedMs <= 0L) return Color(0x1F8E8E8E)
    val level = (listenedMs.toFloat() / maxMs.toFloat()).coerceIn(0.12f, 1f)
    return Color(
        red = 0.18f + 0.05f * level,
        green = 0.48f + 0.34f * level,
        blue = 0.84f - 0.36f * level,
        alpha = 0.40f + 0.56f * level
    )
}

private fun formatHistoryTime(timestampMs: Long): String {
    if (timestampMs <= 0L) return ""
    val now = Calendar.getInstance()
    val then = Calendar.getInstance().apply { timeInMillis = timestampMs }
    val sameYear = now.get(Calendar.YEAR) == then.get(Calendar.YEAR)
    val sameDay = sameYear &&
        now.get(Calendar.DAY_OF_YEAR) == then.get(Calendar.DAY_OF_YEAR)
    val pattern = when {
        sameDay -> "HH:mm"
        sameYear -> "MM-dd HH:mm"
        else -> "yyyy-MM-dd"
    }
    return SimpleDateFormat(pattern, Locale.getDefault()).format(then.time)
}

private val qualityOrder = listOf("Dolby", "MQ", "Hi-Res", "无损", "HQ", "LQ", "未知", "其他")

private val formatPalette = listOf(
    Color(0xFF4C6F9F),
    Color(0xFFA9B98D),
    Color(0xFFD9B99E),
    Color(0xFFC13561),
    Color(0xFFB97784),
    Color(0xFF6C89C8),
    Color(0xFF8E8E8E)
)

private val qualityPalette = listOf(
    Color(0xFF2FD8FF),
    Color(0xFFE95D38),
    Color(0xFFFFA21A),
    Color(0xFF9A3AC7),
    Color(0xFF2E6BFF),
    Color(0xFF17B55E),
    Color(0xFF8E8E8E),
    Color(0xFFB0A08F)
)
