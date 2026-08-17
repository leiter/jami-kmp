# End-to-End Device Testing

Status: **IMPLEMENTED (single- and two-device)** — the harness runs real scenarios against
the live daemon on USB-connected Android devices. M0–M2 plus an account-fixture pool and the
import/name-registration edge cases are **live-validated on hardware**. Two-device contact
(M3) is now **live-validated on two devices** (2026-07-01: B's DHT contact request reached A
and the contact confirmed bidirectionally). M4's messaging half (`send-message`) is
**implemented but currently FAILS on hardware** (2026-08-14) — see `doc/TODO.md` → "Bug
Findings (2026-08-14)"; calls + recording are not started.

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
`GetKnownDevices`, `RenameDevice`, `GetAccountUri`, `RegisterName`, `SendContactRequest`,
`AcceptContactRequest`, `SendMessage`, `SetProfile`, `SeedConversationMessages`. Current events:
`Pong`, `AccountsSnapshot`, `AccountAdded`, `AccountRemoved`, `RegistrationStateChanged`,
`NameRegistrationEnded`, `AccountExported`, `PasswordChanged`, `NameLookupResult`,
`KnownDevices`, `AccountUri`, `IncomingContactRequest`, `ContactAdded`, `MessageReceived`,
`ProfileUpdated`, `ErrorEvent`.

`Hello(requestedRole)` also backs **mid-scenario reconnects**: `DeviceController.startAgent(role)`
passes the role as an intent extra (`--es role <name>`) to the agent service, which threads it
into `HarnessAgent(scope, requestedRole)` → `Hello(requestedRole = ...)`. This is how a device
that was `pm clear`-wiped and relaunched (see "Conversation-pair fixtures" below) reconnects
under its original role instead of falling back to the (by-then empty) connect-order queue.
`HarnessServer.connection(role)` exposes the **live** connection map so `ScenarioContextImpl`
resolves against reconnects rather than the frozen snapshot taken at scenario start.

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

## Conversation-pair fixtures

A second, higher-level fixture tier on top of the account pool: **two identities that already
know each other**, each named + avatar-set, sharing a real swarm conversation with a seeded
message transcript — captured as one snapshot so a test can restore that exact state instead of
re-running registration + handshake + send every time.

- **Registry** — `MemoryStore` gains a parallel index at `harness-memory/fixtures/conversation-pairs/index.json`,
  blobs at `conversation-pairs/<label>/{A,B}.tar`. `ConversationPairAsset` records both
  fingerprints, both names, whether avatars were set, the real `conversationId`, and the seeded
  message count. `claimConversationPair(label?)` — by label, or the first available; `null` is
  the cue to run the producer.
- **Snapshot unit is the whole app-data directory**, not just the account archive:
  `DeviceController.snapshotAppData`/`restoreAppData` tar the harness app's entire private data
  dir via `run-as tar` (relies on toybox `tar` under `run-as`, present on the lab devices).
  This is necessary — not just convenient — because seeded messages live only in the local
  SQLDelight DB file, which the account-archive export never includes.
- **Message seeding bypasses the daemon entirely.** `SeedConversationMessages` writes rows
  straight into the device's local `interaction`/`conversation` tables (the same store
  `ConversationFacade` reads from) rather than sending real swarm traffic — deliberately routing
  around the still-open initiator-can't-send bug (`send-message`, "Bug Findings (2026-08-14)")
  so fixture-building isn't blocked by it. Each device seeds its own copy independently.
- **Avatar is real**, not faked: `SetProfile` drives `AccountService.updateProfile` (flag=1,
  base64 payload) — the same call the profile-edit UI uses — with a small fixed PNG checked in
  at `e2e-runner/src/main/resources/fixture-avatar.png`.
- **Producer**: `build-conversation-fixture` (see the suite table above) builds and captures one
  pair. Consuming (burns two names) — run it once, not per test.
- **Restore ("hard reset to a specific test case")**: `ScenarioContext.installConversationPairAsset(pair, roleA, roleB)`
  wipes each device (`pm clear`), restores the matching tar, relaunches the app + agent, and
  waits for both roles to reconnect and re-register — a consuming scenario then starts from a
  known-good two-party conversation instead of rebuilding it. Non-consuming to the fixture
  itself: the stored tars are never mutated by a restore.
