# Mini-Plan: Contacts, Presence & Account Management Gaps

_Audited 2026-09-25. Scope: contacts, trust requests, presence, devices, account lifecycle._

## Summary

Jami-kmp implements core account and contact operations (creation, import, device linking, trust requests, contact add/remove, profile updates) with good coverage of the business-logic layer. However, the audit found **5 daemon capabilities never used**, **1 significant architectural gap** in presence subscription lifecycle, and **3 high-value smaller gaps** in contact/account state diagnostics. The reference Android client's coverage is broader: it uses presence notifications live (online-status indicators in conversation list/details), implements proper background-unsubscribe, and offers network connectivity diagnostics. Most gaps are low-priority (audio device enumeration, certificate pinning, connection state queries), but **F3** (presence unsubscribe on background) is documented as an open decision, and **F1** (live presence status display) would improve UX significantly.

## Findings

### F1: No live presence status display — gap — **ALREADY IMPLEMENTED; finding was stale (verified 2026-10-12)**
**Evidence:** 
- `ContactService.kt`: implements `onNewBuddyNotification(accountId, buddyUri, status, lineStatus)` callback (line 439 in DaemonBridge.kt); message is never read or acted upon
- `ContactService.kt:216-222`: `subscribeBuddy()` wires subscriptions but never stores or emits status
- No presence indicators in UI: `ContactDetailsScreen.kt`, `ConversationsViewModel.kt` never display online/offline/away badges
- `Contact.kt` model has no `presenceStatus` or `onlineStatus` field

**Impact:** Users cannot see whether a contact is online before starting a call or conversation. The conversation list shows no online indicators. This is a UX regression vs. the reference app (`jami-client-android` displays presence in `ConversationAdapter` and contact details).

**Daemon support:** `publish(accountId, status, note)` (presencemanager_interface.h:36), `subscribeBuddy(accountId, uri, flag)` (line 38), `NewBuddyNotification` signal (callback: `onNewBuddyNotification` in DaemonCallbacks; line 439 in DaemonBridge.kt). Signal includes `status` (int, likely 0=offline/1=online/2=away) and `lineStatus` (string, e.g., message).

**Suggested fix:** Add `presenceStatus: StateFlow<Int>` to `Contact` model; emit status changes from `onNewBuddyNotification`; wire into `ContactDetailsScreen` (badge) and conversation list row (optional subtle indicator).

---

### F2: Presence unsubscribe on background — open decision (documented) — **DONE (2026-10-28); adopted on both platforms, and the three independent subscribers had to be unified first**
**Evidence:**
- `doc/TODO.md:331-337`: explicitly flagged as an open decision
- `HardwareService.connectivityState` is collected (AccountService.kt:142-148), but never drives presence subscribe/unsubscribe
- No lifecycle observer (e.g., `ProcessLifecycleOwner` / `onPause`/`onResume` equivalent) triggers bulk `subscribeBuddy(..., subscribe=false)` in background
- Reference (`jami-client-ios`): `AppDelegate.swift:340` calls `presenceService.subscribeBuddies(..., subscribe: false)` in `sceneDidEnterBackground`

**Impact:** Presence subscriptions remain active while the app is backgrounded, consuming battery and daemon resources. The reference explicitly unsubscribes; jami-kmp does not. The daemon reference-counts subscriptions (each unsubscribe must pair with a subscribe), so the pattern is safe.

**Daemon support:** `subscribeBuddy(accountId, uri, flag)` (presencemanager_interface.h:38).

