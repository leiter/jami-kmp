# TODO

## POSTPONED: device verification of the 2026-11-06 fixes

Started on the Pixel pair on 2026-11-06 at ~22:10 and **stopped at the user's request before
any verdict was reached**. Nothing below is verified; resume from here rather than from scratch.

**Devices.** A = Pixel 7a `37281JEHN03065`, Android 17 / SDK 37 (the subject: its SDK is what
makes A2 testable at all). B = Pixel 2 `FA7AJ1A06417`, Android 11 / SDK 30. Both had
`net.jami.android` reinstalled from `android-app/build/outputs/apk/standard/debug/` built at
commit `cbdfddf`. Always target adb by serial.

**Accounts as found** (unchanged; nothing was deleted): A = `unitedhumans`,
`66d57f55227639b58fdcc6d74ec090446ffa9bc3`, account dir `3f3dad8b328eb995`. B =
`d9f8c7f0bf768178542d402c6ae233072bfff6df`, account dir `965287c3eb08e39e`. Decode the daemon's
`files/<accountdir>/contacts` (MessagePack) to read contact state; A lists B as
`confirmed: false, removed: 1793627642`, i.e. removed.

**The one thing that was actually confirmed.** The `jami_calls_silent` channel (B2) **is created
on-device** on Android 17 — `adb -s <serial> shell dumpsys notification --noredact | grep -oE
"mId='jami[a-z_0-9]*'"` lists it beside `jami_calls`. That is the channel existing, not the
routing of a call to it.

**Blockers found, each of which needs solving before the run can work:**

- [x] **The app's own log lines are invisible on modern Android.** Fixed 2026-11-07.
  `net.jami.utils.Log` was `println`, and Android routes app stdout to `/dev/null` on the
  Pixel 7a (SDK 37) while the Pixel 2 (SDK 30) still shows it — so every logcat discriminator
  in `doc/manual-test-plan-2026-09-10.md` was unobservable on the subject device, and the
  in-app "Debug logs" screen showed only the daemon's native logs. `Log` now delegates to an
  internal `platformLog` expect/actual: `android.util.Log` on Android, `NSLog` on iOS/macOS,
  `println` on Desktop/Web. Verified on the Pixel 7a — `D/ContactService`, `D/ConversationsVM`
  and `D/AppViewModel` lines now appear under the app's PID, which is also what
  `LogCapture.captureRecentLogs` reads, so the Debug-logs screen gains the KMP layer for free.

- [ ] **The existing pair cannot produce a fresh contact request.** A removed B, but B never
  removed A, so B still holds A as a contact: opening "a new conversation" with A on B reused
  the old 1:1 swarm `52c33a0bdf3038c4026ad0903dd6488022593b53` and sent **no trust request** —
  A's `convRequests` stayed empty (1 byte) throughout. B's message parked at "Waiting to sync",
  which is the durable outbox doing its job on a `Bootstrap with 0 device(s)` swarm.
- [x] **A throwaway account cannot be made without registering a public username.** Fixed
  2026-11-07 (the parity fix, as preferred). `CreateAccountScreen`'s Create button required a
  non-empty username and `AccountCreationViewModel.createAccount()` refused an empty one with
  `USERNAME_REQUIRED`, so no local-only account could be made — while the reference client's
  wizard offers "Skip choosing username" (`JamiAccountCreationPresenter.registerUsernameChanged`
  clears the name and revalidates). The username is now optional: blank is allowed and shows
  "Optional" as supporting text, a typed name must still be free and settled before Create
  enables. `AccountService.createJamiAccount` already handled this — it falls back to a
  generated alias and sets no `ACCOUNT_REGISTERED_NAME` — so nothing registers on the public
  directory. Covered by `createAccountWithoutUsernameSucceedsAndRegistersNoName`; not yet
  exercised on a device.

**New bug found while setting up — fixed 2026-11-07:**

- [x] **Calling from a not-yet-live 1:1 conversation silently placed no call.** On B, the call
  button in the chat with A produced
  `CallViewModel: initOutgoing: ... peerUri=swarm:2cb2dbaee7f6515c5c1c47de74ed745cfd02ba8d
  scheme=swarm: isGroupCall=true` → `DaemonBridge: placeCall ← JNI: callId=''` → `CallService:
  placeCall: daemon returned callId=''`, and the UI sat on "Calling…" forever with no error.
  Cause: `Conversation.isSwarmGroup` read `Mode.Syncing` as "not OneToOne, therefore a group".
  `Syncing` is not a shape — it means the swarm's git repo has not been cloned yet, and the
  daemon's real mode is kept in `requestMode` while it lasts (`ConversationFacade` sets it) —
  so a still-cloning 1:1 was called on `swarm:<conversationId>`, which no device answers.
  Three changes: `isSwarmGroup` now judges a `Syncing` conversation by `requestMode` (unknown
  means 1:1, as the facade already assumes); `CallViewModel.initOutgoing` re-resolves the peer
  from the conversation's own member when the uri it was handed is a `swarm:` one (the chat
  screen falls back to the conversation id before it knows a peer) and fails visibly when there
  is none; and a hosted group call that the daemon never starts now fails after the 15s grace
  period instead of waiting forever. Covered by `SyncingConversationShapeTest` plus two
  `CallViewModelTest` cases; not yet re-run on the pair.

**Device run of 2026-11-07 (Pixel pair, continued from the postponed run above).**

- [x] **B3 verified on hardware.** A local-only throwaway account on B (now possible thanks to
  the optional-username fix) sent A a genuine stranger request. With **Block unknown contacts
  on**, A dropped it — `W/ConversationFacade: onConversationRequestReceived: dropping request
  from a non-contact (block unknown contacts is on): 716e83d1…` — and no notification was
  posted. With the setting **off** and a restart, the same request surfaced as "Requests (1)"
  with the pending-invitation banner, so the filtering is setting-driven and the startup poll
  path works. Observable only because of the logging fix above.
- [x] **The contact-request notification works.** Verified on hardware 2026-11-07 09:00. A
  fresh request from B's throwaway reached A and produced
  `ConversationFacade: onConversationRequestReceived: 46814601…` →
  `AndroidNotificationService: Showing trust request notification … 1 pending`, a live record on
  `channel=jami_requests` with `actions=2`, and a shade entry reading **"Neue Einladung"** with
  **Annehmen / Ablehnen**. Tapping Annehmen accepted it (the daemon fetched the conversation and
  the notification cleared), so both actions work.

  Two corrections to what the earlier run concluded. The Pixel 7a's **airplane mode was never
  the cause** — Wi-Fi is connected and `bootstrap.jami.net` answers in 87 ms; the earlier
  invites did arrive, just later than the two minutes that were waited. The real obstacle is the
  one already recorded above: **two accounts that are already contacts cannot produce a trust
  request**, so a fresh one needs the contact dropped on *both* sides first. With that done the
  request landed within 20 s of restarting the sender.

**Two bugs found during that run, both fixed:**

- [x] **A newly created account did not become current** (`54aa5f9` → fixed in a follow-up
  commit). Profile setup writes to the current account, so the display name typed right after
  creating an account was written onto the *previously selected* account — on B, creating the
  throwaway renamed the existing `peace4us` account, and the new account stayed nameless. The
  account only became current after an app restart. `AccountService.setCurrentAccountById` now
  selects by id (reloading the list once, since a just-created account is not in `accounts`
  yet) and `AccountCreationViewModel` calls it for both Jami and SIP before navigating.
  The overwritten profile name was restored on the device.
- [x] **"Delete contact" and "Block contact" were silently dead.** Opened from a conversation,
  `ContactDetailsViewModel.loadContact()` kept the *swarm id* as `currentContactUri`, so every
  action addressed `swarm:<conversationId>` — the daemon has no contact under that id, did
  nothing and said nothing. The screen itself showed the right peer, which is why it looked
  functional. It now keeps the resolved peer. Verified on the device: tapping delete produces
  `contact_list.cpp [Contacts] removeContact: 66d57f55… (banned: false)` and the conversation
  disappears.

**Two smaller things seen in that run — both fixed 2026-11-07:**

- [x] **The contact-details screen stayed open on a conversation it had just deleted**, so the
  delete looked like it had done nothing. `ContactDetailsState.isGone` is now set after delete,
  block, unblock, leave *and* on a `ContactEvent.ContactRemoved` for the loaded peer (a removal
  made on another device), and the screen pops back on it. The reference finishes the activity
  after every one of those actions (`ConversationActionsFragment`). A state flag rather than a
  one-shot event, so acting on it twice cannot pop twice. Three new
  `ContactDetailsViewModelTest` cases.
- [x] **A message held in the durable outbox told the sender nothing.** The held bubble carries
  a clock glyph and nothing else, which is indistinguishable from "sending". The composer now
  shows a one-line notice while the conversation cannot deliver — `ChatState.messageHold`, asked
  of `SendQueueService.shouldHold` so the notice and the hold cannot disagree, with the mode
  picking the wording: `conversation_syncing` ("Downloading conversation from peer", the
  reference's own string) while the swarm is cloning, and a new `conversation_awaiting_peer`
  while a 1:1's peer is still only invited. The reference refuses the send outright and hides
  the input in both states; keeping the input and holding the message is the better behaviour,
  it just has to say so. Computed before the suspending history load, so it is right from the
  moment the screen appears. Three new tests in `SendQueueServiceTest`.

**Device run of 2026-11-07 (second session, 06:40–09:10). Everything below was verified on the
Pixel pair with the build at that commit.**

- [x] **The composer notice for a held message**, in both wordings: "Waiting for the invitation
  to be accepted…" while B's invite to A was unanswered, and the reference's "Downloading
  conversation from peer" after the contact was deleted and the conversation fell back to
  syncing. The bubble appeared with its clock glyph, the log read `SendQueueService: held
  message 3 for d2a8a8a2… until it is live`, and the moment A accepted,
  `conversation d2a8a8a2… is live; sending 1 held message(s)` fired, the notice disappeared and
  the message was delivered. **That also closes the UI-driven verification the durable outbox
  (F4) had been owed since 2026-09-18** — the `send-before-member-join` e2e scenario bypasses
  the outbox, this did not.
- [x] **The details screen leaves when its contact is deleted**, on both devices (Android 11 and
  Android 17): the screen closed by itself and the daemon logged
  `removeContact: 66d57f55… (banned: false)`.
- [x] **Lock-screen notification visibility reaches the notification.** With the setting on
  Secret, two newly posted message notifications came out `vis=SECRET` while the ones posted
  before the change stayed `vis=PRIVATE`.
- [x] **The proximity wake lock.** Answering an audio call acquired
  `PROXIMITY_SCREEN_OFF_WAKE_LOCK 'jami:callProximity' ACQUIRE_CAUSES_WAKEUP ON_AFTER_RELEASE`,
  and hanging up released it (`REL jami:callProximity`, none held afterwards). The
  speaker-on/off half failed: the lock stayed held across two taps and no `toggleSpeakerphone`
  line was ever logged. That was not a missed tap — see the find below.

**The run's own find — the speaker button was dead on every 1:1 call (fixed 2026-11-07,
not yet re-run on the pair):**

- [x] **A 1:1 call never had a `Conference`, so `CallViewModel.toggleSpeaker()` returned on its
  first line.** `CallService.conferences` was only ever filled by the daemon's conference
  signals (`onConferenceCreated` / `onConferenceChanged`), and a plain 1:1 call is not a
  conference as far as the daemon is concerned — so `getConfUpdates` had nothing to emit,
  `handleConferenceUpdate` never ran, and `currentConference` stayed null for the whole call.
  `toggleSpeaker` needs one only to hand to `toggleSpeakerphone(conf, enabled)`, and bailed
  instead. Nothing was logged on either side of the bail, which is the whole reason this read
  as "the taps did not land".

  The hardware layer was never at fault: `HardwareService.android.kt:259` already releases the
  proximity lock on speaker-on and takes it again on speaker-off, which is what the earlier
  entry was trying to confirm.

  Fixed the way the reference does it. jami-client-android wraps a plain call in a
  single-participant `Conference` on subscription — `getConfUpdates(call)` → `getConference(call)`
  → `addConference(call)` (`CallService.kt:531`) — and KMP's `Conference(call)` constructor and
  `isSimpleCall` flag were already there for it, just never used. `getConfUpdates` now seeds
  itself from a new `conferenceFor()`, keyed `confId ?: callId` as the reference keys it, so
  `onConferenceCreated` replaces the wrapper if a real conference turns up and the
  `CallStatus.OVER` branch already drops it with the call. It is deliberately **not** pushed
  into `_conferenceUpdates`, which replays. `toggleSpeaker` also logs both the toggle and the
  bail now, so a dead speaker button can never again be silent in the log.

  Worth knowing: the same null made nine other `CallViewModel` methods no-ops in a 1:1 call,
  though all nine are conference moderation (`muteAllParticipants`, `removeParticipant`,
  `setParticipantModerator`, …) and have no 1:1 meaning. `endCall` and `toggleHold` already
  handled the null explicitly, which is why neither of them ever showed the problem.

  Covered by `aPlainOneToOneCallStillGetsAConference` and
  `theSeededConferenceIsDroppedWhenTheCallEnds` (desktop suite now 915).

**The run's own find — incoming calls could not be answered at all (fixed, verified):**

- [x] **An incoming call was reported to Telecom as outgoing, so it had no answerable UI.**
  B called A twice and both rang out as missed, with no ringtone and no call screen. A's log:
  `CallService: onCallStateChanged: state=INCOMING` → `HardwareService: updateAudioState:
  status=RINGING incoming=false` → `JamiTelecomManager: placeCall` →
  `JamiConnectionService: onCreateOutgoingConnection: missing callId or accountId`.
  Cause: an incoming call's **first** daemon state change is CONNECTING, which arrives before
  the incomingCall signal and already carries a peer number, so `CallService.addCall` created
  the call as OUTGOING and then kept it — its replace condition only looked at an empty peerUri,
  never at the direction. `JamiTelecomManager` had meanwhile latched the call as outgoing and
  would not re-report it. Both halves fixed: `addCall` now lets the caller's direction win, and
  the Telecom layer re-reports a call as incoming if it was reported as outgoing and no
  Connection exists yet. Covered by
  `aStateChangeBeforeTheIncomingSignalDoesNotMakeTheCallOutgoing`. **Verified on the device
  after the fix:** `updateAudioState: status=RINGING incoming=true`, the incoming-call screen
  with Accept/Decline, a 1 min 24 s answered call, and the proximity lock above.

**Smaller things seen on 2026-11-07 — the first three were fixed the same day, see the
third-session block below:**

- [x] A long-ish message's timestamp overlaps the text in the bubble ("held-test-064206:43").
- [x] The conversation-details screen shows "Unknown" as the display name with the registered
  name as the subtitle, and a "?" avatar, for a peer the chat screen renders with name and
  avatar.
- [x] The chat screen stays open on a conversation whose contact was just deleted (the details
  screen now leaves, but it pops back *into* that chat). The reference closes the whole
  conversation.
