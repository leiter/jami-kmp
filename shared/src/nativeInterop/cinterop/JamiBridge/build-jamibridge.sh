#!/bin/bash
#
# Build JamiBridge static library for iOS/macOS
#
# This script compiles JamiBridgeWrapper.mm into a static library
# that can be linked with Kotlin/Native via cinterop.
#
# Prerequisites:
# - libjami.a in ../lib/
# - libjami headers in ../headers/
# - Xcode Command Line Tools installed
#

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
CINTEROP_DIR="$(dirname "$SCRIPT_DIR")"

# Configuration
HEADERS_DIR="$CINTEROP_DIR/headers"
LIB_DIR="$CINTEROP_DIR/lib"
OUTPUT_DIR="$LIB_DIR"

# Build settings
CXX_FLAGS="-std=c++17 -fobjc-arc -fmodules -DNDEBUG -O2"
INCLUDE_FLAGS="-I$HEADERS_DIR -I$SCRIPT_DIR"

echo "=== Building JamiBridge Static Library ==="
echo "Headers: $HEADERS_DIR"
echo "Output:  $OUTPUT_DIR"
echo ""

# Check prerequisites
if [ ! -f "$LIB_DIR/libjami.a" ]; then
    echo "Error: libjami.a not found in $LIB_DIR"
    echo "Please copy libjami.a from gettogether or build it from jami-daemon"
    exit 1
fi

if [ ! -f "$HEADERS_DIR/jami.h" ]; then
    echo "Error: jami.h not found in $HEADERS_DIR"
    echo "Please copy libjami headers from gettogether or jami-daemon"
    exit 1
fi

# Detect architecture
ARCH=$(uname -m)
if [ "$ARCH" = "arm64" ]; then
    TARGET="arm64-apple-macos11.0"
    IOS_TARGET="arm64-apple-ios14.0"
else
    TARGET="x86_64-apple-macos11.0"
    IOS_TARGET="x86_64-apple-ios14.0-simulator"
fi

echo "Building for architecture: $ARCH"
echo ""

# Create output directory
mkdir -p "$OUTPUT_DIR"

# Build for macOS
echo "=== Compiling for macOS ($TARGET) ==="
clang++ -c "$SCRIPT_DIR/JamiBridgeWrapper.mm" \
    -o "$OUTPUT_DIR/JamiBridgeWrapper_macos.o" \
    $CXX_FLAGS \
    $INCLUDE_FLAGS \
    -target "$TARGET"

echo "Creating libJamiBridge_macos.a..."
ar rcs "$OUTPUT_DIR/libJamiBridge_macos.a" "$OUTPUT_DIR/JamiBridgeWrapper_macos.o"

# Build for iOS (if on arm64 Mac)
if [ "$ARCH" = "arm64" ]; then
    # Get SDK paths
    IOS_SDK=$(xcrun --sdk iphoneos --show-sdk-path)
    IOS_SIM_SDK=$(xcrun --sdk iphonesimulator --show-sdk-path)

    echo ""
    echo "=== Compiling for iOS ($IOS_TARGET) ==="
    echo "Using SDK: $IOS_SDK"
    clang++ -c "$SCRIPT_DIR/JamiBridgeWrapper.mm" \
        -o "$OUTPUT_DIR/JamiBridgeWrapper_ios.o" \
        $CXX_FLAGS \
        $INCLUDE_FLAGS \
        -target "$IOS_TARGET" \
        -isysroot "$IOS_SDK"

    echo "Creating libJamiBridge_ios.a..."
    ar rcs "$OUTPUT_DIR/libJamiBridge_ios.a" "$OUTPUT_DIR/JamiBridgeWrapper_ios.o"

    # Also create iOS simulator build
    echo ""
    echo "=== Compiling for iOS Simulator ==="
    echo "Using SDK: $IOS_SIM_SDK"
    clang++ -c "$SCRIPT_DIR/JamiBridgeWrapper.mm" \
        -o "$OUTPUT_DIR/JamiBridgeWrapper_iossim.o" \
        $CXX_FLAGS \
        $INCLUDE_FLAGS \
        -target "arm64-apple-ios14.0-simulator" \
        -isysroot "$IOS_SIM_SDK"

    echo "Creating libJamiBridge_iossim.a..."
    ar rcs "$OUTPUT_DIR/libJamiBridge_iossim.a" "$OUTPUT_DIR/JamiBridgeWrapper_iossim.o"
fi

# Cleanup object files
rm -f "$OUTPUT_DIR"/*.o

echo ""
echo "=== Build Complete ==="
echo ""
echo "Created libraries:"
ls -lh "$OUTPUT_DIR"/libJamiBridge*.a
echo ""
echo "Next steps:"
echo "1. Edit shared/build.gradle.kts"
echo "2. Set enableJamiBridgeCinterop = true"
echo "3. Rebuild the project"
