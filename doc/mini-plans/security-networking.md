# Mini-Plan: Security, Privacy & Networking Settings Gaps

_Audited 2026-09-25. Scope: TLS/STUN/TURN/proxy/UPnP/ICE config, account security settings._

## Summary

The audit compared the complete daemon account property space (`account_const.h`, 78+ properties) against what jami-kmp's settings UI exposes (28 security/networking properties across four screens). KMP is missing 20+ settings that the daemon supports and the official Android app exposes. Most notably: SIP-specific account configuration, SRTP fallback, DHT proxy server explicit port control, call limiter, presence/subscription settings, certificate verification policy overrides, TLS disableSecureDlgCheck, and DTMF settings. All observable settings are read/written via `AccountSubSettingsViewModel` → `AccountService.setAccountDetails()`. No security bugs were found *in the settings surface this audit covered*, but three medium-risk items remain: TLS verification toggles lack clear warnings, TURN passwords stored plaintext in UI state, and no input validation on port fields. **Not covered by that statement:** the certificate-management slice built for F11 later turned out to carry five real bugs, found by running it on an iOS simulator on 2026-10-14 and fixed in `89d1443` — see `doc/mini-plans/contacts-presence-accounts.md` and `ios_implementation_gap.md` §8. Four of them came from assuming the daemon's shape instead of reading it.

## Findings

### F1: SIP Account Settings Entirely Absent — gap — **CLOSED WITHOUT WORK (2026-10-28); SIP-only, and the largest item in that cluster**

**Evidence:** 
- KMP hides SIP settings in `AccountSettingsScreen.kt:504–516` (shows only hostname + username for SIP, read-only display)
- Android reference exposes full SIP settings: `account_general_prefs.xml:19–73` (alias, hostname, username, password, proxy, user-agent, auto-answer)
- Daemon properties: `ACCOUNT_ALIAS`, `ACCOUNT_HOSTNAME`, `ACCOUNT_USERNAME`, `ACCOUNT_PASSWORD`, `ACCOUNT_REALM`, `ACCOUNT_ROUTE_SET`, `ACCOUNT_USER_AGENT`, `ACCOUNT_REGISTRATION_EXPIRE`

**Impact:** 
SIP users cannot edit SIP server credentials, proxy routing, or expiry settings. Account configuration is locked to creation-time defaults.

**Daemon support:** 
Account.alias, Account.hostname, Account.username, Account.password, Account.routeset, Account.useragent, Account.registrationExpire; `account_const.h:119–130`.

**Closed 2026-10-28 — SIP-only.** Same grounds as F13 and F14, closed on 2026-10-13: Jami is the
primary account type, SIP is secondary, and no SIP user has asked for this. Reopen if that changes.

This is the anchor of the SIP cluster — F8, F12, F15, F20 and `contacts-presence-accounts.md` F7
(SIP credential editing) are all pieces of the same unbuilt screen. If SIP account editing is ever
wanted they should be planned together as one feature, not as six findings.

---

### F2: SRTP Fallback and Key Exchange Details Hidden — gap — **CLOSED WITHOUT WORK (2026-10-28); Android parity, and the finding says so itself**

**Evidence:**
- KMP exposes only one toggle: `SRTP_KEY_EXCHANGE` (maps "sdes"/"") in `AccountAdvancedSettingsScreen.kt:338–342`
- Daemon provides two independent controls: `SRTP.enable` (master on/off) and `SRTP.rtpFallback` (allow RTP if SRTP negotiation fails)
- Android reference: only shows `SRTP.keyExchange` toggle (same as KMP)
- Daemon properties: `SRTP.keyExchange`, `SRTP.enable`, `SRTP.rtpFallback`; `account_const.h:220–223`

