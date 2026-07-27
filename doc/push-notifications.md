# Push Notifications

**Status:** Android client-side integration complete (2026-07-27). iOS/APNs not started.
Delivery is **not** yet functional end to end — see "What is still missing" below.

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

**2. iOS / APNs.** `DaemonBridge.ios.kt` implements `setPushNotificationToken` /
`pushNotificationReceived`, but nothing registers for remote notifications and there is no
Notification Service Extension. This is a prerequisite for CallKit waking the device on an
incoming call. The shape of the work (from `todo.md` P1):

- register via `UNUserNotificationCenter` for message/sync pushes;
- handle **VoIP** pushes through `PKPushRegistry` — on iOS a call wake-up must arrive on the
  PushKit channel and report to CallKit immediately, or the OS kills the app;
- feed both into the existing `PushConfigNotifier` / `setPushNotificationConfig` path, so the
  policy layer stays shared with Android.

**3. UnifiedPush.** `ConnectivityMode.UNIFIED_PUSH` is selectable in settings but unimplemented.
It is the degoogled path and needs the `topic` argument of `setPushNotificationConfig`, which the
FCM path leaves empty.

---

## Files

| File | Role |
|---|---|
| `android-app/.../push/PushServiceManager.kt` | Token ownership, policy, reconciliation |
| `android-app/.../push/JamiFirebaseMessagingService.kt` | FCM receiver |
| `android-app/.../service/PushForegroundService.kt` | Reconnect window for call pushes |
| `shared/.../services/PushConfigNotifier.kt` | commonMain → platform re-apply seam |
| `shared/.../services/AccountService.kt` | `setPushNotificationConfig` / `pushNotificationReceived` (pre-existing) |
