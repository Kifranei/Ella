package com.ella.music.ui.analytics

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ella.music.R
import com.ella.music.data.PlaybackHistoryEntry
import com.ella.music.data.SongPlaybackStats
import com.ella.music.data.audioQualitySummary
import com.ella.music.data.model.AudioInfo
import com.ella.music.data.model.Song
import com.ella.music.ui.components.DefaultAlbumCover
import com.ella.music.ui.components.ellaPageBackground
import com.ella.music.viewmodel.MainViewModel
import com.ella.music.viewmodel.PlayerViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.basic.ArrowRight
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.theme.MiuixTheme
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@Composable
fun AnalyticsScreen(
    mainViewModel: MainViewModel,
    onBack: () -> Unit,
    onNavigateToHistory: () -> Unit = {}
) {
    val context = LocalContext.current
    val songs by mainViewModel.songs.collectAsState()
    val playbackStats by mainViewModel.playbackStats.collectAsState()
    val playbackHistory by mainViewModel.playbackHistory.collectAsState()
    val dailyListenMs by mainViewModel.dailyListenMs.collectAsState()
    val libraryById = remember(songs) { songs.associateBy { it.id } }

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
                    contentDescription = stringResource(R.string.common_back),
                    tint = MiuixTheme.colorScheme.onBackground,
                    modifier = Modifier.size(24.dp)
                )
            }
            Text(
                text = stringResource(R.string.analytics_title),
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
                HistoryCard(
                    history = playbackHistory.take(20),
                    totalCount = playbackHistory.size,
                    libraryById = libraryById,
                    mainViewModel = mainViewModel,
                    onClick = onNavigateToHistory
                )
            }

            item {
                ListenHeatmapCard(dailyListenMs = dailyListenMs)
            }

            item {
                RankingCard(
                    title = stringResource(R.string.analytics_listen_duration_ranking),
                    emptyText = stringResource(R.string.analytics_listen_duration_empty),
                    stats = playbackStats
                        .filter { it.listenedMs > 0L }
                        .sortedByDescending { it.listenedMs }
                        .take(10),
                    libraryById = libraryById,
                    mainViewModel = mainViewModel,
                    valueText = { formatListenDuration(context, it.listenedMs) }
                )
            }

            item {
                RankingCard(
                    title = stringResource(R.string.analytics_play_count_ranking),
                    emptyText = stringResource(R.string.analytics_play_count_empty),
                    stats = playbackStats
                        .filter { it.playCount > 0 }
                        .sortedByDescending { it.playCount }
                        .take(10),
                    libraryById = libraryById,
                    mainViewModel = mainViewModel,
                    valueText = { context.getString(R.string.analytics_times_count, it.playCount) }
                )
            }
        }
    }
}

@Composable
fun PlaybackHistoryScreen(
    mainViewModel: MainViewModel,
    playerViewModel: PlayerViewModel,
    onBack: () -> Unit,
    onNavigateToAlbum: (Long) -> Unit = {},
    onNavigateToArtist: (String) -> Unit = {}
) {
    ListeningCalendarHistoryScreen(
        mainViewModel = mainViewModel,
        playerViewModel = playerViewModel,
        onBack = onBack,
        onNavigateToAlbum = onNavigateToAlbum,
        onNavigateToArtist = onNavigateToArtist
    )
}

@Composable
fun LibraryAnalysisScreen(
    mainViewModel: MainViewModel,
    onBack: () -> Unit
) {
    val songs by mainViewModel.songs.collectAsState()
    val playbackStats by mainViewModel.playbackStats.collectAsState()
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
                    contentDescription = stringResource(R.string.common_back),
                    tint = MiuixTheme.colorScheme.onBackground,
                    modifier = Modifier.size(24.dp)
                )
            }
            Text(
                text = stringResource(R.string.analytics_library_analysis),
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
                DonutChartCard(
                    title = stringResource(R.string.analytics_audio_format_stats),
                    loadingText = stringResource(R.string.analytics_loading_audio_formats),
                    buckets = analysis?.formatBuckets,
                    total = analysis?.totalCount ?: 0,
                    totalSizeBytes = analysis?.totalSizeBytes ?: 0L,
                    palette = formatPalette
                )
            }

            item {
                DonutChartCard(
                    title = stringResource(R.string.analytics_audio_quality_stats),
                    loadingText = stringResource(R.string.analytics_loading_audio_quality),
                    buckets = analysis?.qualityBuckets,
                    total = analysis?.totalCount ?: 0,
                    totalSizeBytes = analysis?.totalSizeBytes ?: 0L,
                    palette = qualityPalette
                )
            }
        }
    }
}

