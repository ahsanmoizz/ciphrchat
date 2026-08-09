package org.ciphrchat.app.messaging

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TransportPresentationPolicyTest {
    @Test
    fun presentsPersistedRoutesTruthfully() {
        assertEquals("global", TransportPresentationPolicy.forName("INTERNET_DIRECT")?.operatingRange)
        assertEquals("nearby", TransportPresentationPolicy.forName("BLUETOOTH_DIRECT")?.operatingRange)
        assertEquals("BLE carrier", TransportPresentationPolicy.forName("UWB_ASSIST")?.operatingRange)
        assertNull(TransportPresentationPolicy.forName(null))
    }
}
