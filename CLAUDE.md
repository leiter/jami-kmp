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
│       │   ├── services/     # 20 services including ConversationFacade
│       │   ├── repository/   # SettingsRepository, DraftRepository
│       │   ├── domain/       # Use cases
│       │   ├── ui/
│       │   │   ├── JamiApp.kt           # Root Compose entry point (all platforms)
│       │   │   ├── navigation/          # Screen (23 routes), JamiNavigation
│       │   │   ├── screens/             # 25 screens
│       │   │   ├── viewmodel/           # 17 ViewModels
│       │   │   ├── components/          # Reusable components
│       │   │   │   ├── actions/         # JamiButton, JamiIconButton, JamiFilterChip
│       │   │   │   ├── content/         # JamiAvatar, JamiBadge, JamiToggle, JamiSectionTitle
│       │   │   │   ├── inputs/          # JamiSearchField, JamiMessageInput, JamiInputText
│       │   │   │   ├── navigation/      # JamiTopBar
│       │   │   │   ├── container/       # JamiScaffold
│       │   │   │   └── notification/    # JamiAlertDialog
│       │   │   └── theme/               # JamiTheme, JamiColors, JamiTypography, ThemeTokens
│       │   └── utils/        # Log, FileUtils, QRCodeUtils, HashUtils, StringUtils, …
│       ├── commonTest/       # 55 test files (ViewModel + model + service + utility tests)
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
| DI | Koin 4.0 — `jamiModule` (common) + `platformModule` (expect/actual) |
| State | `StateFlow` / `SharedFlow` + Kotlin coroutines (no RxJava, no LiveData) |
| Navigation | Jetpack Navigation Compose — type-safe `Screen` sealed class, 3 graphs: Loading / Onboarding / Main |
| UI | Material 3 with `JamiTheme` composition locals (colors, typography, tokens) |
| Database | SQLDelight 2.0 — platform-specific `DatabaseDriverFactory` |
| Testing | `runTest` + `advanceUntilIdle` + stub implementations in `TestFixtures.kt` |

### Key Tech Versions

- Kotlin 2.1.20 · Compose Multiplatform 1.9.0 · Koin 4.0.0
- Navigation Compose 2.9.1 · SQLDelight 2.0.2 · Coroutines 1.10.1
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

## Known Gaps (as of 2026-06-12)

Recently closed: SIP account awareness (AccountSettingsScreen shows SIP server/username, hides Jami identity/devices; ChatScreen shows call-only notice for legacy/SIP conversations; ChatViewModel falls back to contact URI lookup for non-swarm conversations), TLS/SRTP security section in AccountAdvancedSettingsScreen (SRTP toggle, TLS enable + port + CA/cert/key file pickers + method/ciphers/verify toggles), video call rendering (TextureView + daemon video sink), SIP account creation (full server/user/pass/port wizard), call controls (mute, speaker, camera flip, DTMF dial pad), device export / link-new-device sheet (AccountSettingsScreen), conference calls (multi-party grid + moderator controls), screen sharing (MediaProjection + daemon callback), message reactions UI, read receipt checkmarks (SENDING/DELIVERED/READ), location accuracy circle + center-on-me FAB, URI scheme deep links (`ring://`, `jami://`, `sip:`, `tel:`), chat media (full-screen image viewer + inline video), biometric authentication (lock on background, unlock prompt), Share-to-Jami (ACTION_SEND / ACTION_SEND_MULTIPLE), AccountSettingsScreen change-password and export-account flows, message editing UX, link previews, file-transfer auto-accept confirmation, permission handling (POST_NOTIFICATIONS onboarding + foreground service type declarations + READ/WRITE_CONTACTS), attended call transfer (TransferSheet shows active-call picker alongside blind-transfer URI input; CallViewModel.attendedTransfer() wired to daemon), system contacts sync toggle (Settings > System section, calls ContactService.loadContacts on enable), ringtone → notification channel (AndroidNotificationService.refreshCallsChannel() deletes + recreates jami_calls channel with AudioAttributes-wrapped URI when the setting changes; called on each incoming call notification).

Still open:

- **Push notifications** — FCM (Android) and APNs (iOS) not integrated; calls and messages only work when the daemon is running in the foreground.
- **CallKit (iOS)** — iOS CallKit not integrated; incoming calls on iOS do not use the native call UI and do not wake the device from background.
- **Telecom API / ConnectionService** — native Android dialer integration missing; calls do not appear in the system call log or native dialer.
- **Conversation categories / filtering** — filter chips are live (All / Unread / Groups); Requests filter not yet wired (pending requests are shown via a separate banner, not a filter chip).
- **Chat plugins** — Jami plugin system not ported to KMP. Menu item shows a "not yet supported" snackbar.
- **OsmMapView (Desktop/macOS)** — no viable JVM or AppKit map library in scope; shows coordinate text instead of a map.
- **Desktop DaemonBridge** — all 100+ methods are no-ops. Architectural blocker: SWIG-generated JNI classes conflict with KMP's Android plugin, requiring a separate JVM module. Deprioritised.
- **Web/JS platform** — entire daemon bridge is REST stubs. Explicitly experimental; candidate for removal if a REST bridge server is not developed.

---

## Daemon Bridge

The `DaemonBridge` expect class (100+ methods) is the gateway to `libjami`:

| Platform | Implementation | Status |
|----------|---------------|--------|
| Android | JNI via SWIG-generated bindings + `libjami-core-jni.so` | Working |
| iOS | Kotlin/Native C interop via `JamiBridge.def` / `JamiBridgeWrapper.mm` | Working |
| macOS | Kotlin/Native C interop (same as iOS) | Working |
| Desktop | JVM FFI or socket bridge | No-op stubs (deprioritised) |
| Web/JS | WebSocket / HTTP | REST stubs (experimental) |

When adding a new daemon capability, add the method to `DaemonBridge` and implement it in at minimum the Android and iOS source sets. Other platforms are best-effort.
