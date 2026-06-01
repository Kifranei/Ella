package com.ella.music.ui.about

import android.content.Intent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ella.music.data.AppNetworkLoggingInterceptor
import com.ella.music.BuildConfig
import com.ella.music.R
import com.ella.music.ui.components.ellaPageBackground
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import com.ella.music.ui.components.EllaSmallTopAppBar
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.icon.extended.Refresh
import top.yukonga.miuix.kmp.theme.MiuixTheme
import java.util.concurrent.TimeUnit

@Composable
fun UpdateScreen(
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val isDark = MiuixTheme.colorScheme.background.luminance() < 0.5f
    val pageBackground = ellaPageBackground()
    val cardColor = if (isDark) Color(0xFF1D1D21) else Color.White
    var state by remember { mutableStateOf<UpdateUiState>(UpdateUiState.Loading) }

    fun checkUpdate() {
        state = UpdateUiState.Loading
        scope.launch {
            state = withContext(Dispatchers.IO) {
                runCatching { fetchLatestRelease() }
                    .fold(
                        onSuccess = { release ->
                            val hasUpdate = compareVersionNames(release.versionName, BuildConfig.VERSION_NAME) > 0
                            UpdateUiState.Ready(release = release, hasUpdate = hasUpdate)
                        },
                        onFailure = { error ->
                            UpdateUiState.Error(error.localizedMessage ?: context.getString(R.string.update_check_failed))
                        }
                    )
            }
        }
    }

    LaunchedEffect(Unit) {
        delay(250)
        checkUpdate()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(pageBackground)
            .windowInsetsPadding(WindowInsets.statusBars)
    ) {
        EllaSmallTopAppBar(
            title = stringResource(R.string.about_update),
            color = pageBackground,
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = MiuixIcons.Regular.Back,
                        contentDescription = stringResource(R.string.common_back),
                        tint = MiuixTheme.colorScheme.onSurface
                    )
                }
            },
            actions = {
                IconButton(onClick = ::checkUpdate) {
                    Icon(
                        imageVector = MiuixIcons.Regular.Refresh,
                        contentDescription = stringResource(R.string.update_check_action),
                        tint = MiuixTheme.colorScheme.onSurface
                    )
                }
            }
        )

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                val updateButtonTarget = state.updateButtonTargetUrl()
                UpdatePressureHero(
                    state = state,
                    isDark = isDark,
                    buttonText = state.updateButtonText(),
                    buttonEnabled = updateButtonTarget != null,
                    onButtonClick = {
                        updateButtonTarget?.let(context::openUrl)
                    }
                )
            }

            item {
                Card(
                    colors = CardDefaults.defaultColors(color = cardColor)
                ) {
                    BasicComponent(
                        title = stringResource(R.string.update_current_version),
                        summary = "v${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})"
                    )
                    when (val current = state) {
                        UpdateUiState.Loading -> {
                            BasicComponent(
                                title = stringResource(R.string.update_checking),
                                summary = stringResource(R.string.update_fetching_latest)
                            )
                        }
                        is UpdateUiState.Error -> {
                            BasicComponent(
                                title = stringResource(R.string.update_check_failed_title),
                                summary = current.message
                            )
                        }
                        is UpdateUiState.Ready -> {
                            BasicComponent(
                                title = if (current.hasUpdate) stringResource(R.string.update_new_version_found) else stringResource(R.string.update_already_latest),
                                summary = stringResource(R.string.update_latest_version, current.release.tagName)
                            )
                        }
                    }
                }
            }

            val release = (state as? UpdateUiState.Ready)?.release
            if (release != null) {
                item {
                    Card(
                        colors = CardDefaults.defaultColors(color = cardColor)
                    ) {
                        BasicComponent(
                            title = release.title,
                            summary = release.publishedAt.takeIf { it.isNotBlank() } ?: release.tagName
                        )
                        ReleaseMarkdown(
                            markdown = release.body.ifBlank { stringResource(R.string.update_empty_changelog) },
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
                        )
                    }
                }
            }

            item {
                Spacer(
                    modifier = Modifier
                        .height(120.dp)
                        .navigationBarsPadding()
                )
            }
        }
    }
}

