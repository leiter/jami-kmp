# iOS release — TestFlight and App Store

All lanes run from `ios-app/`.

```
cd ios-app
fastlane check_api_key     # does the ASC key authenticate, and does the app exist?
fastlane check_signing     # does the Release entitlement match the profile? (~1s)
fastlane beta              # clean → bump build → archive → export → TestFlight
fastlane deploy            # same, but uploads to App Store Connect (no review submission)
```

`fastlane build` stops after producing the IPA, if you want to inspect it before uploading.

## Authentication

Every lane uses an **App Store Connect API key** — no Apple ID, no app-specific
password, nothing that breaks under 2FA. The key is looked up in this order:

1. `$ASC_KEY_PATH`
2. `~/.appstoreconnect/private_keys/AuthKey_<KEY_ID>.p8` ← normal location
3. `AuthKey_<KEY_ID>.p8` next to the Fastfile (for CI, which materialises it from a secret)

The `.p8` is a private key that authenticates uploads as this team. It is
gitignored and must never be committed. Override the key and issuer per machine
with `ASC_KEY_ID` / `ASC_ISSUER_ID` / `ASC_KEY_PATH` rather than editing the Fastfile.

## Versioning

`Info.plist` contains `$(CURRENT_PROJECT_VERSION)` and `$(MARKETING_VERSION)`, so the
Xcode build settings are the single source of truth and the plist never holds a literal.

`bump_build` therefore edits the build setting directly through the `xcodeproj` gem.
It deliberately does **not** use fastlane's `increment_build_number`, which drives
`agvtool` and rewrites those `$(...)` references into literals — recreating the
two-sources-of-truth problem this project does not currently have.

`fastlane beta` bumps automatically, so two uploads can never collide on a build
number. Marketing version is manual: `fastlane set_version version:1.1.0`.

## check_signing, and why it exists

A signing mismatch surfaces roughly fifteen minutes into an archive, which is an
expensive way to learn that an entitlement does not match a profile. `check_signing`
answers the same question in about a second, and `build` runs it first.

It caught a real one. `iosApp.entitlements` used to hardcode
`aps-environment = development`, while the `Jami-KMP-test` distribution profile
grants `production`. With `CODE_SIGN_STYLE = Manual` nothing rewrites that, so every
Release archive would have failed entitlement validation at codesign time. The
entitlement is now `$(APS_ENVIRONMENT)`, resolved per configuration:

| Configuration | APS_ENVIRONMENT | Certificate |
|---|---|---|
| Debug | `development` | Apple Development |
| Release (device) | `production` | Apple Distribution |
| Release (simulator) | `development` | Apple Development |

## Prerequisites

- The `Jami-KMP-test` provisioning profile installed (Xcode → Settings → Accounts →
  Download Manual Profiles). `check_signing` fails with that instruction if it is missing,
  and also fails if the profile has expired.
- An `Apple Distribution` certificate in the login keychain.
- The app record must exist in App Store Connect before the first upload —
  `check_api_key` is the cheapest way to confirm it does.

## Relationship to scripts/build-ios-ipa.sh

`scripts/build-ios-ipa.sh` predates this and only archives and exports; uploading was
manual through Xcode's Organizer. It still works for producing an IPA locally, but it
does not bump the build number and has no preflight, so prefer `fastlane beta`.
