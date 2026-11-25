# Findings protocol — iOS-first fix round (from 2026-11-18)

Each finding: symptom, iOS status, Android status, root cause, fix, verification, device
steps still owed. iOS is fixed first; Android is checked for the same issue and changed only
where it has the gap too. "Before" / "after" refer to runs on the two-simulator harness
(`scripts/ios-two-sim`, JamiTest-A = Alice, JamiTest-B = Bob, real Jami network).

Source of the findings: the user's device test of TestFlight build 46 (iPhone ↔ Pixel), plus the
open items in `doc/TODO.md`. Plan: `~/.claude/plans/i-want-to-create-robust-glade.md`.

| # | Finding | iOS | Android | Status |
|---|---|---|---|---|
| F1 | Edit becomes a quote | **fixed** `36d8b9c`, verified on sims | not affected | done (iOS) |
| F2 | Own reaction becomes a quote; peer reactions missing | **fixed** `36d8b9c`, verified on sims | not affected | done (iOS) |
| F3 | Message order / parent id wrong | **fixed** `36d8b9c`, verified on sims | not affected | done (iOS) |
| F4 | Member roles and events mistranslated (1:1 leave not seen) | **fixed** `36d8b9c` + `3f2292b`, verified on sims | `3f2292b` shared (details-screen leave) | done (iOS) |
| F5 | No sync after background, until restart | **fixed** `35c9397`, verified on sims | not affected (foreground service) | device test |
| F6 | Unread badge never clears | **fixed** `36d8b9c` + `6928afb` (restart) | shared restart fix also applies | verify restart |
| F7 | "Mark as read" in a notification has no effect | **fixed** `698c86d` | **fixed** `698c86d` | device test |
| F8 | Notification shows hash and app icon | **fixed** (names) `698c86d` | shared change | device test |
| F9 | Notification sound inaudible | code was fine; logging + small fixes `698c86d` | — | check iPhone settings |
| F10 | vCard (profile) sync | to check | works live (user) | open |
| F11 | Re-adding a contact hangs | **not reproduced** on sims (works) | to retest on the Pixels | no change |
| F12 | "Block unknown" refuses peers we sync with | not testable on sims (needs calls) | to retest on the Pixels | no change |
| F13 | Self-conversation / call to own account possible | **guarded** `6dde67e` | **guarded** `6dde67e` | done |
| F14 | Refused incoming call still reaches Telecom / CallKit | **fixed** `1c66e38` (shared) | **fixed** `1c66e38`; outgoing Telecom extras too | device test |
| F15 | Account export / import fails (user, 2026-11-18) | **fixed** `36d8b9c` | link-device import fixed too | device test |
| F17 | Edit: composer not focused, not cleared after sending | **fixed** `84ffbd9` | same shared code, fixed | done |
| F19 | Deleted message shown as an empty "edited" bubble | **fixed** `c431e02` | same shared code | done |
| F20 | Details-screen leave / member actions passed `swarm:<id>` to the daemon | **fixed** `3f2292b`, verified | same shared code, fixed | done |
| F18 | UPnP flapping after the addAccount fix (regression, caught on sims) | **fixed** `eb9cb44` | not affected | done |
| F16 | No navigation to onboarding after deleting the account (user, 2026-11-18) | **fixed** `36d8b9c` | shared race fixed | device test |

Calls: the user reports calls working on both platforms (2026-11-18), so the call items stay
out of this round except F14.

---

## F15 — account export / import (iOS)

**Symptom (user, build 46):** export and import fail on iOS; both work on Android.

**Root causes (code, 2026-11-18):**
- **iOS `addAccount` dropped the details map.** `DaemonBridge.ios.kt` called the wrapper's
  `createAccountWithDisplayName:password:`, which rebuilt a fresh RING map from the display
  name and password alone. Lost: `Account.archivePath` (archive import) and
  `Account.archiveURL = "jami-auth"` (link to an existing device), SIP type, template values.
  Each import or link attempt therefore silently created a **new identity** instead.
  Fix: wrapper `addAccount:` passes the caller's map to `libjami::addAccount` unchanged.
- **Export ignored the scheme.** The wrapper passed `""` to `libjami::exportToFile`; with a
  password and no scheme the daemon never decrypts the archive, so every password-protected
  export failed. Same in `registerName`. Fix: `scheme` passed through.
