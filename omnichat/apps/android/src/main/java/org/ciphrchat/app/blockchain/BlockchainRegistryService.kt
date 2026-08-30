package org.ciphrchat.app.blockchain

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.ciphrchat.app.BuildConfig
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Service for fetching bootstrap relay records from an EVM testnet smart contract via standard JSON-RPC (eth_call)
 * and decoding ABI returned struct arrays.
 */
@Singleton
class BlockchainRegistryService @Inject constructor() {

    data class DecodedRelay(
        val owner: String,
        val multiaddr: String,
        val capacity: Long,
        val registeredAt: Long,
        val active: Boolean
    )

    suspend fun getActiveRelays(
        rpcUrl: String = BuildConfig.CIPHRCHAT_BLOCKCHAIN_RPC_URL.ifBlank { BlockchainConfig.DEFAULT_BASE_SEPOLIA_RPC_URL },
        contractAddress: String = BuildConfig.CIPHRCHAT_REGISTRY_CONTRACT_ADDRESS.ifBlank { BlockchainConfig.DEFAULT_BASE_SEPOLIA_REGISTRY_ADDRESS },
        chainId: Long = if (BuildConfig.CIPHRCHAT_BLOCKCHAIN_CHAIN_ID > 0) BuildConfig.CIPHRCHAT_BLOCKCHAIN_CHAIN_ID else BlockchainConfig.CHAIN_BASE_SEPOLIA
    ): Result<List<RelayDiscoveryParser.DiscoveredRelay>> = withContext(Dispatchers.IO) {
        runCatching {
            // Strictly enforce non-mainnet policy before sending any RPC
            MainnetProtectionPolicy.validateChainId(chainId)

            if (contractAddress == "0x0000000000000000000000000000000000000000" || contractAddress.isBlank()) {
                // Placeholder address before production contract deployment returns empty list gracefully
                return@runCatching emptyList()
            }

            // Encode eth_call data: getActiveRelays(0, 50)
            val callData = "0x" + BlockchainConfig.SELECTOR_GET_ACTIVE_RELAYS +
                    padUint256(0) + padUint256(50)

            val requestBody = JSONObject()
                .put("jsonrpc", "2.0")
                .put("id", 1)
                .put("method", "eth_call")
                .put(
                    "params", JSONArray()
                        .put(
                            JSONObject()
                                .put("to", contractAddress)
                                .put("data", callData)
                        )
                        .put("latest")
                )

            val rawResult = executeRpc(rpcUrl, requestBody.toString())
            if (rawResult.has("error")) {
                throw IllegalStateException("RPC error: " + rawResult.optJSONObject("error")?.optString("message", "Unknown error"))
            }

            val hex = rawResult.optString("result", "")
            if (hex.isBlank() || hex == "0x") return@runCatching emptyList()

            val decodedList = decodeRelayInfoArray(hex)
            decodedList
                .filter { it.active && it.multiaddr.isNotBlank() }
                .map { RelayDiscoveryParser.DiscoveredRelay(relayAddress = it.multiaddr, isAlive = true, latencyMs = 0L) }
        }
    }

    fun decodeRelayInfoArray(rawHex: String): List<DecodedRelay> {
        val cleanHex = if (rawHex.startsWith("0x", ignoreCase = true)) rawHex.substring(2) else rawHex
        if (cleanHex.length < 64) return emptyList()

        val byteData = runCatching {
            cleanHex.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
        }.getOrNull() ?: return emptyList()

        if (byteData.size < 64) return emptyList()

        // Read array offset
        val arrayOffset = readUint256(byteData, 0).toInt()
        if (arrayOffset < 0 || arrayOffset + 32 > byteData.size) return emptyList()

        // Read array length
        val arrayLength = readUint256(byteData, arrayOffset).toInt()
        if (arrayLength <= 0 || arrayLength > 1000) return emptyList()

        val results = mutableListOf<DecodedRelay>()
        val arrayDataStart = arrayOffset + 32

        for (i in 0 until arrayLength) {
            val elementOffsetPointer = arrayDataStart + (i * 32)
            if (elementOffsetPointer + 32 > byteData.size) break

            val elementRelativeOffset = readUint256(byteData, elementOffsetPointer).toInt()
            val structStart = arrayDataStart + elementRelativeOffset
            if (structStart + 160 > byteData.size) continue

            val ownerBytes = byteData.copyOfRange(structStart + 12, structStart + 32)
            val ownerHex = "0x" + ownerBytes.joinToString("") { "%02x".format(it) }

            val stringRelativeOffset = readUint256(byteData, structStart + 32).toInt()
            val capacity = readUint256(byteData, structStart + 64)
            val registeredAt = readUint256(byteData, structStart + 96)
            val active = readUint256(byteData, structStart + 128) != 0L

            val stringStart = structStart + stringRelativeOffset
            val multiaddr = if (stringStart + 32 <= byteData.size) {
                val stringLength = readUint256(byteData, stringStart).toInt()
                if (stringLength in 0..4096 && stringStart + 32 + stringLength <= byteData.size) {
                    val strBytes = byteData.copyOfRange(stringStart + 32, stringStart + 32 + stringLength)
                    String(strBytes, Charsets.UTF_8)
                } else ""
            } else ""

            results.add(
                DecodedRelay(
                    owner = ownerHex,
                    multiaddr = multiaddr,
                    capacity = capacity,
                    registeredAt = registeredAt,
                    active = active
                )
            )
        }
        return results
    }

    private fun readUint256(bytes: ByteArray, offset: Int): Long {
        if (offset < 0 || offset + 32 > bytes.size) return 0L
        var value = 0L
        for (i in 24 until 32) {
            value = (value shl 8) or (bytes[offset + i].toLong() and 0xFF)
        }
        return value
    }

    private fun padUint256(value: Long): String {
        val hex = java.lang.Long.toHexString(value)
        return hex.padStart(64, '0')
    }

    private fun executeRpc(endpoint: String, payload: String): JSONObject {
        val url = URL(endpoint)
        val connection = url.openConnection() as HttpURLConnection
        connection.requestMethod = "POST"
        connection.setRequestProperty("Content-Type", "application/json")
        connection.connectTimeout = 10_000
        connection.readTimeout = 10_000
        connection.doOutput = true

        connection.outputStream.use { it.write(payload.toByteArray(Charsets.UTF_8)) }
        val response = connection.inputStream.use { stream ->
            BufferedReader(InputStreamReader(stream)).readText()
        }
        return JSONObject(response)
    }
}
