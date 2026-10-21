package de.tapcontrol

import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.math.sin
import kotlin.random.Random

/**
 * Simuliert einen Beschleunigungssensor mit 400 Hz: Handy liegt flach (z = 9.81),
 * leichtes Rauschen, Klopfer als kurze gedämpfte Schwingung.
 */
class TapDetectorTest {

    private val rateHz = 400
    private val dtNs = 1_000_000_000L / rateHz

    private fun run(
        durationMs: Int,
        taps: List<Int> = emptyList(),
        tapAmp: Float = 12f,
        noiseAmp: Float = 0.05f,
        extra: (tMs: Double) -> Float = { 0f },
    ): List<Int> {
        val gestures = mutableListOf<Int>()
        val d = TapDetector(onGesture = { gestures += it })
        val rnd = Random(42)
        val samples = durationMs * rateHz / 1000
        for (i in 0 until samples) {
            val tMs = i * 1000.0 / rateHz
            var z = 9.81f + (rnd.nextFloat() - 0.5f) * 2 * noiseAmp + extra(tMs)
            for (tap in taps) {
                val dt = tMs - tap
                if (dt in 0.0..25.0) {
                    // gedämpfte Schwingung ~150 Hz, ca. 20 ms lang
                    z += (tapAmp * kotlin.math.exp(-dt / 6.0) * sin(2 * Math.PI * 150 * dt / 1000)).toFloat()
                }
            }
            d.onSample(i * dtNs, 0f, 0f, z)
        }
        return gestures
    }

    @Test fun single() = assertEquals(listOf(1), run(2000, listOf(1000)))
    @Test fun double() = assertEquals(listOf(2), run(2000, listOf(800, 1050)))
    @Test fun triple() = assertEquals(listOf(3), run(2500, listOf(800, 1050, 1300)))
    @Test fun twoSeparateSingles() = assertEquals(listOf(1, 1), run(4000, listOf(1000, 2500)))
    @Test fun fourIgnored() = assertEquals(emptyList<Int>(), run(3000, listOf(800, 1000, 1200, 1400)))
    @Test fun quietNoTrigger() = assertEquals(emptyList<Int>(), run(3000))
    @Test fun weakTapIgnored() = assertEquals(emptyList<Int>(), run(2000, listOf(1000), tapAmp = 1.5f))

    @Test fun walkingIgnored() {
        // Gehen: ~2 Hz Schwankung mit 3 m/s² plus harte Fußaufsetzer
        val g = run(5000, taps = listOf(500, 1000, 1500, 2000, 2500, 3000, 3500, 4000)) { t ->
            (3.0 * sin(2 * Math.PI * 2 * t / 1000)).toFloat()
        }
        assertEquals(emptyList<Int>(), g)
    }

    @Test fun pickUpIgnored() {
        // Handy anheben: längerer Ruck über ~300 ms
        val g = run(3000) { t ->
            if (t in 1000.0..1300.0) (6.0 * sin(Math.PI * (t - 1000) / 300)).toFloat() else 0f
        }
        assertEquals(emptyList<Int>(), g)
    }
}
