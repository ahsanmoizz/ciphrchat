package org.ciphrchat.app.transport.internet

import android.util.Log
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import org.ciphrchat.app.BuildConfig
import org.ciphrchat.app.security.PeerKeyStore
import javax.inject.Inject
import javax.inject.Singleton
import java.util.concurrent.atomic.AtomicBoolean

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
    private val _relayReservationReady = MutableStateFlow(false)
    val relayReservationReady: StateFlow<Boolean> = _relayReservationReady.asStateFlow()
    private val swarmStartAccepted = AtomicBoolean(false)
    private val deliveryAwaiter = DeliveryAwaiter()

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
        if (swarmStartAccepted.get()) return Result.success(Unit)
        return runCatching {
            val started = peerKeyStore.withNativeKeyFile { keyFile ->
                nativeStartSwarm(relayAddress, keyFile)
            }
            check(started) {
                "Native relay client rejected startup"
            }
            swarmStartAccepted.set(true)
        }
    }

    fun connectPeer(peerId: String, address: String): Boolean = libraryLoaded &&
        runCatching { nativeConnectPeer(peerId, address) }.getOrDefault(false)

    private fun queueMessage(peerId: String, messageId: String, payload: ByteArray): Boolean = libraryLoaded &&
        runCatching { nativeSendMessage(peerId, messageId, payload) }.getOrDefault(false)

    suspend fun awaitRelayReservation(timeoutMs: Long = 20_000L): Result<Unit> {
        if (!libraryLoaded) return Result.failure(
            IllegalStateException("Secure network engine is unavailable on this device ABI")
        )
        if (_relayReservationReady.value) return Result.success(Unit)
        val ready = withTimeoutOrNull(timeoutMs) { _relayReservationReady.first { it } } == true
        return if (ready) Result.success(Unit) else Result.failure(
            IllegalStateException("Secure relay connection timed out")
        )
    }

    fun hasRelayReservation(): Boolean = _relayReservationReady.value

    suspend fun sendMessageAwaitingDelivery(
        peerId: String,
        messageId: String,
        payload: ByteArray,
        timeoutMs: Long = 40_000L
    ): Result<Unit> {
        return deliveryAwaiter.await(messageId, timeoutMs) {
            queueMessage(peerId, messageId, payload)
        }
    }

    fun sendControlMessage(peerId: String, messageId: String, payload: ByteArray): Boolean =
        queueMessage(peerId, messageId, payload)

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
            active?.let { manager ->
                manager._relayReservationReady.value = true
                manager._events.tryEmit(RustNetworkEvent.RelayReservationReady(relayPeerId))
            }
        }

        @JvmStatic
        fun onMessageReceived(peerId: String, payload: ByteArray) {
            active?._events?.tryEmit(RustNetworkEvent.MessageReceived(peerId, payload))
        }

        @JvmStatic
        fun onDeliveryAccepted(peerId: String, messageId: String) {
            active?.let { manager ->
                manager.deliveryAwaiter.accepted(messageId)
                manager._events.tryEmit(RustNetworkEvent.DeliveryAccepted(peerId, messageId))
            }
        }

        @JvmStatic
        fun onDeliveryFailed(peerId: String, messageId: String, reason: String) {
            active?.let { manager ->
                manager.deliveryAwaiter.failed(messageId, reason)
                manager._events.tryEmit(RustNetworkEvent.DeliveryFailed(peerId, messageId, reason))
            }
        }

        @JvmStatic
        fun onNetworkError(detail: String) {
            active?._events?.tryEmit(RustNetworkEvent.NetworkError(detail))
        }
    }
}
