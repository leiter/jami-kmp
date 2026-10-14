# Apple bridge rebuild — the pending Mac task

> Written 2026-10-14 on a Linux host, at `fe40027`.
> **Read this on the Mac before touching the iOS build.**

Nine commits added ~514 lines of Objective-C++ to the JamiBridge wrapper that has never been
compiled, because those sessions ran on Linux where Apple targets are skipped. The committed
`.a` files are stale by the same nine commits. This describes what is waiting, what is already
verified, and what to do in order.

Delete this file once the rebuild has landed; until then it is the only record of which wrapper
methods are unbuilt.

## Goal

Get the **iOS target building, linking and running again** after nine commits of Apple-side
additions that were written on a Linux host and therefore never compiled. Concretely: rebuild
`lib/libJamiBridge_ios.a` and `lib/libJamiBridge_iossim.a` from the current
`JamiBridgeWrapper.mm`, fix whatever the Obj-C++ compiler and the Kotlin/Native linker reject,
and confirm on a simulator that the features added in those commits actually work on iOS.
Then, separately, the macOS target (~95 errors) via a shared `appleMain` source set.

The Kotlin side is already done and verified on Android and desktop: 760 tests, 0 failures,
`:shared:compileDebugKotlinAndroid` and `:android-app:compileDebugKotlin` clean. **Nothing in
commonMain is expected to need changing.** This task is about the Apple boundary only.

## Current State

**Working:**
- Android and desktop compile clean; full desktop suite is 760 tests / 0 failures.
- All Kotlin `iosMain` / `macosMain` actuals are written and type-check on Linux (Kotlin
  compiles for Apple targets are skipped on this host, so this is weaker than it sounds).
- Every libjami symbol the new wrapper code calls **has been verified to exist** in
  `shared/src/nativeInterop/cinterop/headers/` — all 27 functions and all 62
  `*Signal::` names resolve. So failures should not be missing declarations.
- `git status` is clean and everything is pushed to `origin/docs/mini-plans` over SSH.

**Broken / unverified:**
- `lib/libJamiBridge_ios.a` and `lib/libJamiBridge_iossim.a` are **stale**: last rebuilt at
  `ed6673a`, nine commits ago. They do not contain any of the methods below, so an iOS link
  will fail with undefined symbols for every one of them.
- The +355 lines added to `JamiBridgeWrapper.mm` since then **have never been through a
  compiler**. Expect syntax and type errors on the first run, not a clean build.
- macOS target does not compile (~95 errors, mostly `MacOSHardwareService.kt`).

## Files in Play

| File | Why It Matters |
|------|---------------|
| `shared/src/nativeInterop/cinterop/JamiBridge/JamiBridgeWrapper.h` | +136 lines of declarations since `ed6673a`; cinterop parses this, so an error here breaks the Kotlin build too |
| `shared/src/nativeInterop/cinterop/JamiBridge/JamiBridgeWrapper.mm` | +355 lines of never-compiled Obj-C++; the main risk surface |
| `shared/src/nativeInterop/cinterop/JamiBridge/build-jamibridge.sh` | The rebuild. **Overwrites the two tracked `.a` files** — tree is clean, so a bad run is `git checkout`-able |
| `shared/src/nativeInterop/cinterop/JamiBridge/README.md` | Has the symlink setup and a dependency-drift section describing the three link errors that bit on the last Mac build (2026-09-08) |
| `shared/src/iosMain/kotlin/net/jami/services/DaemonBridge.ios.kt` | Kotlin actuals calling the new wrapper methods, plus the delegate callbacks |
| `shared/src/macosMain/kotlin/net/jami/services/DaemonBridge.macos.kt` | Same, for macOS; substantively a duplicate of the iOS file (hence the `appleMain` idea) |
| `scripts/make_sim_links.py` | Regenerates `lib-sim/` simulator symlinks; reports anything it cannot find |
| `shared/src/nativeInterop/cinterop/headers/` | The libjami header set the wrapper compiles against. Note `networkservice_interface.h` is **absent** — see constraints |

## Wrapper methods added since the last `.a` rebuild — verification checklist

