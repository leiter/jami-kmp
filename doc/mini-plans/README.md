# Mini-plans — index and progress

Six per-area gap analyses, all audited **2026-09-25** against the daemon headers and the
`jami-client-android` reference. Each one lists numbered findings (`F1`, `F2`, …) whose heading
carries its own status marker; this file is only a roll-up, so the individual files stay
authoritative.

**Counted from the finding headings on 2026-10-29: 89 findings — 43 done, 6 partly done,
39 closed without work (invalid, blocked, already implemented, or not applicable to mobile),
1 still open** — `daemon-api-surface-audit.md` F3, the plugin system, which is planned and
waiting on a product decision.

**2026-10-29 — the backlog was worked to the end.** `messaging-conversations.md` F4 implemented
and F13 closed; `notifications-push-background.md` F9 implemented (audio half);
`calling-conferencing.md` F10 implemented; `daemon-api-surface-audit.md` F2 closed. Three of the
five turned out to be **already built or mis-described** — F13 and F2 were fully implemented, and
F4 named a control that was on screen all along. Only F10 and F9 were real gaps.

**2026-10-28 — two plans triaged to the bone, then two more closed.**
`contacts-presence-accounts.md` is **closed**: F2 was implemented (presence suspended on
background), F3/F4/F7/F9 closed without work, F14 already implemented.
`security-networking.md` is **also closed**: it went from 14 open to 3 (F4 and F18 disprove
themselves in their own text and had never been marked; the other nine closed as five SIP-only —
F1, F8, F12, F15, F20 — three Android-parity — F2, F9, F16 — and one stale premise, F21), then F19
was implemented, and F10 and F17 both turned out invalid against the daemon source. The same day
`daemon-api-surface-audit.md` F6 was implemented as a peer-to-peer status note and F9 closed as
absent from the bindings, as was `messaging-conversations.md` F8. Running total:
**40 done, 6 partly done, 37 closed without work, 6 still open** (89 in all).

The counts in this file are produced by classifying the status marker on each `### Fn` heading,
not by hand. An earlier roll-up on 2026-10-26 said 37 done / 29 open using looser bucketing;
where the two disagree, the per-file headings are authoritative.

Every closure was checked against current source first, which caught four findings whose evidence
had gone stale — see the caveat at the end of this file.

| Mini-plan | Findings | Done | Partly | Closed w/o work | Open |
|---|---|---|---|---|---|
| [`calling-conferencing.md`](calling-conferencing.md) | 16 | 12 | 2 | 2 | **0 — closed** |
| [`messaging-conversations.md`](messaging-conversations.md) | 13 | 7 | 0 | 6 | **0 — closed** |
| [`notifications-push-background.md`](notifications-push-background.md) | 13 | 10 | 1 | 2 | **0 — closed** |
| [`contacts-presence-accounts.md`](contacts-presence-accounts.md) | 14 | 5 | 1 | 8 | **0 — closed** |
| [`daemon-api-surface-audit.md`](daemon-api-surface-audit.md) | 12 | 4 | 2 | 5 | 1 |
| [`security-networking.md`](security-networking.md) | 21 | 5 | 0 | 16 | **0 — closed** |

"Closed without work" means the finding was disproved, found already implemented, blocked on a
daemon function that does not exist, or judged not applicable to a mobile client — each with a
dated note in its heading.

## Where the remaining work is concentrated

**Heading counts are not effort.** A re-read on 2026-10-28 found the plan with the largest open
count (security-networking, 14) held only three findings worth implementing, while the smallest
(daemon-api-surface-audit) held the single biggest remaining feature. Sort by the sub-bullets, not
the numbers.

One finding is open, in one plan.

- **`daemon-api-surface-audit.md` — 1 open, and it is the biggest item left.**
  **F3, the plugin system**, planned in [`../plan_plugin_system.md`](../plan_plugin_system.md):
  an install format, a file-management story, per-account and per-conversation state, and a
  dynamic preference form built from daemon metadata. The plan corrects the finding (the bindings
  hold ~20 relevant functions, three of the audit's 19 are unrelated ALSA/PulseAudio device
  calls, and the handler API it never mentions is what makes a loaded plugin do anything) and it
  asks for a decision before any code: plugins are desktop-first in the reference clients, and
  nothing has established that the mobile-primary targets want them.

