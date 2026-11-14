#!/usr/bin/env bash
# Two jami-kmp iOS clients on two simulators, talking over the real Jami network.
# See README.md next to this file.
#
#   scripts/ios-two-sim/run.sh [--build] [--keep] [--only PHASE[,PHASE…]] [--from PHASE]
#
# Phases run in order and stop at the first failure. The simulators are dedicated
# (JamiTest-A / JamiTest-B, created on first use) and are ERASED at the start of a run.
set -euo pipefail

HERE="$(cd "$(dirname "$0")" && pwd)"
REPO="$(cd "$HERE/../.." && pwd)"
FLOWS="$HERE/flows"
OUT="${OUT:-$REPO/build/ios-two-sim}"
APP_ID=net.jami.iosapp
SIM_A_NAME=JamiTest-A
SIM_B_NAME=JamiTest-B
DEVICE_TYPE="${DEVICE_TYPE:-iPhone 17}"
DERIVED="$OUT/DerivedData"
APP="${APP:-$DERIVED/Build/Products/Debug-iphonesimulator/iosApp.app}"
# Maestro installs an XCTest driver per simulator; on a freshly erased simulator under the
# memory pressure of two booted devices (8 GB Mac) the 15 s default is not enough.
export MAESTRO_DRIVER_STARTUP_TIMEOUT="${MAESTRO_DRIVER_STARTUP_TIMEOUT:-240000}"

PHASES=(setup onboard invite accept chat edit react delete media offline group)
BUILD=0 KEEP=0 ONLY="" FROM=""
while [ $# -gt 0 ]; do
    case "$1" in
        --build) BUILD=1 ;;
        --keep) KEEP=1 ;;               # leave the simulators running afterwards
        --only) ONLY="$2"; shift ;;
        --from) FROM="$2"; shift ;;
        -h|--help) sed -n '2,9p' "$0"; exit 0 ;;
        *) echo "unknown argument: $1" >&2; exit 2 ;;
    esac
    shift
done

mkdir -p "$OUT"
STATE="$OUT/state.env"   # Jami IDs etc., so --from / --only can resume a run
[ -f "$STATE" ] && . "$STATE"
log() { printf '\033[1m[%s] %s\033[0m\n' "$(date +%H:%M:%S)" "$*"; }
save() { grep -v "^$1=" "$STATE" 2>/dev/null > "$STATE.tmp" || true; echo "$1=$2" >> "$STATE.tmp"; mv "$STATE.tmp" "$STATE"; eval "$1=\$2"; }

udid_of() {
    xcrun simctl list devices -j | jq -r --arg n "$1" \
        '[.devices[][] | select(.name == $n and .isAvailable)][0].udid // empty'
}
ensure_sim() {
    local id; id=$(udid_of "$1")
    [ -n "$id" ] || id=$(xcrun simctl create "$1" "$DEVICE_TYPE")
    echo "$id"
}
A=$(ensure_sim "$SIM_A_NAME")
B=$(ensure_sim "$SIM_B_NAME")

shot() { xcrun simctl io "$1" screenshot "$OUT/$2.png" >/dev/null 2>&1 || true; }

# maestro <sim> <flow> [KEY=VALUE…] — on failure keeps both screens and Maestro's output.
flow() {
    local sim="$1" name="$2"; shift 2
    local args=() kv
    for kv in "$@"; do args+=(-e "$kv"); done
    if ! maestro --device "$sim" test ${args[@]+"${args[@]}"} "$FLOWS/$name.yaml" > "$OUT/maestro-$name.log" 2>&1; then
        shot "$A" "FAIL-$name-A"; shot "$B" "FAIL-$name-B"
        grep -E 'FAILED|Element not found|Assertion' "$OUT/maestro-$name.log" | head -5 >&2 || true
        echo "FAIL: flow $name on $([ "$sim" = "$A" ] && echo A || echo B) — see $OUT/maestro-$name.log and FAIL-$name-*.png" >&2
        exit 1
    fi
}

jami_id() {
    local data; data=$(xcrun simctl get_app_container "$1" "$APP_ID" data)
    sed -nE 's/^username: *([0-9a-f]{40})$/\1/p' "$data/Library/Application Support/jami/"*/config.yml | head -1
}

# ── phases ────────────────────────────────────────────────────────────────────

