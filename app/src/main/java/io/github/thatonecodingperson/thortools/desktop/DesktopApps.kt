package io.github.thatonecodingperson.thortools.desktop

/**
 * Where desktop controls work: only in [onlyIn] apps when that list has any, never in [neverIn] apps. An app is on at
 * most one of the lists: adding it to one while it is on the other is refused, so nothing moves without the user.
 */
data class DesktopApps(val onlyIn: Set<String> = emptySet(), val neverIn: Set<String> = emptySet()) {
    enum class Side { ONLY_IN, NEVER_IN }

    sealed class Change {
        data class Done(val apps: DesktopApps) : Change()

        /** The app is on the other list; it has to be taken off there first. */
        data class OnOtherList(val other: Side) : Change()
    }

    fun list(side: Side): Set<String> = if (side == Side.ONLY_IN) onlyIn else neverIn

    /** [packageName] put on [side]'s list, or taken off it when it is there already. */
    fun toggle(side: Side, packageName: String): Change {
        val own = list(side)
        if (packageName in own) return Change.Done(with(side, own - packageName))
        val other = if (side == Side.ONLY_IN) Side.NEVER_IN else Side.ONLY_IN
        if (packageName in list(other)) return Change.OnOtherList(other)
        return Change.Done(with(side, own + packageName))
    }

    /**
     * Whether desktop controls work while [app] is on the screen that has the controller; null is no app (a home
     * screen). A never-in app stops them; with only-in apps they work only there.
     */
    fun worksFor(app: String?): Boolean = when {
        app != null && app in neverIn -> false
        onlyIn.isNotEmpty() -> app != null && app in onlyIn
        else -> true
    }

    private fun with(side: Side, apps: Set<String>) = if (side == Side.ONLY_IN) copy(onlyIn = apps) else copy(neverIn = apps)
}
