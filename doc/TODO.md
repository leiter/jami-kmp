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

- [ ] **iOS/macOS conference hold/unhold/resume/setActiveParticipant** — `DaemonBridge.ios.kt:231-249` and `DaemonBridge.macos.kt:223-241` stub all four methods. Root cause: `JamiBridgeWrapper.h` does not expose these methods. Fix: add `holdConference`, `unholdConference`, `resumeConference`, `setActiveParticipant` to `JamiBridgeWrapper.h` + `JamiBridgeWrapper.mm`, then wire the Kotlin stubs.

## Migration

- [ ] **Account migration dialog** — `Account.needsMigration` flag and `AppState.HasAccounts(needsMigration)` are detected and passed through to `MainNavigation`, but the dialog overlay is a TODO at `JamiNavigation.kt:168`. Missing: `AccountService.migrateAccount()` method (triggers daemon via `setAccountDetails` with `ARCHIVE_PASSWORD`), a `MigrationDialog` composable (password input + migrate/delete actions), and wiring the TODO. Reference: `AccountMigrationFragment.kt` in jami-android-client.

## QR Code

- [ ] **QR camera scanning** — `QrScanScreen.kt` is an explicit stub with manual text-paste only (`Camera-based scanning is a future platform-specific effort`). Need a `QrCodeScannerView` expect/actual composable: Android (CameraX `ImageAnalysis` + ZXing `MultiFormatReader`), iOS (`UIKitView` wrapping `AVCaptureSession` + `AVMetadataObjectTypeQRCode`), Desktop/macOS/JS stubs. Replace the text input in `QrScanScreen` with the camera view; keep manual fallback toggle.

## Chat

- [ ] **Audio recording in chat** — `ChatScreen.kt:1051` has a TODO for "Record audio" menu item. Need: `AudioRecorderService` expect/actual class (Android: `MediaRecorder`; iOS: `AVAudioRecorder`; others: no-op); recording state in `ChatViewModel` (`isRecordingAudio`, `startAudioRecording`, `stopAudioRecording`, `cancelAudioRecording`); recording indicator UI in `ChatScreen`; pipe completed file to existing `sendFile` flow.
- [ ] **Video recording in chat** — `ChatScreen.kt:1066` has a TODO for "Record video". Deferred: requires Camera2/AVFoundation capture session. Currently shows placeholder snackbar "Video recording coming soon".
- [ ] **Chat extensions / plugins** — `ChatScreen.kt:1111` has a TODO for "Chat extensions". Jami plugin system not yet ported to KMP. Currently shows placeholder snackbar "Plugins not yet supported".

## Location Sharing

- [ ] **OsmMapView — iOS** — `OsmMapView.ios.kt` shows placeholder text. Implement using `MapKit` (`MKMapView`) via `UIKitView` + `CLLocationManager` for device location updates. `NSLocationWhenInUseUsageDescription` should already be in `Info.plist`.
- [ ] **OsmMapView — macOS** — `OsmMapView.macos.kt` shows placeholder text. Same MapKit approach as iOS but using `NSViewFactory` instead of `UIKitView`.
- [ ] **OsmMapView — Desktop** — `OsmMapView.desktop.kt` shows placeholder text. Acceptable stub; no viable JVM map library in scope.

## Biometric Auth

- [ ] **BiometricService — macOS** — `BiometricService.macos.kt` returns `UNAVAILABLE`/`false` for all methods. `LocalAuthentication` and `Security` frameworks are available on macOS 10.15+ with identical API to the working iOS implementation (`BiometricService.ios.kt`). Implementation is a near-copy: same `LAContext`, `canEvaluatePolicy`, `evaluatePolicy`, Keychain APIs.

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
