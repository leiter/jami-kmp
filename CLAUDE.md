# Jami KMP Project - Development Guide

This document serves as a comprehensive guide for building a Kotlin Multiplatform (KMP) shared library for Jami, using the existing Android `libjamiclient` as the primary blueprint.

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
# The jami-kmp directory will be created in:
/Users/user289697/Documents/JAMI/jami-kmp/

# Clone existing Jami repositories for reference (already present):
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
│       │   ├── model/              # 17 data classes (Account, Call, Contact, etc.)
│       │   │   └── interaction/    # Interaction types (TextMessage, CallHistory, etc.)
│       │   ├── services/           # 11 services with expect declarations
│       │   ├── domain/             # Use cases
│       │   └── utils/              # 7 shared utilities
│       ├── commonTest/kotlin/net/jami/
│       │   ├── model/              # Model unit tests
│       │   ├── services/           # Service tests (mock-based)
│       │   └── utils/              # Utility tests
│       ├── androidMain/kotlin/net/jami/
│       │   ├── services/           # AndroidDeviceRuntimeService, AndroidHardwareService
│       │   │                       # DaemonBridge.android.kt, Settings.android.kt
│       │   └── utils/              # QRCodeUtils.android.kt, FileUtils, HashUtils, Time
│       ├── iosMain/kotlin/net/jami/
│       │   ├── services/           # IOSDeviceRuntimeService, DaemonBridge, Settings
│       │   └── utils/              # QRCodeUtils (CoreImage), HashUtils (CommonCrypto)
│       ├── macosMain/kotlin/net/jami/
│       │   ├── services/           # MacOSDeviceRuntimeService, DaemonBridge, Settings
│       │   └── utils/              # QRCodeUtils (CoreImage), HashUtils (CommonCrypto)
│       ├── desktopMain/kotlin/net/jami/
│       │   ├── services/           # DesktopDeviceRuntimeService, DesktopHardwareService
│       │   │                       # DaemonBridge.desktop.kt, Settings.desktop.kt
│       │   └── utils/              # QRCodeUtils (ZXing), FileUtils, HashUtils, Time
│       ├── jsMain/kotlin/net/jami/
│       │   ├── services/           # WebDeviceRuntimeService, DaemonBridge, Settings
│       │   └── utils/              # QRCodeUtils (pure Kotlin), HashUtils (pure Kotlin)
│       └── nativeInterop/cinterop/ # libjami.def for iOS/macOS cinterop
├── android-app/                    # Android Compose UI (placeholder)
├── ios-app/                        # SwiftUI wrapper (placeholder)
├── desktop-app/                    # Compose Desktop UI (placeholder)
├── web-app/                        # Compose for Web / JS (placeholder)
└── build-logic/convention/         # Gradle convention plugins
```

### Dependency Flow

```
┌─────────────────────────────────────────────────────────────────┐
│                        Platform Apps                             │
│  ┌──────────┐ ┌──────────┐ ┌──────────┐ ┌──────────┐ ┌────────┐│
│  │ Android  │ │   iOS    │ │ Desktop  │ │  macOS   │ │  Web   ││
│  │ Compose  │ │ SwiftUI  │ │ Compose  │ │ SwiftUI  │ │Compose ││
│  └────┬─────┘ └────┬─────┘ └────┬─────┘ └────┬─────┘ └───┬────┘│
└───────┼────────────┼────────────┼────────────┼───────────┼─────┘
        │            │            │            │           │
        ▼            ▼            ▼            ▼           ▼
┌─────────────────────────────────────────────────────────────────┐
│                      shared (commonMain)                         │
│  ┌─────────────┐  ┌─────────────┐  ┌─────────────┐              │
│  │   domain/   │  │  services/  │  │   model/    │              │
│  │  Use Cases  │──│ Business    │──│ Data Types  │              │
│  │             │  │ Logic       │  │             │              │
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
/Users/user289697/Documents/JAMI/jami-client-android/jami-android/libjamiclient/
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

### Key Files to Port

#### Models (25 files → direct port to commonMain)

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

#### Services (12 files → port with expect/actual)

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

#### Utilities (11 files → mostly common)

| File | Description | KMP Strategy |
|------|-------------|--------------|
| `VCardUtils.kt` | VCard parsing (9K) | Replace ez-vcard with KMP lib |
| `StringUtils.kt` | String operations | Common |
| `QRCodeUtils.kt` | QR generation | expect/actual |
| `FileUtils.kt` | File operations | Use okio |
| `HashUtils.kt` | Hashing | Common (kotlin-crypto) |
| `Log.kt` | Logging wrapper | expect/actual |
| `SwigNativeConverter.kt` | SWIG type conversions | Platform-specific |

### Dependencies to Replace

| Android/JVM | KMP Alternative | Notes |
|-------------|-----------------|-------|
| RxJava3 | Kotlin Coroutines + Flow | Major refactor |
| javax.inject | Koin or manual DI | Simpler for KMP |
| Gson | kotlinx.serialization | Native KMP support |
| java.io.File | okio | Cross-platform I/O |
| ez-vcard | Custom parser or expect/actual | No KMP equivalent |
| OrmLite | SQLDelight | KMP database |
| ZXing | expect/actual per platform | Platform QR libraries |

