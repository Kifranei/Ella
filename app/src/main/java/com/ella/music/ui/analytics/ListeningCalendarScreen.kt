package com.ella.music.ui.analytics

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.stringResource
import com.ella.music.R
import com.ella.music.data.PlaybackHistoryEntry
import com.ella.music.data.audioQualitySummary
import com.ella.music.data.model.AudioInfo
import com.ella.music.data.model.Song
import com.ella.music.data.model.formatPlaybackDuration
import com.ella.music.ui.components.DefaultAlbumCover
import com.ella.music.ui.components.SongMoreActionHost
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
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.theme.MiuixTheme
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

@Composable
fun ListeningCalendarHistoryScreen(
    mainViewModel: MainViewModel,
    playerViewModel: PlayerViewModel,
    onBack: () -> Unit,
    onNavigateToAlbum: (Long) -> Unit = {},
    onNavigateToArtist: (String) -> Unit = {}
) {
    val songs by mainViewModel.songs.collectAsState()
    val playbackHistory by mainViewModel.playbackHistory.collectAsState()
    val dailyListenMs by mainViewModel.dailyListenMs.collectAsState()
    val libraryById = remember(songs) { songs.associateBy { it.id } }
    val dayAggregates = remember(playbackHistory, dailyListenMs, libraryById) {
        buildListeningDayAggregates(playbackHistory, dailyListenMs, libraryById)
    }
    val monthSections = remember(dayAggregates) { buildListeningMonths(dayAggregates) }
    val firstDayWithHistory = remember(dayAggregates) { dayAggregates.values.firstOrNull { it.entries.isNotEmpty() }?.dateKey }
    var selectedDateKey by remember(firstDayWithHistory) { mutableStateOf(firstDayWithHistory) }
    val selectedDay = remember(selectedDateKey, dayAggregates) {
        selectedDateKey?.let(dayAggregates::get)
    }
    var actionSong by remember { mutableStateOf<Song?>(null) }

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
            Column(modifier = Modifier.padding(start = 8.dp)) {
                Text(
                    text = stringResource(R.string.listening_calendar_title),
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = MiuixTheme.colorScheme.onBackground
                )
                Text(
                    text = if (playbackHistory.isEmpty()) {
                        stringResource(R.string.listening_calendar_empty_day)
                    } else {
                        stringResource(R.string.listening_calendar_records_total, playbackHistory.size)
                    },
                    fontSize = 12.sp,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary
                )
            }
        }

        if (dayAggregates.isEmpty()) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp)
            ) {
                Text(
                    text = stringResource(R.string.listening_calendar_empty_day),
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    modifier = Modifier.padding(16.dp)
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 160.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp)
            ) {
                item("selected-day") {
                    ListeningDayDetailSection(
                        day = selectedDay,
                        mainViewModel = mainViewModel,
                        playerViewModel = playerViewModel,
                        onSongMore = { song -> actionSong = song }
                    )
                }
                items(monthSections, key = { it.label }) { month ->
                    ListeningMonthCard(
                        month = month,
                        selectedDateKey = selectedDateKey,
                        onDayClick = { dateKey ->
                            if (dayAggregates[dateKey]?.entries?.isNotEmpty() == true) {
                                selectedDateKey = dateKey
                            }
                        }
                    )
                }
            }
        }
    }

    SongMoreActionHost(
        actionSong = actionSong,
        mainViewModel = mainViewModel,
        playerViewModel = playerViewModel,
        onDismissAction = { actionSong = null },
        onNavigateToAlbum = onNavigateToAlbum,
        onNavigateToArtist = onNavigateToArtist
    )
}

@Composable
private fun ListeningMonthCard(
    month: ListeningMonthSection,
    selectedDateKey: String?,
    onDayClick: (String) -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
            Text(
                text = month.label,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = MiuixTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(12.dp))
            month.weeks.forEach { week ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    week.forEach { day ->
                        Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.TopCenter) {
                            if (day == null) {
                                Spacer(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .aspectRatio(0.82f)
                                )
                            } else {
                                ListeningCalendarDayCell(
                                    day = day,
                                    selected = day.dateKey == selectedDateKey,
                                    onClick = { onDayClick(day.dateKey) }
                                )
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(10.dp))
            }
        }
    }
}

