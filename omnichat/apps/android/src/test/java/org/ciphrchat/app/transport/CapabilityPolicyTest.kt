package org.ciphrchat.app.transport

import android.Manifest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CapabilityPolicyTest {
    @Test
    fun android13WithBluetoothPermissionIsReady() {
        val snapshot = CapabilityPolicy.evaluate(
            features(sdkInt = 33, hasBluetoothLe = true, bluetoothEnabled = true),
            setOf(
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_CONNECT,
                Manifest.permission.BLUETOOTH_ADVERTISE,
                Manifest.permission.NEARBY_WIFI_DEVICES
            )
        )

        assertTrue(snapshot.assessment(TransportKind.BLUETOOTH_DIRECT).canStart)
    }

    @Test
    fun android13BluetoothRequestsOnlyNearbyBluetoothPermissions() {
        val snapshot = CapabilityPolicy.evaluate(
            features(sdkInt = 33, hasBluetoothLe = true, bluetoothEnabled = null),
            emptySet()
        )

        val bluetooth = snapshot.assessment(TransportKind.BLUETOOTH_DIRECT)
        assertEquals(TransportAvailability.PERMISSION_REQUIRED, bluetooth.availability)
        assertEquals(
            setOf(
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_CONNECT,
                Manifest.permission.BLUETOOTH_ADVERTISE
            ),
            bluetooth.missingPermissions.toSet()
        )
    }

    @Test
    fun android11BluetoothRequiresLocationPermissionAndService() {
        val missing = CapabilityPolicy.evaluate(
            features(sdkInt = 30, hasBluetoothLe = true, bluetoothEnabled = true),
            emptySet()
        ).assessment(TransportKind.BLUETOOTH_DIRECT)
        assertEquals(TransportAvailability.PERMISSION_REQUIRED, missing.availability)

        val locationOff = CapabilityPolicy.evaluate(
            features(
                sdkInt = 30,
                hasBluetoothLe = true,
                bluetoothEnabled = true,
                locationEnabled = false
            ),
            setOf(Manifest.permission.ACCESS_FINE_LOCATION)
        ).assessment(TransportKind.BLUETOOTH_DIRECT)
        assertFalse(locationOff.canStart)
        assertTrue(locationOff.detail.contains("Location"))
    }

    @Test
    fun android13WithoutUwbReportsMissingHardwareNotOldAndroid() {
        val uwb = CapabilityPolicy.evaluate(
            features(sdkInt = 33, hasUwb = false),
            emptySet()
        ).assessment(TransportKind.UWB_ASSIST)

        assertTrue(uwb.detail.contains("No UWB radio"))
        assertTrue(uwb.detail.contains("Android version alone"))
    }

    @Test
    fun android11ReportsUwbAndroidRequirement() {
        val uwb = CapabilityPolicy.evaluate(
            features(sdkInt = 30, hasUwb = false),
            emptySet()
        ).assessment(TransportKind.UWB_ASSIST)

        assertTrue(uwb.detail.contains("Android 12"))
    }

    @Test
    fun nfcMessagingRequiresHostCardEmulation() {
        val unsupported = CapabilityPolicy.evaluate(
            features(sdkInt = 33, hasNfc = true, hasNfcHostCardEmulation = false, nfcEnabled = true),
            emptySet()
        ).assessment(TransportKind.NFC_PAIRING)
        assertFalse(unsupported.canStart)
        assertTrue(unsupported.detail.contains("host card emulation"))

        val supported = CapabilityPolicy.evaluate(
            features(sdkInt = 33, hasNfc = true, hasNfcHostCardEmulation = true, nfcEnabled = true),
            emptySet()
        ).assessment(TransportKind.NFC_PAIRING)
        assertTrue(supported.canStart)
    }

    @Test
    fun absentOptionalHardwareDoesNotRequestItsPermissions() {
        val snapshot = CapabilityPolicy.evaluate(features(sdkInt = 33), emptySet())

        assertFalse(Manifest.permission.CAMERA in snapshot.missingPermissions)
        assertFalse(Manifest.permission.RECORD_AUDIO in snapshot.missingPermissions)
        assertFalse(Manifest.permission.UWB_RANGING in snapshot.missingPermissions)
        assertFalse(Manifest.permission.BLUETOOTH_SCAN in snapshot.missingPermissions)
    }

    private fun features(
        sdkInt: Int,
        hasWifi: Boolean = false,
        wifiEnabled: Boolean = false,
        hasWifiDirect: Boolean = false,
        hasWifiAware: Boolean = false,
        wifiAwareAvailable: Boolean = false,
        hasBluetoothLe: Boolean = false,
        bluetoothEnabled: Boolean? = false,
        hasNfc: Boolean = false,
        hasNfcHostCardEmulation: Boolean = false,
        nfcEnabled: Boolean = false,
        hasUwb: Boolean = false,
        hasConsumerIrEmitter: Boolean = false,
        hasCamera: Boolean = false,
        hasMicrophone: Boolean = false,
        locationEnabled: Boolean = true
    ) = AndroidDeviceFeatures(
        sdkInt,
        hasWifi,
        wifiEnabled,
        hasWifiDirect,
        hasWifiAware,
        wifiAwareAvailable,
        hasBluetoothLe,
        bluetoothEnabled,
        hasNfc,
        hasNfcHostCardEmulation,
        nfcEnabled,
        hasUwb,
        hasConsumerIrEmitter,
        hasCamera,
        hasMicrophone,
        locationEnabled
    )
}
