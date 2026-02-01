# Deprecated Scripts

Disse scripts er forældede og bruges ikke længere i production.

## build-apk.sh

**Status:** ⛔ Deprecated (flyttet 2025-01-31)

**Hvorfor deprecated:**
- Brugte lokal Docker build med `mingc/android-build-box`
- Ingen konsistent APK signing (bryder OTA updates)
- Ingen automatisk upload til GitHub Releases
- Kræver lokal Docker setup

**Brug i stedet:**
- `build-release.sh` - MQTT-baseret build med auto version increment
- MQTT build service på ufitechbox-docker-01 (172.18.0.101)
- Se `/iocast-build` skill for fuld dokumentation

**Hvorfor bevaret:**
- Reference til historisk build metode
- Kan være nyttig til lokal debugging hvis MQTT service er nede
- Dokumenterer forskellen mellem lokal vs. server builds