- `avc: denied { link }` from libgit2 on the Pixel 2 during swarm writes — benign here (the
  repo was created and synced), but it is the SELinux hard-link restriction, worth knowing
  before blaming it for a stall. Seen again on 2026-11-07 on the Pixel 2 when the daemon
  rewrote `profile.vcf`: it falls back to a symlink and carries on.

**Device run of 2026-11-07 (fifth session, 16:45–17:25, driven jointly: the user operated the
UI, the model read the logs).**

- [x] **The speaker button and the proximity lock — verified, after the fix below.** On an
  audio call A→B: `CallViewModel: toggleSpeaker: true (conf=5185414353139926)` →
  `HardwareService: toggleSpeakerphone: true` → `REL jami:callProximity`; the second tap gave
  `toggleSpeaker: false` → `ACQ jami:callProximity`; hang-up released it with none held
  afterwards. The user confirmed the audio audibly switched to the loudspeaker and back. The
  conference id *is* the call id, which is the seeded wrapper's key — so the new path is what
  ran. This closes the half the second session could not exercise.

- [x] **The non-live 1:1 call — the addressing half is verified; the fix of 2026-11-06 was
  wrong and has been replaced.** Tested on B's conversation `2cb2dbae…`, the very one named in
  the original report, which is still permanently stuck cloning (`Clone failed. Re-clone in
  5s`). First run, with the old fix in place, reproduced the bug exactly:
  `peerUri=swarm:2cb2dbae… isGroupCall=true` → `placeCall ← JNI: callId=''`. After the fix:
  `peerUri=66d57f55… scheme=null isGroupCall=false` → `daemonId=4983077485835589` →
  `jamiaccount.cpp: New outgoing call with jami:66d57f55…`. The daemon placed a real call to
  the real peer.
  - **Why the old fix failed.** It judged a `Syncing` conversation by `requestMode`, assuming
    that holds the daemon's real mode. It does not: the daemon cannot know a mode before it has
    cloned the repo that states it. Measured: `mode=Syncing requestMode=InvitesOnly
    contacts=[peer, me]` on a 1:1. Membership is now the discriminator, which is both what
    `Conversation.isGroup` already used and what jami-client-android's `isGroup()` uses — and
    it is why `ChatViewModel` logged `isGroup=false` for the same conversation in the same
    second that `CallViewModel` called it a group.
  - **Also confirmed:** the 15 s watchdog works. Before the shape fix the call failed visibly
    with `hosted conference never started after 15000ms`, instead of sitting on "Calling…".
  - **Not verified:** that the call is *answerable* on the far side from a syncing
    conversation. Every attempt was refused or mishandled by A for the unrelated reasons below.

**Found during that run — five things, none of them fixed:**

- [ ] **An incoming call is still reported to Telecom as outgoing** — the same failure
  `c77dbec` addressed this morning, reproducing on the Pixel 7a at 17:21 with that fix present.
  Daemon: `New incoming call from d9f8c7f0…` → `Create a new [INCOMING] SIP call` →
  `onCallStateChanged: CONNECTING` (before the incoming signal, as before) → `INCOMING`.
  Client: `updateAudioState: status=CONNECTING incoming=false`, then `status=RINGING
  incoming=false`, `JamiTelecomManager: placeCall`, `createConnection … isIncoming: false`,
  and 30 s later `BUSY` → `OVER code=486`. No ringtone, no answerable UI.
  `Call.isIncoming` stayed false for the call's whole life, so neither
  `ConversationFacade`'s `incomingCall` flag nor `JamiTelecomManager`'s `misreportedAsOutgoing`
  re-report (which needs `call.isIncoming == true`) ever fired. `addCall`'s direction-wins fix
  is present and reads correctly, so the question is why `onIncomingCall`'s replacement never
  reached the state the RINGING handler saw — a different trigger from the one `c77dbec` fixed,
  not a regression of it. **This blocks any end-to-end verification of incoming calls on A.**
- [ ] **A refused incoming call still drives the Telecom outgoing path.** With "Block unknown
  contacts" on, `onIncomingCall` refuses and returns before registering the call — but
  `JamiTelecomManager: placeCall` still fired 16 ms later, ending in
  `JamiConnectionService: onCreateOutgoingConnection: missing callId or accountId`. Refusing
  should tell the Telecom layer to stand down.
- [ ] **Re-adding a contact leaves the adder waiting forever.** A re-added B; A's UI stayed on
  "waiting for confirmation" and B was shown nothing to accept, because B had never removed A.
  This is the blocker already recorded at the top of this file, reached from the other
  direction — and it has a sharper consequence than "no invitation appears": with **Block
  unknown contacts** on, A then refuses calls from a peer it is actively syncing swarms with
  (`onIncomingCall: refusing call from a non-contact`). A device that is git-syncing with a
  peer should not treat that peer as a stranger.
- [ ] **A holds a conversation with itself.** `231af38e…` on A has A's own fingerprint in both
  `admins/` and `invited/`, no members, and never syncs; messages into it log `Message sent:
  failure`. Origin unknown — it predates this run.
