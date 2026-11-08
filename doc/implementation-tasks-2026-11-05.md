# Implementation tasks — assembled 2026-11-05

Collected from `STATUS.md`, `CLAUDE.md`, `doc/TODO.md`, `doc/KNOWN-ISSUES.md`,
`doc/status-note-open-ends.md`, `doc/mini-plans/`, `doc/apple-bridge-rebuild.md`,
`docs/refine-video-call.md` and `doc/gap-analysis-medium-opportunities.md`.
Each task names the file or doc that carries its evidence; nothing here was re-derived
against source, so premises older than 2026-10 should be checked before coding (the
mini-plans roll-up records four findings whose evidence had gone stale).

Current HEAD when assembled: `989ba1b` "Apply three settings that were only ever stored".
A1 and A2 were closed on 2026-11-05, A3, B2 and B3 on 2026-11-06.

---

## A. Defects with a known fix

- **A1 — ~~`Account.getByContact` throws when the account holds a group~~.** Already fixed in
  `69ab199`; `doc/KNOWN-ISSUES.md` still said "not yet done" and was corrected 2026-11-05. Both
  walks in `model/Account.kt` use `contactOrNull`, and `AccountGroupLookupTest` now also covers
  the `pending` walk (the second changed line, untested until then). **No work left.**

- **A2 — ~~screen sharing on Android 14+ has no `mediaProjection` foreground service~~.**
  Fixed 2026-11-05. Premise confirmed against source first (no permission, no service with the
  type, and `MainActivity.kt:98` redeeming the consent token straight out of `onActivityResult`).
  Fixed with a dedicated `net.jami.services.ScreenShareService` rather than by adding the type to
  `CallNotificationService` — that service starts for every call with no capture session to back
  the type with, as its own manifest comment already argued. **A device check is still owed on
  both the defect and the fix.**

- **A3 — ~~`AccountSettingsViewModel.loadAccount()` constructs a fresh state~~.**
  Fixed 2026-11-06. Premise confirmed against source first: it listed 15 of the state's 22
  fields, so `linkDeviceState` and the six `registerName*` fields were reset on every
  `AccountEvent.DetailsChanged` — the register-username dialog closed itself mid-typing. Now a
  `copy()`, and the status-note fields no longer need carrying through by hand. Regression test:
  `AccountSettingsViewModelTest.loadAccountDoesNotCloseTheRegisterNameDialog`.
  (`doc/status-note-open-ends.md` open end 3)

## B. Settings stored but applied nowhere

Static evidence from `doc/manual-test-plan-2026-09-10.md` B10–B14; B13/B15/B16 were closed
on 2026-10-31.

- **B1 (B10/B11) — ~~notification sound and vibration toggles do nothing~~.** Fixed 2026-11-08.
  Per-setting channel ids, not recreation: a channel recreated under an id it has held before
  comes back with its *old* settings, which is how the platform preserves a user's own edits —
  so delete-and-recreate could never have worked either, and the ringtone setting was broken
  the same way (only the first ringtone a device ever saw was used). `NotificationChannelIds`
  (commonMain, so it is testable without a device) derives an id per settings combination;
  `ensureChannelVariant()` creates the matching channel on demand and deletes the stale
  siblings, including the legacy un-suffixed one, so system settings keeps showing one entry
  per channel. Messages, requests and calls are variant channels now and left
  `createNotificationChannels()`; the builder path stays for pre-O, where it is the only place
  the settings can be applied.

  The `getSoundUri()` ambiguity is now `NotificationGuard.notificationSound()`, returning
  `Silent` / `Default` / `Custom(uri)`. The old accessor is kept and documented as ambiguous.
  That ambiguity had a second victim: iOS decided a notification's **sound** by reading
  `shouldVibrate()`, so the sound toggle did nothing there and the vibration toggle silenced
  notifications — vibration is not a notification's to control on iOS. Four call sites now go
  through one `applySound()`.

  Nine tests: four in `NotificationGuardTest` for the three sound cases and off-beats-custom,
  five in the new `NotificationChannelIdsTest` for id uniqueness, stability and sibling
  matching. **Device check owed:** that a channel really goes silent, and that the ringtone
  now changes.

