#!/bin/bash
# ============================================
# IOCast Android - MQTT Build & Release
# ============================================
# Trigger build via MQTT og overvåg progress
#
# Brug:
#   ./build-release.sh                    # Auto-increment patch version
#   ./build-release.sh 2.1.0              # Specify version
#   ./build-release.sh 2.1.0 18           # Specify version + versionCode
#
# ============================================

set -e

# Farver
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
MAGENTA='\033[0;35m'
CYAN='\033[0;36m'
NC='\033[0m'

# MQTT Configuration
MQTT_HOST="188.228.60.134"
MQTT_PORT="1883"
MQTT_USER="admin"
MQTT_PASS="BZs9UBDViukWaZu+1O6Hd77qr+Dshomu"

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
GRADLE_FILE="$SCRIPT_DIR/app/build.gradle.kts"
BUILD_BRANCH="main"

# ============================================
# FUNKTIONER
# ============================================

print_header() {
    echo ""
    echo -e "${BLUE}╔════════════════════════════════════════════╗${NC}"
    echo -e "${BLUE}║     IOCast Android - MQTT Build           ║${NC}"
    echo -e "${BLUE}╚════════════════════════════════════════════╝${NC}"
    echo ""
}

print_step() {
    echo -e "${CYAN}▶${NC} $1"
}

print_success() {
    echo -e "${GREEN}✓${NC} $1"
}

print_error() {
    echo -e "${RED}✗${NC} $1"
}

print_info() {
    echo -e "${YELLOW}ℹ${NC} $1"
}

check_dependencies() {
    print_step "Tjekker dependencies..."

    if ! command -v mosquitto_pub &> /dev/null; then
        print_error "mosquitto_pub ikke fundet!"
        echo ""
        echo "Installer med: brew install mosquitto"
        exit 1
    fi

    if ! command -v mosquitto_sub &> /dev/null; then
        print_error "mosquitto_sub ikke fundet!"
        echo ""
        echo "Installer med: brew install mosquitto"
        exit 1
    fi

    if ! command -v jq &> /dev/null; then
        print_error "jq ikke fundet!"
        echo ""
        echo "Installer med: brew install jq"
        exit 1
    fi

    print_success "Alle dependencies OK"
}

get_current_version() {
    if [ ! -f "$GRADLE_FILE" ]; then
        print_error "build.gradle.kts ikke fundet!"
        exit 1
    fi

    CURRENT_VERSION=$(grep -E 'versionName\s*=' "$GRADLE_FILE" | sed -E 's/.*"([0-9.]+)".*/\1/')
    CURRENT_VERSION_CODE=$(grep -E 'versionCode\s*=' "$GRADLE_FILE" | sed -E 's/.*=\s*([0-9]+).*/\1/')

    if [ -z "$CURRENT_VERSION" ] || [ -z "$CURRENT_VERSION_CODE" ]; then
        print_error "Kunne ikke læse version fra build.gradle.kts"
        exit 1
    fi
}

increment_version() {
    # Split version (e.g., 2.0.4 -> 2 0 4)
    IFS='.' read -r MAJOR MINOR PATCH <<< "$CURRENT_VERSION"

    # Increment patch
    NEW_PATCH=$((PATCH + 1))
    NEW_VERSION="${MAJOR}.${MINOR}.${NEW_PATCH}"
    NEW_VERSION_CODE=$((CURRENT_VERSION_CODE + 1))
}

print_version_info() {
    echo ""
    echo -e "${MAGENTA}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
    echo -e "  ${YELLOW}Nuværende:${NC} v${CURRENT_VERSION} (code: ${CURRENT_VERSION_CODE})"
    echo -e "  ${GREEN}Ny version:${NC} v${NEW_VERSION} (code: ${NEW_VERSION_CODE})"
    echo -e "${MAGENTA}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
    echo ""
}

confirm_build() {
    read -p "$(echo -e ${YELLOW}Fortsæt med build? [y/N]:${NC} )" -n 1 -r
    echo
    if [[ ! $REPLY =~ ^[Yy]$ ]]; then
        print_info "Build aflyst"
        exit 0
    fi
}

