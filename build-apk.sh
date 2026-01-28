#!/bin/bash
#
# Build IOCast APK using Docker
# Uses mingc/android-build-box which includes Android SDK + Gradle
# Reference: https://hub.docker.com/r/mingc/android-build-box
#

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

echo "🔧 Building IOCast APK with Docker..."
echo ""

# Build type: debug or release
BUILD_TYPE="${1:-debug}"

if [ "$BUILD_TYPE" == "release" ]; then
    GRADLE_TASK="assembleRelease"
    APK_PATH="app/build/outputs/apk/release/app-release-unsigned.apk"
else
    GRADLE_TASK="assembleDebug"
    APK_PATH="app/build/outputs/apk/debug/app-debug.apk"
fi

echo "📦 Build type: $BUILD_TYPE"
echo "📂 Project dir: $SCRIPT_DIR"
echo ""

# Use platform flag for ARM Macs running x86 images
PLATFORM_FLAG=""
if [[ "$(uname -m)" == "arm64" ]]; then
    PLATFORM_FLAG="--platform linux/amd64"
    echo "🔄 ARM64 detected, using x86 emulation (Rosetta)..."
    echo ""
fi

# Run Docker build using mingc/android-build-box
# This image has Gradle pre-installed, so we use 'gradle' directly instead of wrapper
docker run --rm $PLATFORM_FLAG \
    -v "$SCRIPT_DIR":/project \
    -w /project \
    -e GRADLE_OPTS="-Xmx3072m -XX:MaxMetaspaceSize=756m" \
    mingc/android-build-box:latest \
    bash -c "
        echo '📋 Accepting Android SDK licenses...'
        yes | sdkmanager --licenses 2>/dev/null || true

        echo '🔨 Running Gradle build (using container Gradle)...'
        gradle $GRADLE_TASK --no-daemon --stacktrace
    "

# Check if APK was created
if [ -f "$APK_PATH" ]; then
    APK_SIZE=$(du -h "$APK_PATH" | cut -f1)
    echo ""
    echo "✅ APK bygget succesfuldt!"
    echo "📱 APK: $APK_PATH"
    echo "📊 Størrelse: $APK_SIZE"
    echo ""

    # Copy to output directory
    mkdir -p output
    cp "$APK_PATH" "output/iocast-$BUILD_TYPE.apk"
    echo "📁 Kopieret til: output/iocast-$BUILD_TYPE.apk"
else
    echo ""
    echo "❌ Fejl: APK blev ikke oprettet"
    echo "   Forventet sti: $APK_PATH"
    exit 1
fi
