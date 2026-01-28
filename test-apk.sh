#!/bin/bash
#
# Test IOCast APK på tilsluttet Android device
# Kræver: adb (Android Debug Bridge) installeret
#
# Installation af adb på Mac:
#   brew install android-platform-tools
#
# Eller download fra:
#   https://developer.android.com/tools/releases/platform-tools
#

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
APK_PATH="$SCRIPT_DIR/output/iocast-debug.apk"
PACKAGE_NAME="dk.iocast.kiosk"

# Farver
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

echo -e "${BLUE}🔧 IOCast APK Test Script${NC}"
echo ""

# Check if adb is installed
if ! command -v adb &> /dev/null; then
    echo -e "${RED}❌ adb ikke fundet!${NC}"
    echo ""
    echo "Installer med Homebrew:"
    echo "  brew install android-platform-tools"
    echo ""
    echo "Eller download fra:"
    echo "  https://developer.android.com/tools/releases/platform-tools"
    exit 1
fi

# Check if APK exists
if [ ! -f "$APK_PATH" ]; then
    echo -e "${RED}❌ APK ikke fundet: $APK_PATH${NC}"
    echo ""
    echo "Byg først APK'en med:"
    echo "  docker-compose run --rm build"
    exit 1
fi

# Check for connected devices
echo -e "${YELLOW}📱 Søger efter tilsluttede enheder...${NC}"
DEVICES=$(adb devices | grep -v "List" | grep -v "^$" | wc -l)

if [ "$DEVICES" -eq 0 ]; then
    echo -e "${RED}❌ Ingen Android enheder tilsluttet!${NC}"
    echo ""
    echo "Tilslut en Android enhed via USB og aktiver USB debugging:"
    echo "  1. Gå til Indstillinger > Om telefonen"
    echo "  2. Tryk 7 gange på 'Build nummer' for at aktivere Udviklerindstillinger"
    echo "  3. Gå til Indstillinger > Udviklerindstillinger"
    echo "  4. Aktiver 'USB debugging'"
    echo "  5. Tilslut enheden via USB og godkend computeren"
    echo ""
    echo "Eller tilslut via WiFi:"
    echo "  adb connect <device-ip>:5555"
    exit 1
fi

echo -e "${GREEN}✅ Fundet $DEVICES enhed(er)${NC}"
adb devices
echo ""

# Get device info
DEVICE_MODEL=$(adb shell getprop ro.product.model 2>/dev/null | tr -d '\r')
ANDROID_VERSION=$(adb shell getprop ro.build.version.release 2>/dev/null | tr -d '\r')
echo -e "${BLUE}📱 Enhed: $DEVICE_MODEL (Android $ANDROID_VERSION)${NC}"
echo ""

# Uninstall existing app if present
echo -e "${YELLOW}🗑️  Fjerner eksisterende installation...${NC}"
adb uninstall $PACKAGE_NAME 2>/dev/null || true

# Install APK
echo -e "${YELLOW}📦 Installerer APK...${NC}"
adb install -r "$APK_PATH"
echo -e "${GREEN}✅ APK installeret!${NC}"
echo ""

# Start app
echo -e "${YELLOW}🚀 Starter app...${NC}"
adb shell am start -n "$PACKAGE_NAME/.MainActivity"
echo -e "${GREEN}✅ App startet!${NC}"
echo ""

# Show logcat
echo -e "${BLUE}📋 Logcat output (Ctrl+C for at stoppe):${NC}"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
adb logcat -s "IOCast:*" "MqttService:*" "CommandHandler:*" "MainActivity:*" | while read line; do
    # Colorize output
    if [[ $line == *"E/"* ]]; then
        echo -e "${RED}$line${NC}"
    elif [[ $line == *"W/"* ]]; then
        echo -e "${YELLOW}$line${NC}"
    elif [[ $line == *"I/"* ]]; then
        echo -e "${GREEN}$line${NC}"
    else
        echo "$line"
    fi
done
