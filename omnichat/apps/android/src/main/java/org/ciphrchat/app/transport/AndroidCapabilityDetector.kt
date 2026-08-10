package org.ciphrchat.app.transport

import android.Manifest
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.ConsumerIrManager
import android.location.LocationManager
import android.net.wifi.WifiManager
import android.net.wifi.aware.WifiAwareManager
import android.nfc.NfcManager
import android.os.Build
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

data class AndroidDeviceFeatures(
    val sdkInt: Int,
    val hasWifi: Boolean,
    val wifiEnabled: Boolean,
    val hasWifiDirect: Boolean,
    val hasWifiAware: Boolean,
    val wifiAwareAvailable: Boolean,
    val hasBluetoothLe: Boolean,
    val bluetoothEnabled: Boolean?,
    val hasNfc: Boolean,
    val hasNfcHostCardEmulation: Boolean,
    val nfcEnabled: Boolean,
    val hasUwb: Boolean,
    val hasConsumerIrEmitter: Boolean,
    val hasCamera: Boolean,
    val hasMicrophone: Boolean,
    val locationEnabled: Boolean
)

data class CapabilityAssessment(
    val kind: TransportKind,
    val availability: TransportAvailability,
    val detail: String,
    val missingPermissions: List<String> = emptyList()
) {
    val canStart: Boolean
        get() = availability == TransportAvailability.STARTING ||
            availability == TransportAvailability.AVAILABLE
}

data class AndroidCapabilitySnapshot(
    val assessments: Map<TransportKind, CapabilityAssessment>
) {
    fun assessment(kind: TransportKind): CapabilityAssessment =
        assessments[kind] ?: CapabilityAssessment(
            kind,
            TransportAvailability.UNAVAILABLE,
            "This connection method is not supported"
        )

    val missingPermissions: List<String>
        get() = assessments.values
            .filter { it.availability == TransportAvailability.PERMISSION_REQUIRED }
            .flatMap { it.missingPermissions }
            .distinct()
}

