package com.ella.music.ui.player

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.util.Log
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import com.ella.music.R
import com.ella.music.data.model.Song
import com.ella.music.ui.components.EllaLoadingIndicator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.window.WindowBottomSheet
import java.net.URLEncoder

@Composable
fun DynamicCoverWebViewSheet(
    show: Boolean,
    song: Song?,
    onDismissRequest: () -> Unit
) {
    if (!show || song == null) return

    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    WindowBottomSheet(
        show = true,
        enableNestedScroll = false,
        title = context.getString(R.string.player_match_dynamic_cover),
        onDismissRequest = onDismissRequest
    ) {
        DynamicCoverWebViewContent(
            song = song,
            onDownloadComplete = { path ->
                scope.launch {
                    Toast.makeText(
                        context,
                        context.getString(R.string.player_dynamic_cover_downloaded),
                        Toast.LENGTH_SHORT
                    ).show()
                }
            },
            onDownloadFailed = { error ->
                scope.launch {
                    Toast.makeText(
                        context,
                        context.getString(R.string.player_dynamic_cover_download_failed),
                        Toast.LENGTH_SHORT
                    ).show()
                    Log.e("DynamicCoverWebView", "Download failed", error)
                }
            }
        )
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun DynamicCoverWebViewContent(
    song: Song,
    onDownloadComplete: (String) -> Unit,
    onDownloadFailed: (Throwable) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var isLoading by remember { mutableStateOf(true) }

    val searchUrl = remember(song.artist, song.album) {
        val query = listOf(song.artist, song.album)
            .filter { it.isNotBlank() }
            .joinToString(" ")
        "https://covers.musichoarders.xyz/?q=${URLEncoder.encode(query, "UTF-8")}"
    }

    val downloadHelper = remember(song) {
        DynamicCoverDownloadHelper(context, song)
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .fillMaxHeight(0.6f)
            .navigationBarsPadding()
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            AndroidView(
                factory = { ctx ->
                    WebView(ctx).apply {
                        settings.apply {
                            javaScriptEnabled = true
                            domStorageEnabled = true
                            mediaPlaybackRequiresUserGesture = false
                            cacheMode = WebSettings.LOAD_DEFAULT
                            allowContentAccess = true
                            mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
                            userAgentString = settings.userAgentString.replace(
                                "wv",
                                ""
                            ).trim()
                        }

                        webChromeClient = object : WebChromeClient() {
                            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                                isLoading = newProgress < 100
                            }
                        }

                        webViewClient = object : WebViewClient() {
                            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                                super.onPageStarted(view, url, favicon)
                                isLoading = true
                            }

                            override fun onPageFinished(view: WebView?, url: String?) {
                                super.onPageFinished(view, url)
                                isLoading = false
                                // Inject JS to intercept video clicks and extract URLs
                                view?.evaluateJavascript(VIDEO_INTERCEPT_JS, null)
                            }

                            override fun shouldOverrideUrlLoading(
                                view: WebView?,
                                request: WebResourceRequest?
                            ): Boolean {
                                return false // Let WebView handle all URLs
                            }
                        }

                        // Bridge for JS to call Kotlin
                        addJavascriptInterface(
                            object {
                                @JavascriptInterface
                                fun onVideoUrlDetected(videoUrl: String) {
                                    if (videoUrl.isBlank()) return
                                    scope.launch(Dispatchers.IO) {
                                        try {
                                            downloadHelper.downloadVideo(videoUrl)
                                            withContext(Dispatchers.Main) {
                                                onDownloadComplete(videoUrl)
                                            }
                                        } catch (e: Exception) {
                                            withContext(Dispatchers.Main) {
                                                onDownloadFailed(e)
                                            }
                                        }
                                    }
                                }
                            },
                            "AndroidBridge"
                        )

                        // Set download listener for direct download buttons
                        setDownloadListener { url, userAgent, contentDisposition, mimeType, _ ->
                            if (url != null && mimeType?.contains("video") == true) {
                                scope.launch(Dispatchers.IO) {
                                    try {
                                        downloadHelper.downloadVideo(url)
                                        withContext(Dispatchers.Main) {
                                            onDownloadComplete(url)
                                        }
                                    } catch (e: Exception) {
                                        withContext(Dispatchers.Main) {
                                            onDownloadFailed(e)
                                        }
                                    }
                                }
                            }
                        }

                        loadUrl(searchUrl)
                    }
                },
                modifier = Modifier.fillMaxSize()
            )

            if (isLoading) {
                EllaLoadingIndicator(
                    modifier = Modifier
                        .align(Alignment.Center)
                )
            }
        }
    }
}
