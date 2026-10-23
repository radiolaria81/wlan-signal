# WLAN-Signal

Kleine Android-App (ab Android 10), die die Signalstärke des verbundenen WLANs live anzeigt.

- RSSI in dBm, großer Balken und Einstufung
- Verlauf der letzten 2 Minuten, Min / Mittel / Max seit Start
- SSID, BSSID, Band, Kanal, Frequenz, Linkrate (Senden/Empfangen), WLAN-Standard (ab Android 11)
- Display bleibt an, solange die App offen ist (zum Herumlaufen und Messen)
- Keine Abhängigkeiten außer der Kotlin-Standardbibliothek, kein Internetzugriff

## Installieren

APK aus dem neuesten [Release](../../releases/latest) herunterladen und installieren
(Installation aus unbekannten Quellen erlauben).

## Berechtigungen

Die Signalstärke funktioniert ohne Sonderrechte. Für **SSID und BSSID** verlangt Android die
Standortberechtigung *und* eingeschalteten Standort – ohne beides zeigt die App „verborgen“.

## Hinweise

- Android aktualisiert den RSSI-Wert selbst nur etwa alle 3 Sekunden; die App fragt jede Sekunde ab.
- Einstufung (≥ −50 ausgezeichnet, ≥ −60 gut, ≥ −67 ordentlich, ≥ −75 schwach, darunter sehr schwach)
  sind gängige Faustwerte, keine Norm.

## Bauen

GitHub Actions baut bei jedem Push auf `main`. Ändert sich die Datei `VERSION`, entsteht ein neues Release
mit der APK. Lokal: `./gradlew assembleRelease`.

Der Signierschlüssel `app/signing.jks` liegt bewusst im Repo, damit Updates sich über die vorige
Version installieren lassen. Für eine reine Privat-App ist das in Ordnung; für Verteilung an Dritte
sollte er durch ein Secret ersetzt werden.
