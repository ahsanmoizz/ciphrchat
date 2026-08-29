package org.ciphrchat.app.blockchain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class IdentityHashPrivacyTest {

    @Test
    fun generatesUniqueBlindedCommitmentsForSameIdentity() {
        val identity = "ciphr:8f4c2e10a9b3d5e71234567890abcdef"
        val commitment1 = IdentityHashPrivacy.createBlindedCommitment(identity)
        val commitment2 = IdentityHashPrivacy.createBlindedCommitment(identity)

        // Salts must be randomly generated and distinct
        assertNotEquals(commitment1.saltHex, commitment2.saltHex)
        // Hashes must be distinct due to salt blinding (zero-knowledge privacy property)
        assertNotEquals(commitment1.identityHashHex, commitment2.identityHashHex)
        assertEquals(64, commitment1.identityHashHex.length) // 32 bytes hex
        assertEquals(64, commitment1.saltHex.length) // 32 bytes hex
    }

    @Test
    fun verifiesValidCommitmentWithCorrectSalt() {
        val identity = "ciphr:8f4c2e10a9b3d5e71234567890abcdef"
        val commitment = IdentityHashPrivacy.createBlindedCommitment(identity)

        val isValid = IdentityHashPrivacy.verifyCommitment(
            identityRaw = identity,
            saltHex = commitment.saltHex,
            expectedHashHex = commitment.identityHashHex
        )
        assertTrue("Commitment must verify with matching identity and salt", isValid)
    }

    @Test
    fun rejectsVerificationWithWrongIdentityOrSalt() {
        val identity1 = "ciphr:8f4c2e10a9b3d5e71234567890abcdef"
        val identity2 = "ciphr:112233445566778899aabbccddeeff00"
        val commitment = IdentityHashPrivacy.createBlindedCommitment(identity1)

        val wrongIdentityResult = IdentityHashPrivacy.verifyCommitment(
            identityRaw = identity2,
            saltHex = commitment.saltHex,
            expectedHashHex = commitment.identityHashHex
        )
        assertFalse("Verification must fail with wrong identity", wrongIdentityResult)

        val wrongSaltResult = IdentityHashPrivacy.verifyCommitment(
            identityRaw = identity1,
            saltHex = "00".repeat(32),
            expectedHashHex = commitment.identityHashHex
        )
        assertFalse("Verification must fail with wrong salt", wrongSaltResult)
    }
}
