package com.ella.music.ui.components

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
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.ella.music.data.BottomBarGlassEffect
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.shadow.Shadow
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun LiquidGlassBottomBar(
    backdrop: com.kyant.backdrop.Backdrop?,
    isBlurEnabled: Boolean = true,
    glassEffect: BottomBarGlassEffect = BottomBarGlassEffect.Blur,
    content: @Composable RowScope.() -> Unit
) {
    val shape = RoundedCornerShape(32.dp)
    val isLight = MiuixTheme.colorScheme.background.simpleLuminance() > 0.5f
    val hasBackdrop = isBlurEnabled && backdrop != null
    val containerColor = bottomBarGlassContainerColor(
        isLight = isLight,
        glassEffect = glassEffect,
        lightAlpha = 0.62f,
        darkAlpha = 0.66f,
        lightLiquidAlpha = 0.36f,
        darkLiquidAlpha = 0.40f
    )

    val glassModifier = if (hasBackdrop) {
        Modifier.drawBackdrop(
            backdrop = backdrop,
            shape = { shape },
            effects = {
                applyBottomBarGlassEffect(
                    glassEffect = glassEffect,
                    blurRadius = 26f,
                    liquidBlurRadius = 10f
                )
            },
            highlight = {
                Highlight.Default.copy(
                    alpha = when (glassEffect) {
                        BottomBarGlassEffect.Blur -> if (isLight) 0.18f else 0.10f
                        BottomBarGlassEffect.LiquidGlass -> if (isLight) 0.34f else 0.24f
                    }
                )
            },
            shadow = {
                Shadow.Default.copy(
                    color = Color.Black.copy(
                        alpha = when (glassEffect) {
                            BottomBarGlassEffect.Blur -> if (isLight) 0.10f else 0.26f
                            BottomBarGlassEffect.LiquidGlass -> if (isLight) 0.18f else 0.38f
                        }
                    )
                )
            },
            onDrawSurface = { drawRect(containerColor) }
        )
    } else {
        Modifier.background(containerColor, shape)
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .clip(shape)
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
    var isPressed by remember { mutableStateOf(false) }
    val currentOnClick by rememberUpdatedState(onClick)
    val isLight = MiuixTheme.colorScheme.background.simpleLuminance() > 0.5f
    val selectedColor = if (isLight) {
        MiuixTheme.colorScheme.primary.copy(alpha = 0.12f)
    } else {
        MiuixTheme.colorScheme.primary.copy(alpha = 0.18f)
    }
    val pressedColor = if (isLight) {
        Color.Black.copy(alpha = 0.06f)
    } else {
        Color.White.copy(alpha = 0.08f)
    }

    Column(
        modifier = Modifier
            .weight(1f)
            .height(56.dp)
            .clip(RoundedCornerShape(28.dp))
            .background(
                color = if (isPressed) pressedColor else Color.Transparent,
                shape = RoundedCornerShape(28.dp)
            )
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    isPressed = true
                    val up = waitForUpOrCancellation()
                    isPressed = false
                    if (up != null) currentOnClick()
                }
            }
            .padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(modifier = Modifier.graphicsLayer { alpha = if (selected) 1f else 0.62f }) {
            icon()
        }
        Box(modifier = Modifier.graphicsLayer { alpha = if (selected) 1f else 0.56f }) {
            label()
        }
    }
}
