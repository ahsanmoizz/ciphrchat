package org.ciphrchat.app.transport.bluetooth

import android.annotation.SuppressLint
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.ciphrchat.app.transport.DiscoveredPeer
import org.ciphrchat.app.transport.TransportKind
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
@SuppressLint("MissingPermission")
class BleScanner @Inject constructor(
    private val context: Context
) {
    private val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager?
    private val scanner get() = bluetoothManager?.adapter?.bluetoothLeScanner
    
    private var isScanning = false
    private var scanCallback: ScanCallback? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var stalePeerJob: Job? = null

    private val _discoveredPeers = MutableStateFlow<List<DiscoveredPeer>>(emptyList())
    val discoveredPeers: StateFlow<List<DiscoveredPeer>> = _discoveredPeers.asStateFlow()
    private val _lastError = MutableStateFlow<String?>(null)
    val lastError: StateFlow<String?> = _lastError.asStateFlow()
    
    // Map of hardware MAC address -> DiscoveredPeer
    private val discoveredMap = mutableMapOf<String, DiscoveredPeer>()
    private val mapLock = Any()

    fun start(): Boolean {
        if (isScanning) return true
        val activeScanner = runCatching { scanner }.getOrNull() ?: return false
        _lastError.value = null

        val filter = ScanFilter.Builder()
            .setServiceUuid(BleAdvertiser.OMNICHAT_SERVICE_UUID)
            .build()

        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()

        scanCallback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult?) {
                result?.let { processScanResult(it) }
            }

            override fun onBatchScanResults(results: MutableList<ScanResult>?) {
                results?.forEach { processScanResult(it) }
            }

            override fun onScanFailed(errorCode: Int) {
                isScanning = false
                _lastError.value = "Bluetooth scan failed (Android error $errorCode)"
            }
        }

        return runCatching {
            activeScanner.startScan(listOf(filter), settings, scanCallback)
            isScanning = true
            stalePeerJob?.cancel()
            stalePeerJob = scope.launch {
                while (isActive) {
                    delay(STALE_PEER_SWEEP_MS)
                    pruneStalePeers()
                }
            }
            true
        }.getOrElse {
            scanCallback = null
            isScanning = false
            false
        }
    }

    private fun processScanResult(result: ScanResult) {
        val record = result.scanRecord ?: return
        val serviceData = record.serviceData[BleAdvertiser.OMNICHAT_SERVICE_UUID] ?: return
        
        val discoveryToken = String(serviceData, Charsets.US_ASCII)
        val macAddress = result.device.address

        val peer = DiscoveredPeer(
            id = discoveryToken,
            displayName = "BLE Peer",
            transportKind = TransportKind.BLUETOOTH_DIRECT,
            reachabilityScore = result.rssi,
            lastSeenEpochMs = System.currentTimeMillis()
        )

        synchronized(mapLock) {
            discoveredMap[macAddress] = peer
            _discoveredPeers.value = discoveredMap.values.toList()
        }
    }

    fun stop() {
        val activeScanner = runCatching { scanner }.getOrNull()
        scanCallback?.let { callback -> runCatching { activeScanner?.stopScan(callback) } }
        isScanning = false
        stalePeerJob?.cancel()
        stalePeerJob = null
        scanCallback = null
        _lastError.value = null
        synchronized(mapLock) {
            discoveredMap.clear()
            _discoveredPeers.value = emptyList()
        }
    }

    fun deviceAddressFor(peerId: String): String? {
        pruneStalePeers()
        return synchronized(mapLock) {
            discoveredMap.entries.firstOrNull { it.value.id == peerId }?.key
        }
    }

    fun discoveredDeviceAddresses(): List<String> {
        pruneStalePeers()
        return synchronized(mapLock) { discoveredMap.keys.toList() }
    }

    private fun pruneStalePeers(nowEpochMs: Long = System.currentTimeMillis()) {
        synchronized(mapLock) {
            val changed = discoveredMap.entries.removeAll {
                nowEpochMs - it.value.lastSeenEpochMs > PEER_STALE_AFTER_MS
            }
            if (changed) _discoveredPeers.value = discoveredMap.values.toList()
        }
    }

    private companion object {
        const val PEER_STALE_AFTER_MS = 20_000L
        const val STALE_PEER_SWEEP_MS = 5_000L
    }
}
