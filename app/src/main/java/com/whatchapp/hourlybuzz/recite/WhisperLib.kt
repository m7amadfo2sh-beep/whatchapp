package com.whatchapp.hourlybuzz.recite

import java.nio.ByteBuffer
import java.nio.ByteOrder

/** JNI bridge to whisper.cpp (app/src/main/cpp). */
object WhisperLib {
    @JvmStatic external fun init(modelPath: String): Long
    @JvmStatic external fun free(context: Long)
    @JvmStatic external fun transcribe(context: Long, audio: FloatArray, threads: Int, audioCtx: Int): ByteArray

    /** Loads the native library (on the watch: from the APK). */
    fun load(path: String? = null) {
        if (path != null) System.load(path) else System.loadLibrary("hourlybuzz_whisper")
    }
}

/** Something that turns a spoken phrase (16 kHz mono) into words. */
fun interface Transcriber {
    fun transcribe(audio: FloatArray): List<HypWord>
}

/**
 * Speech recognition with a Quran-trained Whisper model through whisper.cpp.
 * Not thread-safe: call from one thread.
 */
class WhisperTranscriber(modelPath: String, private val threads: Int = 4) : Transcriber, AutoCloseable {
    private var ctx = WhisperLib.init(modelPath)

    init {
        require(ctx != 0L) { "Could not load speech model: $modelPath" }
    }

    override fun transcribe(audio: FloatArray): List<HypWord> {
        if (ctx == 0L || audio.isEmpty()) return emptyList()
        // Whisper normally looks at a 30 s window; a smaller window for a short
        // phrase is much faster (50 frames per second of audio, plus a margin).
        val seconds = audio.size / 16_000.0
        val audioCtx = if (seconds < 25) ((seconds * 50).toInt() + 64).coerceIn(128, 1500) else 0
        return parseTokens(WhisperLib.transcribe(ctx, audio, threads, audioCtx))
    }

    override fun close() {
        if (ctx != 0L) WhisperLib.free(ctx)
        ctx = 0
    }

    companion object {
        /**
         * Rebuilds words from the native token list (probability, length, UTF-8
         * bytes). A word is split at spaces; its confidence is the lowest of its
         * tokens' probabilities.
         */
        fun parseTokens(raw: ByteArray): List<HypWord> {
            val buf = ByteBuffer.wrap(raw).order(ByteOrder.LITTLE_ENDIAN)
            val words = ArrayList<HypWord>()
            val current = java.io.ByteArrayOutputStream()
            var prob = 1.0
            fun flush() {
                if (current.size() > 0) {
                    val text = current.toByteArray().toString(Charsets.UTF_8).trim()
                    if (text.isNotEmpty()) words += HypWord(text, prob)
                }
                current.reset()
                prob = 1.0
            }
            while (buf.remaining() >= 6) {
                val p = buf.float.toDouble()
                val len = (buf.get().toInt() and 0xff) or ((buf.get().toInt() and 0xff) shl 8)
                if (len > buf.remaining()) break
                val bytes = ByteArray(len).also { buf.get(it) }
                for (b in bytes) {
                    if (b == ' '.code.toByte()) flush()
                    else current.write(b.toInt())
                }
                // A token belongs to the word it adds bytes to.
                if (bytes.any { it != ' '.code.toByte() }) prob = minOf(prob, p)
            }
            flush()
            return words
        }
    }
}
