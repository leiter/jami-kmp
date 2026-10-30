package net.jami.services

import net.jami.model.CertificateStatus
import net.jami.model.MediaAttribute
import net.jami.model.SwarmMessage

/**
 * Platform-agnostic interface to the Jami daemon.
 *
 * Services and repositories depend on this interface rather than the concrete
 * [DaemonBridge] expect class, enabling [StubDaemonBridge] to be used in tests.
 */
interface DaemonBridgeApi {
    // ==================== Lifecycle ====================
    fun init(callbacks: DaemonCallbacks): Boolean
    fun start(): Boolean
    fun stop()
    fun isRunning(): Boolean

    /**
     * Tell the daemon that the host's network environment has changed (interface up/down,
     * Wi-Fi <-> cellular switch, VPN toggle). The daemon re-evaluates its transports:
     * DHT nodes, ICE/TURN allocations and swarm sync sockets are dropped and rebuilt
     * against the new interface instead of waiting for their own slow internal timeouts.
     *
     * Mirrors `JamiService.connectivityChanged()` in the reference client. Platforms with
     * no live daemon (Desktop/Web stubs) treat this as a no-op.
     */
    fun connectivityChanged()

    // ==================== Account Operations ====================
    fun addAccount(details: Map<String, String>): String
    fun removeAccount(accountId: String)
    fun getAccountDetails(accountId: String): Map<String, String>
    fun setAccountDetails(accountId: String, details: Map<String, String>)
    fun getAccountList(): List<String>
    fun setAccountActive(accountId: String, active: Boolean)
    fun getAccountTemplate(accountType: String): Map<String, String>
    fun getVolatileAccountDetails(accountId: String): Map<String, String>
    fun sendRegister(accountId: String, enable: Boolean)
    fun setAccountsOrder(order: String)
    fun changeAccountPassword(accountId: String, oldPassword: String, newPassword: String): Boolean
    fun exportToFile(accountId: String, path: String, scheme: String, password: String): Boolean

    // ==================== Credentials ====================
    fun getCredentials(accountId: String): List<Map<String, String>>
    fun setCredentials(accountId: String, credentials: List<Map<String, String>>)

    // ==================== Device Management ====================
    fun getKnownRingDevices(accountId: String): Map<String, String>
    fun revokeDevice(accountId: String, deviceId: String, scheme: String, password: String)
    fun setDeviceName(accountId: String, deviceName: String)
    /** Initiate linking a new device using its device-request URI. Returns an operation ID ≥ 0, or negative on error. */
    fun addDevice(accountId: String, uri: String): Long
    /** Confirm the peer identity during the export-side add-device handshake. */
    fun confirmAddDevice(accountId: String, opId: Long): Boolean
    /** Cancel an in-progress add-device operation. */
    fun cancelAddDevice(accountId: String, opId: Long): Boolean
    /** Provide authentication (password) to unlock an account being imported on this device. */
    fun provideAccountAuthentication(accountId: String, password: String, scheme: String): Boolean

    // ==================== Profile ====================
    fun updateProfile(accountId: String, displayName: String, avatar: String, fileType: String, botOwner: String, flag: Int)

    // ==================== Call Operations ====================
    fun placeCall(accountId: String, uri: String, mediaList: List<MediaAttribute>): String
    fun accept(accountId: String, callId: String, mediaList: List<MediaAttribute>)
    fun refuse(accountId: String, callId: String)
    fun hangUp(accountId: String, callId: String)
    fun hold(accountId: String, callId: String)
    fun unhold(accountId: String, callId: String)
    fun resume(accountId: String, callId: String): Boolean
    fun muteLocalMedia(accountId: String, callId: String, mediaType: String, mute: Boolean)

    fun playDtmf(key: String)
    fun muteRingtone(mute: Boolean)
    fun muteCapture(mute: Boolean)
    fun restartAudioLayer()
    fun isCaptureMuted(): Boolean
    fun setNoiseSuppression(enabled: Boolean)

    /**
     * Turn the daemon's RMS metering on or off for one ring buffer, or for all of them when [id]
     * is empty (`RingBufferPool::setAudioMeterState`). While it is on, the daemon emits
     * [DaemonCallbacks.onAudioMeter] roughly every 100 ms per metered buffer, so it is only
     * turned on while something is showing the level.
     *
     * @param id a ring-buffer id: [AudioMeter.CAPTURE_ID] for the microphone, a call id for a
     *   peer's stream, or "" for every buffer.
     */
    fun setAudioMeterState(id: String, enabled: Boolean)

    /**
     * Noise-suppression state as the daemon reports it ("enabled"/"disabled", possibly a mode
     * name on newer daemons). Empty when the platform cannot query it.
     */
    fun getNoiseSuppressState(): String

    /**
     * Automatic gain control, which normalises the microphone level.
     *
     * Note this is *not* echo cancellation: libjami's `setEchoCancellationState` exists in
     * `configurationmanager_interface.h` but is absent from the SWIG bindings, so it cannot be
     * reached from Kotlin on Android today. See [setEchoCancellation].
     */
    fun setAgcState(enabled: Boolean)

    /**
     * Echo cancellation. Unreachable on Android: `setEchoCancellationState` is not in the
     * generated bindings, so implementations log and no-op rather than silently toggling
     * something else. Regenerating the SWIG interface would be needed to wire it.
     */
    fun setEchoCancellation(enabled: Boolean)
    fun transfer(accountId: String, callId: String, to: String): Boolean
    fun attendedTransfer(accountId: String, transferId: String, targetId: String): Boolean
    fun getCallDetails(accountId: String, callId: String): Map<String, String>
    fun toggleRecording(accountId: String, callId: String): Boolean
    fun getIsRecording(accountId: String, callId: String): Boolean

    // ==================== Conference Operations ====================
    fun holdConference(accountId: String, confId: String): Boolean
    fun unholdConference(accountId: String, confId: String): Boolean
    fun resumeConference(accountId: String, confId: String): Boolean
    fun setActiveParticipant(accountId: String, confId: String, callId: String)
    fun setConferenceLayout(accountId: String, confId: String, layout: Int)
    fun hangUpConference(accountId: String, confId: String): Boolean
    fun joinParticipant(accountId: String, selCallId: String, account2Id: String, dragCallId: String): Boolean
    fun addParticipant(accountId: String, callId: String, account2Id: String, confId: String): Boolean

    /**
     * Dial several peers at once and join them into one conference
     * (`Manager::createConfFromParticipantList`). Each entry is `"<peer uri>,<account id>"` —
     * the daemon splits on the first comma. It does nothing at all with fewer than two entries,
     * and only attaches the conference if at least two of the calls were created, so a partial
     * failure leaves ordinary 1:1 calls behind rather than a conference.
     *
     * Returns no call ids, so the calls it creates arrive as `callStateChanged` for ids the
     * client has never seen — see `CallService.createConferenceWith`.
     */
    fun createConfFromParticipantList(accountId: String, participants: List<String>)
    fun addMainParticipant(accountId: String, confId: String): Boolean
    fun detachParticipant(accountId: String, callId: String): Boolean
    fun getParticipantList(accountId: String, confId: String): List<String>
    fun getConferenceDetails(accountId: String, confId: String): Map<String, String>

