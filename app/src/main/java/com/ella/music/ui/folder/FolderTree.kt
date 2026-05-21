package com.ella.music.ui.folder

import com.ella.music.data.model.Song

internal data class FolderTreeEntry(
    val path: String,
    val name: String,
    val songCount: Int,
    val duration: Long
)

internal fun Song.folderPath(): String {
    val normalized = path.replace('\\', '/')
    val lastSlash = normalized.lastIndexOf('/')
    return if (lastSlash > 0) normalized.substring(0, lastSlash).normalizeFolderPath() else "/"
}

internal fun List<Song>.commonFolderRoot(): String {
    val folders = map { it.folderPath() }.distinct()
    if (folders.isEmpty()) return "/"
    if (folders.size == 1) return folders.first().parentFolderPath()

    val commonSegments = folders
        .map { it.trim('/').split('/').filter(String::isNotBlank) }
        .reduce { common, next ->
            common.zip(next)
                .takeWhile { (left, right) -> left.equals(right, ignoreCase = true) }
                .map { it.first }
        }
    return commonSegments.toFolderPath().ifBlank { "/" }
}

internal fun List<Song>.childFoldersOf(parentPath: String): List<FolderTreeEntry> {
    val normalizedParent = parentPath.normalizeFolderPath()
    return asSequence()
        .mapNotNull { song ->
            val childPath = song.folderPath().immediateChildOf(normalizedParent) ?: return@mapNotNull null
            childPath to song
        }
        .groupBy({ it.first }, { it.second })
        .map { (path, songs) ->
            FolderTreeEntry(
                path = path,
                name = path.substringAfterLast('/').ifBlank { "根目录" },
                songCount = songs.size,
                duration = songs.sumOf { it.duration }
            )
        }
}

internal fun List<Song>.directSongsInFolder(folderPath: String): List<Song> {
    val normalizedFolder = folderPath.normalizeFolderPath()
    return filter { it.folderPath().equals(normalizedFolder, ignoreCase = true) }
}

internal fun List<Song>.recursiveSongsInFolder(folderPath: String): List<Song> {
    val normalizedFolder = folderPath.normalizeFolderPath()
    return filter { song ->
        val songFolder = song.folderPath()
        songFolder.equals(normalizedFolder, ignoreCase = true) ||
            songFolder.startsWith("${normalizedFolder.trimEnd('/')}/", ignoreCase = true)
    }
}

internal fun String.normalizeFolderPath(): String {
    val normalized = replace('\\', '/').trim().trimEnd('/')
    return normalized.ifBlank { "/" }
}

internal fun String.parentFolderPath(): String {
    val normalized = normalizeFolderPath()
    if (normalized == "/") return "/"
    val parent = normalized.substringBeforeLast('/', missingDelimiterValue = "")
    return parent.ifBlank { "/" }
}

private fun String.immediateChildOf(parentPath: String): String? {
    val folder = normalizeFolderPath()
    val parent = parentPath.normalizeFolderPath()
    if (folder.equals(parent, ignoreCase = true)) return null

    val remainder = if (parent == "/") {
        folder.trimStart('/')
    } else {
        val prefix = "${parent.trimEnd('/')}/"
        if (!folder.startsWith(prefix, ignoreCase = true)) return null
        folder.substring(prefix.length)
    }
    val childName = remainder.substringBefore('/').takeIf { it.isNotBlank() } ?: return null
    return if (parent == "/") "/$childName" else "${parent.trimEnd('/')}/$childName"
}

private fun List<String>.toFolderPath(): String =
    if (isEmpty()) "/" else joinToString(prefix = "/", separator = "/")