- **B2 (B12) — ~~quiet hours suppresses incoming calls~~.**
  Fixed 2026-11-06. Premise confirmed against source first (the term was in the conjunction,
  and both platforms early-return on it at four call sites). Semantics chosen: quiet hours
  *silence* a call rather than hiding it, and blocking a call outright is left to the system's
  own Do Not Disturb. `shouldShowCallNotification()` lost the quiet-hours term; a new
  `shouldSilenceCallNotification()` sends Android's call notifications to `CHANNEL_CALLS_SILENT`
  (a separate channel because from API 26 the channel owns the sound; `IMPORTANCE_HIGH` is kept
  so the heads-up banner and full-screen intent survive) and drops the sound on iOS. Messages
  and requests keep the old suppression. Covered by the new `NotificationGuardTest`.
  **A device check is still owed**, and `doc/manual-test-plan-2026-09-10.md` B12 was rewritten
  to test the fix.

- **B3 (B14) — ~~`blockUnknownContacts` is written and read back by the settings screen and
  used nowhere else~~.** Fixed 2026-11-06. Premise confirmed against source first: four
  references in the tree, none of them enforcement. Enforced on both surfaces a stranger can
  reach — `CallService.onIncomingCall` refuses the call before it is registered or announced,
  and `ConversationFacade` withholds a conversation request both on arrival and in the daemon
  poll the request screens do on launch (so it survives a restart). Withheld, not declined: the
  daemon keeps the request, so turning the setting off reveals it. Predicate:
  `DaemonBridgeApi.isConfirmedContact`, which asks the daemon rather than a client cache and
  treats a banned peer as not a contact. `DHT.PublicInCalls` is deliberately left alone.
  Covered by `BlockUnknownContactsTest`. **A device check is still owed**, and
  `doc/manual-test-plan-2026-09-10.md` B14 was rewritten to test the fix.
  Two findings from this work were closed straight after it, neither of them on this list:
  `showIncomingTrustRequestNotification` was implemented on all five platforms and called from
  none (so a contact request raised no notification at all — now driven from
  `ConversationFacade`, deduped, with Accept/Decline actions on Android), and
  `PrivacySettings.allowContactRequests`/`blockedContacts` were dead in both directions and were
  removed. See `doc/TODO.md`.

- **B4 — build and run the iOS half of B13.** `HardwareService.isMeteredNetwork()` uses
  `nw_path_is_expensive` on iOS; Apple targets are skipped on the Linux host, so that actual
  has never been through a compiler. (`STATUS.md`)

## C. Chat parity with jami-android-client

- **C1 — @mentions in group chat.** Reference parses and highlights `@username`; KMP has no
  mention system in the chat UI or view model.
- **C2 — Markdown in message bodies.** Reference renders with Markwon (bold, italics, code);
  KMP shows plain text. Clickable http(s) links already landed 2026-09-18.
- **C3 — DONE 2026-11-08.** The indicator now follows
  `ConversationAdapter.configureDisplayIndicator`, so it appears at most twice in a
  conversation instead of once per message: a delivered double tick **only** on
  `Conversation.lastSent`, and each peer's avatar parked at the message their
  `lastDisplayedMessages` entry points to. Delivered-but-older and read-but-not-a-marker both
  render nothing; avatars win over a tick on the same message. Failure and waiting-to-sync keep
  their own glyphs — the reference surfaces failure separately and the clock belongs to this
  client's durable outbox.

  `MessageItem` gained `isLastSent` and `seenBy`; neither can be derived from an interaction
  alone, because both move as new messages arrive, so `applyReadMarkers` runs over the whole
  list from `loadMessagesFromHistory` and from `updateDeliveryStatus`. The daemon chain was
  already in place: `onAccountMessageStatusChanged` awaits `Conversation.updateSwarmInteraction`
  — which is what maintains both fields — before emitting `MessageStatusChanged`, so the view
  model always recomputes against current model state. Peer avatars are cached per peer per
  conversation rather than re-read from disk per message. New `AvatarSize.Tiny` (14 dp) and a
  "Read by %1$s" description, since avatars replacing a spoken status would otherwise be silent.

  13 tests in `ReadMarkerTest` cover the four cases, the list pass and `SeenBy`'s hand-written
  `ByteArray` equality. **Not yet seen on hardware:** avatars only appear once a peer actually
  reports DISPLAYED, so the visible half needs the two phones.

