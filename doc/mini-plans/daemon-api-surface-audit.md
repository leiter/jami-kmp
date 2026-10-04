# Mini-Plan: Full Daemon API Surface Audit

_Audited 2026-09-25. Scope: everything not already covered by the calling/messaging/contacts/security/notifications mini-plans — video/audio device management, codecs, plugins, diagnostics, and a general map for future reference._

## Summary

The Jami daemon (`libjami`) exposes **~150 public functions** across 9 interface headers. jami-kmp wires **~100 of them** (67%); jami-client-android (the reference app) calls **~120 of them** (80%). The single largest unused capability is the **Jami plugin system** (19 functions in `plugin_manager_interface.h`) — entirely absent from jami-kmp, and marked "TODO: not yet ported" in its own code. Audio device selection (6 functions: input/output/ringtone device choice by index or name) is also absent from KMP but implemented in the reference app. History management, certificate pinning/validation, exposed services (service discovery), and several video capabilities are gaps.

## Inventory by Category

### 1. Core Lifecycle (`jami.h`) — 6 functions
| Function | KMP | Android Ref | Notes |
|----------|-----|-------------|-------|
| `version()` | N | Y | Version string; unused in both |
| `platform()` | N | N | Platform identifier; never called |
| `arch()` | N | N | Architecture string; never called |
| `init(flags)` | Y | Y | Daemon initialization with debug/log flags |
| `start(config_file)` | Y | Y | Daemon startup |
| `fini()` | Y | Y | Daemon shutdown |
| `initialized()` | N | N | Check init state; never called |
| `logging(whom, action)` | N | N | Log handler control; diagnostic only |

**Gap:** No access to version/platform/arch info; no diagnostic logging control.

### 2. Account Management (`configurationmanager_interface.h`) — 50+ functions

#### Core Account Operations (10/10 wired in KMP)
| Function | KMP | Android Ref |
|----------|-----|-------------|
| `getAccountDetails` | Y | Y |
| `getVolatileAccountDetails` | Y | Y |
| `setAccountDetails` | Y | Y |
| `setAccountActive` | Y | Y |
| `addAccount` | Y | Y |
| `removeAccount` | Y | Y |
| `getAccountTemplate` | Y | Y |
| `getAccountList` | Y | Y |
| `sendRegister` | Y | Y |
| `setAccountsOrder` | Y | Y |

#### Device Linking (5/5 wired)
| Function | KMP | Android Ref |
|----------|-----|-------------|
| `addDevice` | Y | Y |
| `confirmAddDevice` | Y | Y |
| `cancelAddDevice` | Y | Y |
| `revokeDevice` | Y | Y |
| `getKnownRingDevices` | Y | Y |

#### Credentials & Security (6/6 wired)
| Function | KMP | Android Ref |
|----------|-----|-------------|
| `getCredentials` | Y | Y |
| `setCredentials` | Y | Y |
| `changeAccountPassword` | Y | Y |
| `exportToFile` | Y | Y |
| `getPasswordKey` | N | Y | Retrieves raw password key; advisory only |
| `isPasswordValid` | N | N | Validate password without changing; unused |

#### Name Services (4/4 wired)
| Function | KMP | Android Ref |
|----------|-----|-------------|
| `lookupName` | Y | Y |
| `lookupAddress` | Y | Y |
| `registerName` | Y | Y |
| `searchUser` | Y | Y |

#### Contacts & Trust (5/5 wired)
| Function | KMP | Android Ref |
|----------|-----|-------------|
| `addContact` | Y | Y |
| `removeContact` | Y | Y |
| `getContacts` | Y | Y |
| `getContactDetails` | Y | Y |
| `getTrustRequests` | Y | Y |
| `acceptTrustRequest` | Y | Y |
| `discardTrustRequest` | Y | Y |
| `sendTrustRequest` | Y | Y |

#### Profile (1/1 wired)
| Function | KMP | Android Ref |
|----------|-----|-------------|
| `updateProfile` | Y | Y |

#### Messaging (2/3 wired)
| Function | KMP | Android Ref | Notes |
|----------|-----|-------------|-------|
| `sendAccountTextMessage` | Y | Y | Multi-MIME-type message |
| `setIsComposing` | Y | Y | "Typing" indicator |
| `setMessageDisplayed` | Y | Y | Read receipt |
| `getLastMessages` | N | N | Fetch messages by timestamp; likely unused |

#### **Audio Device Management (12 functions — ALL MISSING FROM KMP)**
| Function | KMP | Android Ref | Notes |
|----------|-----|-------------|-------|
| `getAudioPluginList` | N | Y | List available audio backends |
| `setAudioPlugin` | N | Y | Switch audio plugin (PulseAudio, ALSA, etc.) |
| `getAudioInputDeviceList` | N | Y | Enumerate input devices |
| `getAudioOutputDeviceList` | N | Y | Enumerate output devices |
| `setAudioInputDevice(index)` | N | Y | Select input by index |
| `setAudioOutputDevice(index)` | N | Y | Select output by index |
| `setAudioRingtoneDevice(index)` | N | Y | Select ringtone device |
| `getAudioInputDeviceIndex` | N | Y | Get index by name |
| `getAudioOutputDeviceIndex` | N | Y | Get index by name |
| `getCurrentAudioDevicesIndex` | N | Y | Get current device selections |
| `getCurrentAudioOutputPlugin` | N | Y | Get active plugin name |
| `getSupportedAudioManagers` | N | Y | List audio manager APIs |
| `getAudioManager` | N | Y | Get active manager |
| `setAudioManager` | N | Y | Switch manager |

