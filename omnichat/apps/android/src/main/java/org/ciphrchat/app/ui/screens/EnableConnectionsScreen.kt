package org.ciphrchat.app.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.StateFlow
import org.ciphrchat.app.transport.TransportAvailability
import org.ciphrchat.app.transport.TransportKind
import org.ciphrchat.app.transport.TransportState
import org.ciphrchat.app.ui.components.CiphrPrimaryButton
import org.ciphrchat.app.ui.components.CiphrTransportRow
import org.ciphrchat.app.ui.theme.*

@Composable
fun EnableConnectionsScreen(
    transportStates: StateFlow<List<TransportState>>,
    missingPermissions: List<String>,
    onPermissionsChanged: () -> Unit,
    onEnable: () -> Unit
) {
    var permissionMessage by remember { mutableStateOf<String?>(null) }
    val scrollState = rememberScrollState()
    val states by transportStates.collectAsStateWithLifecycle()
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        val denied = results.filterValues { !it }.keys
        permissionMessage = if (denied.isEmpty()) {
            "Available connection permissions granted"
        } else {
            "Some connection permissions were denied. You can grant them later from Connect."
        }
        onPermissionsChanged()
        onEnable()
    }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .background(CiphrBackground)
            .padding(horizontal = 32.dp)
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        Spacer(Modifier.height(80.dp))

        Text(
            text = "Choose your connections",
            style = MaterialTheme.typography.headlineLarge,
            color = CiphrText
        )

        Spacer(Modifier.height(8.dp))

        Text(
            text = "CiphrChat uses secure Internet delivery whenever available, with nearby transports as device-supported fallbacks.",
            style = MaterialTheme.typography.bodyMedium,
            color = CiphrTextSecondary
        )

        Spacer(Modifier.height(24.dp))

        CapabilityRow(states, TransportKind.INTERNET_DIRECT, Icons.Default.Public, "Internet")
        HorizontalDivider(color = CiphrBorder)
        CapabilityRow(states, TransportKind.WIFI_DIRECT, Icons.Default.Wifi, "Wi-Fi and Wi-Fi Direct")
        HorizontalDivider(color = CiphrBorder)
        CapabilityRow(states, TransportKind.BLUETOOTH_DIRECT, Icons.Default.Bluetooth, "Bluetooth direct")
        HorizontalDivider(color = CiphrBorder)
        CapabilityRow(states, TransportKind.WIFI_AWARE, Icons.Default.WifiTethering, "Wi-Fi Aware")
        HorizontalDivider(color = CiphrBorder)
        CapabilityRow(states, TransportKind.ULTRASOUND, Icons.Default.GraphicEq, "Nearby audio")
        HorizontalDivider(color = CiphrBorder)
        CapabilityRow(states, TransportKind.NFC_PAIRING, Icons.Default.Nfc, "NFC")
        HorizontalDivider(color = CiphrBorder)
        CapabilityRow(states, TransportKind.UWB_ASSIST, Icons.Default.RadioButtonChecked, "UWB")
        HorizontalDivider(color = CiphrBorder)
        CapabilityRow(states, TransportKind.INFRARED, Icons.Default.SettingsRemote, "Infrared")

        permissionMessage?.let {
            Spacer(Modifier.height(12.dp))
            Text(it, style = MaterialTheme.typography.bodySmall, color = CiphrTextSecondary)
        }

        Spacer(Modifier.height(24.dp))

        CiphrPrimaryButton(
            text = "Continue",
            onClick = {
                if (missingPermissions.isEmpty()) onEnable()
                else permissionLauncher.launch(missingPermissions.toTypedArray())
            }
        )

        Spacer(Modifier.height(40.dp))
    }
}

@Composable
private fun CapabilityRow(
    states: List<TransportState>,
    kind: TransportKind,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    name: String
) {
    val state = states.firstOrNull { it.kind == kind }
    val availability = state?.availability ?: TransportAvailability.STARTING
    val color = when (availability) {
        TransportAvailability.AVAILABLE -> CiphrSuccess
        TransportAvailability.PERMISSION_REQUIRED, TransportAvailability.STARTING -> CiphrWarning
        TransportAvailability.ERROR -> CiphrDanger
        else -> CiphrTextSecondary
    }
    CiphrTransportRow(icon, name, state?.detail ?: "Checking this device…", color)
}
