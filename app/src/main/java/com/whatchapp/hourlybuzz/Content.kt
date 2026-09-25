package com.whatchapp.hourlybuzz

import android.content.Context
import org.json.JSONArray

/** One dua or verse, shown full screen. */
class Card(
    val title: String,
    val text: String,
    val count: Int = 0,
    val source: String? = null,
    val isVerse: Boolean = false,
)

/** The card collections, each with its own place in the rotation. */
enum class Pool { MORNING, EVENING, GENERAL, VERSES }

/**
 * The dhikr, duas and verses from assets/content.json (built from verified
 * sources by tools/build_content.py).
 */
object Content {

    /**
     * Which collection a card slot uses: odd slots (odd hours) are Quran verses,
     * even ones are duas: morning/evening adhkar in those hours, otherwise general.
     */
    fun poolFor(slot: Long, hour: Int): Pool = when {
        slot % 2 == 1L -> Pool.VERSES
        hour in Config.MORNING_HOURS -> Pool.MORNING
        hour in Config.EVENING_HOURS -> Pool.EVENING
        else -> Pool.GENERAL
    }

    private class Data(val dhikr: List<String>, val pools: Map<Pool, List<Card>>)

    @Volatile
    private var data: Data? = null

    private fun load(context: Context): Data {
        data?.let { return it }
        val json = org.json.JSONObject(
            context.assets.open("content.json").bufferedReader().use { it.readText() }
        )
        val duas = json.getJSONObject("duas")
        fun cards(array: JSONArray, isVerse: Boolean) = List(array.length()) { i ->
            val o = array.getJSONObject(i)
            Card(
                title = o.getString("title"),
                text = o.getString("text"),
                count = o.optInt("count", 0),
                source = if (isVerse) null else o.optString("source").ifEmpty { null },
                isVerse = isVerse,
            )
        }
        val dhikr = json.getJSONArray("dhikr").let { a -> List(a.length()) { a.getString(it) } }
        return Data(
            dhikr,
            mapOf(
                Pool.MORNING to cards(duas.getJSONArray("morning"), false),
                Pool.EVENING to cards(duas.getJSONArray("evening"), false),
                Pool.GENERAL to cards(duas.getJSONArray("general"), false),
                Pool.VERSES to cards(json.getJSONArray("verses"), true),
            ),
        ).also { data = it }
    }

    fun cards(context: Context, pool: Pool): List<Card> = load(context).pools[pool].orEmpty()

    /** The next dhikr in the rotation (advances it). */
    fun nextDhikr(context: Context): String? {
        val list = load(context).dhikr
        if (list.isEmpty()) return null
        val state = State(context)
        val index = Schedule.wrapIndex(state.dhikrIndex, list.size)
        state.dhikrIndex = index + 1
        return list[index]
    }

    /** Picks the next card of [pool] (advances that pool's rotation). Returns its index. */
    fun takeNext(context: Context, pool: Pool): Int {
        val size = cards(context, pool).size
        val state = State(context)
        val index = Schedule.wrapIndex(state.poolIndex(pool), size)
        state.setPoolIndex(pool, index + 1)
        return index
    }
}
