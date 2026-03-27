# Jami KMP Project - Development Guide

This document serves as a comprehensive guide for building a Kotlin Multiplatform (KMP) shared library for Jami, using the existing Android `libjamiclient` as the primary blueprint.

---

## Project Status

**Last Updated:** March 2026

The jami-kmp project includes a **complete shared library** and **Compose Multiplatform UI**, with thin platform wrappers for Android and Desktop. Total codebase: ~42,600 LOC across ~206 files.

### Shared Library (complete)

| Milestone | Status |
|-----------|--------|
| Kotlin Source Code | ✅ **~160 files, ~35,600 LOC** |
| Models (21) | ✅ All with @Serializable |
| Services (13) | ✅ All with Flow-based APIs |
| Platform Abstractions | ✅ 5 platforms via expect/actual |
| Unit Tests (51 classes, ~174 tests) | ✅ All passing |
| Koin DI | ✅ All platforms configured |
| SQLDelight Database | ✅ Schemas defined |
| iOS/macOS cinterop | ✅ JamiBridge wrapper complete (71 methods) |
| Build Targets (8) | ✅ All compiling |

### Compose Multiplatform UI (complete)

| Component | Files | LOC |
|-----------|-------|-----|
| Screens (13) | WelcomeScreen, HomeScreen, ChatScreen, CallScreen, CreateAccountScreen, ImportAccountScreen, AccountSettingsScreen, AppSettingsScreen, ConversationDetailsScreen, NewConversationScreen, SearchScreen, BlockedContactsScreen, AboutScreen | 2,807 |
| ViewModels (12) | AppViewModel, ChatViewModel, CallViewModel, ConversationsViewModel, ContactsViewModel, ContactDetailsViewModel, AccountCreationViewModel, AccountSettingsViewModel, AppSettingsViewModel, ImportAccountViewModel, NewConversationViewModel, AboutViewModel | 2,073 |
| Components (13) | JamiButton, JamiIconButton, JamiFilterChip, JamiAvatar, JamiBadge, JamiSectionTitle, JamiToggle, JamiSearchField, JamiMessageInput, JamiInputText, JamiTopBar, JamiScaffold, JamiAlertDialog | 1,430 |
| Theme (5) | JamiTheme, JamiColors, JamiTypography, ThemeTokens, ThemeOverrides | ~500 |
| Navigation (2) | JamiNavigation, Screen | ~210 |
| **Total UI** | **45 files** | **~7,020** |

### Platform App Wrappers

| Module | Files | LOC | Status |
|--------|-------|-----|--------|
| **android-app** | 2 Kotlin + manifest + theme | ~115 | Working wrapper: MainActivity + JamiApplication (Koin init, daemon lifecycle). Pre-built native libs included (arm64-v8a, x86_64). |
| **desktop-app** | 1 Kotlin + build config | ~68 | Working wrapper: Main.kt (Koin init, Window + JamiApp). Distribution configured (DMG/MSI/DEB). |
| **web-app** | build config only | ~29 | Scaffold only |

### Pending (External Dependencies)

- Native libjami compilation for iOS/macOS/Desktop (Android .so files already present)
- SWIG Java class generation for Desktop (Android classes already generated)
- JamiBridge static library compilation for iOS/macOS
- iOS/macOS app wrappers (SwiftUI entry points)
- End-to-end integration testing with live daemon
- Desktop-specific features (system tray, menus, keyboard shortcuts)

---

## Project Overview

**Purpose:** Create a unified KMP shared library (`jami-kmp`) that enables feature parity across all Jami client platforms.

**Target Platforms:**
1. **Android** - JNI (existing SWIG bindings)
2. **iOS** - Kotlin/Native + cinterop to libjami
3. **Desktop (JVM)** - JNI (reuse Android bindings)
4. **macOS** - Kotlin/Native + cinterop
5. **Web** - Kotlin/JS (REST bridge or WebAssembly)

**Architecture:** Clean architecture with platform abstraction using Kotlin's `expect`/`actual` mechanism.

---

## Quick Start

### Repository Setup

```bash
# Clone existing Jami repositories for reference:
# - jami-client-android   (primary blueprint)
# - jami-daemon           (native library + JNI bindings)
# - jami-client-ios       (iOS patterns reference)
# - jami-client-macos     (macOS patterns reference)
```

### Build Commands

```bash
# Build all platforms
./gradlew build

# Android
./gradlew :shared:assembleDebug
./gradlew :shared:assembleRelease

# iOS (generates framework)
./gradlew :shared:linkDebugFrameworkIosArm64
./gradlew :shared:linkDebugFrameworkIosSimulatorArm64

# macOS
./gradlew :shared:linkDebugFrameworkMacosArm64

# Desktop JVM
./gradlew :shared:desktopTest

# Web/JS
./gradlew :shared:jsBrowserTest

# Android app
./gradlew :android-app:assembleDebug
./gradlew :android-app:installDebug

# Desktop app
./gradlew :desktop-app:run
./gradlew :desktop-app:packageDeb    # Linux
./gradlew :desktop-app:packageDmg    # macOS
./gradlew :desktop-app:packageMsi    # Windows

# Run all tests
./gradlew allTests
# Or individual platforms:
./gradlew :shared:desktopTest
./gradlew :shared:jsBrowserTest
./gradlew :shared:iosSimulatorArm64Test
./gradlew :shared:macosArm64Test
```

---

## Architecture

### Module Structure

