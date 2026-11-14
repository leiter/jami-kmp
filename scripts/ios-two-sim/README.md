# Two-simulator peer test (iOS)

Two jami-kmp iOS clients on two simulators on one Mac, talking over the **real** Jami network
(DHT proxy + TURN; on one Mac ICE connects over host candidates, so NAT is not in the way).
Driven by [Maestro](https://maestro.mobile.dev) through the UI; no test hooks in the app.

```sh
scripts/ios-two-sim/run.sh --build        # build, reset both simulators, run every phase
scripts/ios-two-sim/run.sh                # reuse the last build
scripts/ios-two-sim/run.sh --from chat    # resume a run (IDs are kept in build/ios-two-sim/state.env)
scripts/ios-two-sim/run.sh --only edit,react --keep
```

Phases, in order — each stops the run on the first failure:

| Phase | What it proves |
|---|---|
| `setup` | builds if needed; **erases** JamiTest-A/B, sets them to English, installs, seeds Photos |
| `onboard` | account without username + display name, on both; reads both Jami IDs from `config.yml` |
| `invite` | Alice finds Bob by ID and opens the chat (creates the 1:1 swarm, sends the request) |
| `accept` | Bob sees exactly "1 pending invitation" and accepts; both see "Bob has joined" |
| `chat` | one message each way |
| `edit` | Alice edits; Bob sees the new text, not the old |
| `react` | Bob reacts 👍; Alice sees it |
| `delete` | Alice deletes a message Bob already has; it disappears for Bob |
| `offline` | Bob's app is killed, Alice sends, Bob relaunches and receives it |

Artefacts go to `build/ios-two-sim/`: one Maestro log per flow and, on failure, a screenshot of
both simulators (`FAIL-<flow>-A.png` / `-B.png`).

## Requirements and cost

- Xcode, Maestro 2.8+ (`~/.maestro/bin/maestro`), `jq`, the simulator symlinks
  (`python3 scripts/make_sim_links.py`).
- The simulators **JamiTest-A** and **JamiTest-B** (device type `$DEVICE_TYPE`, default
  iPhone 17) are created on first use and **erased at the start of every run**. Nothing else is
  touched. The display-name field is tapped by position, so changing the device type may need
  `flows/onboard-profile.yaml` adjusted.
- First run on a new simulator is slow (first boot ≈ 4 min each, Maestro's XCTest driver
  install). Later runs: ≈ 1 min reset per simulator.
- **Memory.** Two booted simulators on an 8 GB Mac leave no room for a Gradle build: run Gradle
  first, or with the simulators shut down. `MAESTRO_DRIVER_STARTUP_TIMEOUT` is raised to 240 s
  because the driver starts slowly under that pressure.

## Things that will bite

- **Other tools on the same Mac.** Anything that targets `booted` (a `simctl … booted`, a
  `maestro test` without `--device`) lands on these simulators while they are the only ones
  booted. Every flow starts with `launchApp: stopApp: false` to bring Jami back to the front,
  but taps from another tool in the middle of a flow will still break it.
- **Maestro and Compose.** Text fields expose no text, not even a placeholder — reach them by
  position or relative to a label. The search bar is "Search conversations" (its description),
  not "Search or add". A message bubble's text is the message plus a status line
  (`"Hello\nWaiting to sync"`), hence the `(\n[\s\S]*)?` suffix on message matchers.
  Percentages in `point:` must be integers.
- **System prompts** stay in the simulator's original language even after the app is switched
  to English, so `onboard-profile.yaml` matches "Allow|Erlauben".

## Findings so far

- 2026-11-14 — the home banner read "%d pending invitation": bare `%d` is not substituted by
  Compose resources. Fixed (`8ac0a75`); `accept` now asserts the rendered count.
- 2026-11-14 — a race in `AccountService.onContactAdded` downgraded Alice's freshly created 1:1
  to `Syncing` ("Downloading conversation from peer"); every message then waited in the outbox
  ("Waiting to sync") and never reached Bob. Fixed with `Account.newSwarmIfAbsent` (`2e00655`).
  The inviter's "<peer> was invited" event was missing in the same run, so `invite` does not
  assert it.
- 2026-11-14 — `enterForeground()` called `connectivityChanged`, re-registering the account on
  every foreground; an invitation accepted in that window never cloned. Fixed (`35199a2`).
- 2026-11-14 — SIGSEGV right after onboarding's `updateProfile`: libjami dereferences account
  info that does not exist until the account has finished initializing. Fixed by waiting for
  initialization (`11df510`).

## Status

Setup, onboard, invite and accept have passed; chat → offline had not yet had a clean
end-to-end run when this was committed. Media and group phases are not written.