- **C4 — full emoji picker for reactions.** KMP offers six fixed emojis; reference has a
  "more" button opening a full picker.
- **C5 — video recording in chat.** Menu item shows a "coming soon" snackbar; needs a
  Camera2 / AVFoundation capture session.

## D. Calls

- **D1 — ~~proximity sensor~~.** Done 2026-11-08, and the premise was half stale: Android had
  already grown `updateProximityLock()` with a real `PROXIMITY_SCREEN_OFF_WAKE_LOCK`, gated on
  the setting, called from `updateAudioState`, `toggleSpeakerphone` and `closeAudioState`.
  **iOS had nothing** — `UIDevice.proximityMonitoringEnabled` appeared nowhere, so an iPhone
  lit its screen against the user's cheek for every call and the setting was read on one
  platform only. iOS now turns monitoring on at the same three points and off at the end of
  the call (left on, it blanks the screen whenever anything comes near the phone).
  The rule itself moved to `ProximityPolicy.shouldBlankScreen()` in common code so the two
  actuals cannot drift apart unnoticed — that difference is invisible until someone is holding
  both phones. `ProximityPolicyTest` states all eight input combinations.
  **Not compiled:** the iOS half, like every Apple change made on this host.
- **D2 — haptic feedback on answer/decline.** No `Vibrator` / `HapticFeedbackConstants`
  anywhere in the call flow.
- **D3 — Bluetooth headset routing.** `toggleSpeaker()` exists; the 3-way
  earpiece/speaker/BT route is not fully wired through `AudioManager`.
- **D4 — incoming video call screen (`docs/refine-video-call.md` P2).** Add the live camera
  preview while a video call is ringing and a separate "accept audio only" button, matching
  `initIncomingCallDisplay`.
- **D5 — expose `videoEnabled` in call settings (P2)** and **gate outgoing calls on
  `videoEnabled` + `hasCamera()` (P3)**, matching `CallPresenter.initOutGoing():98`.

## E. App settings / platform integration

- **E1 — ~~battery-optimisation exemption prompt~~.** Done 2026-11-08. Premise confirmed first:
  no `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` anywhere — and the reference client does not prompt
  either, so this was written from scratch rather than resampled. A new
  `rememberBatteryOptimizationState()` expect/actual (`ui/platform/BatteryOptimization.kt`)
  reports `isSupported`/`isExempt` and launches the system request; the Android actual re-reads
  `PowerManager.isIgnoringBatteryOptimizations()` on every `ON_RESUME` (the Activity result is
  `RESULT_CANCELED` even on a grant), goes through `PendingActivityResultTracker` like the other
  system-Activity launchers, and falls back to the settings list and then the app-details page
  for ROMs without the dialog. The other four source sets report `isSupported = false` /
  `isExempt = true`, so no caller nags where there is nothing to grant. Surfaced in two places:
  an explained dialog at the end of `AccountSummaryScreen`'s onboarding permission chain (queued
  behind the notifications-denied dialog), and a status row in `AppSettingsScreen`'s System
  section, which is also prompted from when "Run in the background" is switched on.
  **A device check is still owed.**
- **E2 — language selector.** `UiSettings` already carries a `language` field, so this is UI
  wiring in `AppSettingsScreen` plus applying the locale.
- **E3 — ~~"display name in notifications" privacy toggle~~.** Done 2026-11-08. The reference
  has no such toggle after all — what it has is notification *visibility* (public / private /
  secret), which KMP already implements as `applyUserVisibility()`. That setting only governs
  a locked screen, so this is a KMP addition rather than parity:
  `PrivacySettings.showNamesInNotifications`, surfaced in the Privacy section and enforced
  through `NotificationGuard.shouldShowNames()`. Off, a message notification drops the
  `MessagingStyle` entirely and says "New message" (keeping reply and mark-read, which reveal
  nothing), a call notification falls back to its call-type label, and a single contact
  request says "New contact request" — while the *count* of several requests still goes out,
  being nobody's name. Android, iOS and Desktop. **macOS is not covered:** that service reads
  no notification setting at all, guard or otherwise, which is a larger pre-existing gap on a
  target that does not currently compile (F4).
