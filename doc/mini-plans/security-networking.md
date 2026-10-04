# Mini-Plan: Security, Privacy & Networking Settings Gaps

_Audited 2026-09-25. Scope: TLS/STUN/TURN/proxy/UPnP/ICE config, account security settings._

## Summary

The audit compared the complete daemon account property space (`account_const.h`, 78+ properties) against what jami-kmp's settings UI exposes (28 security/networking properties across four screens). KMP is missing 20+ settings that the daemon supports and the official Android app exposes. Most notably: SIP-specific account configuration, SRTP fallback, DHT proxy server explicit port control, call limiter, presence/subscription settings, certificate verification policy overrides, TLS disableSecureDlgCheck, and DTMF settings. All observable settings are read/written via `AccountSubSettingsViewModel` → `AccountService.setAccountDetails()`. No security bugs found, but three medium-risk items: TLS verification toggles lack clear warnings, TURN passwords stored plaintext in UI state, and no input validation on port fields.

## Findings

### F1: SIP Account Settings Entirely Absent — gap

**Evidence:** 
- KMP hides SIP settings in `AccountSettingsScreen.kt:504–516` (shows only hostname + username for SIP, read-only display)
- Android reference exposes full SIP settings: `account_general_prefs.xml:19–73` (alias, hostname, username, password, proxy, user-agent, auto-answer)
- Daemon properties: `ACCOUNT_ALIAS`, `ACCOUNT_HOSTNAME`, `ACCOUNT_USERNAME`, `ACCOUNT_PASSWORD`, `ACCOUNT_REALM`, `ACCOUNT_ROUTE_SET`, `ACCOUNT_USER_AGENT`, `ACCOUNT_REGISTRATION_EXPIRE`

**Impact:** 
SIP users cannot edit SIP server credentials, proxy routing, or expiry settings. Account configuration is locked to creation-time defaults.

**Daemon support:** 
Account.alias, Account.hostname, Account.username, Account.password, Account.routeset, Account.useragent, Account.registrationExpire; `account_const.h:119–130`.

---

### F2: SRTP Fallback and Key Exchange Details Hidden — gap

**Evidence:**
- KMP exposes only one toggle: `SRTP_KEY_EXCHANGE` (maps "sdes"/"") in `AccountAdvancedSettingsScreen.kt:338–342`
- Daemon provides two independent controls: `SRTP.enable` (master on/off) and `SRTP.rtpFallback` (allow RTP if SRTP negotiation fails)
- Android reference: only shows `SRTP.keyExchange` toggle (same as KMP)
- Daemon properties: `SRTP.keyExchange`, `SRTP.enable`, `SRTP.rtpFallback`; `account_const.h:220–223`

