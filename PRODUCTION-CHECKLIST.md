# IOCast Tablet - Produktions Checkliste

## Pre-Shipping Checkliste

Brug denne checkliste før du sender en tablet til en kunde.

### Hardware

- [ ] Tablet er fuldt opladet (100%)
- [ ] Strømforsyning medfølger
- [ ] USB-kabel medfølger (til evt. fejlfinding)
- [ ] Tablet holder/stativ medfølger (hvis relevant)

### Software Setup

Kør provisioning scriptet:
```bash
./provision-tablet.sh
```

Eller verificer manuelt:

- [ ] IOCast APK installeret (seneste version)
- [ ] IOCast sat som default launcher
- [ ] WiFi konfigureret til kundens netværk
- [ ] MQTT forbindelse verificeret (device vises i admin)

### System Indstillinger

| Indstilling | Forventet Værdi | Kommando |
|-------------|-----------------|----------|
| Lock screen | Deaktiveret | `adb shell settings get secure lockscreen.disabled` → `1` |
| Skærm timeout | Max (2147483647) | `adb shell settings get system screen_off_timeout` |
| Stay awake | 7 (alle strømkilder) | `adb shell settings get global stay_on_while_plugged_in` |
| Auto-rotate | Deaktiveret (0) | `adb shell settings get system accelerometer_rotation` |
| Rotation | Landscape (1) | `adb shell settings get system user_rotation` |
| WiFi sleep | Aldrig (2) | `adb shell settings get global wifi_sleep_policy` |
| Lydstyrke | 0 | `adb shell settings get system volume_music` |
| Heads-up | Deaktiveret (0) | `adb shell settings get global heads_up_notifications_enabled` |

### Battery Optimization

- [ ] IOCast på battery whitelist
  ```bash
  adb shell dumpsys deviceidle whitelist | grep iocast
  # Skal vise: dk.iocast.kiosk
  ```

- [ ] Doze mode deaktiveret
  ```bash
  adb shell dumpsys deviceidle
  # Skal vise: mDeepEnabled=false, mLightEnabled=false
  ```

### Deaktiverede Apps

Verificer at disse er deaktiveret:
```bash
adb shell pm list packages -d | grep -E "lenovo|launcher|vending|setupwizard"
```

Forventet output:
```
package:com.tblenovo.launcher
package:com.lenovo.ota
package:com.android.vending
package:com.google.android.setupwizard
```

### Netværk

- [ ] WiFi forbundet til kundens netværk
- [ ] MQTT broker tilgængelig fra netværket
- [ ] Test: Device vises som "online" i admin dashboard

### Funktionstest

| Test | Forventet Resultat |
|------|-------------------|
| Tryk Home-knap | IOCast forbliver på skærmen |
| Genstart tablet | IOCast starter automatisk, ingen lock screen |
| Vent 5 min | Skærm forbliver tændt |
| Send reload via admin | WebView genindlæser |
| Send screenshot via admin | Screenshot modtages i admin |

### Quick Verification Script

Kør dette for at verificere alle indstillinger:

```bash
#!/bin/bash
DEVICE="${1:-}"
[ -z "$DEVICE" ] && DEVICE=$(adb devices | grep -v List | awk 'NR==1{print $1}')

echo "Verificerer tablet: $DEVICE"
echo ""

adb -s "$DEVICE" shell "
echo '=== LAUNCHER ==='
cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME | tail -1

echo ''
echo '=== BATTERY WHITELIST ==='
dumpsys deviceidle whitelist | grep iocast && echo 'OK' || echo 'FEJL: Ikke på whitelist!'

echo ''
echo '=== LOCK SCREEN ==='
VAL=\$(settings get secure lockscreen.disabled)
[ \"\$VAL\" = \"1\" ] && echo \"OK: Deaktiveret\" || echo \"FEJL: lockscreen.disabled=\$VAL\"

echo ''
echo '=== SKÆRM TIMEOUT ==='
VAL=\$(settings get system screen_off_timeout)
[ \"\$VAL\" = \"2147483647\" ] && echo \"OK: Max\" || echo \"ADVARSEL: timeout=\$VAL\"

echo ''
echo '=== AUTO-ROTATE ==='
VAL=\$(settings get system accelerometer_rotation)
[ \"\$VAL\" = \"0\" ] && echo \"OK: Deaktiveret\" || echo \"FEJL: auto-rotate=\$VAL\"

echo ''
echo '=== LYDSTYRKE ==='
VAL=\$(settings get system volume_music)
[ \"\$VAL\" = \"0\" ] && echo \"OK: Muted\" || echo \"ADVARSEL: volume=\$VAL\"

echo ''
echo '=== WIFI ==='
dumpsys wifi | grep 'mWifiInfo' | head -1

echo ''
echo '=== MQTT STATUS ==='
logcat -d -t 10 | grep -i mqtt | tail -3
"
```

---

## Troubleshooting

### Tablet starter ikke IOCast efter reboot

```bash
# Verificer launcher
adb shell cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME

# Sæt igen
adb shell cmd package set-home-activity dk.iocast.kiosk/.MainActivity
```

### Lock screen vises stadig

```bash
adb shell locksettings clear --old ''
adb shell locksettings set-disabled true
adb shell settings put secure lockscreen.disabled 1
```

### MQTT forbinder ikke

1. Tjek WiFi forbindelse
2. Tjek at MQTT broker er tilgængelig: `ping 188.228.60.134`
3. Tjek IOCast logs: `adb logcat -s MqttService:* MqttClient:*`

### Skærm slukker

```bash
adb shell settings put system screen_off_timeout 2147483647
adb shell settings put global stay_on_while_plugged_in 7
adb shell dumpsys deviceidle whitelist +dk.iocast.kiosk
```

### App crasher / bliver dræbt

```bash
# Tilføj til battery whitelist
adb shell dumpsys deviceidle whitelist +dk.iocast.kiosk

# Deaktiver battery optimization helt
adb shell dumpsys deviceidle disable
```

---

## Remote Management

Efter deployment kan tabletten styres via:

1. **Admin Dashboard**: http://192.168.40.152:3000
2. **MQTT Commands**: `devices/{deviceId}/cmd/*`
3. **ADB over WiFi** (hvis aktiveret): `adb connect <tablet-ip>:5555`

### Finde Tablet IP

Tablet IP vises i admin dashboard under device telemetri, eller:

```bash
adb shell ip route | grep wlan0
```

---

## Changelog

### 2026-01-30
- Initial produktions-checkliste
- Verificeret på Lenovo TB-X606F (HVA3SSH4)
