package com.whatchapp.hourlybuzz.prayer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class PostureClassifierTest {
    // Left wrist, hands folded: forearm across the chest (gravity along -y),
    // ruku: arm down to the knee (gravity along x), sujood/jalsa: watch face up (gravity along z).
    private val cal = Calibration(
        qiyam = Centroid(0.1, -0.95, 0.2, 0.0),
        ruku = Centroid(0.9, -0.2, 0.35, 0.06),
        sujood = Centroid(0.3, 0.0, 0.95, 0.12),
        jalsa = Centroid(-0.1, -0.2, 0.97, 0.07),
    )

    @Test
    fun matchesNearestPosture() {
        val c = PostureClassifier(cal)
        c.startBaseline(1000.0)
        assertEquals(Posture.QIYAM, c.classify(0.15, -0.93, 0.25, 0.0, 1000.0))
        assertEquals(Posture.RUKU, c.classify(0.85, -0.3, 0.4, 0.0, 1000.06))
        assertEquals(Posture.LOW, c.classify(0.25, 0.05, 0.96, 0.0, 1000.12))
        assertEquals(Posture.LOW, c.classify(-0.05, -0.2, 0.97, 0.0, 1000.07))
    }

    @Test
    fun movingAndUnclearReadings() {
        val c = PostureClassifier(cal)
        assertEquals(Posture.MOVING, c.classify(0.1, -0.95, 0.2, 2.0, Double.NaN))
        assertEquals(Posture.UNKNOWN, c.classify(-0.9, 0.3, -0.3, 0.0, Double.NaN))
    }

    @Test
    fun calibrationRoundTripAndValidation() {
        val back = Calibration.decode(cal.encode())
        assertNotNull(back)
        assertEquals(cal.ruku, back!!.ruku)
        assertNull(cal.problem())
        val bad = Calibration(cal.qiyam, cal.qiyam, cal.sujood, cal.jalsa)
        assertEquals("qiyam/ruku", bad.problem())
        assertNull(Calibration.decode("garbage"))
    }
}
