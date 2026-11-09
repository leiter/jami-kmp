# iOS task queue — assembled 2026-11-09

**Status (2026-11-09):** **T0 done** — iOS Kotlin compiles; `iosSimulatorArm64Test` 973/973
green after binding `SettingsRepository` in `shared/src/iosTest/.../PlatformTestKoin.ios.kt` (the
proximity change, 084fa1a, made the iOS `HardwareService` inject it); Xcode Debug build for the
iPhone 17 Pro simulator links and the app launches to the conversation list with the account
online. **T1 done** (747e5e7). **T2 done** (5ae83d8). **T3 done** (c09f7fb; needs a developer-portal step). **T4 done** (2c6866e). **T5 done** (7da11c8). **T6 done** (see T6). T7 is next. Derived from reading `git diff 938e572..HEAD` (938e572 = 2026-10-31,
the last iOS commit; HEAD = `e5af1df`, 2026-11-08). That range is 233 files / ~60 commits, almost
all Android-side or shared Compose work. This file lists what iOS needs to keep up.

**Key fact:** every Apple change in that range was written on a Linux host and has **never been
compiled** (`doc/solo-task-queue-2026-11-08.md`: "iOS and macOS changes are uncompiled"). We are
now on a Mac, so T0 is possible and comes first.

## Resume here

1. Run T0. Everything else assumes the iOS code compiles.
2. T1 is decided (CallKit rings, silent swap for quiet hours, hide the ringtone row, D4 for
   T2) — implement it.
3. Then T2 → T7 in order. Tick items off in this file and note the commit hash, as
   `solo-task-queue-2026-11-08.md` does.

---

## T0 — Compile and test the never-built Apple code *(first)*

Files touched blind: `shared/src/iosMain/.../services/IOSNotificationService.kt`,
`services/expect/HardwareService.kt` (proximity), `ui/platform/AppLanguage.ios.kt`,
`ui/platform/BatteryOptimization.ios.kt`, `utils/PlatformLog.ios.kt`, `di/PlatformModule.ios.kt`.
(macOS has the same set of new actuals but does not compile anyway — see `CLAUDE.md`.)

- `./gradlew :shared:compileKotlinIosSimulatorArm64`
- `./gradlew :shared:iosSimulatorArm64Test` — ~20 new `commonTest` files have never run on
  Native (ReadMarkerTest, MentionsTest, MessageBodyTest, EmojiCatalogTest, ShippedLocalesTest,
  StubDaemonEventsTest, NotificationGuardTest, NotificationChannelIdsTest, ProximityPolicyTest,
  RingtonePolicyTest, BlockUnknownContactsTest, TrustRequestNotificationTest,
  ConversationReadyBackfillTest, ContactDetailsViewModelTest, …).
- Xcode simulator build that links the app, with an explicit arm64 destination
  (`-destination 'platform=iOS Simulator,name=iPhone 17 Pro'`). A green Gradle link proves
  nothing (`isStatic = true`). Run `python3 scripts/make_sim_links.py` first on a fresh checkout.

**Done when:** compile clean, Native tests green (or failures recorded), app links and launches.

## T1 — Who rings on iOS, and quiet hours for calls

**Decided 2026-11-09 (Marco):**
- **D1 — CallKit rings**, as jami-client-ios does (`Ring/Helpers/CallsHelper.swift:29`
  `ringtoneSound = "default.wav"`; `Ring/Services/AccountsService.swift:412` creates accounts with
  `Account.ringtoneEnabled = false`). Bundle the reference's `Ring/Resources/default.wav` (same
  GPLv3 upstream), set `ringtoneSound` in `CallKitManager`, create iOS accounts with
  `ringtoneEnabled=false`, **and** repair existing accounts to `false` (the reference never needs
  the repair; ours were created with the daemon ringtone on).
- **D2 — quiet hours: silent CallKit swap.** Before `reportNewIncomingCall` during quiet hours,
  set `provider.configuration` with a silent `ringtoneSound`; restore after. Check that settings
  are readable on a PushKit cold wake; if not, fall back to "calls follow Focus" and say so in
  the setting's summary on iOS.
