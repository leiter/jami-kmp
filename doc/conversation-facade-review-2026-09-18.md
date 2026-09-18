# ConversationFacade review — 2026-09-18

Comparison of `shared/src/commonMain/kotlin/net/jami/services/ConversationFacade.kt` against
the reference `jami-client-android` (libjamiclient). The reference splits this logic: its
`ConversationFacade.kt` holds the UI-facing operations, while the daemon conversation signal
handlers live in its `AccountService.kt` (`loadAccount`, `conversationReadyNow`,
`swarmMessageReceivedNow`, `dataTransferEvent`, …). jami-kmp merges both into one facade.

Findings come from reading the code. None was reproduced on a device at review time.
Line numbers refer to the file at commit `f1e1f3e`.

Status legend: ⬜ open · ✅ fixed

## High

### 1. ✅ Data-transfer event codes mapped with a wrong, shifted table
`onDataTransferEvent` (~1702-1712) used its own table `0 -> CREATED, 1 -> AWAITING_HOST, …`.
The daemon's `DataTransferEventCode` (`daemon/src/jami/datatransfer_interface.h`) is
`invalid=0, created=1, unsupported=2, wait_peer_acceptance=3, wait_host_acceptance=4,
ongoing=5, finished=6, closed_by_host=7, closed_by_peer=8, invalid_pathname=9,
unjoinable_peer=10, timeout_expired=11`, and the bridges forward the raw value. The effect:
`ongoing` showed as `TRANSFER_ERROR`, `finished` as `TRANSFER_UNJOINABLE_PEER`,
`wait_host_acceptance` as `TRANSFER_FINISHED`, and `created` as `AWAITING_HOST`, which wrongly
took the auto-accept path. The correct mapping already existed as
`Interaction.TransferStatus.fromIntFile()`.

### 2. ✅ Incoming files are never auto-accepted when their message arrives
In the reference, a new swarm file message goes through
`dataTransfersProcessor → ConversationFacade.handleDataTransferEvent`, which auto-accepts a
`FILE_AVAILABLE` transfer within the size limit. jami-kmp's `onMessageReceived` only added the
message to the model. Auto-accept lived solely in `onDataTransferEvent`, and #1 broke that too.

