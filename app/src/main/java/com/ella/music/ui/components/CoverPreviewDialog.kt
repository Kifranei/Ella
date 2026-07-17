package com.ella.music.ui.components

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.IntSize as ComposeIntSize
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.FileProvider
import coil3.compose.AsyncImage
import coil3.imageLoader
import coil3.request.ImageRequest
import coil3.toBitmap
import com.ella.music.R
import java.io.File
import java.io.FileOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.Text

/** Full-resolution cover preview shared by the player and album pages. */
@Composable
internal fun CoverPreviewDialog(
    model: Any,
    title: String,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var scale by remember(model) { mutableFloatStateOf(1f) }
    var offset by remember(model) { mutableStateOf(Offset.Zero) }
    var resolution by remember(model) { mutableStateOf<CoverResolution?>(null) }
    var viewportSize by remember(model) { mutableStateOf(ComposeIntSize.Zero) }
    val transformState = rememberTransformableState { centroid, zoomChange, panChange, _ ->
        val previousScale = scale
        val nextScale = (previousScale * zoomChange).coerceIn(COVER_MIN_SCALE, COVER_MAX_SCALE)
        val viewportCenter = Offset(viewportSize.width / 2f, viewportSize.height / 2f)
        val focalPoint = centroid.takeIf { it != Offset.Unspecified } ?: viewportCenter
        val scaleRatio = nextScale / previousScale
        val focalOffset = focalPoint - viewportCenter
        val scaledOffset = (offset + focalOffset) * scaleRatio - focalOffset
        scale = nextScale
        offset = (scaledOffset + panChange).coerceWithin(
            coverPreviewPanBounds(
                resolution = resolution,
                viewportSize = viewportSize,
                scale = nextScale
            )
        )
    }

    LaunchedEffect(transformState) {
        snapshotFlow { transformState.isTransformInProgress }
            .distinctUntilChanged()
            .collectLatest { transforming ->
                if (!transforming && scale < 1f) {
                    val initialScale = scale
                    animate(
                        initialValue = initialScale,
                        targetValue = 1f,
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioNoBouncy,
                            stiffness = Spring.StiffnessMediumLow
                        )
                    ) { value, _ ->
                        scale = value
                        offset = offset.coerceWithin(
                            coverPreviewPanBounds(
                                resolution = resolution,
                                viewportSize = viewportSize,
                                scale = value
                            )
                        )
                    }
                    scale = 1f
                    offset = Offset.Zero
                }
            }
    }
    val controlsVisible = scale <= 1.01f

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
        ) {
            AsyncImage(
                model = remember(context, model) {
                    ImageRequest.Builder(context)
                        .data(model)
                        .build()
                },
                contentDescription = title,
                contentScale = ContentScale.Fit,
                onSuccess = { state ->
                    resolution = CoverResolution(state.result.image.width, state.result.image.height)
                },
                modifier = Modifier
                    .fillMaxSize()
                    .onSizeChanged { viewportSize = it }
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                        translationX = offset.x
                        translationY = offset.y
                    }
                    .transformable(
                        state = transformState,
                        // Preserve ordinary taps at the original scale; panning becomes active
                        // only after the cover has actually been enlarged.
                        canPan = { scale > 1f }
                    )
            )

            if (controlsVisible) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .statusBarsPadding()
                        .navigationBarsPadding()
                        .padding(18.dp),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CoverPreviewAction(
                            text = "‹",
                            contentDescription = stringResource(R.string.cover_preview_back),
                            onClick = onDismiss
                        )
                        Text(
                            text = title,
                            color = Color.White,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier
                                .weight(1f)
                                .padding(horizontal = 16.dp)
                        )
                        CoverPreviewAction(
                            text = "↗",
                            contentDescription = stringResource(R.string.cover_preview_share),
                            onClick = {
                                scope.launch {
                                    val shared = writeAndShareCover(context, model, title)
                                    if (!shared) {
                                        Toast.makeText(
                                            context,
                                            context.getString(R.string.cover_preview_share_failed),
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    }
                                }
                            }
                        )
                    }
                    resolution?.takeIf { it.width > 0 && it.height > 0 }?.let { size ->
                        Text(
                            text = context.getString(
                                R.string.cover_preview_resolution,
                                size.width,
                                size.height
                            ),
                            color = Color.White.copy(alpha = 0.88f),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier
                                .align(Alignment.CenterHorizontally)
                                .background(Color.Black.copy(alpha = 0.46f), RoundedCornerShape(99.dp))
                                .padding(horizontal = 13.dp, vertical = 7.dp)
                        )
                    } ?: Spacer(modifier = Modifier.size(1.dp))
                }
            }
        }
    }
}

