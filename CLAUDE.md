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
| **Current Version** | 1.1.0 (versionCode 7) |
| **APK Download** | [GitHub Releases](https://github.com/ufi-tech/iocast-android/releases) |

## Hvad er IOCast?

IOCast er en Android kiosk-app der:
- Viser en webside i fullscreen (digital signage)
- Modtager kommandoer via MQTT (tovejs!)
- Auto-starter ved boot
- Provisioning via 4-cifret kundekode
- Integrerer med infoscreen-admin platform

## Provisioning Flow

```
┌─────────────────────────────────────────────────────────────┐
│  SetupTvActivity - Numpad/Remote Code Entry                 │
│                                                             │
│  Bruger indtaster 4-cifret kundekode:                       │
│  - Touch numpad (tablet/phone)                              │
│  - Tal-taster på TV fjernbetjening                          │
└─────────────────────────────────────────────────────────────┘
                            │
                            ▼
┌─────────────────────────────────────────────────────────────┐
│  MQTT Provision Request                                     │
│                                                             │
│  Topic: provision/{code}/request                            │
│  Payload: { deviceId, customerCode, timestamp, deviceInfo } │
└─────────────────────────────────────────────────────────────┘
                            │
                            ▼
┌─────────────────────────────────────────────────────────────┐
│  Admin godkender i dashboard                                │
│  (infoscreen-admin platform)                                │
└─────────────────────────────────────────────────────────────┘
                            │
                            ▼
┌─────────────────────────────────────────────────────────────┐
│  MQTT Provision Response                                    │
│                                                             │
│  Topic: provision/{code}/response/{deviceId}                │
│  Payload: { approved: true, startUrl, brokerUrl, ... }      │
└─────────────────────────────────────────────────────────────┘
                            │
                            ▼
┌─────────────────────────────────────────────────────────────┐
│  MainActivity starter med konfigureret WebView              │
└─────────────────────────────────────────────────────────────┘
```

**Hardcoded MQTT credentials** i `ProvisionConfig.kt`:
- Broker: `188.228.60.134:1883`
- Credentials baked into APK

## MQTT Topics

```
devices/{deviceId}/status      → Publish: online/offline + LWT
devices/{deviceId}/telemetry   → Publish: batteri, wifi, IP, etc.
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
│   │   │   ├── SetupTvActivity.kt     # Numpad/remote provisioning
│   │   │   ├── service/
│   │   │   │   └── MqttService.kt     # MQTT foreground service
│   │   │   ├── config/
│   │   │   │   └── ProvisionConfig.kt # Hardcoded MQTT broker
│   │   │   ├── mqtt/
│   │   │   │   └── MqttConfig.kt      # Runtime MQTT config
│   │   │   ├── command/
│   │   │   │   └── CommandHandler.kt  # Command dispatcher
│   │   │   ├── receiver/
│   │   │   │   ├── BootReceiver.kt    # Auto-start
│   │   │   │   └── ScreenReceiver.kt  # Screen events
│   │   │   ├── webview/
│   │   │   │   └── JsInterface.kt     # JavaScript bridge
│   │   │   └── util/
│   │   │       ├── DeviceInfo.kt      # Telemetri collector
│   │   │       ├── DeviceType.kt      # TV/tablet detection
│   │   │       └── Prefs.kt           # SharedPreferences
│   │   ├── res/
│   │   └── AndroidManifest.xml
│   └── build.gradle.kts
├── build.gradle.kts
├── settings.gradle.kts
└── CLAUDE.md
```

## Build via MQTT (Anbefalet)

Automatiseret build service der bygger APK og uploader til GitHub Releases.

```bash
# Trigger build (husk at opdatere version og versionCode!)
source admin-platform/.env  # eller brug password direkte
mosquitto_pub -h 188.228.60.134 -u admin -P "$MQTT_PASSWORD" \
  -t "build/iocast-android/trigger" \
  -m '{"branch":"main","version":"1.2.0","versionCode":8}'

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

## Backend Integration

MQTT Broker: 188.228.60.134:1883
Admin Platform: infoscreen-admin (same repo)

## Telemetri Data

DeviceInfo.kt sender følgende i `devices/{id}/telemetry`:

| Felt | Beskrivelse |
|------|-------------|
| deviceId | Unik device identifier |
| timestamp | Unix timestamp |
| appVersion | IOCast app version |
| androidVersion | Android OS version |
| manufacturer | Device manufacturer |
| model | Device model |
| batteryLevel | Batteri % |
| batteryCharging | true/false |
| batteryTemperature | Celsius |
| cpuTemperature | Celsius (hvis tilgængelig) |
| networkConnected | true/false |
| wifiSsid | WiFi netværksnavn |
| wifiSignal | RSSI (dBm) |
| ipAddress | Device IP adresse |
| macAddress | WiFi MAC adresse |
| memoryTotal/Free | RAM i MB |
| storageTotal/Free | Disk i MB |
| screenOn | true/false |
| uptime | Sekunder siden boot |
| currentUrl | Aktuel WebView URL |

## GitHub Releases

APK'er uploades automatisk til: https://github.com/ufi-tech/iocast-android/releases

```bash
# List releases
gh release list --repo ufi-tech/iocast-android

# Download latest APK
gh release download --repo ufi-tech/iocast-android --pattern "*.apk"
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
