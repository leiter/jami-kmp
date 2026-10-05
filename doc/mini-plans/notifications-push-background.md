# Mini-Plan: Notifications, Push & Background Sync Gaps

_Audited 2026-09-25. Scope: daemon signal coverage, push delivery, background service lifecycle._

## Summary

The jami-kmp daemon bridge (`DaemonBridge.android.kt`) implements only 50% of the signals declared in the SWIG interface files (`configurationmanager.i`, `callmanager.i`, `presencemanager.i`, `conversation.i`, `datatransfer.i`). **16 active signals are silently dropped** or no-op, meaning features that emit them have zero effect in the KMP client (e.g., audio muting UI changes, presence subscription errors, call hold state). Background service lifecycle and push setup are correct; connectivity handling is wired. The Android foreground service types are correctly declared for Android 14+ enforcement. iOS push infrastructure is complete but untested on a Mac.

## Findings

### F1: 13 call/presence/configuration signals silently dropped — gap — **DONE (2026-10-04)**
**Evidence:**
- `callmanager.i` declares `audioMuted`, `videoMuted`, `peerHold`, `connectionUpdate`, `remoteRecordingChanged`, `mediaNegotiationStatus`; none have handlers in `DaemonBridge.createCallCallback()` (lines 821–854)
- `presencemanager.i` declares `newServerSubscriptionRequest`, `serverError`, `subscriptionStateChanged`, `nearbyPeerNotification`; only `newBuddyNotification` has a handler in `DaemonBridge.createPresenceCallback()` (lines 856–860)
- Reference app (`jami-client-android/DaemonService.kt` lines 240–267) implements all of them, forwarding to `CallService.audioMuted()`, `videoMuted()`, `peerHold()`, `connectionUpdate()`, `remoteRecordingChanged()`, `mediaNegotiationStatus()`

**Impact:** Call UI cannot display mute state changes, call hold notifications, or connection status from the peer. Presence subscription errors and SIP presence notifications are never surfaced. Feature parity gap vs. reference app.

---

### F2: 14 configuration signals never forwarded — gap — **PARTLY DONE (2026-10-04): Android wired except certificate signals and audioMeter; iOS/macOS pending**
**Evidence:**
- `configurationmanager.i` declares `volumeChanged`, `historyChanged`, `stunStatusFailure`, `activeCallsChanged`, `errorAlert`, `getHardwareAudioFormat`, `hardwareDecodingChanged`, `hardwareEncodingChanged`, `audioMeter`, `messageSend`, and certificate-related signals (`certificatePinned`, `certificatePathPinned`, `certificateExpired`, `certificateStateChanged`)
- `DaemonBridge.createConfigurationCallback()` implements none of these (only 21 of 37 signals)
- Reference app implements all; e.g., `DaemonService.kt` line 146–148 handles `errorAlert`, line 88–89 handles `volumeChanged`

**Impact:** Volume changes not reflected in UI, STUN failures (NAT traversal errors) silently ignored, certificate pinning/expiry warnings never shown, audio hardware codec warnings never logged.

---

### F3: Presence subscription model incomplete — gap — **DONE (2026-10-04)**
**Evidence:**
- `presencemanager.i` defines 5 presence callbacks; KMP only implements 1 (`newBuddyNotification` at line 857)
- Missing: `newServerSubscriptionRequest` (lines 49, 300), `serverError` (lines 50, 304), `subscriptionStateChanged` (lines 53, 312), `nearbyPeerNotification` (lines 29, 51)
- Reference handles `subscriptionStateChanged` at line 312–314; `newServerSubscriptionRequest` at 300–302; `serverError` at 304–306

**Impact:** SIP presence subscriptions (if enabled) have no visibility into server state. "User offline" notifications only work for one callback type.

---

### F4: NetworkServiceCallback (peer-services) unimplemented — gap — **DONE (2026-10-04) by the daemon-audit Phase 2 work**
**Evidence:**
- `jni_interface.i` exposes `NetworkServiceCallback` with three methods: `peerServicesReceived`, `serviceTunnelOpened`, `serviceTunnelClosed`
- `DaemonBridge.createNetworkServiceCallback()` at line 862–864 is a no-op: `// No-op: jami-kmp does not use the peer-service-discovery/tunnel API yet.`
- Reference app implements all three at `DaemonService.kt` lines 222–232, forwarding to `PeerServicesService`

**Impact:** Peer service discovery (e.g., file transfer tunnels, screen sharing negotiation) is completely disabled. Feature not ported yet.

---

### F5: Conversation error callback missing — gap — **DONE (2026-10-04)**
**Evidence:**
- `conversation.i` declares `onConversationError(accountId, conversationId, code, what)` at lines 37, 109
- `DaemonBridge.createConversationCallback()` (lines 956–1009) does not override it
- Reference app does not explicitly handle it either (no override), but signal is at least declared in SWIG

