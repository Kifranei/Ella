package com.ella.music.ui.about

import androidx.compose.foundation.isSystemInDarkTheme
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
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
import top.yukonga.miuix.kmp.basic.SmallTopAppBar
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
import top.yukonga.miuix.kmp.icon.extended.Music
import top.yukonga.miuix.kmp.shapes.SmoothRoundedCornerShape
import top.yukonga.miuix.kmp.theme.MiuixTheme.colorScheme
import top.yukonga.miuix.kmp.utils.overScrollVertical
import top.yukonga.miuix.kmp.utils.scrollEndHaptic

@Composable
fun AboutScreen(
    onBack: () -> Unit
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
            SmallTopAppBar(
                title = "关于",
                scrollBehavior = scrollBehavior,
                color = colorScheme.surface.copy(alpha = if (scrollProgress == 1f) 1f else 0f),
                titleColor = colorScheme.onSurface.copy(alpha = scrollProgress),
                defaultWindowInsetsPadding = false,
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = MiuixIcons.Regular.Back,
                            contentDescription = "返回",
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
) {
    val backdrop = rememberLayerBackdrop()
    val isDark = isSystemInDarkTheme()
    val blurEnable by remember { mutableStateOf(isRenderEffectSupported()) }
    val shaderSupported = remember { isRuntimeShaderSupported() }
    val uriHandler = LocalUriHandler.current

    val density = LocalDensity.current
    var logoHeightDp by remember { mutableStateOf(300.dp) }

    val titleBlend = remember(isDark) {
        if (isDark) {
            listOf(
                BlendColorEntry(Color(0xe6a1a1a1.toInt()), BlurBlendMode.ColorDodge),
                BlendColorEntry(Color(0x4de6e6e6), BlurBlendMode.LinearLight),
                BlendColorEntry(Color(0xff1af500.toInt()), BlurBlendMode.Lab),
            )
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
                BlendColorEntry(Color(0x4DA9A9A9), BlurBlendMode.Luminosity),
                BlendColorEntry(Color(0x1A9C9C9C), BlurBlendMode.PlusDarker),
            )
        } else {
            listOf(
                BlendColorEntry(Color(0x340034F9), BlurBlendMode.Overlay),
                BlendColorEntry(Color(0xB3FFFFFF.toInt()), BlurBlendMode.HardLight),
            )
        }
    }

    BgEffectBackground(
        dynamicBackground = shaderSupported,
        modifier = Modifier.fillMaxSize(),
        bgModifier = Modifier.layerBackdrop(backdrop),
        effectBackground = shaderSupported,
        alpha = { 1f - scrollProgress },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = padding.calculateTopPadding() + 120.dp)
                .onSizeChanged { size -> with(density) { logoHeightDp = size.height.toDp() } },
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                modifier = Modifier
                    .size(90.dp)
                    .clip(CircleShape)
                    .then(
                        if (blurEnable) Modifier.textureBlur(
                            backdrop = backdrop,
                            shape = SmoothRoundedCornerShape(45.dp),
                            blurRadius = 150f,
                            noiseCoefficient = BlurDefaults.NoiseCoefficient,
                            colors = BlurColors(blendColors = titleBlend),
                            contentBlendMode = BlendMode.DstIn,
                            enabled = true,
                        ) else Modifier
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = MiuixIcons.Regular.Music,
                    contentDescription = null,
                    tint = colorScheme.onBackground,
                    modifier = Modifier.size(48.dp),
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                modifier = Modifier
                    .padding(bottom = 5.dp)
                    .then(
                        if (blurEnable) Modifier.textureBlur(
                            backdrop = backdrop,
                            shape = SmoothRoundedCornerShape(16.dp),
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
                text = "编译于 ${BuildConfig.BUILD_TIME}",
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
                SmallTitle(text = "项目")
                FrostedCard(backdrop = backdrop, blurEnable = blurEnable, cardBlendColors = cardBlendColors) {
                    BasicComponent(
                        title = "版本",
                        summary = "v${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
                    )
                    BasicComponent(
                        title = "Ella Music",
                        summary = "github.com/Kifranei/Ella",
                        onClick = { uriHandler.openUri("https://github.com/Kifranei/Ella") },
                    )
                }
            }

            item {
                SmallTitle(text = "致谢")
                FrostedCard(backdrop = backdrop, blurEnable = blurEnable, cardBlendColors = cardBlendColors) {
                    BasicComponent(
                        title = "Codex",
                        summary = "主要开发",
                    )
                    BasicComponent(
                        title = "GPT-5.5",
                        summary = "代码协作",
                    )
                }
            }

            item {
                SmallTitle(text = "开源项目")
                FrostedCard(backdrop = backdrop, blurEnable = blurEnable, cardBlendColors = cardBlendColors) {
                    BasicComponent(
                        title = "Miuix",
                        summary = "MIUI/HyperOS 风格 Compose UI 组件库",
                        onClick = { uriHandler.openUri("https://github.com/miuix-kmp/miuix") },
                    )
                    BasicComponent(
                        title = "AndroidX Media3",
                        summary = "播放、媒体会话与 ExoPlayer 扩展",
                        onClick = { uriHandler.openUri("https://github.com/androidx/media") },
                    )
                    BasicComponent(
                        title = "FFmpeg",
                        summary = "ALAC 等音频格式软件解码",
                        onClick = { uriHandler.openUri("https://ffmpeg.org") },
                    )
                    BasicComponent(
                        title = "Jaudiotagger",
                        summary = "音频标签、内嵌歌词与封面读取",
                        onClick = { uriHandler.openUri("https://github.com/Adonai/jaudiotagger") },
                    )
                    BasicComponent(
                        title = "TagLib",
                        summary = "音频标签与兜底读取",
                        onClick = { uriHandler.openUri("https://github.com/taglib/taglib") },
                    )
                    BasicComponent(
                        title = "Kyant TagLib",
                        summary = "Android/Kotlin TagLib 绑定",
                        onClick = { uriHandler.openUri("https://github.com/Kyant0/TagLib") },
                    )
                    BasicComponent(
                        title = "Lyricon",
                        summary = "词幕 Provider API 与状态栏歌词",
                        onClick = { uriHandler.openUri("https://github.com/proify/lyricon") },
                    )
                    BasicComponent(
                        title = "Kyant Backdrop",
                        summary = "液态玻璃与背景模糊效果",
                        onClick = { uriHandler.openUri("https://github.com/Kyant0/AndroidLiquidGlass") },
                    )
                    BasicComponent(
                        title = "Coil",
                        summary = "Kotlin 图片加载库",
                        onClick = { uriHandler.openUri("https://github.com/coil-kt/coil") },
                    )
                    BasicComponent(
                        title = "AndroidX DataStore",
                        summary = "设置持久化",
                        onClick = { uriHandler.openUri("https://developer.android.com/jetpack/androidx/releases/datastore") },
                    )
                    BasicComponent(
                        title = "Kotlinx Coroutines",
                        summary = "异步任务与 Flow 状态流",
                        onClick = { uriHandler.openUri("https://github.com/Kotlin/kotlinx.coroutines") },
                    )
                }
            }

            item {
                Spacer(Modifier.navigationBarsPadding())
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
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp)
            .padding(bottom = 12.dp)
            .then(
                if (blurEnable) Modifier.textureBlur(
                    backdrop = backdrop,
                    shape = SmoothRoundedCornerShape(16.dp),
                    blurRadius = 60f,
                    noiseCoefficient = BlurDefaults.NoiseCoefficient,
                    colors = BlurColors(blendColors = cardBlendColors),
                    enabled = true,
                ) else Modifier
            ),
        colors = CardDefaults.defaultColors(
            if (blurEnable) Color.Transparent else colorScheme.surfaceContainer,
            Color.Transparent,
        ),
    ) {
        content()
    }
}