**Impact:** 
Minor — the key-exchange toggle is present. But users cannot disable SRTP entirely (only toggle key exchange method) and cannot control fallback behavior. This is also an Android parity issue (Android also doesn't expose `SRTP.enable` and `SRTP.rtpFallback`).

**Daemon support:** 
`SRTP.enable`, `SRTP.rtpFallback`; `account_const.h:220–223`.

**Closed 2026-10-28.** The finding records its own answer: "Android reference: only shows
`SRTP.keyExchange` toggle (same as KMP)" and "This is also an Android parity issue (Android also
doesn't expose `SRTP.enable` and `SRTP.rtpFallback`)". Matching the reference client is the bar this
plan was written against, and KMP meets it. The plan's own Phase 4 already lists F2 as "matches
Android; defer".

Worth being explicit about why it should stay deferred rather than merely waiting: both controls
weaken call confidentiality — `SRTP.enable` off disables media encryption, `rtpFallback` lets a
call silently continue in the clear when SRTP negotiation fails. Neither belongs in a settings
screen without a deliberate decision and a warning, which is the treatment F7 gave the TLS
verification toggles. Not a gap to fill by default.

---

### F3: DHT Proxy Configuration Incomplete — gap — **INVALID (2026-10-13): the key means the opposite of what the finding assumes**

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

**Verified against the daemon we link (2026-10-13): the finding misreads the key.**
`Account.dhtProxyServerPort` is not the port of the proxy this device *connects to* — it is the
port this device *listens on when it runs a DHT proxy server itself*. Its only consumer is
`jamidht/jamiaccount.cpp:2019-2023`, inside `if (conf.dhtProxyServerEnabled)`, where it fills
`dht::ProxyServerConfig::port` for a locally hosted `DhtProxyServer` (default 42443,
`jamiaccount_config.h:60`). It pairs with `Account.dhtProxyServerEnabled`, which this plan does
not mention.

The premise is wrong too: `Account.proxyServer` is *designed* to carry a port, and the daemon's
own default is a port **range** — `dhtproxy.jami.net:[80-95]` (`jamiaccount_config.h:56`). So
"users must hardcode it into the proxyServer string" describes the intended format, not a
workaround, and a separate numeric port field could not express the default.

Nothing to implement. The real capability behind these two keys — hosting a DHT proxy on this
device — is not a mobile feature: it needs inbound reachability and a process that stays alive,
neither of which a phone offers. Note also that the newer daemon in `letsJam/jami-daemon` has
dropped both keys from `account_const.h` entirely.

---

### F4: STUN Configuration Incomplete — gap — **CLOSED WITHOUT WORK (2026-10-28); the finding disproves itself, and the heading was never marked**

**Evidence:**
- KMP exposes: `stunEnabled`, `stunServer` in `AccountAdvancedSettingsScreen.kt:261–277`
- Daemon provides: `STUN.enable`, `STUN.server` only (no additional properties)
- But `account_const.h` shows no other STUN keys
- Comparison OK; both KMP and Android match daemon support

**Impact:** None observed; this is correctly implemented.

**Closed 2026-10-28.** Nothing was ever open here. The finding's own Evidence ends "Comparison OK;
both KMP and Android match daemon support" and its Impact reads "None observed; this is correctly
implemented" — it is a verification that passed, filed under a "gap" title. The heading simply
never got a marker, so it kept counting as open work in the index.

Re-confirmed against source: the daemon exposes only `STUN.enable` and `STUN.server`, both are
mapped (`ConfigKey.kt:79-80`), read at `AccountSubSettingsViewModel.kt:209-210`, written at
`:411`/`:416`, and rendered at `AccountAdvancedSettingsScreen.kt:274-280`. Full coverage of the
available surface.

---

### F5: TURN Credentials Stored Plaintext in UI State — improvement — **DONE (2026-10-12), with a caveat**

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

### F6: Port Range Fields Lack Input Validation — improvement — **DONE (2026-10-12)**

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

### F7: TLS Verification Toggles Lack Context Warnings — improvement — **DONE (2026-10-12)**

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

### F8: DTMF Type Configuration Missing — gap — **CLOSED WITHOUT WORK (2026-10-28); SIP-only, and the ConfigKey claim is stale**

**Evidence:**
- Daemon property: `Account.dtmfType`; `account_const.h:112`
- KMP: no ConfigKey mapping, no UI control
- Android reference: also not exposed (deferred feature)
- Purpose: selects DTMF payload (in-band vs. out-of-band SIP INFO)

**Impact:** 
SIP users cannot choose DTMF delivery method. Daemon uses default. If peer requires INFO-based DTMF, calls may not support tone dialing.

**Daemon support:** 
`Account.dtmfType`; `account_const.h:112`.

**Closed 2026-10-28 — SIP-only.** Same grounds as F13 and F14, closed on 2026-10-13: Jami is the
primary account type, SIP is secondary, and no SIP user has asked for this. Reopen if that changes.

The evidence is also out of date: "KMP: no ConfigKey mapping" is wrong —
`ACCOUNT_DTMF_TYPE("Account.dtmfType")` exists at `ConfigKey.kt:41`. Only the UI control is
missing, so adopting this would be a one-row settings change, not a mapping exercise.

Consistent with `calling-conferencing.md` F13, which closed DTMF tone generation as "WON'T DO
(2026-10-05): no user-facing need".

---

### F9: Active Call Limit Not Exposed — gap — **CLOSED WITHOUT WORK (2026-10-28); Android parity, low-use, ConfigKey claim stale**

**Evidence:**
- Daemon property: `Account.activeCallLimit`; `account_const.h:118`
- KMP: no ConfigKey mapping, no UI control
- Android reference: not exposed
- Purpose: limits concurrent calls on the account (e.g., 1 for typical phone, many for business)

**Impact:** 
Accounts without a configured limit accept unlimited concurrent calls. Users cannot set per-account call limits.

**Daemon support:** 
`Account.activeCallLimit`; `account_const.h:118`.

**Closed 2026-10-28.** "Android reference: not exposed", and the plan's own Phase 4 already files
this as "low-use feature; defer to expert-settings dialog". A concurrent-call cap is a business-PBX
control; on a mobile client the platform call UI and the daemon default already bound this in
practice.

The evidence is stale as well: `ACCOUNT_ACTIVE_CALL_LIMIT("Account.activeCallLimit")` exists at
`ConfigKey.kt:26`, so "no ConfigKey mapping" is wrong and the remaining work would be UI only.

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

### F11: Allow Certificate from History/Contact/Trusted — gap — **DONE (2026-10-13)**

**Evidence:**
- Daemon properties: `Account.allowCertFromHistory`, `Account.allowCertFromContact`, `Account.allowCertFromTrusted`; `account_const.h:133–135`
- KMP: no ConfigKey mapping, no UI
- Android reference: also not exposed
- Purpose: relaxes TLS peer verification to accept certs previously seen or from trusted contacts

**Impact:** 
TLS verification is all-or-nothing; no option to use contact/history-based trust (weaker than full verification but stronger than disabled).

**Daemon support:** 
`Account.allowCertFromHistory`, `Account.allowCertFromContact`, `Account.allowCertFromTrusted`; `account_const.h:133–135`.

**Done (2026-10-13)** alongside the certificate-management work, since it is the same trade-off
from the other side: three `ConfigKey`s and three toggles under Advanced → Security, inside the
TLS block because that is where the daemon applies them. They share one warning rather than
carrying three identical ones — the point is the same each time: a certificate is accepted on the
strength of who presents it rather than because it verifies.

---

### F12: TLS Disable Secure Dialog Check Not Exposed — gap — **CLOSED WITHOUT WORK (2026-10-28); SIP-only by its own description**

**Evidence:**
- Daemon property: `TLS.disableSecureDlgCheck`; `account_const.h:241`
- KMP: no ConfigKey mapping, no UI
- Android reference: not exposed
- Purpose: SIP-specific; disables "Secure" dialog header requirement in TLS

**Impact:** 
Interoperability issue only; affects SIP servers that don't send Secure dialog headers. Users cannot work around.

**Daemon support:** 
`TLS.disableSecureDlgCheck`; `account_const.h:241`.

**Closed 2026-10-28 — SIP-only.** Same grounds as F13 and F14, closed on 2026-10-13: Jami is the
primary account type, SIP is secondary, and no SIP user has asked for this. Reopen if that changes.

The finding describes itself as SIP-specific in both its Purpose ("SIP-specific; disables 'Secure'
dialog header requirement in TLS") and its Impact ("Interoperability issue only; affects SIP
servers that don't send Secure dialog headers"). The plan's Phase 4 already groups it with F16 and
F20 as "SIP-specific interop". It is also a security-weakening toggle, so it would need the same
warning treatment as F7 if ever exposed.

---

### F13: Published Address and Port Configuration Missing — gap — **NOT APPLICABLE to Jami accounts (2026-10-13); SIP-only**

**Evidence:**
- Daemon properties: `Account.publishedAddress`, `Account.publishedPort`, `Account.publishedSameAsLocal`; `account_const.h:126–129`
- KMP: no ConfigKey mapping, no UI
- Android reference: exposed in `account_advanced_prefs.xml:127–132` (SIP-only, conditional visibility)
- Purpose: for accounts behind NAT, publish different (external) address:port than locally bound

**Impact:** 
NAT traversal less granular; relies on UPnP/STUN only. Manual published address setting not available.

**Daemon support:** 
`Account.publishedAddress`, `Account.publishedPort`, `Account.publishedSameAsLocal`; `account_const.h:126–129`.

**Verified against the daemon (2026-10-13). Split three ways, and none of it is a Jami gap:**

- `Account.publishedPort` lives in `SIPAccountConfig` (`sip/sipaccount_config.cpp:190-198`), not
  in the shared base — it does not exist for a Jami account at all.
- `Account.publishedAddress` and `publishedSameAsLocal` *are* in `SipAccountBaseConfig`
  (`sip/sipaccountbase_config.h:47-49`), which `JamiAccountConfig` extends, so they are stored
  for Jami accounts and `SIPAccountBase::loadConfig` does call `setPublishedAddress`
  (`sipaccountbase.cpp:120-122`). But **a Jami account never reads the value back**:
  `JamiAccount::getPublishedIpAddress()` overrides the base to return
  `connectionManager_->getPublishedIpAddress(...)` (`jamiaccount.cpp:3327-3330`), i.e. the address
  the daemon *discovered*, and `JamiAccount::loadConfig` copies only the UPnP and TURN settings
  into the connection manager's config (`jamiaccount.cpp:1012-1020`). The one call site that
  matters for a Jami call picks between the local interface address and the connection manager's
  discovered address (`jamiaccount.cpp:881-884`) — the configured string is not consulted on
  either branch.
- The consumers that *do* read it are in `sip/sipvoiplink.cpp:407-410, 989-991` and
  `sip/sipcall.cpp`, on the SIP transport path.

So a published-address field on a Jami account would be stored, shown back, and ignored — a
setting that lies. The reference client agrees and is explicit about it:
`AdvancedAccountFragment.kt:126-131` sets `isVisible = !isJamiAccount` on PUBLISHED_ADDRESS,
PUBLISHED_PORT and PUBLISHED_SAMEAS_LOCAL. Belongs with the SIP work (F1), not before it.

---

### F14: Local Interface and Bind Address Selection Missing — gap — **MOSTLY SIP-ONLY (2026-10-13); the one Jami-visible part is desktop-only in practice**

**Evidence:**
- Daemon properties: `Account.localInterface`, `Account.bindAddress`, `Account.localPort`; `account_const.h:121–128`
- KMP: no ConfigKey mapping, no UI
- Android reference: `Account.localInterface` exposed in `account_advanced_prefs.xml:96–124` as a dropdown (SIP only, conditional)
- Purpose: choose which network interface to bind to (multi-homed hosts)

**Impact:** 
On multi-interface machines, the daemon auto-selects an interface. Users cannot force a specific one (e.g., prefer IPv4 over IPv6 interface).

**Daemon support:** 
`Account.localInterface`, `Account.bindAddress`, `Account.localPort`; `account_const.h:121–128`.

**Verified against the daemon (2026-10-13):**

- `Account.bindAddress` and `Account.localPort` are `SIPAccountConfig` fields
  (`sip/sipaccount_config.cpp:190, 198, 241-242`) — they do not exist for a Jami account.
- `Account.localInterface` is in `SipAccountBaseConfig` (`sipaccountbase_config.h:41`, default
  `"default"`) and a Jami account **does** read it: `JamiAccount::onConnectedOutgoingCall` resolves
  it to the local address that goes into the SDP of an outgoing call
  (`jamiaccount.cpp:881`, via `getLocalInterface()` at `sipaccountbase.h:132`).

So one of the three is real for Jami — but it is a multi-homed-host knob, and the reference hides
it for Jami accounts too (`AdvancedAccountFragment.kt:123-124`,
`sipLocalInterface?.isVisible = !isJamiAccount`). On a phone there is one sensible interface and
the daemon's `"default"` already picks it; a picker would also need interface enumeration, which
no daemon call provides — it would be a five-target expect/actual (`NetworkInterface` on
Android/JVM, `getifaddrs` on Apple) for a setting that only pays off on a desktop, which CLAUDE.md
ranks secondary. Left open rather than closed: worth doing *with* the SIP work or if desktop
becomes a priority, not before.

---

### F15: Mailbox and User Agent Customization Missing — gap — **CLOSED WITHOUT WORK (2026-10-28); SIP-only, as the plan's own phasing says**

**Evidence:**
- Daemon properties: `Account.mailbox`, `Account.useragent`, `Account.hasCustomUserAgent`; `account_const.h:111, 130, 132`
- KMP: no ConfigKey mapping for mailbox or useragent, no UI
- Android reference: `Account.useragent` exposed in `account_general_prefs.xml:61–64` (optional category), `Account.mailbox` not shown
- Purpose: mailbox for voicemail; user-agent for SIP identification (custom or default)

**Impact:** 
Voicemail configuration not accessible. User-Agent field cannot be customized (defaults to daemon-generated).

**Daemon support:** 
`Account.mailbox`, `Account.useragent`, `Account.hasCustomUserAgent`; `account_const.h:111, 130, 132`.

**Closed 2026-10-28 — SIP-only.** Same grounds as F13 and F14, closed on 2026-10-13: Jami is the
primary account type, SIP is secondary, and no SIP user has asked for this. Reopen if that changes.

Both properties are SIP concepts: `mailbox` is a SIP voicemail number and `useragent` is the SIP
`REGISTER` header. The plan's own Phase 3 entry for F15 already says "Show only for SIP accounts",
which makes it part of the F1 cluster rather than a finding of its own.

---

### F16: ICE, TURN Realm, and Trickle-ICE Not Exposed — gap — **CLOSED WITHOUT WORK (2026-10-28); Android parity, ConfigKey claim stale**

**Evidence:**
- Daemon property: `TURN.realm`; `account_const.h:192` (exists but no trickle-ICE or ICE-restart settings visible)
- KMP: `TURN_REALM` not in ConfigKey enum; no realm field in UI
- Android reference: also not exposed for TURN realm

**Impact:** 
If TURN server requires realm authentication, users cannot configure it. Realm must be server-default or hardcoded by daemon.

**Daemon support:** 
`TURN.realm`; `account_const.h:192`.

**Closed 2026-10-28.** "Android reference: also not exposed for TURN realm", and the plan's Phase 4
groups it with F12 and F20 to defer. Jami ships a default TURN server; a realm is only needed for a
custom TURN deployment that demands realm authentication, which no user has reported.

The evidence is stale: "`TURN_REALM` not in ConfigKey enum" is wrong — `TURN_REALM("TURN.realm")`
exists at `ConfigKey.kt:87`. Only the field is missing. Note also that the finding's title promises
trickle-ICE and ICE-restart settings, but its own evidence concedes the daemon exposes no such
keys, so there was never anything to expose there.

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

### F18: Account Enable Flag Wired but Hidden from Main Settings — semi-gap — **CLOSED WITHOUT WORK (2026-10-28); parity by different UX, as the finding itself concludes**

**Evidence:**
- Daemon property: `Account.enable` (volatile-accessible via `Account.active`)
- KMP: `ACCOUNT_ENABLE` exists in ConfigKey but not exposed in `AccountSettingsScreen` or `AccountAdvancedSettingsScreen`
- Android reference: exposed as a top-level toggle in `account_general_prefs.xml:20–23` (`android:id="@+id/account_status"`)
- KMP alternative: toggle via `JamiAccountSwitch` in `AccountSettingsScreen.kt:385–393` (top app bar, maps to `setOnline()`)

**Impact:** 
No functional gap; KMP moves the toggle to the top bar rather than a settings section. Parity maintained via different UX.

**Daemon support:** 
`Account.enable`; `account_const.h:110`.

**Closed 2026-10-28.** The finding reaches its own verdict — "No functional gap… Parity maintained
via different UX" — and classes itself a semi-gap rather than a gap. Accepted: moving a control
from a settings row to the top app bar is a UX choice, not missing functionality, and the reference
client's placement is not binding.

Re-confirmed against source: `JamiAccountSwitch` is present at `AccountSettingsScreen.kt:385-387`
and its `onCheckedChange` calls `AccountSettingsViewModel.setOnline`
(`AccountSettingsViewModel.kt:362-366`). One nuance the finding glosses: that path goes through
`accountService.setAccountEnabled` → `daemonBridge.sendRegister(accountId, enabled)`, so it drives
registration rather than writing `Account.enable` directly. The user-visible effect — account
registered or not — is the same, and `ACCOUNT_ENABLE` remains mapped (`ConfigKey.kt:12`) for
whoever wants the property itself.

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

### F20: IP Auto-Rewrite and UI Customization Hidden — gap — **CLOSED WITHOUT WORK (2026-10-28); SIP NAT workaround plus a legacy key**

**Evidence:**
- Daemon properties: `Account.allowIPAutoRewrite`, `Account.uiCustomization`; `account_const.h:161–162`
- KMP: no ConfigKey mappings, no UI
- Android reference: not exposed
- Purpose: IP auto-rewrite for SIP routing; UI customization (legacy/experimental)

**Impact:** 
SIP NAT workaround not controllable.

**Daemon support:** 
`Account.allowIPAutoRewrite`, `Account.uiCustomization`; `account_const.h:161–162`.

**Closed 2026-10-28 — SIP-only.** Same grounds as F13 and F14, closed on 2026-10-13: Jami is the
primary account type, SIP is secondary, and no SIP user has asked for this. Reopen if that changes.

The finding gives the grounds itself: `allowIPAutoRewrite` is a "SIP NAT workaround" (Jami's
ICE/TURN path does not need it) and `uiCustomization` is marked "legacy/experimental" — a key with
no defined client contract, which should not be surfaced on guesswork. The plan's Phase 4 already
groups it with F12 and F16.

---

### F21: TLS Specific Settings Incomplete — improvement — **CLOSED WITHOUT WORK (2026-10-28); the premise is stale, the fields are not free text**

**Evidence:**
- `AccountAdvancedSettingsScreen.kt:388–429` exposes: port, CA list, cert, key, password, method, ciphers, server name, verify server, verify client, require client cert, negotiation timeout
- Daemon also supports: `TLS.port` (different from `TLS.listenerPort`), custom CA/cert/key (file paths stored as strings, no path validation in UI), `TLS.disableSecureDlgCheck`
- Android reference: exposes nearly same as KMP

**Impact:** 
File path validation missing; users can enter invalid paths and daemon will silently reject. No error feedback in UI if cert file is unreadable or malformed.

**Daemon support:** 
`TLS.certificateListFile`, `TLS.certificateFile`, `TLS.privateKeyFile`, `TLS.password`, `TLS.method`, `TLS.ciphers`, `TLS.serverName`, `TLS.verifyServer`, `TLS.verifyClient`, `TLS.requireClientCertificate`, `TLS.negotiationTimeoutSec`, `TLS.disableSecureDlgCheck`; `account_const.h:228–242`.

**Closed 2026-10-28 — the premise no longer holds.** The whole finding rests on "file paths stored
as strings, no path validation in UI" and "users can enter invalid paths". They cannot: the three
certificate fields are not text inputs. Each is a `CertFileRow`
(`AccountAdvancedSettingsScreen.kt:403-423`) — a tappable row that opens `FilePickerEffect`
(`:91-101`) and then displays the chosen file's basename (`:643-649`). A path only ever arrives
from the platform file picker, so an unparseable or non-existent path cannot be typed in.

This is the one place F21 differs from F6, the port-range validation that *was* implemented on this
plan (2026-10-12) precisely because those fields did pass raw strings to the daemon. The
certificate fields never had that problem.

What remains of the finding is thinner than its title: a picked file could still be malformed or
unreadable, and the daemon would reject it without UI feedback. That is a daemon-error-surfacing
concern shared by every setting on the screen, not a TLS-specific gap, and `TLS.disableSecureDlgCheck`
is F12's. Nothing TLS-specific left to do.

---

## Status (2026-10-28) — triaged to 3 open

18 of 21 findings are resolved or closed. **F10, F17 and F19 are the only open work**, and all
three are Jami account surface rather than SIP.

| Outcome | Count | Findings |
|---|---|---|
| Done | 4 | F5, F6, F7, F11 |
| Closed without work | 14 | F1, F8, F12, F15, F20 (SIP-only) · F2, F9, F16 (Android parity) · F4, F18 (self-disproved) · F21 (stale premise) · F3 (invalid) · F13, F14 (SIP-only, 2026-10-13) |
| Open | 3 | F10, F17, F19 |

Counts to 21 because F13 and F14 were closed on 2026-10-13 and are listed once.

**Why the count was so misleading.** This plan carried 14 open headings into 2026-10-28 and led the
index's "where the remaining work is concentrated" section on that basis. Reading the findings
rather than the headings dissolved most of it: the plan is largely an enumeration of
`account_const.h` against the settings screens, and that method cannot tell a Jami gap from a SIP
property, a real omission from a deliberate parity choice, or a live claim from one the code has
since overtaken.

**Four premises had gone stale,** which is why each closure was checked against source first:

- **F21** rests on "users can enter invalid paths". They cannot — the three certificate fields are
  `CertFileRow` + `FilePickerEffect`, not text inputs. This is the one real difference from F6, the
  port validation that *was* implemented here because those fields genuinely did pass raw strings
  to the daemon.
- **F8, F9, F16** each claim the property has "no ConfigKey mapping". All three exist
  (`ConfigKey.kt:41`, `:26`, `:87`); only a UI control is missing, so had they been adopted the job
  was smaller than written.
- **F4 and F18** contain their own disproof. F4's Impact reads "None observed; this is correctly
  implemented"; F18's reads "No functional gap… Parity maintained via different UX". Both had been
  counted as open work since the audit.
- **F16's title** promises trickle-ICE and ICE-restart settings its own evidence then concedes the
  daemon does not expose.

**The SIP cluster is one feature, not five findings.** F1, F8, F12, F15, F20 and
`contacts-presence-accounts.md` F7 are all pieces of the same unbuilt SIP account screen. If SIP
editing is ever wanted, plan them together.

**Two of the closed toggles weaken security** and should not be surfaced casually if reopened:
`SRTP.enable`/`rtpFallback` (F2) can put call media in the clear, and `TLS.disableSecureDlgCheck`
(F12) drops a TLS dialog requirement. F7's treatment of the TLS verification toggles — expose with
an explicit warning — is the precedent to follow.

## Earlier status (2026-10-12)

**Phase 1 plus F6 done** (`b53d3ec`) — the three items this plan calls medium risk. No new
settings yet; this was the hygiene pass over the settings that already exist.

- **F6 (port validation) — done, and the finding understated it.** The four RTP port fields did
  not merely lack validation: each setter called `updateDetail` with the raw string, so "99999",
  "80" and "abc" were all sent to the daemon, which rejects or clamps them silently. Ports are
  now checked (1024–65535, min ≤ max, non-numeric rejected) and only valid values are written;
  the typed text stays in state so the field does not fight the keyboard, and the reason shows as
  supporting text. An empty field is not an error — the user may be mid-edit, and the daemon keeps
  its previous value.
- **F7 (TLS verification warnings) — done.** The toggles had no supporting text at all, not just
  no warning. They now describe what they do, and turning server or client verification off
  shows an inline warning in error colour.
- **F5 (TURN password) — done as hygiene; the proposed fix is not achievable.** The password was
  read into Compose state on load purely to render it as dots, and the field is now write-only
  (state holds only what the user types, a placeholder says "Unchanged" when one exists, and the
  typed value is dropped with the screen).
  **Caveat, stated plainly:** the plan proposes a `SecureString`/KeyStore wrapper and a test that
  verifies "no plaintext password in memory dumps". Neither is reachable. The daemon keeps this
  password in its own configuration and it has to cross the JNI boundary in plaintext to get
  there, so the app cannot be the thing that protects it; what was removed is an unnecessary
  second copy. Anything stronger needs the daemon to hold a reference rather than the secret.

Incidental finding while testing: `AccountService` seeds Jami's public TURN credentials
(`turn.jami.net` / `ring` / `ring`) onto any account that has none, so a freshly loaded account
*does* have a TURN password set. Worth knowing before reasoning about these fields.

**Still open:** the 18 settings-breadth findings (F1–F4, F8–F21). Phase 2 is the highest-value
block of those (SIP account editing, published address/port, interface binding, DHT proxy port),
but note that most of this plan is SIP-specific, which sits against the swarm-first, mobile-first
priority in CLAUDE.md — worth deciding scope before working through it.

---

## Status (2026-10-13): F11 done; F3/F13/F14 verified and closed, and the "non-SIP" grouping was wrong

**F11 (relaxed peer-certificate trust) — done** with the certificate-management slice; see the
finding.

**F3, F13 and F14 were picked as the three items of this plan that affect Jami accounts rather
than SIP. Checked against the daemon source, that grouping does not hold** — which is why they
are closed with evidence instead of implemented:

- **F3 is invalid.** `Account.dhtProxyServerPort` is the port used when this device *hosts* a DHT
  proxy, not the port of the proxy it connects to; and `Account.proxyServer` is meant to carry a
  port, its default being the range `dhtproxy.jami.net:[80-95]`.
- **F13 does not apply to a Jami account.** `publishedPort` is SIP-only; `publishedAddress` is
  stored but never read back, because `JamiAccount` overrides the getter to return the address the
  daemon discovered. The field would be a setting that lies.
- **F14 is two-thirds SIP-only** (`bindAddress`, `localPort`). `localInterface` is genuinely used
  by `JamiAccount` for the SDP address of an outgoing call, so it stays open — but it is a
  multi-homed-host setting that needs interface enumeration on five targets and only pays off on
  desktop.

The reference client corroborates all three: `AdvancedAccountFragment.kt` explicitly sets
`isVisible = !isJamiAccount` on PUBLISHED_ADDRESS, PUBLISHED_PORT, PUBLISHED_SAMEAS_LOCAL,
LOCAL_PORT and LOCAL_INTERFACE.

**Revised view of what is left here:** with F5/F6/F7/F11 done, this plan has no remaining items
that change behaviour for a Jami account on mobile. The other 17 are SIP surface (F1, F8, F15,
F16, F20), hosting/diagnostic keys that do not apply (F3), or small settings-breadth additions
whose value depends on SIP support landing first. The plan is best treated as blocked on the
decision to support SIP accounts properly, rather than worked through finding by finding.

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
