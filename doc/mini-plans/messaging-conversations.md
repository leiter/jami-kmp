# Mini-Plan: Messaging & Swarm Conversation Gaps

_Audited 2026-09-25. Scope: text/file messaging, swarm conversation management, moderation._

## Summary

jami-kmp implements most core messaging and conversation features (message send/receive/edit, file transfers, member add/remove, conversation metadata). However, several daemon capabilities exist but are not exposed or called in the KMP codebase: conversation history search via daemon, message/conversation count queries, conversation cache management, and member role changes via daemon. One correctness bug found in `ConversationFacade.setConversationPreferences()` which claims it will send to daemon but never does (**fixed 2026-10-04**). File message replies lack UI affordance to attach/quote files. Lazy message history loading on scroll shows signs of race conditions and incomplete interaction parsing.

## Findings

### F1: ConversationFacade.setConversationPreferences() silently fails to call daemon — bug — **DONE (2026-10-04)**
**Evidence:** `ConversationFacade.kt:259-267`
**Impact:** Conversation preferences set via the facade (e.g., mute settings, notification prefs) are never persisted to the daemon. They only update local storage. If the app is killed and restarted, user preferences are lost. Correct path is `AccountService.setConversationPreferences()` which does call the daemon.
**Daemon support:** `setConversationPreferences(accountId, conversationId, prefs)` — `conversation_interface.h:72-74`; signal `ConversationPreferencesUpdated` exists to notify of changes — `conversation_interface.h:234-240`

### F2: No daemon-side conversation history search — capability gap — **ALREADY IMPLEMENTED; finding was stale (verified 2026-10-12)**
**Evidence:** `DaemonBridge.kt:169` defines `searchConversation()` and it is called in `AccountService.kt` (line ~1250), but no UI screen or viewmodel triggers it. The search result is handled via `onMessagesFound()` callback but never displayed.
**Impact:** Users cannot search within a conversation history on the daemon side. Client-side filtering in memory is currently the only option, which fails for large histories.
**Daemon support:** `searchConversation(accountId, conversationId, author, lastId, regexSearch, type, after, before, maxResult, flag)` — `conversation_interface.h:108-117`; signal `MessagesFound` — `conversation_interface.h:130-137`

### F3: Message history history loading race condition — correctness issue — **DONE (2026-10-04)**
**Evidence:** `ChatViewModel.kt:849-890` (`loadMore()` and `loadMessagesFromHistory()`) and `ConversationFacade.kt:894-912` — history is loaded asynchronously via `loadConversation()` daemon call, but there is no guard against overlapping loads. If user scrolls to load more while a previous load is in-flight, the `onSwarmLoaded` callback may insert messages out of order. The `messages` StateFlow is updated via `appendMessage()` without checking if the daemon load has completed.
**Impact:** Chat history can appear out of order or with duplicates if the user rapidly scrolls. No visible loading indicator during history fetch.
**Daemon support:** `loadConversation(accountId, conversationId, fromMessage, n)` returns a task ID; `loadSwarmUntil()` for date-range queries — `conversation_interface.h:94-101`

### F4: File transfer replies lack file picker on reply — UX gap
**Evidence:** `ChatViewModel.kt:469-477` (`startReply()`) captures `replyToId` but does not offer a "quote file" or "include file with reply" UI. `ChatScreen.kt` message input shows reply banner but no file attachment option while replying.
**Impact:** User can only reply with text to a file message; cannot directly re-share or attach a file in the same interaction. User must cancel reply, send the file separately, then try to remember to reply to the original message.
**Daemon support:** `sendFile()` accepts `replyTo` parameter — `datatransfer_interface.h:106-110`; files can be nested in reply threads via the daemon

