#!/usr/bin/env bash
# Build, archive, and export a signed iOS IPA for Jami KMP.
#
# Usage:
#   ./scripts/build-ios-ipa.sh [--skip-kmp] [--output DIR]
#
#   --skip-kmp   Skip the Kotlin/Native framework build (use when only
#                Swift/plist/asset changes were made).
#   --output DIR Export the IPA to DIR instead of the default ./build/ios-export.
#
# After the IPA is produced, upload it to TestFlight via:
#   Xcode → Window → Organizer → Distribute App → App Store Connect → Upload
# or via altool/notarytool if your API key is configured.

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT="$SCRIPT_DIR/.."

# ── Config ────────────────────────────────────────────────────────────────────
XCPROJECT="$ROOT/ios-app/iosApp.xcodeproj"
SCHEME="iosApp"
CONFIGURATION="Release"
ARCHIVE_PATH="/tmp/JamiKMP.xcarchive"
EXPORT_OPTIONS_PLIST="$ROOT/ios-app/ExportOptions.plist"

TEAM_ID="K4K982LMZ9"
PROVISIONING_PROFILE="Jami-KMP-test"

OUTPUT_DIR="$ROOT/build/ios-export"
SKIP_KMP=false

# ── Arg parsing ───────────────────────────────────────────────────────────────
while [[ $# -gt 0 ]]; do
    case "$1" in
        --skip-kmp)  SKIP_KMP=true; shift ;;
        --output)    OUTPUT_DIR="$2"; shift 2 ;;
        *)           echo "Unknown option: $1"; exit 1 ;;
    esac
done

# ── Helpers ───────────────────────────────────────────────────────────────────
step() { echo; echo "▶  $*"; }
ok()   { echo "✓  $*"; }

# ── Step 1: Kotlin/Native framework ──────────────────────────────────────────
if [ "$SKIP_KMP" = false ]; then
    step "Building KMP release framework (iosArm64)…"
    cd "$ROOT"
    ./gradlew :shared:linkReleaseFrameworkIosArm64
    ok "KMP framework built"
else
    ok "Skipping KMP framework build (--skip-kmp)"
fi

# ── Step 2: Xcode archive ─────────────────────────────────────────────────────
step "Archiving Xcode project…"
rm -rf "$ARCHIVE_PATH"

xcodebuild archive \
    -project "$XCPROJECT" \
    -scheme "$SCHEME" \
    -configuration "$CONFIGURATION" \
    -destination "generic/platform=iOS" \
    -archivePath "$ARCHIVE_PATH" \
    | grep -E "error:|warning: Run script|ARCHIVE (SUCCEEDED|FAILED)|Compile Kotlin" \
    || true

if [ ! -d "$ARCHIVE_PATH" ]; then
    echo "✗  Archive failed — no .xcarchive produced" >&2
    exit 1
fi
ok "Archive succeeded: $ARCHIVE_PATH"

# ── Step 3: Export IPA ───────────────────────────────────────────────────────
step "Exporting IPA…"
rm -rf "$OUTPUT_DIR"
mkdir -p "$(dirname "$OUTPUT_DIR")"

xcodebuild -exportArchive \
    -archivePath "$ARCHIVE_PATH" \
    -exportPath "$OUTPUT_DIR" \
    -exportOptionsPlist "$EXPORT_OPTIONS_PLIST" \
    | grep -E "error:|EXPORT (SUCCEEDED|FAILED)" \
    || true

IPA_PATH=$(find "$OUTPUT_DIR" -name "*.ipa" | head -1)
if [ -z "$IPA_PATH" ]; then
    echo "✗  Export failed — no .ipa produced" >&2
    exit 1
fi

ok "IPA ready: $IPA_PATH"
echo
echo "Upload to TestFlight:"
echo "  Xcode → Window → Organizer → Distribute App → App Store Connect → Upload"
echo "  or: xcrun altool --upload-app --type ios --file \"$IPA_PATH\" \\"
echo "        --apiKey <KEY_ID> --apiIssuer <ISSUER_UUID>"
