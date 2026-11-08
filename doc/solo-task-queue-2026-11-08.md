# Solo task queue — assembled 2026-11-08, **worked through the same day**

**Status: all sixteen are closed.** Twelve were implemented, four turned out to be already
done or stale on inspection (D1 half, D4, D5 half, H1), and one landed partially with its
remainder written down (I1). Each item below carries what actually happened. The device list
at the bottom is unchanged and has grown by the new work's own device checks.

Everything here can be taken from premise to committed code **without devices, without a Mac,
and without a decision from you**. Each line names where the evidence sits and what "done"
means when no hardware can confirm it. Derived from `doc/implementation-tasks-2026-11-05.md`;
item ids are that document's, so closing one here closes it there.

Premises re-checked against source today for every tier-1 and tier-2 entry, except where the
line says otherwise.

---

## Tier 1 — settings and plumbing that silently do nothing

These are not parity gaps; they are switches the UI already offers that have no effect. A unit
test proves each fix.

1. **B1 — notification sound and vibration toggles.** `AndroidNotificationService` sets both on
   the `Notification.Builder`, which Android has ignored since API 26; the channels hardcode
   `enableVibration(...)` and the default sound, and only `CHANNEL_CALLS` is ever recreated.
   Needs channel recreation on change, or per-setting channel ids. Separately,
   `NotificationGuard.getSoundUri()` returns `null` both for "sound off" and "no custom sound",
   and `null` means *use the default* — two cases, one value.
   *Verifiable here:* the guard's two cases and the channel-id selection, in unit tests. That a
   channel really goes silent is a device check.

2. **I2 — `StubDaemonBridge.init(callbacks)` discards its callbacks.** Nothing can exercise the
   15 daemon callbacks without a live peer today. Retaining them turns a device-only surface
   into a testable one, so this comes before the tests that would use it.

3. **D1 — proximity sensor.** `CallSettings.proximityEnabled` defaults to **on**, the settings
   row is wired, and `PROXIMITY_SCREEN_OFF_WAKE_LOCK` is acquired nowhere — confirmed today:
   the only hits in the tree are the setting, its repository writer and the toggle. So the
   screen stays lit against the user's ear on every call.
   *Verifiable here:* the acquire/release lifecycle against a fake `PowerManager`. The sensor
   itself is a device check.

4. **E3 — "display name in notifications" privacy toggle.** The reference lets users keep
   contact names off the lock screen; `AppSettingsScreen` has no such row. Setting, enforcement
   in the notification builders on both platforms, test.

## Tier 2 — parity work, largest value first

5. **E2 — language selector.** `UiSettings.language` exists (`SettingsModels.kt:49`) and —
   confirmed today — is read by nothing: it is the field's only occurrence outside its own
   declaration. 100+ translations ship in the app and cannot be picked from inside it. UI row
   plus applying the locale per platform.

6. **C4 — full emoji picker for reactions.** Six fixed emoji in a `DropdownMenu` today; the
   reference opens a full picker behind a "more" button.

7. **C1 — @mentions in group chat.** Parsing, highlighting and a picker in the composer. The
   largest tier-2 item and the one most worth doing properly.

8. **C2 — Markdown in message bodies.** Bold, italics, code — the reference uses Markwon, which
   is Android-only, so this wants a small common renderer rather than a dependency. Clickable
   links already landed 2026-09-18.

9. **E4 — conversation app shortcuts.** None in the KMP manifest (confirmed: no `ShortcutManager`
   or shortcuts resource anywhere). Dynamic shortcuts for recent conversations, Android only.

10. **D2 — haptic feedback on answer/decline.** Confirmed today: no `Vibrator` and no
    `HapticFeedbackConstants` anywhere in the tree. Small.

11. **D5 — expose `videoEnabled` in call settings, and gate outgoing calls on it plus
    `hasCamera()`,** matching `CallPresenter.initOutGoing():98`.

12. **D4 — incoming video call screen.** Live camera preview while a video call rings, and a
    separate "accept audio only" button, matching `initIncomingCallDisplay`.
    *Writable in full; the visual half is a device check.*

13. **H1 — conversation search and history pagination** (`onMessagesFound`, `loadMore()`).
    Premise is from a superseded 2026-03 gap analysis — **read the source first**; parts of it
    may already exist under other names.

## Tier 3 — test infrastructure

14. **I1 — Robolectric**, so `HardwareServiceTest`, `AppSettingsViewModelTest`, `AppViewModelTest`
    and `CallViewModelTest` run against the Android actuals instead of the desktop ones only.

15. **Wire `compose-ui-test-junit4`** — it sits in the version catalog (`libs.versions.toml:104`)
    unwired in `shared/build.gradle.kts`, so the `TestTags` added by L1 are decoration and the
    whole accessibility pass has no regression guard. Then the rest of L1's own open list: the
    incoming-message and recording-banner live regions, and a touch-target sweep outside the
    chat composer.

16. **I3 — harden the harness "no competing daemon" precondition.** Add `cx.ring.debug` (and
    consider `com.savoirfairelinux.ring`) to `COMPETING_APP_IDS`; re-assert the force-stop just
    before the first cross-device step rather than once per run; add a pre-run
    `pm clear net.jami.android.harness` so a timed-out run cannot poison the next scenario.
    *Writable alone; it only proves itself on a device run.*