@Composable
private fun ListeningCalendarDayCell(
    day: ListeningDayAggregate,
    selected: Boolean,
    onClick: () -> Unit
) {
    val bgColor = listeningHeatColor(day.heatValue, day.maxHeatValue)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(if (selected) Color.White.copy(alpha = 0.06f) else Color.Transparent)
            .clickable(enabled = day.entries.isNotEmpty(), onClick = onClick)
            .padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = day.dayOfMonth.toString(),
            fontSize = 13.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            color = if (day.entries.isNotEmpty()) MiuixTheme.colorScheme.onSurface else MiuixTheme.colorScheme.onSurfaceVariantSummary
        )
        Spacer(modifier = Modifier.height(6.dp))
        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(bgColor)
        )
    }
}

@Composable
private fun ListeningDayDetailSection(
    day: ListeningDayAggregate?,
    mainViewModel: MainViewModel,
    playerViewModel: PlayerViewModel,
    onSongMore: (Song) -> Unit
) {
    if (day == null || day.entries.isEmpty()) {
        Card(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = stringResource(R.string.listening_calendar_empty_day),
                fontSize = 14.sp,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                modifier = Modifier.padding(18.dp)
            )
        }
        return
    }

    val representativeSong = day.representativeSong
    val playableSongs = remember(day.entries) { day.entries.mapNotNull { it.song } }
    val coverBitmap by produceState<Bitmap?>(initialValue = null, representativeSong?.id, day.dateKey) {
        value = withContext(Dispatchers.IO) {
            representativeSong?.let(mainViewModel::getAlbumCoverArtBitmap)
        }
    }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalAlignment = Alignment.Top
            ) {
                DayRepresentativeCover(
                    bitmap = coverBitmap,
                    modifier = Modifier.size(84.dp)
                )
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = formatCalendarDetailDate(day.dateKey),
                        fontSize = 26.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = MiuixTheme.colorScheme.onSurface,
                        lineHeight = 30.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        ListeningActionIconButton(
                            iconRes = R.drawable.ic_shuffle,
                            contentDescription = stringResource(R.string.listening_calendar_shuffle),
                            active = false,
                            onClick = {
                                if (playableSongs.isNotEmpty()) {
                                    playerViewModel.setPlaylist(playableSongs.shuffled(), 0)
                                }
                            }
                        )
                        ListeningActionIconButton(
                            iconRes = R.drawable.ic_player_play,
                            contentDescription = stringResource(R.string.listening_calendar_play_all),
                            active = true,
                            onClick = {
                                if (playableSongs.isNotEmpty()) {
                                    playerViewModel.setPlaylist(playableSongs, 0)
                                }
                            }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = stringResource(
                    R.string.listening_calendar_total,
                    day.playCount,
                    formatCalendarTotalDuration(day.totalDurationMs)
                ),
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = MiuixTheme.colorScheme.onSurface
            )
            Text(
                text = stringResource(R.string.listening_calendar_unique_songs, day.uniqueSongsCount),
                fontSize = 12.sp,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                modifier = Modifier.padding(top = 4.dp)
            )

            Spacer(modifier = Modifier.height(14.dp))

            day.entries.forEachIndexed { index, entry ->
                ListeningTimelineRow(
                    entry = entry,
                    isLast = index == day.entries.lastIndex,
                    mainViewModel = mainViewModel,
                    playerViewModel = playerViewModel,
                    onSongMore = onSongMore
                )
            }
        }
    }
}

@Composable
private fun DayRepresentativeCover(
    bitmap: Bitmap?,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(22.dp))
            .background(Color.White.copy(alpha = 0.05f)),
        contentAlignment = Alignment.Center
    ) {
        if (bitmap != null && !bitmap.isRecycled) {
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = null,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            DefaultAlbumCover(modifier = Modifier.fillMaxSize())
        }
    }
}

@Composable
private fun ListeningActionIconButton(
    iconRes: Int,
    contentDescription: String,
    active: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Box(
        modifier = modifier
            .size(52.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(if (active) Color.White.copy(alpha = 0.90f) else Color.White.copy(alpha = 0.10f))
            .clickable(onClick = onClick)
            .padding(14.dp),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            painter = androidx.compose.ui.res.painterResource(id = iconRes),
            contentDescription = contentDescription,
            tint = if (active) Color(0xFF111111) else Color.White,
            modifier = Modifier.fillMaxSize()
        )
    }
}

