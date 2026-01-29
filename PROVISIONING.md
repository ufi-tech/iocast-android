# IOCast Android TV - Provisioning Guide

## Thomson 240G Specifikationer

| Key | Value |
|-----|-------|
| **Model** | Thomson 240G |
| **Android Version** | 12 (API 31) |
| **Nuværende IP** | 192.168.40.88 |
| **ADB Port** | 5555 |

---

## Installerede Pakker (Komplet Liste)

### 🎬 Streaming Apps (KAN DEAKTIVERES)

| Pakke | App | Status |
|-------|-----|--------|
| `com.amazon.amazonvideo.livingroom` | Prime Video | Deaktiver |
| `dk.tv2.tv2playtv` | TV2 Play | Deaktiver |
| `dk.dr.tvplayer` | DR TV | Deaktiver |
| `com.viaplay.android` | Viaplay | Deaktiver |
| `com.disney.disneyplus` | Disney+ | Deaktiver |
| `com.google.android.youtube.tv` | YouTube | Deaktiver |
| `com.google.android.youtube.tvmusic` | YouTube Music | Deaktiver |
| `com.netflix.ninja` | Netflix | Deaktiver |
| `com.google.android.videos` | Google TV | Deaktiver |
| `com.google.android.play.games` | Play Games | Deaktiver |

### 🛒 Store & Updates (KAN DEAKTIVERES)

| Pakke | App | Status |
|-------|-----|--------|
| `com.android.vending` | Play Store | Deaktiver |
| `android.autoinstalls.config.google.gtvpai` | Auto Install | Deaktiver |

### 🏠 Launcher (DEAKTIVER EFTER IOCast ER SAT SOM HOME)

| Pakke | App | Status |
|-------|-----|--------|
| `com.google.android.apps.tv.launcherx` | Google TV Launcher | Deaktiver |

### ✅ IOCast (VORES APP)

| Pakke | App | Status |
|-------|-----|--------|
| `dk.iocast.kiosk` | IOCast Kiosk | ✅ Installeret |

### ⚠️ KRITISKE SYSTEM APPS (MÅ IKKE RØRES!)

| Pakke | Formål | Konsekvens hvis deaktiveret |
|-------|--------|------------------------------|
| `com.android.systemui` | System UI | TV crasher |
| `com.google.android.gms` | Google Play Services | Apps virker ikke |
| `com.android.tv.settings` | TV Settings | Ingen WiFi/settings adgang |
| `com.droidlogic.tv.settings` | Droidlogic Settings | Ingen adgang til avancerede indstillinger |
| `com.google.android.gsf` | Google Services Framework | Apps crasher |
| `com.android.providers.settings` | Settings Provider | System fejler |
| `com.android.bluetooth` | Bluetooth | Ingen fjernbetjening |
| `com.nes.blerc` | Bluetooth RCU | Fjernbetjening virker ikke |
| `com.google.android.katniss` | Google Assistant/Voice | Voice search |
| `com.google.android.webview` | WebView | IOCast virker ikke! |

---

## Lockdown Script

### Fuld Provisioning Script

