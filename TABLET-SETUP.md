# IOCast Tablet Setup Guide

## Lenovo TB-X606F (Tab M10 FHD Plus)

Denne guide dækker setup af Lenovo tablets til IOCast kiosk-brug, inklusiv bootloader unlock og FRP bypass.

---

## Device Specifikationer

| Key | Value |
|-----|-------|
| **Model** | Lenovo TB-X606F (Tab M10 FHD Plus) |
| **Chipset** | MediaTek Helio P22T (MT6765) |
| **Android** | 9.0 → 11 (via OTA) |
| **Storage** | 64GB eMMC |
| **RAM** | 4GB |
| **Display** | 10.3" FHD (1920x1200) |

---

## Situation: Låst Tablet

Hvis tabletten er låst med:
- PIN/mønster du ikke kender
- Google-konto (FRP lock)
- Bootloader låst

**Løsning: MTKClient BROM Exploit**

---

## MTKClient Installation (macOS)

### 1. Installer Dependencies

```bash
# Homebrew dependencies
brew install python libusb

# Clone mtkclient
cd ~/Downloads
git clone https://github.com/bkerler/mtkclient.git
cd mtkclient

# Python dependencies
pip3 install -r requirements.txt

# Ekstra modules for sudo
sudo pip3 install pycryptodomex colorama pyusb pyserial
```

### 2. Verificer Installation

```bash
cd ~/Downloads/mtkclient
python3 mtk.py --help
```

---

## BROM Mode Entry

**BROM (Boot ROM)** er en lavniveau tilstand der aktiveres FØR Android booter. Dette tillader MTKClient at bypasse sikkerhedsforanstaltninger.

### Sådan Entrer du BROM Mode:

1. **Sluk tabletten helt** (hold power 15 sek)
2. **Start MTKClient** på Mac
3. **Hold BEGGE volume-knapper** (Vol Up + Vol Down)
4. **Tilslut USB-kablet** mens du holder knapperne
5. **Hold knapperne i 10 sekunder**
6. Skærmen skal forblive **sort/slukket**

**Hvis du ser Lenovo logo eller tekst = IKKE i BROM mode**

---

## FRP Bypass (Factory Reset Protection)

FRP bypass fjerner Google-konto verifikation så du kan bruge tabletten uden den originale Google-konto.

### Kommandoer

```bash
cd ~/Downloads/mtkclient

# 1. Slet FRP partition (kræver sudo for USB permissions)
sudo python3 mtk.py e frp

# 2. Slet userdata, metadata og persist for ren start
sudo python3 mtk.py e userdata,metadata,persist
```

### Forventet Output

```
Port - Device detected :)
Preloader - CPU: MT6765/MT8768t(Helio P35/G35)
Preloader - BROM mode detected.
DaHandler - Device is in BROM-Mode. Bypassing security.
PLTools - Loading payload from mt6765_payload.bin
Exploitation - Kamakiri Run
Exploitation - Done sending payload...
PLTools - Successfully sent payload
...
DAXFlash - Formatting addr 0x3588000 with length 0x100000
Done |██████████| 100.0%
Formatted sector 109632 with sector count 2048.
```

---

## Bootloader Unlock

**Note:** Lenovo TB-X606F har DAA (Download Agent Authentication) aktiveret, som gør det svært at unlocke bootloader via MTKClient. FRP bypass virker dog stadig.

### Forsøg på Bootloader Unlock

```bash
# Prøv via MTKClient (virker muligvis ikke pga. DAA)
sudo python3 mtk.py da seccfg unlock

# Via fastboot (kræver OEM unlock aktiveret i Android først)
fastboot flashing unlock
```

### Typisk Fejl

```
DaHandler - Device has is either already unlocked or algo is unknown. Aborting.
```

Dette betyder at Lenovo's seccfg format ikke er supporteret af MTKClient.

### Alternativ: Aktivér OEM Unlock i Android

Hvis du kan komme ind i Android:

1. **Settings → About tablet → Tap "Build number" 7 gange**
2. **Settings → Developer options → Enable "OEM unlocking"**
3. Boot til fastboot: `adb reboot bootloader`
4. `fastboot flashing unlock`

---

