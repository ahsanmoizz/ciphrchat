package org.ciphrchat.app.identity

import org.junit.Assert.assertTrue
import org.junit.Test

class InvitationCodecValidationTest {
    @Test
    fun bareCiphrIdExplainsWhySecurePairingNeedsTheFullInvitation() {
        val error = runCatching {
            InvitationCodec.decode("ciphr:1234567890abcdef")
        }.exceptionOrNull()

        assertTrue(error is IllegalArgumentException)
        assertTrue(error?.message.orEmpty().contains("ID alone cannot pair securely"))
    }

}
