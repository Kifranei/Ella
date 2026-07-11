package com.ella.music.ui.components

import android.graphics.Typeface
import android.graphics.fonts.Font
import android.graphics.fonts.FontFamily
import android.graphics.fonts.FontStyle
import java.io.File
import java.util.Base64

internal data class ScriptFontPaths(
    val western: String,
    val cjk: String
) {
    fun encode(): String = listOf(western, cjk)
        .joinToString(SEPARATOR) { value ->
            Base64.getUrlEncoder().withoutPadding().encodeToString(value.toByteArray(Charsets.UTF_8))
        }
        .let { "$PREFIX$it" }

    companion object {
        private const val PREFIX = "script-font:v1:"
        private const val SEPARATOR = "."

        fun decode(value: String): ScriptFontPaths? {
            if (!value.startsWith(PREFIX)) return null
            val parts = value.removePrefix(PREFIX).split(SEPARATOR, limit = 2)
            if (parts.size != 2) return null
            return runCatching {
                ScriptFontPaths(
                    western = String(Base64.getUrlDecoder().decode(parts[0]), Charsets.UTF_8),
                    cjk = String(Base64.getUrlDecoder().decode(parts[1]), Charsets.UTF_8)
                )
            }.getOrNull()
        }
    }
}

internal fun loadScriptAwareTypeface(
    paths: ScriptFontPaths,
    weight: Int,
    italic: Boolean,
    boldFallback: Boolean
): Typeface {
    val safeWeight = weight.coerceIn(100, 900)
    val customFamilies = listOf(paths.western, paths.cjk)
        .mapNotNull(::fontFamilyFromPath)

    if (customFamilies.isEmpty()) {
        val fallback = if (boldFallback) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
        return Typeface.create(fallback, safeWeight, italic)
    }

    return runCatching {
        val builder = Typeface.CustomFallbackBuilder(customFamilies.first())
            .setSystemFallback("sans-serif")
            .setStyle(
                FontStyle(
                    safeWeight,
                    if (italic) FontStyle.FONT_SLANT_ITALIC else FontStyle.FONT_SLANT_UPRIGHT
                )
            )
        customFamilies.drop(1).forEach(builder::addCustomFallback)
        builder.build()
    }.getOrElse {
        val firstPath = listOf(paths.western, paths.cjk).firstOrNull(::isReadableFontPath)
        val base = firstPath?.let { runCatching { Typeface.createFromFile(it) }.getOrNull() }
            ?: if (boldFallback) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
        Typeface.create(base, safeWeight, italic)
    }
}

private fun fontFamilyFromPath(path: String): FontFamily? {
    if (!isReadableFontPath(path)) return null
    return runCatching {
        FontFamily.Builder(Font.Builder(File(path)).build()).build()
    }.getOrNull()
}

private fun isReadableFontPath(path: String): Boolean =
    path.isNotBlank() && path != SYSTEM_FONT_SENTINEL && File(path).let { it.isFile && it.canRead() }

private const val SYSTEM_FONT_SENTINEL = "__system_default__"
