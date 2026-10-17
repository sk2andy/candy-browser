# Android regression audit — 2026-10-17

Die Kotlin-/JVM-Tests bestehen vollständig. Die Android-Gerätesuite ist rot. Sämtliche entdeckten
Test-IDs sind erfasst; bedingte Auslassungen und Prozessabbrüche sind ausdrücklich ausgewiesen.
Mehrere gezielt verglichene Fehler reproduzieren auf dem vorherigen Stand. Weitere Fehler und
instabile UI-Beobachtungen sind damit nicht automatisch geklärt. Eine vollständige Android-Freigabe
lässt sich aus diesem Lauf nicht ableiten.

## Geprüfter Stand und Umgebung

| Merkmal | Wert |
| --- | --- |
| Worktree | `codex/ios-shared-parity` |
| Basis | `3117df4799123564acaee579c336fedb8a2eff06` plus bestehende iOS-/Shared-Änderungen |
| Varianten | Full, FOSS und System WebView; alle aktuell aktivierten Android-Unit-Test-Aufgaben |
| Gerätetests | Android API 37, arm64, dedizierte Pixel-7-AVDs |
| Phone | 1080 × 2400, 420 dpi, Portrait, anfänglich Light |
| Tablet-Nachläufe | 2560 × 1600, 240 dpi, Landscape, `sw1067dp` |
| Isolation | Eigene Emulatoren je Agent; jeder Gerätezugriff mit expliziter Seriennummer |
| Eigene Seriennummern | `emulator-5842`, `5844`, `5846`, `5848`, `5850` |
| Andere Geräte | `emulator-5832` unverändert; kein physisches Gerät benutzt |
| Quellen während des Laufs | 1767 Android-/Shared-/Build-Dateien per SHA-256 unverändert, keine zusätzlichen Android-/Shared-Quelldateien |

Der Testauftrag änderte keinen Produkt- oder Testcode. Die Ausgangs-APKs wurden vor den
Geräteläufen eingefroren; getrennte APKs aus einem sauberen Basis-Checkout dienen den Gegenproben.
Die Hashes liegen im Belegarchiv. Der temporäre Basis-Checkout wurde nach Abschluss der
Gegenproben entfernt. Der Arbeitsworktree bleibt erhalten.

## Unit-Tests und Build

| Aufgabe | Tests | Fehler | Errors | Ausgelassen |
| --- | ---: | ---: | ---: | ---: |
| `:shared:testDebugUnitTest` | 325 | 0 | 0 | 0 |
| `:app:testFullDebugUnitTest` | 1996 | 0 | 0 | 0 |
| `:app:testFossDebugUnitTest` | 1996 | 0 | 0 | 0 |
| `:app:testSystemwebviewDebugUnitTest` | 1996 | 0 | 0 | 0 |
| Gesamt | **6313** | **0** | **0** | **0** |

Alle drei Debug-Apps und zugehörigen Instrumentierungs-APKs gebaut. Ein anfänglicher paralleler
Kotlin-Build scheiterte am Speicherlimit; der vollständige erneute Build mit einem Worker,
In-Process-Kotlin-Compiler und 6 GiB Heap bestand. Die Tabelle stammt aus 982 tatsächlichen
JUnit-XML-Dateien. Alle vier Testaufgaben wurden abschließend mit dem auf die jeweilige Aufgabe
begrenzten `--rerun` erneut tatsächlich ausgeführt; Kompilierungsabhängigkeiten durften aktuell
bleiben. Release-Unit-Test-Aufgaben sind in der bestehenden AGP-Konfiguration nicht
aktiviert; der Lauf aktiviert keine zusätzlichen Buildvarianten.

## Gerätetests: vollständige ID-Erfassung

| Variante | Erfasste / erwartete IDs | PASS | FAIL / ERROR | SKIP | Fehlend |
| --- | ---: | ---: | ---: | ---: | ---: |
| Full Debug | 1609 / 1609 | 1476 | 95 | 38 | 0 |
| FOSS Debug | 1607 / 1607 | 1478 | 93 | 36 | 0 |
| System WebView Debug | 1607 / 1607 | 1179 | 375 | 53 | 0 |

Die Matrix zählt eindeutige `class#method`-IDs aus der Discovery. Ein beobachteter Fehler behält
Vorrang vor einem späteren PASS. Ein SKIP ist kein PASS. Abgebrochene, bereits gestartete Methoden
ohne Terminalereignis werden anhand von START und Runner-/Crash-Beleg als ERROR zugeordnet.
Ein Klassenfehler `test=null` zählt nicht als ausgeführte Methode. Die dadurch zunächst fehlenden
zehn HTTPS-Methoden wurden für Full und FOSS in frischen Instrumentierungsprozessen nachgeholt.

