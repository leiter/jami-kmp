# Play Console — `FOREGROUND_SERVICE_SPECIAL_USE` justification

Prepared 2026-07-24. Covers `net.jami.android`'s `JamiDaemonService`
(`android-app/src/androidMain/kotlin/net/jami/android/service/JamiDaemonService.kt`),
declared in the manifest with the subtype property `jami_daemon`.

Google Play reviews the free-form `PROPERTY_SPECIAL_USE_FGS_SUBTYPE` value and the
foreground-service declaration in **Play Console → App content → Foreground service types**.
The declaration form also asks for a short video showing the feature in use; that has to be
recorded separately — this document only supplies the written case.

---

## 1. The short value (already in the manifest)

```xml
<property
    android:name="android.app.PROPERTY_SPECIAL_USE_FGS_SUBTYPE"
    android:value="jami_daemon" />
```

Keep this stable — it is what the reviewer sees next to the longer declaration below.

## 2. Declaration text (paste into Play Console)

> **What the service does**
>
> Jami is a fully peer-to-peer communicator. It has no central server: there is no message
> broker, no account server, and no push-notification backend. Each installation runs a local
> daemon that joins a distributed hash table (OpenDHT), publishes the device's presence under
> the user's own cryptographic identity, and keeps an encrypted transport open so that other
> users can reach it directly.
>
> `JamiDaemonService` is a foreground service whose only job is to keep that daemon process
> resident while the app's UI is not visible. It performs no work of its own — the daemon is
> initialised by the application object, and the service exists solely to stop Android from
> reaping the process. It is started after boot by a `BOOT_COMPLETED` receiver and is declared
> `START_STICKY`.
>
> **Why this is necessary**
>
> Because there is no push infrastructure, a Jami device that is not running cannot be reached
> at all. Incoming calls and incoming messages arrive over a direct peer-to-peer connection to
> this device. If the process is killed, the user silently stops receiving calls and messages
> until they next open the app — there is no server that queues them and no push message that
> can wake the app. Process persistence is therefore the delivery mechanism itself, not an
> optimisation.
>
> **Why no other foreground service type applies**
>
> - `dataSync` — scoped to finite, user-initiated data transfer that completes. This service
>   transfers nothing; it maintains reachability indefinitely. It is also disallowed from
>   `BOOT_COMPLETED` receivers on Android 15.
> - `remoteMessaging` — documented as transferring text messages between a user's devices for
>   continuity when switching devices. Jami's daemon is not device-to-device message
>   continuity, and, more importantly, the primary reason for persistence is receiving
>   incoming **calls**, which this type does not describe.
> - `phoneCall` — requires an ongoing call. This service runs when there is no call; it exists
>   precisely so that a call can arrive. It is also disallowed from `BOOT_COMPLETED` receivers
>   on Android 15. When a call is actually in progress, Jami uses a separate foreground service
>   with the `phoneCall`, `microphone` and `camera` types.
> - `shortService` — time-capped and cannot be persistent.
> - `connectedDevice`, `health`, `location`, `mediaPlayback`, `mediaProjection`, `camera`,
>   `microphone` — none describe maintaining peer-to-peer network reachability.
> - `systemExempted` — reserved for system-level applications.
>
> No existing type covers "maintain a peer-to-peer presence so that incoming calls and messages
> can be delivered without any push service", which is why `specialUse` is declared.
>
> **User visibility and control**
>
> The service posts an ongoing, low-importance, silent notification for as long as it runs, so
> its presence is always disclosed. Tapping it opens the app. The user can turn the notification
> channel off or stop the service from system settings; the app remains usable in the
> foreground. The service collects no data and performs no work beyond keeping the daemon
> resident.

## 3. Supporting facts a reviewer may ask for

- Jami is free/libre software published by Savoir-faire Linux; the protocol is fully
  distributed and documented publicly. The same architecture is used by the upstream official
  Jami Android client.
- The app does not integrate Firebase Cloud Messaging or any other push service. (Alternatives
  were explored and rejected — see the FCM discussion in the project history.)
- Manifest permission: `android.permission.FOREGROUND_SERVICE_SPECIAL_USE`, alongside the
  `RECEIVE_BOOT_COMPLETED` permission used by the boot receiver.

## 4. Fallback if the declaration is rejected

`remoteMessaging` is **not** on Android 15's `BOOT_COMPLETED` blocklist (that list is
`dataSync`, `camera`, `mediaPlayback`, `phoneCall`, `mediaProjection`, `microphone`), needs no
Play review, and the `FOREGROUND_SERVICE_REMOTE_MESSAGING` permission is already declared. So
switching `JamiDaemonService` to start as `remoteMessaging` is a viable retreat that keeps the
boot path working and removes the review entirely.

The trade-off is honesty of description rather than function: `remoteMessaging` is documented
as message continuity across a user's devices, which does not describe keeping a P2P daemon
reachable for incoming **calls**. `specialUse` is the more accurate declaration, which is why
it is the primary choice — but if review stalls, `remoteMessaging` ships.

Note that the manifest currently declares the union
`specialUse|dataSync|remoteMessaging` (required because the runtime passes `dataSync` below
API 34), so switching the runtime constant needs no manifest change.

## 5. Verified references

- Android 15 behaviour changes — `BOOT_COMPLETED` receivers may not launch `dataSync`,
  `camera`, `mediaPlayback`, `phoneCall`, `mediaProjection`, `microphone` foreground services:
  <https://developer.android.com/about/versions/15/behavior-changes-15>
- Foreground service types, including the `specialUse` declaration requirements and the note
  that the subtype values "are reviewed during the app submission process":
  <https://developer.android.com/develop/background-work/services/fg-service-types>