---

## Not on this list, and why

- **J2–J5 (licensing)** — blocked on **J1**, which is yours: the copyright holder's name or
  entity. It must not be guessed from a git config. Answer it and J2 (the line the About screen
  shows users today) is a two-line change.
- **K1–K4** — decisions, not code: chat plugins, the Web/JS target's future, Desktop JVM, and
  `QRCodeUtils.js.kt` behind it.
- **F1–F5** — need a Mac: the batched wrapper regeneration and everything folded into it.
- **G1** — needs a self-hosted `dhtnode --proxyserver` holding this app's own FCM/APNs keys.
- **M4** — three fully-merged local branches are deletable, but deleting branches is yours to
  say.
- **C5 (video recording in chat), D3 (Bluetooth routing), F3 (iOS remote video)** — writable in
  part, but each is unprovable without hardware and large enough that building blind is poor
  value. They belong after a device session, not before.

## What is waiting on the phones

A single session with the Pixel 2 and the Pixel 7a closes all of these; several are verification
owed on work that is already committed:

| Item | What needs seeing |
|---|---|
| E1 | Doze stops killing the daemon once exempt (new, 2026-11-08) |
| A2 | screen sharing on Android 14+, defect and fix both |
| B2 | quiet hours silences a call without hiding it |
| B3 | `blockUnknownContacts` withholds, and reveals again when switched off |
| C3 | peer avatars appear — they need a real `DISPLAYED` from a peer |
| I4 | the durable outbox through the UI, not the e2e scenario that bypasses it |
| I5 | microphone level ring (needs a call someone answers) |
| I8 | whether the Pixel 7a shares the Pixel 2's SELinux reinstall race |
| I9 | the role-swapped direction of `66c5aef` — one scenario variant, no code |
| B1, D1, D2, D4 | the device halves of the tier-1/2 items above, once written |


---

# What happened, 2026-11-08

Worked top to bottom in one session. Desktop suite 999 tests, Android unit tests 919, both
green; `:android-app:assembleDebug` and `:e2e-runner:compileKotlin` clean. iOS and macOS
changes are uncompiled, as every Apple change made on this host is.

| # | Item | Outcome | Commit |
|---|---|---|---|
| 1 | B1 notification sound/vibration | Fixed via per-setting channel ids; also fixed the ringtone, broken the same way, and iOS reading `shouldVibrate()` to decide *sound* | `11ada3b` |
| 2 | I2 stub callbacks | Retained, plus `fireDaemonEvent` and a `TestFixtures` wiring over the real `DaemonCallbacksImpl` | `333795c` |
| 3 | D1 proximity | **Premise half stale** — Android already had it; iOS had nothing. Rule moved to a shared `ProximityPolicy` | `084fa1a` |
| 4 | E3 names in notifications | New setting; reference has only lock-screen visibility, which KMP already had | `b59976c` |
| 5 | E2 language selector | Set below the UI toolkit per platform (`LocalComposeEnvironment` is internal); locale list generated | `fe5043d` |
| 6 | C4 emoji picker | 639 emoji, generated; recents, search, categories | `7e41471` |
| 7 | C1 @mentions | **Premise wrong** — neither reference client has mentions. Literal `@name`, matched against members only | `d40c6ac` |
| 8 | C2 Markdown | Inline only; one tokenizer for Markdown + links + mentions, because delimiters shift offsets | `adede5c` |
| 9 | E4 app shortcuts | Dynamic, from the smartlist; needed a new `OpenConversationState` route | `ce17110` |
| 10 | D2 haptics | Compose haptics, not `Vibrator`: no permission, obeys the system setting | `a112a72` |
| 11 | D5 video gating | **Half already done**; the setting was read by nothing, now it gates | `eab16b9` |
| 12 | D4, H1 | **Already implemented**; premises stale | `a710d74` |
| 13 | I1 Robolectric | Wired; one of four classes crossed over, the other three blocked for reasons now recorded | `ac242d7` |
| 14 | Compose UI tests + L1 leftovers | First UI test in the repo; both live regions; touch-target sweep found and fixed two sites | `b270171` |
| 15 | I3 harness precondition | All three parts; the `pm clear` is conditional on a run marker, not unconditional | `155b183` |

**Four premises on the task list were wrong or stale** (C1, D1, D4, E3, H1, half of D5). Each
is corrected in `doc/implementation-tasks-2026-11-05.md` where it sits, rather than silently
skipped — the same failure mode the 2026-11-07 screenshot pass was written to stop.

**New findings worth someone's time**, none of which could be seen before today:

- `AppViewModelTest` and `CallViewModelTest` never leave `Loading` / `Outgoing` under
  Robolectric although `advanceUntilIdle()` returns — something in the Android path is off the
  test scheduler.
- `HardwareServiceTest` cannot run on Android until `DaemonBridge` is behind an interface:
  binding it in a unit test loads `libjami-core-jni.so`.
- `MessageInputBar` holds a `String`, not a `TextFieldValue`, so @mention completion works
  only at the end of the text.
- The macOS notification service consults no notification setting at all — not the new one,
  not the existing ones.
