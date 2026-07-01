# E2E Test Harness — Implementation Status

Groundwork vertical slice for the end-to-end device test harness. Design: see
`doc/end2endTesting.md`. Plan: `~/.claude/plans/polymorphic-sparking-hippo.md`.

- **Branch:** `feature/e2e-test-harness`
- **Status:** groundwork + M3 implemented; compiles, assembles, packages.
  **Live device run PASSED** — M0 `ping`, M1 `account-creation`, and M3
  `two-device-contact` all green on physical hardware (Pixel 2 / Android 11 +
  Pixel 7a / Android 16). Plus host-side **screenshot diagnostics** (on-demand +
  auto-on-failure) and a **distinct harness app icon** (amber launcher background).
- **Last verified:** 2026-06-28

## Scope

The machinery, proven on real hardware from single-device transport up to a
two-device DHT handshake:
- **M0 `ping`** — proves transport: install → `adb reverse` → app + agent boot → role
  assignment → bidirectional WebSocket → ledger. No daemon interaction.
- **M1 `account-creation`** — drives the real `AccountCreationViewModel.createAccount()`
  and asserts the account is added (then removes it).
- **M3 `two-device-contact`** — two devices (roles A/B). Both create real Jami accounts
  and announce on the DHT (folds in the M2 REGISTERED await); each device's own Jami
  fingerprint is fetched over the **out-of-band coordination channel** (the identity
  relay); **B initiates a real contact request to A peer-to-peer over the DHT**; A observes
  it arrive (core proof) and accepts; both sides confirm the contact. Coordination channel
  carries the relayed identity + commands only — never the contact request itself.

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
| `.../e2e/HarnessServer.kt` | Ktor CIO WebSocket server, `DeviceConnection`, `Ledger`, `ScenarioContextImpl` (incl. `snapshot`/`snapshotAll`), `awaitNextConnection` |
| `.../e2e/DeviceController.kt` | adb/am wrappers (`reverse`, `startApp`, `startAgent`, `screenshot`, `listDevices`) |
| `.../e2e/Main.kt` | Entry point: start server → adb reverse + launch agents **sequentially** (deterministic role↔serial) → run scenario → auto-snapshot on failure → ledger → exit code |
| `.../e2e/scenarios/PingScenario.kt` | M0 |
| `.../e2e/scenarios/AccountCreationScenario.kt` | M1 |
| `.../e2e/scenarios/TwoDeviceContactScenario.kt` | M3 (two devices + identity relay) |

The runner is the **brain**: it owns scenarios, controls device state, stamps every event
with its own clock into a single merged timeline, and emits the verdict (exit code).

### Phase 4 — on-device agent (`android-app` `harness` flavor)
| File | Role |
|------|------|
| `android-app/build.gradle.kts` | `harness` product flavor (`applicationIdSuffix = ".harness"`); flavor-scoped Ktor-client deps |
| `src/androidHarness/AndroidManifest.xml` | `usesCleartextTraffic`; foreground `HarnessAgentService` |
| `src/androidHarness/.../harness/HarnessAgentService.kt` | Foreground service that hosts the agent |
| `src/androidHarness/.../harness/HarnessAgent.kt` | WS client; observes accounts, registration, and contacts (`IncomingTrustRequest`/`ContactAdded`) → `DomainEvent`s; waits for `JamiKoinHolder.koin` |
| `src/androidHarness/.../harness/CommandHandler.kt` | Maps `Directive` → real `AccountCreationViewModel`/`AccountService`/`DaemonBridgeApi` calls (incl. account URI, send/accept contact request) |

The device is a **thin executor**: no scenario knowledge. It exposes a command surface
(`CommandHandler`) and a state/observation surface (flow collection). No production-code or
`commonMain` changes — observability reuses existing public service Flows, and the account
fingerprint is read live via the already-bound `DaemonBridgeApi`.

### Diagnostics — screenshots + run artifacts (host-only)

First slice of the plan's Phase 7 "visible state", entirely host-side (no wire/APK change):

