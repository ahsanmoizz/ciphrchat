package org.ciphrchat.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.ciphrchat.app.ui.components.CiphrPrimaryButton
import org.ciphrchat.app.ui.components.CiphrTransportRow
import org.ciphrchat.app.ui.theme.*

@Composable
fun EnableConnectionsScreen(
    onEnable: () -> Unit
) {
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

        CiphrTransportRow(Icons.Default.Public, "Internet", "Available", CiphrSuccess)
        HorizontalDivider(color = CiphrBorder)
        CiphrTransportRow(Icons.Default.Wifi, "Wi-Fi and Wi-Fi Direct", "Available", CiphrSuccess)
        HorizontalDivider(color = CiphrBorder)
        CiphrTransportRow(Icons.Default.Bluetooth, "Bluetooth and mesh", "Permission needed", CiphrWarning)
        HorizontalDivider(color = CiphrBorder)
        CiphrTransportRow(Icons.Default.GraphicEq, "Nearby audio", "Experimental", CiphrTextSecondary)
        HorizontalDivider(color = CiphrBorder)
        CiphrTransportRow(Icons.Default.Nfc, "NFC", "Available", CiphrSuccess)
        HorizontalDivider(color = CiphrBorder)
        CiphrTransportRow(Icons.Default.RadioButtonChecked, "UWB", "Not detected", CiphrTextSecondary)
        HorizontalDivider(color = CiphrBorder)
        CiphrTransportRow(Icons.Default.SettingsRemote, "Infrared", "Not detected", CiphrTextSecondary)

        Spacer(Modifier.weight(1f))

        CiphrPrimaryButton(
            text = "Enable available connections",
            onClick = onEnable
        )

        Spacer(Modifier.height(32.dp))
    }
}
