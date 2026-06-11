# Plan: Fix Three Calling TODOs

> **Status: COMPLETED** — All three phases implemented (see `doc/TODO.md`).

Three bugs to fix in order of complexity. Each phase is self-contained.

---

## Phase 1 — PiP: fix stale call-state snapshot

**Root cause.** `CallScreen.kt:216` uses `DisposableEffect(Unit)`, which runs once at composition time and captures `state` at that moment (typically `CallMode.Connecting`). `AndroidPictureInPictureManager.enterPipMode()` checks `currentCallState.callMode == OnGoing`, which is never true because `attachCallState` is never re-called as the mode transitions.

The manifest already has `android:supportsPictureInPicture="true"` (line 43). The active-call guard is already in `enterPipMode()`. Only the re-attachment is missing.

**File:** `shared/src/commonMain/kotlin/net/jami/ui/screens/CallScreen.kt` ~line 216

**Change:** Replace `DisposableEffect(Unit)` with `DisposableEffect(state.callMode)`.

```kotlin
// Before
DisposableEffect(Unit) {
    try {
        val pipManager = org.koin.core.context.GlobalContext.get().get<net.jami.services.PictureInPictureManager>()
        pipManager.attachCallState(state)
        onDispose { pipManager.detachCallState() }
    } catch (e: Exception) {
        onDispose { }
    }
}

// After
DisposableEffect(state.callMode) {
    try {
        val pipManager = org.koin.core.context.GlobalContext.get().get<net.jami.services.PictureInPictureManager>()
        pipManager.attachCallState(state)
        onDispose { pipManager.detachCallState() }
    } catch (e: Exception) {
        onDispose { }
    }
}
```

`DisposableEffect(state.callMode)` re-runs (dispose + re-attach) every time `callMode` changes, so when the call reaches `OnGoing` the PiP manager has the fresh state before `onUserLeaveHint` fires.

---

## Phase 2 — Video calls: wire camera through CameraService

**Root cause.** Six methods in `HardwareService.android.kt` are hardcoded stubs:
- `isVideoAvailable = false`
- `hasCamera() = false` / `cameraCount() = 0`
- `getCameraInfo()` and `setParameters()` are no-ops
- `addPreviewVideoSurface()` logs and returns
- `startCapture()` only handles the `SCREEN_SHARING` path; the regular camera path is missing

`CameraService` already has working implementations for all of these. The task is to wire `HardwareService` through to them, mirroring `HardwareServiceImpl` from the Android reference.

**File:** `shared/src/androidMain/kotlin/net/jami/services/expect/HardwareService.kt`

### 2a — Add fields (after existing `pendingScreenShareProjection` field)

```kotlin
private var mCameraPreviewSurface: WeakReference<TextureView> = WeakReference(null)
private var mCameraPreviewCall: WeakReference<Conference?> = WeakReference(null)
private val shouldCapture = mutableSetOf<String>()
private val pendingStartCodec = mutableSetOf<String>()
```

Add imports: `java.lang.ref.WeakReference`, `android.view.WindowManager`

### 2b — Fix property / simple delegates

```kotlin
// Replace the three hardcoded stubs:
actual val isVideoAvailable: Boolean
    get() = context.packageManager.hasSystemFeature(android.content.pm.PackageManager.FEATURE_CAMERA_ANY)
            || cameraService.hasCamera()

actual fun hasCamera(): Boolean = cameraService.hasCamera()
actual fun cameraCount(): Int = cameraService.getCameraCount()
```

### 2c — Implement `getCameraInfo`

Reference: `HardwareServiceImpl.kt:461–467` + `CameraService.getCameraInfo(camId, formats, sizes, rates, minVideoSize): Unit` (KMP signature at `CameraService.kt:194–208`).

```kotlin
actual fun getCameraInfo(
    camId: String,
    formats: MutableList<Int>,
    sizes: MutableList<Int>,
    rates: MutableList<Int>
) {
    cameraService.getCameraInfo(camId, formats, sizes, rates, android.util.Size(1280, 720))
}
```

The 1280×720 default mirrors the reference's `VIDEO_WIDTH`/`VIDEO_HEIGHT` constants for 64-bit devices.

### 2d — Implement `setParameters`

Reference: `HardwareServiceImpl.kt:469–473`. `CameraService.setParameters` signature: `(camId, format, width, height, rate, rotation)` (`CameraService.kt:150`).

```kotlin
@Suppress("DEPRECATION")
actual fun setParameters(camId: String, format: Int, width: Int, height: Int, rate: Int) {
    val rotation = (context.getSystemService(Context.WINDOW_SERVICE) as WindowManager)
        .defaultDisplay.rotation
    cameraService.setParameters(camId, format, width, height, rate, rotation)
}
```

### 2e — Implement `addPreviewVideoSurface`

Reference: `HardwareServiceImpl.kt:696–706`.

```kotlin
actual fun addPreviewVideoSurface(holder: Any, conference: Conference?) {
    synchronized(shouldCapture) {
        if (holder !is TextureView) return
        if (mCameraPreviewSurface.get() === holder) return
        mCameraPreviewSurface = WeakReference(holder)
        mCameraPreviewCall = WeakReference(conference)
        for (cam in shouldCapture) {
            openCameraPreview(cam)
        }
    }
}
```

