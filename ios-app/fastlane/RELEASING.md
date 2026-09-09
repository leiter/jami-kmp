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

## Memory: the Release archive needs ~6 GB, and only one setting controls it

The Release archive is far hungrier than anything else in this project, and the
first one ever built on a Mac (2026-09-09) took eight attempts to get through.
Most of those failures were caused by tuning the wrong knob. The short version:

> **On this project the Kotlin/Native compile runs inside the Gradle daemon.** No
> separate konan JVM is spawned, so `kotlin.native.jvmArgs` and
> `kotlin.daemon.jvm.options` do not affect it. **`org.gradle.jvmargs` is the only
> heap the Native compiler gets.**

Observed directly: during the compile the only JVM on the machine was the Gradle
daemon, and it died at exactly its own `-Xmx`. Verify the same way if this ever
changes — `ps -eo pid,rss,command | grep -E "GradleDaemon|konan"` while the
`Compile Kotlin Framework` phase runs.

Consequence: shrinking the Gradle daemon to "make room" for the Native compiler
starves the compiler instead. It dies in `DevirtualizationAnalysis`, the
whole-program LTO pass Kotlin/Native runs **only in Release** — which is why Debug
and simulator builds never hit any of this.

| `org.gradle.jvmargs` | Result on an 8 GB Mac |
|---|---|
| ≤ 2 GB | `OutOfMemoryError` in ~90 s, inside `DevirtualizationAnalysis` |
| 4 GB (the repo default) | ~21 min of real work, then killed by the OS |
| **6 GB** | **Succeeds in ~16 min**, heap peaking well under the ceiling |

6 GB on an 8 GB machine only fits with the IDE and browser closed. That makes it a
property of the machine, not of the project, so it belongs in
**`~/.gradle/gradle.properties`** — which overrides the project's `gradle.properties`
— and not in the repo, where it would be wrong for anyone on a larger machine:

```properties
org.gradle.jvmargs=-Xmx6g -Dfile.encoding=UTF-8 -Dkotlin.daemon.jvm.options="-Xmx1g"
```

### Two traps that cost real time here

**`./gradlew --stop` can report "No Gradle daemons are running" while one still is.**
A daemon orphaned by an OOM kill is not in the registry `--stop` consults, but it
still holds `.gradle/8.9/executionHistory` — and the next build fails with
`Timeout waiting to lock execution history cache. Owner PID: <n>`. Check
`pgrep -f GradleDaemon` and `kill -9` what it finds; trusting `--stop`'s output is
what let this one survive.

**A dead build looks exactly like a slow one.** `xcpretty` prints nothing for the
entire ~16-minute Kotlin compile, so the fastlane log's last line stays
`Running script 'Compile Kotlin Framework'` whether it is working or long dead. The
Gradle daemon fails to *its own* log (`~/.gradle/daemon/8.9/daemon-*.out.log`) and
`xcodebuild` then hangs on the dead script phase, so nothing appears in the fastlane
log at all. To tell them apart, check the daemon's CPU: pegged near 100% with a
climbing heap means progress; 0% means it is finished or dead. Watch that daemon log
as well as the fastlane one.

### Failed builds still consume build numbers

`build` runs `bump_build` before archiving, so every failed attempt burns a number —
the first successful upload here was build 33, not the 28 the first attempt claimed.
That is the safe direction to fail (a number is never reused), but a `--skip-bump`
option for retries would be worth adding.

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
