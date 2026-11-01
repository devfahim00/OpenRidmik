package com.devfahim00.openridmik

object Layouts {

    private val lowerHint = mapOf(
        'a' to "আ", 'b' to "ব", 'c' to "চ", 'd' to "দ", 'e' to "এ", 'f' to "ফ",
        'g' to "গ", 'h' to "হ", 'i' to "ই", 'j' to "জ", 'k' to "ক", 'l' to "ল",
        'm' to "ম", 'n' to "ন", 'o' to "অ", 'p' to "প", 'r' to "র", 's' to "স",
        't' to "ত", 'u' to "উ", 'v' to "ভ", 'w' to "ও", 'y' to "য়", 'z' to "য",
    )

    private val upperHint = mapOf(
        'T' to "ট", 'D' to "ড", 'N' to "ণ", 'S' to "শ", 'R' to "ড়",
        'I' to "ঈ", 'U' to "ঊ", 'O' to "ও",
    )

    private fun special(label: String, code: Int, weight: Float = 1f, repeat: Boolean = false) =
        Key(label, code, label, weight, special = true, repeat = repeat)

    private fun spacer(weight: Float) = Key("", 0, weight = weight, spacer = true)

    private fun plain(c: Char) = Key(c.toString(), c.code, c.toString())

    private fun letters(chars: String, shift: Boolean, bangla: Boolean): List<Key> = chars.map { raw ->
        val c = if (shift) raw.uppercaseChar() else raw
        val hint = if (bangla) (if (shift) upperHint[c] else lowerHint[c]) else null
        Key(c.toString(), c.code, c.toString(), hint = hint, alt = hint)
    }

    private fun digitRow(bangla: Boolean): List<Key> = "1234567890".map { d ->
        val t = if (bangla) AvroEngine.banglaDigit(d) else d.toString()
        Key(t, d.code, t)
    }

    private fun bottom(alpha: Boolean, bangla: Boolean): List<Key> {
        val first = if (alpha) special("?123", Codes.SYMBOLS, 1.5f) else special("ABC", Codes.ALPHA, 1.5f)
        val second = if (alpha) special("", Codes.LANG) else special("", Codes.EMOJI)
        val comma = Key(",", ','.code, ",", hint = if (alpha) "☺" else null)
        val period = if (bangla && alpha) {
            Key("।", '.'.code, "।", alt = ".", hint = ".")
        } else {
            Key(".", '.'.code, ".")
        }
        return listOf(
            first,
            second,
            comma,
            Key("", Codes.SPACE, " ", weight = 4f),
            period,
            Key("", Codes.ENTER, "\n", weight = 1.5f, special = true),
        )
    }

    fun alpha(shift: Boolean, bangla: Boolean, numberRow: Boolean, banglaDigits: Boolean): List<List<Key>> {
        val rows = ArrayList<List<Key>>()
        if (numberRow) rows.add(digitRow(bangla && banglaDigits))
        rows.add(letters("qwertyuiop", shift, bangla))
        rows.add(listOf(spacer(0.5f)) + letters("asdfghjkl", shift, bangla) + listOf(spacer(0.5f)))
        rows.add(
            listOf(special("", Codes.SHIFT, 1.5f)) +
                letters("zxcvbnm", shift, bangla) +
                listOf(special("", Codes.DELETE, 1.5f, repeat = true)),
        )
        rows.add(bottom(true, bangla))
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
