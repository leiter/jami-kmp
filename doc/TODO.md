# TODO

## Calling

- [x] **Screen sharing not transmitted** — Fixed: `CameraService.startScreenSharing()` now wires `VirtualDisplay` output through a `Surface` obtained from `JamiService.addVideoDevice`; `HardwareService.startCapture("desktop")` is called by the daemon after the media renegotiation completes.

- [x] **Video calls not working** — Fixed: `HardwareService.addPreviewVideoSurface()` now registers the `TextureView` as `mCameraPreviewSurface` and calls `cameraService.openCamera()`; `startCameraPreview()` opens and starts the camera session; `hasCamera()` / `cameraCount()` delegate to `CameraService`.

- [x] **Picture-in-Picture not working** — Fixed: added `android:supportsPictureInPicture="true"` and the required `android:configChanges` to `MainActivity` in `AndroidManifest.xml`; `enterPipMode()` is now guarded to only fire when a call is active.

## UI / Live Updates

- [x] **Live cross-device own-profile avatar update** — When the user changes their avatar on another device, the change is not reflected live in the HomeScreen search bar avatar (only after app restart).
- [x] **VCard Bitmap Scaling and Caching** — Implemented `VCardService` (Koin `single`) with `loadLocalAvatar`/`loadPeerAvatar`; scales to 512px JPEG q88 via `scaleImageBytes` expect/actual, caches to disk under `getCachePath()` with mtime freshness check, plus an in-memory map. `ConversationsViewModel` and `ContactService` now route avatar loads through `VCardService`; `ContactService.onProfileReceived` invalidates the cache on profile update.
- [x] **Enhanced Call Handling** — Ported media-change answering: `CallService.mediaChangeRequested` mutes video when the call has no active video, mirrors audio-mute state, then calls `daemonBridge.answerMediaChangeRequest`. Added `answerMediaChangeRequest` to `DaemonBridgeApi` (Android: JNI via `VectMap`/`StringMap`; others: no-op stub). Conference participant reconciliation ported to `onConferenceCreated` and `onConferenceChanged`: queries `getParticipantList`/`getConferenceDetails`, populates `Conference.participants`, and demotes back to a plain call when one participant remains.

## Conference & Calling (Cross-Platform)

- [x] **iOS/macOS conference hold/unhold/resume/setActiveParticipant** — Added `holdConference`, `unholdConference`, `resumeConference`, `setActiveParticipant` to `JamiBridgeWrapper.h` and `JamiBridgeWrapper.mm` (delegating to `libjami::holdConference`, `libjami::resumeConference`, `libjami::setActiveParticipant`); wired in `DaemonBridge.ios.kt` and `DaemonBridge.macos.kt`.

## Migration

- [x] **Account migration dialog** — Added `AccountService.migrateAccount()` (sets `ARCHIVE_PASSWORD` via `setAccountDetails`; result arrives via `AccountEvent.MigrationEnded`). New `MigrationDialog.kt` composable: password input with 6-char guard, Migrate/Skip actions, observes migration result for success/error feedback. Wired in `JamiNavigation.kt`: overlay shown when `needsMigration && !migrationDismissed`, dismissed per session.

## QR Code

- [x] **QR camera scanning** — New `QrCodeScannerView` expect/actual: Android (CameraX `ImageAnalysis` + ZXing `MultiFormatReader`, requests `CAMERA` permission), iOS (`UIKitView` wrapping `AVCaptureSession` + `AVMetadataObjectTypeQRCode`), Desktop/macOS/JS stubs. `QrScanScreen` shows camera view by default; Edit button in top bar toggles manual text-paste fallback; unrecognised scan falls back to manual mode.

## Chat

- [x] **Audio recording in chat** — New `AudioRecorderService` expect/actual (Android: `MediaRecorder` MPEG4/AAC; iOS: `AVAudioRecorder`; others: no-op stubs). `ChatViewModel`: `isRecordingAudio` `StateFlow` + `startAudioRecording` / `stopAudioRecording` / `cancelAudioRecording`; completed recording piped to `sendFile`. `ChatScreen`: recording indicator row with mic icon, Cancel and Send buttons shown while recording.
- [ ] **Video recording in chat** — Deferred: requires Camera2/AVFoundation capture session. Menu item currently shows snackbar "Video recording coming soon".
- [ ] **Chat extensions / plugins** — Jami plugin system not yet ported to KMP. Menu item currently shows snackbar "Plugins not yet supported".

## Location Sharing

