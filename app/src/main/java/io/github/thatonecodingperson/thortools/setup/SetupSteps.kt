package io.github.thatonecodingperson.thortools.setup

/** The setup wizard's steps, in order. */
enum class WizardStep {
    WELCOME,
    ACCESS,
    HOTKEYS,
    PANEL,
    THEME,
    DONE,
    ;

    val number: Int get() = ordinal + 1

    fun next(): WizardStep? = entries.getOrNull(ordinal + 1)

    fun previous(): WizardStep? = entries.getOrNull(ordinal - 1)
}

/** When the wizard opens by itself. Pure. */
object FirstRun {
    /**
     * Only on a fresh install that never finished or skipped it: an app that was updated ([lastUpdateTime] after
     * [firstInstallTime]) was set up by hand before the wizard existed.
     */
    fun showWizard(setupDone: Boolean, firstInstallTime: Long, lastUpdateTime: Long): Boolean =
        !setupDone && firstInstallTime == lastUpdateTime
}
