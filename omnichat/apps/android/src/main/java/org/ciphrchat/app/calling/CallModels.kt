package org.ciphrchat.app.calling

import org.json.JSONObject

data class CallDiagnostics(
    val rttMs: Long = 0L,
    val jitterMs: Double = 0.0,
    val packetsLost: Long = 0L,
    val localCandidateType: String = "",
    val remoteCandidateType: String = "",
    val iceConnectionState: String = "NEW"
)

sealed interface CallState {
    object Idle : CallState

    data class OutgoingRinging(
        val callId: String,
        val contactId: String,
        val contactName: String,
        val startedAtEpochMs: Long = System.currentTimeMillis()
    ) : CallState

    data class IncomingRinging(
        val callId: String,
        val contactId: String,
        val contactName: String,
        val sdpOffer: String,
        val startedAtEpochMs: Long = System.currentTimeMillis()
    ) : CallState

    data class Connecting(
        val callId: String,
        val contactId: String,
        val contactName: String
    ) : CallState

    data class Connected(
        val callId: String,
        val contactId: String,
        val contactName: String,
        val connectedAtEpochMs: Long = System.currentTimeMillis(),
        val isMuted: Boolean = false,
        val isSpeakerOn: Boolean = false,
        val diagnostics: CallDiagnostics = CallDiagnostics()
    ) : CallState

    data class Reconnecting(
        val callId: String,
        val contactId: String,
        val contactName: String,
        val attempt: Int = 1,
        val maxAttempts: Int = 3,
        val reconnectStartedEpochMs: Long = System.currentTimeMillis()
    ) : CallState

    data class Ended(
        val callId: String,
        val reason: String,
        val durationSeconds: Long
    ) : CallState

    data class Failed(
        val callId: String,
        val error: String
    ) : CallState
}

sealed class CallSignal(val type: String) {
    abstract val callId: String
    abstract val recipientId: String

    data class Offer(
        override val callId: String,
        val sdp: String,
        val senderId: String,
        override val recipientId: String,
        val timestamp: Long = System.currentTimeMillis(),
        val isIceRestart: Boolean = false
    ) : CallSignal("OFFER")

    data class Answer(
        override val callId: String,
        val sdp: String,
        val senderId: String,
        override val recipientId: String,
        val timestamp: Long = System.currentTimeMillis()
    ) : CallSignal("ANSWER")

    data class IceCandidate(
        override val callId: String,
        override val recipientId: String,
        val sdpMid: String,
        val sdpMLineIndex: Int,
        val sdpCandidate: String,
        val senderId: String = ""
    ) : CallSignal("ICE_CANDIDATE")

    data class Ringing(
        override val callId: String,
        val senderId: String,
        override val recipientId: String
    ) : CallSignal("RINGING")

    data class Reject(
        override val callId: String,
        val senderId: String,
        override val recipientId: String,
        val reason: String
    ) : CallSignal("REJECT")

    data class Hangup(
        override val callId: String,
        val senderId: String,
        override val recipientId: String,
        val durationSeconds: Long
    ) : CallSignal("HANGUP")

    fun toJson(): String {
        val root = JSONObject().put("type", type).put("callId", callId).put("recipientId", recipientId)
        when (this) {
            is Offer -> root.put("sdp", sdp).put("senderId", senderId).put("timestamp", timestamp).put("isIceRestart", isIceRestart)
            is Answer -> root.put("sdp", sdp).put("senderId", senderId).put("timestamp", timestamp)
            is IceCandidate -> root.put("sdpMid", sdpMid).put("sdpMLineIndex", sdpMLineIndex).put("sdpCandidate", sdpCandidate).put("senderId", senderId)
            is Ringing -> root.put("senderId", senderId)
            is Reject -> root.put("senderId", senderId).put("reason", reason)
            is Hangup -> root.put("senderId", senderId).put("durationSeconds", durationSeconds)
        }
        return root.toString()
    }

    companion object {
        fun fromJson(jsonStr: String): CallSignal? = runCatching {
            val json = JSONObject(jsonStr)
            val type = json.getString("type")
            val callId = json.getString("callId")
            val recipientId = json.optString("recipientId", "")
            when (type) {
                "OFFER" -> Offer(
                    callId = callId,
                    sdp = json.getString("sdp"),
                    senderId = json.getString("senderId"),
                    recipientId = recipientId.ifBlank { json.getString("recipientId") },
                    timestamp = json.optLong("timestamp", System.currentTimeMillis()),
                    isIceRestart = json.optBoolean("isIceRestart", false)
                )
                "ANSWER" -> Answer(
                    callId = callId,
                    sdp = json.getString("sdp"),
                    senderId = json.getString("senderId"),
                    recipientId = recipientId.ifBlank { json.getString("recipientId") },
                    timestamp = json.optLong("timestamp", System.currentTimeMillis())
                )
                "ICE_CANDIDATE" -> IceCandidate(
                    callId = callId,
                    recipientId = recipientId,
                    sdpMid = json.getString("sdpMid"),
                    sdpMLineIndex = json.getInt("sdpMLineIndex"),
                    sdpCandidate = json.getString("sdpCandidate"),
                    senderId = json.optString("senderId", "")
                )
                "RINGING" -> Ringing(
                    callId = callId,
                    senderId = json.getString("senderId"),
                    recipientId = recipientId.ifBlank { json.getString("recipientId") }
                )
                "REJECT" -> Reject(
                    callId = callId,
                    senderId = json.getString("senderId"),
                    recipientId = recipientId.ifBlank { json.getString("recipientId") },
                    reason = json.optString("reason", "Decline")
                )
                "HANGUP" -> Hangup(
                    callId = callId,
                    senderId = json.getString("senderId"),
                    recipientId = recipientId.ifBlank { json.getString("recipientId") },
                    durationSeconds = json.optLong("durationSeconds", 0L)
                )
                else -> null
            }
        }.getOrNull()
    }
}
