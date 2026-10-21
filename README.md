# TapControl

Steuert Spotify (oder jeden anderen Musikplayer) durch Klopfen ans Handy:

| Klopfen | Aktion            |
|---------|-------------------|
| 1×      | Play / Pause      |
| 2×      | Nächster Titel    |
| 3×      | Titel zurück      |

## Bauen und installieren

1. **Android Studio** installieren (aktuelle Version).
2. Ordner `TapControl` über *File → Open* öffnen. Android Studio lädt Gradle und die Abhängigkeiten selbst.
3. Am Handy die **Entwickleroptionen** und **USB-Debugging** einschalten, Handy per USB verbinden.
4. Oben auf ▶ *Run* klicken.

## Benutzung

1. App öffnen, „Klopfsteuerung starten" tippen und Benachrichtigungen erlauben.
2. Handy hinlegen, ein paar Mal klopfen und unter „Stärke des letzten Klopfers" ablesen, wie stark deine Klopfer sind.
3. Schwelle knapp unter diesen Wert stellen.
4. Spotify starten – fertig. Funktioniert auch bei gesperrtem Bildschirm.

## Wie die Erkennung funktioniert

`TapDetector.kt` rechnet die Schwerkraft aus den Sensordaten heraus und sucht kurze Spitzen.
Klopfer im Abstand von max. 450 ms zählen als eine Folge. Gegen Fehlauslöser:

- Das erste Klopfen zählt nur, wenn das Handy vorher ruhig lag (Gehen löst nichts aus).
- Spitzen länger als 70 ms gelten als Bewegen/Hochheben, nicht als Klopfen.
- 4× oder mehr wird ignoriert.
- Nach jeder Geste 0,8 s Sperrzeit.

Die Werte stehen oben in `TapDetector` und lassen sich leicht anpassen.
Tests: `app/src/test/.../TapDetectorTest.kt` (in Android Studio per Rechtsklick → *Run*).

## Hinweise

- **Akku:** Der Dienst hält den Sensor mit hoher Abtastrate dauerhaft wach. Über „Stopp" in der Benachrichtigung ausschalten, wenn du ihn nicht brauchst.
- **Titel zurück:** Spotify springt beim ersten „Zurück" an den Anfang des aktuellen Titels, wenn dieser schon länger als ein paar Sekunden läuft. Erst ein zweites Mal geht zum vorherigen Titel.
- Manche Hersteller (Xiaomi, Huawei, Samsung) beenden Hintergrunddienste aggressiv. Dann in den Akku-Einstellungen für TapControl „Nicht optimieren" wählen.