    // ==================== Conversation Operations ====================
    fun getConversations(accountId: String): List<String>
    fun startConversation(accountId: String): String
    fun sendMessage(accountId: String, conversationId: String, message: String, replyTo: String, flag: Int)
    fun loadConversation(accountId: String, conversationId: String, fromMessage: String, size: Int)
    fun getConversationMembers(accountId: String, conversationId: String): List<Map<String, String>>
    fun getConversationInfo(accountId: String, conversationId: String): Map<String, String>
    fun removeConversation(accountId: String, conversationId: String)
    fun addConversationMember(accountId: String, conversationId: String, uri: String)
    fun removeConversationMember(accountId: String, conversationId: String, uri: String)
    fun updateConversationInfo(accountId: String, conversationId: String, info: Map<String, String>)
    fun getConversationPreferences(accountId: String, conversationId: String): Map<String, String>
    fun setConversationPreferences(accountId: String, conversationId: String, prefs: Map<String, String>)
    fun setMessageDisplayed(accountId: String, conversationUri: String, messageId: String, status: Int)
    fun getActiveCalls(accountId: String, conversationId: String): List<Map<String, String>>

    // ==================== Conversation Requests ====================
    fun getConversationRequests(accountId: String): List<Map<String, String>>
    fun acceptConversationRequest(accountId: String, conversationId: String)
    fun declineConversationRequest(accountId: String, conversationId: String)

    // ==================== Trust Requests ====================
    fun getTrustRequests(accountId: String): List<Map<String, String>>
    fun acceptTrustRequest(accountId: String, uri: String)
    fun discardTrustRequest(accountId: String, uri: String)
    fun sendTrustRequest(accountId: String, uri: String, payload: ByteArray)

    // ==================== Certificate Management ====================
    /**
     * Certificates this account has pinned, as daemon certificate ids (the fingerprint-like id the
     * daemon uses everywhere else in this group of functions).
     */
    fun getPinnedCertificates(accountId: String): List<String>

    /**
     * Pinned certificate ids filtered by trust status — one of the values in
     * [net.jami.model.CertificateStatus]. The daemon's own constants are in `security_const.h`.
     */
    fun getCertificatesByStatus(accountId: String, status: String): List<String>

    /**
     * Everything the daemon knows about a certificate: subject, issuer, fingerprints, validity
     * dates, whether it is a CA. Keys are libjami's `Certificate::DetailsNames`. Empty if the
     * daemon does not know this id.
     */
    fun getCertificateDetails(accountId: String, certId: String): Map<String, String>

    /**
     * Run the daemon's checks over a certificate. Keys are libjami's `Certificate::ChecksNames`
     * (EXPIRED, NOT_REVOKED, KNOWN_AUTHORITY, …), values PASSED/FAILED/UNSUPPORTED.
     */
    fun validateCertificate(accountId: String, certId: String): Map<String, String>

    /** Pin a certificate from raw DER/PEM bytes. Returns the ids the daemon stored. */
    fun pinCertificate(accountId: String, certificate: ByteArray, local: Boolean): List<String>

    /** Pin a certificate, or a whole directory of them, from a file path on this device. */
    fun pinCertificatePath(accountId: String, path: String)

    fun unpinCertificate(accountId: String, certId: String): Boolean

    /** Unpin everything that came from [path]. Returns how many certificates were removed. */
    fun unpinCertificatePath(accountId: String, path: String): Int

    /**
     * Set a pinned certificate's trust status (see [net.jami.model.CertificateStatus]). BANNED
     * refuses connections presenting it; ALLOWED accepts it even when it would not otherwise
     * verify.
     */
    fun setCertificateStatus(accountId: String, certId: String, status: String): Boolean

    // ==================== Contact Operations ====================
    fun addContact(accountId: String, uri: String)
    fun removeContact(accountId: String, uri: String, ban: Boolean)
    fun getContacts(accountId: String): List<Map<String, String>>
    fun getContactDetails(accountId: String, uri: String): Map<String, String>
    fun subscribeBuddy(accountId: String, uri: String, subscribe: Boolean)

    /**
     * Publish a presence note — a short free-text status ("away", "in a meeting") — to every
     * contact of this account.
     *
     * Mirrors libjami's `publish()` (`presencemanager_interface.h:36`). On a Jami account the
     * daemon ignores [status] entirely and forwards only the note
     * (`presencemanager.cpp:79` -> `JamiAccount::sendPresenceNote`), wrapping it in a PIDF
     * document sent over each contact's message channel. It is re-sent when a contact's device
     * reconnects, but it is **not** persisted by the daemon: after a daemon restart the note is
     * gone unless the client publishes it again. [SettingsRepository] owns that replay.
     *
     * The peer's daemon recovers the note with the regex `<basic>([\w\s]+)</basic>`
     * (`jamiaccount.cpp:4177`), so a note carrying anything outside ASCII letters, digits,
     * underscore and whitespace fails to match and the peer is told nothing at all. Callers must
     * validate before publishing; see `PresenceNoteError`.
     *
     * Platforms whose wrapper has no entry point for this (iOS/macOS) log and drop the call.
     */
    fun publishPresence(accountId: String, status: Boolean, note: String)

    // ==================== Name Lookup ====================
    fun lookupName(accountId: String, nameServiceUrl: String, name: String): Boolean
    fun lookupAddress(accountId: String, nameServiceUrl: String, address: String): Boolean
    fun registerName(accountId: String, name: String, scheme: String, password: String): Boolean
    fun searchUser(accountId: String, query: String): Boolean

    // ==================== Messaging ====================
    fun sendTextMessage(accountId: String, callIdOrUri: String, message: String)

    /**
     * Send a text message inside an ongoing call or conference (SIP MESSAGE / in-call chat).
     *
     * Distinct from [sendTextMessage], which sends to a contact URI through the account and so
     * cannot address a call. Received messages arrive as DaemonCallbacks.onInCallMessage.
     *
     * @param callId Call or conference ID
     * @param messages MIME type → body, usually a single "text/plain" entry
     * @param from Our own URI, used as the message author
     * @param isMixed True for a conference, so every participant receives it
     */
    fun sendCallTextMessage(
        accountId: String,
        callId: String,
        messages: Map<String, String>,
        from: String,
        isMixed: Boolean
    )
    fun setIsComposing(accountId: String, uri: String, isComposing: Boolean)

    /**
     * Send a message with multiple mime types to a conversation.
     * Used for geolocation sharing and other special message types.
     *
     * @param accountId The account ID
     * @param conversationId The conversation ID
     * @param messages Map of mime type to content (e.g., "application/geo" -> JSON)
     * @param flag Message flag (0 = normal, 1 = edit/delete, etc.)
     */
    fun sendAccountTextMessage(accountId: String, conversationId: String, messages: Map<String, String>, flag: Int)

    // ==================== File Transfer ====================
    fun sendFile(accountId: String, conversationId: String, filePath: String, displayName: String, parent: String)
    fun downloadFile(accountId: String, conversationId: String, interactionId: String, fileId: String, path: String)
    fun cancelDataTransfer(accountId: String, conversationId: String, fileId: String)
    fun fileTransferInfo(accountId: String, conversationId: String, fileId: String): FileTransferInfo?

    // ==================== Codec Operations ====================
    fun getCodecList(): List<Long>
    fun getActiveCodecList(accountId: String): List<Long>
    fun setActiveCodecList(accountId: String, codecList: List<Long>)
    fun getCodecDetails(accountId: String, codecId: Long): Map<String, String>

    // ==================== Peer Services & Tunnels ====================
    /**
     * Announce a service this device offers to its peers (id assigned by the daemon).
     *
     * @param details Service description: `name`, `description`, `scheme`, `port`, …
     * @return The new service's id, or an empty string on failure.
     */
    fun addExposedService(accountId: String, details: Map<String, String>): String

