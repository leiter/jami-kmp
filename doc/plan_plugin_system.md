# Plan — port the Jami plugin system (daemon-audit F3)

**Status:** not started. Written 2026-10-28, when `daemon-api-surface-audit.md` F3 became the
largest remaining item in the mini-plans after the contacts and security plans were worked down.

The audit entry is three lines long and says "Plugin System Not Ported — Major Gap, 19 daemon
functions, untouched". That is accurate but not plannable, so this file establishes what the surface
actually is, what the reference client built on it, and what the open questions are. It is a plan
for one feature, not a finding.

---

## Why this is the next big thing, and the one caveat

Every other open finding is a settings row or a small capability. This is a subsystem: an install
format, a file-management story, per-account and per-conversation state, and two different kinds of
handler. `CLAUDE.md` lists it under Known Gaps as "Chat plugins — Jami plugin system not ported to
KMP. Menu item shows a 'not yet supported' snackbar", which is today's entire implementation
(`ChatScreen.kt:836`).

**The caveat to settle before any code:** this is a desktop-first feature in the reference clients,
and nothing has established that users want it on mobile. Plugins are `.jpl` bundles the user
obtains and installs by hand; on Android that means a file picker and a copy into app storage, and
on iOS it means the Files app and an app-group container. A deliberate decision that mobile wants
this at all should precede the work — otherwise it is a large build for a feature the primary
platforms may not surface. Raise it before Phase 1.

---

## The actual API surface

The audit's "19 functions" undercounts, and the functions are not all the same kind of thing. All of
the following are present in the SWIG bindings (`shared/src/androidMain/java/net/jami/daemon/JamiService.java`)
and **none** is declared in `DaemonBridgeApi` — a grep for "plugin" in `DaemonBridge.kt` returns
zero. So every one of them needs a bridge method plus six platform actuals.

**Plugin lifecycle (8).** `installPlugin` → Int, `uninstallPlugin` → Int, `loadPlugin` → Boolean,
`unloadPlugin` → Boolean, `getInstalledPlugins` → StringVect, `getLoadedPlugins` → StringVect,
`getPluginsEnabled` → Boolean, `setPluginsEnabled`.

**Plugin preferences (4).** `getPluginDetails` → StringMap, `getPluginPreferences` → VectMap,
`getPluginPreferencesValues` → StringMap, `setPluginPreference` → Boolean,
`resetPluginPreferencesValues` → Boolean. (Five, counting the reset.)

**Handlers — the part the audit misses entirely (8).** `getChatHandlers`, `getChatHandlerDetails`,
`getChatHandlerStatus`, `toggleChatHandler`, and the four `CallMediaHandler` equivalents. These are
what a loaded plugin actually *exposes*, and they are per-account and per-conversation, not global.
The reference client's per-conversation UI (`PluginHandlersListFragment.kt:68`) calls
`toggleChatHandler(handlerId, accountId, conversationId.removePrefix("swarm:"), enabled)` — note the
`swarm:` prefix strip, which is the kind of detail that will not be guessed.

**Audio plugins are a different thing with a colliding name.** `getAudioPluginList`,
`setAudioPlugin` and `getCurrentAudioOutputPlugin` are ALSA/PulseAudio device plumbing, nothing to
do with `.jpl` extensions. They belong with audio device selection, which
`daemon-api-surface-audit.md` F1 already closed as not applicable to mobile. **Do not pull them into
this work** on the strength of the shared word "plugin".

---

## What the reference client built

The reference calls them **extensions**, not plugins, and splits the UI three ways
(`jami-client-android/jami-android/app/src/main/java/cx/ring/`):

| Reference file | Role |
|---|---|
| `settings/extensionssettings/ExtensionListSettingsFragment.kt` | the installed-extension list, global on/off |
| `settings/extensionssettings/ExtensionSettingsFragment.kt` | one extension's preferences |
| `extensions/ExtensionUtils.kt` | reads `getInstalledPlugins()` and maps paths to details |
| `fragments/PluginHandlersListFragment.kt` | per-conversation chat-handler toggles |
| `settings/SettingsFragment.kt` | the global "enable extensions" switch |

