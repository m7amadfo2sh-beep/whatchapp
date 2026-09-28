package com.whatchapp.hourlybuzz.recite

/**
 * The whole Quran as one long list of words, for following a recitation.
 *
 * Built from assets/quran.txt ("surah|ayah|text" per line, Uthmani text from
 * QuranEnc). Each word keeps its original Uthmani form for display and a
 * normalized form for matching. A 3-word index finds where a phrase occurs.
 */
class QuranIndex(lines: Sequence<String>, surahNames: Map<Int, String> = emptyMap()) {

    /** Normalized words, in mushaf order. */
    val words: Array<String>
    /** The Uthmani text of each word, for display. */
    val display: Array<String>
    val surahOf: IntArray
    val ayahOf: IntArray
    /** Position of the first word of each ayah ("surah:ayah" -> index). */
    private val ayahStart = HashMap<Int, Int>()
    private val trigrams = HashMap<String, IntArray>()
    private val names = surahNames

    init {
        val w = ArrayList<String>(80_000)
        val d = ArrayList<String>(80_000)
        val s = ArrayList<Int>(80_000)
        val a = ArrayList<Int>(80_000)
        for (line in lines) {
            val parts = line.split('|', limit = 3)
            if (parts.size < 3) continue
            val surah = parts[0].toIntOrNull() ?: continue
            val ayah = parts[1].toIntOrNull() ?: continue
            ayahStart[key(surah, ayah)] = w.size
            for (token in parts[2].split(' ')) {
                val n = ArabicText.normalize(token)
                if (n.isEmpty()) continue // pause signs such as "۞" on their own
                w += n; d += token; s += surah; a += ayah
            }
        }
        words = w.toTypedArray()
        display = d.toTypedArray()
        surahOf = s.toIntArray()
        ayahOf = a.toIntArray()

        val lists = HashMap<String, ArrayList<Int>>()
        for (i in 0 until words.size - 2) {
            lists.getOrPut(trigramKey(words[i], words[i + 1], words[i + 2])) { ArrayList(2) } += i
        }
        for ((k, v) in lists) trigrams[k] = v.toIntArray()
    }

    val size: Int get() = words.size

    fun start(surah: Int, ayah: Int): Int? = ayahStart[key(surah, ayah)]

    /** True when [i] is the first word of an ayah. */
    fun isAyahStart(i: Int): Boolean = i <= 0 || i >= size || ayahOf[i] != ayahOf[i - 1] || surahOf[i] != surahOf[i - 1]

    /** True when [i] is the first word of a surah (or past the end). */
    fun isSurahStart(i: Int): Boolean = i <= 0 || i >= size || surahOf[i] != surahOf[i - 1]

    /** Where a 3-word phrase occurs (exact normalized match, vowels ignored). */
    fun find(w1: String, w2: String, w3: String): IntArray = trigrams[trigramKey(w1, w2, w3)] ?: EMPTY

    /** Uthmani text of words [from, to). */
    fun text(from: Int, to: Int): String =
        (from.coerceAtLeast(0) until to.coerceAtMost(size)).joinToString(" ") { display[it] }

    fun surahName(surah: Int): String = names[surah] ?: surah.toString()

    /** e.g. "البقرة ٢٥٥" */
    fun place(i: Int): String {
        val j = i.coerceIn(0, size - 1)
        return "${surahName(surahOf[j])} ${ArabicText.digits(ayahOf[j])}"
    }

    private fun key(surah: Int, ayah: Int) = surah * 1000 + ayah

    companion object {
        private val EMPTY = IntArray(0)

        private fun trigramKey(a: String, b: String, c: String) =
            ArabicText.consonants(a) + " " + ArabicText.consonants(b) + " " + ArabicText.consonants(c)
    }
}
