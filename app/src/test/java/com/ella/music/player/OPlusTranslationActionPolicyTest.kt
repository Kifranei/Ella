package com.ella.music.player

import com.ella.music.data.SettingsManager
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OPlusTranslationActionPolicyTest {
    private val translatedPayload =
        """{"lyric":"[00:01.00]Hello","rawLyric":"[00:01.000]Hello","translationLyric":"[00:01.000]你好"}"""

    @Test
    fun publishesForEnabledModulePayloadWithTranslation() {
        assertTrue(
            OPlusTranslationActionPolicy.shouldPublish(
                colorOsLyricEnabled = true,
                deliveryMode = SettingsManager.OPLUS_LYRIC_MODE_MODULE,
                lyricInfoJson = translatedPayload
            )
        )
    }

    @Test
    fun hidesForSystemMode() {
        assertFalse(
            OPlusTranslationActionPolicy.shouldPublish(
                colorOsLyricEnabled = true,
                deliveryMode = SettingsManager.OPLUS_LYRIC_MODE_SYSTEM,
                lyricInfoJson = translatedPayload
            )
        )
    }

    @Test
    fun hidesWhenFeatureOrTranslationIsUnavailable() {
        assertFalse(
            OPlusTranslationActionPolicy.shouldPublish(
                colorOsLyricEnabled = false,
                deliveryMode = SettingsManager.OPLUS_LYRIC_MODE_MODULE,
                lyricInfoJson = translatedPayload
            )
        )
        assertFalse(
            OPlusTranslationActionPolicy.shouldPublish(
                colorOsLyricEnabled = true,
                deliveryMode = SettingsManager.OPLUS_LYRIC_MODE_MODULE,
                lyricInfoJson = """{"lyric":"[00:01.00]Hello","rawLyric":"[00:01.000]Hello"}"""
            )
        )
    }
}
