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
    private val adapter = bluetoothManager?.adapter
    private val scanner = adapter?.bluetoothLeScanner
    
    private var isScanning = false
    private var scanCallback: ScanCallback? = null

    private val _discoveredPeers = MutableStateFlow<List<DiscoveredPeer>>(emptyList())
    val discoveredPeers: StateFlow<List<DiscoveredPeer>> = _discoveredPeers.asStateFlow()
    
    // Map of hardware MAC address -> DiscoveredPeer
    private val discoveredMap = mutableMapOf<String, DiscoveredPeer>()

    fun start(): Boolean {
        if (scanner == null || isScanning) return false

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
                // handle failure
            }
        }

        scanner.startScan(listOf(filter), settings, scanCallback)
        isScanning = true
        return true
    }

    private fun processScanResult(result: ScanResult) {
        val record = result.scanRecord ?: return
        val serviceData = record.serviceData[BleAdvertiser.OMNICHAT_SERVICE_UUID] ?: return
        
        val truncatedId = String(serviceData, Charsets.UTF_8)
        val macAddress = result.device.address

        val peer = DiscoveredPeer(
            id = truncatedId, // Real app would look up full ID or resolve identity, here we use truncated ID as proxy
            displayName = "BLE Peer",
            transportKind = TransportKind.BLUETOOTH_DIRECT,
            reachabilityScore = 70 + (result.rssi + 100), // Simple heuristic for RSSI mapping
            lastSeenEpochMs = System.currentTimeMillis()
        )

        discoveredMap[macAddress] = peer
        _discoveredPeers.value = discoveredMap.values.toList()
    }

    fun stop() {
        if (!isScanning || scanner == null) return
        scanCallback?.let {
            scanner.stopScan(it)
        }
        isScanning = false
        scanCallback = null
        discoveredMap.clear()
        _discoveredPeers.value = emptyList()
    }

    fun deviceAddressFor(peerId: String): String? =
        discoveredMap.entries.firstOrNull { it.value.id == peerId }?.key
}
