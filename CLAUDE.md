# Jami KMP — Development Guide

## Objective

Build a full-featured, cross-platform Jami client in **Kotlin Multiplatform + Compose Multiplatform**. The primary targets are **Android and iOS**. Desktop (JVM), macOS, and Web (JS) are secondary — if a platform hits a hard architectural wall (daemon bridge, platform API, toolchain), it may be deprioritised or dropped rather than blocking mobile progress.

---

## Ground Rules

- **All implementation happens in `jami-kmp`.** Other repos are read-only references.
- **Mobile first.** Android is the primary development platform; iOS is the close second. All features must work correctly on both before other platforms are considered.
- When implementing any feature, account for all related concerns up front — do not ship partial features (see "Feature Completeness" below).

---

## Platform Priority

| Priority | Platform | Status |
|----------|----------|--------|
| 1 | Android | Primary — always working |
| 2 | iOS | Primary — must work |
| 3 | Desktop (JVM) | Secondary — best effort |
| 4 | macOS | Secondary — best effort |
| 5 | Web/JS | Experimental — may be dropped |

If a non-mobile platform requires disproportionate effort or hits a structural wall (e.g. daemon bridge cannot be realised), document the blocker and move on rather than compromising mobile quality.

---

## Reference Projects

`libjamiclient` is the **primary reference** for shared/domain logic. Service interfaces, models, business rules, and data flow in `shared/` should be guided by it — it embodies years of edge-case handling that should not be reinvented. That said, `libjamiclient` is Java/Android-centric; diverge from it freely when a pattern does not translate cleanly to KMP, when a Kotlin-idiomatic approach is clearly better, or when a Java/Android API has no multiplatform equivalent. The goal is **behavioural parity**, not structural mirroring.

`jami-android-client` is the **UI/UX reference**. Screens, navigation flows, interactions, and settings should be resampled to match it faithfully. Diverge when a platform constraint makes an exact match impractical, or when the KMP approach produces a clearly better result.

When in doubt about any implementation detail — logic **or** UI — read the reference source first, then apply judgement.

---

## Architecture

### Module Structure

```
jami-kmp/
├── shared/               # KMP shared module — ALL cross-platform code lives here
│   └── src/
│       ├── commonMain/kotlin/net/jami/
│       │   ├── di/           # Koin modules (JamiModule, KoinInit, ViewModelHelper)
│       │   ├── model/        # Domain data classes (Account, Call, Conversation, …)
│       │   │   ├── interaction/   # TextMessage, DataTransfer, CallHistory, …
│       │   │   └── settings/      # @Serializable settings models
│       │   ├── services/     # 30 services including ConversationFacade, SendQueueService
│       │   ├── repository/   # SettingsRepository, DraftRepository
│       │   ├── domain/       # Use cases
│       │   ├── ui/
│       │   │   ├── JamiApp.kt           # Root Compose entry point (all platforms)
│       │   │   ├── navigation/          # Screen (32 routes), JamiNavigation
│       │   │   ├── screens/             # 34 screen files
│       │   │   ├── viewmodel/           # 20 ViewModels
│       │   │   ├── components/          # Reusable components
│       │   │   │   ├── actions/         # JamiButton, JamiIconButton, JamiFilterChip
│       │   │   │   ├── content/         # JamiAvatar, JamiBadge, JamiToggle, JamiSectionTitle
│       │   │   │   ├── inputs/          # JamiSearchField, JamiMessageInput, JamiInputText
│       │   │   │   ├── navigation/      # JamiTopBar
│       │   │   │   ├── container/       # JamiScaffold
│       │   │   │   └── notification/    # JamiAlertDialog
│       │   │   └── theme/               # JamiTheme, JamiColors, JamiTypography, ThemeTokens
│       │   └── utils/        # Log, FileUtils, QRCodeUtils, HashUtils, StringUtils, …
│       ├── commonTest/       # 75 test files (ViewModel + model + service + utility tests)
│       ├── androidMain/      # JNI bridge, Android services, SharedPreferences, JNI libs
│       ├── iosMain/          # C interop bridge, Foundation/AVFoundation services
│       ├── macosMain/        # C interop bridge, macOS AppKit services
│       ├── desktopMain/      # JVM/FFI daemon bridge, AWT/Swing helpers
│       └── jsMain/           # WebSocket daemon bridge (WIP), Web API services
├── android-app/          # Android host — thin shell, wires Koin + renders JamiApp
├── desktop-app/          # Desktop JVM host — Compose Desktop window + entry point
└── web-app/              # Web host — Kotlin/JS entry point
```

