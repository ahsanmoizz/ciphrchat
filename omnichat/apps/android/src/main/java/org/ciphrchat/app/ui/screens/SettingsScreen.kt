package org.ciphrchat.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.ciphrchat.app.ui.components.CiphrSectionHeader
import org.ciphrchat.app.ui.theme.*

@Composable
fun SettingsScreen(
    onShareApp: () -> Unit = {},
    onBackupIdentity: () -> Unit = {},
    backupMessage: String? = null
) {
    Column(
        modifier = Modifier.fillMaxSize().background(CiphrBackground).statusBarsPadding()
            .padding(horizontal = 24.dp)
    ) {
        Spacer(Modifier.height(16.dp))
        Text("Settings", style = MaterialTheme.typography.headlineLarge, color = CiphrText)
        Spacer(Modifier.height(24.dp))

        backupMessage?.let {
            Text(it, style = MaterialTheme.typography.bodySmall, color = CiphrTextSecondary)
            Spacer(Modifier.height(8.dp))
        }

        CiphrSectionHeader("Identity")
        SettingsRow("My QR code") {}
        SettingsRow("Display name") {}
        SettingsRow("Back up identity") { onBackupIdentity() }
        SettingsRow("Restore identity") {}

        Spacer(Modifier.height(16.dp))
        CiphrSectionHeader("Connections")
        SettingsRow("Automatic routing") {}
        SettingsRow("Internet") {}
        SettingsRow("Wi-Fi") {}
        SettingsRow("Bluetooth mesh") {}
        SettingsRow("Experimental methods") {}

        Spacer(Modifier.height(16.dp))
        CiphrSectionHeader("Privacy")
        SettingsRow("App lock") {}
        SettingsRow("Local storage") {}
        SettingsRow("Blocked contacts") {}
        SettingsRow("Delete local data") {}

        Spacer(Modifier.height(16.dp))
        CiphrSectionHeader("Distribution")
        SettingsRow("Share CiphrChat") { onShareApp() }
        SettingsRow("Verify installed build") {}
        SettingsRow("Check for update") {}

        Spacer(Modifier.height(16.dp))
        CiphrSectionHeader("About")
        SettingsRow("Source code") {}
        SettingsRow("Open-source licenses") {}
        SettingsRow("Security model") {}
        SettingsRow("Version 0.1.0-dev") {}
    }
}

@Composable
private fun SettingsRow(label: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 14.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge, color = CiphrText)
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = CiphrTextSecondary)
    }
    HorizontalDivider(color = CiphrBorder)
}
