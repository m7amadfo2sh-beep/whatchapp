package com.whatchapp.hourlybuzz.prayer

/**
 * Counts rak'ahs from a stream of postures, following the fixed order of the
 * prayer: قيام → ركوع → (اعتدال) → سجود / جلسة / سجود → قيام (next rak'ah) or تشهد.
 *
 * - A posture counts only once it is held steadily ([holdMs], or the longer
 *   [holdNoVoiceMs] when no takbir was heard around the change).
 * - The rak'ah number goes up when the imam stands (or bows) again after
 *   sujood, so sujood vs jalsa never needs to be told apart.
 * - Short blips that break the order are ignored.
 */
class RakahCounter(
    val total: Int,
    private val holdMs: Long = 800,
    private val holdNoVoiceMs: Long = 1600,
    private val voiceWindowMs: Long = 2000,
) {
    enum class Phase { BEFORE_RUKU, AFTER_RUKU, SUJOOD }

    /** The rak'ah the imam is in now (1-based). */
    var rakah = 1
        private set
    var phase = Phase.BEFORE_RUKU
        private set

    /** The last steadily held posture, and since when. */
    var stable: Posture? = null
        private set
    var stableSince = 0L
        private set

    private var candidate: Posture? = null
    private var candidateSince = 0L
    private val voices = ArrayDeque<Long>()

    /** Rak'ahs still to come after the current one. */
    fun remainingAfterCurrent(): Int = (total - rakah).coerceAtLeast(0)

    fun onVoice(t: Long) {
        voices.addLast(t)
        while (voices.size > 20) voices.removeFirst()
    }

    /** Feed one classified sample. Returns true when the steady posture changed. */
    fun onPosture(p: Posture, t: Long): Boolean {
        if (p == Posture.MOVING || p == Posture.UNKNOWN) {
            candidate = null
            return false
        }
        if (p == stable) {
            candidate = null
            return false
        }
        if (p != candidate) {
            candidate = p
            candidateSince = t
            return false
        }
        val heard = voices.any { it >= candidateSince - voiceWindowMs && it <= t }
        if (t - candidateSince < if (heard) holdMs else holdNoVoiceMs) return false
        apply(p, candidateSince)
        return true
    }

    private fun apply(p: Posture, t: Long) {
        stable = p
        stableSince = t
        candidate = null
        when (p) {
            Posture.RUKU -> when (phase) {
                Phase.BEFORE_RUKU -> phase = Phase.AFTER_RUKU
                // Bowing straight after sujood: the standing was missed; new rak'ah.
                Phase.SUJOOD -> {
                    rakah++
                    phase = Phase.AFTER_RUKU
                }
                Phase.AFTER_RUKU -> Unit
            }
            Posture.LOW -> phase = Phase.SUJOOD
            Posture.QIYAM -> if (phase == Phase.SUJOOD) {
                rakah++
                phase = Phase.BEFORE_RUKU
            }
            else -> Unit
        }
    }

    /** Manual correction (bezel). */
    fun adjust(delta: Int) {
        rakah = (rakah + delta).coerceIn(1, maxOf(total, 1))
    }

    /** How long the imam has been in sujood/sitting, or 0. */
    fun lowFor(now: Long): Long = if (stable == Posture.LOW) now - stableSince else 0

    /** True once the prayer looks finished (last rak'ah done and sitting long, or stood up after it). */
    fun finished(now: Long, sitMs: Long): Boolean =
        rakah > total || (rakah == total && phase == Phase.SUJOOD && lowFor(now) >= sitMs)
}
