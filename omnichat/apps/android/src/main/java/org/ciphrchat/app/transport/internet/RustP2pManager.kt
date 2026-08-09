package org.ciphrchat.app.transport.internet

import android.util.Log
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import org.ciphrchat.app.BuildConfig
import org.ciphrchat.app.security.PeerKeyStore
import javax.inject.Inject
import javax.inject.Singleton

sealed interface RustNetworkEvent {
    data class SwarmReady(val peerId: String) : RustNetworkEvent
    data class RelayReservationReady(val relayPeerId: String) : RustNetworkEvent
    data class MessageReceived(val peerId: String, val payload: ByteArray) : RustNetworkEvent
    data class DeliveryAccepted(val peerId: String, val messageId: String) : RustNetworkEvent
    data class DeliveryFailed(val peerId: String, val messageId: String, val reason: String) : RustNetworkEvent
    data class NetworkError(val detail: String) : RustNetworkEvent
}

@Singleton
class RustP2pManager @Inject constructor(
    private val peerKeyStore: PeerKeyStore
) {
    private val libraryLoaded: Boolean
    private val _events = MutableSharedFlow<RustNetworkEvent>(extraBufferCapacity = 64)
    val events: SharedFlow<RustNetworkEvent> = _events.asSharedFlow()

    init {
        active = this
        libraryLoaded = try {
            System.loadLibrary("ciphrchat_ffi")
            true
        } catch (error: UnsatisfiedLinkError) {
            Log.e(TAG, "Failed to load ciphrchat_ffi native library", error)
            false
        }
    }

    fun startSwarm(relayAddress: String = BuildConfig.CIPHRCHAT_RELAY_ADDRESS): Result<Unit> {
        if (!libraryLoaded) {
            return Result.failure(IllegalStateException("Secure network engine is unavailable on this device ABI"))
        }
        if (relayAddress.isBlank()) {
            return Result.failure(IllegalStateException("No CiphrChat relay address is configured"))
        }
        return runCatching {
            val started = peerKeyStore.withNativeKeyFile { keyFile ->
                nativeStartSwarm(relayAddress, keyFile)
            }
            check(started) {
                "Native relay client rejected startup"
            }
        }
    }

    fun connectPeer(peerId: String, address: String): Boolean = libraryLoaded &&
        runCatching { nativeConnectPeer(peerId, address) }.getOrDefault(false)

    fun sendMessage(peerId: String, messageId: String, payload: ByteArray): Boolean = libraryLoaded &&
        runCatching { nativeSendMessage(peerId, messageId, payload) }.getOrDefault(false)

    fun localPeerId(): String? = if (libraryLoaded) runCatching { nativeLocalPeerId() }.getOrNull() else null

    private external fun nativeStartSwarm(relayAddress: String, keyFile: String): Boolean
    private external fun nativeConnectPeer(peerId: String, address: String): Boolean
    private external fun nativeSendMessage(peerId: String, messageId: String, payload: ByteArray): Boolean
    private external fun nativeLocalPeerId(): String?

    companion object {
        private const val TAG = "RustP2pManager"

        @Volatile
        private var active: RustP2pManager? = null

        @JvmStatic
        fun onSwarmReady(peerId: String) {
            active?._events?.tryEmit(RustNetworkEvent.SwarmReady(peerId))
        }

        @JvmStatic
        fun onRelayReservationReady(relayPeerId: String) {
            active?._events?.tryEmit(RustNetworkEvent.RelayReservationReady(relayPeerId))
        }

        @JvmStatic
        fun onMessageReceived(peerId: String, payload: ByteArray) {
            active?._events?.tryEmit(RustNetworkEvent.MessageReceived(peerId, payload))
        }

        @JvmStatic
        fun onDeliveryAccepted(peerId: String, messageId: String) {
            active?._events?.tryEmit(RustNetworkEvent.DeliveryAccepted(peerId, messageId))
        }

        @JvmStatic
        fun onDeliveryFailed(peerId: String, messageId: String, reason: String) {
            active?._events?.tryEmit(RustNetworkEvent.DeliveryFailed(peerId, messageId, reason))
        }

        @JvmStatic
        fun onNetworkError(detail: String) {
            active?._events?.tryEmit(RustNetworkEvent.NetworkError(detail))
        }
    }
}