- **E4 — conversation app shortcuts.** Declared in the reference `AndroidManifest.xml`, not
  in the KMP manifest.

## F. Apple platforms

- **F1 — one Mac session for the batched wrapper regeneration.** `JamiBridgeWrapper.h/.mm`
  needs five entry points, then `build-jamibridge.sh` and an Xcode link (a green
  `linkDebugFrameworkIosSimulatorArm64` proves nothing — the framework is `isStatic = true`;
  verify with `nm <archive> | grep -cE '[-+]\[JamiBridgeWrapper '`):
  - `connectivityChanged` — `NWPathMonitor` already feeds `HardwareService`, but
    `DaemonBridge.ios.kt` can only log it, so libjami never re-resolves connections after a
    network change.
  - `setDefaultModerator`.
  - `publish` (presence status note) **plus `lineStatus` in the presence callback** —
    `DaemonBridge.ios.kt:1364` / `.macos.kt:1063` synthesise the buddy notification and pass
    `""`, so Apple can neither send nor receive a note.
  - `sendFile`'s missing `parent` argument — a file sent as a reply arrives as a plain file.
  - `setAudioMeterState` + the `AudioMeter` callback — the microphone level ring stays dark.
  - `createConfFromParticipantList`.
  (`doc/apple-bridge-rebuild.md`, `doc/status-note-open-ends.md` open end 4)

- **F2 — iOS/macOS call recording.** `toggleRecording` / `getIsRecording` return `false`.
  Needs the two wrapper methods plus a `RecordingStateChanged` delegate, then a `.a` rebuild —
  fold into F1. (`doc/plan_call_recording.md`)

- **F3 — iOS remote video rendering.** No Metal/CALayer `SinkTarget`; audio-only calls work.
  Architectural, multi-week. (`ios_implementation_gap.md`)

- **F4 — shared `appleMain` source set, fixing the macOS build.** ~95 Kotlin errors, mostly
  `MacOSHardwareService.kt`; the Darwin implementations are now substantively identical, so
  one copy fixes both. Start by rebasing `origin/macos-build-and-apple-source-set` (`f0f66ce`)
  onto `main` — it predates the nine wrapper commits. Separately,
  `build-jamibridge.sh`'s macOS stage fails on UIKit code compiled against the macOS SDK
  (long-standing, `93df547a`).

- **F5 — script the device-side native symlinks.**
  `shared/src/nativeInterop/cinterop/lib/` is hand-made and drifts silently when the daemon's
  dependency set changes (three of five first-build failures were exactly this). The simulator
  side is already discovered by `scripts/make_sim_links.py`; mirror it for
  `jami-client-ios/DEPS/arm64-iPhoneOS/lib/` and keep `OTHER_LDFLAGS` in `project.pbxproj`
  in step.

## G. Push delivery

- **G1 — stand up a push-capable DHT proxy.** Client integration is done on both platforms;
  the push is sent by the proxy, and the public one only holds SFL's FCM credentials. Needs a
  self-hosted `dhtnode --proxyserver` holding this app's own Firebase/APNs server keys.
  (`doc/push-notifications.md`)

## H. Older gap-analysis leftovers — confirm before implementing

`doc/gap-analysis-medium-opportunities.md` is marked superseded (2026-03-19) and much of it
has since landed under other names. Treat each as a read-the-source check first:

- **H1** Conversation search and history pagination (`onMessagesFound`, `loadMore()`).
- **H2** Name-registration state machine (in-progress → success/failure) for the creation wizard.
- **H3** Presence streamed onto the `Contact` model as a `StateFlow` + availability indicators.
- **H4** Account wizard presenter parity (SIP creation, device linking/import, profile step).
- **H5** `CallViewModel` parity with the reference `CallPresenter`.
- **H6** Certificate/device management leftovers (`validateCertificatePath`,
  `getCertificateDetails`, `migrateAccount`, TLS method enumeration) — the certificates screen
  landed in `security-networking.md` F11, so scope this to what is genuinely missing.

