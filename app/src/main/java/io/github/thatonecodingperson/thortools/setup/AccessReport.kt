package io.github.thatonecodingperson.thortools.setup

/** Everything Thor Tools needs to be allowed, in the order the Permissions & access screen lists it. */
enum class AccessCheck {
    ROOT,
    ACCESSIBILITY,
    NOTIFICATIONS,
    BATTERY,
    BACKGROUND_LIST,
    INPUT_HELPER,
}

enum class AccessState { OK, MISSING, NOT_NEEDED, UNKNOWN }

data class AccessReport(val states: Map<AccessCheck, AccessState>) {
    fun state(check: AccessCheck): AccessState = states[check] ?: AccessState.UNKNOWN

    /** Something the user can fix is missing. Unknown states (not checked yet) don't count. */
    val needsAttention: Boolean get() = states.values.any { it == AccessState.MISSING }

    companion object {
        fun of(vararg states: Pair<AccessCheck, AccessState>) = AccessReport(mapOf(*states))

        fun ok(present: Boolean) = if (present) AccessState.OK else AccessState.MISSING

        /** A requirement that only applies when [needed]. */
        fun okIfNeeded(needed: Boolean, present: Boolean) = if (needed) ok(present) else AccessState.NOT_NEEDED
    }
}
