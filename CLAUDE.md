# IOCast - Android Kiosk App

## Projekt Oversigt

| Key | Value |
|-----|-------|
| **Projekt** | Android kiosk browser med MQTT kommandoer |
| **Domain** | iocast.dk |
| **Package** | dk.iocast.kiosk |
| **Tech Stack** | Kotlin + HiveMQ MQTT + WebView |
| **Min SDK** | Android 7.0 (API 24) |
| **GitHub** | TBD |

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

## Build med Docker (Anbefalet)

Ingen Android Studio eller SDK installation påkrævet!

```bash
# Byg debug APK
./build-apk.sh

# Byg release APK
./build-apk.sh release

# Eller med docker-compose
docker-compose run --rm build

# Start shell i build container
docker-compose run --rm shell
```

**Output:** `output/iocast-debug.apk` eller `output/iocast-release.apk`

**Docker image:** [mingc/android-build-box](https://hub.docker.com/r/mingc/android-build-box/) (~16GB)

## Build med Gradle (Lokal)

Kræver Android Studio eller Android SDK installeret.

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
