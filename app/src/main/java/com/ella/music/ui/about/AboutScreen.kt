package com.ella.music.ui.about

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ella.music.R
import com.ella.music.BuildConfig
import com.ella.music.ui.effect.BgEffectBackground
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTitle
import com.ella.music.ui.components.EllaSmallTopAppBar
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.blur.BlendColorEntry
import top.yukonga.miuix.kmp.blur.BlurBlendMode
import top.yukonga.miuix.kmp.blur.BlurColors
import top.yukonga.miuix.kmp.blur.BlurDefaults
import top.yukonga.miuix.kmp.blur.isRenderEffectSupported
import top.yukonga.miuix.kmp.blur.isRuntimeShaderSupported
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.blur.rememberLayerBackdrop
import top.yukonga.miuix.kmp.blur.textureBlur
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.theme.MiuixTheme.colorScheme
import top.yukonga.miuix.kmp.utils.overScrollVertical
import top.yukonga.miuix.kmp.utils.scrollEndHaptic

@Composable
fun AboutScreen(
    onBack: () -> Unit,
    onNavigateToUpdate: () -> Unit = {},
) {
    val scrollBehavior = MiuixScrollBehavior()
    val lazyListState = rememberLazyListState()
    var logoHeightPx by remember { mutableIntStateOf(0) }

    val scrollProgress by remember {
        derivedStateOf {
            if (logoHeightPx <= 0) 0f
            else {
                val index = lazyListState.firstVisibleItemIndex
                val offset = lazyListState.firstVisibleItemScrollOffset
                if (index > 0) 1f else (offset.toFloat() / logoHeightPx).coerceIn(0f, 1f)
            }
        }
    }

    Scaffold(
        topBar = {
            EllaSmallTopAppBar(
                title = stringResource(R.string.about),
                scrollBehavior = scrollBehavior,
                color = colorScheme.surface.copy(alpha = if (scrollProgress == 1f) 1f else 0f),
                titleColor = colorScheme.onSurface.copy(alpha = scrollProgress),
                defaultWindowInsetsPadding = false,
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = MiuixIcons.Regular.Back,
                            contentDescription = stringResource(R.string.common_back),
                            tint = colorScheme.onSurface,
                        )
                    }
                },
            )
        },
    ) { innerPadding ->
        AboutContent(
            padding = PaddingValues(top = innerPadding.calculateTopPadding()),
            scrollBehavior = scrollBehavior,
            scrollProgress = scrollProgress,
            lazyListState = lazyListState,
            onLogoHeightChanged = { logoHeightPx = it },
            onNavigateToUpdate = onNavigateToUpdate,
        )
    }
}

