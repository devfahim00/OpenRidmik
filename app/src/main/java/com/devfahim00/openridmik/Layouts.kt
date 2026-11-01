package com.devfahim00.openridmik

object Layouts {

    private val lowerHint = mapOf(
        'a' to "আ", 'b' to "ব", 'd' to "দ", 'e' to "এ", 'f' to "ফ",
        'g' to "গ", 'h' to "হ", 'i' to "ই", 'j' to "জ", 'k' to "ক", 'l' to "ল",
        'm' to "ম", 'n' to "ন", 'o' to "অ", 'p' to "প", 'r' to "র", 's' to "স",
        't' to "ত", 'u' to "উ", 'w' to "ও", 'y' to "য়", 'z' to "য",
    )

    private val upperHint = mapOf(
        'T' to "ট", 'D' to "ড", 'N' to "ণ", 'S' to "শ", 'R' to "ড়",
        'I' to "ঈ", 'U' to "ঊ", 'O' to "ও",
    )

    /** Press-and-hold on these physical letter positions: x = cut, c = copy, v = paste. */
    private val clipActions = mapOf('x' to Codes.CUT, 'c' to Codes.COPY, 'v' to Codes.PASTE)

    private fun clipLabel(action: Int) = when (action) {
        Codes.CUT -> "cut"
        Codes.COPY -> "copy"
        else -> "paste"
    }

    private fun special(label: String, code: Int, weight: Float = 1f, repeat: Boolean = false) =
        Key(label, code, label, weight, special = true, repeat = repeat)

    private fun spacer(weight: Float) = Key("", 0, weight = weight, spacer = true)

    /** Keys that Avro remaps ($ : ^) type the plain character on long-press. */
    private fun plain(c: Char) =
        if (c == '$' || c == ':' || c == '^') Key(c.toString(), c.code, c.toString(), alt = c.toString())
        else Key(c.toString(), c.code, c.toString())

    private fun letters(chars: String, shift: Boolean, bangla: Boolean): List<Key> = chars.map { raw ->
        val c = if (shift) raw.uppercaseChar() else raw
        val act = clipActions[raw] ?: 0
        if (act != 0) {
            Key(c.toString(), c.code, c.toString(), hint = clipLabel(act), action = act)
        } else {
            val hint = if (bangla) (if (shift) upperHint[c] else lowerHint[c]) else null
            Key(c.toString(), c.code, c.toString(), hint = hint, alt = hint)
        }
    }

    // ---- Probhat (fixed Bangla layout) ----

    /** [sa] = extra long-press character available on the shifted key (e.g. ঐ -> ঞ). */
    private class PK(val latin: Char, val n: String, val s: String, val sa: String? = null)

    private val probhatRow1 = listOf(
        PK('q', "দ", "ধ"), PK('w', "ূ", "ঊ"), PK('e', "ী", "ঈ"), PK('r', "র", "\u09DC"), PK('t', "ট", "ঠ"),
        PK('y', "এ", "ঐ", "ঞ"), PK('u', "ু", "উ", "ৎ"), PK('i', "ি", "ই"), PK('o', "ও", "ঔ"), PK('p', "প", "ফ"),
        PK('[', "ে", "ৈ"), PK(']', "ো", "ৌ"),
    )
    private val probhatRow2 = listOf(
        PK('a', "া", "অ"), PK('s', "স", "ষ"), PK('d', "ড", "ঢ"), PK('f', "ত", "থ"), PK('g', "গ", "ঘ"),
        PK('h', "হ", "ঃ"), PK('j', "জ", "ঝ"), PK('k', "ক", "খ"), PK('l', "ল", "ং"),
    )
    private val probhatRow3 = listOf(
        PK('z', "\u09DF", "য", "\u09CD\u09AF"), PK('x', "শ", "\u09DD"), PK('c', "চ", "ছ"), PK('v', "আ", "ঋ"),
        PK('b', "ব", "ভ"), PK('n', "ন", "ণ"), PK('m', "ম", "ঙ"),
        PK(',', "ৃ", "ৃ"), PK('/', "্", "ঁ"),
    )

    private const val SIGNS = "\u09BE\u09BF\u09C0\u09C1\u09C2\u09C3\u09C7\u09C8\u09CB\u09CC\u0981\u09CD"

    /** Dependent signs are shown on a dotted circle so they are visible on a key. */
    private fun disp(s: String): String = if (s.isNotEmpty() && SIGNS.indexOf(s[0]) >= 0) "\u25CC$s" else s