#### Audio Processing (6 functions — 1/6 wired)
| Function | KMP | Android Ref | Notes |
|----------|-----|-------------|-------|
| `getNoiseSuppressState` | N | N | Get noise suppression setting |
| `setNoiseSuppressState` | N | N | Enable/disable noise suppression |
| `getEchoCancellationState` | N | N | Get EC setting |
| `setEchoCancellationState` | N | N | Enable/disable EC |
| `getVoiceActivityDetectionState` | N | N | Get VAD setting |
| `setVoiceActivityDetectionState` | N | N | Enable/disable VAD |
| `isAgcEnabled` | N | N | Get AGC state |
| `setAgcState` | Y | N | Enable/disable automatic gain control |

#### Audio Muting/Metering (7 functions — 3/7 wired)
| Function | KMP | Android Ref |
|----------|-----|-------------|
| `muteCapture` | Y | Y |
| `isCaptureMuted` | Y | Y |
| `mutePlayback` | N | N |
| `isPlaybackMuted` | N | N |
| `muteRingtone` | Y | Y |
| `isRingtoneMuted` | N | N |
| `muteDtmf` | N | N |
| `isDtmfMuted` | N | N |
| `isAudioMeterActive` | N | Y |
| `setAudioMeterState` | N | Y |

#### Recording & Playback (5 functions — 0/5 wired)
| Function | KMP | Android Ref | Notes |
|----------|-----|-------------|-------|
| `getRecordPath` | N | Y | Call recording destination |
| `setRecordPath` | N | Y | Set destination folder |
| `getIsAlwaysRecording` | N | N | Auto-record calls |
| `setIsAlwaysRecording` | N | N | Enable auto-recording |
| `getRecordPreview` | N | N | Live playback preview during record |
| `setRecordPreview` | N | N | Enable preview |
| `getRecordQuality` | N | N | Recording quality level |
| `setRecordQuality` | N | N | Set quality |
| `getConferenceResolution` | N | N | Video mixer output resolution |
| `setConferenceResolution` | N | N | Set mixer resolution |

#### History & Connection (3 functions — 0/3 wired)
| Function | KMP | Android Ref | Notes |
|----------|-----|-------------|-------|
| `getConnectionList` | N | Y | Low-level connection info |
| `getConversationConnectivity` | N | N | Per-conversation peer connectivity |
| `getConversationTrackedMembers` | N | N | Membership tracking |
| `getChannelList` | N | Y | Get channels in a connection |
| `monitor(continuous)` | N | Y | Daemon status monitoring |
| `setHistoryLimit` | N | N | Retention days for call/msg history |
| `getHistoryLimit` | N | N | Get retention setting |
| `setRingingTimeout` | N | N | Call ring time before fallback |
| `getRingingTimeout` | N | N | Get ring timeout |

#### Codec Management (4/4 wired)
| Function | KMP | Android Ref |
|----------|-----|-------------|
| `getCodecList` | Y | Y |
| `getActiveCodecList` | Y | Y |
| `setActiveCodecList` | Y | Y |
| `getCodecDetails` | Y | Y |
| `setCodecDetails` | N | N | Edit codec parameters; unused |

#### TLS/Ciphers (2/2 wired partially)
| Function | KMP | Android Ref |
|----------|-----|-------------|
| `getSupportedTlsMethod` | N | Y | Supported TLS versions |
| `getSupportedCiphers` | N | N | Supported cipher suites |

#### Network & Connectivity (5 functions)
| Function | KMP | Android Ref |
|----------|-----|-------------|
| `connectivityChanged` | Y | Y | Notify daemon of network change |
| `getAllIpInterface` | N | N | Enumerate local IPs |
| `getAllIpInterfaceByName` | N | N | Enumerate IPs by interface |
| `getAddrFromInterfaceName` | N | N | Resolve interface to IP |
| `setVolume` | N | N | Per-device volume control |
| `getVolume` | N | N | Get device volume |

#### Moderators & Conferences (2 functions)
| Function | KMP | Android Ref |
|----------|-----|-------------|
| `setDefaultModerator` | N | N | Set default conf moderator |
| `getDefaultModerators` | N | N | List default moderators |

#### Push Notifications (3/3 wired)
| Function | KMP | Android Ref |
|----------|-----|-------------|
| `setPushNotificationToken` | Y | Y |
| `setPushNotificationTopic` | N | Y | iOS-specific topic config |
| `setPushNotificationConfig` | Y | Y |
| `pushNotificationReceived` | Y | Y |

### 3. Call Management (`callmanager_interface.h`) — 35+ functions

#### Basic Calls (6/6 wired)
| Function | KMP | Android Ref |
|----------|-----|-------------|
| `placeCall` | Y | Y |
| `placeCallWithMedia` | Y | Y |
| `accept` | Y | Y |
| `acceptWithMedia` | Y | Y |
| `refuse` | Y | Y |
| `hangUp` | Y | Y |

