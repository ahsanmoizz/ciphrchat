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
}
