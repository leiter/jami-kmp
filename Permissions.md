# Permission Handling Audit: jami-kmp vs jami-client-android

Audit date: 2026-06-13

---

## Declared Permissions

### jami-client-android (reference)

| Permission | Type |
|---|---|
| INTERNET | Normal |
| ACCESS_NETWORK_STATE | Normal |
| ACCESS_WIFI_STATE | Normal |
| MODIFY_AUDIO_SETTINGS | Normal |
| CHANGE_WIFI_STATE | Normal |
| VIBRATE | Normal |
| WAKE_LOCK | Normal |
| RECEIVE_BOOT_COMPLETED | Normal |
| MANAGE_OWN_CALLS | Normal |
| RECORD_AUDIO | Dangerous |
| CAMERA | Dangerous |
| READ_CONTACTS | Dangerous |
| WRITE_CONTACTS | Dangerous |
| READ_PROFILE | Dangerous |
| ACCESS_COARSE_LOCATION | Dangerous |
| ACCESS_FINE_LOCATION | Dangerous |
| POST_NOTIFICATIONS | Dangerous (API 33+) |
| BLUETOOTH (maxSdkVersion=30) | Dangerous (legacy) |
| FOREGROUND_SERVICE | Foreground |
| FOREGROUND_SERVICE_CAMERA | Foreground |
| FOREGROUND_SERVICE_MICROPHONE | Foreground |
| FOREGROUND_SERVICE_LOCATION | Foreground |
| FOREGROUND_SERVICE_DATA_SYNC | Foreground |
| FOREGROUND_SERVICE_REMOTE_MESSAGING | Foreground |
| FOREGROUND_SERVICE_MEDIA_PROJECTION | Foreground |
| FOREGROUND_SERVICE_PHONE_CALL | Foreground |
| USE_FULL_SCREEN_INTENT | Other |

### jami-kmp (before fixes)

| Permission | Type | Status |
|---|---|---|
| INTERNET | Normal | OK |
| ACCESS_NETWORK_STATE | Normal | OK |
| RECORD_AUDIO | Dangerous | OK |
| CAMERA | Dangerous | OK |
| VIBRATE | Normal | OK |
| POST_NOTIFICATIONS | Dangerous (API 33+) | Declared but never requested |
| ACCESS_FINE_LOCATION | Dangerous | OK |
| ACCESS_COARSE_LOCATION | Dangerous | OK |
| MODIFY_AUDIO_SETTINGS | Normal | OK |
| WAKE_LOCK | Normal | OK |
| BLUETOOTH_CONNECT | Dangerous (API 31+) | OK (correct modernisation) |
| USE_FULL_SCREEN_INTENT | Other | OK |
| FOREGROUND_SERVICE | Foreground | OK |
| FOREGROUND_SERVICE_DATA_SYNC | Foreground | OK |
| RECEIVE_BOOT_COMPLETED | Normal | OK |

---

## Runtime Permission Request Sites

### jami-client-android

| Location | Permission | Trigger | Method |
|---|---|---|---|
| AccountWizardActivity | POST_NOTIFICATIONS | Account creation complete | requestPermissions() |
| HomeActivity | POST_NOTIFICATIONS | App startup (with rationale dialog) | registerForActivityResult |
| ScanFragment | CAMERA | Open QR scanner | registerForActivityResult |
| ProfilePhotoFragment | CAMERA | Take profile photo | registerForActivityResult |
| CallFragment | CAMERA | Accept/initiate video call | registerForActivityResult |
| ConversationFragment | RECORD_AUDIO | Tap audio capture | requestPermissions() |
| ConversationFragment | CAMERA | Tap camera in chat | requestPermissions() |
| LocationSharingFragment | ACCESS_FINE_LOCATION | Enable location sharing | requestPermissions() |
| HomeActivity | POST_NOTIFICATIONS (rationale) | Startup, after first denial | shouldShowRequestPermissionRationale() |

### jami-kmp (before fixes)

| Location | Permission | Trigger | Method |
|---|---|---|---|
| CallScreen.kt | CAMERA | Accept/initiate video call | PermissionRequesterEffect |
| CallScreen.kt | RECORD_AUDIO | Initiate outgoing call | PermissionRequesterEffect |
| ChatScreen.kt | CAMERA | Tap camera in chat | PermissionRequesterEffect |
| LocationSharingScreen.kt | ACCESS_FINE_LOCATION | Enable location sharing | PermissionRequesterEffect |
| QrCodeScannerView.android.kt | CAMERA | Open QR scanner | rememberLauncherForActivityResult |

---