- **`DeviceAuthStateChanged` was wired on neither platform.** On the device being linked the
  daemon emits only that signal (`archive_account_manager.cpp`); `LinkDeviceImportViewModel`
  listened to `AddDeviceStateChanged`, the exporting side's signal, so no token or QR ever
  appeared. Fix: signal wired end to end (wrapper, iOS delegate, Android override,
  `DaemonCallbacks`, `AccountEvent.DeviceAuthStateChanged`); the ViewModel subscribes before
  `addAccount` so an early `TOKEN_AVAILABLE` is not lost. **Android: shared change** — the
  link-from-another-device import could not have worked there either; to be retested on a Pixel.
- `sendRegister` mapped to `setAccountActive` on iOS (does not persist `Account.enable`); now
  `libjami::sendRegister`. iOS `lookupName`/`lookupAddress` always reported failure; now report
  the request as issued (the answer arrives through `RegisteredNameFound`, as before).

**Stray accounts:** every earlier import / link attempt on the iPhone left an extra account
behind. They need deleting by hand (Settings → account list) — see F16.

## F16 — no navigation to onboarding after deleting the account (iOS)

**Root causes:**
- (a) Probably the stray accounts of F15: `AppViewModel` navigates to onboarding only when the
  account list is empty, and a hidden extra account kept it non-empty.
- (b) A lost-update race in `AppViewModel` (shared): the accounts collector and the
  `AccountRemoved` collector both wrote `_appState`; the collector could overwrite the
  `NoAccounts` the removal had set with the `HasAccounts` it had read a moment before. Fix:
  `AccountService.lastAccountRemovedByUser` is set before the empty list is published, and the
  single collector decides. Test: `AppViewModelTest.removingTheLastAccountGoesToNoAccounts`.
  Android: shared change, harmless there.

## Baseline run (before any fix) — 2026-11-18, code at `0f339f3`

Two simulators, fresh accounts (Alice `d97d48db…`, Bob `17f55b4d…`). Screens in
`build/ios-two-sim/baseline-*.png` (not committed).

- onboard, invite, accept, chat: **pass** (after harness timing fixes, see below).
- **F1 reproduced:** Alice's edit shows as a new bubble quoting "You / Hello from Alice";
  the original is unchanged.
- **F2 reproduced:** Bob's 👍 on his own message arrives at Alice as a new bubble quoting
  "Bob / Reply from Bob" with 👍 as its text. The harness `react` phase passed regardless —
  its assertion only looks for a 👍 anywhere (to tighten).
- **F3/F6 reproduced:** with Bob on the conversation list, Alice sends "Unread one" and
  "Unread two". Bob's daemon receives both and `ConversationFacade.onMessageReceived` fires
  (a notification is posted), but the list row still shows "You: 👍" with **no unread badge**
  after 30 s — the new messages never become the conversation's last event.
- Event rows show the peer's 40-hex id ("17f55b4d… has joined the conversation") although the
  header already shows "Bob" — noted, part of F8/F10 (names).

**Harness fixes made on the way** (`scripts/ios-two-sim/flows`): the contacts prompt can come
after 10 s (wait 30 s); taps during a screen's slide-in hit the wrong element — "Accept" landed
on Decline (log: `PendingRequestsVM: discard`), and the directory result tap was lost — so the
flows now wait for the animation to end and retry the result tap once.

## After the fixes — two-simulator runs, 2026-11-18

- **Regression caught (F18):** the first run after `36d8b9c` failed at `accept` — Bob never
  got the invitation. Alice had sent it (`sendTrustRequest … payload=75 bytes`), but her
  account flapped REGISTERED/TRYING/UNREGISTERED 19 times: passing the caller's details to
  `addAccount` also passed the shared default UPnP=on, and every IGD search on the simulator
  restarted the DHT. The old wrapper and jami-client-ios both keep UPnP off; the iOS actual now
  does too (`eb9cb44`). The Mac's own connection to ns.jami.net / dhtproxy was fine.
- **F1 fixed:** the edit is applied in place with an "edited" label — no quote bubble.
  It also exposed **F17**: after "Edit" the composer had no focus (text could not be
  changed), and kept the old text after sending. Fixed in shared code (`84ffbd9`).
