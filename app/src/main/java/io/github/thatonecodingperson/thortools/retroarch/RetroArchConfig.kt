package io.github.thatonecodingperson.thortools.retroarch

/**
 * RetroArch's `retroarch.cfg`: one `key = "value"` per line. Reading takes a key's last line; a change rewrites every
 * line of that key in place and adds a key that is missing at the end, so every other line, its order and its spelling
 * stay exactly as RetroArch wrote them.
 */
class RetroArchConfig(val text: String) {
    private val lines: List<String> = text.lines()

    private val values: Map<String, String> = buildMap {
        lines.forEach { line -> parse(line)?.let { (key, value) -> put(key, value) } }
    }

    /** The value without its quotes; null when the key isn't there. */
    fun value(key: String): String? = values[key]

    /** [value] read as on or off; null when it is neither. */
    fun flag(key: String): Boolean? = when (value(key)) {
        "true" -> true
        "false" -> false
        else -> null
    }

    /** The same file with [changes] made; unchanged when every value is the same already. */
    fun with(changes: Map<String, String>): RetroArchConfig {
        val wanted = changes.filter { (key, value) -> values[key] != value }
        if (wanted.isEmpty()) return this
        val rewritten = lines.map { line ->
            val key = parse(line)?.first
            if (key != null && key in wanted) line(key, wanted.getValue(key)) else line
        }
        val missing = wanted.filterKeys { it !in values }.map { (key, value) -> line(key, value) }
        val body = (rewritten.dropLastWhile { it.isEmpty() } + missing).joinToString("\n")
        return RetroArchConfig("$body\n")
    }

    /** The keys whose values differ from [other]'s, for checking that a write changed only what it should. */
    fun changedFrom(other: RetroArchConfig): Set<String> =
        (values.keys + other.values.keys).filter { values[it] != other.values[it] }.toSet()

    companion object {
        /** RetroArch's word for a binding that is not set. */
        const val NONE = "nul"

        private val LINE = Regex("""^\s*([A-Za-z0-9_]+)\s*=\s*(.*?)\s*$""")

        private fun parse(line: String): Pair<String, String>? {
            if (line.trimStart().startsWith("#")) return null
            val match = LINE.matchEntire(line) ?: return null
            val raw = match.groupValues[2]
            val value = if (raw.length >= 2 && raw.startsWith('"') && raw.endsWith('"')) raw.substring(1, raw.length - 1) else raw
            return match.groupValues[1] to value
        }

        private fun line(key: String, value: String) = "$key = \"$value\""
    }
}
