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
    val slant = if (italic) FontStyle.FONT_SLANT_ITALIC else FontStyle.FONT_SLANT_UPRIGHT

    // Build Font objects with the exact weight so variable fonts (e.g. Inter Variable)
    // select the correct weight axis instead of falling back to synthetic bolding.
    val customFamilies = listOf(paths.western, paths.cjk)
        .mapNotNull { path -> fontFamilyFromPath(path, safeWeight, slant) }

    if (customFamilies.isEmpty()) {
        val fallback = if (boldFallback) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
        return Typeface.create(fallback, safeWeight, italic)
    }

    return runCatching {
        val builder = Typeface.CustomFallbackBuilder(customFamilies.first())
            .setSystemFallback("sans-serif")
            .setStyle(FontStyle(safeWeight, slant))
        customFamilies.drop(1).forEach(builder::addCustomFallback)
        builder.build()
    }.getOrElse {
        val firstPath = listOf(paths.western, paths.cjk).firstOrNull(::isReadableFontPath)
        val base = firstPath?.let { runCatching { Typeface.createFromFile(it) }.getOrNull() }
            ?: if (boldFallback) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
        Typeface.create(base, safeWeight, italic)
    }
}

/** Loads either a single custom font or the encoded western/CJK fallback pair. */
internal fun loadAndroidTypeface(
    fontPath: String,
    weight: Int,
    italic: Boolean,
    boldFallback: Boolean
): Typeface {
    ScriptFontPaths.decode(fontPath)?.let { paths ->
        return loadScriptAwareTypeface(paths, weight, italic, boldFallback)
    }
    val safeWeight = weight.coerceIn(100, 900)
    val base = fontPath
        .takeIf(::isReadableFontPath)
        ?.let { path -> runCatching { Typeface.createFromFile(path) }.getOrNull() }
        ?: if (boldFallback) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
    return Typeface.create(base, safeWeight, italic)
}

private fun fontFamilyFromPath(path: String, weight: Int, slant: Int): FontFamily? {
    if (!isReadableFontPath(path)) return null
    return runCatching {
        val font = Font.Builder(File(path))
            .setWeight(weight)
            .setSlant(slant)
            .build()
        FontFamily.Builder(font).build()
    }.getOrNull()
}

private fun isReadableFontPath(path: String): Boolean =
    path.isNotBlank() && path != SYSTEM_FONT_SENTINEL && File(path).let { it.isFile && it.canRead() }

private const val SYSTEM_FONT_SENTINEL = "__system_default__"
