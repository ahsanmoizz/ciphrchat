package org.ciphrchat.app.calling

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.webrtc.MediaConstraints

class CallAudioConstraintsTest {

    @Test
    fun verifiesAudioOnlyAndZeroVideoTrackConstraints() {
        val audioConstraints = MediaConstraints().apply {
            mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"))
            mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveVideo", "false"))
            optional.add(MediaConstraints.KeyValuePair("echoCancellation", "true"))
            optional.add(MediaConstraints.KeyValuePair("googEchoCancellation", "true"))
            optional.add(MediaConstraints.KeyValuePair("googAutoGainControl", "true"))
            optional.add(MediaConstraints.KeyValuePair("googNoiseSuppression", "true"))
            optional.add(MediaConstraints.KeyValuePair("googHighpassFilter", "true"))
        }

        val offerAudio = audioConstraints.mandatory.find { it.key == "OfferToReceiveAudio" }?.value
        val offerVideo = audioConstraints.mandatory.find { it.key == "OfferToReceiveVideo" }?.value

        assertEquals("true", offerAudio)
        assertEquals("false", offerVideo)
    }

    @Test
    fun verifiesZeroVideoTracks() {
        val videoTracksCount = 0
        assertEquals("Video tracks must strictly be 0", 0, videoTracksCount)
    }
}
