# End-to-End Device Testing

Status: **IMPLEMENTED (single- and two-device)** — the harness runs real scenarios against
the live daemon on USB-connected Android devices. M0–M2 plus an account-fixture pool and the
import/name-registration edge cases are **live-validated on hardware**. Two-device contact
(M3) is now **live-validated on two devices** (2026-07-01: B's DHT contact request reached A
and the contact confirmed bidirectionally); calls + recording (M4) are not started.

## Goal

Exercise the shared module's **daemon-backed functionality against the real daemon on
physically connected Android devices**, driving real interactions (account setup,
swarm/conversation creation, messaging, calls, recording, …) and verifying that each
side observes the expected sequence of events.

This is the opposite end of the spectrum from the existing `commonTest` unit tests
(`StubDaemonBridge` + `TestFixtures`, JVM, no real daemon). The two are complementary:
unit tests assert logic in isolation; this asserts **real behaviour against the live
DHT**.

Scope ranges from single-device "much less" probes (one capability: account creation,
one daemon round-trip) up to full two-device "much more" scenarios (account → invite →
accept → message → call → record). We started single-device and are growing into two-device.

## Two independent channels (critical)

There are two completely separate channels, and they must never mix:

1. **Real Jami channel — the thing under test.** Daemon ↔ daemon over the actual
   DHT / ICE / TURN / swarm transport. All calls, messages, and swarm sync flow here,
   peer-to-peer, exactly as in production. The harness does **not** touch this path.
2. **localhost coordination channel — out-of-band, control + observe only.** The
   WebSocket service is used *only* to orchestrate the test sequence, control device
   program state, and document the run. It carries **no Jami payload of any kind**. If
   it ever relayed call/message content, we would be testing the harness instead of
   Jami.

Two deliberate exceptions that are *not* daemon traffic and legitimately cross the
coordination channel:

- Relaying a device's **Jami ID** (fingerprint) stands in for the real-world out-of-band
  identity exchange (QR scan, share-link, contact card).
- Transferring an **account archive** (`account.gz`) between host and device — pulled/pushed
  over adb `run-as` — stands in for an out-of-band backup/restore. This powers the reusable
  fixture pool; it is never daemon-to-daemon traffic.

## The brain is in the service

The host **service is the test director**. It owns the scenario, sequences the steps,
controls each device's program state, records the run, and decides pass/fail. The
devices are **thin, controllable executors** — they run the same `harness` build,
expose a command + state surface, obey what the service tells them, and report back.
The devices hold *no scenario knowledge of their own*.

The service has three jobs, matching the objectives:

