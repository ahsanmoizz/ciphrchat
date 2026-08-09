package org.ciphrchat.app.transport

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TransportRegistry @Inject constructor(
    private val adapters: Set<@JvmSuppressWildcards TransportAdapter>,
    private val capabilityDetector: AndroidCapabilityDetector
) {
    fun all(): List<TransportAdapter> = adapters.sortedBy { it.kind.ordinal }

    fun states(): Flow<List<TransportState>> {
        val flows = all().map { it.state }
        return combine(combine(flows) { states -> states.toList() }, capabilityDetector.snapshot) {
                states, snapshot ->
            states.map { runtimeState ->
                val capability = snapshot.assessment(runtimeState.kind)
                if (capability.canStart) runtimeState else TransportState(
                    runtimeState.kind,
                    capability.availability,
                    capability.detail
                )
            }
        }
    }

    fun byKind(kind: TransportKind): TransportAdapter? = adapters.firstOrNull { it.kind == kind }
}