- **Not yet built**: no consuming scenario uses `installConversationPairAsset` yet (retry/edit/
  delete/reaction tests would be the natural next step) — this tier currently only produces and
  restores the fixture.

## Conversation git-repo rewind/resync

A third, more surgical test primitive, distinct from the conversation-pair fixture above: it
targets **one conversation's on-disk swarm git repo**, not a whole account/app-data snapshot,
to answer "does the daemon's peer sync self-heal when one device loses recent history for a
single conversation?"

**On-disk layout** (confirmed from the daemon source,
`letsJam/jami-daemon/src/jamidht/conversationrepository.cpp`):
- Each conversation is a normal (non-bare) git repo with a working tree at
  `<app files dir>/<accountId>/conversations/<conversationId>/` — `.git/`, `admins/`,
  `members/`, `invited/`, `banned/`, `devices/`, `profile.vcf`, `votes/`. Relative to the
  harness app's private storage this is `files/<accountId>/conversations/<conversationId>`,
  the same `files/`-relative convention the rest of `DeviceController` uses.
- Own state is `refs/heads/main`; each peer device is tracked separately at
  `refs/remotes/<deviceId>/main` (`conversationrepository.cpp:3382`).
- When a peer's git socket connects, `addGitSocket()` → `pull()` → `fetch()` →
  `mergeHistory()` (`conversation.cpp:1747-1748`); `mergeHistory()` diffs against the remote
  head via `behind()` (`git_merge_bases()`, `conversationrepository.cpp:2259`) and merges in
  whatever's missing.
- Commit validation is **cryptographic** (signature + authorized-diff checks,
  `conversationrepository.cpp:2844-2878`), not positional — there is no check that a ref hasn't
  moved backward, so a rewound `refs/heads/main` isn't rejected as corruption, it just looks
  like "I'm behind."
- No persistent in-memory conversation cache: `repository()` opens a *temporary* git handle per
  call (`conversationrepository.cpp:198`, comment: *"avoid keeping the file opened"*) — an
  app restart after an on-disk rewind is sufficient to make the daemon re-read from disk.

**Primitives** (`DeviceController.kt`): `pullConversationRepo`/`pushConversationRepo` — scoped
siblings of `snapshotAppData`/`restoreAppData` for just one conversation's subtree —
`forceStopSelf` (stop the harness app itself, distinct from `stopCompetingApps` and
`clearAppData`), plus a host-side git wrapper (`LocalGit.kt`: `commitCount`, `resetHardBack`,
clamped so a rewind can never go past the repo's own root commit). `ScenarioContext.rewindConversation(role, accountId, conversationId, commitsBack)`
bundles the whole pull → local `git reset --hard` → push → relaunch round-trip; the relaunch
half (`startApp`/`startAgent(role)`/reconnect-wait) is a shared `restartRole` helper also used
by `installConversationPairAsset`.

**Scenario**: `chat-conversation-git-rewind-resync` (2 roles) — real contact handshake (B
initiates), A sends 4 real messages that B confirms as real commits, then B's local copy of the
conversation is rewound 2 commits and B is relaunched. A then sends one more "nudge" message.
Messages are sent by A, not B, deliberately: B is the contact-request *initiator*, and the
still-open sender bug means the initiator's device never resolves its own new swarm conversation
locally and can't send into it — A (the request *receiver*) is the reliable sending direction.
The verdict is **diagnostic, not binary**: whether the nudge arrives isolates "sync channel
broken" from "no backfill"; whether the 2 reverted messages also resync separates "full
self-heal" from "daemon only syncs forward." All three outcomes (full recovery / nudge-only /
nothing) are reported as distinct, informative results — directly relevant to the still-open
malformed-commit sync wedge bug (`doc/TODO.md`, found 2026-08-13).

## `-PkeepAccounts` and `-PaccountState`: preserving state past a run

Every scenario's default behavior is to tear its accounts down on finish (`ensureNoAccounts`
sweep, or equivalent) — safe, but it means each hardware run starts from zero, with no way to
leave a device sitting in a known, verified state to poke at by hand between runs (adb shell,
daemon logs, a second scenario run against the same still-registered accounts).

