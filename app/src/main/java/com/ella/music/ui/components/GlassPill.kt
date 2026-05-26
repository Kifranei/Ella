package com.ella.music.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.ella.music.data.BottomBarGlassEffect
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.shadow.Shadow
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun GlassPill(
    backdrop: Backdrop?,
    modifier: Modifier = Modifier,
    shape: RoundedCornerShape = RoundedCornerShape(32.dp),
    blurRadius: Float = 34f,
    liquidBlurRadius: Float = 12f,
    glassEffect: BottomBarGlassEffect = BottomBarGlassEffect.Blur,
    content: @Composable BoxScope.() -> Unit
) {
    val isLight = MiuixTheme.colorScheme.background.simpleLuminance() > 0.5f
    val containerColor = bottomBarGlassContainerColor(
        isLight = isLight,
        glassEffect = glassEffect,
        lightAlpha = 0.56f,
        darkAlpha = 0.58f,
        lightLiquidAlpha = 0.34f,
        darkLiquidAlpha = 0.38f
    )

    val glassModifier = if (backdrop != null) {
        Modifier.drawBackdrop(
            backdrop = backdrop,
            shape = { shape },
            effects = {
                applyBottomBarGlassEffect(
                    glassEffect = glassEffect,
                    blurRadius = blurRadius,
                    liquidBlurRadius = liquidBlurRadius
                )
            },
            highlight = {
                Highlight.Default.copy(alpha = bottomBarGlassHighlightAlpha(isLight, glassEffect))
            },
            shadow = {
                Shadow.Default.copy(
                    color = Color.Black.copy(alpha = bottomBarGlassShadowAlpha(isLight, glassEffect))
                )
            },
            onDrawSurface = {
                drawRect(containerColor)
            }
        )
    } else {
        Modifier.background(containerColor, shape)
    }

    Box(
        modifier = modifier
            .clip(shape)
            .then(glassModifier),
        content = content
    )
}
