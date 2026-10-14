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

## Two YAML config errors on every launch of an account-less install

**Status:** Upstream daemon behaviour — benign, not ours to fix
**Impact:** Two red `[config]` error lines on every launch until an account exists; permanent log noise, no functional effect
**Date:** Traced 2026-10-14 against daemon `421cac609`

### What you see

```
[config] Preferences unserialize YAML exception: invalid node; first invalid key: "preferences"
```
twice, on each launch, followed by the app correctly reaching "NoAccounts".

### The chain

1. `ManagerPimpl::parseConfiguration()` (`manager.cpp:427`) opens `dring.yml` with an
   `std::ifstream` and hands it to `YAML::Load`. **A missing or empty file does not throw here**:
   the ifstream fails silently and `YAML::Load` returns an empty node, so the `catch
   YAML::BadFile` handler never fires. The daemon therefore cannot distinguish "no config yet"
   from "corrupt config".
2. `Preferences::unserialize` (`preferences.cpp:246`) reads `in["preferences"]` off that empty
   node and `.as<T>()` throws `InvalidNode` — the message above, logged at `manager.cpp:2887`.
3. The error sends `init` down "Restoring last working configuration" (`manager.cpp:835`).
   `restore_backup` copies `dring.bak` over `dring.yml`, but on a first run no `.bak` exists —
   and **`copy_over` from a missing source creates an empty destination**. So the recovery path
   manufactures the empty `dring.yml` that caused the failure.
4. The re-parse fails identically, which is why the message appears exactly twice. The daemon
   then falls back to defaults (`manager.cpp:849`) and carries on correctly.

### Why it recurs rather than self-healing

Step 3 leaves a 0-byte `dring.yml` on disk. Every subsequent launch repeats the same two errors
until an account is created and the daemon writes a real config over it.

### Worth knowing

Do not read these lines as a symptom of whatever you are debugging — they predate it. They are
also *not* exclusive to a fresh install: the case that prompted this trace was a simulator whose
data folder dated from a month earlier but had never held a `dring.yml`, because no account was
ever created in it.
