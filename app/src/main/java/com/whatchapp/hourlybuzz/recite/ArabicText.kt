package com.whatchapp.hourlybuzz.recite

import java.text.Normalizer

/**
 * Arabic word comparison that tolerates the differences between the Uthmani
 * mushaf spelling and ordinary spelling (what speech recognition produces):
 * vowel marks, Quranic signs, tatweel and hamza/alif forms are ignored.
 */
object ArabicText {

    private fun isMark(c: Char): Boolean {
        val code = c.code
        return code in 0x0610..0x061A || code in 0x064B..0x065F || code == 0x0670 ||
            code in 0x06D6..0x06ED || code == 0x0640
    }

    /** Letters only: "ٱلرَّحۡمَٰنِ" and "الرحمن" both become "الرحمن". */
    fun normalize(word: String): String {
        val text = Normalizer.normalize(word, Normalizer.Form.NFKC)
        val out = StringBuilder(text.length)
        for (c in text) {
            if (isMark(c)) continue
            when (c) {
                'أ', 'إ', 'آ', 'ٱ' -> out.append('ا')
                'ى' -> out.append('ي')
                'ة' -> out.append('ه')
                'ؤ' -> out.append('و')
                'ئ' -> out.append('ي')
                'ء' -> Unit
                else -> if (Character.isLetter(c)) out.append(c)
            }
        }
        return out.toString()
    }

    /** Splits text into normalized words, dropping anything that has no letters. */
    fun words(text: String): List<String> =
        text.split(' ', '\n', '\t', '،', ',', '.', '؟', '?', '!', '«', '»', '(', ')', '[', ']')
            .map { normalize(it) }
            .filter { it.isNotEmpty() }

    /** Without long vowels (ا و ي), for comparing spellings like «الصلوة» / «الصلاة». */
    fun consonants(w: String): String = w.filter { it != 'ا' && it != 'و' && it != 'ي' }

    /** 0..1 similarity of two normalized words. */
    fun similarity(a: String, b: String): Double {
        if (a == b) return 1.0
        if (a.isEmpty() || b.isEmpty()) return 0.0
        val direct = 1.0 - levenshtein(a, b).toDouble() / maxOf(a.length, b.length)
        val ca = consonants(a)
        val cb = consonants(b)
        val skeleton = if (ca.isNotEmpty() && ca == cb) 0.9 else 0.0
        return maxOf(direct, skeleton)
    }

    fun levenshtein(a: String, b: String): Int {
        var prev = IntArray(b.length + 1) { it }
        var cur = IntArray(b.length + 1)
        for (i in 1..a.length) {
            cur[0] = i
            for (j in 1..b.length) {
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                cur[j] = minOf(prev[j] + 1, cur[j - 1] + 1, prev[j - 1] + cost)
            }
            val t = prev; prev = cur; cur = t
        }
        return prev[b.length]
    }

    /** 12 -> "١٢" */
    fun digits(n: Int): String = n.toString().map { '٠' + (it - '0') }.joinToString("")
}
