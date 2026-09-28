package com.whatchapp.hourlybuzz.recite

import com.whatchapp.hourlybuzz.recite.ReciteEvent.Kind
import com.whatchapp.hourlybuzz.recite.ReciteEvent.Mistake
import org.junit.AfterClass
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.BeforeClass
import org.junit.Test
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.random.Random

/**
 * End-to-end check with real recitation audio (Mishary Alafasy) and the real
 * speech model: phrase cutting → Quran-trained Whisper → mushaf tracking.
 *
 * Needs the host-built native library, the model and the clips from the
 * 'model' branch; skipped unless -DreciteE2E=<dir> points to them
 * (dir/libhourlybuzz_whisper.so, dir/model.bin, and WAV clips in dir/audio).
 */
class RecitationAudioTest {
    companion object {
        private var dir: File? = null
        private var whisper: WhisperTranscriber? = null

        @BeforeClass
        @JvmStatic
        fun setUp() {
            val d = System.getProperty("reciteE2E")?.let { File(it) } ?: return
            if (!File(d, "model.bin").isFile) return
            WhisperLib.load(File(d, "libhourlybuzz_whisper.so").absolutePath)
            whisper = WhisperTranscriber(File(d, "model.bin").path)
            dir = d
        }

        @AfterClass
        @JvmStatic
        fun tearDown() {
            whisper?.close()
        }
    }

    private val rate = 16_000
    private val noise = Random(7)

    private fun clip(ref: String): ShortArray {
        val bytes = File(dir, "audio/$ref.wav").readBytes()
        var i = 12
        while (i + 8 <= bytes.size) { // find the "data" chunk
            val id = String(bytes, i, 4, Charsets.US_ASCII)
            val size = ByteBuffer.wrap(bytes, i + 4, 4).order(ByteOrder.LITTLE_ENDIAN).int
            if (id == "data") {
                val sb = ByteBuffer.wrap(bytes, i + 8, size).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()
                return ShortArray(sb.remaining()).also { sb.get(it) }
            }
            i += 8 + size
        }
        error("no audio in $ref")
    }

    /** Quiet room noise between phrases. */
    private fun pause(seconds: Double) = ShortArray((seconds * rate).toInt()) { (noise.nextInt(-60, 60)).toShort() }

    private fun ayah(surah: Int, ayah: Int) = clip("%03d%03d".format(surah, ayah))

    /** Plays the audio through the whole pipeline; returns all events. */
    private fun recite(vararg parts: ShortArray): List<ReciteEvent> {
        val tracker = RecitationTracker(RecitationTrackerTest.quran)
        val events = ArrayList<ReciteEvent>()
        val segmenter = Segmenter { audio, _, end ->
            val words = whisper!!.transcribe(audio)
            println("  heard: ${words.joinToString(" ") { it.text }}")
            events += tracker.onPhrase(words, end).onEach { println("  -> $it") }
        }
        val all = parts.flatMap { it.asIterable() }.toShortArray()
        val frame = 800
        var lastCheck = 0L
        var i = 0
        while (i < all.size) {
            val n = minOf(frame, all.size - i)
            val t = i * 1000L / rate
            segmenter.onFrame(all.copyOfRange(i, i + n), n, t)
            if (!segmenter.speaking && t - lastCheck >= 500) {
                lastCheck = t
                events += tracker.onSilence(t).onEach { println("  -> $it") }
            }
            i += n
        }
        return events
    }

    private fun List<ReciteEvent>.mistakes() = filterIsInstance<Mistake>()

    @Test
    fun fatihaIsFollowedWithoutAlarms() {
        assumeTrue(dir != null)
        val parts = (1..7).flatMap { listOf(ayah(1, it), pause(1.2)) }
        val e = recite(*parts.toTypedArray())
        assertTrue(e.mistakes().toString(), e.mistakes().isEmpty())
        assertTrue(e.any { it is ReciteEvent.Located })
    }

    @Test
    fun yasinReadCorrectlyRaisesNoAlarm() {
        assumeTrue(dir != null)
        val parts = (1..10).flatMap { listOf(ayah(36, it), pause(1.2)) }
        val e = recite(*parts.toTypedArray())
        assertTrue(e.mistakes().toString(), e.mistakes().isEmpty())
    }

    @Test
    fun skippedAyahIsCaught() {
        assumeTrue(dir != null)
        val order = listOf(1, 2, 3, 5, 6, 7) // 36:4 left out
        val parts = order.flatMap { listOf(ayah(36, it), pause(1.2)) }
        val m = recite(*parts.toTypedArray()).mistakes()
        assertEquals(m.toString(), listOf(Kind.SKIPPED), m.map { it.kind })
        assertTrue(m[0].place.contains("٤"))
    }

    @Test
    fun stoppingMidAyahGetsAPrompt() {
        assumeTrue(dir != null)
        val a9 = ayah(36, 9)
        val half = a9.copyOfRange(0, a9.size / 2)
        val parts = (1..8).flatMap { listOf(ayah(36, it), pause(1.2)) } + listOf(half, pause(6.0))
        val m = recite(*parts.toTypedArray()).mistakes()
        assertEquals(m.toString(), listOf(Kind.FORGOT), m.map { it.kind })
    }

    @Test
    fun jumpingElsewhereMidAyahIsCaught() {
        assumeTrue(dir != null)
        val a9 = ayah(36, 9)
        val half = a9.copyOfRange(0, a9.size / 2)
        val parts = (6..8).flatMap { listOf(ayah(36, it), pause(1.2)) } +
            listOf(half, pause(1.0), ayah(2, 255), pause(1.2))
        val m = recite(*parts.toTypedArray()).mistakes()
        assertTrue(m.toString(), m.any { it.kind == Kind.WRONG_PLACE })
    }
}