- **`-PkeepAccounts=true`** — opt-in, run-level flag (`RunConfig.keepAccounts`, plumbed
  `build.gradle.kts` → `Main.kt` → `ScenarioContext.runConfig`). Every scenario with a teardown
  `finally` block checks it and skips that teardown (logging that it did) when set. Default
  `false` everywhere — today's wipe-on-finish behavior is unchanged unless you ask.
- **`-PaccountState=<label>`** — names a **conversation-pair fixture** (see above) a scenario
  should restore-if-present or build-and-save-as-that-name. Exposes the existing
  `claimConversationPair`/`captureConversationPairAsset`/`installConversationPairAsset` trio as a
  run parameter instead of something only usable from inside scenario code.
- **`default-one-on-one-conversation`** (2 roles) — the orchestration built around both flags:
  establishes two accounts, a real DHT-confirmed contact, and a real swarm conversation with a
  few exchanged messages, then logs a "handoff" line (both accountIds, URIs, conversationId) and
  **deliberately has no teardown `finally` at all**. With `-PaccountState=<label>`: restores that
  exact state in one step if it already exists, or builds fresh and saves it under that name for
  next time. This is the standard starting point for chat investigation going forward — e.g. the
  A-join precondition work below starts from a run of this scenario instead of hand-assembling
  state from whichever scenario happens to leave things in the right shape.
- **`e2eListAccountStates`** — `./gradlew :e2e-runner:e2eListAccountStates` prints every named
  account state currently in the registry (label, both fingerprints/names, conversationId,
  message count) — the "menu" to pick a `-PaccountState=<label>` from, rather than a remembered
  string or a hand-opened `index.json`.
- **`capture-account`** (1 role) — the single-account companion to `-PkeepAccounts=true`:
  registers whatever account is already loaded on device[A] into the persistent fixture pool
  (`ScenarioContext.captureAsset` — export + pull, no removal). Purely additive, no
  `ensureNoAccounts`/`finally` of its own, so it's safe to run regardless of `-PkeepAccounts`.
  Turns a live on-device session left behind by an earlier `-PkeepAccounts=true` run into a
  reusable, named pool asset instead of it only existing as a one-off session a later run's
  `ensureNoAccounts` precondition would otherwise just remove. **Validated on hardware
  2026-08-16**: ran `account-reuse -PkeepAccounts=true` on the Pixel 7a (imported account
  `3bc0819760e29d83`, identity `04ff895b5c718d0f63d993f8c56f1ba2796e4199`), then a *separate*
  `capture-account -PkeepAccounts=true` run confirmed the account was still loaded
  (`AccountsSnapshot(ids=[3bc0819760e29d83])`) and captured it into the pool.