- **D3 — hide the App Settings ringtone row on iOS** (the reference has no picker).
- **D4 (applies to T2) — names hidden ⇒ CallKit shows a generic "Jami" caller and
  `includesCallsInRecents = false`.**

**Implemented 2026-11-09** (branch `ios-catch-up-2026-11`):
- `shared/src/iosMain/.../services/IOSCallRinging.kt` (new, started from `JamiLifecycle.start()`
  before accounts load): switches `Account.ringtoneEnabled` off on every account the daemon lists,
  re-run whenever the account set changes (so new accounts too), also updating the in-memory
  `Account.details`; mirrors the quiet-hours window into `NSUserDefaults`, **only** once
  `SettingsRepository.loadedAccountId` names an account, so defaults never overwrite it.
  `ringtoneSoundNow()` reads the mirror synchronously — usable from the PushKit handler.
- `CallKitManager`: configuration built per sound, `ringtoneSound` set; `applyRingtoneForNow()`
  before both `reportNewIncomingCall` sites (daemon path and push path) swaps between
  `default.wav` and `silence.wav`.
- `ios-app/iosApp/default.wav` (jami-client-ios's file, converted from 32-bit stereo 48 kHz,
  12 MB, to 16-bit mono 44.1 kHz, 2.9 MB; 32.4 s like the original) and `silence.wav` (1 s),
  both Resources of the app target in `project.pbxproj`.
- Common: `QuietHours` (pure rule, extracted from `NotificationGuard`),
  `SettingsRepository.loadedAccountId`, `hasRingtonePicker` expect/actual (true on Android only)
  — the App Settings ringtone row is now hidden on iOS, **and on desktop/macOS/web**, where the
  picker was equally a no-op.
- Tests: `QuietHoursTest`, `SettingsLoadedAccountTest` (common), `IOSCallRingingTest` (iosTest).

Device checks owed (simulator cannot prove them): the tone actually rings from CallKit on a
locked phone; a quiet-hours call shows but stays silent; whether it still vibrates is iOS's own
haptics setting and is not ours to control.

Background (written before the decision):

- Android now bundles 11 `.opus` ringtones and repairs `Account.ringtonePath` so the daemon
  rings (`ba917cb`, `59db588`; `AndroidRingtoneInstaller`, shared `RingtonePolicy`). Both commit
  messages say "iOS needs its own ringtone provisioning."
- iOS never sets `ringtonePath` (daemon logs "Ringtone … is not a valid file"); CallKit plays the
  system ringtone.
- **Quiet hours does nothing for iOS calls**: `IOSNotificationService` honours
  `shouldSilenceCallNotification()`, but its RINGING path is skipped in favour of CallKit, which
  rings regardless. Android's counterpart: `HardwareService.muteRingtoneIfSilenced()`.
- **Recommendation:** CallKit rings — `CXProviderConfiguration.ringtoneSound` with a `.caf`
  converted from `default.opus` (CallKit cannot play opus); disable the daemon ringtone on iOS
  accounts (`ringtoneEnabled=false`) so it can never double-ring. For quiet hours, either mute
  via `CallService.muteRingtone` / a silent CallKit update, or document that iOS Focus covers it.
- The App Settings ringtone row is a no-op on iOS (`RingtoneLauncher.ios.kt`). Hide it or repoint
  it — same open decision as Android (`59db588`, "left alone pending that decision").

## T2 — CallKit caller identity (port of `e5af1df` items 3 and 4)

- `CallKitManager.kt:~163` takes the name from `call.contact`. Likely the same placeholder from
  `ContactService.getContactFromCache` that showed "d9f8c7f0bf76" on Android. Android now resolves
  via `accountService.getAccount(accountId)?.getContactFromCache(uri)` (see the `e5af1df` diff of
  `AndroidNotificationService.kt`). Verify on iOS, apply the same lookup.
