# Open tasks — assembled 2026-11-24

Assembled on a Mac at `7920ac1` (`main` = `ios-catch-up-2026-11`) from every tracked Markdown
file (49), then **cross-checked against the code**. Every item below was confirmed still open in
the source on that date, unless it says "unverified". The items found already done were closed in
the docs that still carried them; they are listed at the end.

This list replaces `doc/ios-next-tasks-2026-11-11.md` and the "Open iOS tasks — consolidated
2026-10-31" block of `doc/TODO.md` as the starting point for the next session. Those files keep
their detail; this one keeps the overview.

Ground rules carried over: iOS can be tested here (two simulators, `scripts/ios-two-sim`).
Android is built and tested on another machine, so a shared change is compiled only for iOS here.

---

## 1. Decisions owed (nothing can start before the answer)

| # | Decision | Where it is described |
|---|---|---|
| D1 | **Copyright holder name/entity.** Blocks all licensing items below (About screen, §5(a) notice, per-file attribution, `AUTHORS`). | `doc/TODO.md` → "Licensing & Copyright Attribution" |
| D2 | **Duplicate 1:1 rows after deleting a 1:1** (daemon recreates the 1:1, the peer keeps the ended copy). Options: split delete into "Clear history" / "Remove contact", hide superseded 1:1s, or revert to the reference. Recommendation on file: 1 + 2. Tied to the New Conversation redesign (two real modes). | `doc/findings-protocol-2026-11.md` → "Parked decision" |
| D3 | **Auto-join invitations from contacts** — a new toggle, not an existing setting. | `doc/TODO.md` → "OPEN DECISION: no auto-join" |
| D4 | **Plugin system** — wanted on the mobile targets at all? | `doc/plan_plugin_system.md`, mini-plans daemon F3 |

## 2. iOS — code work

| # | Task | Size | Notes |
|---|---|---|---|
| I1 | ~~**R1: answering from CallKit turns our video off.**~~ **Done 2026-11-24** — see the closed list. | S | |
| I2 | **R3: the wrapper reduces the incoming media list to a `hasVideo` bool.** A caller with the camera off still looks like a video call. Needs a wrapper change and an `.a` rebuild. | M | `~/.claude/plans/ios-video-receive-end.md` |
| I3 | **R4: no camera interruption handling** (`AVCaptureSessionWasInterrupted` / `InterruptionEnded` / `RuntimeError`). Answering on the lock screen can leave our video off after unlocking. | M | `IOSCameraService.kt` |
| I4 | **R5: the audio session is ours, not CallKit's** — no `didActivate`/`didDeactivate`, mode always VoiceChat. Audio works; low priority. | M | `HardwareService.ios.kt`, `CallKitManager.kt` |
| I5 | **E1: a hang-up in the app reaches CallKit as "remote ended"**; the native client requests a `CXEndCallAction`. | S | `CallKitManager.kt` |
| I6 | **Picture-in-picture during a video call.** | L | `doc/ios-video-pipeline.md` §4 |
| I7 | **Notification Service Extension + App Group**, so message pushes display while suspended. Only useful once a push-capable DHT proxy exists (I12). Scaffold on branch `feat/ios-app-group-and-notification-extension` (`5971f3c`). | L | `doc/ios-native-parity-analysis.md` §2.1 |
| I8 | **Share Extension** (share into Jami from other apps). No extension target exists. | M | parity analysis §2.2 |
| I9 | **Screen sharing (ReplayKit)** — nothing exists. | L | |
| I10 | **Call waiting** — `maximumCallsPerCallGroup = 1u`, a second incoming call is not supported. | M | `CallKitManager.kt:172` |
| I11 | `IOSNotificationService` builds its action titles with `runBlocking { getString(...) }`. | S | `IOSNotificationService.kt:98` |
| I12 | **Push delivery infrastructure**: App ID Push capability plus a self-hosted `dhtnode --proxyserver` holding APNs credentials. Infrastructure, not code. | — | `doc/push-notifications.md` |
| I13 | **macOS / shared `appleMain` source set** — macOS does not compile (~95 errors); start from `origin/macos-build-and-apple-source-set` (`f0f66ce`, far behind). Secondary platform. | L | `doc/apple-bridge-rebuild.md` step 8 |

## 3. Shared (all platforms) — code work

