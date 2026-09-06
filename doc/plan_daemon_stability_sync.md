# jami-kmp ↔ jami-android-client: daemon utilisation, stability & sync — comparison and improvement plan

**Date:** 2026-09-06
**Scope:** how each client drives `jami-daemon` (libjami) for account load, conversation
lifecycle, swarm data sync, network-change handling, and callback delivery. Audio/video excluded.
**Reference tree:** `jami-client-android/jami-android/libjamiclient/src/main/kotlin/net/jami/services/`
**Target tree:** `jami-kmp/shared/src/commonMain/kotlin/net/jami/services/`

---

## 1. Executive summary

The jami-kmp port is a faithful *structural* translation of libjamiclient (RxJava → Flow/coroutines),
and the domain models (`Account`, `Conversation`, `getByUri`/`getByContact`) match. Where it diverges
— and where the observed instability comes from — is in the **disciplines around the daemon**, not
the daemon calls themselves:

| Area | jami-android-client | jami-kmp | Impact |
|---|---|---|---|
| Network-change → daemon | calls `JamiService.connectivityChanged()` on every change | **no such call exists** anywhere; only toggles `setAccountsActive` | daemon never re-bootstraps DHT/ICE/swarm on Wi-Fi↔cellular; stale connections, "messages stop arriving" |
| Account conversation load | `loadAccount` gated on leaving `INITIALIZING`, runs **once** per account via `firstElement()` | `loadSmartlist` fired from 4 loose collectors + synchronously on the send path, no gate, no single-flight | reads `getConversations()` before repos are on disk → F1; concurrent re-entrant runs race `conversationStarted()` |
| Conversation callback ordering | per-`(account,conversation)` serialized queue with generation invalidation (`ConversationCallbackDispatcher`) | **one global channel** for all conversations; ~15 other callbacks use bare `scope.launch{}` (unordered) | `onContactAdded` races `onConversationReady`; member/message events can be processed against a half-built conversation |
| Registration state transition | on `INITIALIZING→ready` re-hydrates details/credentials/devices/volatile | only writes the 3 volatile registration fields | account model can carry stale details/devices after first registration |
| Conversation requests | loaded into `account.addRequest()` inside `loadAccount` | loaded ad-hoc inside two ViewModels | requests missing from the shared model; races between consumers |
| Smartlist priming | `loadMore(conversation, 8)` during load | not primed | empty previews, `lastEvent` only from `created` |
| Background / proxy accounts | proxy-aware deactivation, `explicitlyDeactivatedAccounts` markers | blanket `setAccountActive(all, isConnected)` on every connectivity flip | churns DHT connections; can deactivate an account mid-sync |
| Sync visibility | none, but not needed (delivery is reliable) | `SyncState` tracks `loadSmartlist` **duration**, not daemon swarm health | no signal for `Bootstrap with 0 device(s)` / `no sync connection` |

Two things jami-kmp does **better** and should keep: the `ChatViewModel` optimistic bubble + 20 s
FAILED watchdog + `retryMessage` (`ChatViewModel.kt:396-430`), and the `info["syncing"]` →
`Mode.Syncing` port already landed in `loadSmartlist` (`ConversationFacade.kt:636-648`).

---

## 2. Detailed findings

### 2.1 Network changes are never signalled to the daemon  — **highest-impact gap**

Reference: `HardwareService.kt:119-122`
```kotlin
fun connectivityChanged(isConnected: Boolean) {
    ...
    mExecutor.execute { JamiService.connectivityChanged() }   // tells libjami to re-evaluate transports
}
```
`JamiService.connectivityChanged()` makes the daemon drop and rebuild DHT nodes, ICE, TURN
allocations and swarm sync sockets against the new interface.

jami-kmp: `DaemonBridge` (expect at `DaemonBridge.kt`, Android impl at `DaemonBridge.android.kt`)
has **no `connectivityChanged` method at all**. `HardwareService.android.kt:534` only does
`_connectivityState.value = isConnected`. The only reactions are:
- `AccountService.kt:143-145` → `setAccountsActive(isConnected)` (blunt),
- `ConversationFacade.kt:127-133` → `refreshAllConversations()` (app-side re-read, does nothing for
  daemon transports).