### F5: No member role promotion/demotion in UI or API — capability gap — **BLOCKED: no daemon function exists (verified 2026-10-12)**
**Evidence:** `Conversation.kt` tracks `roles: MutableMap<String, MemberRole>` (with ADMIN, MEMBER, INVITED, LEFT, BLOCKED states), and `ContactDetailsViewModel.kt:22` exposes `memberRoles` to the UI, but there is no function in `DaemonBridge`, `AccountService`, or `ConversationFacade` to change a member's role.
**Impact:** Group admins cannot promote/demote members via the app. The feature is partially exposed (roles are fetched and displayed) but non-functional. No screens (e.g., `ConversationDetailsScreen`) offer a "promote to admin" / "demote" action.
**Daemon support:** No daemon function found in `conversation_interface.h` for role changes; role modifications may be done through `updateConversationInfo()` — `conversation_interface.h:67-69`

### F6: Conversation preferences not synced on load — data sync issue — **DONE (2026-10-04)**
**Evidence:** `ConversationFacade.kt:1750-1774` loads conversation info and members on-demand, but never calls `getConversationPreferences()` from the daemon. Preferences are only queried in `AccountService.getConversationPreferences()` on explicit request, not during conversation load.
**Impact:** Conversation preferences (e.g., mute, archive, notification settings) are stale until the user opens conversation settings. Changes made on another device are not reflected until the user manually refreshes.
**Daemon support:** `getConversationPreferences()` — `conversation_interface.h:75-76`; signal `ConversationPreferencesUpdated` — `conversation_interface.h:234-240`

### F7: clearCache() and countInteractions() daemon functions unused — capability gap
**Evidence:** `DaemonBridge.kt` does not define `clearCache()` or `countInteractions()` methods despite these existing in `conversation_interface.h:107, 102-106`.
**Impact:** No way to clear cached messages or count messages matching criteria (e.g., "how many unread messages in this conversation?"). Unread count is inferred from the contact's unread flag, not a daemon query.
**Daemon support:** `clearCache(accountId, conversationId)` — `conversation_interface.h:107`; `countInteractions()` with filters — `conversation_interface.h:102-106`

### F8: reloadConversationsAndRequests() not wired — capability gap
**Evidence:** `DaemonBridge.kt` does not define `reloadConversationsAndRequests()` method despite it existing in `conversation_interface.h:118`.
**Impact:** No explicit "refresh all conversations and pending requests" operation. The app relies on daemon signals to update state; no force-refresh from the service layer.
**Daemon support:** `reloadConversationsAndRequests(accountId)` — `conversation_interface.h:118`

### F9: Message status mapping may be incomplete — potential bug
**Evidence:** `ConversationFacade.kt:1687` maps daemon status integers via `Interaction.MessageStates.fromInt(it.value)`. The daemon sends status codes (e.g., 0=sent, 1=delivered, 2=displayed, etc.), but there is no comprehensive test of all possible daemon status codes or validation that the UI renders all states correctly.
**Impact:** Unknown/unmapped message statuses (e.g., from a newer daemon) may silently fall through to a default state, giving the user incorrect delivery feedback.
**Daemon support:** Daemon emits `AccountMessageStatusChanged` signal — `configurationmanager_interface.h:407-415`

### F10: Lazy-loaded message history does not preserve scroll position — UX issue — **DOES NOT APPLY to this implementation (verified 2026-10-12)**
**Evidence:** `ChatViewModel.kt:849-890` appends old messages but does not adjust scroll offset. The list is rebuilt with new messages prepended, which causes the UI to jump. Android `LazyColumn` and iOS equivalents need explicit scroll anchor management.
**Impact:** When user scrolls up to load history, the chat jumps and loses context. User sees the message list shift instead of scrolling into history.
**Daemon support:** Not a daemon issue; architectural pattern issue in message ordering

### F11: No handling of "initial" invitation messages in group conversations — silent no-op — **INVALID; adjacent bug fixed instead (2026-10-04)**
**Evidence:** `ConversationFacade.kt:1571-1579` only renders "initial" (invitation) events in 1:1 conversations; in group chats, the event is silently converted to a base `Interaction` with no visible trace. If a user is added to a group, there is no system message or notification shown to the group members.
**Impact:** Group members never see who was added, when, or by whom. Audit trail is lost. The daemon emits the event (a "member" event with action "add"), but it is silently dropped.
**Daemon support:** `ConversationMemberEvent` signal — `conversation_interface.h:197-204` (events: 0=add, 1=joins, 2=leave, 3=banned)

