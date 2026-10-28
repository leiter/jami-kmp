# Mini-plans — index and progress

Six per-area gap analyses, all audited **2026-09-25** against the daemon headers and the
`jami-client-android` reference. Each one lists numbered findings (`F1`, `F2`, …) whose heading
carries its own status marker; this file is only a roll-up, so the individual files stay
authoritative.

**Counted from the finding headings on 2026-10-26: 89 findings — 37 done, 6 partly done,
17 closed without work (invalid, blocked, or not applicable to mobile), 29 still open.**

**2026-10-28 — two plans triaged to the bone.** `contacts-presence-accounts.md` is **closed**: F2
was implemented (presence suspended on background), F3/F4/F7/F9 closed without work, F14 already
implemented. `security-networking.md` went from 14 open to **3** — F4 and F18 disprove themselves
in their own text and had never been marked, and the other nine closed as five SIP-only (F1, F8,
F12, F15, F20), three Android-parity (F2, F9, F16) and one stale premise (F21). Running total:
**38 done, 6 partly done, 33 closed without work, 12 still open** (89 in all).

Every closure was checked against current source first, which caught four findings whose evidence
had gone stale — see the caveat at the end of this file.

| Mini-plan | Findings | Done | Partly | Closed w/o work | Open |
|---|---|---|---|---|---|
| [`calling-conferencing.md`](calling-conferencing.md) | 16 | 11 | 2 | 2 | 1 |
| [`messaging-conversations.md`](messaging-conversations.md) | 13 | 6 | 0 | 4 | 3 |
| [`notifications-push-background.md`](notifications-push-background.md) | 13 | 9 | 1 | 2 | 1 |
| [`contacts-presence-accounts.md`](contacts-presence-accounts.md) | 14 | 5 | 1 | 8 | **0 — closed** |
| [`daemon-api-surface-audit.md`](daemon-api-surface-audit.md) | 12 | 3 | 2 | 3 | 4 |
| [`security-networking.md`](security-networking.md) | 21 | 4 | 0 | 14 | 3 |

"Closed without work" means the finding was disproved, found already implemented, blocked on a
daemon function that does not exist, or judged not applicable to a mobile client — each with a
dated note in its heading.

## Where the remaining work is concentrated

**Heading counts are not effort.** The three plans below are ordered by open-heading count, which
is how this section used to read as well — but a re-read on 2026-10-28 found the largest count
(security-networking, 14) holds only three findings worth implementing, while the smallest
(daemon-api-surface-audit, 4) holds the single biggest remaining feature. Sort by the sub-bullets,
not the numbers.

- **`security-networking.md` — 3 open: F10, F17, F19.** Triaged 2026-10-28; the other 18 are
  resolved or closed. What is left is genuine Jami surface, not SIP:

  - **F10** presence/subscription settings at account level. The `PRESENCE_*` keys are mapped
    (`ConfigKey.kt:103-106`) but reach no UI, and `Account.presenceEnabled` is not mapped at all.
    Natural companion to `contacts-presence-accounts.md` F2, which added the background
    suspend/resume but no user-facing switch.
  - **F17** `Account.bootstrapListUrl` and `RingNS.account`, neither mapped — DHT bootstrap and
    nameserver account, Jami-specific.
  - **F19** swarm moderator settings (`defaultModerators`, `localModeratorsEnabled`,
    `allModeratorsEnabled`) plus `accountPublish`/`accountDiscovery`, nothing mapped. The natural
    follow-on to `calling-conferencing.md` F3, which wired `setModerator` per call but left
    account-level defaults unset.

  The closures fell into four groups, each recorded with its grounds in the finding itself: five
  SIP-only (F1, F8, F12, F15, F20 — F1 is the anchor, and they are really one unbuilt screen rather
  than five findings, together with `contacts-presence-accounts.md` F7), three Android-parity
  non-gaps the reference client does not expose either (F2, F9, F16), two self-disproved (F4, F18)
  and one whose premise had gone stale (F21).

- **`daemon-api-surface-audit.md` — 4 open.** The plugin system (19 daemon functions, untouched —
  confirmed present in the Android bindings on 2026-10-28, so it is buildable rather than blocked,
  but it is a whole feature and wants its own plan), codec configuration UI, presence server
  integration, media-player instance management.
- **`contacts-presence-accounts.md` — closed 2026-10-28.** Nothing open. The one piece of real
  work was F2, presence suspended on background, which also unified three independent presence
  subscribers behind a single refcount in `ContactService.subscribeBuddy`. The rest closed without
  work: F3 (`publish()`), F4 (`getSubscriptions()`/`setSubscriptions()`) and F9
  (`getMessageStatus`) are not wanted, and F7's SIP credential editing belongs with the SIP cluster
  in `security-networking.md`.

## Caveats worth carrying

- The audit's "~150 daemon functions, ~100 wired" figure counts **functions declared in the
  interface headers**, which is not the same set as the SWIG bindings the Android bridge actually
  calls. Check the bindings before planning work off that number. For reference, the Android
  bridge calls 149 distinct `JamiService.*` entry points as of 2026-10-26.
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

- **No QA/Testing/Verification sections** in these files; that is a standing preference, not an
  oversight.
