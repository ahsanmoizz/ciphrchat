package org.ciphrchat.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.StateFlow
import org.ciphrchat.app.transport.TransportAvailability
import org.ciphrchat.app.transport.TransportKind
import org.ciphrchat.app.transport.TransportState
import org.ciphrchat.app.ui.components.*
import org.ciphrchat.app.ui.theme.*

@Composable
fun ConnectScreen(
    onScanQr: () -> Unit = {},
    onImportInvitation: (String) -> Unit = {},
    onShowMyQr: () -> Unit = {},
    onFindNearby: () -> Unit = {},
    statusMessage: String? = null,
    nearbyStatus: String? = null,
    transportStates: StateFlow<List<TransportState>>? = null
) {
    var invitation by remember { mutableStateOf("") }
    val states = if (transportStates != null) {
        val collected by transportStates.collectAsState()
        collected
    } else {
        emptyList()
    }
    Column(
        modifier = Modifier.fillMaxSize().background(CiphrBackground).statusBarsPadding()
            .padding(horizontal = 24.dp)
    ) {
        Spacer(Modifier.height(16.dp))
        Text("Connect", style = MaterialTheme.typography.headlineLarge, color = CiphrText)
        Spacer(Modifier.height(24.dp))

        CiphrSectionHeader("Add someone")
        CiphrCard {
            CiphrSecondaryButton("Scan QR", onClick = onScanQr)
            Spacer(Modifier.height(8.dp))
            CiphrSecondaryButton("Show my QR", onClick = onShowMyQr)
            Spacer(Modifier.height(8.dp))
            CiphrSecondaryButton("Find nearby", onClick = onFindNearby)
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = invitation,
                onValueChange = { invitation = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Paste invitation") },
                minLines = 2
            )
            Spacer(Modifier.height(8.dp))
            CiphrSecondaryButton(
                "Pair invitation",
                onClick = { onImportInvitation(invitation.trim()); invitation = "" }
            )
            statusMessage?.let {
                Spacer(Modifier.height(8.dp))
                Text(it, style = MaterialTheme.typography.bodySmall, color = CiphrTextSecondary)
            }
            nearbyStatus?.let {
                Spacer(Modifier.height(4.dp))
                Text(it, style = MaterialTheme.typography.bodySmall, color = CiphrTextSecondary)
            }
        }

        Spacer(Modifier.height(24.dp))
        CiphrSectionHeader("Connection methods")

        CiphrTransportRow(Icons.Default.SwapVert, "Automatic routing", "On", CiphrSuccess)
        HorizontalDivider(color = CiphrBorder)
        TransportRow(states, TransportKind.INTERNET_DIRECT, Icons.Default.Public, "Internet")
        HorizontalDivider(color = CiphrBorder)
        TransportRow(states, TransportKind.WIFI_LAN, Icons.Default.Wifi, "Wi-Fi LAN")
        HorizontalDivider(color = CiphrBorder)
        TransportRow(states, TransportKind.WIFI_AWARE, Icons.Default.WifiTethering, "Wi-Fi Aware")
        HorizontalDivider(color = CiphrBorder)
        TransportRow(states, TransportKind.BLUETOOTH_DIRECT, Icons.Default.Bluetooth, "Bluetooth")
        HorizontalDivider(color = CiphrBorder)
        TransportRow(states, TransportKind.BLUETOOTH_MESH, Icons.Default.Share, "Bluetooth mesh")
        HorizontalDivider(color = CiphrBorder)
        TransportRow(states, TransportKind.ULTRASOUND, Icons.Default.GraphicEq, "Ultrasound")
        HorizontalDivider(color = CiphrBorder)
        TransportRow(states, TransportKind.NFC_PAIRING, Icons.Default.Nfc, "NFC tap session")
        HorizontalDivider(color = CiphrBorder)
        TransportRow(states, TransportKind.INFRARED, Icons.Default.SettingsRemote, "Infrared")
        HorizontalDivider(color = CiphrBorder)
        TransportRow(states, TransportKind.UWB_ASSIST, Icons.Default.RadioButtonChecked, "UWB")
    }
}

@Composable
private fun TransportRow(
    states: List<TransportState>,
    kind: TransportKind,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    name: String
) {
    val state = states.firstOrNull { it.kind == kind }
    val availability = state?.availability ?: TransportAvailability.UNAVAILABLE
    val color = when (availability) {
        TransportAvailability.AVAILABLE -> CiphrSuccess
        TransportAvailability.PERMISSION_REQUIRED,
        TransportAvailability.STARTING -> CiphrWarning
        TransportAvailability.ERROR -> CiphrDanger
        else -> CiphrTextSecondary
    }
    CiphrTransportRow(icon, name, state?.detail ?: "Not configured", color)
}