| # | Task | Size | Notes |
|---|---|---|---|
| S1 | ~~**45 strings exist only in English and German.**~~ **Done 2026-11-25** — see the closed list. Left: Acehnese and Kabardian have no translations for the hand-made strings and no CLDR date data. | M | |
| S2 | ~~**Guard against importing an account that is already on the device.**~~ **Done 2026-11-25** — see the closed list. | S–M | |
| S3 | ~~**Two contact caches per peer.**~~ **Done 2026-11-25** — see the closed list. | L | |
| S4 | ~~**No per-route navigation transitions.**~~ **Done 2026-11-25**, see the closed list. Left: a row → chat container transform. | M | |
| S5 | ~~**`MediaViewerScreen` has no pager.**~~ **Done 2026-11-25**, see the closed list. | M | |
| S6 | **Haptics only in the call flow** — none on long-press menus, swipe-to-reply, swipe-to-delete, reaction picker. | S | same |
| S7 | **Video tiles in the media gallery have no thumbnail.** | M | `MediaGalleryScreen.kt` |
| S8 | **Audio route picker** (earpiece / speaker / Bluetooth) — the call screen has one binary speaker toggle. Backend partly there on Android. | M | `doc/TODO.md` → Calls |
| S9 | **Tapping a 1:1 peer's avatar in details does nothing.** Check the reference before treating it as a gap. | S | unverified parity |
| S10 | **Video recording in chat** — menu shows "coming soon". | L | |
| S11 | **Robolectric for the four shared test classes excluded from the Android unit-test task.** | M | Android machine |
| S12 | **Calling F9: call/stream introspection** (`getCallList`, `getConferenceList`, `currentMediaList`, `getConferenceInfos`) — diagnostics only. | S | mini-plans README |
| S13 | `Permissions.md` leftovers: pre-request rationale for camera/microphone, an "open settings" button in the denial dialog. | S | `Permissions.md` |

## 4. Seen once, cause unknown or unverified

- A peer's message notification falls back to the raw conversation id when the peer's profile
  name goes blank (Pixel, 2026-11-07). Names in notifications were reworked since (`698c86d`);
  not re-observed.
- The account switcher showed the current account as a green "J"/"Jami" while the home avatar
  was a magenta "M" (Pixel 2, 2026-11-07). Not re-observed, not traced.
- `ChatViewModel.loadConversation` runs once more after the conversation was removed (harmless).
- Why a peer's `.vcf` became 0 bytes (failed profile transfer leaves the daemon's stub).
- Video takes a while to return when the peer re-enables it — one 12.3 s gap on the Pixel 2.
- Re-adding a contact hangs (F11) and "Block unknown" refusing peers we sync with (F12): not
  reproduced on the simulators, Android retest owed.
- Android e2e harness: competing daemons and leftover accounts between runs
  (`doc/TODO.md` → "Infra TODO (2026-09-05)").
- iOS two-simulator harness on a bad network night (2026-11-24/25, five runs before one
  passed): an invitation sent while the sender's account flapped UNREGISTERED/TRYING never
  arrived; the public directory lookup failed; Bob's first clone of the 1:1 took longer than
  `accept`'s wait, and a group invitation was still not cloned after 10 minutes. All network,
  none app code. A harness that waits for both accounts to be REGISTERED before `invite`, and a
  longer clone wait, would make the run less sensitive to this.

## 5. Verification owed (no code change expected)

**iPhone (physical device):** CallKit tone on a locked phone, silent quiet-hours call, caller
name and hidden names (T1/T2); sender photos (T3); quick actions (T4); Accept from the lock
screen with the app killed (T6); outgoing call on CallKit; background sync (F5); mark-as-read
from the lock screen (F7); notification sound (F9); export / import / link device (F15);
delete-last-account → onboarding (F16); video fixes of build 50 (`1cbbc3b`), and E2–E4/R6
teardown checks. Lists: `doc/ios-task-queue-2026-11-09.md` item 5,
`doc/findings-protocol-2026-11.md` "Device test checklist".

**Pixel pair:** F11, F12, link-device import (F15 shared part), F14 Telecom extras, screen
sharing on Android 14+, proximity lock, B13 cellular branch.

**Simulators (possible here):** read markers, @mentions, Markdown, emoji fonts, SQLDelight
settings/drafts, presence suspend/resume (`doc/ios-next-tasks-2026-11-11.md` item 7); manual
test plan 2026-11-19 sections C (jump to latest) and D (call screen name).

## 6. Deprioritised platforms (unchanged)

Desktop daemon bridge (no-op stubs), Web/JS (REST stubs, `QRCodeUtils` null), desktop/web
video surfaces, OsmMapView on desktop/macOS, the Qt IPC split (`doc/plan_ui_logic_separation_qt_ipc.md`, dormant).

---

## Closed in this pass (2026-11-24)

Implemented today:
- **A group created with one invitee read as a 1:1** — missing from the "Groups" filter, shown
  with the peer's name and avatar, contact details instead of group details, and
  `Account.getByContact` could return it instead of the real 1:1. `Conversation.isGroup` now also
  honours an explicit group mode.
- **Leaving from conversation details asked nothing** — it now confirms, with the reference's
  strings (`swarm_group_action_leave_title` for a group, `conversation_action_remove_title` for a
  1:1).
- **Call buttons stayed in an ended chat** — hidden once the conversation has ended.
- **R1: answering from CallKit, a notification or a deferred push answer turned our video off** —
  the answer now carries video when the call offers it and the camera is authorised.
