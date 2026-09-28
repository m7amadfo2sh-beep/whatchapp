package com.whatchapp.hourlybuzz.recite

import com.whatchapp.hourlybuzz.recite.Aligner.Op

/** Something the reciter wants to tell the imam. */
sealed class ReciteEvent {
    /** Found (or moved to) a place in the mushaf. */
    data class Located(val index: Int, val place: String) : ReciteEvent()

    /** Followed along; [index] is the next expected word. */
    data class Progress(val index: Int, val place: String) : ReciteEvent()

    /** A mistake: buzz hard and show [correct]. */
    data class Mistake(
        val kind: Kind,
        val place: String,
        /** The correct mushaf words (Uthmani). */
        val correct: String,
        /** What was heard instead, if anything. */
        val heard: String,
    ) : ReciteEvent()

    enum class Kind { WRONG_WORD, SKIPPED, WRONG_PLACE, FORGOT }
}

/**
 * Follows a recitation through the mushaf, one spoken phrase at a time,
 * and reports mistakes. Built to stay quiet unless it is sure: speech
 * recognition makes errors, and a false alarm during prayer is worse than a
 * missed one.
 *
 * - Wrong word: a clearly different word, confidently heard, with correct
 *   words on both sides.
 * - Skipped: words or ayat left out (at least [MIN_SKIP] words, or a whole
 *   ayah), followed by a solid match.
 * - Wrong place: continued in a different place in the mushaf (e.g. a
 *   similar ayah elsewhere) instead of the next words.
 * - Forgot: stopped in the middle of an ayah for [FORGOT_MS]; the next words
 *   are shown as a prompt.
 */
class RecitationTracker(private val index: QuranIndex) {

    /** Next expected word, or -1 when not following yet. */
    var position = -1
        private set

    private var lastSpeechEnd = 0L
    private var lastGood = false
    private var promptedAt = -1
    private var lastMistakeAt = Long.MIN_VALUE / 2
    private var misses = 0
    /** Where the imam might have deliberately moved to (after a wrong-place alarm). */
    private var alternative = -1
    /** Recent words while searching: short ayat (e.g. «يس») only become findable together. */
    private val recent = ArrayList<HypWord>()

    val following: Boolean get() = position >= 0

    fun reset() {
        position = -1
        recent.clear()
        lastGood = false
        misses = 0
        alternative = -1
    }

    /** A spoken phrase was recognised; [end] = when the phrase ended (ms). */
    fun onPhrase(heard: List<HypWord>, end: Long): List<ReciteEvent> {
        lastSpeechEnd = end
        val hyp = stripNonQuran(heard.filter { it.norm.isNotEmpty() })
        if (hyp.size < MIN_WORDS) {
            // Too short to judge; a takbir, "amin", or a cough.
            lastGood = false
            return emptyList()
        }
        return if (position < 0) search(hyp) else follow(hyp, end)
    }

    /** Called during silence; [now] in ms. May prompt the next words if the imam stopped mid-ayah. */
    fun onSilence(now: Long): List<ReciteEvent> {
        if (position < 0 || !lastGood || index.isAyahStart(position) || promptedAt == position) return emptyList()
        if (now - lastSpeechEnd < FORGOT_MS) return emptyList()
        promptedAt = position
        lastMistakeAt = now
        return listOf(
            ReciteEvent.Mistake(
                ReciteEvent.Kind.FORGOT, index.place(position),
                index.text(position, position + PROMPT_WORDS), "",
            )
        )
    }

    // ---- following -----------------------------------------------------------