Die Phone-Matrix vereinigt Hauptlauf, disjunkte bzw. überlappende Fortsetzungen und UI-Shards;
Wiederholungen erhöhen die Zahl eindeutiger IDs nicht. Fortsetzungen der Hauptläufe starteten
noch offene IDs; UI-Shards und gezielte Gegenproben wurden separat ausgeführt. Kontrollierte
Stopps zum Shardwechsel sind getrennt dokumentiert und werden nicht als
Produktabsturz umgedeutet. Bei System WebView enthält die Originalmatrix 260 FAIL und 115 ERROR;
zwei ursprüngliche FOSS-Prozessabbrüche bleiben ebenfalls erhalten. Full enthält einen Java-Abbruch
im lokalen Popup-Testserver mit terminalem Fehlerstatus.

Der von einem zweiten Agenten unabhängig implementierte Eventparser bestätigt die Zählweise.
Ein Runner-Exit allein gilt nicht als Vollständigkeitsbeleg; maßgeblich ist die Inventarvereinigung.

## Zusätzliche Voraussetzungen gezielt erfüllt

| Nachlauf | Umfang | Ergebnis | Einordnung |
| --- | ---: | --- | --- |
| System-Dunkelmodus / kalter Gecko-Start | 7 Fälle | 7 PASS | Hero in allen Varianten; Gecko-Appearance und ColdStart in Full/FOSS |
| Breite Adressleiste auf Tablet | 3 je Variante | 9 PASS | Tatsächliche breite Landscape-Konfiguration belegt |
| Tablet-Übersicht / Orientierung | 1 je Variante | 3 PASS | Tatsächliche Tablet-Voraussetzung belegt |
| YouTube-PiP-Fixture, isolierter Einzelstart | 6 je Variante | Full Debug: 0 PASS, 6 FAIL, 0 ERROR, 0 SKIP; FOSS Debug: 0 PASS, 6 FAIL, 0 ERROR, 0 SKIP; System WebView Debug: 0 PASS, 0 FAIL, 6 ERROR, 0 SKIP | Frische Daten und genau ein Methodenfilter pro Prozess; Original-SKIPs bleiben in Phone-Matrix |

Die 19 Nacht-/Tablet-Prüfungen bestehen ohne Auslassung. Die sechs Full-PiP-Nachläufe scheitern
bereits beim Laden der lokalen YouTube-Fixture; die sechs System-PiP-Nachläufe brechen im nativen
Gecko aus dem Test-APK ab. Die genauen FOSS-PiP-Ergebnisse und sämtliche Einzelmethoden stehen
in den archivierten Supplementreports. Diese Nachläufe ersetzen die ursprünglichen Beobachtungen
nicht. Verbleibende SKIPs nennen ihre Voraussetzungen im Rohlog: unter anderem echte
Downloadmanager-Installationen, gezielt aktivierbare Diagnose-/Messläufe und Engine-Fähigkeiten.

## Gegenproben auf Basis und aktuellem Stand

| Konkreter Vergleich | Basis / aktuell | Aussage |
| --- | --- | --- |
| Drei externe APK-Download-Fälle | FAIL / FAIL, gleiche Signaturen | Im geprüften Umfeld bereits auf Basis vorhanden |
| Fullscreen-Verhinderung zweiter GeckoView, HTTP-Auth-Prompt, Notices-XPI-Hash, PageResume-Viewidentität, FindInPage-IME, RootBack | Je FAIL / FAIL, gleiche normalisierte Stacks | Sechs konkret verglichene Fehler bereits auf Basis vorhanden |
| Sticky-Header-Fallback und echte Gecko-Scroll-/Pillen-Synchronisation | Je PASS / PASS frisch | Frisch isoliert nicht reproduziert; ursprünglicher Fehler und Ursache bleiben offen |
| System: privater PNG-Dateiname, RootBack-about:blank, ein direkter nativer Gecko-Fall | Je FAIL bzw. ERROR auf beiden | Drei konkrete System-Gegenproben, einschließlich nativem Abbruch |
| FOSS: Settings-Footer-Scrollaufruf | FAIL / FAIL, gleicher Stack | Test scrollt einen feststehenden Footer; nachfolgende Feature-Aussage wird nicht erreicht |
| FOSS: vertikales LinkPeek-Scrollen | PASS / PASS frisch | Ursprünglicher FOSS-Fehler bleibt als instabile Beobachtung erhalten |
| FOSS: manuelle Pillenerweiterung / Composer / Tastatur | Zunächst FAIL129 / FAIL134; weitere Basisläufe FAIL110 und FAIL129, aktuelle Läufe zweimal PASS | Kein stabiler zusätzlicher Fehler des aktuellen Stands belegt; ursprünglicher FAIL134 nicht als identischer Basisfehler bewiesen |
| HTTPS-Retry-Einzelmethode | FAIL / FAIL, `UnknownHost`, keine HTTP-/TLS-Anfrage | Lokale DNS-Fixture scheitert auf beiden; nur diese eine Methode direkt paarweise verglichen |
| System-404, zwei Gecko-Overview- und drei Snooze-Fälle | Frühere protokollierte Basisläufe mit gleichen Fehlerstellen | Vorherige Basisevidenz aus dem iOS-/Shared-Audit, Rohlogs mitgesichert |

