# Mini-Plan: Contacts, Presence & Account Management Gaps

_Audited 2026-09-25. Scope: contacts, trust requests, presence, devices, account lifecycle._

## Summary

Jami-kmp implements core account and contact operations (creation, import, device linking, trust requests, contact add/remove, profile updates) with good coverage of the business-logic layer. However, the audit found **5 daemon capabilities never used**, **1 significant architectural gap** in presence subscription lifecycle, and **3 high-value smaller gaps** in contact/account state diagnostics. The reference Android client's coverage is broader: it uses presence notifications live (online-status indicators in conversation list/details), implements proper background-unsubscribe, and offers network connectivity diagnostics. Most gaps are low-priority (audio device enumeration, certificate pinning, connection state queries), but **F3** (presence unsubscribe on background) is documented as an open decision, and **F1** (live presence status display) would improve UX significantly.

## Findings

### F1: No live presence status display — gap
**Evidence:** 
- `ContactService.kt`: implements `onNewBuddyNotification(accountId, buddyUri, status, lineStatus)` callback (line 439 in DaemonBridge.kt); message is never read or acted upon
- `ContactService.kt:216-222`: `subscribeBuddy()` wires subscriptions but never stores or emits status
- No presence indicators in UI: `ContactDetailsScreen.kt`, `ConversationsViewModel.kt` never display online/offline/away badges
- `Contact.kt` model has no `presenceStatus` or `onlineStatus` field

**Impact:** Users cannot see whether a contact is online before starting a call or conversation. The conversation list shows no online indicators. This is a UX regression vs. the reference app (`jami-client-android` displays presence in `ConversationAdapter` and contact details).

**Daemon support:** `publish(accountId, status, note)` (presencemanager_interface.h:36), `subscribeBuddy(accountId, uri, flag)` (line 38), `NewBuddyNotification` signal (callback: `onNewBuddyNotification` in DaemonCallbacks; line 439 in DaemonBridge.kt). Signal includes `status` (int, likely 0=offline/1=online/2=away) and `lineStatus` (string, e.g., message).

**Suggested fix:** Add `presenceStatus: StateFlow<Int>` to `Contact` model; emit status changes from `onNewBuddyNotification`; wire into `ContactDetailsScreen` (badge) and conversation list row (optional subtle indicator).

---

### F2: Presence unsubscribe on background — open decision (documented)
**Evidence:**
- `doc/TODO.md:331-337`: explicitly flagged as an open decision
- `HardwareService.connectivityState` is collected (AccountService.kt:142-148), but never drives presence subscribe/unsubscribe
- No lifecycle observer (e.g., `ProcessLifecycleOwner` / `onPause`/`onResume` equivalent) triggers bulk `subscribeBuddy(..., subscribe=false)` in background
- Reference (`jami-client-ios`): `AppDelegate.swift:340` calls `presenceService.subscribeBuddies(..., subscribe: false)` in `sceneDidEnterBackground`

**Impact:** Presence subscriptions remain active while the app is backgrounded, consuming battery and daemon resources. The reference explicitly unsubscribes; jami-kmp does not. The daemon reference-counts subscriptions (each unsubscribe must pair with a subscribe), so the pattern is safe.

**Daemon support:** `subscribeBuddy(accountId, uri, flag)` (presencemanager_interface.h:38).