- The new "show names in notifications" setting (`b59976c`, `NotificationGuard.shouldShowNames()`)
  does not reach CallKit: `localizedCallerName` and the `CXHandle` value expose the name on the
  lock screen and in Phone → Recents. When names are hidden: generic title, and consider
  `includesCallsInRecents = false`.

**Implemented 2026-11-09** (branch `ios-catch-up-2026-11`):
- `resolveCallerName()` in `CallKitManager.kt`: account contact store → 1:1 conversation peer →
  `ContactService` cache → `call.contact`, first one *with a name* wins (Android's order from
  `e5af1df`). Used for the daemon-path report and the push-placeholder adoption update.
- D4: every CallKit update (`callUpdate()`, and the outgoing handle) goes through
  `presentedName()` — "Jami" for both handle and caller name while names are hidden;
  `includesCallsInRecents` follows the setting, swapped per call with the ringtone
  (`applyConfigurationForNow()`). The setting is mirrored by `IOSCallRinging` (`showNamesNow()`),
  so a push-woken call obeys it too.
- Tests: `CallerNameTest` (new), `IOSCallRingingTest` extended.

**Found, not fixed:** `CallKitManager.reportOutgoingCallStarted` has **no callers** — outgoing
calls are never reported to CallKit, so they get no system call UI, no audio-session handling by
CallKit and no Recents entry. Candidate for its own task.

Device check owed: a named contact's call shows the name on the lock screen; with names hidden it
shows "Jami" and does not appear in Phone → Recents.

## T3 — Sender avatars in iOS notifications

Android notifications now carry the sender's name and avatar (`e5af1df` item 4, via
`loadPeerAvatar()`). iOS equivalent: Communication Notifications — donate an
`INSendMessageIntent` (and `INStartCallIntent` for missed calls) and update the content with
`content.updating(from:)`. Needs the Communication Notifications entitlement in
`ios-app/iosApp/iosApp.entitlements`. Must respect the hide-names setting.

**Implemented 2026-11-09** (branch `ios-catch-up-2026-11`):
- `shared/src/iosMain/.../services/CommunicationNotifications.kt` (new): messages become an
  `INSendMessageIntent` (sender `INPerson` with avatar; groups get `speakableGroupName`, the other
  members as recipients and the conversation avatar), missed calls an `INStartCallIntent`; the
  interaction is donated, then `contentByUpdatingWithProvider`. If iOS refuses (no entitlement),
  the plain content is posted — never a lost notification. Skipped entirely while names are
  hidden (the photo identifies as plainly as the name; a donation would also feed Siri/share
  suggestions).