### F12: No visible UI for banning/unbanning conversation members — capability gap
**Evidence:** Daemon supports `removeConversationMember()` (which may include a ban flag in the message body), but `DaemonBridge.kt` only exposes a simple `removeConversationMember(accountId, conversationId, uri)` with no ban parameter. `ConversationDetailsScreen.kt` shows a list of members but has no "ban member" action.
**Impact:** Conversation admins cannot ban members. Only removal (which may rejoin) is possible via the UI.
**Daemon support:** `removeConversationMember()` — `conversation_interface.h:82-84`; `ConversationMemberEvent` signal indicates banned state (event=3) — `conversation_interface.h:197-204`

### F13: Profile vCard exchange not tied to conversation level — data model issue
**Evidence:** `ConversationFacade.kt` does not handle profile vCard updates per conversation. `onProfileReceived()` callback in `DaemonBridge.kt:445` receives the vCard path but there is no code to parse it or link it to a specific conversation's contact profile.
**Impact:** Contact profiles updated via vCard are not reflected in conversation UI. User info (name, avatar) may be stale.
**Daemon support:** `updateProfile()` and `ProfileReceived` signal — `configurationmanager_interface.h:104-109, 428-434`

## Three further findings resolved on inspection (verified 2026-10-12)

Checked against source while taking stock of the plans; none needed code changes:

- **F2 (no daemon-side search) — already implemented.** `ChatViewModel.searchConversation()`
  (`:1207`) calls `accountService.searchConversation` → daemon, results arrive via
  `onMessagesFound` → `handleSearchResults` (`:1257`), and `ChatState` carries
  `searchQuery`/`searchResults`/`isSearchActive` with search UI in `ChatScreen`. The finding's
  "no UI screen or viewmodel triggers it" was already untrue.
- **F10 (scroll jump on history load) — does not apply.** The message list uses
  `reverseLayout = true` (`ChatScreen.kt:571`), so older messages appended to the data list
  render *above* the viewport and nothing shifts. The finding assumed a prepend into a normal
  layout, which this screen does not do.
- **F5 (member role promotion/demotion) — blocked, not deferred.** `conversation_interface.h`
  exposes only `addConversationMember`, `removeConversationMember` and `getConversationMembers`;
  there is no role-change function in the header *or* the SWIG bindings. The plan's own
  "blocking risks" note anticipated this. Roles can only change through
  `updateConversationInfo()` if the daemon ever honours that, which is unverified — so this
  cannot be implemented as written.

**Genuinely open:** F4 (attach a file while replying), F7 (`clearCache`/`countInteractions`),
F8 (`reloadConversationsAndRequests`), F9 (message-status mapping test coverage),
F12 (ban/unban member UI), F13 (per-conversation vCard profile).

---

## Implementation Status (2026-10-04, branch `docs/mini-plans`)

**Phase 1 is complete** (commit `837a320`). Phases 2 and 3 untouched.

- **F1 — fixed.** `ConversationFacade.setConversationPreferences()` wrote only to local storage
  behind a `// For now, just save locally` comment, so preferences never reached the swarm and
  never synced to the user's other devices. Swarm conversations now also go through
  `AccountService.setConversationPreferences()`; legacy/SIP conversations stay local-only, since
  they have no daemon-side preferences.
- **F6 — fixed.** Nothing read the daemon's copy back. New
  `ConversationFacade.refreshConversationPreferences()` merges daemon values over the local ones
  (local-only keys survive), called during `loadSmartlistLocked()` for each non-syncing
  conversation and from `loadConversationHistory()` when a swarm conversation is opened.
  Note for anyone following the plan's wording: `Conversation.id` is the local DB row id
  (`Int?`), not the swarm id — the swarm id is `conversation.uri.rawRingId`.
