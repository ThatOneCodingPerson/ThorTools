package io.github.thatonecodingperson.thortools.actions

/**
 * Finds the launcher activity Android lists on one display in `dumpsys activity activities`, for a screen whose home
 * hasn't been seen since the service started. Displays are listed as "Display #<id>", activities top to bottom.
 */
object LauncherLookup {
    private val COMPONENT = Regex("""u0 ([A-Za-z0-9_.]+)/([A-Za-z0-9_.$]+)""")

    /** Package and full class name of the top-most launcher activity on [displayId], or null. */
    fun onDisplay(dump: String, displayId: Int, launcherPackages: Set<String>): Pair<String, String>? {
        var inside = false
        for (line in dump.lineSequence()) {
            val text = line.trim()
            if (text.startsWith("Display #")) {
                inside = text.removePrefix("Display #").takeWhile { it.isDigit() } == displayId.toString()
                continue
            }
            if (!inside) continue
            for (match in COMPONENT.findAll(text)) {
                val (packageName, className) = match.destructured
                if (packageName in launcherPackages) {
                    return packageName to if (className.startsWith(".")) packageName + className else className
                }
            }
        }
        return null
    }
}
