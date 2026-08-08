package org.ciphrchat.app.transport.bluetooth

import org.ciphrchat.app.transport.OutboundEnvelope
import java.util.Collections
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MeshRouter @Inject constructor() {

    // Bounded duplicate cache prevents forwarding loops when mesh routing is enabled.
    private val MAX_CACHE_SIZE = 1000
    private val seenMessageIds = Collections.synchronizedSet(LinkedHashSet<String>())

    fun shouldForward(envelope: OutboundEnvelope, currentIdentityId: String): Boolean {
        // Don't forward if it's meant for us
        if (envelope.recipientId == currentIdentityId) {
            return false
        }

        // Check if we've seen this message recently to prevent routing loops
        val messageIdStr = envelope.messageId
        if (seenMessageIds.contains(messageIdStr)) {
            return false
        }

        // Add to cache, maintain max size
        seenMessageIds.add(messageIdStr)
        if (seenMessageIds.size > MAX_CACHE_SIZE) {
            val iterator = seenMessageIds.iterator()
            if (iterator.hasNext()) {
                iterator.next()
                iterator.remove()
            }
        }

        // Check hop limit
        return envelope.hopLimit > 0
    }

    fun markOrigin(envelope: OutboundEnvelope) {
        seenMessageIds.add(envelope.messageId)
        if (seenMessageIds.size > MAX_CACHE_SIZE) {
            val iterator = seenMessageIds.iterator()
            if (iterator.hasNext()) {
                iterator.next()
                iterator.remove()
            }
        }
    }

    fun prepareForForwarding(envelope: OutboundEnvelope): OutboundEnvelope {
        // Decrement hop limit for the next hop
        return envelope.copy(hopLimit = envelope.hopLimit - 1)
    }
}
