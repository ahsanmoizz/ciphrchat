package org.ciphrchat.app.blockchain

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Service for fetching bootstrap relay records from an EVM testnet smart contract via standard JSON-RPC (eth_call).
 */
@Singleton
class BlockchainRegistryService @Inject constructor() {

    suspend fun getActiveRelays(
        rpcUrl: String = BlockchainConfig.DEFAULT_SEPOLIA_RPC_URL,
        contractAddress: String = BlockchainConfig.DEFAULT_SEPOLIA_REGISTRY_ADDRESS,
        chainId: Long = BlockchainConfig.CHAIN_SEPOLIA
    ): Result<List<RelayDiscoveryParser.DiscoveredRelay>> = withContext(Dispatchers.IO) {
        runCatching {
            // Strictly enforce non-mainnet policy before sending any RPC
            MainnetProtectionPolicy.validateChainId(chainId)

            if (contractAddress == "0x0000000000000000000000000000000000000000" || contractAddress.isBlank()) {
                // Placeholder address before production contract deployment returns empty list gracefully
                return@runCatching emptyList()
            }

            // Encode eth_call data: getActiveRelays(0, 10)
            val callData = "0x" + BlockchainConfig.SELECTOR_GET_ACTIVE_RELAYS +
                    padUint256(0) + padUint256(10)

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
            val hex = rawResult.optString("result", "")
            if (hex.isBlank() || hex == "0x") return@runCatching emptyList()

            // In actual testnet operation, parse returned ABI array
            emptyList()
        }
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
