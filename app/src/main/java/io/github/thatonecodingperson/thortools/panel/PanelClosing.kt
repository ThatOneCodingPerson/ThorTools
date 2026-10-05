package io.github.thatonecodingperson.thortools.panel

/** Why the quick panel would close. */
enum class CloseReason {
    /** A single tap of the AYN button (the quick panel action). */
    AYN,

    /** The panel's own close button. */
    CLOSE_BUTTON,

    /** B, Back or Escape with nothing to go back to inside the panel. */
    BACK,

    /** A touch beside the side sheet. */
    TOUCH_OUTSIDE,

    /** A tile whose action leaves the panel (Home, Recents, controller moves...). */
    TILE,

    /** A hotkey whose action leaves the panel. */
    HOTKEY,

    /** The pad's Home, or a Home action. */
    HOME,

    /** Another app came to the front. */
    OTHER_APP,

    /** Thor Tools opens over it (its tile, or the edit button). */
    OPEN_THOR_TOOLS,

    SCREEN_OFF,

    /** The accessibility service stops. */
    SERVICE_STOPPED,
}

/**
 * Whether the quick panel closes for a [CloseReason]. With "Only the AYN button closes the panel" (the default) it stays
 * open until AYN is tapped or its close button is pressed; opening Thor Tools, the screen going off and the service
 * stopping close it regardless. Off: every reason closes it, as before that setting. Pure.
 */
object PanelClosing {
    private val ALWAYS = setOf(
        CloseReason.AYN,
        CloseReason.CLOSE_BUTTON,
        CloseReason.OPEN_THOR_TOOLS,
        CloseReason.SCREEN_OFF,
        CloseReason.SERVICE_STOPPED,
    )

    fun closes(reason: CloseReason, onlyAynCloses: Boolean): Boolean = !onlyAynCloses || reason in ALWAYS
}
