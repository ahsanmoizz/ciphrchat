package org.ciphrchat.app.transport.bluetooth

import android.annotation.SuppressLint
import android.bluetooth.BluetoothManager
import android.bluetooth.le.AdvertiseCallback
import android.bluetooth.le.AdvertiseData
import android.bluetooth.le.AdvertiseSettings
import android.content.Context
import android.os.ParcelUuid
import org.ciphrchat.app.identity.IdentityRepository
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

@Singleton
@SuppressLint("MissingPermission")
class BleAdvertiser @Inject constructor(
    private val context: Context,
    private val identityRepository: IdentityRepository
) {
    companion object {
        val OMNICHAT_SERVICE_UUID: ParcelUuid = ParcelUuid(UUID.fromString("0000FF01-0000-1000-8000-00805F9B34FB"))
    }

    private val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager?
    private val advertiser get() = bluetoothManager?.adapter?.bluetoothLeAdvertiser
    private var isAdvertising = false

    private var advertiseCallback: AdvertiseCallback? = null

    suspend fun start(): Boolean {
        val identity = identityRepository.current() ?: return false
        val activeAdvertiser = runCatching { advertiser }.getOrNull() ?: return false

        return suspendCoroutine { cont ->
        if (isAdvertising) {
            cont.resume(true)
            return@suspendCoroutine
        }

        val settings = AdvertiseSettings.Builder()
            .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
            .setConnectable(true)
            .setTimeout(0)
            .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_HIGH)
            .build()

        val idBytes = ContactDiscoveryToken.forContactId(identity.publicId).toByteArray(Charsets.US_ASCII)

        val data = AdvertiseData.Builder()
            .setIncludeDeviceName(false)
            .addServiceUuid(OMNICHAT_SERVICE_UUID)
            .addServiceData(OMNICHAT_SERVICE_UUID, idBytes)
            .build()

        advertiseCallback = object : AdvertiseCallback() {
            override fun onStartSuccess(settingsInEffect: AdvertiseSettings?) {
                isAdvertising = true
                cont.resume(true)
            }

            override fun onStartFailure(errorCode: Int) {
                isAdvertising = false
                cont.resume(false)
            }
        }

        runCatching { activeAdvertiser.startAdvertising(settings, data, advertiseCallback) }
            .onFailure {
                advertiseCallback = null
                isAdvertising = false
                cont.resume(false)
            }
        }
    }

    fun stop() {
        if (!isAdvertising) return
        val activeAdvertiser = runCatching { advertiser }.getOrNull()
        advertiseCallback?.let {
            runCatching { activeAdvertiser?.stopAdvertising(it) }
            isAdvertising = false
        }
        advertiseCallback = null
    }
}
