# Candy Browser

Kleiner Android-Browser mit Arc-inspirierter Bedienung und Material-3-Expressive-Design. Die App nutzt
Android System WebView als Chromium-Engine und ergänzt eigene Tabs, schwebende Browser-Chrome
und lokalen Content-Schutz.

## Funktionen

- Edge-to-edge-Webinhalt mit schwebender unterer Bedienleiste
- Material-3-Dynamic-Color und OS-gesteuerter Darkmode
- Mehrere Tabs mit Arc-inspirierter Kartenübersicht, dauerhaft gespeicherten Vorschauen, Favicon/Titel, Hero-Animation und persistenter Sitzung
- Candy Trails: verzweigte, persistente Navigations-Journeys pro normalem Tab mit animiertem Graph, Pan/Zoom und direkter Knotennavigation
- Tabwechsel durch horizontales Ziehen der Adressleiste; Hochwischen öffnet die Übersicht
- Tabs lassen sich mit Rubberband-Effekt nach oben herauswischen und schließen
- Haptisches Feedback bei Tabwechseln, Öffnen, Schließen und dem Erstellen neuer Tabs
- Flackerfreier Tab-Handoff und Hero-Animation in beide Richtungen
- Pull-to-refresh und Material-3-Ladefortschritt direkt in der Adressleiste
- Direkte URL-Navigation; sonst Google-Suche
- Direkt ausführbare, lokalisierte Browser-Commands in der Adresssuche über `>`
- Lokaler Verlauf mit Autovervollständigung sowie persistente Favoriten auf neuen Tabs
- Lokaler Werbe-/Tracker-Filter mit rund 55.000 kompilierten EasyList-/EasyPrivacy-Hosts inklusive Service-Worker-Anfragen
- Interaktives Privacy X-Ray pro Tab mit gebündelten Live-Zählern, deterministischen Kategorien,
  begrenzter Domain-Übersicht und temporären bzw. profilbezogenen Website-Ausnahmen
- Filter Studio für globale oder profilbezogene Host-, Site→Host- und originbegrenzte CSS-Regeln;
  Privacy X-Ray kann konkrete Block-/Allow-Regeln direkt anlegen und deren Trefferregel öffnen
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

Privacy X-Ray zeigt lokal blockierte, einem Tab verlässlich zuordenbare WebView-Anfragen. Globale
Service-Worker-Anfragen bleiben aus der Tab-Telemetrie ausgeschlossen, weil WebView dafür keine
zuverlässige Tab-ID liefert. Cookie-Angaben beschreiben ausschließlich aktive Regeln, keine
beobachteten Cookie-Ereignisse.

### Candy Rules v1

Filter Studio importiert das kleine, versionierte Candy-Format sowie einen bewusst begrenzten,
sicher abbildbaren Teil von Adblock-Plus-/uBlock-Listen: exakte `||host^`-Block-/Allow-Regeln,
positive `domain=`-/`from=`-Hostpaare, HOSTS-Einträge und ursprungsgebundene Standard-CSS-Selektoren.
JavaScript, reguläre Ausdrücke, Negationen, Umleitungen, HTML-/Response-Header-Filter und erweiterte
kosmetische Operatoren werden sichtbar übersprungen. Filter Studio behauptet keine vollständige
Adblock-Plus-/uBlock-Kompatibilität. Vor dem Import wählt der Nutzer ausdrücklich ein vorhandenes
Profil oder alle Profile; nicht unterstützte Zeilen müssen bestätigt werden.

Candy-Exporte bleiben bei `candy-rules:1`. Danach enthält jede tab-getrennte Zeile `rule`, Aktion
(`block`, `allow`, `css`), Typ (`host`, `pair`, `origin`) und Ziel; weitere Felder halten
CSS-Selector, ID, Profil, Gruppe und Aktivstatus. Candy-Import ist auf 512 KiB und 8.192 Zeilen,
Adblock-Import auf 5 MiB und 100.000 Zeilen begrenzt. Mehr als insgesamt 4.096 Regeln oder 64
kosmetische Regeln werden atomar abgelehnt statt nur teilweise importiert.

HTTPS-Abonnements werden nur nach explizitem Abruf importiert. Jede Aktualisierung zeigt zuerst
einen Diff und verlangt Bestätigung. Als deaktivierter Preset ist die offizielle uBlock-Origin-
Ads-Quelle hinterlegt; Candy lädt sie erst auf Nutzeraktion und übernimmt nur den sicher
abbildbaren Host-/Paarumfang. Fremde Syntax, CSS, Scriptlets und JavaScript werden sichtbar
übersprungen und niemals ausgeführt. Abonnements folgen keinen Redirects und lassen sich global
oder genau einem Profil zuordnen. Inkognito-Regeln und -Importe bleiben im Arbeitsspeicher.

## Filterquellen

Werbe-/Tracker-Hosts sowie kosmetische Cookie-Banner-Regeln stammen von den EasyList-Autoren und werden unter
Creative Commons Attribution-ShareAlike 3.0 oder neuer verwendet. Quelle und Lizenz stehen zusätzlich in
`app/src/main/assets/content_filter.LICENSE.txt`. Mit
`scripts/update_content_filter_hosts.sh` und `scripts/update_cookie_banner_css.sh` lassen sich die
eingebetteten, auf feste Quellrevisionen gepinnten Regellisten reproduzierbar aktualisieren.
Der uBlock-Origin-Preset bündelt keine GPL-uAssets-Daten im APK. Er verweist auf die offizielle,
vom Nutzer explizit abzurufende Quelle; Candy behauptet weder vollständige uBO-Kompatibilität noch
eine Empfehlung oder Unterstützung durch das uBlock-Origin-Projekt.
