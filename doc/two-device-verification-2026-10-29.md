# Two-device verification round, 2026-10-29 — what was proved and what is still open

> Pixel 7a (`37281JEHN03065`), account **unitedhumans** `3f3dad8b328eb995`, Jami id
> `66d57f55227639b58fdcc6d74ec090446ffa9bc3`.
> Pixel 2 (`FA7AJ1A06417`), account `965287c3eb08e39e`, Jami id
> `d9f8c7f0bf768178542d402c6ae233072bfff6df`, **registered name `peace4us`**.
>
> Note for anyone reading older notes: `doc/status-note-open-ends.md` calls the Pixel 2
> "marco100 d9f8c7f0…". The id is right, the name is not — `d9f8c7f0…` resolves to **peace4us**.
> `marco100` is `35be7fd1a4333e484812dc73f01836c887bb430d`, a contact with no device attached
> here, which is why every call to it returns 480. Two of this round's attempts were wasted on
> that confusion.

## Verified

### 1. Clearing a status note (was open end 2 of `status-note-open-ends.md`)

The `PresenceNote.CLEARED` single-space path works against a live peer.

- 7a set the note: `publishPresence: accountId='3f3dad8b328eb995' status=true note='Clearing'`
  and `Saved settings [KMP.Settings.Presence]`.
- Pixel 2, chat open, header rendered **"unitedhumans" / "Clearing"** — ~100 ms later.
- 7a tapped ✕: `publishPresence: … note=' '` — a single space, exactly as designed, because an
  empty note cannot match the peer daemon's `[\w\s]+`.
- Pixel 2 received `onPresenceUpdate … status=2` and the header dropped back to **"unitedhumans"**
  alone, live, with the chat still open.

**This open end is closed.**

### 2. Conference from a participant list (mini-plan calling F10)

- 7a selected peace4us and marco100; the call button appeared only at the second selection.
- `createConferenceWith: [d9f8c7f0…,3f3dad8b328eb995, 35be7fd1…,3f3dad8b328eb995]` — both
  entries in the `"<peer uri>,<account id>"` form the daemon parses.
- marco100 failed immediately (480, no device). peace4us went **CONNECTING → RINGING**, and the
  **Pixel 2 rang**: `onCallStateChanged … state=INCOMING` plus
  `updateAudioState: status=RINGING incoming=false`.
- The 7a showed the **outgoing** call UI — "Calling…" with only an end-call button, not
  accept/decline — so a call the daemon placed on our behalf is not mistaken for an incoming one.
- Navigation opened the call screen on the first of our calls, as designed.

**One thing this did not prove, and it is worth writing down.** The call reached the client as
**CONNECTING first, then RINGING**. `CallService.onCallStateChanged` creates the `Call` on the
first state it sees, so it was created at CONNECTING, where the direction is OUTGOING regardless —
the `isOurConferenceDial` branch never had to decide anything. The guard is unit-tested
(`InstantConferenceTest`) and correct, but on this daemon the hazard it defends against may not
arise at all, because RINGING is never the first state for an outgoing call. Do not treat it as
load-bearing without a case where RINGING arrives first.

The daemon's two-successful-calls rule also showed itself: one peer reachable out of two, so no
conference was attached — just the one 1:1 call, exactly as `manager.cpp:1557` says.

## Still open

### 3. Microphone level values (mini-plan notifications F9)

**Not verified, and the reason is specific:** no call ever connected. Three attempts; the peer
rang for the full 30 s and timed out (`state=HUNGUP code=487`) because nobody answered the Pixel 2
in time.

What *is* confirmed on the device:

- `setMicrophoneMeterEnabled: true` on entering the call screen, `false` on leaving it, every
  time — the `DisposableEffect` lifecycle holds, including for an outgoing call that fails.
- The JNI call returns cleanly, so `JamiService.setAudioMeterState` is bound correctly.

What is not: that `audioMeter` callbacks arrive and carry non-zero levels. The daemon only
captures while a call is connected, so this needs **someone to answer the phone** — it cannot be
driven from adb alone, since the incoming call on the Pixel 2 appears as a notification that
timed out before it could be tapped. A one-line log was added for this
(`audioMeter: first sample from the capture buffer, level=…`, once per metering session): if it
appears during a connected call, the feature is proved end to end.

## Found while verifying: a crash with any group conversation (fixed)

Not one of the three checks, and not from this week's work. Creating one 3-member group made the
**new-conversation screen crash-loop**:

```
java.lang.IllegalStateException: getContact() called for group conversation of size 3
  at net.jami.model.Conversation.getContact(Conversation.kt:215)
  at net.jami.ui.viewmodel.NewConversationViewModel.loadAllConversations(NewConversationViewModel.kt:129)
```

`Conversation.contact` throws by design for a group — it is a guard for code that already knows it
holds a 1:1 conversation. Six places walked **all** conversations and read it anyway:
`NewConversationViewModel` (twice), `ConversationsViewModel` (twice), `ContactDetailsViewModel`
and `PendingRequestsViewModel`. `ConversationsViewModel:506` already had the right guard
(`if (isGroup) null else …`), so the pattern was known and simply not applied everywhere.

Three of those sites wrote `conv.contact ?: continue` or `conv.contact?.uri`, which reads as safe
and is not: the getter **raises**, so neither `?:` nor `?.` catches anything.

Fixed with `Conversation.contactOrNull`, which returns null for a group, used at all six sites;
`contact` keeps its throw. `GroupConversationContactTest` covers it, including the mixed-list walk
that crashed. Verified on the device: the new-conversation screen opens with the group present and
no `FATAL EXCEPTION` in logcat.

**Blast radius, which is wider than the screen I hit.** `getPending()` holds group invitations, so
`ConversationsViewModel:293` and `PendingRequestsViewModel:85` would have taken down the **home
screen** for anyone invited to a group. Nothing in the test suite had a group in a conversation
list, which is why 824 passing tests said nothing about it.

### The seventh site, found the next day (2026-10-30)

The sweep above covered the places that walk a conversation *list*. One more had the same read in
a different shape: `ChatViewModel.loadConversation` reads `contact` for the **open** conversation,
and that whole block sits in its own `try/catch(Exception)`. So opening a group chat was never a
crash — it was a **silent abort** of the rest of the method, leaving the header on default state.

It was parked here as "needs a device to characterise, do not guess", and that was the right call:
it surfaced the moment a group was created through the new picker, showing a chat headed `?`. Now
fixed via `Conversation.title`, and confirmed on the Pixel 7a:
`loadConversation: … contact=null peerUri= isGroup=true` runs through to the end.

`Account.getByContact` (`Account.kt:216`/`:221`) was the other unguarded walk identified that day,
reached from `ConversationFacade:1158` in the incoming-call path. **Still open** — the picker work
took priority.