Nine commits, newest first. Use this to tell which change caused a failure instead of
bisecting blind, and to exercise each feature once the app runs.

| Commit | Wrapper Δ | Where it surfaces in the app |
|--------|-----------|-----------------------------------|
| `fe40027` | +33 | `countInteractions`, `clearCache` — conversation sync panel shows `daemon/loaded` counts; clear-history releases daemon memory |
| `4000a79` | +154 | 9 certificate functions + 4 certificate signals — Certificates screen under Account → Advanced → Security lists/pins/unpins, trust status sticks |
| `286d56e` | +11 | `recordPath` / `setRecordPathValue` — Recordings browser lists and plays files |
| `cb96545` | +21 | `sendTextMessage` in-call — in-call chat sends and receives |
| `1a3fd28` | +10 | `noiseSuppressState`, `setAgcState` — audio processing settings apply (NOT peer services; see constraints) |
| `da5fdd9` | +38 | `historyLimit`, `ringingTimeout`, `monitorDaemon`, `connectionList` — diagnostics + settings |
| `8df3fdf` | +133 | ~13 previously-dropped signals (presence, conversation error, in-call message, connection update, remote recording, nearby peer) — these are the ones most likely to have a **handler signature mismatch**, since each is an `exportable_callback<...>` lambda whose arity must match the header exactly |
| `916fcd8` | +68 | `muteParticipantStream`, `setActiveParticipantStream`, `raiseParticipantHand`, `setParticipantModerator` — conference moderation in a 3-way call |
| `ea0081a` | +23 | `updateProfile` — contact profile changes appear live, avatars update |

## What Has Been Tried (and Why It Failed)

**Nothing has been attempted on a Mac for any of this.** There are no failed approaches to
avoid — this is a first build. Recorded explicitly so nobody goes looking for a history that
does not exist.

What *is* known from the **previous** Mac build of the iOS host app (2026-09-08), per the
README's drift section, and worth expecting again:

### Known hazard 1: hand-made device symlinks go stale
- **What happens:** `lib/` holds ~48 symlinks into `jami-client-ios/DEPS/arm64-iPhoneOS/lib/`,
  created by hand with no script. When the daemon submodule moves, its dependency set changes
  and `lib/` does not.
- **How it shows up:** an Xcode/Kotlin **link** error with undefined symbols, never a compile
  error. Three separate instances of this bit the last Mac build.

### Known hazard 2: `OTHER_LDFLAGS` in `project.pbxproj` drifts the same way
- Same cause, same symptom, different file. Check it against the current `lib/` contents.

### Known hazard 3: `build-jamibridge.sh`'s macOS stage fails, and always has
- **What happens:** the script compiles three stages (device, simulator, macOS). The macOS one
  fails because the wrapper's UIKit file-picker code (`UIScene`, `UIWindow`) is compiled against
  the macOS SDK.
- **Not caused by the nine commits:** that code dates to `93df547a` (April), so macOS has been
  broken here since long before. Confirmed on 2026-10-14.
- **What to do:** it is step 8's problem, not step 4's. Run the script's two iOS stages with the
  script's own flags rather than editing the script, and leave macOS to the
  `macos-build-and-apple-source-set` branch.

### Known hazard 4: Release archives die in `DevirtualizationAnalysis`
- **Cause:** Kotlin/Native LTO runs *inside* the Gradle daemon here, so only
  `org.gradle.jvmargs` controls its heap — `kotlin.native.jvmArgs` and
  `kotlin.daemon.jvm.options` are inert. Needs ~6 GB.
- Debug and simulator builds never show it. On an 8 GB machine put the override in
  `~/.gradle/gradle.properties`, not the repo. Detail: `ios-app/fastlane/RELEASING.md`.

## Current Best Theory

The Obj-C++ is more likely to be *nearly* right than badly wrong, because the risky part —
whether each libjami function and signal name exists — has already been checked against the
bundled headers and all 89 resolve. The plausible failure modes, in order:

