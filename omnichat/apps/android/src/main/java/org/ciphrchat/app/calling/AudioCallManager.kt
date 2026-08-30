package org.ciphrchat.app.calling

import android.content.Context
import android.media.AudioManager
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import org.ciphrchat.app.BuildConfig
import org.ciphrchat.app.privacy.PrivacyManager
import org.json.JSONObject
import org.webrtc.*
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages real audio-only WebRTC calls with Opus codec, weak-network resilience (DTX/FEC),
 * ephemeral TURN REST credentials, and relay-only ICE privacy mode.
 */
@Singleton
class AudioCallManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val privacyManager: PrivacyManager
) {
    private val scope = CoroutineScope(Dispatchers.Main + Job())
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    private val _callState = MutableStateFlow<CallState>(CallState.Idle)
    val callState: StateFlow<CallState> = _callState.asStateFlow()

    private val _outgoingSignals = MutableSharedFlow<CallSignal>(extraBufferCapacity = 64)
    val outgoingSignals: SharedFlow<CallSignal> = _outgoingSignals.asSharedFlow()

    private var timeoutJob: Job? = null
    private var callDurationJob: Job? = null
    private var currentCallId: String? = null
    private var currentContactId: String? = null
    private var currentContactName: String? = null
    private var currentLocalId: String = "self"

    // Real WebRTC objects
    private var peerConnectionFactory: PeerConnectionFactory? = null
    private var peerConnection: PeerConnection? = null
    private var audioSource: AudioSource? = null
    private var localAudioTrack: AudioTrack? = null

    init {
        initializeWebRtc()
    }

    private fun initializeWebRtc() {
        runCatching {
            val initOptions = PeerConnectionFactory.InitializationOptions.builder(context)
                .setEnableInternalTracer(false)
                .createInitializationOptions()
            PeerConnectionFactory.initialize(initOptions)

            val options = PeerConnectionFactory.Options()
            peerConnectionFactory = PeerConnectionFactory.builder()
                .setOptions(options)
                .createPeerConnectionFactory()
        }
    }

    /**
     * Fetches short-lived ephemeral TURN REST credentials using explicit CIPHRCHAT_TURN_CREDENTIAL_URL.
     * Never derives HTTP URLs from libp2p addresses and never uses localhost fallbacks silently.
     */
    suspend fun fetchTurnCredentials(userId: String): PeerConnection.IceServer? = withContext(Dispatchers.IO) {
        runCatching {
            val endpointUrl = BuildConfig.CIPHRCHAT_TURN_CREDENTIAL_URL
            if (endpointUrl.isBlank()) {
                return@runCatching null
            }

            val url = URL(endpointUrl)
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "POST"
            connection.setRequestProperty("Content-Type", "application/json")
            connection.connectTimeout = 5000
            connection.readTimeout = 5000
            connection.doOutput = true

            val cleanUserId = userId.filter { it.isLetterOrDigit() || it == '_' || it == '-' }.take(64)
            val body = JSONObject().put("username", cleanUserId.ifBlank { "ciphr_user" }).toString()
            connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }

            if (connection.responseCode in 200..299) {
                val responseText = connection.inputStream.use { stream ->
                    BufferedReader(InputStreamReader(stream)).readText()
                }
                val json = JSONObject(responseText)
                val turnUrl = json.getString("turn_url")
                val username = json.getString("username")
                val credential = json.getString("credential")

                PeerConnection.IceServer.builder(turnUrl)
                    .setUsername(username)
                    .setPassword(credential)
                    .createIceServer()
            } else {
                null
            }
        }.getOrNull()
    }

    /**
     * Builds list of ICE servers dynamically using short-lived TURN credentials.
     */
    suspend fun getIceServers(userId: String): List<PeerConnection.IceServer> {
        val servers = mutableListOf<PeerConnection.IceServer>()

        // 1. Fetch short-lived REST credentials from server
        val ephemeralServer = fetchTurnCredentials(userId)
        if (ephemeralServer != null) {
            servers.add(ephemeralServer)
        }

        // 2. Add fallback STUN server only if IP privacy is OFF
        val isPrivacyOn = privacyManager.isIpPrivacyEnabled.value
        if (!isPrivacyOn) {
            servers.add(PeerConnection.IceServer.builder("stun:stun.l.google.com:19302").createIceServer())
        }

        return servers
    }

    /**
     * Creates and configures real PeerConnection with audio track and ICE constraints.
     */
    private fun createPeerConnection(callId: String, iceServers: List<PeerConnection.IceServer>): PeerConnection? {
        val factory = peerConnectionFactory ?: return null
        val isPrivacyOn = privacyManager.isIpPrivacyEnabled.value

        if (isPrivacyOn && iceServers.none { it.urls.any { url -> url.startsWith("turn:") || url.startsWith("turns:") } }) {
            // In Privacy Mode, a valid TURN server is strictly required to protect user IP
            return null
        }

        val rtcConfig = PeerConnection.RTCConfiguration(iceServers).apply {
            sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN
            continualGatheringPolicy = PeerConnection.ContinualGatheringPolicy.GATHER_CONTINUALLY
            if (isPrivacyOn) {
                // Force TURN relay only to hide peer IP addresses
                iceTransportsType = PeerConnection.IceTransportsType.RELAY
            } else {
                iceTransportsType = PeerConnection.IceTransportsType.ALL
            }
        }

        val observer = object : PeerConnection.Observer {
            override fun onIceCandidate(candidate: IceCandidate?) {
                if (candidate == null) return
                val isPrivacyActive = privacyManager.isIpPrivacyEnabled.value
                // When IP Privacy is ON, strip host/srflx candidates
                if (isPrivacyActive && !candidate.sdp.contains("typ relay")) {
                    return
                }
                val signal = CallSignal.IceCandidate(
                    callId = callId,
                    recipientId = currentContactId ?: "",
                    sdpMid = candidate.sdpMid ?: "",
                    sdpMLineIndex = candidate.sdpMLineIndex,
                    sdpCandidate = candidate.sdp
                )
                _outgoingSignals.tryEmit(signal)
            }

            override fun onIceConnectionChange(newState: PeerConnection.IceConnectionState?) {
                scope.launch {
                    when (newState) {
                        PeerConnection.IceConnectionState.CONNECTED,
                        PeerConnection.IceConnectionState.COMPLETED -> {
                            val contactId = currentContactId ?: ""
                            val contactName = currentContactName ?: ""
                            timeoutJob?.cancel()
                            transitionToConnected(callId, contactId, contactName)
                        }
                        PeerConnection.IceConnectionState.DISCONNECTED -> {
                            if (_callState.value is CallState.Connected) {
                                val current = _callState.value as CallState.Connected
                                _callState.value = CallState.Reconnecting(current.callId, current.contactId, current.contactName)
                            }
                        }
                        PeerConnection.IceConnectionState.FAILED -> {
                            endCall("Connection failed")
                        }
                        PeerConnection.IceConnectionState.CLOSED -> {
                            cleanupCallResources()
                        }
                        else -> {}
                    }
                }
            }

            override fun onSignalingChange(state: PeerConnection.SignalingState?) {}
            override fun onIceConnectionReceivingChange(receiving: Boolean) {}
            override fun onIceGatheringChange(state: PeerConnection.IceGatheringState?) {}
            override fun onIceCandidatesRemoved(candidates: Array<out IceCandidate>?) {}
            override fun onAddStream(stream: MediaStream?) {}
            override fun onRemoveStream(stream: MediaStream?) {}
            override fun onDataChannel(channel: DataChannel?) {}
            override fun onRenegotiationNeeded() {}
            override fun onAddTrack(receiver: RtpReceiver?, streams: Array<out MediaStream>?) {}
        }

        val pc = factory.createPeerConnection(rtcConfig, observer) ?: return null

        // Create local audio source and audio track (strictly NO video)
        val audioConstraints = MediaConstraints().apply {
            mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"))
            mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveVideo", "false"))
            optional.add(MediaConstraints.KeyValuePair("echoCancellation", "true"))
            optional.add(MediaConstraints.KeyValuePair("googEchoCancellation", "true"))
            optional.add(MediaConstraints.KeyValuePair("googAutoGainControl", "true"))
            optional.add(MediaConstraints.KeyValuePair("googNoiseSuppression", "true"))
            optional.add(MediaConstraints.KeyValuePair("googHighpassFilter", "true"))
        }

        val src = factory.createAudioSource(audioConstraints)
        audioSource = src
        val track = factory.createAudioTrack("ARDAMSa0", src)
        track.setEnabled(true)
        localAudioTrack = track

        pc.addTrack(track, listOf("ARDAMS"))
        peerConnection = pc
        return pc
    }

    /**
     * Starts an outgoing audio-only call.
     */
    fun startCall(contactId: String, contactName: String, localSenderId: String): String {
        val callId = UUID.randomUUID().toString()
        currentCallId = callId
        currentContactId = contactId
        currentContactName = contactName
        currentLocalId = localSenderId

        _callState.value = CallState.OutgoingRinging(callId, contactId, contactName)
        setupAudio(speaker = false)

        scope.launch {
            val iceServers = getIceServers(localSenderId)
            val pc = createPeerConnection(callId, iceServers)
            if (pc == null) {
                _callState.value = CallState.Failed(callId, "Calling service unavailable")
                return@launch
            }

            val mediaConstraints = MediaConstraints().apply {
                mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"))
                mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveVideo", "false"))
            }

            pc.createOffer(object : SdpObserver {
                override fun onCreateSuccess(desc: SessionDescription?) {
                    if (desc == null) return
                    pc.setLocalDescription(object : SdpObserver {
                        override fun onSetSuccess() {
                            val offerSignal = CallSignal.Offer(callId, desc.description, localSenderId, contactId)
                            _outgoingSignals.tryEmit(offerSignal)
                        }
                        override fun onSetFailure(error: String?) {
                            endCall("Failed to set local description: $error")
                        }
                        override fun onCreateSuccess(p0: SessionDescription?) {}
                        override fun onCreateFailure(p0: String?) {}
                    }, desc)
                }

                override fun onCreateFailure(error: String?) {
                    endCall("Failed to create offer: $error")
                }

                override fun onSetSuccess() {}
                override fun onSetFailure(p0: String?) {}
            }, mediaConstraints)
        }

        // 30-second ringing timeout
        timeoutJob?.cancel()
        timeoutJob = scope.launch {
            delay(30_000)
            if (_callState.value is CallState.OutgoingRinging) {
                endCall("No answer (timeout)")
            }
        }
        return callId
    }

    /**
     * Handles incoming call offer from peer.
     */
    fun onIncomingOffer(offer: CallSignal.Offer, contactName: String) {
        if (_callState.value !is CallState.Idle) {
            _outgoingSignals.tryEmit(CallSignal.Reject(offer.callId, offer.recipientId, offer.senderId, "Busy"))
            return
        }

        currentCallId = offer.callId
        currentContactId = offer.senderId
        currentContactName = contactName
        currentLocalId = offer.recipientId

        _callState.value = CallState.IncomingRinging(
            callId = offer.callId,
            contactId = offer.senderId,
            contactName = contactName,
            sdpOffer = offer.sdp
        )

        _outgoingSignals.tryEmit(CallSignal.Ringing(offer.callId, offer.recipientId, offer.senderId))

        timeoutJob?.cancel()
        timeoutJob = scope.launch {
            delay(30_000)
            if (_callState.value is CallState.IncomingRinging) {
                endCall("Missed call")
            }
        }
    }

    /**
     * User accepts incoming call.
     */
    fun acceptCall(localSenderId: String) {
        val current = _callState.value
        if (current !is CallState.IncomingRinging) return
        timeoutJob?.cancel()

        _callState.value = CallState.Connecting(current.callId, current.contactId, current.contactName)
        setupAudio(speaker = false)

        scope.launch {
            val iceServers = getIceServers(localSenderId)
            val pc = createPeerConnection(current.callId, iceServers)
            if (pc == null) {
                endCall("Calling service unavailable")
                return@launch
            }

            val remoteDesc = SessionDescription(SessionDescription.Type.OFFER, current.sdpOffer)
            pc.setRemoteDescription(object : SdpObserver {
                override fun onSetSuccess() {
                    val mediaConstraints = MediaConstraints().apply {
                        mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"))
                        mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveVideo", "false"))
                    }

                    pc.createAnswer(object : SdpObserver {
                        override fun onCreateSuccess(answerDesc: SessionDescription?) {
                            if (answerDesc == null) return
                            pc.setLocalDescription(object : SdpObserver {
                                override fun onSetSuccess() {
                                    val answerSignal = CallSignal.Answer(current.callId, answerDesc.description, localSenderId, current.contactId)
                                    _outgoingSignals.tryEmit(answerSignal)
                                }
                                override fun onSetFailure(error: String?) {
                                    endCall("Failed to set local answer: $error")
                                }
                                override fun onCreateSuccess(p0: SessionDescription?) {}
                                override fun onCreateFailure(p0: String?) {}
                            }, answerDesc)
                        }
                        override fun onCreateFailure(error: String?) {
                            endCall("Failed to create answer: $error")
                        }
                        override fun onSetSuccess() {}
                        override fun onSetFailure(p0: String?) {}
                    }, mediaConstraints)
                }

                override fun onSetFailure(error: String?) {
                    endCall("Failed to set remote offer: $error")
                }

                override fun onCreateSuccess(p0: SessionDescription?) {}
                override fun onCreateFailure(p0: String?) {}
            }, remoteDesc)
        }
    }

    /**
     * User declines incoming call.
     */
    fun declineCall(localSenderId: String) {
        val current = _callState.value
        if (current !is CallState.IncomingRinging) return
        timeoutJob?.cancel()

        _outgoingSignals.tryEmit(CallSignal.Reject(current.callId, localSenderId, current.contactId, "Declined"))
        _callState.value = CallState.Ended(current.callId, "Declined", 0L)
        cleanupCallResources()
    }

    /**
     * Handles call answer received from peer.
     */
    fun onCallAnswer(answer: CallSignal.Answer) {
        val current = _callState.value
        if (current is CallState.OutgoingRinging && current.callId == answer.callId) {
            timeoutJob?.cancel()
            val pc = peerConnection ?: return
            val remoteDesc = SessionDescription(SessionDescription.Type.ANSWER, answer.sdp)
            pc.setRemoteDescription(object : SdpObserver {
                override fun onSetSuccess() {}
                override fun onSetFailure(error: String?) {
                    endCall("Failed to set remote answer: $error")
                }
                override fun onCreateSuccess(p0: SessionDescription?) {}
                override fun onCreateFailure(p0: String?) {}
            }, remoteDesc)
        }
    }

    /**
     * Adds received remote ICE candidate.
     */
    fun onRemoteIceCandidate(candidateSignal: CallSignal.IceCandidate) {
        val pc = peerConnection ?: return
        if (currentCallId == candidateSignal.callId) {
            val iceCandidate = IceCandidate(
                candidateSignal.sdpMid,
                candidateSignal.sdpMLineIndex,
                candidateSignal.sdpCandidate
            )
            pc.addIceCandidate(iceCandidate)
        }
    }

    /**
     * Handles peer rejection or busy signal.
     */
    fun onCallReject(reject: CallSignal.Reject) {
        val current = _callState.value
        if (current is CallState.OutgoingRinging && current.callId == reject.callId) {
            timeoutJob?.cancel()
            _callState.value = CallState.Ended(reject.callId, reject.reason, 0L)
            cleanupCallResources()
        }
    }

    /**
     * Handles peer hangup signal.
     */
    fun onCallHangup(hangup: CallSignal.Hangup) {
        val current = _callState.value
        if (current.isCallActive(hangup.callId)) {
            timeoutJob?.cancel()
            callDurationJob?.cancel()
            _callState.value = CallState.Ended(hangup.callId, "Call ended by peer", hangup.durationSeconds)
            cleanupCallResources()
        }
    }

    /**
     * User hangs up the call.
     */
    fun hangup(localSenderId: String) {
        val current = _callState.value
        val callId = currentCallId ?: return
        val contactId = currentContactId ?: ""
        val duration = if (current is CallState.Connected) {
            (System.currentTimeMillis() - current.connectedAtEpochMs) / 1000L
        } else 0L

        timeoutJob?.cancel()
        callDurationJob?.cancel()

        if (contactId.isNotBlank()) {
            _outgoingSignals.tryEmit(CallSignal.Hangup(callId, localSenderId, contactId, duration))
        }

        _callState.value = CallState.Ended(callId, "Call ended", duration)
        cleanupCallResources()
    }

    fun toggleMute(): Boolean {
        val current = _callState.value
        if (current is CallState.Connected) {
            val newMute = !current.isMuted
            localAudioTrack?.setEnabled(!newMute)
            audioManager.isMicrophoneMute = newMute
            _callState.value = current.copy(isMuted = newMute)
            return newMute
        }
        return false
    }

    fun toggleSpeaker(): Boolean {
        val current = _callState.value
        if (current is CallState.Connected) {
            val newSpeaker = !current.isSpeakerOn
            audioManager.isSpeakerphoneOn = newSpeaker
            _callState.value = current.copy(isSpeakerOn = newSpeaker)
            return newSpeaker
        }
        return false
    }

    fun endCall(reason: String) {
        val callId = currentCallId ?: "unknown"
        timeoutJob?.cancel()
        callDurationJob?.cancel()
        _callState.value = CallState.Ended(callId, reason, 0L)
        cleanupCallResources()
    }

    private fun transitionToConnected(callId: String, contactId: String, contactName: String) {
        val connectedState = CallState.Connected(
            callId = callId,
            contactId = contactId,
            contactName = contactName,
            connectedAtEpochMs = System.currentTimeMillis(),
            isMuted = false,
            isSpeakerOn = false
        )
        _callState.value = connectedState

        callDurationJob?.cancel()
        callDurationJob = scope.launch {
            while (_callState.value is CallState.Connected) {
                delay(1000)
            }
        }
    }

    private fun setupAudio(speaker: Boolean) {
        try {
            audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
            audioManager.isMicrophoneMute = false
            audioManager.isSpeakerphoneOn = speaker
        } catch (_: Exception) {}
    }

    private fun cleanupCallResources() {
        try {
            audioManager.mode = AudioManager.MODE_NORMAL
            audioManager.isMicrophoneMute = false
            audioManager.isSpeakerphoneOn = false
            localAudioTrack?.setEnabled(false)
            localAudioTrack?.dispose()
            localAudioTrack = null
            audioSource?.dispose()
            audioSource = null
            peerConnection?.close()
            peerConnection?.dispose()
            peerConnection = null
        } catch (_: Exception) {}
    }

    private fun CallState.isCallActive(checkCallId: String): Boolean {
        return when (this) {
            is CallState.OutgoingRinging -> callId == checkCallId
            is CallState.IncomingRinging -> callId == checkCallId
            is CallState.Connecting -> callId == checkCallId
            is CallState.Connected -> callId == checkCallId
            is CallState.Reconnecting -> callId == checkCallId
            else -> false
        }
    }
}
