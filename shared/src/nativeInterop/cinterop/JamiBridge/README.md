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

## Native library setup — required before any iOS build

**A fresh clone cannot build the iOS targets.** Most of the native libraries this module
links against are machine-specific symlinks that are deliberately not committed, and nothing
in the Gradle build creates them. Kotlin/Native compiles fine and then fails at *link* time
with missing symbols.

### What is committed, and what is not

| Path | Tracked? | What it is |
|------|----------|------------|
| `lib/libJamiBridge_ios.a` | **Yes** | The compiled ObjC++ wrapper, arm64 device |
| `lib/libJamiBridge_iossim.a` | **Yes** | The compiled ObjC++ wrapper, arm64 simulator |
| `lib/*.a` (everything else) | No | Symlinks into `jami-client-ios/DEPS/arm64-iPhoneOS/lib/` — device libjami and its ~48 dependencies |
| `lib-sim/` (all of it) | No | Symlinks into `jami-client-ios/xcframework/*/ios-arm64-simulator/` — the simulator equivalents |

`lib/.gitignore` excludes `*.a` with an exception for the two bridge libraries. `lib-sim/`
is untracked in its entirety.

The two committed `.a` files are build outputs kept in git so that a normal Kotlin change
does not require a working ObjC++ toolchain. They are rebuilt by `build-jamibridge.sh`
(below) whenever `JamiBridgeWrapper.mm` or `.h` changes — that rebuild **overwrites tracked
binaries**, so commit unrelated work first.

### Recreating the symlinks

Both sets point at a sibling checkout of **`jami-client-ios`**, which must exist and must
already have been built (its `DEPS/` and `xcframework/` directories are build products, not
checked-in sources).

**Simulator** — `scripts/make_sim_links.py` regenerates `lib-sim/`:

```bash
python3 scripts/make_sim_links.py
# Created 48 symlinks
# No arm64-simulator slice (1): libvpx   <- expected, see below
```

It reports any library it could not find, which is the quickest way to tell whether the
`jami-client-ios` xcframework build is complete.

The script no longer hardcodes paths: it derives them from its own location, and
`JAMI_CLIENT_IOS` / `JAMI_XCFRAMEWORK` override the defaults. It also **discovers** the
library set and the simulator slice name from the xcframework directory instead of using a
fixed list — both have changed underneath us before (see the drift note below).

**Device** — `scripts/make_device_links.py` (added 2026-11-11) regenerates `lib/` from
`jami-client-ios/DEPS/arm64-iPhoneOS/lib/`, linking `libjami-core.a` as `lib/libjami.a` (the
`.def`, the build script and `-ljami` all expect the shorter name):

```bash
python3 scripts/make_device_links.py          # relink, then check
python3 scripts/make_device_links.py --check  # change nothing; exit 1 on drift
# DEPS:   …/jami-client-ios/DEPS/arm64-iPhoneOS/lib (48 libraries)
# No drift: lib/, DEPS and OTHER_LDFLAGS[sdk=iphoneos*] agree.
```

It discovers the library set, removes symlinks whose library left `DEPS`, never touches the
two tracked `libJamiBridge_*.a` files, and cross-checks `OTHER_LDFLAGS[sdk=iphoneos*]` in
`project.pbxproj` both ways: a `-l` with no library behind it (an undefined-symbol link error)
and a library in `DEPS` the app does not link (a new daemon dependency). `JAMI_CLIENT_IOS` /
`JAMI_DEPS_LIB` override the paths. It does not edit `project.pbxproj`.

### Dependency drift — the failure this actually causes

The daemon's dependency set changes, and a hand-made `lib/` does not. All three of these bit
on the first Mac build of the iOS host app (2026-09-08), each as an Xcode link error rather
than anything Gradle or the test suites could catch:

- **`http_parser` was dropped upstream** in favour of llhttp — `-lhttp_parser` stayed on the
  link line in `project.pbxproj` and had to be removed from all three blocks.
- **`vpx` has no arm64-simulator slice** — ffmpeg is configured `--disable-libvpx` there, so
  `-lvpx` must be absent from the *simulator* link line while remaining on the device one.
- **`yrs` (Y-CRDT) was added** — it was missing from both the link line and `lib/`. `lib-sim/`
  had picked it up automatically only because the script discovers rather than enumerates.

When the daemon submodule moves, run `python3 scripts/make_device_links.py` (it does the
`DEPS` / `lib/` / device `OTHER_LDFLAGS` comparison), then `scripts/make_sim_links.py`, and fix
any `OTHER_LDFLAGS` it reports by hand (simulator link lines are not checked). Refresh the
libjami headers at the same time: that same update changed four enum underlying types and
added a `botOwner` parameter to `updateProfile`, which is silent ABI drift the Kotlin
compiler cannot see.

### Symptom when this is missing

`:shared:compileKotlinIosSimulatorArm64` succeeds and
`:shared:linkDebugFrameworkIosSimulatorArm64` fails with undefined symbols from libjami or
its dependencies. Per the workspace playbook, link failures are the ones a JVM build never
catches — if the link step fails on a clean machine, check these symlinks before suspecting
the Kotlin code.

---

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