- **F2 fixed:** Bob's 👍 shows as a reaction chip under his message on Alice's screen.
- **F3/F6 fixed (live):** with Bob on the list, Alice's "Unread one"/"Unread two" update the
  row immediately ("Now Unread two") with a **2** badge.
- **F6 after a restart — new finding, fixed:** restarting Bob showed **3**: "Hello from Alice",
  read with the chat open, was counted again. The read position lived only in memory; it is
  now restored from the daemon's per-member `lastDisplayed` (`6928afb`, shared — Android too).

**Not testable here / owed on devices:** notification sound (F9 — check the ring/silent
switch, Focus, and Settings → Notifications → Jami → Sounds; the app now logs
`Notification settings: sound=…` once), lock-screen "Mark as read" (F7), export/import/link
(F15), delete-to-onboarding (F16).

**Android unit tests:** `android-app/src/test` does not compile on HEAD (mockk, JUnit and
koin-test are missing from that source set; 48 errors) — `NotificationActionReceiverTest` was
updated but cannot run. Recorded, not fixed in this round.

### Run 6 (07:01–07:15) — first fully green harness run

`setup onboard invite accept chat edit react delete offline`: **all pass** on `3f2292b`.
Then by hand on the same pair:
- **F5 (background):** Alice sent to the background (Settings app in front) for 35 s — the log
  shows `enterBackground`, after 16 s `setAccountActive … active: 0 shutdownConnections: 1` and
  `accounts released`. Bob sent "Sent while Alice is away"; on return `enterForeground
  (accounts released: true)` and the message arrived **4 s later, no restart**. (A simulator does
  not suspend a backgrounded app the way an iPhone does; the device test is still owed.)
- **F4 (leave):** Bob → conversation details → "Leave group". First attempt did nothing — the
  daemon got `swarm:e4edc3be…` (**F20**, shared: the details screen stored the prefixed id).
  After `3f2292b`: `removeConversation … conversationId: 807b51f1…` (bare) and Alice shows
  **"Bob has left the conversation."** within a second. Member roles now show (Admin badge).
- **F19:** a deleted message showed as an empty bubble labelled "edited" on both sides — now
  "<who> deleted a message".
- Side notes, not fixed: the details screen lists members as hex ids; "Leave group" is the
  label on a 1:1 too; the "edited" label sits below a reaction chip; event rows written before
  a peer's name arrived keep the hex id until the chat is reopened.

### Leave → re-add, same pair (07:20–07:33)

- **After Bob left the 1:1** (and both apps restarted), Bob's daemon had started a **fresh 1:1
  with Alice** by itself (`ConversationModule::removeConversation` keeps a 1:1 with a confirmed
  contact), and Alice's daemon joined it automatically. Alice's list then showed **two "Bob"
  rows**: the ended one ("Bob has left the conversation.") and the new, empty one. This follows
  from the leave design chosen on 2026-11-17 (contact kept) and is daemon behaviour, so Android
  will show the same. **Open decision for the user:** hide an ended 1:1 when a newer 1:1 with
  the same peer exists, or keep both.
- **F11 not reproduced:** Alice then deleted Bob as a contact (both rows went), searched Bob's id
  and wrote "Hello again after re-adding". It was delivered (double tick) and Bob's list showed
  it with a **1** badge within seconds — no "waiting for confirmation". Bob is left with a stale
  second "Alice" row (the conversation Alice's contact deletion erased) — same open decision.
  No code change, per the plan's rule. Android: retest on the Pixels (the 2026-11-07 hang
  predates `9d5d508`, which made addContact send the trust request).
- **F12** needs calls; out of scope on the simulators. Android retest owed.

### Parked decision — 1:1 deletion and conversation creation (2026-11-18, evening)

**Reproduced on the simulators** (fresh Alice/Bob, current build). The user sees the same on
Android and iOS devices, sometimes three rows for one peer; removing the contact is their only
way to get rid of them.

| Step | Action | Alice's repos | Bob's repos | Rows |
|---|---|---|---|---|
| 0 | invite, accept, one message | `d45b596c` 1:1 | `d45b596c` | A 1 · B 1 |
| 1 | Alice deletes the 1:1 | `87235cb9` **new** | `d45b596c` ended + `87235cb9` **auto-joined, no request** | A 1 · **B 2** |
| 2 | Bob deletes the *ended* row | — | `87235cb9` | A 1 · B 1 |
| 3 | Bob deletes the *live* one | `87235cb9` ended + `97f8b6a8` **new**, auto-joined | `97f8b6a8` | **A 2** · B 1 |

