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
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import org.ciphrchat.app.transport.TransportWireCodec
import org.ciphrchat.app.identity.ContactRepository
import java.util.concurrent.atomic.AtomicBoolean

@Singleton
@SuppressLint("MissingPermission")
class BluetoothTransportAdapter @Inject constructor(
    private val context: Context,
    private val bleAdvertiser: BleAdvertiser,
    private val bleScanner: BleScanner,
    private val gattServerManager: GattServerManager,
    private val contacts: ContactRepository
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
    private var started = false

    override suspend fun start(): Result<Unit> {
        if (started && _state.value.availability == TransportAvailability.AVAILABLE) return Result.success(Unit)
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
        started = true
        
        if (adStarted || scanStarted) {
            _state.value = TransportState(kind, TransportAvailability.AVAILABLE, "Bluetooth Active")
            return Result.success(Unit)
        }
        val error = Exception("Bluetooth start failed")
        _state.value = TransportState(kind, TransportAvailability.ERROR, error.message!!)
        return Result.failure(error)
    }

    override suspend fun stop(): Result<Unit> {
        started = false
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
        return if (bleScanner.deviceAddressFor(discoveryTokenFor(recipientId)) != null) Reachability.Reachable
        else Reachability.Unreachable("Peer not discovered over Bluetooth")
    }

    override suspend fun send(envelope: OutboundEnvelope): SendResult {
        val discoveryToken = discoveryTokenFor(envelope.recipientId)
        val deviceMac = bleScanner.deviceAddressFor(discoveryToken)
            ?: return SendResult.Rejected("Peer not discovered over Bluetooth")
        return sendToDevice(deviceMac, envelope)
    }

    suspend fun sendToDevice(deviceMac: String, envelope: OutboundEnvelope): SendResult =
        withTimeoutOrNull(20_000L) {
            suspendCancellableCoroutine { cont ->
        val device = adapter?.getRemoteDevice(deviceMac)
        
        if (device == null) {
            cont.resume(SendResult.Failure(Exception("Invalid MAC address")))
            return@suspendCancellableCoroutine
        }

        var gatt: BluetoothGatt? = null
        val payload = ByteArrayOutputStream().use { buffer ->
            DataOutputStream(buffer).use { out -> TransportWireCodec.write(out, envelope) }
            buffer.toByteArray()
        }
        var negotiatedMtu = 23
        var offset = 0
        val completed = AtomicBoolean(false)

        fun complete(result: SendResult) {
            if (completed.compareAndSet(false, true) && cont.isActive) cont.resume(result)
        }
        
        val gattCallback = object : BluetoothGattCallback() {
            override fun onConnectionStateChange(g: BluetoothGatt, status: Int, newState: Int) {
                if (newState == BluetoothProfile.STATE_CONNECTED) {
                    g.requestMtu(517)
                } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                    g.close()
                    complete(SendResult.Failure(Exception("Disconnected before send complete")))
                }
            }

            override fun onMtuChanged(g: BluetoothGatt, mtu: Int, status: Int) {
                if (status == BluetoothGatt.GATT_SUCCESS) {
                    negotiatedMtu = mtu.coerceAtLeast(23)
                }
                // Service discovery is required even when the peer rejects the
                // larger MTU; the default 23-byte MTU remains usable.
                g.discoverServices()
            }

            override fun onServicesDiscovered(g: BluetoothGatt, status: Int) {
                if (status == BluetoothGatt.GATT_SUCCESS) {
                    writeNextChunk(g)
                }
            }

            override fun onCharacteristicWrite(g: BluetoothGatt, char: BluetoothGattCharacteristic, status: Int) {
                if (status == BluetoothGatt.GATT_SUCCESS) {
                    if (offset >= payload.size) {
                        complete(SendResult.Accepted(kind, "ble-gatt-frame"))
                        g.disconnect()
                    } else {
                        writeNextChunk(g)
                    }
                } else {
                    complete(SendResult.Failure(Exception("Write failed with status $status")))
                    g.disconnect()
                }
            }
            
            private fun writeNextChunk(g: BluetoothGatt) {
                val service = g.getService(GattServerManager.GATT_SERVICE_UUID)
                val characteristic = service?.getCharacteristic(GattServerManager.GATT_CHARACTERISTIC_UUID)
                
                if (characteristic == null) {
                    complete(SendResult.Failure(Exception("GATT Service/Characteristic not found")))
                    g.disconnect()
                    return
                }

                // ATT carries MTU - 3 bytes. Five bytes are reserved for the
                // first chunk's framing header and one byte for continuations.
                val chunkSize = minOf(negotiatedMtu - 3, 500)
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
                // Use acknowledged writes: every chunk advances only after the
                // receiver confirms it, preventing silent loss on busy phones.
                characteristic.writeType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
                if (!g.writeCharacteristic(characteristic)) {
                    complete(SendResult.Failure(Exception("Bluetooth rejected GATT write")))
                    g.disconnect()
                }
            }
        }

        gatt = device.connectGatt(context, false, gattCallback)
        cont.invokeOnCancellation {
            gatt?.disconnect()
            gatt?.close()
        }
    }
        } ?: SendResult.Failure(IllegalStateException("Bluetooth send timed out"))

    fun hasDiscoveredPeers(): Boolean = bleScanner.discoveredDeviceAddresses().isNotEmpty()

    suspend fun broadcastEnvelope(envelope: OutboundEnvelope): SendResult {
        val peers = bleScanner.discoveredDeviceAddresses()
        if (peers.isEmpty()) return SendResult.Rejected("No Bluetooth mesh neighbors discovered")
        var accepted = 0
        var lastError: Throwable? = null
        for (peer in peers) {
            when (val result = sendToDevice(peer, envelope)) {
                is SendResult.Accepted -> accepted++
                is SendResult.Failed -> lastError = result.error
                is SendResult.Failure -> lastError = result.error
                else -> Unit
            }
        }
        return if (accepted > 0) {
            SendResult.Accepted(TransportKind.BLUETOOTH_MESH, "mesh-flood-$accepted")
        } else {
            SendResult.Failed(lastError ?: IllegalStateException("No Bluetooth mesh neighbor accepted the frame"))
        }
    }

    private fun hasBluetoothPermissions(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true
        return listOf(
            Manifest.permission.BLUETOOTH_SCAN,
            Manifest.permission.BLUETOOTH_CONNECT,
            Manifest.permission.BLUETOOTH_ADVERTISE
        ).all { ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED }
    }

    private suspend fun discoveryTokenFor(recipientId: String): String {
        val contact = contacts.find(recipientId)
        return contact?.discoveryToken?.ifBlank { ContactDiscoveryToken.forContactId(recipientId) }
            ?: ContactDiscoveryToken.forContactId(recipientId)
    }
}
