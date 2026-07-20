# Candy Browser

Kleiner Android-Browser mit Arc-inspirierter Bedienung und Material-3-Expressive-Design. Die App nutzt
Android System WebView als Chromium-Engine und ergänzt eigene Tabs, schwebende Browser-Chrome
und lokalen Content-Schutz.

## Funktionen

- Edge-to-edge-Webinhalt mit schwebender unterer Bedienleiste
- Material-3-Dynamic-Color und OS-gesteuerter Darkmode
- Mehrere Tabs mit Arc-inspirierter Kartenübersicht, dauerhaft gespeicherten Vorschauen, Favicon/Titel, Hero-Animation und persistenter Sitzung
- Tabwechsel durch horizontales Ziehen der Adressleiste; Hochwischen öffnet die Übersicht
- Tabs lassen sich mit Rubberband-Effekt nach oben herauswischen und schließen
- Haptisches Feedback bei Tabwechseln, Öffnen, Schließen und dem Erstellen neuer Tabs
- Flackerfreier Tab-Handoff und Hero-Animation in beide Richtungen
- Pull-to-refresh und Material-3-Ladefortschritt direkt in der Adressleiste
- Direkte URL-Navigation; sonst Google-Suche
- Lokaler Verlauf mit Autovervollständigung sowie persistente Favoriten auf neuen Tabs
- Lokaler Werbe-/Tracker-Filter inklusive Service-Worker-Anfragen
- Drittanbieter-Cookies standardmäßig blockiert; First-party-Cookies für Logins erlaubt
- Früher Cookie-Banner-Blocker auf Basis der EasyList Cookie List
- Safe Browsing, TLS-Abbruch, verbotene Datei-/Content-Schemes und externe Scheme-Allowlist
- Downloads mit Systembenachrichtigung in den öffentlichen Download-Ordner
- Long-Press-Menü für Bild-Downloads und Links in Hintergrund-Tabs; native Textauswahl bleibt erhalten
- Nutzerinitiierte Pop-ups und neue Fenster werden als Tabs geöffnet
- HTTP/HTTPS-Intent-Filter, Standardbrowser-Rolle und Öffnen in externen Apps
- System-Autofill für Passwortmanager sowie WebAuthn-/Passkey-Unterstützung des installierten WebView-Providers

## Build

Voraussetzungen: Android SDK 35, JDK 17. Die App unterstützt Android 14 (API 34) und neuer.

```bash
JAVA_HOME=/opt/homebrew/opt/openjdk@17 ./gradlew \
  testDebugUnitTest lintDebug assembleDebug
```

APK: `app/build/outputs/apk/debug/app-debug.apk`

## Grenzen

System WebView bietet keine Chromium-Extension-API. Der Blocker arbeitet deshalb lokal und
heuristisch; WebSockets, CNAME-Cloaking, manche Weiterleitungen und Banner in geschlossenen
Cross-Origin-/Shadow-DOM-Kontexten können durchkommen. Das vollständige Abschalten aller Cookies
ist absichtlich nicht Standard, weil es Logins und viele Websites bricht.

## Filterquellen

Die kosmetischen Cookie-Banner-Regeln stammen von den EasyList-Autoren und werden unter
Creative Commons Attribution-ShareAlike 3.0 oder neuer verwendet. Quelle und Lizenz stehen zusätzlich in
`app/src/main/assets/easylist_cookie.LICENSE.txt`. Mit
`scripts/update_cookie_banner_css.sh` lässt sich die eingebettete Regelliste aktualisieren.
