package com.ella.music.ui.settings

import android.content.Context
import android.graphics.Typeface
import android.net.Uri
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ella.music.data.SettingsManager
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Slider
import top.yukonga.miuix.kmp.basic.SmallTopAppBar
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.basic.Check
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.icon.extended.Download
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun LyricFontScreen(
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val settingsManager = remember { SettingsManager(context) }
    val selectedFontPath by settingsManager.lyricFontPath.collectAsState(initial = "")
    val lyricFontWeight by settingsManager.lyricFontWeight.collectAsState(initial = 800)
    var fonts by remember { mutableStateOf(collectFontChoices(context)) }
    val isDark = MiuixTheme.colorScheme.background.luminance() < 0.5f
    val pageBackground = if (isDark) Color(0xFF101014) else Color(0xFFF4F4F7)
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            runCatching {
                withContext(Dispatchers.IO) { copyImportedFont(context, uri) }
            }.onSuccess { font ->
                settingsManager.setLyricFont(font.name, font.path)
                fonts = collectFontChoices(context)
                Toast.makeText(context, "已应用 ${font.name}", Toast.LENGTH_SHORT).show()
            }.onFailure {
                Toast.makeText(context, "字体导入失败", Toast.LENGTH_SHORT).show()
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(pageBackground)
            .windowInsetsPadding(WindowInsets.statusBars)
    ) {
        SmallTopAppBar(
            title = "歌词字体",
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = MiuixIcons.Regular.Back,
                        contentDescription = "返回",
                        tint = MiuixTheme.colorScheme.onSurface
                    )
                }
            },
            actions = {
                IconButton(
                    onClick = {
                        importLauncher.launch(
                            arrayOf(
                                "font/ttf",
                                "font/otf",
                                "application/x-font-ttf",
                                "application/x-font-otf",
                                "application/vnd.ms-opentype",
                                "application/octet-stream"
                            )
                        )
                    }
                ) {
                    Icon(
                        imageVector = MiuixIcons.Regular.Download,
                        contentDescription = "导入字体",
                        tint = MiuixTheme.colorScheme.onSurface
                    )
                }
            },
            color = pageBackground
        )

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp)
        ) {
            item {
                Spacer(modifier = Modifier.height(8.dp))
                Card(
                    modifier = Modifier.padding(vertical = 4.dp),
                    onClick = {
                        scope.launch {
                            settingsManager.clearLyricFont()
                            Toast.makeText(context, "已使用系统默认字体", Toast.LENGTH_SHORT).show()
                        }
                    }
                ) {
                    BasicComponent(
                        title = "系统默认",
                        summary = "使用应用默认字体渲染歌词",
                        endActions = {
                            if (selectedFontPath.isBlank()) {
                                Icon(
                                    imageVector = MiuixIcons.Basic.Check,
                                    contentDescription = null,
                                    tint = MiuixTheme.colorScheme.primary,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        }
                    )
                }
                Card(
                    modifier = Modifier.padding(vertical = 4.dp)
                ) {
                    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "歌词字重",
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = MiuixTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "当前 ${lyricFontWeight.coerceIn(100, 900)}",
                                    fontSize = 12.sp,
                                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                    modifier = Modifier.padding(top = 2.dp)
                                )
                            }
                            Text(
                                text = "春江花月夜",
                                fontSize = 18.sp,
                                fontWeight = FontWeight(lyricFontWeight.coerceIn(100, 900)),
                                color = MiuixTheme.colorScheme.onSurface
                            )
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                        Slider(
                            value = (lyricFontWeight.coerceIn(100, 900) - 100) / 800f,
                            onValueChange = { fraction ->
                                val weight = ((fraction.coerceIn(0f, 1f) * 8).toInt() + 1) * 100
                                scope.launch { settingsManager.setLyricFontWeight(weight) }
                            },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Row(modifier = Modifier.fillMaxWidth()) {
                            Text(text = "细", fontSize = 11.sp, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
                            Spacer(modifier = Modifier.weight(1f))
                            Text(text = "粗", fontSize = 11.sp, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
                        }
                    }
                }
                Text(
                    text = "系统字体",
                    fontSize = 14.sp,
                    color = MiuixTheme.colorScheme.primary,
                    modifier = Modifier.padding(start = 4.dp, top = 12.dp, bottom = 4.dp)
                )
            }

            items(fonts, key = { it.path }) { font ->
                FontChoiceItem(
                    font = font,
                    selected = selectedFontPath == font.path,
                    onClick = {
                        scope.launch {
                            settingsManager.setLyricFont(font.name, font.path)
                            Toast.makeText(context, "已应用 ${font.name}", Toast.LENGTH_SHORT).show()
                        }
                    },
                    onDelete = if (font.source == "已导入") {
                        {
                            scope.launch {
                                val deleted = withContext(Dispatchers.IO) {
                                    deleteImportedFont(font)
                                }

                                if (selectedFontPath == font.path) {
                                    settingsManager.clearLyricFont()
                                }

                                fonts = collectFontChoices(context)

                                Toast.makeText(
                                    context,
                                    if (deleted) "字体已删除" else "字体删除失败",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        }
                    } else {
                        null
                    }
                )
            }

            item {
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}

@Composable
private fun FontChoiceItem(
    font: FontChoice,
    selected: Boolean,
    onClick: () -> Unit,
    onDelete: (() -> Unit)? = null
) {
    val fontFamily = remember(font.path) { font.path.toFontFamilyOrNull() }

    Card(
        modifier = Modifier.padding(vertical = 4.dp),
        onClick = onClick
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = font.name,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                    color = MiuixTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "春江花月夜  Shape of You  123",
                    fontSize = 18.sp,
                    fontFamily = fontFamily,
                    color = MiuixTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 6.dp)
                )
                Text(
                    text = font.source,
                    fontSize = 12.sp,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
            if (selected) {
                Icon(
                    imageVector = MiuixIcons.Basic.Check,
                    contentDescription = null,
                    tint = MiuixTheme.colorScheme.primary,
                    modifier = Modifier.size(22.dp)
                )
            }

            if (onDelete != null) {
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = "删除",
                    fontSize = 13.sp,
                    color = MiuixTheme.colorScheme.primary,
                    modifier = Modifier.clickable(onClick = onDelete)
                )
            }
        }
    }
}

private data class FontChoice(
    val name: String,
    val path: String,
    val source: String
)

private fun collectFontChoices(context: Context): List<FontChoice> {
    val importedDir = File(context.filesDir, IMPORTED_FONT_DIR)
    val fontDirs = listOf(
        importedDir to "已导入",
        File("/system/fonts") to "系统",
        File("/product/fonts") to "系统",
        File("/system_ext/fonts") to "系统",
        File("/vendor/fonts") to "系统"
    )
    return fontDirs
        .flatMap { (dir, source) ->
            dir.listFiles()
                ?.asSequence()
                ?.filter { it.isFile && it.extension.lowercase() in SUPPORTED_FONT_EXTENSIONS && it.canRead() }
                ?.map { file -> FontChoice(file.nameWithoutExtension.cleanFontName(), file.absolutePath, source) }
                ?.toList()
                .orEmpty()
        }
        .distinctBy { it.path }
        .sortedWith(compareBy<FontChoice> { it.source != "已导入" }.thenBy { it.name.lowercase() })
}

private fun String.toFontFamilyOrNull(): FontFamily? {
    val file = File(this)
    if (!file.exists() || !file.canRead()) return null
    return runCatching { FontFamily(Typeface.createFromFile(file)) }.getOrNull()
}

private fun copyImportedFont(context: Context, uri: Uri): FontChoice {
    val rawName = context.resolveDisplayName(uri).ifBlank { "lyric_font.ttf" }
    val safeName = rawName.sanitizeFileName().ensureFontExtension()
    val dir = File(context.filesDir, IMPORTED_FONT_DIR).apply { mkdirs() }
    val target = File(dir, "${System.currentTimeMillis()}_$safeName")
    context.contentResolver.openInputStream(uri)?.use { input ->
        target.outputStream().use { output -> input.copyTo(output) }
    } ?: error("Unable to open font")
    return FontChoice(target.nameWithoutExtension.cleanFontName(), target.absolutePath, "已导入")
}

private fun Context.resolveDisplayName(uri: Uri): String {
    return contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
        if (cursor.moveToFirst()) cursor.getString(0).orEmpty() else ""
    }.orEmpty().ifBlank {
        uri.lastPathSegment.orEmpty().substringAfterLast('/')
    }
}

private fun deleteImportedFont(font: FontChoice): Boolean {
    if (font.source != "已导入") return false

    val file = File(font.path)
    if (!file.exists()) return true

    return runCatching {
        file.delete()
    }.getOrDefault(false)
}

private fun String.sanitizeFileName(): String {
    return replace(Regex("""[\\/:*?"<>|]+"""), "_").trim().ifBlank { "lyric_font.ttf" }
}

private fun String.ensureFontExtension(): String {
    return if (substringAfterLast('.', "").lowercase() in SUPPORTED_FONT_EXTENSIONS) this else "$this.ttf"
}

private fun String.cleanFontName(): String {
    return replace('_', ' ').replace('-', ' ').trim().ifBlank { "字体" }
}

private const val IMPORTED_FONT_DIR = "lyric_fonts"
private val SUPPORTED_FONT_EXTENSIONS = setOf("ttf", "otf", "ttc")