- [x] **OsmMapView — iOS** — Full `MapKit` implementation: `UIKitView` wrapping `MKMapView`, `CLLocationManager` for live location updates, `MKPointAnnotation` per contact marker, `setRegion` when `centerOnMyLocation`.
- [ ] **OsmMapView — macOS** — Stub retained: no stable `NSView` embedding API in Compose Multiplatform for macOS targets. Shows location coordinates as text.
- [ ] **OsmMapView — Desktop** — Stub retained; no viable JVM map library in scope.

## Biometric Auth

- [x] **BiometricService — macOS** — Full implementation ported from iOS: same `LAContext`, `canEvaluatePolicy`, `evaluatePolicy`, Security framework Keychain APIs (available macOS 10.15+). On Macs without biometric hardware `checkAvailability()` correctly returns `NO_HARDWARE`.

## Contacts

- [x] **BlockedContactsScreen always empty** — Added `blockedContacts: StateFlow<List<ContactItem>>` to `ContactsViewModel` (filters `Contact.Status.BLOCKED`); `BlockedContactsScreen` now collects it; `unblockContact()` wired to `contactService.addContact`.

## Sharing

- [x] **ShareUtils — iOS** — Implemented with `UIActivityViewController(activityItems: [body])` presented from `UIApplication.sharedApplication.keyWindow?.rootViewController`.
- [x] **ShareUtils — macOS** — Implemented with `NSSharingService.sharingServicesForItems([body]).first()?.performWithItems([body])`.

## Group Conversations

- [x] **Leave conversation + add/remove group members** — Added `addConversationMember()` / `removeConversationMember()` to `ConversationFacade`; added `leaveConversation()`, `addMember()`, `removeMember()` to `ContactDetailsViewModel`; `ConversationDetailsScreen` shows a `GroupMemberSection` composable when `state.isSwarm` with member list, admin-only Add/Remove buttons (with confirmation dialogs), and Leave group button.

## Localization (Hardcoded Strings)

- [x] **Key hardcoded strings fixed** — `PendingRequestsScreen` title uses `invitation_card_title`; `JamiMessageInput` content descriptions use `Res.string.content_desc_*`; `AccountCreationViewModel` errors converted to `UsernameCheckError`/`AccountCreationError` enums translated at screen level. Remaining hardcoded strings (conversation type labels in `ContactDetailsViewModel`, date labels "Today"/"Yesterday" in `ChatViewModel`, biometric prompts in `AccountSettingsViewModel`) require passing values from the Composable layer and are deferred.

## Accounts

- [x] **SIP account creation** — `CreateAccountScreen` now has a Jami/SIP tab toggle; SIP tab shows server, username, password, port (optional), and display name fields; `AccountCreationViewModel.createSipAccount()` builds `ACCOUNT_TYPE=SIP` details map and calls `accountService.addAccount()`.

## Android Service

- [x] **Foreground service notification not showing on Pixel 2** — Root cause: channel `"jami_daemon_service"` was originally created at `IMPORTANCE_MIN` and Android channels are immutable once created. Fixed by bumping to `"jami_daemon_service_v2"` (forces fresh creation at `IMPORTANCE_LOW`). Also replaced system `android.R.drawable.*` icons with the app's own `ic_jami_24` across all notification types.

## Known Gaps (Lower Priority)

- [ ] **Desktop DaemonBridge** — All 100+ methods are no-ops. Architectural blocker: SWIG-generated JNI classes conflict with KMP's Android plugin, requiring a separate JVM module. Deprioritised.
- [ ] **Web/JS platform** — Entire daemon bridge is REST stubs with `// TODO: Call REST API`. Explicitly experimental per CLAUDE.md; candidate for removal if REST bridge server is not developed.
- [ ] **Desktop/Web VideoSurface** — `VideoSurface.desktop.kt` and `.js.kt` show placeholder text. No viable path without daemon bridge working first.
- [ ] **ShareUtils — iOS/macOS** — `ShareUtils.ios.kt` and `.macos.kt` are `// TODO` stubs (`UIActivityViewController` / `NSSharingService`). Low impact.
- [ ] **QRCodeUtils — Web/JS** — `QRCodeUtils.js.kt` returns null (no QR generation on Web).

## Testing

- [ ] Run ViewModel tests: `./gradlew :shared:desktopTest --tests "net.jami.viewmodel.*"`
- [ ] Run integration tests: `./gradlew :shared:desktopTest --tests "net.jami.services.*IntegrationTest"`
- [ ] Run full test suite: `./gradlew :shared:desktopTest`
- [ ] Verify platform builds: `./gradlew :shared:compileKotlinDesktop :shared:compileDebugKotlinAndroid`
- [ ] Fix any failing tests from the `viewModelScope()` migration (replaced `backgroundScope` to fix `UncompletedCoroutinesError`)