- [ ] **Nothing stops a call to your own account.** Calling from that self-conversation placed
  a real call to A's own id: `account_manager: Found 1 device(s) for 66d57f55…` → `Ask
  a08f24eb… for a new SIP channel` (its own device) → `No channeled socket with this peer` →
  `FAILURE code=500` in 300 ms, with nothing said in the UI.

**Device run of 2026-11-07 (third session, 10:20–10:55). Driven jointly: the user operated the
UI, the model read the logs and took the screenshots. Everything below was verified on the
Pixel pair.**

- [x] **The timestamp no longer overlaps the bubble text.** The room for the overlay was
  reserved with two trailing *spaces*, and trailing whitespace is dropped when a line is
  measured — so the 12 dp tick had no reserved width at all. It is now an inline placeholder
  (a glyph box the layout cannot trim), `spacing.xs + 14 dp` on outgoing messages, applied to
  the normal and the search-result bubble. On the device, messages of 25/27/29/31 characters:
  at 27 the text ends, a clear gap follows, and `10:28 ✓✓` sits inside the bubble; from 29 the
  overlay wraps to its own line instead of printing on top of the text.
- [x] **The details screen never shows "Unknown" again**, verified three ways against B's
  throwaway account: with no profile at all it shows the 40-hex id (as the chat and the list
  do); when a vCard arrives it shows the name; and when the peer *clears* its name the last
  known name is kept rather than blanked. A blank `FN` is no longer stored as a name
  (`ContactService.onProfileReceived`), `Contact.displayName` treats blank as absent, and the
  header falls back name → registered name → id instead of the untranslated literals
  "Unknown" / "?".
- [x] **The chat screen leaves when its conversation is deleted.** `ChatState.isGone`, set on
  `ConversationEvent.ConversationRemoved` for the open conversation. On the device, one tap on
  "Kontakt löschen" closed *both* screens and landed on the conversation list, 16 ms end to
  end: `contact_list.cpp removeContact: 9045ef4c… (banned: false)` →
  `ConversationFacade: onConversationRemoved: 46814601762c…` →
  `ChatViewModel: conversation 46814601762c… was removed; leaving the chat`.

**The run's own find — a profile arriving while the details screen is open was ignored
(fixed, verified):**

- [x] **`refreshContact()` opened with `findContactInCache(...) ?: return`.** That cache is
  filled lazily and is keyed by the *full* URI string, while this screen resolves its peer from
  the conversation's member list — so for a peer that was not in it, every `ProfileUpdated`
  was dropped on the first line. On the device the vCard arrived at 10:37:03 and the open
  screen kept showing the hex; closing and reopening it showed the name, which is what gave
  the cause away. The refresh now works from the profile alone and each field falls back to
  what is already displayed. Verified after the fix: with the screen open, B's rename to
  "Testname2" changed the header and the avatar initial live, with no reopen.
  - Two related things were fixed in the same pass and are covered by tests but were not
    separately exercised on hardware: a presence ping (which carries no profile) used to
    overwrite the resolved name with `contact.displayUsername`, demoting a named peer to its
    40-hex id, and the avatar was never refreshed at all.

**Smaller things seen in that run, not addressed:**

- ~~"Kontakt löschen" on the details screen deletes immediately, with **no confirmation
  dialog**.~~ **Fixed and verified on hardware 2026-11-07 (fourth session, 12:05–12:15, driven
  jointly).** The button now opens a `JamiAlertDialog` (`conversation_action_remove_this_title`
  / `_message`, destructive styling), the same guard the conversation list already had on its
  own delete. A throwaway contact was re-established A→B for the test — B auto-confirmed the
  trust request because B had never removed A, so no invitation card appeared, which is
  correct. Cancel: no `removeContact` in the log at all and the contact still in A's `contacts`
  file. Confirm: the full chain in 18 ms — `contact_list.cpp removeContact: 9045ef4c…` →
  `ConversationFacade: onConversationRemoved: 8f856248…` → `ChatViewModel: … leaving the chat`
  → `conversationrepository.cpp Erasing …/conversations/8f856248…/`, landing on the list.
  - `action_cancel` turned out to have **no translation in any locale** (it is a KMP-only key,
    absent from the Android client), so a German dialog read "Cancel" next to German body text.
    German was added (`Abbrechen`); the other 30-odd locales still fall back to English for
    this key and for the other six dialogs that use it.
- When a peer's profile name goes blank, the message notification falls back to the raw swarm
  id — `android.title=String (46814601762c: 46814601762c)`, the conversation id twice, once as
  the title and once as the sender. The details screen kept the last known name at the same
  moment, so the two disagree.
- A peer's profile **name** is not re-read from disk at startup; only the avatar is
  (`VCardUtils.loadPeerProfileFromDisk`). After a reinstall or restart a peer whose name was
  known shows its hex id again until a new vCard arrives.
- The details screen of a **1:1** swarm shows the group UI — a "Members" list with an
  Administrator badge and "Gruppe verlassen" — because that section is gated on
  `state.isSwarm` rather than on the conversation being a group. Same confusion between "is a
  swarm" and "is a swarm *group*" as the `Mode.Syncing` bug fixed on 2026-11-06.
- B's account switcher lists the current account as "Jami" with a green "J" avatar while the
  home screen's top-left avatar for the same account is a magenta "M".

**Seen on 2026-11-07 while setting up the delete-confirmation test, not addressed:**

- **A deleted conversation leaves a dead duplicate on the peer.** A deleted its side of
  `46814601762c…`; B never removed A, so B kept that conversation in its `convInfo` with
  `removed` unset. When A re-added B, the daemon created a *new* conversation
  (`8f856248…`) and repointed B's contact record at it, leaving B's list showing the same
  peer twice — one live thread and one that no longer exists on the other side. Whether the
  stale entry should be pruned when the peer's contact record moves to another conversation
  is a client question (the daemon keeps both).
- **`ChatViewModel.loadConversation` runs once more after the conversation was removed**:
  141 ms after "leaving the chat" the log shows `loadConversation: id=8f856248… contact=null
  peerUri= isGroup=null`, i.e. the screen re-loads the conversation it has just been told to
  leave, during the pop. Harmless (the screen is going away and the load finds nothing) but
  it is wasted work on a `LaunchedEffect(conversationId)` that should not re-fire.

**Still owed a verdict**

## Calling

- [x] **Screen sharing not transmitted** — Fixed: `CameraService.startScreenSharing()` now wires `VirtualDisplay` output through a `Surface` obtained from `JamiService.addVideoDevice`; `HardwareService.startCapture("desktop")` is called by the daemon after the media renegotiation completes.

- [x] **Video calls not working** — Fixed: `HardwareService.addPreviewVideoSurface()` now registers the `TextureView` as `mCameraPreviewSurface` and calls `cameraService.openCamera()`; `startCameraPreview()` opens and starts the camera session; `hasCamera()` / `cameraCount()` delegate to `CameraService`.

- [x] **Picture-in-Picture not working** — Fixed: added `android:supportsPictureInPicture="true"` and the required `android:configChanges` to `MainActivity` in `AndroidManifest.xml`; `enterPipMode()` is now guarded to only fire when a call is active.

## UI / Live Updates

- [x] **Live cross-device own-profile avatar update** — When the user changes their avatar on another device, the change is not reflected live in the HomeScreen search bar avatar (only after app restart).
- [x] **VCard Bitmap Scaling and Caching** — Implemented `VCardService` (Koin `single`) with `loadLocalAvatar`/`loadPeerAvatar`; scales to 512px JPEG q88 via `scaleImageBytes` expect/actual, caches to disk under `getCachePath()` with mtime freshness check, plus an in-memory map. `ConversationsViewModel` and `ContactService` now route avatar loads through `VCardService`; `ContactService.onProfileReceived` invalidates the cache on profile update.
- [x] **Enhanced Call Handling** — Ported media-change answering: `CallService.mediaChangeRequested` mutes video when the call has no active video, mirrors audio-mute state, then calls `daemonBridge.answerMediaChangeRequest`. Added `answerMediaChangeRequest` to `DaemonBridgeApi` (Android: JNI via `VectMap`/`StringMap`; others: no-op stub). Conference participant reconciliation ported to `onConferenceCreated` and `onConferenceChanged`: queries `getParticipantList`/`getConferenceDetails`, populates `Conference.participants`, and demotes back to a plain call when one participant remains.

## Conference & Calling (Cross-Platform)

- [x] **iOS/macOS conference hold/unhold/resume/setActiveParticipant** — Added `holdConference`, `unholdConference`, `resumeConference`, `setActiveParticipant` to `JamiBridgeWrapper.h` and `JamiBridgeWrapper.mm` (delegating to `libjami::holdConference`, `libjami::resumeConference`, `libjami::setActiveParticipant`); wired in `DaemonBridge.ios.kt` and `DaemonBridge.macos.kt`.

## Migration

- [x] **Account migration dialog** — Added `AccountService.migrateAccount()` (sets `ARCHIVE_PASSWORD` via `setAccountDetails`; result arrives via `AccountEvent.MigrationEnded`). New `MigrationDialog.kt` composable: password input with 6-char guard, Migrate/Skip actions, observes migration result for success/error feedback. Wired in `JamiNavigation.kt`: overlay shown when `needsMigration && !migrationDismissed`, dismissed per session.

## QR Code

- [x] **QR camera scanning** — New `QrCodeScannerView` expect/actual: Android (CameraX `ImageAnalysis` + ZXing `MultiFormatReader`, requests `CAMERA` permission), iOS (`UIKitView` wrapping `AVCaptureSession` + `AVMetadataObjectTypeQRCode`), Desktop/macOS/JS stubs. `QrScanScreen` shows camera view by default; Edit button in top bar toggles manual text-paste fallback; unrecognised scan falls back to manual mode.

## Chat

- [x] **Audio recording in chat** — New `AudioRecorderService` expect/actual (Android: `MediaRecorder` MPEG4/AAC; iOS: `AVAudioRecorder`; others: no-op stubs). `ChatViewModel`: `isRecordingAudio` `StateFlow` + `startAudioRecording` / `stopAudioRecording` / `cancelAudioRecording`; completed recording piped to `sendFile`. `ChatScreen`: recording indicator row with mic icon, Cancel and Send buttons shown while recording.
- [ ] **Video recording in chat** — Deferred: requires Camera2/AVFoundation capture session. Menu item currently shows snackbar "Video recording coming soon".
- [ ] **Chat extensions / plugins** — Jami plugin system not yet ported to KMP. Menu item currently shows snackbar "Plugins not yet supported".

## Location Sharing

- [x] **OsmMapView — iOS** — Full `MapKit` implementation: `UIKitView` wrapping `MKMapView`, `CLLocationManager` for live location updates, `MKPointAnnotation` per contact marker, `setRegion` when `centerOnMyLocation`.

## Biometric Auth

- [x] **BiometricService — macOS** — Full implementation ported from iOS: same `LAContext`, `canEvaluatePolicy`, `evaluatePolicy`, Security framework Keychain APIs (available macOS 10.15+). On Macs without biometric hardware `checkAvailability()` correctly returns `NO_HARDWARE`.

## Contacts

- [x] **BlockedContactsScreen always empty** — Added `blockedContacts: StateFlow<List<ContactItem>>` to `ContactsViewModel` (filters `Contact.Status.BLOCKED`); `BlockedContactsScreen` now collects it; `unblockContact()` wired to `contactService.addContact`.

## Sharing

- [x] **ShareUtils — iOS** — Implemented with `UIActivityViewController(activityItems: [body])` presented from `UIApplication.sharedApplication.keyWindow?.rootViewController`.
- [x] **ShareUtils — macOS** — Implemented with `NSSharingService.sharingServicesForItems([body]).first()?.performWithItems([body])`.

## Group Conversations

- [x] **Leave conversation + add/remove group members** — Added `addConversationMember()` / `removeConversationMember()` to `ConversationFacade`; added `leaveConversation()`, `addMember()`, `removeMember()` to `ContactDetailsViewModel`; `ConversationDetailsScreen` shows a `GroupMemberSection` composable when `state.isSwarm` with member list, admin-only Add/Remove buttons (with confirmation dialogs), and Leave group button.

## Localization (Hardcoded Strings)

- [x] **Key hardcoded strings fixed** — `PendingRequestsScreen` title uses `invitation_card_title`; `JamiMessageInput` content descriptions use `Res.string.content_desc_*`; `AccountCreationViewModel` errors converted to `UsernameCheckError`/`AccountCreationError` enums translated at screen level. Remaining hardcoded strings (conversation type labels in `ContactDetailsViewModel`, date labels "Today"/"Yesterday" in `ChatViewModel`, biometric prompts in `AccountSettingsViewModel`) require passing values from the Composable layer and are deferred.

## Accounts

- [x] **SIP account creation** — `CreateAccountScreen` now has a Jami/SIP tab toggle; SIP tab shows server, username, password, port (optional), and display name fields; `AccountCreationViewModel.createSipAccount()` builds `ACCOUNT_TYPE=SIP` details map and calls `accountService.addAccount()`.

## Android Service

- [x] **Foreground service notification not showing on Pixel 2** — Root cause: channel `"jami_daemon_service"` was originally created at `IMPORTANCE_MIN` and Android channels are immutable once created. Fixed by bumping to `"jami_daemon_service_v2"` (forces fresh creation at `IMPORTANCE_LOW`). Also replaced system `android.R.drawable.*` icons with the app's own `ic_jami_24` across all notification types.

## Call Recording

- [x] **Call recording (Android)** — `toggleRecording` / `getIsRecording` exposed in `DaemonBridgeApi`, wired through `CallService` (action + `onRecordingStateChanged` callback fed by the daemon's `RecordingStateChanged` signal), `CallViewModel` (`CallState.isRecording` + `toggleRecording()`), and a `FiberManualRecord` record button in `OnGoingControls`. `Call.isRecording` promoted to observable. See `doc/plan_call_recording.md`.
- [ ] **Call recording (iOS/macOS)** — Stubbed (returns false, inert). Requires `JamiBridgeWrapper.h/.mm` to expose `toggleRecording`/`getIsRecording` + a `RecordingStateChanged` delegate, then a `libJamiBridge.a` rebuild. See "iOS / macOS follow-up" in `doc/plan_call_recording.md`.

## Push Notifications

- [x] **FCM push notifications (Android)** — `PushServiceManager` owns the token and reconciles it with the `ConnectivityMode.GOOGLE_SERVICES` setting (previously stored but never applied); `JamiFirebaseMessagingService` receives pushes and feeds `AccountService.pushNotificationReceived`; `PushForegroundService` covers the reconnect window for high-priority call pushes. Firebase is opt-in at build time. **Delivery still needs infrastructure** — the push is sent by the DHT proxy, and the public one only holds SFL's FCM credentials. See `doc/push-notifications.md`.
- [x] **APNs push notifications (iOS)** — `IOSPushServiceManager` mirrors the Android policy layer; `AppDelegate` registers for both APNs and PushKit VoIP and forwards tokens via `IOSPushHelperKt`. A VoIP push reports a placeholder call to CallKit synchronously (iOS 13+ requirement) which the real daemon call then adopts. `UIBackgroundModes` + `aps-environment` entitlement added. **Compiled and run on a Mac since 2026-10-14** — the simulator logs `no valid aps-environment entitlement`, which is an artifact of building with `CODE_SIGNING_ALLOWED=NO`, not a missing entitlement. Delivery is still blocked on the proxy, as on Android. See `doc/push-notifications.md`.

## Deep Links & Intents

- [x] **URI scheme intent filters** — Single `intent-filter` on `MainActivity` handles `ring`, `jami`, `sip`, `tel` schemes with `ACTION_DIAL` + `ACTION_VIEW` + `DEFAULT` + `BROWSABLE` categories. Mirrors `HomeActivity` filter in reference manifest.
- [x] **Share-to-Jami (ACTION_SEND)** — `MainActivity` now carries `ACTION_SEND` and `ACTION_SEND_MULTIPLE` intent filters with `mimeType="*/*"`; Jami appears in the Android share sheet.

## Chat — Missing Features

- [x] **Message reactions UI** — `ReactionGroup` data class aggregates emoji+count+isMine. `ChatViewModel` handles `ReactionAdded/ReactionRemoved` events via `rebuildMessageReactions`; `interactionToMessageItem` snapshots reactions via `groupReactions`. `ConversationFacade.onReactionAdded/Removed` now updates the `Conversation` model before emitting events; `swarmMessageToInteraction` loads history reactions from `SwarmMessage.reactions`. `ChatBubble` shows reaction pills below bubbles and an emoji quick-picker (👍 ❤️ 😂 😮 😢 👏) in the long-press menu.
- [x] **Read receipt display (checkmarks)** — `DeliveryStatus` enum (SENDING/DELIVERED/READ/FAILED) added to `ChatViewModel`. `aggregateStatus()` derives it from `Interaction.statusMap` (DISPLAYED→READ, SUCCESS→DELIVERED, else SENDING). `MessageItem.deliveryStatus` wired from `interactionToMessageItem` TEXT case. `ChatBubble` overlays a `Row[timestamp, Icon]` at bottom-right of outgoing bubbles: `Done` (single tick, gray) = SENDING, `DoneAll` (double tick, gray) = DELIVERED, `DoneAll` (double tick, primary color) = READ, `Warning` (error color) = FAILED. See the 2026-08-16 entry below for the FAILED state + retry addition.
- [ ] **@Mentions in group chat** — Reference client parses `@username` in message text and highlights them. No mention system in KMP's chat UI or viewmodel.
- [x] **Full-screen image viewer** — `ui/screens/MediaViewerScreen.kt`.
- [x] **Video message playback in chat** — `ui/components/video/VideoPlayerView.kt` + `ui/screens/VideoPlayerScreen.kt`, routed via `MediaNavigationState`.
- [x] **Retry failed file transfer** (2026-09-25) — Tap a failed outgoing transfer bubble
  (`TransferStatus.isError`: `TRANSFER_ERROR`/`TRANSFER_UNJOINABLE_PEER`/`TRANSFER_CANCELED`/
  `TRANSFER_TIMEOUT_EXPIRED`/`FAILURE`) to re-send, mirroring `ChatBubble`'s failed-text retry.
  `ChatViewModel.retryFileTransfer()` re-dispatches `accountService.sendFile()` from the file's
  existing `destinationPath` (already stored in the conversation cache by the first attempt, so
  no re-copy needed). The original failed interaction is real swarm history (unlike a text
  `pending-*` bubble) and stays visible; retry just adds a new transfer attempt. No daemon-level
  `resendFile`/`retryDataTransfer` exists, and `jami-client-android` was checked and has no
  equivalent retry feature either — re-sending is the only available mechanism on both.
- [x] **Group creation, resampled on the reference picker** (2026-10-30, `51084e8`) — "New Group"
  used to navigate to the search screen and rely on a toggle plus a name field. It now opens a
  bottom sheet over the search view, as `HomeFragment.startNewSwarm` ->
  `ContactPickerFragment` does: **no search**, a grid of the contacts you already have, picks
  shown as avatar chips with an X, an empty state, and one button. No group-name step, because
  the reference has none — the name is set afterwards from conversation details. One picked
  contact still makes a swarm rather than a 1:1, matching `ConversationFacade.kt:714`, which
  hands whatever was picked to `startConversation` unchanged.
  - `Conversation.title` now applies `ConversationItemViewModel.getTitle`'s rule (profile name ->
    the other party -> up to three member names with `+ N`), so an unnamed group reads
    "marco100, peace4us" in the list and the chat header instead of its swarm id.
  - **Three defects found behind one symptom** ("picking a second person erased the first",
    reported 2026-10-30). The selection was never *rendered* — it lived only in the row
    checkmark and the buttons' `enabled`, so searching for the second person took the first out
    of the result list and nothing else showed it. The data was never lost: `search()` does not
    touch `selectedContacts`. Second, the browsable list did not exist: the LazyColumn rendered
    `publicDirectoryResults` and nothing read `conversationResults`, so an empty query gave a
    blank screen. Third, the one function that did fill a list threw on a group (below).
  - Verified on the Pixel 7a: the sheet lists contacts in a grid, two picks show two avatar
    chips, the button creates the swarm, adds both members and lands in the new conversation.
- [x] **The syncing placeholder title is localized** (2026-10-30) — `Conversation.title`
  returned the literal `"(Syncing)"` for a swarm with no members yet, as the reference does
  (`ConversationItemViewModel.getTitle` hardcodes it untranslated). It is a model getter and
  cannot reach Compose resources, so it now returns `""` and the new `isUnnamedSyncing` tells
  the UI what the empty title means. `conversationDisplayName()` (new, in
  `ui/components/content/`) resolves it for HomeScreen, SearchScreen, SharePickerScreen and the
  chat header alike, so the lists and the header cannot drift apart.
  - `conversation_title_syncing` is KMP-only: upstream has no title string for this state, and
    the existing `conversation_syncing` ("Downloading conversation from peer") is a body message
    used in a banner, not a header. Added to `values/`, `values-de/`, `values-de-rDE/`.
  - **Test-verified only.** Seeing it live needs a conversation caught mid-sync, i.e. a fresh
    invitation accepted while its members have not arrived; the pending invitations were
    cancelled before that check could run. Four tests cover the model and `ChatViewModel`
    (empty title + flag, non-syncing fallback to the swarm id, flag clearing once a member
    joins), but nothing exercises the Compose substitution — there are no Compose UI tests here.
- [x] **Join/invite rows name people instead of printing their Jami id**
  (2026-10-30) — a group's system messages read
  `d9f8c7f0bf768178542d402c6ae233072bfff6df ist der Unterhaltung beigetreten.`
  `Contact.displayUsername` falls back to the 40-hex id when neither a profile name nor a
  registered one is known, and nothing ever asked for one: the lookups in `ConversationFacade`
  fire on registration-state changes and smartlist loads, neither of which covers the members of
  a group you just joined. `ChatViewModel.displayNameFor` now serves both the live and the
  history path and requests one name-service lookup per unknown id, and
  `AccountEvent.RegisteredNameFound` patches the rendered rows (they hold a string, not a
  `Contact`, so nothing re-reads the name by itself).
  - **The local user is the case no lookup can fix** — you cannot resolve yourself, and the self
    `Contact` carries no profile, so "you joined" stayed hex even after the rest worked. It now
    comes from the account: profile name, else registered name, else alias.
  - **A live member row was also about the wrong person.** The sentence is about
    `body["uri"]`, but the live path named the commit *author* — for an invite, the inviter — so
    a row read "alice was invited" when Alice had invited Bob, and changed identity on reload
    because the history path already used `body["uri"]`.
  - `conversation_contact_added` was missing the space before "ist" in both German files.
    Upstream's `values-de` has it right and its `values-de-rDE` has the same typo, so
    `values-de` was a sync error on our side and `values-de-rDE` now deliberately diverges —
    worth reporting upstream.
  - Verified on the Pixel 2: the three rows that printed hex now read "peace4us ist der
    Unterhaltung beigetreten.", "marco100 wurde eingeladen…", "peace4us wurde eingeladen…".
- [x] **Opening a just-accepted invitation no longer shows its swarm URI as the title**
  (2026-10-30) — the accept path navigated with `conversationUri.uri`, which carries the
  `swarm:` scheme, while every other caller passes a bare id. `ChatViewModel.loadConversation`
  then built `swarm:swarm:<id>`, matched nothing, and fell through to showing its own route
  argument as the header. The title was only the visible symptom: with `conversation == null`
  the screen also loaded no history, marked nothing read and never set `isVisible`.
  `loadConversation` now normalises the argument through `Uri.fromString(...).rawRingId` (a
  no-op on a bare id), and the two accept sites pass `rawRingId` like everyone else
  (`HomeScreen.kt:350`, `PendingRequestsScreen.kt:107`).
  - Still English-only, and untouched here: `Conversation.title` returns the literal
    `"(Syncing)"` for a swarm with no members yet. It is a model getter, so it cannot reach
    Compose resources; the reference resolves `conversation_syncing` at the UI layer instead.
- [x] **A second invitation from the same person no longer crashes the requests list**
  (2026-10-30) — reported from the Pixel 2 as "accepting an invitation crashes the app"; the
  crash was in fact on *rendering* the list, before any accept. Both pending-request
  `LazyColumn`s keyed their rows by `PendingRequestItem.ringId`, the **sender**'s id, while the
  loader de-duplicates on the **conversation** id. Two invites from one contact are therefore two
  legitimate rows sharing a key, and Compose throws `IllegalArgumentException: Key "<ringId>" was
  already used` out of the measure pass — so it takes the whole screen down, not just the row.
  Both screens now key on `conversationUri.uri`, which is also what `PendingRequestItem.equals`
  already used. `HomeScreen.kt:345`, `PendingRequestsScreen.kt:102`.
  - Verified on the Pixel 2 with three invitations from `unitedhumans`: all three render,
    accepting one lands in the new conversation and leaves `Requests (2)` intact, no
    `FATAL EXCEPTION` anywhere in the run.
  - The new `ConversationsViewModelTest` case pins the invariant (two invites from one sender
    stay two rows, one `ringId`, two `conversationUri`s) but cannot exercise the Compose key
    itself — there are no Compose UI tests in this module, so the fix proper rests on the
    device check.
- [x] **A group conversation no longer crashes the screens that list conversations**
  (2026-10-30, `bbba9fb`) — `Conversation.contact` throws by design for a group, and six places
  walked *all* conversations and read it anyway (`NewConversationViewModel` twice,
  `ConversationsViewModel` twice, `ContactDetailsViewModel`, `PendingRequestsViewModel`). Three
  of them wrote `conv.contact ?: continue` or `conv.contact?.uri`, which reads as safe and is
  not — the getter **raises**, so neither `?:` nor `?.` catches anything. `getPending()` holds
  group invitations, so two of those sites would have taken down the home screen for anyone
  invited to a group. `Conversation.contactOrNull` returns null for a group and is used at every
  walking site; `contact` keeps its throw as the 1:1 invariant guard.
  - A seventh, different in shape: `ChatViewModel.loadConversation` read `contact` for the
    *open* conversation inside its own `try/catch`, so opening any group chat was not a crash
    but a **silent abort** — the header stayed on default state. Found by opening a freshly
    created group; fixed and confirmed (`loadConversation: … contact=null isGroup=true`).
  - No fixture anywhere put a group in a conversation list, which is why 824 passing tests said
    nothing about any of it. `GroupConversationContactTest` and the picker fixtures now do.
- [x] **Message long-press: share file** — Done 2026-09-18 (`c4d413f`): file/image messages have a long-press menu (Open / Save / Share / Delete file once complete, Delete on own messages), and the image viewer has Share/Save.

Parity gaps found 2026-09-18 comparing chat interactions with jami-android-client
(`ConversationAdapter`, `AccountService.getInteraction`):

- [x] **Replies** (2026-09-19) — **Reply** in the text/file long-press menu and swipe-to-reply;
  a reply bar above the composer (`ChatState.replyingTo`); the sent message carries `reply-to`
  (also through the durable outbox); reply bubbles quote the original ("You" for own messages),
  tap scrolls to it, and an original not in history yet is fetched once via `loadSwarmUntil`.
  Tests: `ReplyTest`. **Completed 2026-10-29** (mini-plan messaging F4): replying *with* a file
  now carries `reply-to` through `sendFile`'s `parent`, a retry keeps it, and the quote is drawn
  on file bubbles. Verified on device against the swarm commit. iOS/macOS still drop the parent —
  the Apple wrapper's `sendFile` has no such argument.
- [ ] **Markdown** — reference renders message text with Markwon (bold, italics, code, …); KMP
  shows plain text. *Clickable links done 2026-09-18:* http(s) URLs in bubbles are `LinkAnnotation`s.
- [ ] **Read markers** — reference shows peers' avatars at the last message each has read
  (`Conversation.lastDisplayedMessages`) and a status icon only on the last sent message
  (`lastSent`). KMP shows a tick on every outgoing message and no read avatars.
  `Conversation.updateSwarmInteraction` already records `lastDisplayedMessages` / `lastSent`.
- [ ] **Full emoji picker for reactions** — reference has a "more" button opening a full emoji
  picker; KMP offers 6 fixed emojis.
- [x] **Share text** — Done 2026-09-18: "Share" in the text message long-press menu (`shareText`).
- [x] **Edit history** — Done 2026-09-18: "Message history" in the long-press menu of edited
  messages opens a dialog with every version, newest first. `swarmMessageToInteraction` now keeps
  `SwarmMessage.editions` as `Interaction.history` (libjamiclient `addEdits`).

## Home Screen — Missing Features

- [x] **Conversation filtering tabs** — Done. `HomeScreen` has a `JamiFilterChip` row (All / Unread / Groups) wired to `ConversationsViewModel.setFilter(ConversationFilter)`; `state.activeFilter` drives the filtered list (verified 2026-06-26).
- [x] **Long-press context menu on conversation rows** — Done. Each row is wrapped in `combinedClickable` (`onLongClick` opens a `DropdownMenu` with Pin/Unpin, Mute/Unmute, Block, Delete). Pin/mute state is read from `SettingsRepository` and surfaced as row indicator icons; pinned conversations float to the top of the list. `ConversationsViewModel` gained `toggleConversationPin`, `toggleConversationMute`, and `blockConversation` (verified 2026-06-26, commit e6cd592).
- [x] **Swipe-to-delete** — Done. `SwipeToDismissBox` EndToStart now sets `pendingDelete` and shows a `JamiAlertDialog` confirmation (instead of silently deleting); confirming calls `ConversationsViewModel.removeConversation(id)`. Swipe-to-archive proper (distinct from delete) is still not implemented (verified 2026-06-26, commit e6cd592).

## Calls — Missing Features

- [x] **Proximity sensor (screen off during calls)** — Fixed 2026-11-07. `proximityEnabled` was
  stored in `CallSettings` (defaulting to on), offered by no screen and read by nothing, so the
  screen stayed lit against the user's ear for every audio call. `HardwareService.android` now
  holds a `PROXIMITY_SCREEN_OFF_WAKE_LOCK` (`ACQUIRE_CAUSES_WAKEUP or ON_AFTER_RELEASE`, not
  reference-counted, as `CallFragment.handleCallWakelock` does) while a call is `CURRENT`,
  audio-only and not on speaker; it is released when the call ends and when the speaker goes on,
  and re-taken when it goes off. Guarded by `isWakeLockLevelSupported` and given a 4 h timeout as
  a safety net against a missed release. A toggle was added to App settings → Calls
  (`pref_proximity_title`), with `SettingsRepository.updateProximityEnabled` persisting it;
  covered by `toggleProximityPersistsToCallSettings`. **Android only** — not yet verified on a
  device, and iOS has `AVAudioSession`'s own proximity handling, not this.
- [ ] **Haptic feedback on call answer/decline** — No `Vibrator` / `HapticFeedbackConstants` calls in the call flow.
- [ ] **Bluetooth headset routing** — `toggleSpeaker()` backend exists; full 3-way routing (earpiece / speaker / BT) may not be fully wired through `AudioManager`.

## Notifications — Missing Features

- [x] **Inline reply handler** — `NotificationActionReceiver.handleReply` reads `RemoteInput.getResultsFromIntent` → `KEY_REPLY_TEXT`, calls `daemonBridge.sendMessage(accountId, conversationId, text, "", 0)`, then cancels the notification. Mark-read action calls `setConversationPreferences` + cancels notification.

## Location Sharing — Missing Features

- [x] **Location share duration selector** — `LocationSharingScreen` has 10 min / 1 hour `FilterChip` duration selector wired to `LocationSharingViewModel.selectDuration()`.
- [x] **Multi-peer location on same map** — `ContactLocationsInfo` map in `LocationSharingState` tracks all peers; each is rendered as a separate `ContactMarker` / `MKPointAnnotation`.
- [x] **"Center on me" button** — `LocationSharingScreen` has a `FloatingActionButton` (MyLocation icon) wired to `viewModel.centerOnMyLocation()`; Android OsmMapView now has a dedicated `LaunchedEffect(centerOnMyLocation)` to re-center even when the location has not changed.
- [x] **Location accuracy radius circle** — Android: `Polygon` overlay drawn around the user marker scaled to `GeoLocation.accuracy` meters (flat-earth approximation, accurate to <1% for radii ≤20 km). iOS: `MKMapView.showsUserLocation = true` renders the native blue-dot accuracy circle automatically via MapKit.

## App Settings — Missing Features

- [ ] **Battery optimisation exemption prompt** — Reference prompts the user to whitelist the app. No `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` intent in KMP.
- [ ] **Language selector** — Reference has a locale preference. Not found in `AppSettingsScreen`.
- [ ] **Display name in notifications setting** — Reference lets users hide contact names from notifications for privacy. Not in `AppSettingsScreen`.
- [x] **Log export / debug mode** — `DebugLogsScreen` + `DebugLogsViewModel` (AppSettings → Advanced
  → Debug logs): scrollable monospace view, refresh, share. `LogCapture` expect/actual reads logcat
  on Android and is a no-op elsewhere. (This item was left unchecked long after the screen landed.)
- [ ] **App shortcuts** — Reference `AndroidManifest.xml` declares conversation shortcuts. Not in KMP manifest.

### Contact requests raised no notification at all (2026-11-06)

Still owed a device verdict.

- [x] **`showIncomingTrustRequestNotification` was never called** — it was implemented on all
  five platforms (Android, iOS, macOS, desktop, web) and invoked from nowhere in production,
  the only reference being a test. An incoming contact request appeared in the app's pending
  list and nowhere else: no notification, no sound, nothing while the app was backgrounded.
  - `ConversationFacade.refreshTrustRequestNotification()` now drives it from every place the
    account's pending set moves — a request arriving, one being declined from another device,
    and our own accept/discard. The reference client gets the same effect by subscribing to the
    account's pending subject; this is the same thing without an Rx subject to subscribe to.
  - Called **after** the "block unknown contacts" gate, so a withheld request stays silent.
  - **Dedupe:** the pending set is republished on every change, so without a guard the user
    would be re-notified about requests they had already seen and left pending. Two methods on
    `PreferencesService` — `hasRequestBeenNotified` / `setRequestNotified` — persist a flag
    under the same `req_<account>:<contact>` key that `removeRequestPreferences` already
    cleared on accept and discard, which is exactly the reference's
    `loadRequestsPreferences`/`saveRequestPreferences` pair. Persisted rather than in memory, so
    relaunching does not re-notify; cleared on accept/discard, so a later request from the same
    peer notifies again.
  - **Android content**, resampled from `NotificationServiceImpl`: one pending request names the
    sender and carries working **Accept** and **Decline** actions (new `ACCEPT_REQUEST` /
    `DECLINE_REQUEST` in `NotificationActionReceiver`, going straight to the daemon — it answers
    with `conversationReady` or `conversationRequestDeclined`, which the facade already handles);
    several collapse into `invitation_message` ("You have N contact requests"); an empty pending
    set cancels. Tapping opens the app. **Not ported:** the reference's Block action and the
    sender's avatar as the large icon (that needs a bitmap decode off the main thread).
  - **iOS** had `setBody(account.displayUsername)` — the *local* account's own name, which told
    the user nothing about who was asking. Now the sender's name, or the count.
  - macOS, desktop and web still show a generic, **hardcoded English** body ("You have a new
    contact request"). Pre-existing in those three files and left alone; they are secondary
    targets and macOS does not compile.
  - Covered by `TrustRequestNotificationTest` (6 tests), using a new
    `RecordingNotificationService` test double. `makeConversationFacade` and
    `makeTestServiceStack` now take the notification and preferences services instead of
    hardcoding stubs, and the stack exposes both — the same reason the stack was made to share
    one `SettingsRepository` on 2026-10-31.

### Two privacy settings that were dead in both directions (2026-11-06)

- [x] **Removed `PrivacySettings.allowContactRequests` and `PrivacySettings.blockedContacts`**,
  plus `SettingsRepository.blockContact()`/`unblockContact()`. Nothing wrote them, nothing read
  them, and neither had a UI control, so no behaviour changed. Both duplicated something that
  already works: `blockUnknownContacts` for the first, and the daemon's own ban list — which is
  what `BlockedContactsScreen` lists, via `ContactService.removeContact(ban = true)` — for the
  second. Removed rather than left for someone to "fix" into a second, divergent block list.
  Settings JSON is decoded with `ignoreUnknownKeys`, so stored values carrying the old keys
  still load. A comment in `SettingsModels.kt` records what went and why.

### "Block unknown contacts" was enforced nowhere (B14, 2026-11-06)

Still owed a device verdict.

- [x] **Block unknown contacts (B14)** — the setting had four references in the whole tree: the
  model field, the repository writer, the `AppSettingsState` mirror and the toggle. The screen
  promises "Only accept messages from your contacts" and nothing enforced it. Now enforced on
  both surfaces a stranger can reach:
  - **Incoming calls** — `CallService.onIncomingCall` refuses (and hangs up) a call from a
    non-contact before `addCall`/`_callUpdates`. Enforced there rather than downstream because
    `callUpdates` has four independent consumers: the notification via `ConversationFacade`, the
    call UI, Android Telecom and iOS CallKit. Refusing rather than ignoring is deliberate —
    silence would leave the caller on ringback until the daemon times out.
  - **Conversation requests** — `ConversationFacade.onConversationRequestReceived` drops a
    request from a non-contact before it reaches the pending map or the event stream, and the
    new `ConversationFacade.getConversationRequests()` filters the daemon poll that
    `PendingRequestsViewModel` and `ConversationsViewModel` do on launch. Both VMs were switched
    from `accountService.getConversationRequests()` to the facade's; without the second half the
    block would hold only until the next launch. A request with no `from` is withheld too.
  - Requests are **withheld, not declined**. The daemon keeps them, so turning the setting off
    shows them again; auto-declining would destroy a request the user never saw.
  - The predicate is `DaemonBridgeApi.isConfirmedContact` (`services/UnknownContacts.kt`), which
    asks the daemon's contact list. `ContactService`'s cache is not usable for this:
    `getContactFromCache` mints a placeholder `Contact` for any URI the app merely sees —
    including the stranger now calling — so a cache hit does not mean "contact". A banned peer
    is listed by the daemon with `banned=true` and does not count as one either.
  - `DHT.PublicInCalls` (the reference client's per-account "allow calls from unknown contacts",
    already exposed in Account → Call settings) is left alone, rather than driven from this
    app-level switch: two masters for one behaviour would be worse than none.
  - Covered by `BlockUnknownContactsTest` (8 tests). Two things noticed in passing were fixed
    straight after, in the two entries above this one.

### Quiet hours hid incoming calls (B12, 2026-11-06)

Still owed a device verdict.

- [x] **Quiet hours suppresses incoming calls (B12)** — `NotificationGuard` dropped
  `!isInQuietHours(settings)` from `shouldShowCallNotification()`; quiet hours now *silence* a
  call instead of hiding it, via the new `shouldSilenceCallNotification()`. On Android the call
  is posted to `CHANNEL_CALLS_SILENT` — `CHANNEL_CALLS` without sound or vibration and without
  `setBypassDnd` — because from API 26 the channel owns the sound and no builder call can mute a
  notification posted to a sounding channel. Importance stays `HIGH` so the heads-up banner and
  full-screen intent survive: quiet hours are about silence, not about hiding the call.
  `applySoundAndVibration()` also calls `setSilent(true)` and returns early, which covers pre-O
  devices where there are no channels at all. iOS drops `UNNotificationSound` on the same
  condition (its `RINGING` branch is already skipped in favour of CallKit). Messages and contact
  requests keep the old suppression, and blocking a call outright is left to the system's Do Not
  Disturb. Covered by `NotificationGuardTest`.

### The register-name dialog closed itself (A3, 2026-11-06)

- [x] **`AccountSettingsViewModel.loadAccount()` built a fresh state** — it constructed a new
  `AccountSettingsState` listing 15 of the 22 fields, so the seven it omitted (`linkDeviceState`
  and the whole `registerName*` group) reset to their defaults on every
  `AccountEvent.DetailsChanged` — including the event our own `setAccountDetails` triggers. In
  the app that closed the register-username dialog while the user was typing in it, and dropped
  the link-device sheet back to `Init`. Now a `copy()`, so a field added to the state later
  cannot blank itself by being forgotten here; the status-note fields that had been carried
  through by hand no longer need to be. Covered by
  `AccountSettingsViewModelTest.loadAccountDoesNotCloseTheRegisterNameDialog`.

### Lock-screen notification visibility was stored but applied nowhere (2026-11-07)

- [x] **Fixed.** App settings → Notifications offered Private / Public / Secret, persisted the
  choice in `LocalPrefKeys.NOTIFICATION_VISIBILITY`, and no notification ever read it: every one
  was posted at the platform default, so "hide content on the lock screen" did nothing.
  `AndroidNotificationService.applyUserVisibility()` now applies it to the notifications that
  carry contact content — calls, missed calls, group calls, messages (and their group summary),
  requests, file transfers, location sharing — and deliberately not to the foreground-service
  ones, which carry none and are pinned to `VISIBILITY_PRIVATE`. `NotificationVisibility`'s
  constants are **not** the platform's (`SECRET` is 2 here, -1 there), which is why the mapping
  is explicit; the doc comment claiming they matched was wrong and has been corrected.
  Android-only (iOS has no per-notification lock-screen visibility), so it is not covered by the
  desktop suite — needs a device check: lock the phone, send a message, and look at the
  notification under each of the three settings.

### Settings that were stored but applied nowhere (2026-10-31)

Three of the B10–B16 cluster in `doc/manual-test-plan-2026-09-10.md`. Still owed a device
verdict — the plan entries were rewritten to test the fix rather than confirm the bug.

- [x] **Conversation sort order (B15)** — `ConversationsViewModel.conversationComparator()` reads
  `UiSettings.conversationSort` (last activity / alphabetical / unread first) instead of the old
  hardcoded `pinned desc, timestamp desc`. Pinned rows stay on top under every mode, and every
  mode ends with a timestamp tiebreak so the order is total and stable. The setting is DHT-synced,
  so a change from another device re-sorts the loaded list in place.
- [x] **Compact mode (B16)** — `ConversationsState.isCompactMode` mirrors the setting;
  `ConversationListItem` drops its vertical padding (`spacing.m` → `spacing.xs`) and the gap
  between its two text lines. **Conversation list only** — whether chat bubbles should tighten
  too is still open.
- [x] **Auto-download on Wi-Fi / mobile data (B13)** — `ConversationFacade`'s new
  `autoDownloadAllowedOnCurrentNetwork()` gates all three auto-accept sites on
  `autoDownloadWifi` / `autoDownloadMobile`, picked by the new `HardwareService.isMeteredNetwork()`
  (Android `NET_CAPABILITY_NOT_METERED`, iOS `nw_path_is_expensive`, desktop always unmetered).
  Manual downloads stay ungated. **The iOS actual has not been compiled** — Apple targets are
  skipped on a Linux host, so `nw_path_is_expensive` needs a Mac build before it is trusted.
- Test-fixture fix that fell out of this: `makeTestServiceStack` gave `ConversationFacade` and
  `CallService` their own `SettingsRepository` instances, so a test that changed a setting through
  `stack.settingsRepository` was writing where no service read. The stack now shares one. Any
  earlier test that looked like it covered settings-driven service behaviour did not.

## Bug Fixes (2026-08-07)

- [x] **Biometric authentication completely non-functional** — `BiometricService` (Koin singleton, built with `androidContext()` before any Activity exists) cast `context as? FragmentActivity` at every `BiometricPrompt` call site, which could never succeed. Fixed by giving `MainActivity` (now a `FragmentActivity`) ownership of the prompt host; `BiometricLockScreen` was already fully wired but could never actually launch a prompt in production.
  - **Re-verified on-device 2026-08-12: the fix itself works** (enroll flow completed end-to-end — real `BiometricPrompt` fingerprint dialog appeared, "Authenticate to secure your account password", touch-to-confirm succeeded, `"Biometric enrollment successful for account: ..."` in logcat) — but on-device testing found **two new bugs in the lock-screen flow that sits on top of the fix**, neither present in the original bug report:
    1. **[Security, FIXED 2026-08-12] `AppViewModel._isLocked` (`AppViewModel.kt:53`) was not persisted and not re-derived at startup — force-stopping (or the OS killing) the app and relaunching bypassed the biometric lock entirely.** `_isLocked` starts `false` on every fresh `AppViewModel` construction; the only writer was `lockIfNeeded()` (`AppViewModel.kt:133`), called solely from `JamiNavigation`'s `ON_STOP` lifecycle observer. Reproduced: enabled biometric lock, force-stopped the app, relaunched — landed directly on the conversation list, no lock screen, no prompt. This defeated the feature's purpose, since Android routinely kills backgrounded apps under memory pressure (not just an explicit force-stop). **Fix**: `AppViewModel.init`'s `accounts.collect` block now checks `biometricService.isEnabled(accountId)` on the first `Loading -> HasAccounts` transition per process (guarded by a new `initialLockCheckDone` flag) and sets `_isLocked.value = true` before that state is exposed if biometric lock is enabled for the current (or first) account. Re-verified end-to-end on-device: enabled biometric, force-stopped, relaunched — lock screen now appears immediately and auto-launches the fingerprint prompt (`logcat`: `"initial lock check: locking app on startup"`), and unlocking with a fingerprint touch correctly returns to the conversation list.
    2. **[FIXED 2026-08-13] `BiometricLockScreen`'s auto-prompt could get permanently stuck disabled mid-authentication** — pressing HOME then reopening the app (same process, not force-stopped) sometimes hit `BiometricPromptCompat: Unable to start authentication. Called after onSaveInstanceState()` in logcat: the lock screen's `LaunchedEffect` auto-launch races the Activity's `onStop()`/`onSaveInstanceState()` lifecycle callbacks. **Root cause**: in that state, AndroidX's `BiometricPrompt.authenticate()` silently logs the warning and returns without ever invoking any `AuthenticationCallback` — no success, no error, no failure. `BiometricService.android.kt`'s `authenticateAndEncrypt`/`authenticateAndDecrypt` only resume their `suspendCancellableCoroutine` from those callbacks, so the coroutine hung forever, leaving `BiometricLockScreen`'s `isAuthenticating` flag stuck `true` and the button permanently disabled with no retry affordance short of force-stopping the whole app. **Fix**: both functions in `BiometricService.android.kt` now check `activity.supportFragmentManager.isStateSaved` immediately before calling `biometricPrompt.authenticate()`; if true they resume the coroutine with `null` right away (surfacing as `BiometricResult.Cancelled`/`Error`), which flows through to reset `isAuthenticating = false` and re-enable the retry button instead of hanging. Verified on-device: compiled clean, rebuilt, installed on device A, repeated HOME→reopen cycles against the lock screen with no stuck button, then completed a full clean unlock with a real fingerprint touch back to the conversation list.
- [x] **Deep link routing (`jami:`/`sip:`/`tel:`/`ring:`) never wired** — `MainActivity` never read `intent.data` for `ACTION_VIEW`/`ACTION_DIAL` despite the manifest declaring all four intent filters. Added `DeepLinkState` singleton (mirrors `ShareState`) that routes the stripped identifier into `SearchScreen` as a pre-filled query, matching `jami-client-android`'s pre-fill (not auto-dial) behavior.
- [x] **`isEdited` flag lost after resync — fix verified 2026-08-13 on fresh test accounts.** `ChatViewModel.updateMessage()` was patched to refresh `isEdited` from `event.message.editions` on the live `MessageUpdated` event, but that only covers a message edited while the chat stays open.
  - **Root cause**: the cold-start / resync path never set it. `ConversationFacade.swarmMessageToInteraction()` (`ConversationFacade.kt:1325`) set `interaction.edit = message.body["edit"]?.ifEmpty { null }` — that reads the `edit` key off the raw `application/edited-message` event body (which points at the *original* message id), not off the resulting/current message. `SwarmMessage.editions` (the actual per-message edit-history list, correctly used by the live-event fix) was never read in this function at all. `ChatViewModel.interactionToMessageItem()` (`ChatViewModel.kt:675`) then derives `isEdited = interaction.edit != null`, which was always `false` for anything loaded from history.
  - **Fix applied**: `swarmMessageToInteraction()` now sets `interaction.edit = message.body["edit"]?.ifEmpty { null } ?: message.id.takeIf { message.editions.isNotEmpty() }`, mirroring the live-event logic, so history-loaded messages get the correct state on every app restart and conversation resync.
  - **Verified on-device 2026-08-13** with a brand-new, never-shared pair of test accounts (`manualtesta`/`manualtestb`, one per physical device — see the pending-ID bug entry below for why this was necessary). Edited both a fully-confirmed message and a still-pending one; both edits landed correctly with the "edited" label shown immediately and no data loss. This also transitively exercises the resync path's `edit`/`editions` handling since the edited state is what gets loaded back on next conversation open.

- [x] **[RESOLVED 2026-08-13 — was account-state pollution, not an app/daemon bug] Editing freshly-sent messages ("`Unable to edit commit pending-<timestamp>`") does NOT reproduce with fresh test accounts.** Original report: `ChatViewModel.sendMessage()` (`ChatViewModel.kt:367`) adds an optimistic placeholder item with `id = "pending-${Clock.System.now().toEpochMilliseconds()}"`, replaced by `ChatViewModel.appendMessage()` (`ChatViewModel.kt:774-799`) once the daemon's `swarmMessageReceived` JNI callback fires. On the old shared/polluted test account (`unitedhumans`, used as the *same* account on both test devices, with 5+ stale "connected devices" accumulated from prior sessions) that callback never fired at all — a full logcat scan found zero `"onMessageReceived:"` lines — so the placeholder was never reconciled and every edit attempt hit the daemon's rejection.
  - **Reproduction with a clean environment**: fresh install on both devices (`pm clear` to defeat Android's auto-backup-restore, which otherwise silently restores old polluted account data on reinstall), two brand-new never-shared accounts (`manualtesta` on device A, `manualtestb` on device B, one account per device this time — not shared), contact + swarm conversation established from scratch. Sent a text message, confirmed `onMessageReceived: ...msgId=<real-commit-id>` fired in logcat within seconds. Then, twice: (1) edited an already-confirmed message, and (2) edited a message immediately after sending, while it still showed only a single (unconfirmed) checkmark — the exact race from the original report. Both times logcat showed a clean `onMessageUpdated: ...msgId=<same-real-commit-id>` with no `pending-` ID involved and no daemon error, and the UI updated the bubble in place with an "edited" label, no duplicate bubble, no crash.
  - **Conclusion**: the swarm send/confirm/edit pipeline itself is healthy. The original failure was caused by the daemon withholding commit confirmation for a specific polluted multi-device account (`Bootstrap with 0 device(s)` / `no sync connection` in logs, consistent with orphaned stale device registrations), not a regression in `ChatViewModel`/`ConversationFacade`/the JNI bridge. No code change was needed. Recommendation: treat `unitedhumans` (and similarly polluted long-lived test accounts) as unreliable for future send/receive testing — prefer fresh throwaway accounts per test run.
  - **Residual minor UX gap (unfixed, low priority)**: `ChatViewModel.editMessage()` (`ChatViewModel.kt:549-556`) still never calls `updateInput("")` after a successful edit — the composer doesn't reliably clear/reset itself post-send in all cases. Not investigated further; cosmetic only.
  - **Bidirectional re-test 2026-08-13 (device B reconnected)**: with both fresh accounts back online, confirmed the reverse direction too — B→A send (`hello_from_B_test1`) delivered and read-receipted correctly, and a follow-up A→B send while both apps were foregrounded together delivered in ~2s with a heads-up notification on B. No regressions either direction.
  - **[NEW, found 2026-08-13 during the bidirectional re-test] A single rejected/malformed commit can wedge one peer's swarm sync until a fresh reconnect — daemon does not appear to self-heal automatically.** While probing the pending-ID edit race, an ADB text-injection artifact (stray leftover composer text merged mid-string via cursor-position bugs in my test script, not a real user action) produced a garbled message body that Device B's daemon rejected on receipt: `conversationrepository.cpp: Malformed commit <id>. Please ensure that you are using the latest version of Jami...`, followed immediately by `Unable to validate history with <device>` and `Unable to fetch new commit from <device>, other peer may be disconnected`. From that point, B stopped pulling *any* further new commits from A — a subsequent message A sent arrived on A's own screen fine but never reached B, even after 20+ seconds and a full `am force-stop` + relaunch of B's app (B's resync on relaunch only re-fetched up to its own last-known commit, not A's newer one). Sync only recovered once a *new* message was sent from A while both apps were simultaneously foregrounded on the conversation — that appears to have opened a fresh P2P channel, which then correctly backfilled everything, including the originally-malformed message (which rendered fine once pulled). **Not root-caused** — could be a narrow daemon robustness gap (a validation failure should arguably trigger a retry/resubscribe rather than silently going quiet), or could be specific to the malformed commit itself confusing the sync-state machine. Given the trigger was an artifact of ADB `input text`/`KEYCODE_DEL` cursor-position quirks rather than anything reachable through the real UI, this is logged for awareness rather than as a confirmed app bug — worth a closer look if malformed/rejected commits are ever seen from genuine user action.
  - **Root cause traced (2026-08-13, daemon source read, not further reproduced)**: the exact log format seen (`"Malformed commit {}. Please ensure that you are using the latest version of Jami... {}"` with the raw commit body appended) only comes from one call site: `ConversationRepository::Impl::isValidUserAtCommit()` (`daemon/src/jamidht/conversationrepository.cpp:3105-3117`), reached via the generic per-commit validation fallthrough at line ~3086 (`validCommits()`). This is **cryptographic/tree validation, not message-content validation** — the logged message body is incidental (it's just `commit.commit_msg` printed for diagnostics), so the "malformed" framing was misleading. `isValidUserAtCommit()` performs, in order: (1) look up `devices/<deviceId>.crt` in the conversation's git tree at that commit, (2) resolve the device cert's issuer URI and look up the matching `members/`/`admins/` cert, (3) check both certs aren't expired as of the commit timestamp, (4) verify the git commit's cryptographic signature against the device cert's public key, (5) confirm the parent/member cert's ID matches the resolved issuer URI — any single failure returns `false` here. Most likely candidate given the timing (this was device B's very first pull of a commit signed by device A, moments after both accounts/the conversation were freshly created): a **one-time signature/cert-pinning race** on a brand-new device pair, not a content-parsing bug. Not reproduced a second time; no code change made. If this recurs, capture `JAMI_ERROR`/`JAMI_WARNING` lines immediately above the "Malformed commit" line (`Failed to look up commit`, `announced but not found`, `got no issuer UID`, `Certificate not found for`, `Certificate ... expired`, or `not signed by device`) — one of those five will pinpoint which of the five checks actually failed, which this session did not capture (buffer had already rotated by the time this was investigated).
- [x] **Screen-share permission wait had no timeout — verified on-device 2026-08-12.** `startScreenShare()` waited indefinitely on `hardwareService.screenShareReady`; nothing emitted when the user denied the OS permission prompt, hanging the call UI forever. Wrapped in `withTimeoutOrNull(30s)`; also fixes a latent hang on iOS/macOS/Desktop/Web where `screenShareReady` is never emitted at all. Confirmed working on a live two-device video call: triggered the MediaProjection consent dialog ("Share screen with Jami-KMP?"), left it unanswered, and `CallViewModel` logged `"startScreenShare: permission not granted within timeout, giving up"` after the expected ~30s window — the call stayed fully healthy throughout (audio/video encoder kept running normally) and the "Start screen share" button was available again immediately after, no stuck state, no crash.
- [x] **Hardware-acceleration toggle never read** — Setting worked end-to-end in the UI/repository layer and `CameraService.android.kt` already had the full software-encode fallback path (`ImageReader(YUV_420_888)` → `JamiService.captureVideoFrame()`), but every caller of `openCamera()` hardcoded `hwAccel = true` — dead code, setting never consulted.
- [x] **Debug log viewer excluded daemon logs** — `captureRecentLogs()` filtered logcat by the `"Jami"` tag, but the daemon's native layer (`daemon/src/logger.cpp`) logs via `__android_log_write` tagged by source filename, never `"Jami"`. Filter by the app's own PID instead, which picks up both app- and daemon-level lines.
- [x] **Conversation media gallery — verified on-device 2026-08-12.** `MediaViewerScreen` only showed a single image with no way to browse all media in a conversation. Added `MediaGalleryScreen` (3-column grid of completed picture/video transfers, reached from `ChatScreen`'s overflow menu "Shared media"), reusing `ChatViewModel`'s existing message history. Re-verified end-to-end on a live two-device call: took a photo in-chat (camera capture path, unaffected by the `FilePickerEffect` bug below), it rendered correctly as an inline chat bubble immediately (no restart needed), "Shared media" showed a 2-column grid with correct thumbnails for both prior `.jpg` photos (and correctly excluded the earlier `.gz`-named file, which has no thumbnail/mime match), and tapping a thumbnail opened `MediaViewerScreen` full-screen with working back navigation.
  - **Note on a prior (2026-08-10) reproduction that looked like a blocking bug**: a sent file/photo did not appear in the chat live, even after backing out and reopening. Re-attempting the identical steps on 2026-08-12 succeeded cleanly (no `"Unable to attach interaction"` or `"Error parsing data-transfer message"` in logcat, image appeared immediately). Two plausible causes remain documented in code but unconfirmed as the trigger: (1) `ChatViewModel.appendMessage()` (`ChatViewModel.kt:774`) has no `MessageType.Transfer` case for the real-time `MessageReceived` event — relies on the sibling `DataTransferEvent` handler's `loadMessagesFromHistory()` reload to correct it; (2) `Conversation.addSwarmElement()` (`Conversation.kt:448-490`) falls back to `aggregateHistory.add(0, interaction)` (prepending to the very top of scrollback, off-screen) when its parentId-chain matching fails on an out-of-order arrival, logging `"Unable to attach interaction ... - prepending to history"`. Treat as a low-priority intermittent/race issue, not a hard blocker — flag for a fast-fix in `appendMessage()` (add the missing `MessageType.Transfer` branch) if it recurs.
- [x] **[FIXED 2026-08-13] "Sync system contacts" toggle (Android) never requested `READ_CONTACTS` and silently no-op'd without it — found on-device 2026-08-12.** `AppSettingsScreen.kt:492-493` wired the toggle straight to `AppSettingsViewModel.toggleSystemContactsSync()`, which relied on a code comment's assumption that "`READ_CONTACTS` is requested during onboarding (`AccountSummaryScreen`)" and that `syncForAccount()` is "a safe no-op if it was denied." Reproduced: on a real device where that permission was never granted (account not created through the fresh onboarding wizard on this device), flipping the toggle ON succeeded with no dialog, no error, and no log output at all — `dumpsys package net.jami.android` confirmed `READ_CONTACTS: granted=false` the whole time. The switch showed ON and stayed ON; there was no way from Settings to see that sync wasn't actually working or to grant the permission.
  - **Fix**: `AppSettingsScreen` now wires `PermissionRequesterEffect(AppPermission.Contacts, ...)` (already fully implemented in `PermissionRequester.android.kt`, just previously unused here) into the toggle. `onCheckedChange` no longer calls `viewModel.toggleSystemContactsSync()` directly when turning the toggle ON — it sets a local `requestContactsPermission` flag instead, and the ViewModel toggle only fires from the `PermissionRequesterEffect`'s `onResult` callback once `granted == true`. Turning the toggle OFF still calls `toggleSystemContactsSync()` directly since that path never needs the permission. Updated the stale "requested during onboarding" comment in `AppSettingsViewModel.toggleSystemContactsSync()` to reflect that the permission is now requested here, at point of use.
  - **Verified on-device (device A, 37281JEHN03065)**: confirmed `READ_CONTACTS: granted=false` beforehand, force-stopped/relaunched, navigated to App Settings → System → "Sync system contacts", tapped the toggle — the real Android runtime permission dialog appeared ("Allow Jami-KMP to access contacts and accounts on this device?"), granted it, and confirmed `dumpsys package net.jami.android` now shows `READ_CONTACTS: granted=true` and the toggle UI reads `checked=true`. No crash, clean logcat.
  - **Fix**: call `PermissionRequesterEffect(AppPermission.Contacts, request = <toggling on>, onResult = ...)` from `AppSettingsScreen` when the toggle is flipped on and permission isn't already granted; on denial, keep the toggle off (or show it in a "denied" state) instead of leaving it ON with a feature that can't run.
- [x] **Group rename / avatar change / admin badges — verified on-device 2026-08-12.** Member add/remove already worked but there was no way to rename a group, change its avatar, or see who's an admin. `ConversationDetailsScreen`'s header avatar/title are now tappable for admins (avatar → `FilePickerEffect`, title → rename dialog), calling new `ConversationFacade.updateConversationTitle()`/`updateConversationAvatar()` (the underlying `DaemonBridge.updateConversationInfo()` already existed on every platform but nothing called it). Member list now shows an "Admin" badge per member from `conversation.roles`. Note: no promote/demote capability exists at the daemon layer at all — admin role is fixed at group creation, so per-member promotion is a hard architectural limit, not a missing feature.
  - **Verified two ways.** First on a pre-existing 3-member group where the test device was a plain (non-admin) member: header avatar/title were correctly **not** clickable, and no member showed an edit affordance — correct admin-gating. Then created a fresh group (device as creator/admin) to test the positive path: header showed a camera-badge on the avatar and a pencil icon next to the title; tapping the pencil opened a "Titel bearbeiten" dialog, and renaming from the raw ID to "Test group renamed" worked end-to-end (title and avatar-initials both updated live in the details screen); tapping the avatar correctly launched the system file picker (same `FilePickerEffect` as elsewhere — didn't complete the pick, to avoid re-triggering the already-documented `.gz`-naming bug). Member list on the new group correctly showed "Administrator" on the creator and no badge plus "Add member"/"Remove" controls for the admin only. No bugs found in this feature.
- [x] **`ConversationFacade.onConversationProfileUpdated()` discarded avatar on every profile update** — Found while fixing group avatar editing: it always rebuilt `Profile(title, null)` on any profile-update event, silently dropping the avatar (and description) — including the daemon's own echo right after an admin sets one. Fixed to decode `profile["avatar"]` when present, otherwise keep the cached value.
- [x] **[FIXED 2026-08-13] `FilePickerEffect` (Android) hardcoded every picked file to `import_<timestamp>.gz` — found on-device 2026-08-10.** `FilePicker.android.kt:29` copied whatever `ActivityResultContracts.OpenDocument()` returned into a cache file unconditionally named `import_${System.currentTimeMillis()}.gz`, discarding the original filename and extension. This function is shared generic infra — it is called from `ImportAccountScreen` (where the `.gz` name is correct: this is literally an account-archive import), `ChatScreen` ("Send file" in chat), `ConversationDetailsScreen` (group avatar picker), `AccountSettingsScreen` (profile avatar picker), `AccountMediaSettingsScreen`, and `AccountAdvancedSettingsScreen` (TLS CA/cert/key file pickers) — none of which want a `.gz` name. Reproduced live: sending a 7.8 KB PNG through `ChatScreen`'s "Send file" picker staged it as `import_1786348588521.gz`; the daemon transfer/message ended up with that name, so the chat bubble rendered as a blank/generic-file bubble (no thumbnail) instead of an image, and it would not appear in `MediaGalleryScreen`'s "Shared media" grid (filtered by `isPicture || isVideo`, which is presumably extension/MIME-based). The in-chat camera capture path (`ImageCaptureEffect`/`ImageCapture.android.kt`) is unaffected — it correctly names captures `img_<timestamp>.jpg`.
  - **Fix**: added a `resolveFileName()` helper in `FilePicker.android.kt` that queries the picked `Uri` via `ContentResolver.query(uri, [OpenableColumns.DISPLAY_NAME])` (the standard contract every `ACTION_OPEN_DOCUMENT` provider is required to support) and uses that real name for the cache file; falls back to a generic `file_<timestamp>` name with an extension guessed from `ContentResolver.getType(uri)` via `MimeTypeMap` only if the provider doesn't supply a display name. `ImportAccountScreen` (the one caller that actually wanted `.gz`) doesn't depend on the filename at all — it just forwards the returned path to `viewModel.setArchivePath(path)` — so no regression there.
  - **Verified on-device (device A, 37281JEHN03065)**: picked a real `testimg.png` through `ChatScreen`'s "Send file" picker — it landed in the chat bubble and app cache dir (`run-as net.jami.android ls cache/`) as `testimg.png`, not `import_<timestamp>.gz`. Old buggy `import_1786348588521.gz` entries from the pre-fix reproduction remain visible in chat history as a before/after baseline.
  - **[FIXED 2026-09-02] New bug found while verifying this fix.** With biometric lock enabled, opening the system file picker backgrounds the app and triggers `lockIfNeeded()`; if the picker's result was delivered while the app was showing `BiometricLockScreen`, the pick was silently dropped — no file landed in cache, no error, nothing sent — because `ChatScreen`'s `rememberLauncherForActivityResult` (and its `onFilePicked` callback) was disposed when the composition swapped to the lock screen before the `ActivityResultRegistry` delivered the callback. Reproduced on device A: picked `testimg.png` while biometric lock was enabled, got routed through the post-pick lock screen, and after unlocking, `cache/` contained no new file at all.
    - **Confirmed mechanism (2026-08-28 code cross-check)**: `JamiNavigation.kt:90-96` does an early `return` that swaps the *entire* `MainNavigation` composition tree for `BiometricLockScreen` whenever `isLocked && appState is HasAccounts`. `AppViewModel.lockIfNeeded()` sets `isLocked = true` from the `ON_STOP` lifecycle event — which fires the instant a system picker Activity comes to the foreground, i.e. before its result can return. `rememberLauncherForActivityResult` unregisters from the `ActivityResultRegistry` when its composable leaves composition (standard AndroidX behavior), so disposing `ChatScreen` mid-flight dropped the pending result outright.
    - **Broader scope than "file picker"**: this was a structural race in `JamiNavigation`'s lock-screen swap, not something specific to `FilePickerEffect`. Every screen that lives under `MainNavigation` and launches an external Activity via `rememberLauncherForActivityResult` was equally exposed — confirmed present in `ImageCapture.android.kt` (in-chat camera capture) and `RingtoneLauncher.android.kt` (system ringtone picker, `AccountMediaSettingsScreen`), in addition to every `FilePickerEffect` call site (`AccountSettingsScreen` avatar, `ConversationDetailsScreen` group avatar, `AccountAdvancedSettingsScreen` TLS cert/key pickers). `ImportAccountScreen`'s use of `FilePickerEffect` was never exposed — it only runs during onboarding, before any account exists, so `isLocked` can never be true there.
    - **Fix**: new `PendingActivityResultTracker` (`shared/src/commonMain/kotlin/net/jami/utils/PendingActivityResultTracker.kt`) — a process-wide counter of in-flight system-Activity results. `FilePickerEffect`, `ImageCaptureEffect`, and `RingtoneLauncherEffect` (all in `shared/src/androidMain/kotlin/net/jami/ui/platform/`) call `begin()` immediately before `launcher.launch(...)` and `end()` as the first line of the result callback (success, cancel, or exception alike — `ImageCaptureEffect`'s file-creation try/catch guards against calling `end()` for a launch that never actually began). `AppViewModel.lockIfNeeded()` now checks `PendingActivityResultTracker.hasPending` before locking: if a result is in flight it sets a `lockPending` flag instead of `isLocked = true`, and a new `AppViewModel.init` collector applies the deferred lock the moment `hasPending` clears (result delivered or cancelled) — so the composable, and its launcher, survive long enough for the callback to fire, and the lock still lands right afterward rather than being skipped. A plain `object` singleton rather than a Koin-injected service, specifically to avoid adding a constructor parameter to `AppViewModel` and breaking existing test call sites.
    - Compile-verified (`:shared:compileDebugKotlinAndroid`, BUILD SUCCESSFUL). **Not yet re-verified on hardware** — the original repro (pick a file with biometric lock enabled) should be re-run to confirm the fix closes it end-to-end.

## Bug Findings (2026-08-14) — likely root cause of "message sending broken"

- [x] **[FIXED 2026-08-17] A 1:1 swarm conversation never becomes locally resolvable on the device that *sent* the contact request — it can never send into a conversation it just created, even though the daemon is confirmed to be delivering real swarm traffic for it.**
  - **Root cause, found via three parallel investigations (daemon-callback wiring, `ConversationFacade`'s conversation-tracking data structures, and a direct comparison against the reference `jami-android-client` implementation)**: in this KMP port, exactly one thing ever creates a local `Conversation` object on an `Account` for a 1:1 swarm conversation — `ConversationFacade.onConversationReady()` — and that signal doesn't reliably fire on the *sender's* own device after the peer accepts. Every other handler (`onMessageReceived`, `onConversationMemberEvent`) silently no-ops if the conversation doesn't already exist. `AccountService.onContactAdded()` — the handler for `ContactAdded`, which fires reliably on **both** sides — did nothing but emit an event, never touching the `Account`'s conversation tracking at all. The reference `jami-android-client` does not have this gap because its equivalent `contactAdded()` (`jami-client-android/jami-android/libjamiclient/src/main/kotlin/net/jami/services/AccountService.kt:1337-1352`) *proactively* registers a `Syncing`-mode conversation the moment the contact is confirmed, for both sender and receiver — it never depends on `ConversationReady` for this case at all.
  - **Fix, part 1** (`shared/src/commonMain/kotlin/net/jami/services/AccountService.kt`, `onContactAdded`): ported the reference's proactive-registration logic — reads the contact's `conversationId` via the already-wired-but-previously-unused `DaemonBridgeApi.getContactDetails(accountId, uri)`, sets it on the cached `Contact`, and if the swarm conversation doesn't exist locally yet, creates it (`account.newSwarm(..., Conversation.Mode.Syncing)`) **and** calls `account.conversationStarted(conversation)` — the second call is what actually indexes it into the URI-keyed map that `getByUri()`/`getByContact()` (and so `ConversationFacade.startConversation()`) search; `newSwarm()` alone only indexes into the swarm-id-keyed map, a gap caught during hardware verification of this same fix (`onConversationReady()`'s existing code does both calls together, the exact pattern this fix now mirrors).
  - **Fix, part 2, needed on top of part 1** (harness-only, `e2e-runner/src/main/kotlin/net/jami/e2e/scenarios/SendMessageScenario.kt` and `DefaultOneOnOneConversationScenario.kt`): even with the conversation locally resolvable, sending *immediately* after `ContactAdded(confirmed=true)` still raced the sender's own swarm channel to the peer not being fully live yet — the send would succeed locally (the sender got its own echo) but never reach the peer. Gating the first send on `ScenarioSupport.awaitMemberJoined(ctx, <senderRole>, ..., timeoutMillis = 30_000)` — waiting for the sender's own device to observe `ConversationMemberEvent(action=1)` for the peer's join — closed this reliably. This is the real, confirmed cause the `awaitMemberJoined` helper (built earlier the same day for a different, unproven hypothesis) turned out to actually fix.
  - **Validated on hardware, 2026-08-17**: `send-message` (B, the initiator, sends first — the exact previously-broken direction) passed cleanly twice with full bidirectional B→A and A→B message confirmation. Handshake-level DHT flakiness (unrelated, pre-existing, seen throughout this session in every scenario) caused a couple of earlier-stage timeouts before ever reaching the send step — not a regression, the same class of flake documented elsewhere in this file.
  - Superseded content below retained for the investigation trail. Found while building and exercising a new two-device `send-message` harness scenario (`e2e-runner/src/main/kotlin/net/jami/e2e/scenarios/SendMessageScenario.kt`) against the real DHT with both connected devices, immediately after this session's from-source daemon rebuild (see the `daemon-from-source-reconcile` branch). This is very likely the actual mechanism behind user reports of "message sending broken."
  - **Reproduction (harness, but the underlying service-layer calls are identical to the real UI's)**: two accounts, B sends a contact request to A, A accepts, both sides report `ContactAdded(confirmed=true)`. B then tries to send a message into the resulting 1:1 conversation. **B's own `ConversationFacade.getConversation(accountId, peerUri)` and `startConversation(accountId, peerUri)` never resolve** — confirmed with three independent strategies, all failing the same way:
    1. Bounded poll on `getConversation()` for 30s — never resolves.
    2. `startConversation()` (throws as expected when not yet known) → await `ConversationEvent.ConversationReady` for up to 30s — **never fires** for B.
    3. Meanwhile, `ConversationFacade.onMessageReceived()` on B's device **is** observed firing with the correct `conversationId` (proven via the harness's `MessageReceived` event carrying a real `conversationId` matching the one A received) — so the daemon layer is healthy and B's `ConversationCallback` pipeline is receiving real swarm signals; the gap is purely between that daemon callback and B's local `Account`/`ConversationFacade` model, which never registers the conversation object for it.
  - **Two things were tried for how B initiates the request, both exhibiting the gap** (though via different paths, so this isn't narrowly about one specific API call):
    - `AccountService.sendTrustRequest(accountId, to)` (the legacy identity-only trust-request API) — reliably delivers the request to A (confirmed across ~5 runs), but B never gets `ConversationReady` afterward.
    - `AccountService.addContact(accountId, uri)` — this is what the **real UI** actually calls for a brand-new contact (`NewConversationViewModel.createConversation()`, `shared/src/commonMain/kotlin/net/jami/ui/viewmodel/NewConversationViewModel.kt:300`), followed by `ConversationFacade.startConversation()` with a `ConversationReady`-await fallback on failure (same as the fix attempt above). Swapping to this real-UI path made the request **never arrive at A at all** (2/2 timeouts at 120s) — a different, and possibly separate, problem worth its own investigation (does `addContact` alone actually push anything over the DHT, or does the real flow depend on something else this harness isn't replicating?).
  - **A separate, confirmed-and-fixed bug found along the way**: the harness's own `AcceptContactRequest` was passing the bare peer URI instead of the request's own `conversationUri` (`TrustRequest.conversationUri`, a `swarm:<id>` handle carried on `AccountService.onIncomingTrustRequest`). The real UI (`PendingRequestsViewModel.kt:137`, `ConversationsViewModel.kt:386`) always accepts via `conversationUri`, never the peer URI — accepting via the peer URI silently takes the legacy accept path even when the daemon already issued a modern swarm request, confirming the contact but never actually joining the swarm conversation. This is now fixed in the harness (`CommandHandler.kt`'s `AcceptContactRequest` case, `HarnessAgent.kt`'s `IncomingContactRequest` relay, and the `e2e-protocol` wire types) and is very likely present as a real bug too anywhere in the app that might mirror the old (wrong) pattern — worth a targeted grep/audit of every `acceptTrustRequest` call site.
  - **Suggested next steps for whoever picks this up**: (1) grep every real-UI call site that originates a *new* 1:1 conversation (`NewConversationViewModel`, `ContactDetailsViewModel.startConversation()`) and manually reproduce this exact scenario through the actual UI on two physical devices — confirm whether real users hit the identical "sender can't send" gap, or whether something UI-only (e.g. a lifecycle-bound observer) papers over it that this headless harness doesn't have; (2) if reproduced, instrument/log `DaemonBridge.android.kt`'s `conversationReady`/`conversationRequestReceived`/`onConversationMemberEvent` callback wiring (`DaemonBridge.android.kt:951-999`) with `Log.d` on both devices to see which (if any) of those actually fires for the *sender* role after the peer accepts; (3) compare against the official `jami-client-android` reference implementation's equivalent flow for how it bootstraps the sender-side conversation object — it's possible KMP's port is missing a step present there.
  - **Where to find the harness code**: `e2e-runner/src/main/kotlin/net/jami/e2e/scenarios/SendMessageScenario.kt`, `android-app/src/androidHarness/kotlin/net/jami/android/harness/CommandHandler.kt` (`SendContactRequest`/`AcceptContactRequest`/`SendMessage` cases), `android-app/src/androidHarness/kotlin/net/jami/android/harness/HarnessAgent.kt` (`observeMessages`), `e2e-protocol/src/main/kotlin/net/jami/e2e/protocol/Wire.kt` (`SendMessage`/`MessageReceived`/updated `IncomingContactRequest`/`AcceptContactRequest`). Run via `./gradlew :e2e-runner:e2e -Pscenario=send-message -Pdevices=<A>,<B>`.

## Bug Fixes (2026-08-16)

- [x] **Optimistic pending-message reconciliation collapsed duplicate-text sends.** `ChatViewModel.appendMessage()` (`ChatViewModel.kt:774-838`) matched the incoming daemon echo against *every* `pending-*` placeholder with the same text via `filterNot`, instead of just the one it was actually confirming. Sending identical text twice in a row (e.g. "ok" then "ok") removed both pending bubbles on the very first echo — the second bubble vanished from the UI until (if) its own echo arrived, or permanently if that second send failed, with no trace it was ever sent. **Fix**: now finds and removes only the single oldest matching `pending-*` item (`indexOfFirst` + `removeAt`), so each echo reconciles exactly one placeholder.
- [x] **No feedback when a sent message never gets a daemon echo.** Swarm sends are fire-and-forget from the Kotlin layer (`JamiService.sendMessage()` returns void — see comparison research this session against `jami-android-client`'s equivalent `AccountService.sendConversationMessage()`); if the daemon never confirms (network drop, rejected/malformed commit — see the 2026-08-13 "malformed commit" entry above), the optimistic bubble was stuck showing "sending" (single checkmark) forever with no way to tell the user or retry. **Fix**: added `DeliveryStatus.FAILED` and a 20s (`SEND_TIMEOUT_MS`) watchdog per send — if `appendMessage()` hasn't reconciled the placeholder by then, it flips to FAILED (warning icon, error color, in `ChatScreen.kt`'s `ChatBubble`).
- [x] **Tap-to-retry on failed messages.** `ChatViewModel.retryMessage(messageId)` drops a FAILED bubble and re-runs the same optimistic-send flow (extracted into `sendOptimistic()`, shared with `sendMessage()`) with the original text. `ChatBubble`'s tap handler (previously a no-op) now calls `onRetry()` only when the bubble's `deliveryStatus == FAILED`; normal taps on sent/delivered/read bubbles remain inert.
- Commits: `cdaa9ad` (this fix), on branch `daemon-from-source-reconcile`.
- **Not done**: no retry for failed *file transfers* (see "Retry failed file transfer" under Chat — Missing Features, a separate pre-existing gap); `deleteMessage`/`editMessage`/`sendReaction` still assume a resolved (non-`pending-`) ID, which is fine today since the UI can't target a bubble still in SENDING/FAILED state, but would need handling if those actions are ever exposed on unconfirmed bubbles.

## Bug Fixes (2026-08-17) — registration-state race dropped for pre-loaded accounts

- [x] **`AccountService.accountEvents` (a `MutableSharedFlow` with no replay) silently dropped
  `RegistrationStateChanged` for any account that was already provisioned at daemon startup** —
  root cause of the "A→B send produced no `MessageReceived` within 60s" finding below. The
  daemon can carry an already-loaded account through `UNREGISTERED`→`TRYING`→`REGISTERED`
  within ~300ms of daemon init (confirmed via device logcat: full registration burst — identity
  loaded, DHT connected, identity announcement succeeded, buddy online — inside one 300ms window
  at cold start), often before any UI/service subscriber (harness or real app) has attached its
  collector. The event fired and was gone; nobody was listening yet. Reproduced twice,
  identically, restoring the `contact-confirmed-no-messages-1` fixture
  (`e2e-runner/src/main/kotlin/net/jami/e2e/HarnessServer.kt`'s `installConversationPairAsset`
  timed out both times waiting for B's `REGISTERED`, despite the daemon having actually
  registered it). **Fix** (`AccountService.kt:78-107`): `accountEvents` now backfills each
  known account's *current* registration state (read from `_accounts`, which
  `onRegistrationStateChanged` already updates synchronously before emitting) to every new
  collector via `.onSubscription { ... }`, before continuing to relay the live stream.
  Deliberately narrow — only this one state-representable event type is backfilled, not the
  whole `AccountEvent` stream, since replaying one-shot events (contact requests,
  name-registration results, …) to a late subscriber would be wrong. Public type changed from
  `SharedFlow<AccountEvent>` to `Flow<AccountEvent>` (all 15 consumers just `.collect`/`.filter`,
  no `SharedFlow`-specific API used — compile-verified across `shared`, `android-app`,
  `e2e-runner`). **Validated on hardware 2026-08-17**: same restore that failed twice before now
  shows `RegistrationStateChanged(state=REGISTERED)` firing immediately on connect for both
  roles, and the follow-on `send-reply-roundtrip` scenario (new, see `doc/end2endTesting.md`)
  confirmed real bidirectional messaging (A→B and B→A) against the previously-stuck fixture —
  the original "message never arrives" symptom was entirely this race, not a messaging bug.
  This is a real, non-harness-specific fix: any real UI code that subscribes to `accountEvents`
  shortly after a cold launch with an existing account was exposed to the identical gap.

## Infra Fix (2026-08-17) — reinstall-triggered SELinux relabel race stalls swarm git writes

- [x] **Reinstalling the harness APK before a run (`installHarnessDebug`, done by the `e2e`
  Gradle task on every invocation) can race the SELinux `restorecon` pass it triggers, causing
  the daemon's swarm conversation git writes to stall silently on whichever device loses the
  race** — this, not any Kotlin/daemon logic bug, was the actual cause of the message-delivery
  and contact-confirmation failures chased through most of 2026-08-17 (see the now-superseded
  "Bug Fixes" entry above, which fixed a real but unrelated `accountEvents` race). Evidence,
  device B (Pixel 2, Android 11/API 30) logcat during a failing run: `installd` logs `"Detected
  label change ... running recursive restorecon"` for the app's data dir right after every
  reinstall; moments later, mid-handshake, 12 SELinux denials appear —
  `avc: denied { link }` for `HEAD.lock`, `index.lock`, `main.lock`, `tmp_object_git2_*`,
  `pack_git2_*.idx.lock` (libgit2's lock-file mechanism uses `link()`); the daemon's own log then
  shows the new swarm conversation stuck — `fetching '<oid>'` → `Not yet bootstrapped, save
  notification` → nothing further, ever. That stall is why `ContactAdded(confirmed=true)`/
  `MessageReceived` never arrived: the conversation object never finished syncing locally.
  **Confirmed by experiment**: rerunning the identical scenario with `-x
  :android-app:installHarnessDebug` (skip the reinstall — APK already present, unchanged) — same
  devices, same code — passed cleanly and fast (full handshake + 3-message round trip in ~14s,
  vs. 60–120s timeouts on every prior attempt that reinstalled first). Explains several of
  2026-08-17's "reproducible" failures were actually a self-inflicted harness artifact, not real
  app/daemon bugs. **Fix** (`Main.kt`): a fixed `delay(INSTALL_SETTLE_MS)` (3s) at the very start
  of the run, before any device/app interaction — gives `restorecon` time to finish regardless of
  whether the caller happens to skip the reinstall. **Validated on hardware**: three consecutive
  fresh-build `default-one-on-one-conversation` runs, each *with* the full `installHarnessDebug`
  reinstall (no `-x` workaround), all passed cleanly (full handshake + 3-message round trip each
  time). Device-specific note still open: unconfirmed whether the Pixel 7a (role A) was ever
  equally exposed, or whether this was particular to the Pixel 2's storage/SELinux timing — every
  observed instance of the stall was on device B (the Pixel 2).

## Infra TODO (2026-09-05) — harden the "no competing daemon during a harness run" precondition

- [ ] **Ensure the standard jami-kmp build and jami-android-client are fully deactivated before
  every harness run.** Both embed a real libjami daemon; if either is alive on the same physical
  device while the harness drives its own daemon, they contend for the DHT, local UDP ports and
  the TURN/proxy connections — a plausible contributor to the intermittent
  `IncomingContactRequest`-never-arrives / handshake-timeout flakiness seen on 2026-09-05 (known-good
  `send-message` failed ~4/6, `two-device-contact` 1/4, while `ping` passed).
  - Mechanism **already exists**: `DeviceController.stopCompetingApps()` (`COMPETING_APP_IDS =
    ["net.jami.android", "cx.ring"]`), invoked once per run at `Main.kt:115` after
    `INSTALL_SETTLE_MS`, before `startApp()`. Verified 2026-09-05: on both lab devices
    `net.jami.android` and `cx.ring` were `stopped=true` / not in `ps` after a run.
  - Gaps to close:
    - `COMPETING_APP_IDS` misses the reference client's **debug build id** `cx.ring.debug`
      (AGP `applicationIdSuffix`), used when jami-android-client is built from source for comparison.
      Also consider historical ids (`com.savoirfairelinux.ring`).
    - `am force-stop` is a one-shot kill — nothing re-checks that a Telecom `ConnectionService`,
      `START_STICKY` service, or WorkManager job hasn't relaunched the competing daemon by the time
      the scenario's DHT work starts. Consider a re-assert (or a `ps`/`dumpsys` guard that fails the
      run loudly) just before the first cross-device step.
    - Not covered by this precondition: harness state **left behind by a timed-out run** — a stale
      account on a device poisons the next scenario (`device[A] resolved URI '…' does not match
      installed asset …`, seen 3× consecutively on 2026-09-05). Needs a robust pre-run
      `pm clear net.jami.android.harness` / account-wipe, separate from `stopCompetingApps()`.

