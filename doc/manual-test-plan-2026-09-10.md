# Manual Test Plan — Candidate Bug Confirmation (2026-09-10)

**Purpose.** This document is a work order, not a bug list. Every entry below is a
*hypothesis* derived from reading the source, not an observed failure. Each one has a
repro procedure designed so that a single run produces an unambiguous
**CONFIRMED** / **REJECTED** verdict.

**Scope of the scan.** `shared/src/commonMain` (158 files), `shared/src/androidMain`
(37 files), plus the Android host manifest. iOS-only paths were read but not
prioritised — Apple targets do not compile on the current Linux host, so nothing
iOS-specific is testable here.

**What was deliberately excluded.** Anything already tracked in `doc/TODO.md`,
`doc/KNOWN-ISSUES.md`, or `doc/stabilization-findings-2026-09-04.md` as a known gap
(chat plugins, video recording, desktop DaemonBridge no-ops, push infrastructure,
iOS remote video). Section 8 lists the small number of previously-known items that
are re-tested here because a *new* code path may have changed their behaviour.

---

## How to run this

**Devices.** Two Android devices, A and B, each with its own Jami account, both
already contacts of each other with at least one live conversation containing
20+ messages. Where a test needs a *fresh* pair, it says so explicitly.

**Instrumentation.** Keep `adb logcat` running throughout:

```
adb logcat -s ChatViewModel:* ConversationsVM:* ConversationFacade:* AccountService:* CallService:*
```

Several tests below key off specific log lines. The in-app log view at
**Settings → Debug logs** (`ui/screens/DebugLogsScreen.kt`) mirrors the same output
and additionally reports swarm bootstrap state
(`bootstrapping (n/m not yet live)`) — check it whenever a messaging test behaves
oddly, since a still-bootstrapping swarm is a known confound
(`doc/stabilization-findings-2026-09-04.md` F4).

**Recording a verdict.** Fill in the box at the end of each test:

```
VERDICT: CONFIRMED | REJECTED | INCONCLUSIVE
Build/commit:
Device + OS:
Notes:
```

**INCONCLUSIVE is a real answer.** Several tests below depend on swarm sync timing.
If a run is contaminated by a bootstrap stall, mark it INCONCLUSIVE and re-run after
an airplane-mode cycle on both devices rather than recording a false REJECTED.

---

## 1. Chat — message loss and delivery state

These are the highest-severity hypotheses in this document. A confirmed B1 means
messages can be silently destroyed before they are ever sent.

### B1 — A pending message is destroyed when history reloads

**Severity if confirmed:** High — silent message loss, no error shown to the user.

**Hypothesis.** `loadMessagesFromHistory()` rebuilds `state.messages` wholesale from
`conversation.getSortedHistory()`. Optimistic bubbles (`pending-*` ids) live only in
UI state and are not in that history, so any reload deletes them. Reload is triggered
by `SwarmLoaded` and by *every* `DataTransferEvent` — including file-transfer progress
ticks for an unrelated file in the same conversation.

For a `WAITING_TO_SYNC` bubble the consequence is worse than a cosmetic flicker: that
message has not been dispatched to the daemon at all, and `flushPendingSyncSends()`
recovers it by scanning `state.messages`. Once the bubble is gone, the text is gone.

**Code evidence.**
- `ui/viewmodel/ChatViewModel.kt:742` — `loadMessagesFromHistory()` assigns
  `messages = withSeparators`, discarding anything not in daemon history.
- `ui/viewmodel/ChatViewModel.kt:200,215` — the event collector calls it on
  `SwarmLoaded` and `DataTransferEvent`.
- `ui/viewmodel/ChatViewModel.kt:474` — `flushPendingSyncSends()` reads the held
  message back out of `state.messages`.

**Repro — B1a (pending SENDING bubble, easier).**
1. On A, open the conversation with B.
2. Put A into airplane mode (so no daemon echo can arrive).
3. Send a text message `"b1a-probe"`. A grey single-tick "sending" bubble appears.
4. Without leaving the screen, have B send A a **file** (any small image).
   (Offline A will not receive it — instead, use B1b below if no transfer event fires.)
5. Alternative trigger, no second device needed: leave airplane mode on, background
   and foreground the app to provoke a `SwarmLoaded` reload of the conversation.

**Expected (no bug).** The `"b1a-probe"` bubble stays on screen, still marked sending,
and after 20 s flips to a red FAILED state with a retry affordance.

**Suspected actual.** The bubble disappears entirely at the moment of the reload. No
FAILED state, no retry, no trace in the UI.

