# Push Notifications

**Status:** Android **and** iOS client-side integration complete (2026-07-27).
Delivery is **not** yet functional end to end — see "What is still missing" below.
The iOS code has **not been compiled** — Apple targets are skipped on a Linux host and this
machine has no macOS toolchain. It needs a build on a Mac before it can be trusted.

---

## Why push matters here

Jami is peer-to-peer: there is no message server holding a queue. A device only learns about an
incoming call or message while it has a live DHT connection. Without push, the only way to stay
reachable is to keep the daemon process alive permanently — which is exactly what
`JamiDaemonService` does, and exactly why the app needs a `specialUse` foreground service type
and a Play Console justification (`doc/play-console-special-use-justification.md`).

Push replaces that: the DHT proxy holds the connection on the device's behalf and sends an FCM
wake-up when something arrives. The daemon then reconnects just long enough to fetch it.

---

## How it is wired (Android)

```
FCM ──▶ JamiFirebaseMessagingService
          ├─▶ PARTIAL_WAKE_LOCK (10 s)
          ├─▶ PushForegroundService          (only for high-priority call pushes,
          │                                    self-stops after 5 s)
          └─▶ PushServiceManager.onPushReceived
                 ├─▶ AccountService.pushNotificationReceived ─▶ libjami
                 └─▶ NotificationService.processPush
```

Token registration runs the other way:

```
JamiApplication.onCreate
   └─▶ PushServiceManager.initialize
          ├─▶ FirebaseApp.initializeApp        (null when unconfigured → push stays off)
          └─▶ FirebaseMessaging.token ─▶ onTokenAvailable ─▶ applyCurrentMode
                                                                ├─▶ setPushNotificationConfig
                                                                └─▶ setProxyEnabled(true)
```

`applyCurrentMode()` is the single reconciliation point and is idempotent. It is called when the
token arrives, after accounts finish loading (`JamiApplication`), and whenever the user changes
the connectivity mode (`AppSettingsViewModel` → `PushConfigNotifier`).

### Policy

Push is only armed when the user selects **`ConnectivityMode.GOOGLE_SERVICES`** in app settings —
that setting already existed in the UI but was stored and never applied. In every other mode the
daemon token is cleared and the app falls back to the persistent daemon service.

Enabling push also enables the DHT proxy on all Jami accounts, because a wake-up is useless if
the account is not reachable through a proxy that can trigger one.

### Build-time opt-in

The `com.google.gms.google-services` plugin is applied by `android-app/build.gradle.kts` **only
when `android-app/google-services.json` exists**. Without that file the app still builds and runs
normally; `FirebaseApp.initializeApp()` returns null and `PushServiceManager` logs

```
I PushServiceManager: No Firebase configuration — push disabled, relying on the daemon service
```

Verified on a Pixel 7a (Android 15): app starts, daemon initialises, no crash.

---

## What is still missing

**1. A Firebase project — and a matching push-capable DHT proxy.** This is the real blocker, and
it is a deployment question, not a code one. The push is not sent by the peer; it is sent by the
DHT proxy the account is registered with. The public proxy (`dhtproxy.jami.net`) holds
Savoir-faire Linux's FCM server credentials and can therefore only send to *their* app's sender
ID. Dropping a custom `google-services.json` into this repo gets a valid token, and the daemon
will happily register it, but no push will ever arrive from the public proxy.

Two ways forward:

- **Self-host a DHT proxy** (`dhtnode --proxyserver`) configured with your own Firebase server
  key, and point accounts at it via `Account.dhtProxyListUrl` / `Account.proxyServer`.
- **Ship under Jami's own sender ID**, which is only possible for builds published by SFL.

**2. An APNs certificate/key on the proxy, and a build on a Mac.** Same shape as the Android
blocker: the proxy sends the push, so it needs the APNs auth key for this bundle ID. Unlike FCM
this is tractable for a self-distributed build, because you control the APNs key for your own App
ID directly — the proxy just has to be configured with it.

Also outstanding on iOS:

- **the code is uncompiled** (see status above);
- the **Push Notifications capability** must be enabled on the App ID in the developer portal,
  and `aps-environment` in `iosApp.entitlements` flipped from `development` to `production` for
  TestFlight/App Store builds;