#### Call Control (6/6 wired)
| Function | KMP | Android Ref |
|----------|-----|-------------|
| `hold` | Y | Y |
| `resume` | Y | Y |
| `transfer` | Y | Y |
| `attendedTransfer` | Y | Y |
| `muteLocalMedia` | Y | Y |
| `toggleRecording` | Y | Y |

#### Call Information (3/3 wired)
| Function | KMP | Android Ref |
|----------|-----|-------------|
| `getCallDetails` | Y | Y |
| `getCallList` | N | N | Get active calls; unused |
| `getIsRecording` | Y | Y |

#### Media Negotiation (2/2 wired)
| Function | KMP | Android Ref |
|----------|-----|-------------|
| `requestMediaChange` | Y | Y |
| `answerMediaChangeRequest` | Y | Y |

#### DTMF & Audio (3/3 wired)
| Function | KMP | Android Ref |
|----------|-----|-------------|
| `playDTMF` | Y | Y |
| `startTone` | N | N | Play system tone; unused |
| `switchInput` | Y | Y |

#### Instant Messaging (1/1 wired)
| Function | KMP | Android Ref |
|----------|-----|-------------|
| `sendTextMessage` | Y | Y |

#### File Playback (2/2 not wired)
| Function | KMP | Android Ref | Notes |
|----------|-----|-------------|-------|
| `startRecordedFilePlayback` | N | Y | Play call recording |
| `stopRecordedFilePlayback` | N | Y | Stop playback |
| `recordPlaybackSeek` | N | N | Seek in playback |

#### Conference Operations (18/13 wired)
| Function | KMP | Android Ref | Notes |
|----------|-----|-------------|-------|
| `joinParticipant` | Y | Y | Add two calls to conference |
| `createConfFromParticipantList` | N | N | Create conf from contact list |
| `setConferenceLayout` | Y | Y | Change video layout |
| `isConferenceParticipant` | N | N | Check if call is in conf |
| `addParticipant` | Y | Y | Add call to conf |
| `addMainParticipant` | Y | Y | Add local device to conf |
| `detachLocalParticipant` | N | N | Remove local from conf |
| `detachParticipant` | Y | Y | Remove call from conf |
| `joinConference` | N | Y | Merge two confs |
| `hangUpConference` | Y | Y | End conference |
| `holdConference` | Y | Y | Hold conference |
| `resumeConference` | Y | Y | Resume conference |
| `getConferenceList` | N | N | Get active conferences |
| `getConferenceDetails` | Y | Y | Get conf info |
| `getParticipantList` | Y | Y | Get call IDs in conf |
| `getConferenceInfos` | N | N | Get detailed participant info |
| `setModerator` | N | N | Set participant as moderator |
| `muteParticipant` | N | N | Mute participant (DEPRECATED) |
| `muteStream` | N | Y | Mute specific stream by URI/device |
| `setActiveParticipant` | Y | Y | Set focus participant |
| `setActiveStream` | N | Y | Set focus stream by URI/device |
| `hangupParticipant` | N | Y | Disconnect a participant |
| `raiseParticipantHand` | N | N | Raise hand (DEPRECATED) |
| `raiseHand` | N | Y | Raise hand by URI/device |

### 4. Conversation Management (`conversation_interface.h`) — 18 functions

All core functions are wired (9/9):
- `startConversation`, `acceptConversationRequest`, `declineConversationRequest`, `removeConversation`, `getConversations`
- `updateConversationInfos`, `conversationInfos`, `setConversationPreferences`, `getConversationPreferences`
- `addConversationMember`, `removeConversationMember`, `getConversationMembers`
- `sendMessage`, `loadConversation`, `loadSwarmUntil`
- `countInteractions`, `clearCache`, `searchConversation`
- `reloadConversationsAndRequests`
- `getActiveCalls`, `getConversationRequests`

| Function | KMP | Android Ref | Notes |
|----------|-----|-------------|-------|
| All core ops | Y | Y | Fully wired in KMP |
| `countInteractions` | N | N | Count msgs in range; unused |
| `clearCache` | N | N | Clear local cache; unused |
| `reloadConversationsAndRequests` | N | N | Force reload from daemon; unused |

### 5. File Transfer (`datatransfer_interface.h`) — 6 functions

| Function | KMP | Android Ref |
|----------|-----|-------------|
| `sendFile` | Y | Y |
| `downloadFile` | Y | Y |
| `cancelDataTransfer` | Y | Y |
| `fileTransferInfo` | Y | Y |

All four core functions are wired in KMP.

### 6. Video Device Management (`videomanager_interface.h`) — 20 functions

#### Device Enumeration & Selection (6 functions — 4/6 partially)
| Function | KMP | Android Ref | Notes |
|----------|-----|-------------|-------|
| `getDeviceList` | N | N | Enumerate cameras; missing from both |
| `getCapabilities` | N | N | Get camera resolutions/framerates; missing |
| `getSettings` | N | N | Get per-camera settings; missing |
| `applySettings` | Y | Y | Apply camera settings |
| `setDefaultDevice` | Y | Y | Select default camera |
| `getDefaultDevice` | N | N | Get current default; missing |
| `getDeviceParams` | N | N | Get device parameters; missing |