```
jami-kmp/
├── shared/
│   └── src/
│       ├── commonMain/kotlin/net/jami/
│       │   ├── di/                 # Koin modules (JamiModule, KoinInit)
│       │   ├── model/              # 21 data classes (Account, Call, Contact, etc.)
│       │   │   ├── interaction/    # Interaction types (TextMessage, CallHistory, etc.)
│       │   │   └── settings/       # SettingsModels.kt (@Serializable settings)
│       │   ├── repository/         # SettingsRepository, DraftRepository
│       │   ├── services/           # 13 services with expect declarations
│       │   ├── database/           # DatabaseDriverFactory
│       │   ├── domain/             # Use cases
│       │   ├── ui/                 # Compose Multiplatform UI (~7,020 LOC)
│       │   │   ├── JamiApp.kt     # Root composable (entry point for all platforms)
│       │   │   ├── screens/       # 13 screens (Home, Chat, Call, Settings, etc.)
│       │   │   ├── viewmodel/     # 12 ViewModels (MVVM)
│       │   │   ├── components/    # 13 reusable UI components
│       │   │   │   ├── actions/   # JamiButton, JamiIconButton, JamiFilterChip
│       │   │   │   ├── content/   # JamiAvatar, JamiBadge, JamiToggle, JamiSectionTitle
│       │   │   │   ├── inputs/    # JamiSearchField, JamiMessageInput, JamiInputText
│       │   │   │   ├── navigation/# JamiTopBar
│       │   │   │   ├── container/ # JamiScaffold
│       │   │   │   └── notification/# JamiAlertDialog
│       │   │   ├── navigation/    # JamiNavigation, Screen (route definitions)
│       │   │   └── theme/         # JamiTheme, JamiColors, JamiTypography, ThemeTokens
│       │   └── utils/              # 7 shared utilities
│       ├── commonTest/kotlin/net/jami/
│       │   ├── model/              # Model unit tests
│       │   ├── services/           # Service tests (mock-based)
│       │   └── utils/              # Utility tests
│       ├── androidMain/kotlin/net/jami/
│       │   ├── di/                 # PlatformModule.android.kt
│       │   ├── services/           # AndroidDeviceRuntimeService, AndroidHardwareService
│       │   │                       # AndroidNotificationService, DaemonBridge, Settings
│       │   └── utils/              # QRCodeUtils.android.kt, FileUtils, HashUtils, Time
│       ├── iosMain/kotlin/net/jami/
│       │   ├── di/                 # PlatformModule.ios.kt
│       │   ├── services/           # IOSDeviceRuntimeService, IOSNotificationService
│       │   │                       # DaemonBridge, Settings
│       │   └── utils/              # QRCodeUtils (CoreImage), HashUtils (CommonCrypto)
│       ├── macosMain/kotlin/net/jami/
│       │   ├── di/                 # PlatformModule.macos.kt
│       │   ├── services/           # MacOSDeviceRuntimeService, MacOSNotificationService
│       │   │                       # DaemonBridge, Settings
│       │   └── utils/              # QRCodeUtils (CoreImage), HashUtils (CommonCrypto)
│       ├── desktopMain/kotlin/net/jami/
│       │   ├── di/                 # PlatformModule.desktop.kt
│       │   ├── services/           # DesktopDeviceRuntimeService, DesktopHardwareService
│       │   │                       # DesktopNotificationService, DaemonBridge, Settings
│       │   └── utils/              # QRCodeUtils (ZXing), FileUtils, HashUtils, Time
│       ├── jsMain/kotlin/net/jami/
│       │   ├── di/                 # PlatformModule.js.kt
│       │   ├── services/           # WebDeviceRuntimeService, WebNotificationService
│       │   │                       # DaemonBridge, Settings
│       │   └── utils/              # QRCodeUtils (pure Kotlin), HashUtils (pure Kotlin)
│       ├── nativeInterop/cinterop/ # iOS/macOS native integration
│       │   ├── JamiBridge.def      # cinterop definition
│       │   ├── headers/            # libjami C headers (16+ files)
│       │   └── JamiBridge/         # Objective-C++ wrapper
│       │       ├── JamiBridgeWrapper.h/.mm  # 71 methods bridging libjami
│       │       └── build-jamibridge.sh      # Build script
│       └── sqldelight/net/jami/database/
│           ├── Interaction.sq      # Message/interaction storage
│           ├── Conversation.sq     # Conversation data
│           └── Profile.sq          # Profile storage
├── android-app/                    # Android wrapper (MainActivity + JamiApplication + jniLibs)
├── desktop-app/                    # Desktop wrapper (Main.kt + distribution config)
├── web-app/                        # Web wrapper (scaffold only)
└── build-logic/convention/         # Gradle convention plugins
```

### Dependency Flow

```
┌─────────────────────────────────────────────────────────────────┐
│                     Platform App Wrappers                        │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐           │
│  │  android-app │  │  desktop-app │  │   web-app    │           │
│  │  (Activity + │  │  (Window +   │  │  (scaffold)  │           │
│  │   Koin+Daemon│  │   Koin init) │  │              │           │
│  │   lifecycle) │  │              │  │              │           │
│  └──────┬───────┘  └──────┬───────┘  └──────┬───────┘           │
└─────────┼─────────────────┼─────────────────┼───────────────────┘
          │                 │                 │
          └────────────┐    │    ┌────────────┘
                       ▼    ▼    ▼
┌─────────────────────────────────────────────────────────────────┐
│                      shared (commonMain)                         │
│  ┌─────────────┐  ┌─────────────┐  ┌─────────────┐              │
│  │    ui/      │  │  services/  │  │   model/    │              │
│  │ JamiApp()  │──│ Business    │──│ Data Types  │              │
│  │ 13 screens │  │ Logic       │  │             │              │
│  │ 12 VMs     │  │             │  │             │              │
│  └─────────────┘  └──────┬──────┘  └─────────────┘              │
└──────────────────────────┼──────────────────────────────────────┘
                           │ expect/actual
        ┌──────────────────┼──────────────────┐
        ▼                  ▼                  ▼
┌───────────────┐  ┌───────────────┐  ┌───────────────┐
│  androidMain  │  │    iosMain    │  │    jsMain     │
│  desktopMain  │  │   macosMain   │  │               │
│     (JNI)     │  │  (cinterop)   │  │ (REST/WASM)   │
└───────┬───────┘  └───────┬───────┘  └───────┬───────┘
        │                  │                  │
        ▼                  ▼                  ▼
┌───────────────┐  ┌───────────────┐  ┌───────────────┐
│    libjami    │  │    libjami    │  │  REST Bridge  │
│ (SWIG/JNI)    │  │  (C headers)  │  │  or WASM      │
└───────────────┘  └───────────────┘  └───────────────┘
```

---

## Blueprint Reference: libjamiclient

The Android client's `libjamiclient` is the primary blueprint. It's already written in Kotlin with a clean architecture.

### Location

```
../jami-client-android/jami-android/libjamiclient/
└── src/main/kotlin/net/jami/
    ├── account/        # 20 files - Account creation, wizard flows, profile management
    ├── model/          # 25 files - Core data models (Account, Call, Contact, etc.)
    │   └── interaction/  # Interaction types (TextMessage, CallHistory, DataTransfer)
    ├── services/       # 12 files - Business logic services
    ├── utils/          # 11 files - Utilities (VCard, String, QR, File, etc.)
    ├── settings/       # 7 files  - Settings presenters
    ├── call/           # 2 files  - Call presentation
    ├── contactrequests/# 4 files  - Contact request handling
    ├── conversation/   # 2 files  - Conversation presenter/view
    ├── home/           # 2 files  - Home screen
    ├── mvp/            # 2 files  - MVP base classes (RootPresenter, GenericView)
    ├── navigation/     # 3 files  - Navigation
    ├── smartlist/      # 3 files  - Conversation list
    ├── scan/           # 1 file   - QR scanning
    └── share/          # 1 file   - Sharing
```

**Total: 95 Kotlin files, ~756KB of source code**

### Blueprint Files (Reference)

The following files from libjamiclient were used as blueprints and have been ported to KMP:

#### Models (25 files → ✅ Ported to commonMain as 21 files)

| File | Size | Description |
|------|------|-------------|
| `Account.kt` | 34K | Account state, contacts, conversations management |
| `Conversation.kt` | 34K | Conversation model with message handling |
| `Conference.kt` | 10K | Conference/group call management |
| `Call.kt` | 8K | Call state machine |
| `Contact.kt` | 5K | Contact information |
| `Uri.kt` | 6K | Jami URI handling |
| `ConfigKey.kt` | 5K | Configuration constants |
| `Profile.kt` | 2K | User profile |
| `Media.kt` | 3K | Media types |

**Interactions subdirectory:**
- `Interaction.kt` (10K) - Base interaction model
- `TextMessage.kt` (2.5K) - Text messages
- `CallHistory.kt` (4K) - Call history entries
- `DataTransfer.kt` (5K) - File transfers
- `ContactEvent.kt` (3K) - Contact events

#### Services (12 files → ✅ Ported as 13 services with expect/actual)

| File | Size | Description | KMP Strategy |
|------|------|-------------|--------------|
| `AccountService.kt` | 78K | Account management (largest) | expect/actual for platform APIs |
| `ConversationFacade.kt` | 40K | Conversation operations | Mostly common |
| `CallService.kt` | 33K | Call handling | expect/actual for hardware |
| `DaemonService.kt` | 17K | JNI callback bridge | Full expect/actual |
| `HistoryService.kt` | 10K | Call/message history | Common with storage actual |
| `HardwareService.kt` | 10K | Camera, audio | Full expect/actual |
| `ContactService.kt` | 10K | Contact operations | Mostly common |
| `PreferencesService.kt` | 4K | Settings storage | expect/actual |
| `NotificationService.kt` | 3K | Notifications | Full expect/actual |
| `DeviceRuntimeService.kt` | 3K | File paths, permissions | Full expect/actual |
| `VCardService.kt` | 2K | VCard handling | Common |
| `LogService.kt` | 1K | Logging | expect/actual |

