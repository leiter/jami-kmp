# Gap Analysis: jami-android-client vs jami-kmp (Medium Opportunities)

**Date:** 2026-03-19
**Context:** After completing the low-hanging fruit plan (Groups A-G), this report identifies the next tier of medium-effort opportunities.

---

## Tier 1: Highest Impact / Effort Ratio

| # | Gap | Effort | Impact | Area |
|---|-----|--------|--------|------|
| **1** | Data Transfer Completion | 1-1.5h | High | ConversationFacade + AccountService |
| **2** | Conference/Group Call Ops | 1-1.5h | High | CallService |
| **3** | Conversation Search & History Pagination | 1.5-2h | Medium-High | ConversationFacade |

**1. Data Transfer Completion** -- `cancelDataTransfer()` is still stubbed in ConversationFacade (line 377). Missing: `acceptFileTransfer()` full wiring, transfer progress tracking via Flow, retry/resume logic, and mapping of all 5 transfer event codes from daemon. Android AccountService has ~100 lines of transfer ops (lines 1557-1650).

**2. Conference/Group Call Ops** -- CallService has TODO stubs for `holdConference()`/`unholdConference()`. Missing from Android's CallPresenter (787 lines): `setConfMaximizedParticipant()`, `setConferenceLayout()`, conference participant info updates, moderator detection, remote recording state. These require ~5 new DaemonBridge methods.

**3. Conversation Search & History Pagination** -- `onMessagesFound` callback handler is a TODO (ConversationFacade line 912). `loadMore()` doesn't call daemon. Missing: `searchConversation()`, `loadUntil()`, pagination state in Conversation model, and swarm message parent/child linearization.

---

## Tier 2: Important Functional Gaps

| # | Gap | Effort | Impact | Area |
|---|-----|--------|--------|------|
| **4** | Active Calls Tracking | 30m-1h | Medium | ConversationFacade |
| **5** | Profile/VCard Loading | 45m-1.5h | Medium | ContactService |
| **6** | Presence & Subscriptions | 45m-1.5h | Medium | ContactService |
| **7** | Name Registration State Machine | 1-1.5h | Medium | AccountService + ViewModel |

**4. Active Calls Tracking** -- `onActiveCallsChanged` is a TODO (ConversationFacade line 967). Need to add active call state to Conversation model and wire the callback so the UI can show "ongoing call" indicators.

**5. Profile/VCard Loading** -- ContactService has TODOs at lines ~203, 220 for VCard parsing. `accountProfileReceived()` callback needs wiring. Peer profile images aren't extracted or cached. Android client has full VCard-to-profile pipeline.

**6. Presence & Subscriptions** -- `subscribeBuddy()` exists in AccountService but presence change events aren't fully streamed to the Contact model. Missing: presence state Flow on Contact, buddy event callbacks, and availability indicator updates.

**7. Name Registration State Machine** -- `registerName()` and `lookupName()` return booleans but don't track in-progress state. Android client has full state machine (in-progress -> success/failure) with `nameRegistrationEnded()` callback wired to observable state. Needed for the account creation wizard to block on name availability.

---

## Tier 3: ViewModel/Presenter Parity

| # | Gap | Effort | Impact | Area |
|---|-----|--------|--------|------|
| **8** | Account Wizard Presenters | 2-3h | High | ViewModels |
| **9** | Call Presenter Logic | 2.5h+ | High | CallViewModel |
| **10** | Certificate & Device Management | 1-2h | Medium | AccountService |

**8. Account Wizard Presenters** -- Android has 20 files in `account/` (~2000 LOC). KMP has only basic `AccountCreationViewModel`. Missing: SIP creation flow, device linking/import, profile creation step, account summary/validation, and security/TLS configuration.

**9. Call Presenter Logic** -- KMP CallViewModel is ~200 lines vs Android's CallPresenter at 787 lines. Missing: permission handling integration, hardware state sync (speaker/mute/video toggle), conference participant management, call duration tracking, audio focus, video surface management.

**10. Certificate & Device Management** -- `validateCertificatePath()`, `validateCertificate()`, `getCertificateDetails()`, `migrateAccount()`, TLS method enumeration -- all present in Android AccountService (lines 725-776) but absent from KMP.

---

## Remaining Stubs in KMP Code

| Pattern | Count | Key Locations |
|---------|-------|---------------|
| `// TODO` | ~8 | ConversationFacade (search, active calls), ContactService (VCard) |
| `// Cancel via daemon` | 1 | ConversationFacade (data transfer) |
| `// Load more from daemon` | 1 | ConversationFacade line 515 |
| Empty method bodies | ~5 | `clearHistoryForAccount()`, `clearAllHistory()` loops |

---

## Recommended Implementation Order

1. **Data Transfer Completion** (1-1.5h) -- fixes file ops, immediate UX improvement
2. **Active Calls Tracking** (30m-1h) -- small change, enables call indicators
3. **Conference Operations** (1-1.5h) -- enables group calls, a core feature
4. **Profile/VCard Loading** (45m-1.5h) -- makes contacts show real names/avatars
5. **Search & History Pagination** (1.5-2h) -- unlocks conversation search
6. **Name Registration State Machine** (1-1.5h) -- needed for account setup wizard
7. **Presence & Subscriptions** (45m-1.5h) -- online/offline indicators
8. **Account Wizard + Call Presenter** (4-5h combined) -- larger effort, deferred

Items 1-4 alone (~4 hours) would address roughly 40% of the remaining functional gaps.

---

## Implementation Status

- [x] 1. Data Transfer Completion (2026-03-19)
- [x] 2. Active Calls Tracking (2026-03-19)
- [x] 3. Conference/Group Call Ops (2026-03-19) -- DaemonBridge + CallService wired
- [x] 4. Profile/VCard Loading (2026-03-19) -- VCard parsing + contact profile caching
- [ ] 5. Search & History Pagination
- [ ] 6. Name Registration State Machine
- [ ] 7. Presence & Subscriptions
- [ ] 8. Account Wizard Presenters
- [ ] 9. Call Presenter Logic
- [ ] 10. Certificate & Device Management
