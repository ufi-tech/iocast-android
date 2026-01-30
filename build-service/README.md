# IOCast Android Build Service

MQTT-triggered Android build service that automatically builds APKs and uploads to GitHub Releases.

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

## Quick Start

### 1. Setup

```bash
# SSH to build server
ssh -J ingress-01 ubuntu@172.18.0.101

# Clone repo
cd /opt
git clone https://github.com/ufi-tech/iocast-android.git
cd iocast-android/build-service

# Configure environment
cp .env.example .env
nano .env  # Fill in MQTT_PASSWORD and GITHUB_TOKEN
```

### 2. Start Service

```bash
docker-compose up -d
docker-compose logs -f  # Monitor logs
```

### 3. Trigger a Build

```bash
mosquitto_pub -h 188.228.60.134 -u admin -P <password> \
  -t "build/iocast-android/trigger" \
  -m '{"branch":"main","version":"1.3.0","versionCode":8}'
```

### 4. Monitor Build

```bash
mosquitto_sub -h 188.228.60.134 -u admin -P <password> \
  -t "build/iocast-android/#" -v
```

## MQTT Topics

| Topic | Direction | Description |
|-------|-----------|-------------|
| `build/iocast-android/trigger` | → Service | Trigger new build |
| `build/iocast-android/cancel` | → Service | Cancel current build |
| `build/iocast-android/status` | ← Service | Build status (retained) |
| `build/iocast-android/progress` | ← Service | Progress updates |
| `build/iocast-android/result` | ← Service | Build result (retained) |

## Trigger Payload

```json
{
  "branch": "main",
  "version": "1.3.0",
  "versionCode": 8,
  "requestedBy": "admin-platform",
  "timestamp": 1706612345
}
```

## Status Responses

### Progress

```json
{
  "progress": 45,
  "step": "Compiling Kotlin sources",
  "branch": "main",
  "version": "1.3.0",
  "startedAt": 1706612345,
  "timestamp": 1706612400
}
```

### Success

```json
{
  "status": "success",
  "version": "1.3.0",
  "versionCode": 8,
  "apkUrl": "https://github.com/ufi-tech/iocast-android/releases/download/v1.3.0/iocast-v1.3.0.apk",
  "apkSize": 12345678,
  "sha256": "abc123...",
  "buildTime": 180,
  "timestamp": 1706612525
}
```

### Failed

```json
{
  "status": "failed",
  "version": "1.3.0",
  "versionCode": 8,
  "error": "Build failed: Gradle daemon crashed",
  "buildTime": 45,
  "timestamp": 1706612390
}
```

## Environment Variables

| Variable | Default | Description |
|----------|---------|-------------|
| `MQTT_HOST` | 188.228.60.134 | MQTT broker host |
| `MQTT_PORT` | 1883 | MQTT broker port |
| `MQTT_USER` | admin | MQTT username |
| `MQTT_PASSWORD` | - | MQTT password (required) |
| `GITHUB_TOKEN` | - | GitHub personal access token (required) |
| `GITHUB_REPO` | ufi-tech/iocast-android | GitHub repository |
| `BUILD_TIMEOUT` | 1800 | Build timeout in seconds |
| `DOCKER_IMAGE` | mingc/android-build-box:latest | Docker image for builds |

## GitHub Token

Create a Personal Access Token with these scopes:
- `repo` - Full repository access
- `write:packages` - Upload releases

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

### Service not connecting to MQTT

```bash
# Check MQTT broker accessibility
mosquitto_pub -h 188.228.60.134 -u admin -P <password> -t test -m "hello"
```

### Build fails

```bash
# Check logs
docker-compose logs -f build-service

# Test Docker access
docker run --rm hello-world
```

### GitHub upload fails

```bash
# Verify token
curl -H "Authorization: token YOUR_TOKEN" https://api.github.com/user
```