**Suggested fix:** Wire lifecycle foreground/background transitions (via `ProcessLifecycleOwner.Lifecycle` on Android, `AppDelegate` callbacks on iOS, `onPause`/`onResume` equivalent on other platforms) into `AccountService` or `ContactService` to call `daemonBridge.subscribeBuddy(accountId, uri, false)` for all tracked contacts on background, and re-subscribe on foreground. See `TODO.md` for the broader context (jami-kmp's daemon stays alive in a foreground service, so the battery impact may be lower than on iOS where the daemon stops; this is the "open decision").

---

### F3: No `publish()` for presence status updates — not used
**Evidence:**
- `publish(accountId, status, note)` (configurationmanager_interface.h:36) never called anywhere in jami-kmp
- `AccountService`, `ContactService`, no reference to setting user's own presence status
- No "Set status" / "Away message" UI in `AccountSettingsScreen` or any other screen
- Reference (`jami-android-client`): `AccountService.kt` does not call `publish` directly either — presence is managed implicitly via account registration state

**Impact:** Users cannot set a custom online status or away message. The daemon likely publishes the user's presence automatically on registration, but there is no UI to set a custom status message or toggle online/offline manually.

**Daemon support:** `publish(accountId, status, note)` (presencemanager_interface.h:36). Function accepts status (int, likely 0=offline/1=online) and optional note (string).

**Suggested fix:** Low priority. If a "Set status" feature is desired, add a UI option in account settings, then call `daemonBridge.publish(accountId, status, note)`. Not present in reference Android app either, so not a functional gap.

---

### F4: No `getSubscriptions()` / `setSubscriptions()` for buddy list management — not used
**Evidence:**
- `getSubscriptions(accountId)` (presencemanager_interface.h:39) never called
- `setSubscriptions(accountId, uris)` (line 40) never called
- No bulk subscription management in jami-kmp
- Each subscription is managed individually via `subscribeBuddy(accountId, uri, flag)`

**Impact:** No way to bulk-import or export the user's presence subscription list. This is a niche feature; most users subscribe to contacts reactively (one at a time) rather than pre-loading a list.

**Daemon support:** `getSubscriptions(accountId)` → list of maps with subscription data; `setSubscriptions(accountId, uris)` → bulk replace the list.

**Suggested fix:** Not needed unless there is a user feature request (e.g., "export my buddy list"). Skip for now.

---

### F5: No connectivity diagnostics (`getConnectionList`, `getConversationConnectivity`, `getConversationTrackedMembers`) — not used
**Evidence:**
- `getConnectionList(accountId, conversationId)` (configurationmanager_interface.h:54) never called
- `getConversationConnectivity(accountId, conversationId)` (line 56) never called
- `getConversationTrackedMembers(accountId, conversationId)` (line 58) never called
- No "Connection status" or "Peer connectivity" UI
- `CallService`, `ConversationFacade`, no use of these functions

**Impact:** Users cannot see which peers are currently connected or the network path to a conversation. This is useful for debugging multi-device / multi-peer swarm sync issues, but not critical for basic use.

**Daemon support:** All three return `std::vector<std::map<std::string, std::string>>` with connection metadata (peer URI, connection type, address, port, etc.).

**Suggested fix:** Low priority. If detailed connectivity debugging is desired, add a developer/diagnostics screen. Skip for normal users.

---

### F6: Audio/video device list functions never called — not used
**Evidence:**
- `getAudioOutputDeviceList()` (configurationmanager_interface.h:131) never called
- `getAudioInputDeviceList()` (line 135) never called
- `getAudioManager()` (line 161) never called
- `getSupportedAudioManagers()` (line 160) never called
- `getCurrentAudioDevicesIndex()` (line 136) never called
- No "Audio output" / "Microphone" device selector UI
- `HardwareService` manages audio routing via `setAudioOutputDevice` (never called) / `setAudioInputDevice` (never called)

**Impact:** Users cannot select which speaker, microphone, or Bluetooth device to use for calls. The daemon defaults are used. On Android, the audio routing is managed implicitly via `AudioManager`; iOS probably has similar OS-level routing.

**Daemon support:** All six functions documented; return device lists or current selection.

**Suggested fix:** Not critical. Audio device selection is often device-level (OS-managed), not app-level. If a user feature request comes in, add a "Call audio device" selector in `CallSettings` (within account/app settings). Not blocking.

---

### F7: No account credential list fetch/sync — partially used
**Evidence:**
- `getCredentials(accountId)` (configurationmanager_interface.h:183) called in `AccountService.kt:482`, returns `List<Map<String, String>>`
- `setCredentials(accountId, credentials)` (line 184) called in `AccountService.kt:489`
- No credential editing UI in `AccountSettingsScreen` (deferred per STATUS.md §108)
- SIP credentials (server, username, password) not displayed or editable

**Impact:** SIP account credential management is incomplete. Users cannot view or edit SIP server, username, password after account creation without modifying the account details directly.

**Daemon support:** Both functions exist and are wired into `DaemonBridgeApi`.

**Suggested fix:** Low priority (SIP is secondary to Jami). If SIP account editing is desired, implement credential list UI in `AccountSettingsScreen` and call `setCredentials()`.

---

### F8: No certificate pinning UI — functions exist but no UI
**Evidence:**
- `getPinnedCertificates(accountId)` (configurationmanager_interface.h:213) never called
- `pinCertificate(accountId, cert, local)` (line 215) never called
- `unpinCertificate(accountId, certId)` (line 218) never called
- `pinCertificatePath(accountId, path)` (line 220) never called
- `unpinCertificatePath(accountId, path)` (line 221) never called
- No "Pinned Certificates" or "Certificate Pinning" screen in account settings
- SIP/TLS certificate pinning not configurable by users

**Impact:** Users cannot manually pin or revoke server certificates for SIP accounts. Useful for security in high-threat environments, but not mainstream.

**Daemon support:** Five functions present in configurationmanager_interface.h; also `validateCertificate`, `getCertificateDetails`, `setCertificateStatus`, `getCertificatesByStatus`.

**Suggested fix:** Skip for now. Certificate pinning is niche. If a security-conscious user base emerges, add a "Certificate Management" advanced settings screen.

---

### F9: No message status query (`getMessageStatus`) — not used
**Evidence:**
- `getMessageStatus(id)` (configurationmanager_interface.h:110) never called
- `getMessageStatus(accountId, id)` (line 111) never called
- Message delivery status is inferred from swarm message events (`MessageUpdated`, `MessageReceived`), not polled
- `ChatViewModel` derives `DeliveryStatus` from `Interaction.statusMap` (DISPLAYED/SUCCESS/etc.), not daemon queries

**Impact:** No ability to force a sync of a single message's delivery status. The current event-driven approach is correct for live updates; polling would be redundant.

**Daemon support:** Both function overloads exist.

**Suggested fix:** Not needed. Current implementation is better (event-driven). Skip.

---

### F10: No `monitor(bool continuous)` call for daemon state monitoring — not used
**Evidence:**
- `monitor(bool continuous)` (configurationmanager_interface.h:53) never called
- `DaemonBridge` has no `monitor()` method
- Daemon state is monitored via callback events, not polling
- No equivalent in `jami-client-android` either (it uses events, not polling)

**Impact:** No impact. The callback-based model is correct.

**Daemon support:** Function exists but is legacy/optional.

**Suggested fix:** Skip. Not needed with event-driven architecture.

---

### F11: Missing `SubscriptionStateChanged` callback handling — gap
**Evidence:**
- Daemon signal `SubscriptionStateChanged(accountId, buddyUri, state)` (presencemanager_interface.h:73-76) has no callback in `DaemonCallbacks`
- No `onSubscriptionStateChanged` method in `DaemonCallbacks` interface
- Presence subscription state changes are never observed by the app

**Impact:** The app cannot react to presence subscription failures (e.g., server denying a subscription). Only `NewBuddyNotification` is handled, which fires only when a buddy's status changes *after* successful subscription.

**Daemon support:** `SubscriptionStateChanged` signal (presencemanager_interface.h:73-76). Payload: `accountId`, `buddyUri`, `state` (int, likely 0=success/1=denied/2=error).

**Suggested fix:** Add `onSubscriptionStateChanged(accountId, buddyUri, state)` to `DaemonCallbacks` interface and implement in all platforms. Wire into presence subscription error handling. Low priority unless subscription failures are common.

---

### F12: Unused presence signals — not handled
**Evidence:**
- `NewServerSubscriptionRequest(remote)` (presencemanager_interface.h:45-48) no callback
- `ServerError(accountId, error, msg)` (line 50-55) no callback
- `NearbyPeerNotification(accountId, buddyUri, state, displayname)` (line 65-71) no callback
- No corresponding methods in `DaemonCallbacks`
- These signals are server/XMPP-related, not DHT-native (low priority for DHT-only accounts)

**Impact:** Server presence subscription requests are silently ignored. Server errors in presence are not logged. Nearby peer discovery (mDNS / LAN) is not used.

**Daemon support:** Three callback signals defined in presencemanager_interface.h.

**Suggested fix:** Low priority. DHT accounts do not use presence servers; these are for SIP/XMPP interop. If XMPP support is added, implement handlers. Skip for now.

---

### F13: Trust request accept uses both `conversationUri` (correct) and bare `uri` (potential bug) — minor issue
**Evidence:**
- `ContactService.kt:792`: `daemonBridge.acceptTrustRequest(accountId, from.uri)` passes the peer URI
- `doc/TODO.md:209`: harness testing found that accepting via peer URI (vs. `conversationUri`) takes the legacy path and silently fails to join the swarm — "confirming the contact but never actually joining the swarm conversation"
- Reference (`jami-client-android`, `PendingRequestsViewModel.kt:137`, `ConversationsViewModel.kt:386`) always uses `conversationUri`, never the peer URI

**Impact:** Trust request acceptance may silently fail to join the swarm on one code path. The newer swarm-based trust system expects the `conversationUri` (a `swarm:<id>` handle), not the bare peer URI. Accepting via peer URI falls back to legacy behavior.

**Daemon support:** `acceptTrustRequest(accountId, from)` (configurationmanager_interface.h:231). Parameter is `from` (the other party's URI); daemon internally maps it to the conversation if it exists. Suggest clarifying the daemon's behavior: does it prefer `swarm:<id>` or handle both?

**Suggested fix:** Trace through `onIncomingTrustRequest` callback in `AccountService.kt` to confirm `conversationUri` is captured. Audit all `acceptTrustRequest` call sites to ensure they pass `conversationUri` (not bare URI) when available. Cross-check against reference Android app (already done in harness, line 209 of TODO.md, but not in the real UI code yet).

---

### F14: No `searchUser()` UI despite daemon support — minor gap
**Evidence:**
- `searchUser(accountId, query)` (configurationmanager_interface.h:92) is wired in `DaemonBridge`
- `AccountService.kt:634-644` implements `searchUser()`, calls daemon, emits `UserSearchEnded` event
- No UI screen calls `searchUser()` or displays results
- Reference (`jami-client-android`): equivalent is present but not exposed in primary UI (legacy/XMPP feature)

**Impact:** DHT user search (e.g., full-text search across the DHT network) is not accessible to users. Niche feature; most users find contacts via QR code, URI, or name registration lookup.

**Daemon support:** `searchUser(accountId, query)` initiates search; `UserSearchEnded(accountId, state, query, results)` callback returns results.

**Suggested fix:** Low priority. If a "Search all users" feature is desired (niche), add a search screen and wire it. Not blocking.

---

## Cross-Check: Official Android Client Features Not in KMP

Scanning `jami-client-android` for features not yet ported to KMP:

1. **Presence/online status in conversation list** (reference: `ConversationAdapter.kt`, `lastActivity` includes presence) — **not in KMP** (F1)
2. **Presence subscription management screen** — not in either (F2/F4)
3. **Certificate pinning UI** (reference: SIP settings) — **not in KMP** (F8)
4. **SIP credential editing** (reference: SIP account settings) — **not in KMP** (F7)
5. **Device connectivity status** (reference: diagnostics/debug screens, not mainstream) — **not in KMP** (F5)
6. **Audio device selector** (reference: call settings, though often OS-managed) — **not in KMP** (F6)
7. **Multi-device presence** (e.g., showing which device a contact is online from) — **not in either** (daemon/network limitation, not app)
8. **Trust request history** (reference: not found; single-request UI only) — **not in KMP** (correct parity)

**Conclusion**: KMP's contact/presence/account management is **functionally equivalent** to the reference for mainstream features (add, remove, block, device linking, profile, account creation/import). Gaps are in **niche/diagnostic features** (certificate pinning, subscription state, connection diagnostics) and **one UX feature** (live presence status display, F1).

---

## Proposed Plan

### Phase 1: High-impact, low-effort improvements
1. **F1: Add presence status display** — Add `presenceStatus` field to `Contact` model; emit from `onNewBuddyNotification`; show badge in `ContactDetailsScreen` and optional indicator in conversation list. Effort: 1–2 days. Impact: UX improvement.
2. **F13: Audit and fix trust request accept call sites** — Ensure all `acceptTrustRequest` calls pass `conversationUri` when available (not bare URI). Effort: 2–4 hours (grep + audit + 1 test). Impact: correctness (may fix a rare silent-failure bug).

### Phase 2: Complete presence lifecycle (documented open decision)
3. **F2: Implement presence unsubscribe on background** — Wire lifecycle foreground/background transitions into bulk `subscribeBuddy(..., subscribe=false)` calls. Effort: 1–2 days. Impact: battery savings. **Deferred pending: decision on whether jami-kmp's foreground-service model makes this necessary** (see TODO.md §331-337).

### Phase 3: Lower-priority improvements (niche/diagnostic)
4. **F11: Add SubscriptionStateChanged callback** — Complete presence subscription error handling. Effort: 4–6 hours. Impact: edge-case robustness.
5. **F6: Audio device selector** (if user request) — Add call settings UI for audio device selection. Effort: 2–3 days. Impact: niche (OS usually handles this).
6. **F8: Certificate pinning UI** (if user request) — Add advanced SIP settings for certificate management. Effort: 2–3 days. Impact: niche (high-security environments).

### Phase 4: Skip (not blocking, low impact)
- F3 (`publish`): Users cannot set custom status — low priority, reference app doesn't expose this either
- F4 (bulk subscriptions): Niche feature, individual subscriptions work fine
- F5 (connectivity diagnostics): Debug feature, not mainstream
- F7 (credential editing): SIP is secondary; deferrable
- F9, F10, F12, F14: Already explained why skipping is correct

---

## Effort Estimate

- **F1 (presence status)**: 1–2 days → **High value**
- **F13 (trust request audit)**: 2–4 hours → **Correctness**
- **F2 (background unsubscribe)**: 1–2 days (after open decision) → **Battery optimization**
- **F11 (subscription errors)**: 4–6 hours → **Robustness**
- **Other (F3–F12, except F1/F13/F2/F11)**: Skip (niche or correct-as-is)

**Total for Phase 1+2+3**: ~5–7 days, with Phase 2 deferred pending the documented open decision. Phase 1 is recommended now; Phases 2–3 can follow based on priority and user feedback.