### Key Patterns

| Concern | Approach |
|---------|----------|
| DI | Koin 4.1 — `jamiModule` (common) + `platformModule` (expect/actual) |
| State | `StateFlow` / `SharedFlow` + Kotlin coroutines (no RxJava, no LiveData) |
| Navigation | Jetpack Navigation Compose — type-safe `Screen` sealed class, 3 graphs: Loading / Onboarding / Main |
| UI | Material 3 with `JamiTheme` composition locals (colors, typography, tokens) |
| Database | SQLDelight 2.0 — platform-specific `DatabaseDriverFactory` |
| Testing | `runTest` + `advanceUntilIdle` + stub implementations in `TestFixtures.kt` |

### Key Tech Versions

- Kotlin 2.1.20 · Compose Multiplatform 1.9.0 · Koin 4.1.0
- Navigation Compose 2.9.1 · SQLDelight 2.0.2 · Coroutines 1.10.1
- Android Gradle Plugin 8.7.3
- Android min/compile/target SDK: 24 / 36 / 36 · JVM target 17

---

## Feature Completeness

When implementing any screen or feature, always include **all** of the following concerns — do not defer them:

- **Runtime permissions** — camera, microphone, contacts, storage, notifications; handle denied/permanently-denied states gracefully
- **Platform settings integration** — notification channels (Android), ringtone, battery optimisation exemption, background execution
- **Account & profile state** — multi-account support, active-account switching, profile updates
- **Connectivity & lifecycle** — background/foreground transitions, daemon reconnect on network change, proper coroutine scope cleanup
- **Accessibility** — content descriptions, focus order, screen-reader compatibility
- **Localisation** — all user-visible strings via `Res.string.*` (never hardcoded)
- **Error & edge-case handling** — empty states, loading states, network errors, permission denial

A feature is **done** when it behaves like its `jami-android-client` counterpart end-to-end on Android and iOS, not just visually.

---

## String Resources

All user-visible strings live in `shared/src/commonMain/composeResources/`. The file structure mirrors `jami-android-client` exactly:

| File | Contents |
|------|----------|
| `strings.xml` | General UI strings not covered by the other files |
| `strings_account.xml` | Account settings, devices, export, security |
| `strings_call.xml` | In-call UI |
| `strings_content_description.xml` | Accessibility / content descriptions |
| `strings_preferences.xml` | App preferences / settings labels and summaries |

**Rules:**
- **No `strings_kmp.xml`.** That file is a legacy scratch pad and must be phased out. When adding a new string, place it in one of the five files above based on category.
- **Reuse Android key names.** Check `jami-client-android/jami-android/app/src/main/res/values/` first. If the same string exists there, use the identical key. This keeps translations in sync and makes it trivial to import new locales from the Android project.
- **When there is no Android equivalent** (new KMP-only screens/features), follow the same naming conventions: `screen_title_*`, `action_*`, `pref_*`, `account_*`, etc.
- **Locale folders** (`values-de/`, `values-fr/`, …) mirror the same 5-file structure. A locale file only needs the keys it overrides; everything else falls back to `values/`.
- `values/` is the canonical default (English). A key must exist there before it can be referenced as `Res.string.key` — adding it only in a locale folder will cause a compile error.

---

## Known Gaps (as of 2026-10-30)

All major mobile features are implemented, including CallKit (iOS) and Telecom API/ConnectionService (Android). The remaining gaps are:

