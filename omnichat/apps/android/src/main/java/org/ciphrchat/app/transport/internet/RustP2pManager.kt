package org.ciphrchat.app.transport.internet

import android.content.Context
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import org.ciphrchat.app.BuildConfig
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

sealed interface RustNetworkEvent {
    data class SwarmReady(val peerId: String) : RustNetworkEvent
    data class MessageReceived(val peerId: String, val payload: ByteArray) : RustNetworkEvent
    data class DeliveryAccepted(val peerId: String) : RustNetworkEvent
    data class DeliveryFailed(val peerId: String, val reason: String) : RustNetworkEvent
    data class NetworkError(val detail: String) : RustNetworkEvent
}

@Singleton
class RustP2pManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val _events = MutableSharedFlow<RustNetworkEvent>(extraBufferCapacity = 64)
    val events: SharedFlow<RustNetworkEvent> = _events.asSharedFlow()

    private val peerKeyFile = File(context.filesDir, "ciphrchat-peer-key.bin")

    init {
        active = this
        try {
            System.loadLibrary("ciphrchat_ffi")
        } catch (error: UnsatisfiedLinkError) {
            Log.e(TAG, "Failed to load ciphrchat_ffi native library", error)
        }
    }

    fun startSwarm(relayAddress: String = BuildConfig.CIPHRCHAT_RELAY_ADDRESS): Result<Unit> {
        if (relayAddress.isBlank()) {
            return Result.failure(IllegalStateException("No CiphrChat relay address is configured"))
        }
        return runCatching {
            check(nativeStartSwarm(relayAddress, peerKeyFile.absolutePath)) {
                "Native relay client rejected startup"
            }
        }
    }

    fun connectPeer(peerId: String, address: String): Boolean =
        nativeConnectPeer(peerId, address)

    fun sendMessage(peerId: String, payload: ByteArray): Boolean =
        nativeSendMessage(peerId, payload)

    fun localPeerId(): String? = nativeLocalPeerId()?.toString()

    private external fun nativeStartSwarm(relayAddress: String, keyFile: String): Boolean
    private external fun nativeConnectPeer(peerId: String, address: String): Boolean
    private external fun nativeSendMessage(peerId: String, payload: ByteArray): Boolean
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
        fun onMessageReceived(peerId: String, payload: ByteArray) {
            active?._events?.tryEmit(RustNetworkEvent.MessageReceived(peerId, payload))
        }

        @JvmStatic
        fun onDeliveryAccepted(peerId: String) {
            active?._events?.tryEmit(RustNetworkEvent.DeliveryAccepted(peerId))
        }

        @JvmStatic
        fun onDeliveryFailed(peerId: String, reason: String) {
            active?._events?.tryEmit(RustNetworkEvent.DeliveryFailed(peerId, reason))
        }

        @JvmStatic
        fun onNetworkError(detail: String) {
            active?._events?.tryEmit(RustNetworkEvent.NetworkError(detail))
        }
    }
}