## I. Test and harness infrastructure

- **I1 — Robolectric for the Android unit tests**, so `HardwareServiceTest`,
  `AppSettingsViewModelTest`, `AppViewModelTest` and `CallViewModelTest` run against the
  Android actuals instead of desktop only.
- **I2 — ~~let `StubDaemonBridge` retain its callbacks~~.** Done 2026-11-08. `init()` keeps
  them, and `fireDaemonEvent { … }` plays the daemon against whatever the stack registered,
  failing loudly if nothing has. `connectDaemonCallbacks()` in `TestFixtures` wires a real
  `DaemonCallbacksImpl` over a test stack the way `DaemonLifecycleService` does in the app, so
  a test now drives the production routing — the per-conversation channels included — rather
  than calling service internals directly. `StubDaemonEventsTest` is the pattern, with five
  tests: registration reaching the account model, channel ordering, `ConversationReady`
  reaching the facade on both an early and a late subscriber, and firing before `init()`
  failing loudly. The callback coverage this unlocks is not written yet; it is now writable
  without a device.
- **I3 — harden the harness "no competing daemon" precondition.** Add `cx.ring.debug` (and
  consider `com.savoirfairelinux.ring`) to `COMPETING_APP_IDS`; re-assert the force-stop just
  before the first cross-device step rather than once per run; add a pre-run
  `pm clear net.jami.android.harness` / account wipe so a timed-out run cannot poison the next
  scenario.
- **I4 — device verification owed on the durable outbox.** The `send-before-member-join` e2e
  scenario bypasses `SendQueueService`, so the UI-driven path has never been checked.
- **I5 — microphone level ring.** Needs a call someone answers, which adb cannot do alone.
  (`doc/two-device-verification-2026-10-29.md`)

## J. Licensing and attribution (GPLv3)

- **J1 — BLOCKED: decide the copyright holder name/entity.** Nothing else in this section can
  be written first, and it must not be guessed from a git config.
- **J2 — fix `AboutViewModel.kt:39`.** `COPYRIGHT = "Copyright (C) 2004-2025 Savoir-faire
  Linux Inc."` is rendered in the About screen, so the running app tells users this is an SFL
  product; GPLv3 §5(d) makes it a compliance matter. Becomes a two-line attribution.
  `AboutViewModelTest.kt:37` asserts on the constant and changes in the same commit.
  Highest priority after J1 — the only item users see.
- **J3 — declare the modification (§5(a)).** No file states that it was modified or when. Add
  a "part of Jami KMP, a modified cross-platform port of jami-client-android; modified 2026"
  line to the header block.
- **J4 — classify the ~341 `.kt` and 477 `.xml` files derived vs. original and add our line**
  below SFL's where derived, alone where original. Keep every SFL notice (§4/§5(b)).
- **J5 — consider an `AUTHORS` file** separating upstream from this port's contributors.

## K. Decisions before any code

- **K1 — chat plugins (`daemon-api-surface-audit.md` F3).** The single biggest remaining
  feature and the last open mini-plan finding: install format, file management, per-account
  and per-conversation state, dynamic preference forms from daemon metadata. Plugins are
  desktop-first in the reference clients and nothing has established that the mobile-primary
  targets want them. Plan: `doc/plan_plugin_system.md`.
- **K2 — Web/JS platform.** The whole daemon bridge is REST stubs; candidate for removal
  unless a REST bridge server is actually developed. Decide rather than carry it.
- **K3 — Desktop JVM.** `DaemonBridge` is 100+ no-ops; the blocker is SWIG-generated JNI
  classes conflicting with KMP's Android plugin, needing a separate JVM module.
  `VideoSurface.desktop.kt` and `OsmMapView` desktop/macOS stubs all sit behind it.
  Deprioritised — keep it that way, or fund the separate module.
- **K4 — `QRCodeUtils.js.kt`** returns null; only worth doing if K2 keeps the web target.