**Repro — B1b (WAITING_TO_SYNC bubble, the severe case).**
1. Use two **fresh** accounts with no prior contact.
2. From A, add B as a contact and immediately open the conversation.
3. While the swarm is still bootstrapping — confirm via **Settings → Debug logs**
   showing `bootstrapping (1/… not yet live)` — send `"b1b-probe"`.
4. Verify the bubble shows the WAITING_TO_SYNC state and that logcat contains
   `conversation … is syncing; holding message pending-…`.
5. Now force a history reload before the swarm goes live: send a file from A into the
   same conversation (this fires `DataTransferEvent` locally).

**Expected (no bug).** `"b1b-probe"` remains held, and when the peer joins, logcat
shows `conversation … now live; flushing 1 held message(s)` and B receives it.

**Suspected actual.** The held bubble vanishes at step 5. The flush log line never
appears (or reports `0`), and B never receives `"b1b-probe"`.

**Discriminator.** The log line `now live; flushing N held message(s)` is decisive.
`N == 0` after a held message was visibly present = CONFIRMED.

```
VERDICT:
Build/commit:
Device + OS:
Notes:
```

---

### B2 — Your own messages appear as the other person's in chat search

**Severity if confirmed:** Medium — cosmetic but pervasive and obviously wrong.

**Hypothesis.** `handleSearchResults()` decides authorship by comparing the daemon's
`author` field against `currentAccountId`. But `author` is a **ring ID** (the 40-hex
public-key hash) while `currentAccountId` is the **local account id** (a short local
identifier). They can never be equal. Elsewhere in the same file the code does this
correctly — `appendMessage()` deliberately looks up `accountService.getAccount(id)?.username`
and carries a comment explaining that `account.username` is the public-key hash. The
search path was not given the same treatment.

The fallback `author.isEmpty()` is the only way a search result is ever classified as
outgoing, and the daemon populates `author` for swarm messages.

**Code evidence.**
- `ui/viewmodel/ChatViewModel.kt:1029` — `val myId = currentAccountId ?: ""`
- `ui/viewmodel/ChatViewModel.kt:1039` — `isOutgoing = author.isEmpty() || author == myId`
- Contrast `ui/viewmodel/ChatViewModel.kt:899` — the correct pattern.

**Repro.**
1. In a conversation with at least 3 messages sent by **you** and 3 sent by the peer,
   ensure one distinctive word (e.g. `searchprobe`) appears in both directions.
2. Open the chat overflow menu → Search.
3. Type `searchprobe`.

**Expected (no bug).** Your own results render as outgoing (right-aligned / outgoing
bubble styling), the peer's as incoming — matching how they look in the main list.

**Suspected actual.** *Every* result renders as incoming, including your own messages.

**Discriminator.** If even one result renders as outgoing, the hypothesis is wrong —
mark REJECTED and note which message it was.

```
VERDICT:
Build/commit:
Device + OS:
Notes:
```

---

### B3 — The thumbs-up shortcut is silently dropped in a new conversation

**Severity if confirmed:** Medium — message loss, but only in a narrow window.

**Hypothesis.** `sendEmoji()` calls `accountService.sendConversationMessage()`
directly. It does not go through `sendOptimistic()`, so it gets no optimistic bubble,
no `Mode.Syncing` check, no WAITING_TO_SYNC hold, and no send watchdog. In a
still-bootstrapping 1:1 swarm this is exactly the "fire into the void" case that
`sendOptimistic()` was written to prevent.

**Code evidence.** `ui/viewmodel/ChatViewModel.kt:498` — the whole function body is an
unconditional `sendConversationMessage`, versus `sendOptimistic()` at
`ui/viewmodel/ChatViewModel.kt:414` which branches on `Mode.Syncing`.

**Repro.**
1. Fresh account pair, as in B1b. Add contact, open conversation while still
   bootstrapping (verify in Debug logs).
2. Tap the thumbs-up / default-emoji send button (do **not** type anything).
3. Wait for the conversation to go live, then wait a further 60 s.

**Expected (no bug).** The 👍 either appears held and is flushed on join, or is
delivered once the swarm is live.

**Suspected actual.** Nothing appears in A's own chat at all, and B never receives it.
Compare against a typed `"b3-control"` message sent at the same moment — if the typed
one is held and delivered while the emoji is not, that is a clean CONFIRMED.

**Note.** Run the typed control message in the same session. Without it you cannot
distinguish this bug from the already-known F4 daemon-level drop.

```
VERDICT:
Build/commit:
Device + OS:
Notes:
```

---

### B4 — The typing indicator sticks on after you leave mid-draft

**Severity if confirmed:** Low — privacy-adjacent annoyance, visible to the peer.

