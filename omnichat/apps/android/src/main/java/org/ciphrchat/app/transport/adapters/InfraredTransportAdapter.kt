package org.ciphrchat.app.transport.adapters

import android.content.Context
import android.hardware.ConsumerIrManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.ciphrchat.app.transport.*
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class InfraredTransportAdapter @Inject constructor(
    private val context: Context
) : TransportAdapter {
    override val kind: TransportKind = TransportKind.INFRARED
    override val capabilities: Set<TransportCapability> = setOf(
        TransportCapability.SMALL_TEXT,
        TransportCapability.OFFLINE
    )

    private val _state = MutableStateFlow(
        TransportState(TransportAvailability.EXPERIMENTAL, "Infrared PoC Ready")
    )
    override val state: StateFlow<TransportState> = _state.asStateFlow()

    private val irManager = context.getSystemService(Context.CONSUMER_IR_SERVICE) as ConsumerIrManager?

    override suspend fun start(): Result<Unit> {
        if (irManager == null || !irManager.hasIrEmitter()) {
            _state.value = TransportState(TransportAvailability.UNAVAILABLE, "No IR hardware detected")
            return Result.failure(Exception("IR not supported"))
        }
        
        _state.value = TransportState(TransportAvailability.AVAILABLE, "IR Emitter Ready")
        return Result.success(Unit)
    }

    override suspend fun stop(): Result<Unit> {
        _state.value = TransportState(TransportAvailability.AVAILABLE, "Stopped")
        return Result.success(Unit)
    }

    override suspend fun discoverPeers(): Result<List<DiscoveredPeer>> {
        // Infrared is unidirectional and cannot discover peers
        return Result.success(emptyList())
    }

    override suspend fun canReach(recipientId: String): Reachability {
        return Reachability.UNREACHABLE
    }

    override suspend fun send(envelope: OutboundEnvelope): SendResult {
        if (irManager == null || !irManager.hasIrEmitter()) {
            return SendResult.Failure(Exception("IR hardware not available"))
        }

        val frequency = 38000 // Standard 38kHz
        
        // Very basic mock translation of bytes to IR pulses for the scaffold
        val pattern = mutableListOf<Int>()
        for (byte in envelope.encryptedPayload) {
            pattern.add(1000) // mark
            pattern.add(if (byte > 0) 1000 else 500) // space
        }
        
        try {
            irManager.transmit(frequency, pattern.toIntArray())
        } catch (e: Exception) {
            return SendResult.Failure(e)
        }
        
        return SendResult.Success
    }
}