/** Pure Android-version and hardware policy, kept separate so API scenarios can be unit tested. */
object CapabilityPolicy {
    fun evaluate(
        features: AndroidDeviceFeatures,
        grantedPermissions: Set<String>
    ): AndroidCapabilitySnapshot {
        fun missing(required: List<String>) = required.filterNot(grantedPermissions::contains)
        fun permissionOrReady(
            kind: TransportKind,
            required: List<String>,
            readyDetail: String,
            afterPermissions: () -> CapabilityAssessment
        ): CapabilityAssessment {
            val missing = missing(required)
            return if (missing.isNotEmpty()) {
                CapabilityAssessment(
                    kind,
                    TransportAvailability.PERMISSION_REQUIRED,
                    "Permission required to use ${displayName(kind)}",
                    missing
                )
            } else afterPermissions().let {
                if (it.detail.isBlank()) it.copy(detail = readyDetail) else it
            }
        }

        val bluetoothPermissions = if (features.sdkInt >= Build.VERSION_CODES.S) {
            listOf(
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_CONNECT,
                Manifest.permission.BLUETOOTH_ADVERTISE
            )
        } else {
            listOf(Manifest.permission.ACCESS_FINE_LOCATION)
        }
        val wifiPermissions = if (features.sdkInt >= Build.VERSION_CODES.TIRAMISU) {
            listOf(Manifest.permission.NEARBY_WIFI_DEVICES)
        } else {
            listOf(Manifest.permission.ACCESS_FINE_LOCATION)
        }

        fun bluetooth(kind: TransportKind): CapabilityAssessment {
            if (!features.hasBluetoothLe) {
                return CapabilityAssessment(kind, TransportAvailability.UNAVAILABLE, "Bluetooth LE hardware not detected")
            }
            return permissionOrReady(kind, bluetoothPermissions, "Bluetooth detected") {
                when {
                    features.sdkInt < Build.VERSION_CODES.S && !features.locationEnabled ->
                        CapabilityAssessment(kind, TransportAvailability.UNAVAILABLE, "Turn on Location services for Bluetooth discovery on this Android version")
                    features.bluetoothEnabled == false ->
                        CapabilityAssessment(kind, TransportAvailability.UNAVAILABLE, "Bluetooth is turned off")
                    features.bluetoothEnabled == null ->
                        CapabilityAssessment(kind, TransportAvailability.STARTING, "Bluetooth radio state is refreshing")
                    else -> CapabilityAssessment(kind, TransportAvailability.STARTING, "Bluetooth detected; ready to start")
                }
            }
        }

        fun nearbyWifi(kind: TransportKind, aware: Boolean): CapabilityAssessment {
            if (!features.hasWifi) {
                return CapabilityAssessment(kind, TransportAvailability.UNAVAILABLE, "Wi-Fi hardware not detected")
            }
            if (aware && !features.hasWifiAware) {
                return CapabilityAssessment(kind, TransportAvailability.UNAVAILABLE, "Wi-Fi Aware hardware not detected")
            }
            if (!aware && !features.hasWifiDirect) {
                return CapabilityAssessment(kind, TransportAvailability.UNAVAILABLE, "Wi-Fi Direct hardware not detected")
            }
            return permissionOrReady(kind, wifiPermissions, "Nearby Wi-Fi detected") {
                when {
                    !features.wifiEnabled -> CapabilityAssessment(kind, TransportAvailability.UNAVAILABLE, "Wi-Fi is turned off")
                    features.sdkInt < Build.VERSION_CODES.TIRAMISU && !features.locationEnabled ->
                        CapabilityAssessment(kind, TransportAvailability.UNAVAILABLE, "Turn on Location services for nearby Wi-Fi discovery on this Android version")
                    aware && !features.wifiAwareAvailable ->
                        CapabilityAssessment(kind, TransportAvailability.UNAVAILABLE, "Wi-Fi Aware is temporarily unavailable")
                    else -> CapabilityAssessment(kind, TransportAvailability.STARTING, "Nearby Wi-Fi detected; ready to start")
                }
            }
        }

        val assessments = mutableMapOf<TransportKind, CapabilityAssessment>()
        assessments[TransportKind.INTERNET_DIRECT] = CapabilityAssessment(
            TransportKind.INTERNET_DIRECT,
            TransportAvailability.STARTING,
            "Internet transport ready to start"
        )
        assessments[TransportKind.INTERNET_RELAY] = CapabilityAssessment(
            TransportKind.INTERNET_RELAY,
            TransportAvailability.STARTING,
            "Internet relay ready to start"
        )
        assessments[TransportKind.WIFI_LAN] = when {
            !features.hasWifi -> CapabilityAssessment(TransportKind.WIFI_LAN, TransportAvailability.UNAVAILABLE, "Wi-Fi hardware not detected")
            !features.wifiEnabled -> CapabilityAssessment(TransportKind.WIFI_LAN, TransportAvailability.UNAVAILABLE, "Wi-Fi is turned off")
            else -> CapabilityAssessment(TransportKind.WIFI_LAN, TransportAvailability.STARTING, "Wi-Fi LAN ready to start")
        }
        assessments[TransportKind.WIFI_DIRECT] = nearbyWifi(TransportKind.WIFI_DIRECT, aware = false)
        assessments[TransportKind.WIFI_AWARE] = nearbyWifi(TransportKind.WIFI_AWARE, aware = true)
        assessments[TransportKind.BLUETOOTH_DIRECT] = bluetooth(TransportKind.BLUETOOTH_DIRECT)
        assessments[TransportKind.BLUETOOTH_MESH] = bluetooth(TransportKind.BLUETOOTH_MESH)

        assessments[TransportKind.ULTRASOUND] = if (!features.hasMicrophone) {
            CapabilityAssessment(TransportKind.ULTRASOUND, TransportAvailability.UNAVAILABLE, "Microphone hardware not detected")
        } else {
            permissionOrReady(
                TransportKind.ULTRASOUND,
                listOf(Manifest.permission.RECORD_AUDIO),
                "Microphone detected"
            ) { CapabilityAssessment(TransportKind.ULTRASOUND, TransportAvailability.STARTING, "Microphone detected; nearby audio ready to start") }
        }

        assessments[TransportKind.NFC_PAIRING] = when {
            !features.hasNfc -> CapabilityAssessment(TransportKind.NFC_PAIRING, TransportAvailability.UNAVAILABLE, "NFC hardware not detected")
            !features.hasNfcHostCardEmulation -> CapabilityAssessment(TransportKind.NFC_PAIRING, TransportAvailability.UNAVAILABLE, "NFC messaging requires host card emulation, which this phone does not report")
            !features.nfcEnabled -> CapabilityAssessment(TransportKind.NFC_PAIRING, TransportAvailability.UNAVAILABLE, "NFC is turned off")
            else -> CapabilityAssessment(TransportKind.NFC_PAIRING, TransportAvailability.STARTING, "NFC detected; ready for a tap session")
        }

        assessments[TransportKind.INFRARED] = when {
            !features.hasConsumerIrEmitter -> CapabilityAssessment(TransportKind.INFRARED, TransportAvailability.UNAVAILABLE, "IR emitter hardware not detected")
            !features.hasCamera -> CapabilityAssessment(TransportKind.INFRARED, TransportAvailability.UNAVAILABLE, "Camera hardware not detected for optical receiving")
            else -> CapabilityAssessment(
                TransportKind.INFRARED,
                TransportAvailability.UNAVAILABLE,
                "IR remote-control hardware detected, but Android provides no verified bidirectional IR message link"
            )
        }

        assessments[TransportKind.UWB_ASSIST] = when {
            features.sdkInt < Build.VERSION_CODES.S -> CapabilityAssessment(
                TransportKind.UWB_ASSIST,
                TransportAvailability.UNAVAILABLE,
                "UWB requires Android 12 or newer"
            )
            !features.hasUwb -> CapabilityAssessment(
                TransportKind.UWB_ASSIST,
                TransportAvailability.UNAVAILABLE,
                "No UWB radio reported by this phone; Android version alone does not add UWB"
            )
            !features.hasBluetoothLe -> CapabilityAssessment(
                TransportKind.UWB_ASSIST,
                TransportAvailability.UNAVAILABLE,
                "UWB messaging also requires Bluetooth LE"
            )
            else -> permissionOrReady(
                TransportKind.UWB_ASSIST,
                listOf(Manifest.permission.UWB_RANGING) + bluetoothPermissions,
                "UWB hardware detected"
            ) { CapabilityAssessment(TransportKind.UWB_ASSIST, TransportAvailability.STARTING, "UWB hardware detected; ready to start") }
        }

        assessments[TransportKind.EXTERNAL] = CapabilityAssessment(
            TransportKind.EXTERNAL,
            TransportAvailability.UNAVAILABLE,
            "No external transport is connected"
        )
        return AndroidCapabilitySnapshot(assessments)
    }