@Composable
private fun ListeningTimelineRow(
    entry: ListeningTimelineEntry,
    isLast: Boolean,
    mainViewModel: MainViewModel,
    playerViewModel: PlayerViewModel,
    onSongMore: (Song) -> Unit
) {
    val song = entry.song
    val coverBitmap by produceState<Bitmap?>(initialValue = null, song?.id, entry.entry.playedAt) {
        value = withContext(Dispatchers.IO) {
            song?.let(mainViewModel::getCoverArtBitmap)
        }
    }
    val audioInfo by produceState<AudioInfo?>(initialValue = null, song?.id) {
        value = withContext(Dispatchers.IO) {
            song?.let(mainViewModel::getAudioInfo)
        }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = if (isLast) 0.dp else 10.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Column(
            modifier = Modifier.width(58.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(12.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.84f))
            )
            if (!isLast) {
                Box(
                    modifier = Modifier
                        .width(2.dp)
                        .height(94.dp)
                        .background(Color.White.copy(alpha = 0.20f))
                )
            }
        }

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = formatHistoryClock(entry.entry.playedAt),
                fontSize = 20.sp,
                fontWeight = FontWeight.Medium,
                color = MiuixTheme.colorScheme.onSurface,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            Card(
                modifier = Modifier.fillMaxWidth(),
                onClick = {
                    song?.let { playerViewModel.playSong(it) }
                }
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    DayRepresentativeCover(
                        bitmap = coverBitmap,
                        modifier = Modifier.size(64.dp)
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = song?.title ?: entry.entry.title,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = MiuixTheme.colorScheme.onSurface,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.height(5.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            audioInfo?.let {
                                audioQualitySummary(it).listTag?.let { tag ->
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(qualityTagColor(tag))
                                            .padding(horizontal = 5.dp, vertical = 2.dp)
                                    ) {
                                        Text(
                                            text = tag,
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color.Black
                                        )
                                    }
                                }
                            }
                            Text(
                                text = buildString {
                                    append(song?.artist?.ifBlank { entry.entry.artist } ?: entry.entry.artist)
                                    val album = song?.album?.ifBlank { entry.entry.album } ?: entry.entry.album
                                    if (album.isNotBlank()) append(" - $album")
                                },
                                fontSize = 12.sp,
                                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = formatTrackDuration(song?.duration ?: 0L),
                            fontSize = 12.sp,
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        if (song != null) {
                            Box(
                                modifier = Modifier
                                    .clip(CircleShape)
                                    .clickable { onSongMore(song) }
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = "\u22ef",
                                    fontSize = 18.sp,
                                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private data class ListeningTimelineEntry(
    val entry: PlaybackHistoryEntry,
    val song: Song?
)

private data class ListeningDayAggregate(
    val dateKey: String,
    val dayOfMonth: Int,
    val entries: List<ListeningTimelineEntry>,
    val playCount: Int,
    val totalDurationMs: Long,
    val uniqueSongsCount: Int,
    val representativeSong: Song?,
    val heatValue: Long,
    val maxHeatValue: Long = 1L
)

private data class ListeningMonthSection(
    val label: String,
    val year: Int,
    val month: Int,
    val weeks: List<List<ListeningDayAggregate?>>
)

private fun buildListeningDayAggregates(
    history: List<PlaybackHistoryEntry>,
    dailyListenMs: Map<String, Long>,
    libraryById: Map<Long, Song>
): Map<String, ListeningDayAggregate> {
    val groupedHistory = history
        .groupBy(::historyDateKey)
        .mapValues { (_, entries) -> entries.sortedBy { it.playedAt } }
    val allDateKeys = (groupedHistory.keys + dailyListenMs.keys)
        .filter { it.isNotBlank() }
        .distinct()
        .sortedDescending()

    val rawAggregates = allDateKeys.associateWith { dateKey ->
        val entries = groupedHistory[dateKey].orEmpty().map { ListeningTimelineEntry(it, libraryById[it.songId]) }
        val totalDuration = dailyListenMs[dateKey]
            ?: entries.sumOf { it.song?.duration ?: 0L }
        val dayOfMonth = dateKey.substringAfterLast('-').toIntOrNull() ?: 1
        val representativeSong = entries
            .groupBy { it.song?.id ?: -1L }
            .maxByOrNull { (_, rows) -> rows.size }
            ?.value
            ?.firstOrNull()
            ?.song
            ?: entries.firstOrNull()?.song
        ListeningDayAggregate(
            dateKey = dateKey,
            dayOfMonth = dayOfMonth,
            entries = entries,
            playCount = entries.size,
            totalDurationMs = totalDuration,
            uniqueSongsCount = entries.map { it.entry.songId }.distinct().size,
            representativeSong = representativeSong,
            heatValue = if (totalDuration > 0L) totalDuration else entries.size.toLong()
        )
    }
    val maxHeatValue = rawAggregates.values.maxOfOrNull { it.heatValue }?.coerceAtLeast(1L) ?: 1L
    return rawAggregates.mapValues { (_, day) -> day.copy(maxHeatValue = maxHeatValue) }
}

private fun buildListeningMonths(dayAggregates: Map<String, ListeningDayAggregate>): List<ListeningMonthSection> {
    if (dayAggregates.isEmpty()) return emptyList()
    val monthKeys = dayAggregates.keys
        .mapNotNull(::yearMonthParts)
        .distinct()
        .sortedWith(compareByDescending<Pair<Int, Int>> { it.first }.thenByDescending { it.second })

    return monthKeys.map { (year, month) ->
        val daysInMonth = daysInMonth(year, month)
        val firstWeekdayOffset = firstWeekdayOffset(year, month)
        val cells = buildList<ListeningDayAggregate?> {
            repeat(firstWeekdayOffset) { add(null) }
            (1..daysInMonth).forEach { day ->
                val key = "%04d-%02d-%02d".format(year, month, day)
                add(dayAggregates[key] ?: ListeningDayAggregate(
                    dateKey = key,
                    dayOfMonth = day,
                    entries = emptyList(),
                    playCount = 0,
                    totalDurationMs = 0L,
                    uniqueSongsCount = 0,
                    representativeSong = null,
                    heatValue = 0L,
                    maxHeatValue = dayAggregates.values.firstOrNull()?.maxHeatValue ?: 1L
                ))
            }
            while (size % 7 != 0) add(null)
        }
        ListeningMonthSection(
            label = "%04d-%02d".format(year, month),
            year = year,
            month = month,
            weeks = cells.chunked(7)
        )
    }
}

private fun firstWeekdayOffset(year: Int, month: Int): Int {
    val calendar = Calendar.getInstance().apply {
        set(Calendar.YEAR, year)
        set(Calendar.MONTH, month - 1)
        set(Calendar.DAY_OF_MONTH, 1)
    }
    return calendar.get(Calendar.DAY_OF_WEEK) - 1
}

private fun daysInMonth(year: Int, month: Int): Int {
    val calendar = Calendar.getInstance().apply {
        set(Calendar.YEAR, year)
        set(Calendar.MONTH, month - 1)
        set(Calendar.DAY_OF_MONTH, 1)
    }
    return calendar.getActualMaximum(Calendar.DAY_OF_MONTH)
}

private fun yearMonthParts(dateKey: String): Pair<Int, Int>? {
    val parts = dateKey.split('-')
    if (parts.size < 2) return null
    val year = parts.getOrNull(0)?.toIntOrNull() ?: return null
    val month = parts.getOrNull(1)?.toIntOrNull() ?: return null
    return year to month
}

private fun listeningHeatColor(value: Long, maxValue: Long): Color {
    if (value <= 0L) return Color(0xFF7B7B7B).copy(alpha = 0.38f)
    val level = (value.toFloat() / maxValue.toFloat()).coerceIn(0f, 1f)
    return when {
        level < 0.20f -> Color(0xFFE9F4E8)
        level < 0.45f -> Color(0xFFAFE7A7)
        level < 0.75f -> Color(0xFF5FD05A)
        else -> Color(0xFF1A8815)
    }
}

private fun qualityTagColor(tag: String): Color {
    return when (tag) {
        "HR" -> Color(0xFFFFA726)
        "HQ" -> Color(0xFF4A90FF)
        "LQ" -> Color(0xFF45D06B)
        "SQ" -> Color(0xFFD16CFF)
        "MQ" -> Color(0xFFFF6E40)
        "Dolby" -> Color(0xFF2FD8FF)
        else -> Color.White.copy(alpha = 0.72f)
    }
}

private fun formatCalendarDetailDate(dateKey: String): String {
    val date = parseHistoryDateKey(dateKey) ?: return dateKey
    return SimpleDateFormat("yyyy/M/d", Locale.getDefault()).format(date)
}

private fun formatCalendarTotalDuration(durationMs: Long): String {
    return durationMs.formatPlaybackDuration()
}

private fun formatTrackDuration(durationMs: Long): String {
    return durationMs.formatPlaybackDuration()
}

private fun formatHistoryClock(timestampMs: Long): String {
    if (timestampMs <= 0L) return ""
    return SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(timestampMs))
}

private fun historyDateKey(entry: PlaybackHistoryEntry): String = historyDateKey(entry.playedAt)

private fun historyDateKey(timestampMs: Long): String {
    if (timestampMs <= 0L) return ""
    return SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date(timestampMs))
}

private fun parseHistoryDateKey(dateKey: String): Date? {
    return runCatching {
        SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).parse(dateKey)
    }.getOrNull()
}
