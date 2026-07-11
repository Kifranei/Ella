package com.ella.music.ui.album

import java.util.Locale

internal fun Map<String, List<String>>.albumPublisherValues(): List<String> =
    entries
        .asSequence()
        .filter { (key, _) -> key.normalizedPublisherTagKey() in PUBLISHER_TAG_KEYS }
        .flatMap { (_, values) -> values.asSequence() }
        .map(String::trim)
        .filter(String::isNotBlank)
        .distinctBy { it.lowercase(Locale.ROOT) }
        .toList()

private fun String.normalizedPublisherTagKey(): String =
    trim().uppercase(Locale.ROOT).filter(Char::isLetterOrDigit)

private val PUBLISHER_TAG_KEYS = setOf(
    "ORGANIZATION",
    "ORGANISATION",
    "PUBLISHER",
    "TPUB",
    "LABEL"
)