    private fun displayName(kind: TransportKind): String = when (kind) {
        TransportKind.WIFI_AWARE -> "Wi-Fi Aware"
        TransportKind.WIFI_DIRECT -> "Wi-Fi Direct"
        TransportKind.BLUETOOTH_DIRECT -> "Bluetooth"
        TransportKind.BLUETOOTH_MESH -> "Bluetooth mesh"
        TransportKind.ULTRASOUND -> "nearby audio"
        TransportKind.INFRARED -> "infrared"
        TransportKind.UWB_ASSIST -> "UWB"
        else -> kind.name.lowercase().replace('_', ' ')
    }
}

@Singleton
class AndroidCapabilityDetector @Inject constructor(
    private val context: Context
) {
    private val pm: PackageManager get() = context.packageManager
    private val relevantPermissions = listOf(
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.BLUETOOTH_SCAN,
        Manifest.permission.BLUETOOTH_CONNECT,
        Manifest.permission.BLUETOOTH_ADVERTISE,
        Manifest.permission.NEARBY_WIFI_DEVICES,
        Manifest.permission.RECORD_AUDIO,
        Manifest.permission.CAMERA,
        Manifest.permission.UWB_RANGING
    )

    private val _snapshot = MutableStateFlow(scan())
    val snapshot: StateFlow<AndroidCapabilitySnapshot> = _snapshot.asStateFlow()

    fun refresh(): AndroidCapabilitySnapshot = scan().also { _snapshot.value = it }

    fun canStart(kind: TransportKind): Boolean = snapshot.value.assessment(kind).canStart

    fun missingPermissions(): List<String> = snapshot.value.missingPermissions

    private fun scan(): AndroidCapabilitySnapshot {
        val granted = relevantPermissions.filterTo(mutableSetOf()) {
            Build.VERSION.SDK_INT < permissionIntroducedAt(it) ||
                ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
        }
        val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager?
        val bluetoothEnabled = if (
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            Manifest.permission.BLUETOOTH_CONNECT !in granted
        ) null else runCatching { bluetoothManager?.adapter?.isEnabled }.getOrNull()
        val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager?
        val awareManager = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.getSystemService(Context.WIFI_AWARE_SERVICE) as WifiAwareManager?
        } else null
        val nfcAdapter = (context.getSystemService(Context.NFC_SERVICE) as NfcManager?)?.defaultAdapter
        val irManager = context.getSystemService(Context.CONSUMER_IR_SERVICE) as ConsumerIrManager?
        val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager?
        val features = AndroidDeviceFeatures(
            sdkInt = Build.VERSION.SDK_INT,
            hasWifi = pm.hasSystemFeature(PackageManager.FEATURE_WIFI),
            wifiEnabled = runCatching { wifiManager?.isWifiEnabled == true }.getOrDefault(false),
            hasWifiDirect = pm.hasSystemFeature(PackageManager.FEATURE_WIFI_DIRECT),
            hasWifiAware = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && pm.hasSystemFeature(PackageManager.FEATURE_WIFI_AWARE),
            wifiAwareAvailable = runCatching { awareManager?.isAvailable == true }.getOrDefault(false),
            hasBluetoothLe = pm.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE),
            bluetoothEnabled = bluetoothEnabled,
            hasNfc = pm.hasSystemFeature(PackageManager.FEATURE_NFC),
            hasNfcHostCardEmulation = pm.hasSystemFeature(PackageManager.FEATURE_NFC_HOST_CARD_EMULATION),
            nfcEnabled = runCatching { nfcAdapter?.isEnabled == true }.getOrDefault(false),
            hasUwb = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && pm.hasSystemFeature(PackageManager.FEATURE_UWB),
            hasConsumerIrEmitter = runCatching { irManager?.hasIrEmitter() == true }.getOrDefault(false),
            hasCamera = pm.hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY),
            hasMicrophone = pm.hasSystemFeature(PackageManager.FEATURE_MICROPHONE),
            locationEnabled = runCatching {
                when {
                    locationManager == null -> false
                    Build.VERSION.SDK_INT >= Build.VERSION_CODES.P -> locationManager.isLocationEnabled
                    else -> locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) ||
                        locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
                }
            }.getOrDefault(false)
        )
        return CapabilityPolicy.evaluate(features, granted)
    }

    private fun permissionIntroducedAt(permission: String): Int = when (permission) {
        Manifest.permission.BLUETOOTH_SCAN,
        Manifest.permission.BLUETOOTH_CONNECT,
        Manifest.permission.BLUETOOTH_ADVERTISE,
        Manifest.permission.UWB_RANGING -> Build.VERSION_CODES.S
        Manifest.permission.NEARBY_WIFI_DEVICES -> Build.VERSION_CODES.TIRAMISU
        else -> Build.VERSION_CODES.BASE
    }
}
