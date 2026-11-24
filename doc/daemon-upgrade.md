# Upgrading the libjami daemon

_Findings from 2026-11-20. Method: the SWIG bindings jami-kmp compiles against were diffed
against every `JamiService.*` call site and every callback override in `jami-client-android` at
`7e8edd73e` (2026-11-19), whose client code targets a **newer** daemon than either repo has
checked out. Arity was compared mechanically, not by eye._

## STATE: this repo now needs a locally built `.so` (2026-11-21)

**`main` and `feat/conversation-permissions` carry SWIG bindings generated from daemon
`4117f880c`, but `jniLibs/` is gitignored, so the matching `libjami-core-jni.so` is not in git
and cannot be.** A fresh clone therefore builds and then fails at *runtime*: the committed
bindings declare `getConversationPermissions`, `addSharedService` and a five-argument
`swarmLoaded`, none of which exist in an older library. Symptoms are `UnsatisfiedLinkError` on
peer services and conversation history that never loads.

Anyone who needs a runnable Android build must produce the library themselves by following
"Checklist for the bump" below against daemon `4117f880c` or later. There is no way to make git
enforce this pairing; it is the structural hazard described under "How the daemon reaches
jami-kmp", in its sharpest form.

Known-good state on the machine this was done on (2026-11-21): daemon `4117f880c`,
`libjami-core-jni.so` 42 MB stripped, `libc++_shared.so` from NDK 30, verified on a Pixel 7a —
the daemon starts, the account registers, and `SwarmLoaded` delivers a real permission vector.

## Why this document exists

Not to be confused with `plan_daemon_stability_sync.md` (2026-09-06), which is about *how* this
client drives the daemon it already has — callback ordering, load gating, sync health. This
document is only about moving to a newer daemon generation: which APIs changed and what the bump
costs.


jami-kmp is pinned to a daemon generation from around 2026-08. The reference client has moved
past it and now calls APIs that do not exist in our bindings. Three of those gaps are additive
and cost nothing; two force code changes, and one of them is a hard compile break. This records
which is which so the bump is not re-derived from scratch.

---

## How the daemon reaches jami-kmp

There is no daemon build in this repo. The pipeline is manual and in two halves that are
**tracked differently** — which is the main hazard:

| Artifact | Location | Tracked |
|---|---|---|
| `libjami-core-jni.so`, `libc++_shared.so` | `android-app/src/androidMain/jniLibs/<abi>/` | **No** — `.gitignore:7` |
| SWIG Java bindings (24 files, incl. `JamiService.java`, `ConversationCallback.java`) | `shared/src/androidMain/java/net/jami/daemon/` | **Yes** — committed |

`../pipe_so_files.sh` builds the daemon through `jami-client-android`'s Gradle (which runs SWIG as
part of its own build) and copies the resulting `.so` here. The Java bindings were copied by hand
at some point and committed.

In `jami-client-android` those same bindings are gitignored and regenerated on every build
(`jami-android/.gitignore:33`), so they cannot drift there. **jami-kmp is the only place where the
bindings can go stale against the `.so`**, and that mismatch is invisible to the compiler: it
surfaces as `UnsatisfiedLinkError` at runtime, on whichever code path first touches a missing
symbol.

### This drift is already real (pre-existing, 2026-11-20)

| ABI | `.so` date | Has `addExposedService`? |
|---|---|---|
| `arm64-v8a` | 2026-08-14 | yes — matches the committed bindings |
| `x86_64` | 2026-02-24 | **no** |

The committed bindings declare `addExposedService`, so the peer/exposed-services path throws
`UnsatisfiedLinkError` on an x86_64 emulator. Device builds (arm64) are consistent. Treat an
x86_64 emulator result as untrustworthy until its `.so` is refreshed, or drop the folder —
`pipe_so_files.sh` already suggests removing it.

---

## What the newer daemon requires

### 1. `swarmLoaded` gained a fifth parameter — the hard break

```
bindings today:  swarmLoaded(id, accountId, conversationId, messages)
newer daemon:    swarmLoaded(id, accountId, conversationId, messages, permissions: StringVect)
```

`DaemonBridge.android.kt` overrides the four-argument form. After the bump that `override`
overrides nothing and Kotlin fails the compile — the good failure mode, loud rather than silent.

**This is also where conversation permissions arrive.** The newer daemon has two channels:
`getConversationPermissions(accountId, conversationId)` (pull) and this fifth argument (push, on
every history load). The reference applies the pushed set directly —
`AccountService.swarmLoadedNow` → `conversation.setPermissions(permissions)`
(`libjamiclient/.../AccountService.kt:1714`).