#### Video Input (2 functions — 0/2)
| Function | KMP | Android Ref | Notes |
|----------|-----|-------------|-------|
| `openVideoInput` | N | N | Open custom video input; unused |
| `closeVideoInput` | N | N | Close video input; unused |

#### Device Orientation (1/1 wired)
| Function | KMP | Android Ref |
|----------|-----|-------------|
| `setDeviceOrientation` | Y | Y |

#### Frame Capture (Android-specific, 3 functions — 2/3)
| Function | KMP | Android Ref | Notes |
|----------|-----|-------------|-------|
| `addVideoDevice` | Y | Y | Register camera with daemon |
| `removeVideoDevice` | Y | Y | Unregister camera |
| `getNewFrame` | N | N | Get frame buffer from daemon |
| `publishFrame` | N | N | Publish captured frame |

#### Media Recording (2 functions — 0/2)
| Function | KMP | Android Ref | Notes |
|----------|-----|-------------|-------|
| `startLocalMediaRecorder` | N | Y | Record local video |
| `stopLocalRecorder` | N | Y | Stop recording |

#### Video Rendering (3 functions — 2/3)
| Function | KMP | Android Ref | Notes |
|----------|-----|-------------|-------|
| `registerSinkTarget` | N | N | Register frame sink; no implementation |
| `startShmSink` | N | N | Shared memory video (Linux-specific) |
| `getRenderer` | N | N | Get rendering info; unused |

#### Hardware Acceleration (2/2 wired)
| Function | KMP | Android Ref |
|----------|-----|-------------|
| `getDecodingAccelerated` | N | N | Unused in both |
| `setDecodingAccelerated` | N | N | Unused in both |
| `getEncodingAccelerated` | N | N | Unused in both |
| `setEncodingAccelerated` | N | N | Unused in both |

#### Media Player (5 functions — 0/5 not called from services)
| Function | KMP | Android Ref | Notes |
|----------|-----|-------------|-------|
| `createMediaPlayer` | N | N | Create player instance |
| `closeMediaPlayer` | N | N | Close player |
| `pausePlayer` | N | N | Pause playback |
| `mutePlayerAudio` | N | N | Mute player audio |
| `playerSeekToTime` | N | N | Seek to time |
| `getPlayerPosition` | N | N | Get current time |
| `getPlayerDuration` | N | N | Get duration |
| `setAutoRestart` | N | N | Auto-restart on end |

### 7. Presence Management (`presencemanager_interface.h`) — 8 functions

| Function | KMP | Android Ref |
|----------|-----|-------------|
| `publish` | N | N | Publish presence status |
| `answerServerRequest` | N | N | Answer subscription request |
| `subscribeBuddy` | Y | Y | Subscribe to contact presence |
| `getSubscriptions` | N | N | Get subscription list |
| `setSubscriptions` | N | N | Set subscriptions from list |

Presence is wired for buddy subscription only; server-based presence (SIP PUBLISH) not implemented.

### 8. Plugins (`plugin_manager_interface.h`) — 19 functions (ALL MISSING)

| Function | KMP | Android Ref | Notes |
|----------|-----|-------------|-------|
| `loadPlugin` | N | N | Load .jpl file |
| `unloadPlugin` | N | N | Unload plugin |
| `getPluginDetails` | N | N | Get plugin metadata |
| `getPluginPreferences` | N | N | Get pref UI schema |
| `setPluginPreference` | N | N | Set pref value |
| `getPluginPreferencesValues` | N | N | Get all pref values |
| `resetPluginPreferencesValues` | N | N | Reset to defaults |
| `getInstalledPlugins` | N | N | List installed plugins |
| `getLoadedPlugins` | N | N | List loaded plugins |
| `getPlatformInfo` | N | N | Get platform details |
| `installPlugin` | N | N | Install from archive |
| `uninstallPlugin` | N | N | Uninstall by path |
| `getCallMediaHandlers` | N | N | List call media plugins |
| `getChatHandlers` | N | N | List chat plugins |
| `toggleCallMediaHandler` | N | N | Enable/disable for call |
| `toggleChatHandler` | N | N | Enable/disable for chat |
| `getCallMediaHandlerDetails` | N | N | Get handler metadata |
| `getCallMediaHandlerStatus` | N | N | Get handler state |
| `getChatHandlerDetails` | N | N | Get handler metadata |
| `getChatHandlerStatus` | N | N | Get handler state |
| `getPluginsEnabled` | N | N | Plugin system on/off |
| `setPluginsEnabled` | N | N | Enable/disable plugins |
| `sendWebViewMessage` | N | N | Send message to plugin UI |
| `sendWebViewAttach` | N | N | Open plugin UI |
| `sendWebViewDetach` | N | N | Close plugin UI |

**Note:** jami-kmp's own TODO.md lists plugins as "not yet ported — deprioritised." The plugin system is a complete subsystem with load/config/unload operations and a WebView integration layer for plugin UIs.

### 9. Network Services & Service Exposure (`networkservice_interface.h`) — 8 functions