    /** Update an exposed service in place; [details] must carry its `id`. */
    fun updateExposedService(accountId: String, details: Map<String, String>): Boolean

    /** Stop announcing an exposed service. */
    fun removeExposedService(accountId: String, serviceId: String): Boolean

    /** Services this account currently announces. */
    fun getExposedServices(accountId: String): List<Map<String, String>>

    /**
     * Ask a peer which services it offers. Answered asynchronously by
     * DaemonCallbacks.onPeerServicesReceived, correlated by the returned request id.
     */
    fun queryPeerServices(accountId: String, peerUri: String): Long

    /**
     * Open a tunnel to one of a peer's services, forwarding it to a local port.
     *
     * @param localPort Desired local port, or 0 to let the daemon choose.
     * @return The tunnel id, or an empty string if the tunnel could not be opened.
     */
    fun openServiceTunnel(
        accountId: String,
        peerUri: String,
        deviceId: String,
        serviceId: String,
        serviceName: String,
        localPort: Int
    ): String

    /** Close a tunnel previously opened with [openServiceTunnel]. */
    fun closeServiceTunnel(accountId: String, tunnelId: String): Boolean

    /** Tunnels currently open for an account. */
    fun getActiveTunnels(accountId: String): List<Map<String, String>>

    // ==================== Daemon Settings & Diagnostics ====================
    /**
     * Directory the daemon writes call recordings into (daemon-global).
     * Empty when the platform cannot report it.
     */
    fun getRecordPath(): String

    /** Change the directory the daemon writes call recordings into. */
    fun setRecordPath(path: String)

    /**
     * Call-history retention in days (daemon-global, not per account).
     * 0 means "keep forever"; the daemon prunes older entries on start.
     */
    fun getHistoryLimit(): Int

    /** Set the call-history retention in days; 0 keeps everything. */
    fun setHistoryLimit(days: Int)

    /** How long an outgoing call rings before the daemon gives up, in seconds. */
    fun getRingingTimeout(): Int

    /** Set the outgoing-call ringing timeout, in seconds. */
    fun setRingingTimeout(seconds: Int)

    /**
     * Ask the daemon to dump its internal state (connections, channels, sockets) to its own log.
     * With [continuous] = true it keeps dumping on every change. The output arrives through the
     * daemon's log, i.e. DaemonCallbacks.onDaemonLogMessage and the platform log.
     */
    fun monitor(continuous: Boolean)

    /**
     * Active peer connections for an account, optionally narrowed to one conversation.
     * Each entry describes a device connection (peer, device, status, channels).
     */
    fun getConnectionList(accountId: String, conversationId: String = ""): List<Map<String, String>>

    /**
     * Number of commits in a conversation, walking back from [fromId] (empty = the newest commit)
     * and stopping at [toId] (empty = the first commit).
     *
     * Note what [authorUri] does here, because the name suggests the opposite: in the daemon's
     * fast-log path it is not a filter but a **boundary** — the walk stops at the first commit by
     * that author (`conversationrepository.cpp:2385-2392`). So passing one's own URI counts what
     * has happened since one last posted.
     *
     * The count covers commits of every kind — messages, member events, votes, profile updates —
     * since the fast log does not parse content and so cannot filter by type.
     */
    fun countInteractions(
        accountId: String,
        conversationId: String,
        toId: String = "",
        fromId: String = "",
        authorUri: String = "",
    ): Int

    /**
     * Drop the daemon's in-memory copy of a conversation's history (message list, pending
     * edits/reactions and per-member delivery status).
     *
     * This frees memory; it does not delete anything. For a swarm the commits live in the
     * conversation's git repository, so the next load reads them back from disk.
     */
    fun clearCache(accountId: String, conversationId: String)

    // ==================== Search & History ====================
    fun searchConversation(accountId: String, conversationId: String, author: String, lastId: String, query: String, type: String, after: Long, before: Long, maxResult: Long, flag: Int): Long
    fun loadSwarmUntil(accountId: String, conversationId: String, fromMessage: String, toMessage: String): Long

    // ==================== Push Notifications ====================
    fun setPushNotificationToken(token: String)
    fun setPushNotificationConfig(config: Map<String, String>)
    fun pushNotificationReceived(from: String, data: Map<String, String>)

    // ==================== Video Device Management ====================
    /**
     * Register a video capture device with the daemon.
     * Called when a camera becomes available.
     */
    fun addVideoDevice(deviceId: String)

    /**
     * Unregister a video capture device from the daemon.
     * Called when a camera is disconnected or unavailable.
     */
    fun removeVideoDevice(deviceId: String)

    /**
     * Set the default video capture device.
     */
    fun setDefaultDevice(deviceId: String)

    /**
     * Set the device orientation for video rotation correction.
     *
     * @param deviceId Camera device ID
     * @param rotation Rotation in degrees (0, 90, 180, 270)
     */
    fun setDeviceOrientation(deviceId: String, rotation: Int)

    /**
     * Apply camera settings to a device.
     *
     * @param deviceId Camera device ID
     * @param settings Map of setting key-value pairs
     */
    fun applySettings(deviceId: String, settings: Map<String, String>)

    // ==================== Video Frame Capture ====================
    /**
     * Send a raw video frame to the daemon for encoding.
     * Used when software encoding is needed.
     *
     * @param uri Video input URI (e.g., "camera://0")
     * @param data Raw frame data (typically NV21 or YUV format)
     * @param rotation Frame rotation in degrees
     */
    fun captureVideoFrame(uri: String, data: ByteArray, rotation: Int)

    /**
     * Send an encoded video packet to the daemon.
     * Used with hardware encoding (MediaCodec on Android).
     *
     * @param uri Video input URI
     * @param data Encoded packet data
     * @param size Packet size in bytes
     * @param offset Offset into the data buffer
     * @param isKeyFrame Whether this is a keyframe
     * @param timestamp Presentation timestamp in microseconds
     * @param rotation Frame rotation in degrees
     */
    fun captureVideoPacket(
        uri: String,
        data: Any,
        size: Int,
        offset: Int,
        isKeyFrame: Boolean,
        timestamp: Long,
        rotation: Int
    )

    // ==================== Native Window Management ====================
    /**
     * Acquire a native window for video rendering.
     *
     * @param surface Platform-specific surface (SurfaceHolder on Android)
     * @return Native window handle, or 0 on failure
     */
    fun acquireNativeWindow(surface: Any): Long

    /**
     * Release a native window.
     *
     * @param windowId Native window handle from acquireNativeWindow
     */
    fun releaseNativeWindow(windowId: Long)

    /**
     * Set the geometry of a native window.
     *
     * @param windowId Native window handle
     * @param width Window width in pixels
     * @param height Window height in pixels
     */
    fun setNativeWindowGeometry(windowId: Long, width: Int, height: Int)

    // ==================== Video Callback Registration ====================
    /**
     * Register for video callbacks from a specific sink.
     *
     * @param id Video sink identifier
     * @param windowId Native window handle to render to
     * @return true if registration succeeded
     */
    fun registerVideoCallback(id: String, windowId: Long): Boolean

    /**
     * Unregister video callbacks for a sink.
     *
     * @param id Video sink identifier
     * @param windowId Native window handle
     */
    fun unregisterVideoCallback(id: String, windowId: Long)

    // ==================== Video Input Switching ====================
    /**
     * Switch the video input source for an active call.
     *
     * @param accountId Account ID
     * @param callId Call ID
     * @param uri New video input URI (e.g., "camera://1" or "camera://desktop")
     */
    fun switchVideoInput(accountId: String, callId: String, uri: String)