**Hypothesis.** `updateInput()` sends `setIsComposing(…, true)` on every keystroke.
`sendMessage()` clears it. `onLeave()` — called from `ChatScreen`'s `DisposableEffect`
on navigating back — sets `conversation.isVisible = false` but never sends
`setIsComposing(…, false)`. So abandoning a half-typed message leaves the peer
looking at a permanent "typing…".

**Code evidence.** `ui/viewmodel/ChatViewModel.kt:1050` (`onLeave`) versus
`ui/viewmodel/ChatViewModel.kt:626` (`updateInput`).

**Repro.**
1. Watch B's screen showing the conversation with A open.
2. On A, open the conversation with B and type `"hello"` — do not send.
3. Confirm B shows the typing indicator.
4. On A, press back to return to the conversation list. Do not send, do not clear the field.
5. Watch B for 60 s.

**Expected (no bug).** B's typing indicator clears within a few seconds of A leaving.

**Suspected actual.** B's typing indicator remains until it is cleared by some other
event (A sending a message, or a daemon-side timeout).

**Caveat.** The daemon may impose its own composing timeout. If the indicator clears
after a long but consistent interval (e.g. exactly 30 s or 60 s), record the interval
and mark INCONCLUSIVE — that is a daemon timeout masking, not disproving, the missing
clear.

```
VERDICT:
Build/commit:
Device + OS:
Notes:
```

---

### B5 — Scrolling back through history stops early

**Severity if confirmed:** Medium — older history becomes unreachable.

**Hypothesis.** `loadMore()` decides whether more history exists by comparing list
sizes before and after: `hasMore = newCount > previousCount`. That count includes
injected date separators and excludes interactions that
`interactionToMessageItem()` maps to `null` (`InteractionType.INVALID`). A page that
loads only invalid interactions — or that races with B1's removal of a pending
bubble — leaves the count unchanged, latching `hasMoreHistory = false` permanently
for that screen visit.

**Code evidence.**
- `ui/viewmodel/ChatViewModel.kt:721` — `val hasMore = newCount > previousCount`
- `ui/viewmodel/ChatViewModel.kt:811` — `interactionToMessageItem` returns `null` for `INVALID`.

**Repro.**
1. Use a conversation with substantial history — at least 200 messages, ideally
   including deleted/edited messages and file transfers.
2. Open it and scroll to the very top repeatedly, allowing each page to load.
3. Note the oldest message reachable, and cross-check it against the true oldest
   message (visible on the other device, or after force-stopping and reopening the app).

**Expected (no bug).** You can page back to the first message in the conversation.

**Suspected actual.** Paging stops at some intermediate point. Logcat shows
`loadMore: previous=N new=N hasMore=false` while older messages demonstrably exist.

**Discriminator.** The `hasMore=false` log line printed while `previous == new` **and**
older messages exist on the peer device = CONFIRMED. If `previous == new` only when
you have genuinely reached the start, REJECTED.

```
VERDICT:
Build/commit:
Device + OS:
Notes:
```

---

### B6 — "Clear history" does not survive a restart

**Severity if confirmed:** Low-to-medium — depends on intended semantics.

**Hypothesis.** `clearHistory()` calls `conversation.clearHistory(delete = false)` and
empties the UI list. It issues no daemon call. For a swarm conversation the history
lives in the git-backed swarm and will be re-read on next load.

This may well be *intended* ("clear local view only"). The test exists to pin down
which it is, because the menu label promises deletion.

**Code evidence.** `ui/viewmodel/ChatViewModel.kt:689`.

**Repro.**
1. In a conversation with 10+ messages, use the overflow menu → Clear history.
2. Confirm the list empties.
3. Force-stop the app (`adb shell am force-stop <applicationId>`) and reopen it.
4. Open the same conversation.

**Expected (if "clear" means clear).** The conversation is still empty.

**Suspected actual.** All messages are back.

**Follow-up regardless of verdict.** Note whether the peer device is affected at all
(it should not be), and whether the menu wording matches the observed behaviour. If
history returns, the finding is a **labelling** bug even if the code is behaving as
designed.

```
VERDICT:
Build/commit:
Device + OS:
Notes:
```

---

## 2. Conversation list

### B7 — The unread-count badge can never appear

**Severity if confirmed:** Medium — a whole feature is inert.

**Hypothesis.** `buildConversationItems()` hardcodes `unreadCount = 0` for every row.
`HomeScreen` renders `JamiBadge(count = conversation.unreadCount)` behind an
`if (conversation.unreadCount > 0)` guard. The guard can therefore never be true and
`JamiBadge` is dead code. Unread state is conveyed *only* by bold styling, driven by
the separate `isRead` field.