- **"Tapping the chat title does not open details"** (seen 2026-11-24) — checked on the
  simulators: the tap does open details. For a group with one invitee it opened the peer's
  contact-style details, which read as "not the details". With the group fix above it opens the
  group's details (group photo badge, "Group (invite only)", members, "Add member").
- **Details of an unnamed group were titled by the swarm id** (any group size) — now by its
  members, as the list and the chat header already did (`ContactDetailsState.conversationTitle`).
- **An admin was offered "Block" on their own member row** — the action is no longer shown on
  the row that is this account.
- **A group's row in the conversation list carried a presence dot** (always "offline") — no
  dot on group rows, as in the reference.

Found already done, docs corrected:
- Self-conversation and calls to your own account — guarded in `6dde67e` (F13).
- A refused incoming call still reaching Telecom — fixed in `1c66e38` (F14).
- A peer's name not re-read from disk at startup — `ContactService.restoreProfileFromDisk` /
  `VCardService.loadPeerName` (`698c86d`).
- "Display name in notifications" setting — `NotificationSettings.showNamesInNotifications`
  (`b59976c`).
- `AccountService.explicitlyDeactivatedAccounts` init order — fixed in `1cfbc80`; the checkbox
  was left open.
- `strings_kmp.xml` locale cleanup — no such file remains.
- Notification sound/vibration toggles (B10/B11) — `11ada3b`.
- Incoming video call: camera preview while ringing and "accept audio only" — both present
  (`a79e345`; `CallScreen.kt` `startIncomingPreview`, `onAcceptAudio`).
- `aps-environment` hardcoded to development — now `$(APS_ENVIRONMENT)`.
- Duplicate `UIBackgroundModes` — collapsed 2026-10-31.

## Closed 2026-11-25

- **S1 — strings only in English and German.** First `scripts/sync_android_strings.py` against
  jami-client-android `91b7759` (586 translations, `date_today`/`date_yesterday` newly aliased
  to Android's `today`/`yesterday`). Then month names, the full-date pattern,
  "Today"/"Yesterday"/"Now" and the short time units came from the CLDR data macOS ships
  (`scripts/cldr_date_strings.swift`, 1826 strings, region folders included, German left
  hand-made). The remaining 30 strings were translated by hand for 69 folders
  (`scripts/insert_translations.py`, add-only). The German translations of the four
  `account_biometric_error_*` strings and `conversation_read_only` were missing too, and were
  added. Machine-made, like `856ad36`; review welcome. Not translated: Acehnese (`ace`) and
  Kabardian (`kbd`). Both use a script or orthography that could not be checked here, and
  macOS has no CLDR data for either. They keep the English fallback for these keys. Every used
  translatable key now has a translation in every other folder.
- **S2 — importing an account already on the device.** Neither the daemon nor the reference
  clients refuse it. The archive's identity is only known after the daemon has decrypted it,
  so `ImportAccountViewModel` waits for that (the account leaves INITIALIZING) when other Jami
  accounts exist. If the identity matches one of them, it removes the new copy (its own
  account directory only) and shows `import_account_already_on_device`. Onboarding, with no
  accounts, completes as before. The removed copy will have announced a new device
  certificate for that identity in the meantime, which is harmless but visible in the device
  list of the account's other devices.
- **S3 — two contact caches.** `ContactService` no longer keeps Contact objects of its own: it
  hands out the account's (`Account.getContactFromCache` / new `findContactInCache`) and only
  remembers which peers are on the daemon's contact list. This also fixes a latent leak: a
  call peer or a contact-details lookup used to put a stranger into the Contacts/Blocked
  lists. `ConversationFacade.getLoadedContact` gave out detached `Contact(uri)` objects and
  uses the account's now. One visible change follows the reference: a contact the daemon
  lists as banned is now `BLOCKED` on the object conversations hold, so its 1:1 conversation
  leaves the list, as `getConversationsForAccount` always intended.
- **S4 — navigation transitions.** `ui/navigation/NavTransitions.kt` gives both NavHosts the
  reference's motion (`BaseSupportFragment.replaceFragmentWithSlide`): the new screen slides in
  from the trailing edge, the old one out to the leading edge, and Back reverses it. It runs for
  220 ms (`config_activityDefaultDur`) with a decelerate curve, and Start/End flip in RTL. The
  media viewer, video player and call screens fade instead, at Navigation's previous 700 ms, so
  the image shared element runs exactly as it did when checked on the Pixel pair. Not done: a
  container transform from the conversation row into the chat. Not walked on a device yet:
  motion looks wrong long before a test can tell.
- **S5 — media viewer pager.** `MediaViewerScreen` is a `HorizontalPager` over the
  conversation's images (`pagedImagePaths`: picture transfers on disk, in message order). It
  opens on the image tapped in the chat or the gallery. Each page has its own zoom; the pager
  only moves while the current page is unzoomed, and a page only pans while zoomed. Share, Save
  and swipe-to-dismiss act on the page on screen, and the shared element flies back to that
  page's bubble. An image missing from the list is shown alone. The reference viewer has no
  pager.