**Impact:** 
Minor — the key-exchange toggle is present. But users cannot disable SRTP entirely (only toggle key exchange method) and cannot control fallback behavior. This is also an Android parity issue (Android also doesn't expose `SRTP.enable` and `SRTP.rtpFallback`).

**Daemon support:** 
`SRTP.enable`, `SRTP.rtpFallback`; `account_const.h:220–223`.

---

### F3: DHT Proxy Configuration Incomplete — gap

**Evidence:**
- KMP exposes: `proxyEnabled`, `proxyServer`, `proxyListEnabled`, `proxyListUrl` in `AccountAdvancedSettingsScreen.kt:161–197`
- Daemon provides: `Account.proxyEnabled`, `Account.proxyServer`, `Account.proxyListEnabled`, `Account.dhtProxyListUrl`, **`Account.dhtProxyServerPort`**, `Account.proxyPushToken`
- KMP ConfigKey enum has no `DHT_PROXY_SERVER_PORT` mapping
- Android reference: matches KMP (no port setting exposed)
- Daemon property: `Account.dhtProxyServerPort`; `account_const.h:147`

**Impact:** 
If a DHT proxy server runs on a non-standard port, users must hardcode it into the `proxyServer` string (e.g., "proxy.example.com:5000"). No separate port field for clarity.

**Daemon support:** 
`Account.dhtProxyServerPort`; `account_const.h:147`.

---

### F4: STUN Configuration Incomplete — gap

**Evidence:**
- KMP exposes: `stunEnabled`, `stunServer` in `AccountAdvancedSettingsScreen.kt:261–277`
- Daemon provides: `STUN.enable`, `STUN.server` only (no additional properties)
- But `account_const.h` shows no other STUN keys
- Comparison OK; both KMP and Android match daemon support

**Impact:** None observed; this is correctly implemented.

---

### F5: TURN Credentials Stored Plaintext in UI State — improvement

**Evidence:**
- `AccountSubSettingsViewModel.kt:80–81`: `val turnPassword: String = ""`
- `AccountAdvancedSettingsScreen.kt:246–256`: password field with `PasswordVisualTransformation()` (only visual)
- State is in `MutableStateFlow` which can be inspected in debuggers, memory dumps, or recomposition logs
- Daemon stores credentials in account archive (encrypted when account has password)

**Impact:** 
Low in practice (password is not broadcast to network, only sent via JNI to daemon), but technically the plaintext password persists in Kotlin memory across compose recompositions and can be logged/inspected.

**Daemon support:** 
`TURN.password`; `account_const.h:191`.

---

### F6: Port Range Fields Lack Input Validation — improvement

**Evidence:**
- `AccountAdvancedSettingsScreen.kt:284–331` audio/video port fields use `KeyboardOptions(keyboardType = KeyboardType.Number)` only
- No range checks (0–65535 not enforced in UI)
- `AccountSubSettingsViewModel.kt:360–388` directly forwards strings to daemon via `updateDetail()`
- Android reference: same (no validation in preferences XML)

**Impact:** 
Malformed port values could be written to daemon config (e.g., "99999" or "abc"). Daemon may reject or silently ignore, but UI does not warn user.

**Daemon support:** 
`Account.audioPortMin`, `Account.audioPortMax`, `Account.videoPortMin`, `Account.videoPortMax`; `account_const.h:167–176`.

---

### F7: TLS Verification Toggles Lack Context Warnings — improvement

**Evidence:**
- `AccountAdvancedSettingsScreen.kt:433–453` three toggles: `tlsVerifyServer`, `tlsVerifyClient`, `tlsRequireClientCert`
- No inline help text or warning when verification is disabled
- Daemon properties exist but UI does not explain security implications
- Android reference: also no warnings (same limitation)

**Impact:** 
Disabling server verification silently downgrades to susceptibility to MITM attacks. Users may not understand the consequence. Standard practice is to show a warning or link to documentation.

**Daemon support:** 
`TLS.verifyServer`, `TLS.verifyClient`, `TLS.requireClientCertificate`; `account_const.h:238–240`.

---

### F8: DTMF Type Configuration Missing — gap

**Evidence:**
- Daemon property: `Account.dtmfType`; `account_const.h:112`
- KMP: no ConfigKey mapping, no UI control
- Android reference: also not exposed (deferred feature)
- Purpose: selects DTMF payload (in-band vs. out-of-band SIP INFO)

**Impact:** 
SIP users cannot choose DTMF delivery method. Daemon uses default. If peer requires INFO-based DTMF, calls may not support tone dialing.

**Daemon support:** 
`Account.dtmfType`; `account_const.h:112`.

---

### F9: Active Call Limit Not Exposed — gap

**Evidence:**
- Daemon property: `Account.activeCallLimit`; `account_const.h:118`
- KMP: no ConfigKey mapping, no UI control
- Android reference: not exposed
- Purpose: limits concurrent calls on the account (e.g., 1 for typical phone, many for business)

**Impact:** 
Accounts without a configured limit accept unlimited concurrent calls. Users cannot set per-account call limits.

**Daemon support:** 
`Account.activeCallLimit`; `account_const.h:118`.

---

### F10: Presence and Subscription Settings Missing — gap

**Evidence:**
- Daemon properties: `Account.presencePublishSupported`, `Account.presenceSubscribeSupported`, `Account.presenceEnabled`; `account_const.h:197–200`
- KMP: ConfigKey.PRESENCE_* exist in enum but not wired to any UI screen
- Android reference: not exposed in preferences XML
- Purpose: controls whether account publishes/subscribes to contact presence (online/offline status)

**Impact:** 
Presence features may not be controllable at account level. Users cannot opt out if account type supports presence.

**Daemon support:** 
`Account.presencePublishSupported`, `Account.presenceSubscribeSupported`, `Account.presenceEnabled`; `account_const.h:197–200`.

---

### F11: Allow Certificate from History/Contact/Trusted — gap

**Evidence:**
- Daemon properties: `Account.allowCertFromHistory`, `Account.allowCertFromContact`, `Account.allowCertFromTrusted`; `account_const.h:133–135`
- KMP: no ConfigKey mapping, no UI
- Android reference: also not exposed
- Purpose: relaxes TLS peer verification to accept certs previously seen or from trusted contacts

**Impact:** 
TLS verification is all-or-nothing; no option to use contact/history-based trust (weaker than full verification but stronger than disabled).

**Daemon support:** 
`Account.allowCertFromHistory`, `Account.allowCertFromContact`, `Account.allowCertFromTrusted`; `account_const.h:133–135`.

---

### F12: TLS Disable Secure Dialog Check Not Exposed — gap

**Evidence:**
- Daemon property: `TLS.disableSecureDlgCheck`; `account_const.h:241`
- KMP: no ConfigKey mapping, no UI
- Android reference: not exposed
- Purpose: SIP-specific; disables "Secure" dialog header requirement in TLS

**Impact:** 
Interoperability issue only; affects SIP servers that don't send Secure dialog headers. Users cannot work around.

**Daemon support:** 
`TLS.disableSecureDlgCheck`; `account_const.h:241`.

---

### F13: Published Address and Port Configuration Missing — gap

**Evidence:**
- Daemon properties: `Account.publishedAddress`, `Account.publishedPort`, `Account.publishedSameAsLocal`; `account_const.h:126–129`
- KMP: no ConfigKey mapping, no UI
- Android reference: exposed in `account_advanced_prefs.xml:127–132` (SIP-only, conditional visibility)
- Purpose: for accounts behind NAT, publish different (external) address:port than locally bound

**Impact:** 
NAT traversal less granular; relies on UPnP/STUN only. Manual published address setting not available.

**Daemon support:** 
`Account.publishedAddress`, `Account.publishedPort`, `Account.publishedSameAsLocal`; `account_const.h:126–129`.

---

### F14: Local Interface and Bind Address Selection Missing — gap

**Evidence:**
- Daemon properties: `Account.localInterface`, `Account.bindAddress`, `Account.localPort`; `account_const.h:121–128`
- KMP: no ConfigKey mapping, no UI
- Android reference: `Account.localInterface` exposed in `account_advanced_prefs.xml:96–124` as a dropdown (SIP only, conditional)
- Purpose: choose which network interface to bind to (multi-homed hosts)

**Impact:** 
On multi-interface machines, the daemon auto-selects an interface. Users cannot force a specific one (e.g., prefer IPv4 over IPv6 interface).

**Daemon support:** 
`Account.localInterface`, `Account.bindAddress`, `Account.localPort`; `account_const.h:121–128`.

---

### F15: Mailbox and User Agent Customization Missing — gap

**Evidence:**
- Daemon properties: `Account.mailbox`, `Account.useragent`, `Account.hasCustomUserAgent`; `account_const.h:111, 130, 132`
- KMP: no ConfigKey mapping for mailbox or useragent, no UI
- Android reference: `Account.useragent` exposed in `account_general_prefs.xml:61–64` (optional category), `Account.mailbox` not shown
- Purpose: mailbox for voicemail; user-agent for SIP identification (custom or default)

**Impact:** 
Voicemail configuration not accessible. User-Agent field cannot be customized (defaults to daemon-generated).

**Daemon support:** 
`Account.mailbox`, `Account.useragent`, `Account.hasCustomUserAgent`; `account_const.h:111, 130, 132`.

---

### F16: ICE, TURN Realm, and Trickle-ICE Not Exposed — gap

**Evidence:**
- Daemon property: `TURN.realm`; `account_const.h:192` (exists but no trickle-ICE or ICE-restart settings visible)
- KMP: `TURN_REALM` not in ConfigKey enum; no realm field in UI
- Android reference: also not exposed for TURN realm

**Impact:** 
If TURN server requires realm authentication, users cannot configure it. Realm must be server-default or hardcoded by daemon.

**Daemon support:** 
`TURN.realm`; `account_const.h:192`.

---

### F17: DNS Resolver and Bootstrap List URL Not Exposed — gap

**Evidence:**
- Daemon properties: `Account.bootstrapListUrl`, `Account.dhtProxyListUrl` (proxy list is exposed in KMP, but not bootstrap list)
- Daemon also has: `RingNS.account` (nameserver account), `RingNS.uri` (nameserver URI)
- KMP: `RINGNS_HOST` maps `RingNS.uri` and is exposed. `RingNS.account` not mapped.
- Android reference: `RingNS.uri` shown, `RingNS.account` not exposed
- Daemon property: `RingNS.account`; `account_const.h:256`

**Impact:** 
Minor; nameserver account selection not user-facing. Expected to be default per account type.

**Daemon support:** 
`RingNS.account`, `Account.bootstrapListUrl`; `account_const.h:155, 256`.

---

### F18: Account Enable Flag Wired but Hidden from Main Settings — semi-gap

**Evidence:**
- Daemon property: `Account.enable` (volatile-accessible via `Account.active`)
- KMP: `ACCOUNT_ENABLE` exists in ConfigKey but not exposed in `AccountSettingsScreen` or `AccountAdvancedSettingsScreen`
- Android reference: exposed as a top-level toggle in `account_general_prefs.xml:20–23` (`android:id="@+id/account_status"`)
- KMP alternative: toggle via `JamiAccountSwitch` in `AccountSettingsScreen.kt:385–393` (top app bar, maps to `setOnline()`)

**Impact:** 
No functional gap; KMP moves the toggle to the top bar rather than a settings section. Parity maintained via different UX.

**Daemon support:** 
`Account.enable`; `account_const.h:110`.

---

### F19: Moderator Settings and Auto-Publish Not Exposed — gap

**Evidence:**
- Daemon properties: `Account.defaultModerators`, `Account.localModeratorsEnabled`, `Account.allModeratorsEnabled`, `Account.accountPublish`, `Account.accountDiscovery`; `account_const.h:150–152, 158–160`
- KMP: no ConfigKey mappings, no UI
- Android reference: not exposed (group/moderator features deferred)
- Purpose: group/swarm conversation moderation settings

**Impact:** 
Swarm moderation config not user-settable; relies on daemon defaults.

**Daemon support:** 
`Account.defaultModerators`, `Account.localModeratorsEnabled`, `Account.allModeratorsEnabled`, `Account.accountPublish`, `Account.accountDiscovery`; `account_const.h:150–152, 158–160`.

---

### F20: IP Auto-Rewrite and UI Customization Hidden — gap

**Evidence:**
- Daemon properties: `Account.allowIPAutoRewrite`, `Account.uiCustomization`; `account_const.h:161–162`
- KMP: no ConfigKey mappings, no UI
- Android reference: not exposed
- Purpose: IP auto-rewrite for SIP routing; UI customization (legacy/experimental)

**Impact:** 
SIP NAT workaround not controllable.

**Daemon support:** 
`Account.allowIPAutoRewrite`, `Account.uiCustomization`; `account_const.h:161–162`.

---

### F21: TLS Specific Settings Incomplete — improvement

**Evidence:**
- `AccountAdvancedSettingsScreen.kt:388–429` exposes: port, CA list, cert, key, password, method, ciphers, server name, verify server, verify client, require client cert, negotiation timeout
- Daemon also supports: `TLS.port` (different from `TLS.listenerPort`), custom CA/cert/key (file paths stored as strings, no path validation in UI), `TLS.disableSecureDlgCheck`
- Android reference: exposes nearly same as KMP

**Impact:** 
File path validation missing; users can enter invalid paths and daemon will silently reject. No error feedback in UI if cert file is unreadable or malformed.

**Daemon support:** 
`TLS.certificateListFile`, `TLS.certificateFile`, `TLS.privateKeyFile`, `TLS.password`, `TLS.method`, `TLS.ciphers`, `TLS.serverName`, `TLS.verifyServer`, `TLS.verifyClient`, `TLS.requireClientCertificate`, `TLS.negotiationTimeoutSec`, `TLS.disableSecureDlgCheck`; `account_const.h:228–242`.

---

## Proposed Plan

### Phase 1: Audit-Critical Improvements (2 pts)

**F7 — Add TLS verification context warnings:**
- Inline warning text below `tlsVerifyServer` toggle: "Disabling server verification is insecure; only use for testing or if you trust all peers."
- Link to security docs if available.
- Update `AccountAdvancedSettingsScreen.kt:433–449`.
- Test: verify warning is visible when toggle is off.

**F5 — Store TURN password securely:**
- Replace `String` with a `SecureString` wrapper or use Android's `KeyStore` (platform-specific via expect/actual).
- Store plaintext only during active edit session; clear after submit.
- Update `AccountSubSettingsViewModel.kt:80, 355–358` and `AccountAdvancedSettingsScreen.kt:246–256`.
- Test: verify no plaintext password in memory dumps.

### Phase 2: High-Value Gaps (8 pts)

**F1 — Unlock SIP Account Editing:**
- Show full SIP settings form when `account.isSip == true` in `AccountSettingsScreen`.
- Expose alias, hostname, username, password, route-set, user-agent, auto-answer, registration expiry.
- Match Android layout: `account_general_prefs.xml`.
- Add ConfigKey mappings if missing: already exist (ACCOUNT_ALIAS, ACCOUNT_USERNAME, etc.).
- Test: edit SIP account, verify changes persist after daemon restart.

**F13 — Add Published Address/Port for NAT:**
- New section in `AccountAdvancedSettingsScreen` after P2P section: "Network Address (NAT)".
- Expose: `publishedSameAsLocal` toggle, then conditionally `publishedAddress`, `publishedPort` (like Android does for SIP).
- Test: set custom published address, verify it's used in SIP/DHT registration.

**F14 — Add Local Interface Selection:**
- After the published address section, add "Bind to Interface" dropdown.
- Query system network interfaces (platform-specific).
- Expose: `localInterface`, `localPort`.
- Test: select different interface, verify daemon binds to it.

**F3 — Add DHT Proxy Port Configuration:**
- Refactor `proxyServer` field into two: `proxyServer` (host) and separate integer `proxyServerPort`.
- Add ConfigKey for `DHT_PROXY_SERVER_PORT`.
- Update `AccountAdvancedSettingsScreen.kt:167–177`.
- Test: set custom proxy with non-standard port, verify connection works.

### Phase 3: Medium-Value Gaps (5 pts)

**F6 — Validate Port Ranges:**
- Add input validation to audio/video port fields: reject out-of-range (< 0 or > 65535).
- Show error inline (red underline + helper text).
- Update `AccountAdvancedSettingsScreen.kt:284–331` and `AccountSubSettingsViewModel.kt:360–388`.
- Test: try entering 99999, verify error displayed and not sent to daemon.

**F10 — Wire Presence Settings:**
- Create new `AccountMessagesSettingsScreen` section or append to existing.
- Expose: `presenceEnabled`, `dhtPublicInCalls` (already exists but misplaced; should be presence section).
- Add toggles with context: "Share online/offline status with contacts."
- Test: toggle presence, verify daemon setting changes.

**F15 — Add User-Agent and Voicemail Customization:**
- New section in general account settings or advanced: "Advanced SIP".
- Expose: `useragent` (text field), `mailbox` (text field).
- Show only for SIP accounts.
- Test: set custom user-agent, verify SIP REGISTER includes it.

### Phase 4: Deprecate or Inline (2 pts; deferred to post-release)

**F2, F9, F11, F12, F16, F19, F20 — Defer:**
- F2 (SRTP fallback): matches Android; defer.
- F9 (active call limit): low-use feature; defer to expert-settings dialog.
- F11 (cert from history): interactive certificate pinning not yet implemented; defer.
- F12, F16, F20 (TLS disableSecureDlgCheck, TURN realm, IP auto-rewrite): SIP-specific interop; defer to Phase 5.
- F19 (moderators): group moderation in-flight; defer pending group feature completion.

---

## Effort Estimate

| Phase | Items | Complexity | Est. Effort |
|-------|-------|-----------|------------|
| Phase 1 (Audit Crit) | 2 (F7, F5) | Low–Med | 4–6 hrs |
| Phase 2 (High-Val) | 4 (F1, F13, F14, F3) | Med–High | 16–20 hrs |
| Phase 3 (Med-Val) | 3 (F6, F10, F15) | Low–Med | 8–10 hrs |
| Phase 4 (Defer) | 7 (F2, F9, F11, F12, F16, F19, F20) | — | — |
| **Total** | **9–16 items** | — | **28–36 hrs** |

**Priority:** Phase 1 (critical security/UX improvements) → Phase 2 (unblock SIP users) → Phase 3 (robustness) → Phase 4 (defer).

**Testing:** Each phase requires:
- Settings persist across account reload (check daemon via JNI/bridge).
- Android parity regression test (sync with latest Android app behavior).
- Manual smoke test on device/simulator.
