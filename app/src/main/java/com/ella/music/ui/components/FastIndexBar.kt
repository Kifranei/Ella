package com.ella.music.ui.components

import android.os.SystemClock
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.changedToUpIgnoreConsumed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.floor
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun FastIndexBar(
    letters: List<String>,
    onLetterClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val indexLetters = remember(letters) { letters.toFastIndexLetters() }
    var heightPx by remember { mutableStateOf(1) }
    var contentHeightPx by remember { mutableStateOf(1) }
    var lastSelectedLetter by remember { mutableStateOf<String?>(null) }
    var lastDispatchTimeMs by remember { mutableStateOf(0L) }

    fun selectAt(y: Float, force: Boolean = false) {
        if (indexLetters.isEmpty()) return
        val now = SystemClock.uptimeMillis()
        if (!force && now - lastDispatchTimeMs < 80L) return
        val contentTop = ((heightPx - contentHeightPx) / 2f).coerceAtLeast(0f)
        val localY = (y - contentTop).coerceIn(0f, contentHeightPx.toFloat() - 1f)
        val index = floor((localY / contentHeightPx) * indexLetters.size)
            .toInt()
            .coerceIn(0, indexLetters.lastIndex)
        val letter = indexLetters[index]
        if (letter != lastSelectedLetter) {
            lastSelectedLetter = letter
            lastDispatchTimeMs = now
            onLetterClick(letter)
        }
    }

    Box(
        modifier = modifier
            .onSizeChanged { heightPx = it.height.coerceAtLeast(1) }
            .pointerInput(indexLetters, heightPx, contentHeightPx) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    selectAt(down.position.y, force = true)
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull() ?: break
                        if (change.changedToUpIgnoreConsumed()) break
                        if (change.pressed) {
                            selectAt(change.position.y)
                            change.consume()
                        }
                    }
                    lastSelectedLetter = null
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier.onSizeChanged { contentHeightPx = it.height.coerceAtLeast(1) },
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            indexLetters.forEach { letter ->
                Text(
                    text = letter,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = MiuixTheme.colorScheme.primary,
                    modifier = Modifier
                        .clickable {
                            lastSelectedLetter = letter
                            lastDispatchTimeMs = SystemClock.uptimeMillis()
                            onLetterClick(letter)
                        }
                        .padding(horizontal = 8.dp, vertical = 1.dp)
                )
            }
        }
    }
}

fun List<String>.toFastIndexLetters(): List<String> =
    distinct().sortedWith(compareBy<String> { if (it == "#") "ZZZ" else it })
