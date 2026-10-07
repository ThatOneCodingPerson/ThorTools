package io.github.thatonecodingperson.thortools.retroarch

import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction

/** One cheat of a `.cht` file: its number, what it does, its code and whether it is on. */
data class Cheat(val index: Int, val description: String, val code: String, val enabled: Boolean)

/**
 * A RetroArch cheat file: `cheats = N`, then `cheatN_desc`, `cheatN_code`, `cheatN_enable` and maybe more keys per cheat.
 * The file is read byte for byte (as ISO-8859-1, so no byte is lost whatever its encoding), and [withEnabled] changes only
 * the `cheatN_enable` lines, so every other line stays exactly as it was.
 */
class ChtFile private constructor(private val text: String) {
    private val lines: List<String> = text.split('\n')

    private val count: Int? = lines.firstNotNullOfOrNull { line ->
        COUNT.matchEntire(line.trimEnd('\r'))?.groupValues?.get(1)?.toIntOrNull()
    }

    private val fields: Map<Int, Map<String, String>> = buildMap<Int, MutableMap<String, String>> {
        lines.forEach { line ->
            val match = FIELD.matchEntire(line.trimEnd('\r')) ?: return@forEach
            getOrPut(match.groupValues[1].toInt()) { mutableMapOf() }[match.groupValues[2]] = unquote(match.groupValues[3])
        }
    }

    /** Every cheat, in order; a file without its count line has as many as its highest number says. */
    val cheats: List<Cheat> = run {
        val total = count ?: ((fields.keys.maxOrNull() ?: -1) + 1)
        (0 until total.coerceAtMost(MAX_CHEATS)).map { index ->
            val field = fields[index].orEmpty()
            Cheat(
                index = index,
                description = readable(field["desc"].orEmpty()),
                code = field["code"].orEmpty(),
                enabled = field["enable"]?.lowercase() in ON,
            )
        }
    }

    val enabledCount: Int get() = cheats.count { it.enabled }

    /** The file as bytes, the same bytes it was read from. */
    val bytes: ByteArray get() = text.toByteArray(Charsets.ISO_8859_1)

    /**
     * The same file with the cheats numbered in [on] switched on and every other one off: each `cheatN_enable` line gets
     * the new value, and a cheat switched on without such a line gets one after its last line.
     */
    fun withEnabled(on: Set<Int>): ChtFile {
        val withLine = mutableSetOf<Int>()
        val lastLine = mutableMapOf<Int, Int>()
        lines.forEachIndexed { at, line ->
            FIELD.matchEntire(line.trimEnd('\r'))?.let { match ->
                val index = match.groupValues[1].toInt()
                lastLine[index] = at
                if (match.groupValues[2] == "enable") withLine += index
            }
        }
        val crlf = lines.firstOrNull()?.endsWith('\r') == true
        val added = on.filter { it !in withLine && it in lastLine }.associateBy { lastLine.getValue(it) }
        val rewritten = buildList {
            lines.forEachIndexed { at, line ->
                val match = FIELD.matchEntire(line.trimEnd('\r'))
                if (match != null && match.groupValues[2] == "enable") {
                    val index = match.groupValues[1].toInt()
                    add(enableLine(index, index in on) + if (line.endsWith('\r')) "\r" else "")
                } else {
                    add(line)
                }
                added[at]?.let { index -> add(enableLine(index, true) + if (crlf) "\r" else "") }
            }
        }
        return ChtFile(rewritten.joinToString("\n"))
    }

    companion object {
        /** Beyond any real file; keeps a broken count line from making a huge list. */
        private const val MAX_CHEATS = 20000
        private val COUNT = Regex("""^\s*cheats\s*=\s*"?(\d+)"?\s*$""")
        private val FIELD = Regex("""^\s*cheat(\d+)_([a-z_]+)\s*=\s*(.*?)\s*$""")
        private val ON = setOf("true", "1")

        fun of(bytes: ByteArray): ChtFile = ChtFile(String(bytes, Charsets.ISO_8859_1))

        private fun enableLine(index: Int, on: Boolean) = "cheat${index}_enable = $on"

        private fun unquote(value: String) =
            if (value.length >= 2 && value.startsWith('"') && value.endsWith('"')) value.substring(1, value.length - 1) else value

        /** A value read as ISO-8859-1, shown as UTF-8 when its bytes are UTF-8 (most files' are). */
        private fun readable(value: String): String {
            val raw = value.toByteArray(Charsets.ISO_8859_1)
            return runCatching {
                Charsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(raw))
                    .toString()
            }.getOrDefault(value)
        }
    }
}