    private fun follow(hyp: List<HypWord>, end: Long): List<ReciteEvent> {
        val local = Aligner.align(hyp, index, position, hyp.size * 3 + 30)
        if (local.quality >= GOOD && local.lead <= MAX_LOCAL_LEAD) {
            return accept(local, hyp, end)
        }
        // After a wrong-place alarm: did he deliberately continue there?
        if (alternative >= 0) {
            val alt = Aligner.align(hyp, index, alternative, hyp.size * 3 + 30)
            alternative = -1
            if (alt.quality >= GOOD && alt.lead <= MIN_SKIP) {
                position = alt.end
                lastGood = true
                return listOf(ReciteEvent.Located(position, index.place(position)))
            }
        }
        val found = locate(hyp)
        if (found == null) {
            lastGood = false
            if (++misses >= LOST_AFTER) reset()
            return emptyList()
        }
        misses = 0
        val finishedSurah = index.isSurahStart(position)
        val atAyahEnd = index.isAyahStart(position)
        val resemblesExpected = local.quality >= RESEMBLES
        val silentMove = finishedSurah || (atAyahEnd && !resemblesExpected)
        if (silentMove) {
            position = found.end
            lastGood = true
            return listOf(ReciteEvent.Located(position, index.place(position)))
        }
        // Mid-ayah jump, or a similar ayah from elsewhere: a classic mix-up.
        val events = ArrayList<ReciteEvent>()
        if (mayAlarm(end)) {
            events += ReciteEvent.Mistake(
                ReciteEvent.Kind.WRONG_PLACE, index.place(position),
                index.text(position, position + PROMPT_WORDS), index.place(found.first),
            )
            lastMistakeAt = end
        }
        alternative = found.end
        lastGood = false
        return events
    }

    private fun accept(r: Aligner.Result, hyp: List<HypWord>, end: Long): List<ReciteEvent> {
        val events = ArrayList<ReciteEvent>()
        val mistake = skippedAtStart(r) ?: skippedInside(r) ?: wrongWord(r, hyp)
        if (mistake != null && mayAlarm(end)) {
            events += mistake
            lastMistakeAt = end
        }
        position = r.end
        misses = 0
        alternative = -1
        val last = r.steps.lastOrNull()
        lastGood = last != null && (last.op == Op.MATCH || last.op == Op.NEAR)
        events += ReciteEvent.Progress(position, index.place(position))
        return events
    }

    private fun skippedAtStart(r: Aligner.Result): ReciteEvent.Mistake? {
        if (r.lead <= 0 || !anchoredStart(r)) return null
        val from = position
        val to = position + r.lead
        val wholeAyat = index.isAyahStart(from) && index.isAyahStart(to)
        if (r.lead < MIN_SKIP && !wholeAyat) return null
        return ReciteEvent.Mistake(ReciteEvent.Kind.SKIPPED, index.place(from), index.text(from, minOf(to, from + MAX_SHOWN)), "")
    }

    private fun skippedInside(r: Aligner.Result): ReciteEvent.Mistake? {
        val s = r.steps
        var i = 0
        while (i < s.size) {
            if (s[i].op != Op.MISSING) { i++; continue }
            var j = i
            while (j < s.size && s[j].op == Op.MISSING) j++
            val count = s[i].r0.let { s[j - 1].r1 - it }
            if (count >= MIN_SKIP && isGood(s, i - 1) && isGood(s, j) && isGood(s, j + 1)) {
                val from = s[i].r0
                return ReciteEvent.Mistake(
                    ReciteEvent.Kind.SKIPPED, index.place(from),
                    index.text(from, minOf(from + count, from + MAX_SHOWN)), "",
                )
            }
            i = j
        }
        return null
    }

    private fun wrongWord(r: Aligner.Result, hyp: List<HypWord>): ReciteEvent.Mistake? {
        val s = r.steps
        var i = 0
        while (i < s.size) {
            if (s[i].op != Op.SUB) { i++; continue }
            var j = i
            while (j < s.size && s[j].op == Op.SUB) j++
            val run = s.subList(i, j)
            val confident = run.all { st -> (st.h0 until st.h1).all { hyp[it].prob >= MIN_PROB } }
            val different = run.all { st ->
                ArabicText.similarity(hyp[st.h0].norm, index.words[st.r0]) < MAX_SIMILAR
            }
            if (run.size <= 3 && confident && different && isGood(s, i - 1) && isGood(s, j)) {
                val r0 = run.first().r0
                val r1 = run.last().r1
                val heard = run.joinToString(" ") { st -> (st.h0 until st.h1).joinToString(" ") { hyp[it].text } }
                return ReciteEvent.Mistake(ReciteEvent.Kind.WRONG_WORD, index.place(r0), index.text(r0, r1), heard)
            }
            i = j
        }
        return null
    }

