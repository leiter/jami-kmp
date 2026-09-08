# Stabilization Findings — 2026-09-04 (unattended session)

**Goal:** surface bugs / inconsistencies to stabilize jami-kmp. Functionality first (not audio/video).
**Method:** e2e-runner harness scenarios (repeated, order-varied) + targeted UI exploration on two devices.
**Devices:** A = Pixel 7a `37281JEHN03065` (Android ?) · B = Pixel 2 `FA7AJ1A06417` (Android 11)
**Output mode:** document only — no code changes.

---

## Fix progress — 2026-09-05

Work started on F1/F4. Key correction to the earlier "Revised fix direction": **the reference
client does NOT queue-and-flush.** `libjamiclient ConversationFacade` (`:131`) calls
`sendConversationMessage` unconditionally, including for `Mode.Syncing` conversations, and relies
on the daemon to deliver the commit once the swarm bootstraps. So F1/F4 is **not** an app-code
queue mechanism — it is one correctness port plus test-infra fixes.

| # | Change | Status |
|---|---|---|
| 1 | `ConversationFacade.loadSmartlist`: read `info["syncing"]` → `Mode.Syncing` + `requestMode` + `lastEvent` from `created` (faithful port of `libjamiclient AccountService.loadAccount`). Also removed the temp `[F1DIAG]` logs. | ✅ done, compiles (`:shared:compileDebugKotlinAndroid`) |
| 2 | `findConversation` throws `IllegalStateException` where the reference lazily returns a `getByKey` stub — but the diagnostic showed this throw is only hit by the **mislabeled** `default-pair-with-messages-1` fixture (send-to-self), where throwing is arguably correct. **No app-code change made**; folded into #4. | superseded |
| 3 | `ChatConversationGitRewindResyncScenario`: added the `awaitMemberJoined(A, …, bUri)` gate after `establishContact`, matching `send-message` / `default-one-on-one`. Lets the scenario reach its rewind/resync assertions instead of dying on the F4 race. | ✅ done, compiles (`:e2e-runner:compileKotlin`) |
| 4 | Re-capture `default-pair-with-messages-1` (mislabeled) **and** make pair-capture include the conversation git repo so restore doesn't force a DHT re-clone (the residual `no sync connection` in the `e2e-mellow-salmon` run traces to the missing repo, not app code). | ⏳ pending — needs both devices; only Pixel 7a currently connected |

### Verification attempt — 2026-09-05, both devices reconnected — INCONCLUSIVE

Clean uninstall/reinstall of the harness (Part 1 + Part 3) on both devices, then:

| scenario | result | notes |
|---|---|---|
| `ping` | ✅ PASS | basic DHT reachability fine |
| `send-message` (was ✅✅✅ on 09-04) | **~1/3 pass** across 7 runs today | failures = device A never receives `IncomingContactRequest`, or the message never arrives after "safe to send". All timeout-based, not assertion failures. |
| `send-message`, Part 1 **stashed** | 1/1 pass | sample too small to be a real baseline given the flakiness |
| `two-device-contact` | run 1 ✅, runs 2–4 ❌ in ~9s | **harness cleanup leak**: a timed-out run leaves its account on device A; the next run's precondition (`resolved URI … does not match installed asset`) then fails instantly. `adb shell pm clear net.jami.android.harness` resets it. |
| `chat-conversation-git-rewind-resync` (Part 3 target) | ❌ can't reach | dies in `establishContact` under today's DHT conditions, before the new gate |

**Verdict:** the two-device DHT bus is too unreliable today to get a trustworthy baseline —
the known-good `send-message` scenario itself only passes ~1/3. No clean before/after comparison
is possible in this state.

**Part 1 cannot mechanically cause the observed failures:** in fresh-account scenarios
(`send-message`, `two-device-contact`) `getConversations()` is empty at handshake time, so the new
`isSyncing` branch in `loadSmartlist` never executes. The failing symptom — an incoming trust
request not arriving over the DHT — is entirely daemon-side networking.

**Both changes compile** (`:shared:compileDebugKotlinAndroid`, `:e2e-runner:compileKotlin`).
`:shared:testDebugUnitTest` currently fails to compile, but on a **pre-existing** unrelated error
in `ChatViewModelTest`/`TestFixtures` (`No value passed for parameter 'context'`) from the
in-flight biometric/`AppViewModel` work — not from these changes.

**Re-run Part 1 + Part 3 verification when DHT conditions improve** (devices on stable WiFi, off
cellular). Separately worth filing: the harness leaves accounts on a device after a timed-out
run, poisoning the next run.

---

## Prioritized stabilization task list

