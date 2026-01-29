#!/bin/bash
# ============================================
# IOCast Android TV Provisioning Script
# ============================================
# Version: 1.0
# Testet på: Thomson 240G (Android 12)
#
# Brug:
#   ./provision-tv.sh                    # Scan netværk og provision
#   ./provision-tv.sh 192.168.40.88      # Provision specifik IP
#   ./provision-tv.sh 192.168.40.88 iocast-v1.0.4.apk
#
# ============================================

set -e

# Farver til output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# === KONFIGURATION ===
TV_IP="${1:-}"
APK_PATH="${2:-$(dirname "$0")/iocast-v1.0.3-permissions-fix.apk}"
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"

# Bloatware pakker der skal deaktiveres
BLOATWARE=(
    "com.amazon.amazonvideo.livingroom"    # Prime Video
    "dk.tv2.tv2playtv"                      # TV2 Play
    "dk.dr.tvplayer"                        # DR TV
    "com.viaplay.android"                   # Viaplay
    "com.disney.disneyplus"                 # Disney+
    "com.google.android.youtube.tv"         # YouTube
    "com.google.android.youtube.tvmusic"    # YouTube Music
    "com.netflix.ninja"                     # Netflix
    "com.google.android.videos"             # Google TV
    "com.google.android.play.games"         # Play Games
)

# === FUNKTIONER ===

print_header() {
    echo ""
    echo -e "${BLUE}============================================${NC}"
    echo -e "${BLUE}  IOCast Android TV Provisioning${NC}"
    echo -e "${BLUE}============================================${NC}"
    echo ""
}

print_step() {
    echo -e "${YELLOW}[$1/8]${NC} $2"
}

print_success() {
    echo -e "${GREEN}✓${NC} $1"
}

print_error() {
    echo -e "${RED}✗${NC} $1"
}