phase_setup() {
    if [ "$BUILD" = 1 ] || [ ! -d "$APP" ]; then
        log "building the simulator app"
        xcodebuild -project "$REPO/ios-app/iosApp.xcodeproj" -scheme iosApp -configuration Debug \
            -destination "platform=iOS Simulator,id=$A" -derivedDataPath "$DERIVED" build \
            > "$OUT/xcodebuild.log" 2>&1 || { tail -20 "$OUT/xcodebuild.log"; exit 1; }
    fi
    : > "$STATE"
    for sim in "$A" "$B"; do
        log "resetting simulator $sim"
        xcrun simctl shutdown "$sim" 2>/dev/null || true
        xcrun simctl erase "$sim"
        xcrun simctl boot "$sim"
        xcrun simctl bootstatus "$sim" >/dev/null
        # English UI so the flows' labels match; read by the app at launch.
        xcrun simctl spawn "$sim" defaults write -g AppleLanguages -array en
        xcrun simctl spawn "$sim" defaults write -g AppleLocale -string en_US
        xcrun simctl addmedia "$sim" "$REPO/e2e-runner/src/main/resources/fixture-avatar.png"
    done
    open -a Simulator
}

# A fresh install on both, so this phase can be rerun on its own (--from onboard).
phase_onboard() {
    for pair in "$A:Alice" "$B:Bob"; do
        local sim="${pair%%:*}" name="${pair#*:}"
        log "onboarding $name"
        xcrun simctl uninstall "$sim" "$APP_ID" 2>/dev/null || true
        # Right after a reset, install sometimes fails with IXErrorDomain 2 ("Failed to set
        # metadata"); it succeeds a few seconds later.
        local try
        for try in 1 2 3; do
            xcrun simctl install "$sim" "$APP" && break
            [ "$try" = 3 ] && exit 1
            sleep 5
        done
        xcrun simctl privacy "$sim" grant photos "$APP_ID"
        xcrun simctl launch "$sim" "$APP_ID" >/dev/null
        flow "$sim" onboard NAME="$name"
    done
    save ID_A "$(jami_id "$A")"
    save ID_B "$(jami_id "$B")"
    [ -n "$ID_A" ] && [ -n "$ID_B" ] || { echo "FAIL: could not read the Jami IDs" >&2; exit 1; }
    log "Alice = $ID_A"
    log "Bob   = $ID_B"
}

# Alice finds Bob by ID and opens the conversation → invitation sent.
phase_invite() { flow "$A" invite PEER_ID="$ID_B"; }

# Bob sees exactly "1 pending invitation" and accepts → both are swarm members.
phase_accept() { flow "$B" accept; }

# One message each way.
phase_chat() {
    flow "$A" send TEXT="Hello from Alice"
    flow "$B" expect TEXT="Hello from Alice"
    flow "$B" send TEXT="Reply from Bob"
    flow "$A" expect TEXT="Reply from Bob"
}

# Alice edits her message; Bob sees the new text and loses the old one.
phase_edit() {
    flow "$A" edit OLD="Hello from Alice" NEW="Hello from Alice, edited"
    flow "$B" expect TEXT="Hello from Alice, edited"
    flow "$B" expect-gone TEXT="Hello from Alice"
}

# Bob reacts to Alice's message; Alice sees the reaction.
phase_react() {
    flow "$B" react TEXT="Hello from Alice, edited"
    flow "$A" expect TEXT=".*👍.*"
}

# Alice sends a throwaway message, Bob receives it, Alice deletes it, it disappears for Bob.
phase_delete() {
    flow "$A" send TEXT="Delete me"
    flow "$B" expect TEXT="Delete me"
    flow "$A" delete TEXT="Delete me"
    flow "$B" expect-gone TEXT="Delete me"
}

# Bob's app is killed (daemon gone with it); Alice sends; Bob relaunches and gets it.
phase_offline() {
    xcrun simctl terminate "$B" "$APP_ID" 2>/dev/null || true
    flow "$A" send TEXT="Sent while Bob was away"
    sleep 5
    xcrun simctl launch "$B" "$APP_ID" >/dev/null
    flow "$B" open PEER_NAME=Alice
    flow "$B" expect TEXT="Sent while Bob was away"
}

# ── driver ────────────────────────────────────────────────────────────────────

run=0
[ -z "$FROM" ] && [ -z "$ONLY" ] && run=1
for p in "${PHASES[@]}"; do
    [ "$p" = "$FROM" ] && run=1
    if [ -n "$ONLY" ]; then
        case ",$ONLY," in *",$p,"*) ;; *) continue ;; esac
    elif [ "$run" = 0 ]; then
        continue
    fi
    if ! declare -F "phase_$p" >/dev/null; then log "phase $p: not implemented yet, skipped"; continue; fi
    log "── $p"
    "phase_$p"
    log "PASS $p"
done

if [ "$KEEP" = 0 ]; then
    for s in "$A" "$B"; do xcrun simctl shutdown "$s" 2>/dev/null || true; done
fi
log "done — artefacts in $OUT"