    /**
     * Request a media change for an active call (renegotiates the media set with the peer).
     * Used for screen sharing — changes the video source without adding a new call leg.
     *
     * @param accountId Account ID
     * @param callId Call/conference ID
     * @param mediaList New media attributes list (each entry is a String→String map)
     */
    fun requestMediaChange(accountId: String, callId: String, mediaList: List<Map<String, String>>)

    /**
     * Answer an incoming media-change request from the peer (or the daemon).
     * Called in response to the onMediaChangeRequested callback to confirm or
     * adjust the proposed media set and let the daemon complete the renegotiation.
     *
     * @param accountId Account ID
     * @param callId    Call ID the request applies to
     * @param mediaList Accepted/adjusted media list (each entry is a String→String map)
     */
    fun answerMediaChangeRequest(accountId: String, callId: String, mediaList: List<Map<String, String>>)

    // ==================== Conference Participant Controls ====================
    /**
     * Mute audio for all participants in a conference.
     *
     * The daemon has no "mute all" primitive: callers should prefer iterating the
     * conference participants and calling [muteStream] for each one (which is what
     * CallService.muteAllParticipants does). This method is kept for platforms whose
     * bridge offers a coarse equivalent.
     *
     * @param accountId Account ID
     * @param confId Conference ID
     */
    fun muteAllParticipants(accountId: String, confId: String)

    /**
     * Lock or unlock a conference.
     *
     * @param accountId Account ID
     * @param confId Conference ID
     * @param locked true to lock, false to unlock
     */
    fun setConferenceLocked(accountId: String, confId: String, locked: Boolean)

    /**
     * Mute audio for a specific participant in a conference.
     *
     * @param accountId Account ID
     * @param confId Conference ID
     * @param participantId Participant ID
     */
    fun muteParticipantAudio(accountId: String, confId: String, participantId: String)

    /**
     * Unmute audio for a specific participant in a conference.
     *
     * @param accountId Account ID
     * @param confId Conference ID
     * @param participantId Participant ID
     */
    fun unmuteParticipantAudio(accountId: String, confId: String, participantId: String)

    /**
     * Disable video for a specific participant in a conference.
     *
     * @param accountId Account ID
     * @param confId Conference ID
     * @param participantId Participant ID
     */
    fun disableParticipantVideo(accountId: String, confId: String, participantId: String)

    /**
     * Enable video for a specific participant in a conference.
     *
     * @param accountId Account ID
     * @param confId Conference ID
     * @param participantId Participant ID
     */
    fun enableParticipantVideo(accountId: String, confId: String, participantId: String)

    /**
     * Mute or unmute one media stream of a conference participant (moderator only).
     *
     * This is the modern per-stream replacement for the deprecated muteParticipant API.
     * The daemon resolves the target by (accountUri, deviceId) pair, so [deviceId] must be
     * the participant's device as reported in the conference info — an empty device only
     * matches when the daemon can infer a single call for that URI.
     *
     * @param accountId Account ID
     * @param confId Conference ID
     * @param peerUri Participant account URI (without the "jami:" scheme)
     * @param deviceId Participant device ID (from Conference.ParticipantInfo.device)
     * @param streamId Stream (sink) ID, or empty for the participant's default stream
     * @param state true to mute, false to unmute
     */
    fun muteStream(accountId: String, confId: String, peerUri: String, deviceId: String, streamId: String, state: Boolean)

    /**
     * Set or clear a participant stream as the conference's active (focused) stream.
     *
     * Modern replacement for [setActiveParticipant], which only takes a call ID.
     *
     * @param accountId Account ID
     * @param confId Conference ID
     * @param peerUri Participant account URI
     * @param deviceId Participant device ID
     * @param streamId Stream (sink) ID to focus
     * @param state true to make the stream active, false to clear it
     */
    fun setActiveStream(accountId: String, confId: String, peerUri: String, deviceId: String, streamId: String, state: Boolean)

    /**
     * Eject a single participant from a conference (moderator only).
     *
     * @param accountId Account ID
     * @param confId Conference ID
     * @param peerUri Participant account URI
     * @param deviceId Participant device ID, or empty for all of that peer's devices
     */
    fun hangupParticipant(accountId: String, confId: String, peerUri: String, deviceId: String)

    /**
     * Raise or lower a participant's hand in a conference.
     *
     * Participants may only raise their own hand; moderators may lower anyone's.
     *
     * @param accountId Account ID
     * @param confId Conference ID
     * @param peerUri Participant account URI
     * @param deviceId Participant device ID
     * @param state true to raise, false to lower
     */
    fun raiseHand(accountId: String, confId: String, peerUri: String, deviceId: String, state: Boolean)

    /**
     * Grant or revoke moderator rights for a conference participant (moderator only).
     *
     * @param accountId Account ID
     * @param confId Conference ID
     * @param peerUri Participant account URI
     * @param state true to promote to moderator, false to demote
     */
    fun setModerator(accountId: String, confId: String, peerUri: String, state: Boolean)
}

/**
 * Platform-specific daemon bridge (JNI, cinterop, or REST).
 *
 * Each platform provides its own implementation:
 * - Android/Desktop: JNI via SWIG-generated bindings
 * - iOS/macOS: Kotlin/Native cinterop to libjami C headers
 * - Web: REST/WebSocket bridge to a daemon server
 */
expect class DaemonBridge : DaemonBridgeApi

/**
 * File transfer information from the daemon.
 */
/** One in-call message handed to the daemon, recorded by [StubDaemonBridge] for tests. */
data class CallMessageSend(
    val accountId: String,
    val callId: String,
    val messages: Map<String, String>,
    val from: String,
    val isMixed: Boolean,
)

/** Ring-buffer ids used with [DaemonBridgeApi.setAudioMeterState]. */
object AudioMeter {
    /**
     * The audio layer's own buffer, which is where captured microphone audio lands
     * (`RingBufferPool::DEFAULT_ID` in the daemon). Metering it gives the local input level.
     */
    const val CAPTURE_ID = "audiolayer_id"
}

data class FileTransferInfo(
    val path: String,
    val totalSize: Long,
    val bytesProgress: Long
)

/**
 * Callback interface for daemon events.
 * Implementations convert these callbacks to Kotlin Flow emissions.
 */
interface DaemonCallbacks {
    // ==================== Account Callbacks ====================
    fun onAccountsChanged()
    fun onAccountDetailsChanged(accountId: String, details: Map<String, String>)
    fun onRegistrationStateChanged(accountId: String, state: String, code: Int, detail: String)
    fun onVolatileAccountDetailsChanged(accountId: String, details: Map<String, String>)
    fun onKnownDevicesChanged(accountId: String, devices: Map<String, String>)
    fun onDeviceRevocationEnded(accountId: String, deviceId: String, state: Int)
    fun onAddDeviceStateChanged(accountId: String, opId: Long, state: Int, details: Map<String, String>)
    fun onMigrationEnded(accountId: String, state: String)
    fun onAccountProfileReceived(accountId: String, name: String, photo: String)

