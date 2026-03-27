# TODO

## Testing

- [ ] Run ViewModel tests: `./gradlew :shared:desktopTest --tests "net.jami.viewmodel.*"`
- [ ] Run integration tests: `./gradlew :shared:desktopTest --tests "net.jami.services.*IntegrationTest"`
- [ ] Run full test suite: `./gradlew :shared:desktopTest`
- [ ] Verify platform builds: `./gradlew :shared:compileKotlinDesktop :shared:compileDebugKotlinAndroid`
- [ ] Fix any failing tests from the `viewModelScope()` migration (replaced `backgroundScope` to fix `UncompletedCoroutinesError`)
