package org.ciphrchat.app.permissions

import javax.inject.Inject
import javax.inject.Singleton

/**
 * Coordinates progressive permission requests.
 * Phase 1 scaffold — actual permission request logic is handled in Compose screens.
 */
@Singleton
class PermissionCoordinator @Inject constructor() {
    fun allGroups(): List<PermissionGroup> = listOf(
        PermissionSets.bluetooth,
        PermissionSets.nearbyWifi,
        PermissionSets.microphone,
        PermissionSets.notifications
    )
}