**Code evidence.**
- `ui/viewmodel/ConversationsViewModel.kt:524` — `unreadCount = 0,`
- `ui/screens/HomeScreen.kt:719-721` — the guarded badge.

**Repro.**
1. Ensure A is on the conversation list screen (not inside the conversation).
2. From B, send five messages in a row to A.
3. Look at A's conversation row for B.

**Expected (no bug).** A numeric badge reading `5`.

**Suspected actual.** No badge at all. The row title goes bold, and nothing else changes.

**Note.** This is a near-certain CONFIRMED from code reading alone; the test exists to
document the user-visible symptom and to check whether bold-only is considered
acceptable parity with `jami-android-client`'s `SmartListViewHolder`.

```
VERDICT:
Build/commit:
Device + OS:
Notes:
```

---

### B8 — A brand-new conversation sorts to the bottom of the list

**Severity if confirmed:** Medium — new contacts are hard to find on a busy list.

**Hypothesis.** Rows are sorted `pinned desc, timestamp desc`, where
`timestamp = lastEvent?.timestamp ?: 0L`. A conversation with no interactions yet gets
`0` and therefore sorts *last*, below years-old conversations. A newly added contact is
exactly this case.

**Code evidence.**
- `ui/viewmodel/ConversationsViewModel.kt:507` — `lastEvent?.timestamp ?: 0L`
- `ui/viewmodel/ConversationsViewModel.kt:536` — `compareByDescending { it.isPinned }.thenByDescending { it.timestamp }`

**Repro.**
1. Use an account A with at least 8 existing conversations spread over time.
2. Add a new contact C (a third account or a known public Jami id).
3. Return to the conversation list without sending anything.

**Expected (no bug).** The new conversation with C appears at or near the top.

**Suspected actual.** It appears at the very bottom, below the oldest conversation.

```
VERDICT:
Build/commit:
Device + OS:
Notes:
```

---

### B9 — Searching the conversation list corrupts the cached list

**Severity if confirmed:** Low — transient, self-corrects on next reload.

**Hypothesis.** `loadConversations()` applies the search query while building rows,
then assigns the **filtered** result to `cachedConversations`. That cache is what
`setFilter()` and the presence-update handler operate on. So after a search, switching
the All/Unread/Groups filter re-filters an already-truncated list.

**Code evidence.**
- `ui/viewmodel/ConversationsViewModel.kt:308` — `buildConversationItems(accountId, query)`
- `ui/viewmodel/ConversationsViewModel.kt:328` — `cachedConversations = syncedConversations`
- `ui/viewmodel/ConversationsViewModel.kt:372` — `setFilter` reads `cachedConversations`.

**Repro.**
1. On a list with 8+ conversations, tap search and type a query matching exactly one.
2. Confirm the list narrows to one row.
3. Clear the search field text but **do not** trigger a reload (do not background the app).
4. Immediately tap the **Groups** filter chip, then back to **All**.

**Expected (no bug).** **All** shows the full list again.

**Suspected actual.** **All** shows only the single search-matched row until something
else forces a reload (an incoming message, an account event, backgrounding).

**Caveat.** `search("")` calls `loadConversations()`, so clearing the field may itself
repair the cache before you can observe the fault. If so, try step 4 *while the query
text is still present* and check whether **Groups** correctly shows groups that were
excluded by the search.

```
VERDICT:
Build/commit:
Device + OS:
Notes:
```

---

## 3. Notifications

This is the strongest cluster in the scan: two user-facing toggles that appear to have
no effect at all on Android 8.0+, which is effectively the entire install base
(min SDK is 24, but API 26 is where channels took over).

### B10 — The "Notification sound" toggle does nothing

**Severity if confirmed:** Medium — a settings toggle that lies to the user.

**Hypothesis.** Two independent failures stack here.

First, `NotificationGuard.getSoundUri()` returns the custom URI only when
`soundEnabled && soundUri.isNotEmpty()`, and `null` otherwise. It returns `null`
for *both* "sound disabled" and "no custom sound chosen" — and `null` is the
Android idiom for **use the default sound**. Disabling sound therefore requests the
default sound.

Second, and decisively: `applySoundAndVibration()` calls
`builder.setSound(...)` / `builder.setVibrate(...)`, both of which Android **ignores**
on API 26+. Sound and vibration come from the `NotificationChannel`, and every channel
in `createNotificationChannels()` is created with the default sound and
`enableVibration(true)` hardcoded. Nothing rebuilds those channels when the setting
changes — only `CHANNEL_CALLS` is ever recreated, and only for the ringtone URI.

