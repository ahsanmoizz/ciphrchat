package org.ciphrchat.app.transport.wifi

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.NetworkInfo
import android.net.wifi.p2p.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.ciphrchat.app.transport.DiscoveredPeer
import org.ciphrchat.app.transport.TransportKind
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
@SuppressLint("MissingPermission") // Simplified for prototype, assuming permissions granted
class WifiDirectManager @Inject constructor(
    private val context: Context
) {
    private val manager: WifiP2pManager? by lazy(LazyThreadSafetyMode.NONE) {
        context.getSystemService(Context.WIFI_P2P_SERVICE) as WifiP2pManager?
    }
    private var channel: WifiP2pManager.Channel? = null
    private var receiver: BroadcastReceiver? = null

    private val _discoveredPeers = MutableStateFlow<List<DiscoveredPeer>>(emptyList())
    val discoveredPeers: StateFlow<List<DiscoveredPeer>> = _discoveredPeers.asStateFlow()

    private val _connectionInfo = MutableStateFlow<WifiP2pInfo?>(null)
    val connectionInfo: StateFlow<WifiP2pInfo?> = _connectionInfo.asStateFlow()

    private val intentFilter = IntentFilter().apply {
        addAction(WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION)
        addAction(WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION)
        addAction(WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION)
        addAction(WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION)
    }

    private val peerListListener = WifiP2pManager.PeerListListener { peerList ->
        _discoveredPeers.value = peerList.deviceList.map { device ->
            DiscoveredPeer(
                id = device.deviceAddress,
                displayName = device.deviceName,
                transportKind = TransportKind.WIFI_DIRECT,
                reachabilityScore = 90,
                lastSeenEpochMs = System.currentTimeMillis()
            )
        }
    }

    private val connectionInfoListener = WifiP2pManager.ConnectionInfoListener { info ->
        _connectionInfo.value = info
    }

    fun start() {
        if (manager == null) return
        channel = manager?.initialize(context, context.mainLooper, null)

        receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                when (intent.action) {
                    WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION -> {
                        manager?.requestPeers(channel, peerListListener)
                    }
                    WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION -> {
                        val networkInfo = intent.getParcelableExtra<NetworkInfo>(WifiP2pManager.EXTRA_NETWORK_INFO)
                        if (networkInfo?.isConnected == true) {
                            manager?.requestConnectionInfo(channel, connectionInfoListener)
                        } else {
                            _connectionInfo.value = null
                        }
                    }
                }
            }
        }
        context.registerReceiver(receiver, intentFilter)

        manager?.discoverPeers(channel, object : WifiP2pManager.ActionListener {
            override fun onSuccess() {}
            override fun onFailure(reasonCode: Int) {}
        })
    }

    fun stop() {
        receiver?.let {
            try { context.unregisterReceiver(it) } catch (e: Exception) {}
            receiver = null
        }
        manager?.stopPeerDiscovery(channel, null)
    }

    fun connect(deviceAddress: String, onResult: (Boolean) -> Unit) {
        val config = WifiP2pConfig().apply {
            this.deviceAddress = deviceAddress
        }
        manager?.connect(channel, config, object : WifiP2pManager.ActionListener {
            override fun onSuccess() {
                onResult(true)
            }
            override fun onFailure(reason: Int) {
                onResult(false)
            }
        })
    }
}