@Composable
private fun CoverPreviewAction(
    text: String,
    contentDescription: String,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(46.dp)
            .background(Color.Black.copy(alpha = 0.42f), CircleShape)
            .clickable(onClick = onClick)
            .semantics { this.contentDescription = contentDescription },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = Color.White,
            fontSize = 30.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(bottom = 3.dp)
        )
    }
}

private suspend fun writeAndShareCover(context: Context, model: Any, title: String): Boolean {
    return runCatching {
        // Coil may hand us the same Bitmap object that is currently rendered by the preview/player.
        // Sharing must only recycle a private copy; recycling the source made the preview black and
        // could later crash the player when it attempted to reuse its cover.
        val sharedBitmap = withContext(Dispatchers.IO) {
            val source = (model as? Bitmap) ?: context.imageLoader.execute(
                ImageRequest.Builder(context)
                    .data(model)
                    .build()
            ).image?.toBitmap()
            source?.copy(Bitmap.Config.ARGB_8888, false)
        } ?: return false
        val uri = withContext(Dispatchers.IO) {
            val dir = File(context.cacheDir, "cover_share").apply { mkdirs() }
            // Keep existing files valid while a target app is still reading their content URI.
            // Old files are cheap to remove, but never remove the whole directory before sharing.
            val staleBefore = System.currentTimeMillis() - COVER_SHARE_CACHE_MAX_AGE_MS
            dir.listFiles()
                ?.filter { it.isFile && it.lastModified() < staleBefore }
                ?.forEach(File::delete)
            try {
                val file = File(dir, "halcyon_cover_${System.currentTimeMillis()}.png")
                FileOutputStream(file).use { output ->
                    sharedBitmap.compress(Bitmap.CompressFormat.PNG, 100, output)
                }
                FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            } finally {
                sharedBitmap.recycle()
            }
        }
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            clipData = ClipData.newUri(context.contentResolver, title, uri)
        }
        context.startActivity(Intent.createChooser(shareIntent, context.getString(R.string.cover_preview_share)))
        true
    }.getOrElse { false }
}

private fun coverPreviewPanBounds(
    resolution: CoverResolution?,
    viewportSize: ComposeIntSize,
    scale: Float
): Offset {
    val width = viewportSize.width.toFloat()
    val height = viewportSize.height.toFloat()
    val imageWidth = resolution?.width?.toFloat() ?: return Offset.Zero
    val imageHeight = resolution.height.toFloat()
    if (width <= 0f || height <= 0f || imageWidth <= 0f || imageHeight <= 0f) {
        return Offset.Zero
    }
    val imageRatio = imageWidth / imageHeight
    val viewportRatio = width / height
    val fittedWidth: Float
    val fittedHeight: Float
    if (imageRatio >= viewportRatio) {
        fittedWidth = width
        fittedHeight = width / imageRatio
    } else {
        fittedHeight = height
        fittedWidth = height * imageRatio
    }
    return Offset(
        x = ((fittedWidth * scale - fittedWidth) / 2f).coerceAtLeast(0f),
        y = ((fittedHeight * scale - fittedHeight) / 2f).coerceAtLeast(0f)
    )
}

private fun Offset.coerceWithin(bounds: Offset): Offset = Offset(
    x = x.coerceIn(-bounds.x, bounds.x),
    y = y.coerceIn(-bounds.y, bounds.y)
)

private data class CoverResolution(val width: Int, val height: Int)

private const val COVER_MIN_SCALE = 0.82f
private const val COVER_MAX_SCALE = 4f
private const val COVER_SHARE_CACHE_MAX_AGE_MS = 24L * 60L * 60L * 1000L