- **`DeviceController.screenshot(file)`** — `adb exec-out screencap -p` captured as raw bytes
  (stderr kept separate so the PNG isn't corrupted); never throws, returns `false` on failure
  so diagnostics can't derail a run or flip a verdict.
- **`ScenarioContext.snapshot(role, label): Path?`** — a scenario can grab a labeled
  screenshot at any point; it lands in the run dir and adds a timeline line.
- **Auto-snapshot on failure** — when a scenario returns a failing verdict *or* throws, the
  runner captures every assigned role (`snapshotAll("fail")`). Highest-value diagnostic.
- **Run directory** — each run writes to `e2e-runner/harness-memory/runs/<utcStamp>__<scenario>/`
  (gitignored via `e2e-runner/.gitignore`). Lightweight precursor to the Phase 6 `MemoryStore`.
- **Deterministic role ↔ serial** — a `snapshot("A", …)` must hit A's *physical* device, but
  roles were assigned in connect order. `Main` now starts agents one at a time and binds each
  connection to the serial just launched (`HarnessServer.awaitNextConnection`), so role A is
  always the first-launched device.

### Harness app icon

The `harness` flavor overlays `res/values/ic_launcher_background.xml` with a vivid amber
(`#FFAB00`) launcher background (same Jami logo foreground), so the E2E build is
unmistakable next to a white-background production install. The flavor's `res` dir is
repointed to `src/androidHarness/res` in `android-app/build.gradle.kts` — same KMP
layout-v2 reason the manifest needed repointing. `standard` flavor is unaffected.

## Wire protocol (out-of-band only)

```
device → runner:  Hello(requestedRole?)            ReportFrame(role, event, deviceTsMillis)
runner → device:  CommandFrame(commandId, directive)
DomainEvent:      Pong | AccountsSnapshot | AccountAdded | AccountRemoved
                  | RegistrationStateChanged | ErrorEvent
                  | AccountUri | IncomingContactRequest | ContactAdded
Directive:        Ping | GetAccounts | CreateJamiAccount | RemoveAccount
                  | GetAccountUri | SendContactRequest | AcceptContactRequest
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
| `:e2e-runner:e2e -Pscenario=ping` (live) | ✅ Pong round-trip on two devices |
| `:e2e-runner:e2e -Pscenario=account-creation` (live) | ✅ created + removed a real Jami account |
| `:e2e-runner:e2e -Pscenario=two-device-contact` (live) | ✅ B reached A over the DHT; contact confirmed on both (~14s) |
| Auto-snapshot on failure (live) | ✅ failing scenario wrote a valid 1080×2400 PNG to the run dir |
| `:android-app:assembleHarnessDebug` (amber icon) | ✅ merged `ic_launcher_background` = `#FFAB00` for harness flavor |

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
- **Flavor manifest path override** — under KMP android-source-set-layout-v2 the flavor's
  Kotlin is read from `src/androidHarness/`, but AGP-managed files (manifest, res) for a
  flavor still default to the legacy `src/harness/` path. The overlay manifest declaring
  `HarnessAgentService` was therefore silently ignored (the service compiled into the APK
  but was never registered → `am start-foreground-service` failed with "Not found"). Fixed
  by repointing the source set: `sourceSets.getByName("harness").manifest.srcFile(
  "src/androidHarness/AndroidManifest.xml")`, keeping manifest + Kotlin in one directory.

## Risks / things to watch on first live run

- **Agent start path** — launched via `am start-foreground-service`. Validated on the live
  run (Android 11 + Android 16): adb-initiated starts are privileged enough, no FGS block.
- **`await` event consumption** — `ScenarioContext.await` drains the role's event channel and
  discards non-matching events. Fine for the sequential M0/M1 scenarios; revisit if a future
  scenario needs to match out-of-order or replay events.
- **Account isolation** — relies on the `.harness` applicationId giving a separate data dir;
  M1 also removes the account it creates (ephemeral-per-run).

## Deferred (not in this groundwork)

- **M2** name registration (registering a public username + `NameRegistrationEnded` await).
  Note: M3 already folds in the DHT-async REGISTERED await that M2 was scoped around.
- **M4** call + recording (audio/video over the established connection).
- Account strategy beyond ephemeral-create-then-remove (reset-able pool).
- An `EventSink` seam in `commonMain` — only if a future event isn't already a public Flow.
