# iOS video pipeline

**Status (2026-11-11): implemented on branch `ios-video-pipeline`, built and linked, NOT yet
verified in a call.** Outgoing (camera → peer) and incoming (peer → screen) are both wired; the
simulator has no camera and a two-party call needs a device plus a second client, so the
§2/§3 "Verify" steps are still open. Picture-in-picture (§4) is not done. What was built, and
where it departs from the plan below, is in §0; §1–§6 are the original plan, kept for the
reasoning.

## 0. What was implemented

**Wrapper (`JamiBridgeWrapper.{h,mm}`, rebuilt `.a`, 1274 methods):**
- `addVideoDevice:width:height:rate:` — **a bug the plan missed:** the daemon's
  `VideoDeviceMonitor::addDevice` silently drops a device registered without at least one
  format/width/height/rate entry, and `addVideoDevice:` sent none. No camera ever reached the
  daemon, so neither direction could have worked whatever else was built.
  `DaemonBridge.addVideoDevice` now sends 1280x720@30 (the capture preset).
- `writeOutgoingFrame:angle:videoInputId:` — `getNewFrame` → `configureFrame` → `publishFrame`.
- `registerSinkTarget:width:height:`, `removeSinkTarget:`, `attachDisplayLayer:toSink:`,
  `renderSizeForSink:`, `+layoutDisplayLayer:`. **Departure:** frames are not passed up to
  Kotlin. The `Renderer` wraps each pixel buffer in a `CMSampleBuffer` and enqueues it on the
  `AVSampleBufferDisplayLayer` attached for the sink, from the daemon's thread, as the native
  client does — no per-frame Kotlin/Native call on a foreign thread. A layer attached before
  the daemon announces its sink is parked and picked up on registration.
- `VideoSignal::StartCapture` / `StopCapture` → `onStartCapture:` / `onStopCapture:`. Not in
  the plan either; the daemon uses them to tell the client when to capture.
- `MediaUtils` ported as `jbvideo::` free functions inside the `.mm`. The software-frame
  copy was rewritten row by row (the original mixed `new[]` with `free()` and used the luma
  stride for the chroma plane); behaviour is otherwise the same.
- **FFmpeg headers are not vendored.** `build-jamibridge.sh` includes
  `jami-client-ios/DEPS/<arch>/include` — the dependency build the linked daemon was compiled
  against, so the `AVFrame` layout cannot drift. `JAMI_CLIENT_IOS` overrides the path.
- FFmpeg's `enum AVMediaType` collides with AVFoundation's `AVMediaType`; it is renamed for the
  duration of the include (the native client keeps them in separate files).

**Kotlin:**
- `IOSCameraService`: NV12 output; the output connection is pinned to landscape-left and never
  mirrored (it used to follow the device and mirror the front camera, which sent the peer a
  mirror image and changed the frame size on every turn); each frame carries
  `outgoingFrameAngle`. The preview layer still rotates and mirrors on its own. Frames are
  published under the daemon input named by the last `StartCapture`, so a front/back switch
  keeps feeding the same call.
- `outgoingFrameAngle`: the native client's table, **with the landscape rows swapped** — the
  native client maps device landscape-left to capture landscape-left, Apple (and
  `captureOrientationFor`) to capture landscape-right. Pinned by `CaptureOrientationTest`.
