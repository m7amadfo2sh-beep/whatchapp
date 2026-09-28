package com.whatchapp.hourlybuzz.recite

import kotlin.math.log10
import kotlin.math.sqrt

/**
 * Cuts the microphone stream into spoken phrases at the pauses between them,
 * so each phrase can be recognised on its own.
 *
 * Loudness is compared with an adaptive background level. A phrase starts
 * after [START_FRAMES] loud frames (a little audio before it is kept) and
 * ends after [END_SILENCE_MS] of quiet, or is cut at [MAX_PHRASE_MS].
 */
class Segmenter(
    private val sampleRate: Int = 16_000,
    private val onPhrase: (audio: FloatArray, startMs: Long, endMs: Long) -> Unit,
) {
    private var floor = Double.NaN
    private var loudRun = 0
    private var quietMs = 0L
    private var inPhrase = false
    private var phraseStart = 0L
    private val phrase = FloatArrayBuilder()
    private val preroll = ArrayDeque<FloatArray>()
    private var prerollSamples = 0

    /** Time (ms) the last phrase ended, or when listening started. */
    var lastSpeechEnd = 0L
        private set
    val speaking: Boolean get() = inPhrase

    fun onFrame(samples: ShortArray, count: Int, t: Long) {
        if (count <= 0) return
        var sum = 0.0
        val frame = FloatArray(count)
        for (i in 0 until count) {
            val v = samples[i] / 32768f
            frame[i] = v
            sum += v.toDouble() * v
        }
        val db = 20 * log10(sqrt(sum / count) + 1e-9)
        if (floor.isNaN()) {
            floor = db
            lastSpeechEnd = t
        }
        val frameMs = count * 1000L / sampleRate
        val loud = db > floor + START_DB
        val quiet = db < floor + END_DB

        if (!inPhrase) {
            if (quiet) floor += FLOOR_ALPHA * (db - floor)
            keepPreroll(frame)
            loudRun = if (loud) loudRun + 1 else 0
            if (loudRun >= START_FRAMES) {
                inPhrase = true
                quietMs = 0
                phrase.clear()
                preroll.forEach { phrase.add(it) }
                phraseStart = t - prerollSamples * 1000L / sampleRate
                preroll.clear(); prerollSamples = 0
            }
            return
        }

        phrase.add(frame)
        quietMs = if (quiet) quietMs + frameMs else 0
        val length = phrase.size * 1000L / sampleRate
        if (quietMs >= END_SILENCE_MS || length >= MAX_PHRASE_MS) {
            val speechEnd = t - quietMs
            val audio = phrase.toArray()
            inPhrase = false
            loudRun = 0
            lastSpeechEnd = speechEnd
            if (length - quietMs >= MIN_PHRASE_MS) onPhrase(audio, phraseStart, speechEnd)
            if (length >= MAX_PHRASE_MS && quietMs == 0L) {
                // Cut while still speaking: carry straight on with a new phrase.
                inPhrase = true
                phrase.clear()
                phraseStart = t
            }
        }
    }

    private fun keepPreroll(frame: FloatArray) {
        preroll.addLast(frame)
        prerollSamples += frame.size
        while (prerollSamples > sampleRate * PREROLL_MS / 1000) {
            prerollSamples -= preroll.removeFirst().size
        }
    }

    companion object {
        const val START_DB = 10.0
        const val END_DB = 6.0
        const val START_FRAMES = 3
        const val END_SILENCE_MS = 650L
        const val MAX_PHRASE_MS = 12_000L
        const val MIN_PHRASE_MS = 400L
        const val PREROLL_MS = 250L
        const val FLOOR_ALPHA = 0.05
    }
}

/** A growable float buffer. */
class FloatArrayBuilder {
    private var data = FloatArray(16_000 * 4)
    var size = 0
        private set

    fun add(values: FloatArray) {
        if (size + values.size > data.size) data = data.copyOf(maxOf(data.size * 2, size + values.size))
        values.copyInto(data, size)
        size += values.size
    }

    fun clear() {
        size = 0
    }

    fun toArray(): FloatArray = data.copyOf(size)
}
