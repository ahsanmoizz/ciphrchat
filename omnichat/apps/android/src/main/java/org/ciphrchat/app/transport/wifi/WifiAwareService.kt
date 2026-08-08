package org.ciphrchat.app.transport.wifi

import android.annotation.SuppressLint
import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.wifi.aware.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import org.ciphrchat.app.identity.IdentityRepository
import org.ciphrchat.app.transport.DiscoveredPeer
import org.ciphrchat.app.transport.TransportKind
import java.net.Inet6Address
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

@Singleton
@SuppressLint("MissingPermission")
class WifiAwareService @Inject constructor(
    private val context: Context,
    private val identityRepository: IdentityRepository
) {
    private val awareManager = context.getSystemService(Context.WIFI_AWARE_SERVICE) as WifiAwareManager?
    private val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private var awareSession: WifiAwareSession? = null
    private var publishSession: PublishDiscoverySession? = null
    private var subscribeSession: SubscribeDiscoverySession? = null
    private var publishedIdentity: String? = null
    private var publisherNetworkCallback: ConnectivityManager.NetworkCallback? = null

    private val _discoveredPeers = MutableStateFlow<List<DiscoveredPeer>>(emptyList())
    val discoveredPeers: StateFlow<List<DiscoveredPeer>> = _discoveredPeers.asStateFlow()

    private val discoveredHandles = mutableMapOf<String, PeerHandle>()

    data class PeerConnection(
        val network: Network,
        val peerAddress: Inet6Address,
        val peerPort: Int,
        private val releaseNetwork: () -> Unit
    ) {
        fun close() = releaseNetwork()
    }

    suspend fun start(): Boolean = suspendCancellableCoroutine { cont ->
        if (awareSession != null) {
            cont.resume(true)
            return@suspendCancellableCoroutine
        }
        if (awareManager == null || !awareManager.isAvailable) {
            cont.resume(false)
            return@suspendCancellableCoroutine
        }

        awareManager.attach(object : AttachCallback() {
            override fun onAttached(session: WifiAwareSession) {
                awareSession = session
                startPublishing()
                startSubscribing()
                if (cont.isActive) cont.resume(true)
            }

            override fun onAttachFailed() {
                if (cont.isActive) cont.resume(false)
            }
        }, null)
    }

    private fun startPublishing() {
        scope.launch {
            val identity = identityRepository.current() ?: return@launch
            publishedIdentity = identity.publicId
            val config = PublishConfig.Builder()
                .setServiceName(SERVICE_NAME)
                .setServiceSpecificInfo(identity.publicId.toByteArray(Charsets.UTF_8))
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
            .setServiceName(SERVICE_NAME)
            .build()

        awareSession?.subscribe(config, object : DiscoverySessionCallback() {
            override fun onSubscribeStarted(session: SubscribeDiscoverySession) {
                subscribeSession = session
            }

            override fun onServiceDiscovered(
                peerHandle: PeerHandle,
                serviceSpecificInfo: ByteArray,
                matchFilter: List<ByteArray>
            ) {
                val publicId = serviceSpecificInfo.toString(Charsets.UTF_8)
                if (publicId.isBlank()) return
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

            override fun onServiceLost(peerHandle: PeerHandle, reason: Int) {
                discoveredHandles.entries.removeAll { it.value == peerHandle }
                _discoveredPeers.value = discoveredHandles.keys.map { id ->
                    DiscoveredPeer(id, "Aware Peer", TransportKind.WIFI_AWARE, 80, System.currentTimeMillis())
                }
            }
        }, null)
    }

    suspend fun startPublisherNetwork(port: Int): Boolean {
        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.S) return false
        val session = withTimeoutOrNull(5_000L) {
            while (publishSession == null) delay(50)
            publishSession
        } ?: return false
        val identity = publishedIdentity ?: return false
        val specifier = runCatching {
            WifiAwareNetworkSpecifier.Builder(session)
                .setPskPassphrase(pskFor(identity))
                .setPort(port)
                .setTransportProtocol(6)
                .build()
        }.getOrNull() ?: return false
        val request = NetworkRequest.Builder()
            .addTransportType(NetworkCapabilities.TRANSPORT_WIFI_AWARE)
            .setNetworkSpecifier(specifier)
            .build()

        publisherNetworkCallback?.let { runCatching { connectivityManager.unregisterNetworkCallback(it) } }
        lateinit var callback: ConnectivityManager.NetworkCallback
        return withTimeoutOrNull(5_000L) {
            suspendCancellableCoroutine { cont ->
                callback = object : ConnectivityManager.NetworkCallback() {
                    override fun onAvailable(network: Network) {
                        if (cont.isActive) cont.resume(true)
                    }

                    override fun onUnavailable() {
                        if (cont.isActive) cont.resume(false)
                    }

                    override fun onLost(network: Network) {
                        if (cont.isActive) cont.resume(false)
                    }
                }
                publisherNetworkCallback = callback
                cont.invokeOnCancellation {
                    runCatching { connectivityManager.unregisterNetworkCallback(callback) }
                }
                runCatching { connectivityManager.requestNetwork(request, callback) }
                    .onFailure { if (cont.isActive) cont.resume(false) }
            }
        } ?: false
    }

    fun stop() {
        publisherNetworkCallback?.let { runCatching { connectivityManager.unregisterNetworkCallback(it) } }
        publisherNetworkCallback = null
        publishSession?.close()
        subscribeSession?.close()
        awareSession?.close()
        publishSession = null
        subscribeSession = null
        awareSession = null
        publishedIdentity = null
        discoveredHandles.clear()
        _discoveredPeers.value = emptyList()
    }

    suspend fun requestNetwork(recipientId: String): PeerConnection? = withTimeoutOrNull(10_000L) {
        suspendCancellableCoroutine { cont ->
            if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.Q) {
                cont.resume(null)
                return@suspendCancellableCoroutine
            }

            val peerHandle = discoveredHandles[recipientId]
            val session = subscribeSession
            if (peerHandle == null || session == null) {
                cont.resume(null)
                return@suspendCancellableCoroutine
            }

            val specifier = runCatching {
                WifiAwareNetworkSpecifier.Builder(session, peerHandle)
                    .setPskPassphrase(pskFor(recipientId))
                    .build()
            }.getOrNull()
            if (specifier == null) {
                cont.resume(null)
                return@suspendCancellableCoroutine
            }

            val request = NetworkRequest.Builder()
                .addTransportType(NetworkCapabilities.TRANSPORT_WIFI_AWARE)
                .setNetworkSpecifier(specifier)
                .build()

            lateinit var callback: ConnectivityManager.NetworkCallback
            callback = object : ConnectivityManager.NetworkCallback() {
                override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
                    val info = capabilities.transportInfo as? WifiAwareNetworkInfo ?: return
                    val address = info.peerIpv6Addr ?: return
                    val port = info.port
                    if (port <= 0 || !cont.isActive) return
                    cont.resume(PeerConnection(network, address, port) {
                        runCatching { connectivityManager.unregisterNetworkCallback(callback) }
                    })
                }

                override fun onUnavailable() {
                    if (cont.isActive) cont.resume(null)
                }

                override fun onLost(network: Network) {
                    if (cont.isActive) cont.resume(null)
                }
            }
            cont.invokeOnCancellation {
                runCatching { connectivityManager.unregisterNetworkCallback(callback) }
            }
            runCatching { connectivityManager.requestNetwork(request, callback) }
                .onFailure { if (cont.isActive) cont.resume(null) }
        }
    }

    fun hasPeer(recipientId: String): Boolean = discoveredHandles.containsKey(recipientId)

    private fun pskFor(identity: String): String {
        val digest = java.security.MessageDigest.getInstance("SHA-256")
            .digest(identity.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }.take(63)
    }

    private companion object {
        const val SERVICE_NAME = "ciphrchat_aware"
    }
}