scan_network() {
    echo -e "${YELLOW}Scanner netværk for Android TV enheder...${NC}"
    echo "(Dette tager ca. 10-20 sekunder)"
    echo ""

    FOUND_IPS=()

    # Scan almindelige subnet ranges
    for subnet in "192.168.40" "192.168.1" "192.168.0" "10.0.0"; do
        for ip in $(seq 1 254); do
            (nc -z -w1 ${subnet}.${ip} 5555 2>/dev/null && echo "${subnet}.${ip}") &
        done
    done | while read ip; do
        echo -e "  ${GREEN}Fundet:${NC} $ip"
        FOUND_IPS+=("$ip")
    done

    wait 2>/dev/null

    if [ ${#FOUND_IPS[@]} -eq 0 ]; then
        echo ""
        echo -e "${RED}Ingen ADB-enheder fundet på netværket.${NC}"
        echo ""
        echo "Sørg for at:"
        echo "  1. TV'et er tændt og på samme netværk"
        echo "  2. ADB Debugging er aktiveret:"
        echo "     Settings → Device Preferences → Developer options → USB debugging"
        echo ""
        exit 1
    fi
}

verify_apk() {
    if [ ! -f "$APK_PATH" ]; then
        print_error "APK ikke fundet: $APK_PATH"
        echo ""
        echo "Tilgængelige APK filer:"
        ls -la "$SCRIPT_DIR"/*.apk 2>/dev/null || echo "  (ingen fundet)"
        exit 1
    fi
    print_success "APK fundet: $(basename "$APK_PATH")"
}

connect_tv() {
    print_step "1" "Forbinder til TV..."

    adb connect ${TV_IP}:5555 > /dev/null 2>&1
    sleep 2

    # Verificer forbindelse
    if ! adb -s ${TV_IP}:5555 shell echo "OK" > /dev/null 2>&1; then
        print_error "Kunne ikke forbinde til ${TV_IP}:5555"
        echo ""
        echo "Mulige årsager:"
        echo "  - TV'et er slukket eller på andet netværk"
        echo "  - ADB Debugging er ikke aktiveret"
        echo "  - Første forbindelse: Godkend på TV-skærmen"
        exit 1
    fi

    # Hent device info
    MODEL=$(adb -s ${TV_IP}:5555 shell getprop ro.product.model 2>/dev/null | tr -d '\r')
    ANDROID=$(adb -s ${TV_IP}:5555 shell getprop ro.build.version.release 2>/dev/null | tr -d '\r')

    print_success "Forbundet til: $MODEL (Android $ANDROID)"
}

install_apk() {
    print_step "2" "Installerer IOCast APK..."

    OUTPUT=$(adb -s ${TV_IP}:5555 install -r "$APK_PATH" 2>&1)

    if echo "$OUTPUT" | grep -q "Success"; then
        print_success "APK installeret"
    else
        print_error "APK installation fejlede: $OUTPUT"
        exit 1
    fi
}

set_launcher() {
    print_step "3" "Sætter IOCast som default launcher..."

    adb -s ${TV_IP}:5555 shell cmd package set-home-activity dk.iocast.kiosk/.MainActivity > /dev/null 2>&1
    print_success "IOCast sat som home activity"
}

disable_bloatware() {
    print_step "4" "Deaktiverer streaming apps..."

    for pkg in "${BLOATWARE[@]}"; do
        RESULT=$(adb -s ${TV_IP}:5555 shell pm disable-user --user 0 $pkg 2>&1)
        if echo "$RESULT" | grep -q "disabled"; then
            echo -e "    ${GREEN}✓${NC} $pkg"
        else
            echo -e "    ${YELLOW}-${NC} $pkg (ikke installeret)"
        fi
    done

    print_success "Streaming apps deaktiveret"
}

disable_playstore() {
    print_step "5" "Deaktiverer Play Store..."

    adb -s ${TV_IP}:5555 shell pm disable-user --user 0 com.android.vending > /dev/null 2>&1
    adb -s ${TV_IP}:5555 shell pm disable-user --user 0 android.autoinstalls.config.google.gtvpai > /dev/null 2>&1

    print_success "Play Store deaktiveret (ingen auto-opdateringer)"
}

disable_launcher() {
    print_step "6" "Deaktiverer Google TV launcher..."

    adb -s ${TV_IP}:5555 shell pm disable-user --user 0 com.google.android.apps.tv.launcherx > /dev/null 2>&1

    print_success "Standard launcher deaktiveret"
}

configure_screen() {
    print_step "7" "Konfigurerer skærm-indstillinger..."

    # Slå skærm-timeout fra
    adb -s ${TV_IP}:5555 shell settings put system screen_off_timeout 2147483647 > /dev/null 2>&1
    echo -e "    ${GREEN}✓${NC} Screen timeout: disabled"

    # Deaktiver screensaver
    adb -s ${TV_IP}:5555 shell settings put secure screensaver_enabled 0 > /dev/null 2>&1
    echo -e "    ${GREEN}✓${NC} Screensaver: disabled"

    # Hold skærm tændt når tilsluttet strøm
    adb -s ${TV_IP}:5555 shell settings put global stay_on_while_plugged_in 3 > /dev/null 2>&1
    echo -e "    ${GREEN}✓${NC} Stay on while plugged in: enabled"

    print_success "Skærm-indstillinger konfigureret"
}

grant_permissions() {
    print_step "8" "Giver app tilladelser..."

    # Allow install from unknown sources (requires user interaction on Android 8+)
    # This opens the settings screen for the user to enable
    adb -s ${TV_IP}:5555 shell appops set dk.iocast.kiosk REQUEST_INSTALL_PACKAGES allow > /dev/null 2>&1 || true
    echo -e "    ${GREEN}✓${NC} Install unknown apps: attempting via appops"

    # Grant location permission for WiFi scanning
    adb -s ${TV_IP}:5555 shell pm grant dk.iocast.kiosk android.permission.ACCESS_FINE_LOCATION > /dev/null 2>&1 || true
    adb -s ${TV_IP}:5555 shell pm grant dk.iocast.kiosk android.permission.ACCESS_COARSE_LOCATION > /dev/null 2>&1 || true
    echo -e "    ${GREEN}✓${NC} Location permission: granted"

    # Grant notification permission (Android 13+)
    adb -s ${TV_IP}:5555 shell pm grant dk.iocast.kiosk android.permission.POST_NOTIFICATIONS > /dev/null 2>&1 || true
    echo -e "    ${GREEN}✓${NC} Notification permission: granted"

    print_success "Tilladelser konfigureret"
}

test_home() {
    echo ""
    echo -e "${YELLOW}Tester Home-knap...${NC}"
    adb -s ${TV_IP}:5555 shell input keyevent KEYCODE_HOME > /dev/null 2>&1
    sleep 2

    # Verificer launcher
    CURRENT_LAUNCHER=$(adb -s ${TV_IP}:5555 shell cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME 2>/dev/null | grep -o "dk.iocast.kiosk" || echo "")

    if [ -n "$CURRENT_LAUNCHER" ]; then
        print_success "IOCast er nu default launcher"
    else
        print_error "ADVARSEL: IOCast er muligvis ikke sat som launcher"
    fi
}

print_done() {
    echo ""
    echo -e "${GREEN}============================================${NC}"
    echo -e "${GREEN}  ✅ Provisioning komplet!${NC}"
    echo -e "${GREEN}============================================${NC}"
    echo ""
    echo "TV: $MODEL @ ${TV_IP}"
    echo ""
    echo "Næste skridt:"
    echo "  1. Indtast kunde-kode i IOCast app"
    echo "  2. Verificer at TV starter i IOCast efter reboot"
    echo ""
    echo "Nyttige kommandoer:"
    echo "  adb connect ${TV_IP}:5555"
    echo "  adb -s ${TV_IP}:5555 exec-out screencap -p > screenshot.png"
    echo "  adb -s ${TV_IP}:5555 reboot"
    echo ""
}

# === MAIN ===

print_header

# Verificer APK findes
verify_apk

# Scan netværk hvis ingen IP angivet
if [ -z "$TV_IP" ]; then
    echo "Ingen IP angivet. Scanner netværk..."
    echo ""

    # Find første ADB device
    FOUND=$(for ip in 192.168.40.{1..254}; do (nc -z -w1 $ip 5555 2>/dev/null && echo "$ip" && exit 0) & done; wait)

    if [ -z "$FOUND" ]; then
        print_error "Ingen ADB-enheder fundet"
        echo ""
        echo "Kør scriptet med IP: ./provision-tv.sh 192.168.40.XX"
        exit 1
    fi

    TV_IP="$FOUND"
    echo -e "${GREEN}Fundet TV på: ${TV_IP}${NC}"
fi

echo "TV IP: ${TV_IP}"
echo "APK: $(basename "$APK_PATH")"
echo ""

# Kør provisioning
connect_tv
install_apk
set_launcher
disable_bloatware
disable_playstore
disable_launcher
configure_screen
grant_permissions
test_home
print_done
