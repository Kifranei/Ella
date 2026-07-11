package com.ella.music.ui.album

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class AlbumPublisherMetadataTest {
    @Test
    fun readsOrganizationAndPublisherAliases() {
        val tags = linkedMapOf(
            "organization" to listOf("JASRAC", "FlyingDog"),
            "TPUB" to listOf("Victor Entertainment"),
            "LABEL" to listOf("FlyingDog")
        )

        assertEquals(
            listOf("JASRAC", "FlyingDog", "Victor Entertainment"),
            tags.albumPublisherValues()
        )
    }

    @Test
    fun producerIsNotMisreportedAsPublisher() {
        val values = mapOf("PRODUCER" to listOf("Producer Name")).albumPublisherValues()

        assertFalse(values.contains("Producer Name"))
    }
}
