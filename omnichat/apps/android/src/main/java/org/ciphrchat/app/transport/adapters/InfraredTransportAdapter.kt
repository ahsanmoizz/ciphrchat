package org.ciphrchat.app.transport.adapters

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.ConsumerIrManager
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.ciphrchat.app.transport.*
import org.ciphrchat.app.transport.infrared.InfraredCameraReceiver
import org.ciphrchat.app.transport.infrared.InfraredFrameCodec
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class InfraredTransportAdapter @Inject constructor(
    private val context: Context,
    private val receiver: InfraredCameraReceiver,
    private val inboundBus: TransportInboundBus
) : TransportAdapter {
    override val kind: TransportKind = TransportKind.INFRARED
    override val capabilities: Set<TransportCapability> = setOf(
        TransportCapability.SMALL_TEXT,
        TransportCapability.OFFLINE
    )

    private val _state = MutableStateFlow(
        TransportState(kind, TransportAvailability.STARTING, "Optical IR link is not started")
    )
    override val state: StateFlow<TransportState> = _state.asStateFlow()
    private val irManager = context.getSystemService(Context.CONSUMER_IR_SERVICE) as ConsumerIrManager?
    private val txMutex = Mutex()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var collectionStarted = false

    override suspend fun start(): Result<Unit> {
        if (irManager == null || !irManager.hasIrEmitter()) {
            _state.value = TransportState(kind, TransportAvailability.UNAVAILABLE, "No IR emitter detected")
            return Result.failure(Exception("IR transmitter is not supported"))
        }
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            _state.value = TransportState(kind, TransportAvailability.PERMISSION_REQUIRED, "Camera permission is required to receive optical messages")
            return Result.failure(SecurityException("Camera permission is required for the IR receiver"))
        }
        if (!receiver.isAttached()) {
            _state.value = TransportState(kind, TransportAvailability.STARTING, "Waiting for the camera receiver")
            scope.launch {
                delay(1_500L)
                if (receiver.isAttached()) start()
            }
            return Result.success(Unit)
        }
        if (!collectionStarted) {
            collectionStarted = true
            scope.launch {
                receiver.frames.collect { frame ->
                    runCatching {
                        val envelope = org.ciphrchat.app.transport.TransportWireCodec.read(
                            java.io.DataInputStream(java.io.ByteArrayInputStream(frame))
                        )
                        inboundBus.publish(TransportKind.INFRARED, envelope)
                    }
                }
            }
        }
        _state.value = TransportState(
            kind,
            TransportAvailability.UNAVAILABLE,
            "IR emitter detected, but Android remote-control IR is not a verified bidirectional message link"
        )
        return Result.failure(IllegalStateException(_state.value.detail))
    }

    override suspend fun stop(): Result<Unit> {
        _state.value = TransportState(kind, TransportAvailability.DISABLED_BY_USER, "Stopped")
        return Result.success(Unit)
    }

    override suspend fun discoverPeers(): Result<List<DiscoveredPeer>> = Result.success(emptyList())

    override suspend fun canReach(recipientId: String): Reachability =
        if (_state.value.availability == TransportAvailability.AVAILABLE) Reachability.Reachable
        else Reachability.Unreachable("Align the two phones and enable camera access")

    override suspend fun send(envelope: OutboundEnvelope): SendResult {
        val manager = irManager ?: return SendResult.Rejected("IR transmitter unavailable")
        if (_state.value.availability != TransportAvailability.AVAILABLE) return SendResult.Rejected(_state.value.detail)
        val wire = ByteArrayOutputStream().use { buffer ->
            DataOutputStream(buffer).use { TransportWireCodec.write(it, envelope) }
            buffer.toByteArray()
        }
        if (wire.size > InfraredFrameCodec.MAX_PAYLOAD_BYTES) return SendResult.Rejected("Message is too large for the optical link")
        return runCatching {
            txMutex.withLock {
                val frame = InfraredFrameCodec.encode(wire)
                manager.transmit(38_000, InfraredFrameCodec.toConsumerIrPattern(frame))
            }
            SendResult.Accepted(kind, "ir-optical")
        }.getOrElse { SendResult.Failed(it) }
    }
}
