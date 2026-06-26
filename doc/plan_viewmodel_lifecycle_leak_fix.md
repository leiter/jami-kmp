# Plan: Fix ViewModel Lifecycle Leak (migrate to `androidx.lifecycle.ViewModel`)

**Status:** Proposed — no code written yet
**Date:** 2026-06-26
**Scope:** Self-contained. Lives entirely within the current `:shared` module; does **not**
depend on the module split or the Qt/IPC effort. (It also satisfies the ViewModel-migration
step of `doc/plan_ui_logic_separation_qt_ipc.md`, so doing this first is a clean prerequisite.)

---

## 1. The bug

The 18 ViewModels in `shared/src/commonMain/kotlin/net/jami/ui/viewmodel` are plain Kotlin
classes. Each owns a long-lived scope:

```kotlin
class XViewModel(
    ...,
    scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
)
```

and exposes a **plain** `fun onCleared()` that calls `scope.cancel()`.

They are wired into Koin and Compose like this:

- `di/JamiModule.kt` registers each with `viewModelFactory { XViewModel(get(), ...) }`.
- `di/ViewModelHelper.*.kt` (every platform) implements that as:
  ```kotlin
  actual inline fun <reified T : Any> Module.viewModelFactory(...) = factory { definition(it) }
  @Composable actual inline fun <reified T : Any> getViewModel(): T = koinInject()
  ```

So resolution is a Koin **`factory`** fetched via **`koinInject()`** — i.e. an ordinary
object injection with **no lifecycle ownership**. Nothing calls `onCleared()` automatically.

Cleanup today is a manual convention applied in only **3 places**:

- `ui/navigation/JamiNavigation.kt:74` → `onDispose { appViewModel.onCleared() }`
- `ui/screens/CallScreen.kt:130` and `:215` → `onDispose { viewModel.onCleared() }`
- `ui/screens/LinkDeviceImportScreen.kt:106` → `onDispose { viewModel.onCleared() }`

### Consequence

For every other screen, the ViewModel's `CoroutineScope(SupervisorJob() + Dispatchers.Default)`
is **never cancelled**. Navigating away and back creates a fresh VM (factory) while the prior
one's scope, collectors, and any daemon subscriptions keep running. Scopes accumulate for the
process lifetime — a coroutine/observer leak. The pattern is also fragile: correctness depends
on each screen author remembering to hand-wire `onDispose { viewModel.onCleared() }`.

---

## 2. Root cause

ViewModel lifetime is not bound to any owner. `koinInject()` + a `factory` definition gives
plain DI with manual teardown, instead of `koinViewModel()` + `androidx.lifecycle.ViewModel`,
which binds the VM to the current `ViewModelStoreOwner` (the `NavBackStackEntry` in
Navigation Compose) and clears it automatically when that owner is destroyed.

---

## 3. Fix

Adopt real `androidx.lifecycle.ViewModel`s resolved through `koinViewModel()`. This ties each
VM's lifetime to its navigation destination / composition owner, so `onCleared()` runs (and the
scope cancels) exactly when the screen is gone — automatically, for all 18 VMs, with no
per-screen wiring.

`androidx.lifecycle.ViewModel` is a multiplatform *lifecycle* type (not Compose) and works on
all current targets. `koinViewModel` comes from `koin-compose-viewmodel`, **already a
`commonMain` dependency** (`shared/build.gradle.kts:113`), which also brings the multiplatform
`lifecycle-viewmodel` transitively (add an explicit `commonMain` dep for clarity).

---

## 4. Per-ViewModel migration (mechanical, 18 files)

For each `XViewModel`:

1. `class XViewModel(...) : ViewModel()`.
2. Delete the `scope` constructor parameter and the `private val scope = scope` field;
   replace all `scope.` usages with the built-in `viewModelScope`.
3. Convert `fun onCleared()` to `override fun onCleared()`; keep its body (any extra
   `job?.cancel()` cleanup such as `CallViewModel`'s timers stays). The explicit
   `scope.cancel()` line can be dropped — `viewModelScope` is cancelled by the base class.
4. Leave the public `StateFlow` API and all action methods unchanged.

VMs confirmed to have an explicit `onCleared()` today: `AppViewModel`, `ConversationsViewModel`,
`PendingRequestsViewModel`, `AboutViewModel`, `DebugLogsViewModel`, `ContactDetailsViewModel`
(and `CallViewModel` with extra job cancellation). VMs without one still gain correct teardown
once they extend `ViewModel`.

---

## 5. Cross-cutting changes

| Action | Target |
|---|---|
| Add explicit dep | `androidx.lifecycle:lifecycle-viewmodel` (multiplatform) to `commonMain`; point the catalog away from the Android-only `-ktx` entry (`gradle/libs.versions.toml:74`) to the KMP coordinate |
| Change DI registration | `di/JamiModule.kt` — replace each `viewModelFactory { XViewModel(...) }` with `viewModelOf(::XViewModel)` (or `viewModel { XViewModel(get(), ...) }`) from `koin-compose-viewmodel` |
| Change resolver | `di/ViewModelHelper.*.kt` (6 files) — `getViewModel()` actual becomes `koinViewModel()` instead of `koinInject()`; drop the custom `viewModelFactory` expect/actual in favour of Koin's built-in `viewModel { }` DSL |
| Remove manual teardown | delete `onDispose { ...onCleared() }` at `JamiNavigation.kt:74`, `CallScreen.kt:130` & `:215`, `LinkDeviceImportScreen.kt:106` — now handled by the owner |
| Update tests | `shared/src/commonTest/.../viewmodel/*Test.kt` call `vm.onCleared()` directly; after migration `onCleared()` is `protected`. Drive teardown through a `ViewModelStore` (put the VM, call `store.clear()`) or a small test helper exposing clear. See §6. |

---

## 6. Considerations / risks

- **`onCleared()` visibility in tests.** `androidx.lifecycle.ViewModel.onCleared()` is
  `protected`, so the ~10 tests that call `vm.onCleared()` directly will not compile as-is.
  Resolve by having tests own a `ViewModelStore`, register the VM, and call `viewModelStore.clear()`
  (the same mechanism the JVM IPC host would use). Decide on one shared test helper for this.
- **`koinViewModel()` requires a `ViewModelStoreOwner` in scope.** Navigation Compose provides
  one per `NavBackStackEntry`; for any VM obtained outside a nav destination (dialogs, nested
  composables), confirm a `LocalViewModelStoreOwner` is present, otherwise scope it explicitly.
- **Re-fetch semantics change.** With `koinViewModel()` the same destination returns the *same*
  VM instance across recompositions (owner-scoped), whereas the old `factory` could yield a new
  instance. This is the desired behaviour but worth verifying for any screen that relied on
  getting a fresh VM.
- **Default-scope call sites.** A few VMs were instantiated directly in tests with the default
  `scope` argument; removing the constructor parameter means those construction sites no longer
  pass a scope (they already relied on the default, so this is a straightforward edit).

---

## 7. Suggested order

1. Add the lifecycle dep + switch the DI helpers (`ViewModelHelper.*.kt`, `JamiModule.kt`).
2. Migrate VMs file by file (§4).
3. Remove the 4 manual `onDispose { onCleared() }` call sites.
4. Update the affected tests to clear via `ViewModelStore`.
