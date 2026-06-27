# End-to-End Device Testing — First Sketch

Status: **DRAFT / sketch** — capturing the agreed shape before committing to a plan.

## Goal

Exercise the shared module's **daemon-backed functionality against the real daemon on
two physically connected Android devices**, driving real peer interactions (account
setup, swarm/conversation creation, messaging, calls, recording, …) and verifying that
both sides observe the expected sequence of events.

This is the opposite end of the spectrum from the existing `commonTest` unit tests
(`StubDaemonBridge` + `TestFixtures`, JVM, no real daemon). The two are complementary:
unit tests assert logic in isolation; this asserts **distributed behaviour against the
live DHT**.

Scope ranges from "much more" (full A↔B scenario: account → invite → accept → message →
call → record) down to "much less" (single-capability probes: one daemon round-trip,
one callback).

## Two independent channels (critical)

There are two completely separate channels, and they must never mix:

1. **Real Jami channel — the thing under test.** Daemon ↔ daemon over the actual
   DHT / ICE / TURN / swarm transport. All calls, messages, and swarm sync flow here,
   peer-to-peer, exactly as in production. The harness does **not** touch this path.
2. **localhost coordination channel — out-of-band, observe + sequence only.** The
   WebSocket service is used *only* to coordinate the test sequence and document test
   state. It carries **no Jami payload of any kind**. If it ever relayed call/message
   content, we would be testing the harness instead of Jami.

One deliberate exception that is *not* daemon traffic: relaying a device's **Jami ID**
over the coordination channel stands in for the real-world out-of-band identity exchange
(QR scan, share-link, contact card) — it's coordination metadata, never daemon
communication.

## Topology

A central, network-addressable **rendezvous/coordination service** runs on the host.
Both phones are clients of it for **test orchestration and observability only** — never
for daemon-to-daemon communication (see "Two independent channels" above). One
authoritative service means one clock, one merged timeline, one verdict.

```
Host (laptop / CI)
  └─ Rendezvous Service   ws://:8080    single clock · merged timeline · correlation
        ▲                      ▲          · ID/value relay · pass-fail verdict source
   adb reverse            adb reverse
        │ ws                   │ ws
  Pixel 7a (role A)      Pixel 2 (role B)
   EventSink → agent      EventSink → agent   in-process Flow collection, no broadcasts
```

### Reachability — `adb reverse` over USB

Both devices are USB-connected, so each gets a USB-tunnelled loopback to the host:

```
adb -s 37281JEHN03065 reverse tcp:8080 tcp:8080   # Pixel 7a
adb -s FA7AJ1A06417   reverse tcp:8080 tcp:8080   # Pixel 2
```

Each device then connects to `ws://localhost:8080`. No Wi-Fi/LAN/firewall config, fully
deterministic.

## Layers (interface-first)

### 1. Scenario layer — commonMain, pure Kotlin, device-agnostic

A scenario is a sequence of steps bound to a **Role** (`A`/`B`, or
`inviter`/`invitee`, `caller`/`callee`), not to a specific device. The same scenario
definition runs on both phones; each phone is handed its role at launch.

```
Role        — logical participant ("caller", "callee", …)
Step        — an action a role performs (createAccount, invite, call, toggleRecording, …)
Expectation — "await a DomainEvent matching <predicate> within <timeout>"
Scenario    — ordered steps + expectations across roles
```

A "much less" probe is just a one-step scenario.

### 2. Observability layer — commonMain interface + testHarness impl

```
DomainEvent — normalized, serializable event (call placed, incoming call, message
              received, recording state changed, account ready, …)
EventSink   — interface events are emitted to
```

- **Production** binds a **no-op** `EventSink`.
- **testHarness** binds an impl that **collects the existing service `Flow`s**
  (`CallService._callUpdates`, `ConversationFacade` conversation/message events,
  `AccountService` account events, the `DaemonCallbacksImpl` fan-out) and translates
  them into `DomainEvent`s.

The daemon runs **in the same process** as the UI (confirmed: no `android:process` in
the manifest), so events are already available as in-process coroutine Flows. No
`BroadcastReceiver` indirection is needed for intra-device signalling.

### 3. Transport / orchestration layer

**On-device agent** (testHarness flavor): subscribes to the `EventSink`, serializes
each `DomainEvent`, ships it over the WebSocket as a `Report`, and applies inbound
`Command`s. Thin.

**Rendezvous service** (host): two responsibilities, kept conceptually distinct behind
one protocol:

1. **Ledger + correlation.** Stamps every inbound event with its *own* arrival time
   (single authoritative clock — avoids cross-device skew) and correlates related
   events by key `(runId, callerAccountId, calleeAccountId)`. Example: caller's
   "I placed a call → X" + callee's "I'm receiving a call ← Y" within a window = one
   verified `CallEstablished` fact.
2. **Rendezvous / coordination.** Relays runtime values between devices and gates
   steps. Device A creates an account → reports its Jami ID → service hands that ID to
   Device B so B knows whom to invite/call. (Pure event-reporting can't do this; the
   identity handshake *must* cross the service.)

## Wire protocol

Persistent bidirectional **WebSocket** per device (reporting is one-way, but
coordination needs server→device push, so plain HTTP POST is insufficient).

```
Report(event: DomainEvent)        device → service
Command(directive: Directive)     service → device   (proceed, here-is-peer-id, abort, …)
```

The `DomainEvent` schema is **shared between the app and the service** (common module)
so reports are type-safe end to end — one source of truth for the wire types.

## Build setup

A dedicated product **flavor**, not just a build type:

```
flavorDimension "mode"
  standard      — production
  testHarness   — adds src/testHarness/ : agent, EventSink impl, harness DI module,
                  test-only permissions; absent from the production APK
```

Combined with the `debug` build type → `testHarnessDebug`. The harness DI module swaps
the no-op `EventSink` for the real Flow-collecting one.

## Hard parts to design around

- **Async DHT is slow / non-deterministic.** DHT bootstrap, NAT traversal, swarm clone
  vary in timing. Every assertion is **event-driven with generous timeouts** (await on
  the event stream) — never fixed sleeps. The event stream *is* the synchronization
  primitive.
- **Account lifecycle / isolation.** Fresh ephemeral accounts per run = clean but slow
  (creation hits the network); a reset-able pool of pre-created accounts is the
  pragmatic middle. Decide early — it shapes the harness lifecycle.
- **Identity handshake.** A's Jami ID must reach B at runtime; design the protocol
  around "device emits a value the service relays", not only "device emits events".

## Open decisions

1. **Verdict location** — passive ledger queried by a Kotlin driver that asserts
   (leaning this; keeps scenario logic versioned with the app and the service reusable)
   vs. service actively holds expectations and emits pass/fail.
2. **Service implementation** — small **Ktor** WebSocket server in-repo, sharing the
   `DomainEvent` schema with the app via a common module, runnable as a Gradle task
   (leaning this) vs. a standalone service.
3. **Driver entry point** — JUnit/Espresso instrumented tests in `androidTest`
   (Gradle/CI integration) vs. a standalone harness app launched and watched live.
4. **Account strategy** — ephemeral-per-run vs. reset-able pool.

## Default end-to-end stack (recommendation)

Ktor WebSocket rendezvous service reached via `adb reverse`; passive ledger + Kotlin
scenario driver; on-device agent feeding it from the existing service Flows; everything
in a `testHarness` flavor; shared `DomainEvent` schema between app and service.