Naming decision to make early: follow the reference's user-facing "extensions" wording, or the
daemon's "plugin"? The daemon API says plugin, the reference UI says extension. Picking one and
documenting it in `CLAUDE.md` avoids a half-and-half vocabulary. Recommendation: **"extensions" in
UI strings, "plugin" in code that touches the daemon**, matching the reference.

---

## Phases

### Phase 1 — bridge and service layer, no UI

Add all 20 declarations to `DaemonBridgeApi` and implement the Android and iOS actuals; macOS,
desktop and JS take the usual stubs. Then a `PluginService` in `commonMain` holding the installed
list, loaded set, the global enabled flag, and a `StateFlow` the UI can bind to, in the shape of the
existing services.

Two things to verify rather than assume:

- **`installPlugin`/`uninstallPlugin` return `Int`, not `Boolean`.** Find the daemon's meaning for
  the non-zero values before mapping them to a UI message. Guessing a result code is exactly what
  made `reference_name_registration_codes` worth writing down.
- **Apple wrapper coverage.** `JamiBridgeWrapper.h` is the gate for iOS and macOS, and it has
  already blocked `connectivityChanged` and `setDefaultModerator`. Check whether the plugin and
  handler entry points are in the rebuilt 1096-method wrapper **before** planning the iOS half; if
  they are missing, that is a Mac-only `build-jamibridge.sh` rebuild that overwrites tracked
  binaries, and it should be batched with the other missing passthroughs rather than done alone.

### Phase 2 — install and file management

`installPlugin` takes a path to a `.jpl` on local storage, so this phase is mostly platform file
work, not daemon work: a picker, a copy into app-private storage, and `getInstalledPlugins()`
returning paths that must stay valid across app updates. `FilePickerEffect` already exists and is
used by the TLS certificate rows, so there is a pattern to follow.

Decide here what happens on uninstall of a plugin whose files the user has since deleted, and
whether an install is allowed while a call is active.

### Phase 3 — settings UI

An extensions list screen and a per-extension preferences screen, plus the global toggle. The
preferences are daemon-described (`getPluginPreferences` returns a `VectMap` of
key/type/label/value), so the screen is a **dynamic form built from daemon metadata**, not a
hand-written settings page. That is the one genuinely novel piece of UI in this plan: everything
else in the app renders a known, typed state. Expect the preference types to be undocumented and to
need reading off the reference's renderer.

### Phase 4 — handlers

The global list is useless on its own: a plugin does nothing until a handler is toggled on for a
conversation. Wire `getChatHandlers`/`toggleChatHandler` per conversation, replacing the
`ChatScreen.kt:836` snackbar, and `getCallMediaHandlers`/`toggleCallMediaHandler` for calls. Keep the
`swarm:` prefix strip the reference does.

### Phase 5 — iOS

Treat as a separate pass, not an afterthought. Everything written on a Linux host for Apple targets
in this project has needed fixing on first Mac build — push notifications took five fixes, the
certificate slice yielded five bugs on its first simulator run. The file-management half (Phase 2)
is where iOS will diverge most, because there is no equivalent of an Android file picker writing
into app-private storage.

---

## What this plan deliberately does not do

- It does not touch the audio-plugin functions. See above.
- It does not assume the audit's function count or categories. They were read off the interface
  headers, and `doc/mini-plans/README.md` carries a standing caveat that the header set is not the
  binding set — in this case the bindings turned out to be *more* complete than the audit implied
  (the handler API is there and unmentioned), which is the opposite of the usual direction.
- It sets no estimate. Phase 3's dynamic preference form and Phase 2's per-platform file story are
  both unknown-shaped until the daemon metadata is inspected, and a number invented now would only
  be quoted back later.
