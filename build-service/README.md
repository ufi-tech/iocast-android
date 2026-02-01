# IOCast Android Build Service

MQTT-triggered Android build service that automatically builds APKs and uploads to GitHub Releases.

## Quick Reference

| Key | Value |
|-----|-------|
| **Server** | `ufitechbox-docker-01` (172.18.0.101) |
| **Container** | `iocast-build-service` |
| **MQTT Broker** | 188.228.60.134:1883 |
| **GitHub Repo** | [ufi-tech/iocast-android](https://github.com/ufi-tech/iocast-android) |
| **Build Time** | ~90 sekunder |
| **Path on Server** | `/opt/iocast-build-service/` |

## Architecture

```
┌─────────────────────────────────────────────────────────────┐
│              ufitechbox-docker-01 (172.18.0.101)            │
│                    MQTT Build Service                        │
├─────────────────────────────────────────────────────────────┤
│   MQTT Subscriber ──► Build Queue ──► Docker Builder        │
│        │                                     │               │
│        ◄─────────── Status Publisher ◄───────┘               │
└─────────────────────────────────────────────────────────────┘
         │                                     │
         ▼                                     ▼
    MQTT Broker                          GitHub API
  (188.228.60.134)                  (ufi-tech/iocast-android)
```

## Trigger a Build

### Via MQTT (primær metode)

```bash
mosquitto_pub -h 188.228.60.134 -u admin -P 'BZs9UBDViukWaZu+1O6Hd77qr+Dshomu' \
  -t "build/iocast-android/trigger" \
  -m '{"branch":"main","version":"2.0.5","versionCode":21}'
```

**Payload felter:**
- `branch` - Git branch (default: `main`)
- `version` - Semantic version (f.eks. `2.0.5`)
- `versionCode` - Android version code (integer, skal øges for hver release)

### Monitor Build Progress

```bash
mosquitto_sub -h 188.228.60.134 -u admin -P 'BZs9UBDViukWaZu+1O6Hd77qr+Dshomu' \
  -t "build/iocast-android/#" -v
```

### Check Logs

```bash
ssh -J ingress-01 ubuntu@172.18.0.101 "docker logs iocast-build-service --tail 30"
```

## MQTT Topics

| Topic | Retning | Beskrivelse |
|-------|---------|-------------|
| `build/iocast-android/trigger` | → Service | Trigger nyt build |
| `build/iocast-android/cancel` | → Service | Afbryd igangværende build |
| `build/iocast-android/status` | ← Service | Build status (retained) |
| `build/iocast-android/progress` | ← Service | Progress updates |
| `build/iocast-android/result` | ← Service | Build resultat (retained) |

## Build Process

1. **Clone** (10%) - Cloner GitHub repo
2. **Update Version** (20%) - Opdaterer `build.gradle.kts`
3. **Docker Build** (30-85%) - Kører Gradle i Docker container
4. **Checksum** (85%) - Beregner SHA256
5. **Upload** (90%) - Uploader til GitHub Releases
6. **Done** (100%) - Cleanup

## Response Format

### Progress

```json
{
  "progress": 52,
  "step": "Compiling Kotlin sources",
  "branch": "main",
  "version": "2.0.4",
  "startedAt": 1706612345,
  "timestamp": 1706612400
}
```

### Success

```json
{
  "status": "success",
  "version": "2.0.4",
  "versionCode": 20,
  "apkUrl": "https://github.com/ufi-tech/iocast-android/releases/download/v2.0.4/iocast-v2.0.4.apk",
  "apkSize": 12345678,
  "sha256": "abc123...",
  "buildTime": 92,
  "timestamp": 1706612525
}
```

### Failed

```json
{
  "status": "failed",
  "version": "2.0.4",
  "versionCode": 20,
  "error": "Build failed: Gradle daemon crashed",
  "buildTime": 45,
  "timestamp": 1706612390
}
```

## Server Setup

### Initial Deployment

```bash
# SSH til build server
ssh -J ingress-01 ubuntu@172.18.0.101

# Clone repo
cd /opt
git clone https://github.com/ufi-tech/iocast-android.git iocast-build-service
cd iocast-build-service/build-service

# Konfigurer environment
cp .env.example .env
nano .env  # Udfyld credentials

# Start service
docker-compose up -d
```

### Environment Variables

| Variable | Default | Beskrivelse |
|----------|---------|-------------|
| `MQTT_HOST` | 188.228.60.134 | MQTT broker host |
| `MQTT_PORT` | 1883 | MQTT broker port |
| `MQTT_USER` | admin | MQTT brugernavn |
| `MQTT_PASSWORD` | - | MQTT password (påkrævet) |
| `GITHUB_TOKEN` | - | GitHub personal access token (påkrævet) |
| `GITHUB_REPO` | ufi-tech/iocast-android | GitHub repository |
| `BUILD_TIMEOUT` | 1800 | Build timeout i sekunder |
| `DOCKER_IMAGE` | cimg/android:2024.01.1 | Docker image til builds |
| `KEYSTORE_BASE64` | - | Base64-encoded keystore |
| `KEYSTORE_PASSWORD` | - | Keystore password |
| `KEY_ALIAS` | iocast | Key alias |
| `KEY_PASSWORD` | - | Key password |

## Files

```
build-service/
├── build_service.py    # Main MQTT listener
├── builder.py          # Docker build logic
├── github_release.py   # GitHub API integration
├── config.py           # Configuration
├── Dockerfile          # Service container
├── docker-compose.yml  # Docker Compose config
├── requirements.txt    # Python dependencies
├── .env.example        # Environment template
└── README.md           # This file
```

## Troubleshooting

### Service kører ikke

```bash
# Check container status
ssh -J ingress-01 ubuntu@172.18.0.101 "docker ps | grep iocast-build"

# Restart service
ssh -J ingress-01 ubuntu@172.18.0.101 "cd /opt/iocast-build-service/build-service && docker-compose restart"
```

### MQTT connection fejler

```bash
# Test MQTT forbindelse
mosquitto_pub -h 188.228.60.134 -u admin -P 'BZs9UBDViukWaZu+1O6Hd77qr+Dshomu' -t test -m "hello"
```

### Build fejler

```bash
# Check full logs
ssh -J ingress-01 ubuntu@172.18.0.101 "docker logs iocast-build-service --tail 100"

# Check build cache
ssh -J ingress-01 ubuntu@172.18.0.101 "ls -la /opt/iocast-build-service/build-cache/"
```

### GitHub upload fejler

```bash
# Verify token (på serveren)
ssh -J ingress-01 ubuntu@172.18.0.101 "curl -H 'Authorization: token <TOKEN>' https://api.github.com/user"
```

## Signing

APK'en signeres automatisk med release keystore. Keystoren er Base64-encoded i `.env` filen og dekodes under build.

**Keystore detaljer:**
- Alias: `iocast`
- Validity: 25+ år
- Algorithm: RSA 2048

## Integration med Admin Platform

Build service kan integreres med Admin Platform for at trigge builds fra UI:

```python
# Python eksempel
import paho.mqtt.client as mqtt
import json

client = mqtt.Client()
client.username_pw_set("admin", "BZs9UBDViukWaZu+1O6Hd77qr+Dshomu")
client.connect("188.228.60.134", 1883)

payload = {
    "branch": "main",
    "version": "2.0.5",
    "versionCode": 21
}

client.publish("build/iocast-android/trigger", json.dumps(payload))
```
