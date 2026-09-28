package com.whatchapp.hourlybuzz.prayer

import kotlin.math.log10
import kotlin.math.sqrt

/**
 * Notices the imam's voice starting (takbir, «سمع الله لمن حمده») from the
 * loudness of 50 ms microphone frames. Nothing is recorded or recognised here.
 * An onset = louder than the background by [ONSET_DB] for [ONSET_FRAMES] frames.
 */
class VoiceOnsetDetector(private val onOnset: (Long) -> Unit) {
    private var floor = Double.NaN
    private var loudFrames = 0
    private var quietFrames = QUIET_FRAMES

    fun onFrame(samples: ShortArray, count: Int, t: Long) {
        if (count <= 0) return
        var sum = 0.0
        for (i in 0 until count) sum += samples[i].toDouble() * samples[i]
        val db = 20 * log10(sqrt(sum / count) + 1.0)
        if (floor.isNaN()) floor = db
        if (db < floor + QUIET_DB) floor += 0.05 * (db - floor)

        if (db > floor + ONSET_DB) {
            loudFrames++
            quietFrames = 0
            if (loudFrames == ONSET_FRAMES) onOnset(t - ONSET_FRAMES * FRAME_MS)
        } else if (db < floor + QUIET_DB) {
            quietFrames++
            // Wait for a pause before the next onset can count.
            if (quietFrames >= QUIET_FRAMES) loudFrames = 0
        }
    }

    companion object {
        private const val FRAME_MS = 50L
        private const val ONSET_DB = 12.0
        private const val QUIET_DB = 6.0
        private const val ONSET_FRAMES = 5
        private const val QUIET_FRAMES = 6
    }
}