trigger_build() {
    print_step "Trigger MQTT build..."

    PAYLOAD=$(cat <<EOF
{
  "branch": "${BUILD_BRANCH}",
  "version": "${NEW_VERSION}",
  "versionCode": ${NEW_VERSION_CODE}
}
EOF
)

    mosquitto_pub \
        -h "$MQTT_HOST" \
        -p "$MQTT_PORT" \
        -u "$MQTT_USER" \
        -P "$MQTT_PASS" \
        -t "build/iocast-android/trigger" \
        -m "$PAYLOAD"

    if [ $? -eq 0 ]; then
        print_success "Build trigget!"
        echo ""
        print_info "Version: v${NEW_VERSION}"
        print_info "Branch: ${BUILD_BRANCH}"
    else
        print_error "Kunne ikke trigge build"
        exit 1
    fi
}

monitor_build() {
    print_step "Overvåger build progress..."
    echo ""

    START_TIME=$(date +%s)
    LAST_PROGRESS=0

    # Use temp files to communicate between subshell and parent
    TEMP_DIR=$(mktemp -d)
    TEMP_STATUS="$TEMP_DIR/status"
    TEMP_OUTPUT="$TEMP_DIR/output"

    trap "rm -rf $TEMP_DIR" EXIT

    # Subscribe to all build topics
    (mosquitto_sub \
        -h "$MQTT_HOST" \
        -p "$MQTT_PORT" \
        -u "$MQTT_USER" \
        -P "$MQTT_PASS" \
        -t "build/iocast-android/#" \
        -v \
        -W 180 | while read -r line; do

        TOPIC=$(echo "$line" | awk '{print $1}')
        MESSAGE=$(echo "$line" | cut -d' ' -f2-)

        # Parse JSON message
        case "$TOPIC" in
            "build/iocast-android/progress")
                PROGRESS=$(echo "$MESSAGE" | jq -r '.progress // 0' 2>/dev/null)
                STEP=$(echo "$MESSAGE" | jq -r '.step // "Unknown"' 2>/dev/null)

                # Only show if progress increased
                if [ "$PROGRESS" -gt "$LAST_PROGRESS" ] 2>/dev/null; then
                    LAST_PROGRESS=$PROGRESS

                    # Progress bar
                    BAR_WIDTH=40
                    FILLED=$((PROGRESS * BAR_WIDTH / 100))
                    EMPTY=$((BAR_WIDTH - FILLED))

                    BAR=$(printf "%${FILLED}s" | tr ' ' '█')
                    BAR="${BAR}$(printf "%${EMPTY}s" | tr ' ' '░')"

                    echo -ne "\r${CYAN}[${BAR}]${NC} ${PROGRESS}% - ${STEP}   "
                fi
                ;;

            "build/iocast-android/result")
                STATUS=$(echo "$MESSAGE" | jq -r '.status' 2>/dev/null)

                if [ "$STATUS" = "success" ]; then
                    BUILD_URL=$(echo "$MESSAGE" | jq -r '.apkUrl')
                    BUILD_SHA256=$(echo "$MESSAGE" | jq -r '.sha256')
                    BUILD_SIZE=$(echo "$MESSAGE" | jq -r '.apkSize')
                    BUILD_TIME=$(echo "$MESSAGE" | jq -r '.buildTime')

                    # Save result to temp files
                    echo "success" > "$TEMP_STATUS"
                    cat > "$TEMP_OUTPUT" <<EOF
${GREEN}╔════════════════════════════════════════════╗${NC}
${GREEN}║          BUILD SUCCEEDED! 🎉               ║${NC}
${GREEN}╚════════════════════════════════════════════╝${NC}

${YELLOW}Version:${NC}   v${NEW_VERSION}
${YELLOW}Build tid:${NC} ${BUILD_TIME}s
${YELLOW}Størrelse:${NC} $(numfmt --to=iec-i --suffix=B $BUILD_SIZE 2>/dev/null || echo "${BUILD_SIZE} bytes")