- [ ] **Desktop DaemonBridge** — All 100+ methods are no-ops. Architectural blocker: SWIG-generated JNI classes conflict with KMP's Android plugin, requiring a separate JVM module. Deprioritised.
- [ ] **Web/JS platform** — Entire daemon bridge is REST stubs with `// TODO: Call REST API`. Explicitly experimental per CLAUDE.md; candidate for removal if REST bridge server is not developed.
- [ ] **Desktop/Web VideoSurface** — `VideoSurface.desktop.kt` and `.js.kt` show placeholder text. No viable path without daemon bridge working first.
- [x] **ShareUtils — iOS/macOS** — See completed items above (implemented with UIActivityViewController / NSSharingService).
- [ ] **QRCodeUtils — Web/JS** — `QRCodeUtils.js.kt` returns null (no QR generation on Web).
- [ ] **OsmMapView — macOS** — Stub retained: no stable `NSView` embedding API in Compose Multiplatform for macOS targets. Shows location coordinates as text.
- [ ] **OsmMapView — Desktop** — Stub retained; no viable JVM map library in scope.

## Candidate defects found while reconciling the docs (2026-10-26) — read-only, not reproduced

- [x] **Screen sharing was broken on Android 14+: no `mediaProjection` foreground service**
  (fixed 2026-11-05). `FOREGROUND_SERVICE_MEDIA_PROJECTION` was undeclared, no service carried
  the `mediaProjection` type, and `MainActivity.onActivityResult` redeemed the consent token
  directly — which from API 34 throws `SecurityException` unless such a service is already
  running. `targetSdk` is 36, so this could not have worked on any recent device.
  New `net.jami.services.ScreenShareService` holds the type; `MainActivity` now hands it the
  consent result and the service enters the foreground before redeeming the token, since
  `startForegroundService()` is asynchronous and the activity cannot know when `startForeground()`
  has run. It stops itself on `MediaProjection.Callback.onStop` (covering both the system-UI stop
  and `CameraService.closeCamera()`), with a 60 s guard for consent that `startCapture` never
  consumes. The type was **not** added to `CallNotificationService`: that service starts for every
  call with no capture session to back it, as its own manifest comment says.
  **Neither the defect nor the fix has been run on a device** — one screen-share attempt on an
  Android 14+ device settles both. See `Permissions.md`.

