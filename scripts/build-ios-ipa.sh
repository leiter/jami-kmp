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
ARCHIVE_PATH="$HOME/Library/Developer/Xcode/Archives/$(date +%Y-%m-%d)/JamiKMP $(date '+%d-%m-%Y, %H.%M').xcarchive"
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

# ── Step 1: Clean KMP outputs ────────────────────────────────────────────────
# Always clean before archiving so Gradle's incremental cache cannot silently
# embed a stale JamiShared.framework built from an older commit.
# (embedAndSignAppleFrameworkForXcode, invoked by the Xcode build phase, handles
# the actual compile+link; we just ensure it starts from a clean slate.)
if [ "$SKIP_KMP" = false ]; then
    step "Cleaning shared module build outputs…"
    cd "$ROOT"
    ./gradlew :shared:clean
    ok "Shared module cleaned — xcodebuild will trigger a full recompile"
else
    ok "Skipping KMP clean (--skip-kmp)"
fi

# ── Step 2: Xcode archive ─────────────────────────────────────────────────────
step "Archiving Xcode project…"
mkdir -p "$(dirname "$ARCHIVE_PATH")"
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
