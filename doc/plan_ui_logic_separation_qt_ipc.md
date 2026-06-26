# Plan: Separate UI from Business Logic to Support Qt (JVM logic + IPC)

**Status:** Proposed — no code written yet
**Date:** 2026-06-26
**Goal:** Make the Jami business + presentation logic reusable by UI frameworks that
are not Jetpack/Compose-compatible (specifically **Qt/QML**), while keeping the
existing Compose Multiplatform apps working unchanged.
**Chosen binding mechanism:** JVM keeps the logic; Qt runs as a separate process and
talks to it over **IPC** (HTTP + WebSocket, reusing the existing Ktor REST-bridge stack).

---

## 1. Why this is mostly a packaging problem, not a rewrite

The codebase is already close to the target. Findings from the current `:shared` module:

| Layer | Location | Compose coupling |
|---|---|---|
| Services (20) | `shared/src/commonMain/kotlin/net/jami/services` | none |
| Models (23), repository, database, DI | `net/jami/{model,repository,database,di}` | none (1 exception, below) |
| ViewModels (18) | `net/jami/ui/viewmodel` | almost none (1 exception, below) |
| Compose UI (70 files) | `net/jami/ui/{screens,composables,components,theme,modifiers}` | all Compose |

Key facts that make this feasible:

- **ViewModels are plain Kotlin classes.** They do *not* extend
  `androidx.lifecycle.ViewModel`. They receive services via constructor injection,
  own a `CoroutineScope(SupervisorJob() + Dispatchers.Default)`, and expose state
  exclusively as `StateFlow`. A non-Compose frontend can consume them as-is.
- **`VideoSink` already abstracts the render target** as `surface: Any` and streams
  size via `connectSink(): Flow<Pair<Int,Int>>`.
- **An IPC bridge pattern already exists.** The Kotlin/JS web client
  (`shared/src/jsMain/.../DaemonBridge.js.kt`) reaches libjami over HTTP + WebSocket,
  specified in `docs/REST_BRIDGE_API.md`. Ktor is already in `gradle/libs.versions.toml`.

### The only 3 places logic touches Compose

1. **`:shared` `commonMain` depends on Compose** (`shared/build.gradle.kts:118-125`:
   `compose.runtime/foundation/material3/ui`, plus `navigation.compose:124`). Any
   consumer transitively inherits Compose.
2. **i18n via Compose Resources.** `ui/viewmodel/ConversationsViewModel.kt` imports
   `org.jetbrains.compose.resources.getString` and reads `Res.string.*` (lines 500-514)
   to format notification/history text. Strings live in `commonMain/composeResources/values-*`.
3. **`viewModelFactory`/`getViewModel` is `@Composable`** in `di/ViewModelHelper.kt`
   (the only Compose import under `di/`).

That is the entire coupling surface to remove.

---

## 2. Target architecture

### 2.1 Module split

```
:core           commonMain, NO Compose   → services, models, db, repository, di-core, DaemonBridge
:presentation   commonMain, NO Compose   → the 18 ViewModels, framework-free navigation/state
:ui-compose     depends on core+presentation → today's 70 Compose files
:android-app / :desktop-app / :web-app   → Compose hosts (unchanged behaviour)
:core-host      (new, JVM) → headless process: hosts core+presentation, exposes IPC server
:qt-app         (new, C++/QML) → separate process, IPC client, renders state
```

Splitting into Gradle modules is the enforcement mechanism: once `:core` and
`:presentation` declare no Compose dependency, the build *fails* if logic reaches for
Compose. That guarantee is the real deliverable of Phase 1.

### 2.2 Where to cut the IPC boundary

Two candidate cut points:

- **Daemon-level** (what the web REST bridge does today): Qt would re-implement all 18
  ViewModels and services in C++ and only proxy raw libjami calls. Rejected — it
  duplicates the entire presentation layer and defeats the goal.
- **ViewModel/state-level (chosen):** a headless JVM process hosts services + ViewModels
  and exposes **ViewModel state snapshots + user actions** over IPC. Qt becomes a thin
  view: it renders the latest `StateFlow` snapshot as a QML model and sends user actions
  back. All Kotlin business + presentation logic is reused.