## Open Decisions

- [x] **Publish a presence status note** *(done 2026-10-28, Android only — `publish` is not in
  `JamiBridgeWrapper.h`.)* — libjami's `publish()` has a Jami branch that forwards a free-text
  note to every contact over the message channels, so a custom status ("away", "in a meeting")
  works peer-to-peer with no presence server. The receive half was already arriving and being
  dropped. Three daemon constraints shape it: the daemon does not persist the note (so
  `SettingsRepository` stores it in the `KMP.*` settings and republishes on load), the peer parses
  it with `<basic>([\w\s]+)</basic>` so only ASCII word characters and spaces can be delivered,
  and an empty note cannot clear a status — clearing publishes a single space. Verified between
  two devices the same day: typed on a Pixel 7a, shown under the contact's name on a Pixel 2, and
  updated live. The persistence half needed a second fix: the daemon drops `KMP.*`
  keys, so client settings were moved to a SQLDelight table on 2026-10-29 and a cold start now
  republishes the stored note by itself — see `doc/status-note-open-ends.md`, which also lists
  what is still untested and the iOS/macOS gaps. See mini-plan `daemon-api-surface-audit.md` F6.
- [x] **Add presence unsubscribe on background like the official app** *(decided and done 2026-10-28:
  adopted on both platforms. Android keeps proxy accounts registered in background, so the
  subscriptions cost DHT traffic with nothing to display them; the reference Android client gets the
  same effect by deactivating proxy accounts entirely, which jami-kmp does not yet do. Required
  unifying the three independent presence subscribers behind the refcount in
  `ContactService.subscribeBuddy` first — see mini-plan `contacts-presence-accounts.md` F2.
  Verified on an Android device the same day: three background/foreground cycles with the count
  steady at 1, no churn on rotation. iOS is written but has never been compiled.)* —
  jami-client-ios unsubscribes presence for all contacts in `sceneDidEnterBackground`
  (`presenceService.subscribeBuddies(..., subscribe: false)`, `AppDelegate.swift:340`) and
  re-subscribes in `sceneWillEnterForeground`. jami-kmp keeps presence subscriptions while in the
  background (`IOSApplicationHelper.enterBackground()` only starts the ~30 s `SyncManager` window).
  To decide: adopt it on iOS only (the app is suspended there anyway), or on Android too — where
  `JamiDaemonService` may keep the process alive and presence would stay useful. Note the daemon
  reference-counts `subscribeBuddy`, so each unsubscribe must pair with an earlier subscribe
  (see ConversationFacade review #8).

## Testing

- [x] **Unit tests compile and pass on both JVM targets (2026-09-18)** — `:shared:desktopTest` 617/617,
  `:shared:testDebugUnitTest` 530/530. **Re-run 2026-10-26: `:shared:desktopTest` 769/769, 0 failures.** The Android unit-test build failed with 35 × "No value passed
  for parameter 'context'": common tests constructed Android actuals (`HardwareService`,
  `SystemContactsService`, `BiometricService`, `AudioRecorderService`) without a Context. They now
  use per-platform `test*()` helpers (`commonTest/.../TestHardwareService.kt`,
  `TestPlatformServices.kt`) that pass a placeholder Context on Android
  (`unitTests.isReturnDefaultValues = true`); Android's `HardwareService` reads its AudioManager
  lazily. Four classes (`HardwareServiceTest`, `AppSettingsViewModelTest`, `AppViewModelTest`,
  `CallViewModelTest`) need an Android runtime there (Koin-backed LocalPrefs, AudioManager,
  MediaProjection) and are excluded from the Android unit-test task only — they run on desktop.
