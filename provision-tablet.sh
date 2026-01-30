#!/bin/bash
#
# IOCast Tablet Provisioning Script
# For Lenovo TB-X606F (Tab M10 FHD Plus) og lignende Android tablets
#
# Brug: ./provision-tablet.sh [DEVICE_SERIAL] [APK_PATH]
#

set -e

# === KONFIGURATION ===
DEVICE="${1:-}"
APK_PATH="${2:-iocast-v1.0.5-ack-fix.apk}"
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

# Farver
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

echo "============================================"
echo "  IOCast Tablet Provisioning"
echo "============================================"
echo ""

# === 1. FIND DEVICE ===
echo -e "${YELLOW}[1/10] Finder tablet...${NC}"

if [ -z "$DEVICE" ]; then
    # Auto-detect device
    DEVICES=$(adb devices | grep -v "List" | grep -v "^$" | awk '{print $1}')
    DEVICE_COUNT=$(echo "$DEVICES" | wc -l | tr -d ' ')

    if [ "$DEVICE_COUNT" -eq 0 ] || [ -z "$DEVICES" ]; then
        echo -e "${RED}FEJL: Ingen enheder fundet. Tilslut tablet via USB og aktiver USB debugging.${NC}"
        exit 1
    elif [ "$DEVICE_COUNT" -gt 1 ]; then
        echo "Flere enheder fundet:"
        echo "$DEVICES"
        echo ""
        echo "Brug: $0 <DEVICE_SERIAL>"
        exit 1
    else
        DEVICE="$DEVICES"
    fi
fi

echo -e "${GREEN}✓ Device: $DEVICE${NC}"

# Verificer forbindelse
if ! adb -s "$DEVICE" shell echo "OK" > /dev/null 2>&1; then
    echo -e "${RED}FEJL: Kunne ikke forbinde til $DEVICE${NC}"
    exit 1
fi

# Hent device info
MODEL=$(adb -s "$DEVICE" shell getprop ro.product.model | tr -d '\r')
ANDROID=$(adb -s "$DEVICE" shell getprop ro.build.version.release | tr -d '\r')
echo "  Model: $MODEL"
echo "  Android: $ANDROID"
echo ""

# === 2. INSTALLER APK ===
echo -e "${YELLOW}[2/10] Installerer IOCast APK...${NC}"

if [ -f "$SCRIPT_DIR/$APK_PATH" ]; then
    APK_FULL="$SCRIPT_DIR/$APK_PATH"
elif [ -f "$APK_PATH" ]; then
    APK_FULL="$APK_PATH"
