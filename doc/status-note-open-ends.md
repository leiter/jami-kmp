# Status note and KMP settings — what is verified, and what is open

> Written 2026-10-28 after the two-device run (Pixel 7a / Android 17, account `unitedhumans`
> `66d57f55…`; Pixel 2 / Android 11, account `marco100` `d9f8c7f0…`), at the point where the work
> was paused. Everything below was observed on those devices or read out of the daemon source;
> nothing here is inferred from the unit tests.

## Verified on two devices

The status note works end to end between two real accounts over the Jami network.

- Typed "In a meeting" in Account settings on the Pixel 7a, tapped the commit tick:
  `D/DaemonBridge: publishPresence: accountId='3f3dad8b328eb995' status=true note='In a meeting'`.
- The Pixel 2 rendered **"In a meeting" under "unitedhumans" in the chat header** — the note
  crossed the network, survived the peer daemon's PIDF regex, and reached the UI.
- Changing it to "Back at 5" with the chat **open** on the Pixel 2 updated the header live:
  two `onPresenceUpdate … status=2` arrivals (one per peer device channel) and the header text
  changed without reopening anything.
- The field behaves as designed: the trailing tick appears only while the typed text differs
  from the published one, and the supporting line shows the summary.

Note for anyone reading logs from this run: the two devices' clocks differ by about 1.5 s, so a
peer's arrival line can carry an earlier timestamp than the publish line that caused it.

## Open end 1 — the note is not persisted, and neither is any other KMP setting

**This is the significant finding, and it is not specific to the status note.**

`SettingsRepository` stores everything under `KMP.*` keys inside daemon account details
("stored as JSON in daemon account details … syncs across devices via DHT", per its own
documentation). **The daemon does not keep those keys.** `setAccountDetails` parses the map into
typed config and drops anything it does not recognise, so:

- `grep -c KMP files/3f3dad8b328eb995/config.yml` on the device returns **0** after a successful
  save (`D/SettingsRepository: Saved settings [KMP.Settings.Presence] …`).
- After `am force-stop` and a cold start, `Loaded settings` runs and the presence branch does not
  fire — no republish, and the Status field comes back empty.

So the "stored and republished after a restart" half of the status note is **written and tested
but inert against this daemon**, and the same is true of theme, read receipts, typing indicators,
notification preferences, the call audio flags and file-transfer limits: every `KMP.*` setting is
a session-only value today.

This was invisible until 2026-10-28 because nothing ever called `observeAccount()`, so no write
was ever attempted (fixed in `8504c3b`). The fix made the write happen; the device run showed the
daemon discarding it. A decision is needed before more is built on this mechanism:

- keep client settings client-side (platform preferences / DataStore / a file), losing the
  cross-device sync the design was reaching for, or
- find a daemon-side store that does round-trip — note that `Account.defaultModerators` and the
  rest of the real config keys do persist, so the mechanism, not the idea, is what is wrong.

Until then, no feature should depend on a `KMP.*` value surviving a restart.

## Open end 2 — what was not tested on the devices

- **Clearing a note.** The `PresenceNote.CLEARED` single-space path (an empty note cannot match
  the peer's `[\w\s]+`, so clearing publishes one space) is covered by unit tests and was **not**
  confirmed against the peer. The run that would have shown it instead uncovered the
  `loadAccount()` state wipe below.
- **Rejection of an undeliverable note on a real keyboard** — the validator is unit-tested, but
  no device run typed an emoji or an accent into the field.
- **A second device of the same account** receiving the note through the settings sync. It cannot
  work while open end 1 stands.
- **The group-conversation case.** The note is only rendered in 1:1 chat headers.

## Open end 3 — a fresh-state trap in AccountSettingsViewModel

`loadAccount()` assigns a **newly constructed** `AccountSettingsState(...)`, so any field it does
not list is reset. It runs on every `AccountEvent.DetailsChanged`, including the one our own
`setAccountDetails` triggers — so the Status field blanked itself a few milliseconds after each
commit. Fixed by carrying the note fields through, but the trap is still there for the next field
somebody adds to that state; a `copy()` would be the safer shape.

## Open end 4 — iOS and macOS

Neither Apple target can send or receive a status note.

- **`publish` is not in `JamiBridgeWrapper.h`.** The iOS and macOS actuals log and drop the call.
  It joins `connectivityChanged` and `setDefaultModerator` in waiting for a wrapper regeneration
  and a `build-jamibridge.sh` rebuild on a Mac — see `apple-bridge-rebuild.md`.
- **The receive half is also blind.** `DaemonBridge.ios.kt:1364` and `DaemonBridge.macos.kt:1063`
  synthesise the buddy notification themselves and pass `""` for `lineStatus`, so even a note
  that arrived could not be displayed. The wrapper's presence callback has to carry the line
  status before the Apple side can show anything.
- **macOS still does not compile** (~95 errors, the `appleMain` source set work on
  `origin/macos-build-and-apple-source-set`), so the macOS half cannot even be built to check.
- The presence suspend/resume from the contacts plan F2 is in the same position: written in
  `IOSApplicationHelper`, never compiled.

Batch all of this into one Mac session rather than paying the rebuild cost three times.