**Work that came out of using the app, not out of these plans.** Two days of device runs turned up
more than the findings did: a crash class where any group conversation took down every screen that
walks a conversation list (`bbba9fb`), the contact picker showing neither your selection nor
anybody to pick from (`c1b440b`), and group creation not following the reference client at all
(`51084e8`). None of the six plans had a finding for any of it, because they were audited against
the daemon API rather than against the screens. One site of the crash class is still open —
`Account.getByContact`, see [`../KNOWN-ISSUES.md`](../KNOWN-ISSUES.md).

**What is left that is not a finding.** A two-device round on 2026-10-29 closed two of the three
outstanding checks — clearing a status note, and a conference actually ringing — and left one:
**the microphone level reading anything** (notifications F9) needs a call that someone answers,
which adb cannot do on its own. See
[`../two-device-verification-2026-10-29.md`](../two-device-verification-2026-10-29.md), which also
records what the conference run did *not* prove. The Apple batch in
[`../apple-bridge-rebuild.md`](../apple-bridge-rebuild.md) has grown to five items and wants a
Mac.

**Closed plans.** `contacts-presence-accounts.md` (2026-10-28) — the real work was F2, presence
suspended on background, which also unified three independent presence subscribers behind a single
refcount in `ContactService.subscribeBuddy`. `security-networking.md` (2026-10-28) — the real work
was F6 (port validation), F11 (certificates) and F19 (moderation and local-network settings);
F10 and F17 were both closed as invalid after reading the daemon rather than `account_const.h`,
which is the lesson that plan ends on.

## Caveats worth carrying

- The audit's "~150 daemon functions, ~100 wired" figure counts **functions declared in the
  interface headers**, which is not the same set as the SWIG bindings the Android bridge actually
  calls. Check the bindings before planning work off that number. For reference, the Android
  bridge calls 149 distinct `JamiService.*` entry points as of 2026-10-26. Two findings closed on
  2026-10-28 for exactly this reason: `messaging-conversations.md` F8
  (`reloadConversationsAndRequests`) and this plan's F9 (all eight media-player functions) are in
  the headers and in **none** of the bindings, so there is nothing to call from any platform.
  The reverse case bit the contacts plan on 2026-10-28: `publish`, `getSubscriptions`,
  `setSubscriptions` and `getMessageStatus` are all **in** the bindings but absent from
  `DaemonBridgeApi`, so those findings were "not wanted", not "not available" — and F3's cited
  signature was wrong, because it was read off the header (`publish` takes a `boolean` status, not
  an int).
- A finding marked DONE means the capability is wired and unit-tested, not that it has been
  exercised against a live peer. The certificate slice (`security-networking.md` F11) was marked
  done on 2026-10-13 and then yielded five real bugs when it was first run on an iOS simulator the
  next day — see `ios_implementation_gap.md` §8.
- **A finding's own evidence goes stale too, not just its verdict.** Verify before closing, not
  only before implementing. The 2026-10-28 triage checked every premise against source and four did
  not survive: `contacts-presence-accounts.md` F14 was fully implemented, F7's fetch/sync half had
  been implemented all along, and `security-networking.md` F21's whole premise ("users can enter
  invalid paths") was void because the certificate fields are a file picker, not text inputs. A
  further three understated themselves — F8, F9 and F16 each claim "no ConfigKey mapping" while
  `ACCOUNT_DTMF_TYPE`, `ACCOUNT_ACTIVE_CALL_LIMIT` and `TURN_REALM` all exist (`ConfigKey.kt:41`,
  `:26`, `:87`) and only want a UI control. Two more, F4 and F18, contained their own disproof in
  their Impact sections and had been counted as open work for a month.
  A fifth case on 2026-10-29: `messaging-conversations.md` F4 described a missing control ("no
  file attachment option while replying") that is plainly present on screen. The underlying gap
  was real but one layer down — the reply-to was dropped between the view model and the daemon —
  so the finding would have been closed as invalid by anyone who checked only the sentence it
  wrote. Read the finding's *Impact*, not its *Evidence*, when the two could come apart.

- **No QA/Testing/Verification sections** in these files; that is a standing preference, not an
  oversight.
