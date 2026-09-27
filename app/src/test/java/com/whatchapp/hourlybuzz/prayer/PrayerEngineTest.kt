package com.whatchapp.hourlybuzz.prayer

import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.math.PI
import kotlin.math.sin

/** A simulated Dhuhr, fed to the engine as raw 50 Hz sensor samples. */
class PrayerEngineTest {
    private val cal = Calibration(
        qiyam = Centroid(0.1, -0.95, 0.2, 0.0),
        ruku = Centroid(0.9, -0.2, 0.35, 0.06),
        sujood = Centroid(0.3, 0.0, 0.95, 0.12),
        jalsa = Centroid(-0.1, -0.2, 0.97, 0.07),
    )
    private val answers = mutableListOf<Int>()
    private val engine = PrayerEngine(4, cal, object : PrayerEngine.Listener {
        override fun onShake(remainingAfterCurrent: Int) {
            answers += remainingAfterCurrent
        }
    })
    private var t = 0L
    private val basePressure = 1012.0

    /** Hold a posture for [ms] with small hand tremor; takbir at the start. */
    private fun hold(c: Centroid, ms: Long) {
        engine.onVoice(t)
        val end = t + ms
        while (t < end) {
            val tremor = 0.02 * sin(t / 37.0)
            engine.onAccel(t, 9.81 * c.gx + tremor, 9.81 * c.gy, 9.81 * c.gz - tremor)
            engine.onGyro(t, 0.05 * sin(t / 53.0), 0.03, -0.02)
            if (t % 100 == 0L) engine.onPressure(t, basePressure + c.dp)
            t += 20
        }
    }

    /** Moving between postures: the arm swings for ~1 s. */
    private fun move(from: Centroid, to: Centroid) {
        val end = t + 1000
        val start = t
        while (t < end) {
            val f = (t - start) / 1000.0
            fun mix(a: Double, b: Double) = 9.81 * (a + (b - a) * f)
            engine.onAccel(t, mix(from.gx, to.gx), mix(from.gy, to.gy), mix(from.gz, to.gz))
            engine.onGyro(t, 2.0 * sin(PI * f), 1.2, 0.5)
            t += 20
        }
    }

    /** Two fast wrist twists. */
    private fun shake(c: Centroid) {
        val start = t
        while (t < start + 700) {
            engine.onAccel(t, 9.81 * c.gx, 9.81 * c.gy, 9.81 * c.gz)
            engine.onGyro(t, 8 * sin(2 * PI * 3 * (t - start) / 1000.0), 0.0, 0.0)
            t += 20
        }
        hold(c, 1500)
    }

    @Test
    fun dhuhrCountsAndAnswersEachShake() {
        val q = cal.qiyam
        for (rakah in 1..4) {
            hold(q, 8000)
            shake(q)                      // ask: how many left after this one?
            assertEquals(rakah, engine.counter.rakah)
            move(q, cal.ruku); hold(cal.ruku, 4000)
            move(cal.ruku, q); hold(q, 2000)
            move(q, cal.sujood); hold(cal.sujood, 4000)
            move(cal.sujood, cal.jalsa); hold(cal.jalsa, 2000)
            move(cal.jalsa, cal.sujood); hold(cal.sujood, 4000)
            if (rakah == 2 || rakah == 4) {
                move(cal.sujood, cal.jalsa); hold(cal.jalsa, 30_000)
            }
            if (rakah < 4) move(cal.jalsa, q)
        }
        assertEquals(listOf(3, 2, 1, 0), answers)
        assertEquals(4, engine.counter.rakah)
    }
}