| Function | KMP | Android Ref | Notes |
|----------|-----|-------------|-------|
| `addExposedService` | N | Y | Advertise a service |
| `updateExposedService` | N | Y | Update service info |
| `removeExposedService` | N | Y | Stop advertising |
| `getExposedServices` | N | Y | Get advertised services |
| `queryPeerServices` | N | Y | Query peer's services |
| `openServiceTunnel` | N | Y | Open tunnel to peer service |
| `closeServiceTunnel` | N | Y | Close tunnel |
| `getActiveTunnels` | N | Y | List active tunnels |

Service discovery/exposure is entirely missing from KMP. This is used by the reference app for feature negotiation (e.g., which peer supports file transfer, screen sharing).

## Findings

### F1: Audio Device Selection — Missing (Category: Gap) — **NOT APPLICABLE TO MOBILE (2026-10-04)**
**Evidence:** `configurationmanager_interface.h:129–140`
**Impact:** Users cannot switch between multiple Bluetooth headsets, wired earbuds, or speaker—the daemon exposes device enumeration and selection by index, but KMP has no UI or backend wiring.
**Daemon support:** 
- `getAudioInputDeviceList()`, `getAudioOutputDeviceList()`, `getAudioRingtoneDeviceList()`
- `setAudioInputDevice(index)`, `setAudioOutputDevice(index)`, `setAudioRingtoneDevice(index)`
- `getAudioInputDeviceIndex(name)`, `getAudioOutputDeviceIndex(name)`
- `getCurrentAudioDevicesIndex()`

**Reference:** jami-client-android calls all of these in `HardwareService.kt` for audio device switching.

### F2: Codec Configuration UI — Incomplete (Category: Gap)
**Evidence:** `configurationmanager_interface.h:118–127`, KMP DaemonBridge methods `getCodecList`, `getActiveCodecList`, `setActiveCodecList`, `getCodecDetails`
**Impact:** The daemon supports full codec enumeration and reordering of active codecs (priority order matters for negotiation), but KMP exposes no UI to reorder or disable codecs. Users cannot prefer VP9 over H.264, or disable specific audio codecs. The reference app also does not expose this in its UI (settings are daemon-side only, not user-configurable).
**Daemon support:** Four functions are wired; `setCodecDetails` (per-codec param editing) is not called.

### F3: Plugin System Not Ported — Major Gap (Category: Gap)
**Evidence:** `plugin_manager_interface.h` (19 functions); jami-kmp/doc/TODO.md explicitly lists "Chat plugins: not yet ported — deprioritised."
**Impact:** The Jami plugin system allows third-party message processors (e.g., spam filters, encryption wrappers, translation), call media handlers (echo cancellation plugins), and WebView-based plugin UIs. None of this is ported to KMP. A user cannot install a Jami plugin archive (`.jpl` file) or toggle plugin features.
**Daemon support:** Full subsystem: install, load, configure, enable/disable, WebView attachment/detachment.
**Scope:** 3–4 weeks for basic install/load/config; additional 2 weeks for WebView integration if needed.

### F4: Video Device Enumeration — Missing (Category: Gap) — **DEFERRED (2026-10-04): getDeviceList/getCapabilities are not in the SWIG bindings**
**Evidence:** `videomanager_interface.h:178–185`, missing from both KMP and reference app for external camera lists.
**Impact:** Cannot enumerate attached USB cameras or other external video sources. Desktop/laptop scenario: cannot switch between built-in and external webcams dynamically without re-enumerating.
**Daemon support:** `getDeviceList()`, `getCapabilities(deviceId)`, `getSettings(deviceId)`, `applySettings(deviceId, map)`

### F5: History Management Functions Absent (Category: Gap) — **DONE (2026-10-04)**
**Evidence:** `configurationmanager_interface.h:175–177`, `getHistoryLimit`, `setHistoryLimit`, `getRingingTimeout`, `setRingingTimeout`
**Impact:** No way to set call history retention policy or ringing timeout from app UI. These are hard-coded defaults or require manual daemon config file editing.
**Daemon support:** Four functions exist; never called.

### F6: Presence Server Integration Missing (Category: Gap)
**Evidence:** `presencemanager_interface.h:35–41`, only buddy subscription is wired.
**Impact:** Jami supports SIP-based presence publishing (announcing "away," "busy," "on the phone") via `publish()` and `answerServerRequest()`. This is completely absent from KMP and reference Android app. Advanced deployments (corporate SIP presence) rely on this.
**Daemon support:** `publish(status, note)`, `answerServerRequest(uri, flag)`, `getSubscriptions()`, `setSubscriptions()`

### F7: Service Discovery/Exposure — Missing (Category: Gap) — **DONE on Android (2026-10-04); Apple targets blocked on a missing header**
**Evidence:** `networkservice_interface.h`, 8 functions; zero KMP wiring.
**Impact:** Cannot advertise local services (e.g., "this peer has file transfer," "this peer supports screen share") or query peer capabilities before attempting operations. Peer feature negotiation is implicit/trial-based.
**Daemon support:** `addExposedService()`, `updateExposedService()`, `removeExposedService()`, `queryPeerServices()`, `openServiceTunnel()`, `closeServiceTunnel()`, `getExposedServices()`, `getActiveTunnels()`
**Reference:** jami-client-android wires all of these in `PeerServicesService.kt` and `ExposedServicesService.kt`.