- **K5 — Qt/QML client via a headless JVM core-host. DORMANT (decided 2026-11-07).**
  `doc/plan_ui_logic_separation_qt_ipc.md` proposed splitting `:core` / `:presentation` /
  `:ui-compose`, hosting the ViewModels in a headless JVM process and driving a Qt client over
  HTTP+WebSocket. No code was ever written for it, and its §5 ended in four unanswered
  decisions — chief among them whether the Compose desktop app becomes an IPC client too. Parked
  with K2 and K3 rather than left open: reopening means answering §5 first. The plan body is
  still accurate, and the one §5 decision that *was* answered — adopting
  `androidx.lifecycle.ViewModel` — already shipped on its own merits (see below), so a restart
  would begin from Phase 1, not Phase 2.

---

# Addendum — recovered from session screenshots, 2026-11-07

Thirty-nine screenshots of earlier sessions (2026-08-08 … 2026-11-07) were read back and each
claim re-checked against the current source before being written down here. Most of what they
contained is already in sections A–K above or in `doc/TODO.md`, and was confirmed as such. What
follows is only what no file carried.

## L. Accessibility

- **L1 — DONE 2026-11-07.** The F11 audit had been run on 2026-09-30 and its results never
  written down; they are now in `doc/accessibility-audit-2026-09-30.md`, and the open list in it
  is implemented. Conversation rows are merged nodes with `Role.Button`, a composed label,
  pin/mute as `stateDescription` and the four long-press entries as custom actions; the search
  row and chat header are named; the typing indicator and call-status line are polite live
  regions; the composer field is labelled; screen titles are headings; five chat touch targets
  went to 48 dp; and all 15 hardcoded English `contentDescription`s are resources, including the
  pending-invitations banner, whose text was assembled in source as `"$n pending invitation" +
  "s"` and could neither translate nor pluralise. `net.jami.ui.TestTags` now exists. Desktop
  suite 918/918, Android and desktop both compile.

  **Left open, recorded at the end of the audit file:** incoming messages and the recording
  banner are still not live regions; no compose UI test exercises any of this, because
  `compose-ui-test-junit4` sits in the version catalog unwired (the neighbour of I1); and touch
  targets outside the chat composer were not swept.

## M. Repository and document hygiene

*M1–M3 were written against local `main` before it was brought up to date. Merging
`origin/main` on 2026-11-07 answered most of them; what each one actually turned out to be is
recorded below rather than deleted, because the screenshots that raised them will otherwise
raise them again.*

- **M1 — resolved 2026-11-07.** `main` had drifted 25 ahead of `origin/main` and 5 behind: the
  whole 2026-11-06/07 device-session effort sat only on this machine, while origin carried the
  2026-10-31 iOS reconciliation. Merged clean (`ort`, no conflicts; `doc/TODO.md` and `STATUS.md`
  auto-merged). **Still unpushed — 27 commits ahead.** Pushing is the half that actually defends
  the work.

- **M2 — mostly already done; what remains is deliberate.** `doc/ios-native-parity-analysis.md`
  and `doc/ios-video-pipeline.md` are on `main` — `4a2dcad` and `8baaf0f` were cherry-picked off
  `feat/ios-app-group-and-notification-extension` on 2026-10-31, and `doc/TODO.md:1138` records
  it. The duplicate `UIBackgroundModes` key in `Info.plist` was fixed the same day (`938e572`).
  What is still only on the branch is `5971f3c`, the App Group entitlement plus the Notification
  Service Extension scaffold, and it is held back **on purpose**: it moves the daemon's working
  directory into the shared container, which needs the group registered on the App ID first, and
  it is only useful once a push-capable proxy exists (G1). Merge it when those two are true, not
  before. The iOS gaps themselves now live in `doc/ios-native-parity-analysis.md`; read that
  rather than the list a screenshot once carried.

- **M3 — withdrawn.** `ios_implementation_gap.md` was stale when the screenshot said so, but it
  has been reconciled against the code twice since (2026-10-26, then 2026-10-31 for the iOS
  bundle claims). `CLAUDE.md`, `README.md` and `STATUS.md` can keep citing it.

