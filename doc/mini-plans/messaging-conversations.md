# Mini-Plan: Messaging & Swarm Conversation Gaps

_Audited 2026-09-25. Scope: text/file messaging, swarm conversation management, moderation._

## Summary

jami-kmp implements most core messaging and conversation features (message send/receive/edit, file transfers, member add/remove, conversation metadata). However, several daemon capabilities exist but are not exposed or called in the KMP codebase: conversation history search via daemon, message/conversation count queries, conversation cache management, and member role changes via daemon. One correctness bug found in `ConversationFacade.setConversationPreferences()` which claims it will send to daemon but never does. File message replies lack UI affordance to attach/quote files. Lazy message history loading on scroll shows signs of race conditions and incomplete interaction parsing.

## Findings

### F1: ConversationFacade.setConversationPreferences() silently fails to call daemon — bug
**Evidence:** `ConversationFacade.kt:259-267`
**Impact:** Conversation preferences set via the facade (e.g., mute settings, notification prefs) are never persisted to the daemon. They only update local storage. If the app is killed and restarted, user preferences are lost. Correct path is `AccountService.setConversationPreferences()` which does call the daemon.
**Daemon support:** `setConversationPreferences(accountId, conversationId, prefs)` — `conversation_interface.h:72-74`; signal `ConversationPreferencesUpdated` exists to notify of changes — `conversation_interface.h:234-240`

### F2: No daemon-side conversation history search — capability gap
**Evidence:** `DaemonBridge.kt:169` defines `searchConversation()` and it is called in `AccountService.kt` (line ~1250), but no UI screen or viewmodel triggers it. The search result is handled via `onMessagesFound()` callback but never displayed.
**Impact:** Users cannot search within a conversation history on the daemon side. Client-side filtering in memory is currently the only option, which fails for large histories.
**Daemon support:** `searchConversation(accountId, conversationId, author, lastId, regexSearch, type, after, before, maxResult, flag)` — `conversation_interface.h:108-117`; signal `MessagesFound` — `conversation_interface.h:130-137`

### F3: Message history history loading race condition — correctness issue
**Evidence:** `ChatViewModel.kt:849-890` (`loadMore()` and `loadMessagesFromHistory()`) and `ConversationFacade.kt:894-912` — history is loaded asynchronously via `loadConversation()` daemon call, but there is no guard against overlapping loads. If user scrolls to load more while a previous load is in-flight, the `onSwarmLoaded` callback may insert messages out of order. The `messages` StateFlow is updated via `appendMessage()` without checking if the daemon load has completed.
**Impact:** Chat history can appear out of order or with duplicates if the user rapidly scrolls. No visible loading indicator during history fetch.
**Daemon support:** `loadConversation(accountId, conversationId, fromMessage, n)` returns a task ID; `loadSwarmUntil()` for date-range queries — `conversation_interface.h:94-101`

### F4: File transfer replies lack file picker on reply — UX gap
**Evidence:** `ChatViewModel.kt:469-477` (`startReply()`) captures `replyToId` but does not offer a "quote file" or "include file with reply" UI. `ChatScreen.kt` message input shows reply banner but no file attachment option while replying.
**Impact:** User can only reply with text to a file message; cannot directly re-share or attach a file in the same interaction. User must cancel reply, send the file separately, then try to remember to reply to the original message.
**Daemon support:** `sendFile()` accepts `replyTo` parameter — `datatransfer_interface.h:106-110`; files can be nested in reply threads via the daemon

### F5: No member role promotion/demotion in UI or API — capability gap
**Evidence:** `Conversation.kt` tracks `roles: MutableMap<String, MemberRole>` (with ADMIN, MEMBER, INVITED, LEFT, BLOCKED states), and `ContactDetailsViewModel.kt:22` exposes `memberRoles` to the UI, but there is no function in `DaemonBridge`, `AccountService`, or `ConversationFacade` to change a member's role.
**Impact:** Group admins cannot promote/demote members via the app. The feature is partially exposed (roles are fetched and displayed) but non-functional. No screens (e.g., `ConversationDetailsScreen`) offer a "promote to admin" / "demote" action.
**Daemon support:** No daemon function found in `conversation_interface.h` for role changes; role modifications may be done through `updateConversationInfo()` — `conversation_interface.h:67-69`

### F6: Conversation preferences not synced on load — data sync issue
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

### F10: Lazy-loaded message history does not preserve scroll position — UX issue
**Evidence:** `ChatViewModel.kt:849-890` appends old messages but does not adjust scroll offset. The list is rebuilt with new messages prepended, which causes the UI to jump. Android `LazyColumn` and iOS equivalents need explicit scroll anchor management.
**Impact:** When user scrolls up to load history, the chat jumps and loses context. User sees the message list shift instead of scrolling into history.
**Daemon support:** Not a daemon issue; architectural pattern issue in message ordering

### F11: No handling of "initial" invitation messages in group conversations — silent no-op
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

## Proposed Plan

### Phase 1: Critical Correctness Fixes
1. **Fix ConversationFacade.setConversationPreferences()** — route through `daemonBridge.setConversationPreferences()` or remove the facade wrapper and call `accountService` directly. Add test to verify daemon is called.
2. **Add load guard in ChatViewModel.loadMore()** — use a Mutex or completion flag to prevent overlapping history loads. Queue new loads if one is in-flight.
3. **Load conversation preferences on init** — in `loadConversation()` and in the conversation refresh flow, fetch `getConversationPreferences()` and merge into the `Conversation` model.
4. **Fix "initial" event visibility in groups** — render "initial" events in all conversation modes as a system message: "{User} was added to the conversation".

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