    private fun isGood(s: List<Aligner.Step>, k: Int) = k in s.indices && (s[k].op == Op.MATCH || s[k].op == Op.NEAR)

    private fun anchoredStart(r: Aligner.Result) = (0 until minOf(ANCHOR, r.steps.size)).all { isGood(r.steps, it) }

    private fun mayAlarm(now: Long) = now - lastMistakeAt >= MIN_ALARM_GAP_MS

    // ---- finding the place ---------------------------------------------------

    private fun search(hyp: List<HypWord>): List<ReciteEvent> {
        recent += hyp
        while (recent.size > SEARCH_WORDS) recent.removeAt(0)
        val found = locate(hyp) ?: locate(recent) ?: return emptyList()
        recent.clear()
        position = found.end
        lastGood = true
        misses = 0
        return listOf(ReciteEvent.Located(position, index.place(position)))
    }

    /** Finds the one place in the whole Quran that matches [hyp], or null if none/ambiguous. */
    fun locate(hyp: List<HypWord>): Aligner.Result? {
        if (hyp.size < 3) return null
        val starts = HashMap<Int, Int>() // candidate start -> votes
        for (k in 0..hyp.size - 3) {
            for (p in index.find(hyp[k].norm, hyp[k + 1].norm, hyp[k + 2].norm)) {
                val s = (p - k).coerceAtLeast(0)
                starts[s] = (starts[s] ?: 0) + 1
            }
        }
        if (starts.isEmpty()) return null
        val candidates = starts.entries.sortedByDescending { it.value }.take(MAX_CANDIDATES)
        val results = candidates.map { (s, _) ->
            Aligner.align(hyp, index, (s - 3).coerceAtLeast(0), hyp.size * 2 + 10)
        }.sortedByDescending { it.score }
        val best = results.first()
        if (best.quality < GOOD || best.matched < 3) return null
        // Must be clearly better than any other place (many phrases repeat in the Quran).
        val rival = results.drop(1).firstOrNull { kotlin.math.abs(it.first - best.first) > 5 }
        if (rival != null && best.score - rival.score < UNIQUE_MARGIN) return null
        return best
    }

    // ---- words that aren't part of the recitation ----------------------------

    private fun stripNonQuran(words: List<HypWord>): List<HypWord> {
        var list = words
        var changed = true
        while (changed && list.isNotEmpty()) {
            changed = false
            for (p in NON_QURAN) {
                if (startsWith(list, p)) { list = list.drop(p.size); changed = true }
                if (endsWith(list, p)) { list = list.dropLast(p.size); changed = true }
            }
        }
        return list
    }

    private fun startsWith(list: List<HypWord>, p: List<String>) =
        list.size >= p.size && p.indices.all { ArabicText.similarity(list[it].norm, p[it]) >= 0.8 }

    private fun endsWith(list: List<HypWord>, p: List<String>) =
        list.size >= p.size && p.indices.all { ArabicText.similarity(list[list.size - p.size + it].norm, p[it]) >= 0.8 }

    companion object {
        const val MIN_WORDS = 2
        const val GOOD = 0.6
        const val RESEMBLES = 0.3
        const val MAX_LOCAL_LEAD = 40
        const val MIN_SKIP = 3
        const val ANCHOR = 3
        const val MIN_PROB = 0.55
        const val MAX_SIMILAR = 0.5
        const val FORGOT_MS = 4000L
        const val MIN_ALARM_GAP_MS = 4000L
        const val PROMPT_WORDS = 6
        const val MAX_SHOWN = 12
        const val LOST_AFTER = 3
        const val MAX_CANDIDATES = 25
        const val UNIQUE_MARGIN = 2.0
        const val SEARCH_WORDS = 16

        /** Said aloud in prayer but not part of the recited text. */
        val NON_QURAN: List<List<String>> = listOf(
            "امين", "الله اكبر", "سمع الله لمن حمده", "ربنا ولك الحمد", "ربنا لك الحمد",
            "السلام عليكم ورحمه الله", "بسم الله الرحمن الرحيم", "اعوذ بالله من الشيطان الرجيم",
        ).map { ArabicText.words(it) }
    }
}
