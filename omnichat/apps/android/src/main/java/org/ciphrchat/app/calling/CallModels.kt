package org.ciphrchat.app.calling

import org.json.JSONObject

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
        val isSpeakerOn: Boolean = false
    ) : CallState

    data class Reconnecting(
        val callId: String,
        val contactId: String,
        val contactName: String
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

    data class Offer(
        override val callId: String,
        val sdp: String,
        val senderId: String,
        val recipientId: String,
        val timestamp: Long = System.currentTimeMillis()
    ) : CallSignal("OFFER")

    data class Answer(
        override val callId: String,
        val sdp: String,
        val senderId: String,
        val recipientId: String,
        val timestamp: Long = System.currentTimeMillis()
    ) : CallSignal("ANSWER")

    data class IceCandidate(
        override val callId: String,
        val sdpMid: String,
        val sdpMLineIndex: Int,
        val sdpCandidate: String
    ) : CallSignal("ICE_CANDIDATE")

    data class Ringing(
        override val callId: String,
        val senderId: String,
        val recipientId: String
    ) : CallSignal("RINGING")

    data class Reject(
        override val callId: String,
        val senderId: String,
        val recipientId: String,
        val reason: String
    ) : CallSignal("REJECT")

    data class Hangup(
        override val callId: String,
        val senderId: String,
        val recipientId: String,
        val durationSeconds: Long
    ) : CallSignal("HANGUP")

    fun toJson(): String {
        val root = JSONObject().put("type", type).put("callId", callId)
        when (this) {
            is Offer -> root.put("sdp", sdp).put("senderId", senderId).put("recipientId", recipientId).put("timestamp", timestamp)
            is Answer -> root.put("sdp", sdp).put("senderId", senderId).put("recipientId", recipientId).put("timestamp", timestamp)
            is IceCandidate -> root.put("sdpMid", sdpMid).put("sdpMLineIndex", sdpMLineIndex).put("sdpCandidate", sdpCandidate)
            is Ringing -> root.put("senderId", senderId).put("recipientId", recipientId)
            is Reject -> root.put("senderId", senderId).put("recipientId", recipientId).put("reason", reason)
            is Hangup -> root.put("senderId", senderId).put("recipientId", recipientId).put("durationSeconds", durationSeconds)
        }
        return root.toString()
    }

    companion object {
        fun fromJson(jsonStr: String): CallSignal? = runCatching {
            val json = JSONObject(jsonStr)
            val type = json.getString("type")
            val callId = json.getString("callId")
            when (type) {
                "OFFER" -> Offer(callId, json.getString("sdp"), json.getString("senderId"), json.getString("recipientId"), json.optLong("timestamp"))
                "ANSWER" -> Answer(callId, json.getString("sdp"), json.getString("senderId"), json.getString("recipientId"), json.optLong("timestamp"))
                "ICE_CANDIDATE" -> IceCandidate(callId, json.getString("sdpMid"), json.getInt("sdpMLineIndex"), json.getString("sdpCandidate"))
                "RINGING" -> Ringing(callId, json.getString("senderId"), json.getString("recipientId"))
                "REJECT" -> Reject(callId, json.getString("senderId"), json.getString("recipientId"), json.optString("reason", "Decline"))
                "HANGUP" -> Hangup(callId, json.getString("senderId"), json.getString("recipientId"), json.optLong("durationSeconds", 0L))
                else -> null
            }
        }.getOrNull()
    }
}
