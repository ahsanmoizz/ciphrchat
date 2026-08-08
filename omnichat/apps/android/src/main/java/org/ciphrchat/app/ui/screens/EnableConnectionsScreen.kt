package org.ciphrchat.app.ui.screens

import android.Manifest
import android.os.Build
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.ciphrchat.app.BuildConfig
import org.ciphrchat.app.ui.components.CiphrPrimaryButton
import org.ciphrchat.app.ui.components.CiphrTransportRow
import org.ciphrchat.app.ui.theme.*

@Composable
fun EnableConnectionsScreen(
    onEnable: () -> Unit
) {
    var permissionMessage by remember { mutableStateOf<String?>(null) }
    val scrollState = rememberScrollState()
    val relayConfigured = BuildConfig.CIPHRCHAT_RELAY_ADDRESS.isNotBlank()
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        val denied = results.filterValues { !it }.keys
        permissionMessage = if (denied.isEmpty()) {
            "Available connection permissions granted"
        } else {
            "Some nearby permissions were denied; Internet messaging still works when configured"
        }
        onEnable()
    }
    val nearbyPermissions = remember {
        buildList {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                add(Manifest.permission.BLUETOOTH_SCAN)
                add(Manifest.permission.BLUETOOTH_CONNECT)
                add(Manifest.permission.BLUETOOTH_ADVERTISE)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                add(Manifest.permission.NEARBY_WIFI_DEVICES)
            } else {
                add(Manifest.permission.ACCESS_FINE_LOCATION)
                add(Manifest.permission.ACCESS_COARSE_LOCATION)
            }
            add(Manifest.permission.RECORD_AUDIO)
        }.toTypedArray()
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
            text = "Internet messaging is ready. Nearby connections are optional and require permission.",
            style = MaterialTheme.typography.bodyMedium,
            color = CiphrTextSecondary
        )

        Spacer(Modifier.height(24.dp))

        CiphrTransportRow(
            Icons.Default.Public,
            "Internet",
            if (relayConfigured) "Secure messaging ready" else "Unavailable in this build",
            if (relayConfigured) CiphrSuccess else CiphrDanger
        )
        HorizontalDivider(color = CiphrBorder)
        CiphrTransportRow(Icons.Default.Wifi, "Wi-Fi and Wi-Fi Direct", "Nearby permission", CiphrWarning)
        HorizontalDivider(color = CiphrBorder)
        CiphrTransportRow(Icons.Default.Bluetooth, "Bluetooth direct", "Nearby permission", CiphrWarning)
        HorizontalDivider(color = CiphrBorder)
        CiphrTransportRow(Icons.Default.WifiTethering, "Wi-Fi Aware", "Compatible devices", CiphrTextSecondary)
        HorizontalDivider(color = CiphrBorder)
        CiphrTransportRow(Icons.Default.GraphicEq, "Nearby audio", "Microphone permission", CiphrWarning)
        HorizontalDivider(color = CiphrBorder)
        CiphrTransportRow(Icons.Default.Nfc, "NFC", "Tap phones to transfer", CiphrTextSecondary)
        HorizontalDivider(color = CiphrBorder)
        CiphrTransportRow(Icons.Default.RadioButtonChecked, "UWB", "Proximity verification", CiphrTextSecondary)
        HorizontalDivider(color = CiphrBorder)
        CiphrTransportRow(Icons.Default.SettingsRemote, "Infrared", "Transmitter only on Android", CiphrTextSecondary)

        permissionMessage?.let {
            Spacer(Modifier.height(12.dp))
            Text(it, style = MaterialTheme.typography.bodySmall, color = CiphrTextSecondary)
        }

        Spacer(Modifier.height(24.dp))

        CiphrPrimaryButton(
            text = "Continue",
            onClick = {
                if (nearbyPermissions.isEmpty()) onEnable()
                else permissionLauncher.launch(nearbyPermissions)
            }
        )

        Spacer(Modifier.height(40.dp))
    }
}
