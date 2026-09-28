package com.whatchapp.hourlybuzz.recite

/** One recognised word and how sure the speech model was (0..1). */
data class HypWord(val text: String, val prob: Double = 1.0) {
    val norm: String = ArabicText.normalize(text)
}

/**
 * Lines up what was heard with the mushaf text starting at a given word,
 * by dynamic programming. The heard words must all be used; the mushaf side
 * may start later (words skipped at the start) and may end anywhere.
 */
object Aligner {
    enum class Op { MATCH, NEAR, SUB, EXTRA, MISSING }

    /** One step: hyp words [h0, h1) against mushaf words [r0, r1) (absolute indices). */
    data class Step(val op: Op, val h0: Int, val h1: Int, val r0: Int, val r1: Int)

    class Result(
        val steps: List<Step>,
        /** Mushaf words skipped before the first heard word. */
        val lead: Int,
        /** First mushaf word after the aligned part. */
        val end: Int,
        val score: Double,
        val hypCount: Int,
    ) {
        /** Heard words that matched the mushaf. */
        val matched: Int = steps.filter { it.op == Op.MATCH || it.op == Op.NEAR }.sumOf { it.h1 - it.h0 }
        val quality: Double = if (hypCount == 0) 0.0 else matched.toDouble() / hypCount
        val first: Int get() = end - steps.filter { it.op != Op.EXTRA }.sumOf { it.r1 - it.r0 }
    }

    private const val MATCH = 2.0
    private const val NEAR = 1.0
    private const val SUB = -1.0
    private const val GAP = -1.0
    private const val LEAD = -0.3

    fun align(hyp: List<HypWord>, index: QuranIndex, from: Int, maxRef: Int): Result {
        val n = hyp.size
        val m = maxRef.coerceAtMost(index.size - from).coerceAtLeast(0)
        val ref = Array(m) { index.words[from + it] }
        val h = Array(n) { hyp[it].norm }
        val score = Array(n + 1) { DoubleArray(m + 1) { Double.NEGATIVE_INFINITY } }
        val back = Array(n + 1) { IntArray(m + 1) } // encoded move
        for (j in 0..m) score[0][j] = LEAD * j
        for (i in 1..n) {
            for (j in 0..m) {
                var best = Double.NEGATIVE_INFINITY
                var move = 0
                fun consider(v: Double, mv: Int) {
                    if (v > best) { best = v; move = mv }
                }
                // Extra heard word.
                consider(score[i - 1][j] + GAP, MOVE_EXTRA)
                if (j >= 1) {
                    // Missing mushaf word (inside the aligned part).
                    if (i >= 1) consider(score[i][j - 1] + GAP, MOVE_MISSING)
                    val s = ArabicText.similarity(h[i - 1], ref[j - 1])
                    consider(score[i - 1][j - 1] + pairScore(s), MOVE_PAIR)
                    // Two heard words = one mushaf word ("يا ايها" / "يايها").
                    if (i >= 2) {
                        val s2 = ArabicText.similarity(h[i - 2] + h[i - 1], ref[j - 1])
                        if (s2 >= 0.8) consider(score[i - 2][j - 1] + MATCH, MOVE_MERGE)
                    }
                    // One heard word = two mushaf words.
                    if (j >= 2) {
                        val s2 = ArabicText.similarity(h[i - 1], ref[j - 2] + ref[j - 1])
                        if (s2 >= 0.8) consider(score[i - 1][j - 2] + MATCH, MOVE_SPLIT)
                    }
                }
                score[i][j] = best
                back[i][j] = move
            }
        }
        var bestJ = 0
        for (j in 0..m) if (score[n][j] > score[n][bestJ]) bestJ = j

        val steps = ArrayList<Step>()
        var i = n
        var j = bestJ
        while (i > 0) {
            when (back[i][j]) {
                MOVE_EXTRA -> { steps += Step(Op.EXTRA, i - 1, i, from + j, from + j); i-- }
                MOVE_MISSING -> { steps += Step(Op.MISSING, i, i, from + j - 1, from + j); j-- }
                MOVE_PAIR -> {
                    val s = ArabicText.similarity(h[i - 1], ref[j - 1])
                    val op = when {
                        s >= 0.8 -> Op.MATCH
                        s >= 0.6 -> Op.NEAR
                        else -> Op.SUB
                    }
                    steps += Step(op, i - 1, i, from + j - 1, from + j); i--; j--
                }
                MOVE_MERGE -> { steps += Step(Op.MATCH, i - 2, i, from + j - 1, from + j); i -= 2; j-- }
                MOVE_SPLIT -> { steps += Step(Op.MATCH, i - 1, i, from + j - 2, from + j); i--; j -= 2 }
            }
        }
        steps.reverse()
        // Leading "missing" steps are really skipped words before the recitation resumed.
        var lead = j
        while (steps.isNotEmpty() && steps[0].op == Op.MISSING) {
            steps.removeAt(0)
            lead++
        }
        return Result(steps, lead, from + bestJ, score[n][bestJ], n)
    }

    private fun pairScore(s: Double) = when {
        s >= 0.8 -> MATCH
        s >= 0.6 -> NEAR
        else -> SUB
    }

    private const val MOVE_EXTRA = 1
    private const val MOVE_MISSING = 2
    private const val MOVE_PAIR = 3
    private const val MOVE_MERGE = 4
    private const val MOVE_SPLIT = 5
}
