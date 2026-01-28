#!/bin/bash
#
# Start Android Emulator og test IOCast APK
# Kører native ARM emulator på Mac M1/M2/M3
#

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
APK_PATH="$SCRIPT_DIR/output/iocast-debug.apk"
PACKAGE_NAME="dk.iocast.kiosk"
AVD_NAME="iocast-test"

# Android SDK paths
export ANDROID_HOME="/opt/homebrew/share/android-commandlinetools"
export PATH="$ANDROID_HOME/emulator:$ANDROID_HOME/platform-tools:$PATH"

# Farver
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m'

echo -e "${BLUE}🤖 IOCast Emulator Test${NC}"
echo ""

# Check if APK exists
if [ ! -f "$APK_PATH" ]; then
    echo -e "${RED}❌ APK ikke fundet: $APK_PATH${NC}"
    echo "Byg først med: docker-compose run --rm build"
    exit 1
fi

# Check if emulator is already running
if adb devices | grep -q "emulator"; then
    echo -e "${GREEN}✅ Emulator kører allerede${NC}"
else
    echo -e "${YELLOW}🚀 Starter emulator...${NC}"
    echo "   (Dette kan tage 1-2 minutter første gang)"

    # Start emulator in background
    nohup emulator -avd "$AVD_NAME" -no-audio -no-boot-anim -gpu host > /tmp/emulator.log 2>&1 &
    EMULATOR_PID=$!
    echo "   Emulator PID: $EMULATOR_PID"

    # Wait for emulator to boot
    echo -e "${YELLOW}⏳ Venter på emulator boot...${NC}"

    MAX_WAIT=120
    WAITED=0
    while [ $WAITED -lt $MAX_WAIT ]; do
        if adb shell getprop sys.boot_completed 2>/dev/null | grep -q "1"; then
            echo -e "${GREEN}✅ Emulator klar!${NC}"
            break
        fi
        sleep 2
        WAITED=$((WAITED + 2))
        echo -n "."
    done
    echo ""

    if [ $WAITED -ge $MAX_WAIT ]; then
        echo -e "${RED}❌ Timeout - emulator startede ikke${NC}"
        exit 1
    fi
fi

# Wait a bit more for system to stabilize
sleep 3

# Get device info
echo ""
DEVICE_MODEL=$(adb shell getprop ro.product.model 2>/dev/null | tr -d '\r')
ANDROID_VERSION=$(adb shell getprop ro.build.version.release 2>/dev/null | tr -d '\r')
echo -e "${BLUE}📱 Enhed: $DEVICE_MODEL (Android $ANDROID_VERSION)${NC}"

# Uninstall existing app
echo -e "${YELLOW}🗑️  Fjerner eksisterende installation...${NC}"
adb uninstall $PACKAGE_NAME 2>/dev/null || true

# Install APK
echo -e "${YELLOW}📦 Installerer APK...${NC}"
adb install -r "$APK_PATH"
echo -e "${GREEN}✅ APK installeret!${NC}"

# Grant permissions
echo -e "${YELLOW}🔐 Giver permissions...${NC}"
adb shell pm grant $PACKAGE_NAME android.permission.POST_NOTIFICATIONS 2>/dev/null || true
adb shell pm grant $PACKAGE_NAME android.permission.CAMERA 2>/dev/null || true

# Start app
echo -e "${YELLOW}🚀 Starter app...${NC}"
adb shell am start -n "$PACKAGE_NAME/.SetupActivity"
echo -e "${GREEN}✅ App startet!${NC}"
echo ""

# Show options
echo -e "${BLUE}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
echo -e "${BLUE}📋 Test Commands:${NC}"
echo ""
echo "  Vis logcat:    adb logcat -s 'IOCast:*' 'MqttService:*'"
echo "  Tag screenshot: adb exec-out screencap -p > screenshot.png"
echo "  Stop emulator:  adb emu kill"
echo "  ADB shell:      adb shell"
echo ""
echo -e "${BLUE}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"

# Ask if user wants to see logcat
read -p "Vis logcat output? (y/n) " -n 1 -r
echo
if [[ $REPLY =~ ^[Yy]$ ]]; then
    echo -e "${BLUE}📋 Logcat (Ctrl+C for at stoppe):${NC}"
    adb logcat -s "IOCast:*" "MqttService:*" "CommandHandler:*" "MainActivity:*" "SetupActivity:*"
fi