```bash
#!/bin/bash
# IOCast Android TV Provisioning Script
# Version: 1.0
# Testet på: Thomson 240G (Android 12)

set -e

# === KONFIGURATION ===
TV_IP="${1:-192.168.40.88}"
APK_PATH="${2:-iocast-v1.0.3-permissions-fix.apk}"

echo "============================================"
echo "  IOCast Android TV Provisioning"
echo "============================================"
echo "TV IP: $TV_IP"
echo "APK: $APK_PATH"
echo ""

# === 1. CONNECT ===
echo "[1/7] Forbinder til TV..."
adb connect $TV_IP:5555
sleep 2

# Verificer forbindelse
if ! adb -s $TV_IP:5555 shell echo "OK" > /dev/null 2>&1; then
    echo "FEJL: Kunne ikke forbinde til $TV_IP:5555"
    echo "Sørg for at ADB Debugging er aktiveret på TV'et:"
    echo "  Settings → Device Preferences → Developer options → USB debugging"
    exit 1
fi
echo "✓ Forbundet"

# === 2. INSTALLER APK ===
echo ""
echo "[2/7] Installerer IOCast APK..."
adb -s $TV_IP:5555 install -r "$APK_PATH"
echo "✓ APK installeret"

# === 3. SÆT SOM DEFAULT LAUNCHER ===
echo ""
echo "[3/7] Sætter IOCast som default launcher..."
adb -s $TV_IP:5555 shell cmd package set-home-activity dk.iocast.kiosk/.MainActivity
echo "✓ IOCast sat som home activity"

# === 4. DEAKTIVER STREAMING APPS ===
echo ""
echo "[4/7] Deaktiverer streaming apps..."

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

for pkg in "${BLOATWARE[@]}"; do
    echo "  Deaktiverer: $pkg"
    adb -s $TV_IP:5555 shell pm disable-user --user 0 $pkg 2>/dev/null || echo "    (ikke fundet)"
done
echo "✓ Streaming apps deaktiveret"

# === 5. DEAKTIVER PLAY STORE ===
echo ""
echo "[5/7] Deaktiverer Play Store (stopper auto-opdateringer)..."
adb -s $TV_IP:5555 shell pm disable-user --user 0 com.android.vending
adb -s $TV_IP:5555 shell pm disable-user --user 0 android.autoinstalls.config.google.gtvpai 2>/dev/null || true
echo "✓ Play Store deaktiveret"

# === 6. DEAKTIVER STANDARD LAUNCHER ===
echo ""
echo "[6/7] Deaktiverer Google TV launcher..."
adb -s $TV_IP:5555 shell pm disable-user --user 0 com.google.android.apps.tv.launcherx
echo "✓ Standard launcher deaktiveret"

# === 7. SKÆRM-INDSTILLINGER ===
echo ""
echo "[7/7] Konfigurerer skærm-indstillinger..."

# Slå skærm-timeout fra (maks værdi)
adb -s $TV_IP:5555 shell settings put system screen_off_timeout 2147483647

# Deaktiver screensaver
adb -s $TV_IP:5555 shell settings put secure screensaver_enabled 0

# Hold skærm tændt når tilsluttet strøm (AC=1, USB=2, begge=3)
adb -s $TV_IP:5555 shell settings put global stay_on_while_plugged_in 3

echo "✓ Skærm-indstillinger konfigureret"

# === DONE ===
echo ""
echo "============================================"
echo "  ✅ Provisioning komplet!"
echo "============================================"
echo ""
echo "Test med Home-knap:"
adb -s $TV_IP:5555 shell input keyevent KEYCODE_HOME
echo ""
echo "TV'et skulle nu vise IOCast setup-skærmen."
echo ""
echo "Næste skridt:"
echo "1. Indtast kunde-kode i IOCast"
echo "2. Verificer at TV starter i IOCast efter reboot"
echo ""
```

### Kør Scriptet

```bash
cd /Volumes/abiler/Projeckter/Skamstrup\ Recover/iocast-android

# Med default IP
./provision-tv.sh

# Med specifik IP
./provision-tv.sh 192.168.40.88

# Med specifik IP og APK
./provision-tv.sh 192.168.40.88 iocast-v1.0.4.apk
```

---

## Revert Script (Nulstil til Normal)

```bash
#!/bin/bash
# Genaktiver alle apps og nulstil launcher

TV_IP="${1:-192.168.40.88}"

echo "Nulstiller TV til normal..."

# Re-enable launcher først
adb -s $TV_IP:5555 shell pm enable --user 0 com.google.android.apps.tv.launcherx

# Re-enable Play Store
adb -s $TV_IP:5555 shell pm enable --user 0 com.android.vending

# Re-enable streaming apps
adb -s $TV_IP:5555 shell pm enable --user 0 com.amazon.amazonvideo.livingroom
adb -s $TV_IP:5555 shell pm enable --user 0 dk.tv2.tv2playtv
adb -s $TV_IP:5555 shell pm enable --user 0 dk.dr.tvplayer
adb -s $TV_IP:5555 shell pm enable --user 0 com.viaplay.android
adb -s $TV_IP:5555 shell pm enable --user 0 com.disney.disneyplus
adb -s $TV_IP:5555 shell pm enable --user 0 com.google.android.youtube.tv
adb -s $TV_IP:5555 shell pm enable --user 0 com.netflix.ninja

# Nulstil skærm-timeout (30 min = 1800000 ms)
adb -s $TV_IP:5555 shell settings put system screen_off_timeout 1800000

# Genaktiver screensaver
adb -s $TV_IP:5555 shell settings put secure screensaver_enabled 1

echo "✅ TV nulstillet. Genstart TV'et."
```

---

## Remote APK Update

### Metode 1: Via ADB over netværk (Simpelt, anbefalet til få enheder)

```bash
# Opdater APK remotely - virker når TV har ADB aktiveret
adb connect 192.168.40.88:5555
adb -s 192.168.40.88:5555 install -r iocast-v1.0.4.apk
```