**Suggested fix:** Wire lifecycle foreground/background transitions (via `ProcessLifecycleOwner.Lifecycle` on Android, `AppDelegate` callbacks on iOS, `onPause`/`onResume` equivalent on other platforms) into `AccountService` or `ContactService` to call `daemonBridge.subscribeBuddy(accountId, uri, false)` for all tracked contacts on background, and re-subscribe on foreground. See `TODO.md` for the broader context (jami-kmp's daemon stays alive in a foreground service, so the battery impact may be lower than on iOS where the daemon stops; this is the "open decision").

**Resolved (2026-10-28).** Adopted on **both** platforms, not iOS only.

The open decision assumed Android might want to keep presence because the daemon stays alive.
It does stay alive — and that is the argument for suspending, not against it. `setAccountsActive(false)`
deliberately keeps proxy-enabled accounts active (`AccountService.kt:536-546`), so on Android the
subscriptions keep costing DHT traffic for as long as `JamiDaemonService` runs, with nothing on
screen to display the result. The reference Android client reaches the same outcome by a different
route: it deactivates proxy accounts outright in background when push is available
(`JamiApplicationFirebase.kt:183-225`, upstream `a60107b32`), which subsumes presence. jami-kmp has
no such background-deactivation path yet — the comment at `AccountService.kt:418-422` calls it "a
future path" — so presence has to be released on its own.

**What the fix actually required.** Not a lifecycle hook calling `subscribeBuddy(false)` in a loop.
Three owners subscribe independently, each with its own bookkeeping, and the daemon reference-counts
`subscribeBuddy`, so one buddy can hold one to three daemon refs:

| Owner | Bookkeeping | Released when |
|---|---|---|
| `ConversationFacade.subscribedBuddies` | set, `firstTime()` guarded | never |
| `ConversationsViewModel.subscribedBuddies` | set | `onCleared()` |
| `ContactService.activeSubscriptions` | refcounted map | `observeContact` flow completes |

Unsubscribing from any one of them would have dropped a buddy the other two still held, or left
refs behind on resume. So the aggregate count was moved into `ContactService.subscribeBuddy`
(`ContactService.kt:240-270`), which is the one call all three pass through: it refcounts, forwards
only the 0→1 and 1→0 edges to the daemon, and ignores an unsubscribe with no matching subscribe.
The three owners are untouched and keep their own dedupe; it is now harmless redundancy.

`suspendPresence()` / `resumePresence()` then release and restore whatever is referenced. A buddy
first referenced while suspended is subscribed once, on resume; a buddy whose last ref is released
while suspended is not resurrected. Both are idempotent.

**Wiring:** `JamiApplication` observes `ProcessLifecycleOwner` (`onStop`/`onStart`), so a rotation or
a move between activities is not mistaken for a background transition — this needed a new
`androidx.lifecycle:lifecycle-process` dependency. iOS hooks the existing
`IOSApplicationHelper.enterBackground()` / `enterForeground()`, which the Swift `AppDelegate`
already drives. Desktop's bridge is a no-op and macOS does not compile, so neither is wired.

**Not verified on a device.** The Apple half could not be compiled on this Linux host, and neither
platform has been exercised against a live peer — only `:shared:desktopTest`.

---

### F3: No `publish()` for presence status updates — not used — **CLOSED WITHOUT WORK (2026-10-28); no user-facing need, and the finding's signature is wrong**
**Evidence:**
- `publish(accountId, status, note)` (configurationmanager_interface.h:36) never called anywhere in jami-kmp
- `AccountService`, `ContactService`, no reference to setting user's own presence status
- No "Set status" / "Away message" UI in `AccountSettingsScreen` or any other screen
- Reference (`jami-android-client`): `AccountService.kt` does not call `publish` directly either — presence is managed implicitly via account registration state

**Impact:** Users cannot set a custom online status or away message. The daemon likely publishes the user's presence automatically on registration, but there is no UI to set a custom status message or toggle online/offline manually.

**Daemon support:** `publish(accountId, status, note)` (presencemanager_interface.h:36). Function accepts status (int, likely 0=offline/1=online) and optional note (string).

**Suggested fix:** Low priority. If a "Set status" feature is desired, add a UI option in account settings, then call `daemonBridge.publish(accountId, status, note)`. Not present in reference Android app either, so not a functional gap.

**Closed 2026-10-28.** Jami presence is published implicitly on registration; a manual status or
away message is a feature nobody has asked for, and the reference Android client does not have one
either. There is nothing to fix until that feature is wanted.

Two corrections for whoever picks it up later. `publish` **is** in the SWIG bindings
(`JamiService.java:708`) — it is simply not declared in `DaemonBridgeApi`, so adopting it means
adding a bridge method, not working around a missing binding. And its signature is
`publish(String accountID, boolean status, String note)`: the finding's "status (int, likely
0=offline/1=online)" was read off the header and is wrong about the type. Exactly the
header-versus-bindings drift the index warns about.

