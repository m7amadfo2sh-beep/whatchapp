package com.whatchapp.hourlybuzz.prayer

import kotlin.math.abs

/**
 * Detects a deliberate hand shake: fast wrist twists back and forth.
 *
 * On any gyroscope axis, [peaksNeeded] fast rotations (> [threshold] rad/s)
 * in alternating directions within [windowMs] trigger it. Prayer movements
 * are slow and one-directional, so they don't.
 */
class ShakeDetector(
    private val threshold: Double = 5.0,
    private val windowMs: Long = 800,
    private val peaksNeeded: Int = 3,
    private val refractoryMs: Long = 1500,
) {
    private val armed = BooleanArray(3) { true }
    private val peaks = Array(3) { ArrayDeque<Pair<Long, Int>>() }
    private var lastTrigger = Long.MIN_VALUE / 2

    /** Feed one gyroscope sample (rad/s). Returns true when a shake is recognised. */
    fun onGyro(t: Long, x: Double, y: Double, z: Double): Boolean {
        val values = doubleArrayOf(x, y, z)
        var shake = false
        for (i in 0..2) {
            val v = values[i]
            if (armed[i] && abs(v) > threshold) {
                armed[i] = false
                val list = peaks[i]
                val sign = if (v > 0) 1 else -1
                // Keep the list alternating: a repeat in the same direction replaces the last one.
                if (list.isNotEmpty() && list.last().second == sign) list.removeLast()
                list.addLast(t to sign)
                while (list.isNotEmpty() && t - list.first().first > windowMs) list.removeFirst()
                if (list.size >= peaksNeeded && t - lastTrigger >= refractoryMs) shake = true
            } else if (!armed[i] && abs(v) < threshold * 0.4) {
                armed[i] = true
            }
        }
        if (shake) {
            lastTrigger = t
            peaks.forEach { it.clear() }
        }
        return shake
    }
}