#### Utilities (11 files → ✅ Ported as 7 utilities)

| File | Description | KMP Strategy |
|------|-------------|--------------|
| `VCardUtils.kt` | VCard parsing (9K) | Replace ez-vcard with KMP lib |
| `StringUtils.kt` | String operations | Common |
| `QRCodeUtils.kt` | QR generation | expect/actual |
| `FileUtils.kt` | File operations | Use okio |
| `HashUtils.kt` | Hashing | Common (kotlin-crypto) |
| `Log.kt` | Logging wrapper | expect/actual |
| `SwigNativeConverter.kt` | SWIG type conversions | Platform-specific |

### Dependencies Replaced ✅

| Android/JVM Original | KMP Replacement | Status |
|---------------------|-----------------|--------|
| RxJava3 | Kotlin Coroutines + Flow | ✅ Complete |
| javax.inject | Koin 4.0.0 | ✅ Complete |
| Gson | kotlinx.serialization 1.8.0 | ✅ Complete |
| java.io.File | Platform-specific FileUtils | ✅ Complete |
| ez-vcard | Pure Kotlin VCardUtils parser | ✅ Complete |
| OrmLite | SQLDelight 2.0.2 | ✅ Complete |
| ZXing | expect/actual (ZXing JVM, CoreImage Apple, Pure Kotlin JS) | ✅ Complete |

---

## Daemon Integration

### JNI Bindings (Android/Desktop)

**Location:** `../jami-daemon/bin/jni/`

#### SWIG Interface Files

| File | Size | Purpose |
|------|------|---------|
| `jni_interface.i` | 19K | Main interface - module JamiService, type mappings, init() |
| `configurationmanager.i` | 19K | Account, codec, audio, security, settings APIs |
| `videomanager.i` | 19K | Video handling (Android ANativeWindow, FFmpeg) |
| `callmanager.i` | 12K | Call operations, conference management |
| `conversation.i` | 9K | Conversation/messaging, SwarmMessage struct |
| `datatransfer.i` | 4K | File transfer operations |
| `presencemanager.i` | 3K | Presence/subscription management |
| `plugin_manager_interface.i` | 3K | Plugin system |
| `managerimpl.i` | 1K | fini() - daemon finalization |
| `data_view.i` | 2K | Type mappings for byte arrays |

#### Build Process

```bash
# SWIG generates Java bindings
bin/jni/make-swig.sh
# Outputs:
#   - jami_wrapper.cpp (C++ JNI implementation)
#   - JamiServiceJNI.java (native method declarations)
#   - jamiservice_loader.c (JNI_OnLoad registration)
```

#### Callback Pattern (Director)

SWIG uses the "director" pattern for callbacks. Java classes extend SWIG-generated base classes:

```java
// Generated by SWIG
public class Callback {
    public void callStateChanged(String accountId, String callId, String state, int code) {}
    public void incomingCall(String accountId, String callId, String from) {}
    // ... 20+ callback methods
}

// Android implementation
class JamiCallbacks : Callback() {
    override fun callStateChanged(accountId: String, callId: String, state: String, code: Int) {
        // Forward to CallService via RxJava (→ Flow in KMP)
    }
}
```

### C Headers for cinterop (iOS/macOS)

**Location:** `../jami-daemon/src/jami/`

#### Main Headers

| Header | Lines | Purpose |
|--------|-------|---------|
| `jami.h` | 276 | Main API: init(), start(), fini(), version() |
| `configurationmanager_interface.h` | 574 | Account/configuration API (largest) |
| `callmanager_interface.h` | 314 | Call management API |
| `videomanager_interface.h` | 303 | Video management API |
| `conversation_interface.h` | 241 | Conversation/messaging API |
| `datatransfer_interface.h` | 176 | File transfer API |
| `plugin_manager_interface.h` | 87 | Plugin system API |
| `presencemanager_interface.h` | 83 | Presence API |

#### Constants Headers

| Header | Purpose |
|--------|---------|
| `account_const.h` | Account type/state constants |
| `call_const.h` | Call states, media types |
| `media_const.h` | Codec/format constants |
| `security_const.h` | Certificate/TLS constants |
| `presence_const.h` | Presence status constants |

#### cinterop Definition Example

```kotlin
// shared/src/nativeInterop/cinterop/libjami.def
headers = jami/jami.h jami/callmanager_interface.h jami/configurationmanager_interface.h
headerFilter = jami/**
compilerOpts = -I/path/to/jami-daemon/src
linkerOpts = -L/path/to/libjami -ljami
```

### expect/actual Pattern for DaemonBridge

```kotlin
// ═══════════════════════════════════════════════════════════════
// commonMain/kotlin/net/jami/services/DaemonBridge.kt
// ═══════════════════════════════════════════════════════════════

expect class DaemonBridge {
    fun init(callbacks: DaemonCallbacks): Boolean
    fun start(): Boolean
    fun stop()

    // Call operations
    fun placeCall(accountId: String, uri: String, mediaList: List<MediaAttribute>): String
    fun hangUp(accountId: String, callId: String)
    fun accept(accountId: String, callId: String)

    // Account operations
    fun addAccount(details: Map<String, String>): String
    fun removeAccount(accountId: String)
    fun getAccountDetails(accountId: String): Map<String, String>

    // Conversation operations
    fun sendMessage(accountId: String, conversationId: String, message: String, replyTo: String?)
    fun loadConversation(accountId: String, conversationId: String, fromMessage: String, size: Int)
}

interface DaemonCallbacks {
    // Call callbacks
    fun onCallStateChanged(accountId: String, callId: String, state: String, code: Int)
    fun onIncomingCall(accountId: String, callId: String, from: String)

    // Account callbacks
    fun onAccountsChanged()
    fun onRegistrationStateChanged(accountId: String, state: String, code: Int, detail: String)

    // Conversation callbacks
    fun onConversationReady(accountId: String, conversationId: String)
    fun onMessageReceived(accountId: String, conversationId: String, message: SwarmMessage)
}

// ═══════════════════════════════════════════════════════════════
// androidMain/kotlin/net/jami/services/DaemonBridge.kt (JNI)
// ═══════════════════════════════════════════════════════════════

actual class DaemonBridge {
    private val jamiService = JamiService()  // SWIG-generated

    actual fun init(callbacks: DaemonCallbacks): Boolean {
        val swigCallbacks = object : Callback() {
            override fun callStateChanged(accountId: String, callId: String, state: String, code: Int) {
                callbacks.onCallStateChanged(accountId, callId, state, code)
            }
            // ... map all callbacks
        }
        return JamiService.init(swigCallbacks)
    }

    actual fun placeCall(accountId: String, uri: String, mediaList: List<MediaAttribute>): String {
        val swigMediaList = mediaList.toSwigVector()
        return JamiService.placeCallWithMedia(accountId, uri, swigMediaList)
    }
    // ... implement all methods using JamiService
}

// ═══════════════════════════════════════════════════════════════
// iosMain/kotlin/net/jami/services/DaemonBridge.kt (cinterop)
// ═══════════════════════════════════════════════════════════════

actual class DaemonBridge {
    actual fun init(callbacks: DaemonCallbacks): Boolean {
        // Register C callbacks via cinterop
        libjami.registerCallStateCallback { accountId, callId, state, code ->
            callbacks.onCallStateChanged(
                accountId?.toKString() ?: "",
                callId?.toKString() ?: "",
                state?.toKString() ?: "",
                code
            )
        }
        return libjami.jami_init(InitFlag.NONE.value)
    }

    actual fun placeCall(accountId: String, uri: String, mediaList: List<MediaAttribute>): String {
        memScoped {
            val cMediaList = mediaList.toCStringVector(this)
            return libjami.placeCallWithMedia(accountId.cstr.ptr, uri.cstr.ptr, cMediaList)?.toKString() ?: ""
        }
    }
    // ... implement all methods using cinterop
}

// ═══════════════════════════════════════════════════════════════
// jsMain/kotlin/net/jami/services/DaemonBridge.kt (REST/WebSocket)
// ═══════════════════════════════════════════════════════════════

actual class DaemonBridge {
    private val client = HttpClient()
    private val wsConnection: WebSocketSession? = null

    actual suspend fun init(callbacks: DaemonCallbacks): Boolean {
        // Connect to REST bridge server
        wsConnection = client.webSocketSession("ws://localhost:8080/jami")
        wsConnection?.incoming?.collect { frame ->
            when (val event = Json.decodeFromString<DaemonEvent>(frame.text)) {
                is DaemonEvent.CallState -> callbacks.onCallStateChanged(...)
                // ... handle all events
            }
        }
        return true
    }

    actual suspend fun placeCall(accountId: String, uri: String, mediaList: List<MediaAttribute>): String {
        return client.post("http://localhost:8080/api/calls") {
            setBody(PlaceCallRequest(accountId, uri, mediaList))
        }.body<CallResponse>().callId
    }
}
```

