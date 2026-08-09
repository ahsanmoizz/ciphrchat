package org.ciphrchat.app.identity

import org.junit.Assert.assertTrue
import org.junit.Assert.assertThrows
import org.junit.Test

class InvitationCodecValidationTest {
    @Test
    fun bareCiphrIdExplainsWhySecurePairingNeedsTheFullInvitation() {
        val error = assertThrows(IllegalArgumentException::class.java) {
            InvitationCodec.decode("ciphr:1234567890abcdef")
        }

        assertTrue(error.message.orEmpty().contains("ID alone cannot pair securely"))
    }

    @Test
    fun malformedJsonReturnsAUserFacingInvitationError() {
        val error = assertThrows(IllegalArgumentException::class.java) {
            InvitationCodec.decode("{not-json")
        }

        assertTrue(error.message.orEmpty().contains("not a valid CiphrChat invitation"))
    }
}
