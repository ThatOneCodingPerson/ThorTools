package io.github.thatonecodingperson.thortools.retroarch

/**
 * Reads JSON into plain values: maps, lists, strings, numbers (as Double), booleans and null. Enough for GitHub's API
 * answers; a pure reader so it works the same in JVM tests, where Android's own JSON classes are stubs.
 */
class TinyJson private constructor(private val text: String) {
    private var at = 0

    private fun value(): Any? {
        space()
        return when (val c = peek()) {
            '{' -> obj()
            '[' -> array()
            '"' -> string()
            't' -> word("true", true)
            'f' -> word("false", false)
            'n' -> word("null", null)
            else -> if (c == '-' || c in '0'..'9') number() else fail("unexpected '$c'")
        }
    }

    private fun obj(): Map<String, Any?> {
        at++
        val map = LinkedHashMap<String, Any?>()
        space()
        if (peek() == '}') return map.also { at++ }
        while (true) {
            space()
            val key = string()
            space()
            expect(':')
            map[key] = value()
            space()
            when (next()) {
                ',' -> continue
                '}' -> return map
                else -> fail("expected , or }")
            }
        }
    }

    private fun array(): List<Any?> {
        at++
        val list = mutableListOf<Any?>()
        space()
        if (peek() == ']') return list.also { at++ }
        while (true) {
            list += value()
            space()
            when (next()) {
                ',' -> continue
                ']' -> return list
                else -> fail("expected , or ]")
            }
        }
    }

    private fun string(): String {
        expect('"')
        val out = StringBuilder()
        while (true) {
            when (val c = next()) {
                '"' -> return out.toString()
                '\\' -> when (val escaped = next()) {
                    'n' -> out.append('\n')
                    't' -> out.append('\t')
                    'r' -> out.append('\r')
                    'b' -> out.append('\b')
                    'f' -> out.append('\u000C')
                    'u' -> {
                        if (at + HEX > text.length) fail("short \\u")
                        out.append(text.substring(at, at + HEX).toInt(16).toChar())
                        at += HEX
                    }
                    else -> out.append(escaped)
                }
                else -> out.append(c)
            }
        }
    }

    private fun number(): Double {
        val start = at
        while (at < text.length && (text[at] in "+-.eE" || text[at] in '0'..'9')) at++
        return text.substring(start, at).toDoubleOrNull() ?: fail("bad number")
    }

    private fun word(word: String, value: Any?): Any? {
        if (!text.startsWith(word, at)) fail("expected $word")
        at += word.length
        return value
    }

    private fun space() {
        while (at < text.length && text[at].isWhitespace()) at++
    }

    private fun peek(): Char = if (at < text.length) text[at] else fail("unexpected end")

    private fun next(): Char = peek().also { at++ }

    private fun expect(c: Char) {
        if (next() != c) fail("expected '$c'")
    }

    private fun fail(why: String): Nothing = throw IllegalArgumentException("JSON at $at: $why")

    companion object {
        private const val HEX = 4

        /** The value [text] holds; throws IllegalArgumentException when it isn't JSON. */
        fun parse(text: String): Any? = TinyJson(text).run {
            val value = value()
            space()
            if (at != text.length) fail("trailing text")
            value
        }
    }
}