Consequence: after a network switch the daemon keeps trying dead sockets until its own slow
internal timers fire. This is consistent with the stabilization-findings symptom "DHT connects,
then `sync_module: no sync connection` repeats" and the generally flaky two-device runs.

### 2.2 `loadSmartlist` is ungated and not single-flight

Reference `refreshAccountsCacheFromDaemon` (`AccountService.kt:260-280`):
```kotlin
account.registrationStateObservable
    .filter { it != UNLOADED && it != INITIALIZING }
    .firstElement()
    .observeOn(scheduler)
    .flatMapCompletable { Completable.fromAction { loadAccount(account) } }
    .subscribe(account.loadedSubject)
```
→ `loadAccount` runs exactly once, only after the daemon has left `INITIALIZING` (i.e. archive
decrypted, conversation git repos enumerated).

jami-kmp fires `loadSmartlist` from **four** places (`ConversationFacade.kt`):
- `init` `currentAccount.collect` (`:104`) — immediately, daemon may still be `INITIALIZING`
- `AccountEvent.AccountsChanged` (`:147`) — comment claims this means "repos loaded from disk"; it
  does not (it is the daemon `accountsChanged` list/config signal)
- `AccountEvent.RegistrationStateChanged && isRegistered` (`:160`) — `REGISTERED` = DHT reachable,
  not repos-loaded
- `getAccountWithSmartlist()` on the send path (`:215`) — synchronous, one shot, no retry

None take a per-account lock, so two triggers can run `loadSmartlist` concurrently and both call
`account.conversationStarted()` on the same ids. F1 in `doc/stabilization-findings-2026-09-04.md`
is the ungated-early-run case.

### 2.3 One global conversation channel; most callbacks unordered

Reference `ConversationCallbackDispatcher.kt` keys tasks by `(accountId, conversationId)`,
`.groupBy(Task::key).flatMapCompletable { … concatMapCompletable … }` — per-conversation FIFO,
cross-conversation parallel — plus a `generation` counter so a `conversationRemoved`
(`dispatchAndClose`) invalidates in-flight tasks for the old instance before a re-add.

jami-kmp `DaemonCallbacksImpl.kt`:
- `conversationTasks = Channel<ConversationTask>(UNLIMITED)` — **all** conversations share one FIFO;
  a slow `SwarmLoaded` for conv A head-of-line-blocks a `MessageReceived` for conv B (`:69-105`).
- Only 8 conversation event types + 3 account types are queued. Everything else —
  `onAccountDetailsChanged`, `onVolatileAccountDetailsChanged`, `onNewBuddyNotification`,
  `onContactAdded`, `onContactRemoved`, `onIncomingTrustRequest`, `onProfileReceived`,
  `onConversationProfileUpdated`, `onConversationPreferencesUpdated`, `onReactionAdded/Removed`,
  `onActiveCallsChanged`, `onComposingStatusChanged`, name-service callbacks — uses bare
  `scope.launch { … }` (`:129-319`), i.e. no ordering relative to the queued events or each other.
- `onContactAdded` (`:277-279`, `scope.launch`) can therefore run before **or** after
  `onConversationReady` (`:213-215`, channel). Both now create+index the conversation
  (`AccountService.onContactAdded` `:1191-1213`, `ConversationFacade.onConversationReady`
  `:989-1047`), so it is idempotent, but member/message events landing between the two halves of a
  build are processed against a partial `Conversation`.
- No borrowed-value barrier equivalent to reference `snapshotConversationCallback`
  (`DaemonService.kt:434`). jami-kmp converts SWIG values inline inside each native callback
  (`DaemonBridge.android.kt:949+`), which is fine, but two native callbacks can interleave during
  conversion.

### 2.4 `onMessageReceived` / `onConversationMemberEvent` / `onSwarmLoaded` no-op on unknown id

`ConversationFacade.kt:1120`, `:1150`, `:1195` all `?: return` when `account.getSwarm(convId)` is
null. Reference does the same (`AccountService.kt:530`, `:1564`, `:1514`). The reference is safe
because gating + a reliable `conversationReady` guarantee the swarm exists by then; jami-kmp has
neither guarantee, so real swarm traffic for a not-yet-indexed conversation is silently discarded
and nothing rebuilds it.

