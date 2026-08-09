package org.ciphrchat.app.messaging

data class TransportPresentation(val label: String, val operatingRange: String)

/** User-facing route descriptions; ranges are qualitative unless hardware provides a measurement. */
object TransportPresentationPolicy {
    fun forName(name: String?): TransportPresentation? = when (name) {
        "INTERNET_DIRECT", "INTERNET_RELAY" -> TransportPresentation("Internet relay", "global")
        "WIFI_LAN" -> TransportPresentation("Wi-Fi LAN", "same network")
        "WIFI_AWARE" -> TransportPresentation("Wi-Fi Aware", "nearby")
        "WIFI_DIRECT" -> TransportPresentation("Wi-Fi Direct", "nearby")
        "BLUETOOTH_DIRECT" -> TransportPresentation("Bluetooth", "nearby")
        "BLUETOOTH_MESH" -> TransportPresentation("Bluetooth mesh", "nearby hops")
        "ULTRASOUND" -> TransportPresentation("Nearby audio", "same room")
        "INFRARED" -> TransportPresentation("Infrared", "line of sight")
        "NFC_PAIRING" -> TransportPresentation("NFC", "tap range")
        "UWB_ASSIST" -> TransportPresentation("UWB verified", "BLE carrier")
        "EXTERNAL" -> TransportPresentation("External route", "provider defined")
        else -> null
    }
}