- `iosApp.entitlements` is referenced by `CODE_SIGN_ENTITLEMENTS` but is not in the Xcode file
  navigator — signing works, but adding it to the project would make it visible;
- there is **no Notification Service Extension**, so a message push cannot be decrypted and
  displayed while the app is suspended — only VoIP/call wake-up works. The upstream iOS client
  has `jamiNotificationExtension` for this.

**4. Which token the daemon gets (iOS).** The daemon holds one token, but iOS has two:
the APNs token (messages/sync) and the PushKit VoIP token (the only channel allowed to wake a
suspended app for a call). `IOSPushServiceManager.applyCurrentMode()` **prefers the VoIP token**,
because waking for calls is the reason push exists in a P2P client. Consequence: a proxy sending
*message* pushes must address the VoIP topic too. If message delivery turns out to matter more
than call wake-up, flip the preference — it is one line.

**3. UnifiedPush.** `ConnectivityMode.UNIFIED_PUSH` is selectable in settings but unimplemented.
It is the degoogled path and needs the `topic` argument of `setPushNotificationConfig`, which the
FCM path leaves empty.

---

## How it is wired (iOS)

```
AppDelegate.didFinishLaunching
   ├─▶ IOSPushHelperKt.initPush()             (registers the PushConfigNotifier hook)
   ├─▶ registerForRemoteNotifications()        ─▶ didRegisterFor… ─▶ onApnsToken
   └─▶ PKPushRegistry(desiredPushTypes: .voIP) ─▶ didUpdate…      ─▶ onVoipToken
                                                          both ─▶ applyCurrentMode
                                                                     ├─▶ setPushNotificationConfig
                                                                     │     (topic = bundle id,
                                                                     │      platform = "ios")
                                                                     └─▶ setProxyEnabled(true)
```

Incoming call, app suspended:

```
PushKit VoIP push
   └─▶ IOSPushServiceManager.onVoipPushReceived
          ├─▶ CallKitManager.reportIncomingCallFromPush   ← synchronous, iOS 13+ requires this
          │      (placeholder call from the payload's peerId/displayName/hasVideo)
          └─▶ AccountService.pushNotificationReceived ─▶ daemon reconnects to the DHT
                 └─▶ callUpdates(RINGING) ─▶ adoptPendingPush
                        takes over the placeholder UUID, corrects the caller name,
                        and applies an answer/decline the user already tapped
```

The adoption step is the whole reason this is not just "call the Android code with different
names": iOS gives a VoIP push only a few hundred milliseconds to put a call on screen, while the
daemon needs seconds to reconnect. Reporting a placeholder and letting the real call inherit its
UUID is what keeps that a single continuous ring rather than two calls or a terminated app. A
placeholder that is never adopted is ended after 25 s (`PUSH_CALL_TIMEOUT_MS`).

Push is armed on iOS for every connectivity mode **except** `LOCAL_NODE` — unlike Android there
is no Firebase-or-nothing choice, APNs is the only transport iOS offers.

## Files

| File | Role |
|---|---|
| `android-app/.../push/PushServiceManager.kt` | Token ownership, policy, reconciliation |
| `android-app/.../push/JamiFirebaseMessagingService.kt` | FCM receiver |
| `android-app/.../service/PushForegroundService.kt` | Reconnect window for call pushes |
| `shared/.../services/IOSPushServiceManager.kt` | iOS token ownership, policy, VoIP push entry |
| `shared/.../net/jami/IOSPushHelper.kt` | Swift-facing forwarders (`IOSPushHelperKt.*`) |
| `shared/.../services/CallKitManager.kt` | `reportIncomingCallFromPush` + placeholder adoption |
| `ios-app/iosApp/AppDelegate.swift` | APNs + `PKPushRegistryDelegate` |
| `ios-app/iosApp/iosApp.entitlements` | `aps-environment` |
| `shared/.../services/PushConfigNotifier.kt` | commonMain → platform re-apply seam |
| `shared/.../services/AccountService.kt` | `setPushNotificationConfig` / `pushNotificationReceived` (pre-existing) |
