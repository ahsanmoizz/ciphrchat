package org.ciphrchat.app.transport.wifi

import android.annotation.SuppressLint
import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.wifi.aware.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.ciphrchat.app.identity.IdentityRepository
import org.ciphrchat.app.transport.DiscoveredPeer
import org.ciphrchat.app.transport.TransportKind
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

@Singleton
@SuppressLint("MissingPermission")
class WifiAwareService @Inject constructor(
    private val context: Context,
    private val identityRepository: IdentityRepository
) {
    private val awareManager = context.getSystemService(Context.WIFI_AWARE_SERVICE) as WifiAwareManager?
    private val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    
    private var awareSession: WifiAwareSession? = null
    private var publishSession: PublishDiscoverySession? = null
    private var subscribeSession: SubscribeDiscoverySession? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val _discoveredPeers = MutableStateFlow<List<DiscoveredPeer>>(emptyList())
    val discoveredPeers: StateFlow<List<DiscoveredPeer>> = _discoveredPeers.asStateFlow()

    // Map PeerHandle to their public ID
    private val discoveredHandles = mutableMapOf<String, PeerHandle>()

    suspend fun start(): Boolean = suspendCoroutine { cont ->
        if (awareManager == null || !awareManager.isAvailable) {
            cont.resume(false)
            return@suspendCoroutine
        }

        awareManager.attach(object : AttachCallback() {
            override fun onAttached(session: WifiAwareSession) {
                awareSession = session
                startPublishing()
                startSubscribing()
                cont.resume(true)
            }
            override fun onAttachFailed() {
                cont.resume(false)
            }
        }, null)
    }

    private fun startPublishing() {
        scope.launch {
            val identity = identityRepository.current() ?: return@launch
            val config = PublishConfig.Builder()
                .setServiceName("ciphrchat_aware")
                .setServiceSpecificInfo(identity.publicId.toByteArray())
                .build()

            awareSession?.publish(config, object : DiscoverySessionCallback() {
                override fun onPublishStarted(session: PublishDiscoverySession) {
                    publishSession = session
                }
            }, null)
        }
    }

    private fun startSubscribing() {
        val config = SubscribeConfig.Builder()
            .setServiceName("ciphrchat_aware")
            .build()
            
        awareSession?.subscribe(config, object : DiscoverySessionCallback() {
            override fun onSubscribeStarted(session: SubscribeDiscoverySession) {
                subscribeSession = session
            }
            
            override fun onServiceDiscovered(peerHandle: PeerHandle, serviceSpecificInfo: ByteArray, matchFilter: List<ByteArray>) {
                val publicId = String(serviceSpecificInfo)
                discoveredHandles[publicId] = peerHandle
                
                _discoveredPeers.value = discoveredHandles.keys.map { id ->
                    DiscoveredPeer(
                        id = id,
                        displayName = "Aware Peer",
                        transportKind = TransportKind.WIFI_AWARE,
                        reachabilityScore = 80,
                        lastSeenEpochMs = System.currentTimeMillis()
                    )
                }
            }
        }, null)
    }

    fun stop() {
        publishSession?.close()
        subscribeSession?.close()
        awareSession?.close()
        publishSession = null
        subscribeSession = null
        awareSession = null
        discoveredHandles.clear()
        _discoveredPeers.value = emptyList()
    }

    suspend fun requestNetwork(recipientId: String): Network? = suspendCoroutine { cont ->
        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.Q) {
            cont.resume(null)
            return@suspendCoroutine
        }

        val peerHandle = discoveredHandles[recipientId]
        if (peerHandle == null || awareSession == null) {
            cont.resume(null)
            return@suspendCoroutine
        }

        // Derive a robust PSK from the known public recipient ID
        val digest = java.security.MessageDigest.getInstance("SHA-256")
        val hash = digest.digest(recipientId.toByteArray())
        val psk = android.util.Base64.encodeToString(hash, android.util.Base64.NO_WRAP).take(63)

        val networkSpecifier = WifiAwareNetworkSpecifier.Builder(subscribeSession!!, peerHandle)
            .setPskPassphrase(psk)
            .build()

        val networkRequest = NetworkRequest.Builder()
            .addTransportType(NetworkCapabilities.TRANSPORT_WIFI_AWARE)
            .setNetworkSpecifier(networkSpecifier)
            .build()

        connectivityManager.requestNetwork(networkRequest, object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                cont.resume(network)
            }
            override fun onUnavailable() {
                cont.resume(null)
            }
        })
    }
}
