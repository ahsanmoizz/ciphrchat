package org.ciphrchat.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import dagger.hilt.android.AndroidEntryPoint
import org.ciphrchat.app.app.CiphrChatApp
import org.ciphrchat.app.ui.theme.CiphrChatTheme
import org.ciphrchat.app.transport.nfc.NfcTransportCoordinator
import org.ciphrchat.app.transport.AndroidCapabilityDetector
import org.ciphrchat.app.transport.TransportKind
import org.ciphrchat.app.transport.TransportRuntimeManager
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject lateinit var nfcCoordinator: NfcTransportCoordinator
    @Inject lateinit var transportRuntime: TransportRuntimeManager
    @Inject lateinit var capabilityDetector: AndroidCapabilityDetector

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            CiphrChatTheme {
                CiphrChatApp()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        val capabilities = capabilityDetector.refresh()
        transportRuntime.startAll()
        if (capabilities.assessment(TransportKind.NFC_PAIRING).canStart) {
            runCatching { nfcCoordinator.attach(this) }
        }
    }

    override fun onPause() {
        runCatching { nfcCoordinator.detach(this) }
        super.onPause()
    }
}