### F8: Call Recording Playback — Missing (Category: Gap) — **DEFERRED, WRONG API (2026-10-04)**
**Evidence:** `callmanager_interface.h:159–168`, `startRecordedFilePlayback()`, `stopRecordedFilePlayback()`, `recordPlaybackSeek()`.
**Impact:** Recordings can be made, but the app cannot play them back in-app; user must open a media player externally.
**Daemon support:** Three functions for playback control and seeking.

### F9: Media Player Instance Management — Missing (Category: Gap)
**Evidence:** `videomanager_interface.h:192–199` (8 functions for media player creation, playback control, seeking).
**Impact:** No daemon-native media player support in KMP. This is rarely used (ringtone playback is Android-native).

### F10: Diagnostic Functions Unused (Category: Improvement) — **PARTLY DONE (2026-10-04); version/platform/arch and getConversationConnectivity are not in the SWIG bindings**
**Evidence:** `jami.h`, `platform()`, `arch()`, `version()` never called; `logging()` callback registration for debug logs; `configurationmanager_interface.h::monitor()` for daemon status; `getConnectionList()`, `getConversationConnectivity()` for network diagnostics.
**Impact:** No in-app "debug screen" showing daemon version, platform, connection quality, or network stats. Useful for field debugging.
**Daemon support:** `version()`, `platform()`, `arch()`, `monitor()`, `logging()`, `getConnectionList()`, `getConversationConnectivity()`.

### F11: Audio Processing Parameters Not Exposed (Category: Gap) — **PARTLY DONE (2026-10-04); echo-cancellation mis-mapping fixed**
**Evidence:** `configurationmanager_interface.h:140–158`, noise suppression, echo cancellation, VAD, AGC states.
**Impact:** Audio quality tuning is not user-controllable in KMP. The daemon supports these, but KMP only exposes the raw `setAgcState()` call (wired, but with no UI).
**Daemon support:** 8 functions for noise suppression, EC, VAD, AGC.

### F12: Certificate Pinning — Partially Wired (Category: Gap)
**Evidence:** `configurationmanager_interface.h:198–227`, 14 functions; only `getTrustRequests/acceptTrustRequest` are used; no certificate validation/pinning UI.
**Impact:** Advanced TLS cert validation (certificate pinning, custom CA) is available in daemon but not exposed to users. Only peer-side trust (via QR code / trust requests) is implemented.
**Daemon support:** `validateCertificate()`, `getCertificateDetails()`, `pinCertificate()`, `pinCertificatePath()`, `getSupportedTlsMethod()`, etc.

## Proposed Plan

### Phase 1 status (2026-10-04, branch `docs/mini-plans`) — **done, with two items dropped after verification**

Commit `da5fdd9`. Verifying the four Phase 1 items against the daemon headers, the SWIG
bindings and the reference client changed the shape of this phase considerably:

1. **Audio Device Selection — dropped, not a mobile gap.** The claim that
   "jami-client-android calls all of these in `HardwareService.kt`" is false: grepping the whole
   reference tree for `getAudioOutputDeviceList`, `setAudioOutputDevice`,
   `getAudioInputDeviceList`, `getCurrentAudioDevicesIndex` and `setAudioRingtoneDevice` returns
   nothing. On Android and iOS, routing between earpiece/speaker/Bluetooth belongs to
   `AudioManager`/`AVAudioSession`, which jami-kmp already drives through `HardwareService`; the
   daemon's device-index API is for desktop audio backends (PulseAudio/ALSA/CoreAudio). Doing
   this would have been ~1 week of work for the one platform whose bridge is entirely no-ops.
   Revisit only if the desktop bridge is ever built.
2. **History Retention — done.** `getHistoryLimit`/`setHistoryLimit` plus
   `getRingingTimeout`/`setRingingTimeout` are now on `DaemonBridgeApi`, real on Android, wired
   on iOS/macOS through new `JamiBridgeWrapper` passthroughs, and selectable under App
   settings → Advanced. Note: the plan called this an "easy win; reference app already does
   this" — the reference does *not* expose it either (only the daemon's D-Bus XML mentions it),
   so there was no UI to copy.
3. **Call Recording Playback — deferred; the proposed API is the wrong tool.**
   `startRecordedFilePlayback()`/`recordPlaybackSeek()` drive the daemon's *audio layer* (for
   playing a file into a call), not an in-app player. The reference plays recordings with
   Android `MediaPlayer`, and jami-kmp already bundles media3/ExoPlayer and plays voice
   messages with it. The real feature here is a recordings browser screen reusing that player —
   a UI task, not a daemon-wiring task. Tracked by the calling plan's F12.
4. **Diagnostics — done as far as the bindings allow, and folded into the existing screen.**
   `monitor(continuous)` and `getConnectionList(accountId, conversationId)` are now wired.
   `version()`, `platform()`, `arch()` and `getConversationConnectivity()` are **not present in
   `JamiService.java`/`JamiServiceJNI.java`**, so they cannot be called from Android without
   regenerating the SWIG interface and rebuilding `libjami-core-jni.so`; the iOS wrapper has no
   entries for them either. They are out of reach from Kotlin alone.
   Rather than add the proposed new screen, the existing **Debug Logs** screen (which already
   showed captured logs and per-conversation swarm-sync health) gained a daemon section: history
   limit and ringing timeout, hardware decode/encode state, last error alert, last STUN failure,
   the account's connection list, a bounded tail of the daemon's own log, and a "dump daemon
   state to log" action calling `monitor()`. `AccountService.daemonDiagnostics` records the
   signals forwarded in the notifications plan's work, which until now only reached the log.