### 2.5 Registration transition does not re-hydrate the account

Reference `registrationStateChanged` (`AccountService.kt:1164-1170`):
```kotlin
if (oldState == INITIALIZING && state != INITIALIZING) {
    account.setDetails(JamiService.getAccountDetails(id).toNative())
    account.setCredentials(JamiService.getCredentials(id).toNative())
    account.devices = JamiService.getKnownRingDevices(id).toNative()
    account.setVolatileDetails(JamiService.getVolatileAccountDetails(id).toNative())
}
```
jami-kmp `onRegistrationStateChanged` (`AccountService.kt:1054-1075`) only writes
`ACCOUNT_REGISTRATION_STATUS/STATE_CODE/STATE_DESC` into `volatileDetails`. After the very first
registration of an imported/created account the model can hold the pre-registration details,
credentials and device list.

### 2.6 Smaller parity gaps

- **Conversation requests**: reference loads them in `loadAccount` (`AccountService.kt:346-360`,
  `account.addRequest(TrustRequest(...))`). jami-kmp loads them inside `ConversationsViewModel.kt:285`
  and `PendingRequestsViewModel.kt:99` only — the `Account` model itself has no request set for
  other consumers, and the two ViewModels can race.
- **History priming**: reference calls `loadMore(conversation, 8)` during load
  (`AccountService.kt:341`). jami-kmp `loadSmartlist` never primes history, so smartlist previews
  are blank until a conversation is opened and `lastEvent` for a settled conv is missing.
- **`setAccountsActive`**: reference is proxy-aware and has `explicitlyDeactivatedAccounts` /
  `backgroundDeactivatedAccounts` markers so a queued background deactivation can't undo an
  explicit reactivation (`AccountService.kt:596-640`). jami-kmp `setAccountsActive`
  (`AccountService.kt:399-408`) toggles every account on every connectivity flip.
- **`SyncState`**: `ConversationFacade.kt:88` + `loadSmartlist` set
  `Syncing/Complete/Error` from wall-clock duration of the app-side load loop, not from daemon
  swarm state (`Refreshing tracked members: n/m active`, `no sync connection`). It cannot report a
  conversation that is "loaded" locally but has 0 connected peer devices.

---

## 3. Improvement plan

Ordered by stability impact. Each phase is independently shippable.

### Execution status

| Phase | Status |
|---|---|
| Phase 1 — network-change signal | ✅ landed (`bcf74c3`) — Android callback + daemon forward + Phase 1b markers |
| Phase 2 — gate & single-flight account load | ✅ core landed — load gate, per-account single-flight `Mutex`, `INITIALIZING→ready` re-hydration, `loadMore(conv, 8)` preview priming. **Deferred:** moving conversation-request loading into `loadSmartlist` / repointing `ConversationsViewModel` + `PendingRequestsViewModel` at the model — `Account` has no request store yet and the two ViewModels currently work; low stated impact, tracked as a follow-up. |
| Phase 3 — per-conversation ordered callbacks | ✅ landed — keyed `(accountId, conversationId)` FIFO channels + per-key consumer + `Removed` teardown/generation in `DaemonCallbacksImpl`. **Deferred:** the `DaemonBridge.android.kt` SWIG-conversion barrier (defense-in-depth; libjami already serialises callback delivery and a lock across full vector conversion would re-serialise what the keying just parallelised). |
| Phase 4 — self-heal + resilient send | 🟡 2 of 3 landed — `ConversationFacade.ensureSwarm()` self-heal on all four unknown-id handlers; `ChatViewModel` `WAITING_TO_SYNC` hold + `flushPendingSyncSends()` auto-flush on `ConversationReady` / peer-join. **Deferred:** the durable outbox (below) — a schema migration on a shipping DB that needs on-device verification and 5-platform DI wiring; low marginal value until Phases 1-4 are hardware-verified. |
| Phase 5 — real sync observability | ⏳ pending |