---

### F4: No `getSubscriptions()` / `setSubscriptions()` for buddy list management — not used — **CLOSED WITHOUT WORK (2026-10-28); per-buddy subscription is the right granularity here**
**Evidence:**
- `getSubscriptions(accountId)` (presencemanager_interface.h:39) never called
- `setSubscriptions(accountId, uris)` (line 40) never called
- No bulk subscription management in jami-kmp
- Each subscription is managed individually via `subscribeBuddy(accountId, uri, flag)`

**Impact:** No way to bulk-import or export the user's presence subscription list. This is a niche feature; most users subscribe to contacts reactively (one at a time) rather than pre-loading a list.

**Daemon support:** `getSubscriptions(accountId)` → list of maps with subscription data; `setSubscriptions(accountId, uris)` → bulk replace the list.

**Suggested fix:** Not needed unless there is a user feature request (e.g., "export my buddy list"). Skip for now.

**Closed 2026-10-28.** Accepted as written: no feature wants a bulk buddy list. The per-buddy path
is also now the load-bearing one — F2 put the aggregate refcount in
`ContactService.subscribeBuddy`, and a `setSubscriptions()` bulk replace would write straight past
it and desynchronise the count from the daemon's. Adopting these would mean teaching the registry
about bulk operations first.

Both are in the SWIG bindings (`JamiService.java:720`, `:724`) but absent from `DaemonBridgeApi`.

---

### F5: No connectivity diagnostics (`getConnectionList`, `getConversationConnectivity`, `getConversationTrackedMembers`) — not used — **PARTLY DONE (2026-10-04)**
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

### F6: Audio/video device list functions never called — not used — **NOT APPLICABLE TO MOBILE (2026-10-04)**
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

### F7: No account credential list fetch/sync — partially used — **CLOSED WITHOUT WORK (2026-10-28); the fetch/sync half is stale, the editing half is SIP-only**
**Evidence:**
- `getCredentials(accountId)` (configurationmanager_interface.h:183) called in `AccountService.kt:482`, returns `List<Map<String, String>>`
- `setCredentials(accountId, credentials)` (line 184) called in `AccountService.kt:489`
- No credential editing UI in `AccountSettingsScreen` (deferred per STATUS.md §108)
- SIP credentials (server, username, password) not displayed or editable

**Impact:** SIP account credential management is incomplete. Users cannot view or edit SIP server, username, password after account creation without modifying the account details directly.

**Daemon support:** Both functions exist and are wired into `DaemonBridgeApi`.

**Suggested fix:** Low priority (SIP is secondary to Jami). If SIP account editing is desired, implement credential list UI in `AccountSettingsScreen` and call `setCredentials()`.

**Closed 2026-10-28, in two halves.**

*Fetch/sync — stale, already implemented.* The title claims there is none. In fact every account
load fetches the credential list and parses it into the domain model:
`AccountService.kt:198-207` calls `daemonBridge.getCredentials(accountId)` and populates
`Account.credentials` as `AccountCredentials` (`Account.kt:308-320`), and `:1215` refreshes it. The
line numbers the finding cites (`AccountService.kt:482`/`:489`) have moved to `:571`/`:578`.

*Editing UI — genuinely absent, and deliberately so.* Nothing under `ui/` mentions credentials at
all. But this is SIP server/username/password editing, and `security-networking.md` closed its
whole SIP account-settings cluster as SIP-only on 2026-10-13 (F1, F13, F14). This belongs with that
cluster, not here, and is deferred on the same grounds: SIP is secondary to Jami and no SIP user
has asked.

---

### F8: No certificate pinning UI — functions exist but no UI — **DONE (2026-10-13)**
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

**Done (2026-10-13).** Built as the plan's own suggestion describes: a Certificates screen under
Account settings → Advanced → Security, listing what is pinned with the daemon's own checks, the
SHA-1 fingerprint in monospace for out-of-band comparison, pin-from-file, unpin, and the trust
decision (verify normally / always allow / block). All nine functions are on `DaemonBridgeApi`
and implemented on Android and Apple.