@Composable
private fun AboutContent(
    padding: PaddingValues,
    scrollBehavior: top.yukonga.miuix.kmp.basic.ScrollBehavior,
    scrollProgress: Float,
    lazyListState: androidx.compose.foundation.lazy.LazyListState,
    onLogoHeightChanged: (Int) -> Unit,
    onNavigateToUpdate: () -> Unit,
) {
    val backdrop = rememberLayerBackdrop()
    val isDark = colorScheme.background.luminance() < 0.5f
    val blurEnable by remember { mutableStateOf(isRenderEffectSupported()) }
    val shaderSupported = remember { isRuntimeShaderSupported() }
    val uriHandler = LocalUriHandler.current

    val density = LocalDensity.current
    var logoHeightDp by remember { mutableStateOf(300.dp) }
    val logoLiftPx = with(density) { 96.dp.toPx() }

    val titleBlend = remember(isDark) {
        if (isDark) {
            emptyList()
        } else {
            listOf(
                BlendColorEntry(Color(0xcc4a4a4a.toInt()), BlurBlendMode.ColorBurn),
                BlendColorEntry(Color(0xff4f4f4f.toInt()), BlurBlendMode.LinearLight),
                BlendColorEntry(Color(0xff1af200.toInt()), BlurBlendMode.Lab),
            )
        }
    }

    val cardBlendColors = remember(isDark) {
        if (isDark) {
            listOf(
                BlendColorEntry(Color(0x4D1D2A7A), BlurBlendMode.Screen),
                BlendColorEntry(Color(0x332B1C60), BlurBlendMode.Lighten),
                BlendColorEntry(Color(0x1A3E2C78), BlurBlendMode.Luminosity),
            )
        } else {
            listOf(
                BlendColorEntry(Color(0x34F7D6FF), BlurBlendMode.Overlay),
                BlendColorEntry(Color(0xB3FFFFFF.toInt()), BlurBlendMode.HardLight),
            )
        }
    }

    BgEffectBackground(
        dynamicBackground = shaderSupported,
        modifier = Modifier.fillMaxSize(),
        bgModifier = Modifier.layerBackdrop(backdrop),
        effectBackground = shaderSupported,
        isDarkTheme = isDark,
        alpha = {
            val fade = 1f - scrollProgress
            if (isDark) 0.42f + 0.40f * fade else fade
        },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .graphicsLayer {
                    alpha = (1f - scrollProgress * 1.35f).coerceIn(0f, 1f)
                    translationY = -logoLiftPx * scrollProgress
                }
                .padding(top = padding.calculateTopPadding() + 120.dp)
                .onSizeChanged { size -> with(density) { logoHeightDp = size.height.toDp() } },
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(
                painter = painterResource(id = R.drawable.ic_flyme_ticker),
                contentDescription = null,
                tint = colorScheme.primary.copy(alpha = if (isDark) 0.72f else 0.58f),
                modifier = Modifier
                    .size(112.dp)
                    .padding(bottom = 18.dp)
            )
            Text(
                modifier = Modifier
                    .padding(bottom = 5.dp)
                    .then(
                        if (blurEnable && !isDark) Modifier.textureBlur(
                            backdrop = backdrop,
                            shape = RoundedCornerShape(16.dp),
                            blurRadius = 150f,
                            noiseCoefficient = BlurDefaults.NoiseCoefficient,
                            colors = BlurColors(blendColors = titleBlend),
                            contentBlendMode = BlendMode.DstIn,
                            enabled = true,
                        ) else Modifier
                    ),
                text = "Ella Music",
                color = colorScheme.onBackground,
                fontWeight = FontWeight.Bold,
                fontSize = 35.sp,
            )
            Text(
                modifier = Modifier.fillMaxWidth(),
                color = colorScheme.onSurfaceVariantSummary,
                text = "v${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
                fontSize = 14.sp,
                textAlign = TextAlign.Center,
            )
            Text(
                modifier = Modifier.fillMaxWidth(),
                color = colorScheme.onSurfaceVariantSummary.copy(alpha = 0.7f),
                text = stringResource(R.string.about_build_time, BuildConfig.BUILD_TIME),
                fontSize = 12.sp,
                textAlign = TextAlign.Center,
            )
        }

        LazyColumn(
            state = lazyListState,
            modifier = Modifier
                .fillMaxSize()
                .scrollEndHaptic()
                .overScrollVertical()
                .nestedScroll(scrollBehavior.nestedScrollConnection),
            contentPadding = PaddingValues(top = padding.calculateTopPadding()),
            overscrollEffect = null,
        ) {
            item(key = "logoSpacer") {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(logoHeightDp + 218.dp)
                        .onSizeChanged { size -> onLogoHeightChanged(size.height) },
                )
            }

            item {
                SmallTitle(text = stringResource(R.string.about_project))
                FrostedCard(backdrop = backdrop, blurEnable = blurEnable, cardBlendColors = cardBlendColors) {
                    BasicComponent(
                        title = stringResource(R.string.about_update),
                        summary = stringResource(R.string.about_update_summary),
                        onClick = onNavigateToUpdate,
                    )
                    BasicComponent(
                        title = stringResource(R.string.about_open_source),
                        summary = "Apache-2.0",
                        onClick = { uriHandler.openUri("https://www.apache.org/licenses/LICENSE-2.0") },
                    )
                }
            }

            item {
                SmallTitle(text = stringResource(R.string.about_acknowledgements))
                FrostedCard(backdrop = backdrop, blurEnable = blurEnable, cardBlendColors = cardBlendColors) {
                    BasicComponent(
                        title = "Codex (GPT-5.5)",
                        summary = "1.0.2 至今主要开发与代码协作",
                    )
                    BasicComponent(
                        title = "BetterLyrics",
                        summary = "播放页封面模糊背景和歌词视觉效果参考",
                        onClick = { uriHandler.openUri("https://github.com/jayfunc/BetterLyrics") },
                    )
                    BasicComponent(
                        title = "SPlayer",
                        summary = "播放页动效和歌词体验参考",
                        onClick = { uriHandler.openUri("https://github.com/imsyy/SPlayer") },
                    )
                    BasicComponent(
                        title = "光锥音乐",
                        summary = "部分界面视觉与 UI 效果参考",
                        onClick = { uriHandler.openUri("https://coneplayer.trantor.ink/") },
                    )
                    BasicComponent(
                        title = "Lyrico",
                        summary = "外部标签编辑器适配与日志页面交互参考",
                        onClick = { uriHandler.openUri("https://github.com/Replica0110/Lyrico") },
                    )
                    BasicComponent(
                        title = "Mimo-V2.5-Pro",
                        summary = "1.0.0 至 1.0.1 早期版本主要开发",
                    )
                }
            }

            item {
                SmallTitle(text = stringResource(R.string.about_open_source_projects))
                FrostedCard(backdrop = backdrop, blurEnable = blurEnable, cardBlendColors = cardBlendColors) {
                    BasicComponent(
                        title = "Miuix",
                        summary = "MIUI/HyperOS 风格 Compose UI 组件库（Apache-2.0）",
                        onClick = { uriHandler.openUri("https://github.com/compose-miuix-ui/miuix") },
                    )
                    BasicComponent(
                        title = "AndroidX Media3",
                        summary = "播放、媒体会话与 ExoPlayer 扩展（Apache-2.0）",
                        onClick = { uriHandler.openUri("https://github.com/androidx/media") },
                    )
                    BasicComponent(
                        title = "FFmpeg",
                        summary = "ALAC 等音频格式软件解码（LGPL-2.1）",
                        onClick = { uriHandler.openUri("https://ffmpeg.org") },
                    )
                    BasicComponent(
                        title = "Kyant TagLib",
                        summary = "Android/Kotlin TagLib 绑定（Apache-2.0）",
                        onClick = { uriHandler.openUri("https://github.com/Kyant0/TagLib") },
                    )
                    BasicComponent(
                        title = "Lyricon",
                        summary = "词幕 Provider API 与状态栏歌词（Apache-2.0）",
                        onClick = { uriHandler.openUri("https://github.com/proify/lyricon") },
                    )
                    BasicComponent(
                        title = "SuperLyricApi",
                        summary = "SuperLyric 歌词发布 API（LGPL-2.1）",
                        onClick = { uriHandler.openUri("https://github.com/HChenX/SuperLyricApi") },
                    )
                    BasicComponent(
                        title = "LyricGetter-API",
                        summary = "Lyric Getter 原文歌词传递 API（LGPL-2.1）",
                        onClick = { uriHandler.openUri("https://github.com/xiaowine/Lyric-Getter-Api") },
                    )
                    BasicComponent(
                        title = "Lyrico",
                        summary = "歌曲标签读取与应用日志页面参考（Apache-2.0）",
                        onClick = { uriHandler.openUri("https://github.com/Replica0110/Lyrico") },
                    )
                    BasicComponent(
                        title = "163KeyDecrypter",
                        summary = "网易云音乐 163 key 解密流程参考（MIT）",
                        onClick = { uriHandler.openUri("https://github.com/lycode404/163KeyDecrypter") },
                    )
                    BasicComponent(
                        title = "Kyant Backdrop",
                        summary = "液态玻璃与背景模糊效果（Apache-2.0）",
                        onClick = { uriHandler.openUri("https://github.com/Kyant0/AndroidLiquidGlass") },
                    )
                    BasicComponent(
                        title = "Coil",
                        summary = "Kotlin 图片加载库（Apache-2.0）",
                        onClick = { uriHandler.openUri("https://github.com/coil-kt/coil") },
                    )
                    BasicComponent(
                        title = "quickjs-wrapper Android",
                        summary = "LX Music API JavaScript 运行时（Apache-2.0）",
                        onClick = { uriHandler.openUri("https://github.com/HarlonWang/quickjs-wrapper") },
                    )
                    BasicComponent(
                        title = "LX Music Mobile",
                        summary = "在线音乐源与 API 适配参考（Apache-2.0）",
                        onClick = { uriHandler.openUri("https://github.com/lyswhut/lx-music-mobile") },
                    )
                    BasicComponent(
                        title = "accompanist-lyrics-core",
                        summary = "歌词解析与 TTML/LRC 结构参考（Apache-2.0）",
                        onClick = { uriHandler.openUri("https://github.com/6xingyv/accompanist-lyrics-core") },
                    )
                }
            }

            item {
                Spacer(
                    Modifier
                        .height(160.dp)
                        .navigationBarsPadding()
                )
            }
        }
    }
}

@Composable
private fun FrostedCard(
    backdrop: top.yukonga.miuix.kmp.blur.LayerBackdrop,
    blurEnable: Boolean,
    cardBlendColors: List<BlendColorEntry>,
    content: @Composable () -> Unit,
) {
    val isDark = colorScheme.background.luminance() < 0.5f
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp)
            .padding(bottom = 12.dp)
            .then(
                if (blurEnable && !isDark) Modifier.textureBlur(
                    backdrop = backdrop,
                    shape = RoundedCornerShape(16.dp),
                    blurRadius = 64f,
                    noiseCoefficient = BlurDefaults.NoiseCoefficient,
                    colors = BlurColors(blendColors = cardBlendColors),
                    enabled = true,
                ) else Modifier
            ),
        colors = CardDefaults.defaultColors(
            if (isDark) {
                colorScheme.surfaceContainer
            } else if (blurEnable) {
                Color.Transparent
            } else {
                colorScheme.surfaceContainer
            },
            colorScheme.onSurface,
        ),
    ) {
        content()
    }
}
