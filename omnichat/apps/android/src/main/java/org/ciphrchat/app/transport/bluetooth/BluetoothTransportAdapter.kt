package org.ciphrchat.app.transport.bluetooth

import android.annotation.SuppressLint
import android.Manifest
import android.bluetooth.*
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.ciphrchat.app.transport.*
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine
import kotlinx.coroutines.delay
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import org.ciphrchat.app.transport.TransportWireCodec

@Singleton
@SuppressLint("MissingPermission")
class BluetoothTransportAdapter @Inject constructor(
    private val context: Context,
    private val bleAdvertiser: BleAdvertiser,
    private val bleScanner: BleScanner,
    private val gattServerManager: GattServerManager
) : TransportAdapter {
    override val kind: TransportKind = TransportKind.BLUETOOTH_DIRECT
    override val capabilities: Set<TransportCapability> = setOf(
        TransportCapability.DISCOVERY,
        TransportCapability.SMALL_TEXT,
        TransportCapability.OFFLINE
    )

    private val _state = MutableStateFlow(
        TransportState(
            kind,
            if (hasBluetoothPermissions()) TransportAvailability.STARTING else TransportAvailability.PERMISSION_REQUIRED,
            if (hasBluetoothPermissions()) "Bluetooth ready to start" else "Bluetooth permissions are required"
        )
    )
    override val state: StateFlow<TransportState> = _state.asStateFlow()

    private val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager?
    private val adapter = bluetoothManager?.adapter

    override suspend fun start(): Result<Unit> {
        if (adapter == null) {
            val error = IllegalStateException("Bluetooth is not available on this device")
            _state.value = TransportState(kind, TransportAvailability.UNAVAILABLE, error.message!!)
            return Result.failure(error)
        }
        if (!hasBluetoothPermissions()) {
            val error = SecurityException("Grant Bluetooth nearby-device permissions")
            _state.value = TransportState(kind, TransportAvailability.PERMISSION_REQUIRED, error.message!!)
            return Result.failure(error)
        }
        if (!adapter.isEnabled) {
            val error = IllegalStateException("Bluetooth is disabled")
            _state.value = TransportState(kind, TransportAvailability.UNAVAILABLE, error.message!!)
            return Result.failure(error)
        }
        val adStarted = bleAdvertiser.start()
        val scanStarted = bleScanner.start()
        gattServerManager.start()
        
        if (adStarted || scanStarted) {
            _state.value = TransportState(kind, TransportAvailability.AVAILABLE, "Bluetooth Active")
            return Result.success(Unit)
        }
        val error = Exception("Bluetooth start failed")
        _state.value = TransportState(kind, TransportAvailability.ERROR, error.message!!)
        return Result.failure(error)
    }

    override suspend fun stop(): Result<Unit> {
        bleAdvertiser.stop()
        bleScanner.stop()
        gattServerManager.stop()
        _state.value = TransportState(kind, TransportAvailability.DISABLED_BY_USER, "Stopped")
        return Result.success(Unit)
    }

    override suspend fun discoverPeers(): Result<List<DiscoveredPeer>> {
        return Result.success(bleScanner.discoveredPeers.value)
    }

    override suspend fun canReach(recipientId: String): Reachability {
        return if (bleScanner.deviceAddressFor(recipientId) != null) Reachability.Reachable
        else Reachability.Unreachable("Peer not discovered over Bluetooth")
    }

    override suspend fun send(envelope: OutboundEnvelope): SendResult = suspendCoroutine { cont ->
        val deviceMac = bleScanner.deviceAddressFor(envelope.recipientId)
            ?: return@suspendCoroutine cont.resume(SendResult.Rejected("Peer not discovered over Bluetooth"))
        val device = adapter?.getRemoteDevice(deviceMac)
        
        if (device == null) {
            cont.resume(SendResult.Failure(Exception("Invalid MAC address")))
            return@suspendCoroutine
        }

        var gatt: BluetoothGatt? = null
        val payload = ByteArrayOutputStream().use { buffer ->
            DataOutputStream(buffer).use { out -> TransportWireCodec.write(out, envelope) }
            buffer.toByteArray()
        }
        val mtu = 512 // Request highest MTU, assume 512 for now
        var offset = 0
        
        val gattCallback = object : BluetoothGattCallback() {
            override fun onConnectionStateChange(g: BluetoothGatt, status: Int, newState: Int) {
                if (newState == BluetoothProfile.STATE_CONNECTED) {
                    g.requestMtu(512)
                } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                    g.close()
                    if (offset < payload.size) {
                        cont.resume(SendResult.Failure(Exception("Disconnected before send complete")))
                    }
                }
            }

            override fun onMtuChanged(g: BluetoothGatt, mtu: Int, status: Int) {
                if (status == BluetoothGatt.GATT_SUCCESS) {
                    g.discoverServices()
                }
            }

            override fun onServicesDiscovered(g: BluetoothGatt, status: Int) {
                if (status == BluetoothGatt.GATT_SUCCESS) {
                    writeNextChunk(g)
                }
            }

            override fun onCharacteristicWrite(g: BluetoothGatt, char: BluetoothGattCharacteristic, status: Int) {
                if (status == BluetoothGatt.GATT_SUCCESS) {
                    if (offset >= payload.size) {
                        cont.resume(SendResult.Accepted(kind, "ble-gatt-frame"))
                        g.disconnect()
                    } else {
                        writeNextChunk(g)
                    }
                } else {
                    cont.resume(SendResult.Failure(Exception("Write failed with status $status")))
                    g.disconnect()
                }
            }
            
            private fun writeNextChunk(g: BluetoothGatt) {
                val service = g.getService(GattServerManager.GATT_SERVICE_UUID)
                val characteristic = service?.getCharacteristic(GattServerManager.GATT_CHARACTERISTIC_UUID)
                
                if (characteristic == null) {
                    cont.resume(SendResult.Failure(Exception("GATT Service/Characteristic not found")))
                    g.disconnect()
                    return
                }

                // Chunk size: MTU - 3 (header overhead) - 1/5 (our protocol overhead)
                val chunkSize = minOf(mtu - 10, 500)
                val remaining = payload.size - offset
                val take = minOf(chunkSize, remaining)
                
                val chunk = if (offset == 0) {
                    // Start frame
                    val buf = ByteArray(5 + take)
                    buf[0] = 1
                    buf[1] = (payload.size shr 24).toByte()
                    buf[2] = (payload.size shr 16).toByte()
                    buf[3] = (payload.size shr 8).toByte()
                    buf[4] = payload.size.toByte()
                    System.arraycopy(payload, 0, buf, 5, take)
                    buf
                } else {
                    // Continuation frame
                    val buf = ByteArray(1 + take)
                    buf[0] = 2
                    System.arraycopy(payload, offset, buf, 1, take)
                    buf
                }
                
                offset += take
                
                characteristic.value = chunk
                characteristic.writeType = BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
                g.writeCharacteristic(characteristic)
            }
        }

        gatt = device.connectGatt(context, false, gattCallback)
    }

    private fun hasBluetoothPermissions(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true
        return listOf(
            Manifest.permission.BLUETOOTH_SCAN,
            Manifest.permission.BLUETOOTH_CONNECT,
            Manifest.permission.BLUETOOTH_ADVERTISE
        ).all { ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED }
    }
}
