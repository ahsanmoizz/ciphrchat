package org.ciphrchat.app.blockchain

/**
 * Parser for on-chain relay discovery records and ABI-encoded multiaddress strings.
 */
object RelayDiscoveryParser {

    data class DiscoveredRelay(
        val ownerAddress: String,
        val multiaddress: String,
        val capacity: Long,
        val registeredAtEpochMs: Long,
        val isActive: Boolean
    )

    /**
     * Parses raw JSON-RPC result or raw ABI string into a DiscoveredRelay.
     * Validates that the multiaddress is a valid libp2p multiaddress containing /p2p/ and transport ports.
     */
    fun parseMultiaddress(multiaddrRaw: String): String {
        val trimmed = multiaddrRaw.trim()
        require(trimmed.length in 16..512) { "Invalid multiaddress length: ${trimmed.length}" }
        require(trimmed.contains("/p2p/")) { "Multiaddress must contain a /p2p/<peer-id> segment" }
        require(
            trimmed.startsWith("/ip4/") ||
                    trimmed.startsWith("/ip6/") ||
                    trimmed.startsWith("/dns4/") ||
                    trimmed.startsWith("/dns6/")
        ) { "Multiaddress must start with a routable host protocol (/ip4, /ip6, /dns4, /dns6)" }
        require(trimmed.contains("/tcp/") || trimmed.contains("/udp/")) {
            "Multiaddress must specify a transport port (/tcp/ or /udp/)"
        }
        return trimmed
    }

    /**
     * Parses dynamic ABI bytes representation of an array of strings/structs from eth_call.
     */
    fun parseAbiString(hexData: String, offsetBytes: Int = 0): String {
        val clean = hexData.removePrefix("0x")
        if (clean.length < (offsetBytes + 64) * 2) return ""
        val lengthHex = clean.substring(offsetBytes * 2, (offsetBytes + 32) * 2)
        val length = lengthHex.toLongOrNull(16)?.toInt() ?: return ""
        if (length <= 0 || length > 1024) return ""
        val dataStart = (offsetBytes + 32) * 2
        val dataEnd = dataStart + length * 2
        if (clean.length < dataEnd) return ""
        val bytes = ByteArray(length) { i ->
            clean.substring(dataStart + i * 2, dataStart + i * 2 + 2).toInt(16).toByte()
        }
        return String(bytes, Charsets.UTF_8)
    }
}
