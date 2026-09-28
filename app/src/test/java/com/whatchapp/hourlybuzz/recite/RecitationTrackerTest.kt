package com.whatchapp.hourlybuzz.recite

import com.whatchapp.hourlybuzz.recite.ReciteEvent.Kind
import com.whatchapp.hourlybuzz.recite.ReciteEvent.Mistake
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class RecitationTrackerTest {
    companion object {
        /** The app's own asset, found from the module or the repository root. */
        val quran: QuranIndex by lazy {
            val file = listOf("src/main/assets/quran.txt", "app/src/main/assets/quran.txt",
                System.getProperty("quranTxt") ?: "")
                .map { File(it) }.first { it.isFile }
            QuranIndex(file.readLines().asSequence())
        }
    }

    private var t = 0L

    /** What a speech model would hear for these ayat: plain letters, no vowels. */
    private fun heard(surah: Int, from: Int, to: Int = from): List<HypWord> {
        val a = quran.start(surah, from)!!
        val b = quran.start(surah, to + 1) ?: quran.start(surah + 1, 1) ?: quran.size
        return (a until b).map { HypWord(quran.words[it], 0.9) }
    }

    private fun words(text: String, prob: Double = 0.9) = text.split(' ').map { HypWord(it, prob) }

    private fun RecitationTracker.say(words: List<HypWord>): List<ReciteEvent> {
        t += 3000
        val e = onPhrase(words, t)
        t += 600
        return e
    }

    private fun List<ReciteEvent>.mistakes() = filterIsInstance<Mistake>()

    @Test
    fun uthmaniAndPlainSpellingMatch() {
        assertEquals(ArabicText.normalize("الرحمن"), ArabicText.normalize("ٱلرَّحۡمَٰنِ"))
        assertEquals(ArabicText.normalize("آمنوا"), ArabicText.normalize("ءَامَنُواْ"))
        assertTrue(ArabicText.similarity(ArabicText.normalize("ٱلصَّلَوٰةَ"), ArabicText.normalize("الصلاة")) >= 0.8)
        assertTrue(ArabicText.similarity("يايها", ArabicText.normalize("يا")) < 0.6)
    }

    @Test
    fun findsWhereTheRecitationStarts() {
        val r = RecitationTracker(quran)
        val e = r.say(words("الحمد لله رب العالمين الرحمن الرحيم"))
        assertTrue(e.single() is ReciteEvent.Located)
        assertEquals(quran.start(1, 4), r.position)
    }

    @Test
    fun correctRecitationRaisesNoAlarm() {
        val r = RecitationTracker(quran)
        for (ayah in 1..12) {
            assertTrue(r.say(heard(36, ayah)).mistakes().isEmpty())
            assertTrue(r.onSilence(t + 6000).isEmpty()) // pauses at ayah ends are normal
        }
        assertEquals(quran.start(36, 13), r.position)
    }

    @Test
    fun skippedAyahIsReported() {
        val r = RecitationTracker(quran)
        r.say(heard(36, 1, 2))
        r.say(heard(36, 3))
        val e = r.say(heard(36, 5)).mistakes() // 36:4 left out
        assertEquals(Kind.SKIPPED, e.single().kind)
        assertEquals(quran.text(quran.start(36, 4)!!, quran.start(36, 5)!!), e.single().correct)
    }

    @Test
    fun wrongWordIsReportedWithTheCorrection() {
        val r = RecitationTracker(quran)
        r.say(heard(36, 1, 5))
        val ayah = heard(36, 6).toMutableList() // 6 words: a changed word has correct words around it
        val i = 2
        val correct = quran.display[quran.start(36, 6)!! + i]
        ayah[i] = HypWord("الشمس", 0.9)
        val e = r.say(ayah).mistakes()
        assertEquals(Kind.WRONG_WORD, e.single().kind)
        assertEquals(correct, e.single().correct)
        assertEquals("الشمس", e.single().heard)
    }

    @Test
    fun unsureRecognitionDoesNotAlarm() {
        val r = RecitationTracker(quran)
        r.say(heard(36, 1, 5))
        val ayah = heard(36, 6).toMutableList()
        ayah[2] = HypWord("الشمس", 0.3) // the model itself isn't sure
        assertTrue(r.say(ayah).mistakes().isEmpty())
    }

    @Test
    fun stoppingMidAyahPromptsTheNextWords() {
        val r = RecitationTracker(quran)
        r.say(heard(36, 1, 8))
        val ayah9 = heard(36, 9)
        r.say(ayah9.take(4))
        assertTrue(r.onSilence(t + 2000).isEmpty()) // short pause: fine
        val e = r.onSilence(t + 5000).mistakes()
        assertEquals(Kind.FORGOT, e.single().kind)
        val next = quran.start(36, 9)!! + 4
        assertEquals(quran.text(next, next + RecitationTracker.PROMPT_WORDS), e.single().correct)
        assertTrue(r.onSilence(t + 9000).isEmpty()) // prompted once only
    }

    @Test
    fun newSurahAfterFatihaIsNotAMistake() {
        val r = RecitationTracker(quran)
        r.say(words("الله اكبر"))
        r.say(heard(1, 2, 4))
        r.say(heard(1, 5, 7))
        r.say(words("امين"))
        val e = r.say(words("بسم الله الرحمن الرحيم") + heard(112, 1, 2))
        assertTrue(e.mistakes().isEmpty())
        assertEquals(quran.start(112, 3), r.position)
    }

    @Test
    fun continuingFromAnotherPlaceMidAyahIsReported() {
        val r = RecitationTracker(quran)
        r.say(heard(36, 1, 8))
        r.say(heard(36, 9).take(4))
        val e = r.say(heard(2, 255).drop(6).take(8)).mistakes()
        assertEquals(Kind.WRONG_PLACE, e.single().kind)
    }

    @Test
    fun repeatedPhraseIsNotPinnedToOnePlace() {
        val r = RecitationTracker(quran)
        assertNull(r.locate(heard(55, 13)))
        assertFalse(r.following)
        assertNotNull(r.locate(heard(55, 1, 4)))
    }
}