else
    echo -e "${RED}FEJL: APK ikke fundet: $APK_PATH${NC}"
    echo "Tilgængelige APK'er:"
    ls -la "$SCRIPT_DIR"/*.apk 2>/dev/null || echo "  (ingen)"
    exit 1
fi

adb -s "$DEVICE" install -r "$APK_FULL"
echo -e "${GREEN}✓ APK installeret${NC}"
echo ""

# === 3. SÆT SOM DEFAULT LAUNCHER ===
echo -e "${YELLOW}[3/10] Sætter IOCast som default launcher...${NC}"
adb -s "$DEVICE" shell cmd package set-home-activity dk.iocast.kiosk/.MainActivity
echo -e "${GREEN}✓ IOCast sat som home activity${NC}"
echo ""

# === 4. DEAKTIVER LAUNCHERS ===
echo -e "${YELLOW}[4/10] Deaktiverer andre launchers...${NC}"

LAUNCHERS=(
    "com.tblenovo.launcher"
    "com.tblenovo.kidslauncher"
    "com.lenovo.launcher.provider"
    "com.google.android.apps.tv.launcherx"
)

for pkg in "${LAUNCHERS[@]}"; do
    adb -s "$DEVICE" shell pm disable-user --user 0 "$pkg" 2>/dev/null && echo "  Deaktiveret: $pkg" || true
done
echo -e "${GREEN}✓ Launchers deaktiveret${NC}"
echo ""

# === 5. DEAKTIVER BLOATWARE ===
echo -e "${YELLOW}[5/10] Deaktiverer bloatware og opdateringer...${NC}"

BLOATWARE=(
    # Opdateringer
    "com.lenovo.ota"
    "com.lenovo.lsf.device"
    "com.lenovo.lsf.user"
    "com.android.vending"
    "com.google.android.gms.update"
    # Setup
    "com.google.android.setupwizard"
    "com.android.provision"
    # Lenovo apps
    "com.lenovo.browser"
)

for pkg in "${BLOATWARE[@]}"; do
    adb -s "$DEVICE" shell pm disable-user --user 0 "$pkg" 2>/dev/null && echo "  Deaktiveret: $pkg" || true
done
echo -e "${GREEN}✓ Bloatware deaktiveret${NC}"
echo ""

# === 6. DEAKTIVER LOCK SCREEN ===
echo -e "${YELLOW}[6/10] Deaktiverer lock screen...${NC}"

adb -s "$DEVICE" shell "
locksettings clear --old '' 2>/dev/null || true
locksettings set-disabled true 2>/dev/null || true
settings put secure lockscreen.disabled 1
settings put secure lockscreen.password_type 65536
settings put secure lockscreen.password_type_alternate 0
settings put global device_provisioned 1
settings put secure user_setup_complete 1
"
echo -e "${GREEN}✓ Lock screen deaktiveret${NC}"
echo ""

# === 7. DEAKTIVER LYD OG NOTIFIKATIONER ===
echo -e "${YELLOW}[7/10] Deaktiverer lyd og notifikationer...${NC}"

adb -s "$DEVICE" shell "
# Lydstyrker
settings put system volume_music 0
settings put system volume_ring 0
settings put system volume_alarm 0
settings put system volume_notification 0
settings put system volume_system 0

# Notifikationer
settings put secure notification_badging 0
settings put global heads_up_notifications_enabled 0

# Vibration og touch lyde
settings put system vibrate_when_ringing 0
settings put system haptic_feedback_enabled 0
settings put system sound_effects_enabled 0
settings put system dtmf_tone 0

# Media volume
media volume --stream 3 --set 0 2>/dev/null || true
"
echo -e "${GREEN}✓ Lyd og notifikationer deaktiveret${NC}"
echo ""

# === 8. SKÆRM-INDSTILLINGER ===
echo -e "${YELLOW}[8/10] Konfigurerer skærm...${NC}"

adb -s "$DEVICE" shell "
# Aldrig sluk skærm
settings put system screen_off_timeout 2147483647

# Hold skærm vågen på strøm (AC=1, USB=2, Wireless=4, alle=7)
settings put global stay_on_while_plugged_in 7

# Deaktiver screensaver
settings put secure screensaver_enabled 0

# Deaktiver adaptive brightness
settings put system screen_brightness_mode 0
"
echo -e "${GREEN}✓ Skærm konfigureret${NC}"
echo ""

# === 9. DEAKTIVER OPDATERINGER ===
echo -e "${YELLOW}[9/12] Deaktiverer automatiske opdateringer...${NC}"

adb -s "$DEVICE" shell "
settings put global auto_time 0
settings put global auto_time_zone 0
settings put global ota_disable_automatic_update 1
settings put global package_verifier_enable 0
"
echo -e "${GREEN}✓ Opdateringer deaktiveret${NC}"
echo ""

# === 10. BATTERY & PERFORMANCE ===
echo -e "${YELLOW}[10/12] Optimerer batteri og performance...${NC}"

adb -s "$DEVICE" shell "
# Tilføj til battery whitelist (undgå at systemet dræber appen)
dumpsys deviceidle whitelist +dk.iocast.kiosk

# Deaktiver Doze mode
dumpsys deviceidle disable 2>/dev/null || true

# WiFi skal aldrig sove
settings put global wifi_sleep_policy 2

# Deaktiver adaptive battery
settings put global adaptive_battery_management_enabled 0 2>/dev/null || true
"
echo -e "${GREEN}✓ Batteri optimeret${NC}"
echo ""

# === 11. DISPLAY & ROTATION ===
echo -e "${YELLOW}[11/12] Konfigurerer display...${NC}"

adb -s "$DEVICE" shell "
# Deaktiver auto-rotate
settings put system accelerometer_rotation 0

# Sæt til landscape mode
settings put system user_rotation 1

# Fuld skærm lysstyrke (255 = max)
settings put system screen_brightness 255

# Deaktiver adaptive brightness
settings put system screen_brightness_mode 0
"
echo -e "${GREEN}✓ Display konfigureret${NC}"
echo ""

# === 12. START IOCAST ===
echo -e "${YELLOW}[12/12] Starter IOCast...${NC}"

# Tryk Home for at starte IOCast
adb -s "$DEVICE" shell input keyevent KEYCODE_HOME
sleep 2

# Tag screenshot for at verificere
SCREENSHOT="/tmp/iocast-provision-$DEVICE.png"
adb -s "$DEVICE" exec-out screencap -p > "$SCREENSHOT" 2>/dev/null && echo "  Screenshot: $SCREENSHOT" || true

echo -e "${GREEN}✓ IOCast startet${NC}"
echo ""

# === DONE ===
echo "============================================"
echo -e "  ${GREEN}✅ Provisioning komplet!${NC}"
echo "============================================"
echo ""
echo "Device: $DEVICE ($MODEL)"
echo "Android: $ANDROID"
echo "APK: $APK_PATH"
echo ""
echo "Tablet er nu konfigureret som kiosk med:"
echo "  • IOCast som launcher"
echo "  • Lock screen deaktiveret"
echo "  • Lyd og notifikationer deaktiveret"
echo "  • Skærm altid tændt"
echo "  • Opdateringer deaktiveret"
echo ""
echo "Genstart tabletten for at verificere:"
echo "  adb -s $DEVICE reboot"
echo ""