- [ ] **Robolectric for the Android unit tests** — would let the four excluded classes run against the
  Android actuals too.

- [ ] Run ViewModel tests: `./gradlew :shared:desktopTest --tests "net.jami.viewmodel.*"`
- [ ] Run integration tests: `./gradlew :shared:desktopTest --tests "net.jami.services.*IntegrationTest"`
- [ ] Run full test suite: `./gradlew :shared:desktopTest`
- [ ] Verify platform builds: `./gradlew :shared:compileKotlinDesktop :shared:compileDebugKotlinAndroid`
- [ ] Fix any failing tests from the `viewModelScope()` migration (replaced `backgroundScope` to fix `UncompletedCoroutinesError`)
- [x] **Pre-existing broken test — fixed.** `AppSettingsViewModelTest.kt` now calls
  `AppSettingsViewModel(repo, accountService, contactService, syncService, scope)` and compiles. It
  is still excluded from the *Android* unit-test task only (needs an Android runtime), and runs on
  desktop as part of the 769-test suite.

## iOS — after the first simulator run (2026-09-08)

The iOS host app had never been compiled on a Mac until now. It builds, launches and stays up;
these are what the run surfaced.

**Second Mac session, 2026-10-14 — the Apple bridge rebuild landed.** The wrapper `.a` files were
rebuilt and committed (`5b4a17e`: 1096 methods, up from 875, all 153 header-declared ones present),
the Xcode iOS-simulator build links and runs, `iosX64` was dropped (`022e684`) because the
simulator libraries are arm64-only, and walking the feature checklist on the simulator found five
certificate bugs, all fixed in `89d1443`. `doc/apple-bridge-rebuild.md` has the detail. Still open
from that session: the macOS target, and `build-jamibridge.sh`'s macOS stage, which fails on UIKit
code compiled against the macOS SDK (long-standing, dates to `93df547a`).

