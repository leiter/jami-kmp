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

1. ~~**Wrapper rebuild batch**~~ — **done 2026-11-11**, see `doc/apple-bridge-rebuild.md`; `setDefaultModerator` was not needed, and `sendFile` turned out never to have sent a file. Was: none of these exist in `JamiBridgeWrapper.h`; the iOS actuals log
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
2. ~~**Script the device-side symlinks**~~ — **done 2026-11-11**, `scripts/make_device_links.py`. Was: (`shared/src/nativeInterop/cinterop/lib/`) — hand-made,
   drift silently, surface only as link errors. Same session as 1. **S.**
3. **Translations — partly done 2026-11-11.** The gap was larger than measured above: besides
   the 80 used German-only keys, 297 used keys had no translation at all, German included — the
   KMP-only keys added since the one-off April import (`29f36b0`). Not a stale import: of 646 used
   keys only 277 exist in Android under the same name, and those were already translated.
   `scripts/sync_android_strings.py` (repeatable import) + `scripts/android_string_aliases.txt`
   (72 reviewed KMP-key → Android-key aliases, 5 rejected false friends) added **8049**
   translations across 95 locale folders. **German complete (2026-11-11):** the remaining 246
   used keys were translated by hand (machine-quality, formal "Sie", terminology taken from the
   existing German catalogue; not reviewed by a native speaker), and `account_type_jami` /
   `account_type_sip` are marked `translatable="false"`. Every used key now has German.
   **Major languages done (2026-11-11):** the ~300 KMP-only keys were translated by hand into
   French, Spanish, Italian, Portuguese (pt and pt-BR separately), Dutch, Polish, Russian,
   Ukrainian, Simplified Chinese, Traditional Chinese (zh-rTW, also zh-rHK) and Japanese — plus
   72 upstream keys Japanese never had: 4062 strings. Machine-quality, not reviewed by native
   speakers; terms follow each locale's existing catalogue (e.g. 主持人 / モデレータ for moderator).
   Each of these locales and German now has every used key; regional variants (fr-rCA, es-rMX,
   …) fall back to them. `placeholder_archive_path` is now `translatable="false"`.
   **Second round done (2026-11-11):** the same ~300 keys, machine-quality and unreviewed, in
   sv, da, nb, nn, fi, et, cs-rCZ, sk, ro, hu, el, bg, hr, sr (Latin, matching the existing
   folder), sl, lt, tr, ar, he, fa, ca, gl, eu, eo, ko, vi, th, id, hi, bn, ta, te, ml, kn, mr,
   gu, pa, ne, be, kk, hy, mn-rMN, sq-rAL, sw, ga, oc, ast-rES; `zh` copied into cmn, hak, nan
   (commits `4fe7493`…`5cbf0d0`). Kannada uses ಮಾಡರೇಟರ್ for moderator — upstream's ಸಂರಚಕ means
   "configurator"; upstream's Belarusian "mute" and Mongolian "settings" are also wrong and were
   not followed. **Deliberately skipped, still English:** ace, kbd, ig, kab, tt, si (too little
   confidence in a machine translation); `ms` (upstream `values-ms` is about two-thirds
   Malayalam script); `az` (upstream `values-az` is Perso-Arabic South Azerbaijani, near-identical
   to `az-rIR`, mixed with Latin — the folder needs sorting out before adding to it). `ru-rRU` and
   `ro-rRO` fall back to their base folders. `--update` was run the same day: 404 existing translations refreshed in 79
   folders (mostly upstream rewordings, e.g. `action_call_hangup` now "End call"); 93 kept
   (German and aliased keys are never updated). Upstream's `values-zh` mixes in Traditional
   strings; the one update that would have put one into our Simplified folder was replaced by
   the `zh-rCN` text. The script now checks Chinese script itself (see below).
   `--new-locales` was run and **discarded** (Marco, 2026-11-11): the 8 Android-only folders
   held 1–12 strings each (`en-rGB`/`en-rIE`/`en-rUS`/`brx` only English words, `es-rVE` 3,
   `or` 12), or near-copies of existing ones (`es-rES` 232 of 240 identical to `es`, `nb-rNO`
   206 of 209 to `nb`), and every folder becomes a language-picker entry.
   **Chinese script check (2026-11-11):** each Chinese folder has an expected script (Simplified:
   `zh`, `zh-rCN`, `cmn`, `nan`, `hak`; Traditional: `zh-rTW`, `zh-rHK`); a wrong-script import
   is replaced by the same key from a right-script sibling folder. Its first `--update` run
   repaired 301 used strings — `zh-rHK` had been mostly Simplified (upstream's own `zh-rHK` is
   ~70% Simplified), and `zh`/`cmn`/`nan`/`hak` carried stray Traditional strings. No used
   string is left in the wrong script.
4. ~~**Small UI bugs**~~ — **fixed 2026-11-11**: the empty-state title and hint are centred
   (`textAlign`), and `filter_all` / `filter_unread` / `filter_groups` have German. Checked on
   the simulator in German.
5. **Video pipeline** — **implemented 2026-11-11** on branch `ios-video-pipeline` (not merged
   into `ios-catch-up-2026-11`), shipped as TestFlight build 37, unverified in a call (needs a
   device and a second client; checklist in `doc/ios-video-pipeline.md` §0). PiP still open.
   Was: calls send and render no video.
6. ~~**Callback tests via `fireDaemonEvent`**~~ — **done 2026-11-11**:
   `DaemonCallbackRoutingTest`, 15 tests for the 14 §2.1 callbacks (revocation has a success and
   a failure case), through the production `DaemonCallbacksImpl` on `iosSimulatorArm64Test`.
   Checked by sabotage: blanking three routes failed exactly those three tests. Not covered:
   the Obj-C++ registration itself (needs the native daemon) and the two video-decoding
   signals, which go to `HardwareService`. Item 5 (video) was skipped for now (Marco).
7. **Simulator walk of shared work never run on iOS:** read markers, @mentions, inline Markdown,
   emoji fonts, SQLDelight settings/drafts, presence suspend/resume. No code change expected. **S.**

## Not doable here

Device checks from `doc/ios-task-queue-2026-11-09.md` item 5 (CallKit tone on a locked phone,
quick actions, Accept from the lock screen with the app killed, outgoing calls on CallKit) need a
physical iPhone.