---

## Development Guidelines

### Coding Conventions

Follow the existing `libjamiclient` style:

1. **Package structure:** `net.jami.<feature>`
2. **Naming:**
   - Services: `*Service.kt`
   - Models: Direct class names (`Account`, `Call`)
   - Presenters: `*Presenter.kt` (if using MVP)
   - ViewModels: `*ViewModel.kt` (if using MVVM)
3. **Coroutines over RxJava:**
   ```kotlin
   // Old (RxJava)
   fun getAccounts(): Observable<List<Account>>

   // New (Flow)
   fun getAccounts(): Flow<List<Account>>
   ```

### RxJava → Flow Migration

| RxJava | Kotlin Flow |
|--------|-------------|
| `Observable<T>` | `Flow<T>` |
| `Single<T>` | `suspend fun(): T` |
| `Completable` | `suspend fun()` |
| `Subject<T>` | `MutableStateFlow<T>` or `MutableSharedFlow<T>` |
| `BehaviorSubject<T>` | `MutableStateFlow<T>` |
| `PublishSubject<T>` | `MutableSharedFlow<T>` |
| `.subscribe()` | `.collect()` or `.launchIn(scope)` |
| `.map()` | `.map()` |
| `.flatMap()` | `.flatMapConcat()` / `.flatMapMerge()` |
| `.filter()` | `.filter()` |
| `.distinctUntilChanged()` | `.distinctUntilChanged()` |

### Testing Strategy

```
shared/src/
├── commonTest/          # Platform-agnostic tests
│   └── kotlin/net/jami/
│       ├── model/       # Model unit tests
│       ├── services/    # Service logic tests (with mocked DaemonBridge)
│       └── utils/       # Utility tests
├── androidUnitTest/     # Android-specific unit tests
├── iosTest/             # iOS-specific tests
└── jsTest/              # JS-specific tests
```

**Testing Libraries:**
- `kotlin.test` - Common assertions
- `kotlinx-coroutines-test` - Coroutine testing
- `mockk` (JVM) / platform equivalents - Mocking

### Adding New Features

1. **Define models in `commonMain/model/`**
2. **Create service interface in `commonMain/services/`** with `expect` for platform APIs
3. **Implement `actual` in each platform source set**
4. **Write tests in `commonTest/`** with platform-specific tests as needed
5. **Update DaemonBridge** if new daemon API calls are needed

---

## Dependency Injection (Koin)