1. **Signal handler arity/type mismatches** in the `8df3fdf` batch (13 handlers added in one
   commit, each a lambda whose parameters must match the header's `cb_type` exactly).
2. **Obj-C method naming collisions** with the Kotlin side's expectations. Several wrapper
   methods were deliberately renamed to dodge Kotlin/Native JVM-style signature clashes
   (`recordPath`/`setRecordPathValue`, `historyLimit`/`setHistoryLimitDays`,
   `ringingTimeout`/`setRingingTimeoutSeconds`, `monitorDaemon`, `connectionList`). If a Kotlin
   actual calls a name the header does not declare, cinterop fails before the linker runs.
3. **`NSData` ↔ `std::vector<uint8_t>` conversion** in `pinCertificate` — the only new method
   doing raw byte marshalling.
4. The symlink/`OTHER_LDFLAGS` drift above, which is environmental rather than code.

## Next Steps

1. **Prerequisites — already verified on 2026-10-14, skip unless something has moved.**
   Checked on the Mac that day: all 47 libraries named in
   `OTHER_LDFLAGS[sdk=iphoneos*]` are present in
   `../jami-client-ios/DEPS/arm64-iPhoneOS/lib/`, `libjami-core.a` among them; the
   `xcframework/` set is complete including `libjami-core.xcframework`; Xcode is at
   `/Applications/Xcode.app/Contents/Developer`. **No dependency drift.**

   To re-check after the daemon submodule moves, compare the pbxproj's expected set against
   what `DEPS/` holds — that mismatch is the drift described below, and it surfaces only as an
   undefined-symbol error:
   ```bash
   awk '/OTHER_LDFLAGS\[sdk=iphoneos/,/\);/' ios-app/iosApp.xcodeproj/project.pbxproj \
     | sed 's/^[ \t]*//' | tr -d '",' | grep '^-l' | sed 's/^-l//' | sort -u \
     | grep -vE '^(c\+\+|iconv|resolv|sqlite3|z|jami|JamiBridge_ios)$' \
     | while read l; do
         [ -f "../jami-client-ios/DEPS/arm64-iPhoneOS/lib/lib$l.a" ] || echo "MISSING: lib$l.a"
       done
   ```
2. **Regenerate simulator symlinks:** `python3 scripts/make_sim_links.py`. Correct output is
   **"Created 48 symlinks / No arm64-simulator slice (1): libvpx"** — libvpx genuinely has no
   arm64-sim slice (ffmpeg is configured `--disable-libvpx` there), which the script treats as
   non-fatal and the README lists as one of the three 2026-09-08 drift findings. The README's
   example output showing "49 symlinks / All libs found" is stale; do not chase the difference.
   Any *other* library it cannot find does mean the xcframework build is incomplete.
3. **Check device symlinks** in `shared/src/nativeInterop/cinterop/lib/` against the current
   `jami-client-ios/DEPS/arm64-iPhoneOS/lib/`. Note `libjami-core.a` must be linked as
   `lib/libjami.a` — both the `.def` and the build script expect the short name. The README
   suggests scripting this; worth doing while here.
4. **Rebuild the wrapper:**
   `shared/src/nativeInterop/cinterop/JamiBridge/build-jamibridge.sh`. Fix compile errors
   against the checklist table above. This overwrites two tracked `.a` files; the tree is clean
   so `git checkout -- shared/src/nativeInterop/cinterop/lib/` resets a bad run.
5. **Verify the symbols, then link in Xcode. Do not trust a green Gradle link.**

   `shared/build.gradle.kts:36-60` builds the iOS framework with `isStatic = true`, and the
   iOS targets pass no `-ljami` or per-dependency linker flags — only macOS does. A static
   archive tolerates undefined symbols, and the real resolution happens in Xcode's app link
   using `OTHER_LDFLAGS`. So `linkDebugFrameworkIosSimulatorArm64` **can succeed while the
   `.a` is still missing every method added in step 4**. Two checks instead:

   ```bash
   # a) are the header's methods actually in the rebuilt archive?
   #    NOTE: not `nm -gU`. Objective-C methods are local (t) symbols, not global, so a
   #    global-symbol listing finds none of them and looks like total failure.
   L=shared/src/nativeInterop/cinterop/lib
   for a in ios iossim; do
     echo "$a: $(nm $L/libJamiBridge_$a.a | grep -cE '[-+]\[JamiBridgeWrapper ') methods"
   done
   # Cross-check every declared method against the archive (empty output = all present):
   awk '/@interface JamiBridgeWrapper/,/@end/' \
     shared/src/nativeInterop/cinterop/JamiBridge/JamiBridgeWrapper.h \
     | grep -oE '^[-+] \([^)]*\)[A-Za-z]+' | grep -oE '[A-Za-z]+$' | sort -u \
     | while read m; do
         nm $L/libJamiBridge_ios.a | grep -q "JamiBridgeWrapper $m" || echo "declared, not in .a: $m"
       done

   # b) the real link
   xcodebuild -project ios-app/iosApp.xcodeproj -scheme iosApp \
     -sdk iphonesimulator -configuration Debug build
   ```

   For reference, the 2026-10-14 rebuild gave **1096 methods, up from 875**, and all 153
   header-declared methods present. (a) is what tells you the Obj-C++ rebuild worked. Also note `build-jamibridge.sh` overwrites
   tracked `.a` files, so a run that silently did nothing looks identical to one that worked —
   `git status` should show both `.a` files modified afterwards.
6. **Run the app on a simulator** and walk the right-hand column of the checklist table.
7. **Commit the rebuilt `.a` files on their own**, with a message listing the nine commits they
   now cover, so the next drift is easy to date.
8. **Then macOS — but do not start it from scratch.** There is already a branch:
   `origin/macos-build-and-apple-source-set`, one commit (`f0f66ce`, "Make the macOS target
   build, and share Darwin code via appleMain", 2026-10-01). Continue that rather than
   re-deriving it. It predates all nine wrapper commits, so it needs rebasing onto `main`
   before it can even be evaluated:
   ```bash
   git log --oneline origin/macos-build-and-apple-source-set
   git rev-list --left-right --count main...origin/macos-build-and-apple-source-set
   ```
   The approach in it is the right one — the iOS and macOS Darwin implementations are
   substantively identical, so a shared `appleMain` source set beats patching
   `MacOSHardwareService.kt` twice. See also `doc/TODO.md`.

## Key Constraints

- **Mobile first** (CLAUDE.md): Android is primary, iOS a close second, desktop/macOS/web
  secondary and droppable. Do not let macOS work block iOS.
- **`networkservice_interface.h` is not in the bundled header set**, so the peer-services
  feature (daemon-audit F7) is Android-only by design. Do not try to add it to the wrapper —
  the header would have to come from the daemon first. The `1a3fd28` wrapper change is only
  the two audio-processing methods, nothing to do with peer services.
- **Rebuilding the `.a` files overwrites tracked binaries.** Commit or stash unrelated work
  first. (Currently nothing to lose — tree is clean.)
- **Plan docs omit QA/Testing/Verification sections** — a standing user preference. If you
  update `doc/mini-plans/*.md`, do not add them.
- **Do not remove existing code while fixing something**; change only what the task requires,
  and ask before deleting.
- Skip routine compile-checks for small source-verified edits, but **do** compile for
  cross-module changes. On a Mac, every change in this task is cross-module.
- Do not mix new mini-plan findings into this work. Steps 1–7 are unpredictable debugging and
  interleaving feature work makes both harder to reason about.
- The `origin` remote is SSH (`git@github.com:leiter/jami-kmp.git`). An older HTTPS URL had a
  PAT embedded in `.git/config`; that token is rotated and gone. Do not reintroduce it.

## Where the reasoning lives

The nine commits' messages carry the detail of *why* each wrapper method exists — several
findings were disproved against the daemon source before anything was written, and the
messages record that. `git log --oneline ed6673a..HEAD -- shared/src/nativeInterop/cinterop/JamiBridge/`
lists exactly the set this file is about.

Related docs: `shared/src/nativeInterop/cinterop/JamiBridge/README.md` (symlinks, drift),
`doc/ios_implementation_gap.md` (remote video, the three first-Mac-build fixes),
`ios-app/fastlane/RELEASING.md` (the Release heap problem), `doc/TODO.md` (the `appleMain`
item).