**Impact:** Conversation sync errors (e.g., permission denied on group add, swarm integrity issues) are silently swallowed. No in-app error indicator to the user.

---

### F6: Certificate management signals absent — gap
**Evidence:**
- `configurationmanager.i` lines 46–49 declare: `certificatePinned`, `certificatePathPinned`, `certificateExpired`, `certificateStateChanged`
- No handlers in `DaemonBridge.createConfigurationCallback()`
- Reference omits these too (no override in `DaemonService.kt`), but they are daemon-side signals that some deployments use for certificate pinning

**Impact:** Custom CA pinning strategies or certificate expiry warnings are not available to the app, even if the daemon emits them.

---

### F7: Call recording state incomplete — gap — **INVALID (2026-10-04): `recordStateChange` has no daemon signal behind it**
**Evidence:**
- `callmanager.i` declares both `recordingStateChanged(callId, code)` and `recordStateChange(callId, state)` at lines 42, 43, 165, 166
- `DaemonBridge.createCallCallback()` only handles `recordingStateChanged` (line 834–836), not `recordStateChange`
- Reference app only handles `recordingStateChanged` at line 165; `recordStateChange` also not overridden
- Likely: `recordingStateChanged` is legacy, `recordStateChange` is newer; daemon may emit both

**Impact:** If daemon emits the newer `recordStateChange` signal, call recording UI does not update. Backward-compatibility cover is missing.

---

### F8: SIP call messaging not wired — gap — **DONE (2026-10-05) by the calling plan's F8 work**
**Evidence:**
- `callmanager.i` line 31 declares `incomingMessage(accountId, callId, from, messages)` for in-call SIP MESSAGE requests
- `DaemonBridge.createCallCallback()` does not override it (line 821–854)
- Reference app does not override either (no in-call SIP messaging in `DaemonCallAndConferenceCallback`)
- Jami swarm uses `swarmMessageReceived` (conversation.i); SIP path uses call-level messages

**Impact:** In-call SIP chat (used in legacy SIP accounts) is not supported. Feature gap for SIP-only deployments.

---

### F9: Audio/video device metrics not exposed — gap
**Evidence:**
- `configurationmanager.i` line 67 declares `audioMeter(id, level)` for real-time audio level reporting
- No handler in `DaemonBridge.createConfigurationCallback()`
- Reference app forwards it at line 293–294, but provides no-op in `DaemonVideoCallback.audioMeter()`

**Impact:** Audio input level meters (VU display during calls) cannot be rendered. Voice activity detection for "speaking" indicators missing.

---

### F10: JamiSyncService background timeout relies on daemon process lifecycle — improvement
**Evidence:**
- `JamiSyncService.kt` lines 69–99: foreground service with timeout mechanism, but relies on daemon being alive
- No connectivity check before timeout removal; if daemon crashes, sync service stops
- No reconnection strategy after daemon restart
- Reference app's `SyncService` has similar structure; architectural pattern

**Impact:** If daemon is killed (low memory, SELinux issue, OOM), the sync service stops but app is unaware. On next user interaction, daemon must be manually restarted. Intermittent sync outages possible.

---

### F11: Callback dispatcher snapshot locking not used in KMP — gap — **INVALID (2026-10-04): already solved by the per-conversation channel dispatcher**
**Evidence:**
- Reference app's `DaemonService.kt` lines 434–438 uses `snapshotConversationCallback()` to hold a lock during callback processing, preventing race conditions when concurrent daemon signals arrive
- KMP's `DaemonBridge.createConversationCallback()` (lines 956–1009) calls `callbacks.onMessageReceived()` etc. directly without locking
- SWIG callbacks can fire concurrently from daemon threads

**Impact:** If two `swarmMessageReceived` signals fire simultaneously (e.g., bulk sync), Kotlin coroutines launched by the callback may interleave, causing message ordering issues or lost updates. Race condition likely but not yet observed.

---

### F12: Connection update signal unused in calls — gap — **DONE (2026-10-04)**
**Evidence:**
- `callmanager.i` line 49 declares `connectionUpdate(id, state)` for ICE connection state reporting
- Reference implements at `DaemonService.kt` line 260–262, forwarding to `CallService.connectionUpdate()`
- KMP does not override it

**Impact:** Call connection state (ICE connected → disconnected) is invisible to the UI. Useful for debugging call quality issues, but not critical for basic call functionality.

---

### F13: Message send logging signal missing — gap — **DONE (2026-10-04)**
**Evidence:**
- `configurationmanager.i` line 68 declares `messageSend(message)` for daemon debug logging
- Reference app forwards at line 198–200 to `HardwareService.logMessage()`
- KMP does not implement it

