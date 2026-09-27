package com.whatchapp.hourlybuzz.prayer

/**
 * Turns raw sensor samples into rak'ah counts and shake answers.
 * Pure Kotlin (no Android), so it can be unit-tested; PrayerService feeds it.
 * All times are milliseconds on one monotonic clock.
 */
class PrayerEngine(
    val total: Int,
    calibration: Calibration,
    private val listener: Listener,
) {
    interface Listener {
        /** The imam shook his hand: tell him how many rak'ahs are left after this one. */
        fun onShake(remainingAfterCurrent: Int)
        fun onStableChange(posture: Posture, rakah: Int) {}
        fun onRawPosture(posture: Posture) {}
    }

    val counter = RakahCounter(total)
    private val classifier = PostureClassifier(calibration)
    private val shake = ShakeDetector()

    private var gx = Double.NaN
    private var gy = 0.0
    private var gz = 0.0
    private var gyroEnergy = 0.0
    private var pressure = Double.NaN
    private var lastClassify = Long.MIN_VALUE / 2
    var rawPosture = Posture.UNKNOWN
        private set

    fun onAccel(t: Long, x: Double, y: Double, z: Double) {
        if (gx.isNaN()) {
            gx = x; gy = y; gz = z
        } else {
            gx += GRAVITY_ALPHA * (x - gx)
            gy += GRAVITY_ALPHA * (y - gy)
            gz += GRAVITY_ALPHA * (z - gz)
        }
        if (t - lastClassify < CLASSIFY_EVERY_MS) return
        lastClassify = t
        val n = norm(gx, gy, gz)
        val posture = classifier.classify(gx / n, gy / n, gz / n, gyroEnergy, pressure)
        if (posture != rawPosture) {
            rawPosture = posture
            listener.onRawPosture(posture)
        }
        if (counter.onPosture(posture, t)) listener.onStableChange(posture, counter.rakah)
        if (counter.stable == Posture.QIYAM && posture == Posture.QIYAM) classifier.updateBaseline(pressure)
    }

    fun onGyro(t: Long, x: Double, y: Double, z: Double) {
        gyroEnergy += ENERGY_ALPHA * ((x * x + y * y + z * z) - gyroEnergy)
        if (shake.onGyro(t, x, y, z) && t - counter.stableSince >= SHAKE_QUIET_AFTER_CHANGE_MS) {
            listener.onShake(counter.remainingAfterCurrent())
        }
    }

    fun onPressure(@Suppress("UNUSED_PARAMETER") t: Long, hPa: Double) {
        if (pressure.isNaN()) {
            pressure = hPa
            // The prayer starts standing.
            classifier.startBaseline(hPa)
        } else {
            pressure += PRESSURE_ALPHA * (hPa - pressure)
        }
    }

    fun onVoice(t: Long) = counter.onVoice(t)

    companion object {
        const val GRAVITY_ALPHA = 0.1
        const val ENERGY_ALPHA = 0.1
        const val PRESSURE_ALPHA = 0.2
        const val CLASSIFY_EVERY_MS = 100L
        const val SHAKE_QUIET_AFTER_CHANGE_MS = 1500L
    }
}
