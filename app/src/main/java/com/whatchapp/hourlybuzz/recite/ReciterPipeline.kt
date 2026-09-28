package com.whatchapp.hourlybuzz.recite

import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * Microphone frames → phrases → speech recognition → mushaf tracking → events.
 *
 * Recognition runs on one background thread, so phrases are handled in order
 * and silence checks never overtake a phrase that is still being recognised.
 * If recognition falls behind, the oldest waiting phrases are dropped rather
 * than letting delays pile up.
 */
class ReciterPipeline(
    private val tracker: RecitationTracker,
    private val transcriber: Transcriber,
    private val onEvent: (ReciteEvent) -> Unit,
    private val onHeard: (List<HypWord>) -> Unit = {},
) {
    private val worker = Executors.newSingleThreadExecutor { r -> Thread(r, "reciter").apply { priority = Thread.NORM_PRIORITY } }
    private val waiting = AtomicInteger(0)
    private val segmenter = Segmenter { audio, _, end -> submit(audio, end) }
    private var lastSilenceCheck = 0L

    /** Feed 16 kHz mono PCM; [t] = time of the frame (ms). Call from the mic thread. */
    fun onFrame(samples: ShortArray, count: Int, t: Long) {
        segmenter.onFrame(samples, count, t)
        if (!segmenter.speaking && t - lastSilenceCheck >= 500 && waiting.get() == 0) {
            lastSilenceCheck = t
            worker.execute { tracker.onSilence(t).forEach(onEvent) }
        }
    }

    private fun submit(audio: FloatArray, end: Long) {
        if (waiting.get() >= MAX_WAITING) return // falling behind: skip this phrase
        waiting.incrementAndGet()
        worker.execute {
            try {
                val words = transcriber.transcribe(audio)
                onHeard(words)
                tracker.onPhrase(words, end).forEach(onEvent)
            } catch (e: Exception) {
                // Never let one bad phrase stop the reciter.
            } finally {
                waiting.decrementAndGet()
            }
        }
    }

    fun close() {
        worker.shutdown()
        worker.awaitTermination(3, TimeUnit.SECONDS)
    }

    companion object {
        const val MAX_WAITING = 3
    }
}
