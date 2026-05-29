package com.ella.music.ui.components

import android.graphics.Bitmap
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.stringResource
import com.ella.music.R
import com.ella.music.data.model.LyricLine
import com.ella.music.data.model.Song
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.icon.extended.Share

private const val MAX_SHARE_LINES = 14

@Composable
fun LyricSharePicker(
    song: Song?,
    lyrics: List<LyricLine>,
    initialLine: LyricLine,
    cover: Bitmap?,
    backgroundColors: List<Color>,
    annotation: String = "",
    onDismiss: () -> Unit,
    onShare: (List<LyricLine>) -> Unit
) {
    BackHandler(onBack = onDismiss)

    val initialIndex = remember(lyrics, initialLine) {
        lyrics.indexOfFirst { it.timeMs == initialLine.timeMs && it.text == initialLine.text }
            .takeIf { it >= 0 }
            ?: lyrics.indexOf(initialLine).takeIf { it >= 0 }
            ?: 0
    }
    var selectedIndexes by remember(lyrics, initialIndex) { mutableStateOf(setOf(initialIndex)) }
    val listState = rememberLazyListState()

    LaunchedEffect(initialIndex) {
        listState.scrollToItem((initialIndex - 4).coerceAtLeast(0))
    }
    val selectedLines = remember(selectedIndexes, lyrics) {
        selectedIndexes
            .sorted()
            .mapNotNull(lyrics::getOrNull)
            .filter { it.sharePrimaryText().isNotBlank() }
    }

    val colors = backgroundColors.ifEmpty {
        listOf(Color(0xFF301E1F), Color(0xFF221F1E), Color(0xFF0D0C0E))
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        colors.first().copy(alpha = 0.98f),
                        colors.getOrElse(1) { colors.first() }.copy(alpha = 0.98f),
                        colors.last().copy(alpha = 1f)
                    )
                )
            )
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(64.dp)
                    .padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onDismiss) {
                    Icon(
                        imageVector = MiuixIcons.Regular.Back,
                        contentDescription = "返回",
                        tint = Color.White
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.lyric_share_picker_title),
                        color = Color.White,
                        fontSize = 19.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = stringResource(
                            R.string.lyric_share_selected_count,
                            selectedIndexes.size,
                            MAX_SHARE_LINES
                        ),
                        color = Color.White.copy(alpha = 0.56f),
                        fontSize = 12.sp
                    )
                }
                IconButton(
                    onClick = {
                        selectedIndexes
                            .sorted()
                            .mapNotNull(lyrics::getOrNull)
                            .takeIf { it.isNotEmpty() }
                            ?.let(onShare)
                    }
                ) {
                    Icon(
                        imageVector = MiuixIcons.Regular.Share,
                        contentDescription = "分享",
                        tint = if (selectedIndexes.isEmpty()) Color.White.copy(alpha = 0.34f) else Color.White
                    )
                }
            }

            LyricSharePreviewCard(
                annotation = annotation,
                artist = song?.artist?.takeIf { it.isNotBlank() }
                    ?: stringResource(R.string.lyric_share_unknown_artist),
                title = song?.title?.takeIf { it.isNotBlank() }
                    ?: stringResource(R.string.lyric_share_unknown_song),
                cover = cover,
                colors = colors,
                lines = selectedLines.ifEmpty { listOf(initialLine) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 12.dp)
            )

            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                itemsIndexed(lyrics) { index, line ->
                    val selected = index in selectedIndexes
                    LyricSharePickerRow(
                        line = line,
                        selected = selected,
                        onClick = {
                            selectedIndexes = if (selected) {
                                selectedIndexes - index
                            } else if (selectedIndexes.size >= MAX_SHARE_LINES) {
                                selectedIndexes
                            } else {
                                selectedIndexes + index
                            }
                        }
                    )
                }
                item { Spacer(modifier = Modifier.height(18.dp)) }
            }
        }
    }
}