- Deleting a **live** 1:1 makes the deleter's daemon start a fresh one at once
  (`ConversationModule::removeConversation` → `handleNewConversation`); the peer, being a
  contact, joins it silently and keeps the ended copy. Deleting an **ended** copy is harmless
  (`findMatchingOneToOneConversation` finds the live one). Both sides deleting live ones before
  syncing (or while one is offline) gives each a replacement — the likely source of three rows.
- Our trigger: `18e6461` (2026-11-17) made "Delete conversation" on a 1:1 leave via
  `removeConversation` so the peer sees "X left". The reference removes the **contact**
  instead: no recreation, but the peer is told nothing. Not established: whether the user's
  devices showed duplicates before that build.
- Side finding: Bob's first clone after accepting took ~2–3 min ("Downloading conversation from
  peer"), so the harness's `accept` assertion timed out.

**Options (undecided):**
1. Split the 1:1 actions like the reference: "Clear history" (leave → daemon recreates; matches
   what actually happens) vs "Remove contact" (gone for good). No plain "Delete" on a 1:1.
2. Hide superseded 1:1s: an ended `ONE_TO_ONE` gets no list row when the contact has a newer live
   1:1 (`contact.conversationUri`); old history reachable from contact details, or dropped.
3. Revert to the reference (delete = remove contact); simpler, the peer is not told.

Claude's recommendation: 1 + 2.

**Related idea, also parked — conversation creation.** Only two of the daemon's modes are real:
`ONE_TO_ONE` (made by `addContact`) and `INVITES_ONLY` (`libjami::startConversation`, no mode
parameter). `ADMIN_INVITES_ONLY` and `PUBLIC` cannot be created through the public API and are
not enforced (`checkValidAdd` / `Conversation::addMember` only special-case 1:1), so they are
out. Planned direction: redesign New Conversation around the two real modes (1:1: search, pick
one, open; group: search incl. directory, pick many as chips, optional title). Starting a 1:1
with an existing peer keeps redirecting to it; a "separate conversation" with that peer would
have to be an `INVITES_ONLY` swarm with one invitee (peer must accept, it is a group with you as
admin, calls take the swarm-conference path, removal is only local). Open: where the option
lives, whether a title is required, group vs 1:1 look, cleanup action.

## Device test checklist — build 47 (iPhone + Pixel pair)

The Android APK must be rebuilt from this branch for the Android items (shared fixes).

**iPhone ↔ Pixel**
1. **Edit / react / delete** (F1, F2, F19): edit a message → changed in place with "edited" on
   both phones; react 👍 → chip under the message on both; delete → "<who> deleted a message".
2. **Background** (F5): send the iPhone to the home screen for > 30 s, write from the Pixel,
   reopen the iPhone → the message arrives within seconds, without restarting the app. Then
   write from the iPhone → the Pixel gets it.
3. **Unread badge** (F6): with the iPhone on the list, receive 2 messages → badge 2; open the
   chat → 0; force-quit and reopen → still 0.
4. **Mark as read** (F7, both phones): lock the phone, receive a message, use "Mark as read" on
   the notification → unlock: the conversation has no badge; on iPhone the app icon badge is
   gone too; the sender sees the read receipt.
5. **Names in notifications** (F8): force-quit the iPhone app, receive a message → the
   notification shows the sender's name (and photo if they have one), not a hash.
6. **Sound** (F9): if still silent, check the ring/silent switch, Focus, and
   Settings → Notifications → Jami → Sounds; the debug log shows
   `Notification settings: sound=…` (2 = enabled, 1 = disabled).
7. **Leave a 1:1** (F4/F20): from conversation details → "Leave group" → the other phone shows
   "<name> has left the conversation". Expect a second row for the same person afterwards
   (open decision, see above).
8. **Self** (F13): search your own Jami ID → no result; no call buttons in a self-conversation.

**iPhone only**
9. **Export** (F15): Account → export to a file with a password → the save dialog appears and
   the file is written.
10. **Import** (F15): import that archive on a fresh install → the *same* account (same Jami ID)
    comes back, not a new one.
11. **Link a device** (F15): "Link from another device" shows a token/QR; on the Pixel, add the
    device with it → the iPhone ends on the same account.