| P | Item | Evidence |
|---|---|---|
| **P0** | Fix silent-drop of first send into a not-yet-bootstrapped 1:1 swarm (F1 + F4 — **now confirmed same root cause**, see the "⚠️ UPDATE" box under "F1 — deep root-cause"). Daemon reports `Bootstrap with 0 device(s)` / `no sync connection`; the commit is created but never pushed. Gate/queue the send until the swarm has ≥1 active peer device (or `ConversationMemberEvent(action=1)` / `onConversationReady`), then flush; never no-op. Surface a pending/failed bubble. | F1 (fresh fixture) + F4 2/2 deterministic |
| **P0** | Port the reference client's `info["syncing"]` handling into `ConversationFacade.loadSmartlist` — jami-kmp reads only `info["mode"]` (came back `null` for a still-cloning repo) and falls through to `OneToOne`, so a not-yet-bootstrapped conversation looks "ready" and the send is allowed through. Setting `Conversation.Mode.Syncing` is what lets the send path know to queue. | F1 diag |
| **P1** | `default-pair-with-messages-1` fixture is **mislabeled** (`A.tar` holds account `4ae429a9…` but `index.json` says `fingerprintA = d29c6d5f…`) → `send-reply-roundtrip` sends to self. Re-capture it or swap `fingerprintA`/`B` in `conversation-pairs/index.json`. My earlier "F1 3/3 on this fixture" was partly this bad data. | F1 diag |
| **P1** | Send path must fail fast + visibly instead of blocking ~30s on an unresolvable conversation (F2). | F2 |
| **P2** | Check what `clearAppData`/`restoreAppData` actually tar up — the fresh fixture's restored data had **no conversation git repo** on disk (`Failed to read conversations directory`), forcing a DHT re-clone that starts seconds after the send. Either a `captureConversationPairAsset` gap or expected behavior worth documenting. | F1 diag |
| **P1** | Give `chat-conversation-git-rewind-resync` the `awaitMemberJoined` gate so it actually reaches its rewind/resync assertions — that self-heal path is currently untested. | F4 |
| **P1** | Stop rendering raw 40-hex fingerprints as titles; fix missing separator in join/invite system strings (F6). | F6 |
| **P2** | Characterize restored-account re-registration flakiness with a dedicated loop (F5). | F5 |
| **P2** | Accessibility audit — add semantics/contentDescription across Home + Chat (F11). | F11 |
| **P2** | Translate Home filter chips; sweep remaining hardcoded strings (F7). | F7 |
| **P3** | Fresh-repro F8 (ordering), F9 (edited body corruption), F10 (UUID attachment name). | F8/F9/F10 |
| **P3** | Doc/UX polish on harness scenarios needing `-P` args (F3). | F3 |

## Scenario run matrix

| scenario | roles | runs | result | notes |
|---|---|---|---|---|
| ping | 1 | 1 | ✅ | 25ms round-trip |
| account-creation | 1 | 1 | ✅ | |
| account-creation-bare | 1 | 1 | ✅ | |
| account-creation-username | 1 | 1 | ✅ | name registered, state=0 |
| account-enable-disable | 1 | 1 | ✅ | DHT leave/rejoin clean |
| account-reuse | 1 | 1 | ✅ | export/import preserves identity |
| name-lookup | 1 | 1 | ✅ | positive + NotFound both correct |
| register-name-taken | 1 | 1 | ✅ | rejection (non-zero state) — `state=3` per `doc/end2endTesting.md` and prior 2026-07-01 hardware runs; the "state=2" first logged here is believed to be a transcription slip (2 is the `name-lookup` NotFound code) |
| register-name-on-account | 1 | 2 | ⚠️ usage | needs `-Pusername` + a prior keepAccounts run (by design) — F3 |
| change-password | 1 | 1 | ✅ | change/remove/add + archive re-encrypt all verified |
| device-rename | 1 | 1 | ✅ | rename + restore, read back from daemon registry |
| import-correct/no/wrong-password | 1 | 0 | — | not run this session (covered by prior hardware validation in doc/TODO.md) |
| two-device-contact | 2 | 2 | ✅✅ | contact confirmed both sides ~13s |
| send-message | 2 | 3 | ✅✅✅ | bidirectional, gated on `awaitMemberJoined` |
| default-one-on-one-conversation | 2 | 3 | ✅✅✅ | 3 msgs A→B, gated on `awaitMemberJoined` |
| send-reply-roundtrip `contact-confirmed-no-messages-1` | 2 | 1 | ✅ | settled 0-msg fixture, both directions |
| send-reply-roundtrip `default-pair-with-messages-1` | 2 | 4 (1 w/ diag) | ❌ ×4 | **mislabeled fixture** — `A.tar` account ≠ `index.json` fingerprintA → sends to self → "no conversation". Not an app bug. |
| send-reply-roundtrip `e2e-mellow-salmon-…` (fresh 4-msg) | 2 | 3 (1 w/ diag) | ❌ ×3 | **F1=F4**: `startConversation` resolves OK; daemon re-clones repo ~3.7s post-send, commits into `Bootstrap with 0 device(s)` / `no sync connection` → never delivered (1 run also hit **F5**) |
| build-conversation-fixture | 2 | 1 | ✅ | captured fresh 4-msg pair |
| chat-conversation-git-rewind-resync | 2 | 2 | ❌❌ | **F4** — first send lost (sent ~2s before member-join processed); never reaches rewind logic |
| capture-live-pair | 2 | 0 | — | not run |