    // ==================== Call Callbacks ====================
    fun onCallStateChanged(accountId: String, callId: String, state: String, code: Int)
    fun onIncomingCall(accountId: String, callId: String, from: String)
    fun onIncomingCallWithMedia(accountId: String, callId: String, from: String, mediaList: List<Map<String, String>>)
    fun onMediaChangeRequested(accountId: String, callId: String, mediaList: List<Map<String, String>>)
    fun onAudioMuted(callId: String, muted: Boolean)
    fun onVideoMuted(callId: String, muted: Boolean)
    fun onRecordingStateChanged(callId: String, recording: Boolean)
    fun onMediaNegotiationStatus(callId: String, event: String, mediaList: List<Map<String, String>>)
    /** The peer put the call on hold (or took it off hold). */
    fun onPeerHold(callId: String, holding: Boolean)
    /** ICE/connection state for a call or a device connection: 0 = connected, 1/2 = closed. */
    fun onConnectionUpdate(id: String, state: Int)
    /** A conference participant started or stopped recording the call. */
    fun onRemoteRecordingChanged(callId: String, peerUri: String, recording: Boolean)
    fun onConferenceCreated(accountId: String, conversationId: String, confId: String)
    fun onConferenceChanged(accountId: String, confId: String, state: String)
    fun onConferenceRemoved(accountId: String, confId: String)
    fun onConferenceInfoUpdated(confId: String, info: List<Map<String, String>>)

    // ==================== Conversation Callbacks ====================
    fun onConversationReady(accountId: String, conversationId: String)
    fun onConversationRemoved(accountId: String, conversationId: String)
    fun onConversationRequestReceived(accountId: String, conversationId: String, metadata: Map<String, String>)
    fun onConversationRequestDeclined(accountId: String, conversationId: String)
    fun onConversationMemberEvent(accountId: String, conversationId: String, memberId: String, event: Int)
    fun onMessageReceived(accountId: String, conversationId: String, message: SwarmMessage)
    fun onMessageUpdated(accountId: String, conversationId: String, message: SwarmMessage)
    fun onMessagesFound(messageId: Int, accountId: String, conversationId: String, messages: List<Map<String, String>>)
    fun onSwarmLoaded(id: Long, accountId: String, conversationId: String, messages: List<SwarmMessage>)
    fun onConversationProfileUpdated(accountId: String, conversationId: String, profile: Map<String, String>)
    fun onConversationPreferencesUpdated(accountId: String, conversationId: String, preferences: Map<String, String>)
    fun onReactionAdded(accountId: String, conversationId: String, messageId: String, reaction: Map<String, String>)
    fun onReactionRemoved(accountId: String, conversationId: String, messageId: String, reactionId: String)
    fun onActiveCallsChanged(accountId: String, conversationId: String, activeCalls: List<Map<String, String>>)
    /** The daemon failed to sync or operate on a conversation (swarm error). */
    fun onConversationError(accountId: String, conversationId: String, code: Long, message: String)

    // ==================== Presence Callbacks ====================
    fun onNewBuddyNotification(accountId: String, buddyUri: String, status: Int, lineStatus: String)
    /** A remote peer asked to subscribe to our presence (SIP presence server flow). */
    fun onNewServerSubscriptionRequest(remoteUri: String)
    /** The presence server reported an error for this account. */
    fun onPresenceServerError(accountId: String, error: String, message: String)
    /** Our subscription to a buddy's presence changed state (0 = inactive, 1 = active). */
    fun onPresenceSubscriptionStateChanged(accountId: String, buddyUri: String, state: Int)
    /** A peer was seen on the local network (nearby-peer discovery). */
    fun onNearbyPeerNotification(accountId: String, peerId: String, state: Int, displayName: String)

    // ==================== Contact Callbacks ====================
    fun onContactAdded(accountId: String, uri: String, confirmed: Boolean)
    fun onContactRemoved(accountId: String, uri: String, banned: Boolean)
    fun onIncomingTrustRequest(accountId: String, conversationId: String, from: String, payload: ByteArray, receiveTime: Long)
    fun onProfileReceived(accountId: String, peerId: String, vcardPath: String)

    // ==================== Message Callbacks ====================
    fun onIncomingAccountMessage(accountId: String, messageId: String?, callId: String?, from: String, messages: Map<String, String>)
    fun onAccountMessageStatusChanged(accountId: String, conversationId: String, messageId: String, contactId: String, status: Int)
    fun onComposingStatusChanged(accountId: String, conversationId: String, contactUri: String, status: Int)

    // ==================== Name Service Callbacks ====================
    fun onNameRegistrationEnded(accountId: String, state: Int, name: String)
    fun onRegisteredNameFound(accountId: String, state: Int, address: String, name: String, query: String = "")
    fun onUserSearchEnded(accountId: String, state: Int, query: String, results: List<Map<String, String>>)

    // ==================== Data Transfer Callbacks ====================
    fun onDataTransferEvent(accountId: String, conversationId: String, interactionId: String, fileId: String, eventCode: Int)

    /**
     * A text message arrived inside a call or conference (in-call chat / SIP MESSAGE).
     *
     * @param messages MIME type → body
     */
    fun onInCallMessage(accountId: String, callId: String, from: String, messages: Map<String, String>)

    // ==================== Peer Services Callbacks ====================
    /**
     * A peer answered a [DaemonBridgeApi.queryPeerServices] request.
     *
     * @param status 0 = ok, 1 = no devices, 2 = unreachable, 3 = timeout, 4 = internal error
     * @param servicesJson JSON array of `{id, name, description, scheme, device}` objects
     */
    fun onPeerServicesReceived(requestId: Long, accountId: String, peerId: String, status: Int, servicesJson: String)

    /** A service tunnel is up and forwarding to [localPort] on this device. */
    fun onServiceTunnelOpened(accountId: String, tunnelId: String, localPort: Int)

    /** A service tunnel was closed, by either side. */
    fun onServiceTunnelClosed(accountId: String, tunnelId: String, reason: String)

    // ==================== Certificate Callbacks ====================
    /** A certificate was pinned, by us or by the daemon accepting a peer's. */
    fun onCertificatePinned(certId: String)

    /** A path was pinned; [certIds] are the certificates found there. */
    fun onCertificatePathPinned(path: String, certIds: List<String>)

    /**
     * A pinned certificate is past its validity date. The daemon keeps it, but connections
     * relying on it will fail verification.
     */
    fun onCertificateExpired(certId: String)

    /** A certificate's trust status changed (see [net.jami.model.CertificateStatus]). */
    fun onCertificateStateChanged(accountId: String, certId: String, state: String)

    // ==================== Diagnostics Callbacks ====================
    /** Daemon-level error alert (see libjami Alert codes). */
    fun onErrorAlert(alert: Int)
    /** An audio device's volume was changed by the daemon. */
    fun onVolumeChanged(device: String, value: Int)

    /**
     * RMS level of one ring buffer while metering is on. [id] is the buffer, [level] is linear
     * RMS in 0..1 (the daemon's `rmsLevel_ / RMS_SIGNAL_INTERVAL`, not dB).
     */
    fun onAudioMeter(id: String, level: Float)
    /** STUN resolution failed for this account — NAT traversal may be degraded. */
    fun onStunStatusFailure(accountId: String)
    /** The daemon's call history file changed. */
    fun onHistoryChanged()
    /** Hardware-accelerated video decoding was enabled/disabled by the daemon. */
    fun onHardwareDecodingChanged(enabled: Boolean)
    /** Hardware-accelerated video encoding was enabled/disabled by the daemon. */
    fun onHardwareEncodingChanged(enabled: Boolean)
    /** A log line emitted by the daemon itself. */
    fun onDaemonLogMessage(message: String)
}

/**
 * No-op stub implementation of [DaemonBridgeApi] for use in tests.
 *
 * Controllable fields allow tests to set up specific scenarios:
 *
 * ```kotlin
 * val stub = StubDaemonBridge()
 * stub.accountList = listOf("acc1", "acc2")
 * stub.accountDetails["acc1"] = mapOf("Account.displayName" to "Alice")
 * ```
 */