- **F3 — fixed, though narrower than described.** `loadMore()` did have an `isLoadingMore`
  guard, but it tested the flag and then set it inside the launched coroutine, so two scroll
  callbacks arriving before the dispatcher ran both passed. The slot is now claimed atomically
  with `getAndUpdate` before launching. The plan's "no guard at all" framing was out of date.
- **F11 — the finding is invalid; a different bug in that code was fixed.** Group member adds
  are *not* dropped: they arrive as `"member"` events and are rendered in both the live
  (`appendMessage`) and history (`interactionToMessageItem`) paths. `"initial"` is the
  conversation's *creation* commit, and libjamiclient also shows it only in 1:1 conversations
  (`AccountService.kt:1414-1421` turns it into an INVALID interaction for groups), so KMP
  matching that is correct. The real defect was the inverse inconsistency: the live path
  rendered `"initial"` as an "invited" system row in groups too, which then disappeared on
  reload because the history path drops it. The live path now checks the conversation mode.

Tests added (all in the existing suites): daemon forwarding for swarm preferences, local-only
for non-swarm, the daemon→local merge, and the synchronous `loadMore()` claim.
`StubDaemonBridge` now records conversation preferences so these paths are assertable.

---

## Proposed Plan

### Phase 1: Critical Correctness Fixes — **COMPLETE (2026-10-04)**
1. [x] **Fix ConversationFacade.setConversationPreferences()** — now writes locally *and*, for swarm conversations, through `accountService.setConversationPreferences()`. Test asserts the daemon receives it.
2. [x] **Add load guard in ChatViewModel.loadMore()** — the in-flight slot is claimed atomically with `getAndUpdate` before the coroutine launches. Test asserts the claim happens synchronously.
3. [x] **Load conversation preferences on init** — `ConversationFacade.refreshConversationPreferences()` merges daemon values over local ones; called from the smartlist load and when a conversation is opened.
4. [x] **"initial" event visibility** — *not* implemented as written; the finding was wrong (see F11 below). The live path was instead aligned with the history path and libjamiclient.

### Phase 2: Missing Daemon APIs
1. **Implement member role changes** — add `setConversationMember(accountId, conversationId, memberUri, role)` to `DaemonBridge` and wire UI for promote/demote actions in `ConversationDetailsScreen`.
2. **Expose clearCache and countInteractions** — add these methods to `DaemonBridge` and expose as explicit service functions. Tie cache clearing to conversation lifecycle or offer as a manual action.
3. **Implement reloadConversationsAndRequests()** — add to `DaemonBridge` and call on manual refresh or network reconnect.
4. **Implement daemon-side search UI** — wire `searchConversation()` callback results to a new search result screen or bottom sheet. Show progress indicator during daemon search.

### Phase 3: UX Improvements
1. **Add file attachment to reply UI** — in `ChatScreen`, add a "Attach file" button in the reply banner. Allow sending file + reply text together via `sendFile(replyTo=)`.
2. **Fix message history scroll jump** — anchor scroll position to the first-loaded message or use `rememberLazyListState().requestScrollToItem()` with proper index math.
3. **Add loading indicator for history load** — show a progress bar at the top of the chat while `loadMore()` is in-flight.
4. **Add ban member action** — extend `DaemonBridge.removeConversationMember()` to accept an optional `ban: Boolean` parameter. Add a "ban" option to the member context menu.
5. **Sync profiles from vCard** — parse `onProfileReceived()` vCard and update the contact's profile in the conversation. Refresh contact avatar/name.

## Effort Estimate

| Phase | Effort | Dependencies |
|-------|--------|--------------|
| Phase 1 | 3-4 days | None (fixes only) |
| Phase 2 | 5-6 days | Phase 1; daemon API verification |
| Phase 3 | 4-5 days | Phases 1–2; UI design review |
| **Total** | **12-15 days** | — |

**Blocking risks:**
- Daemon does not expose `setConversationMember()` for role changes — may require fallback via `updateConversationInfo()` or daemon feature request.
- vCard parsing library dependency — ensure `VCardUtils` is available on all platforms before adding profile sync.