### 2f — Add private `openCameraPreview`

Reference: `HardwareServiceImpl.kt:535–570`. Calls `cameraService.openCamera()`.

```kotlin
private fun openCameraPreview(cam: String) {
    val videoParams = cameraService.getParams(cam) ?: return
    val surface = mCameraPreviewSurface.get() ?: run {
        Log.w(TAG, "openCameraPreview: no surface for $cam")
        return
    }
    videoParams.isCapturing = true
    cameraService.openCamera(
        videoParams,
        surface,
        object : CameraListener {
            override fun onOpened() {
                Log.d(TAG, "Camera opened: $cam")
            }
            override fun onError() {
                pendingStartCodec.remove(cam)
                stopCapture(cam)
            }
        },
        hwAccel = true,
        resolution = 0,
        bitrate = 0,
        codecStart = true,
        videoPreview = false
    )
    _cameraEvents.tryEmit(VideoEvent(cam, started = true, w = videoParams.size.width, h = videoParams.size.height))
}
```

Imports needed: `CameraListener` (already in scope via `CameraService`), `VideoEvent`.

### 2g — Expand `startCapture` for regular cameras

Reference: `HardwareServiceImpl.kt:587–642`. Add the regular camera branch after the existing SCREEN_SHARING block:

```kotlin
actual fun startCapture(camId: String?) {
    val rawId = camId?.substringAfter("camera://", missingDelimiterValue = camId)
    val cam = rawId?.takeIf { it.isNotEmpty() } ?: cameraService.switchInput(true) ?: return
    Log.d(TAG, "startCapture: $cam (original: $camId)")
    synchronized(shouldCapture) { shouldCapture.add(cam) }

    if (cam == VideoDevices.SCREEN_SHARING) {
        val projection = pendingScreenShareProjection ?: run {
            Log.w(TAG, "startCapture(SCREEN_SHARING): no pending projection")
            return
        }
        pendingScreenShareProjection = null
        val params = cameraService.getParams(cam) ?: run {
            Log.w(TAG, "startCapture(SCREEN_SHARING): no params for $cam")
            return
        }
        val surface = mCameraPreviewSurface.get() ?: run {
            Log.w(TAG, "startCapture(SCREEN_SHARING): no preview surface")
            return
        }
        if (!cameraService.startScreenSharing(params, projection, surface, context.resources.displayMetrics)) {
            projection.stop()
        }
        return
    }

    // Regular camera
    val surface = mCameraPreviewSurface.get()
    if (surface == null) {
        Log.w(TAG, "startCapture($cam): no surface registered yet, queued")
        return
    }
    openCameraPreview(cam)
}
```

Key changes from the old version:
- `shouldCapture.add(cam)` at the top — so if the surface arrives later via `addPreviewVideoSurface`, the queued camera ID triggers `openCameraPreview`
- Screen sharing now uses `mCameraPreviewSurface.get()` instead of `TextureView(context)` (an unattached view with no SurfaceTexture)

### 2h — Implement `startCameraPreview`

```kotlin
actual fun startCameraPreview(videoPreview: Boolean) {
    val cam = cameraService.switchInput(true) ?: return
    val surface = mCameraPreviewSurface.get() ?: return
    openCameraPreview(cam)
}
```

---

## Phase 3 — Screen sharing: force `startCapture` after renegotiation

**Root cause.** After `callService.replaceVideoMedia(accountId, callId, "camera://desktop")` triggers SDP renegotiation, the daemon does not reliably call `startCapture("desktop")` on the app side. This means `CameraService.startScreenSharing()` + `VirtualDisplay` are never started and the remote peer sees nothing.

**Fix:** After `replaceVideoMedia`, explicitly call `hardwareService.startCapture("camera://desktop")` in the ViewModel. If the daemon also calls `startCapture`, the second call is safe — `pendingScreenShareProjection` is already `null` (cleared on first call) so the duplicate call returns early.

**File:** `shared/src/commonMain/kotlin/net/jami/ui/viewmodel/CallViewModel.kt` — `startScreenShare()` function.

```kotlin
screenShareReadyJob = scope.launch {
    hardwareService.screenShareReady.collect {
        val a = pendingScreenShareAccountId ?: return@collect
        val c = pendingScreenShareCallId ?: return@collect
        pendingScreenShareAccountId = null
        pendingScreenShareCallId = null
        callService.replaceVideoMedia(a, c, "camera://desktop")
        hardwareService.startCapture("camera://desktop")   // ← add this line
        _state.value = _state.value.copy(isScreenSharing = true)
        screenShareReadyJob?.cancel()
    }
}
```

**Note:** Phase 2 must be completed first — Phase 3's `startCapture` fix depends on `mCameraPreviewSurface` being populated (via `addPreviewVideoSurface`) so the SCREEN_SHARING branch has a valid surface to pass to `CameraService.startScreenSharing`.
