package org.ciphrchat.app.transport

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import javax.inject.Inject
import javax.inject.Singleton

data class InboundTransportEnvelope(
    val transport: TransportKind,
    val envelope: OutboundEnvelope
)

@Singleton
class TransportInboundBus @Inject constructor() {
    private val _events = MutableSharedFlow<InboundTransportEnvelope>(extraBufferCapacity = 64)
    val events: SharedFlow<InboundTransportEnvelope> = _events.asSharedFlow()

    fun publish(transport: TransportKind, envelope: OutboundEnvelope): Boolean =
        _events.tryEmit(InboundTransportEnvelope(transport, envelope))
}
