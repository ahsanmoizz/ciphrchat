package org.ciphrchat.app.transport.bluetooth

/** BLE RSSI is a live signal estimate, not a trustworthy physical distance measurement. */
object BluetoothSignalPolicy {
    fun label(rssiDbm: Int): String = when {
        rssiDbm >= -55 -> "very strong"
        rssiDbm >= -67 -> "strong"
        rssiDbm >= -75 -> "good"
        rssiDbm >= -85 -> "weak"
        else -> "very weak"
    }

    fun detail(rssiDbm: Int): String = "$rssiDbm dBm (${label(rssiDbm)} signal; approximate)"
}