**Code evidence.**
- `services/NotificationGuard.kt:103` — `getSoundUri()` collapses two cases to `null`.
- `androidMain/.../AndroidNotificationService.kt:753-766` — `applySoundAndVibration`.
- `androidMain/.../AndroidNotificationService.kt:118-167` — channels hardcode
  `enableVibration(true)` and default sound.
- `androidMain/.../AndroidNotificationService.kt:177` — `refreshCallsChannel()`
  recreates only `CHANNEL_CALLS`, only for the ringtone.

**Repro.**
1. On A: **Settings → Notifications → Notification sound → OFF**.
2. Put A's app in the background (notifications are suppressed for a visible chat).
3. From B, send a message.

**Expected (no bug).** The notification appears silently.

**Suspected actual.** The notification plays the default sound.

**Confirmatory check.** On A, go to *Android* system settings → Apps → Jami →
Notifications → the **Messages** channel. If the OS shows a sound assigned and
vibration on regardless of the in-app toggle, that independently confirms the
channel-level cause.

```
VERDICT:
Build/commit:
Device + OS:
Notes:
```

---

### B11 — The "Vibration" toggle does nothing

**Severity if confirmed:** Medium.

Same root cause as B10: `builder.setVibrate()` is inert on API 26+, and all channels
are created with `enableVibration(true)`.

**Repro.** As B10, but toggle **Vibration → OFF** and observe whether the device
still vibrates on an incoming message. Test with the device off silent mode.

**Expected (no bug).** No vibration.

**Suspected actual.** It vibrates.

**Note.** B10 and B11 should be recorded separately even though they share a cause —
if one is CONFIRMED and the other REJECTED, the shared-cause theory is wrong and the
fix is narrower than it looks.

```
VERDICT:
Build/commit:
Device + OS:
Notes:
```

---

### B12 — Quiet hours suppresses incoming call notifications

**Severity if confirmed:** High — silently missed calls.

**Hypothesis.** `shouldShowCallNotification()` includes `!isInQuietHours(settings)` in
its conjunction, exactly as the message and request variants do. Quiet hours is
normally understood to silence *messages*, not to hide incoming calls entirely. If
this fires, A receives no incoming-call UI at all during the configured window.

**Code evidence.** `services/NotificationGuard.kt:44-50` — `shouldShowCallNotification`
gates on `!isInQuietHours`.

**Repro.**
1. On A: **Settings → Notifications → Quiet hours → ON**, and set the window to
   bracket the current time (e.g. start = one hour ago, end = one hour from now).
   The wraparound logic at `NotificationGuard.kt:80` handles both orderings, so a
   non-wrapping window is the cleaner test.
2. Background A's app, or lock the screen.
3. From B, place a call to A.

**Expected (no bug).** A rings / shows the incoming-call notification. Quiet hours
should affect messages only.

**Suspected actual.** Nothing appears on A. B hears ringing until timeout. Logcat on
A shows the call arriving at `CallService` but no notification posted.

**Second half of the test.** Turn Quiet hours OFF and repeat step 3 immediately. If
the call now shows, the causal link is established and this is CONFIRMED.

**Escalation.** If CONFIRMED, also check whether an in-app full-screen call UI still
appears when A's app is in the *foreground* — that determines whether calls are
merely un-notified or completely unreachable.

```
VERDICT:
Build/commit:
Device + OS:
Notes:
```

---

## 4. Settings that appear to be stored but never read

Each of these was found by grepping every reference to the settings field across all
source sets. In each case the only references are: the model definition, the
repository writer, and the ViewModel/Screen that renders the control. Nothing consumes
the value. The tests below confirm the user-visible consequence.

Treat these as one cluster — if the first two confirm, the rest almost certainly do.

### B13 — "Auto-download on Wi-Fi / mobile data" has no effect

**Severity if confirmed:** Medium — unexpected mobile-data usage.

**Hypothesis.** `autoDownloadWifi` and `autoDownloadMobile` have zero consumers outside
the settings screen. The actual auto-accept decision in `ConversationFacade` consults
only `maxAutoAcceptSize` and never checks the network type. So files auto-download on
metered connections regardless of the toggle.

**Code evidence.**
- `services/ConversationFacade.kt:1728-1737` — auto-accept branch, size check only.
- `services/ConversationFacade.kt:1740-1747` — the race branch, which additionally
  accepts when `totalSize == 0L`, bypassing the size limit entirely.
- `model/settings/SettingsModels.kt:183,185` and
  `repository/SettingsRepository.kt:431-435` — written, never read.

**Repro.**
1. On A: **Settings → File transfers**. Set auto-download **Wi-Fi = ON**,
   **mobile data = OFF**. Set max auto-accept size to 100 MB.
