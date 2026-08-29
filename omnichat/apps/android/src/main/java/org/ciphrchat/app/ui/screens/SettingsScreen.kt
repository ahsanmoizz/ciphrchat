package org.ciphrchat.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.ciphrchat.app.BuildConfig
import org.ciphrchat.app.privacy.PrivacyManager
import org.ciphrchat.app.ui.components.CiphrSectionHeader
import org.ciphrchat.app.ui.theme.*

@Composable
fun SettingsScreen(
    onShareApp: () -> Unit = {},
    onBackupIdentity: () -> Unit = {},
    onShowQr: () -> Unit = {},
    onRestoreIdentity: () -> Unit = {},
    isIpPrivacyEnabled: Boolean = true,
    onToggleIpPrivacy: (Boolean) -> Unit = {},
    backupMessage: String? = null
) {
    val scrollState = rememberScrollState()
    Column(
        modifier = Modifier.fillMaxSize().background(CiphrBackground).statusBarsPadding()
            .verticalScroll(scrollState)
            .padding(horizontal = 24.dp)
            .padding(bottom = 24.dp)
    ) {
        Spacer(Modifier.height(16.dp))
        Text("Settings", style = MaterialTheme.typography.headlineLarge, color = CiphrText)
        Spacer(Modifier.height(24.dp))

        backupMessage?.let {
            Text(it, style = MaterialTheme.typography.bodySmall, color = CiphrTextSecondary)
            Spacer(Modifier.height(8.dp))
        }

        CiphrSectionHeader("Identity")
        SettingsRow("My QR code", onShowQr)
        SettingsInfoRow("Display name", "Edit from a future profile release")
        SettingsRow("Back up identity") { onBackupIdentity() }
        SettingsRow("Restore identity", onRestoreIdentity)

        Spacer(Modifier.height(16.dp))
        CiphrSectionHeader("Privacy")
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f).padding(end = 16.dp)) {
                Text("Hide my IP", style = MaterialTheme.typography.bodyLarge, color = CiphrText)
                Text(
                    PrivacyManager.TRUTHFUL_PRIVACY_EXPLANATION,
                    style = MaterialTheme.typography.bodySmall,
                    color = CiphrTextSecondary
                )
            }
            Switch(
                checked = isIpPrivacyEnabled,
                onCheckedChange = onToggleIpPrivacy,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = CiphrPrimary,
                    checkedTrackColor = CiphrPrimary.copy(alpha = 0.5f)
                )
            )
        }
        HorizontalDivider(color = CiphrBorder)
        SettingsInfoRow("Local storage", "Messages and attachments are encrypted in CiphrChat's private app storage")
        SettingsInfoRow("App lock", "Not available in this release")
        SettingsInfoRow("Blocked contacts", "Not available in this release")
        SettingsInfoRow("Delete local data", "Use Android app storage settings")

        Spacer(Modifier.height(16.dp))
        CiphrSectionHeader("Connections")
        SettingsInfoRow("Automatic routing", "Always enabled")
        SettingsInfoRow("Internet", "Primary long-range route over mobile data or Internet-connected Wi-Fi")
        SettingsInfoRow("Wi-Fi LAN", "Works on the same local network even when upstream Internet is unavailable")
        SettingsInfoRow("Bluetooth mesh", "Counts and forwards only through nearby CiphrChat participants")
        SettingsInfoRow("Nearby methods", "Wi-Fi Aware, Wi-Fi Direct, Bluetooth, and acoustic delivery are device-dependent")

        Spacer(Modifier.height(16.dp))
        CiphrSectionHeader("Distribution")
        SettingsRow("Share CiphrChat") { onShareApp() }
        SettingsInfoRow("Verify installed build", "Compare the release checksum on GitHub")
        SettingsInfoRow("Check for update", "Install releases from the project page")

        Spacer(Modifier.height(16.dp))
        CiphrSectionHeader("About")
        SettingsInfoRow("Source code", "github.com/ahsanmoizz/ciphrchat")
        SettingsInfoRow("Open-source licenses", "See the repository notices")
        SettingsInfoRow("Security model", "See docs/ARCHITECTURE.md")
        SettingsInfoRow("Version ${BuildConfig.VERSION_NAME}", "Cross-network reliability build")
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

@Composable
private fun SettingsInfoRow(label: String, detail: String) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp)) {
        Text(label, style = MaterialTheme.typography.bodyLarge, color = CiphrText)
        Text(detail, style = MaterialTheme.typography.bodySmall, color = CiphrTextSecondary)
    }
    HorizontalDivider(color = CiphrBorder)
}
