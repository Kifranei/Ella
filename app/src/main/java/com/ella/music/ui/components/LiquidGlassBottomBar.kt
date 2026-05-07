package com.ella.music.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.shadow.InnerShadow
import com.kyant.backdrop.shadow.Shadow
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun LiquidGlassBottomBar(
    backdrop: com.kyant.backdrop.Backdrop?,
    isBlurEnabled: Boolean = true,
    content: @Composable RowScope.() -> Unit
) {
    val isLight = MiuixTheme.colorScheme.background.luminance() > 0.5f
    val hasBackdrop = isBlurEnabled && backdrop != null
    val containerColor = if (hasBackdrop) {
        if (isLight) Color(0xFFF8F8FA).copy(alpha = 0.72f) else Color(0xFF111114).copy(alpha = 0.88f)
    } else {
        if (isLight) Color(0xFFF8F8FA).copy(alpha = 0.88f) else Color(0xFF111114).copy(alpha = 0.94f)
    }

    val glassModifier = if (hasBackdrop) {
        Modifier.drawBackdrop(
            backdrop = backdrop,
            shape = { RoundedCornerShape(28.dp) },
            effects = {
                blur(6f.dp.toPx())
            },
            highlight = {
                Highlight.Default.copy(alpha = 0.28f)
            },
            shadow = {
                Shadow.Default.copy(
                    color = Color.Black.copy(alpha = if (isLight) 0.08f else 0.2f)
                )
            },
            onDrawSurface = { drawRect(containerColor) }
        )
    } else {
        Modifier.background(containerColor, RoundedCornerShape(28.dp))
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .clip(RoundedCornerShape(28.dp))
            .then(glassModifier)
            .height(64.dp)
            .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        content = content
    )
}

@Composable
fun RowScope.LiquidGlassBottomBarItem(
    selected: Boolean,
    onClick: () -> Unit,
    backdrop: com.kyant.backdrop.Backdrop?,
    isBlurEnabled: Boolean = true,
    icon: @Composable () -> Unit,
    label: @Composable () -> Unit
) {
    val scope = rememberCoroutineScope()
    val pressProgress = remember { Animatable(0f) }
    var isPressed by remember { mutableStateOf(false) }
    val isLight = MiuixTheme.colorScheme.background.luminance() > 0.5f

    Column(
        modifier = Modifier
            .weight(1f)
            .height(56.dp)
            .clip(RoundedCornerShape(16.dp))
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    isPressed = true
                    scope.launch {
                        pressProgress.animateTo(
                            1f,
                            animationSpec = spring(stiffness = 400f, dampingRatio = 0.7f)
                        )
                    }
                    waitForUpOrCancellation()
                    isPressed = false
                    scope.launch {
                        pressProgress.animateTo(
                            0f,
                            animationSpec = spring(stiffness = 300f, dampingRatio = 0.6f)
                        )
                    }
                    onClick()
                }
            }
            .graphicsLayer {
                val scale = lerp(1f, 1.08f, pressProgress.value)
                scaleX = scale
                scaleY = scale
            }
            .then(
                if (isBlurEnabled && backdrop != null) Modifier.drawBackdrop(
                    backdrop = backdrop,
                    shape = { RoundedCornerShape(16.dp) },
                    effects = {
                        val p = pressProgress.value
                        if (p > 0.01f) {
                            blur(2f.dp.toPx() * p)
                        }
                    },
                    highlight = {
                        Highlight.Default.copy(alpha = pressProgress.value * 0.8f)
                    },
                    innerShadow = {
                        InnerShadow(
                            radius = 6f.dp * pressProgress.value,
                            alpha = pressProgress.value * 0.3f
                        )
                    },
                    onDrawSurface = {
                        val p = pressProgress.value
                        drawRect(
                            color = if (isLight) Color.Black.copy(0.06f) else Color.White.copy(0.06f),
                            alpha = p
                        )
                    }
                ) else Modifier
            )
            .padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(modifier = Modifier.graphicsLayer { alpha = if (selected) 1f else 0.6f }) {
            icon()
        }
        Box(modifier = Modifier.graphicsLayer { alpha = if (selected) 1f else 0.5f }) {
            label()
        }
    }
}

private fun Color.luminance(): Float {
    return 0.2126f * red + 0.7152f * green + 0.0722f * blue
}