**Phase 4 durable-outbox follow-up (scoped):**
- `shared/src/commonMain/sqldelight/net/jami/database/Outbox.sq`: `CREATE TABLE outbox_message(id INTEGER PK AUTOINCREMENT, account_id TEXT, conversation_id TEXT, body TEXT, reply_to TEXT, created_at INTEGER)` + `insert` / `selectByConversation` / `selectAll` / `deleteById` / `deleteByBody`.
- `shared/src/commonMain/sqldelight/net/jami/database/1.sqm`: same `CREATE TABLE` (SQLDelight derives `Schema.version = 2`; `verifyMigrations=true` will check fresh-schema == empty+`1.sqm`). Regenerate `2.db` via `./gradlew generateCommonMainJamiDatabaseSchema`. Bump `DatabaseSchema.VERSION` to 2. The 5 `DatabaseDriverFactory` actuals already pass `JamiDatabase.Schema`, so `AndroidSqliteDriver` / `NativeSqliteDriver` / `JdbcSqliteDriver` run the migration automatically — no per-platform code change expected, but confirm each.
- New `services/SendQueueService.kt`; register in all 5 `PlatformModule.*.kt` alongside `SqlDelightHistoryService` (they own the per-platform `JamiDatabase`).
- Write on a send with no daemon echo (hook where `ChatViewModel.dispatchSend` arms the watchdog, or in `ConversationFacade.sendTextMessage`); replay on `ConversationEvent.ConversationReady` and once at app start (an `AppViewModel`/init seam); delete the row on the matching `onMessageReceived` echo (match on account+conv+body, same heuristic as `ChatViewModel.appendMessage` reconciliation at `:829`).

### Phase 1 — Signal the daemon on every network change

- Add `fun connectivityChanged()` to the `DaemonBridge` expect class.
  - Android impl → `JamiService.connectivityChanged()` on the daemon executor.
  - iOS/macOS impl → the equivalent C-interop symbol; desktop/web → no-op.
- In `HardwareService` (each platform actual), when the OS reports a network change, call
  `daemonBridge.connectivityChanged()` **in addition to** updating `_connectivityState`.
- Keep `_connectivityState` for UI, but change `AccountService.kt:143-145` so a mere connectivity
  flip no longer blanket-calls `setAccountsActive`; leave account activation to real
  background/foreground transitions (Phase 1b).
- **Phase 1b:** make `setAccountsActive` proxy-aware — keep `isDhtProxyEnabled` accounts active
  when going inactive; add the `explicitlyDeactivatedAccounts` marker set so a background
  deactivation cannot override an explicit user reactivation. Port from
  reference `AccountService.kt:596-640`.

*Critical files:* `shared/src/commonMain/kotlin/net/jami/services/DaemonBridge.kt`,
`shared/src/androidMain/.../DaemonBridge.android.kt`, `shared/src/iosMain/.../DaemonBridge.*.kt`,
`shared/src/*/…/HardwareService.*.kt`, `AccountService.kt`.

### Phase 2 — Gate and single-flight account load

- Introduce an "account loaded" gate mirroring the reference: after `loadAccounts`, per Jami
  account, wait for the first `RegistrationStateChanged` whose state ∉ {`UNLOADED`,`INITIALIZING`}
  before the first `loadSmartlist`. Expose it as `Account.loaded: Deferred<Unit>` (or a
  `StateFlow<Boolean>`), completed once.
- Add a per-account `Mutex` in `ConversationFacade`; every `loadSmartlist(account)` acquires it, so
  the four triggers coalesce instead of racing. Collapse the redundant collectors to: (a) one
  "account became loaded" trigger, (b) one `AccountsChanged` trigger, (c) keep the send-path call
  but have it `await` the gate rather than reading possibly-empty `getConversations()`.
- In `onRegistrationStateChanged`, on `INITIALIZING → ready`, re-read and apply
  `getAccountDetails` / `getCredentials` / `getKnownRingDevices` / `getVolatileAccountDetails`
  (port reference `AccountService.kt:1164-1170`).
- Move conversation-request loading into `loadSmartlist`: populate `account.addRequest(...)` from
  `daemonBridge.getConversationRequests(accountId)`; point `ConversationsViewModel` /
  `PendingRequestsViewModel` at the model instead of calling the bridge themselves.
- Call `accountService.loadMore(conversation, 8)` for each conversation during `loadSmartlist` to
  prime previews and `lastEvent`.