2. Disable Wi-Fi on A; leave mobile data on. Confirm A is on cellular.
3. From B, send a ~5 MB file.
4. Watch A's chat row for that transfer.

**Expected (no bug).** The transfer sits at "awaiting" with a manual Download button.

**Suspected actual.** It downloads immediately without asking.

**Second case — the size bypass.** With max auto-accept set to **5 MB**, send a 50 MB
file from B while A's app is cold-starting (kill A's app, send, then launch A). If the
`transfer == null` race branch is hit, the oversized file auto-downloads anyway. This
is timing-dependent — three attempts; if it never reproduces, mark that sub-case
INCONCLUSIVE rather than REJECTED.

```
VERDICT (network-type):
VERDICT (size bypass):
Build/commit:
Device + OS:
Notes:
```

---

### B14 — "Block unknown contacts" has no effect

**Severity if confirmed:** Medium — a privacy control that does not work.

**Hypothesis.** `blockUnknownContacts` is written by
`SettingsRepository.updateBlockUnknownContacts` and read back only into
`AppSettingsState`. No service consults it. Incoming trust requests and messages from
non-contacts are handled identically whether it is on or off.

**Code evidence.** `model/settings/SettingsModels.kt:114`,
`repository/SettingsRepository.kt:223-224`,
`ui/viewmodel/AppSettingsViewModel.kt:222` — the complete set of references.

**Repro.**
1. On A: **Settings → Privacy → Block unknown contacts → ON**.
2. From a third account C that A has never contacted, send A a contact request and a
   message.

**Expected (no bug).** A receives nothing, or the request is auto-declined.

**Suspected actual.** A receives the request and the notification exactly as if the
setting were off.

```
VERDICT:
Build/commit:
Device + OS:
Notes:
```

---

### B15 — Conversation sort order (Alphabetical / Unread first) has no effect

**Severity if confirmed:** Low.

**Hypothesis.** `ConversationSort` is stored and rendered as a three-way choice in
`AppSettingsScreen`, but `ConversationsViewModel` sorts with a hardcoded
`pinned desc, timestamp desc` comparator and never reads the setting.

**Code evidence.** `ui/screens/AppSettingsScreen.kt:155-180` (the control) versus
`ui/viewmodel/ConversationsViewModel.kt:536` (the hardcoded comparator). No reference
to `conversationSort` exists in any ViewModel other than `AppSettingsViewModel`.

**Repro.**
1. Note the current order of A's conversation list.
2. **Settings → Appearance → Conversation sort → Alphabetical**.
3. Return to the conversation list.

**Expected (no bug).** The list re-sorts by display name.

**Suspected actual.** The order is unchanged (still most-recent-first, pinned on top).
Repeat with **Unread first** for the same result.

```
VERDICT:
Build/commit:
Device + OS:
Notes:
```

---

### B16 — "Compact mode" has no effect

**Severity if confirmed:** Low.

**Hypothesis.** `compactMode` is stored in `UiSettings` and surfaced as a toggle, but
no composable reads it. Row heights and padding are fixed.

**Code evidence.** `model/settings/SettingsModels.kt:53`,
`repository/SettingsRepository.kt:186-187`,
`ui/viewmodel/AppSettingsViewModel.kt:197` — the complete set of references. No screen
consumes it.

**Repro.** Toggle **Settings → Appearance → Compact mode** and compare the conversation
list and chat screen before and after. Take screenshots to compare row heights precisely.

**Expected (no bug).** Visibly tighter rows.

**Suspected actual.** Pixel-identical layout.

```
VERDICT:
Build/commit:
Device + OS:
Notes:
```

---

### B17 — Noise suppression / echo cancellation reach the daemon but are unverifiable in-app

**Severity if confirmed:** Low — this one is likely a REJECT.

Unlike B13–B16, these two *are* consumed:
`repository/SettingsRepository.kt:612-613` pushes them to
`daemonBridge.setNoiseSuppression` / `setEchoCancellation`. The scan flagged them only
because nothing outside the repository reads the model fields.

**Repro.** Toggle each setting, then place a call and confirm via logcat that the
daemon received the change. This test exists to **reject** the hypothesis, not confirm
it. Record REJECTED unless the daemon call is absent from the log.

```
VERDICT:
Build/commit:
Device + OS:
Notes:
```

---

### B18 — Read receipts are controlled from two places that do not agree

**Severity if confirmed:** Low — user confusion, contradictory UI.

**Hypothesis.** There are two independent read-receipt switches:

- **App Settings → Privacy → Read receipts** → `PrivacySettings.readReceipts`,
  consulted at `services/ConversationFacade.kt:322` to decide whether to send display
  status.