**Five bugs in the first version, found by testing it on an iOS simulator (fixed 2026-10-14).**
Four of them came from assuming the daemon's shape instead of reading it:

1. **Every certificate showed "Expired".** libjami's `EXPIRED` check key is a misnomer: the
   function behind it is `TlsValidator::notExpired()` (`tlsvalidator.cpp:774`), which returns
   PASSED when the certificate is *valid*. The model read PASSED as expired. Worse, the mistake
   was asserted in a comment and locked in by two tests, so the suite confirmed it.
2. **Each certificate was listed twice, and the rows disagreed about trust status.**
   `CertificateStore` emplaces both the short and the long id, and repeats that up the issuer
   chain (`certstore.cpp:299-302`), so `getPinnedCertificates` returns two ids for a self-signed
   certificate and six for one with a CA. Rows are now collapsed on the SHA-1 fingerprint.
3. **Trust status was set on one id only**, which is why the two rows diverged. It is now applied
   to every id of the certificate.
4. **Unpin always reported failure.** `unpinCertificate` returns the result of deleting
   `certPath_/<id>` (`certstore.cpp:396-402`), and the stored file is named after the *long* id —
   so unpinning by the short id fails, and a path-pinned certificate has no file there at all.
   Now: the path when known, then every id, longest first.
5. **Pins did not survive a relaunch**, because `pinCertificatePath` only fills the daemon's
   in-memory `paths_` map and writes nothing. The UI now reads the file and pins by *content*,
   which takes the `local = true` branch and persists.

Also: the file picker was left on the default `"*/*"` mime type, which greys out every file in
the iOS document picker — the pin flow was unusable on iOS as first shipped.

Two things worth recording. First, the daemon has no "list with details" call, so the list is one
`getCertificateDetails` + `validateCertificate` per id; the pinned set is a handful of entries in
practice. Second, trust status can only be read as "which certificates have status X", so the
per-certificate status is assembled from the ALLOWED and BANNED answers and anything absent is
UNDEFINED. Neither reference client has a certificate UI — libjamiclient has only the
`validateCertificate`/`getCertificateDetails` helpers in `AccountService` — so the screen follows
our own settings patterns rather than resampling one.

---

### F9: No message status query (`getMessageStatus`) — not used — **CLOSED WITHOUT WORK (2026-10-28); the event-driven path is correct and was separately fixed**
**Evidence:**
- `getMessageStatus(id)` (configurationmanager_interface.h:110) never called
- `getMessageStatus(accountId, id)` (line 111) never called
- Message delivery status is inferred from swarm message events (`MessageUpdated`, `MessageReceived`), not polled
- `ChatViewModel` derives `DeliveryStatus` from `Interaction.statusMap` (DISPLAYED/SUCCESS/etc.), not daemon queries

**Impact:** No ability to force a sync of a single message's delivery status. The current event-driven approach is correct for live updates; polling would be redundant.

**Daemon support:** Both function overloads exist.

**Suggested fix:** Not needed. Current implementation is better (event-driven). Skip.

**Closed 2026-10-28.** Accepted as written — polling a status the daemon already pushes would be
redundant. Reinforced since: `messaging-conversations.md` F9 found and fixed a real off-by-one in
the event-driven status mapping on 2026-10-13 (`49fb07a`), so that path is now mapped against the
header rather than guessed. A query API would not have surfaced that bug; reading the header did.

Both overloads are in the SWIG bindings (`JamiService.java:280`, `:284`) but absent from
`DaemonBridgeApi`.

---

### F10: No `monitor(bool continuous)` call for daemon state monitoring — not used — **DONE (2026-10-04)**
**Evidence:**
- `monitor(bool continuous)` (configurationmanager_interface.h:53) never called
- `DaemonBridge` has no `monitor()` method
- Daemon state is monitored via callback events, not polling
- No equivalent in `jami-client-android` either (it uses events, not polling)

**Impact:** No impact. The callback-based model is correct.

**Daemon support:** Function exists but is legacy/optional.

**Suggested fix:** Skip. Not needed with event-driven architecture.

---