**Script til mass-update:**
```bash
#!/bin/bash
# update-all-tvs.sh
APK="iocast-v1.0.4.apk"

# Liste over TV IP'er (fra admin platform database)
TVS=(
    "192.168.40.88"
    "192.168.1.100"
    "10.0.0.50"
)

for tv in "${TVS[@]}"; do
    echo "Opdaterer: $tv"
    adb connect $tv:5555
    adb -s $tv:5555 install -r "$APK" && echo "✓ $tv opdateret" || echo "✗ $tv fejlede"
    adb disconnect $tv:5555
done
```

### Metode 2: Via MQTT Kommando + Download (Til mange enheder)

**Sådan virker det:**
1. Send MQTT kommando: `devices/{deviceId}/cmd/update`
2. IOCast downloader APK via DownloadManager
3. Android viser "Installer" dialog
4. Bruger bekræfter (eller auto-install hvis Device Owner)

**Kræver implementation i CommandHandler.kt** (TODO):
```kotlin
"update" -> handleUpdate(json)

private fun handleUpdate(json: JSONObject): CommandResult {
    val apkUrl = json.optString("url", "")
    if (apkUrl.isEmpty()) return CommandResult(false, "Missing 'url'")

    val request = DownloadManager.Request(Uri.parse(apkUrl))
        .setTitle("IOCast Update")
        .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
        .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, "iocast-update.apk")

    val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
    dm.enqueue(request)

    return CommandResult(true, "Download started")
}
```

### Metode 3: Auto-update Check ved Start (Produktion)

IOCast kan tjekke for opdateringer ved opstart:

```kotlin
// I MainActivity.kt
private fun checkForUpdates() {
    val response = URL("https://iocast.dk/api/version.json").readText()
    val json = JSONObject(response)
    val latestVersion = json.getString("latestVersion")
    val currentVersion = packageManager.getPackageInfo(packageName, 0).versionName

    if (latestVersion > currentVersion) {
        downloadUpdate(json.getString("apkUrl"))
    }
}
```

**Server endpoint (version.json):**
```json
{
  "latestVersion": "1.0.4",
  "versionCode": 5,
  "apkUrl": "https://iocast.dk/releases/iocast-v1.0.4.apk",
  "changelog": "- Bug fixes\n- Performance improvements",
  "forceUpdate": false
}
```

---

### Anbefaling

| Scenario | Metode |
|----------|--------|
| **1-10 enheder** | ADB over netværk (Metode 1) |
| **10-50 enheder** | MQTT + Download (Metode 2) |
| **50+ enheder** | Auto-update check (Metode 3) |

**For nu: Brug Metode 1 (ADB)** - det virker allerede!

---

## Automatisk Provisioning (Fremtidig)

### QR-kode Flow

1. **Admin Dashboard:** Generer QR med provisioning data
2. **Ny Android TV:** Scan QR → Downloader IOCast → Auto-konfigurerer

### QR Payload

```json
{
  "type": "iocast-provision",
  "apkUrl": "https://iocast.dk/releases/iocast-v1.0.3.apk",
  "mqttBroker": "tcp://188.228.60.134:1883",
  "customerCode": "1234",
  "autoLockdown": true
}
```

---

## ADB Debugging Aktivering på Thomson TV

1. **Settings** → **Device Preferences** → **About**
2. Tryk 7 gange på **Build number** → "Du er nu udvikler"
3. Gå tilbage til **Device Preferences** → **Developer options**
4. Aktiver **USB debugging** (eller **Network debugging**)
5. Første gang du forbinder: Godkend på TV'et

---

## Troubleshooting

### ADB kan ikke forbinde

```bash
# Scan netværk for ADB devices
for ip in 192.168.40.{1..254}; do
  (nc -z -w1 $ip 5555 2>/dev/null && echo "Found: $ip") &
done; wait
```

### IOCast starter ikke som launcher

```bash
# Tjek nuværende launcher
adb shell cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME

# Sæt manuelt
adb shell cmd package set-home-activity dk.iocast.kiosk/.MainActivity
```

### Apps kan ikke deaktiveres

```bash
# Tjek om pakken findes
adb shell pm list packages | grep <pakkenavn>

# Prøv at tvinge stop først
adb shell am force-stop <pakkenavn>
adb shell pm disable-user --user 0 <pakkenavn>
```

### Factory Reset (sidste udvej)

```bash
adb shell am broadcast -a android.intent.action.FACTORY_RESET
```