@Composable
private fun UpdatePressureHero(
    state: UpdateUiState,
    isDark: Boolean,
    buttonText: String,
    buttonEnabled: Boolean,
    onButtonClick: () -> Unit
) {
    val foldProgress = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        delay(80)
        foldProgress.animateTo(
            targetValue = 1f,
            animationSpec = tween(
                durationMillis = 760,
                easing = CubicBezierEasing(0.18f, 0.82f, 0.22f, 1f)
            )
        )
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(276.dp)
            .graphicsLayer {
                val progress = foldProgress.value
                cameraDistance = 34f * density
                transformOrigin = TransformOrigin(0.5f, 0f)
                rotationX = -58f * (1f - progress)
                rotationY = -4f * (1f - progress)
                translationY = -18.dp.toPx() * (1f - progress)
                scaleX = 0.96f + 0.04f * progress
                scaleY = 0.94f + 0.06f * progress
                alpha = 0.88f + 0.12f * progress
                shadowElevation = 18.dp.toPx()
            }
            .clip(RoundedCornerShape(28.dp))
            .background(
                Brush.linearGradient(
                    colors = updateHeroColors(isDark)
                )
            )
    ) {
        Box(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .offset(x = 42.dp, y = (-18).dp)
                .size(168.dp)
                .background(Color.White.copy(alpha = 0.15f), CircleShape)
        )
        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 96.dp, bottom = 58.dp)
                .size(112.dp)
                .background(Color.White.copy(alpha = 0.10f), CircleShape)
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(28.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Spacer(modifier = Modifier.height(2.dp))

            Column {
                Text(
                    text = state.heroTitle(),
                    color = Color.White,
                    fontSize = 30.sp,
                    fontWeight = FontWeight.ExtraBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = state.heroSummary(),
                    color = Color.White.copy(alpha = 0.72f),
                    fontSize = 14.sp,
                    lineHeight = 20.sp,
                    modifier = Modifier.padding(top = 8.dp)
                )
                Button(
                    onClick = onButtonClick,
                    enabled = buttonEnabled,
                    modifier = Modifier.padding(top = 20.dp)
                ) {
                    Text(text = buttonText)
                }
            }
        }
    }
}

private fun updateHeroColors(isDark: Boolean): List<Color> =
    if (isDark) {
        listOf(
            Color(0xFF202231),
            Color(0xFF1B2B45),
            Color(0xFF28385E)
        )
    } else {
        listOf(
            Color(0xFF1A73FF),
            Color(0xFF6B8CFF),
            Color(0xFFFF7A94)
        )
    }

