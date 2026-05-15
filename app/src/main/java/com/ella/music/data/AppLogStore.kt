package com.ella.music.data

import android.content.Context
import android.os.Build
import android.util.Log
import com.ella.music.BuildConfig
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

data class AppLogEntry(
    val time: Long,
    val level: String,
    val tag: String,
    val message: String,
    val throwable: String? = null
)

object AppLogStore {
    private const val FILE_NAME = "ella_logs.tsv"
    private const val PREF_NAME = "ella_log_prefs"
    private const val KEY_RETENTION_DAYS = "retention_days"
    private const val MAX_LINES = 800
    private val lock = Any()
    private val timeFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())

    fun info(context: Context, tag: String, message: String) {
        append(context.applicationContext, AppLogEntry(System.currentTimeMillis(), "INFO", tag, message))
    }

    fun debug(context: Context, tag: String, message: String) {
        append(context.applicationContext, AppLogEntry(System.currentTimeMillis(), "DEBUG", tag, message))
    }

    fun warn(context: Context, tag: String, message: String, throwable: Throwable? = null) {
        append(
            context.applicationContext,
            AppLogEntry(
                time = System.currentTimeMillis(),
                level = "WARN",
                tag = tag,
                message = message,
                throwable = throwable?.stackTraceToString()
            )
        )
    }

    fun error(context: Context, tag: String, message: String, throwable: Throwable? = null) {
        append(
            context.applicationContext,
            AppLogEntry(
                time = System.currentTimeMillis(),
                level = "ERROR",
                tag = tag,
                message = message,
                throwable = throwable?.stackTraceToString()
            )
        )
    }

    fun crash(context: Context, threadName: String, throwable: Throwable) {
        append(
            context.applicationContext,
            AppLogEntry(
                time = System.currentTimeMillis(),
                level = "CRASH",
                tag = threadName,
                message = throwable.message ?: throwable.javaClass.name,
                throwable = throwable.stackTraceToString()
            )
        )
    }

    fun read(context: Context): List<AppLogEntry> = synchronized(lock) {
        pruneByRetentionLocked(context)
        val file = logFile(context)
        if (!file.exists()) return@synchronized emptyList()
        file.readLines()
            .mapNotNull(::decode)
            .asReversed()
    }

    fun retentionDays(context: Context): Int = context.applicationContext
        .getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        .getInt(KEY_RETENTION_DAYS, 7)

    fun setRetentionDays(context: Context, days: Int): Int = synchronized(lock) {
        val safeDays = days.coerceIn(1, 30)
        context.applicationContext
            .getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .edit()
            .putInt(KEY_RETENTION_DAYS, safeDays)
            .apply()
        clearOlderThanLocked(context.applicationContext, safeDays)
    }

    fun clear(context: Context) = synchronized(lock) {
        val file = logFile(context)
        if (file.exists()) file.delete()
    }

    fun clearOlderThan(context: Context, days: Int): Int = synchronized(lock) {
        clearOlderThanLocked(context, days)
    }

    private fun clearOlderThanLocked(context: Context, days: Int): Int {
        val file = logFile(context)
        if (!file.exists()) return 0
        val cutoff = System.currentTimeMillis() - days.coerceAtLeast(1) * 24L * 60L * 60L * 1000L
        val entries = file.readLines().mapNotNull(::decode)
        val kept = entries.filter { it.time >= cutoff }
        val removed = entries.size - kept.size
        if (removed > 0) {
            file.writeText(kept.joinToString(separator = "\n") { encode(it) })
        }
        return removed
    }

    fun buildDetailedReport(context: Context, entries: List<AppLogEntry> = read(context)): String {
        val appContext = context.applicationContext
        return buildString {
            appendLine("Ella Music 运行日志")
            appendLine("生成时间: ${formatTime(System.currentTimeMillis())}")
            appendLine("应用版本: ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
            appendLine("构建时间: ${BuildConfig.BUILD_TIME}")
            appendLine("包名: ${appContext.packageName}")
            appendLine("设备: ${Build.MANUFACTURER} ${Build.MODEL}")
            appendLine("Android: ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
            appendLine("日志条数: ${entries.size}")
            appendLine()
            appendLine("== AppLogStore ==")
            if (entries.isEmpty()) {
                appendLine("暂无持久化日志")
            } else {
                entries.asReversed().forEach { entry ->
                    appendLine(entry.toReportLine())
                    entry.throwable?.takeIf { it.isNotBlank() }?.let { throwable ->
                        appendLine(throwable.trimEnd())
                    }
                }
            }
            appendLine()
            appendLine("== Logcat Tail ==")
            append(readLogcatTail())
        }
    }

    fun exportDetailedReport(context: Context, entries: List<AppLogEntry> = read(context)): File {
        val dir = File(context.cacheDir, "shared_logs").apply { mkdirs() }
        val file = File(dir, "ella-log-${exportTimeFormat()}.txt")
        file.writeText(buildDetailedReport(context, entries))
        return file
    }

    fun formatTime(time: Long): String = synchronized(timeFormat) {
        timeFormat.format(Date(time))
    }

    private fun append(context: Context, entry: AppLogEntry) = synchronized(lock) {
        Log.println(entry.logPriority(), entry.tag, entry.message)
        val file = logFile(context)
        val cutoff = System.currentTimeMillis() - retentionDays(context) * 24L * 60L * 60L * 1000L
        val lines = if (file.exists()) {
            file.readLines()
                .mapNotNull { line -> decode(line)?.takeIf { it.time >= cutoff }?.let(::encode) }
                .takeLast(MAX_LINES - 1)
        } else {
            emptyList()
        }
        file.parentFile?.mkdirs()
        file.writeText((lines + encode(entry)).joinToString(separator = "\n"))
    }

    private fun AppLogEntry.logPriority(): Int = when (level) {
        "DEBUG" -> Log.DEBUG
        "WARN" -> Log.WARN
        "ERROR", "CRASH" -> Log.ERROR
        else -> Log.INFO
    }

    private fun logFile(context: Context): File = File(context.filesDir, FILE_NAME)

    private fun pruneByRetentionLocked(context: Context) {
        clearOlderThanLocked(context.applicationContext, retentionDays(context))
    }

    private fun AppLogEntry.toReportLine(): String {
        return "[${formatTime(time)}] $level/$tag: $message"
    }

    private fun exportTimeFormat(): String {
        val formatter = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US)
        return formatter.format(Date())
    }

    private fun readLogcatTail(): String {
        return runCatching {
            val process = ProcessBuilder("logcat", "-d", "-t", "600").redirectErrorStream(true).start()
            if (!process.waitFor(2, TimeUnit.SECONDS)) {
                process.destroy()
                return@runCatching "读取 logcat 超时\n"
            }
            process.inputStream.bufferedReader().use { it.readText() }.ifBlank { "logcat 暂无可读内容\n" }
        }.getOrElse { error ->
            "读取 logcat 失败: ${error.message ?: error.javaClass.name}\n"
        }
    }

    private fun encode(entry: AppLogEntry): String = listOf(
        entry.time.toString(),
        entry.level,
        entry.tag,
        entry.message,
        entry.throwable.orEmpty()
    ).joinToString("\t") { it.escape() }

    private fun decode(line: String): AppLogEntry? {
        val parts = line.split('\t').map { it.unescape() }
        if (parts.size < 5) return null
        return AppLogEntry(
            time = parts[0].toLongOrNull() ?: return null,
            level = parts[1],
            tag = parts[2],
            message = parts[3],
            throwable = parts[4].takeIf { it.isNotBlank() }
        )
    }

    private fun String.escape(): String = replace("\\", "\\\\")
        .replace("\t", "\\t")
        .replace("\n", "\\n")
        .replace("\r", "\\r")

    private fun String.unescape(): String {
        val result = StringBuilder(length)
        var escaping = false
        for (char in this) {
            if (escaping) {
                result.append(
                    when (char) {
                        't' -> '\t'
                        'n' -> '\n'
                        'r' -> '\r'
                        else -> char
                    }
                )
                escaping = false
            } else if (char == '\\') {
                escaping = true
            } else {
                result.append(char)
            }
        }
        if (escaping) result.append('\\')
        return result.toString()
    }
}