- **M4 — five local branches, three of them now dead weight.** `daemon-from-source-reconcile`,
  `fix/settings-stored-but-unapplied` and `stability/phase1-daemon-connectivity` are **fully
  merged into `main`** (zero unmerged commits each) and can be deleted.
  `docs/ios-native-parity-analysis` holds one commit whose content reached `main` by cherry-pick,
  so it is deletable too once someone confirms the file matches. `feat/ios-app-group-and-
  notification-extension` keeps its three commits for the reason in M2 — leave it.

## Additions to existing sections

- **I6 — DONE 2026-11-07.** `TestFixtures` gained `groupConversation()`, `oneToOneConversation()`
  and `seedMixedConversationList()`, which puts a group *and* a 1:1 in one account's list — the
  shape that broke on a device, where anything walking the list meets the group first. The new
  `GroupInConversationListTest` drives that list through `ConversationsViewModel.loadConversations`
  (both rows load, the group row is flagged as one), through `Account.getByContact` (the 1:1 is
  still found with a group ahead of it, and a group member is not mistaken for a 1:1 peer) and
  through the facade. `AccountGroupLookupTest` already covered the model accessor; this covers
  the layer above it, which is where the 918 green tests had been silent.

- **I7 — DONE 2026-11-07.** `conversationEvents` now backfills `ConversationReady` to each new
  subscriber, exactly as `AccountService.accountEvents` has since 2026-08-17. The facade keeps
  the set of conversations that have reached ready (dropped again on removal) and replays them
  through `onSubscription`.

  This was not only latent. Two call sites awaited that event with no way to win the race:
  `NewConversationViewModel.startConversation` subscribes only *after*
  `ConversationFacade.startConversation` has thrown, and `CommandHandler` does the same in the
  harness — so a conversation that went ready in between left the first hanging on
  `isLoading = true` forever and the second burning its 30 s timeout.

  Deliberately narrow, as in the account case: only `ConversationReady` is replayed, because
  "this conversation is live" is durable, while a received message or a member join is one-shot
  and replaying it would be actively wrong. `ConversationReadyBackfillTest` has four tests —
  two prove the backfill (both fail without it, checked by disabling it), two prove its limits
  (a removed conversation is not announced, and nothing but `ConversationReady` is replayed).

- **I8 — the SELinux reinstall-race fix was validated on device B (Pixel 2) only.** Whether the
  Pixel 7a is equally exposed was never checked. `doc/TODO.md`'s 2026-08-17 infra entry does not
  say which device proved it.

- **I9 — the sender-side conversation-resolution fix (`66c5aef`) has only ever been tested in
  one direction.** Every passing run has B initiate the contact request *and* send first. The
  fix in `onContactAdded` should be symmetric, but that has never been demonstrated — a scenario
  variant that swaps which role sends `SendContactRequest` closes it in roughly one test run.

- **I10 — the JNI reconciliation plan's device checklist was never explicitly signed off.** The
  plan lives outside the repo at `~/.claude/plans/polished-dancing-puppy.md`; its code changes
  (`NetworkServiceCallback` wiring, `updateProfile` arity, `unholdConference` →
  `resumeConference`, `cancelMessage` removal) are the reason the daemon was rebuilt at all, but
  step 6 — app starts without `UnsatisfiedLinkError`, profile save works, hold/resume works —
  was never checked off. Either verify it or copy the checklist into `doc/` so it stops being
  invisible.

- **Two `daemon-api-surface-audit.md` findings are dead and can be closed.**
  `reloadConversationsAndRequests` (messaging F8) and `createMediaPlayer` (daemon-audit F9) do
  not exist in the SWIG bindings at all — zero hits across `JamiService.java` and every
  `DaemonBridge` actual — so there is nothing to wire. Closing both takes the mini-plan backlog
  to six open findings.

## Second screenshot pass (the 18 older ones, 2026-04-14 … 2026-08-07)

