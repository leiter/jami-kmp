# TODO

## UI / Live Updates

- [ ] **Live cross-device own-profile avatar update** — When the user changes their avatar on another device, the change is not reflected live in the HomeScreen search bar avatar (only after app restart). The official client handles this in `accountProfileReceived` by writing the VCard to disk and updating `account.loadedProfile` via RxJava combineLatest. Our KMP approach (listening for `AccountEvent.ProfileReceived`) is wired up but the live update does not fire. Possible causes: (a) `event.photo` is empty in the cross-device case, (b) the daemon version used here does not fire this callback on DHT sync. To investigate: add logcat logging in `DaemonCallbacksImpl.onAccountProfileReceived` to confirm the callback is reached; if not, look for an alternative daemon event.

## Testing

- [ ] Run ViewModel tests: `./gradlew :shared:desktopTest --tests "net.jami.viewmodel.*"`
- [ ] Run integration tests: `./gradlew :shared:desktopTest --tests "net.jami.services.*IntegrationTest"`
- [ ] Run full test suite: `./gradlew :shared:desktopTest`
- [ ] Verify platform builds: `./gradlew :shared:compileKotlinDesktop :shared:compileDebugKotlinAndroid`
- [ ] Fix any failing tests from the `viewModelScope()` migration (replaced `backgroundScope` to fix `UncompletedCoroutinesError`)