1. **Orchestrate scenarios** — drive the step sequence; gate progression (e.g. don't
   tell B to answer until A's call has actually reached the daemon).
2. **Protocol the run** — record a timestamped, merged timeline of every command issued
   and every state/event each device reported. This is the live documentation and the
   basis for the verdict. On failure, screenshots of every role are auto-captured.
3. **Control device program state** — push each device into specific states (create
   account, place call, answer, toggle recording, …) through the device's real app
   entry points, and query/await each device's reported state.

## Topology

```
Host (laptop / CI)
  └─ Coordination service / runner  ws://:8080
        the brain: scenario · sequencing · device control · merged timeline · verdict
        ▲                      ▲
   adb reverse            adb reverse
        │ ws                   │ ws
  Pixel 7a (role A)      Pixel 2 (role B)        ← thin controllable executors
   command + state        command + state           (same harness build)
   surface, agent         surface, agent
```

For single-device scenarios (the current default) only one device/role is connected; the
topology and protocol are otherwise identical.

### Reachability — `adb reverse` over USB

Devices are USB-connected, so each gets a USB-tunnelled loopback to the host:

```
adb -s 37281JEHN03065 reverse tcp:8080 tcp:8080   # Pixel 7a
adb -s FA7AJ1A06417   reverse tcp:8080 tcp:8080   # Pixel 2
```

Each device then connects to `ws://localhost:8080`. No Wi-Fi/LAN/firewall config, fully
deterministic.

## Layers (interface-first)

### 1. Scenario layer — lives in the runner (host), not on devices

A scenario is host-side Kotlin in `:e2e-runner`. It addresses devices by **Role**
(`A`/`B`, …); the runner maps roles to connected devices at launch. A `Scenario`
implements `suspend fun run(ctx: ScenarioContext): Verdict` and declares `requiredRoles`.
`ScenarioContext` is the whole control + observation surface:

```
send(role, directive)                 issue a command to a role's device
await(role, timeout, predicate)       suspend until a matching DomainEvent (never a sleep)
log(message)                          append to the merged timeline
snapshot(role, label)                 screenshot into the run dir
pull/pushArtifact(role, …)            move an account.gz over the coordination channel
memory                                the persistent fixture registry (MemoryStore)
captureAsset / installAsset / pushAsset   export→store / restore / push-only for fixtures
```

A single-device "much less" probe is just a one-role scenario.

### 2. Device command + state surface — `harness` flavor, on device

The thin executor side (`android-app/src/androidHarness/`). `HarnessAgent` connects back
over the WebSocket and `CommandHandler` maps each `Directive` to the **real app entry
points** (ViewModels / services resolved via Koin), so the harness exercises the same
paths users hit. The device knows *how* to perform atomic actions and *how* to report; it
does not know the scenario.

### 3. Observability — `HarnessAgent` collects the existing service Flows

The originally-sketched `EventSink` seam (a commonMain interface with a no-op prod binding)
proved unnecessary and was **not** built. Because all agent code lives only in the `harness`
flavor and is absent from the production APK, the agent can subscribe to the real service
`Flow`s **directly** and translate them into `DomainEvent`s on the wire:

- `AccountService.accounts` diffed → `AccountAdded` / `AccountRemoved`
- `AccountService.accountEvents` → `RegistrationStateChanged`, `NameRegistrationEnded`,
  `IncomingContactRequest`, `ContactAdded`

The daemon runs **in the same process** as the UI (no `android:process` in the manifest),
so events are already in-process coroutine Flows — no `BroadcastReceiver` indirection.

> Caveat baked into the harness: `AccountAdded` is derived by **diffing the accounts Flow
> from its value at connect time**, so an account stranded by a previous run is invisible in
> the timeline. Any account-touching scenario therefore calls `ensureNoAccounts` first (see
> "Clean-slate precondition").

## Wire protocol

Persistent bidirectional **WebSocket** per device. Frames (`Envelope` sealed hierarchy):

```
Hello(requestedRole?)                 device → runner   announce presence / request a role
ReportFrame(event, deviceTsMillis)    device → runner   observed DomainEvent
CommandFrame(commandId, directive)    runner → device   a Directive to execute
```

The protocol types live in the shared **`:e2e-protocol`** module, depended on by both the
runner and the `harness` flavor — one source of truth, type-safe end to end. Current
directives: `Ping`, `GetAccounts`, `CreateJamiAccount`, `CreateBareAccount`, `ExportAccount`,
`ImportAccount`, `RemoveAccount`, `ChangePassword`, `SetAccountEnabled`, `LookupName`,
`GetAccountUri`, `RegisterName`, `SendContactRequest`, `AcceptContactRequest`. Current events:
`Pong`, `AccountsSnapshot`, `AccountAdded`, `AccountRemoved`, `RegistrationStateChanged`,
`NameRegistrationEnded`, `AccountExported`, `PasswordChanged`, `NameLookupResult`, `AccountUri`,
`IncomingContactRequest`, `ContactAdded`, `ErrorEvent`.

## Account fixtures & the reuse pool (the account strategy — resolved)

Account **creation** (key gen + DHT bootstrap) is the slow operation, and name registration
is worse — it **permanently burns a global name** on the Jami name server. So the harness
minimizes both by reusing a persistent pool of pre-created account archives.

- **`MemoryStore`** (`:e2e-runner`, under `harness-memory/` — git-ignored, archives hold
  private keys) is the registry: `fixtures/index.json` is the source of truth,
  `fixtures/blobs/<fingerprint>.gz` are identity-keyed archives, and
  `fixtures/accounts/acct__<state-hint>__<fp8>.json` are **regenerated human-hint**
  descriptor filenames (never parsed for logic).
- **Claim by STATE, not fixed key**: `claim(named=?, hasPassword=?)`. A scenario asks for the
  state it needs; only a `null` result triggers creation (last resort).
- **Consuming vs non-consuming.** Name registration is *consuming* (flips an asset
  unnamed→named and burns a global name — one-way, logged in `burnedNames`). Import and
  password checks are *non-consuming* — one password-protected fixture serves forever.
- **`seed-pool`** is a status-aware, idempotent producer: it reports the current composition
  and creates **only the missing** unnamed accounts to reach per-state targets — **zero name
  burns**. Named assets are never seeded; they *emerge* when the registration test flips one.
- **Memorable names.** Registration test names are human-memorable and namespaced
  (`e2e-<adjective>-<noun>`, e.g. `e2e-mellow-raven`), generated by `MemorableNames` and
  deduped against `burnedNames`. Charset matches Jami's registered-name filter.

## Clean-slate precondition

An import/registration is an onboarding-state operation — it must run with **no account
loaded**. `ensureNoAccounts(ctx, role)` snapshots via `GetAccounts`, removes any loaded
account, and re-snapshots to *confirm* empty (recorded in the timeline). Scenarios also run
a best-effort `ensureNoAccounts` sweep in `finally`, so a run can never strand an account for
the next one.

## Entry point — parameterized Gradle task

The task is a thin launcher; all logic lives in the runner (real, debuggable Kotlin).

```
./gradlew :e2e-runner:e2e -Pscenario=account-creation-bare -Pdevices=37281JEHN03065
./gradlew :e2e-runner:e2e -Pscenario=two-device-contact -Pdevices=37281JEHN03065,FA7AJ1A06417
./gradlew :e2e-runner:e2eList            # prints the scenario registry
```

`-Pscenario=` selects from the **scenario registry** (`id → Scenario`) in the runner. Each
scenario declares `requiredRoles`, so the runner validates the device set first. What the
task orchestrates: build + install the `harnessDebug` APK (`dependsOn installHarnessDebug`),
`adb reverse tcp:8080`, start the runner + launch the agent, run the scenario, print the
merged timeline, exit code = verdict.

## Scenario suite (implemented)

| id | roles | what it proves |
|---|---|---|
| `ping` | 1 | round-trip Report/Command (M0) |
| `account-creation-bare` | 1 | bare account, no username (M1) |
| `account-creation-username` | 1 | reuse-first name registration → `REGISTERED` + `NameRegistrationEnded state=0` (M2, consuming) |
| `register-name-taken` | 1 | registering an already-burned name is rejected (`state=3`) |
| `account-reuse` | 1 | export → pull → remove → push → import preserves identity |
| `import-correct-password` | 1 | protected archive + correct password restores identity |
| `import-wrong-password` | 1 | wrong password rejected (no usable account) |
| `import-no-password` | 1 | empty password on a protected archive rejected |
| `change-password` | 1 | add / change / remove an archive password; the re-encrypted archive imports under the new password and rejects the old (reuses a `pw` fixture, non-consuming) |
| `account-enable-disable` | 1 | registration toggle: disable → `UNREGISTERED`, enable → `REGISTERED` (reuses a fixture, non-consuming) |
| `name-lookup` | 1 | name-server **read**: a burned name resolves to its exact owner fingerprint (`state=0`), an unregistered name returns NotFound (`state=2`) — non-consuming |
| `seed-pool` | 1 | status-aware pool top-up, zero name burns |
| `two-device-contact` | 2 | B's contact request reaches A over the real DHT, accept + confirm (M3, **validated on 2 devices 2026-07-01**) |

## Recommended additional scenarios — account handling

Gaps found by cross-referencing the `AccountService` surface against the suite above
(2026-07-01). Prioritized by value × reachability × fit with the existing harness. Only
daemon-backed, user-reachable operations are listed (unit-testable logic is out of scope).

### Single-device — high value, do first

| candidate | operation | proves | notes |
|---|---|---|---|
| ~~`change-password`~~ ✅ **DONE (2026-07-01)** | `changeAccountPassword(id, old, new)` | add / change / remove an archive password | Implemented as `change-password` (see the suite above). Reuses a `pw` pool fixture (realistic "change an existing password"), drives change → remove → add on the phone copy, and proves the archive re-encrypted via an export→re-import round-trip (new password imports + preserves identity; old password rejected with `ERROR_GENERIC` teardown). Non-consuming — the phone copy is removed, the host blob never rewritten. A **negative control** (wrong old password ⇒ `success=false`) anchors the boolean. Gate: password ops must wait for `REGISTERED` — `changeAccountPassword` fails while the account is still `INITIALIZING`. Added `ChangePassword` directive + `PasswordChanged` event. |
| ~~`account-enable-disable`~~ ✅ **DONE (2026-07-01)** | `setAccountEnabled(id, false/true)` | registration toggle | Implemented as `account-enable-disable` (see the suite above). Non-consuming: claim a fixture → baseline `REGISTERED` → disable → `UNREGISTERED` → enable → `REGISTERED`, observed via the existing `RegistrationStateChanged` flow (no result event needed). Added a `SetAccountEnabled` directive. Validated on Pixel 7a (~4s). |
| ~~`name-lookup`~~ ✅ **DONE (2026-07-01)** | `findRegistrationByName` | name-server **read** side | Implemented as `name-lookup` (see the suite above). Uses the harness burn log as ground truth: a burned name resolves to its exact owner fingerprint (`state=0`), an unregistered name returns NotFound (`state=2`). Non-consuming. `findRegistrationByName` is `suspend` → the handler awaits it (bounded, `-1` sentinel on no answer) and emits a synchronous `NameLookupResult`; no new agent Flow needed. Added `LookupName` directive + `NameLookupResult` event. Validated on Pixel 7a (~4s). |

### Two-device / higher effort — gate on M3

| candidate | operation | proves | notes |
|---|---|---|---|
| device linking & management | `addDevice` / `confirmAddDevice` / `provideAccountAuthentication`, `getKnownRingDevices`, `revokeDevice`, `renameDevice` | link a new device to an existing account over the DHT, list, revoke | The **biggest untested account area.** The link flow is inherently two-device (DHT-async) → fold into the M3 push. `renameDevice` + `getKnownRingDevices` are single-device observable and could be a small standalone test sooner. |
| multi-account coexistence | `setCurrentAccount`, `setAccountOrder` | two Jami accounts on one device, independent registration + switching | Reachable, single-device, but requires relaxing the `ensureNoAccounts` precondition for just this scenario. Medium value. |

### Deliberately out of scope

- **`migrateAccount`** — needs a legacy-format archive we can't easily produce.
- **`updateProfile`** — weak observability; belongs with the deferred contact/profile scenarios.
- **SIP account creation** (`createSipAccount`) — reachable, but SIP has no Jami identity / DHT; a separate track, not "account handling" here.

Suggested order: ~~`change-password`~~ → ~~`account-enable-disable`~~ → ~~`name-lookup`~~ — **all
three single-device recommendations are now done.** Remaining account-handling work is the
two-device / gated tier (device linking & management, multi-account coexistence).

## Module layout

```
:e2e-protocol   JVM   — wire types (Envelope/DomainEvent/Directive); shared source of truth
:e2e-runner     JVM   — the brain: scenario registry, orchestration, ledger, verdict,
                        MemoryStore fixture registry, DeviceController (adb); hosts the Ktor
                        WebSocket server; the `e2e` / `e2eList` Gradle tasks drive this
android-app
  harness flavor      — src/androidHarness/: HarnessAgent + CommandHandler, distinct app
                        icon + applicationId suffix `.harness`; depends on :e2e-protocol;
                        absent from the production APK
```

### Build setup

A dedicated product **flavor** (dimension `mode`): `standard` (production) and `harness`.
AGP forbids flavor names starting with `test`, hence `harness`. Combined with `debug` →
`harnessDebug` (`applicationIdSuffix = ".harness"`, so it installs alongside a standard
build). KMP's android-source-set-layout-v2 reads the flavor's **Kotlin** from
`src/androidHarness/`, while AGP reads the flavor's manifest/res from `src/harness/` by
default — both are repointed to `src/androidHarness/` in `android-app/build.gradle.kts`.

## Daemon behaviours observed (empirical, on-device)

- **A Jami account's own address is its public-key fingerprint**, exposed by the daemon under
  `ConfigKey.ACCOUNT_USERNAME`. It appears shortly *after* creation (key derivation lags,
  notably for password accounts), so `GetAccountUri` retries.
- **Import is optimistic.** `addAccount(archive)` emits `AccountAdded` → `INITIALIZING`
  *before* it decrypts, even for a wrong/empty password — then fails with `ERROR_GENERIC` and
  the daemon **tears the account down** (`AccountRemoved`). So `AccountAdded` is **not** a
  success signal for imports; success is judged by the **resolved fingerprint** (correct
  decrypt → URI equals the known asset fingerprint; failed decrypt → torn down / never
  resolves).
- **Name-server result codes** (`NameRegistrationEnded.state`): `0` = success, `3` = already
  taken. The app treats any non-zero as failure without distinguishing codes.
- **`changeAccountPassword` needs a fully-loaded account.** It re-encrypts the on-disk archive
  and returns `false` if the account is still `INITIALIZING` (observed: a call ~6 ms after
  `AccountAdded` failed even with the correct current password). Wait for `REGISTERED` before
  any password operation. A wrong *old* password also returns `false`, so the boolean doubles
  as a password check — pair it with a known-good positive to disambiguate "wrong password"
  from "not ready yet".

## Hard parts to design around

- **Async DHT is slow / non-deterministic.** Every assertion is **event-driven with generous
  timeouts** (`await` on the reported event stream) — never fixed sleeps. The event stream
  *is* the synchronization primitive.
- **Account lifecycle / isolation** — resolved via the reset-able fixture pool +
  `ensureNoAccounts` (above).
- **Identity handshake** — A's Jami ID reaches B at runtime as coordination metadata via a
  `GetAccountUri` relay.

## Milestone ladder

- **M0** ✅ harness skeleton: runner + Ktor service, one device via `adb reverse`, `ping`
  round-trip.
- **M1** ✅ single-device bare account creation via the real service path; agent observes
  `AccountAdded` + registration state.
- **M2** ✅ name registration (`TRYING → REGISTERED → NameRegistrationEnded state=0`), the
  first genuine DHT-async await; introduced scenario parameters (the username) and the
  fixture pool.
- **M3** ✅ second device joins; identity relay; two-device contact request + accept
  (`two-device-contact` **validated on two devices 2026-07-01**: full run ~13 s — both
  accounts `REGISTERED`, B's request reached A over the DHT, contact confirmed bidirectionally,
  clean teardown with no stranded accounts).
- **M4** ⬜ call + recording end to end across two devices.

## Resolved decisions

- **Brain location** — in the host runner (scenario, control, verdict); devices are thin
  executors with no scenario knowledge.
- **Service implementation** — Ktor WebSocket server in `:e2e-runner`; wire schema shared via
  `:e2e-protocol`.
- **Entry point** — `./gradlew :e2e-runner:e2e -Pscenario=…` driving the runner.
- **In-process signalling** — the `harness`-flavor agent collects existing service `Flow`s
  directly (no `EventSink` seam, no `BroadcastReceiver`s).
- **Account strategy** — reset-able **fixture pool** (`MemoryStore`, claim-by-state), creation
  minimized and treated as a producer; clean-slate precondition per run.

## Still open

- **Harden `two-device-contact` onto the pool** — the two-device run is validated (2026-07-01),
  but the scenario still creates fresh accounts per run and does **not** call `ensureNoAccounts`
  first. Fold it onto the fixture pool (claim two unnamed assets) + add the clean-slate
  precondition/sweep both roles, matching the single-device scenarios.
- **M4** — calls + recording across two devices.
- **Contact / data-state fixtures** — the registry models identity + password + name only;
  contacts / swarm membership are deferred to the contact scenarios.
