package com.devfahim00.openridmik

import android.content.Context
import org.json.JSONObject

/**
 * On-device learning of the user's own typing: word frequencies and word-pair (bigram) counts.
 * Used to rank prefix suggestions and to predict the next word. Never leaves the device.
 */
object UserDict {
    private const val FILE = "openridmik_userdict"
    private const val MAX_WORDS = 4000
    private const val MAX_PAIRS = 8000
    private const val SAVE_EVERY = 8

    private val words = HashMap<String, Int>()
    private val pairs = HashMap<String, Int>() // "prev\u0001next" -> count
    private var loaded = false
    private var dirty = 0

    /** Latin words are stored lower-case so "The" and "the" count together. */
    fun norm(w: String): String = if (w.isNotEmpty() && (w[0] in 'a'..'z' || w[0] in 'A'..'Z')) w.lowercase() else w

    private fun sp(c: Context) = c.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    @Synchronized
    private fun ensure(c: Context) {
        if (loaded) return
        loaded = true
        try {
            val o = JSONObject(sp(c).getString("data", "{}") ?: "{}")
            o.optJSONObject("w")?.let { j -> j.keys().forEach { words[it] = j.getInt(it) } }
            o.optJSONObject("p")?.let { j -> j.keys().forEach { pairs[it] = j.getInt(it) } }
        } catch (e: Exception) {
            words.clear()
            pairs.clear()
        }
    }

    @Synchronized
    fun save(c: Context) {
        if (!loaded || dirty == 0) return
        dirty = 0
        val o = JSONObject()
        o.put("w", JSONObject(words as Map<*, *>))
        o.put("p", JSONObject(pairs as Map<*, *>))
        sp(c).edit().putString("data", o.toString()).apply()
    }

    private fun prune(map: HashMap<String, Int>, max: Int) {
        if (map.size <= max) return
        val keep = map.entries.sortedByDescending { it.value }.take(max * 3 / 4)
        val m = keep.associate { it.key to it.value }
        map.clear()
        map.putAll(m)
    }

    @Synchronized
    fun learn(c: Context, prev0: String?, word0: String) {
        ensure(c)
        val word = norm(word0)
        val prev = prev0?.let { norm(it) }
        if (word.length < 2) return
        words[word] = (words[word] ?: 0) + 1
        if (!prev.isNullOrEmpty()) {
            val k = "$prev\u0001$word"
            pairs[k] = (pairs[k] ?: 0) + 1
        }
        prune(words, MAX_WORDS)
        prune(pairs, MAX_PAIRS)
        if (++dirty >= SAVE_EVERY) save(c)
    }

    /** Words the user has typed that start with [prefix], most relevant first (prev-word pairs boosted). */
    @Synchronized
    fun suggest(c: Context, prefix0: String, prev0: String?, max: Int): List<String> {
        ensure(c)
        if (prefix0.isEmpty()) return emptyList()
        val prefix = norm(prefix0)
        val prev = prev0?.let { norm(it) }
        return words.entries.asSequence()
            .filter { it.key.startsWith(prefix) && it.key != prefix }
            .sortedByDescending { it.value + 5 * (if (prev.isNullOrEmpty()) 0 else pairs["$prev\u0001${it.key}"] ?: 0) }
            .take(max)
            .map { it.key }
            .toList()
    }

    /** Most likely words to follow [prev]. */
    @Synchronized
    fun next(c: Context, prev0: String, max: Int): List<String> {
        ensure(c)
        if (prev0.isEmpty()) return emptyList()
        val prev = norm(prev0)
        val pre = "$prev\u0001"
        return pairs.entries.asSequence()
            .filter { it.key.startsWith(pre) }
            .sortedByDescending { it.value }
            .take(max)
            .map { it.key.substring(pre.length) }
            .toList()
    }
}
