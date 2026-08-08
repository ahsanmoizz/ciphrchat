package org.ciphrchat.app.permissions

data class PermissionGroup(
    val name: String,
    val permissions: List<String>,
    val rationale: String,
    val isGranted: Boolean = false
)

object PermissionSets {
    val bluetooth = PermissionGroup(
        name = "Bluetooth",
        permissions = listOf(
            "android.permission.BLUETOOTH_SCAN",
            "android.permission.BLUETOOTH_CONNECT",
            "android.permission.BLUETOOTH_ADVERTISE"
        ),
        rationale = "Bluetooth enables direct device-to-device messaging and mesh forwarding."
    )

    val nearbyWifi = PermissionGroup(
        name = "Wi-Fi Direct & Aware",
        permissions = listOf("android.permission.NEARBY_WIFI_DEVICES"),
        rationale = "Wi-Fi Direct and Aware enable high-speed nearby communication."
    )

    val microphone = PermissionGroup(
        name = "Microphone (Ultrasound)",
        permissions = listOf("android.permission.RECORD_AUDIO"),
        rationale = "Microphone access enables secure short-message transfer over nearby audio."
    )

    val notifications = PermissionGroup(
        name = "Notifications",
        permissions = listOf("android.permission.POST_NOTIFICATIONS"),
        rationale = "Notifications alert you to new messages when the app is in the background."
    )
}