@Composable
private fun ListenHeatmapCard(dailyListenMs: Map<String, Long>) {
    val context = LocalContext.current
    val days = remember(dailyListenMs) { recentDateKeys(56) }
    val maxMs = days.maxOfOrNull { dailyListenMs[it] ?: 0L }?.coerceAtLeast(1L) ?: 1L
    val todayListenMs = days.lastOrNull()?.let { dailyListenMs[it] } ?: 0L
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = stringResource(R.string.analytics_heatmap_title),
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = MiuixTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.analytics_recent_8_weeks),
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
                    text = stringResource(R.string.analytics_heatmap_low),
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
                    text = stringResource(R.string.analytics_heatmap_high),
                    fontSize = 11.sp,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary
                )
                Spacer(modifier = Modifier.weight(1f))
                Text(
                    text = stringResource(R.string.analytics_heatmap_today, formatListenDuration(context, todayListenMs)),
                    fontSize = 11.sp,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary
                )
            }
        }
    }
}

@Composable
private fun HistoryCard(
    history: List<PlaybackHistoryEntry>,
    totalCount: Int,
    libraryById: Map<Long, Song>,
    mainViewModel: MainViewModel,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.analytics_listening_history_title),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = MiuixTheme.colorScheme.onSurface
                    )
                    if (totalCount > history.size) {
                        Text(
                            text = stringResource(R.string.analytics_view_all_records, totalCount),
                            fontSize = 12.sp,
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary
                        )
                    }
                }
                Icon(
                    imageVector = MiuixIcons.Basic.ArrowRight,
                    contentDescription = null,
                    tint = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(modifier = Modifier.height(10.dp))
            if (history.isEmpty()) {
                Text(
                    text = stringResource(R.string.analytics_history_empty),
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary
                )
            } else {
                history.forEach { entry ->
                    HistoryRow(
                        entry = entry,
                        song = libraryById[entry.songId],
                        mainViewModel = mainViewModel
                    )
                }
            }
        }
    }
}

@Composable
private fun HistoryRow(
    entry: PlaybackHistoryEntry,
    song: Song?,
    mainViewModel: MainViewModel,
    timeText: String = formatHistoryTime(entry.playedAt)
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AnalyticsSongCover(
            song = song,
            mainViewModel = mainViewModel,
            modifier = Modifier.size(42.dp)
        )
        Spacer(modifier = Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = song?.title ?: entry.title,
                fontSize = 14.sp,
                color = MiuixTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = song?.artist ?: entry.artist,
                fontSize = 12.sp,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Text(
            text = timeText,
            fontSize = 12.sp,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            textAlign = TextAlign.End,
            modifier = Modifier.widthIn(min = 72.dp)
        )
    }
}

@Composable
private fun DateChip(
    dateKey: String,
    count: Int,
    selected: Boolean,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        modifier = Modifier.widthIn(min = 88.dp)
    ) {
        Column(
            modifier = Modifier
                .background(
                    if (selected) MiuixTheme.colorScheme.primary.copy(alpha = 0.16f)
                    else Color.Transparent
                )
                .padding(horizontal = 14.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = formatHistoryDateChip(dateKey),
                fontSize = 13.sp,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                color = if (selected) MiuixTheme.colorScheme.primary else MiuixTheme.colorScheme.onSurface
            )
            Text(
                text = stringResource(R.string.analytics_times_count, count),
                fontSize = 11.sp,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary
            )
        }
    }
}

@Composable
private fun SummaryCard(
    songs: List<Song>,
    playbackStats: List<SongPlaybackStats>
) {
    val context = LocalContext.current
    val listenedMs = playbackStats.sumOf { it.listenedMs }
    val playCount = playbackStats.sumOf { it.playCount }
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = stringResource(R.string.analytics_overview),
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = MiuixTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(10.dp))
            StatLine(stringResource(R.string.analytics_library_song_count), stringResource(R.string.analytics_song_count_value, songs.size))
            StatLine(stringResource(R.string.analytics_library_size), formatFileSize(songs.sumOf { it.fileSize }))
            StatLine(stringResource(R.string.analytics_total_plays), stringResource(R.string.analytics_times_count, playCount))
            StatLine(stringResource(R.string.analytics_total_listen), formatListenDuration(context, listenedMs))
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
                    text = stringResource(R.string.analytics_no_songs),
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
                    StatLine(
                        stringResource(R.string.analytics_all_label),
                        stringResource(R.string.analytics_all_summary, total, formatFileSize(totalSizeBytes))
                    )
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
            text = stringResource(
                R.string.analytics_bucket_summary,
                bucket.count,
                formatPercent(percent),
                formatFileSize(bucket.sizeBytes)
            ),
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
    libraryById: Map<Long, Song>,
    mainViewModel: MainViewModel,
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
                    RankingRow(
                        index = index + 1,
                        stat = stat,
                        value = valueText(stat),
                        song = libraryById[stat.songId],
                        mainViewModel = mainViewModel
                    )
                }
            }
        }
    }
}

