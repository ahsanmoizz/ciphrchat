package org.ciphrchat.app.transport

import android.bluetooth.BluetoothAdapter
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.location.LocationManager
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.wifi.WifiManager
import android.net.wifi.aware.WifiAwareManager
import android.nfc.NfcAdapter
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton
import org.ciphrchat.app.worker.PendingMessageRetryScheduler

/** Keeps every supported transport alive while the application process is alive. */
@Singleton
class TransportRuntimeManager @Inject constructor(
    private val registry: TransportRegistry,
    private val capabilityDetector: AndroidCapabilityDetector,
    private val retryScheduler: PendingMessageRetryScheduler,
    @ApplicationContext private val context: Context
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val startMutex = Mutex()
    private val isStarting = AtomicBoolean(false)
    private val hasPendingStart = AtomicBoolean(false)

    private val radioStateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            startAll()
        }
    }

    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            startAll()
        }

        override fun onLost(network: Network) {
            // Transient loss - keep state, don't crash
        }

        override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) {
            if (networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)) {
                retryScheduler.scheduleNow()
            }
        }
    }

    init {
        runCatching {
            val filter = IntentFilter().apply {
                addAction(BluetoothAdapter.ACTION_STATE_CHANGED)
                addAction(WifiManager.WIFI_STATE_CHANGED_ACTION)
                addAction(WifiAwareManager.ACTION_WIFI_AWARE_STATE_CHANGED)
                addAction(NfcAdapter.ACTION_ADAPTER_STATE_CHANGED)
                addAction(LocationManager.PROVIDERS_CHANGED_ACTION)
            }
            ContextCompat.registerReceiver(
                context,
                radioStateReceiver,
                filter,
                ContextCompat.RECEIVER_EXPORTED
            )
        }

        runCatching {
            val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager?
            val request = NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .build()
            connectivityManager?.registerNetworkCallback(request, networkCallback)
        }
    }

    fun startAll() {
        if (isStarting.get()) {
            hasPendingStart.set(true)
            return
        }
        scope.launch {
            if (!isStarting.compareAndSet(false, true)) {
                hasPendingStart.set(true)
                return@launch
            }
            try {
                do {
                    hasPendingStart.set(false)
                    startMutex.withLock {
                        val snapshot = capabilityDetector.refresh()
                        registry.all().forEach { adapter ->
                            if (snapshot.assessment(adapter.kind).canStart) {
                                runCatching { adapter.start() }
                            } else {
                                runCatching { adapter.stop() }
                            }
                        }
                        retryScheduler.scheduleNow()
                    }
                } while (hasPendingStart.getAndSet(false))
            } finally {
                isStarting.set(false)
            }
        }
    }
}

