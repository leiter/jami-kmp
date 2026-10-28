# Jami KMP

A cross-platform [Jami](https://jami.net) client built with **Kotlin Multiplatform** and
**Compose Multiplatform** — domain logic, UI, and the `libjami` daemon bridge shared across
Android, iOS, desktop, and web from a single `shared` module.

This is not a reimplementation of the Jami protocol. It drives the real **`libjami` daemon**
through a per-platform `DaemonBridge`, and it is built as a deliberate port of the official
Android client rather than a fresh design.

> ⚠️ **Work in progress.** This is an actively developed project, not a finished product.
> Android is solid and iOS is close behind, but there are real gaps — see
> [Platform support](#platform-support) and [Known limitations](#known-limitations) before
> depending on it. Expect APIs, module boundaries, and screens to move.

---

## Aim: surface more of the daemon

Reaching parity with the official clients is the **baseline, not the ceiling**. The longer-term
goal is to **expose more of what the Jami daemon can already do** — a substantial part of
`libjami` is implemented, tested, and shipping in the daemon, yet no client ever calls it, so
users never see it.

The scale of that gap is measured in
[`doc/mini-plans/daemon-api-surface-audit.md`](doc/mini-plans/daemon-api-surface-audit.md)
(audited 2026-09-25):

| Surface | Daemon functions wired |
|---|---|
| `libjami` public API | **~150** across 9 interface headers |
| `jami-client-android` (reference) | ~120 — **80%** |
| `jami-kmp` (this project) | ~100 — **67%** at audit time |

So there are two distinct tracks of work, and both matter:

Since that audit, several of those gaps have closed — certificate pinning and validation,
conference per-stream moderation, in-call text, history limit and conversation counts, and the
recording path among them. As of 2026-10-26 the Android bridge calls **149 distinct
`JamiService.*` entry points** (SWIG bindings, which is a different count from the header
functions the audit measured — the two are not directly comparable). The per-finding status
lives in the mini-plans; roughly half of their 89 findings are now closed, deferred or
disproved.

1. **Close the gap to the reference client** (~20 functions at audit time) — audio device
   selection by index or name, `getPasswordKey`, history management, and several video
   capabilities. Certificate pinning and validation has since landed.
2. **Go past it** into the ~30 functions *no* client wires today — most prominently the
   **Jami plugin system** (19 functions in `plugin_manager_interface.h`, untouched by this
   project and only partly used upstream), plus exposed services / service discovery,
   diagnostic logging control, and daemon version/platform introspection.

The per-area analyses in [`doc/mini-plans/`](doc/mini-plans/) break this down by domain
(calling and conferencing, messaging, contacts and presence, notifications and push, security
and networking) and are the working roadmap for that effort. Because the shared module is the
only place the bridge is defined, a capability added to `DaemonBridge` becomes available to
every platform host at once — which is a large part of why this client is built as KMP in the
first place.

---

## Built on jami-client-android

**This project leans heavily on [`jami-client-android`](https://review.jami.net/admin/repos/jami-client-android),
and that is a design decision, not a shortcut.** The official client embodies years of
accumulated edge-case handling around the DHT, swarm conversations, call state, and account
lifecycle — none of which is obvious from the daemon API alone, and all of which is expensive
to rediscover by trial and error. Reinventing it would mean reintroducing bugs that were
already found and fixed upstream.

Two parts of the Android client serve as references, with different weight:

| Reference | Role | What is taken from it |
|---|---|---|
| **`libjamiclient`** (`jami-android/libjamiclient`) | Primary reference for shared/domain logic | Service interfaces, domain models, business rules, daemon event handling, data flow. `shared/services/` and `shared/model/` are guided directly by it. |
| **`jami-android/app`** | UI/UX reference | Screens, navigation flows, interactions, settings hierarchy, visual detail. Resampled to match faithfully. |

The goal is **behavioural parity, not structural mirroring.** `libjamiclient` is Java- and
Android-centric and uses RxJava throughout; this project diverges freely where a pattern does
not translate to KMP, where a Kotlin-idiomatic approach is clearly better, or where a
Java/Android API has no multiplatform equivalent. In practice:

- RxJava `Observable`/`Single` → `StateFlow` / `SharedFlow` + coroutines
- Dagger/Hilt → Koin
- Android `SharedPreferences` → a multiplatform `SettingsRepository`
- Room/SQLite → SQLDelight
- Fragments + XML layouts → Compose Multiplatform

String resources follow the same principle. The files under
`shared/src/commonMain/composeResources/` mirror the Android client's `res/values/` structure
file-for-file (`strings.xml`, `strings_account.xml`, `strings_call.xml`,
`strings_content_description.xml`, `strings_preferences.xml`), and **Android key names are
reused verbatim wherever the same string exists upstream**. This keeps the ~95 locale
directories in sync and makes importing new translations from the Android project trivial.

A feature is considered done here when it behaves like its `jami-client-android` counterpart
end-to-end on Android and iOS — not merely when it looks the same. That is the bar for anything
the reference client already does; for everything it *doesn't*, see
[Aim: surface more of the daemon](#aim-surface-more-of-the-daemon) above.

---

## Platform support

Mobile is the priority; the honest answer varies sharply by platform.

| Platform | UI | Daemon bridge | Status |
|---|---|---|---|
| **Android** | ✅ | JNI via SWIG bindings + `libjami-core-jni.so` | 🟢 Primary — production-ready for implemented features |
| **iOS** | ✅ | Kotlin/Native C interop (`JamiBridge.def` / `JamiBridgeWrapper.mm`) | 🟡 Builds, links and runs on the simulator; no remote video rendering yet |
| **macOS** | ⚠️ | Kotlin/Native C interop (same as iOS) | 🟠 Mirrors iOS in source, but the target does not compile (~95 errors) |
| **Desktop (JVM)** | ✅ | — | 🟠 Shell only — all bridge methods are no-ops |
| **Web (JS)** | ⚠️ | REST stubs | 🔴 Experimental |

Non-mobile platforms are best-effort. Where one hits a structural wall, the blocker is
documented and work moves on rather than compromising mobile quality.

---

## Features

Broadly at parity with the Android client on Android and iOS. The full inventory lives in
[`STATUS.md`](STATUS.md); in summary:

**Calling** — audio/video calls, conferences with participant grid and moderator controls,
screen sharing (Android), Picture-in-Picture (Android), DTMF, hold/resume, call history,
CallKit (iOS), Telecom API / `ConnectionService` (Android).

**Messaging** — swarm conversations, text and emoji, replies, reactions, edit/delete, file
transfer with retry, audio recording, location sharing (OpenStreetMap / MapKit), typing
indicators, read receipts, drafts, media gallery, search, group management with admin roles.

**Accounts & contacts** — multi-account with switching, Jami and SIP account creation, import,
link-device, account export, change password, profile management, biometric auth, QR generation
and scanning, contact requests, trust/block, device management, certificate pinning and trust,
system-contact sync (Android).

**Notifications** — full Android channel setup, `UNUserNotificationCenter` on Apple platforms,
quiet hours with per-type control, full-screen incoming-call intents, answer/decline actions.

**Settings** — reactive `SettingsRepository` with DHT-synced preferences; privacy, call,
appearance, and per-conversation settings enforced throughout; debug-log viewer and a
recordings browser under Advanced.

---

## Architecture

```
jami-kmp/
├── shared/              # KMP shared module — ALL cross-platform code
│   └── src/
│       ├── commonMain/  # di · model · services · repository · domain · ui · utils
│       ├── commonTest/  # 70 test files (ViewModel, model, service, utility) — 97 across all source sets
│       ├── androidMain/ # JNI bridge, Android services
│       ├── iosMain/     # C interop bridge, Foundation/AVFoundation services
│       ├── macosMain/   # C interop bridge, AppKit services
│       ├── desktopMain/ # JVM bridge (stubs), AWT/Swing helpers
│       └── jsMain/      # WebSocket/REST bridge (experimental)
├── android-app/         # Android host — thin shell, wires Koin + renders JamiApp
├── desktop-app/         # Compose Desktop window + entry point
├── web-app/             # Kotlin/JS entry point
├── ios-app/             # Xcode project (iosApp.xcodeproj)
├── e2e-protocol/        # Shared protocol for the on-device test harness
└── e2e-runner/          # Host-side driver for real-device end-to-end scenarios
```

The platform hosts are deliberately thin: they wire up Koin and render `JamiApp`. Everything
substantive lives in `shared`.

| Concern | Approach |
|---|---|
| DI | Koin 4.1 — `jamiModule` (common) + `platformModule` (expect/actual) |
| State | `StateFlow` / `SharedFlow` + coroutines (no RxJava, no LiveData) |
| Navigation | Navigation Compose — type-safe `Screen` sealed class, 3 graphs (Loading / Onboarding / Main) |
| UI | Material 3 with `JamiTheme` composition locals (colors, typography, tokens) |
| Database | SQLDelight 2.0 — platform-specific `DatabaseDriverFactory` |
| Daemon | `DaemonBridge` expect class, 231 methods on `DaemonBridgeApi`, per-platform actuals |
| Testing | `runTest` + `advanceUntilIdle` + stub `DaemonBridgeApi` in `TestFixtures.kt` |

**Versions:** Kotlin 2.1.20 · Compose Multiplatform 1.9.0 · Koin 4.1.0 · Navigation Compose
2.9.1 · SQLDelight 2.0.2 · Coroutines 1.10.1 · AGP 8.7.3 · Android SDK 24/36/36 · JVM target 17

---

## Building

**Prerequisites:** JDK 17, Android SDK (compile/target 36), and a `local.properties` pointing at
your SDK. The Kotlin and Gradle toolchains are managed by the build.

### Android

```sh
./gradlew :android-app:assembleDebug
```

Firebase Cloud Messaging is **opt-in at build time**: the `google-services` plugin is applied
only if `android-app/google-services.json` exists. Without it the app still builds and runs —
`PushServiceManager` falls back to the persistent daemon — so the repo stays buildable with no
Firebase credentials.

### Desktop

```sh
./gradlew :desktop-app:run
```

The UI renders, but the daemon bridge is stubbed, so nothing network-facing works.

### Web

```sh
./gradlew :web-app:jsBrowserDevelopmentRun
```

Experimental; the bridge is REST stubs.

### iOS / macOS — read this before a fresh checkout

**A fresh clone cannot link the iOS targets.** The native libraries under
`shared/src/nativeInterop/cinterop/lib/` (device) and `lib-sim/` (simulator) are
machine-specific symlinks into a sibling **`jami-client-ios`** checkout and are deliberately not
committed. Nothing in the Gradle build creates them, so Kotlin **compiles cleanly and then fails
at link time** with undefined `libjami` symbols. This is a link error, never a compile error —
worth knowing before you spend an afternoon on it.

- **Simulator symlinks:** `python3 scripts/make_sim_links.py`. Paths derive from the script's own
  location; override with `JAMI_CLIENT_IOS` / `JAMI_XCFRAMEWORK`. It discovers the library set
  from the xcframework rather than enumerating it.
- **Device symlinks:** no script exists — they were created by hand into
  `jami-client-ios/DEPS/arm64-iPhoneOS/lib/`, and **this drifts.** When the daemon submodule
  moves, its dependency set changes and both the hand-made `lib/` and the `OTHER_LDFLAGS` in
  `project.pbxproj` go stale.
- **Release archives need ~6 GB of Gradle daemon heap**, and `org.gradle.jvmargs` is the only
  setting that controls it — the Kotlin/Native compile runs *inside* the Gradle daemon, so
  `kotlin.native.jvmArgs` and `kotlin.daemon.jvm.options` are inert for it. Too little heap dies
  in `DevirtualizationAnalysis`, a Release-only LTO pass, which is why Debug and simulator builds
  never show the problem. On an 8 GB machine put the override in `~/.gradle/gradle.properties`.
- The only committed binaries are `lib/libJamiBridge_ios.a` and `lib/libJamiBridge_iossim.a`, the
  compiled ObjC++ wrapper. Rebuilding them with
  `shared/src/nativeInterop/cinterop/JamiBridge/build-jamibridge.sh` **overwrites tracked
  files** — commit unrelated work first.

Full detail: [`shared/src/nativeInterop/cinterop/JamiBridge/README.md`](shared/src/nativeInterop/cinterop/JamiBridge/README.md)
and [`ios-app/fastlane/RELEASING.md`](ios-app/fastlane/RELEASING.md).

---

## Testing

Unit tests run against a stub daemon bridge, no real daemon required:

```sh
./gradlew :shared:allTests          # all targets
./gradlew :shared:desktopTest       # JVM only — fastest
```

`:shared:desktopTest` is **769 tests, 0 failures** as of 2026-10-26.

A separate harness drives **real scenarios against the live daemon on USB-connected Android
devices**, covering account setup, contact requests, and messaging across two physical devices:

```sh
./gradlew :e2e-runner:e2eList       # list available scenarios
./gradlew :e2e-runner:e2e           # run
```

See [`doc/end2endTesting.md`](doc/end2endTesting.md). The two layers are complementary — unit
tests assert logic in isolation, the harness asserts real behaviour against the DHT.

---

## Known limitations

- **Push notification delivery** — client integration (FCM token registration, APNs + PushKit)
  is done on both platforms, but the push is sent by the **DHT proxy, not the peer**, and the
  public proxy only holds Savoir-faire Linux's FCM credentials. Delivery needs a self-hosted
  `dhtnode --proxyserver` with this app's own keys, so calls and messages currently still rely
  on the daemon running. See [`doc/push-notifications.md`](doc/push-notifications.md).
- **iOS connectivity never reaches the daemon** — `NWPathMonitor` correctly updates
  `HardwareService`, but `JamiBridgeWrapper.h` exposes no `connectivityChanged` entry point, so
  the daemon is never told to re-resolve connections after a network change. Android is fully
  wired. Fix is one wrapper passthrough plus a `build-jamibridge.sh` rebuild.
- **iOS remote video rendering** — no Metal/CALayer `SinkTarget` yet; audio-only calls work fine.
  `VideoSurface.ios.kt` creates an `AVSampleBufferDisplayLayer` that nothing enqueues into, and
  outgoing frames are captured, copied and discarded. See
  [`ios_implementation_gap.md`](ios_implementation_gap.md) §5.
- **iOS call recording** — `toggleRecording` / `getIsRecording` return `false`; the wrapper
  exposes no entry point yet. Android recording works.
- **macOS target does not compile** — ~95 errors, mostly in `MacOSHardwareService.kt`. Since the
  iOS and macOS Darwin implementations are now substantively identical, the right fix is a shared
  `appleMain` source set; a start exists on `origin/macos-build-and-apple-source-set` and needs
  rebasing onto `main`.
- **Desktop daemon bridge** — all 100+ methods are no-ops. Architectural blocker: the
  SWIG-generated JNI classes conflict with KMP's Android plugin and need a separate JVM module.
  Deprioritised.
- **Chat plugins** — the Jami plugin system is not ported; the menu item shows a
  "not yet supported" snackbar.
- **Map view on Desktop/macOS** — no viable JVM or AppKit map library in scope; coordinates are
  shown as text.
- **Web/JS** — the entire bridge is REST stubs. Candidate for removal if a REST bridge server is
  not developed.

Current issue list: [`doc/KNOWN-ISSUES.md`](doc/KNOWN-ISSUES.md) ·
[`doc/TODO.md`](doc/TODO.md)

---

## Documentation

| Document | Contents |
|---|---|
| [`CLAUDE.md`](CLAUDE.md) | Development guide — ground rules, architecture, conventions, string-resource policy |
| [`STATUS.md`](STATUS.md) | Full feature inventory, per-platform status |
| [`doc/TODO.md`](doc/TODO.md) | Active work items |
| [`doc/KNOWN-ISSUES.md`](doc/KNOWN-ISSUES.md) | Open issues |
| [`doc/end2endTesting.md`](doc/end2endTesting.md) | On-device test harness |
| [`doc/push-notifications.md`](doc/push-notifications.md) | Push architecture and the proxy constraint |
| [`Permissions.md`](Permissions.md) | Runtime permission matrix per platform |
| [`ios_implementation_gap.md`](ios_implementation_gap.md) | iOS parity gaps against `jami-client-ios` |
| [`doc/mini-plans/`](doc/mini-plans/) | Per-area gap analyses against the Android client — start at [`doc/mini-plans/README.md`](doc/mini-plans/README.md) for the progress roll-up |
| [`doc/apple-bridge-rebuild.md`](doc/apple-bridge-rebuild.md) | The Obj-C++ wrapper rebuild: what landed 2026-10-14, and the Apple build hazards |
| [`doc/stabilization-findings-2026-09-04.md`](doc/stabilization-findings-2026-09-04.md) | Two-device findings F1–F5 and how the durable outbox closed F1/F4 |
| [`doc/plan_daemon_stability_sync.md`](doc/plan_daemon_stability_sync.md) | Five-phase daemon stability/sync plan and its per-phase status |

---

## Contributing

When implementing a feature, read the reference source first — `libjamiclient` for logic,
`jami-android/app` for UI — then apply judgement. Features are expected to ship complete:
runtime permissions, platform settings integration, lifecycle and connectivity handling,
accessibility, localisation via `Res.string.*` (never hardcoded), and empty/loading/error
states. See [`CLAUDE.md`](CLAUDE.md) for the full conventions.

---

## License

Licensed under the **GNU General Public License v3.0 or later** — see [`LICENSE`](LICENSE).

This matches upstream Jami. Jami and `libjami` are developed by
[Savoir-faire Linux](https://savoirfairelinux.com) and the Jami community.

**This is an unofficial, third-party client.** It is not produced, endorsed or supported by
Savoir-faire Linux or the Jami project. The copyright-attribution work this implies is tracked
in [`doc/TODO.md`](doc/TODO.md) → "Licensing & Copyright Attribution" and is still open.