@Composable
private fun RankingRow(
    index: Int,
    stat: SongPlaybackStats,
    value: String,
    song: Song?,
    mainViewModel: MainViewModel
) {
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
        AnalyticsSongCover(
            song = song,
            mainViewModel = mainViewModel,
            modifier = Modifier.size(42.dp)
        )
        Spacer(modifier = Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = song?.title ?: stat.title,
                fontSize = 14.sp,
                color = MiuixTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = song?.artist ?: stat.artist,
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
private fun AnalyticsSongCover(
    song: Song?,
    mainViewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    val coverBitmap by produceState<Bitmap?>(initialValue = null, song?.id, song?.dateModified, song?.fileSize) {
        value = withContext(Dispatchers.IO) {
            song?.let(mainViewModel::getCoverArtBitmap)
        }
    }
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(MiuixTheme.colorScheme.surfaceContainer),
        contentAlignment = Alignment.Center
    ) {
        if (coverBitmap != null && !coverBitmap!!.isRecycled) {
            Image(
                bitmap = coverBitmap!!.asImageBitmap(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            DefaultAlbumCover(modifier = Modifier.fillMaxSize())
        }
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
    return when {
        label == "未知" && !song.mimeType.contains("audio", ignoreCase = true) -> "OTHER"
        label == "未知" -> "UNKNOWN"
        label == "无损" -> "LOSSLESS"
        else -> label
    }
}

private fun Song.fileExtension(): String {
    val source = fileName.ifBlank { path.substringAfterLast('/') }
    return source.substringAfterLast('.', missingDelimiterValue = "").lowercase()
}

private fun formatListenDuration(context: Context, ms: Long): String {
    val totalMinutes = (ms / 60_000).coerceAtLeast(0)
    val hours = totalMinutes / 60
    val minutes = totalMinutes % 60
    return when {
        hours > 0 -> context.getString(R.string.analytics_duration_hours_minutes, hours, minutes)
        minutes > 0 -> context.getString(R.string.analytics_duration_minutes, minutes)
        else -> context.getString(R.string.analytics_duration_less_than_minute)
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

private fun historyDateKey(timestampMs: Long): String {
    if (timestampMs <= 0L) return ""
    return SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date(timestampMs))
}

private fun formatHistoryClock(timestampMs: Long): String {
    if (timestampMs <= 0L) return ""
    return SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(timestampMs))
}

private fun formatHistoryDateChip(dateKey: String): String {
    val date = parseHistoryDateKey(dateKey) ?: return dateKey
    val then = Calendar.getInstance().apply { time = date }
    return "%02d-%02d".format(
        then.get(Calendar.MONTH) + 1,
        then.get(Calendar.DAY_OF_MONTH)
    )
}

private fun formatHistoryDateTitle(dateKey: String): String {
    val date = parseHistoryDateKey(dateKey) ?: return dateKey
    val today = Calendar.getInstance()
    val then = Calendar.getInstance().apply { time = date }
    val label = when {
        today.get(Calendar.YEAR) == then.get(Calendar.YEAR) &&
            today.get(Calendar.DAY_OF_YEAR) == then.get(Calendar.DAY_OF_YEAR) -> "TODAY"
        today.apply { add(Calendar.DAY_OF_YEAR, -1) }.let {
            it.get(Calendar.YEAR) == then.get(Calendar.YEAR) &&
                it.get(Calendar.DAY_OF_YEAR) == then.get(Calendar.DAY_OF_YEAR)
        } -> "YESTERDAY"
        else -> SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(date)
    }
    val week = SimpleDateFormat("EEEE", Locale.getDefault()).format(date)
    return "$label · $week"
}

private fun parseHistoryDateKey(dateKey: String): Date? {
    return runCatching {
        SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).parse(dateKey)
    }.getOrNull()
}

private val qualityOrder = listOf("Dolby", "MQ", "Hi-Res", "LOSSLESS", "HQ", "LQ", "UNKNOWN", "OTHER")

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