${CYAN}Download:${NC}
  ${BUILD_URL}

${CYAN}SHA256:${NC}
  ${BUILD_SHA256}
EOF
                    exit 0  # Exit mosquitto_sub
                elif [ "$STATUS" = "failed" ]; then
                    ERROR=$(echo "$MESSAGE" | jq -r '.error // "Unknown error"')

                    # Save result to temp files
                    echo "failed" > "$TEMP_STATUS"
                    cat > "$TEMP_OUTPUT" <<EOF
${RED}╔════════════════════════════════════════════╗${NC}
${RED}║          BUILD FAILED! ❌                  ║${NC}
${RED}╚════════════════════════════════════════════╝${NC}

${RED}Error:${NC} $ERROR
EOF
                    exit 1  # Exit mosquitto_sub
                fi
                ;;
        esac
    done) &

    MONITOR_PID=$!

    # Wait for monitor process to finish
    wait $MONITOR_PID 2>/dev/null
    WAIT_EXIT=$?

    echo ""
    echo ""

    # Check if we got a result
    if [ -f "$TEMP_STATUS" ]; then
        BUILD_STATUS=$(cat "$TEMP_STATUS")

        # Show saved output
        if [ -f "$TEMP_OUTPUT" ]; then
            cat "$TEMP_OUTPUT"
            echo ""
        fi

        if [ "$BUILD_STATUS" = "success" ]; then
            return 0
        else
            exit 1
        fi
    else
        # No result file = timeout
        print_error "Build timeout (ingen response efter 180 sekunder)"
        echo ""
        echo "Tjek build status manuelt:"
        echo "  mosquitto_sub -h $MQTT_HOST -u $MQTT_USER -P '$MQTT_PASS' -t 'build/iocast-android/#' -v"
        exit 1
    fi
}

print_next_steps() {
    echo -e "${MAGENTA}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
    echo -e "${YELLOW}Næste skridt:${NC}"
    echo ""
    echo "  1. Test APK lokalt:"
    echo -e "     ${CYAN}curl -LO ${BUILD_URL}${NC}"
    echo -e "     ${CYAN}adb install -r iocast-v${NEW_VERSION}.apk${NC}"
    echo ""
    echo "  2. Deploy til enheder via MQTT:"
    echo -e "     ${CYAN}mosquitto_pub -h ${MQTT_HOST} -u ${MQTT_USER} -P '***' \\${NC}"
    echo -e "     ${CYAN}  -t 'devices/+/cmd/installApk' \\${NC}"
    echo -e "     ${CYAN}  -m '{\"url\":\"${BUILD_URL}\"}'${NC}"
    echo ""
    echo "  3. Opdater devices via OTA:"
    echo -e "     ${CYAN}Devices downloader automatisk hvis version > current${NC}"
    echo ""
    echo -e "${MAGENTA}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
}

send_notification() {
    # macOS notification
    if command -v osascript &> /dev/null; then
        osascript -e "display notification \"v${NEW_VERSION} er klar til download\" with title \"IOCast Build Complete\" sound name \"Glass\"" 2>/dev/null || true
    fi
}

# ============================================
# MAIN
# ============================================

print_header

# Check dependencies
check_dependencies

# Get current version
print_step "Læser nuværende version..."
get_current_version
print_success "Nuværende version: v${CURRENT_VERSION} (code: ${CURRENT_VERSION_CODE})"

# Determine new version
if [ -n "$1" ]; then
    NEW_VERSION="$1"

    if [ -n "$2" ]; then
        NEW_VERSION_CODE="$2"
    else
        # Auto-increment version code
        NEW_VERSION_CODE=$((CURRENT_VERSION_CODE + 1))
    fi
else
    # Auto-increment patch version
    increment_version
fi

# Print version info
print_version_info

# Confirm build
confirm_build

# Trigger build
trigger_build

# Monitor progress
monitor_build

# Send notification
send_notification

# Print next steps
print_next_steps

echo ""
print_success "Færdig!"
echo ""