- `HardwareService`: `decodingStarted`/`Stopped` register/remove the sink; `addVideoSurface`
  reuses a display layer already in the view (`VideoSurface`) or installs one
  (`DisplayLayerView`, for `VideoRenderer`'s plain `UIView`); `connectSink`/`getSinkSize`
  report real sizes instead of a hardcoded 1280x720.
- Rotated remote frames: the layer is turned and its bounds swapped by `+layoutDisplayLayer:`,
  which every container's layout pass now calls instead of setting `layer.frame`.

**Still open:** on-device verification; PiP (§4); conference grids may want Metal later;
`CallViewModel.stopScreenShare` still sends Android's `camera://0`/`camera://1`.

Reference implementation: `jami-client-ios/Ring/Ring/Bridging/VideoAdapter.mm` +
`VideoUtils.mm` (the `MediaUtils` category) + `Services/VideoService.swift`.

---

## 1. What exists today

| Piece | State |
|---|---|
| `IOSCameraService` | Real `AVCaptureSession`; enumerates cameras, opens, captures NV12 `CMSampleBuffer`s, handles orientation, switch, permissions. Registers the device node with the daemon (`addVideoDevice` / `setDefaultDevice`). **Does not forward frames** — as of this change the dead per-frame `ByteArray` copy that fed a no-op has been removed (see §5). |
| `HardwareService.ios.kt` | `decodingStarted` / `decodingStopped` forward `VideoEvent`s. `addVideoSurface` / `addPreviewVideoSurface` just stash the `UIView`. `connectSink` returns a hardcoded `flow { emit(1280 to 720) }`. `startVideo` calls `daemonBridge.acquireNativeWindow` (stub → `0L`). Encoder controls (`requestKeyFrame`, `setBitrate`) are `{}`. |
| `DaemonBridge.ios.kt` | `captureVideoFrame`, `captureVideoPacket`, `acquireNativeWindow`, `releaseNativeWindow`, `setNativeWindowGeometry`, `registerVideoCallback`, `unregisterVideoCallback` are all `// TODO: Implement via JamiBridge cinterop`. |
| `JamiBridgeWrapper.{h,mm}` | Has device management (`addVideoDevice`, `setDefaultVideoDevice`, `setDeviceOrientation`, `applyVideoSettings`, `switchVideoInput`) and forwards `VideoSignal::DecodingStarted/Stopped`. **No** frame-in, frame-out, or sink-target selectors. |
| `VideoSurface.ios.kt` | Builds a `UIView` containing an `AVSampleBufferDisplayLayer`, registers it via `hardwareService.addVideoSurface(sinkId, view)`. Nothing ever enqueues a sample buffer into that layer. |
| `CameraPreview` (`VideoSurface.ios.kt`) | Works — `AVCaptureVideoPreviewLayer` straight off the capture session. Local self-view is fine; it is the *encoded/sent* stream and the *remote* stream that are missing. |
| `PictureInPictureManager.ios.kt` | `configurePipController(playerLayer: AVPlayerLayer)` — wrong layer type for a call (the surface is `AVSampleBufferDisplayLayer`); nothing calls it; `enterPipMode` returns `false`. |

The daemon side is fully present in `libjami`: `videomanager_interface.h` exposes
`getNewFrame` / `publishFrame` (iOS/Android only) for capture and
`registerSinkTarget(sinkId, SinkTarget)` for render. Only the Objective-C++ glue and the Kotlin
wiring are missing.

---

## 2. Outgoing (camera → daemon → peer)

### Design (from `VideoAdapter.mm#writeOutgoingFrameWithBuffer`)

Zero-copy. Per captured `CVImageBufferRef`:

```objc
auto frame = libjami::getNewFrame(videoInputId);   // VideoFrame* owned by the daemon
if (!frame) return;                                 // daemon not consuming this input yet
[MediaUtils configureFrame:frame->pointer()         // point AVFrame->data/linesize at the
           fromImageBuffer:image                    //   locked CVPixelBuffer planes; attach a
                     angle:angle];                   //   DISPLAYMATRIX side-data for rotation
libjami::publishFrame(videoInputId);                // hand it to the encoder
```

`videoInputId` is the device node string registered with `addVideoDevice` — jami-kmp uses
`camera://<AVCaptureDevice.uniqueID>` (see `IOSCameraService.openCamera`). It must match exactly
what `switchInput` / `switchVideoInput` sends for the call, or `getNewFrame` returns null.

`MediaUtils configureFrame:fromImageBuffer:angle:` is **not yet ported**. It needs
`libavutil/frame.h`, `libavutil/display.h`, `libavutil/buffer.h`. Port `VideoUtils.mm`'s
`configureFrame:` + `getForrmatFromAppleFormat:` verbatim; they are self-contained.

### Steps

1. **Vendor FFmpeg headers.** Add `libavutil/{frame,display,buffer,rational,pixfmt,avutil}.h`
   (the minimal closure `configureFrame:` needs) under
   `shared/src/nativeInterop/cinterop/headers/`. Match the daemon's FFmpeg version.
2. **Port `MediaUtils`.** New `JamiBridge/MediaUtils.{h,mm}` (or fold the two class methods into
   `JamiBridgeWrapper.mm`). Bring `configureFrame:fromImageBuffer:angle:`,
   `getForrmatFromAppleFormat:`, `copyLineByLineSrc:...` from `jami-client-ios`
   `Ring/Ring/Bridging/VideoUtils.mm`.
3. **Add the bridge selector** to `JamiBridgeWrapper.h`:
   ```objc
   - (void)writeOutgoingFrame:(CVImageBufferRef)image
                        angle:(int)angle
                 videoInputId:(NSString *)videoInputId;
   ```
   and implement it in `.mm` with the three-call body above.
4. **Kotlin seam.** In `DaemonBridge.ios.kt`, replace the `captureVideoFrame` stub body (keep
   the `DaemonBridgeApi` signature — Android relies on the `ByteArray` overload) with an
   iOS-only entry:
   ```kotlin
   fun writeOutgoingVideoFrame(pixelBuffer: CVImageBufferRef, rotation: Int, videoInputId: String) =
       bridge.writeOutgoingFrame(pixelBuffer, angle = rotation, videoInputId = videoInputId)
   ```
   `CVImageBufferRef` crosses cinterop as an opaque pointer; no copy.
5. **Call it from `IOSCameraService.sampleBufferDelegate.captureOutput`:** take
   `CMSampleBufferGetImageBuffer(sampleBuffer)` and pass it straight to
   `writeOutgoingVideoFrame(pixelBuffer, deviceOrientation, "camera://${params.cameraId}")`.
   Do **not** lock/copy — `configureFrame:` locks internally and the daemon copies during
   encode. Respect `muteCapture` / `isCaptureMuted`.
6. **Rotation.** `IOSCameraService` already tracks `deviceOrientation` and calls
   `setDeviceOrientation` on the daemon. Pass the same angle here so the DISPLAYMATRIX matches.

### Verify

Place a 1:1 video call KMP → native client. Native client shows the KMP camera. Rotate the
device; the remote image rotates. `Instruments → Allocations` shows no per-frame heap growth.

---

## 3. Incoming (peer → daemon → screen)

### Design (from `VideoAdapter.mm` `Renderer` + `registerSinkTargetWithSinkId`)

The daemon announces a stream with `VideoSignal::DecodingStarted(sinkId, w, h)` (already
forwarded to `HardwareService.decodingStarted`). To actually receive pixels, register a
`SinkTarget` whose `push` closure the daemon calls per decoded frame:

```objc
auto renderer = std::make_shared<Renderer>();      // holds sinkId, w/h, hasListeners, a mutex
renderer->target.push = [renderer](libjami::FrameBuffer frame) {
    if (!renderer->hasListeners) return;
    PixelBufferInfo info = [MediaUtils getCVPixelBufferFromAVFrame:frame.get()];
    if (!info.pixelBuffer) return;
    // hand (sinkId, info.pixelBuffer, info.rotation) to the Kotlin/Swift renderer
    if (info.ownsMemory) CFRelease(info.pixelBuffer);
};
libjami::registerSinkTarget(std::string(sinkId.UTF8String), renderer->target);
```

`push` runs on a **daemon thread**. Frames for a hardware-decoded stream are already
`CVPixelBuffer`-backed (`frame->data[3]`, `ownsMemory == false`, VideoToolbox); software frames
get converted by `converCVPixelBufferRefFromAVFrame` (`ownsMemory == true`). `getRenderSize:`
and `removeSinkTargetWithSinkId:` (with the `isRendering` condition-variable drain) round it
out. `getCVPixelBufferFromAVFrame` is the other half of the `MediaUtils` port (§2 step 2).

### Rendering the CVPixelBuffer

`VideoSurface.ios.kt` already has an `AVSampleBufferDisplayLayer` per sink. On each frame, wrap
the pixel buffer in a `CMSampleBuffer` and enqueue:

```
CMSampleBufferCreateForImageBuffer(... timing = kCMTimingInfoInvalid ...)
-> attach kCMSampleAttachmentKey_DisplayImmediately
-> [displayLayer enqueueSampleBuffer:]      // on the main thread; drop if !isReadyForMoreMediaData
```

Handle `displayLayer.status == .failed` by `flush`-ing and recreating the layer (happens after
backgrounding). Apply `info.rotation` as a layer `transform` (or pass it through the frame's
DISPLAYMATRIX and let the layer honor it).

`AVSampleBufferDisplayLayer` is enough for correctness and matches the surface that already
exists. The native client instead renders through Metal (`VideoView` / `MTKView`), which is
worth doing later for conference grids (many simultaneous sinks) but is not required for 1:1.

### Steps

1. Port `getCVPixelBufferFromAVFrame` + `converCVPixelBufferRefFromAVFrame` (§2 step 2).
2. Add selectors to `JamiBridgeWrapper.h`: `registerSinkTarget:width:height:hasListeners:`,
   `removeSinkTarget:`, `setHasListeners:forSinkId:`, `getRenderSize:`. Keep the
   `std::map<std::string, std::shared_ptr<Renderer>>` and the `Renderer` struct from
   `VideoAdapter.mm`.
3. Frame delivery into Kotlin: give the bridge a
   `@property(copy) void (^onSinkFrame)(NSString *sinkId, CVImageBufferRef buf, int rotation)`
   that `Renderer::push` invokes. `DaemonBridge.ios.kt` sets it once and fans out to
   `HardwareService`.
4. `DaemonBridge.ios.kt`: implement `registerVideoCallback(id, windowId)` →
   `bridge.registerSinkTarget(id, width, height, hasListeners = true)` returning `true`;
   `unregisterVideoCallback` → `removeSinkTarget`.
5. `HardwareService.ios.kt`:
   - `connectSink(id, windowId)`: stop returning the hardcoded flow. Register the sink, then
     emit `(w, h)` pairs from the `VideoEvent` stream (mirror `BaseVideoSinkManager.connectSink`
     — iOS currently bypasses it).
   - keep a `sinkId -> AVSampleBufferDisplayLayer` map (the layer comes from
     `VideoSurface.ios.kt` via `addVideoSurface`); on `onSinkFrame`, enqueue as above on the
     main queue.
   - `decodingStopped`: flush + detach.
6. `startVideo` / `acquireNativeWindow`: the "native window handle" concept is Android-only
   (ANativeWindow). On iOS return a non-zero sentinel so `BaseVideoSinkManager` treats the sink
   as live; the real association is `sinkId -> layer`, not a handle.

### Verify

1:1 video call native → KMP: KMP shows the remote image within ~1 s of connect. Conference of
3: each tile shows its participant (may lag without Metal — acceptable for a first pass).
Background then foreground the app mid-call: remote image resumes (layer-status recovery).

---

## 4. Encoder controls & Picture-in-Picture

- **`requestKeyFrame` / `setBitrate` are correctly no-ops on iOS.** `VideoSignal::RequestKeyFrame`
  and `SetBitrate` are `#ifdef __ANDROID__` in `videomanager_interface.h`; on iOS the encoder is
  VideoToolbox *inside* the daemon and rate/keyframe control is driven by congestion control
  there. This is parity with the native client, not a gap. (The parity analysis doc previously
  over-flagged this.)
- **`setParameters` / `getCameraInfo`** are already served from AVFoundation capabilities in
  `HardwareService.ios.kt` — fine.
- **PiP** (`PictureInPictureManager.ios.kt`): `AVPictureInPictureController(playerLayer:)` is the
  wrong constructor for a live call. Use
  `AVPictureInPictureController(contentSource: AVPictureInPictureController.ContentSource(
  activeVideoCallSourceView:contentViewController:))` with an
  `AVPictureInPictureVideoCallViewController` (iOS 15+) that hosts the remote
  `AVSampleBufferDisplayLayer`. Requires the `voip` background mode (already declared) and
  `AVAudioSession` category `.playAndRecord`. Lower priority than §2/§3.

---

## 5. Done in this change (safe, no rebuild needed)

`IOSCameraService.sampleBufferDelegate.captureOutput` no longer locks the pixel buffer and
copies the full NV12 plane into a fresh `ByteArray` on every frame to hand it to the
`captureVideoFrame` stub. That was ~30 whole-frame heap allocations per second for the entire
duration of every video call, with zero effect (the bridge method is empty). Capture, preview,
dimension tracking and the `FrameEvent` stream are unchanged. Outgoing frames will be wired
per §2 when the bridge is rebuilt.

---

## 6. Order of work

1. Port `MediaUtils` (`configureFrame:` + `getCVPixelBufferFromAVFrame` + helpers) and vendor
   the FFmpeg headers — shared prerequisite for §2 and §3.
2. **Outgoing (§2)** first: smaller, and makes KMP visible to other clients.
3. **Incoming (§3)** via `AVSampleBufferDisplayLayer`.
4. Rebuild `libJamiBridge_{ios,iossim}.a` (`JamiBridge/build-jamibridge.sh` on macOS) after
   every `.h`/`.mm` change; regenerate cinterop.
5. On-device verification per the §2 / §3 "Verify" blocks.
6. Later: Metal renderer for conference grids; PiP video-call controller (§4).
