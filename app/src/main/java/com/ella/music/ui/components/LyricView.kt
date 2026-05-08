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
    showTranslation: Boolean,
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
            val lineTextAlign = line.ttmlTextAlign()

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
                textAlign = lineTextAlign,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = if (isActive) 4.dp else 0.dp)
            )
            if (!line.backgroundText.isNullOrBlank()) {
                Text(
                    text = line.backgroundText,
                    fontSize = if (isActive) 14.sp else 12.sp,
                    color = textColor.copy(alpha = 0.56f),
                    textAlign = lineTextAlign,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 2.dp)
                )
            }
            if (showTranslation && !line.backgroundTranslation.isNullOrBlank()) {
                Text(
                    text = line.backgroundTranslation,
                    fontSize = if (isActive) 13.sp else 11.sp,
                    color = textColor.copy(alpha = 0.48f),
                    textAlign = lineTextAlign,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 2.dp)
                )
            }
            if (showTranslation && !line.translation.isNullOrBlank()) {
                Text(
                    text = line.translation,
                    fontSize = if (isActive) 14.sp else 12.sp,
                    color = textColor.copy(alpha = 0.72f),
                    textAlign = lineTextAlign,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 2.dp)
                )
            }
        }

        item { Box(modifier = Modifier.height(300.dp)) }
    }
}

@Composable
fun WordLyricView(
    lyrics: List<LyricLine>,
    currentIndex: Int,
    currentPositionMs: Long,
    showTranslation: Boolean,
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
            val lineTextAlign = line.ttmlTextAlign()

            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = line.ttmlAlignment()
            ) {
                if (line.words.isNotEmpty() && isActive) {
                    WordLine(
                        words = line.words,
                        currentPositionMs = currentPositionMs,
                        textAlign = lineTextAlign
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
                        textAlign = lineTextAlign,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = if (isActive) 4.dp else 0.dp)
                    )
                }
                if (!line.backgroundText.isNullOrBlank()) {
                    val backgroundColor = when {
                        isActive -> MiuixTheme.colorScheme.primary.copy(alpha = 0.56f)
                        index < currentIndex -> MiuixTheme.colorScheme.onSurfaceVariantSummary.copy(alpha = 0.34f)
                        else -> MiuixTheme.colorScheme.onSurfaceVariantSummary.copy(alpha = 0.48f)
                    }
                    if (isActive && line.backgroundWords.isNotEmpty()) {
                        WordLine(
                            words = line.backgroundWords,
                            currentPositionMs = currentPositionMs,
                            textAlign = lineTextAlign,
                            fontSizeSp = 14,
                            currentColor = MiuixTheme.colorScheme.primary.copy(alpha = 0.66f),
                            sungColor = MiuixTheme.colorScheme.primary.copy(alpha = 0.46f),
                            pendingColor = MiuixTheme.colorScheme.onSurfaceVariantSummary.copy(alpha = 0.48f),
                            modifier = Modifier.padding(top = 3.dp)
                        )
                    } else {
                        Text(
                            text = line.backgroundText,
                            fontSize = if (isActive) 14.sp else 12.sp,
                            color = backgroundColor,
                            textAlign = lineTextAlign,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 3.dp)
                        )
                    }
                }
                if (showTranslation && !line.backgroundTranslation.isNullOrBlank()) {
                    val backgroundTranslationColor = when {
                        isActive -> MiuixTheme.colorScheme.primary.copy(alpha = 0.44f)
                        index < currentIndex -> MiuixTheme.colorScheme.onSurfaceVariantSummary.copy(alpha = 0.28f)
                        else -> MiuixTheme.colorScheme.onSurfaceVariantSummary.copy(alpha = 0.40f)
                    }
                    Text(
                        text = line.backgroundTranslation,
                        fontSize = if (isActive) 13.sp else 11.sp,
                        color = backgroundTranslationColor,
                        textAlign = lineTextAlign,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 2.dp)
                    )
                }
                if (showTranslation && !line.translation.isNullOrBlank()) {
                    val translationColor = when {
                        isActive -> MiuixTheme.colorScheme.primary.copy(alpha = 0.72f)
                        index < currentIndex -> MiuixTheme.colorScheme.onSurfaceVariantSummary.copy(alpha = 0.42f)
                        else -> MiuixTheme.colorScheme.onSurfaceVariantSummary.copy(alpha = 0.58f)
                    }
                    Text(
                        text = line.translation,
                        fontSize = if (isActive) 14.sp else 12.sp,
                        color = translationColor,
                        textAlign = lineTextAlign,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 3.dp)
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
    textAlign: TextAlign,
    modifier: Modifier = Modifier,
    fontSizeSp: Int = 18,
    currentColor: Color = MiuixTheme.colorScheme.primary,
    sungColor: Color = MiuixTheme.colorScheme.primary.copy(alpha = 0.8f),
    pendingColor: Color = MiuixTheme.colorScheme.onSurfaceVariantSummary.copy(alpha = 0.7f)
) {
    val builder = androidx.compose.ui.text.AnnotatedString.Builder()
    for (word in words) {
        val isWordActive = currentPositionMs >= word.startMs
        val isWordCurrent = currentPositionMs in word.startMs..word.endMs

        val color = when {
            isWordCurrent -> currentColor
            isWordActive -> sungColor
            else -> pendingColor
        }

        builder.pushStyle(
            androidx.compose.ui.text.SpanStyle(
                color = color,
                fontSize = fontSizeSp.sp,
                fontWeight = if (isWordCurrent) FontWeight.ExtraBold else FontWeight.Bold
            )
        )
        builder.append(word.text)
        builder.pop()
    }

    Text(
        text = builder.toAnnotatedString(),
        textAlign = textAlign,
        modifier = modifier.fillMaxWidth()
    )
}

private fun LyricLine.ttmlTextAlign(): TextAlign {
    if (!isTtml || agent.isNullOrBlank()) return TextAlign.Center
    return if (agent.equals("v2", ignoreCase = true)) TextAlign.End else TextAlign.Start
}

private fun LyricLine.ttmlAlignment(): Alignment.Horizontal {
    if (!isTtml || agent.isNullOrBlank()) return Alignment.CenterHorizontally
    return if (agent.equals("v2", ignoreCase = true)) Alignment.End else Alignment.Start
}