**Impact:** Daemon debug messages are not captured by the app's logging system. Harder to debug message send issues without console logs.

---

## Proposed Plan

### Phase 1: High-Value, Low-Risk Additions (2–3 days)

**P1a: Implement call mute state callbacks**
- Add overrides for `audioMuted`, `videoMuted` in `createCallCallback()`
- Forward to `callbacks.onAudioMuted()`, `callbacks.onVideoMuted()` in `DaemonCallbacks` interface
- Wire into `CallViewModel` to update in-call UI mute buttons
- Fixes: F1 (partial), enables UI to reflect actual peer mute state

**P1b: Implement presence subscription callbacks**
- Add `newServerSubscriptionRequest`, `serverError`, `subscriptionStateChanged` to `createPresenceCallback()`
- Forward to `DaemonCallbacks` (may be no-ops initially if presence subscription feature is not used)
- Documents API surface even if feature is not utilized

**P1c: Add conversation error callback**
- Override `onConversationError()` in `createConversationCallback()`
- Forward to `callbacks.onConversationError(accountId, conversationId, code, what)`
- Emit snackbar notification to user on error
- Fixes: F5

### Phase 2: Medium-Value, Medium-Risk (1 week)

**P2a: Implement all call-related signals**
- Add `peerHold`, `connectionUpdate`, `remoteRecordingChanged`, `mediaNegotiationStatus` to `createCallCallback()`
- Forward to respective `DaemonCallbacks` methods
- Update `CallViewModel` to display hold state, connection quality indicators, remote recording badge
- Fixes: F1 (remainder), F12

**P2b: Implement configuration signal subset**
- Prioritize: `errorAlert`, `volumeChanged`, `activeCallsChanged`
- Forward to `DaemonCallbacks` and wire into settings/state
- Skip certificate signals (not critical for P2) and `audioMeter` (lower priority)
- Fixes: F2 (partial)

