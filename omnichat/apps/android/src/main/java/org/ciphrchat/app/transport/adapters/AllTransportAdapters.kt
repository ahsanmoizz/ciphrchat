package org.ciphrchat.app.transport.adapters

import org.ciphrchat.app.transport.*
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class InternetTransportAdapter @Inject constructor() : BaseMockTransportAdapter(
    kind = TransportKind.INTERNET_DIRECT,
    initialAvailability = TransportAvailability.AVAILABLE,
    initialDetail = "Network available; real P2P not connected",
    capabilities = setOf(
        TransportCapability.DISCOVERY,
        TransportCapability.SMALL_TEXT,
        TransportCapability.LARGE_PAYLOAD
    )
)

@Singleton
class InternetRelayTransportAdapter @Inject constructor() : BaseMockTransportAdapter(
    kind = TransportKind.INTERNET_RELAY,
    initialAvailability = TransportAvailability.UNAVAILABLE,
    initialDetail = "Relay server not configured",
    capabilities = setOf(
        TransportCapability.DISCOVERY,
        TransportCapability.SMALL_TEXT,
        TransportCapability.LARGE_PAYLOAD
    )
)




@Singleton
class BluetoothTransportAdapter @Inject constructor() : BaseMockTransportAdapter(
    kind = TransportKind.BLUETOOTH_DIRECT,
    initialAvailability = TransportAvailability.PERMISSION_REQUIRED,
    initialDetail = "Requires Bluetooth permissions",
    capabilities = setOf(
        TransportCapability.DISCOVERY,
        TransportCapability.SMALL_TEXT,
        TransportCapability.OFFLINE
    )
)

@Singleton
class BluetoothMeshTransportAdapter @Inject constructor() : BaseMockTransportAdapter(
    kind = TransportKind.BLUETOOTH_MESH,
    initialAvailability = TransportAvailability.EXPERIMENTAL,
    initialDetail = "Mesh forwarding scaffold; not production ready",
    capabilities = setOf(
        TransportCapability.DISCOVERY,
        TransportCapability.SMALL_TEXT,
        TransportCapability.MULTI_HOP,
        TransportCapability.OFFLINE
    )
)

@Singleton
class UltrasoundTransportAdapter @Inject constructor() : BaseMockTransportAdapter(
    kind = TransportKind.ULTRASOUND,
    initialAvailability = TransportAvailability.EXPERIMENTAL,
    initialDetail = "Experimental; microphone permission required",
    capabilities = setOf(
        TransportCapability.DISCOVERY,
        TransportCapability.SMALL_TEXT,
        TransportCapability.PAIRING,
        TransportCapability.OFFLINE
    )
)

@Singleton
class InfraredTransportAdapter @Inject constructor() : BaseMockTransportAdapter(
    kind = TransportKind.INFRARED,
    initialAvailability = TransportAvailability.UNAVAILABLE,
    initialDetail = "No IR emitter detected on this device",
    capabilities = setOf(
        TransportCapability.SMALL_TEXT
    )
)

@Singleton
class NfcPairingAdapter @Inject constructor() : BaseMockTransportAdapter(
    kind = TransportKind.NFC_PAIRING,
    initialAvailability = TransportAvailability.UNAVAILABLE,
    initialDetail = "NFC pairing scaffold",
    capabilities = setOf(
        TransportCapability.PAIRING,
        TransportCapability.OFFLINE
    )
)

@Singleton
class UwbAssistAdapter @Inject constructor() : BaseMockTransportAdapter(
    kind = TransportKind.UWB_ASSIST,
    initialAvailability = TransportAvailability.UNAVAILABLE,
    initialDetail = "UWB not detected on this device",
    capabilities = setOf(
        TransportCapability.RANGING,
        TransportCapability.PAIRING
    )
)

@Singleton
class ExternalTransportAdapter @Inject constructor() : BaseMockTransportAdapter(
    kind = TransportKind.EXTERNAL,
    initialAvailability = TransportAvailability.UNAVAILABLE,
    initialDetail = "No external adapter connected",
    capabilities = setOf(
        TransportCapability.SMALL_TEXT,
        TransportCapability.LARGE_PAYLOAD
    )
)