@Composable
private fun ReleaseMarkdown(
    markdown: String,
    modifier: Modifier = Modifier
) {
    val blocks = remember(markdown) { parseMarkdownBlocks(markdown) }
    val primary = MiuixTheme.colorScheme.onSurface
    val secondary = MiuixTheme.colorScheme.onSurfaceVariantSummary
    val accent = MiuixTheme.colorScheme.primary
    val codeBackground = MiuixTheme.colorScheme.onSurface.copy(alpha = 0.08f)

    SelectionContainer {
        Column(
            modifier = modifier,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            blocks.forEach { block ->
                when (block) {
                    is MarkdownBlock.Heading -> {
                        BasicText(
                            text = inlineMarkdown(block.text, accent, codeBackground),
                            style = TextStyle(
                                color = primary,
                                fontSize = if (block.level <= 2) 18.sp else 15.sp,
                                lineHeight = if (block.level <= 2) 24.sp else 21.sp,
                                fontWeight = FontWeight.Bold
                            )
                        )
                    }
                    is MarkdownBlock.Paragraph -> {
                        BasicText(
                            text = inlineMarkdown(block.text, accent, codeBackground),
                            style = TextStyle(
                                color = secondary,
                                fontSize = 13.sp,
                                lineHeight = 19.sp
                            )
                        )
                    }
                    is MarkdownBlock.Bullet -> {
                        Row {
                            Text(
                                text = "•",
                                color = accent,
                                fontSize = 13.sp,
                                modifier = Modifier.width(18.dp)
                            )
                            BasicText(
                                text = inlineMarkdown(block.text, accent, codeBackground),
                                style = TextStyle(
                                    color = secondary,
                                    fontSize = 13.sp,
                                    lineHeight = 19.sp
                                ),
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                    is MarkdownBlock.Numbered -> {
                        Row {
                            Text(
                                text = "${block.number}.",
                                color = accent,
                                fontSize = 13.sp,
                                modifier = Modifier.width(28.dp)
                            )
                            BasicText(
                                text = inlineMarkdown(block.text, accent, codeBackground),
                                style = TextStyle(
                                    color = secondary,
                                    fontSize = 13.sp,
                                    lineHeight = 19.sp
                                ),
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                    is MarkdownBlock.Quote -> {
                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(MiuixTheme.colorScheme.primary.copy(alpha = 0.08f))
                                .padding(horizontal = 10.dp, vertical = 8.dp)
                        ) {
                            BasicText(
                                text = inlineMarkdown(block.text, accent, codeBackground),
                                style = TextStyle(
                                    color = secondary,
                                    fontSize = 13.sp,
                                    lineHeight = 19.sp
                                )
                            )
                        }
                    }
                    is MarkdownBlock.Code -> {
                        BasicText(
                            text = block.text,
                            style = TextStyle(
                                color = primary,
                                fontSize = 12.sp,
                                lineHeight = 18.sp
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(codeBackground)
                                .padding(10.dp)
                        )
                    }
                }
            }
        }
    }
}

private sealed interface MarkdownBlock {
    data class Heading(val level: Int, val text: String) : MarkdownBlock
    data class Paragraph(val text: String) : MarkdownBlock
    data class Bullet(val text: String) : MarkdownBlock
    data class Numbered(val number: Int, val text: String) : MarkdownBlock
    data class Quote(val text: String) : MarkdownBlock
    data class Code(val text: String) : MarkdownBlock
}

private fun parseMarkdownBlocks(markdown: String): List<MarkdownBlock> {
    val blocks = mutableListOf<MarkdownBlock>()
    val lines = markdown.replace("\r\n", "\n").split('\n')
    val paragraph = StringBuilder()
    var codeFence = false
    val code = StringBuilder()

    fun flushParagraph() {
        val text = paragraph.toString().trim()
        if (text.isNotBlank()) blocks += MarkdownBlock.Paragraph(text)
        paragraph.clear()
    }

    for (rawLine in lines) {
        val line = rawLine.trimEnd()
        val trimmed = line.trim()
        if (trimmed.startsWith("```")) {
            if (codeFence) {
                blocks += MarkdownBlock.Code(code.toString().trimEnd())
                code.clear()
            } else {
                flushParagraph()
            }
            codeFence = !codeFence
            continue
        }
        if (codeFence) {
            code.appendLine(line)
            continue
        }
        if (trimmed.isBlank()) {
            flushParagraph()
            continue
        }
        val heading = Regex("^(#{1,6})\\s+(.+)$").matchEntire(trimmed)
        val bullet = Regex("^[-*+]\\s+(.+)$").matchEntire(trimmed)
        val numbered = Regex("^(\\d+)\\.\\s+(.+)$").matchEntire(trimmed)
        val quote = Regex("^>\\s?(.+)$").matchEntire(trimmed)
        when {
            heading != null -> {
                flushParagraph()
                blocks += MarkdownBlock.Heading(heading.groupValues[1].length, heading.groupValues[2])
            }
            bullet != null -> {
                flushParagraph()
                blocks += MarkdownBlock.Bullet(bullet.groupValues[1])
            }
            numbered != null -> {
                flushParagraph()
                blocks += MarkdownBlock.Numbered(numbered.groupValues[1].toIntOrNull() ?: 1, numbered.groupValues[2])
            }
            quote != null -> {
                flushParagraph()
                blocks += MarkdownBlock.Quote(quote.groupValues[1])
            }
            else -> {
                if (paragraph.isNotEmpty()) paragraph.append(' ')
                paragraph.append(trimmed)
            }
        }
    }
    if (codeFence && code.isNotBlank()) blocks += MarkdownBlock.Code(code.toString().trimEnd())
    flushParagraph()
    return blocks.ifEmpty { listOf(MarkdownBlock.Paragraph(markdown)) }
}

private fun inlineMarkdown(
    text: String,
    accent: Color,
    codeBackground: Color
) = buildAnnotatedString {
    val pattern = Regex("""(\*\*[^*]+\*\*|`[^`]+`|\[[^]]+]\([^)]+\))""")
    var cursor = 0
    pattern.findAll(text).forEach { match ->
        append(text.substring(cursor, match.range.first))
        val token = match.value
        when {
            token.startsWith("**") -> withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                append(token.removeSurrounding("**"))
            }
            token.startsWith("`") -> withStyle(SpanStyle(color = accent, background = codeBackground)) {
                append(token.removeSurrounding("`"))
            }
            token.startsWith("[") -> {
                val label = token.substringAfter('[').substringBefore("](")
                withStyle(SpanStyle(color = accent, fontWeight = FontWeight.Medium)) {
                    append(label)
                }
            }
            else -> append(token)
        }
        cursor = match.range.last + 1
    }
    append(text.substring(cursor))
}

private sealed interface UpdateUiState {
    data object Loading : UpdateUiState
    data class Ready(val release: GithubRelease, val hasUpdate: Boolean) : UpdateUiState
    data class Error(val message: String) : UpdateUiState
}

private data class GithubRelease(
    val tagName: String,
    val title: String,
    val body: String,
    val htmlUrl: String,
    val downloadUrl: String?,
    val publishedAt: String
) {
    val versionName: String get() = tagName.trim().removePrefix("v").removePrefix("V")
}

@Composable
private fun UpdateUiState.heroTitle(): String = when (this) {
    UpdateUiState.Loading -> stringResource(R.string.update_checking)
    is UpdateUiState.Error -> stringResource(R.string.update_unavailable)
    is UpdateUiState.Ready -> if (hasUpdate) stringResource(R.string.update_found_version, release.tagName) else stringResource(R.string.update_already_latest)
}

@Composable
private fun UpdateUiState.heroSummary(): String = when (this) {
    UpdateUiState.Loading -> stringResource(R.string.update_connecting_github)
    is UpdateUiState.Error -> message
    is UpdateUiState.Ready -> if (hasUpdate) {
        stringResource(R.string.update_has_update_summary, BuildConfig.VERSION_NAME)
    } else {
        stringResource(R.string.update_no_update_summary, BuildConfig.VERSION_NAME)
    }
}

@Composable
private fun UpdateUiState.updateButtonText(): String = when (this) {
    UpdateUiState.Loading -> stringResource(R.string.update_checking_short)
    is UpdateUiState.Error -> stringResource(R.string.update_view_github)
    is UpdateUiState.Ready -> if (hasUpdate) stringResource(R.string.update_download) else stringResource(R.string.update_view_github)
}

private fun UpdateUiState.updateButtonTargetUrl(): String? = when (this) {
    UpdateUiState.Loading -> null
    is UpdateUiState.Error -> GITHUB_RELEASES_URL
    is UpdateUiState.Ready -> if (hasUpdate) {
        release.downloadUrl ?: release.htmlUrl
    } else {
        release.htmlUrl.ifBlank { GITHUB_RELEASES_URL }
    }
}

private const val GITHUB_RELEASES_URL = "https://github.com/Kifranei/Ella/releases"

private fun fetchLatestRelease(): GithubRelease {
    val client = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(12, TimeUnit.SECONDS)
        .addInterceptor(AppNetworkLoggingInterceptor("UpdateCheck"))
        .build()
    val request = Request.Builder()
        .url("https://api.github.com/repos/Kifranei/Ella/releases/latest")
        .header("Accept", "application/vnd.github+json")
        .header("User-Agent", "Ella-Music/${BuildConfig.VERSION_NAME}")
        .build()
    client.newCall(request).execute().use { response ->
        if (!response.isSuccessful) error("GitHub returned HTTP ${response.code}")
        val json = JSONObject(response.body?.string().orEmpty())
        val assets = json.optJSONArray("assets") ?: JSONArray()
        val apkUrl = (0 until assets.length())
            .asSequence()
            .mapNotNull { index -> assets.optJSONObject(index) }
            .firstOrNull { asset ->
                asset.optString("name").endsWith(".apk", ignoreCase = true)
            }
            ?.optString("browser_download_url")
            ?.takeIf { it.isNotBlank() }
        return GithubRelease(
            tagName = json.optString("tag_name").ifBlank { json.optString("name") },
            title = json.optString("name").ifBlank { json.optString("tag_name") },
            body = json.optString("body"),
            htmlUrl = json.optString("html_url").ifBlank { "https://github.com/Kifranei/Ella/releases" },
            downloadUrl = apkUrl,
            publishedAt = json.optString("published_at").take(10)
        )
    }
}

private fun compareVersionNames(left: String, right: String): Int {
    val leftParts = left.versionParts()
    val rightParts = right.versionParts()
    val count = maxOf(leftParts.size, rightParts.size)
    for (index in 0 until count) {
        val result = (leftParts.getOrNull(index) ?: 0).compareTo(rightParts.getOrNull(index) ?: 0)
        if (result != 0) return result
    }
    return 0
}

private fun String.versionParts(): List<Int> =
    trim()
        .removePrefix("v")
        .removePrefix("V")
        .split('.', '-', '_')
        .mapNotNull { part -> part.takeWhile { it.isDigit() }.toIntOrNull() }

private fun android.content.Context.openUrl(url: String) {
    if (url.isBlank()) return
    runCatching {
        startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}
