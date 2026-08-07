package org.ciphrchat.app.transport.adapters

import org.ciphrchat.app.transport.TransportAvailability
import org.ciphrchat.app.transport.TransportCapability
import org.ciphrchat.app.transport.TransportKind
import javax.inject.Inject
import javax.inject.Singleton


@Singleton
class ExternalTransportAdapter @Inject constructor() : BaseMockTransportAdapter(
    kind = TransportKind.EXTERNAL,
    initialAvailability = TransportAvailability.UNAVAILABLE,
    initialDetail = "External USB/Serial hardware not connected",
    capabilities = setOf(
        TransportCapability.LARGE_PAYLOAD
    )
)
