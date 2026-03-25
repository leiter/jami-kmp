# Known Issues

## JAMI_DATADIR Mismatch (Native Library Data Path)

**Status:** Fixed (2026-03-24) via Option B — runtime `getAppDataPath` handler
**Impact:** Conversations don't load; auto-login reads wrong data directory
**Date:** 2026-02-24

### Problem

The native `.so` libraries (`libjami-core-jni.so`, `libjami-core.so`) were copied from the letsJam project (`cut.the.crap.gettogether`). These libraries were compiled with a hardcoded `JAMI_DATADIR` macro pointing to letsJam's app directory.

On Android, the daemon determines its data/config directories via:
- `emitSignal<GetAppDataPath>("files", &paths)` - a signal that returns the app data path
- This path is determined at compile time by `JAMI_DATADIR` and at runtime by signal handlers

Since the `.so` files originate from letsJam's build, the daemon stores all account data (keys, conversations, profiles) under letsJam's data directory (`/data/data/cut.the.crap.gettogether/files/`) instead of jami-kmp's (`/data/data/net.jami.android/files/`).

### Symptoms

- Conversations list shows empty after account import (data written to wrong directory)
- Auto-login may appear to work but reads from the wrong app's data
- If letsJam is not installed, the daemon may fail to create directories

### Root Cause (Native Code)

In `jami-daemon/src/fileutils.cpp`, the `get_home_dir_impl()` and `get_data_dir()` functions on Android use:

```cpp
#if defined(__ANDROID__)
    emitSignal<libjami::ConfigurationSignal::GetAppDataPath>("files", &paths);
```

This signal must be handled by the JNI layer, which returns a path based on the compile-time `JAMI_DATADIR` or the app context.

In `jami-client-android/jami-android/app/build.gradle.kts`:
```kotlin
"-DJAMI_DATADIR=/data/data/$namespace/files"
```

The letsJam build used `namespace = "com.gettogether.app"` / `applicationId = "cut.the.crap.gettogether"`.

### Fix Applied

Option B was implemented. `DaemonBridge.android.kt` now:
- Takes `Context` as a constructor parameter (injected via Koin `androidContext()`)
- Overrides `ConfigurationCallback.getAppDataPath()` to return `context.filesDir`, `context.cacheDir`, or `context.getDir(name)` for the `"files"`, `"cache"`, and other named directories respectively

This matches exactly what the official `jami-client-android` does in `DeviceRuntimeServiceImpl.getAppDataPath()`. The runtime handler overrides any compile-time default, so no recompile of the `.so` files is needed.

Related files changed:
- `shared/src/androidMain/kotlin/net/jami/services/DaemonBridge.android.kt`
- `shared/src/androidMain/kotlin/net/jami/di/PlatformModule.android.kt`
- `shared/src/commonMain/kotlin/net/jami/services/DaemonBridge.kt` (constructor removed from expect)
- `shared/src/commonMain/kotlin/net/jami/di/JamiModule.kt` (binding moved to platform modules)
- All 4 other `PlatformModule.*.kt` files (binding added)

### Workaround Options (for reference)

#### Option A: Recompile native libraries

Build `libjami-core-jni.so` and `libjami-core.so` from source with the correct `JAMI_DATADIR`:

```bash
# In CMake configuration, set:
-DJAMI_DATADIR=/data/data/net.jami.android/files
```

This ensures the daemon stores data in jami-kmp's own app directory.

#### Option B: Handle GetAppDataPath signal in JNI

Register a signal handler for `GetAppDataPath` that returns the correct path from `context.filesDir`. This requires:

1. In `JamiApplication.kt`, before `daemonBridge.init()`:
   - Register a JNI callback that responds to `GetAppDataPath` with `context.filesDir.absolutePath`

2. This approach overrides the compile-time `JAMI_DATADIR` at runtime.

Reference: letsJam's `SwigJamiBridge.kt` receives a `dataPath` parameter in `initDaemon()` but does not currently pass it to the native layer. The signal-based approach would be the correct way to inject the path.

#### Option C: Symlink (Temporary hack, NOT recommended)

Create a symlink from the old data path to the new one. Fragile and requires root or matching UIDs.

### Reference Files

- `jami-daemon/src/fileutils.cpp` lines 490-561 - Data path resolution logic
- `jami-client-android/jami-android/app/build.gradle.kts` line 36 - JAMI_DATADIR cmake arg
- `letsJam/androidApp/src/main/kotlin/.../SwigJamiBridge.kt` line 749 - initDaemon()
- `jami-kmp/android-app/src/androidMain/kotlin/.../JamiApplication.kt` - Daemon init