- **Account → Messages settings → Read receipts** → the daemon's
  `Account.sendReadReceipt` config key
  (`ui/viewmodel/AccountSubSettingsViewModel.kt:171,274-275`).

Neither writes the other. They can be set to opposite values, and the app-level one
wins in practice while the account-level one keeps displaying its own stale value.

**Repro.**
1. On A, set **App Settings → Privacy → Read receipts = OFF**.
2. On A, set **Account → Messages → Read receipts = ON**.
3. From B, send A a message. Open it on A.
4. Check on B whether the message shows as read (double tick in the primary colour).

**Expected (no bug).** The two controls are the same control, or one visibly follows
the other.

**Suspected actual.** They hold contradictory values simultaneously, and B sees **no**
read receipt (the app-level OFF wins) despite the account screen saying ON.

**Also record.** Whether toggling either one updates the other on returning to the
screen. If they are genuinely independent by design, this becomes a labelling issue
rather than a logic bug — note which reading applies.

```
VERDICT:
Build/commit:
Device + OS:
Notes:
```

---

## 5. Calls

### B19 — The speaker button is inert before a conference object exists

**Severity if confirmed:** Medium.

**Hypothesis.** `toggleSpeaker()` opens with `val conf = currentConference ?: return`.
`currentConference` is assigned only in `handleConferenceUpdate()`. Between the call
becoming answerable and the first conference update arriving, the speaker button is a
no-op that gives no feedback.

The early-return is itself a deliberate fix (the inline comment explains that updating
state without a conference desynced it permanently), so the question is purely whether
the window is long enough to be user-visible.

**Code evidence.**
- `ui/viewmodel/CallViewModel.kt:404-412` — `toggleSpeaker`, early return.
- `ui/viewmodel/CallViewModel.kt:771` — `currentConference = conf`, the only assignment.

**Repro.**
1. From A, place an audio call to B.
2. The instant the call UI appears — before B answers — tap the speaker button
   repeatedly.
3. Note whether the button's visual state changes and whether audio routing changes.
4. Have B answer. Tap speaker again.

**Expected (no bug).** The button works, or is visibly disabled, at every stage.

**Suspected actual.** During the ringing phase the button appears enabled but does
nothing (no visual change, no routing change). It starts working only after answer.

**Discriminator.** If the button is unresponsive *only* before answer and reliable
after, CONFIRMED. If it is unreliable after answer too, that is a different and worse
bug — record the detail.

```
VERDICT:
Build/commit:
Device + OS:
Notes:
```

---

### B20 — `isConference` is never reset once set

**Severity if confirmed:** Low.

**Hypothesis.** `handleConferenceUpdate()` sets `isConference = true` but nothing sets
it back to `false`. `handleCallUpdate()` does not touch the flag. If a conference
degrades back to a two-party call (the reference client demotes a conference when one
participant remains — see `doc/TODO.md` "Enhanced Call Handling"), the UI may keep
rendering conference-specific chrome.

**Code evidence.** `ui/viewmodel/CallViewModel.kt:810` sets `isConference = true`;
no counterpart assignment exists in the file.

**Repro.** Requires three devices.
1. A calls B. Answer.
2. A adds C to make a conference. Confirm the conference UI (participant grid,
   moderator controls).
3. C hangs up, leaving A and B.

**Expected (no bug).** The UI returns to the plain two-party call layout.

**Suspected actual.** Conference chrome (participant grid, layout selector, "mute all")
persists for a two-party call.

**If only two devices are available.** Mark INCONCLUSIVE — do not guess.

```
VERDICT:
Build/commit:
Device + OS:
Notes:
```

---

## 6. Localisation

### B21 — Untranslated strings in shipped UI

**Severity if confirmed:** Low, but a parity requirement per `CLAUDE.md`
("all user-visible strings via `Res.string.*` (never hardcoded)").

**Hypothesis.** A grep for hardcoded `Text("…")` in `ui/screens/` and `ui/components/`
found these literals reaching users:

| String | Location |
|---|---|
| `"Try Again"`, `"Audio Only"` | `ui/screens/VideoLossOverlay.kt:164,176` |
| `"10 min"`, `"1 hour"`, `"OK"` | `ui/screens/LocationSharingScreen.kt:190,195,284` |
| `"Remove from Call"` | `ui/components/ParticipantContextMenu.kt:111` |
| `"Cancel"`, `"Send"` (audio recording row) | `ui/screens/ChatScreen.kt:583,586` |
| `"React"` | `ui/screens/ChatScreen.kt:910` |
| `"Video recording coming soon"` | `ui/screens/ChatScreen.kt:701` |
| `"Plugins not yet supported"` | `ui/screens/ChatScreen.kt:706` |