---

## Findings

### F1 — [HIGH] Restored/cold-loaded account cannot send into an existing 1:1 conversation that has message history
- **Repro:** `send-reply-roundtrip -PaccountState=default-pair-with-messages-1` — **3/3 deterministic FAIL**.
  Device A restores the whole-app tar (conversation git repo included, 3 messages at capture), then
  `SendMessage(accountId, peerUri, text)` on A → `ConversationFacade.startConversation(accountId, peer)`
  throws (conversation not known locally), the 30s `ConversationReady` fallback never fires, harness emits
  `ErrorEvent("no conversation with <peerUri>")`, scenario times out.
- **Contrast:** same scenario against `contact-confirmed-no-messages-1` (0 messages at capture) → **PASS**,
  both directions. The differentiator is a settled 1:1 conversation *with commits/history* at load time.
- **Likely mechanism (matches 2026-08-17 root-cause family in doc/TODO.md):** the only code path that
  registers a local `Conversation` object for a 1:1 swarm and indexes it into the URI-keyed lookup map is
  `ConversationFacade.onConversationReady()`. On cold-load of a restored account the contact + conversation
  already exist, so neither `onConversationReady()` nor the `onContactAdded()` proactive-registration fix
  (which only runs on a live `ContactAdded` event) fires → `getByUri()`/`getByContact()` never resolve it →
  `startConversation()` by peer URI fails permanently.
- **User impact:** restore-from-backup and link-new-device users cannot reply in any existing 1:1 chat that
  has history until (if ever) something re-triggers `ConversationReady`. The real UI would hang ~30s then
  fail silently (see F2).
- **Caveat CLOSED — confirmed a load-path bug, not a stale fixture:** captured a brand-new messaged pair
  (`build-conversation-fixture` → `e2e-mellow-salmon-e2e-bright-lemur`, 4 seeded msgs, minutes old) and ran
  `send-reply-roundtrip` against it — **same failure**: A registers fine, `SendMessage` from A produces no
  `MessageReceived` on B *or* A, 60s timeout. (One earlier attempt also hit F5.) Two independent fixtures,
  same signature.
- **Next:** trace `ConversationFacade` account-load / `loadConversationHistory` path — where 1:1 swarm
  conversations get indexed by peer URI on cold start vs. only on `ConversationReady`.

### F1 + F4 unifying hypothesis
Both reduce to: **`ConversationFacade.sendTextMessage()` into a 1:1 swarm conversation that is not fully
"live" locally silently no-ops** — no local optimistic echo, no daemon commit, no delivery, and
(sometimes) a delayed `ErrorEvent("no conversation with …")`. The two ways to get an un-live 1:1
conversation:
- **F1:** cold-load a restored/linked account — `onConversationReady()` never fires for the already-existing
  conversation, so it's never indexed by peer URI.
- **F4:** send in the ~2–3s window after `ContactAdded(confirmed=true)` but before
  `ConversationMemberEvent(action=1)` — conversation object exists but the swarm channel isn't ready.
Worth checking whether one fix (gate/queue sends until the conversation is confirmed live, with a visible
pending state) closes both.

### Code trace (for whoever picks this up)

> **Line-number note (added 2026-09-06):** the `info["syncing"]` → `Mode.Syncing` port was
> subsequently applied to `ConversationFacade.loadSmartlist` (~lines 636–648), shifting everything
> below it down by ~14 lines. Current (post-port) locations: `findConversation()` `:926`,
> `loadSmartlist()` `:620`, `onConversationReady()` `:989` (emits `ConversationReady` at `:1038`).
> The `:912` / `:~617` / `:975` / `:1024` refs below are pre-port.

- `ConversationFacade.sendTextMessage()` (`ConversationFacade.kt:290`) for a swarm conversation does
  **only** `accountService.sendConversationMessage(accountId, conversation.uri, text, replyTo)` and returns —
  **no optimistic local interaction is inserted** (unlike the legacy branch). `ChatViewModel.sendMessage()`
  adds its own `pending-` placeholder, but `ConversationFacade` itself is fire-and-forget.
- `AccountService.sendConversationMessage()` (`AccountService.kt:807`) →
  `daemonBridge.sendMessage(accountId, conversationUri.rawRingId, message, replyTo ?: "", flag)`. If the
  daemon's conversation isn't bootstrapped/live, the daemon creates **no commit** and fires **no
  `swarmMessageReceived`** — so no `MessageReceived` on the sender either. That's the silent no-op.
