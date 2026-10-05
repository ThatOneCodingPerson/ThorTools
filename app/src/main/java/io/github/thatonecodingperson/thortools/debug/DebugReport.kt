package io.github.thatonecodingperson.thortools.debug

import io.github.thatonecodingperson.thortools.actions.ThorAction

enum class CheckState { RUNNING, PASS, FAIL, SKIPPED, ASK }

/** One action check's outcome; [restored] null when nothing needed putting back. */
data class CheckResult(
    val action: ThorAction,
    val state: CheckState,
    val before: String? = null,
    val after: String? = null,
    val restored: Boolean? = null,
    val detail: String = "",
)

/**
 * The debug results file: a header, then one section per tool. Each run replaces its own section and keeps the other,
 * so there is only ever one file, with the latest of both. Pure.
 */
object DebugReport {
    const val ACTIONS = "Action check"
    const val HOTKEYS = "Hotkey check"
    private const val TITLE = "Thor Tools debug report"
    private val ORDER = listOf(ACTIONS, HOTKEYS)

    fun actionSection(results: List<CheckResult>, time: String): String = buildString {
        appendLine("time: $time")
        results.forEach { result ->
            append("${result.state.name} ${result.action.id}")
            if (result.before != null || result.after != null) append(": ${result.before ?: "?"} -> ${result.after ?: "?"}")
            result.restored?.let { append("; restored=${if (it) "yes" else "NO"}") }
            if (result.detail.isNotEmpty()) append("; ${result.detail}")
            appendLine()
        }
        val counts = CheckState.entries.associateWith { state -> results.count { it.state == state } }.filterValues { it > 0 }
        append("summary: ").append(counts.entries.joinToString(", ") { "${it.value} ${it.key.name.lowercase()}" })
    }

    fun hotkeySection(type: HotkeyType, rows: List<HotkeyRow>, events: List<HotkeyEvent>, runActions: Boolean, time: String): String =
        buildString {
            appendLine("time: $time")
            appendLine("type: ${type.name.lowercase()}; actions ${if (runActions) "ran" else "not run"}")
            rows.forEach { row ->
                val mark = when {
                    row.onlyInApps -> "APPS"
                    row.seenAt != null -> "SEEN"
                    else -> "MISS"
                }
                appendLine("$mark ${HotkeyCheck.describe(row.hotkey)} -> ${row.hotkey.action.id}")
            }
            events.forEach { appendLine("press @${it.time}: ${HotkeyCheck.describe(it)}") }
            val (seen, total) = HotkeyCheck.summary(rows)
            append("summary: $seen of $total recognised")
        }

    /** The file's new text: a fresh [header], [section] under [name], and the other tool's section from [existing]. */
    fun merge(existing: String?, header: List<String>, name: String, section: String): String {
        val sections = sections(existing).toMutableMap()
        sections[name] = section.trimEnd()
        return buildString {
            appendLine(TITLE)
            header.forEach(::appendLine)
            ORDER.forEach { key -> sections[key]?.let { appendLine().appendLine("=== $key ===").appendLine(it) } }
        }
    }

    /** The sections of a report by name. */
    fun sections(text: String?): Map<String, String> {
        val result = mutableMapOf<String, String>()
        var name: String? = null
        val body = StringBuilder()
        fun flush() {
            name?.let { result[it] = body.toString().trim() }
            body.clear()
        }
        text.orEmpty().lines().forEach { line ->
            val heading = Regex("^=== (.+) ===$").find(line)?.groupValues?.get(1)
            if (heading != null) {
                flush()
                name = heading
            } else if (name != null) {
                body.appendLine(line)
            }
        }
        flush()
        return result
    }
}
