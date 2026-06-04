# TODO

## Calling

- [x] **Screen sharing not transmitted** — Fixed: `CameraService.startScreenSharing()` now wires `VirtualDisplay` output through a `Surface` obtained from `JamiService.addVideoDevice`; `HardwareService.startCapture("desktop")` is called by the daemon after the media renegotiation completes.

- [x] **Video calls not working** — Fixed: `HardwareService.addPreviewVideoSurface()` now registers the `TextureView` as `mCameraPreviewSurface` and calls `cameraService.openCamera()`; `startCameraPreview()` opens and starts the camera session; `hasCamera()` / `cameraCount()` delegate to `CameraService`.

- [x] **Picture-in-Picture not working** — Fixed: added `android:supportsPictureInPicture="true"` and the required `android:configChanges` to `MainActivity` in `AndroidManifest.xml`; `enterPipMode()` is now guarded to only fire when a call is active.

## UI / Live Updates

- [x] **Live cross-device own-profile avatar update** — When the user changes their avatar on another device, the change is not reflected live in the HomeScreen search bar avatar (only after app restart).
- [x] **VCard Bitmap Scaling and Caching** — Implemented `VCardService` (Koin `single`) with `loadLocalAvatar`/`loadPeerAvatar`; scales to 512px JPEG q88 via `scaleImageBytes` expect/actual, caches to disk under `getCachePath()` with mtime freshness check, plus an in-memory map. `ConversationsViewModel` and `ContactService` now route avatar loads through `VCardService`; `ContactService.onProfileReceived` invalidates the cache on profile update.
- [x] **Enhanced Call Handling** — Ported media-change answering: `CallService.mediaChangeRequested` mutes video when the call has no active video, mirrors audio-mute state, then calls `daemonBridge.answerMediaChangeRequest`. Added `answerMediaChangeRequest` to `DaemonBridgeApi` (Android: JNI via `VectMap`/`StringMap`; others: no-op stub). Conference participant reconciliation ported to `onConferenceCreated` and `onConferenceChanged`: queries `getParticipantList`/`getConferenceDetails`, populates `Conference.participants`, and demotes back to a plain call when one participant remains.

## Testing

- [ ] Run ViewModel tests: `./gradlew :shared:desktopTest --tests "net.jami.viewmodel.*"`
- [ ] Run integration tests: `./gradlew :shared:desktopTest --tests "net.jami.services.*IntegrationTest"`
- [ ] Run full test suite: `./gradlew :shared:desktopTest`
- [ ] Verify platform builds: `./gradlew :shared:compileKotlinDesktop :shared:compileDebugKotlinAndroid`
- [ ] Fix any failing tests from the `viewModelScope()` migration (replaced `backgroundScope` to fix `UncompletedCoroutinesError`)