---

## Daemon Integration

### JNI Bindings (Android/Desktop)

**Location:** `/Users/user289697/Documents/JAMI/jami-daemon/bin/jni/`

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

**Location:** `/Users/user289697/Documents/JAMI/jami-daemon/src/jami/`

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

## Platform-Specific Notes

### Android

**Source sets:** `androidMain`, `androidUnitTest`, `androidInstrumentedTest`

**Integration points:**
- Hilt for dependency injection (app module)
- JNI via SWIG-generated `JamiService` class
- Android Camera2/CameraX for video
- MediaCodec for hardware encoding/decoding

**Key files to reference:**
```
jami-client-android/jami-android/app/src/main/java/cx/ring/
├── services/           # Android service implementations
├── fragments/          # UI (reference for Compose migration)
└── application/        # Hilt modules, Application class
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
jami-client-ios/Ring/Ring/
├── Services/           # iOS service layer (RxSwift-based)
├── Features/           # Feature modules
└── Database/           # Local storage
```

### Desktop (JVM)

**Source sets:** `desktopMain`, `desktopTest`

**Integration points:**
- Same JNI as Android (SWIG bindings)
- Java Sound API for audio
- JavaFX or OpenCV for camera (optional)
- Compose Desktop for UI

**Shared with Android:**
- JNI wrapper code can be identical
- Same `JamiService` SWIG-generated class
- Similar service implementations

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
jami-client-macos/src/
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
/Users/user289697/Documents/JAMI/jami-client-android/jami-android/libjamiclient/src/main/kotlin/net/jami/

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
/Users/user289697/Documents/JAMI/jami-daemon/bin/jni/

jni_interface.i                    # Main SWIG interface (19K)
callmanager.i                      # Call operations (12K)
configurationmanager.i             # Account/config operations (19K)
conversation.i                     # Messaging operations (9K)
make-swig.sh                       # Build script
```

### C Headers (for cinterop)

```
/Users/user289697/Documents/JAMI/jami-daemon/src/jami/

jami.h                             # Main API (276 lines)
callmanager_interface.h            # Call API (314 lines)
configurationmanager_interface.h   # Config API (574 lines)
conversation_interface.h           # Conversation API (241 lines)
account_const.h                    # Account constants
call_const.h                       # Call constants
```

### iOS Patterns (Swift reference)

```
/Users/user289697/Documents/JAMI/jami-client-ios/Ring/Ring/