- **Push notifications** — Android (FCM) and iOS (APNs + PushKit) client integration are both done (2026-07-27, see `doc/push-notifications.md`). Delivery still needs a push-capable DHT proxy holding the FCM/APNs credentials for this app, so calls and messages currently still rely on the daemon running. The iOS half now compiles and runs (it was written on a Linux host, where Apple targets are skipped, and needed five fixes on the first Mac build — see `ios_implementation_gap.md` §4.1).
- **iOS connectivity never reaches the daemon** — `NWPathMonitor` feeds `HardwareService.connectivityChanged()`, so `_connectivityState` and `setAccountsActive()` are correct, but `DaemonBridge.ios.kt` cannot forward it: `JamiBridgeWrapper.h` exposes no `connectivityChanged` entry point, so the call is a log line. The daemon is never told to re-resolve its connections after a network change. Android is fully wired (`ConnectivityManager.NetworkCallback` → JNI). Fix is one wrapper passthrough + a `build-jamibridge.sh` rebuild. Two more calls are now waiting
on the same rebuild, so batch them — the list has grown to five and is kept in
`doc/apple-bridge-rebuild.md`: `setDefaultModerator`, `publish` (the presence status note —
Android publishes it, iOS/macOS log and drop it, and their presence callback synthesises
`lineStatus` as `""`, so Apple can neither send nor receive a note until the wrapper is
regenerated), `sendFile`'s missing `parent` argument (a file sent as a reply arrives as a plain
file), `setAudioMeterState` plus its `AudioMeter` callback (the microphone level ring stays dark),
and `createConfFromParticipantList`.
- **macOS target does not compile** — ~95 errors, mostly in `MacOSHardwareService.kt`. Since the iOS and macOS Darwin implementations are now substantively identical, the right fix is a shared `appleMain` source set. A start exists on `origin/macos-build-and-apple-source-set` (`f0f66ce`, 2026-10-01); it predates the nine wrapper commits and needs rebasing onto `main`. `build-jamibridge.sh`'s macOS stage also fails independently, on UIKit code compiled against the macOS SDK — long-standing, not caused by recent work.
- **iOS remote video rendering** — no Metal/CALayer `SinkTarget` implementation yet; audio-only calls work fine. See `ios_implementation_gap.md`.
- **Chat plugins** — Jami plugin system not ported to KMP. Menu item shows a "not yet supported" snackbar.
- **OsmMapView (Desktop/macOS)** — no viable JVM or AppKit map library in scope; shows coordinate text instead of a map.
- **Desktop DaemonBridge** — all 100+ methods are no-ops. Architectural blocker: SWIG-generated JNI classes conflict with KMP's Android plugin, requiring a separate JVM module. Deprioritised.
- **Web/JS platform** — entire daemon bridge is REST stubs. Explicitly experimental; candidate for removal if a REST bridge server is not developed.
- **~~Sending into a not-yet-live 1:1 swarm~~ — closed 2026-09-18.** The daemon never delivers a message committed into a 1:1 swarm that has no peer device, so such sends are no longer handed to it: `SendQueueService` + `OutboxStore` hold them while the conversation is `Syncing` or the peer's role is `invited`, persist them, and send once the conversation is live — on `ConversationReady`, on the peer joining, or after the next smartlist load, so they survive a restart. Started from both hosts (`JamiApplication`, `IOSApplicationHelper`). Still outstanding: UI-driven verification on real devices — the `send-before-member-join` e2e scenario bypasses the outbox. See `doc/stabilization-findings-2026-09-04.md` (F1/F4).

## Daemon Bridge

The `DaemonBridge` expect class is the gateway to `libjami`. `DaemonBridgeApi` declares **235 methods**, and the Android actual calls **152 distinct `JamiService.*` entry points** (counted 2026-10-30):

| Platform | Implementation | Status |
|----------|---------------|--------|
| Android | JNI via SWIG-generated bindings + `libjami-core-jni.so` | Working |
| iOS | Kotlin/Native C interop via `JamiBridge.def` / `JamiBridgeWrapper.mm` | Working |
| macOS | Kotlin/Native C interop (same as iOS) | Does not compile (~95 Kotlin errors) |
| Desktop | JVM FFI or socket bridge | No-op stubs (deprioritised) |
| Web/JS | WebSocket / HTTP | REST stubs (experimental) |

