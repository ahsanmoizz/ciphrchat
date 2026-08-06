package org.ciphrchat.app.transport.lan

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.ciphrchat.app.transport.OutboundEnvelope
import java.io.DataInputStream
import java.io.DataOutputStream
import java.net.ServerSocket
import java.net.Socket
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LanConnection @Inject constructor() {
    private var serverSocket: ServerSocket? = null
    
    // In a real implementation this would hold many sockets and dispatch incoming envelopes.
    // For Phase 3, we just open a port and can connect to a specific IP.

    suspend fun startServer(port: Int) = withContext(Dispatchers.IO) {
        runCatching {
            serverSocket = ServerSocket(port)
            // Start accepting connections loop (stub for this phase)
            // while (true) { val client = serverSocket!!.accept() ... }
        }
    }

    suspend fun stopServer() = withContext(Dispatchers.IO) {
        serverSocket?.close()
        serverSocket = null
    }

    suspend fun sendEnvelope(ipAddress: String, port: Int, envelope: OutboundEnvelope): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            Socket(ipAddress, port).use { socket ->
                val out = DataOutputStream(socket.getOutputStream())
                // Simple framing: version(1 byte) + payloadSize(4 bytes) + payload
                out.writeByte(1)
                out.writeInt(envelope.encryptedPayload.size)
                out.write(envelope.encryptedPayload)
                out.flush()
            }
        }
    }
}