Read back and checked the same way. Almost all of it shipped: media gallery, group admin,
hardware-accel codec toggle, daemon debug logs, the Requests filter chip, CallKit, Telecom API,
attended call transfer (`CallScreen` → `CallViewModel.attendedTransfer`), system-contacts-sync
toggle, ringtone picker, codec list in `AccountSubSettingsViewModel`, STUN and video port range
in `AccountAdvancedSettingsScreen`, connectivity mode, video bitrate, "place system calls",
`supportsPictureInPicture` in the manifest, the draggable local video preview,
`ScreenShareService` with its `MediaProjection` handling, and the `e2eList` Gradle task.
`strings_kmp.xml` is gone — that cleanup was carried out. Three things were not:

- **N1 — SIP is complete up to account creation and absent after it.** `CreateAccountScreen`
  has the full SIP form and `AccountCreationViewModel.createSipAccount()` works, but there is no
  SIP account-settings screen (server credentials cannot be edited after creation; the settings
  screen shows the Jami-centric view regardless of `account.isSip`), `HomeScreen` /
  `ConversationsViewModel` have no SIP awareness, `ChatScreen` makes no distinction for a
  call-only contact, and there is no dialer for starting a SIP call by URI or number. H4 covers
  only the creation wizard, so the post-creation half has never been tracked. Also missing from
  the advanced screen and specific to SIP: registration expiry, local/published address+port,
  SRTP key exchange and the 13 TLS keys.

- **N2 — resolved 2026-11-07: the Qt/IPC plan is now dormant.** Its §5 had blocked Phase 1
  since 2026-06-26 and nobody was going to answer it, so the plan is parked as **K5** above,
  alongside the Web/JS and Desktop-JVM decisions, and says so at its head.

  Checking it turned up something better than the finding: **§5's one answered decision had
  already been implemented and both plan documents still claimed otherwise.** All 20 ViewModels
  extend `androidx.lifecycle.ViewModel`, override `onCleared()` and cancel their scope, and
  every platform's `getViewModel()` goes through `koinViewModel()` — so the coroutine-scope leak
  described in `doc/plan_viewmodel_lifecycle_leak_fix.md` is closed. That file said "Proposed —
  no code written yet" as of this morning and now records the verification, including the one
  deliberate deviation: the ViewModels keep a constructor-injected scope cancelled in
  `onCleared()` rather than using `viewModelScope`, because that scope is what the tests drive.

- **N3 — two documents a session claimed to have written do not exist.**
  `VIDEO_CALL_ROADMAP.md` and `ROADMAP_IMPLEMENTATION_SUMMARY.md` (reported created alongside
  `8fb0467` and `7a4b0d3`) appear in no commit on any branch and are referenced by no file. The
  surviving video-call document is `docs/refine-video-call.md`, which sections D4/D5 already
  draw from. Nothing to recover — noted so the claim is not chased again.

## Confirmed already recorded — no action

Checked against source and found both true and already written down, so the screenshots carrying
them add nothing: haptics on answer/decline (D2), language
selector (E2), app shortcuts (E4), @mentions (C1), Markdown (C2), read markers (C3), the
six-emoji reaction picker (C4), Robolectric for the four excluded Android tests (I1),
`StubDaemonBridge` discarding its callbacks (I2), outbox device verification (I4), the microphone
level ring (I5), the `AboutViewModel` copyright claim (J2, still blocked on J1), plugins (K1),
and the file-picker/biometric-lock race (`doc/TODO.md`).

Fixed since the screenshot that reported them, verified in source today: the `Interaction`
`MessageStates` enum skew (codes are explicit now, `FAILURE`/`CANCELLED` both map to
`DeliveryStatus.FAILED`); `Account.getByContact` and the `ChatViewModel` group walks
(`contactOrNull` throughout); replying with a file (`ChatViewModel.sendFile` takes
`takeReplyTo()` through to the daemon's `parent`); the desktop database startup
(`DatabaseDriverFactory.desktop.kt` tracks `PRAGMA user_version` and migrates); the settings and
drafts storage layer (`ClientSettings.sq`); retry for failed file transfers; the long-press
menu and Share/Save on file and image messages; and `AppSettingsViewModelTest` compiling.

One residual worth knowing rather than fixing: `ConversationFacade.blockConversation` (`:735`)
still reads `conversation.contact` rather than `contactOrNull`, inside a `try/catch` that falls
back to `removeConversation` — so blocking a group aborts quietly instead of crashing.