*Critical files:* `ConversationFacade.kt` (`init`, `loadSmartlist`, `getAccountWithSmartlist`),
`AccountService.kt` (`onRegistrationStateChanged`, load gate), `model/Account.kt`,
`ui/viewmodel/ConversationsViewModel.kt`, `ui/viewmodel/PendingRequestsViewModel.kt`.

### Phase 3 — Per-conversation ordered callback delivery

- Replace the single `conversationTasks` channel in `DaemonCallbacksImpl` with a keyed dispatcher:
  a `Map<ConversationKey, SendChannel<ConversationTask>>` backed by one actor coroutine per key
  (or a `MutableSharedFlow` + `groupBy`-style fan-out), giving per-conversation FIFO and
  cross-conversation concurrency. Port the `generation` invalidation from
  `ConversationCallbackDispatcher.kt` so `Removed` closes the key and a later re-add starts a fresh
  generation.
- Route the currently-`scope.launch` daemon callbacks that mutate conversation or account state
  through the ordered path — at minimum `onContactAdded`, `onContactRemoved`,
  `onIncomingTrustRequest`, `onConversationProfileUpdated`, `onConversationPreferencesUpdated`,
  `onReactionAdded/Removed`, `onActiveCallsChanged`, `onAccountDetailsChanged`,
  `onVolatileAccountDetailsChanged`.
- Add a single-thread confinement (or `Mutex`) around the native→Kotlin conversion+enqueue step,
  mirroring reference `snapshotConversationCallback` (`DaemonService.kt:434`).

*Critical files:* `shared/src/commonMain/kotlin/net/jami/services/DaemonCallbacksImpl.kt`
(new keyed dispatcher — model on reference `ConversationCallbackDispatcher.kt`),
`shared/src/androidMain/.../DaemonBridge.android.kt` (conversion barrier).

### Phase 4 — Self-heal + resilient send

- In `ConversationFacade.onMessageReceived` / `onConversationMemberEvent` / `onSwarmLoaded` /
  `onDataTransferEvent`: when `account.getSwarm(convId) == null`, build the conversation in place
  (`newSwarm` + `getConversationMembers` + `conversationStarted` + emit `ConversationReady`)
  instead of `?: return`. Keeps real daemon traffic from being dropped when Phases 2–3 miss an
  edge.
- `ChatViewModel`: when the target conversation is `Mode.Syncing`, render the optimistic bubble as
  "waiting to sync" rather than "sending", and auto-invoke `retryMessage` on the next
  `ConversationEvent.ConversationReady` / first `ConversationMemberEvent(action=1)` for that
  conversation, instead of only after the 20 s `SEND_TIMEOUT_MS` fail.
- Add a minimal durable outbox: persist `(accountId, convId, text, replyTo, ts)` for sends with no
  daemon echo; replay on `ConversationReady` and on app start; drop on echo. SQLDelight table +
  a `SendQueueService`.

*Critical files:* `ConversationFacade.kt` (event handlers, `sendTextMessage`),
`ui/viewmodel/ChatViewModel.kt`, `services/SqlDelightHistoryService.kt` (+ new `.sq` schema),
new `services/SendQueueService.kt`.

### Phase 5 — Real sync observability

- Derive `SyncState` per conversation from the daemon: expose active-member / active-device counts
  (`getConversationMembers` roles + a periodic refresh, or a daemon signal if one exists) and set
  `SyncState.Bootstrapping` while a `Mode.Syncing` conversation still reports 0 active peer
  devices, `Complete` once ≥1.
- Extend `DebugLogsScreen` with a per-conversation panel: mode, `requestMode`, member/device active
  counts, last commit id, whether a sync connection is up — so the `Bootstrap with 0 device(s)`
  state is visible during testing instead of only in `adb logcat`.

*Critical files:* `ConversationFacade.kt` (`SyncState`), `model/Conversation.kt`,
`ui/screens/DebugLogsScreen.kt`, `ui/viewmodel/DebugLogsViewModel.kt`.

---

## 4. Out of scope

- Audio/video/conference daemon paths.
- iOS remote-video sink (tracked in `ios_implementation_gap.md`).
- Desktop/Web `DaemonBridge` (no-op stubs by design).
- The `default-pair-with-messages-1` mislabelled fixture and other e2e-harness-only items — see
  `doc/stabilization-findings-2026-09-04.md` §"Fix progress".
