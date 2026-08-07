package org.ciphrchat.app.ui.screens

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
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
import org.ciphrchat.app.ui.components.CiphrPrimaryButton
import org.ciphrchat.app.ui.components.CiphrTransportRow
import org.ciphrchat.app.ui.theme.*

@Composable
fun EnableConnectionsScreen(
    onEnable: () -> Unit
) {
    var permissionMessage by remember { mutableStateOf<String?>(null) }
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
        }.toTypedArray()
    }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(CiphrBackground)
            .padding(horizontal = 32.dp)
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        Spacer(Modifier.height(80.dp))

        Text(
            text = "Enable communication",
            style = MaterialTheme.typography.headlineLarge,
            color = CiphrText
        )

        Spacer(Modifier.height(8.dp))

        Text(
            text = "CiphrChat uses the connection methods available on this device.",
            style = MaterialTheme.typography.bodyMedium,
            color = CiphrTextSecondary
        )

        Spacer(Modifier.height(24.dp))

        CiphrTransportRow(Icons.Default.Public, "Internet", "Ready when relay is configured", CiphrTextSecondary)
        HorizontalDivider(color = CiphrBorder)
        CiphrTransportRow(Icons.Default.Wifi, "Wi-Fi and Wi-Fi Direct", "Permission requested below", CiphrWarning)
        HorizontalDivider(color = CiphrBorder)
        CiphrTransportRow(Icons.Default.Bluetooth, "Bluetooth direct", "Permission requested below", CiphrWarning)
        HorizontalDivider(color = CiphrBorder)
        CiphrTransportRow(Icons.Default.GraphicEq, "Nearby audio", "Disabled: experimental", CiphrTextSecondary)
        HorizontalDivider(color = CiphrBorder)
        CiphrTransportRow(Icons.Default.Nfc, "NFC", "Pairing only", CiphrTextSecondary)
        HorizontalDivider(color = CiphrBorder)
        CiphrTransportRow(Icons.Default.RadioButtonChecked, "UWB", "Not detected", CiphrTextSecondary)
        HorizontalDivider(color = CiphrBorder)
        CiphrTransportRow(Icons.Default.SettingsRemote, "Infrared", "Not detected", CiphrTextSecondary)

        permissionMessage?.let {
            Spacer(Modifier.height(12.dp))
            Text(it, style = MaterialTheme.typography.bodySmall, color = CiphrTextSecondary)
        }

        Spacer(Modifier.weight(1f))

        CiphrPrimaryButton(
            text = "Enable available connections",
            onClick = {
                if (nearbyPermissions.isEmpty()) onEnable()
                else permissionLauncher.launch(nearbyPermissions)
            }
        )

        Spacer(Modifier.height(32.dp))
    }
}