- [x] **Welcome-screen button clips long labels** — Fixed in `JamiButton`, which pinned all three
  style variants to a fixed `height(minTouchTarget)`, so any wrapped label was clipped. Now
  `heightIn(min = ...)`, and the label is `weight(1f, fill = false)` + `TextAlign.Center` so it
  wraps instead of pushing a leading icon out. Verified in the simulator at `de-DE`.
  `JamiSearchField` keeps its fixed height deliberately — it is single-line.
- [ ] **Script the device-side native symlinks.** `lib/` is hand-made and drifts silently when the
  daemon's dependency set changes; three of the five first-build failures were exactly this
  (`http_parser` dropped, `vpx` absent on the simulator, `yrs` added). `scripts/make_sim_links.py`
  already does the simulator side by discovery.
- [ ] **Let `StubDaemonBridge` retain its callbacks.** `init(callbacks)` discards them, so none of
  the 15 daemon callbacks added to the iOS bridge can be tested without a live peer. Retaining
  them makes each one testable single-app, on JVM and iOS both.
- [ ] **iOS connectivity never reaches the daemon** — `NWPathMonitor` feeds
  `HardwareService.connectivityChanged()` correctly, but `DaemonBridge.ios.kt` cannot forward it:
  `JamiBridgeWrapper.h` has no `connectivityChanged` entry point, so libjami never re-resolves its
  connections after a network change. One wrapper passthrough + `build-jamibridge.sh` rebuild.
  (Android is already fully wired via `ConnectivityManager.NetworkCallback` → JNI; an earlier note
  here claiming otherwise was wrong.)