## Gap Analysis

### P1 — Blocking

#### 1. POST_NOTIFICATIONS never requested
- `jami-client-android` requests at account creation and at app startup with rationale
- `jami-kmp`: `hasNotificationsPermission()` exists in `AndroidDeviceRuntimeService` but nothing ever triggers a request
- **Impact**: Users are never prompted → push notifications silently disabled

#### 2. Foreground service type permissions missing from manifest
Android 14 (API 34) enforces that every `foregroundServiceType` value used in `startForeground()` must have a matching `uses-permission` entry. The following were missing:

| Permission | Needed for |
|---|---|
| FOREGROUND_SERVICE_CAMERA | Video calls backgrounded |
| FOREGROUND_SERVICE_MICROPHONE | Audio calls backgrounded |
| FOREGROUND_SERVICE_LOCATION | Background location sharing |
| FOREGROUND_SERVICE_REMOTE_MESSAGING | Persistent messaging service |
| FOREGROUND_SERVICE_PHONE_CALL | Call handling |

Additionally, `CallNotificationService` and `JamiDaemonService` declared `foregroundServiceType="dataSync"` instead of the actual types they use at runtime.

### P2 — Feature-breaking

#### 3. READ_CONTACTS not declared in manifest; WRITE_CONTACTS missing entirely
- `READ_CONTACTS` was not in the manifest (despite being in `PermissionRequester.android.kt`)
- `WRITE_CONTACTS` not declared
- Contacts sync calls in `AndroidDeviceRuntimeService` will fail with a `SecurityException` at runtime
- No runtime request exists to prompt the user

#### 4. No first-run permission flow
- `jami-client-android` collects notifications permission at account creation completion
- `jami-kmp` `WelcomeScreen` / `CreateAccountScreen` / `AccountSummaryScreen` request no permissions
- Users land in the app with no permissions pre-granted

#### 5. No permission rationale handling
- `jami-client-android` uses `shouldShowRequestPermissionRationale()` + `SharedPreferences` tracking
- `jami-kmp` has no rationale dialogs — repeated denials silently fail with no explanation

### P3 — Polish

#### 6. READ_PROFILE not declared
Used in `jami-client-android` for profile name query during account setup. Not declared in kmp manifest.

---

## What Is Fine

- Camera / microphone on-demand flows (call, chat, QR scan) are correctly wired via `PermissionRequesterEffect`
- Location permission for location sharing is handled
- `BLUETOOTH_CONNECT` (API 31+) replaces legacy `BLUETOOTH` correctly

---

## Fixes Applied (2026-06-13)

### Fix 1 — Manifest: foreground service type permissions
Added to `android-app/src/androidMain/AndroidManifest.xml`:
- `FOREGROUND_SERVICE_CAMERA`
- `FOREGROUND_SERVICE_MICROPHONE`
- `FOREGROUND_SERVICE_LOCATION`
- `FOREGROUND_SERVICE_REMOTE_MESSAGING`
- `FOREGROUND_SERVICE_PHONE_CALL`
- `READ_CONTACTS`
- `WRITE_CONTACTS`

Updated service `foregroundServiceType` attributes:
- `CallNotificationService`: `dataSync` → `microphone|phoneCall`
- `JamiDaemonService`: `dataSync` → `dataSync|remoteMessaging`

### Fix 2 — POST_NOTIFICATIONS requested at end of onboarding
`AccountSummaryScreen` now fires `PermissionRequesterEffect(AppPermission.Notifications)` on first
composition. If denied, a `JamiAlertDialog` explains that notifications are needed for incoming
calls and messages. After the notifications result, contacts permission is requested sequentially.

### Fix 3 — Contacts permissions added to manifest and requested at onboarding
`READ_CONTACTS` and `WRITE_CONTACTS` added to the manifest. `AccountSummaryScreen` requests
`AppPermission.Contacts` sequentially after the notifications request completes.

### Fix 4 — Basic rationale handling for notifications denial
A `JamiAlertDialog` is shown when the user denies notifications, using the
`permission_dialog_post_notifications_blocked_message` string resource that explains
they can enable the permission in device settings.

---

## Remaining Gaps (out of scope for this pass)

- `READ_PROFILE` not declared (low priority — profile sync via daemon bridge)
- `shouldShowRequestPermissionRationale()` pre-request rationale for camera/microphone (Accompanist or expect/actual required)
- `FOREGROUND_SERVICE_MEDIA_PROJECTION` — only needed if screen-share feature is implemented
- Persistent "open device settings" deep-link button in denial dialog (requires expect/actual `openAppSettings()`)