class StubDaemonBridge : DaemonBridgeApi {
    // Controllable test state
    var accountIds: List<String> = emptyList()
    var accountDetails: MutableMap<String, Map<String, String>> = mutableMapOf()
    var volatileAccountDetails: MutableMap<String, Map<String, String>> = mutableMapOf()
    var accountTemplate: Map<String, String> = emptyMap()
    var conversations: MutableMap<String, List<String>> = mutableMapOf()
    var conversationMembers: MutableMap<String, List<Map<String, String>>> = mutableMapOf()
    var conversationInfo: MutableMap<String, Map<String, String>> = mutableMapOf()
    // Daemon-side conversation preferences, keyed "accountId:conversationId"
    var conversationPreferences: MutableMap<String, Map<String, String>> = mutableMapOf()
    var contacts: MutableMap<String, List<Map<String, String>>> = mutableMapOf()
    var codecs: List<Long> = emptyList()
    var addAccountResult: String = ""
    var placeCallResult: String = ""
    var startConversationResult: String = ""
    var running: Boolean = false
    // Daemon lifecycle controls for tests
    var initSucceeds: Boolean = true
    var startSucceeds: Boolean = true
    var initThrows: Boolean = false
    var initCalls: Int = 0
    var startCalls: Int = 0
    // Named to avoid colliding with the getHistoryLimit()/setHistoryLimit() overrides below:
    // a `var historyLimit` would generate the same JVM signatures.
    var historyLimitDays: Int = 0
    var ringingTimeoutSeconds: Int = 30
    var connectionList: List<Map<String, String>> = emptyList()
    // Named to avoid colliding with the getNoiseSuppressState() override's JVM signature.
    // Named to avoid colliding with the getRecordPath()/setRecordPath() overrides' JVM signatures.
    var recordPathValue: String = ""
    var noiseSuppressStateValue: String = "enabled"
    var agcEnabled: Boolean = false
    val echoCancellationCalls: MutableList<Boolean> = mutableListOf()
    val exposedServices: MutableMap<String, Map<String, String>> = mutableMapOf()
    var lastPeerServicesRequestId: Long = 0
    var openTunnelResult: String = "tunnel1"
    val closedTunnels: MutableList<String> = mutableListOf()
    var activeTunnels: List<Map<String, String>> = emptyList()
    val sentCallMessages: MutableList<CallMessageSend> = mutableListOf()
    val monitorCalls: MutableList<Boolean> = mutableListOf()

    /** accountId -> certId -> details, as getCertificateDetails() would report them. */
    val certificates: MutableMap<String, MutableMap<String, Map<String, String>>> = mutableMapOf()
    /** accountId -> certId -> trust status. Absent means UNDEFINED. */
    val certificateStatuses: MutableMap<String, MutableMap<String, String>> = mutableMapOf()
    /** certId -> what validateCertificate() reports. */
    val certificateChecks: MutableMap<String, Map<String, String>> = mutableMapOf()
    /** Recorded pinCertificatePath() calls as (accountId, path). */
    val pinnedCertificatePaths: MutableList<Pair<String, String>> = mutableListOf()

    /** What countInteractions() reports, keyed "accountId:conversationId". */
    val interactionCounts: MutableMap<String, Int> = mutableMapOf()
    /** Recorded clearCache() calls as (accountId, conversationId). */
    val clearedCaches: MutableList<Pair<String, String>> = mutableListOf()

    // Configurable lookup results: name -> LookupState (0=Success/taken, 1=Invalid, 2=NotFound/available, 3=NetworkError)
    var lookupNameResults: MutableMap<String, Int> = mutableMapOf()
    // Callback for when lookupName is called - set this to accountService.onRegisteredNameFound in tests
    var onLookupNameCallback: ((accountId: String, state: Int, address: String, name: String, query: String) -> Unit)? = null
    /** fileId -> what fileTransferInfo() reports. */
    var fileTransferInfos: MutableMap<String, FileTransferInfo> = mutableMapOf()

    /** Recorded sendMessage() calls (edits/deletes use flag 1, reactions flag 2). */
    data class SentMessage(val accountId: String, val conversationId: String, val message: String, val replyTo: String, val flag: Int)
    val sentMessages: MutableList<SentMessage> = mutableListOf()
    /** Recorded sendFile() calls; `parent` is the reply-to message id, empty when not a reply. */
    data class SentFile(
        val accountId: String, val conversationId: String, val filePath: String,
        val displayName: String, val parent: String,
    )
    val sentFiles: MutableList<SentFile> = mutableListOf()

    /** Call id -> the details the daemon would report (e.g. Call.KEY_PEER_NUMBER). */
    val callDetails: MutableMap<String, Map<String, String>> = mutableMapOf()

    /** Recorded addConversationMember() calls as (conversationId, memberUri). */
    val addedConversationMembers: MutableList<Pair<String, String>> = mutableListOf()

    /** Pending conversation requests the daemon would report, as {"id", "from"} maps. */
    val conversationRequests: MutableList<Map<String, String>> = mutableListOf()

    /** Recorded createConfFromParticipantList() calls. */
    val conferencesCreatedFromList: MutableList<Pair<String, List<String>>> = mutableListOf()

    /** Ring-buffer id -> whether metering was last turned on for it. */
    val audioMeterStates: MutableMap<String, Boolean> = mutableMapOf()

    /** Recorded downloadFile() calls as (conversationId, interactionId, fileId). */
    val downloadedFiles: MutableList<Triple<String, String, String>> = mutableListOf()

    override fun init(callbacks: DaemonCallbacks): Boolean {
        initCalls++
        if (initThrows) throw IllegalStateException("native library not loaded")
        return initSucceeds
    }
    override fun start(): Boolean {
        startCalls++
        if (!startSucceeds) return false
        running = true
        return true
    }
    override fun stop() { running = false }
    override fun isRunning(): Boolean = running
    override fun connectivityChanged() {}

    override fun addAccount(details: Map<String, String>): String = addAccountResult
    override fun removeAccount(accountId: String) {}
    override fun getAccountDetails(accountId: String): Map<String, String> = accountDetails[accountId] ?: emptyMap()
    override fun setAccountDetails(accountId: String, details: Map<String, String>) { accountDetails[accountId] = details }
    override fun getAccountList(): List<String> = accountIds
    override fun setAccountActive(accountId: String, active: Boolean) {}
    override fun getAccountTemplate(accountType: String): Map<String, String> = accountTemplate
    override fun getVolatileAccountDetails(accountId: String): Map<String, String> = volatileAccountDetails[accountId] ?: emptyMap()
    override fun sendRegister(accountId: String, enable: Boolean) {}
    override fun setAccountsOrder(order: String) {}
    override fun changeAccountPassword(accountId: String, oldPassword: String, newPassword: String): Boolean = true
    override fun exportToFile(accountId: String, path: String, scheme: String, password: String): Boolean = true

    override fun getCredentials(accountId: String): List<Map<String, String>> = emptyList()
    override fun setCredentials(accountId: String, credentials: List<Map<String, String>>) {}

    override fun getKnownRingDevices(accountId: String): Map<String, String> = emptyMap()
    override fun revokeDevice(accountId: String, deviceId: String, scheme: String, password: String) {}
    override fun setDeviceName(accountId: String, deviceName: String) {}
    override fun addDevice(accountId: String, uri: String): Long = 0L
    override fun confirmAddDevice(accountId: String, opId: Long): Boolean = true
    override fun cancelAddDevice(accountId: String, opId: Long): Boolean = true
    override fun provideAccountAuthentication(accountId: String, password: String, scheme: String): Boolean = true