Services/                          # Service layer (RxSwift)
Features/Conversations/            # Messaging UI
Features/Calls/                    # Call handling
Database/                          # Local storage patterns
```

---

## Verification Checklist

- [x] KMP project structure follows module diagram
- [x] `expect`/`actual` declarations for DaemonBridge
- [x] DaemonBridge stubs for Android (JNI), iOS (cinterop), Desktop (JNI), Web (REST)
- [x] Models ported from libjamiclient with RxJava → Flow (17 models)
- [x] Services ported with proper platform abstractions (11 services)
- [x] Build succeeds for all target platforms
- [x] Unit tests pass in commonTest (32 test classes)
- [x] Platform-specific tests pass (Android, iOS, macOS, Desktop, JS)
- [x] DeviceRuntimeService implemented for all 5 platforms
- [x] HardwareService implemented for Android and Desktop
- [x] QRCodeUtils implemented for all 5 platforms (CoreImage on iOS/macOS)
- [x] Settings expect/actual for all 5 platforms

---

## Implementation Progress

### Summary (Last Updated: February 2026)

| Category | Complete | Total | Status |
|----------|----------|-------|--------|
| Models | 17 | 17 | ✅ 100% |
| Services | 11 | 11 | ✅ 100% |
| Utilities | 7 | 7 | ✅ 100% |
| Test Classes | 32 | 32 | ✅ All Passing |
| Platform Builds | 5 | 5 | ✅ All Platforms |

### Phase 1: Core Models ✅ COMPLETE

All models ported from `libjamiclient/model/`:

| Model | Status | Notes |
|-------|--------|-------|
| Account.kt | ✅ Done | Full structure with Flow |
| ConfigKey.kt | ✅ Done | Complete enum (all config keys) |
| MediaAttribute.kt | ✅ Done | Media attributes for calls |
| SwarmMessage.kt | ✅ Done | Swarm message structure |
| Call.kt | ✅ Done | Full state machine, CallStatus enum |
| Contact.kt | ✅ Done | Contact with presence, username |
| Conversation.kt | ✅ Done | Conversation model |
| Uri.kt | ✅ Done | Jami URI parsing (jami:, sip:, swarm:) |
| Profile.kt | ✅ Done | User profile data class |
| Codec.kt | ✅ Done | Audio/video codec model |
| TrustRequest.kt | ✅ Done | Contact request model |
| Media.kt | ✅ Done | MediaType, audio/video handling |
| Interaction.kt | ✅ Done | Base class with InteractionType, InteractionStatus |
| TextMessage.kt | ✅ Done | Text message interaction |
| DataTransfer.kt | ✅ Done | File transfer with TransferStatus |
| CallHistory.kt | ✅ Done | Call history entry |
| ContactEvent.kt | ✅ Done | Contact events (added, removed, etc.) |

### Phase 2: Core Services ✅ COMPLETE

All services ported with RxJava → Flow conversion:

| Service | Status | Notes |
|---------|--------|-------|
| DaemonBridge | ✅ Done | expect/actual for all 5 platforms, 60+ methods |
| AccountService | ✅ Done | 50+ methods, 15+ events, full account management |
| CallService | ✅ Done | Call/conference operations with Flow |
| ConversationFacade | ✅ Done | Messaging logic with Flow |
| ContactService | ✅ Done | Full implementation with cache, presence, events |
| HistoryService | ✅ Done | SqlDelightHistoryService with full CRUD |
| HardwareService | ✅ Done | Full interface with Flow, data classes |
| NotificationService | ✅ Done | Full interface with all methods |
| DeviceRuntimeService | ✅ Done | Interface + all 5 platform implementations |
| Settings | ✅ Done | expect/actual for all 5 platforms |
| VCardService | ✅ Done | VCardUtils with parsing |

### Phase 3: Platform Services ✅ COMPLETE

| Service | Android | Desktop | iOS | macOS | Web |
|---------|---------|---------|-----|-------|-----|
| DeviceRuntimeService | ✅ Context | ✅ XDG/AppData | ✅ Foundation | ✅ Foundation | ✅ Virtual FS |
| HardwareService | ✅ AudioManager | ✅ JavaSound | ✅ NSUserDefaults | ✅ NSUserDefaults | ✅ localStorage |
| Settings | ✅ SharedPrefs | ✅ java.util.prefs | ✅ NSUserDefaults | ✅ NSUserDefaults | ✅ localStorage |
| QRCodeUtils | ✅ ZXing | ✅ ZXing | ✅ CoreImage | ✅ CoreImage | ✅ Pure Kotlin |
| DaemonBridge | ⬜ JNI Ready | ⬜ JNI Ready | ⬜ cinterop | ⬜ cinterop | ⬜ REST Ready |

Legend: ✅ = Full impl, ⬜ = Stub (awaiting native library)

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
| iOS cinterop | ⬜ Stub | ✅ Done | Awaiting libjami build |
| macOS cinterop | ⬜ Stub | ✅ Done | Awaiting libjami build |
| Web REST | ⬜ Stub | ✅ Done | Awaiting REST bridge server |

### Phase 6: Testing ✅ COMPLETE (32 Test Classes)

| Test Category | Test Classes | Status |
|---------------|--------------|--------|
| Model Tests | AccountTest, UriTest, CallTest, ContactTest, ConversationTest, MediaTest, MediaAttributeTest, ConfigKeyTest, TrustRequestTest, SwarmMessageTest, InteractionTest | ✅ Pass |
| Service Tests | CallServiceTest, AccountServiceTest, ContactServiceTest, SqlDelightHistoryServiceTest, HardwareServiceTest, NotificationServiceTest | ✅ Pass |
| Utility Tests | StringUtilsTest, HashUtilsTest, FileUtilsTest | ✅ Pass |
| Settings Tests | SettingsTest | ✅ Pass |

All tests passing on: Desktop, JS Browser, Android Debug/Release, macOS Arm64, iOS Simulator Arm64

### Recent Commits
```
3e44bf8 feat: add HardwareService implementations for iOS, macOS, and Web
4255f7b docs: update CLAUDE.md with current implementation status
9a800ab feat: implement platform-specific DeviceRuntimeService, HardwareService, and QRCodeUtils
429334a feat: implement SqlDelightHistoryService with full database operations
01b6bee feat: add SQLDelight database layer and ContactService tests
21e745a test: add AccountService unit tests
a6eec17 feat: implement ContactService and Settings with expect/actual pattern
222284f feat: add QRCodeUtils with expect/actual pattern
b73dd4b feat: add Codec model and VCardUtils for KMP
b50ca0a feat: expand AccountService with comprehensive account management API
401fe14 feat: expand DaemonBridge API with comprehensive daemon operations
ae252f7 feat: add Android JNI integration with SWIG bindings
```

### Next Steps (Requires External Dependencies)

1. **Native Daemon Integration** - Build libjami for each platform
2. **Android JNI** - Include SWIG-generated classes from jami-daemon
3. **iOS/macOS cinterop** - Build JamiAdapters.framework
4. **Web REST Bridge** - Implement REST server (separate project)

---

## Additional Resources

- [Kotlin Multiplatform Documentation](https://kotlinlang.org/docs/multiplatform.html)
- [Kotlin/Native C Interop](https://kotlinlang.org/docs/native-c-interop.html)
- [SWIG Documentation](http://www.swig.org/Doc4.0/SWIGDocumentation.html)
- [Jami Developer Documentation](https://git.jami.net/savoirfairelinux/jami-project)
