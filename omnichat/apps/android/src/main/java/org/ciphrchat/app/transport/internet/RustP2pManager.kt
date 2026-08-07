package org.ciphrchat.app.transport.internet

import javax.inject.Inject
import javax.inject.Singleton
import android.util.Log

@Singleton
class RustP2pManager @Inject constructor() {
    init {
        try {
            System.loadLibrary("ciphrchat_ffi")
        } catch (e: UnsatisfiedLinkError) {
            Log.e("RustP2pManager", "Failed to load ciphrchat_ffi native library", e)
        }
    }

    external fun startSwarm()
    external fun publishMessage(payload: ByteArray)

    companion object {
        @JvmStatic
        fun onMessageReceived(payload: ByteArray) {
            Log.d("RustP2pManager", "Received payload from Rust swarm: ${payload.size} bytes")
            // Here, it would be pushed to a Kotlin Flow/Channel for the app to consume.
        }
    }
}
