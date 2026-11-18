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
| F11 | Re-adding a contact hangs | to reproduce | to retest (seen 2026-11-07) | open |
| F12 | "Block unknown" refuses peers we sync with | to reproduce | to retest (seen 2026-11-07) | open |
| F13 | Self-conversation / call to own account possible | **guarded** `6dde67e` | **guarded** `6dde67e` | done |
| F14 | Refused incoming call still reaches Telecom / CallKit | to check | bug (2026-11-07) | open |
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
