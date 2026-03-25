# Testing Implementation Plan for jami-kmp

## Context

jami-kmp has 32 existing tests (19 model, 8 service, 5 utility) but **zero ViewModel tests** and no integration tests. The primary blocker is `expect class DaemonBridge()` — an expect class cannot be subclassed or mocked in commonTest. All 12 ViewModels also hardcode their `CoroutineScope`, making them untestable with `runTest`. This plan introduces testability without changing runtime behavior.

## Phase 1: Extract DaemonBridgeApi Interface (Unblocks Everything)

**Files to modify:**
- `shared/src/commonMain/kotlin/net/jami/services/DaemonBridge.kt` — extract interface
- `shared/src/commonMain/kotlin/net/jami/services/AccountService.kt` — depend on interface
- `shared/src/commonMain/kotlin/net/jami/services/ConversationFacade.kt` — depend on interface (if it takes DaemonBridge)
- `shared/src/commonMain/kotlin/net/jami/di/JamiModule.kt` — bind interface to impl

**Steps:**
1. Create `DaemonBridgeApi` interface with all 60+ methods currently on `expect class DaemonBridge`
2. Make `DaemonBridge` implement `DaemonBridgeApi`
3. Change all service constructors to accept `DaemonBridgeApi` instead of `DaemonBridge`
4. Update Koin module: `single<DaemonBridgeApi> { DaemonBridge() }`
5. Keep `FileTransferInfo` and `DaemonCallbacks` unchanged

**Pattern reference:** letsJam already does this — `JamiBridge` is an interface with `MockJamiBridge` in tests (`letsJam/shared/src/commonTest/.../MockJamiBridge.kt`, 154 lines).

## Phase 2: Create StubDaemonBridge in commonTest

**File to create:**
- `shared/src/commonTest/kotlin/net/jami/services/StubDaemonBridge.kt`

**Details:**
- Implements `DaemonBridgeApi` with no-op/empty defaults
- Follow existing stub pattern (StubHardwareService, StubNotificationService, etc.)
- Add controllable fields: `accountList`, `conversationList`, `accountDetails` maps
- Methods return these controllable values so tests can set up scenarios

## Phase 3: Make ViewModels Testable

**Files to modify (all 12 ViewModels):**
- Add optional `scope: CoroutineScope` constructor parameter defaulting to `CoroutineScope(SupervisorJob() + Dispatchers.Default)`
- In tests, pass `TestScope()` from kotlinx-coroutines-test (already a dependency)

**Priority order (by complexity and value):**
1. `AppViewModel` — simplest (57 LOC), validates the pattern
2. `ConversationsViewModel` — core list screen
3. `ChatViewModel` — message flow, most complex interactions
4. `AccountCreationViewModel` — multi-step flow
5. `CallViewModel` — call state machine
6. Remaining 7 ViewModels

## Phase 4: Write ViewModel Tests

**Files to create in** `shared/src/commonTest/kotlin/net/jami/ui/viewmodel/`:

### AppViewModelTest (~5 tests)
- Initial state is Loading
- Empty account list → NoAccounts
- Non-empty account list → HasAccounts
- Account needing migration → HasAccounts(needsMigration=true)
- onCleared cancels scope

### ConversationsViewModelTest (~5 tests)
- Initial state has empty conversation list
- Loads conversations from facade
- Updates on new conversation event
- Handles conversation removal
- Search/filter if applicable

### ChatViewModelTest (~8 tests)
- loadConversation sets title and messages
- sendMessage clears input and calls daemon
- updateInput updates state
- Incoming MessageReceived appends to list
- SwarmLoaded triggers history reload
- loadMore calls facade
- Empty conversation handled gracefully
- Loading state transitions

### AccountCreationViewModelTest (~5 tests)
- Initial state
- Username validation
- Password validation
- Account creation flow
- Error handling

### CallViewModelTest (~5 tests)
- Start call state
- Accept/reject incoming
- Mute/unmute
- Hold/unhold
- Hang up

**Estimated: ~30-35 ViewModel tests**

## Phase 5: Service Integration Tests

**File to create:**
- `shared/src/commonTest/kotlin/net/jami/services/AccountServiceIntegrationTest.kt`

**Using StubDaemonBridge to test:**
- Account loading populates accounts StateFlow
- Account creation calls bridge and emits update
- Account removal flow
- Account detail changes propagate

**File to create:**
- `shared/src/commonTest/kotlin/net/jami/services/ConversationFacadeIntegrationTest.kt`

**Tests:**
- Loading conversations from bridge
- Sending messages
- Receiving messages via callback → Flow emission
- Conversation history loading

**Estimated: ~15-20 integration tests**

## Phase 6: Strengthen Existing Tests

- Review 8 existing service tests for gaps
- Add edge cases to model tests where valuable
- **Estimated: ~5-10 additional tests**

## Execution Order

| Step | What | Est. Tests | Risk |
|------|------|-----------|------|
| 1 | Extract DaemonBridgeApi interface | 0 | Medium — touches many files, must not break builds |
| 2 | Create StubDaemonBridge | 0 | Low |
| 3 | Add scope param to AppViewModel | 0 | Low |
| 4 | AppViewModelTest | 5 | Low — validates the whole approach |
| 5 | Add scope param to remaining VMs | 0 | Low |
| 6 | ChatViewModelTest | 8 | Medium — complex interactions |
| 7 | ConversationsViewModelTest | 5 | Low |
| 8 | Remaining VM tests | 15 | Low |
| 9 | Service integration tests | 15-20 | Medium |
| 10 | Strengthen existing tests | 5-10 | Low |

**Total: ~50-65 new tests**

## What We're NOT Doing

- **No mocking library** — follow existing manual stub pattern
- **No UI/Compose tests** — low ROI, high complexity, no infrastructure
- **No platform-specific tests** — focus on commonTest for maximum coverage
- **No test coverage tooling** — premature at this stage

## Verification

1. After Phase 1: `./gradlew :shared:compileKotlinDesktop` (or equivalent) — all platforms still compile
2. After Phase 2-4: `./gradlew :shared:desktopTest` — new tests pass
3. After all phases: `./gradlew :shared:allTests` — full test suite green
4. Android app builds and runs: `./gradlew :android-app:assembleDebug`