### 3. ✅ `onConversationReady` registers the conversation before its members and mode are known
The handler called `account.conversationStarted()` immediately with a default `OneToOne` mode,
then loaded the info and members asynchronously. `conversationStarted()` is what links a 1:1 swarm
to its contact (`contact.setConversationUri`) and drops the contact-keyed placeholder. With
`conversation.contact` still null at that point, that link never happened. The reference
(`conversationReadyNow`) adds the members first ("Making sure to add contacts before changing the
mode"), then calls `conversationStarted(conversation, mode)`, and primes the preview with
`loadMore(conversation, 8)`. jami-kmp did not load a preview for newly ready conversations.

### 4. ✅ No read receipt for messages that arrive while the chat is open
`Conversation.addSwarmElement` marks the message as read when `isVisible`, but
`setMessageDisplayed` is only called when the chat is opened (`ChatViewModel.kt` ~288). The
reference `parseNewMessage` calls `setMessageDisplayed` for every incoming message that is
already read. As a result, the peer never sees "read" and your own other devices keep the
messages unread.

*Fix:* `onMessageReceived` calls `setMessageDisplayed` for an incoming message that is already
read (the chat is open), gated on `readReceipts` like `readMessages` (see #6).

### 5. ✅ Member events only ever add members
`onConversationMemberEvent` re-adds any members that are missing. The reference maps each event to a role:
Add/Join/Remove/Block/Unblock map to INVITED, MEMBER, LEFT and BLOCKED, and Remove and Block call
`conversation.removeContact`. Members who left, were removed or were blocked stay listed with
their old role.

*Fix:* the handler maps the event codes to roles as the reference does.
`Conversation.addContact` no longer duplicates an existing member (it only updates the role), and
`Conversation.removeContact` now follows the reference: a group member is dropped only on LEFT,
and a BLOCKED member stays listed with that role.

## Medium

### 6. ✅ Read receipts off also means no read sync between your own devices
With `privacySettings.readReceipts` off, `readMessages` skips `setMessageDisplayed` entirely. The
reference always calls it and lets the daemon decide whether to send a receipt to the peer, based
on the account's read-receipt setting. Consider setting that daemon account setting instead, and
always calling `setMessageDisplayed`.

*Fix:* matches the reference. The facade always calls `setMessageDisplayed`, and the per-account
`Account.sendReadReceipt` (Account → Messages) decides whether the peer gets a receipt. The extra
app-wide toggle in App Settings → Privacy was removed from the screen. Its stored value
(`privacySettings.readReceipts`) and the view-model toggle are left in place, unused.

### 7. ✅ Message updates, status changes and typing are not applied to the conversation model
`onMessageUpdated`, `onAccountMessageStatusChanged` and `onComposingStatusChanged` only emit
events. The reference also updates the model: `conversation.updateSwarmMessage`,
`updateSwarmInteraction` and `account.composingStatusChanged`. `ChatViewModel` patches its own
UI state, but the model stays stale. List previews and a re-opened chat can therefore show text
from before an edit and old sent/read ticks.

*Fix:* ported `Conversation.updateSwarmMessage` and `updateSwarmInteraction` from the reference.
`onMessageUpdated` and `onAccountMessageStatusChanged` now apply the change to the model before
emitting their event. `onComposingStatusChanged` calls `conversation.composingStatusChanged`.

### 8. ✅ `loadSmartlist` re-runs far more often than the reference
The reference loads once per account and caches the result (`account.historyLoader`). jami-kmp
reloads fully:
- on account selection;
- on every `AccountsChanged`;
- on every `REGISTERED`;
- when the network comes back;
- on every `getAccountWithSmartlist` (`startConversation`, `getLoadedContact`,
  `createConversation`).

Each reload:
- calls `subscribeBuddy(…, true)` again for every contact. The daemon reference-counts these
  (`presence_manager.cpp` `refCount++`), so the count only grows and one unsubscribe never
  stops tracking;
- repeats `lookupAddress` for every contact without a username;
- re-primes `loadMore(8)` for every conversation;
- never removes conversations the daemon no longer lists.

*Fix:* the triggers stay (they pick up conversations pushed by peers), but the side effects now
run once:
- presence subscription once per contact;
- `lookupAddress` once per unresolved contact (the `REGISTERED` handler still retries);
- the `loadMore(8)` preview once per conversation, shared with `onConversationReady`.

`getAccountWithSmartlist` loads only if the account hasn't been loaded yet, like the reference's
cached `historyLoader`. Swarms the daemon no longer lists are removed, and `ConversationRemoved`
is emitted for them. Syncing and Request entries are kept, because they can be local placeholders.

### 9. ✅ The conversation list screen isn't notified when a reload finishes
`ConversationsViewModel` pulls `account.getConversations()` in response to a few events.
`loadSmartlist` publishes to `_conversationList`, which no screen reads, and emits no event when
it's done. A reload triggered by `REGISTERED` or a network change doesn't reach the list, and the
initial load races `AccountsChanged`. `ConversationEvent.MessagesRead` is emitted but
`ConversationsViewModel` ignores it, so unread items stay bold.

*Fix:* `loadSmartlist` emits a new `ConversationEvent.ConversationsLoaded` when it finishes.
`ConversationsViewModel` reloads on it and on `MessagesRead`. New events emitted from inside
`loadSmartlist` are launched rather than awaited. `_conversationEvents` is unbuffered, and
`loadSmartlist` can run inside a subscriber on the send path, which would then wait on itself.

## Low — stubs, unused code, small differences

Unused by the UI at review time, but they look implemented:
- `setConversationPreferences` saves locally only. The reference sends swarm preferences to the
  daemon so they sync across devices.
- `clearHistory` / `clearAllHistory`: the in-memory clearing is empty
  (`clearHistoryForAccount {}`).
- Legacy `sendTextMessage` never sends to the daemon (`sendAccountTextMessage` is commented out).
- SIP branch of `loadSmartlist` reads the history and does nothing with it.
- `getSearchResults`, `getFullConversationList` and `getConversationList` filter by URI only,
  return one-time snapshots that don't react to changes, and have no public-directory results.
- `getLoadedContact` uses the private `getContactFromCache()`, which returns a new `Contact(uri)`
  instead of the cached contact.

Other:
- `onConfStateChange` only logs. The reference clears the call notification when a conference is
  torn down.
- `findOrCreateConference` returns null for `OVER` before looking up an existing conference, so
  cleanup and call history are skipped if `OVER` arrives without a preceding `HUNGUP`.
- `acceptRequest` clears history in a background coroutine but sets the mode immediately (race).
- `sendFile` copies the file where the reference moves it, leaving a duplicate.
- `syncState` is a single global flow, so multiple accounts overwrite each other's status.

## Intentional differences (fine)
- Load gate and per-account single-flight mutex on `loadSmartlist` (F1 fix,
  `doc/plan_daemon_stability_sync.md`).
- `ensureSwarm` self-heal. The reference drops events for unknown conversations.
- Typing indicators gated by the privacy setting.
- `Mode.Syncing` handling for conversations still cloning; sorting by `lastEvent`.