- `ConversationFacade.startConversation()` (`ConversationFacade.kt:203`) = `getAccountWithSmartlist()` +
  `findConversation()`; `findConversation()` (`:912`) = `account.getByUri(uri) ?: account.getByContact(uri)`.
- Cold-load path `loadSmartlist()` (`ConversationFacade.kt:~617`) iterates
  `daemonBridge.getConversations(accountId)`, does `account.newSwarm(convId, mode)` + adds members +
  `account.conversationStarted(conversation)` — so *if* `getConversations()` already returns the restored
  conversation, it should be indexed. F1 strongly suggests `loadSmartlist` runs **before the daemon has
  finished loading conversation repos from disk** on a just-restored account (`getConversations()` still
  empty), and nothing re-runs it — `conversationReady` only fires for genuinely new conversations via
  `onConversationReady()` (`:975`), not for ones loaded from disk, so the harness's 30s
  `ConversationReady`-await fallback never resolves.
- **Suggested fix direction:** on account restore/link, re-run `loadSmartlist` when
  `daemonBridge.getConversations()` transitions empty→non-empty (or poll/settle briefly), and have the send
  path surface a visible pending/failed state + retry rather than a silent daemon drop.

---

## F1 — deep root-cause (2026-09-04, `loadSmartlist` path traced end to end)

