# Reel Commenter – Native Android-App

Eine Android-App mit schwebendem Button und Accessibility-Service, die automatisch Kommentare auf Instagram Reels postet.

## Funktionen
- **Schwebender Button**: Wird über allen Apps angezeigt (auch Instagram). Ziehbar.
- **Rate-Limit**: Eingebauter Schutz (mindestens 12 Sekunden Pause, einstellbar).
- **Accessibility-Service**: Füllt das Kommentarfeld und klickt auf „Posten“ – ohne Root.
- **Einstellungen**: Eigenen Kommentar-Text und Pausenzeit einstellbar.

## Was du brauchst

### Option A: Android Studio auf PC/Mac (empfohlen, 10 Min.)
1. Installiere [Android Studio](https://developer.android.com/studio).
2. Erstelle ein neues Projekt: **Empty Views Activity** → Name: `ReelCommenter` → Package: `com.reelcommenter` → Language: **Kotlin** → Minimum SDK: **API 26**.
3. Ersetze alle generierten Dateien durch die Dateien aus diesem Ordner.
4. Klicke auf den grünen Pfeil (Run) – Android Studio baut die APK und startet sie auf deinem Handy (USB-Debugging muss aktiv sein) oder du findest die APK unter:
   `app/build/outputs/apk/debug/app-debug.apk`
5. Übertrage die APK auf dein Handy (Telegram, WhatsApp, Bluetooth, Google Drive) und installiere sie.

### Option B: GitHub Actions (Cloud, kein PC nötig, 30 Min.)
Falls du keinen PC hast, erstelle ein kostenloses GitHub-Konto, lade alle diese Dateien in ein neues Repository hoch und nutze die mitgelieferte `.github/workflows/build.yml`. GitHub baut die APK automatisch in der Cloud. Du lädst sie dann herunter.

## Einrichtung auf dem Handy (einmalig, 5 Min.)

1. **APK installieren**: Tippe auf die APK-Datei → „Installieren“ (erlaube ggf. unbekannte Quellen).
2. **App starten**: Öffne „Reel Commenter“.
3. **Berechtigungen erteilen**:
   - Tippe auf **„Accessibility-Service aktivieren“** → Suche „Reel Commenter“ in der Liste und aktiviere den Schalter. Bestätige den Warnhinweis.
   - Tippe auf **„Schwebenden Button starten“** → Erlaube „Über anderen Apps einblenden“.
4. Fertig! Der Button schwebt jetzt über deinem Bildschirm.

## Bedienung

1. Öffne die **Instagram-App** und gehe zu einem Reel.
2. Tippe auf den **pinken schwebenden Button**.
3. Die App sucht das Kommentarfeld, tippt deinen Text ein und postet.
4. Wenn weniger als 12 Sekunden seit dem letzten Kommentar vergangen sind, erscheint ein Toast „Warte noch X Sekunden!“.

## Sicherheitshinweise
- **Nicht mehr als 30–50 Kommentare pro Stunde** posten!
- Wenn Instagram „Aktion blockiert“ anzeigt, sofort pausieren und die Pause auf 20–30 Sekunden erhöhen.
- Instagram ändert sein Layout oft. Wenn die App plötzlich nichts mehr findet, melde dich – dann müssen die Text-Suchbegriffe angepasst werden.

## Dateien im Projekt

| Datei | Zweck |
|-------|-------|
| `build.gradle` (Project) | Gradle-Konfiguration |
| `settings.gradle` | Projekt-Name |
| `gradle.properties` | Build-Einstellungen |
| `app/build.gradle` | App-Modul Konfiguration |
| `app/src/main/AndroidManifest.xml` | Berechtigungen & Services |
| `MainActivity.kt` | Einstellungs-Bildschirm |
| `InstagramAccessibilityService.kt` | Steuert Instagram über Accessibility-API |
| `FloatingButtonService.kt` | Zeigt den schwebenden Button |
| `activity_main.xml` | Layout des Einstellungs-Bildschirms |
| `floating_button.xml` | Layout des schwebenden Buttons |
| `circle_background.xml` | Runder pinker Button-Hintergrund |
| `accessibility_service_config.xml` | Konfiguration des Accessibility-Services |
| `strings.xml` / `colors.xml` / `themes.xml` | Texte & Farben |
| `.github/workflows/build.yml` | GitHub Actions Cloud-Build (optional) |

## Fehlerbehebung

**„Button reagiert nicht“** → Accessibility-Service ist nicht aktiv. Gehe in die App und tippe auf „Accessibility-Service aktivieren“.

**„Es passiert nichts auf Instagram“** → Öffne den Reel erst vollständig (nicht nur im Feed-Preview), damit das Kommentarfeld unten sichtbar ist.

**„Posten-Button nicht gefunden“** → Deine Instagram-Sprache ist nicht Deutsch. Ändere die Sprache in Instagram auf Deutsch oder passe die Suchbegriffe in `InstagramAccessibilityService.kt` an (Zeilen mit `findNodeByText`).
