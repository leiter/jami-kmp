# jamiShareExtension — "Share to Jami" (I8)

A share-sheet extension that hands shared items to the app; the app's share picker (the
screen Android's ACTION_SEND opens) chooses the conversation and sends them.

## How it works

1. `ShareViewController.swift` copies files, images and movies into the App Group container,
   `ShareInbox/<uuid>/`, and writes `manifest.plist` (`text`, `files`) last. Text and web
   links become the message text.
2. It opens `jamikmp://share` through the responder chain (the URL scheme is in the app's
   Info.plist). If that fails it asks the user to open Jami.
3. The app reads the inbox in `IOSShareInbox.collect()` (shared/src/iosMain) on launch, on
   `jamikmp://` and whenever it comes to the foreground, moves the files to its tmp directory,
   fills `ShareState` and opens the share picker.

Unlike jami-client-ios, the extension runs no daemon of its own: it would need a second
`libjami` inside the extension's memory limit, sharing the account with the app's daemon.

## Status: simulator only

The App Group `group.net.jami.kmp`, the App ID `net.jami.iosapp.share` and a provisioning
profile for it do not exist in the developer portal yet. Until they do:

- Simulator builds embed the extension (the app target's "Embed Share Extension (simulator
  only)" script phase) and sign both with the App Group
  (`iosApp/iosApp-Simulator.entitlements`, via `CODE_SIGN_ENTITLEMENTS[sdk=iphonesimulator*]`).
- Device and Release builds compile the extension unsigned (`CODE_SIGNING_ALLOWED[sdk=iphoneos*] = NO`)
  and do not embed it. Archives and TestFlight builds are unchanged.

## Turning it on for devices

1. Developer portal: create the App Group `group.net.jami.kmp`; add it to the App ID
   `net.jami.iosapp`; create the App ID `net.jami.iosapp.share` with the same group.
2. Profiles: regenerate the app's (`Jami-KMP-test`) and create one for the extension.
3. Add the App Group to `iosApp/iosApp.entitlements` (then the simulator file can go).
4. Extension target: drop `CODE_SIGNING_ALLOWED[sdk=iphoneos*]`, set its signing like the
   app's (identity, team, profile).
5. Replace the script phase with a normal "Embed App Extensions" copy phase.
6. fastlane: give `gym` the extension's profile (`export_options.provisioningProfiles`).

The I7 notification extension uses the same group.
