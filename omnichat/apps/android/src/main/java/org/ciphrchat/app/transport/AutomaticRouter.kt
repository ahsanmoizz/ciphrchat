package org.ciphrchat.app.transport

import kotlinx.coroutines.withTimeoutOrNull
import org.ciphrchat.app.identity.ContactRepository
import org.ciphrchat.app.privacy.IpPrivacyPolicy
import org.ciphrchat.app.privacy.PrivacyManager
import javax.inject.Inject
import javax.inject.Singleton

internal val DEFAULT_TRANSPORT_PRIORITY = listOf(
    TransportKind.INTERNET_DIRECT,
    TransportKind.WIFI_LAN,
    TransportKind.UWB_ASSIST,
    TransportKind.BLUETOOTH_DIRECT,
    TransportKind.WIFI_AWARE,
    TransportKind.WIFI_DIRECT,
    TransportKind.BLUETOOTH_MESH,
    TransportKind.ULTRASOUND,
    TransportKind.NFC_PAIRING,
    TransportKind.INTERNET_RELAY
)

@Singleton
class AutomaticRouter @Inject constructor(
    private val registry: TransportRegistry,
    private val capabilityDetector: AndroidCapabilityDetector,
    private val privacyManager: PrivacyManager,
    private val contacts: ContactRepository
) {
    // Ordinary Internet is the default. Nearby radios are resilient fallbacks
    // when the recipient or encrypted mailbox cannot be reached through the relay.
    private val priority = DEFAULT_TRANSPORT_PRIORITY

    suspend fun route(envelope: OutboundEnvelope): SendResult {
        val isIpPrivacyEnabled = privacyManager.isIpPrivacyEnabled.value
        val contact = runCatching { contacts.find(envelope.recipientId) }.getOrNull()

        val candidateKinds = when {
            contact != null && contact.relayAddress.isNotBlank() && !contact.peerId.startsWith("local:") -> {
                // Known Internet contact: prioritize Internet first, followed by local LAN/Bluetooth fallbacks
                listOf(
                    TransportKind.INTERNET_DIRECT,
                    TransportKind.INTERNET_RELAY,
                    TransportKind.WIFI_LAN,
                    TransportKind.BLUETOOTH_DIRECT
                )
            }
            contact != null && contact.peerId.startsWith("local:") -> {
                // Direct local peer: prioritize nearby radios only
                listOf(
                    TransportKind.WIFI_LAN,
                    TransportKind.BLUETOOTH_DIRECT,
                    TransportKind.WIFI_DIRECT,
                    TransportKind.WIFI_AWARE,
                    TransportKind.BLUETOOTH_MESH,
                    TransportKind.ULTRASOUND,
                    TransportKind.NFC_PAIRING
                )
            }
            else -> priority
        }

        val adapters = candidateKinds
            .filter { IpPrivacyPolicy.isTransportAllowed(it, isIpPrivacyEnabled) }
            .mapNotNull(registry::byKind)
        val failures = mutableListOf<String>()
        val snapshot = capabilityDetector.refresh()

        for (adapter in adapters) {
            val capability = snapshot.assessment(adapter.kind)
            if (!capability.canStart) {
                failures += "${adapter.kind}: ${capability.detail}"
                continue
            }
            val started = try {
                adapter.start()
            } catch (error: Throwable) {
                failures += "${adapter.kind}: ${error.message ?: "could not start"}"
                continue
            }
            if (started.isFailure) {
                failures += "${adapter.kind}: ${started.exceptionOrNull()?.message ?: "could not start"}"
                continue
            }
            if (envelope.encryptedPayload.size > LARGE_PAYLOAD_THRESHOLD &&
                !adapter.capabilities.contains(TransportCapability.LARGE_PAYLOAD)
            ) {
                failures += "${adapter.kind}: payload is too large for this transport"
                continue
            }
            val reachability = withTimeoutOrNull(2_000L) {
                adapter.canReach(envelope.recipientId)
            } ?: Reachability.Unknown

            when (reachability) {
                Reachability.Reachable, Reachability.DIRECT, Reachability.MESH_PATH -> {
                    val result = withTimeoutOrNull(12_000L) {
                        adapter.send(envelope)
                    } ?: SendResult.Failed(IllegalStateException("${adapter.kind} send timed out"))

                    when (result) {
                        is SendResult.Accepted -> return result
                        is SendResult.Rejected -> failures += "${adapter.kind}: ${result.reason}"
                        is SendResult.Failed -> failures += "${adapter.kind}: ${result.error.message}"
                        is SendResult.Failure -> failures += "${adapter.kind}: ${result.error.message}"
                        SendResult.Success -> failures += "${adapter.kind}: adapter returned an unverified success"
                    }
                }
                Reachability.Unknown -> failures += "${adapter.kind}: reachability unknown"
                else -> failures += "${adapter.kind}: unreachable"
            }
        }

        return SendResult.Rejected(
            failures.joinToString(separator = "; ").ifBlank { "No transport available" }
        )
    }

    private companion object {
        const val LARGE_PAYLOAD_THRESHOLD = 32 * 1024
    }
}

