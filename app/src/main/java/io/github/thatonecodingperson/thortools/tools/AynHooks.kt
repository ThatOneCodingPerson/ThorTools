package io.github.thatonecodingperson.thortools.tools

/** AYN firmware features reached through PServer. */
object AynHooks {
    /** What AYN's framework sends for a short press of the AYN button. A protected broadcast, so it needs root. */
    private const val OPEN_DRAWER =
        "am broadcast -a action.tcc.button.key.event -p com.odin.dualscreen.assistant " +
            "--ez key_action_down false --ez key_isLongPress false"

    fun openDrawer(executor: ShellExecutor): Boolean = executor.executeAsRoot(OPEN_DRAWER).isSuccess
}
