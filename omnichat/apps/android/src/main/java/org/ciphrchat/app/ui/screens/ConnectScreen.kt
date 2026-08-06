package org.ciphrchat.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.ciphrchat.app.ui.components.*
import org.ciphrchat.app.ui.theme.*

@Composable
fun ConnectScreen(
    onScanQr: () -> Unit = {}
) {
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
            CiphrSecondaryButton("Show my QR", onClick = {})
            Spacer(Modifier.height(8.dp))
            CiphrSecondaryButton("Find nearby", onClick = {})
            Spacer(Modifier.height(8.dp))
            CiphrSecondaryButton("Enter invitation", onClick = {})
        }

        Spacer(Modifier.height(24.dp))
        CiphrSectionHeader("Connection methods")

        CiphrTransportRow(Icons.Default.SwapVert, "Automatic routing", "On", CiphrSuccess)
        HorizontalDivider(color = CiphrBorder)
        CiphrTransportRow(Icons.Default.Public, "Internet", "Available", CiphrSuccess)
        HorizontalDivider(color = CiphrBorder)
        CiphrTransportRow(Icons.Default.Wifi, "Wi-Fi", "Searching", CiphrWarning)
        HorizontalDivider(color = CiphrBorder)
        CiphrTransportRow(Icons.Default.Bluetooth, "Bluetooth", "Permission needed", CiphrWarning)
        HorizontalDivider(color = CiphrBorder)
        CiphrTransportRow(Icons.Default.GraphicEq, "Ultrasound", "Experimental", CiphrTextSecondary)
        HorizontalDivider(color = CiphrBorder)
        CiphrTransportRow(Icons.Default.SettingsRemote, "Infrared", "Not supported", CiphrTextSecondary)
        HorizontalDivider(color = CiphrBorder)
        CiphrTransportRow(Icons.Default.RadioButtonChecked, "UWB", "Nearby assist", CiphrTextSecondary)
    }
}
