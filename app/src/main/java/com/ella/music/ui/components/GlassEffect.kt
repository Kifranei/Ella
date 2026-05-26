package com.ella.music.ui.components

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.ella.music.data.BottomBarGlassEffect
import com.kyant.backdrop.BackdropEffectScope
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens

internal fun BackdropEffectScope.applyBottomBarGlassEffect(
    glassEffect: BottomBarGlassEffect,
    blurRadius: Float,
    liquidBlurRadius: Float
) {
    when (glassEffect) {
        BottomBarGlassEffect.Blur -> {
            blur(blurRadius.dp.toPx())
        }

        BottomBarGlassEffect.LiquidGlass -> {
            blur(6f.dp.toPx())
            runCatching {
                lens(
                    refractionHeight = 24f.dp.toPx(),
                    refractionAmount = 24f.dp.toPx()
                )
            }
        }
    }
}

internal fun bottomBarGlassContainerColor(
    isLight: Boolean,
    glassEffect: BottomBarGlassEffect,
    lightAlpha: Float,
    darkAlpha: Float,
    lightLiquidAlpha: Float = 0.38f,
    darkLiquidAlpha: Float = 0.42f
): Color {
    val alpha = when (glassEffect) {
        BottomBarGlassEffect.Blur -> if (isLight) lightAlpha else darkAlpha
        BottomBarGlassEffect.LiquidGlass -> if (isLight) lightLiquidAlpha else darkLiquidAlpha
    }
    return if (isLight) {
        Color.White.copy(alpha = alpha)
    } else {
        Color(0xFF111114).copy(alpha = alpha)
    }
}

internal fun bottomBarGlassHighlightAlpha(isLight: Boolean, glassEffect: BottomBarGlassEffect): Float =
    when (glassEffect) {
        BottomBarGlassEffect.Blur -> if (isLight) 0.22f else 0.14f
        BottomBarGlassEffect.LiquidGlass -> if (isLight) 0.34f else 0.24f
    }

internal fun bottomBarGlassShadowAlpha(isLight: Boolean, glassEffect: BottomBarGlassEffect): Float =
    when (glassEffect) {
        BottomBarGlassEffect.Blur -> if (isLight) 0.12f else 0.30f
        BottomBarGlassEffect.LiquidGlass -> if (isLight) 0.18f else 0.38f
    }

internal fun Color.simpleLuminance(): Float {
    return 0.2126f * red + 0.7152f * green + 0.0722f * blue
}