- Avatar: in-memory profile, else `VCardService.loadPeerAvatar` (Android's order).
- `IOSNotificationService`: missed-call/incoming-call names now go through `resolveCallerName`
  (T2) instead of the placeholder `call.contact` — the missed-call body was the whole `jami:` URI.
- **Crash fixed:** the iOS `Conversation.getDisplayName()` read `Conversation.contact`, which
  throws for a group of more than two by design, so every group message notification and the
  group-call notification threw. Now `conversationTitle()`.
- `ios-app/iosApp/iosApp.entitlements`: `com.apple.developer.usernotifications.communication`.
  `ios-app/iosApp/Info.plist`: `NSUserActivityTypes` = `INSendMessageIntent`, `INStartCallIntent`.
- Tests: `CommunicationNotificationsTest` (iosTest).

**Developer-portal step — Marco, needed before the next device or Release build:**
1. developer.apple.com → Certificates, Identifiers & Profiles → **Identifiers** → the app's
   App ID (team `K4K982LMZ9`) → Capabilities → tick **Communication Notifications** → Save.
2. **Profiles** → regenerate `Jami-KMP-test` (device builds, see
   `"PROVISIONING_PROFILE_SPECIFIER[sdk=iphoneos*]"` in `project.pbxproj`) and the App Store
   distribution profile used by `ios-app/fastlane`; download and install both (double-click, or
   Xcode → Settings → Accounts → Download Manual Profiles).
Until then device/Release codesign fails entitlement validation. Simulator builds are unaffected
(verified: the entitlement is in `iosApp.app-Simulated.xcent`). If you need a device build before
doing this, remove the two lines from the entitlements file — the code degrades to plain
notifications on its own.

Device check owed: a message and a missed call show the sender's photo; a group message shows the
group style; with names hidden, neither photo nor name.

## T4 — Conversation shortcuts (port of `ce17110`)

- iOS binds `NoConversationShortcuts` (`PlatformModule.ios.kt`). Write an iOS
  `ConversationShortcutService` that publishes the publisher's four recent conversations as
  dynamic `UIApplicationShortcutItem`s.
- Handle the tap in `ios-app/iosApp/AppDelegate.swift` / `JamiApp.swift` (cold start and warm)
  and route it through the new `OpenConversationState`, not `DeepLinkState` (which only pre-fills
  search, by design).
- T3's intent donations also give share-sheet suggestions for free.

**Implemented 2026-11-09** (branch `ios-catch-up-2026-11`):
- `shared/src/iosMain/.../services/IOSConversationShortcuts.kt` (new) replaces
  `NoConversationShortcuts`: the shared `ConversationShortcutPublisher`'s four recent
  conversations become `UIApplication.shortcutItems`, set on the main queue. Quick actions take
  only a system symbol or template image, so every item shows `message.fill`, not the avatar.
- The publisher is started from `JamiLifecycle.start()`, as Android starts it from
  `JamiApplication`.
- Taps: `SceneDelegate` in `ios-app/iosApp/AppDelegate.swift` (handed to SwiftUI's scene via
  `configurationForConnecting`) covers both the cold launch (`connectionOptions.shortcutItem`)
  and the warm tap (`windowScene(_:performActionFor:)`), calling
  `openConversationShortcut(type:conversationId:)` → `OpenConversationState` with the bare id.
- **Also:** tapping a *message* notification now opens its conversation (it only opened the
  app); call and request notifications still just open the app.
- Not done: share-sheet suggestions — T3 already donates `INSendMessageIntent`, but the app has
  no share extension to receive the share, so nothing to wire yet.
- Tests: `IOSConversationShortcutsTest` (new), `IOSNotificationDelegateTest` extended.

Verified on the simulator: the window still renders with the scene delegate, and the publisher
runs end to end ("Published 0 home-screen quick actions" — the test account has no
conversations). Device/simulator-with-conversations check owed: long-press shows the four, and a
tap opens the chat from both a cold and a warm start.

## T5 — Language picker on iOS (`fe5043d`)

- Verify on the simulator that writing `AppleLanguages` makes Compose resources switch on next
  launch, including strings fetched outside composition (notifications).
- `Info.plist` `CFBundleLocalizations` has 124 entries, hand-maintained; `ShippedLocales.kt` is
  generated by `scripts/generate_shipped_locales.py`. Make the script emit the plist list too, so
  iOS Settings → Jami → Language matches the in-app list.

**Done 2026-11-09** (branch `ios-catch-up-2026-11`):
- **Verified on the simulator** that the `AppleLanguages` override `AppLanguage.ios.kt` writes is
  honoured by Compose resources at the next launch. Unambiguous setup: simulator system language
  English, app-domain `AppleLanguages = ["de"]` → the app came up in German; override removed →
  English. (A first try with `fr` looked like plain English only because every home-screen string
  is a KMP-only key translated into German alone, so French falls back key by key.) The
  simulator's system languages were restored afterwards (`de-DE, uk-DE, en-DE, ru-DE`).
- `scripts/generate_shipped_locales.py` now also rewrites `CFBundleLocalizations` in
  `ios-app/iosApp/Info.plist` (`en` + every `values-*` folder, sorted), in place so the plist's
  comments survive. The lists were in sync today — regenerating changed nothing — and a dummy
  `values-zz` reached all three outputs and was removed again.
- Strings are not part of this task, but the check made the gap visible: KMP-only keys
  (`placeholder_search`, `home_no_conversation_*`, `action_new_conversation`, `filter_*`, …) are
  translated into German only, so every other language shows an English home screen.

## T6 — Contact-request notifications (`cbdfddf`)

Check that Accept/Decline in `IOSNotificationDelegate.kt:~179-189` behave like Android's new
`ACCEPT_REQUEST`/`DECLINE_REQUEST` (straight to the daemon), that the persisted "already
notified" flag stops re-notifying after relaunch, and that an empty pending set removes the
notification.

**Done 2026-11-09** (branch `ios-catch-up-2026-11`). Checked against Android's `cbdfddf`:
- **Already at parity:** the "already notified" flag is persisted the same way on both (iOS binds
  the same `SettingsPreferencesService`, NSUserDefaults-backed), so a relaunch does not re-notify;
  the shared `TrustRequestNotificationTest` covers the dedupe and runs green on the iOS simulator.
  An empty pending set cancels `request_<account>`. Accept/Decline end in the same daemon calls.
- **Fixed — accept silently lost on a background relaunch:** iOS looked the request up in the
  loaded account model first and gave up when it was not there — the normal state when a button is
  pressed after the app was killed and iOS relaunches it in the background — while still
  dismissing the notification. It now accepts by id through the newly public
  `ConversationFacade.acceptRequest(accountId, uri)`, as Android goes straight to the daemon.
- **Fixed — completion handler called before the work:** `didReceiveNotificationResponse` called
  `withCompletionHandler()` right after launching the coroutine, so iOS could suspend a
  background-launched app before reply / mark-read / accept / decline reached the daemon. It is now
  called (on the main queue) once the action has been dispatched.
- **Fixed — dead buttons on the summary:** with several pending requests the "N contact requests"
  notification still had Accept/Decline (category `JAMI_REQUEST`) but no conversation, so they did
  nothing, not even open the app (they are background actions). Now only a single request gets the
  category; the summary has no buttons and a tap opens the app, as on Android.
- Also: an emptied pending set now removes a stale notification even when request notifications
  were switched off after it was posted.
- Tests: `TrustRequestNotificationTest.aRequestTheAccountHasNotLoadedIsAcceptedById` (common; the
  stub bridge now records `acceptConversationRequest`).

Device check owed: Accept from the lock screen with the app killed; the summary has no buttons.

## T7 — Lock-screen visibility setting *(small)*

Private/Public/Secret is applied on Android only (`a838c8e`); the row still shows on iOS and does
nothing. Hide it on iOS, or map "Secret" to `hiddenPreviewsBodyPlaceholder` on the notification
categories.

---

## Simulator checks for shared changes (no code expected)

- Chat: read markers (`b236634`), @mentions (`d40c6ac`), inline Markdown (`adede5c`), emoji
  picker (`7e41471`) — check colour emoji render in the iOS Compose fonts.
- Calls: incoming-direction fixes (`a36e5dc`, `c77dbec`) and "block unknown contacts"
  (`d7367e4`) against `CallKitManager`, which reports only on `RINGING && isIncoming`.
- Escaped apostrophes (`cfea231`), video gating (`eab16b9`), proximity on an audio call
  (`084fa1a` — simulator has no sensor; just confirm no crash and that it turns off at hang-up).

## Seen on the simulator, minor

- `filter_unread` / `filter_groups` ("Unread", "Groups") have no German translation — KMP-only
  keys, so nothing to import from the Android client.
- The home empty-state subtitle is left-aligned whenever it wraps onto two lines (German), centred
  when it fits on one (English): the text has no `textAlign`, only the single line is centred by
  its container.

## Not new, still open

- The wrapper rebuild batch in `doc/apple-bridge-rebuild.md` (seven items: connectivity, call
  recording, status note `publish`, `sendFile` parent, audio meter, moderator,
  `createConfFromParticipantList`).
- Notifications plan F2: 14 config signals missing in `DaemonBridge.ios.kt`. Android is complete;
  iOS waits on the same rebuild.

## Needs no iOS work

Battery optimisation (no iOS equivalent; actual already returns `isSupported=false`), haptics
(in-app part is shared; lock-screen answer is CallKit's), Android 14 screen-share fix,
notification-channel fixes.