### 2.3 IPC topology (JVM logic + IPC)

```
┌────────────────────────────┐         ┌──────────────────────────────────────┐
│   Qt / QML app (C++)        │         │   JVM core-host process                │
│  ┌──────────────────────┐  │  WS     │  ┌────────────────────────────────┐   │
│  │ QML views            │◀─┼─────────┼──│ State publisher                │   │
│  │ (QAbstractListModel) │  │ state   │  │ (serializes StateFlow snapshots)│   │
│  └──────────┬───────────┘  │ frames  │  └──────────────┬─────────────────┘   │
│             │ actions      │         │     ▲  reuses   │                      │
│  ┌──────────▼───────────┐  │  HTTP   │  ┌──┴────────────▼──────┐  ┌────────┐ │
│  │ IPC client (Qt WS/   │──┼────────▶│  │ :presentation (VMs)  │──│ :core  │ │
│  │  QNetworkAccessMgr)  │  │ actions │  │  StateFlow + actions │  │ libjami│ │
│  └──────────────────────┘  │         │  └──────────────────────┘  │ (JNI)  │ │
└────────────────────────────┘         │                            └────────┘ │
                                        └──────────────────────────────────────┘
```

- **State channel:** one WebSocket. Server pushes a JSON snapshot whenever any
  subscribed ViewModel's `StateFlow` emits. Message shape: `{ vm, instanceId, state }`.
- **Action channel:** HTTP POST (or WS-RPC) `dispatch(vm, instanceId, action, args)`,
  mapping 1:1 onto existing public ViewModel methods.
- **Subscription:** Qt opens a ViewModel by id (`subscribe(vm, key)`); the host
  resolves/creates it through the existing Koin `viewModelFactory`, attaches a collector,
  and streams snapshots until `unsubscribe`.

This deliberately mirrors `docs/REST_BRIDGE_API.md` (HTTP for operations, WS for events)
so the two bridges share serialization, auth, and lifecycle conventions.

---

## 3. Phased implementation

### Phase 1 — Module split (pure refactor, no behaviour change)

- Create `:core` and `:presentation` Gradle modules (KMP, JVM target required for the
  host; keep android/ios/js targets so existing apps still compile).
- Move `net/jami/{services,model,repository,database}` and the non-UI parts of
  `net/jami/di` into `:core`.
- Move `net/jami/ui/viewmodel` into `:presentation` (depends on `:core`).
- Move `net/jami/ui/{screens,composables,components,theme,modifiers,navigation}` into
  `:ui-compose` (depends on `:core` + `:presentation`).
- Repoint `:android-app`, `:desktop-app`, `:web-app` to `:ui-compose`.
- Remove all `compose.*` and `navigation.compose` deps from `:core`/`:presentation`
  build files; they stay only in `:ui-compose`.
- **Outcome:** compiler now enforces "no Compose in logic." Existing apps unchanged.

### Phase 2 — Neutralize the 3 Compose touchpoints

- **i18n (`ConversationsViewModel`):** introduce a framework-free `StringProvider`
  interface in `:core`/`:presentation` (e.g. `interface StringProvider { suspend fun
  get(key: StringKey, vararg args: Any): String }`). Compose apps provide an actual
  backed by `org.jetbrains.compose.resources.getString`; the core-host provides an
  actual backed by a plain `.properties`/resource-bundle lookup over the same string
  keys. Replace the direct `getString(Res.string.*)` calls with `stringProvider.get(...)`.
  *(Existing string XML stays in place for the Compose apps; the host gets its own
  string source generated from or mirroring the same keys.)*
- **`viewModelFactory`/`getViewModel`:** split into (a) a plain Koin factory registration
  in `:presentation` (no `@Composable`), and (b) the `@Composable getViewModel()`
  convenience kept only in `:ui-compose`.
- **Navigation:** keep `navigation-compose` in `:ui-compose`. The
  `ui/navigation/{Screen,MediaNavigationState,ShareState}.kt` state types become plain
  sealed classes/enums in `:presentation` so non-Compose hosts can drive navigation too.
