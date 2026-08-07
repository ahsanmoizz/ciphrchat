package org.ciphrchat.app.transport

import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AutomaticRouter @Inject constructor(
    private val registry: TransportRegistry
) {
    private val priority = listOf(
        TransportKind.INTERNET_DIRECT,
        TransportKind.WIFI_LAN,
        TransportKind.WIFI_AWARE,
        TransportKind.WIFI_DIRECT,
        TransportKind.BLUETOOTH_DIRECT,
        TransportKind.BLUETOOTH_MESH,
        TransportKind.ULTRASOUND,
        TransportKind.INFRARED,
        TransportKind.INTERNET_RELAY
    )

    suspend fun route(envelope: OutboundEnvelope): SendResult {
        val adapters = priority.mapNotNull(registry::byKind)
        val failures = mutableListOf<String>()

        for (adapter in adapters) {
            when (adapter.canReach(envelope.recipientId)) {
                Reachability.Reachable, Reachability.DIRECT, Reachability.MESH_PATH -> {
                    when (val result = adapter.send(envelope)) {
                        is SendResult.Accepted -> return result
                        is SendResult.Rejected -> failures += "${adapter.kind}: ${result.reason}"
                        is SendResult.Failed -> failures += "${adapter.kind}: ${result.error.message}"
                        is SendResult.Failure -> failures += "${adapter.kind}: ${result.error.message}"
                        SendResult.Success -> return SendResult.Accepted(adapter.kind, "prototype")
                    }
                }
                Reachability.Unknown -> failures += "${adapter.kind}: reachability unknown"
                is Reachability.Unreachable, Reachability.UNREACHABLE -> failures += "${adapter.kind}: unreachable"
            }
        }

        return SendResult.Rejected(
            failures.joinToString(separator = "; ").ifBlank { "No transport available" }
        )
    }
}