Coverage-table caveat: the table below is from 2026-09-25 and already understates current state
(Presence reads 1/8 wired; four more presence callbacks and five conference functions have landed
since). Treat its percentages as indicative only.

---

### Phase 1: Low-Hanging Fruit (2–3 weeks)

Quick wins that close obvious gaps without major architectural work:

1. **Audio Device Selection UI** (1 week)
   - Extend `HardwareService` to call `getAudioInputDeviceList()`, `getAudioOutputDeviceList()`
   - Add settings screen for device selection (Bluetooth, wired, speaker, earpiece)
   - Wire `setAudioInputDevice()`, `setAudioOutputDevice()`, `setAudioRingtoneDevice(index)`
   - Android & iOS: both need platform audio enumeration + daemon call integration

2. **History Retention Settings** (3 days)
   - Add Settings screen for history limit (days)
   - Wire `getHistoryLimit()` / `setHistoryLimit(days)` daemon calls
   - Easy win; reference app already does this

3. **Call Recording Playback** (3 days)
   - Add playback UI to MessageBubble when a recording interaction is detected
   - Wire `startRecordedFilePlayback()`, `stopRecordedFilePlayback()`, `recordPlaybackSeek()`
   - Reuse existing media player preview controls

4. **Diagnostic Debug Screen** (1 week)
   - New Settings → About → Diagnostics screen
   - Display: daemon version, platform, architecture, active connection count, per-conversation connectivity
   - Wire `version()`, `platform()`, `arch()`, `monitor()`, `getConnectionList()`, `getConversationConnectivity()`
   - Useful for field support; can catch network/daemon issues

### Phase 2 status (2026-10-04) — **items 5 and 6 done, item 7 dropped**

Commit `1a3fd28`.

- **Item 5, service discovery/exposure (F7) — done on Android.** All eight daemon functions are
  on `DaemonBridgeApi`, `createNetworkServiceCallback()` is no longer a no-op (it forwards
  `peerServicesReceived`, `serviceTunnelOpened`, `serviceTunnelClosed`), and
  `PeerServicesService` is ported from libjamiclient with Flows and kotlinx.serialization in
  place of RxJava and Gson. Two behaviours from the reference worth keeping in mind: the daemon
  fires `TunnelOpened` *synchronously* from `openServiceTunnel`, before returning the tunnel id,
  so opens are matched through a pending-open queue rather than by id; and `queryPeerServices`
  correlates by request id, so one peer's reply cannot satisfy another peer's query.
  Open tunnels are listed in the debug screen. **No feature UI yet** — a peer-services browser
  is its own screen, and the Apple targets still return empty until
  `networkservice_interface.h` plus wrapper methods are added.
- **Item 6, audio processing (F11) — partly done, and it hid a real bug.** Android's
  `setEchoCancellation()` called `JamiService.setAgcState()`: the echo-cancellation toggle in
  settings was switching automatic gain control. AGC is now a setting of its own
  (`CallSettings.automaticGainControl`), and `setEchoCancellation` logs that it is unreachable on
  Android — libjami declares `setEchoCancellationState` but it is **not in the SWIG bindings**.
  iOS/macOS already called the right function, so the bug was Android-only. `getNoiseSuppressState`
  is now exposed too. Of the plan's "8 functions", three are reachable on Android:
  `getNoiseSuppressState`, `setNoiseSuppressState`, `setAgcState`.
- **Item 7, video device enumeration (F4) — dropped.** `getDeviceList` and `getCapabilities` are
  not in the SWIG bindings either (only `getDefaultDevice`, `getSettings`, `applySettings`), and
  the plan itself calls it "minor for Android". Revisit with the desktop bridge.

**Recurring theme across Phases 1 and 2:** the audit counted functions in the daemon headers, not
in the generated bindings. Five Phase 1–2 items turned out to be unreachable from Kotlin
(`version`, `platform`, `arch`, `getConversationConnectivity`, `setEchoCancellationState`,
`getDeviceList`, `getCapabilities`). Check `JamiService.java` before planning any further item
from this document.

---

### Phase 2 — original plan (agreed 2026-10-04)

Checked the same day, before any work starts:

