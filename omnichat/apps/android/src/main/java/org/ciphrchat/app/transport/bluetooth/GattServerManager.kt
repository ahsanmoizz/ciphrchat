package org.ciphrchat.app.transport.bluetooth

import android.annotation.SuppressLint
import android.bluetooth.*
import android.content.Context
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import java.io.ByteArrayOutputStream
import java.io.ByteArrayInputStream
import java.io.DataInputStream
import org.ciphrchat.app.transport.TransportInboundBus
import org.ciphrchat.app.transport.TransportKind
import org.ciphrchat.app.transport.TransportWireCodec

@Singleton
@SuppressLint("MissingPermission")
class GattServerManager @Inject constructor(
    private val context: Context,
    private val inboundBus: TransportInboundBus
) {
    companion object {
        val GATT_SERVICE_UUID: UUID = UUID.fromString("0000FF02-0000-1000-8000-00805F9B34FB")
        val GATT_CHARACTERISTIC_UUID: UUID = UUID.fromString("0000FF03-0000-1000-8000-00805F9B34FB")
        val CONTROL_CHARACTERISTIC_UUID: UUID = UUID.fromString("0000FF04-0000-1000-8000-00805F9B34FB")
        const val MAX_FRAME_BYTES = 1 * 1024 * 1024
    }

    private val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager?
    private var gattServer: BluetoothGattServer? = null
    private var started = false

    private val _incomingData = MutableSharedFlow<ByteArray>(extraBufferCapacity = 10)
    val incomingData: SharedFlow<ByteArray> = _incomingData.asSharedFlow()

    data class IncomingControl(val deviceAddress: String, val payload: ByteArray)
    private val _incomingControl = MutableSharedFlow<IncomingControl>(extraBufferCapacity = 16)
    val incomingControl: SharedFlow<IncomingControl> = _incomingControl.asSharedFlow()

    // Map device address -> ByteArrayOutputStream (assembler for chunked data)
    private val assemblyBuffers = mutableMapOf<String, ByteArrayOutputStream>()
    private val expectedLengths = mutableMapOf<String, Int>()
    private val controlAssemblyBuffers = mutableMapOf<String, ByteArrayOutputStream>()
    private val controlExpectedLengths = mutableMapOf<String, Int>()

    fun start(): Boolean {
        if (started && gattServer != null) return true
        if (bluetoothManager == null) return false

        gattServer = runCatching { bluetoothManager.openGattServer(context, gattServerCallback) }.getOrNull()
            ?: return false
        
        val service = BluetoothGattService(GATT_SERVICE_UUID, BluetoothGattService.SERVICE_TYPE_PRIMARY)
        val characteristic = BluetoothGattCharacteristic(
            GATT_CHARACTERISTIC_UUID,
            BluetoothGattCharacteristic.PROPERTY_WRITE or BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE,
            BluetoothGattCharacteristic.PERMISSION_WRITE
        )
        service.addCharacteristic(characteristic)
        service.addCharacteristic(
            BluetoothGattCharacteristic(
                CONTROL_CHARACTERISTIC_UUID,
                BluetoothGattCharacteristic.PROPERTY_WRITE or BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE,
                BluetoothGattCharacteristic.PERMISSION_WRITE
            )
        )
        val added = runCatching { gattServer?.addService(service) == true }.getOrDefault(false)
        started = added
        if (!added) {
            runCatching { gattServer?.close() }
            gattServer = null
        }
        return added
    }

    fun stop() {
        started = false
        gattServer?.close()
        gattServer = null
        assemblyBuffers.clear()
        expectedLengths.clear()
        controlAssemblyBuffers.clear()
        controlExpectedLengths.clear()
    }

    private val gattServerCallback = object : BluetoothGattServerCallback() {
        override fun onConnectionStateChange(device: BluetoothDevice, status: Int, newState: Int) {
            if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                assemblyBuffers.remove(device.address)
                expectedLengths.remove(device.address)
                controlAssemblyBuffers.remove(device.address)
                controlExpectedLengths.remove(device.address)
            }
        }

        override fun onCharacteristicWriteRequest(
            device: BluetoothDevice,
            requestId: Int,
            characteristic: BluetoothGattCharacteristic,
            preparedWrite: Boolean,
            responseNeeded: Boolean,
            offset: Int,
            value: ByteArray
        ) {
            super.onCharacteristicWriteRequest(device, requestId, characteristic, preparedWrite, responseNeeded, offset, value)
            
            if (characteristic.uuid == GATT_CHARACTERISTIC_UUID || characteristic.uuid == CONTROL_CHARACTERISTIC_UUID) {
                if (responseNeeded) {
                    gattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, offset, value)
                }
                
                if (characteristic.uuid == GATT_CHARACTERISTIC_UUID) {
                    processIncomingChunk(device.address, value)
                } else {
                    processIncomingControlChunk(device.address, value)
                }
            }
        }
    }

    private fun processIncomingControlChunk(deviceAddress: String, chunk: ByteArray) {
        if (chunk.isEmpty()) return
        when (chunk[0].toInt()) {
            1 -> if (chunk.size >= 5) {
                val length = ((chunk[1].toInt() and 0xFF) shl 24) or
                    ((chunk[2].toInt() and 0xFF) shl 16) or
                    ((chunk[3].toInt() and 0xFF) shl 8) or
                    (chunk[4].toInt() and 0xFF)
                if (length !in 1..64 * 1024) return
                controlExpectedLengths[deviceAddress] = length
                controlAssemblyBuffers[deviceAddress] = ByteArrayOutputStream().also {
                    it.write(chunk, 5, chunk.size - 5)
                }
                checkControlComplete(deviceAddress)
            }
            2 -> controlAssemblyBuffers[deviceAddress]?.let {
                it.write(chunk, 1, chunk.size - 1)
                checkControlComplete(deviceAddress)
            }
        }
    }

    private fun checkControlComplete(deviceAddress: String) {
        val buffer = controlAssemblyBuffers[deviceAddress] ?: return
        val expected = controlExpectedLengths[deviceAddress] ?: return
        if (buffer.size() < expected) return
        _incomingControl.tryEmit(IncomingControl(deviceAddress, buffer.toByteArray().copyOf(expected)))
        controlAssemblyBuffers.remove(deviceAddress)
        controlExpectedLengths.remove(deviceAddress)
    }

    private fun processIncomingChunk(deviceAddress: String, chunk: ByteArray) {
        if (chunk.isEmpty()) return
        
        // 1st byte: 0x01 (start frame) + 4-byte length, or 0x02 (continuation).
        
        val type = chunk[0]
        when (type.toInt()) {
            1 -> {
                if (chunk.size >= 5) {
                    val length = ((chunk[1].toInt() and 0xFF) shl 24) or
                                 ((chunk[2].toInt() and 0xFF) shl 16) or
                                 ((chunk[3].toInt() and 0xFF) shl 8) or
                                 (chunk[4].toInt() and 0xFF)
                    
                    if (length !in 1..MAX_FRAME_BYTES) {
                        assemblyBuffers.remove(deviceAddress)
                        expectedLengths.remove(deviceAddress)
                        return
                    }
                    expectedLengths[deviceAddress] = length
                    val buffer = ByteArrayOutputStream()
                    buffer.write(chunk, 5, chunk.size - 5)
                    assemblyBuffers[deviceAddress] = buffer
                    
                    checkBufferComplete(deviceAddress)
                }
            }
            2 -> {
                val buffer = assemblyBuffers[deviceAddress]
                if (buffer != null) {
                    buffer.write(chunk, 1, chunk.size - 1)
                    checkBufferComplete(deviceAddress)
                }
            }
        }
    }

    private fun checkBufferComplete(deviceAddress: String) {
        val buffer = assemblyBuffers[deviceAddress] ?: return
        val expected = expectedLengths[deviceAddress] ?: return
        
        if (buffer.size() >= expected) {
            val fullPayload = buffer.toByteArray()
            val frame = fullPayload.copyOf(expected)
            _incomingData.tryEmit(frame)
            runCatching {
                val envelope = TransportWireCodec.read(DataInputStream(ByteArrayInputStream(frame)))
                inboundBus.publish(TransportKind.BLUETOOTH_DIRECT, envelope)
            }
            assemblyBuffers.remove(deviceAddress)
            expectedLengths.remove(deviceAddress)
        } else if (buffer.size() > MAX_FRAME_BYTES) {
            assemblyBuffers.remove(deviceAddress)
            expectedLengths.remove(deviceAddress)
        }
    }

}
