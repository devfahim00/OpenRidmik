package com.devfahim00.openridmik

/**
 * Rule-driven phonetic (Avro-style) Latin -> Bangla transliterator.
 * Rules are loaded from assets/avro_rules.txt so they can be edited without touching code.
 */
class AvroEngine(rulesText: String) {

    private enum class T { V, C, W, J, S }

    private class Rule(val type: T, val value: String, val kar: String)

    private val rules = HashMap<String, Rule>()
    private var maxLen = 1

    /** One row of the rules file, kept in file order (used by the key-map screen). */
    class Entry(val key: String, val kind: Char, val value: String, val kar: String)

    val entries = ArrayList<Entry>()

    init {
        for (line in rulesText.lines()) {
            val l = line.trim()
            if (l.isEmpty() || l.startsWith("#")) continue
            val p = l.split(Regex("\\s+"))
            if (p.size < 3) continue
            val type = when (p[0]) {
                "V" -> T.V
                "C" -> T.C
                "W" -> T.W
                "J" -> T.J
                else -> T.S
            }
            val value = if (p[2] == "-") "" else p[2]
            val kar = if (p.size > 3 && p[3] != "-") p[3] else ""
            rules[p[1]] = Rule(type, value, kar)
            entries.add(Entry(p[1], p[0][0], value, kar))
            if (p[1].length > maxLen) maxLen = p[1].length
        }
    }

    fun convert(input: String): String {
        val out = StringBuilder()
        var prevCons = false
        var i = 0
        while (i < input.length) {
            var rule: Rule? = null
            var len = 0
            var l = minOf(maxLen, input.length - i)
            while (l > 0) {
                val r = rules[input.substring(i, i + l)]
                if (r != null) {
                    rule = r
                    len = l
                    break
                }
                l--
            }
            if (rule == null && input[i].isUpperCase()) {
                // no dedicated capital rule (e.g. "Ami", "Kemon"): fall back to the lowercase letter's rule
                val lc = input[i].lowercaseChar()
                var l2 = minOf(maxLen, input.length - i)
                while (l2 > 0) {
                    val r = rules[lc.toString() + input.substring(i + 1, i + l2)]
                    if (r != null) {
                        rule = r
                        len = l2
                        break
                    }
                    l2--
                }
            }
            if (rule == null) {
                out.append(input[i])
                prevCons = false
                i++
                continue
            }
            when (rule.type) {
                T.V -> {
                    out.append(if (prevCons) rule.kar else rule.value)
                    prevCons = false
                }
                T.C -> {
                    if (prevCons) out.append(HASANTA)
                    out.append(rule.value)
                    prevCons = true
                }
                T.W -> {
                    if (prevCons) {
                        out.append(HASANTA).append(rule.kar)
                        prevCons = true
                    } else {
                        out.append(rule.value)
                        prevCons = false
                    }
                }
                T.J -> {
                    if (prevCons) out.append(HASANTA).append(rule.kar) else out.append(rule.value)
                    prevCons = true
                }
                T.S -> {
                    out.append(rule.value)
                    prevCons = false
                }
            }
            i += len
        }
        return out.toString()
    }

    companion object {
        const val HASANTA = '্'

        private val DIGITS = "০১২৩৪৫৬৭৮৯"

        fun banglaDigit(d: Char): String =
            if (d in '0'..'9') DIGITS[d - '0'].toString() else d.toString()
    }
}

class Suggester(text: String) {
    private val words = text.lines().map { it.trim() }.filter { it.isNotEmpty() }

    fun suggest(prefix: String, max: Int): List<String> {
        if (prefix.isEmpty()) return emptyList()
        return words.asSequence()
            .filter { it.startsWith(prefix) && it != prefix }
            .take(max)
            .toList()
    }
}
