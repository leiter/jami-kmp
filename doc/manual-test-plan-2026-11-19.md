# Manual test plan — the 2026-11-19 UI work

Covers `62f7120`, `6db96c3`, `8185c11`, `1cfbc80`, `68bca0e`. All of it is shared Kotlin, so it
applies to Android and iOS alike; the steps below are written for the Pixel pair because that is
the quicker loop.

**Sections A, B, C and D passed on the Pixel pair, 2026-11-19**, D only after three attempts —
see `doc/TODO.md` for what the first two fixes missed. The call screen no longer blanks when a
peer switches their camera off, and a peer's avatar now matches between the conversation list
and the chat header. F is still outstanding, as is the slow video recovery.

The suite (1082, green) says nothing about motion, bubble layout or anything whose failure mode
is "looks wrong" — which is most of this list, hence the walk.

## Build and install

    ./gradlew :android-app:installStandardDebug

Two devices are attached; always name one, never let adb pick:

| Serial | Device | Role |
|---|---|---|
| `FA7AJ1A06417` | Pixel 2 | call host |
| `37281JEHN03065` | Pixel 7a | second party (also runs the official `cx.ring`) |

    adb -s FA7AJ1A06417 install -r <apk>        # or: ANDROID_SERIAL=FA7AJ1A06417 ./gradlew …

App id is `net.jami.android` (the `.harness` suffix is the e2e flavour — not this).

---

## A. Message runs, group avatars, and the 1:1 name — `62f7120` — PASSED 2026-11-19

**A1 — the defect this fixes (1:1).** Open any 1:1 conversation with history.

- *Expect:* the peer's name appears **nowhere** above the bubbles. Before this change it was
  printed above every single incoming message.
- The name in the top bar is unaffected and should still be there.

**A2 — a run in a group.** In a group, have one peer send three messages within a minute.

- *Expect:* their name **once**, above the first; their avatar **once**, beside the last; the
  three bubbles sitting flush with tight corners where they meet, and the normal gap only above
  the first and below the last.
- The two upper bubbles keep the avatar column empty — the left edges must stay aligned. If the
  upper two are indented differently from the third, the gutter is wrong.

**A3 — the breaks.** Each of these must split a run in two (name and avatar appear again):

| Do this | Why it breaks |
|---|---|
| Second peer sends between two of the first peer's messages | author changed |
| You reply in the middle | direction changed |
| Wait **more than 10 minutes**, then send again | past the gap window |
| Send a file or image between two text messages | not a text/call entry |
| Send a message that is **only** an emoji | drawn as its own thing |
| Use the reply action on your own last message | a quote needs its own header |

The 10-minute one is the slow case; it can be left until the end, or trusted to
`MessageSequencingTest`, which covers exactly this boundary.

**A4 — files in a group.** Send an image into a group from the other device.

- *Expect:* the sender's name above it and their avatar beside it, same as a text message. A
  transfer is always a run of one, so it always carries both.

---

## B. Media transition and swipe-to-dismiss — `6db96c3` — PASSED 2026-11-19

Needs a conversation with at least one **received, downloaded** image.

**B1 — chat → viewer.** Tap the image in the bubble.

- *Expect:* it grows out of the bubble into full screen. A hard cut means the shared element did
  not resolve — the two ends disagree on the key, or the scope is not reaching the bubble.
- Press back: it should shrink back into the bubble, not just vanish.

**B2 — gallery → viewer.** Chat overflow → gallery, tap a tile. Same expectation.

**B3 — swipe to dismiss.** In the viewer, drag the image down.

- *Expect:* the image follows your finger and the black backdrop thins as it moves, so the chat
  shows through. Past about half a screen it closes; released earlier it springs back.

**B4 — the negative case, and the one most likely to be wrong.** Pinch to zoom in, **then** drag
up and down.

- *Expect:* the image **pans**. It must not dismiss. If a zoomed image closes when you try to
  move it around, the gesture guard is broken and that is worse than having no gesture.

**B5 — list animation.** With three or more conversations, have the other device send to one
that is not already at the top.

- *Expect:* the row slides up to its new position. Before this it teleported.

---

## C. Jump to latest, and the auto-scroll — `8185c11`, `1cfbc80`

Needs a conversation with roughly twenty messages.

**C1 — the button appears.** Scroll back more than ~8 messages.

- *Expect:* a small round down-arrow button fades in over the bottom of the list, above the
  composer, horizontally centred. Scroll back down: it fades out again.

**C2 — it works.** Tap it. *Expect:* the list animates to the newest message and the button goes.

**C3 — no more yanking.** Scroll well back into history and **leave it there**. Have the other
device send a message.

- *Expect:* you **stay where you are**. The button appears (or stays). Before this change you
  were thrown to the bottom and lost your place.

**C4 — still sticky at the bottom.** Scroll to the very bottom. Have the other device send.

- *Expect:* the view follows down to the new message, as it always did. This is the half of the
  old behaviour that was worth keeping, and the threshold is deliberately 2 rather than 0 — if
  C4 fails while C3 passes, that threshold is too tight.

**C5 — your own sends always win.** Scroll back into history, then send a message yourself.

- *Expect:* jumps to the bottom so you can see what you sent.

---

## D. The call screen shows a name — `68bca0e`

**The precondition is the whole test.** A peer whose name was already resolved displayed
correctly before this fix too, so a careless run proves nothing.

1. Force-stop the app on the receiving device: `adb -s <serial> shell am force-stop net.jami.android`
2. Open the app and go **straight to the call** — do not open that peer's conversation, do not
   scroll the contact list to them first. Either place a call to them, or have them call you.
3. *Expect:* the call screen titles them with their **registered name**, with the username
   beneath it when it differs. A 40-hex string is the bug.

It may take a second to arrive — the lookup is asynchronous and the title updates when the
answer lands. A peer who has genuinely never registered a name has none, and the id is then
correct; use a contact you know is registered.

---

## E. Nothing to do

- **`AccountService` init order (`1cfbc80`).** Production dispatchers always deferred the launch
  past construction, so this never misbehaved outside the test runner. `AccountServiceInitOrderTest`
  covers it and was checked against the old declaration order. No device step.

## F. Still open from before today

The build 50 video fixes (`1cbbc3b`) have not been re-verified since the 2026-11-18 session, and
R1 (answering via CallKit turns our video off), R3, R4, R5 and E1 remain open from it. See
`doc/findings-protocol-2026-11.md` → "Video call test session (build 48…)". Worth folding into
the same sitting, since it needs the same two devices.
