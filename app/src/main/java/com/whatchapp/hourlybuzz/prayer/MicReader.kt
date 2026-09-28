package com.whatchapp.hourlybuzz.prayer

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.SystemClock
import android.util.Log

/**
 * Reads the microphone (16 kHz mono) on its own thread and hands each 50 ms
 * frame to the listeners: the takbir detector and the recitation checker.
 * Audio is only passed along in memory, never saved.
 */
class MicReader(private val listeners: List<(ShortArray, Int, Long) -> Unit>) {
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
                maxOf(minBuffer, FRAME * 2 * 8),
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
        thread = Thread({ loop(record) }, "mic").apply { start() }
        return true
    }

    fun stop() {
        running = false
        thread?.join(1000)
        thread = null
    }

    private fun loop(record: AudioRecord) {
        val buffer = ShortArray(FRAME)
        try {
            record.startRecording()
            while (running) {
                val n = record.read(buffer, 0, FRAME)
                if (n <= 0) continue
                val t = SystemClock.elapsedRealtime()
                for (l in listeners) {
                    try {
                        l(buffer, n, t)
                    } catch (e: Exception) {
                        Log.w(TAG, "Listener failed", e)
                    }
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
        private const val TAG = "MicReader"
        const val RATE = 16_000
        const val FRAME = 800 // 50 ms
    }
}
