package com.ella.music.ui.components

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LyricVideoEncoderPolicyTest {
    @Test
    fun directMuxOnlyAllowsBroadlyPlayableMp4AudioCodecs() {
        assertTrue(shouldTryDirectSourceAudioMux("audio/mp4a-latm"))
        assertTrue(shouldTryDirectSourceAudioMux("audio/mpeg"))
        assertFalse(shouldTryDirectSourceAudioMux("audio/alac"))
        assertFalse(shouldTryDirectSourceAudioMux("audio/flac"))
        assertFalse(shouldTryDirectSourceAudioMux(null))
    }
}
