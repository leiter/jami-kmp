# Accessibility audit — HomeScreen, ChatScreen, CallScreen

**Audited 2026-09-30. Re-verified and then largely implemented on 2026-11-07.** Most of the
open list below is now closed; what remains is listed at the end. Line numbers are from the
2026-11-07 re-check.

This is the audit that `doc/stabilization-findings-2026-09-04.md` F11 asked for ("Compose UI
exposes almost no accessibility metadata … worth an audit pass"). The audit was run; its results
lived only in a terminal screenshot until it was recovered on 2026-11-07, and were implemented
the same day.

Repo-wide, before the pass: **three** uses of `semantics(mergeDescendants)` / `liveRegion` in
all of `commonMain`, **zero** `testTag`s, and 15 `contentDescription`s written as English
string literals at their call site. After it: merged semantics and live regions on the nodes
listed below, six tags, and no hardcoded descriptions anywhere. The desktop suite stays at
918 tests, 0 failures, and both the desktop and Android targets compile — but see "Still open"
on why none of this is yet protected by a test.

---

## Fixed 2026-11-07

- **Conversation rows are merged** (`HomeScreen.kt`). One node, `Role.Button`, a composed label
  (name, unread count, preview, timestamp), and the four long-press entries — pin/unpin,
  mute/unmute, block, delete — published as `CustomAccessibilityAction`s, so the menu is
  reachable without performing a long press. Tagged `TestTags.CONVERSATION_ROW`.
- **Pin and mute are state again.** They moved to `stateDescription` ("Pinned", "Muted",
  "Pinned, muted"); the indicator icons no longer carry the *action* string, which had them
  announcing "Mute" for an already-muted conversation.
- **The search row is named.** `Role.Button` plus "Search conversations", so the tappable row
  that wraps two further controls is no longer an unnamed node between them.
- **The chat header is a merged heading button**, instead of an avatar, a name and a status
  note sitting loose beside an unnamed control.
- **Live regions**: the typing indicator and the call-status line are `LiveRegionMode.Polite`,
  so "contact is typing" and ringing → connected → on hold are announced rather than found by
  exploring.
- **The composer field is labelled.** Its placeholder lives in `decorationBox` and vanishes on
  first keystroke, so the field was unnamed exactly while in use.
- **Screen titles are headings** (`JamiTopBar`, both titled styles), giving structural
  navigation where there was none.
- **Touch targets**: five `IconButton`s in the chat (camera, overflow, send, cancel-reply,
  accept-transfer) went from 32–40 dp to the 48 dp Material minimum; the icons inside are
  unchanged.
- **Every hardcoded English `contentDescription` is gone** — 15 of them, across `JamiTopBar`,
  `CallScreen`, `ChatScreen`, `QrScanScreen`, `MediaViewerScreen`, `VideoPlayerScreen`,
  `LocationSharingScreen`, `ParticipantGrid` and `JamiMessageInput`. A `grep` for
  `contentDescription = "` now returns nothing. The pending-invitations banner was worse than a
  label: its text was built in source as `"$n pending invitation" + "s"`, which no locale could
  translate and no language could pluralise. It is a plural resource now.
- **`TestTags` exists** (`net/jami/ui/TestTags.kt`) with six tags on the nodes whose semantics
  are worth protecting. The repo had none.

## Still open

- **Incoming messages and the recording banner are not live regions.** The typing indicator and
  call status are; these two still change silently.
- **No compose UI test runs against any of this.** `compose-ui-test-junit4` is in the version
  catalog but is not wired into `shared/build.gradle.kts`, so the new tags are for manual and
  automated sweeps only. Wiring it up is the natural follow-up — I1's Robolectric item is the
  neighbouring piece of the same gap.
- **Menu toggles carry no `Role.Switch`.** Pin and mute are `DropdownMenuItem`s rather than
  switches, and their state is now on the row, so this is arguably resolved by a different
  route than the audit imagined — but a reader still hears them as plain menu entries.
- **Touch targets outside the chat composer** were not swept; only the sites the audit named
  were measured.

## Closed before this pass

- **Selected account not identifiable** (`HomeScreen.kt:196`) — the account button merges its
  descendants and carries `Role.Button`.
- **`JamiAvatar` / `JamiBadge`** — both already carry descriptions, the badge via a plural
  unread-count string, and Away/Offline no longer share a colour
  (`JamiAvatar.kt:158`: "Away and Offline must not share a colour"). The audit listed this as
  open; it had been fixed in the meantime.
- **Delivery status and the double-read timestamp** — status icons carry
  `content_desc_message_*`, and `ChatScreen.kt:1148` already suppresses the duplicate timestamp
  in the semantics tree.

## The audit's suggested order, for the record

Followed roughly in reverse, because items 1 and 2 turned out to be already done:

1. Unlabelled controls, delivery status and the double-read timestamp — these block core flows.
2. `JamiAvatar` / `JamiBadge` descriptions and the Away/Offline colour collision.
3. Touch targets in the compose bar.
4. Hardcoded strings swapped for the existing resource keys.
5. Merged semantics + custom actions on list rows, then live regions (structural).

That convention now exists: `net.jami.ui.TestTags`, and anything landing here should add to it.