Separately, `ChatViewModel.epochMillisToDateLabel()` produces the date separators
`"Today"` / `"Yesterday"` from hardcoded constants
(`ui/viewmodel/ChatViewModel.kt:843`), and formats other days as
`"${date.month.name…} ${day}, ${year}"` — an English month name in US date order,
regardless of locale.

**Repro.**
1. Set the device system language to German (a `values-de/` locale folder exists).
2. Walk each location in the table and record which strings remain English.
3. Open a conversation with messages spanning at least three different days —
   today, yesterday, and an older date — and check the date separators.

**Expected (no bug).** All strings localised; date separators in German with a
locale-appropriate date order.

**Suspected actual.** Every string in the table stays English, and separators read
`Today` / `Yesterday` / `September 3, 2026`.

**Note.** The last two rows (`"Video recording coming soon"`, `"Plugins not yet
supported"`) are placeholders for features `doc/TODO.md` records as deliberately
deferred. Record them, but they are lower priority than the rest.

```
VERDICT:
Build/commit:
Device + OS:
Notes:
```

---

## 7. Robustness — low-probability crashes

These are `!!` assertions on values that are nullable by declaration. Each is a
crash-on-null. They may all be unreachable in practice; the tests try to reach them.

### B22 — Non-null assertions on nullable identifiers

**Code evidence.**

| Site | Asserted value | Reachable when |
|---|---|---|
| `services/ConversationFacade.kt:679` | `conversation.id!!` | Loading history for a legacy (SIP / non-swarm) conversation whose `id` was never set |
| `services/ConversationFacade.kt:343` | `message.messageId!!` | Marking messages read in a swarm containing an interaction with no `messageId` |
| `services/ConversationFacade.kt:489` | `element.messageId!!` | Deleting a message that has no daemon-assigned id yet |
| `model/Conversation.kt:550` | `interaction.author!!` | Rebuilding a contact-event placeholder for an interaction with a null author |

**Repro — legacy conversation (`:679`).**
1. Create a SIP account on A (**Create account → SIP tab**).
2. Establish a SIP conversation and generate history.
3. Open it and scroll up to force `loadMore()` → `getConversationHistory()`.

**Repro — delete an unsent message (`:489`).**
1. Put A in airplane mode.
2. Send a message so it stays in the pending/sending state.
3. Long-press it and choose Delete.

**Expected (no bug).** No crash; graceful handling or a clear error.

**Suspected actual.** `NullPointerException` / `KotlinNullPointerException` and a
process crash. Capture the full stack trace from logcat.

**Note.** A REJECTED here means "could not reach it", which is weaker than "cannot
happen". Record exactly which paths you exercised.

```
VERDICT (:679 legacy):
VERDICT (:489 delete pending):
Build/commit:
Device + OS:
Notes:
```

---

## 8. Re-tests of previously-known items

These are already recorded in `doc/TODO.md` or `doc/KNOWN-ISSUES.md`. They are listed
here only because adjacent code has changed and the recorded status may be stale.
Confirm the status line is still accurate; do not re-investigate from scratch.

| Item | Recorded status | What to check |
|---|---|---|
| Proximity sensor during calls | Not implemented (`doc/TODO.md`) | `proximityEnabled` still has zero consumers in the scan. Confirm the screen stays on during an audio call with the phone at your ear. |
| Retry failed file transfer | Missing (`doc/TODO.md`) | Fail a transfer (airplane mode mid-send) and check for a retry affordance. |
| Long-press → share a received file | Missing (`doc/TODO.md`) | Long-press a received image; confirm Copy exists but Share does not. |
| Battery optimisation exemption prompt | Missing (`doc/TODO.md`) | Confirm the app never prompts, then check whether messages stop arriving after ~30 min of Doze. |
| Send into a not-yet-live 1:1 swarm | F1 verified, F4 reproduced (`doc/stabilization-findings-2026-09-04.md`) | **Run B1b and B3 first.** They may share a root cause with F4 — if so, do not double-count them. |

---

## Reporting

For each CONFIRMED verdict, capture:

1. The filled-in verdict box.
2. The logcat excerpt spanning the failure, including the discriminator log line where
   the test names one.
3. A screen recording for anything visual (B7, B8, B16, B20, B21).
4. Build commit (`git rev-parse --short HEAD`) and device/OS.

**Triage note.** B1, B12, and B3 are the entries that can cause a user to lose a
message or miss a call. If any of the three confirms, treat it as ahead of everything
else in this document regardless of how many low-severity items also confirm.

If a test yields REJECTED, record it anyway — a rejected hypothesis removes a
suspicion from the source and is worth as much as a confirmation.
