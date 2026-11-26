# jamiNotificationExtension — message pushes while suspended (I7)

A Notification Service Extension for message pushes from a DHT proxy (I12,
`doc/push-notifications.md`). Without it a push that wakes nothing shows nothing.

## What it does

`NotificationService.swift`, Swift only:

| Push | Result |
|---|---|
| Jami is running (answers the Darwin probe within 0.3 s) | nothing; Jami's daemon posts the real notification |
| resubscribe (`timeout` set) or no account (`to`) | nothing |
| otherwise | "Jami — New message", grouped per account; opening it starts Jami, which syncs |

The app answers the probe in `AppDelegate.swift` (`NotificationExtensionPing`).

## Why no sender or text

A push carries only the account and the key of an encrypted DHT value. jami-client-ios
decrypts it by starting a second daemon inside its extension. Here that would mean linking the
shared Kotlin framework with libjami into the extension. That is far above a Notification
Service Extension's ~24 MB memory limit, and the daemon's data would first have to move into
an App Group container. That is the 2026-09 scaffold on
`feat/ios-app-group-and-notification-extension` (`5971f3c`), not merged for these reasons.
Decrypting needs a slim libjami build for the extension; this one stays a fallback until then.

## Status: simulator only

The App ID `net.jami.iosapp.notifications` and a profile for it do not exist. Simulator
builds embed the extension (the app target's "Embed App Extensions (simulator only)" phase).
Device and Release builds compile it unsigned and leave it out.

Simulator check:

```sh
xcrun simctl push booted net.jami.iosapp push.apns   # with the app not running
```

where `push.apns` is `{"aps":{"alert":{"title":"x","body":"x"},"mutable-content":1},"to":"<accountId>","key":"k"}`.

## Turning it on for devices

1. Developer portal: App ID `net.jami.iosapp.notifications` (no capabilities needed), and a
   profile for it.
2. Extension target: drop `CODE_SIGNING_ALLOWED[sdk=iphoneos*]`; sign like the app.
3. Replace the script phase with a normal "Embed App Extensions" copy phase (with the share
   extension).
4. fastlane: add the profile to `gym`'s `export_options.provisioningProfiles`.
5. The DHT proxy must send message pushes with `mutable-content: 1` and an alert, or the
   extension is never called.
