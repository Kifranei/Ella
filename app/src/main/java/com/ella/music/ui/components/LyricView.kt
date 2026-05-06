package com.ella.music.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ella.music.data.model.LyricLine
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun LyricView(
    lyrics: List<LyricLine>,
    currentIndex: Int,
    modifier: Modifier = Modifier
) {
    if (lyrics.isEmpty()) {
        Box(modifier = modifier, contentAlignment = Alignment.Center) {
            Text(
                text = "暂无歌词",
                fontSize = 16.sp,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary
            )
        }
        return
    }

    val listState = rememberLazyListState()

    LaunchedEffect(currentIndex) {
        if (currentIndex >= 0 && currentIndex < lyrics.size) {
            val targetIndex = (currentIndex + 1).coerceAtMost(lyrics.size)
            listState.animateScrollToItem(
                index = targetIndex,
                scrollOffset = -200
            )
        }
    }

    LazyColumn(
        state = listState,
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item { Box(modifier = Modifier.height(200.dp)) }

        itemsIndexed(lyrics) { index, line ->
            val isActive = index == currentIndex
            val isPast = index < currentIndex

            val textColor = when {
                isActive -> MiuixTheme.colorScheme.primary
                isPast -> MiuixTheme.colorScheme.onSurfaceVariantSummary.copy(alpha = 0.5f)
                else -> MiuixTheme.colorScheme.onSurfaceVariantSummary.copy(alpha = 0.7f)
            }

            Text(
                text = line.text.ifBlank { "♪" },
                fontSize = if (isActive) 18.sp else 15.sp,
                fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal,
                color = textColor,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = if (isActive) 4.dp else 0.dp)
            )
        }

        item { Box(modifier = Modifier.height(300.dp)) }
    }
}

@Composable
fun WordLyricView(
    lyrics: List<LyricLine>,
    currentIndex: Int,
    currentPositionMs: Long,
    modifier: Modifier = Modifier
) {
    if (lyrics.isEmpty()) {
        Box(modifier = modifier, contentAlignment = Alignment.Center) {
            Text(
                text = "暂无歌词",
                fontSize = 16.sp,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary
            )
        }
        return
    }

    val listState = rememberLazyListState()

    LaunchedEffect(currentIndex) {
        if (currentIndex >= 0 && currentIndex < lyrics.size) {
            val targetIndex = (currentIndex + 1).coerceAtMost(lyrics.size)
            listState.animateScrollToItem(
                index = targetIndex,
                scrollOffset = -200
            )
        }
    }

    LazyColumn(
        state = listState,
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item { Box(modifier = Modifier.height(200.dp)) }

        itemsIndexed(lyrics) { index, line ->
            val isActive = index == currentIndex

            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                if (line.words.isNotEmpty() && isActive) {
                    WordLine(
                        words = line.words,
                        currentPositionMs = currentPositionMs,
                        isActive = true
                    )
                } else {
                    val textColor = when {
                        isActive -> MiuixTheme.colorScheme.primary
                        index < currentIndex -> MiuixTheme.colorScheme.onSurfaceVariantSummary.copy(alpha = 0.5f)
                        else -> MiuixTheme.colorScheme.onSurfaceVariantSummary.copy(alpha = 0.7f)
                    }
                    Text(
                        text = line.text.ifBlank { "♪" },
                        fontSize = if (isActive) 18.sp else 15.sp,
                        fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal,
                        color = textColor,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = if (isActive) 4.dp else 0.dp)
                    )
                }
            }
        }

        item { Box(modifier = Modifier.height(300.dp)) }
    }
}

@Composable
private fun WordLine(
    words: List<com.ella.music.data.model.LyricWord>,
    currentPositionMs: Long,
    isActive: Boolean
) {
    val builder = androidx.compose.ui.text.AnnotatedString.Builder()
    for (word in words) {
        val isWordActive = currentPositionMs >= word.startMs
        val isWordCurrent = currentPositionMs in word.startMs..word.endMs

        val color = when {
            isWordCurrent -> MiuixTheme.colorScheme.primary
            isWordActive -> MiuixTheme.colorScheme.primary.copy(alpha = 0.8f)
            else -> MiuixTheme.colorScheme.onSurfaceVariantSummary.copy(alpha = 0.7f)
        }

        builder.pushStyle(
            androidx.compose.ui.text.SpanStyle(
                color = color,
                fontSize = 18.sp,
                fontWeight = if (isWordCurrent) FontWeight.ExtraBold else FontWeight.Bold
            )
        )
        builder.append(word.text)
        builder.pop()
    }

    Text(
        text = builder.toAnnotatedString(),
        textAlign = TextAlign.Center
    )
}
