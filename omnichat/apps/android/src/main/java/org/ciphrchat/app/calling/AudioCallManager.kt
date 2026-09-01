package org.ciphrchat.app.calling

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import org.ciphrchat.app.BuildConfig
import org.ciphrchat.app.privacy.IpPrivacyPolicy
import org.ciphrchat.app.privacy.PrivacyManager
import org.json.JSONObject
import org.webrtc.*
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.util.Collections
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages real audio-only WebRTC calls with Opus codec, weak-network resilience (DTX/FEC),
 * ephemeral TURN REST credentials, deterministic ICE candidate buffering, bounded reconnection/ICE restart,
 * audio focus, and relay-only ICE privacy mode.
 */
@Singleton
class AudioCallManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val privacyManager: PrivacyManager
) {
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    private val _callState = MutableStateFlow<CallState>(CallState.Idle)
    val callState: StateFlow<CallState> = _callState.asStateFlow()

    private val _outgoingSignals = MutableSharedFlow<CallSignal>(extraBufferCapacity = 64)
    val outgoingSignals: SharedFlow<CallSignal> = _outgoingSignals.asSharedFlow()

    private var timeoutJob: Job? = null
    private var reconnectJob: Job? = null
    private var callDurationJob: Job? = null
    private var statsJob: Job? = null

    private var currentCallId: String? = null
    private var currentContactId: String? = null
    private var currentContactName: String? = null
    private var currentLocalId: String = "self"

    // ICE Candidate buffering & signal idempotency
    private val pendingRemoteCandidates = Collections.synchronizedList(mutableListOf<CallSignal.IceCandidate>())
    private val seenCandidateSignatures = Collections.synchronizedSet(mutableSetOf<String>())
    @Volatile private var isRemoteDescriptionSet = false
    @Volatile private var isLocalDescriptionSet = false

    // Bounded Reconnect
    private var reconnectAttempt = 0
    private val MAX_RECONNECT_ATTEMPTS = 3
    private val RECONNECT_TIMEOUT_MS = 15_000L

    // Audio Focus
    private var audioFocusRequest: AudioFocusRequest? = null
    private var previousAudioMode: Int = AudioManager.MODE_NORMAL
    private var isAudioFocusHeld = false

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
                iceTransportsType = PeerConnection.IceTransportsType.RELAY
            } else {
                iceTransportsType = PeerConnection.IceTransportsType.ALL
            }
        }

        val observer = object : PeerConnection.Observer {
            override fun onIceCandidate(candidate: IceCandidate?) {
                if (candidate == null) return
                val isPrivacyActive = privacyManager.isIpPrivacyEnabled.value
                if (!IpPrivacyPolicy.filterIceCandidate(candidate.sdp, isPrivacyActive)) {
                    return
                }
                val signal = CallSignal.IceCandidate(
                    callId = callId,
                    recipientId = currentContactId ?: "",
                    sdpMid = candidate.sdpMid ?: "",
                    sdpMLineIndex = candidate.sdpMLineIndex,
                    sdpCandidate = candidate.sdp,
                    senderId = currentLocalId
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
                            reconnectAttempt = 0
                            timeoutJob?.cancel()
                            reconnectJob?.cancel()
                            transitionToConnected(callId, contactId, contactName)
                        }
                        PeerConnection.IceConnectionState.DISCONNECTED -> {
                            handleIceDisconnect(callId)
                        }
                        PeerConnection.IceConnectionState.FAILED -> {
                            if (reconnectAttempt < MAX_RECONNECT_ATTEMPTS) {
                                handleIceDisconnect(callId)
                            } else {
                                endCall("Connection failed")
                            }
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

        // Audio constraints: Opus, Echo Cancellation, AGC, Noise Suppression, Highpass Filter
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
        val existing = _callState.value
        if (existing is CallState.OutgoingRinging || existing is CallState.Connecting || existing is CallState.Connected) {
            if (currentContactId == contactId) {
                return currentCallId ?: ""
            }
        }

        cleanupCallResources()

        val callId = UUID.randomUUID().toString()
        currentCallId = callId
        currentContactId = contactId
        currentContactName = contactName
        currentLocalId = localSenderId
        isRemoteDescriptionSet = false
        isLocalDescriptionSet = false
        reconnectAttempt = 0

        _callState.value = CallState.OutgoingRinging(callId, contactId, contactName)
        setupAudio(speaker = false)

        scope.launch {
            val iceServers = getIceServers(localSenderId)
            val pc = createPeerConnection(callId, iceServers)
            if (pc == null) {
                _callState.value = CallState.Failed(callId, "Calling service unavailable (Relay required in privacy mode)")
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
                            isLocalDescriptionSet = true
                            val offerSignal = CallSignal.Offer(
                                callId = callId,
                                sdp = desc.description,
                                senderId = localSenderId,
                                recipientId = contactId
                            )
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
     * Handles incoming call offer from peer with collision (glare) and idempotency handling.
     */
    fun onIncomingOffer(offer: CallSignal.Offer, contactName: String) {
        val current = _callState.value
        if (current !is CallState.Idle && current !is CallState.Ended && current !is CallState.Failed) {
            if (current is CallState.IncomingRinging && current.callId == offer.callId) {
                // Duplicate offer for same incoming call — re-emit Ringing
                _outgoingSignals.tryEmit(CallSignal.Ringing(offer.callId, offer.recipientId, offer.senderId))
                return
            }
            if (current is CallState.OutgoingRinging && offer.senderId == currentContactId) {
                // Call Glare tie-breaker: compare sender IDs lexicographically
                if (currentLocalId < offer.senderId) {
                    // Outgoing call has priority; reject incoming collision
                    _outgoingSignals.tryEmit(CallSignal.Reject(offer.callId, offer.recipientId, offer.senderId, "Glare: Outgoing call priority"))
                    return
                } else {
                    // Incoming call has priority; cancel outgoing and switch to incoming
                    cleanupCallResources()
                }
            } else {
                _outgoingSignals.tryEmit(CallSignal.Reject(offer.callId, offer.recipientId, offer.senderId, "Busy"))
                return
            }
        }

        cleanupCallResources()

        currentCallId = offer.callId
        currentContactId = offer.senderId
        currentContactName = contactName
        currentLocalId = offer.recipientId
        isRemoteDescriptionSet = false
        isLocalDescriptionSet = false
        reconnectAttempt = 0

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
                endCall("Calling service unavailable (Relay required in privacy mode)")
                return@launch
            }

            val remoteDesc = SessionDescription(SessionDescription.Type.OFFER, current.sdpOffer)
            pc.setRemoteDescription(object : SdpObserver {
                override fun onSetSuccess() {
                    isRemoteDescriptionSet = true
                    flushPendingRemoteCandidates()

                    val mediaConstraints = MediaConstraints().apply {
                        mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"))
                        mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveVideo", "false"))
                    }

                    pc.createAnswer(object : SdpObserver {
                        override fun onCreateSuccess(answerDesc: SessionDescription?) {
                            if (answerDesc == null) return
                            pc.setLocalDescription(object : SdpObserver {
                                override fun onSetSuccess() {
                                    isLocalDescriptionSet = true
                                    val answerSignal = CallSignal.Answer(
                                        callId = current.callId,
                                        sdp = answerDesc.description,
                                        senderId = localSenderId,
                                        recipientId = current.contactId
                                    )
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
        if (currentCallId != answer.callId) return
        val current = _callState.value
        if (current is CallState.OutgoingRinging || current is CallState.Connecting || current is CallState.Reconnecting) {
            if (isRemoteDescriptionSet) return // Idempotency check

            timeoutJob?.cancel()
            val pc = peerConnection ?: return
            val remoteDesc = SessionDescription(SessionDescription.Type.ANSWER, answer.sdp)
            pc.setRemoteDescription(object : SdpObserver {
                override fun onSetSuccess() {
                    isRemoteDescriptionSet = true
                    flushPendingRemoteCandidates()
                    if (_callState.value is CallState.OutgoingRinging) {
                        _callState.value = CallState.Connecting(answer.callId, currentContactId ?: "", currentContactName ?: "")
                    }
                }
                override fun onSetFailure(error: String?) {
                    endCall("Failed to set remote answer: $error")
                }
                override fun onCreateSuccess(p0: SessionDescription?) {}
                override fun onCreateFailure(p0: String?) {}
            }, remoteDesc)
        }
    }

    /**
     * Adds received remote ICE candidate with safe candidate buffering and deduplication.
     */
    fun onRemoteIceCandidate(candidateSignal: CallSignal.IceCandidate) {
        if (currentCallId != candidateSignal.callId) return
        val signature = "${candidateSignal.sdpMid}:${candidateSignal.sdpMLineIndex}:${candidateSignal.sdpCandidate}"
        if (!seenCandidateSignatures.add(signature)) {
            return // Duplicate candidate discarded safely
        }

        val pc = peerConnection
        if (pc != null && isRemoteDescriptionSet) {
            val iceCandidate = IceCandidate(
                candidateSignal.sdpMid,
                candidateSignal.sdpMLineIndex,
                candidateSignal.sdpCandidate
            )
            pc.addIceCandidate(iceCandidate)
        } else {
            pendingRemoteCandidates.add(candidateSignal)
        }
    }

    private fun flushPendingRemoteCandidates() {
        val pc = peerConnection ?: return
        val candidatesToFlush = synchronized(pendingRemoteCandidates) {
            val list = pendingRemoteCandidates.toList()
            pendingRemoteCandidates.clear()
            list
        }
        candidatesToFlush.forEach { candidateSignal ->
            val iceCandidate = IceCandidate(
                candidateSignal.sdpMid,
                candidateSignal.sdpMLineIndex,
                candidateSignal.sdpCandidate
            )
            pc.addIceCandidate(iceCandidate)
        }
    }

    /**
     * Handles peer ringing notification.
     */
    fun onCallRinging(ringing: CallSignal.Ringing) {
        if (currentCallId == ringing.callId && _callState.value is CallState.OutgoingRinging) {
            // Peer confirmed ringing
        }
    }

    /**
     * Handles peer rejection or busy signal.
     */
    fun onCallReject(reject: CallSignal.Reject) {
        if (currentCallId == reject.callId && _callState.value !is CallState.Idle && _callState.value !is CallState.Ended) {
            timeoutJob?.cancel()
            reconnectJob?.cancel()
            _callState.value = CallState.Ended(reject.callId, reject.reason, 0L)
            cleanupCallResources()
        }
    }

    /**
     * Handles peer hangup signal.
     */
    fun onCallHangup(hangup: CallSignal.Hangup) {
        if (currentCallId == hangup.callId && _callState.value !is CallState.Idle && _callState.value !is CallState.Ended) {
            timeoutJob?.cancel()
            reconnectJob?.cancel()
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
        reconnectJob?.cancel()
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
        val duration = (_callState.value as? CallState.Connected)?.let {
            (System.currentTimeMillis() - it.connectedAtEpochMs) / 1000L
        } ?: 0L

        timeoutJob?.cancel()
        reconnectJob?.cancel()
        callDurationJob?.cancel()
        _callState.value = CallState.Ended(callId, reason, duration)
        cleanupCallResources()
    }

    private fun handleIceDisconnect(callId: String) {
        val current = _callState.value
        if (current is CallState.Connected || current is CallState.Connecting) {
            val contactId = currentContactId ?: ""
            val contactName = currentContactName ?: ""
            if (reconnectAttempt >= MAX_RECONNECT_ATTEMPTS) {
                endCall("Call disconnected (max reconnect attempts reached)")
                return
            }

            reconnectAttempt++
            _callState.value = CallState.Reconnecting(
                callId = callId,
                contactId = contactId,
                contactName = contactName,
                attempt = reconnectAttempt,
                maxAttempts = MAX_RECONNECT_ATTEMPTS
            )

            triggerIceRestart(callId, contactId)

            reconnectJob?.cancel()
            reconnectJob = scope.launch {
                delay(RECONNECT_TIMEOUT_MS)
                if (_callState.value is CallState.Reconnecting) {
                    endCall("Call disconnected (reconnect timed out)")
                }
            }
        }
    }

    private fun triggerIceRestart(callId: String, contactId: String) {
        val pc = peerConnection ?: return
        val mediaConstraints = MediaConstraints().apply {
            mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"))
            mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveVideo", "false"))
            mandatory.add(MediaConstraints.KeyValuePair("IceRestart", "true"))
        }

        pc.createOffer(object : SdpObserver {
            override fun onCreateSuccess(desc: SessionDescription?) {
                if (desc == null) return
                pc.setLocalDescription(object : SdpObserver {
                    override fun onSetSuccess() {
                        isLocalDescriptionSet = true
                        val offerSignal = CallSignal.Offer(
                            callId = callId,
                            sdp = desc.description,
                            senderId = currentLocalId,
                            recipientId = contactId,
                            isIceRestart = true
                        )
                        _outgoingSignals.tryEmit(offerSignal)
                    }
                    override fun onSetFailure(error: String?) {}
                    override fun onCreateSuccess(p0: SessionDescription?) {}
                    override fun onCreateFailure(p0: String?) {}
                }, desc)
            }
            override fun onCreateFailure(error: String?) {}
            override fun onSetSuccess() {}
            override fun onSetFailure(p0: String?) {}
        }, mediaConstraints)
    }

    private fun transitionToConnected(callId: String, contactId: String, contactName: String) {
        val previousConnectedAt = (_callState.value as? CallState.Connected)?.connectedAtEpochMs
            ?: (_callState.value as? CallState.Reconnecting)?.reconnectStartedEpochMs
            ?: System.currentTimeMillis()

        val connectedState = CallState.Connected(
            callId = callId,
            contactId = contactId,
            contactName = contactName,
            connectedAtEpochMs = previousConnectedAt,
            isMuted = false,
            isSpeakerOn = audioManager.isSpeakerphoneOn
        )
        _callState.value = connectedState

        callDurationJob?.cancel()
        callDurationJob = scope.launch {
            while (_callState.value is CallState.Connected) {
                delay(1000)
            }
        }

        // Safe WebRTC Stats collection
        statsJob?.cancel()
        statsJob = scope.launch {
            while (_callState.value is CallState.Connected) {
                delay(3000)
                peerConnection?.getStats { report ->
                    var rtt = 0L
                    var jitter = 0.0
                    var packetsLost = 0L
                    var localType = ""
                    var remoteType = ""
                    val iceState = peerConnection?.iceConnectionState()?.name ?: "UNKNOWN"

                    for (stats in report.statsMap.values) {
                        when (stats.type) {
                            "candidate-pair" -> {
                                val roundTrip = (stats.members["currentRoundTripTime"] as? Number)?.toDouble()
                                    ?: (stats.members["roundTripTime"] as? Number)?.toDouble()
                                if (roundTrip != null) {
                                    rtt = (roundTrip * 1000).toLong()
                                }
                            }
                            "inbound-rtp" -> {
                                if (stats.members["kind"] == "audio" || stats.members["mediaType"] == "audio") {
                                    jitter = (stats.members["jitter"] as? Number)?.toDouble() ?: jitter
                                    packetsLost = (stats.members["packetsLost"] as? Number)?.toLong() ?: packetsLost
                                }
                            }
                            "local-candidate" -> {
                                localType = stats.members["candidateType"]?.toString() ?: localType
                            }
                            "remote-candidate" -> {
                                remoteType = stats.members["candidateType"]?.toString() ?: remoteType
                            }
                        }
                    }

                    val current = _callState.value
                    if (current is CallState.Connected) {
                        _callState.value = current.copy(
                            diagnostics = CallDiagnostics(
                                rttMs = rtt,
                                jitterMs = jitter,
                                packetsLost = packetsLost,
                                localCandidateType = localType,
                                remoteCandidateType = remoteType,
                                iceConnectionState = iceState
                            )
                        )
                    }
                }
            }
        }
    }

    private fun setupAudio(speaker: Boolean) {
        try {
            if (!isAudioFocusHeld) {
                previousAudioMode = audioManager.mode
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    val playbackAttributes = AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()
                    val focusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
                        .setAudioAttributes(playbackAttributes)
                        .setAcceptsDelayedFocusGain(false)
                        .setOnAudioFocusChangeListener { /* maintain mode */ }
                        .build()
                    audioFocusRequest = focusRequest
                    audioManager.requestAudioFocus(focusRequest)
                } else {
                    @Suppress("DEPRECATION")
                    audioManager.requestAudioFocus(null, AudioManager.STREAM_VOICE_CALL, AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
                }
                isAudioFocusHeld = true
            }
            audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
            audioManager.isMicrophoneMute = false
            audioManager.isSpeakerphoneOn = speaker
        } catch (_: Exception) {}
    }

    private fun cleanupCallResources() {
        try {
            timeoutJob?.cancel()
            timeoutJob = null
            reconnectJob?.cancel()
            reconnectJob = null
            callDurationJob?.cancel()
            callDurationJob = null
            statsJob?.cancel()
            statsJob = null

            if (isAudioFocusHeld) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    audioFocusRequest?.let { audioManager.abandonAudioFocusRequest(it) }
                    audioFocusRequest = null
                } else {
                    @Suppress("DEPRECATION")
                    audioManager.abandonAudioFocus(null)
                }
                isAudioFocusHeld = false
            }

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

            synchronized(pendingRemoteCandidates) {
                pendingRemoteCandidates.clear()
            }
            seenCandidateSignatures.clear()
            isRemoteDescriptionSet = false
            isLocalDescriptionSet = false
            reconnectAttempt = 0
        } catch (_: Exception) {}
    }
}

