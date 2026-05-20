package com.ella.music.data

import okhttp3.Interceptor
import okhttp3.Response
import java.io.IOException
import java.util.concurrent.TimeUnit

class AppNetworkLoggingInterceptor(
    private val tag: String
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val startNs = System.nanoTime()
        return try {
            val response = chain.proceed(request)
            if (!response.isSuccessful) {
                val durationMs = elapsedMillis(startNs)
                AppLogStore.network(
                    tag = tag,
                    level = if (response.code >= 500) "ERROR" else "WARNING",
                    message = "HTTP ${response.code} ${request.method} ${request.url.host}",
                    detail = buildString {
                        appendLine("url=${request.url}")
                        appendLine("method=${request.method}")
                        appendLine("code=${response.code}")
                        appendLine("protocol=${response.protocol}")
                        appendLine("reasonPhrase=${response.message.ifBlank { "(empty)" }}")
                        appendLine("durationMs=$durationMs")
                    }
                )
            }
            response
        } catch (error: IOException) {
            val durationMs = elapsedMillis(startNs)
            AppLogStore.logGlobal(
                level = "ERROR",
                type = AppLogType.NETWORK,
                tag = tag,
                message = "Network request failed: ${request.method} ${request.url.host} (${durationMs}ms)",
                detail = buildString {
                    appendLine("url=${request.url}")
                    appendLine("method=${request.method}")
                    appendLine("exception=${error.javaClass.name}")
                    appendLine("message=${error.message.orEmpty()}")
                    appendLine("durationMs=$durationMs")
                    appendLine()
                    append(error.stackTraceToString())
                }
            )
            throw error
        }
    }

    private fun elapsedMillis(startNs: Long): Long =
        TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNs)
}