The project uses [Koin](https://insert-koin.io/) for dependency injection across all platforms.

### Module Structure

| File | Location | Purpose |
|------|----------|---------|
| `JamiModule.kt` | `commonMain/kotlin/net/jami/di/` | Common services module |
| `KoinInit.kt` | `commonMain/kotlin/net/jami/di/` | Initialization helper |
| `PlatformModule.android.kt` | `androidMain/kotlin/net/jami/di/` | Android services |
| `PlatformModule.desktop.kt` | `desktopMain/kotlin/net/jami/di/` | Desktop services |
| `PlatformModule.ios.kt` | `iosMain/kotlin/net/jami/di/` | iOS services |
| `PlatformModule.macos.kt` | `macosMain/kotlin/net/jami/di/` | macOS services |
| `PlatformModule.js.kt` | `jsMain/kotlin/net/jami/di/` | Web services |

### Services Provided

**JamiModule (Common):**
- `CoroutineScope` - Application-wide scope with SupervisorJob
- `DaemonBridge` - Platform-specific daemon bridge
- `AccountService` - Account management
- `CallService` - Call handling
- `ContactService` - Contact management
- `ConversationFacade` - Messaging coordination
- `DaemonCallbacksImpl` - Callback orchestrator
- `SettingsRepository` - Daemon-backed settings persistence (JSON)
- `DraftRepository` - Message drafts with debounced saves

**PlatformModule (Per Platform):**
- `DeviceRuntimeService` - File paths, permissions
- `HardwareService` - Audio/video hardware
- `NotificationService` - System notifications (platform-specific implementations)
- `HistoryService` - Database operations
- `PreferencesService` - App preferences
- `Settings` - User settings storage
- `JamiDatabase` - SQLDelight database (not JS)

### Initialization

```kotlin
// ═══════════════════════════════════════════════════════════════
// Android (Application.onCreate)
// ═══════════════════════════════════════════════════════════════
class JamiApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        startKoin {
            androidContext(this@JamiApplication)
            modules(jamiModule, platformModule)
        }
    }
}

// ═══════════════════════════════════════════════════════════════
// Desktop / Web
// ═══════════════════════════════════════════════════════════════
fun main() {
    initKoin()
    // Start app...
}

// ═══════════════════════════════════════════════════════════════
// iOS / macOS (Swift)
// ═══════════════════════════════════════════════════════════════
@main
struct JamiApp: App {
    init() {
        KoinInitKt.doInitKoin()
    }

    var body: some Scene {
        WindowGroup {
            ContentView()
        }
    }
}
```

### Injecting Services

```kotlin
// In a class with KoinComponent
class MyViewModel : KoinComponent {
    private val accountService: AccountService by inject()
    private val callService: CallService by inject()
}

// Or get directly from Koin
val accountService = KoinPlatform.getKoin().get<AccountService>()
```

### Service Dependency Graph

```
jamiModule (common)
├── CoroutineScope
├── DaemonBridge
├── AccountService ← DaemonBridge, Scope
├── CallService ← DaemonBridge, AccountService, Scope
├── ContactService ← Scope, AccountService, DaemonBridge
├── ConversationFacade ← All services
├── DaemonCallbacksImpl ← All services, Scope
├── SettingsRepository ← DaemonBridge, Scope
└── DraftRepository ← DaemonBridge, Scope

platformModule (per platform)
├── DeviceRuntimeService
├── HardwareService
├── NotificationService (platform-specific: Android/iOS/macOS/Desktop/Web)
├── HistoryService ← JamiDatabase
├── PreferencesService
├── Settings
└── JamiDatabase ← DatabaseDriver
```

### Adding New Services to DI

1. **Define the service interface/class** in `commonMain/services/`
2. **Add to JamiModule** if it's cross-platform:
   ```kotlin
   // In JamiModule.kt
   single {
       MyNewService(
           dependency1 = get(),
           dependency2 = get()
       )
   }
   ```
3. **Or add to PlatformModule** if platform-specific:
   ```kotlin
   // In PlatformModule.android.kt (and others)
   single<MyPlatformService> {
       AndroidMyPlatformService(androidContext())
   }
   ```

---

## Platform-Specific Notes

### Android

**Source sets:** `androidMain`, `androidUnitTest`, `androidInstrumentedTest`

**App module:** `android-app/` -- thin wrapper with:
- `JamiApplication.kt` (58 LOC) -- Koin init + daemon start/stop lifecycle
- `MainActivity.kt` (17 LOC) -- `setContent { JamiApp() }` (shared Compose UI)
- Pre-built native libraries in `jniLibs/` (arm64-v8a + x86_64)
- Permissions: INTERNET, RECORD_AUDIO, CAMERA, VIBRATE, POST_NOTIFICATIONS

**Integration points:**
- Koin for dependency injection (not Hilt)
- JNI via SWIG-generated `JamiService` class
- Android Camera2/CameraX for video
- MediaCodec for hardware encoding/decoding

**Key files to reference:**
```
../jami-client-android/jami-android/app/src/main/java/cx/ring/
├── services/           # Android service implementations
├── fragments/          # UI (reference for Compose migration)
└── application/        # Application class
```

### iOS

**Source sets:** `iosMain`, `iosArm64Main`, `iosSimulatorArm64Main`, `iosTest`

**Integration points:**
- cinterop for libjami C headers
- Swift interop for UI layer
- AVFoundation for camera/audio
- CallKit for system call integration

**cinterop setup:**
```kotlin
// build.gradle.kts
kotlin {
    iosArm64 {
        compilations.getByName("main") {
            cinterops {
                create("libjami") {
                    defFile("src/nativeInterop/cinterop/libjami.def")
                    includeDirs("/path/to/jami-daemon/src")
                }
            }
        }
    }
}
```

**Swift patterns reference:**
```
../jami-client-ios/Ring/Ring/
├── Services/           # iOS service layer (RxSwift-based)
├── Features/           # Feature modules
└── Database/           # Local storage
```

### iOS/macOS JamiBridge cinterop

The iOS and macOS platforms use a JamiBridge Objective-C++ wrapper that bridges libjami C++ to Kotlin/Native via cinterop.

**Architecture:**
```
┌─────────────────────────────────────────────────────────────────┐
│                    DaemonBridge.ios.kt                           │
│                    DaemonBridge.macos.kt                         │
└───────────────────────────┬─────────────────────────────────────┘
                            │ cinterop
                            ▼
┌─────────────────────────────────────────────────────────────────┐
│              JamiBridgeWrapper (Objective-C++)                   │
│  - 71 methods bridging libjami API                              │
│  - JamiBridgeDelegateProtocol (30+ callbacks)                   │
│  - Enums: JBRegistrationState, JBCallState, JBLookupState, etc. │
└───────────────────────────┬─────────────────────────────────────┘
                            │ C++ linkage
                            ▼
┌─────────────────────────────────────────────────────────────────┐
│                    libjami (C++ Library)                         │
└─────────────────────────────────────────────────────────────────┘
```

**Files:**
```
shared/src/nativeInterop/cinterop/
├── JamiBridge.def              # cinterop definition
├── lib/                        # Static libraries (after build)
│   ├── libJamiBridge_ios.a     # iOS device (arm64)
│   ├── libJamiBridge_iossim.a  # iOS simulator (arm64 + x86_64)
│   ├── libJamiBridge_macos.a   # macOS (arm64 + x86_64)
│   └── libjami.a               # libjami native library
└── JamiBridge/
    ├── JamiBridgeWrapper.h     # Objective-C header (cinterop parses this)
    ├── JamiBridgeWrapper.mm    # Objective-C++ implementation
    ├── build-jamibridge.sh     # Build script
    └── README.md               # Build instructions
```

**Building:**
```bash
# Requires Xcode and pre-built libjami.a
cd shared/src/nativeInterop/cinterop/JamiBridge
./build-jamibridge.sh
```

### Desktop (JVM)

**Source sets:** `desktopMain`, `desktopTest`

**App module:** `desktop-app/` -- thin wrapper with:
- `Main.kt` (18 LOC) -- `initKoin()` + `Window(title = "Jami") { JamiApp() }`
- Distribution configured: DMG (macOS), MSI (Windows), DEB (Linux)
- Main class: `net.jami.desktop.MainKt`

**Integration points:**
- Same JNI as Android (SWIG bindings)
- Java Sound API for audio
- JavaFX or OpenCV for camera (optional)
- Compose Desktop for UI (shared JamiApp composable)

**Shared with Android:**
- JNI wrapper code can be identical
- Same `JamiService` SWIG-generated class
- Similar service implementations

**Missing desktop-specific features:**
- System tray integration
- Platform menus (File/Edit/Help)
- Keyboard shortcuts
- Window size/position persistence
- Native file dialogs

---

## Android/Desktop JNI Integration

### Overview

The Android and Desktop platforms share the same JNI integration using SWIG-generated bindings.
The integration follows this architecture:

```
┌─────────────────────────────────────────────────────────────────┐
│                        KMP Services                              │
│  ┌─────────────┐  ┌─────────────┐  ┌─────────────────┐         │
│  │AccountService│  │ CallService │  │ConversationFacade│         │
│  └──────┬──────┘  └──────┬──────┘  └────────┬────────┘         │
└─────────┼────────────────┼──────────────────┼───────────────────┘
          │                │                  │
          ▼                ▼                  ▼
┌─────────────────────────────────────────────────────────────────┐
│                     DaemonBridge (expect/actual)                 │
│  ┌─────────────────────────────────────────────────────────┐   │
│  │  DaemonCallbacks interface (events from native code)     │   │
│  └─────────────────────────────────────────────────────────┘   │
└───────────────────────────┬─────────────────────────────────────┘
                            │
                            ▼
┌─────────────────────────────────────────────────────────────────┐
│                  SWIG Director Callbacks                         │
│  ┌──────────────────┐  ┌─────────────┐  ┌──────────────────┐   │
│  │ConfigurationCallback│  │  Callback  │  │ConversationCallback│   │
│  └──────────────────┘  └─────────────┘  └──────────────────┘   │
│  ┌──────────────────┐  ┌─────────────┐  ┌──────────────────┐   │
│  │PresenceCallback   │  │VideoCallback│  │DataTransferCallback│   │
│  └──────────────────┘  └─────────────┘  └──────────────────┘   │
└───────────────────────────┬─────────────────────────────────────┘
                            │
                            ▼
┌─────────────────────────────────────────────────────────────────┐
│                    JamiService (SWIG-generated)                  │
│  ┌─────────────────────────────────────────────────────────┐   │
│  │  init(), placeCall(), hangUp(), sendMessage(), etc.     │   │
│  └─────────────────────────────────────────────────────────┘   │
└───────────────────────────┬─────────────────────────────────────┘
                            │ JNI
                            ▼
┌─────────────────────────────────────────────────────────────────┐
│                    libjami (Native C++ Library)                  │
└─────────────────────────────────────────────────────────────────┘
```

### SWIG Classes Required

The following SWIG-generated classes must be in `net.jami.daemon` package:

| Class | Purpose |
|-------|---------|
| `JamiService` | Static methods for all daemon operations |
| `Callback` | Director class for call/conference events |
| `ConfigurationCallback` | Director class for account/config events |
| `PresenceCallback` | Director class for presence events |
| `VideoCallback` | Director class for camera/video events |
| `DataTransferCallback` | Director class for file transfer events |
| `ConversationCallback` | Director class for messaging events |
| `StringMap` | `std::map<string,string>` wrapper |
| `StringVect` | `std::vector<string>` wrapper |
| `VectMap` | `std::vector<std::map>` wrapper |
| `IntVect`, `UintVect` | Integer vector wrappers |
| `Blob` | `std::vector<uint8_t>` for binary data |
| `SwarmMessage` | Swarm message structure |

### Integration Steps

1. **Build libjami for target platform:**
   ```bash
   # Android (requires NDK)
   cd jami-daemon
   ./configure --host=aarch64-linux-android --with-contrib=...
   make

   # Desktop (Linux)
   ./configure
   make
   ```

2. **Generate SWIG bindings:**
   ```bash
   cd jami-daemon/bin/jni
   ./make-swig.sh /path/to/jami-kmp/shared/src/main/java
   ```

3. **Include SWIG classes in build:**
   - Copy generated Java files to `shared/src/androidMain/java/net/jami/daemon/`
   - Or set up Gradle to include from external location

4. **Load native library:**
   ```kotlin
   // In DaemonBridge companion object
   init {
       System.loadLibrary("jami")
   }
   ```

5. **Initialize with callbacks:**
   ```kotlin
   // Create callback implementations extending SWIG director classes
   val configCallback = object : ConfigurationCallback() {
       override fun accountsChanged() {
           callbacks.onAccountsChanged()
       }
       // ... implement all callback methods
   }

   // Initialize daemon
   JamiService.init(
       configCallback,
       callCallback,
       presenceCallback,
       dataTransferCallback,
       videoCallback,
       conversationCallback
   )
   ```

### Type Conversions

SWIG types must be converted to/from Kotlin types:

```kotlin
// StringMap → Map<String, String>
fun StringMap.toNative(): Map<String, String> {
    val result = HashMap<String, String>()
    val keys = keys()
    for (i in 0 until keys.size.toInt()) {
        val key = keys[i]
        result[key] = get(key)
    }
    return result
}

// Map<String, String> → StringMap
fun Map<String, String>.toSwig(): StringMap {
    val map = StringMap()
    forEach { (k, v) -> map[k] = v }
    return map
}

// VectMap → List<Map<String, String>>
fun VectMap.toNative(): List<Map<String, String>> {
    return (0 until size.toInt()).map { get(it).toNative() }
}
```

### Key Files

| File | Purpose |
|------|---------|
| `DaemonBridge.android.kt` | Android JNI implementation |
| `DaemonBridge.desktop.kt` | Desktop JNI implementation |
| `DaemonCallbacks` | Callback interface (commonMain) |
| `jami-daemon/bin/jni/jni_interface.i` | Main SWIG interface |
| `jami-daemon/bin/jni/make-swig.sh` | SWIG generation script |

### macOS

**Source sets:** `macosMain`, `macosArm64Main`, `macosX64Main`, `macosTest`

**Integration points:**
- cinterop (same as iOS)
- AVFoundation for media
- Swift interop for native UI

**Patterns reference:**
```
../jami-client-macos/src/
├── AccountsVC.mm       # Account management (Objective-C++)
├── CallsVC.mm          # Call handling
└── views/              # UI components
```

### Web (Kotlin/JS)

**Source sets:** `jsMain`, `jsTest`

**Integration approach options:**

1. **REST Bridge** (recommended for initial implementation)
   - Separate server process running libjami
   - WebSocket for real-time events
   - REST API for operations

2. **WebAssembly** (future)
   - Compile libjami to WASM
   - Direct browser integration
   - More complex build setup

**Limitations:**
- No direct native bindings
- Depends on server/WASM runtime
- Audio/video handled via WebRTC

---

## Key Reference Files

### Primary Blueprint (Kotlin)

```
../jami-client-android/jami-android/libjamiclient/src/main/kotlin/net/jami/

# Core Services
services/DaemonService.kt          # JNI callback bridge pattern (17K)
services/AccountService.kt         # Account management (78K) - LARGEST
services/CallService.kt            # Call handling (33K)
services/ConversationFacade.kt     # Messaging logic (40K)

# Core Models
model/Account.kt                   # Account model (34K)
model/Conversation.kt              # Conversation model (34K)
model/Call.kt                      # Call state machine (8K)
model/Contact.kt                   # Contact model (5K)
model/ConfigKey.kt                 # Configuration keys (5K)

# MVP Base
mvp/RootPresenter.kt               # Base presenter
mvp/GenericView.kt                 # Base view interface
```

### JNI Bindings

```
../jami-daemon/bin/jni/

jni_interface.i                    # Main SWIG interface (19K)
callmanager.i                      # Call operations (12K)
configurationmanager.i             # Account/config operations (19K)
conversation.i                     # Messaging operations (9K)
make-swig.sh                       # Build script
```

### C Headers (for cinterop)

```
../jami-daemon/src/jami/

jami.h                             # Main API (276 lines)
callmanager_interface.h            # Call API (314 lines)
configurationmanager_interface.h   # Config API (574 lines)
conversation_interface.h           # Conversation API (241 lines)
account_const.h                    # Account constants
call_const.h                       # Call constants
```

### iOS Patterns (Swift reference)

```
../jami-client-ios/Ring/Ring/

Services/                          # Service layer (RxSwift)
Features/Conversations/            # Messaging UI
Features/Calls/                    # Call handling
Database/                          # Local storage patterns
```

---

## Verification Checklist

### Kotlin Layer (Complete)

- [x] KMP project structure follows module diagram
- [x] `expect`/`actual` declarations for all platform-specific code
- [x] DaemonBridge implementations for Android (JNI), iOS (cinterop), macOS (cinterop), Desktop (JNI), Web (REST)
- [x] Models ported from libjamiclient with RxJava → Flow (21 models)
- [x] Services ported with proper platform abstractions (13 services)
- [x] Build succeeds for all 8 target platforms
- [x] Unit tests pass in commonTest (32 test classes)
- [x] Platform-specific tests pass (Android, iOS, macOS, Desktop, JS)
- [x] DeviceRuntimeService implemented for all 5 platforms
- [x] HardwareService implemented for all 5 platforms (audio complete, video interface ready)
- [x] QRCodeUtils implemented for all 5 platforms (ZXing for JVM, CoreImage for Apple, Pure Kotlin for JS)
- [x] HashUtils implemented for all 5 platforms (MessageDigest for JVM, CommonCrypto for Apple, Pure Kotlin for JS)
- [x] FileUtils implemented for all 5 platforms
- [x] Settings expect/actual for all 5 platforms
- [x] Koin DI modules for all 5 platforms (jamiModule + 5 platformModules)
- [x] DaemonCallbacksImpl callback orchestrator
- [x] SwigTypeConverters for Android/Desktop JNI
- [x] NotificationService implemented for all 5 platforms
- [x] SettingsRepository with daemon-backed JSON persistence (KMP.* prefixed keys)
- [x] DraftRepository with 1500ms debounced saves
- [x] SQLDelight database schemas (Interaction.sq, Conversation.sq, Profile.sq)
- [x] iOS/macOS JamiBridge Objective-C++ wrapper (71 methods)
- [x] iOS/macOS cinterop definitions compiling

### Native Integration (Pending External Dependencies)

- [ ] Native libjami compiled for Android (requires NDK)
- [ ] Native libjami compiled for Desktop (requires platform toolchain)
- [ ] Native libjami compiled for iOS (requires Xcode)
- [ ] Native libjami compiled for macOS (requires Xcode)
- [ ] SWIG Java classes generated from jami-daemon
- [ ] JamiBridge static libraries built (libJamiBridge_ios.a, libJamiBridge_macos.a)
- [ ] REST bridge server for web platform

### UI Applications (Pending)

- [ ] android-app (Compose UI)
- [ ] ios-app (SwiftUI wrapper)
- [ ] desktop-app (Compose Desktop)
- [ ] web-app (Compose for Web)

---

## Implementation Progress

### Summary (Last Updated: February 2026)

**Total Source Files: 140 Kotlin files**

| Category | Complete | Total | Status |
|----------|----------|-------|--------|
| Models | 21 | 21 | ✅ 100% |
| Services | 13 | 13 | ✅ 100% |
| Utilities | 7 | 7 | ✅ 100% |
| Test Classes | 32 | 32 | ✅ All Passing |
| Platform Builds | 8 | 8 | ✅ All Targets |
| DI Modules | 7 | 7 | ✅ All Platforms |
| Repositories | 2 | 2 | ✅ SettingsRepository, DraftRepository |
| NotificationService | 5 | 5 | ✅ All 5 Platforms |
| iOS/macOS cinterop | 5 | 5 | ✅ All Native Targets |

**Source Set Breakdown:**
| Source Set | Files | Description |
|------------|-------|-------------|
| commonMain | 58 | Models, services, utilities, DI, database |
| commonTest | 32 | 32 test classes |
| androidMain | 10 | DaemonBridge JNI + platform services |
| iosMain | 10 | DaemonBridge cinterop + platform services |
| macosMain | 10 | DaemonBridge cinterop + platform services |
| desktopMain | 10 | DaemonBridge JNI + platform services |
| jsMain | 8 | DaemonBridge REST + platform services |

**Build Targets:** Android, iOS (Arm64, X64, SimulatorArm64), macOS (Arm64, X64), Desktop (JVM), Web (JS)

### Key Dependencies (gradle/libs.versions.toml)

| Dependency | Version | Purpose |
|------------|---------|---------|
| Kotlin | 2.1.20 | Language |
| Coroutines | 1.10.1 | Async/Flow |
| Serialization | 1.8.0 | JSON encoding |
| Koin | 4.0.0 | Dependency injection |
| SQLDelight | 2.0.2 | Database |
| Ktor | 3.0.1 | HTTP/WebSocket (web platform) |
| Okio | 3.9.1 | File I/O |
| Compose Multiplatform | 1.9.0 | UI framework (apps) |
| ZXing | 3.5.3 | QR codes (JVM platforms) |
| Android Compile SDK | 36 | Android target |
| Android Min SDK | 24 | Android minimum |

### Phase 1: Core Models ✅ COMPLETE (21 Models)

All models ported from `libjamiclient/model/` with `@Serializable` annotations:

| Model | Status | Notes |
|-------|--------|-------|
| Account.kt | ✅ Done | Full structure with StateFlow |
| Call.kt | ✅ Done | Full state machine, CallStatus enum |
| CallHistory.kt | ✅ Done | Call history entry |
| Codec.kt | ✅ Done | Audio/video codec model |
| Conference.kt | ✅ Done | Conference/group call management |
| ConfigKey.kt | ✅ Done | Complete enum (all config keys) |
| Contact.kt | ✅ Done | Contact with presence, username |
| ContactEvent.kt | ✅ Done | Contact events (added, removed, etc.) |
| Conversation.kt | ✅ Done | Conversation model |
| ConversationHistory.kt | ✅ Done | Conversation history entry |
| DataTransfer.kt | ✅ Done | File transfer with TransferStatus |
| Interaction.kt | ✅ Done | Base class with InteractionType, InteractionStatus |
| Media.kt | ✅ Done | MediaType, audio/video handling |
| MediaAttribute.kt | ✅ Done | Media attributes for calls |
| Phone.kt | ✅ Done | Phone number model |
| Profile.kt | ✅ Done | User profile data class |
| SwarmMessage.kt | ✅ Done | Swarm message structure |
| TextMessage.kt | ✅ Done | Text message interaction |
| TrustRequest.kt | ✅ Done | Contact request model |
| Uri.kt | ✅ Done | Jami URI parsing (jami:, sip:, swarm:) |
| settings/SettingsModels.kt | ✅ Done | All settings data classes (Theme, UiSettings, etc.) |

### Phase 2: Core Services ✅ COMPLETE (13 Services)

All services ported with RxJava → Flow conversion:

| Service | Status | Notes |
|---------|--------|-------|
| DaemonBridge | ✅ Done | expect/actual for all 5 platforms, 60+ methods |
| DaemonCallbacksImpl | ✅ Done | Centralized callback orchestrator for all daemon events |
| AccountService | ✅ Done | 50+ methods, 15+ events via Flow, full account management |
| CallService | ✅ Done | Call/conference operations with Flow |
| ConversationFacade | ✅ Done | Messaging coordination with Flow |
| ContactService | ✅ Done | Full implementation with cache, presence, events |
| HistoryService | ✅ Done | Interface + SqlDelightHistoryService with full CRUD |
| HardwareService | ✅ Done | Interface + 5 platform implementations |
| NotificationService | ✅ Done | Interface + 5 platform implementations |
| DeviceRuntimeService | ✅ Done | Interface + 5 platform implementations |
| PreferencesService | ✅ Done | App preferences management |
| Settings | ✅ Done | expect/actual for all 5 platforms |
| VCardService | ✅ Done | Merged into VCardUtils with parsing |

### Phase 3: Platform Services ✅ COMPLETE

| Service | Android | Desktop | iOS | macOS | Web |
|---------|---------|---------|-----|-------|-----|
| DeviceRuntimeService | ✅ Context | ✅ XDG/AppData | ✅ Foundation | ✅ Foundation | ✅ Virtual FS |
| HardwareService | ✅ AudioManager | ✅ JavaSound | ✅ NSUserDefaults | ✅ NSUserDefaults | ✅ localStorage |
| Settings | ✅ SharedPrefs | ✅ java.util.prefs | ✅ NSUserDefaults | ✅ NSUserDefaults | ✅ localStorage |
| QRCodeUtils | ✅ ZXing | ✅ ZXing | ✅ CoreImage | ✅ CoreImage | ✅ Pure Kotlin |
| NotificationService | ✅ NotificationManager | ✅ SystemTray | ✅ UNUserNotificationCenter | ✅ UNUserNotificationCenter | ✅ Web Notifications API |
| DaemonBridge | ⬜ JNI Ready | ⬜ JNI Ready | ✅ JamiBridge cinterop | ✅ JamiBridge cinterop | ⬜ REST Ready |

Legend: ✅ = Full impl, ⬜ = Stub (awaiting native library)

**Note:** iOS/macOS DaemonBridge uses JamiBridge Objective-C++ wrapper via cinterop. The wrapper provides 71 methods bridging libjami C++ to Kotlin/Native. Native libraries need to be compiled with `build-jamibridge.sh`.

Note: HardwareService video/camera methods are stubs on all platforms - actual camera integration requires:
- Android: Camera2/CameraX
- iOS/macOS: AVFoundation/AVCaptureSession
- Web: WebRTC getUserMedia

### Phase 4: Utilities ✅ COMPLETE

| Utility | Status | Notes |
|---------|--------|-------|
| Log.kt | ✅ Done | Simple logging |
| Time.kt | ✅ Done | currentTimeMillis expect/actual |
| StringUtils.kt | ✅ Done | capitalize, toPassword, getFileExtension, isOnlyEmoji, truncate, isJamiId, toJamiUri |
| FileUtils.kt | ✅ Done | expect/actual: copyFile, moveFile, readBytes, writeBytes, joinPath |
| HashUtils.kt | ✅ Done | MD5, SHA-1, SHA-256, SHA-512 (not JS) |
| VCardUtils.kt | ✅ Done | VCard parsing |
| QRCodeUtils.kt | ✅ Done | All 5 platforms (ZXing, CoreImage, Pure Kotlin) |

**Platform implementations:**
- Android/Desktop: java.security.MessageDigest, java.io.File, ZXing
- iOS/macOS: CommonCrypto (cinterop), Foundation, CoreImage CIFilter
- JS: Pure Kotlin implementation (SHA-512 unsupported)

### Phase 5: Platform Integration

| Platform | DaemonBridge | Services | Status |
|----------|--------------|----------|--------|
| Android JNI | ⬜ Ready | ✅ Done | Awaiting SWIG classes |
| Desktop JNI | ⬜ Ready | ✅ Done | Awaiting native library |
| iOS cinterop | ✅ JamiBridge | ✅ Done | Compiles, awaiting native lib |
| macOS cinterop | ✅ JamiBridge | ✅ Done | Compiles, awaiting native lib |
| Web REST | ⬜ Stub | ✅ Done | Awaiting REST bridge server |

**iOS/macOS JamiBridge Integration:**
- `JamiBridgeWrapper.h/.mm` - Objective-C++ wrapper (71 methods)
- `JamiBridgeDelegateProtocol` - 30+ callback methods
- cinterop bindings generated and compiling
- Static libraries: `libJamiBridge_ios.a`, `libJamiBridge_iossim.a`, `libJamiBridge_macos.a`
- Build script: `shared/src/nativeInterop/cinterop/JamiBridge/build-jamibridge.sh`

### Phase 6: Testing ✅ COMPLETE (51 Test Classes, ~174 tests)

| Test Category | Test Classes | Status |
|---------------|--------------|--------|
| Model Tests (15) | AccountTest, UriTest, CallTest, ContactTest, ConversationTest, MediaTest, MediaAttributeTest, ConfigKeyTest, TrustRequestTest, SwarmMessageTest, InteractionTest, PhoneTest, CodecTest, ConferenceTest, + more | ✅ Pass |
| Service Tests (6) | AccountServiceTest, ContactServiceTest, ConversationFacadeTest, SqlDelightHistoryServiceTest, HardwareServiceTest, NotificationServiceTest | ✅ Pass |
| Service Integration Tests (5) | AccountServiceIntegrationTest, CallServiceIntegrationTest, ContactServiceIntegrationTest, ConversationFacadeIntegrationTest, SettingsRepositoryIntegrationTest | ✅ New |
| ViewModel Tests (13) | AboutViewModelTest, AppViewModelTest, ProfileSetupViewModelTest, AppSettingsViewModelTest, ImportAccountViewModelTest, AccountCreationViewModelTest, ContactsViewModelTest, ContactDetailsViewModelTest, ConversationsViewModelTest, AccountSettingsViewModelTest, CallViewModelTest, NewConversationViewModelTest, ChatViewModelTest | ✅ New |
| ViewModel Test Fixture (1) | TestFixtures.kt (factory functions for services and scopes) | ✅ New |
| Utility Tests (5) | StringUtilsTest, HashUtilsTest, FileUtilsTest, QRCodeUtilsTest, VCardUtilsTest | ✅ Pass |
| Settings Tests (1) | SettingsTest | ✅ Pass |

All tests passing on: Desktop, JS Browser, Android Debug/Release, macOS Arm64, iOS Simulator Arm64

### Phase 7: Notifications & Settings ✅ COMPLETE

#### NotificationService (All 5 Platforms)

| Platform | File | Technologies |
|----------|------|--------------|
| Android | `AndroidNotificationService.kt` | NotificationManager, NotificationChannel, NotificationCompat |
| iOS | `IOSNotificationService.kt` | UNUserNotificationCenter, categories/actions |
| macOS | `MacOSNotificationService.kt` | UNUserNotificationCenter (shared with iOS) |
| Desktop | `DesktopNotificationService.kt` | java.awt.SystemTray, TrayIcon |
| Web | `WebNotificationService.kt` | Web Notifications API (JsNotification external class) |

#### Settings & Preferences (Daemon-Only Storage)

**Architecture:** Settings stored in jami-daemon as JSON payloads with `KMP.` prefix keys.
- Auto-syncs across devices via DHT
- No SQLite needed for settings/preferences

**Files Created:**
- `model/settings/SettingsModels.kt` - All @Serializable data classes:
  - KmpMeta, UiSettings, Theme, PrivacySettings, NotificationSettings
  - CallSettings, FileTransferSettings, ConversationSettings
  - Draft, DraftsContainer, AllSettings, SettingsKeys
- `repository/SettingsRepository.kt` - Daemon-backed JSON persistence
- `repository/DraftRepository.kt` - Message drafts with 1500ms debounce

**Storage Keys:**
| Key | Content |
|-----|---------|
| `KMP.Meta` | Schema version, migration info |
| `KMP.Settings.UI` | Theme, fontSize, language |
| `KMP.Settings.Privacy` | readReceipts, blockedContacts |
| `KMP.Settings.Notifications` | enabled, sound, quietHours |
| `KMP.Settings.Calls` | videoEnabled, autoAnswer |
| `KMP.Settings.FileTransfer` | autoAccept, maxSize |
| `KMP.Drafts` | Message drafts per conversation |

### Completed Milestones

1. **Core Infrastructure** - Project structure, Gradle configuration, dependency management
2. **Models** - All 21 data classes with @Serializable annotations
3. **Services** - All 13 services with Flow-based reactive APIs
4. **Utilities** - All 7 utilities with platform-specific implementations
5. **Platform Abstractions** - expect/actual for DaemonBridge, Settings, FileUtils, HashUtils, QRCodeUtils, Time
6. **Koin DI** - jamiModule (common) + 5 platformModules with complete dependency graph
7. **Database** - SQLDelight schemas for Interaction, Conversation, Profile
8. **Testing** - 32 test classes covering models, services, utilities
9. **iOS/macOS cinterop** - JamiBridgeWrapper Objective-C++ (71 methods) with cinterop bindings
10. **NotificationService** - All 5 platforms (NotificationManager, UNUserNotificationCenter, SystemTray, Web Notifications)
11. **Settings Persistence** - Daemon-backed JSON storage with KMP.* prefixed keys

### Next Steps (Requires External Dependencies)

1. **Compile JamiBridge Static Libraries**
   ```bash
   cd shared/src/nativeInterop/cinterop/JamiBridge
   ./build-jamibridge.sh
   ```
   Requires: Xcode, libjami.a built for each target

2. **Android JNI** - Include SWIG-generated classes from jami-daemon

3. **Test iOS/macOS Integration** - Run on device/simulator with native libraries

4. **Web REST Bridge** - Implement REST server (separate project)

---

## Additional Resources

- [Kotlin Multiplatform Documentation](https://kotlinlang.org/docs/multiplatform.html)
- [Kotlin/Native C Interop](https://kotlinlang.org/docs/native-c-interop.html)
- [SWIG Documentation](http://www.swig.org/Doc4.0/SWIGDocumentation.html)
- [Jami Developer Documentation](https://git.jami.net/savoirfairelinux/jami-project)
