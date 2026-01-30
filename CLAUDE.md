# IOCast - Android Kiosk App

## Projekt Oversigt

| Key | Value |
|-----|-------|
| **Projekt** | Android kiosk browser med MQTT kommandoer |
| **Domain** | iocast.dk |
| **Package** | dk.iocast.kiosk |
| **Tech Stack** | Kotlin + HiveMQ MQTT + WebView |
| **Min SDK** | Android 7.0 (API 24) |
| **GitHub** | [ufi-tech/iocast-android](https://github.com/ufi-tech/iocast-android) |
| **Current Version** | 1.3.0 (versionCode 8) |
| **APK Download** | [GitHub Releases](https://github.com/ufi-tech/iocast-android/releases) |

## Hvad er IOCast?

IOCast er en Android kiosk-app der:
- Viser en webside i fullscreen (digital signage)
- Modtager kommandoer via MQTT (tovejs!)
- Auto-starter ved boot
- Setup via QR-kode scanning
- Integrerer med infoscreen-admin platform

## MQTT Topics

```
devices/{deviceId}/status      → Publish: online/offline + LWT
devices/{deviceId}/telemetry   → Publish: batteri, wifi, etc.
devices/{deviceId}/events      → Publish: screenOn, screenOff, etc.
devices/{deviceId}/cmd/+       → Subscribe: kommandoer
devices/{deviceId}/cmd/+/ack   → Publish: acknowledgment
```

## Understøttede Kommandoer

| Kommando | Topic | Payload |
|----------|-------|---------|
| loadUrl | cmd/loadUrl | `{"url": "https://..."}` |
| reload | cmd/reload | `{}` |
| screenOn | cmd/screenOn | `{}` |
| screenOff | cmd/screenOff | `{}` |
| reboot | cmd/reboot | `{}` |
| setVolume | cmd/setVolume | `{"level": 50}` |
| speak | cmd/speak | `{"text": "Hej"}` |
| screenshot | cmd/screenshot | `{}` |
| getInfo | cmd/getInfo | `{}` |

## Projekt Struktur

```
iocast-android/
├── app/
│   ├── src/main/
│   │   ├── java/dk/iocast/kiosk/
│   │   │   ├── IOCastApp.kt           # Application class
│   │   │   ├── MainActivity.kt        # Kiosk WebView
│   │   │   ├── SetupActivity.kt       # QR setup
│   │   │   ├── service/
│   │   │   │   └── MqttService.kt     # MQTT foreground service
│   │   │   ├── mqtt/
│   │   │   │   ├── MqttClient.kt      # HiveMQ wrapper
│   │   │   │   └── MqttConfig.kt      # Connection config
│   │   │   ├── command/
│   │   │   │   ├── CommandHandler.kt  # Dispatcher
│   │   │   │   └── Commands.kt        # Command implementations
│   │   │   ├── receiver/
│   │   │   │   ├── BootReceiver.kt    # Auto-start
│   │   │   │   └── ScreenReceiver.kt  # Screen events
│   │   │   ├── webview/
│   │   │   │   ├── KioskWebView.kt    # Custom WebView
│   │   │   │   └── JsInterface.kt     # JavaScript bridge
│   │   │   └── util/
│   │   │       ├── DeviceInfo.kt      # System info collector
│   │   │       └── Prefs.kt           # SharedPreferences
│   │   ├── res/
│   │   └── AndroidManifest.xml
│   └── build.gradle.kts
├── build.gradle.kts
├── settings.gradle.kts
├── gradle.properties
└── CLAUDE.md
```

## Build via MQTT (Anbefalet)

Automatiseret build service der bygger APK og uploader til GitHub Releases.

```bash
# Trigger build (husk at opdatere version og versionCode!)
source admin-platform/.env  # eller brug password direkte
mosquitto_pub -h 188.228.60.134 -u admin -P "$MQTT_PASSWORD" \
  -t "build/iocast-android/trigger" \
  -m '{"branch":"main","version":"1.4.0","versionCode":9}'

# Monitor build progress
mosquitto_sub -h 188.228.60.134 -u admin -P "$MQTT_PASSWORD" \
  -t "build/iocast-android/#" -v
```

**Build stages:**
1. Clone repository
2. Update version in build.gradle.kts
3. Build APK via Docker (cimg/android:2024.01.1 med Java 17)
4. Upload til GitHub Releases

**Build service:** Kører på `ufitechbox-docker-01` (172.18.0.101)

**Skill:** Brug `/iocast-build` for fuld dokumentation

## Build med Docker (Lokal fallback)

```bash
# Byg debug APK lokalt
rm -rf /tmp/iocast-clean && mkdir -p /tmp/iocast-clean
git archive HEAD | tar -x -C /tmp/iocast-clean
docker run --rm -v /tmp/iocast-clean:/project -w /project cimg/android:2024.01.1 \
  bash -c "chmod +x gradlew && ./gradlew assembleDebug --no-daemon"
cp /tmp/iocast-clean/app/build/outputs/apk/debug/app-debug.apk releases/
```

**VIGTIGT:** Brug kun `cimg/android:2024.01.1` imaget der er cached lokalt med Java 17.
Nyere versions fra Docker Hub har Java 21 som bryder buildet!

## Build med Gradle (Android Studio)

Kræver Android Studio eller Android SDK med Java 17.

```bash
# Build debug APK
./gradlew assembleDebug

# Install on connected device
./gradlew installDebug

# Build release APK
./gradlew assembleRelease
```

## Backend Integration

MQTT Broker: 188.228.60.134:1883
Admin Platform: infoscreen-admin (same repo)

## Onboarding Flow

1. Bruger installerer IOCast APK
2. App starter → SetupActivity
3. Bruger scanner QR-kode fra admin dashboard
4. App forbinder til MQTT, viser startUrl
5. Device registreres automatisk i admin

## QR Payload Format

```json
{
  "broker": "tcp://188.228.60.134:1883",
  "username": "device-abc123",
  "password": "auto-generated",
  "deviceId": "abc123",
  "startUrl": "https://kunde.iocast.dk/display"
}
```

## Build Service Administration

**SSH til build server:**
```bash
ssh -J ingress-01 ubuntu@172.18.0.101
```

**Service location:** `/opt/iocast-build-service/build-service/`

**Service commands:**
```bash
# Check logs
ssh -J ingress-01 ubuntu@172.18.0.101 \
  "docker compose -f /opt/iocast-build-service/build-service/docker-compose.yml logs --tail=50"

# Restart service
ssh -J ingress-01 ubuntu@172.18.0.101 \
  "cd /opt/iocast-build-service/build-service && docker compose restart"
```

## GitHub Releases

APK'er uploades automatisk til: https://github.com/ufi-tech/iocast-android/releases

```bash
# List releases
gh release list --repo ufi-tech/iocast-android

# Download latest APK
gh release download --repo ufi-tech/iocast-android --pattern "*.apk"
```
