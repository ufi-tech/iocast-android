#!/bin/bash
# ============================================
# IOCast Android TV - Revert Script
# ============================================
# Nulstiller TV til normal tilstand
#
# Brug:
#   ./revert-tv.sh 192.168.40.88
#
# ============================================

set -e

TV_IP="${1:-192.168.40.88}"

RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m'

echo ""
echo -e "${YELLOW}============================================${NC}"
echo -e "${YELLOW}  IOCast TV Revert Script${NC}"
echo -e "${YELLOW}============================================${NC}"
echo ""

echo "Forbinder til TV: ${TV_IP}..."
adb connect ${TV_IP}:5555 > /dev/null 2>&1
sleep 2

if ! adb -s ${TV_IP}:5555 shell echo "OK" > /dev/null 2>&1; then
    echo -e "${RED}Kunne ikke forbinde til ${TV_IP}:5555${NC}"
    exit 1
fi

echo -e "${GREEN}Forbundet${NC}"
echo ""

echo "Genaktiverer Google TV launcher..."
adb -s ${TV_IP}:5555 shell pm enable --user 0 com.google.android.apps.tv.launcherx 2>/dev/null || true

echo "Genaktiverer Play Store..."
adb -s ${TV_IP}:5555 shell pm enable --user 0 com.android.vending 2>/dev/null || true
adb -s ${TV_IP}:5555 shell pm enable --user 0 android.autoinstalls.config.google.gtvpai 2>/dev/null || true

echo "Genaktiverer streaming apps..."
APPS=(
    "com.amazon.amazonvideo.livingroom"
    "dk.tv2.tv2playtv"
    "dk.dr.tvplayer"
    "com.viaplay.android"
    "com.disney.disneyplus"
    "com.google.android.youtube.tv"
    "com.google.android.youtube.tvmusic"
    "com.netflix.ninja"
    "com.google.android.videos"
    "com.google.android.play.games"
)

for pkg in "${APPS[@]}"; do
    adb -s ${TV_IP}:5555 shell pm enable --user 0 $pkg 2>/dev/null || true
done

echo "Nulstiller skærm-timeout (30 min)..."
adb -s ${TV_IP}:5555 shell settings put system screen_off_timeout 1800000 2>/dev/null || true

echo "Genaktiverer screensaver..."
adb -s ${TV_IP}:5555 shell settings put secure screensaver_enabled 1 2>/dev/null || true

echo ""
echo -e "${GREEN}============================================${NC}"
echo -e "${GREEN}  ✅ TV nulstillet til normal tilstand${NC}"
echo -e "${GREEN}============================================${NC}"
echo ""
echo "Genstart TV'et for at aktivere ændringerne:"
echo "  adb -s ${TV_IP}:5555 reboot"
echo ""
echo "Eller hold Power-knappen nede på fjernbetjeningen."
echo ""
