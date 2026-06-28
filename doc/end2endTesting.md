# End-to-End Device Testing — Design Sketch

Status: **DRAFT** — architecture settled; module/code layout not yet implemented.

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
accept → message → call → record). We start single-device and grow into two-device.

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

One deliberate exception that is *not* daemon traffic: relaying a device's **Jami ID**
over the coordination channel stands in for the real-world out-of-band identity exchange
(QR scan, share-link, contact card) — it's coordination metadata, never daemon
communication.

## The brain is in the service

The host **service is the test director**. It owns the scenario, sequences the steps,
controls each device's program state, records the run, and decides pass/fail. The
devices are **thin, controllable executors** — they run the same `testHarness` build,
expose a command + state surface, obey what the service tells them, and report back.
The devices hold *no scenario knowledge of their own*.

The service has three jobs, matching the objectives:

1. **Orchestrate scenarios** — drive the step sequence; gate progression (e.g. don't
   tell B to answer until A's call has actually reached the daemon).
2. **Protocol the run** — record a timestamped, merged timeline of every command issued
   and every state/event each device reported. This is the live documentation and the
   basis for the verdict.
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
   command + state        command + state           (same testHarness build)
   surface, agent         surface, agent
```

For single-device scenarios (the starting point) only one device/role is connected; the
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

A scenario is defined as host-side Kotlin in the runner module. It addresses devices by
**Role** (`A`/`B`, or `inviter`/`invitee`, `caller`/`callee`); the runner maps roles to
connected devices at launch.

```
Role        — logical participant the runner addresses ("caller", "callee", …)
Step        — a command the runner issues to a role (createAccount, invite, call, …)
Expectation — "await a DomainEvent from a role matching <predicate> within <timeout>"
Scenario    — ordered steps + expectations across roles; declares how many roles it needs
```

A single-device "much less" probe is just a one-role, one-step scenario.

### 2. Device command + state surface — testHarness flavor, on device

The thin executor side. Wraps the **real app entry points** (ViewModels / services
resolved via Koin) so the harness exercises the same paths users hit:

- **Command surface** — the agent receives a `Command` and invokes the matching real
  action (e.g. `AccountCreationViewModel.createAccount(...)`).
- **State / event surface** — see the observability layer below; the agent reports
  `DomainEvent`s and current state back to the runner.

The device knows *how* to perform atomic actions and *how* to report; it does not know
the scenario.

### 3. Observability layer — commonMain interface + testHarness impl

```
DomainEvent — normalized, serializable event (account ready, registration state, call
              placed, incoming call, message received, recording state changed, …)
EventSink   — the seam: an interface that domain events are emitted to
```

- **Production** binds a **no-op** `EventSink` (zero overhead, absent from prod APK).
- **testHarness** binds an impl that **collects the existing service `Flow`s**
  (`CallService._callUpdates`, `ConversationFacade` conversation/message events,
  `AccountService` account events, the `DaemonCallbacksImpl` fan-out) and translates
  them into `DomainEvent`s shipped to the runner.

The daemon runs **in the same process** as the UI (confirmed: no `android:process` in
the manifest), so events are already in-process coroutine Flows. No `BroadcastReceiver`
indirection is needed for intra-device signalling.

## Wire protocol

Persistent bidirectional **WebSocket** per device (control needs server→device push, so
plain HTTP POST is insufficient).

```
Report(event: DomainEvent)        device → runner   (events + state)
Command(directive: Directive)     runner → device   (createAccount, call, answer,
                                                      here-is-peer-id, abort, …)
```

The protocol types (`DomainEvent`, `Directive`, …) live in a **shared protocol module**
depended on by both the runner and the `testHarness` flavor — one source of truth for
the wire schema, type-safe end to end.

## Entry point — parameterized Gradle task

The task is a thin launcher; all logic lives in the runner (real, debuggable Kotlin),
not in the build script.

```
./gradlew e2e -Pscenario=bare-account-creation
./gradlew e2e -Pscenario=call-and-record -Pdevices=37281JEHN03065,FA7AJ1A06417
./gradlew e2eList            # prints the scenario registry
```

`-Pscenario=` selects from a **scenario registry** (`id → Scenario`) in the runner. Each
scenario declares how many roles it needs, so the runner validates the device set first.

What the task orchestrates, in order:

1. **Build + install** the `testHarnessDebug` APK on each assigned device
   (`dependsOn installTestHarnessDebug`).
2. **`adb reverse tcp:8080 tcp:8080`** on each device.
3. **Start the runner (brain)** and **launch the harness app** on each device
   (`am start`); each agent connects back over WebSocket and requests a role.
4. **Run the selected scenario** — issue commands, await reported events, record the
   merged timeline.
5. **Exit code = verdict** (non-zero on failure so CI gates on it); print the timeline.
6. **Teardown** — `adb reverse --remove`, reset accounts per the isolation strategy.

Role→device mapping: explicit `-Pdevices=serialA,serialB` wins; otherwise auto-assign by
`adb devices` order. Scenario-specific parameters (e.g. a username to register) come in
once M2 needs them.

## Module layout

```
:e2e-protocol   common/JVM — wire types (DomainEvent, Directive); shared source of truth
:e2e-runner     JVM        — the brain: scenario registry, orchestration, ledger,
                             verdict; hosts the Ktor WebSocket service; the `e2e`
                             Gradle task drives this
shared / android-app
  testHarness flavor       — on-device agent, EventSink impl, harness DI module;
                             depends on :e2e-protocol; absent from production APK
```

### Build setup

A dedicated product **flavor**, not just a build type:

```
flavorDimension "mode"
  standard      — production
  testHarness   — adds src/testHarness/ : agent, EventSink impl, harness DI module,
                  test-only permissions; absent from the production APK
```

Combined with the `debug` build type → `testHarnessDebug`. The harness DI module swaps
the no-op `EventSink` for the real Flow-collecting one and binds the command surface.

## Hard parts to design around

- **Async DHT is slow / non-deterministic.** DHT bootstrap, NAT traversal, swarm clone
  vary in timing. Every assertion is **event-driven with generous timeouts** (await on
  the reported event stream) — never fixed sleeps. The event stream *is* the
  synchronization primitive.
- **Account lifecycle / isolation.** Fresh ephemeral accounts per run = clean but slow
  (creation hits the network); a reset-able pool of pre-created accounts is the
  pragmatic middle. Still open — it shapes the harness lifecycle.
- **Identity handshake.** A's Jami ID must reach B at runtime; the runner relays it as
  coordination metadata via a `Command`.

## Milestone ladder

Single-device first to prove the whole machinery before adding two-device DHT timing.

- **M0** — harness skeleton: runner + Ktor service up, one device connects via
  `adb reverse`, round-trip a `ping` Report/Command.
- **M1** — single-device **bare account creation** (no username): runner commands
  `createAccount` via the real `AccountCreationViewModel`; `EventSink` observes
  `AccountAdded` + local registration state. First real proof of the full stack.
- **M2** — add **name registration** (`TRYING → REGISTERED`): first genuine DHT-async
  await; introduces scenario parameters (the username).
- **M3** — second device joins; identity relay; first two-device scenario (contact /
  swarm invite + accept).
- **M4** — call + recording end to end across two devices.

## Resolved decisions

- **Brain location** — in the host service/runner (scenario, control, verdict). Devices
  are thin controllable executors with no scenario knowledge.
- **Service implementation** — small **Ktor** WebSocket server inside the `:e2e-runner`
  JVM module; wire schema shared via `:e2e-protocol`.
- **Entry point** — parameterized `./gradlew e2e -Pscenario=…` driving the runner
  (installs, wires `adb reverse`, launches agents, runs scenario, verdict = exit code).
- **In-process signalling** — collect existing service `Flow`s via the `EventSink` seam;
  no `BroadcastReceiver`s.

## Still open

- **Account strategy** — ephemeral-per-run vs. reset-able pool of pre-created accounts.
  Decide before M1's isolation/teardown is finalized.
