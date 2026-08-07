package org.ciphrchat.app.transport.bluetooth

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ContactDiscoveryTokenTest {
    @Test
    fun tokenIsStableAndCompact() {
        val first = ContactDiscoveryToken.forContactId("ciphr:alice")
        assertEquals(first, ContactDiscoveryToken.forContactId("ciphr:alice"))
        assertTrue(first.length <= 16)
    }

    @Test
    fun differentContactsDoNotShareToken() {
        assertTrue(
            ContactDiscoveryToken.forContactId("ciphr:alice") !=
                ContactDiscoveryToken.forContactId("ciphr:bob")
        )
    }
}
