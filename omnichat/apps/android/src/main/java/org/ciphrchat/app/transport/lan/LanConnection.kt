package org.ciphrchat.app.transport.lan

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.ciphrchat.app.transport.OutboundEnvelope
import org.ciphrchat.app.transport.TransportInboundBus
import org.ciphrchat.app.transport.TransportKind
import org.ciphrchat.app.transport.TransportWireCodec
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LanConnection @Inject constructor(
    private val inboundBus: TransportInboundBus
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val serverSockets = ConcurrentHashMap<Int, ServerSocket>()

    suspend fun startServer(port: Int, transport: TransportKind = TransportKind.WIFI_LAN) = withContext(Dispatchers.IO) {
        if (serverSockets[port]?.isClosed == false) {
            return@withContext
        }
        runCatching {
            stopServer(port)
            val server = ServerSocket(port)
            server.reuseAddress = true
            serverSockets[port] = server
            scope.launch {
                while (isActive && !server.isClosed) {
                    runCatching { server.accept() }
                        .onSuccess { socket ->
                            launch { receive(socket, transport) }
                        }
                        .onFailure { if (!server.isClosed) return@launch }
                }
            }
        }
    }

    suspend fun stopServer(port: Int? = null) = withContext(Dispatchers.IO) {
        val targets = if (port == null) serverSockets.values.toList() else listOfNotNull(serverSockets[port])
        targets.forEach(ServerSocket::close)
        if (port == null) serverSockets.clear() else serverSockets.remove(port)
    }

    suspend fun sendEnvelope(ipAddress: String, port: Int, envelope: OutboundEnvelope): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            Socket(ipAddress, port).use { socket ->
                socket.soTimeout = 10_000
                DataOutputStream(BufferedOutputStream(socket.getOutputStream())).use { out ->
                    TransportWireCodec.write(out, envelope)
                    out.flush()
                }
            }
        }
    }

    private fun receive(socket: Socket, transport: TransportKind) {
        socket.use {
            DataInputStream(BufferedInputStream(it.getInputStream())).use { input ->
                val envelope = TransportWireCodec.read(input)
                inboundBus.publish(transport, envelope)
            }
        }
    }
}