### F11: Missing `SubscriptionStateChanged` callback handling — gap — **DONE (2026-10-04)**
**Evidence:**
- Daemon signal `SubscriptionStateChanged(accountId, buddyUri, state)` (presencemanager_interface.h:73-76) has no callback in `DaemonCallbacks`
- No `onSubscriptionStateChanged` method in `DaemonCallbacks` interface
- Presence subscription state changes are never observed by the app

**Impact:** The app cannot react to presence subscription failures (e.g., server denying a subscription). Only `NewBuddyNotification` is handled, which fires only when a buddy's status changes *after* successful subscription.

**Daemon support:** `SubscriptionStateChanged` signal (presencemanager_interface.h:73-76). Payload: `accountId`, `buddyUri`, `state` (int, likely 0=success/1=denied/2=error).

**Suggested fix:** Add `onSubscriptionStateChanged(accountId, buddyUri, state)` to `DaemonCallbacks` interface and implement in all platforms. Wire into presence subscription error handling. Low priority unless subscription failures are common.

---

### F12: Unused presence signals — not handled — **DONE (2026-10-04)**
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

### F13: Trust request accept uses both `conversationUri` (correct) and bare `uri` (potential bug) — minor issue — **INVALID; already correct (verified 2026-10-12)**
**Evidence:**
- `ContactService.kt:792`: `daemonBridge.acceptTrustRequest(accountId, from.uri)` passes the peer URI
- `doc/TODO.md:209`: harness testing found that accepting via peer URI (vs. `conversationUri`) takes the legacy path and silently fails to join the swarm — "confirming the contact but never actually joining the swarm conversation"
- Reference (`jami-client-android`, `PendingRequestsViewModel.kt:137`, `ConversationsViewModel.kt:386`) always uses `conversationUri`, never the peer URI

**Impact:** Trust request acceptance may silently fail to join the swarm on one code path. The newer swarm-based trust system expects the `conversationUri` (a `swarm:<id>` handle), not the bare peer URI. Accepting via peer URI falls back to legacy behavior.

