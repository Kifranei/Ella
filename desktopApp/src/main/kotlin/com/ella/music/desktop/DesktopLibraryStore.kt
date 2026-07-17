package com.ella.music.desktop

import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString
import java.io.IOException
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import kotlin.io.path.createDirectories
import kotlin.io.path.exists

/** Stores desktop-only state under the operating system's application-data location. */
class DesktopLibraryStore(
    private val statePath: Path = desktopDataDirectory().resolve("library.json")
) {
    private val json = Json {
        ignoreUnknownKeys = true
        prettyPrint = true
        encodeDefaults = true
    }

    fun load(): DesktopLibraryState {
        if (!statePath.exists()) return DesktopLibraryState()
        return runCatching {
            json.decodeFromString<DesktopLibraryState>(Files.readString(statePath))
        }.getOrElse { DesktopLibraryState() }
    }

    @Synchronized
    fun save(state: DesktopLibraryState) {
        statePath.parent.createDirectories()
        val temporary = statePath.resolveSibling("${statePath.fileName}.tmp")
        Files.writeString(temporary, json.encodeToString(state))
        try {
            Files.move(
                temporary,
                statePath,
                StandardCopyOption.ATOMIC_MOVE,
                StandardCopyOption.REPLACE_EXISTING
            )
        } catch (_: AtomicMoveNotSupportedException) {
            Files.move(temporary, statePath, StandardCopyOption.REPLACE_EXISTING)
        } catch (error: IOException) {
            Files.deleteIfExists(temporary)
            throw error
        }
    }
}

fun desktopDataDirectory(): Path {
    val home = System.getProperty("user.home")
        ?.takeIf { it.isNotBlank() }
        ?: error("The user home directory is unavailable.")
    val isWindows = System.getProperty("os.name").startsWith("Windows", ignoreCase = true)
    return if (isWindows) {
        val appData = System.getenv("APPDATA")?.takeIf { it.isNotBlank() }
        Path.of(appData ?: home, "Halcyon")
    } else {
        val xdgDataHome = System.getenv("XDG_DATA_HOME")?.takeIf { it.isNotBlank() }
        Path.of(xdgDataHome ?: Path.of(home, ".local", "share").toString(), "Halcyon")
    }
}
