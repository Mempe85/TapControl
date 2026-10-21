package de.tapcontrol

import kotlin.math.sqrt

/**
 * Erkennt 1-, 2- oder 3-maliges Klopfen aus Rohdaten des Beschleunigungssensors.
 *
 * Reine Kotlin-Logik ohne Android-Abhängigkeiten, damit sie sich mit Unit-Tests prüfen lässt.
 *
 * Ablauf:
 *  1. Die Schwerkraft wird mit einem Tiefpass herausgerechnet, übrig bleibt die "Ruck"-Komponente.
 *  2. Überschreitet deren Betrag die Schwelle, beginnt eine Spitze (= ein Klopfen).
 *     Eine Spitze endet erst, wenn der Betrag wieder deutlich darunter fällt (Hysterese).
 *  3. Klopfer, die maximal [maxGapMs] auseinanderliegen, werden zu einer Folge gezählt.
 *  4. Kommt [maxGapMs] lang kein weiteres Klopfen, wird die Folge ausgewertet.
 *
 * Schutz vor Fehlauslösung:
 *  - Das erste Klopfen zählt nur, wenn das Handy vorher ruhig lag (Gehen, Schütteln blockiert).
 *  - Eine Spitze, die länger als [maxPeakMs] dauert, ist kein Klopfen, sondern Bewegen/Hinlegen
 *    und verwirft die ganze Folge.
 *  - Mehr als 3 Klopfer werden ignoriert.
 *  - Nach einer erkannten Geste gibt es eine Sperrzeit (z. B. für die eigene Vibration).
 */
class TapDetector(
    /** Schwelle in m/s² – kleiner = empfindlicher. */
    @Volatile var threshold: Float = DEFAULT_THRESHOLD,
    private val minGapMs: Long = 90,
    private val maxGapMs: Long = 450,
    private val maxPeakMs: Long = 70,
    private val cooldownMs: Long = 800,
    private val onTap: (strength: Float) -> Unit = {},
    private val onGesture: (count: Int) -> Unit,
) {
    companion object {
        const val DEFAULT_THRESHOLD = 3.5f
        private const val GRAVITY_TAU_S = 0.15f
        private const val NOISE_TAU_S = 0.4f
        /** Ruhe-Bedingung: Grundrauschen muss unter diesem Anteil der Schwelle liegen. */
        private const val QUIET_FACTOR = 0.3f
        /** Spitze gilt als beendet, wenn der Betrag unter diesen Anteil der Schwelle fällt. */
        private const val RELEASE_FACTOR = 0.5f
        private const val MS = 1_000_000L
    }

    private val gravity = FloatArray(3)
    private var initialized = false
    private var lastSampleNs = 0L
    private var noise = 0f

    private var tapCount = 0
    private var lastTapNs = 0L
    private var sequenceInvalid = false
    private var cooldownUntilNs = 0L

    private var inPeak = false
    private var peakStartNs = 0L
    private var peakMax = 0f

    fun reset() {
        initialized = false
        resetSequence()
        inPeak = false
        noise = 0f
        cooldownUntilNs = 0L
    }

    /** @param timestampNs Zeitstempel des Sensor-Events in Nanosekunden. */
    fun onSample(timestampNs: Long, x: Float, y: Float, z: Float) {
        if (!initialized) {
            gravity[0] = x; gravity[1] = y; gravity[2] = z
            lastSampleNs = timestampNs
            initialized = true
            return
        }

        val dt = ((timestampNs - lastSampleNs) / 1e9f).coerceIn(0.0005f, 0.1f)
        lastSampleNs = timestampNs

        // 1) Schwerkraft per Tiefpass schätzen und abziehen
        val a = GRAVITY_TAU_S / (GRAVITY_TAU_S + dt)
        gravity[0] = a * gravity[0] + (1 - a) * x
        gravity[1] = a * gravity[1] + (1 - a) * y
        gravity[2] = a * gravity[2] + (1 - a) * z
        val hx = x - gravity[0]
        val hy = y - gravity[1]
        val hz = z - gravity[2]
        val mag = sqrt(hx * hx + hy * hy + hz * hz)

        // 2) Folge abschließen, wenn lange genug kein weiteres Klopfen kam
        if (tapCount > 0 && !inPeak && timestampNs - lastTapNs > maxGapMs * MS) {
            finishSequence(timestampNs)
        }

        val wasQuiet = noise < threshold * QUIET_FACTOR
        val n = NOISE_TAU_S / (NOISE_TAU_S + dt)
        noise = n * noise + (1 - n) * mag

        if (timestampNs < cooldownUntilNs) {
            inPeak = false
            return
        }

        // 3) Spitzen erkennen (mit Hysterese)
        if (!inPeak) {
            if (mag > threshold) {
                inPeak = true
                peakStartNs = timestampNs
                peakMax = mag
                registerTap(timestampNs, wasQuiet)
            }
        } else {
            if (mag > peakMax) peakMax = mag
            if (mag < threshold * RELEASE_FACTOR) {
                inPeak = false
                if (tapCount > 0 && !sequenceInvalid) onTap(peakMax)
            } else if (timestampNs - peakStartNs > maxPeakMs * MS) {
                // zu lange Spitze -> kein Klopfen, sondern Bewegung
                sequenceInvalid = true
            }
        }
    }

    private fun registerTap(t: Long, wasQuiet: Boolean) {
        if (tapCount == 0) {
            if (!wasQuiet) return // Handy war in Bewegung -> ignorieren
            tapCount = 1
            lastTapNs = t
            sequenceInvalid = false
        } else {
            if (t - lastTapNs < minGapMs * MS) return // Nachschwingen desselben Klopfers
            tapCount++
            lastTapNs = t
            if (tapCount > 3) sequenceInvalid = true
        }
    }

    private fun finishSequence(t: Long) {
        val count = tapCount
        val valid = !sequenceInvalid && count in 1..3
        resetSequence()
        if (valid) {
            cooldownUntilNs = t + cooldownMs * MS
            onGesture(count)
        }
    }

    private fun resetSequence() {
        tapCount = 0
        lastTapNs = 0L
        sequenceInvalid = false
    }
}
