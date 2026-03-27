# Testing Implementation Plan for jami-kmp

## Context

jami-kmp started with 32 existing tests (19 model, 8 service, 5 utility) but **zero ViewModel tests** and no integration tests. The primary blocker was `expect class DaemonBridge()` — an expect class cannot be subclassed or mocked in commonTest. All 13 ViewModels also hardcoded their `CoroutineScope`, making them untestable with `runTest`. This plan introduced testability without changing runtime behavior.

## Phase 1: Extract DaemonBridgeApi Interface -- COMPLETE

- Extracted `DaemonBridgeApi` interface with 124 methods from `expect class DaemonBridge`
- Made `DaemonBridge` implement `DaemonBridgeApi` on all platforms
- Changed all service constructors to accept `DaemonBridgeApi` instead of `DaemonBridge`
- Created `StubDaemonBridge` in commonMain with controllable fields
- Fixed `JAMI_DATADIR` environment variable issue

## Phase 2: Make ViewModels Testable (Scope Injection) -- COMPLETE

- Added optional `scope: CoroutineScope` parameter to all 13 ViewModels
- Default value preserves backward compatibility (`CoroutineScope(SupervisorJob() + Dispatchers.Default)`)
- No Koin module changes needed

## Phase 3: ViewModel Tests -- COMPLETE

Created test fixture and 13 test files in `shared/src/commonTest/kotlin/net/jami/viewmodel/`.

### Test Fixture
- `TestFixtures.kt` — Factory functions: `makeAccountService`, `makeCallService`, `makeContactService`, `makeConversationFacade`, `makeSettingsRepository`, `makeTestServiceStack`, `prepareAccountInService`, `disposableScope()`, `viewModelScope()`

### ViewModel Test Files (13 files, ~92 tests)

| Test File | Tests | Key Scenarios |
|-----------|-------|---------------|
| `AboutViewModelTest.kt` | 5 | Initial state values, onCleared |
| `AppViewModelTest.kt` | 6 | Loading -> NoAccounts, Loading -> HasAccounts |
| `ProfileSetupViewModelTest.kt` | 8 | setDisplayName, setAvatarPath, saveProfile |
| `AppSettingsViewModelTest.kt` | 10 | All toggle methods update state |
| `ImportAccountViewModelTest.kt` | 10 | importAccount, registration/migration events |
| `AccountCreationViewModelTest.kt` | 11 | create account, name registration events |
| `ContactsViewModelTest.kt` | 7 | loadContacts, search filter |
| `ContactDetailsViewModelTest.kt` | 6 | loadContact, block/remove no-ops |
| `ConversationsViewModelTest.kt` | 9 | loadConversations, search, refresh |
| `AccountSettingsViewModelTest.kt` | 8 | loadAccount, knownDevicesChanged event |
| `CallViewModelTest.kt` | 11 | toggleMute/Video/Speaker, call state |
| `NewConversationViewModelTest.kt` | 9 | search, select/remove contact, createConversation |
| `ChatViewModelTest.kt` | 13 | updateInput, sendMessage, search |

### Key Patterns
- Services use TestScope (`this`) for `advanceUntilIdle()` integration
- ViewModels use `viewModelScope()` — inherits test scheduler but has independent Job (avoids `UncompletedCoroutinesError` from infinite SharedFlow collectors)
- `onClearedDoesNotThrow` tests use `disposableScope()` — independent scope that can be cancelled without affecting TestScope

## Phase 4: Service Integration Tests -- COMPLETE

Created 5 integration test files in `shared/src/commonTest/kotlin/net/jami/services/` (38 tests):

| Test File | Tests | Key Scenarios |
|-----------|-------|---------------|
| `AccountServiceIntegrationTest.kt` | 10 | loadAccounts populates StateFlow, createJamiAccount, removeAccount, event emission |
| `CallServiceIntegrationTest.kt` | 8 | placeCall, onCallStateChanged, call state transitions, onIncomingCall |
| `ContactServiceIntegrationTest.kt` | 6 | loadContacts from stub, cache operations, add/remove contact |
| `ConversationFacadeIntegrationTest.kt` | 6 | startConversation, conversation list, preferences |
| `SettingsRepositoryIntegrationTest.kt` | 8 | theme/privacy/notification settings, mute/unmute conversation |

## Phase 5: Strengthen Existing Tests -- COMPLETE

Added 12 edge-case tests to existing test files:

| File | Added | Focus |
|------|-------|-------|
| `AccountServiceTest.kt` | +3 | Contact event edge cases, empty messages |
| `CallTest.kt` | +3 | State transitions (SEARCHING->CURRENT, CURRENT->HOLD, CURRENT->OVER) |
| `ConversationTest.kt` | +3 | Mode from int, multi-contact management, syncing mode |
| `ConversationFacadeTest.kt` | +3 | Search result with query, ConversationList defaults |

## Phase 6: Final Verification -- COMPLETE

### Summary

| Phase | Files Created | Files Modified | Tests Added |
|-------|-------------|----------------|-------------|
| 1 — DaemonBridgeApi extraction | 0 | 15+ services/DI | 0 |
| 2 — Scope injection | 0 | 13 ViewModels | 0 |
| 3 — ViewModel tests | 14 (13 tests + 1 fixture) | 0 | ~92 |
| 4 — Integration tests | 5 | 0 | 38 |
| 5 — Strengthen existing | 0 | 4 test files | 12 |
| 6 — Verify + docs | 0 | 2 doc files | 0 |
| **Total** | **19** | **32+** | **~142** |

### Total Test Count
- **Before:** 32 test classes
- **After:** 51 test classes (~174 total tests)

### Verification Commands
```bash
# ViewModel tests
./gradlew :shared:desktopTest --tests "net.jami.viewmodel.*"

# Integration tests
./gradlew :shared:desktopTest --tests "net.jami.services.*IntegrationTest"

# Full test suite
./gradlew :shared:desktopTest

# Platform builds
./gradlew :shared:compileKotlinDesktop :shared:compileDebugKotlinAndroid
```

## What We Did NOT Do

- **No mocking library** — followed existing manual stub pattern
- **No UI/Compose tests** — low ROI, high complexity
- **No platform-specific tests** — focused on commonTest for maximum coverage
- **No test coverage tooling** — premature at this stage
- **No runtime behavior changes** — all ViewModel scope defaults preserved