Die Gegenproben gelten für die genannten Fälle. Sie beweisen nicht, dass jeder andere rote Test
derselben Klasse oder Fehlergruppe auf Basis identisch scheitert.

## Weitere Befunde und offene Einordnung

| Bereich | Beleg / Grenze |
| --- | --- |
| System-WebView-Flavour und direkte Gecko-Tests | Viele Tests erzwingen Gecko trotz fehlender Engine-Unterstützung; native `libxul.so` stammt dabei aus dem Test-APK. Abbrüche bleiben ERROR. Der eigentliche 77-Fälle-System-Engineblock bestand mit 74 PASS und drei Fehlern. |
| FOSS-WebExtension-Fixture | Basis- und aktuelle APKs enthalten beide kein `assets/candy_extension_fixture`; Fixture liegt nur in `src/fullDebug`. Das ist statische Input-Evidenz, kein behaupteter zusätzlicher Basis-Gerätelauf. |
| Reader-Bridge-Asset | Basis und aktuell byteidentisch, SHA-256 `9e9489a6db7da04cbab7bc6dd34a10d1c4e2415f407450a71cb594479e77c70f`; beide ohne erwartetes Literal `self !== top`. Deterministischer Test-/Asset-Widerspruch. |
| CandyRuleStore | Test schreibt übergroße Datei nach `noBackupFilesDir`, Store liest migriertes `filesDir`; relevante Test-/Store-/Validatorquellen bytegleich zur Basis. Statische Fixture-Diagnose, keine zusätzliche paarweise Geräteausführung. |
| Globale GeckoRuntime / Benachrichtigungszustände | Mehrere Warmlauf-Fehler entstehen an bestehenden Ein-Runtime- bzw. globalen Pending-Verträgen. Die Rohfehler bleiben rot; Quellbefund allein ersetzt keinen Basislauf. |
| Popup-Testserver | Full-Fortsetzung brach wegen unaufgefangener `SocketException: Broken pipe` im lokalen Testserver `serve:574` ab. Derselbe System-Fall bestand im System-Flavour. Weitere IDs anschließend vollständig nachgeholt. |
| Safe-Area-, Reader-, UI-Geometrie- und übrige Fehler | Nicht jeder Fall einzeln auf Basis verglichen; deshalb weiterhin ungeklärt. Kleine Pixelabweichungen oder unveränderte Nachbarquellen allein sind keine Freigabe. |

## Reproduktion und Belege

| Inhalt | Lokaler Bericht |
| --- | --- |
| Übersicht und Quellprüfung | [`summary.json`](../../build/reports/android-regression-2026-10-17/summary.json) |
| Jede erwartete Methode, konservativer Status und Rohlogpfade | [`cases.csv`](../../build/reports/android-regression-2026-10-17/cases.csv) |
| Vollständige Ereigniszuordnung | [`coverage.json`](../../build/reports/android-regression-2026-10-17/coverage.json) |
| Rohlogs, Inventare, APK-Hashes, Gegenproben, Supplemente, Parserreview und Unit-XML | [`evidence.tar.gz`](../../build/reports/android-regression-2026-10-17/evidence.tar.gz) |

Die Belege sind im ignorierten lokalen Build-Berichtsverzeichnis dauerhaft für diesen Worktree
abgelegt. Das Archiv enthält keine APK-Binaries und keine Signing-Dateien. Instrumentierung wurde
mit `androidx.test.runner.AndroidJUnitRunner`, `-w -r`, explizitem `adb -s` und Fall-/Dateifiltern
ausgeführt. Variantenübergreifende Vergleiche nutzten ausschließlich eigene Emulatoren.

## Nachfolgender Merge mit main

Der Feature-Commit `4cd58a97` wurde mit `main` auf `ad85a8f4` integriert. Der Swift-Menükonflikt
behält die dynamischen Snooze-/Dock-Werte und die deaktivierte iOS-Constellations-Fähigkeit.
Die neue Constellations-Aktion erhält außerdem den bestehenden SF-Routen-Symbolzweig.
Ein zweiter Agent prüfte alle acht überlappenden Dateien gegen beide Eltern.

| Prüfung des integrierten Stands | Ergebnis |
| --- | ---: |
| Shared-Android-Unit-Tests | 327 PASS |
| Full-Unit-Tests | 2037 PASS |
| FOSS-Unit-Tests | 2037 PASS |
| System-WebView-Unit-Tests | 2037 PASS |
| Gesamt, frisch ausgeführt | **6438 PASS**, keine Fehler oder Skips |
| Kotlin-iOS-Simulator-Framework | Build bestanden |
| Native iOS-App, arm64-Simulator | Xcode-Build bestanden |

Die Gerätematrix oben beschreibt weiterhin den vor dem Merge eingefrorenen Feature-Stand.
Die Gerätefehler bleiben zur anschließenden Untersuchung offen. Die neuen Build-Logs und Unit-XML
liegen unter [`ios-main-integration-2026-10-17`](../../build/reports/ios-main-integration-2026-10-17/).
