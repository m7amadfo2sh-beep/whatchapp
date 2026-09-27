package com.whatchapp.hourlybuzz.prayer

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.SystemClock
import android.util.Log
import kotlin.math.log10
import kotlin.math.sqrt

/**
 * Listens for the imam's voice starting (takbir, «سمع الله لمن حمده»).
 *
 * Only the loudness is measured, in 50 ms frames. Nothing is recorded,
 * stored or recognised. An onset = louder than the background by
 * [ONSET_DB] for at least [ONSET_FRAMES] frames.
 */
class VoiceOnsetDetector(private val onOnset: (Long) -> Unit) {
    @Volatile
    private var running = false
    private var thread: Thread? = null

    /** Call only when RECORD_AUDIO is granted. Returns false if the mic can't be opened. */
    @SuppressLint("MissingPermission")
    fun start(): Boolean {
        val minBuffer = AudioRecord.getMinBufferSize(RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        if (minBuffer <= 0) return false
        val record = try {
            AudioRecord(
                MediaRecorder.AudioSource.VOICE_RECOGNITION, RATE,
                AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT,
                maxOf(minBuffer, FRAME * 4 * 2),
            )
        } catch (e: Exception) {
            Log.w(TAG, "Mic unavailable", e)
            return false
        }
        if (record.state != AudioRecord.STATE_INITIALIZED) {
            record.release()
            return false
        }
        running = true
        thread = Thread({ loop(record) }, "voice-onset").apply { start() }
        return true
    }

    fun stop() {
        running = false
        thread?.join(500)
        thread = null
    }

    private fun loop(record: AudioRecord) {
        val buffer = ShortArray(FRAME)
        var floor = Double.NaN
        var loudFrames = 0
        var quietFrames = QUIET_FRAMES
        try {
            record.startRecording()
            while (running) {
                val n = record.read(buffer, 0, FRAME)
                if (n <= 0) continue
                var sum = 0.0
                for (i in 0 until n) sum += buffer[i].toDouble() * buffer[i]
                val db = 20 * log10(sqrt(sum / n) + 1.0)
                if (floor.isNaN()) floor = db
                if (db < floor + QUIET_DB) floor += 0.05 * (db - floor)

                if (db > floor + ONSET_DB) {
                    loudFrames++
                    quietFrames = 0
                    if (loudFrames == ONSET_FRAMES) {
                        onOnset(SystemClock.elapsedRealtime() - ONSET_FRAMES * FRAME_MS)
                    }
                } else if (db < floor + QUIET_DB) {
                    quietFrames++
                    // Wait for a pause before the next onset can count.
                    if (quietFrames >= QUIET_FRAMES) loudFrames = 0
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Mic stopped", e)
        } finally {
            try {
                record.stop()
            } catch (_: Exception) {
            }
            record.release()
        }
    }

    companion object {
        private const val TAG = "VoiceOnset"
        private const val RATE = 16_000
        private const val FRAME_MS = 50L
        private const val FRAME = (RATE * FRAME_MS / 1000).toInt()
        private const val ONSET_DB = 12.0
        private const val QUIET_DB = 6.0
        private const val ONSET_FRAMES = 5
        private const val QUIET_FRAMES = 6
    }
}