12. **Delete the account** (F16): first delete any stray accounts left by earlier import/link
    attempts (account list); deleting the last account → onboarding screen.

**Pixel only** (retests, no iOS cause found)
13. **Re-add** (F11): A deletes B as a contact, then searches B and writes → B gets it.
14. **Block unknown** (F12): with "Only accept messages from your contacts" on, a peer you share
    a conversation with can still call you; a stranger cannot.
15. **Link from another device on Android** (F15 shared part) — it could not have worked before.

## Follow-up round — 2026-11-18 afternoon (after build 47)

- **F14** (`1c66e38`): the block check now also runs where a state change creates a call;
  refused ids are remembered and their later state changes dropped; a call already announced
  is taken down. Tests cover both orders. Android: the outgoing-call ids now go in
  `EXTRA_OUTGOING_CALL_EXTRAS`, the only bundle Telecom forwards to the ConnectionService
  (explains "missing callId or accountId"); needs a Pixel check.
- **android-app unit tests** (`16ab477`): the test source set had no dependencies; added
  JUnit, Robolectric, MockK, koin-test, coroutines-test. NotificationActionReceiverTest is
  rewritten for the current receiver and passes 4/4 (`./gradlew :android-app:testStandardDebugUnitTest`).
- **Side notes from the simulators fixed** (`1b10c76`): member names in details ("Alice",
  "You"); "Leave conversation" on a 1:1; "Conversation ended." replaces the composer once
  everyone else left; rows rendered before a profile arrived get the name. Harness react phase
  now requires a real chip. Verified on the simulators.
- **Account items** (`faf94a3`): link-device events before addDevice() returns are replayed;
  export runs off the main thread; the iOS save dialog presents from the topmost controller;
  registration state passes the daemon's raw string (wrapper rebuilt) and the shared parser
  maps READY → REGISTERED (Android showed a READY account offline) and unknown ERROR_* →
  ERROR_GENERIC.
- Harness run 7 failed at `accept` on network flakiness (Alice's DHT proxy connection dropped
  and reconnected every few seconds; the invitation went out during a reconnect); run 8 on the
  same build passed every phase.
- ~~Still open: the call buttons remain in an ended chat; the leave has no confirmation dialog.~~
  Both fixed 2026-11-24: no call buttons once the conversation has ended, and leaving from
  conversation details asks first (the reference's strings).

## Video call test session (build 48, iPhone ↔ Pixel, 2026-11-18)
Plan: `~/.claude/plans/ios-video-receive-end.md` (R1–R6, E1–E4). Results reported by the user:

- **Test 1** (Pixel video-calls, answered in Jami's screen): Jami's screen and a banner both
  show; no own preview while ringing; the Pixel's image sometimes shows, then goes black with the
  controls; the iOS call screen takes over and Jami's screen is black and unresponsive; it
  sometimes rings on after pickup. The Pixel sees the iPhone. Hang-up on the iPhone works.
- **Test 2** (answered by tapping the notification): the iPhone shows **its own camera full
  screen** as the received video; taps do not bring the controls back.
- **Test 3** (hang-ups): all four directions end both sides, and a new call can be placed right
  away. But the iPhone hanging up a Pixel-placed call **leaves the ringing screen** on the iPhone,
  and the **Android notification's "Hang up" does nothing**.

Causes and fixes (`1cbbc3b`, TestFlight build 50):
| Symptom | Cause | Platform |
|---|---|---|
| Own camera as remote video, then black | `CallViewModel.handleVideoEvent` took every started sink as remote, the daemon's `camera://…` sink included | shared |
| No own preview | iOS `addPreviewVideoSurface` only stored the view; nothing drew the camera (`LocalPreviewView` now) | iOS |
| Controls cannot be brought back | the interactive UIKit interop view swallowed the tap | iOS |
| Rings on, iOS call screen takes over | an answer in the app never reached CallKit (R2); now a `CXAnswerCallAction` | iOS |
| Notification "Hang up" dead | `hangUpAny` hung up a 1:1 call's one-participant wrapper as a daemon conference | shared (Android visible) |
| Ringing screen stays after hang-up | `initIncoming` showed a call that was already gone as ringing | shared |

Still open: R1 (answering via CallKit turns our video off), R3, R4, R5, E1 (see the plan).
