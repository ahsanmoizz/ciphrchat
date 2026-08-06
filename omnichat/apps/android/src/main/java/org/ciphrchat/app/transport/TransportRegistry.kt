package org.ciphrchat.app.transport

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TransportRegistry @Inject constructor(
    private val adapters: Set<@JvmSuppressWildcards TransportAdapter>
) {
    fun all(): List<TransportAdapter> = adapters.sortedBy { it.kind.ordinal }

    fun states(): Flow<List<TransportState>> {
        val flows = all().map { it.state }
        return combine(flows) { states -> states.toList() }
    }

    fun byKind(kind: TransportKind): TransportAdapter? = adapters.firstOrNull { it.kind == kind }
}
