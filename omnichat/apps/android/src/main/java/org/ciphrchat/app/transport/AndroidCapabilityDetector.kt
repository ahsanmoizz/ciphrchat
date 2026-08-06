package org.ciphrchat.app.transport

import android.content.Context
import android.content.pm.PackageManager
import android.hardware.ConsumerIrManager
import android.net.wifi.aware.WifiAwareManager
import android.os.Build
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AndroidCapabilityDetector @Inject constructor(
    private val context: Context
) {
    private val pm: PackageManager get() = context.packageManager

    fun hasBluetoothLe(): Boolean = pm.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)

    fun hasWifiDirect(): Boolean = pm.hasSystemFeature(PackageManager.FEATURE_WIFI_DIRECT)

    fun hasWifiAware(): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            pm.hasSystemFeature(PackageManager.FEATURE_WIFI_AWARE) &&
            context.getSystemService(WifiAwareManager::class.java) != null

    fun hasNfc(): Boolean = pm.hasSystemFeature(PackageManager.FEATURE_NFC)

    fun hasUwb(): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            pm.hasSystemFeature("android.hardware.uwb")

    fun hasConsumerIrEmitter(): Boolean {
        val manager = context.getSystemService(ConsumerIrManager::class.java)
        return manager?.hasIrEmitter() == true
    }

    fun hasMicrophone(): Boolean = pm.hasSystemFeature(PackageManager.FEATURE_MICROPHONE)
}
