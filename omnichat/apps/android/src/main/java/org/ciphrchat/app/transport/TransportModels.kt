package org.ciphrchat.app.transport

enum class TransportKind {
    INTERNET_DIRECT,
    INTERNET_RELAY,
    WIFI_LAN,
    WIFI_AWARE,
    WIFI_DIRECT,
    BLUETOOTH_DIRECT,
    BLUETOOTH_MESH,
    ULTRASOUND,
    INFRARED,
    NFC_PAIRING,
    UWB_ASSIST,
    EXTERNAL
}

enum class TransportAvailability {
    AVAILABLE,
    UNAVAILABLE,
    PERMISSION_REQUIRED,
    DISABLED_BY_USER,
    EXPERIMENTAL,
    STARTING,
    ERROR
}

data class TransportState(
    val kind: TransportKind,
    val availability: TransportAvailability,
    val detail: String,
    val lastUpdatedEpochMs: Long = System.currentTimeMillis()
)

enum class TransportCapability {
    DISCOVERY,
    SMALL_TEXT,
    LARGE_PAYLOAD,
    MULTI_HOP,
    PAIRING,
    RANGING,
    OFFLINE
}

data class DiscoveredPeer(
    val ephemeralId: String,
    val displayHint: String?,
    val transport: TransportKind,
    val signalHint: Int? = null
)

sealed interface Reachability {
    data object Reachable : Reachability
    data object Unknown : Reachability
    data class Unreachable(val reason: String) : Reachability
}

sealed interface SendResult {
    data class Accepted(val transport: TransportKind, val routeId: String) : SendResult
    data class Rejected(val reason: String) : SendResult
    data class Failed(val error: Throwable) : SendResult
}

data class OutboundEnvelope(
    val protocolVersion: Int,
    val messageId: String,
    val recipientId: String,
    val createdAtEpochMs: Long,
    val expiresAtEpochMs: Long,
    val hopLimit: Int,
    val encryptedPayload: ByteArray,
    /** Any envelope created by mock crypto must set testOnly = true */
    val testOnly: Boolean
)