**Daemon support:** `acceptTrustRequest(accountId, from)` (configurationmanager_interface.h:231). Parameter is `from` (the other party's URI); daemon internally maps it to the conversation if it exists. Suggest clarifying the daemon's behavior: does it prefer `swarm:<id>` or handle both?

**Suggested fix:** Trace through `onIncomingTrustRequest` callback in `AccountService.kt` to confirm `conversationUri` is captured. Audit all `acceptTrustRequest` call sites to ensure they pass `conversationUri` (not bare URI) when available. Cross-check against reference Android app (already done in harness, line 209 of TODO.md, but not in the real UI code yet).

---

### F14: No `searchUser()` UI despite daemon support — minor gap — **ALREADY IMPLEMENTED; finding was stale (verified 2026-10-28)**
**Evidence:**
- `searchUser(accountId, query)` (configurationmanager_interface.h:92) is wired in `DaemonBridge`
- `AccountService.kt:634-644` implements `searchUser()`, calls daemon, emits `UserSearchEnded` event
- No UI screen calls `searchUser()` or displays results
- Reference (`jami-client-android`): equivalent is present but not exposed in primary UI (legacy/XMPP feature)

**Impact:** DHT user search (e.g., full-text search across the DHT network) is not accessible to users. Niche feature; most users find contacts via QR code, URI, or name registration lookup.

**Daemon support:** `searchUser(accountId, query)` initiates search; `UserSearchEnded(accountId, state, query, results)` callback returns results.

**Suggested fix:** Low priority. If a "Search all users" feature is desired (niche), add a search screen and wire it. Not blocking.

**Stale — verified against source 2026-10-28.** The claim "No UI screen calls `searchUser()` or
displays results" is untrue. It is wired end to end:

- `NewConversationViewModel.startSearch` calls `accountService.searchUser(accountId, query)` for
  any plain-text query, alongside the nameserver lookup
  (`NewConversationViewModel.kt:246-249`).
- `AccountEvent.UserSearchEnded` is collected at `:86` and handled by `handleSearchResults` (`:372`),
  which maps each result's `id`/`username`/`firstName`/`lastName` into a `ContactItem` and merges it
  into `publicDirectoryResults` without duplicating nameserver hits.
- `SearchScreen` renders that list under its own heading (`SearchScreen.kt:206`), and the same
  ViewModel backs `NewConversationScreen`.

So JAMS/public-directory search is reachable from the search screen, which is more than the
reference client exposes.

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

## Status (2026-10-28) — plan closed

All 14 findings are resolved. One piece of work was done directly (F2); everything else was either
resolved by work under another plan, already implemented when the audit was written, or closed as
not wanted.

| Outcome | Count | Findings |
|---|---|---|
| Done | 5 | F2, F8, F10, F11, F12 |
| Partly done | 1 | F5 |
| Closed without work | 8 | F1 and F14 (already implemented), F13 (invalid), F6 (not applicable to mobile), F3, F4, F7, F9 (not wanted) |

**F2 — presence suspended on background (2026-10-28)** is the only implementation this plan drove.
It also unified the three independent presence subscribers behind one refcount in
`ContactService.subscribeBuddy`; see the finding for the design and the caveats. Not yet exercised
on a device, and the Apple half has not been compiled.

**Three of the five closures were checked against source first, and two findings did not survive
it:** F14 is fully implemented (JAMS search reaches `SearchScreen`), and F7's fetch/sync half has
been implemented all along — only its SIP credential-editing UI is missing, which belongs with
`security-networking.md`'s SIP cluster. F3, F4 and F9 were accepted as written. That is three stale
findings in this one plan (F1, F13, F14) plus one half-stale (F7), out of fourteen.

**A note for the other plans.** `publish`, `getSubscriptions`, `setSubscriptions` and
`getMessageStatus` are all present in the SWIG bindings but absent from `DaemonBridgeApi` — these
findings are "not wanted", not "not available". And F3's cited signature was wrong: `publish` takes
a `boolean` status, not an int. Read the bindings, not just the headers.

## Earlier status (verified 2026-10-12)

This plan was never worked on directly, but five of its findings were resolved by work under
other plans and two were wrong to begin with. Verified against source on 2026-10-12 before
marking:

- **F1 (no live presence display) — stale.** `Contact` has `PresenceStatus`/`isOnline`,
  `ContactService.onPresenceUpdate` sets it and emits `ContactEvent.PresenceUpdated`,
  `ChatViewModel` tracks `isContactOnline`, and `HomeScreen`/`SharePickerScreen` render presence
  dots on conversation rows (`HomeScreen.kt:654`). The claim that "`Contact.kt` has no
  `presenceStatus` field" was already untrue when the audit was written.
- **F11 (`SubscriptionStateChanged`) and F12 (unused presence signals) — done** by the
  notifications plan's Phase 1 work (`8df3fdf`). All four remaining presence callbacks reach
  `ContactService`; an inactive subscription now marks the contact offline rather than leaving a
  stale dot.
- **F13 (trust-request URI) — invalid.** `AccountService.acceptTrustRequest` branches on
  `from.isSwarm` and routes swarm URIs to `acceptConversationRequest`
  (`AccountService.kt:882-884`), and both UI call sites pass `conversationUri`. The
  `ContactService.kt:792` line the finding cites no longer exists.
- **F10 (`monitor()`) — done** by the daemon-audit Phase 1 work (`da5fdd9`): bridged, called from
  the debug screen's "dump daemon state" action.
- **F5 (connectivity diagnostics) — partly done.** `getConnectionList` is bridged and shown in
  the debug screen. `getConversationConnectivity` and `getConversationTrackedMembers` are **not
  in the SWIG bindings**, so they cannot be reached from Kotlin without regenerating the
  interface and rebuilding `libjami-core-jni.so`.
- **F6 (audio device lists) — not applicable to mobile**, for the reason given under the daemon
  audit's F1: routing belongs to `AudioManager`/`AVAudioSession`, which `HardwareService` already
  drives; the daemon's device-index API targets desktop audio backends.

**Genuinely open:** F2 (presence unsubscribe on background — needs a product decision),
F3/F4 (presence publish and subscription lists — unused daemon APIs), F7 (credential list),
F8 (certificate pinning UI, overlaps the security plan's F11), F9 (`getMessageStatus`),
F14 (`searchUser()` UI).

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