    override fun updateProfile(accountId: String, displayName: String, avatar: String, fileType: String, botOwner: String, flag: Int) {}

    override fun placeCall(accountId: String, uri: String, mediaList: List<MediaAttribute>): String = placeCallResult
    override fun accept(accountId: String, callId: String, mediaList: List<MediaAttribute>) {}
    override fun refuse(accountId: String, callId: String) {}
    override fun hangUp(accountId: String, callId: String) {}
    override fun hold(accountId: String, callId: String) {}
    override fun unhold(accountId: String, callId: String) {}
    override fun resume(accountId: String, callId: String): Boolean = true
    override fun muteLocalMedia(accountId: String, callId: String, mediaType: String, mute: Boolean) {}
    override fun playDtmf(key: String) {}
    override fun muteRingtone(mute: Boolean) {}
    override fun setAudioMeterState(id: String, enabled: Boolean) {
        audioMeterStates[id] = enabled
    }
    override fun muteCapture(mute: Boolean) {}
    override fun isCaptureMuted(): Boolean = false
    override fun restartAudioLayer() {}
    override fun setNoiseSuppression(enabled: Boolean) {
        noiseSuppressStateValue = if (enabled) "enabled" else "disabled"
    }
    override fun getNoiseSuppressState(): String = noiseSuppressStateValue
    override fun setAgcState(enabled: Boolean) { agcEnabled = enabled }
    override fun setEchoCancellation(enabled: Boolean) { echoCancellationCalls += enabled }

    override fun addExposedService(accountId: String, details: Map<String, String>): String {
        val id = "service${exposedServices.size + 1}"
        exposedServices[id] = details + ("id" to id)
        return id
    }
    override fun updateExposedService(accountId: String, details: Map<String, String>): Boolean {
        val id = details["id"] ?: return false
        if (!exposedServices.containsKey(id)) return false
        exposedServices[id] = details
        return true
    }
    override fun removeExposedService(accountId: String, serviceId: String): Boolean =
        exposedServices.remove(serviceId) != null
    override fun getExposedServices(accountId: String): List<Map<String, String>> =
        exposedServices.values.toList()
    override fun queryPeerServices(accountId: String, peerUri: String): Long = ++lastPeerServicesRequestId
    override fun openServiceTunnel(
        accountId: String,
        peerUri: String,
        deviceId: String,
        serviceId: String,
        serviceName: String,
        localPort: Int
    ): String = openTunnelResult
    override fun closeServiceTunnel(accountId: String, tunnelId: String): Boolean {
        closedTunnels += tunnelId
        return true
    }
    override fun getActiveTunnels(accountId: String): List<Map<String, String>> = activeTunnels
    override fun transfer(accountId: String, callId: String, to: String): Boolean = true
    override fun attendedTransfer(accountId: String, transferId: String, targetId: String): Boolean = true
    override fun getCallDetails(accountId: String, callId: String): Map<String, String> =
        callDetails[callId] ?: emptyMap()
    override fun toggleRecording(accountId: String, callId: String): Boolean = false
    override fun getIsRecording(accountId: String, callId: String): Boolean = false

    override fun holdConference(accountId: String, confId: String): Boolean = true
    override fun unholdConference(accountId: String, confId: String): Boolean = true
    override fun resumeConference(accountId: String, confId: String): Boolean = true
    override fun setActiveParticipant(accountId: String, confId: String, callId: String) {}
    override fun setConferenceLayout(accountId: String, confId: String, layout: Int) {}
    override fun hangUpConference(accountId: String, confId: String): Boolean = true
    override fun joinParticipant(accountId: String, selCallId: String, account2Id: String, dragCallId: String): Boolean = true
    override fun addParticipant(accountId: String, callId: String, account2Id: String, confId: String): Boolean = true
    override fun createConfFromParticipantList(accountId: String, participants: List<String>) {
        conferencesCreatedFromList.add(accountId to participants)
    }
    override fun addMainParticipant(accountId: String, confId: String): Boolean = true
    override fun detachParticipant(accountId: String, callId: String): Boolean = true
    override fun getParticipantList(accountId: String, confId: String): List<String> = emptyList()
    override fun getConferenceDetails(accountId: String, confId: String): Map<String, String> = emptyMap()

    override fun getConversations(accountId: String): List<String> = conversations[accountId] ?: emptyList()
    override fun startConversation(accountId: String): String = startConversationResult
    override fun sendMessage(accountId: String, conversationId: String, message: String, replyTo: String, flag: Int) {
        sentMessages.add(SentMessage(accountId, conversationId, message, replyTo, flag))
    }
    override fun loadConversation(accountId: String, conversationId: String, fromMessage: String, size: Int) {}
    override fun getConversationMembers(accountId: String, conversationId: String): List<Map<String, String>> = conversationMembers[conversationId] ?: emptyList()
    override fun getConversationInfo(accountId: String, conversationId: String): Map<String, String> = conversationInfo[conversationId] ?: emptyMap()
    override fun removeConversation(accountId: String, conversationId: String) {}
    override fun countInteractions(
        accountId: String,
        conversationId: String,
        toId: String,
        fromId: String,
        authorUri: String,
    ): Int = interactionCounts["$accountId:$conversationId"] ?: 0

    override fun clearCache(accountId: String, conversationId: String) {
        clearedCaches += accountId to conversationId
    }

    override fun addConversationMember(accountId: String, conversationId: String, uri: String) {
        addedConversationMembers.add(conversationId to uri)
    }
    override fun removeConversationMember(accountId: String, conversationId: String, uri: String) {}
    override fun updateConversationInfo(accountId: String, conversationId: String, info: Map<String, String>) {}
    override fun getConversationPreferences(accountId: String, conversationId: String): Map<String, String> =
        conversationPreferences["$accountId:$conversationId"] ?: emptyMap()
    override fun setConversationPreferences(accountId: String, conversationId: String, prefs: Map<String, String>) {
        conversationPreferences["$accountId:$conversationId"] = prefs
    }
    override fun setMessageDisplayed(accountId: String, conversationUri: String, messageId: String, status: Int) {}
    override fun getActiveCalls(accountId: String, conversationId: String): List<Map<String, String>> = emptyList()

    override fun getConversationRequests(accountId: String): List<Map<String, String>> = conversationRequests.toList()
    override fun acceptConversationRequest(accountId: String, conversationId: String) {}
    override fun declineConversationRequest(accountId: String, conversationId: String) {}

    override fun getTrustRequests(accountId: String): List<Map<String, String>> = emptyList()
    override fun acceptTrustRequest(accountId: String, uri: String) {}
    override fun discardTrustRequest(accountId: String, uri: String) {}
    override fun sendTrustRequest(accountId: String, uri: String, payload: ByteArray) {}

    // ==================== Certificate Management ====================
    override fun getPinnedCertificates(accountId: String): List<String> =
        certificates[accountId]?.keys?.toList() ?: emptyList()

    override fun getCertificatesByStatus(accountId: String, status: String): List<String> =
        certificates[accountId]?.keys?.filter {
            (certificateStatuses[accountId]?.get(it) ?: CertificateStatus.UNDEFINED) == status
        } ?: emptyList()

    override fun getCertificateDetails(accountId: String, certId: String): Map<String, String> =
        certificates[accountId]?.get(certId) ?: emptyMap()

    override fun validateCertificate(accountId: String, certId: String): Map<String, String> =
        certificateChecks[certId] ?: emptyMap()

