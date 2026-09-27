package com.whatchapp.hourlybuzz.prayer

import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.math.PI
import kotlin.math.sin

class ShakeDetectorTest {
    /** Rotation around x following [f] (rad/s) for [ms] at 50 Hz; returns how many shakes were seen. */
    private fun run(d: ShakeDetector, start: Long, ms: Long, f: (Double) -> Double): Int {
        var shakes = 0
        var t = start
        while (t < start + ms) {
            if (d.onGyro(t, f((t - start) / 1000.0), 0.0, 0.0)) shakes++
            t += 20
        }
        return shakes
    }

    @Test
    fun quickBackAndForthTwistIsAShake() {
        // Two twists (out-in-out-in) at 3 Hz, 8 rad/s.
        assertEquals(1, run(ShakeDetector(), 0, 700) { s -> 8 * sin(2 * PI * 3 * s) })
    }

    @Test
    fun slowPrayerMovementIsNot() {
        // Raising hands / going to ruku: up to 4 rad/s, one direction at a time, slow.
        assertEquals(0, run(ShakeDetector(), 0, 5000) { s -> 4 * sin(2 * PI * 0.4 * s) })
    }

    @Test
    fun singleFastFlickIsNot() {
        assertEquals(0, run(ShakeDetector(), 0, 1000) { s -> if (s < 0.1) 9.0 else 0.0 })
    }

    @Test
    fun twoShakesNeedAPauseBetween() {
        val d = ShakeDetector()
        val twist: (Double) -> Double = { s -> 8 * sin(2 * PI * 3 * s) }
        assertEquals(1, run(d, 0, 700, twist))
        assertEquals(0, run(d, 700, 500, twist))       // too soon: ignored
        assertEquals(1, run(d, 3000, 700, twist))
    }
}
