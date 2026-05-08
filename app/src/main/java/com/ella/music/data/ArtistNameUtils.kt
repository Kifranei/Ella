package com.ella.music.data

private val artistSeparators = Regex(
    pattern = """\s*(?:/|&|、|;|；|,|，|\+|×| x | X | feat\.? | ft\.? | with )\s*""",
    options = setOf(RegexOption.IGNORE_CASE)
)

fun splitArtistNames(value: String): List<String> {
    return value
        .replace("（", "(")
        .replace("）", ")")
        .split(artistSeparators)
        .map { it.trim() }
        .filter { it.isNotBlank() && !it.equals("Unknown", ignoreCase = true) }
        .distinctBy { it.lowercase() }
}

fun String.matchesArtistName(artistName: String): Boolean {
    val target = artistName.trim()
    if (target.isBlank()) return false
    return splitArtistNames(this).any { it.equals(target, ignoreCase = true) }
}