- [ ] **Shared `appleMain` source set** — the iOS and macOS Darwin implementations are now
  substantively identical, and macOS does not compile (~95 errors). One copy would fix both.
  Start from `origin/macos-build-and-apple-source-set` (`f0f66ce`, 2026-10-01) rather than from
  scratch; it predates the nine wrapper commits and needs rebasing onto `main` first.

## Licensing & Copyright Attribution (2026-10-01)

`LICENSE` (GPLv3, byte-identical to upstream `jami-client-android/COPYING`) was added with the
README on 2026-10-01. The license choice is correct and settled — GPLv3 on a derivative of GPLv3
work. What is **not** settled is per-file attribution, and the gap runs opposite to the obvious
worry: nothing falsely claims affiliation with Savoir-faire Linux, but our own authorship is
nowhere asserted and the modifications are nowhere declared.

Current state, measured 2026-10-01 (excluding `build/`):

| Files carrying the SFL header | Count |
|---|---|
| `.kt` | 341 |
| `.xml` | 477 |
| `.h` | 16 |
| `.swift` | 3 |

Every one of them carries **only** `Copyright (C) 2004-2025 Savoir-faire Linux Inc.` — there is no
second copyright line anywhere in the repo. The header sits even on files with no upstream
counterpart and no derived content, e.g. `JamiAvatar.kt`, `JamiBadge.kt`, `OsmMapView.ios.kt`, and
the whole e2e harness (`HarnessAgent.kt`, `HarnessAgentService.kt`, `CommandHandler.kt`).

- [ ] **BLOCKED — decide the copyright holder name/entity.** Nothing below can be written until
  this is chosen: a copyright line should say what the holder wants it to say, and it must not be
  guessed from a git config or GitHub handle. Everything else here is mechanical once it is fixed.

- [ ] **Do not strip the SFL notices.** Recording this explicitly so it is not "tidied" later:
  GPLv3 §4 requires keeping intact all notices, and §5(b) modifies that requirement without
  lifting it. Removing SFL's line from a derived file is the one change here that would actually
  be a violation. Stacked multi-holder headers are the normal state of a GPL file.

- [ ] **Declare the modification — GPLv3 §5(a).** This is the genuine compliance gap: §5(a)
  requires prominent notices "stating that you modified it, and giving a relevant date," and
  nothing in the repo does. A reader cannot currently tell these files are third-party
  modifications at all. Add a "part of Jami KMP, a modified cross-platform port of
  jami-client-android; modified 2026" line to the header block.

- [ ] **Classify the ~341 `.kt` files into derived vs. original, then add our line.** The only
  judgement-heavy step; git history plus the presence of an upstream counterpart in
  `jami-client-android/jami-android/{libjamiclient,app}` resolves most of it.
  - Derived (ported from `libjamiclient` or the Android app) → keep the SFL line, add ours below it.
  - Wholly original (KMP components, navigation, e2e harness) → our line only, plus the GPL block.
  - Apply the same split to the 477 `.xml` resource files, where most string files are derived
    (keys are reused verbatim from Android by policy) but KMP-only additions are not.

- [ ] **Fix the user-visible claim in `AboutViewModel.kt:39`.** `const val COPYRIGHT =
  "Copyright (C) 2004-2025 Savoir-faire Linux Inc."` is rendered in the About screen
  (`AboutViewModel.kt:34`), so the running app currently tells users this *is* an SFL product.
  This is the highest-priority item after the name decision — it is the only one users see.
  Should become a two-line attribution (upstream + our port). Note GPLv3 §5(d) ("Appropriate
  Legal Notices" in interactive interfaces) makes this a compliance concern, not just a courtesy.
  **`AboutViewModelTest.kt:37` asserts on this constant and must be updated in the same commit.**

- [x] **Add a non-affiliation disclaimer to `README.md`** *(done 2026-10-26, in the License
  section)*. Unofficial third-party client, not
  affiliated with or endorsed by Savoir-faire Linux; "Jami" is their trademark, used only to
  describe compatibility. Practically this does more to prevent confusion than any header change,
  since the README is what people read first.

- [ ] **Consider an `AUTHORS` file** listing upstream (Savoir-faire Linux and the Jami community)
  and this port's contributors separately, so attribution lives in one canonical place rather than
  only in 800+ file headers.
