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

## Call Recording

- [ ] **Call recording** — `toggleRecording` / `setRecordPath` / `getRecordPath` exist in `callmanager_interface.h` and are fully functional in the reference client. Expose them in `DaemonBridge.kt`, wire into `CallService` and `CallViewModel`, add a record button to `CallScreen`.

## Push Notifications

- [ ] **FCM push notifications (Android)** — Firebase integration missing. Calls and messages only arrive while the daemon is running in the foreground.
- [ ] **APNs push notifications (iOS)** — Same gap on iOS.

## Deep Links & Intents

- [ ] **URI scheme intent filters** — Reference manifest handles `ring://`, `jami://`, `sip://`, `tel://`. KMP `AndroidManifest.xml` has none of these; tapping a Jami link from outside the app does nothing.
- [ ] **Share-to-Jami (ACTION_SEND)** — Reference has a `ShareActivity` with `ACTION_SEND` / `ACTION_SEND_MULTIPLE` intent filter. KMP manifest has no `ACTION_SEND` filter — "Share via Jami" does not appear in the Android share sheet.

## Chat — Missing Features

- [x] **Message reactions UI** — `ReactionGroup` data class aggregates emoji+count+isMine. `ChatViewModel` handles `ReactionAdded/ReactionRemoved` events via `rebuildMessageReactions`; `interactionToMessageItem` snapshots reactions via `groupReactions`. `ConversationFacade.onReactionAdded/Removed` now updates the `Conversation` model before emitting events; `swarmMessageToInteraction` loads history reactions from `SwarmMessage.reactions`. `ChatBubble` shows reaction pills below bubbles and an emoji quick-picker (👍 ❤️ 😂 😮 😢 👏) in the long-press menu.
- [ ] **Read receipt display (checkmarks)** — `SEND_READ_RECEIPT` is stored and enforced. The reference client shows sent/delivered/read checkmarks per message bubble. `ChatScreen.kt` has no status indicator rendering.
- [ ] **@Mentions in group chat** — Reference client parses `@username` in message text and highlights them. No mention system in KMP's chat UI or viewmodel.
- [ ] **Full-screen image viewer** — Reference uses `MediaViewerFragment` with pinch-zoom. Tapping an image in KMP chat does nothing; no image viewer composable exists.
- [ ] **Video message playback in chat** — Reference plays received video files inline. No video player composable in `ChatScreen.kt`.
- [ ] **Retry failed file transfer** — Reference shows a retry button on failed transfers. Not present in KMP.
- [ ] **Message long-press: share file** — Copy-to-clipboard is done (`clipboardManager.setText` in `ChatBubble` long-press menu). Share-file action (for received file transfers) is still missing.

## Home Screen — Missing Features

- [ ] **Conversation filtering tabs (All / Groups / Contacts)** — Reference `HomeFragment` has tab filtering. KMP `HomeScreen` shows a single flat list.
- [ ] **Long-press context menu on conversation rows** — Reference shows mute / pin / block / delete. KMP `HomeScreen` has no long-press handler.
- [ ] **Swipe-to-archive / swipe-to-delete** — Reference `SmartListAdapter` supports swipe gestures. Not implemented in KMP.

## Calls — Missing Features

- [ ] **Proximity sensor (screen off during calls)** — Setting `proximityEnabled` exists in `SettingsModels.kt` but `PROXIMITY_SCREEN_OFF_WAKE_LOCK` is never acquired in the Android `HardwareService`.
- [ ] **Haptic feedback on call answer/decline** — No `Vibrator` / `HapticFeedbackConstants` calls in the call flow.
- [ ] **Bluetooth headset routing** — `toggleSpeaker()` backend exists; full 3-way routing (earpiece / speaker / BT) may not be fully wired through `AudioManager`.

## Notifications — Missing Features

- [x] **Inline reply handler** — `NotificationActionReceiver.handleReply` reads `RemoteInput.getResultsFromIntent` → `KEY_REPLY_TEXT`, calls `daemonBridge.sendMessage(accountId, conversationId, text, "", 0)`, then cancels the notification. Mark-read action calls `setConversationPreferences` + cancels notification.

## Location Sharing — Missing Features

- [x] **Location share duration selector** — `LocationSharingScreen` has 10 min / 1 hour `FilterChip` duration selector wired to `LocationSharingViewModel.selectDuration()`.
- [x] **Multi-peer location on same map** — `ContactLocationsInfo` map in `LocationSharingState` tracks all peers; each is rendered as a separate `ContactMarker` / `MKPointAnnotation`.
- [x] **"Center on me" button** — `LocationSharingScreen` has a `FloatingActionButton` (MyLocation icon) wired to `viewModel.centerOnMyLocation()`; Android OsmMapView now has a dedicated `LaunchedEffect(centerOnMyLocation)` to re-center even when the location has not changed.
- [x] **Location accuracy radius circle** — Android: `Polygon` overlay drawn around the user marker scaled to `GeoLocation.accuracy` meters (flat-earth approximation, accurate to <1% for radii ≤20 km). iOS: `MKMapView.showsUserLocation = true` renders the native blue-dot accuracy circle automatically via MapKit.

## App Settings — Missing Features

- [ ] **Battery optimisation exemption prompt** — Reference prompts the user to whitelist the app. No `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` intent in KMP.
- [ ] **Language selector** — Reference has a locale preference. Not found in `AppSettingsScreen`.
- [ ] **Display name in notifications setting** — Reference lets users hide contact names from notifications for privacy. Not in `AppSettingsScreen`.
- [ ] **Log export / debug mode** — Reference has `LogsActivity`. No log viewer integrated into the KMP app.
- [ ] **App shortcuts** — Reference `AndroidManifest.xml` declares conversation shortcuts. Not in KMP manifest.

## Known Gaps (Lower Priority)

- [ ] **Desktop DaemonBridge** — All 100+ methods are no-ops. Architectural blocker: SWIG-generated JNI classes conflict with KMP's Android plugin, requiring a separate JVM module. Deprioritised.
- [ ] **Web/JS platform** — Entire daemon bridge is REST stubs with `// TODO: Call REST API`. Explicitly experimental per CLAUDE.md; candidate for removal if REST bridge server is not developed.
- [ ] **Desktop/Web VideoSurface** — `VideoSurface.desktop.kt` and `.js.kt` show placeholder text. No viable path without daemon bridge working first.
- [x] **ShareUtils — iOS/macOS** — See completed items above (implemented with UIActivityViewController / NSSharingService).
- [ ] **QRCodeUtils — Web/JS** — `QRCodeUtils.js.kt` returns null (no QR generation on Web).
- [ ] **OsmMapView — macOS** — Stub retained: no stable `NSView` embedding API in Compose Multiplatform for macOS targets. Shows location coordinates as text.
- [ ] **OsmMapView — Desktop** — Stub retained; no viable JVM map library in scope.

## Testing

- [ ] Run ViewModel tests: `./gradlew :shared:desktopTest --tests "net.jami.viewmodel.*"`
- [ ] Run integration tests: `./gradlew :shared:desktopTest --tests "net.jami.services.*IntegrationTest"`
- [ ] Run full test suite: `./gradlew :shared:desktopTest`
- [ ] Verify platform builds: `./gradlew :shared:compileKotlinDesktop :shared:compileDebugKotlinAndroid`
- [ ] Fix any failing tests from the `viewModelScope()` migration (replaced `backgroundScope` to fix `UncompletedCoroutinesError`)
