package io.github.thatonecodingperson.thortools.tools

/**
 * A system setting holding a separator-joined list that other apps write too, such as
 * `enabled_accessibility_services` (`pkg/class` entries) or `app_whiteList` (packages).
 * We only ever add or remove our own entry and leave every other entry exactly as it was.
 */
object SharedList {

    fun containsPackage(list: String?, separator: Char, packageName: String): Boolean =
        entries(list, separator).any { packageOf(it) == packageName }

    /** Prepends [entry] unless an entry for the same package is already there. */
    fun withEntry(list: String?, separator: Char, entry: String): String = when {
        containsPackage(list, separator, packageOf(entry)) -> list.orEmpty()
        entries(list, separator).isEmpty() -> entry
        else -> "$entry$separator$list"
    }

    fun withoutPackage(list: String?, separator: Char, packageName: String): String =
        entries(list, separator).filterNot { packageOf(it) == packageName }.joinToString(separator.toString())

    private fun entries(list: String?, separator: Char): List<String> =
        list.orEmpty().split(separator).map(String::trim).filter(String::isNotEmpty)

    private fun packageOf(entry: String) = entry.substringBefore('/')
}
