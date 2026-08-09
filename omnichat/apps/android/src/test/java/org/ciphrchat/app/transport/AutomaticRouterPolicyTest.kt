package org.ciphrchat.app.transport

import org.junit.Assert.assertEquals
import org.junit.Test

class AutomaticRouterPolicyTest {
    @Test
    fun ordinaryInternetIsThePrimaryRoute() {
        assertEquals(TransportKind.INTERNET_DIRECT, DEFAULT_TRANSPORT_PRIORITY.first())
    }

    @Test
    fun nearbyRoutesRemainAvailableAsFallbacks() {
        assertEquals(
            setOf(
                TransportKind.WIFI_LAN,
                TransportKind.BLUETOOTH_DIRECT,
                TransportKind.BLUETOOTH_MESH,
                TransportKind.WIFI_AWARE,
                TransportKind.WIFI_DIRECT
            ),
            DEFAULT_TRANSPORT_PRIORITY.toSet().intersect(
                setOf(
                    TransportKind.WIFI_LAN,
                    TransportKind.BLUETOOTH_DIRECT,
                    TransportKind.BLUETOOTH_MESH,
                    TransportKind.WIFI_AWARE,
                    TransportKind.WIFI_DIRECT
                )
            )
        )
    }
}
