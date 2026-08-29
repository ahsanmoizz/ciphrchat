package org.ciphrchat.app.calling

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CallAudioConstraintsTest {

    @Test
    fun verifiesAudioOnlyAndZeroVideoTrackCreation() {
        val constraints = AudioCallManager.AudioConstraints(
            audioOnly = true,
            videoEnabled = false,
            codec = "Opus",
            channels = 1,
            sampleRate = 48000,
            dtxEnabled = true,
            fecEnabled = true,
            echoCancellation = true,
            noiseSuppression = true,
            autoGainControl = true
        )

        assertTrue("Call must be audio only", constraints.audioOnly)
        assertFalse("Video tracks must be strictly disabled", constraints.videoEnabled)
        assertEquals("Opus", constraints.codec)
        assertEquals(1, constraints.channels) // Mono audio for weak network optimization
        assertTrue("DTX must be enabled", constraints.dtxEnabled)
        assertTrue("FEC must be enabled", constraints.fecEnabled)
    }
}
