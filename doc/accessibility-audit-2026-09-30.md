# Accessibility audit — HomeScreen, ChatScreen, CallScreen

**Audited 2026-09-30. Findings re-verified against source on 2026-11-07** — line numbers below
are from that re-check, not from the original audit, and each item says what changed since.

This is the audit that `doc/stabilization-findings-2026-09-04.md` F11 asked for ("Compose UI
exposes almost no accessibility metadata … worth an audit pass"). The audit was run; its results
lived only in a terminal screenshot until now. Two of its findings were fixed in the meantime
(noted inline); the rest are open.

Repo-wide numbers on 2026-11-07: **three** uses of `semantics(mergeDescendants)` / `liveRegion`
in all of `commonMain`, and **zero** `testTag`s — so none of what follows is covered by a test,
and a regression in any of it is invisible to the suite.

---

## Open

- **Conversation rows are not merged** (`HomeScreen.kt:643`). The row is a `combinedClickable`
  whose descendants are not merged, so name, mute icon, pin icon, timestamp, preview and badge
  are each announced as a separate node sitting next to the row itself. It wants
  `semantics(mergeDescendants = true)` with one description, plus a `CustomAccessibilityAction`
  for the long-press menu (pin / mute / block / delete), which a screen reader cannot reach at
  all today. The same shape applies to the `ChatScreen` title and the pending-requests banner,
  neither of which carries `Role.Button`.

- **Nested clickables in the search bar** (`HomeScreen.kt`, the search row). A clickable `Row`
  containing a clickable child gives confusing traversal, and the row has neither `Role.Button`
  nor search semantics.

- **State is conveyed as an action label** (`HomeScreen.kt:690`, `:699`). The muted and pinned
  row indicators now carry a `contentDescription`, but they borrow the *action* strings
  (`content_desc_mute`, `conversation_action_pin`), so the indicator for "this conversation is
  muted" announces as "Mute". State belongs in `stateDescription`, not in the label. The menu
  toggles have the same shape: they carry only the next action and no `Role.Switch`.
  *Partially improved since the audit — the descriptions exist now; the state/action confusion
  does not.*

- **No live regions** (`ChatScreen.kt:938`, the typing indicator). The bubble has a
  `contentDescription` but no `liveRegion`, so it is only found by exploring. Incoming messages,
  call-state transitions and the recording banner all change silently for the same reason.

- **`BasicTextField` has no label** (`ChatScreen.kt:1974`). The compose field relies on its
  placeholder (`write_a_message`), which is drawn in the `decorationBox` and disappears the
  moment text is entered, leaving the field unnamed while it is being used.

- **No headings** on any screen title, so there is no structural navigation.

- **Touch targets in the compose bar** were flagged as below the 48 dp minimum.

- **`JamiAvatar` / `JamiBadge` descriptions and the Away/Offline colour collision** — one fix
  that propagates everywhere, since both are used on every screen.

## Closed since the audit

- **Selected account not identifiable** (`HomeScreen.kt:196`) — fixed. The account button now
  merges its descendants and carries `Role.Button`.
- **`JamiAvatar` merges its descendants** (`JamiAvatar.kt:132`) — partially done; the colour
  collision above is the remaining half.

## Suggested order (from the audit, unchanged)

1. Unlabelled controls, delivery status and the double-read timestamp — these block core flows.
2. `JamiAvatar` / `JamiBadge` descriptions and the Away/Offline colour collision.
3. Touch targets in the compose bar.
4. Hardcoded strings swapped for the existing resource keys.
5. Merged semantics + custom actions on list rows, then live regions (structural).

Anything here that lands should take a `testTag` with it; at zero repo-wide, the first one
also establishes the convention.
