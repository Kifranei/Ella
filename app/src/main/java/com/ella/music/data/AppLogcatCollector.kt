package com.ella.music.data

import android.content.Context
import android.os.Process
import android.util.Log
import java.util.Locale
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

object AppLogcatCollector {
    private const val TAG = "AppLogcatCollector"
    private val started = AtomicBoolean(false)
    private val executor = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "EllaLogcatCollector").apply { isDaemon = true }
    }

    fun start(context: Context) {
        AppLogStore.install(context)
        if (!started.compareAndSet(false, true)) return
        executor.execute {
            runCatching { collectByCurrentPid() }
                .onFailure { error ->
                    Log.w(TAG, "logcat collector unavailable", error)
                    AppLogStore.logGlobal(
                        level = "WARNING",
                        type = AppLogType.APP,
                        tag = TAG,
                        message = "logcat collector unavailable: ${error.message ?: error.javaClass.simpleName}",
                        detail = error.stackTraceToString(),
                        echoToLogcat = false
                    )
                }
        }
    }

    private fun collectByCurrentPid() {
        val pid = Process.myPid().toString()
        val process = ProcessBuilder("logcat", "-v", "threadtime")
            .redirectErrorStream(true)
            .start()
        process.inputStream.bufferedReader().useLines { lines ->
            lines.forEach { line ->
                parseThreadTimeLine(line)?.takeIf { it.pid == pid && it.isUsefulForPersisting() }?.let { entry ->
                    AppLogStore.logcat(
                        level = entry.level,
                        tag = entry.tag,
                        message = entry.message
                    )
                }
            }
        }
    }

    private fun LogcatLine.isUsefulForPersisting(): Boolean {
        val normalized = level.uppercase(Locale.ROOT)
        if (normalized in setOf("E", "W", "F")) return true
        val text = "$tag $message".lowercase(Locale.ROOT)
        return usefulKeywords.any { it in text }
    }

    private fun parseThreadTimeLine(line: String): LogcatLine? {
        val match = threadTimeRegex.matchEntire(line.trim()) ?: return null
        return LogcatLine(
            pid = match.groupValues[1],
            level = match.groupValues[3],
            tag = match.groupValues[4].trim(),
            message = match.groupValues[5].trim()
        )
    }

    private data class LogcatLine(
        val pid: String,
        val level: String,
        val tag: String,
        val message: String
    )

    private val threadTimeRegex =
        Regex("""\d{2}-\d{2}\s+\d{2}:\d{2}:\d{2}\.\d+\s+(\d+)\s+(\d+)\s+([VDIWEF])\s+([^:]+):\s?(.*)""")

    private val usefulKeywords = listOf(
        "player",
        "playback",
        "exo",
        "media3",
        "decoder",
        "queue",
        "webdav",
        "lx",
        "quickjs",
        "lyric",
        "ticker",
        "superlyric",
        "lyricon",
        "flyme",
        "scanner",
        "musicrepo",
        "taglib",
        "jaudiotagger",
        "metadata",
        "cover",
        "wav",
        "alac"
    )
}