When adding a new daemon capability, add the method to `DaemonBridge` and implement it in at minimum the Android and iOS source sets. Other platforms are best-effort.

### Building the iOS/macOS targets — read before a fresh checkout

**A fresh clone cannot link the iOS targets.** The native libraries under
`shared/src/nativeInterop/cinterop/lib/` (device) and `lib-sim/` (simulator) are
machine-specific symlinks into a sibling **`jami-client-ios`** checkout and are deliberately
not committed. Nothing in the Gradle build creates them, so Kotlin compiles cleanly and then
fails at *link* time with undefined libjami symbols.

- Simulator symlinks: `python3 scripts/make_sim_links.py`. Paths are derived from the script's
  own location; `JAMI_CLIENT_IOS` / `JAMI_XCFRAMEWORK` override them. It discovers the library
  set from the xcframework rather than enumerating it.
- Device symlinks: no script exists; they were created by hand into
  `jami-client-ios/DEPS/arm64-iPhoneOS/lib/`. **This drifts.** When the daemon submodule moves,
  its dependency set changes and the hand-made `lib/` plus the `OTHER_LDFLAGS` in
  `project.pbxproj` go stale — a link error, never a compile error. See the drift section in
  `shared/src/nativeInterop/cinterop/JamiBridge/README.md`.
- **Release archives need ~6 GB of Gradle daemon heap**, and `org.gradle.jvmargs` is the only
  setting that controls it — the Kotlin/Native compile runs *inside* the Gradle daemon here, so
  `kotlin.native.jvmArgs` and `kotlin.daemon.jvm.options` are inert for it. Too little heap dies
  in `DevirtualizationAnalysis`, a Release-only LTO pass, which is why Debug and simulator builds
  never show the problem. On an 8 GB machine put the override in `~/.gradle/gradle.properties`
  (it wins over the project's) rather than the repo. Full detail, including why `./gradlew --stop`
  cannot be trusted and how to tell a dead build from a slow one:
  `ios-app/fastlane/RELEASING.md`.
- The only committed binaries are `lib/libJamiBridge_ios.a` and `lib/libJamiBridge_iossim.a`,
  the compiled ObjC++ wrapper. Rebuilding them with
  `shared/src/nativeInterop/cinterop/JamiBridge/build-jamibridge.sh` **overwrites tracked
  files** — commit unrelated work first.

Full detail, including the symptom when it is missing:
`shared/src/nativeInterop/cinterop/JamiBridge/README.md`.

---

## iOS/macOS build facts worth not re-deriving (2026-10-14)

- The Obj-C++ wrapper `.a` files were rebuilt on a Mac and committed (`5b4a17e`): **1096 methods,
  up from 875**, with all 153 header-declared methods present. The Xcode iOS-simulator build links
  and runs. `doc/apple-bridge-rebuild.md` carries the per-commit checklist and the hazards.
- **`iosX64` was removed** (`022e684`). `scripts/make_sim_links.py` links arm64-simulator slices
  only, so that target could never link — and it was what a generic simulator destination resolved
  to. Use an explicit arm64 destination, e.g. `-destination 'platform=iOS Simulator,name=iPhone 17 Pro'`.
- `nm -gU` finds **no** Obj-C methods — they are local (`t`) symbols. Verify the archive with
  `nm <archive> | grep -cE '[-+]\[JamiBridgeWrapper '`, not a global-symbol listing.
- A green `linkDebugFrameworkIosSimulatorArm64` does **not** prove the rebuild worked: the framework
  is built `isStatic = true` and tolerates undefined symbols; the real resolution happens in Xcode's
  app link via `OTHER_LDFLAGS`.
- `libvpx` has no arm64-simulator slice by design (ffmpeg is configured `--disable-libvpx` there), so
  `make_sim_links.py` printing "Created 48 symlinks / No arm64-simulator slice (1): libvpx" is correct.