`Conversation.setPermissions` already exists here and is already consumed by the UI, so wiring the
push channel is one line inside the forced signature fix. See
[conversation permissions](#conversation-permissions-follow-up) below.

### 1a. `onConferenceInfosUpdated` → `conferenceInfosUpdated` — a second forced rename

**Added 2026-11-20 after generating the real bindings.** This document originally claimed
`swarmLoaded` was the *only* signature change. It is not: `Callback::onConferenceInfosUpdated`
(2 args) is **removed** and replaced by `conferenceInfosUpdated` with the same two arguments.
jami-kmp overrides the old name (`DaemonBridge.android.kt:1084`), so this too fails the compile
after the bump. Predicting from the reference client's call sites missed it because the reference
had already been updated to the new name, so the symbol simply never appeared in the diff of
"what they call that we do not".

### 2. `ExposedService` → `SharedService` — a pure rename

Four methods, identical shapes, new spelling:

| Ours | Newer daemon |
|---|---|
| `addExposedService(accountId, StringMap)` | `addSharedService` |
| `updateExposedService(accountId, StringMap)` | `updateSharedService` |
| `removeExposedService(accountId, serviceId)` | `removeSharedService` |
| `getExposedServices(accountId)` | `getSharedServices` |

Four one-word edits in `DaemonBridge.android.kt` (~lines 352-361), plus the iOS wrapper
equivalents. Direction confirmed: the pinned daemon submodule
(`jami-client-android/daemon` @ `354abca11`, 2026-08-12) still declares the `Exposed` spelling in
`src/jami/networkservice_interface.h`, and the newer client calls only the `Shared` form.

### 3. Seven new callbacks — additive, no action needed

`collaborativeAttachmentAdded`, `collaborativeAwarenessChanged`, `collaborativeDocumentRemoved`,
`collaborativeDocumentRenamed`, `collaborativeDocumentUpdate`, `collaborativeParticipantLeft`,
`contactMetadataChanged`.

SWIG director classes carry **concrete no-op bodies**, not abstract methods, so jami-kmp's
`object : ConversationCallback()` subclasses keep compiling and simply ignore new signals. Nothing
breaks by not implementing them; they are the entry points for collaborative documents and contact
labels when those are wanted.

### 4. Additive pull-side APIs — no action needed

Nothing jami-kmp calls disappears except the four renamed above. Checked specifically because they
looked like renames and are not — the reference calls **both** members of each pair, side by side:

| Old (we use) | New (also exists) | Evidence |
|---|---|---|
| `connectivityChanged()` | `networkInterfaceChanged()` | `HardwareService.kt:122` and `:127` |
| `getKnownRingDevices(accountId)` | `getDeviceList(accountId)` (richer, returns detail maps) | `AccountService.kt:854` and `:842` |

New and unused by us: the `collaborative*` family (15 calls), `setContactMetadata` /
`getContactMetadata`, `startFeedConversation`, `getConversationPermissions`,
`getConversationPreferences` additions.

**Verified against generated bindings, 2026-11-20.** `swig 4.2.0` was run over
`origin/master` (`4117f880c`, 2026-10-14, 263 commits past our pin) in a throwaway worktree —
`make-swig.sh` needs no C++ build — and the output diffed against the committed bindings. The
real numbers, replacing the earlier estimates from call sites:

| | |
|---|---|
| `JamiService` methods | 253 → 278 (29 added, 4 removed) |
| Methods removed | exactly the four `ExposedService` ones |
| Callback signature changes | `swarmLoaded` 4 → 5 args |
| Callbacks renamed | `onConferenceInfosUpdated` → `conferenceInfosUpdated` |
| Callbacks added | 10 (6 collaborative + `collaborativeDocumentReplicated`, `contactMetadataChanged`, `accountMetadataChanged`, `BlobMap::clear`) |
| New binding class | `BlobMap.java` (24 → 25 files) |

So jami-kmp has **three** forced changes, not two: `swarmLoaded`, `conferenceInfosUpdated`, and
the four renamed service calls. Everything else is additive.

---

## Platform work that comes with any bump

- **iOS** — `JamiBridgeWrapper` must be rebuilt on a Mac (`doc/apple-bridge-rebuild.md`), and
  `scripts/make_device_links.py` re-run: when the daemon submodule moves its dependency set
  changes, so `lib/` and the `OTHER_LDFLAGS` in `project.pbxproj` go stale. That fails at **link**
  time, never compile time. `build-jamibridge.sh` overwrites tracked `.a` files — commit unrelated
  work first.
- **macOS** — already does not compile (~95 errors); a bump neither helps nor worsens it.
- **Desktop / Web** — no-op and REST stubs; the bridge methods only need the new signatures to
  keep overriding, which for an additive change means nothing at all.

---

## Host prerequisites, as actually found (2026-11-21)

The build is driven entirely by Gradle — `app/build.gradle.kts` passes `-DBUILD_CONTRIB=ON`, so
the daemon's CMake builds contrib as well. There is no separate contrib step to run by hand.

| Need | Where it comes from | Status on this host |
|---|---|---|
| NDK `30.0.16248370` | `app/build.gradle.kts: ndkVersion` | `~/Android/Sdk/ndk/` — present |
| cmake `4.1.2` | `externalNativeBuild { cmake { version } }` | `~/Android/Sdk/cmake/4.1.2` — present (the *system* cmake is 3.22.1 and is not what Gradle uses) |
| swig ≥ 4.0 | `bin/jni/make-swig.sh` | 4.2.0 — present |
| ninja | SDK or system | system 1.10.1; note `~/Android/Sdk/cmake/4.1.2/bin` ships no ninja of its own |
| disk | contrib + NDK | needs well over 12 GB free; ~21 GB is reclaimable from stale `~/.gradle/caches/<version>` dirs, `build-cache-1` and `jars-9`, none of which force a re-download (`modules-2` does — keep it) |

**The daemon submodule carries a local patch that a checkout silently drops.**
`contrib/src/main.mak` has `CMAKE_INTERPROCEDURAL_OPTIMIZATION` flipped to `OFF` at the Android
`CMAKE` definition (line ~423) — an LTO workaround for this host. Stash it, check out, pop it:

```bash
cd jami-client-android/daemon
git stash push -m "local LTO-off build patch" contrib/src/main.mak
git checkout <new-rev>
git stash pop
```

There is a *second* `INTERPROCEDURAL_OPTIMIZATION=ON` further down (line ~436). It is not a
missed patch site: it sits in the non-Android branch of the makefile and is gated by
`ifdef ENABLE_LTO`, so it has no effect here.

### A new contrib dependency will not build itself — and Rust is now one

Two failures, both avoidable, before the first successful native build:

**1. `yrs` (Y-CRDT) needs a Rust toolchain.** The collaborative-document work added
`contrib/src/yffi`, a Rust package, and `CMakeLists.txt` declares
`pkg_search_module (yrs REQUIRED ...)` with **no option to disable it**. There is also no cheaper
pin to hide behind: the collab commits merged *after* our old pin (despite earlier author dates),
and `getConversationPermissions` (`622a3f5ce`, 2026-10-05) came later still — so **any** revision
with conversation permissions also requires Rust. Install it without touching the shell profile;
the recipe falls back to `$HOME/.cargo/bin/cargo` on its own:

```bash
curl --proto '=https' --tlsv1.2 -sSf https://sh.rustup.rs | sh -s -- -y --no-modify-path --profile minimal
~/.cargo/bin/rustup target add aarch64-linux-android
```

**2. The Gradle run does not build contrib.** `BUILD_CONTRIB=ON` registers a *build-time* target,
but `pkg_search_module(... REQUIRED)` runs at CMake **configure** time — so a contrib package that
does not exist yet fails configure before contrib is ever built, in about ten seconds, with
`None of the required '<pkg>' found`. The August build only worked because contrib was already
complete. Any bump that introduces a dependency therefore needs the new package built by hand
first:

```bash
cd jami-client-android/daemon/contrib/build-aarch64-linux-android
PATH="$HOME/.cargo/bin:$PATH" make .yffi      # one stamp target per package
```

Everything else in the August contrib tree stayed valid across a 263-commit jump — opendht,
dhtnet, libgit2, ffmpeg, pjproject and the rest all resolved untouched — so only the genuinely
new package needs building, not the whole tree.

Build and copy (arm64 only, as `pipe_so_files.sh` does):

```bash
cd jami-client-android/jami-android
./gradlew assembleNoPushDebug -Parchs=arm64-v8a
cp app/build/intermediates/merged_jni_libs/noPushDebug/*/out/arm64-v8a/*.so \
   ../../jami-kmp/android-app/src/androidMain/jniLibs/arm64-v8a/
```

## Checklist for the bump

1. Bump `jami-client-android/daemon` to the revision that actually has the wanted APIs, and
   confirm with `grep -rl "<apiName>" daemon/src/jami/*.h` before building — the client's HEAD is
   ahead of its own submodule, so its code is not proof the pinned daemon has the call.
2. Build via `pipe_so_files.sh` and copy the `.so` for **every** ABI being shipped, not just
   arm64 (see the drift table above).
3. Copy the regenerated bindings from
   `jami-client-android/jami-android/libjamiclient/src/main/java/net/jami/daemon/` into
   `shared/src/androidMain/java/net/jami/daemon/` and commit them in the same commit as the `.so`
   refresh, so the pair cannot separate.
4. Fix the `swarmLoaded` override (forced) and wire the pushed permission set.
5. Rename the four `ExposedService` calls.
6. Rebuild the Apple wrapper on a Mac and relink.
7. Verify a daemon symbol actually resolves at runtime, not just that it compiles —
   `strings libjami-core-jni.so | grep <apiName>` catches a stale `.so` before a device does.

---

## Conversation permissions follow-up

The permission model, enforcement and UI gating landed in `8f0f8a5` and are deliberately inert:
every `DaemonBridge` actual returns null for `getConversationPermissions`, and
`Conversation.permissionsFlow` reads null as permissive, so nothing is blocked on today's daemon.
Flipping it on needs exactly three edits:

1. `DaemonBridge.android.kt` — `getConversationPermissions` returns
   `JamiService.getConversationPermissions(accountId, conversationId).toList()`.
2. `DaemonBridge.ios.kt` — the same against the rebuilt wrapper.
3. The forced `swarmLoaded` fix — pass the fifth argument to
   `Conversation.setPermissions`, giving the push channel alongside the pull one.

`ConversationFacade.refreshConversationPermissions` already covers the pull side at the four
points the reference uses (swarm indexed, smartlist pass, conversation ready, member event).