- **Migrate the 18 ViewModels to `androidx.lifecycle.ViewModel`** (resolved per §5.5).
  This also fixes a current leak — see §5.5. Mechanical, per file:
  1. Change `class XViewModel(...) : ViewModel()`.
  2. Replace the injected `scope: CoroutineScope = CoroutineScope(SupervisorJob() +
     Dispatchers.Default)` field with the built-in `viewModelScope`.
  3. Move any manual cleanup into an `override fun onCleared()`.
  4. Register with `viewModelOf { ::XViewModel }` (Koin) instead of the plain factory.
  5. Switch the `getViewModel()` actual from `koinInject()` to `koinViewModel()` in
     `:ui-compose` (the `koin.compose.viewmodel` dep is already on the project —
     `shared/build.gradle.kts:113`).
  6. Add the **multiplatform** `androidx.lifecycle:lifecycle-viewmodel` (2.8.x) dep to
     `:presentation`; repoint the catalog entry currently on the Android-only `-ktx`
     variant (`libs.versions.toml:74`) to the KMP coordinate. This is a *lifecycle* dep,
     not `compose.*`, so the no-Compose boundary still holds.

### Phase 3 — Headless JVM core-host process (`:core-host`)

- New JVM application module depending on `:core` + `:presentation`.
- Boots Koin (reuse `di/KoinInit.kt` + `JamiModule.kt`), starts the JVM `DaemonBridge`
  (JNI to libjami — same native libs the JVM desktop app already loads), and runs the
  app lifecycle equivalent to `desktop-app/.../Main.kt` minus the Compose `Window`.
- Hosts an embedded Ktor server (`ktor-server-*` — add to catalog; client side already
  present).
