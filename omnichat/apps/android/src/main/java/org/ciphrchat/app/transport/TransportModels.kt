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
) {
    /** Compatibility constructor for adapters that do not specify their kind in state updates. */
    constructor(availability: TransportAvailability, detail: String) : this(
        TransportKind.EXTERNAL,
        availability,
        detail
    )
}

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
    val signalHint: Int? = null,
    val lastSeenEpochMs: Long = System.currentTimeMillis()
) {
    /** Compatibility constructor for the original discovery model used by older adapters. */
    constructor(
        id: String,
        displayName: String?,
        transportKind: TransportKind,
        reachabilityScore: Int,
        lastSeenEpochMs: Long,
        compatibility: Unit = Unit
    ) : this(
        ephemeralId = id,
        displayHint = displayName,
        transport = transportKind,
        signalHint = reachabilityScore,
        lastSeenEpochMs = lastSeenEpochMs
    )

    val id: String get() = ephemeralId
    val displayName: String? get() = displayHint
    val transportKind: TransportKind get() = transport
    val reachabilityScore: Int get() = signalHint ?: 0
}

sealed interface Reachability {
    data object Reachable : Reachability
    data object Unknown : Reachability
    data class Unreachable(val reason: String) : Reachability

    /** Compatibility states used by the prototype transport adapters. */
    data object DIRECT : Reachability
    data object MESH_PATH : Reachability
    data object UNREACHABLE : Reachability
}

sealed interface SendResult {
    data class Accepted(val transport: TransportKind, val routeId: String) : SendResult
    data class Rejected(val reason: String) : SendResult
    data class Failed(val error: Throwable) : SendResult

    /** Compatibility results used by the prototype transport adapters. */
    data object Success : SendResult
    data class Failure(val error: Throwable) : SendResult
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
) {
    /** Compatibility constructor for the original prototype message envelope. */
    constructor(
        messageId: ByteArray,
        recipientTag: ByteArray,
        encryptedPayload: ByteArray,
        hopLimit: Int
    ) : this(
        protocolVersion = 1,
        messageId = messageId.decodeToString(),
        recipientId = recipientTag.decodeToString(),
        createdAtEpochMs = System.currentTimeMillis(),
        expiresAtEpochMs = System.currentTimeMillis() + 7 * 24 * 60 * 60 * 1000L,
        hopLimit = hopLimit,
        encryptedPayload = encryptedPayload,
        testOnly = true
    )

    /** Compatibility property for socket transports that still consume a byte-array tag. */
    val recipientTag: ByteArray get() = recipientId.toByteArray()
}
