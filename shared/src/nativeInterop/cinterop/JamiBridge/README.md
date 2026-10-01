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
| `lib-macos/libJamiBridge_macos.a` | **Yes** | The compiled ObjC++ wrapper, arm64 macOS |
| `lib-macos/*.a` (everything else) | No | Symlinks into the daemon's **native macOS** contrib prefix plus its `libjami.a` |

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
# Created 49 symlinks
# All libs found
```

It reports any library it could not find, which is the quickest way to tell whether the
`jami-client-ios` xcframework build is complete.

**macOS** — `scripts/make_macos_links.py` regenerates `lib-macos/`:

```bash
python3 scripts/make_macos_links.py
```

macOS does **not** use the xcframeworks: those contain only iOS slices. It needs a native
`arm64-apple-darwin` build of the daemon's contrib tree plus the daemon's own `libjami.a`.
See "Building the macOS native libraries" below — that build does not exist in a fresh
`jami-client-ios` checkout and has to be run once.

The script no longer hardcodes paths: it derives them from its own location, and
`JAMI_CLIENT_IOS` / `JAMI_XCFRAMEWORK` override the defaults. It also **discovers** the
library set and the simulator slice name from the xcframework directory instead of using a
fixed list — both have changed underneath us before (see the drift note below).

**Device** — there is **no** equivalent script. The symlinks in `lib/` were created by hand
and point into `jami-client-ios/DEPS/arm64-iPhoneOS/lib/`. Recreating them means linking each
`.a` from that directory into `lib/`, plus `libjami-core.a` as `lib/libjami.a` (the `.def`
and the build script both expect the shorter name). Worth scripting the next time someone
has to do it.

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

When the daemon submodule moves, diff `jami-client-ios/DEPS/arm64-iPhoneOS/lib/*.a` against
`lib/*.a` and the `OTHER_LDFLAGS` in `ios-app/iosApp.xcodeproj/project.pbxproj`. Refresh the
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

## Building the macOS native libraries

Unlike iOS, nothing in `jami-client-ios` produces macOS libraries — its `DEPS/` and
`xcframework/` trees are iOS-only. The macOS target needs a **native
`arm64-apple-darwin`** build of the daemon's contrib tree, which has to be done once.

Prerequisites (Homebrew): `automake pkg-config libtool gettext yasm`. If `autopoint` is
missing after installing gettext: `brew link --force gettext`.

```bash
cd ../../../../../jami-client-ios/daemon/contrib   # the daemon submodule
mkdir -p native-macos-arm64 && cd native-macos-arm64
../bootstrap --disable-plugin --disable-libav --ignore-system-libs
make -j4
```

Output lands in a sibling install prefix named after the host, e.g.
`contrib/arm64-apple-darwin25.6.0/lib/`. The build directory (`native-macos-arm64/`) is
separate from the iOS ones (`native-arm64-iPhoneOS/` etc.), so this does not disturb an
existing iOS contrib build.

### `--ignore-system-libs` is not optional

Without it, `contrib/src/gmp/rules.mak` runs `need_pkg 'gmp >= 6.2.0'`, finds a **Homebrew**
GMP via pkg-config, adds gmp to `PKGS_FOUND` and skips building it. Contrib's `CFLAGS` only
add contrib's own include directory, so nettle then configures with

```
configure: WARNING: GNU MP not found, or too old. GMP-6.1.0 or later is needed
```

builds without GMP, and never produces `libhogweed.a`. The failure surfaces much later, in
gnutls:

```
configure: error: *** Libhogweed (nettle's companion library) 3.10 was not found.
*** Note that you must compile nettle with gmp support.
```

`make list` is the quick check — it must print `Distribution-provided packages: None`. If it
names any package there, that package will be taken from Homebrew and the static link will
either fail or pull in a dylib that does not belong in a self-contained framework.

Note this means a system-wide Homebrew install can silently change the build. The iOS
builds were unaffected only because cross-compiling makes `need_pkg` fail for every package.

### Then the daemon

Contrib only builds the dependencies; `libjami.a` comes from the daemon itself. Mirror the
settings the iOS build uses (`build-ios-arm64-iPhoneOS/CMakeCache.txt`), pointing
`CMAKE_PREFIX_PATH` at the contrib prefix built above:

```bash
cd ../../../../../jami-client-ios/daemon
mkdir -p build-macos-arm64 && cd build-macos-arm64
cmake .. \
  -DCMAKE_BUILD_TYPE=Release \
  -DBUILD_SHARED_LIBS=OFF \
  -DBUILD_TESTING=OFF \
  -DCMAKE_OSX_ARCHITECTURES=arm64 \
  -DCMAKE_PREFIX_PATH=<daemon>/contrib/arm64-apple-darwin25.6.0 \
  -DJAMI_DBUS=OFF -DJAMI_JNI=OFF -DJAMI_NODEJS=OFF -DJAMI_PLUGIN=OFF \
  -DJAMI_VIDEO=ON -DJAMI_VIDEO_ACCEL=ON
make -j4
```

`-DBUILD_TESTING=OFF` is required: the test target wants `cppunit`, which is the one
dependency contrib does not ship, so configuration otherwise fails with
`None of the required 'cppunit' found`.

### Then the links and the wrapper

```bash
python3 scripts/make_macos_links.py                       # populate lib-macos/
shared/src/nativeInterop/cinterop/JamiBridge/build-jamibridge.sh --target=macos
```

`--target=macos` matters: the default `all` also rebuilds the two **tracked** iOS wrapper
binaries, dirtying them for no reason.

The macOS `-l` flags are **not** hardcoded in `shared/build.gradle.kts`; `macosLinkerLibs()`
derives them from the contents of `lib-macos/`, so a daemon update that changes the
dependency set does not silently go stale the way the iOS device flags do.

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