- Owns ViewModel instances per subscription and bridges their `StateFlow` to IPC.
- **ViewModel lifecycle on the host** (relevant if §5.5 chooses Compose `ViewModel`s):
  the host has no Composition or `LifecycleOwner`, so it manages VMs with a plain
  `ViewModelStore` + `ViewModelProvider` (both lifecycle types, not Compose). On
  `subscribe`, resolve/create the VM from a per-subscription store; on `unsubscribe`,
  call `viewModelStore.clear()`, which invokes `onCleared()` and cancels `viewModelScope`.
  This yields the same lifecycle semantics the Compose apps get from `viewModel { }`,
  without any Compose dependency. (With plain-class VMs instead, the host just calls the
  VM's existing manual `cancel()`/cleanup.)

### Phase 4 — State/Action IPC protocol

- Define a versioned JSON protocol (extend `docs/REST_BRIDGE_API.md` with a
  `/vm` namespace, or a sibling `docs/VM_IPC_API.md`):
  - `POST /vm/subscribe { vm, key } → { instanceId }`
  - `POST /vm/dispatch { instanceId, action, args }`
  - `POST /vm/unsubscribe { instanceId }`
  - `WS /vm/events` → `{ instanceId, state }` snapshots.
- Use `kotlinx.serialization` for state DTOs. Where a `StateFlow<XState>` holds
  non-serializable types, add `@Serializable` snapshot DTOs in `:presentation`
  (the state data classes are already plain data — mostly a mechanical annotation pass).
- Auth + lifecycle conventions copied from the existing REST bridge (`Authorization:
  Bearer`, localhost binding).

### Phase 5 — Qt/QML client (`:qt-app`)

- C++/QML app (separate build, e.g. CMake) that:
  - Connects to the host over `QWebSocket` (state) + `QNetworkAccessManager` (actions).
  - Wraps each subscribed ViewModel state as a `QObject` with `Q_PROPERTY`/signals, or a
    `QAbstractListModel` for list states (conversations, contacts, messages).
  - Maps QML user events to `dispatch(...)` calls.
- **Spike target:** implement the conversations list screen first (subscribe to
  `ConversationsViewModel`, render its `state`, dispatch filter/open actions) to validate
  the whole loop before building more screens.

### Phase 6 — Video pipeline over IPC

- The hardest cross-UI problem; scope it as its own milestone.
- `VideoSink`'s `surface: Any` cannot cross a process boundary as-is. Options to evaluate
  in this phase:
  - Host-side encode + stream decoded frames to Qt over a dedicated WS/shared-memory
    channel; Qt renders into a `QQuickItem`/`QVideoSink`.
  - Or co-locate a thin native video path in the Qt process and keep only signalling/state
    over IPC.
- Decision deferred to a focused design once Phases 1–5 prove the architecture.

---

## 4. Module / file change inventory (first pass)

| Action | Target |
|---|---|
| New module | `:core` (KMP, no Compose) |
| New module | `:presentation` (KMP, no Compose) |
| New module | `:ui-compose` (Compose) |
| New module | `:core-host` (JVM app + Ktor server) |
| New module | `:qt-app` (C++/QML, separate build) |
| Move | `net/jami/{services,model,repository,database}` → `:core` |
| Move | `net/jami/ui/viewmodel` → `:presentation` |
| Move | `net/jami/ui/{screens,composables,components,theme,modifiers,navigation}` → `:ui-compose` |
| Edit | `shared/build.gradle.kts` — strip Compose deps from logic modules |
| Edit | `di/ViewModelHelper.kt` — split Composable vs plain factory |
| Edit | `ui/viewmodel/ConversationsViewModel.kt` — `StringProvider` instead of `getString` |
| Edit | `settings.gradle.kts` — register new modules |
| New doc | `docs/VM_IPC_API.md` (or extend `REST_BRIDGE_API.md`) |

> Note: `:shared` may be kept as an umbrella that re-exports `:ui-compose` to minimize
> churn in the existing apps, or fully decomposed — decided at Phase 1 start.

---

## 5. Open decisions to confirm before Phase 1

1. **Compose-desktop app: client or in-process?** Cleanest symmetry is to also make
   `:desktop-app` an IPC client of `:core-host`. Lower-risk interim is to leave it
   in-process (JVM logic in the same process) and only route Qt over IPC. Recommend
   starting in-process for Compose, IPC for Qt; converge later if desired.
2. **String source for the host:** generate the host's `StringProvider` bundle from the
   existing `composeResources` XML, or maintain a parallel resource set. Recommend a
   small build step that converts the XML keys so there is one source of truth.
3. **Serialization scope:** annotate existing state classes `@Serializable` vs. introduce
   separate DTOs. Recommend annotating in place where types allow, DTOs only where state
   holds non-serializable fields.
4. **Video transport** (Phase 6): streamed decoded frames vs. native co-located path.
5. **ViewModel base: plain classes vs. Compose `ViewModel` — RESOLVED: use
   `androidx.lifecycle.ViewModel`.** Migration steps are in Phase 2.

   **Rationale.** The 18 ViewModels are currently plain Kotlin classes that own a
   `CoroutineScope(SupervisorJob() + Dispatchers.Default)`. Crucially, every platform's
   `getViewModel()` actual is `koinInject()` — **not** `koinViewModel()` — and the VMs are
   resolved as ordinary Koin definitions (`shared/src/*/di/ViewModelHelper.*.kt`). So today
   **nothing clears them**: when a screen leaves, `onCleared()` is never called and the VM's
   scope is never cancelled — a latent coroutine-scope leak that exists independent of Qt.
   Adopting real `ViewModel`s fixes this on the Compose side immediately, and gives the
   Qt-over-IPC host identical teardown semantics via `ViewModelStore.clear()` (Phase 3).

   The cost is low because the machinery is mostly present: `koin.compose.viewmodel` is
   already a `commonMain` dependency (`shared/build.gradle.kts:113`), and each VM already
   centralizes its scope in one field, so swapping to `viewModelScope` is mechanical. The
   only new dependency is the **multiplatform** `androidx.lifecycle:lifecycle-viewmodel`
   (a *lifecycle* dep, not `compose.*`) on `:presentation`, so the no-Compose boundary
   holds. `ViewModel` is a multiplatform lifecycle type usable on plain JVM; only the
   binding helpers (`viewModel { }` / `koinViewModel()` / `NavBackStackEntry` scoping) are
   Compose-bound and stay in `:ui-compose`.

   The only reason to keep plain classes would be a hard requirement that `:presentation`
   carry zero AndroidX dependencies — which buys nothing here and forfeits the leak fix.