- **`capture-live-pair`** (2 roles) — the two-device companion to `capture-account`: rescues
  whatever pair is **already live** on both devices (confirmed contact, established conversation)
  into the fixture pool, without establishing anything itself. For when
  `default-one-on-one-conversation` fails *after* the handshake but *before* its own capture
  step — real state is still sitting on both devices, this is how it gets named instead of being
  silently lost to the next run's `ensureNoAccounts`. Requires `-PaccountState=<label>` and
  `-PconversationId=<id>` (no daemon query exists to discover the id automatically — the caller
  supplies what it already knows from the failed run's own timeline). Purely additive, no
  teardown of its own. **Validated on hardware 2026-08-17**: `default-one-on-one-conversation`
  hit the message-delivery race (A sent, B never received `MessageReceived` within 60s — see
  below) and left both devices live with a confirmed contact + real swarm conversation but zero
  messages; `capture-live-pair -PaccountState=contact-confirmed-no-messages-1
  -PconversationId=f176aa279e3b42a99e8cb6669fb62cd0e1c3046b` captured all three fixture kinds
  from that exact state in 4s. **This fixture's actual content is: two accounts, confirmed
  contact, one real swarm conversation, zero text messages.** Anyone restoring it should not
  expect a chat transcript.
- **Standalone conversation-repo fixtures** — a third, narrower asset type
  (`ConversationRepositoryAsset`, `harness-memory/fixtures/conversation-repos/<label>/repo.tar`),
  captured alongside the whole-tar pair whenever `default-one-on-one-conversation` runs with
  `-PaccountState=<label>` and builds fresh: `ctx.captureAsset` (portable `account.gz` per role)
  and `ctx.captureConversationRepoAsset` (that role's raw on-disk swarm git repo for the
  conversation just established, labeled `<label>-A`/`<label>-B`) both run in addition to the
  existing `captureConversationPairAsset`. Restore via `ctx.installConversationRepoAsset(asset,
  role, accountId, conversationId)` — force-stops the app, replaces just that one conversation's
  on-disk repo (`DeviceController.pullConversationRepo`/`pushConversationRepo`, the same
  primitives `rewindConversation` uses), relaunches. Point: a scenario that only needs "one
  pristine, already-synced conversation to repeatedly rewind" doesn't need a full account/app
  restore each time — it can restore just the repo onto an account that's already live.
  `e2eListAccountStates` lists these alongside the whole-tar pairs.

```
# Build + save a new named state
./gradlew :e2e-runner:e2e -Pscenario=default-one-on-one-conversation \
  -Pdevices=<a>,<b> -PaccountState=two-contacts-with-history

# List what exists
./gradlew :e2e-runner:e2eListAccountStates

# Restore it later, and leave it live for hand inspection
./gradlew :e2e-runner:e2e -Pscenario=default-one-on-one-conversation \
  -Pdevices=<a>,<b> -PaccountState=two-contacts-with-history -PkeepAccounts=true
```

## The A-join precondition race and `ConversationMemberEvent`

Every scenario that "establishes a conversation" (`establishContact` and everything built on it)
currently trusts `ContactAdded(confirmed=true)` — and, for the accepter, its own
`ConversationReady` — as proof the conversation is ready to use. Daemon-source research
(`jami-daemon/src/jamidht/conversation.cpp`/`conversationrepository.cpp`) found this is racy:

- The accepter's `join()` (`conversationrepository.cpp:3658`) is a single **local** git commit —
  it moves the accepter into `members/`, updates the in-memory cache, and fires
  `ConversationReady` (`conversation_module.cpp:825`) essentially immediately.
- The signal that means **the peer has actually seen and processed that join commit** —
  `ConversationMemberEvent` with `action=1` — only fires once the peer's daemon pulls the commit
  over the git channel and runs `announce()` on it (`conversation.cpp:407-445`). That is
  asynchronous and not guaranteed to have happened yet when `ContactAdded(confirmed=true)`/
  `ConversationReady` fire.
- Action codes (`conversation.cpp:424-433`): `0`=add(invited), `1`=join, `2`=remove, `3`=ban,
  `4`=unban.

So a scenario that sends messages right after `ContactAdded(confirmed=true)` may be racing ahead
of the peer's own view of the conversation. `ConversationMemberEvent` is now surfaced end-to-end
(`ConversationFacade.ConversationEvent.MemberEvent` → `Wire.kt`'s `ConversationMemberEvent`
`DomainEvent` → `HarnessAgent.observeMembership`), and `ScenarioSupport.awaitMemberJoined(ctx,
role, accountId, conversationId, memberUri)` awaits it — call it with `role` = the *peer's* role
to confirm the peer's daemon has actually caught up, not the joiner's own device.

Not yet wired into `establishContact` or any scenario's flow — this only makes the signal and
helper available. Whether/where to hard-require it (per the `enforce*` methodology above) is the
actual A-join precondition investigation, still open.

**Update, 2026-08-17**: the leading suspect for a real message-delivery failure found this same
day (`send-reply-roundtrip` against a settled fixture, see below) turned out to be a *different*
race entirely — `AccountService.accountEvents` dropping `RegistrationStateChanged` for
already-provisioned accounts (fixed, see `doc/TODO.md`'s "Bug Fixes (2026-08-17)"), not the
A-join race this section describes. The A-join race is still real and still undemonstrated to
cause an actual failure — `awaitMemberJoined` remains available for whoever picks that
investigation back up.

## Shared contact-handshake helper

The real, DHT-verified contact-request → accept → confirm sequence was identical, copy-pasted
code across four scenarios (`two-device-contact`, `send-message`, `build-conversation-fixture`,
`chat-conversation-git-rewind-resync`) before being extracted into `ScenarioSupport.kt`:
`establishContact(ctx, initiatorRole, initiatorId, initiatorUri, accepterRole, accepterId,
accepterUri)` runs the whole thing and returns the derived swarm `conversationId`.
`two-device-contact` calls the two halves separately instead
(`sendContactRequestAndAwaitIncoming` / `acceptAndConfirmContact`) because it treats "the request
arrived" as a hard requirement but "both sides confirmed" as best-effort — a distinction a single
bundled call would lose.

## Competing-app precondition

The lab devices may also have the standard (non-harness) jami-kmp build (`net.jami.android`)
or the jami-android-client reference app (`cx.ring`) installed from manual testing — both run a
real daemon against the real DHT. Every runner invocation force-stops both
(`DeviceController.stopCompetingApps`, called once per controller at the very start of `main`,
before `adbReverse`/`startApp`) so a leftover foreground daemon session never contends with the
harness's own for sockets/wake locks or produces confusing crosstalk. `am force-stop` on an
app that isn't installed/running is a harmless no-op, so this runs unconditionally regardless of
what's on the device.

## Precondition methodology: `ensure*` vs. `enforce*`

Two distinct precondition patterns, named so a helper's intent is visible at the call site
instead of buried in its body:

- **`ensure*` — act to establish state.** Checks whether the required on-device state is
  already present and, if not, **takes real action** to bring it about, then re-checks. It is
  idempotent (safe to call whether or not the state already holds) and its job is to *act*, not
  merely assert. Existing example: `ensureNoAccounts` (below) — snapshot → remove any loaded
  account → re-snapshot to confirm empty. The conversation-repo primitives fit the same shape:
  "ensure this device has conversation X on disk" means placing the (possibly rewound) git repo
  under `files/<accountId>/conversations/<conversationId>/` — via `pushConversationRepo` or a
  fixture restore — not just checking for its absence and failing.
- **`enforce*` — assert and fail, no remediation.** Checks a precondition and throws/fails the
  scenario immediately if it's unmet. No healing is attempted — use this where remediation isn't
  meaningful, or where silently working around the gap would hide the exact bug the scenario
  exists to catch (e.g. asserting a device actually resolved the swarm conversation locally
  before trusting a downstream `await`, rather than quietly retrying past a real resolution
  failure).

Picking the wrong one is a real failure mode: an `ensure` that only asserts (never acts) forces
every scenario to re-derive state by hand; an `enforce` that quietly heals hides the very
precondition failures a diagnostic scenario is meant to surface.

### Self-healing scenarios: observe the log, heal, retry once

A scenario step can watch its own reported event/log stream to identify *which specific*
precondition is unmet (e.g. "B never received `ConversationReady` for this conversationId"),
log that gap explicitly on the timeline, attempt one bounded heal action via the matching
`ensure*` (resend the invite, re-accept, re-poll membership), and retry the original step once
before failing for real. This turns a brittle hard-timeout into a diagnostic trail: the log
shows exactly what was missing and whether the heal fixed it, instead of us reverse-engineering
a gap from timeline timestamps after the fact — which is how the initiator-send bug and the
current A-join-completion suspicion (`chat-conversation-git-rewind-resync`, 2026-08-16) were
both found, the hard way.

Scope this **narrowly, per precondition** — a generic "auto-heal everything" wrapper is an
anti-goal here: if a scenario heals too readily, it stops being a useful regression signal for
the underlying bug it was built to catch. Add a heal step only for a precondition we've already
identified and named, not speculatively.

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
| `capture-account` | 1 | registers whatever account is already loaded on device[A] into the fixture pool (export + pull, no removal) — the single-account companion to `-PkeepAccounts=true`, see "`-PkeepAccounts` and `-PaccountState`". Purely additive, no teardown of its own. Validated on hardware 2026-08-16. |
| `capture-live-pair` | 2 | rescues an already-live pair (confirmed contact + established conversation) into the fixture pool, e.g. after `default-one-on-one-conversation` fails past the handshake but before its own capture step. Requires `-PaccountState=<label>` and `-PconversationId=<id>`. Purely additive, no teardown of its own. Validated on hardware 2026-08-17. |
| `send-reply-roundtrip` | 2 | restores a named fixture (`-PaccountState=<label>`, must already exist) and tests real bidirectional messaging (A→B then B→A) against that **already-settled** conversation — deliberately isolates messaging from handshake/join timing, unlike `send-message`/`default-one-on-one-conversation` which both send immediately after their own fresh handshake. Non-consuming, `-PkeepAccounts=true` skippable teardown. **Validated on hardware 2026-08-17**: found and helped root-cause the `AccountService.accountEvents` registration-race bug (see `doc/TODO.md`), then passed cleanly (both directions) once that was fixed. |
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
| `device-rename` | 1 | `renameDevice` is read back from the daemon's known-device registry under the same device id, then restored to the baseline name (reuses a fixture, non-consuming) |
| `seed-pool` | 1 | status-aware pool top-up, zero name burns |
| `two-device-contact` | 2 | B's contact request reaches A over the real DHT, accept + confirm (M3, **validated on 2 devices 2026-07-01**) — pool-backed: two *distinct* fixtures, `ensureNoAccounts` on both roles, non-consuming, zero name burns |
| `send-message` | 2 | **implemented 2026-08-14, currently FAILS** — bidirectional message exchange over an established swarm conversation. Contact handshake (steps 1-4, shared with `two-device-contact`) passes; the message-send step consistently fails because B's (the request-sender's) device never resolves the swarm conversation locally, even though its daemon is confirmed to be receiving real swarm traffic for it. See `doc/TODO.md` → "Bug Findings (2026-08-14)" for the full writeup — likely the actual root cause of "message sending broken" reports. Pool-backed, non-consuming, zero name burns, same as `two-device-contact`. |
| `build-conversation-fixture` | 2 | **implemented 2026-08-16** — producer for the **conversation-pair fixture** (see below): registers a name + avatar on two pool identities, runs the proven contact handshake for a real swarm `conversationId`, seeds a fixed transcript directly into each device's local history DB (bypassing the broken send path above), then captures both devices' full app data as one fixture. Consuming (burns two names), but only needs to run once — see "Conversation-pair fixtures". |
| `chat-conversation-git-rewind-resync` | 2 | **implemented 2026-08-16** — tests the daemon's peer-sync self-heal: real handshake (B initiates), A sends 4 real messages (the reliable direction — B, the initiator, hits the sender bug above), B's on-disk copy of that one conversation is rewound 2 commits and relaunched, then a nudge message from A probes whether B recovers. Diagnostic verdict (full self-heal / nudge-only / broken), not just pass/fail — see "Conversation git-repo rewind/resync". Pool-backed, unnamed identities, non-consuming. |
| `default-one-on-one-conversation` | 2 | **implemented 2026-08-16** — orchestration, not a diagnostic: establishes the standard two-account/real-contact/real-conversation baseline and, uniquely, does **not** tear it down — see "`-PkeepAccounts` and `-PaccountState`". Restores a named state in one step if `-PaccountState=<label>` matches an existing fixture, else builds fresh and (if labeled) saves it. |

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
| device linking & management | `addDevice` / `confirmAddDevice` / `provideAccountAuthentication`, `revokeDevice` | link a new device to an existing account over the DHT, revoke | Still the **biggest untested account area**; the link flow is inherently two-device (DHT-async) → fold into the M3 push. `revokeDevice` only becomes meaningful once a second device is linked, so it rides along. The single-device half (`renameDevice` + `getKnownRingDevices`) is ✅ **DONE (2026-07-24)** as `device-rename` — see below. |
| ~~`device-rename`~~ ✅ **DONE (2026-07-24)** | `renameDevice`, `getKnownRingDevices` | device naming, read back through the daemon | Implemented as `device-rename` (see the suite above). `renameDevice` returns nothing, so the proof is a **read-back** of the daemon's own `deviceId → deviceName` registry: baseline → rename to a stamped name → the new name appears under the *same* device id with the key set unchanged → rename back to the baseline name and the registry matches the baseline exactly (a live read-write channel, not a one-shot). The device count is deliberately **not** asserted — repeated imports of one identity can leave earlier device ids known to the account. `getKnownRingDevices` is a plain synchronous getter and `onKnownDevicesChanged` is not among the agent's Flows, so the read-back is polled against a deadline rather than awaited (same shape as the `GetAccountUri` retry). Gate: wait for `REGISTERED` before writing account details. Non-consuming. Added `GetKnownDevices` + `RenameDevice` directives and a `KnownDevices` event. Validated on Pixel 7a, first run (~4.4 s). |
| multi-account coexistence | `setCurrentAccount`, `setAccountOrder` | two Jami accounts on one device, independent registration + switching | Reachable, single-device, but requires relaxing the `ensureNoAccounts` precondition for just this scenario. Medium value. |

### Deliberately out of scope

- **`migrateAccount`** — needs a legacy-format archive we can't easily produce.
- **`updateProfile`** — weak observability; belongs with the deferred contact/profile scenarios.
- **SIP account creation** (`createSipAccount`) — reachable, but SIP has no Jami identity / DHT; a separate track, not "account handling" here.

Suggested order: ~~`change-password`~~ → ~~`account-enable-disable`~~ → ~~`name-lookup`~~ →
~~`device-rename`~~ — **every single-device candidate is now done.** What remains in account
handling needs two devices or a relaxed precondition: device linking (`addDevice` /
`confirmAddDevice` / `provideAccountAuthentication`) with `revokeDevice` riding along, and
multi-account coexistence (which must opt out of `ensureNoAccounts`).

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
- **A freshly-wiped app has a cold DHT.** The first `two-device-contact` run after uninstalling
  and reinstalling the harness on the Android 11 device blew the 120 s core-proof timeout; the
  next run on the same devices passed in ~11 s and stayed stable. The daemon has no bootstrap
  cache on a clean install, so the first peer-to-peer round-trip is far slower than steady state
   — do not read a single post-wipe failure as a regression.
- **`setAccountDetails` bounces the registration.** Renaming the device (which rewrites
  `ACCOUNT_DEVICE_NAME` through `setAccountDetails`) took the account `REGISTERED` →
  `UNREGISTERED` → `TRYING` on every write, observed in the `device-rename` run. So any
  details write costs a re-registration round-trip: a scenario that writes details and then
  awaits a registration state must expect the bounce rather than read the pre-write state.
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
- **M4** 🟡 messaging half implemented but **failing on hardware** (`send-message`,
  2026-08-14): contact handshake reuses M3's proven path; the message-send step surfaced a
  reproducible gap where the request-sender's device never locally resolves the swarm
  conversation it should now be part of, so it can never send into it — see `doc/TODO.md` →
  "Bug Findings (2026-08-14)" for the full investigation. Call + recording not started.

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

- ~~**Harden `two-device-contact` onto the pool**~~ ✅ **DONE (2026-07-24)** — the scenario now
  matches the single-device pattern: `ensureNoAccounts` on **both** roles as precondition and
  teardown sweep, and each role gets a *distinct* pool fixture via the new
  `MemoryStore.claimDistinct(2)` (the plain `claim` always returns the first match, so two calls
  would have installed one identity on both devices — two devices of the *same* peer, which
  cannot exchange a contact request). Roles the pool cannot cover fall back to
  `CreateBareAccount`, not `CreateJamiAccount` — the old path registered `harness_a_<stamp>` /
  `harness_b_<stamp>` on the name server, **burning two usernames per run**; runs are now
  name-burn-free. The identity relay doubles as an import identity check: a pool-installed role's
  resolved URI must equal its asset fingerprint. Non-consuming — the contact exists only in the
  on-device copies, which are removed; host archives are never rewritten. Compiles clean and the
  pool-claim path is regression-checked via `account-enable-disable` (PASS, ~4.4 s).
  **Re-validated on two devices 2026-07-24** (Pixel 7a as A, Pixel 2 / Android 11 as B): four
  consecutive passes, full run ~11–15 s, both fixtures installed distinctly, both roles swept
  clean, no name burned.
- **M4** — messaging implemented but failing (see above); calls + recording across two
  devices not started.
- ~~**Contact / data-state fixtures**~~ ✅ **DONE (2026-08-16)** — added the **conversation-pair
  fixture** tier (see "Conversation-pair fixtures" above): two named + avatar-set identities
  sharing a real swarm conversation with seeded message history, captured as a full app-data
  snapshot and restorable on demand. No consuming scenario built on top of it yet.