> ### ⚠️ UPDATE — diagnostic run overturns the "conversation never gets indexed" hypothesis
>
> Added temporary `[F1DIAG]` `Log.d` in `loadSmartlist` + `startConversation`
> (`shared/.../ConversationFacade.kt`; remove with
> `git checkout shared/src/commonMain/kotlin/net/jami/services/ConversationFacade.kt`) and re-ran on
> **device A = 37281JEHN03065**. Findings:
>
> **`getConversations()` is NOT empty and the indexing is NOT the bug.** On both fixtures it returned
> `[<convId>]` (count 1) on the first `loadSmartlist`, immediately after registration.
>
> **The two failing fixtures fail for two *different* reasons:**
>
> **A) `e2e-mellow-salmon-e2e-bright-lemur` (fresh, self-captured this session) — the real bug.**
> `[F1DIAG]` shows `getConversations()=1`, members `[2c3de47c:null, 3ab8740b:null]`, `isUser` flags
> correct, `contact=2c3de47c` (the peer), and **`startConversation … -> OK swarm:a541598973 mode=OneToOne`**
> — the lookup *succeeds*. The scenario still fails because the daemon can't deliver. Daemon log on A:
> ```
> conversation_module: Start loading conversations…
> conversation_module: Failed to read conversations directory …/637777…/conversations: No such file or directory
> conversation_module: Conversations loaded!            ← ZERO repos on disk
> [F1DIAG] getConversations()=1 [a541…]                 ← daemon lists it anyway (from account/trust metadata)
> … SendMessage command …
> conversation_module: [a541…] Cloning conversation … git://<peerDevice>/a541…   ← re-clone STARTS ~3.7 s AFTER the send
> conversationrepository: New conversation cloned
> conversationrepository: New message added with id 90b7fcd9…   ← A's message committed (post-clone)
> conversation_module: Not yet bootstrapped, save notification
> conversation:        Bootstrap with 0 device(s)
> conversation:        Refreshing tracked members: 0/2 active (0/4 devices)   ← no peer device connected
> sync_module:         no sync connection.  (repeats)
> ```
> So: the restored app-data tar for this fixture **did not contain the conversation git repo**; the daemon
> re-clones it from the peer over the DHT, starting seconds *after* the Kotlin layer already let the send
> through. The message is committed locally into a conversation that has **0 active peer devices** and **no
> sync connection**, and neither recovers within 60 s → B never receives it, scenario times out. This is
> the **F4 family** (send into a not-yet-bootstrapped swarm), reached via the restore path instead of the
> fresh-handshake path.
>
> **B) `default-pair-with-messages-1` — a corrupt/mislabeled fixture, not an app bug.**
> `[F1DIAG]` shows the account restored from `A.tar` reports `username='4ae429a9…'`, but
> `conversation-pairs/index.json` records `fingerprintA = d29c6d5f… / fingerprintB = 4ae429a9…`. Members
> are `[4ae429a9:admin, d29c6d5f:member]` — i.e. `A.tar` **is** account `4ae429a9` (the swarm admin), so
> `fingerprintA` in the index is wrong (swapped with B). `send-reply-roundtrip` therefore sends A's message
> to `pair.fingerprintB = 4ae429a9` = **A's own identity**; `getByContact(ownUri)` correctly matches
> nothing (the conversation's `.contact` is the *other* member) → `startConversation -> NULL` →
> `ErrorEvent("no conversation with …")`. My earlier "F1 3/3 deterministic on `default-pair-with-messages-1`"
> was partly chasing this bad fixture. (Its daemon log also shows `Bootstrap with 0 device(s)` /
> `no sync connection`, so delivery would have failed anyway — but the Kotlin-visible symptom is the
> mislabel.)
>
> **Corrected conclusion:** F1 is **not** a missing-index / `onConversationReady`-never-fires bug.
> `loadSmartlist` indexes the conversation fine. The real issues are:
> 1. **`loadSmartlist` ignores the daemon's `syncing` flag.** The reference client reads `info["syncing"]`
>    and sets `Conversation.Mode.Syncing`; jami-kmp reads only `info["mode"]` (which came back **`null`** here
>    because the repo wasn't cloned yet) and falls through to `OneToOne`. So a still-cloning /
>    not-bootstrapped conversation is presented as a ready `OneToOne`, and the send is allowed through.
> 2. **Nothing gates the send on swarm bootstrap / ≥1 active peer device.** `sendTextMessage` fires into a
>    conversation the daemon reports as `Bootstrap with 0 device(s)`; the commit is created but never
>    pushed, with no retry/queue and no user-visible pending state.
> 3. **The restore path doesn't guarantee the conversation git repo is present** before the account goes
>    online — at least for the fresh fixture, the tar lacked it and the daemon had to re-clone. (Whether
>    that's a `captureConversationPairAsset` gap or expected "repos re-clone on restore" behavior needs a
>    look at what `clearAppData`/`restoreAppData` actually tar up.)
> 4. `default-pair-with-messages-1` fixture is mislabeled — re-capture it or swap `fingerprintA`/`B` in
>    `conversation-pairs/index.json` after verifying against `A.tar`'s account ring id.
>
> **Revised fix direction:** port the reference's `info["syncing"]` handling into `loadSmartlist`; then have
> `ChatViewModel` / `sendTextMessage` **queue-and-flush** (or block with a visible pending state) for a
> `Conversation.Mode.Syncing` conversation, flushing on `onConversationReady` / first
> `ConversationMemberEvent(action=1)`. This also closes F4.

### The lookup that fails
`ConversationFacade.startConversation(accountId, peerUri)` (`ConversationFacade.kt:203`)
→ `getAccountWithSmartlist(accountId)` → `loadSmartlist(account)` → `findConversation(account, peerUri)`
(`:912`) = `account.getByUri(peerUri) ?: account.getByContact(peerUri)`.

`peerUri` here is a **contact** URI (`Uri(null,null,<40hex>,null)`), never a `swarm:` URI. For a
**OneToOne swarm**, `Account.conversationStarted()` (`Account.kt:128`):
- stores the conversation **only** under `conversations["swarm:<convId>"]`,
- and in its `mode == OneToOne` branch *actively removes* any `conversations["jami:<peer>"]` / `cache[...]`
  key and calls `contact.setConversationUri(...)`.

So `getByUri(peerUri)` can **never** hit for a settled OneToOne swarm — it only checks
`conversations[key] ?: pending[key] ?: cache[key]` keyed by the peer URI, which was deleted.
Resolution therefore depends **entirely** on `getByContact(peerUri)` (`Account.kt:205`), whose only
useful branch is `conversations.values.find { it.contact?.uri == peerUri }`.
`Conversation.contact` (`Conversation.kt:211`) = `contacts.firstOrNull { !it.isUser }`. `Uri.equals`
compares `username`+`host` only, and both the caller's `peer` and the member URI are built by the same
`Uri.fromString(<bare hex>)`, so **URI equality is not the problem** — the problem is whether that
non-user member row exists in the `Conversation` at all, which is populated **only** by
`loadSmartlist` / `onConversationReady` from `daemonBridge.getConversationMembers()`.

### Why `loadSmartlist` doesn't populate it on a restored account
`loadSmartlist()` (`ConversationFacade.kt:620`) iterates `daemonBridge.getConversations(accountId)`
(`DaemonBridge.android.kt:396` → `JamiService.getConversations`). On a freshly **imported** account the
daemon's `ConversationModule` loads the on-disk conversation git repos **asynchronously**; for a window
after import, `getConversations()` returns `[]` (or returns the id but `getConversationMembers()` is not
yet fully populated). If `loadSmartlist` runs inside that window, the conversation is never created /
never gets its peer member, and **nothing re-runs `loadSmartlist` for that account afterward** on the
send path.

Every `loadSmartlist` trigger in `ConversationFacade.init` is unreliable as a "repos finished loading"
signal:
| Trigger (`ConversationFacade.kt`) | Why it doesn't cover the restored-account case |
|---|---|
| `accountService.currentAccount.collect` (`:104`) | Fires only when the `Account` **value** changes. `loadAccounts()` reuses the same `Account` instance for an existing account, so it never re-fires — the code's own comment at `:140` says exactly this. |
| `AccountEvent.AccountsChanged` (`:147`) | Comment at `:137` claims it "fires after libjami has finished loading all conversation git repos from disk" — **this is an incorrect assumption**. `AccountsChanged` originates from the daemon's `accountsChanged` signal (`DaemonBridge.android.kt:709` → `AccountService.onAccountsChanged` `:1044` → `loadAccounts()` → emit), which fires on account **list/config** changes, with no contract about conversation-repo load completion. |
| `RegistrationStateChanged → isRegistered` (`:160`) | `REGISTERED` = DHT connectivity established. It does **not** imply on-disk conversation repos are loaded. On the test devices this fires seconds before the repos are ready — and the harness waits on `REGISTERED` before sending, which is why F1 is *deterministic*, not a rare race. |
| connectivity restored → `refreshAllConversations` (`:131`) | N/A on a stable USB/Wi-Fi connection. |
| `getAccountWithSmartlist()` on the send path (`:215`) | Runs `loadSmartlist` once, synchronously, at send time. If `getConversations()` is still `[]` then, it loses — and there is no retry. |

### Why the fallback also fails
When `findConversation` returns null, `startConversation` throws; the harness `CommandHandler.SendMessage`
(and the real UI's `NewConversationViewModel.createConversation()` /
`ContactDetailsViewModel.startConversation()`) fall back to awaiting
`ConversationEvent.ConversationReady`. That event is emitted **only** by
`ConversationFacade.onConversationReady()` (`:975`, → `_conversationEvents.emit(ConversationReady)` at
`:1024`), which is driven by the daemon `ConversationCallback.conversationReady`
(`DaemonBridge.android.kt:951`). **libjami does not emit `conversationReady` for conversations that
already exist on disk when the account is loaded** — only for newly created / freshly cloned ones. So on
a restored account the fallback waits the full 30s and then yields `ErrorEvent("no conversation with …")`
(F2).

### Why the "silent timeout, no ErrorEvent" variant happens
On the fresh `e2e-mellow-salmon-…` run that timed out with **no** `ErrorEvent`: a `loadSmartlist` *did*
catch the conversation, `startConversation` resolved, and `sendTextMessage` →
`AccountService.sendConversationMessage` (`:807`) → `daemonBridge.sendMessage(accountId, swarmId, …)` was
called — but the daemon's conversation wasn't fully active yet, so it created no commit and fired no
`swarmMessageReceived`. No local echo, no delivery, no error. This is the same failure mode as **F4**.

### `onMessageReceived` / `onConversationMemberEvent` don't self-heal
Per the 2026-08-17 notes (doc/TODO.md), these handlers **no-op when the conversation id is unknown**
instead of creating+indexing it. So even when the daemon *does* deliver real swarm traffic for the
restored conversation, that traffic can't bootstrap the missing local `Conversation` object.

### Concrete fix options (pick 1–2)
1. **Self-heal on unknown-conversation daemon events.** In `onMessageReceived` / `onConversationMemberEvent`
   / `onConversationProfileUpdated`, if `account.getSwarm(convId) == null`, build it: `newSwarm` +
   load members via `getConversationMembers` + `conversationStarted` + emit `ConversationReady`. Cheapest
   and closest to how `jami-android-client` behaves.
2. **Poll `getConversations()` to stability after import/link.** After `ImportAccount` / device link, in
   `ConversationFacade` run `loadSmartlist` on a short backoff until `getConversations(accountId)` is
   non-empty *and* unchanged across two polls (cap ~10s), then stop.
3. **Re-run `loadSmartlist` from `getAccountWithSmartlist` when it comes up empty** for an account that has
   confirmed contacts (`account.getContacts()` non-empty but `getConversations()` empty) — retry a few
   times with a small delay before giving up.
4. **Fix the `AccountsChanged` assumption** — either find/introduce a real "conversations loaded" daemon
   signal to hook, or delete the misleading comment at `ConversationFacade.kt:137` so the next reader
   doesn't trust it.
5. **Bound the send-path fallback** — don't block 30s on a `ConversationReady` that will never arrive for a
   disk-loaded conversation; fail fast with a retriable, user-visible error, or queue the message and flush
   on the next `loadSmartlist` that resolves the conversation.

### Checked by the diagnostic run (see UPDATE box at the top of this section)
- `getConversations()` returns `[<convId>]` (count 1) immediately post-restore on **both** fixtures — the
  "empty getConversations / timing race" theory is **disproved**.
- `getConversationMembers()` returns **both** members (not just self). On the well-formed fixture the
  `isUser` flags and `Conversation.contact` are correct and `startConversation` **resolves**.
- Options 1–3 in the list above (self-heal on unknown-conversation events / poll-to-stability / retry
  `loadSmartlist`) are **not the fix** — the conversation is already indexed. The fix is `syncing`-flag
  handling + a send gate/queue (see the UPDATE box's "Revised fix direction").

### jami-android-client: does it load conversations from a database?
**No — not the conversation *set*.** Reference `AccountService.loadAccount()`
(`jami-android/libjamiclient/src/main/kotlin/net/jami/services/AccountService.kt:283`) builds the account's
conversation maps exactly like jami-kmp: `JamiService.getConversations()` → `conversationInfos()` →
`getConversationMembers()` → `account.conversationStarted()`. The ORMLite DB (`HistoryService`) only
stores **message history / read-markers / legacy non-swarm interactions**; swarm conversations are
authoritative from the daemon. So a DB is not what makes the reference resolve restored conversations.
**Two real differences in the reference that matter here:**
1. It reads **`info["syncing"]`** and sets `Conversation.Mode.Syncing` (jami-kmp ignores it — root cause #1
   above). It also stores `requestMode`/`lastEvent` for syncing convs so they still appear in the list.
2. `loadAccount` is gated: `registrationStateObservable.filter { it != UNLOADED && it != INITIALIZING }
   .firstElement()` before loading — it waits for the account to leave INITIALIZING. jami-kmp's
   `loadSmartlist` fires on several looser triggers.

### F2 — [MED] `SendMessage` blocks ~30s before surfacing "no conversation" failure
- In the F1 repro the `ErrorEvent` arrives ~30s after the send command (harness `CommandHandler.SendMessage`
  `withTimeoutOrNull(30_000)` fallback). The real UI's `NewConversationViewModel.createConversation()` uses
  the same `startConversation()` + `ConversationReady`-await fallback pattern, so a real user sending into an
  unresolvable conversation would see the app appear to hang for 30s and then fail with no user-facing error.
- **Ask:** on the send path, surface a fast, visible error (or optimistic-queue + retry) instead of a silent
  30s stall when the conversation can't be resolved.

### F4 — [HIGH] A message sent within ~2–3s of contact confirmation is silently dropped (no echo, no delivery, no error)
- **Repro:** `chat-conversation-git-rewind-resync` — **2/2 deterministic FAIL**. Fresh contact handshake
  (A accepts B), conversation established, then A `SendMessage(...)` issued ~2s later. B never gets a
  `MessageReceived`; **A doesn't even get its own local optimistic echo**; scenario times out at 60s on
  the first `await("B", MessageReceived)`.
- **Timeline proof:** run 1 — contact confirmed +15697ms, SendMessage +15699ms, but B's
  `ConversationMemberEvent(action=1)` for A's join only at +17931ms (send fired ~2.2s *before* the join
  was processed). Run 2 — confirmed +11694ms, send +11697ms, join processed +14451ms (~2.75s early).
- **Contrast:** `send-message` and `default-one-on-one-conversation` both PASS 3/3 — because on 2026-08-17
  they were patched to gate the first send on `ScenarioSupport.awaitMemberJoined` (wait for
  `ConversationMemberEvent action=1`). `chat-conversation-git-rewind-resync` was never given that gate and
  still reproduces the raw race every time.
- **This is the app bug the 2026-08-17 harness gate only papered over** (see doc/TODO.md "Bug Findings
  2026-08-14", part 2): "sending immediately after `ContactAdded(confirmed=true)` still raced the sender's
  own swarm channel … the send would succeed locally but never reach the peer." Observed here it's worse —
  no local echo either, i.e. `sendTextMessage` effectively no-ops when the 1:1 swarm channel isn't live yet.
- **User impact:** accept a contact request, immediately open the chat and send — the message vanishes.
  The 2026-08-16 20s FAILED-watchdog would eventually flip the bubble to FAILED + offer retry, so it's not
  totally invisible, but it's a data-loss-shaped first-contact experience with a 20s dead wait.
- **Ask:** the app's send path (`ConversationFacade.sendTextMessage` / `ChatViewModel.sendMessage`) needs to
  either (a) wait for the swarm member-join before the first send into a just-created 1:1 conversation, or
  (b) durably queue the message and flush it when the conversation goes live — not silently discard it.
- **Coverage side effect:** because it dies at setup, `chat-conversation-git-rewind-resync` currently never
  exercises the git rewind / resync self-heal logic it exists to test. That path is effectively untested.
  Give the scenario the same `awaitMemberJoined` gate so it can reach its actual assertions.

### F5 — [MED, flaky] Restored/linked account intermittently fails to re-register within 60s
- Seen once (`send-reply-roundtrip` on the fresh `e2e-mellow-salmon-…` fixture): after
  `installConversationPairAsset` re-imports the archive, device A never emits
  `RegistrationStateChanged(REGISTERED)` and the harness aborts with
  `role=A did not re-register after restore: Timed out waiting for 60000 ms`. Passed on immediate retry.
- Could be DHT/device throttling after a long test run, or a real import-then-register wedge on an archive
  that carries an existing swarm conversation. Worth a dedicated loop (import fixture → await REGISTERED,
  ×20) to get a failure rate.

### F6 — [MED] Raw 40-hex fingerprints shown instead of names + missing space in join/invite system strings
- Real standard-app UI (device A + B). Conversation-list row title, chat top-bar title, and the in-chat
  system messages all render the full untruncated 40-char account hash. jami-android-client shows the
  registered username or a formatted/truncated form.
- The templated join/invite strings concatenate the id directly onto the next word with **no separator**:
  `4aabafc5b31876f6614434e5e9f8d342fbdf7762wurde zur Unterhaltung eingeladen.` /
  `d4b71b13…d6f2ist der Unterhaltung beigetreten.` — missing space (or the `%s` placeholder butts against
  the literal). Present regardless of data age.

### F7 — [LOW/MED] Localization gaps in a German locale
- Home filter chips **All / Unread / Groups** are untranslated (German everywhere else on the screen:
  "Suchen oder hinzufügen", "Unterhaltung starten"). Matches doc/TODO.md's "conversation type labels" /
  hardcoded-string notes. Chat composer ("Nachricht schreiben") is translated, so it's uneven.

### F11 — [MED] Compose UI exposes almost no accessibility metadata
- `uiautomator dump` of Home and Chat shows nearly every node with empty `resource-id` and empty
  `content-desc` (only "Menü" carried one). Screen-reader users get unlabeled buttons; this also blocks
  reliable automated UI testing. CLAUDE.md's Feature-Completeness list requires content descriptions / focus
  order. Worth an audit pass adding `Modifier.semantics { contentDescription = … }` / `stateDescription` to
  icon buttons, conversation rows, and the message input controls.

### Observed, needs fresh repro (possibly stale test data from Aug/Sep manual sessions)
- **F8 — message ordering vs. timestamp:** in one chat, bubbles under "August 13, 2026" appear in an order
  that doesn't match their shown times (`11:28`, `11:28`, then a `21:28` bubble after them, then `21:38`,
  `21:48`). Could be TZ rendering inconsistency or sort key ≠ commit time. Re-check with a freshly built
  conversation.
- **F9 — edited-message body renders corrupted:** a bubble showed
  `PENDING_EDIT_CLEAN_v3hello_froPENDING_EDIT_TEST_1m_A_v2` — original and edited text interleaved rather
  than replaced. doc/TODO.md (2026-08-13) attributes the source string to an ADB injection artifact, but the
  *rendering* merged the two instead of showing only the current text — worth confirming edit display after
  resync with clean input.
- **F10 — attachment bubble shows raw UUID filename** (`8b893612-3a1d-4363-8641-…jpeg`). Same family as the
  fixed `FilePickerEffect` `.gz` bug (doc/TODO.md, FIXED 2026-08-13); this bubble predates that fix. Confirm
  a newly-sent file/camera capture shows a friendly name.

### F3 — [LOW/INFO] Harness scenario-suite inconsistencies (not app bugs)
- `register-name-on-account` and `send-reply-roundtrip` hard-fail with a usage error unless given
  `-Pusername=` / `-PaccountState=`; every other single-device scenario self-provisions from the pool.
  `register-name-on-account` is by-design ("operate on an already-live account", needs a prior
  `-PkeepAccounts=true` run) — worth a one-line note in `doc/end2endTesting.md` so it's not mistaken for a
  regression.
- `build-conversation-fixture` ignores `-PaccountState=<label>` and derives its own label from the two
  registered usernames (`<nameA>-<nameB>`). Minor: `-PaccountState` looks honored but isn't.

---

## Summary

Ran the full e2e-runner scenario registry (14 single-device runs across 11 scenarios, 16 two-device runs
across 8 scenarios) plus a real-app UI pass on both devices. **The account lifecycle (create / import /
password / name registration / enable-disable / device rename) and the *fresh-handshake* messaging path
are solid** — 0 failures across many repeats. **All failures cluster on one area: sending into a 1:1 swarm
conversation that isn't fully live locally** (F1 restore/cold-load, F4 send-before-member-join) — both
deterministic, both reducing to `sendTextMessage` silently no-opping. Secondary: real-UI presentation
issues (raw fingerprints F6, i18n F7, accessibility F11) and stale-data observations to re-repro
(F8/F9/F10).

Devices left connected and authorized; standard + harness apps left installed; test fixtures added to
`e2e-runner/harness-memory` (`e2e-mellow-salmon-e2e-bright-lemur`). No source files modified.

## Run log
- 2026-09-04 09:40–12:32 CEST — single unattended session, devices 37281JEHN03065 (Pixel 7a) +
  FA7AJ1A06417 (Pixel 2, Android 11). Raw run timelines under `e2e-runner/harness-memory/runs/`.