    override fun pinCertificate(accountId: String, certificate: ByteArray, local: Boolean): List<String> {
        val certId = "cert-${certificate.contentHashCode()}"
        certificates.getOrPut(accountId) { mutableMapOf() }[certId] = emptyMap()
        return listOf(certId)
    }

    override fun pinCertificatePath(accountId: String, path: String) {
        pinnedCertificatePaths += accountId to path
    }

    override fun unpinCertificate(accountId: String, certId: String): Boolean =
        certificates[accountId]?.remove(certId) != null

    override fun unpinCertificatePath(accountId: String, path: String): Int {
        val removed = pinnedCertificatePaths.count { it == accountId to path }
        pinnedCertificatePaths.removeAll { it == accountId to path }
        return removed
    }

    override fun setCertificateStatus(accountId: String, certId: String, status: String): Boolean {
        if (certificates[accountId]?.containsKey(certId) != true) return false
        certificateStatuses.getOrPut(accountId) { mutableMapOf() }[certId] = status
        return true
    }

    override fun addContact(accountId: String, uri: String) {}
    override fun removeContact(accountId: String, uri: String, ban: Boolean) {}
    override fun getContacts(accountId: String): List<Map<String, String>> = contacts[accountId] ?: emptyList()
    override fun getContactDetails(accountId: String, uri: String): Map<String, String> = emptyMap()
    /** Every presence call that actually reached the "daemon", in order. */
    val subscribeBuddyCalls = mutableListOf<Triple<String, String, Boolean>>()
    override fun subscribeBuddy(accountId: String, uri: String, subscribe: Boolean) {
        subscribeBuddyCalls += Triple(accountId, uri, subscribe)
    }

    /** Every publishPresence() call that reached the "daemon", in order. */
    val publishedPresenceNotes = mutableListOf<Triple<String, Boolean, String>>()
    override fun publishPresence(accountId: String, status: Boolean, note: String) {
        publishedPresenceNotes += Triple(accountId, status, note)
    }

    override fun lookupName(accountId: String, nameServiceUrl: String, name: String): Boolean {
        val state = lookupNameResults[name] ?: 2  // Default to NotFound (available)
        val address = if (state == 0) "0x${name.hashCode().toString(16)}" else ""
        onLookupNameCallback?.invoke(accountId, state, address, name, name)
        return true
    }
    override fun lookupAddress(accountId: String, nameServiceUrl: String, address: String): Boolean = true
    override fun registerName(accountId: String, name: String, scheme: String, password: String): Boolean = true
    override fun searchUser(accountId: String, query: String): Boolean = true
    override fun getRecordPath(): String = recordPathValue
    override fun setRecordPath(path: String) { recordPathValue = path }
    override fun getHistoryLimit(): Int = historyLimitDays
    override fun setHistoryLimit(days: Int) { historyLimitDays = days }
    override fun getRingingTimeout(): Int = ringingTimeoutSeconds
    override fun setRingingTimeout(seconds: Int) { ringingTimeoutSeconds = seconds }
    override fun monitor(continuous: Boolean) { monitorCalls += continuous }
    override fun getConnectionList(accountId: String, conversationId: String): List<Map<String, String>> =
        connectionList

    override fun sendTextMessage(accountId: String, callIdOrUri: String, message: String) {}
    override fun sendCallTextMessage(
        accountId: String,
        callId: String,
        messages: Map<String, String>,
        from: String,
        isMixed: Boolean
    ) {
        sentCallMessages += CallMessageSend(accountId, callId, messages, from, isMixed)
    }
    override fun setIsComposing(accountId: String, uri: String, isComposing: Boolean) {}
    override fun sendAccountTextMessage(accountId: String, conversationId: String, messages: Map<String, String>, flag: Int) {}

    override fun sendFile(accountId: String, conversationId: String, filePath: String, displayName: String, parent: String) {
        sentFiles.add(SentFile(accountId, conversationId, filePath, displayName, parent))
    }
    override fun downloadFile(accountId: String, conversationId: String, interactionId: String, fileId: String, path: String) {
        downloadedFiles.add(Triple(conversationId, interactionId, fileId))
    }
    override fun cancelDataTransfer(accountId: String, conversationId: String, fileId: String) {}
    override fun fileTransferInfo(accountId: String, conversationId: String, fileId: String): FileTransferInfo? =
        fileTransferInfos[fileId]

    override fun getCodecList(): List<Long> = codecs
    override fun getActiveCodecList(accountId: String): List<Long> = codecs
    override fun setActiveCodecList(accountId: String, codecList: List<Long>) {}
    override fun getCodecDetails(accountId: String, codecId: Long): Map<String, String> = emptyMap()

    private var nextTaskId: Long = 1L
    override fun searchConversation(accountId: String, conversationId: String, author: String, lastId: String, query: String, type: String, after: Long, before: Long, maxResult: Long, flag: Int): Long = nextTaskId++
    /** toMessage of every loadSwarmUntil call, in order. */
    val loadSwarmUntilRequests = mutableListOf<String>()
    override fun loadSwarmUntil(accountId: String, conversationId: String, fromMessage: String, toMessage: String): Long {
        loadSwarmUntilRequests.add(toMessage)
        return nextTaskId++
    }

    override fun setPushNotificationToken(token: String) {}
    override fun setPushNotificationConfig(config: Map<String, String>) {}
    override fun pushNotificationReceived(from: String, data: Map<String, String>) {}

    // Video stubs
    override fun addVideoDevice(deviceId: String) {}
    override fun removeVideoDevice(deviceId: String) {}
    override fun setDefaultDevice(deviceId: String) {}
    override fun setDeviceOrientation(deviceId: String, rotation: Int) {}
    override fun applySettings(deviceId: String, settings: Map<String, String>) {}
    override fun captureVideoFrame(uri: String, data: ByteArray, rotation: Int) {}
    override fun captureVideoPacket(uri: String, data: Any, size: Int, offset: Int, isKeyFrame: Boolean, timestamp: Long, rotation: Int) {}
    override fun acquireNativeWindow(surface: Any): Long = 0L
    override fun releaseNativeWindow(windowId: Long) {}
    override fun setNativeWindowGeometry(windowId: Long, width: Int, height: Int) {}
    override fun registerVideoCallback(id: String, windowId: Long): Boolean = false
    override fun unregisterVideoCallback(id: String, windowId: Long) {}
    override fun switchVideoInput(accountId: String, callId: String, uri: String) {}
    override fun requestMediaChange(accountId: String, callId: String, mediaList: List<Map<String, String>>) {}
    override fun answerMediaChangeRequest(accountId: String, callId: String, mediaList: List<Map<String, String>>) {}

    override fun muteAllParticipants(accountId: String, confId: String) {}
    override fun setConferenceLocked(accountId: String, confId: String, locked: Boolean) {}
    override fun muteParticipantAudio(accountId: String, confId: String, participantId: String) {}
    override fun unmuteParticipantAudio(accountId: String, confId: String, participantId: String) {}
    override fun disableParticipantVideo(accountId: String, confId: String, participantId: String) {}
    override fun enableParticipantVideo(accountId: String, confId: String, participantId: String) {}
    override fun muteStream(accountId: String, confId: String, peerUri: String, deviceId: String, streamId: String, state: Boolean) {}
    override fun setActiveStream(accountId: String, confId: String, peerUri: String, deviceId: String, streamId: String, state: Boolean) {}
    override fun hangupParticipant(accountId: String, confId: String, peerUri: String, deviceId: String) {}
    override fun raiseHand(accountId: String, confId: String, peerUri: String, deviceId: String, state: Boolean) {}
    override fun setModerator(accountId: String, confId: String, peerUri: String, state: Boolean) {}
}