- **Item 5 (service discovery/exposure, F7).** The Android SWIG bindings *do* carry the whole set
  — `addExposedService`, `updateExposedService`, `removeExposedService`, `getExposedServices`,
  `queryPeerServices`, `openServiceTunnel`, `closeServiceTunnel`, `getActiveTunnels` — and
  `NetworkServiceCallback` exists, with `DaemonBridge.android.kt`'s factory for it still an empty
  no-op (the notifications plan's F4). However there is **no `networkservice_interface.h` under
  `shared/src/nativeInterop/cinterop/headers/`**, so iOS/macOS cannot reach any of it until that
  header and matching `JamiBridgeWrapper` methods are added. Expect Android-first, with iOS
  parity as separate native work.
- **Item 6 (audio processing params).** Only `getNoiseSuppressState`, `setNoiseSuppressState` and
  `setAgcState` are in the SWIG bindings. `getEchoCancellationState`/`setEchoCancellationState`
  are in the daemon header but **not** bound, and there is no `getAgcState` at all — so a UI can
  set AGC but never read back its state. The plan's "8 functions" is three reachable ones.
- **Item 7 (video device enumeration).** `getDeviceList` and `getCapabilities` are **not** bound
  either (only `getDefaultDevice`, `getSettings`, `applySettings`), and the plan itself calls
  this "minor for Android". Lowest value of the three.

---

### Phase 2: Medium Effort (3–4 weeks)

Larger features that improve user experience but require more testing:

5. **Service Discovery & Exposure** (2 weeks)
   - Port `PeerServicesService` + `ExposedServicesService` logic from reference app
   - Wire all 8 functions in `networkservice_interface.h`
   - Enables UI to query "does peer support file transfer?" before attempting
   - Allows peers to announce service capabilities dynamically
   - Requires peer contact model updates to store service info

6. **Audio Processing Parameters UI** (1 week)
   - Settings screen for noise suppression, EC, VAD, AGC (toggle switches)
   - Wire getter/setter pairs: `getNoiseSuppressState()`, `setNoiseSuppressState()`, etc.
   - Test audio quality impact on real calls

7. **Video Device Enumeration & Selection** (1 week)
   - Desktop/laptop scenario: let users pick between built-in camera and external USB camera
   - Wire `getDeviceList()`, `getCapabilities()`, `setDefaultDevice()`
   - Minor for Android (single rear/front switch already works); more useful on desktop

### Phase 3: Major Effort (6–8 weeks)

Substantial subsystems; prioritize by user demand:

8. **Plugin System Partial Port** (4–5 weeks)
   - Basic install/load/unload: `installPlugin()`, `loadPlugin()`, `unloadPlugin()`, `getInstalledPlugins()`, `getLoadedPlugins()`
   - Plugin preferences UI: `getPluginPreferences()`, `setPluginPreference()`
   - Call media handler toggle: `getCallMediaHandlers()`, `toggleCallMediaHandler()`
   - Chat handler toggle: `getChatHandlers()`, `toggleChatHandler()`
   - WebView integration (plugin UI rendering) deferred to Phase 4
   - Risk: plugin architecture divergence from C++ daemon; requires careful testing

9. **Presence Server Integration** (1–2 weeks)
   - Add publish status/note: `publish(status, note)`
   - Handle subscription requests: `answerServerRequest()`, `getSubscriptions()`, `setSubscriptions()`
   - Requires UI: user status picker (available, away, busy, on the phone), note input
   - Mostly straightforward daemon wrapping; UI design is main effort

10. **Certificate Pinning & Validation UI** (2 weeks)
    - Settings screen for certificate management
    - Display peer certificates, allow pinning, show validation status
    - Wire: `validateCertificate()`, `getCertificateDetails()`, `pinCertificate()`, `pinCertificatePath()`, etc.
    - Depends on domain/security audit results; may be pushed to a security-specific plan

### Phase 4: Future/Deferred

- **Plugin WebView Integration** — rendering plugin UIs; moderate complexity; depends on Phase 3
- **Advanced Codec Configuration UI** — reordering codecs by priority; low user demand
- **Media Player Instances** — daemon-side media playback; rarely used (native playback suffices)
- **Desktop JVM Daemon Bridge** — architectural blocker (JNI/KMP conflict); would require separate JVM module

## Effort Estimate

| Phase | Category | Effort | Notes |
|-------|----------|--------|-------|
| Phase 1 | Quick wins | 2–3 wks | Audio device selection, history settings, playback UI, diagnostics screen |
| Phase 2 | Medium | 3–4 wks | Service discovery, audio tuning, video device enum |
| Phase 3 | Major | 6–8 wks | Plugins (core only), presence server, cert pinning |
| Phase 4 | Future | TBD | Plugin WebView, advanced codecs, media player |

**Total (Phases 1–3): 11–15 weeks** to reach parity with jami-client-android on all non-experimental features.

**Quick MVP (Phase 1 only): 2–3 weeks** closes the most visible gaps (audio device selection, diagnostic info).

## Summary Table: Coverage by Category

| Category | Total Functions | Wired in KMP | % | Notes |
|----------|-----------------|--------------|---|-------|
| Core Lifecycle | 6 | 2 | 33% | `init`, `start`, `fini` wired; diagnostic functions unused |
| Account & Auth | 50+ | 30 | 60% | Core account ops 100%; audio device / history / recording missing |
| Calls | 35+ | 20 | 57% | Basic calls 100%; file playback, some conference ops missing |
| Conversations | 18 | 13 | 72% | All core ops wired; some utility functions unused |
| File Transfer | 4 | 4 | 100% | Complete |
| Video Devices | 20 | 3 | 15% | Only basic device switching; no enumeration, recording |
| Presence | 8 | 1 | 12% | Only buddy subscription; server presence missing |
| Plugins | 19 | 0 | 0% | Entire subsystem missing; deprioritised |
| Network Services | 8 | 0 | 0% | Entirely missing; used by reference app |
| **TOTAL** | **~150** | **~100** | **~67%** | |

