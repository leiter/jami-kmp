# E2E Test Harness — Implementation Status

Groundwork vertical slice for the end-to-end device test harness. Design: see
`doc/end2endTesting.md`. Plan: `~/.claude/plans/polymorphic-sparking-hippo.md`.

- **Branch:** `feature/e2e-test-harness`
- **Status:** all 5 groundwork phases implemented; compiles, assembles, packages. **Live
  device run not yet executed** (no device attached at implementation time).
- **Last verified:** 2026-06-28

## Scope

Single-device vertical slice that proves the full machinery before two-device DHT timing:
- **M0 `ping`** — proves transport: install → `adb reverse` → app + agent boot → role
  assignment → bidirectional WebSocket → ledger. No daemon interaction.
- **M1 `account-creation`** — drives the real `AccountCreationViewModel.createAccount()`
  and asserts the account is added (then removes it). Name-registration/REGISTERED is M2.

## What was built

### Phase 1 — protocol + catalog
| File | Change |
|------|--------|
| `gradle/libs.versions.toml` | Added `ktor-server-core`, `ktor-server-cio`, `ktor-server-websockets`; `kotlinJvm` plugin alias |
| `build.gradle.kts` (root) | `alias(libs.plugins.kotlinJvm) apply false` |
| `settings.gradle.kts` | `include(":e2e-protocol")`, `include(":e2e-runner")` |
| `e2e-protocol/build.gradle.kts` | Plain JVM + serialization; `api(kotlinx-serialization-json)` |
| `e2e-protocol/.../protocol/Wire.kt` | Wire schema: `Envelope` (`Hello`/`ReportFrame`/`CommandFrame`), `DomainEvent`, `Directive`, `HarnessJson` |

### Phase 2 + 3 — host runner (`:e2e-runner`)
| File | Role |
|------|------|
| `e2e-runner/build.gradle.kts` | JVM + serialization + `application`; registers `e2e` and `e2eList` Gradle tasks |
| `.../e2e/Scenario.kt` | `Scenario`, `ScenarioContext`, `Verdict`, `ScenarioRegistry`, `ROLE_NAMES` |
| `.../e2e/HarnessServer.kt` | Ktor CIO WebSocket server, `DeviceConnection`, `Ledger`, `ScenarioContextImpl` |
| `.../e2e/DeviceController.kt` | adb/am wrappers (`reverse`, `startApp`, `startAgent`, `listDevices`) |
| `.../e2e/Main.kt` | Entry point: start server → adb reverse + launch → await roles → run scenario → ledger → exit code |
| `.../e2e/scenarios/PingScenario.kt` | M0 |
| `.../e2e/scenarios/AccountCreationScenario.kt` | M1 |

The runner is the **brain**: it owns scenarios, controls device state, stamps every event
with its own clock into a single merged timeline, and emits the verdict (exit code).

### Phase 4 — on-device agent (`android-app` `harness` flavor)
| File | Role |
|------|------|
| `android-app/build.gradle.kts` | `harness` product flavor (`applicationIdSuffix = ".harness"`); flavor-scoped Ktor-client deps |
| `src/androidHarness/AndroidManifest.xml` | `usesCleartextTraffic`; foreground `HarnessAgentService` |
| `src/androidHarness/.../harness/HarnessAgentService.kt` | Foreground service that hosts the agent |
| `src/androidHarness/.../harness/HarnessAgent.kt` | WS client; observes `AccountService.accounts`/`accountEvents` → `DomainEvent`s; waits for `JamiKoinHolder.koin` |
| `src/androidHarness/.../harness/CommandHandler.kt` | Maps `Directive` → real `AccountCreationViewModel`/`AccountService` calls |

The device is a **thin executor**: no scenario knowledge. It exposes a command surface
(`CommandHandler`) and a state/observation surface (flow collection). No production-code or
`commonMain` changes — observability reuses existing public service Flows.

## Wire protocol (out-of-band only)

```
device → runner:  Hello(requestedRole?)            ReportFrame(role, event, deviceTsMillis)
runner → device:  CommandFrame(commandId, directive)
DomainEvent:      Pong | AccountsSnapshot | AccountAdded | AccountRemoved
                  | RegistrationStateChanged | ErrorEvent
Directive:        Ping | GetAccounts | CreateJamiAccount | RemoveAccount
```

Reached over `ws://127.0.0.1:8080` via `adb reverse`. Carries **no Jami payload** — the
real daemon-to-daemon traffic flows separately over the DHT.

## Build verification

| Check | Result |
|-------|--------|
| `:e2e-protocol:compileKotlin` | ✅ |
| `:e2e-runner:compileKotlin` | ✅ |
| `:e2e-runner:e2eList` | ✅ lists `account-creation`, `ping` |
| `:android-app:compileHarnessDebugKotlinAndroid` | ✅ |
| `:android-app:assembleHarnessDebug` | ✅ (APK packaged) |
| Live device scenario run | ⏳ pending (no device attached) |

## Running it (once a device is attached)

```
./gradlew :e2e-runner:e2eList
./gradlew :e2e-runner:e2e -Pscenario=ping             -Pdevices=<serial>
./gradlew :e2e-runner:e2e -Pscenario=account-creation -Pdevices=<serial>
```
Exit code 0 = pass; the merged timeline prints regardless. `-Pdevices` defaults to the
first online device from `adb devices` if omitted.

## Deviations from the plan

- **Flavor named `harness`, not `testHarness`** — AGP forbids product-flavor names starting
  with `test`. Downstream names follow: applicationId `net.jami.android.harness`, source set
  `androidHarness`, install task `installHarnessDebug`.
- Adding the flavor splits `android-app` install tasks into `installStandardDebug` /
  `installHarnessDebug` (no bare `installDebug`). Shared-module compile tasks are unaffected.

## Risks / things to watch on first live run

- **Agent start path** — launched via `am start-foreground-service`. adb-initiated starts are
  normally privileged enough; if Android blocks the background FGS start, start the agent
  from the app side (harness flavor) instead.
- **`await` event consumption** — `ScenarioContext.await` drains the role's event channel and
  discards non-matching events. Fine for the sequential M0/M1 scenarios; revisit if a future
  scenario needs to match out-of-order or replay events.
- **Account isolation** — relies on the `.harness` applicationId giving a separate data dir;
  M1 also removes the account it creates (ephemeral-per-run).

## Deferred (not in this groundwork)

- **M2** name registration (first DHT-async await; scenario parameters).
- **M3** second device + identity relay; **M4** call + recording.
- Account strategy beyond ephemeral-create-then-remove (reset-able pool).
- An `EventSink` seam in `commonMain` — only if a future event isn't already a public Flow.
