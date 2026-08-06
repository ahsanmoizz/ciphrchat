package org.ciphrchat.app.transport.lan

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.ciphrchat.app.identity.IdentityRepository
import org.ciphrchat.app.transport.DiscoveredPeer
import org.ciphrchat.app.transport.TransportKind
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LanDiscovery @Inject constructor(
    private val context: Context,
    private val identityRepository: IdentityRepository
) {
    private val nsdManager = context.getSystemService(Context.NSD_SERVICE) as NsdManager
    private val SERVICE_TYPE = "_ciphr._tcp."

    private val _discoveredPeers = MutableStateFlow<List<DiscoveredPeer>>(emptyList())
    val discoveredPeers: StateFlow<List<DiscoveredPeer>> = _discoveredPeers.asStateFlow()

    private val discoveredServices = mutableMapOf<String, NsdServiceInfo>()
    
    private var registrationListener: NsdManager.RegistrationListener? = null
    private var discoveryListener: NsdManager.DiscoveryListener? = null

    suspend fun start(port: Int): Result<Unit> = runCatching {
        val identity = identityRepository.current() ?: throw IllegalStateException("No identity")
        
        val serviceInfo = NsdServiceInfo().apply {
            serviceName = identity.publicId
            serviceType = SERVICE_TYPE
            this.port = port
        }

        registrationListener = object : NsdManager.RegistrationListener {
            override fun onServiceRegistered(NsdServiceInfo: NsdServiceInfo) {}
            override fun onRegistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {}
            override fun onServiceUnregistered(arg0: NsdServiceInfo) {}
            override fun onUnregistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {}
        }
        
        nsdManager.registerService(serviceInfo, NsdManager.PROTOCOL_DNS_SD, registrationListener)

        discoveryListener = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(regType: String) {}
            override fun onServiceFound(service: NsdServiceInfo) {
                if (service.serviceType == SERVICE_TYPE && service.serviceName != identity.publicId) {
                    nsdManager.resolveService(service, object : NsdManager.ResolveListener {
                        override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {}
                        override fun onServiceResolved(serviceInfo: NsdServiceInfo) {
                            discoveredServices[serviceInfo.serviceName] = serviceInfo
                            updatePeers()
                        }
                    })
                }
            }
            override fun onServiceLost(service: NsdServiceInfo) {
                discoveredServices.remove(service.serviceName)
                updatePeers()
            }
            override fun onDiscoveryStopped(serviceType: String) {}
            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
                nsdManager.stopServiceDiscovery(this)
            }
            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {}
        }

        nsdManager.discoverServices(SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, discoveryListener)
    }

    fun stop() {
        registrationListener?.let {
            try { nsdManager.unregisterService(it) } catch (e: Exception) {}
            registrationListener = null
        }
        discoveryListener?.let {
            try { nsdManager.stopServiceDiscovery(it) } catch (e: Exception) {}
            discoveryListener = null
        }
        discoveredServices.clear()
        updatePeers()
    }

    fun getResolvedService(publicId: String): NsdServiceInfo? {
        return discoveredServices[publicId]
    }

    private fun updatePeers() {
        _discoveredPeers.value = discoveredServices.values.map { info ->
            DiscoveredPeer(
                id = info.serviceName,
                displayName = "LAN Peer",
                transportKind = TransportKind.WIFI_LAN,
                reachabilityScore = 100,
                lastSeenEpochMs = System.currentTimeMillis()
            )
        }
    }
}
