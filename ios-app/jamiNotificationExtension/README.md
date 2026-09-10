# jamiNotificationExtension

iOS Notification Service Extension for jami-kmp. Turns an opaque APNs wake-up into a real
notification when the main app is not running to receive it.

**Status: scaffold.** The Swift lifecycle, the app-active Darwin handshake, payload
classification, and a localized fallback notification are implemented. The headless
daemon-decrypt path (`TODO(nse-daemon)` in `NotificationExtensionHandler.kt`) and the Xcode
target itself are **not done** and require a Mac. This file is the completion checklist.

Reference implementation: `jami-client-ios/Ring/jamiNotificationExtension/`.

---

## Why this exists

iOS suspends the app, so libjami cannot hold a DHT connection open. An incoming message/file
arrives as an APNs push with no readable content. Without an extension, jami-kmp can only show
that notification if the app happens to be alive in the background within its ~30 s window —
otherwise nothing appears. This extension runs in its own process on push arrival, decrypts,
and presents.

---

## Files here

| File | Role |
|------|------|
| `NotificationService.swift` | `UNNotificationServiceExtension` subclass — lifecycle, app-active handshake, presentation |
| `Info.plist` | `NSExtensionPointIdentifier = com.apple.usernotifications.service` |
| `NotificationService-debug.entitlements` / `-release.entitlements` | App Group + `aps-environment` + `usernotifications.filtering` |

Shared logic lives in `shared/src/iosMain/kotlin/net/jami/services/NotificationExtensionHandler.kt`
and constants in `shared/src/iosMain/kotlin/net/jami/IOSConstants.kt`.

---

## Completion checklist (Mac + Xcode required)

### 1. App Group provisioning
- Developer portal: enable **App Groups** on the App ID `net.jami.iosapp` and register
  `group.net.jami.kmp`. Create a matching App ID + group for the extension bundle id
  `net.jami.iosapp.NotificationService`.
- Regenerate/download the provisioning profiles. The app target already references
  `group.net.jami.kmp` in `ios-app/iosApp/iosApp.entitlements` (added with this change).

### 2. Add the extension target in Xcode
- File ▸ New ▸ Target ▸ **Notification Service Extension**, name `jamiNotificationExtension`,
  bundle id `net.jami.iosapp.NotificationService`.
- Delete the template `NotificationService.swift` Xcode generates; add the one from this folder.
- Set `INFOPLIST_FILE = ios-app/jamiNotificationExtension/Info.plist`,
  `CODE_SIGN_ENTITLEMENTS` = the debug/release entitlements files here (per configuration),
  `GENERATE_INFOPLIST_FILE = NO`.
- `APS_ENVIRONMENT`: `development` for Debug, `production` for Release (same split now used by
  the app target).
- Confirm the app target's "Embed App Extensions" build phase includes it.

### 3. Link the shared framework + libjami into the extension
The extension is a separate Mach-O and must link everything itself (the native client does the
same — one `Adapter.mm` per extension).
- Add a "Run Script" phase mirroring the app target's:
  `./gradlew :shared:embedAndSignAppleFrameworkForXcode` (or make the extension depend on the
  app target so it runs once).
- `FRAMEWORK_SEARCH_PATHS`: add
  `$(SRCROOT)/../shared/build/xcode-frameworks/$(CONFIGURATION)/$(SDK_NAME)`.
- Link `JamiShared.framework` (static).
- Copy the app target's `OTHER_LDFLAGS` libjami/ffmpeg/crypto `-l…` list and
  `-framework VideoToolbox -framework AudioToolbox` verbatim — the extension needs the same
  symbols once `TODO(nse-daemon)` is implemented. Until then it still links `JamiShared` for
  `NotificationExtensionHandlerKt`.
- Add the extension's arm64 / sim slices to `scripts/make_sim_links.py` handling if the
  simulator link setup applies.

### 4. Bridging / module import
`NotificationService.swift` does `import JamiShared`. No bridging header needed if the shared
framework is a proper modular framework (it is). If Xcode complains, add an empty
`jamiNotificationExtension-Bridging-Header.h` and set `SWIFT_OBJC_BRIDGING_HEADER`.

### 5. Main-app side of the handshake (do together with this)
In `AppDelegate.swift` / `IOSApplicationHelper.kt`, while the app is alive:
- register a `CFNotificationCenterGetDarwinNotifyCenter` observer for
  `IOSConstants.DARWIN_QUERY_APP_ACTIVE`; on receipt, post `IOSConstants.DARWIN_APP_ACTIVE_RESPONSE`.
- on foreground, drain `UserDefaults(suiteName:)[SHARED_DEFAULTS_PENDING_NOTIFICATIONS]` and
  feed each payload to the live daemon, then clear the key.

Without this the extension's `mainAppIsActive()` probe always times out (harmless — it just
means the extension always takes the daemon path).

### 6. Implement `TODO(nse-daemon)`
In `NotificationExtensionHandler.handlePush`:
1. `DaemonBridge()` → `init(noOpCallbacks)` → `start()` pointed at `dataPath` (the App Group
   container; **same** DB as the app, so no re-clone).
2. Feed the push to the bridge (`pushNotificationReceived`-equivalent) so libjami connects to
   the proxy and pulls the pending values.
3. `withTimeout(timeoutMs)` suspend until `onMessageReceived` / `onDataTransferEvent` fires for
   `conversationId(payload)`; resolve the sender's display name for the title.
4. `libjami::fini()` before returning so the app can reclaim the daemon.
Needs on-device verification: the real DHT-proxy payload key names (the `classify()` heuristics
here are guesses — align with `IOSPushServiceManager` and the proxy config in
`doc/push-notifications.md`).

### 7. Verify
- Physical device, app **force-quit**. Send a message from another device. A notification with
  real sender + text must appear within a few seconds.
- App backgrounded (alive): the message must appear via the app's own path, not doubled.
- Airplane mode / dead push: the fallback ("New message") must still appear, once, before the
  time budget expires.