@Composable
private fun LyricSharePickerRow(
    line: LyricLine,
    selected: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 18.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(if (selected) Color.White.copy(alpha = 0.18f) else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(22.dp)
                .clip(CircleShape)
                .background(if (selected) Color.White else Color.White.copy(alpha = 0.16f)),
            contentAlignment = Alignment.Center
        ) {
            if (selected) {
                Box(
                    modifier = Modifier
                        .size(9.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF111111))
                )
            }
        }
        Column(
            modifier = Modifier
                .padding(start = 14.dp)
                .weight(1f)
        ) {
            Text(
                text = line.text.ifBlank { line.backgroundText.orEmpty().ifBlank { "♪" } },
                color = Color.White.copy(alpha = if (selected) 1f else 0.76f),
                fontSize = 17.sp,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )
            val secondary = listOfNotNull(
                line.translation?.takeIf { it.isNotBlank() },
                line.backgroundTranslation?.takeIf { it.isNotBlank() }
            ).firstOrNull()
            if (!secondary.isNullOrBlank()) {
                Text(
                    text = secondary,
                    color = Color.White.copy(alpha = if (selected) 0.62f else 0.42f),
                    fontSize = 13.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 3.dp)
                )
            }
        }
    }
}

@Composable
private fun LyricSharePreviewCard(
    title: String,
    artist: String,
    annotation: String,
    cover: Bitmap?,
    colors: List<Color>,
    lines: List<LyricLine>,
    modifier: Modifier = Modifier
) {
    val blocks = remember(lines) {
        lines.mapNotNull { it.toShareLyricBlock() }.take(5)
    }
    val previewRows = remember(blocks) {
        blocks.sumOf { 1 + if (it.secondary.isNotEmpty()) 1 else 0 }
    }
    val cardHeight = when {
        previewRows <= 2 -> 220.dp
        previewRows <= 4 -> 266.dp
        previewRows <= 6 -> 308.dp
        previewRows <= 8 -> 346.dp
        else -> 384.dp
    }
    val primarySize = when {
        blocks.size <= 1 -> 30.sp
        blocks.size <= 2 -> 28.sp
        blocks.size <= 4 -> 25.sp
        else -> 22.sp
    }
    val secondarySize = when {
        blocks.size <= 2 -> 14.sp
        else -> 13.sp
    }

    Box(
        modifier = modifier
            .heightIn(min = 220.dp, max = 384.dp)
            .height(cardHeight)
            .clip(RoundedCornerShape(28.dp))
            .background(
                Brush.linearGradient(
                    colors = listOf(
                        colors.first().copy(alpha = 0.98f),
                        colors.getOrElse(1) { colors.first() }.copy(alpha = 0.96f),
                        colors.last().copy(alpha = 0.98f)
                    )
                )
            )
            .padding(horizontal = 22.dp, vertical = 20.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(24.dp))
                .background(Color.Black.copy(alpha = 0.10f))
        )
        Column(modifier = Modifier.fillMaxSize()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (cover != null) {
                    Image(
                        bitmap = cover.asImageBitmap(),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(52.dp)
                            .clip(RoundedCornerShape(16.dp))
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .size(52.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(Color.White.copy(alpha = 0.14f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(text = "\u266a", color = Color.White.copy(alpha = 0.82f), fontSize = 22.sp)
                    }
                }
                Column(
                    modifier = Modifier
                        .padding(start = 14.dp)
                        .weight(1f)
                ) {
                    Text(
                        text = title,
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (annotation.isNotBlank()) {
                        Text(
                            text = annotation,
                            color = Color.White.copy(alpha = 0.74f),
                            fontSize = 12.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(top = 2.dp)
                        )
                    }
                    Text(
                        text = artist,
                        color = Color.White.copy(alpha = 0.60f),
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                blocks.forEach { block ->
                    Column {
                        Text(
                            text = block.primary,
                            color = Color.White,
                            fontSize = primarySize,
                            fontWeight = FontWeight.ExtraBold,
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis,
                            textAlign = TextAlign.Start
                        )
                        block.secondary.firstOrNull()?.let { secondary ->
                            Text(
                                text = secondary,
                                color = Color.White.copy(alpha = 0.62f),
                                fontSize = secondarySize,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        }
                    }
                }
            }

            Text(
                text = stringResource(R.string.lyric_share_footer_default),
                color = Color.White.copy(alpha = 0.42f),
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}
