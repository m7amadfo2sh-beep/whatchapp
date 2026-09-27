package com.whatchapp.hourlybuzz.prayer

import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.sqrt

/** What the watch can tell about the imam's position. LOW covers sujood, jalsa and tashahhud. */
enum class Posture { QIYAM, RUKU, LOW, MOVING, UNKNOWN }

/**
 * One calibrated posture: the direction of gravity in the watch's own axes
 * (a unit vector) and the air pressure relative to standing, in hPa.
 * Higher pressure = wrist lower down.
 */
data class Centroid(val gx: Double, val gy: Double, val gz: Double, val dp: Double) {
    fun angleTo(x: Double, y: Double, z: Double): Double {
        val dot = (gx * x + gy * y + gz * z) / (norm(gx, gy, gz) * norm(x, y, z))
        return acos(dot.coerceIn(-1.0, 1.0))
    }
}

internal fun norm(x: Double, y: Double, z: Double) = sqrt(x * x + y * y + z * z).coerceAtLeast(1e-9)

/** The imam's own postures, recorded once in CalibrationActivity. */
class Calibration(
    val qiyam: Centroid,
    val ruku: Centroid,
    val sujood: Centroid,
    val jalsa: Centroid,
) {
    /** Returns why the calibration can't be used, or null if it is fine. */
    fun problem(): String? {
        val deg = 180 / Math.PI
        fun apart(a: Centroid, b: Centroid) = a.angleTo(b.gx, b.gy, b.gz) * deg
        return when {
            apart(qiyam, ruku) < MIN_APART_DEG -> "qiyam/ruku"
            apart(qiyam, sujood) < MIN_APART_DEG && abs(sujood.dp) < 0.05 -> "qiyam/sujood"
            apart(ruku, sujood) < MIN_APART_DEG && abs(sujood.dp - ruku.dp) < 0.05 -> "ruku/sujood"
            else -> null
        }
    }

    fun encode(): String = listOf(qiyam, ruku, sujood, jalsa)
        .joinToString(";") { "${it.gx},${it.gy},${it.gz},${it.dp}" }

    companion object {
        const val MIN_APART_DEG = 20.0

        fun decode(text: String?): Calibration? = try {
            val c = text!!.split(";").map { part ->
                val v = part.split(",").map { it.toDouble() }
                Centroid(v[0], v[1], v[2], v[3])
            }
            Calibration(c[0], c[1], c[2], c[3])
        } catch (e: Exception) {
            null
        }
    }
}

/**
 * Matches the current sensor readings to the nearest calibrated posture.
 *
 * Distance = angle between gravity directions (radians) + a pressure term,
 * scaled so the full standing→sujood pressure difference counts like
 * [PRESSURE_WEIGHT] radians. The barometer is ignored when the calibration
 * shows no usable pressure difference.
 */
class PostureClassifier(private val cal: Calibration) {
    /** Pressure while standing; follows slow drift (weather, air conditioning) during qiyam. */
    var baselinePressure = Double.NaN
        private set

    private val pressureSpan = maxOf(abs(cal.sujood.dp), abs(cal.jalsa.dp), abs(cal.ruku.dp))
    private val usePressure = pressureSpan >= MIN_PRESSURE_SPAN

    fun startBaseline(pressure: Double) {
        baselinePressure = pressure
    }

    /** Call while the imam is known to be standing. */
    fun updateBaseline(pressure: Double) {
        if (pressure.isNaN()) return
        baselinePressure = if (baselinePressure.isNaN()) pressure
        else baselinePressure * (1 - BASELINE_ALPHA) + pressure * BASELINE_ALPHA
    }

    fun classify(gx: Double, gy: Double, gz: Double, gyroEnergy: Double, pressure: Double): Posture {
        if (gyroEnergy > MOVING_ENERGY) return Posture.MOVING
        val dp = if (usePressure && !pressure.isNaN() && !baselinePressure.isNaN()) pressure - baselinePressure
        else Double.NaN

        fun distance(c: Centroid): Double {
            val angle = c.angleTo(gx, gy, gz)
            val press = if (dp.isNaN()) 0.0 else abs(dp - c.dp) / pressureSpan * PRESSURE_WEIGHT
            return angle + press
        }

        val scored = listOf(
            Posture.QIYAM to distance(cal.qiyam),
            Posture.RUKU to distance(cal.ruku),
            Posture.LOW to minOf(distance(cal.sujood), distance(cal.jalsa)),
        ).sortedBy { it.second }
        val (best, bestDistance) = scored[0]
        if (bestDistance > MAX_DISTANCE) return Posture.UNKNOWN
        if (scored[1].second - bestDistance < MIN_MARGIN) return Posture.UNKNOWN
        return best
    }

    companion object {
        /** Gyro energy (rad/s)² above which the arm counts as moving. */
        const val MOVING_ENERGY = 0.6
        const val MAX_DISTANCE = 0.75
        const val MIN_MARGIN = 0.08
        const val PRESSURE_WEIGHT = 0.6
        const val MIN_PRESSURE_SPAN = 0.03
        const val BASELINE_ALPHA = 0.02
    }
}