    private fun probhatKey(p: PK, shift: Boolean): Key {
        val v = if (shift) p.s else p.n
        val act = clipActions[p.latin] ?: 0
        return if (act != 0) {
            Key(disp(v), v.codePointAt(0), v, hint = clipLabel(act), action = act)
        } else {
            val other = if (shift) p.sa else if (p.s != p.n) p.s else null
            Key(disp(v), v.codePointAt(0), v, hint = other?.let { disp(it) }, alt = other)
        }
    }

    private fun digitRow(bangla: Boolean): List<Key> = "1234567890".map { d ->
        val t = if (bangla) AvroEngine.banglaDigit(d) else d.toString()
        Key(t, d.code, t, hint = if (bangla) d.toString() else null, alt = if (bangla) d.toString() else null)
    }

    private fun bottom(alpha: Boolean, bangla: Boolean): List<Key> {
        val first = if (alpha) special("?123", Codes.SYMBOLS, 1.4f) else special("ABC", Codes.ALPHA, 1.5f)
        val comma = Key(",", ','.code, ",", weight = if (alpha) 0.9f else 1f)
        val period = if (bangla && alpha) {
            Key("।", '.'.code, "।", alt = ".", hint = ".")
        } else {
            Key(".", '.'.code, ".")
        }
        val enter = Key("", Codes.ENTER, "\n", weight = 1.5f, special = true)
        return if (alpha) {
            listOf(
                first,
                special("", Codes.LANG),
                comma,
                Key("", Codes.SPACE, " ", weight = 4f),
                period,
                enter,
            )
        } else {
            listOf(first, comma, Key("", Codes.SPACE, " ", weight = 5f), period, enter)
        }
    }

    fun alpha(
        shift: Boolean,
        bangla: Boolean,
        numberRow: Boolean,
        banglaDigits: Boolean,
        probhat: Boolean = false,
    ): List<List<Key>> {
        val rows = ArrayList<List<Key>>()
        if (numberRow) rows.add(digitRow(bangla && banglaDigits))
        if (bangla && probhat) {
            rows.add(probhatRow1.map { probhatKey(it, shift) })
            rows.add(listOf(spacer(1.5f)) + probhatRow2.map { probhatKey(it, shift) } + listOf(spacer(1.5f)))
            rows.add(
                listOf(special("", Codes.SHIFT, 1.4f)) +
                    probhatRow3.map { probhatKey(it, shift) } +
                    listOf(special("", Codes.DELETE, 1.6f, repeat = true)),
            )
        } else {
            rows.add(letters("qwertyuiop", shift, bangla))
            rows.add(listOf(spacer(0.5f)) + letters("asdfghjkl", shift, bangla) + listOf(spacer(0.5f)))
            rows.add(
                listOf(special("", Codes.SHIFT, 1.5f)) +
                    letters("zxcvbnm", shift, bangla) +
                    listOf(special("", Codes.DELETE, 1.5f, repeat = true)),
            )
        }
        rows.add(bottom(true, bangla))
        return rows
    }

    /** Number pad: Bangla digit row on top, operators on the left, big 0-9 block. */
    fun numpad(): List<List<Key>> {
        fun k(t: String, weight: Float = 1f) = Key(t, t[0].code, t, weight = weight, special = !t[0].isDigit())
        val rows = ArrayList<List<Key>>()
        rows.add(digitRow(true))
        rows.add(listOf(k("+"), k("1"), k("2"), k("3"), k("%")))
        rows.add(listOf(k("-"), k("4"), k("5"), k("6"), k("/")))
        rows.add(
            listOf(k("*"), k("7"), k("8"), k("9"), special("", Codes.DELETE, 1f, repeat = true)),
        )
        rows.add(
            listOf(
                special("ABC", Codes.ALPHA, 1f),
                Key(".", '.'.code, "."),
                Key("0", '0'.code, "0"),
                Key("", Codes.SPACE, " "),
                Key("", Codes.ENTER, "\n", special = true),
            ),
        )
        return rows
    }

    fun symbols(page: Int): List<List<Key>> {
        val rows = ArrayList<List<Key>>()
        rows.add("1234567890".map { plain(it) })
        if (page == 1) {
            rows.add("@#\$_&-+()/".map { plain(it) })
            rows.add(
                listOf(special("=\\<", Codes.SYM2, 1.5f)) +
                    "*\"':;!?".map { plain(it) } +
                    listOf(special("", Codes.DELETE, 1.5f, repeat = true)),
            )
        } else {
            rows.add("~`|৳√π÷×{}".map { plain(it) })
            rows.add(
                listOf(special("?123", Codes.SYM1, 1.5f)) +
                    "£¢€¥^°\\".map { plain(it) } +
                    listOf(special("", Codes.DELETE, 1.5f, repeat = true)),
            )
        }
        rows.add(bottom(false, false))
        return rows
    }
}
