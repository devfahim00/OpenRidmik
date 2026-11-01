package com.devfahim00.openridmik

import android.content.Context
import android.graphics.Paint

/** Emoji catalogue (assets/emoji.txt, grouped like Unicode's emoji-test) filtered to what this device can draw. */
object EmojiData {

    class Emoji(val text: String, val skin: Boolean)
    class Group(val name: String, val icon: String, val items: List<Emoji>)

    val TONES = listOf("\uD83C\uDFFB", "\uD83C\uDFFC", "\uD83C\uDFFD", "\uD83C\uDFFE", "\uD83C\uDFFF")

    private val ICONS = mapOf(
        "Smileys & Emotion" to "😀",
        "People & Body" to "👋",
        "Animals & Nature" to "🐻",
        "Food & Drink" to "🍔",
        "Travel & Places" to "🚗",
        "Activities" to "⚽",
        "Objects" to "💡",
        "Symbols" to "❤",
        "Flags" to "🏳",
    )

    private val paint = Paint()
    private var cache: List<Group>? = null

    fun supported(e: String): Boolean = try {
        paint.hasGlyph(e)
    } catch (ex: Exception) {
        true
    }

    @Synchronized
    fun groups(c: Context): List<Group> {
        cache?.let { return it }
        val result = ArrayList<Group>()
        var name: String? = null
        var items = ArrayList<Emoji>()
        fun flush() {
            val n = name ?: return
            if (items.isNotEmpty()) result.add(Group(n, ICONS[n] ?: items[0].text, items))
        }
        try {
            c.assets.open("emoji.txt").bufferedReader().useLines { lines ->
                for (line in lines) {
                    if (line.isBlank()) continue
                    if (line.startsWith("#")) {
                        flush()
                        name = line.substring(1).trim()
                        items = ArrayList()
                    } else {
                        val parts = line.split('\t')
                        val e = parts[0]
                        if (supported(e)) items.add(Emoji(e, parts.size > 1 && parts[1] == "1"))
                    }
                }
            }
            flush()
        } catch (ex: Exception) {
            // asset missing: fall back to an empty catalogue
        }
        cache = result
        return result
    }

    /** Inserts a skin-tone modifier after the first code point (dropping a variation selector if present). */
    fun withTone(e: String, tone: String): String {
        val first = e.offsetByCodePoints(0, 1)
        var rest = e.substring(first)
        if (rest.startsWith("\uFE0F")) rest = rest.substring(1)
        return e.substring(0, first) + tone + rest
    }

    // ---- recents ----

    private const val RECENT_KEY = "emoji_recent"
    private const val RECENT_MAX = 32

    fun recents(c: Context): List<String> =
        Prefs.getString(c, RECENT_KEY).split('\n').filter { it.isNotEmpty() }

    fun addRecent(c: Context, e: String) {
        val list = ArrayList(recents(c))
        list.remove(e)
        list.add(0, e)
        Prefs.setString(c, RECENT_KEY, list.take(RECENT_MAX).joinToString("\n"))
    }
}