## Efter FRP Bypass

Når FRP er slettet:

1. **Boot tabletten** - hold Power-knappen
2. **Initial setup** - Vælg sprog, WiFi, **SKIP Google login**
3. **Aktivér Developer Options** - Settings → About → tap Build Number 7x
4. **Aktivér USB Debugging** - Settings → Developer Options → USB debugging
5. **Installer IOCast via ADB**

```bash
adb devices
adb install iocast-v1.0.5-ack-fix.apk
```

---

## IOCast Provisioning på Tablet

### 1. Installer APK

```bash
adb install -r iocast-v1.0.5-ack-fix.apk
```

### 2. Sæt som Default Launcher

```bash
adb shell cmd package set-home-activity dk.iocast.kiosk/.MainActivity
```

### 3. Deaktiver Bloatware

```bash
# Lenovo-specifikke apps
adb shell pm disable-user --user 0 com.lenovo.browser
adb shell pm disable-user --user 0 com.lenovo.lsf.user
adb shell pm disable-user --user 0 com.lenovo.lfh

# Google apps
adb shell pm disable-user --user 0 com.android.vending  # Play Store
adb shell pm disable-user --user 0 com.google.android.youtube
adb shell pm disable-user --user 0 com.google.android.gm  # Gmail
```

### 4. Skærm-indstillinger

```bash
# Slå skærm-timeout fra
adb shell settings put system screen_off_timeout 2147483647

# Deaktiver screensaver
adb shell settings put secure screensaver_enabled 0

# Hold skærm tændt når tilsluttet strøm
adb shell settings put global stay_on_while_plugged_in 3
```

---

## Device Info Commands

### Tjek Bootloader Status

```bash
# Boot til fastboot
adb reboot bootloader

# Tjek status
fastboot getvar unlocked
fastboot getvar all
```

### Hent Device Info via MTKClient

```bash
sudo python3 mtk.py gettargetconfig
```

Output:
```
Preloader - CPU: MT6765/MT8768t(Helio P35/G35)
Preloader - Target config: 0xe5
Preloader - SBC enabled: True
Preloader - DAA enabled: True
Preloader - ME_ID: 41ACF2D25FB702825B54578CBCE2960A
Preloader - SOC_ID: 96CAD1047DC29765C9DCE9844E94C897...
```

---

## Kendte Problemer

### 1. "Auth file is required"

**Problem:** MTKClient kræver auth fil
**Løsning:** Brug BROM exploit - MTKClient bypasser automatisk med Kamakiri

### 2. USB Permissions Error

```
[Errno 13] Access denied (insufficient permissions)
```

**Løsning:** Kør med sudo:
```bash
sudo python3 mtk.py e frp
```

### 3. "Handshake failed, retrying"

**Problem:** Tabletten er ikke i BROM mode
**Løsning:** Fjern USB, sluk tabletten helt, prøv igen med Vol Up + Vol Down

### 4. Fastboot Unlock Denied

```
FAILED (remote: 'Unlock operation is not allowed')
```

**Problem:** OEM unlock ikke aktiveret i Android
**Løsning:** Brug FRP bypass først, derefter aktivér OEM unlock i Android

---

## Sikkerhedsadvarsler

| Advarsel | Beskrivelse |
|----------|-------------|
| **Data wipe** | FRP bypass sletter ALT data |
| **Garanti** | Voids warranty |
| **Brick risk** | Fejl i BROM mode kan bricke enheden |
| **Ingen OTA** | Officielle opdateringer virker muligvis ikke |

---

## Kilder

- [MTKClient GitHub](https://github.com/bkerler/mtkclient)
- [XDA: FRP Bypass TB-X606F](https://xdaforums.com/t/finally-frp-bypass-tb-x606f.4579857/)
- [DroidWin: MTKClient Bootloader Unlock](https://droidwin.com/unlock-bootloader-on-mediatek-devices-using-mtkclient/)

---

## Changelog

### 2026-01-30
- Initial dokumentation baseret på succesfuld FRP bypass af TB-X606F (serial: HVA3SSH4)
- Verificeret at Kamakiri exploit virker på MT6765 chipset
- Dokumenteret DAA begrænsning for bootloader unlock
