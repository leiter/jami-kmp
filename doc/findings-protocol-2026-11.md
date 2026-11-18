# Findings protocol — iOS-first fix round (from 2026-11-18)

Each finding: symptom, iOS status, Android status, root cause, fix, verification, device
steps still owed. iOS is fixed first; Android is checked for the same issue and changed only
where it has the gap too. "Before" / "after" refer to runs on the two-simulator harness
(`scripts/ios-two-sim`, JamiTest-A = Alice, JamiTest-B = Bob, real Jami network).

Source of the findings: the user's device test of TestFlight build 46 (iPhone ↔ Pixel), plus the
open items in `doc/TODO.md`. Plan: `~/.claude/plans/i-want-to-create-robust-glade.md`.

| # | Finding | iOS | Android | Status |
|---|---|---|---|---|
| F1 | Edit becomes a quote | bug (bridge drops `flag`) | not affected | open |
| F2 | Own reaction becomes a quote; peer reactions missing | bug (same) | not affected | open |
| F3 | Message order / parent id wrong | bug (`linearizedParent` missing) | not affected | open |
| F4 | Member roles and events mistranslated (1:1 leave not seen) | bug | not affected | open |
| F5 | No sync after background, until restart | bug | not affected (foreground service) | open |
| F6 | Unread badge never clears | bug | works (user, 2026-11-18) | open |
| F7 | "Mark as read" in a notification has no effect | bug | bug | open |
| F8 | Notification shows hash and app icon | bug | to check | open |
| F9 | Notification sound inaudible | to check | — | open |
| F10 | vCard (profile) sync | to check | works live (user) | open |
| F11 | Re-adding a contact hangs | to reproduce | to retest (seen 2026-11-07) | open |
| F12 | "Block unknown" refuses peers we sync with | to reproduce | to retest (seen 2026-11-07) | open |
| F13 | Self-conversation / call to own account possible | missing guard | missing guard | open |
| F14 | Refused incoming call still reaches Telecom / CallKit | to check | bug (2026-11-07) | open |
| F15 | Account export / import fails (user, 2026-11-18) | bug | works | investigating |
| F16 | No navigation to onboarding after deleting the account (user, 2026-11-18) | bug | works | investigating |

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
