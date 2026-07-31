# M110 Label — Android App

Standalone Android-App für den **Phomemo M110** Thermo-Labeldrucker. Verbindet sich
**direkt per Bluetooth Classic (RFCOMM/SPP)** — kein Pi/Server nötig. Portiert vom
Raster-Protokoll des Pi-Dienstes in diesem Repo.

## Features

- **Direkter Bluetooth-Druck** (SPP-UUID `00001101-…`), 384-dot-Kopf, 1-bit-Raster (`ESC @` → `GS v 0` → Bilddaten).
- **Einheitliche Druckvorschau**: Jeder Inhalt erzeugt ein Label in der gewählten Größe → zentrale Vorschau → ein `DRUCKEN`-Button (was du siehst, kommt raus).
- **Inhalte**: Text (Größe/Ausrichtung, mehrzeilig), Foto/Bild (Floyd–Steinberg-Dithering), QR-Code & Code128-Barcode (ZXing), KI-Piktogramm (OpenAI `gpt-image-1`, eigener API-Key).
- **Label-Formate**: 40×30, 50×30, 30×50, 50×25, 25×25 mm (wird gespeichert).
- **Ausrichtung**: Drehung 0/90/180/270°, Feinzentrierung per X/Y-Offset. Standard-Kopfkorrektur 28 dot → druckt ab Werk mittig (nur beim Drucken angewandt, Vorschau bleibt zentriert).
- **Komfort**: letztes BT-Gerät gespeichert + Auto-Connect, dauerhafter Verbindungsstatus.

## Bekannte Fallen (gelöst)

- **Doppel-Label**: Kein Abschluss-Vorschub (`ESC d n`) nach den Bilddaten senden — bei 40×30-Gap-Labels triggert das den Gap-Sensor und wirft ein leeres Zweit-Label aus.

## Build

```bash
# benötigt: JDK 17, Android SDK Platform 34 + Build-Tools 34.0.0
export ANDROID_HOME=$HOME/android-sdk
gradle assembleDebug        # oder ./gradlew, falls Wrapper vorhanden
# APK: app/build/outputs/apk/debug/app-debug.apk
```

`minSdk 24`, `targetSdk 34`. Einzige externe Abhängigkeit: `com.google.zxing:core` (QR/Barcode).

## Nutzung

1. M110 in den Android-Bluetooth-Einstellungen koppeln.
2. App öffnen → Gerät wählen → **Verbinden** (danach Auto-Connect).
3. Label-Format wählen, Inhalt erzeugen (Text/Bild/QR/Barcode/KI) → **DRUCKEN**.

Für KI-Piktogramme einen eigenen OpenAI-API-Key eintragen (wird lokal gespeichert).
