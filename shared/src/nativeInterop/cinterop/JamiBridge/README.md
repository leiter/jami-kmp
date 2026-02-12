# JamiBridge - Objective-C++ Bridge for Kotlin/Native

This directory contains the JamiBridge Objective-C++ wrapper that provides a clean Objective-C interface to libjami for use with Kotlin/Native cinterop.

## Overview

Since Kotlin/Native cinterop only supports C and Objective-C (not C++), we need an Objective-C wrapper layer around the libjami C++ library. The `JamiBridgeWrapper` class provides this bridge with:

- Clean Objective-C interfaces that cinterop can parse
- Delegate protocol for callbacks from the daemon
- Type-safe enums and data classes
- Complete coverage of daemon functionality

## Files

- `JamiBridgeWrapper.h` - Objective-C header (used by cinterop)
- `JamiBridgeWrapper.mm` - Objective-C++ implementation (links to libjami)
- `NativeFileLogger.h/m` - Optional file logging utility

## Building JamiBridge Static Library

### Prerequisites

1. **libjami.a** - The libjami static library built for iOS/macOS
   - Location: `lib/libjami.a`
   - This should be built from jami-daemon for the target platform

2. **libjami headers** - The C++ headers from jami-daemon
   - Location: `headers/` (jami.h, callmanager_interface.h, etc.)

3. **Xcode Command Line Tools** - For clang++ compiler

### Build Steps

#### Option 1: Using the build script

```bash
cd shared/src/nativeInterop/cinterop/JamiBridge
./build-jamibridge.sh
```

#### Option 2: Manual build

```bash
# Navigate to the cinterop directory
cd shared/src/nativeInterop/cinterop

# Compile JamiBridgeWrapper.mm to object file
clang++ -c JamiBridge/JamiBridgeWrapper.mm \
    -o lib/JamiBridgeWrapper.o \
    -I headers \
    -I JamiBridge \
    -std=c++17 \
    -fobjc-arc \
    -fmodules \
    -target arm64-apple-ios14.0

# Create static library
ar rcs lib/libJamiBridge.a lib/JamiBridgeWrapper.o

# Verify
ar -t lib/libJamiBridge.a
```

### Enabling cinterop in build.gradle.kts

Once `libJamiBridge.a` is built and placed in `lib/`:

1. Edit `shared/build.gradle.kts`
2. Change `val enableJamiBridgeCinterop = false` to `true`
3. Rebuild the project

### Architecture Notes

The JamiBridge follows the delegate pattern:

```
Kotlin Code
    │
    ▼
JamiBridgeWrapper (Objective-C)
    │ implements JamiBridgeDelegate
    ▼
libjami (C++)
    │ C++ callbacks
    ▼
JamiBridgeWrapper
    │ calls delegate methods
    ▼
Kotlin Code (via cinterop)
```

## Usage in Kotlin/Native

Once cinterop is enabled, you can use JamiBridge like this:

```kotlin
import net.jami.bridge.*

class DaemonBridgeImpl : NSObject(), JamiBridgeDelegateProtocol {
    private val bridge = JamiBridgeWrapper.shared()

    init {
        bridge.delegate = this
    }

    fun init(dataPath: String) {
        bridge.initDaemonWithDataPath(dataPath)
        bridge.startDaemon()
    }

    // Delegate callbacks
    override fun onRegistrationStateChanged(
        accountId: String,
        state: JBRegistrationState,
        code: Int,
        detail: String
    ) {
        // Handle registration state change
    }

    override fun onIncomingCall(
        accountId: String,
        callId: String,
        peerId: String,
        peerDisplayName: String,
        hasVideo: Boolean
    ) {
        // Handle incoming call
    }
}
```

## License

Copyright (C) 2004-2025 Savoir-faire Linux Inc.
GNU General Public License v3.0