**P2c: Add snapshot locking to conversation callbacks**
- Wrap conversation message/reaction callbacks in a lock (mirror reference app's `snapshotConversationCallback`)
- Define lock in `DaemonBridge` and hold it during callback forwarding
- Eliminates race condition risk
- Fixes: F11

### Phase 3: Future/Lower Priority

**P3a: Peer-services (tunnels, service discovery)**
- Implement `NetworkServiceCallback` to enable peer-to-peer service negotiation
- Requires new service layer (`PeerServicesService`) and UI
- Fixes: F4
- Timeline: Q4 2026+ (depends on feature demand)

**P3b: Daemon lifecycle resilience**
- Add connectivity check before `JamiSyncService` timeout
- Implement graceful degradation if daemon is unresponsive
- Add metrics/telemetry for sync service uptime
- Fixes: F10 (partial)
- Timeline: Q4 2026+

**P3c: SIP call messaging**
- Wire `incomingMessage` callback in call-level context
- Add UI for in-call SIP messages
- Fixes: F8
- Timeline: Defer (low demand; SIP deprecated in favor of swarm)

---

## Effort Estimate

| Phase | Story | Effort | Risk |
|-------|-------|--------|------|
| 1a    | Call mute callbacks | 4 hours | Low |
| 1b    | Presence callbacks (stub) | 2 hours | Low |
| 1c    | Conversation error callback | 3 hours | Low |
| 2a    | All call signals | 6 hours | Medium (complex state) |
| 2b    | Config signal subset | 4 hours | Medium |
| 2c    | Snapshot locking | 2 hours | Low |
| **Total (Phases 1–2)** | — | **21 hours** | — |
| 3a    | Peer-services | 16 hours | High |
| 3b    | Daemon resilience | 8 hours | Medium |
| 3c    | SIP messaging | 8 hours | Medium |

---

## Implementation Status (2026-10-04, branch `docs/mini-plans`)

**Phase 1 and Phase 2 are implemented**, with three corrections to the audit.

Landed:
- **F1 call signals.** `audioMuted`, `videoMuted` and `mediaNegotiationStatus` were already
  declared in `DaemonCallbacks` and consumed by `CallService` — only the Android SWIG callback
  never fired them, so a moderator/peer mute never reached the UI. Now wired. iOS and macOS
  already forwarded the two mute signals through their delegate.
- **F1/F12 remainder.** New `onPeerHold`, `onConnectionUpdate`, `onRemoteRecordingChanged` on
  Android, iOS and macOS. `CallState` gained `isPeerHolding`, `isRemoteRecording` and
  `remoteRecordingBy`; the call overlay shows a hold line and a "<name> recording…" badge.
  `Conference.setParticipantRecording` (a TODO stub) now maintains the recording participant set.
- **F3 presence.** All four remaining presence callbacks reach `ContactService`. A subscription
  going inactive marks the contact offline rather than leaving a stale presence dot.
- **F5 conversation errors.** Forwarded, emitted as `ConversationEvent.ConversationError`, held
  in `ChatState.conversationError` and shown as a snackbar in `ChatScreen`.
- **F2/F13 configuration signals (Android).** `activeCallsChanged` (also declared and consumed
  but never fired), `errorAlert`, `volumeChanged`, `stunStatusFailure`, `historyChanged`,
  `hardwareDecodingChanged`, `hardwareEncodingChanged`, `messageSend`.
- **Not in the audit: `getHardwareAudioFormat`.** The daemon asks the app for the device's
  native sample rate and frames-per-buffer; with no handler it used its own defaults, costing
  latency and resampling on every frame. Implemented from `AudioManager` on Android, mirroring
  `DeviceRuntimeServiceImpl.getHardwareAudioFormat()`.

Corrections to the audit:
- **F11 (snapshot locking) was already fixed and is not a bug.** `DaemonCallbacksImpl` routes
  conversation events through per-`(accountId, conversationId)` FIFO channels with their own
  consumer coroutines (commit `bd66d89`), which preserves per-conversation ordering *and* avoids
  the head-of-line blocking the reference's single lock has. Nothing to do.
- **F7 (`recordStateChange`) does not exist in this daemon.** `callmanager_interface.h` declares
  only `RecordingStateChanged`; `recordStateChange` is a leftover in the generated SWIG
  `Callback.java` with no daemon signal behind it. Wiring it would add a dead override.
- **F2 certificate signals (F6) and `audioMeter` (F9) remain deliberately unwired**, as the plan
  proposed. The diagnostics signals above are Android-only so far: the iOS/macOS delegate has no
  entries for them, and they only log.

**Phase 3 is now mostly closed by work done under other plans** (noted 2026-10-05):

- **F4 (peer services / `NetworkServiceCallback`) — done.** The daemon-audit Phase 2 work
  (`1a3fd28`) bridged all eight NetworkService functions, replaced the deliberately empty
  `createNetworkServiceCallback()` with real forwarding, and added `PeerServicesService`. This
  plan's P3a can be struck; what remains is a peer-services *UI*, which no plan has scoped.
- **F8 (SIP in-call messaging) — done.** The calling plan's F8 work (`cb96545`) wired
  `Callback.incomingMessage` → `DaemonCallbacks.onInCallMessage` → `CallService`, bridged the
  call-level `sendTextMessage`, and added the in-call chat sheet. That is exactly what P3c
  asked for, and it fixed a real bug on the way: the existing send path pointed at the
  account-level API and delivered nowhere.
- **F10 (daemon-lifecycle resilience) — still open**, and still the only Phase 3 item with work
  left in it. Verified 2026-10-05: there is no liveness watchdog anywhere in the app — no
  `isRunning` polling, no restart path — so a daemon killed by the OOM killer or SELinux stops
  `JamiSyncService` without the app noticing.

Follow-up:
- The `JamiBridgeWrapper.h/.mm` additions (call signals, presence, conversation error) need a Mac
  `build-jamibridge.sh` rebuild before they take effect on iOS/macOS; that rebuild also covers the
  moderation passthroughs added for the calling plan.
- Phase 3 (F4 peer-services, F10 daemon-lifecycle resilience, F8 SIP in-call messaging) untouched.

---

## Notes

1. **Push delivery blocker remains unchanged.** The infrastructure (PushServiceManager, FCM wiring, notification routing) is correct, but push never arrives because the public DHT proxy (`dhtproxy.jami.net`) does not send to this app's Firebase sender ID. This is a deployment constraint, not a code bug.

2. **iOS push is complete** (APNs + PushKit token handling, CallKit integration) but **never compiled on a Mac**. iOS targets are skipped on Linux. Recommend a Mac build to catch any native interop issues.

3. **The 16 dropped signals span three categories:**
   - **Call state** (hold, connection, mute, recording, negotiation): Affects in-call UX, but basic audio/video works without them
   - **Presence & subscription** (server state, buddy notifications): Low impact for swarm-first deployments; useful for SIP/legacy
   - **Configuration & debugging** (volume, error alerts, audio levels): Affects settings UI and troubleshooting, not core functionality

4. **Snapshot locking is a concurrency bug** (F11), not a feature gap. Should be treated as a high-priority bug fix even though no production crash has been reported.

5. **The NetworkServiceCallback gap (F4)** means no peer-services tunneling is possible. If file transfer or screen sharing via P2P tunnels is a planned feature, this must be unblocked first.
