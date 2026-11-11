# iOS — what to address next (2026-11-11)

Assembled on a Mac at `a0c1cb7` (branch `ios-catch-up-2026-11`, 15 commits ahead of `main`,
pushed, not merged) from the git history since 2026-10-31, `doc/ios-task-queue-2026-11-09.md`,
`doc/apple-bridge-rebuild.md`, `doc/TODO.md` ("Open iOS tasks — consolidated 2026-10-31") and
`ios_implementation_gap.md`, then cross-checked against `JamiBridgeWrapper.h` and
`DaemonBridge.ios.kt`. Every item below was confirmed open in the code on that date.

## Docs corrected in the same pass

- "`StubDaemonBridge` retains callbacks" was still open in `doc/TODO.md` and
  `ios_implementation_gap.md` §4 — done in `333795c` (2026-11-08, `fireDaemonEvent`). Per-signal
  tests on top of it are still unwritten (item 6).
- `origin/macos-build-and-apple-source-set` is 149 commits behind, not 70.
- `CLAUDE.md` described the video gap as remote rendering only; per `doc/ios-video-pipeline.md`
  neither direction works.
- The German-only KMP strings gap (task-queue item 4) is 87 keys, not just the home screen.

## Prioritised

1. **Wrapper rebuild batch** — none of these exist in `JamiBridgeWrapper.h`; the iOS actuals log
   and drop:
   - `connectivityChanged` — the daemon never re-resolves its connections after a network change.
   - `sendFile` `parent:` — a file sent as a reply arrives as a plain file.
   - `publish` + presence `lineStatus` — status notes neither sent nor received.
   - `setAudioMeterState` + `AudioMeter` signal — the mic level ring stays dark.
   - Call recording (`toggleRecording`, `getIsRecording`, `RecordingStateChanged`).
   - `createConfFromParticipantList`, `setDefaultModerator` / `getDefaultModerators`.
   - Notifications plan F2: configuration signals with no delegate method (`volumeChanged`,
     `errorAlert`, `stunStatusFailure`, `historyChanged`, hardware codec changes, `messageSend`, …).
   - *(low)* a correctly named per-conversation `getActiveCalls`; the existing one wraps
     `getCallList`.

   One header + `.mm` change and one `build-jamibridge.sh` run (overwrites the two tracked `.a`
   files). Hazards and the verification recipe: `doc/apple-bridge-rebuild.md`. **M.**
2. **Script the device-side symlinks** (`shared/src/nativeInterop/cinterop/lib/`) — hand-made,
   drift silently, surface only as link errors. Same session as 1. **S.**
3. **Translate the 87 KMP-only keys** that only `values-de` has (onboarding, account, ~20
   `content_desc_*` accessibility labels, home screen) into the other locales. **S–M.**
4. **Small UI bugs:** the home empty-state hint (`HomeScreen.kt:400`) has no `textAlign`, so it
   goes left-aligned when it wraps; `filter_unread` / `filter_groups` have no German. **S.**
5. **Video pipeline** — calls send and render no video. Starts with wrapper work (frame-in,
   `SinkTarget`), then renderer, encoder controls, PiP. Plan: `doc/ios-video-pipeline.md`. **L**,
   needs a device to verify.
6. **Callback tests via `fireDaemonEvent`** for the 15 signals in `ios_implementation_gap.md` §2.1,
   runnable as `iosSimulatorArm64Test` without a peer. **S–M.**
7. **Simulator walk of shared work never run on iOS:** read markers, @mentions, inline Markdown,
   emoji fonts, SQLDelight settings/drafts, presence suspend/resume. No code change expected. **S.**

## Not doable here

Device checks from `doc/ios-task-queue-2026-11-09.md` item 5 (CallKit tone on a locked phone,
quick actions, Accept from the lock screen with the app killed, outgoing calls on CallKit) need a
physical iPhone.
