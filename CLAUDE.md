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
| **Current Version** | 2.0.5 (versionCode 17) |
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
│  Backend matcher kundekode (CustomerCode tabel)             │
│  auto_approve=true → godkendes automatisk                   │
└─────────────────────────────────────────────────────────────┘
                            │
                            ▼
┌─────────────────────────────────────────────────────────────┐
│  MQTT Provision Response                                    │
│                                                             │
│  Topic: provision/{code}/response/{deviceId}                │
│  Payload: {                                                 │
│    approved: true,                                          │
│    startUrl: "https://kunde.screen.iocast.dk/screen/...",   │
│    brokerUrl: "tcp://188.228.60.134:1883",                  │
│    username: "admin",                                       │
│    password: "****",                                        │
│    kioskMode: true,                                         │
│    keepScreenOn: true,                                      │
│    customerId: "uuid",                                      │
│    customerName: "Kundens Navn"                             │
│  }                                                          │
└─────────────────────────────────────────────────────────────┘
                            │
                            ▼
┌─────────────────────────────────────────────────────────────┐
│  App gemmer config i SharedPreferences:                     │
│  - broker_url, username, password (til MqttService)         │
│  - start_url, current_url (til WebView)                     │
│  - setup_complete = true                                    │
└─────────────────────────────────────────────────────────────┘
                            │
                            ▼
┌─────────────────────────────────────────────────────────────┐
│  MainActivity starter:                                      │
│  - WebView loader startUrl                                  │
│  - MqttService starter med credentials fra prefs            │
│  - Subscribes til devices/{deviceId}/cmd/#                  │
│  - Sender telemetri hvert 60. sekund                        │
└─────────────────────────────────────────────────────────────┘
```

### Provisioning via API

Opret en kundekode i backend:
```bash
# Opret customer code med auto-approve
curl -X POST "https://admin.screen.iocast.dk/api/customer-codes" \
  -H "Content-Type: application/json" \
  -d '{
    "code": "1234",
    "customer_id": "uuid-her",
    "start_url": "https://kunde.screen.iocast.dk/screen/uuid",
    "auto_approve": true,
    "kiosk_mode": true,
    "keep_screen_on": true
  }'
```

**Hardcoded MQTT credentials** i `ProvisionConfig.kt` (kun til provisioning):
- Broker: `188.228.60.134:1883`
- Credentials baked into APK for initial connection
- Efter provisioning bruger app credentials fra response

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

## Build & Release

Build ny APK med det automatiserede build script:

```bash
# Auto-increment patch version (2.0.4 → 2.0.5)
./build-release.sh

# Specificér custom version
./build-release.sh 2.1.0

# Specificér både version og versionCode
./build-release.sh 2.1.0 18
```

**Hvad scriptet gør:**
- Læser nuværende version fra `build.gradle.kts`
- Trigger MQTT build på remote server (ufitechbox-docker-01)
- Viser real-time progress bar
- Sender macOS notifikation når færdig
- Uploader til GitHub Releases
- Viser download URL, SHA256 og deployment vejledning

**Dependencies:** `brew install mosquitto jq`

**Troubleshooting:** Se `build-release.sh` eller `/iocast-build` skill for detaljer

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

Alle builds uploades automatisk til: https://github.com/ufi-tech/iocast-android/releases

Download seneste APK: `gh release download --repo ufi-tech/iocast-android --pattern "*.apk"`

## Scripts Oversigt

| Script | Formål | Status |
|--------|--------|--------|
| **build-release.sh** | 🚀 MQTT build + monitoring + notifikationer | ✅ **Primær** |
| **provision-tablet.sh** | Provisionér tablets (Lenovo Tab M10) til kiosk mode | ✅ **Aktiv** |
| **provision-tv.sh** | Provisionér Android TVs (Thomson 240G) til kiosk mode | ✅ **Aktiv** |
| **revert-tv.sh** | Nulstil TV til normal tilstand | ✅ **Utility** |
| **emulator-test.sh** | Test APK i Android emulator (kun dev) | 🔧 **Dev only** |
| **test-apk.sh** | Test APK på fysisk device via USB (kun dev) | 🔧 **Dev only** |
| **.deprecated/build-apk.sh** | ~~Lokal Docker build~~ | ⛔ **Deprecated** |

**Build service scripts** (kører på server):
- `build-service/build_service.py` - MQTT listener
- `build-service/builder.py` - Docker build logic
- `build-service/github_release.py` - GitHub releases integration
- `build-service/config.py` - Configuration
