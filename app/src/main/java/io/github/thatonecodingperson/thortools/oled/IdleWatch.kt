package io.github.thatonecodingperson.thortools.oled

import io.github.thatonecodingperson.thortools.input.Screen

/**
 * When each screen last had input, and which screens sit idle. A touch counts for the screen it was on; a button for the
 * screen with the controller, or for both while that isn't known. Times are milliseconds of one clock. Pure.
 */
class IdleWatch(start: Long) {
    private val lastInput = mutableMapOf(Screen.TOP to start, Screen.BOTTOM to start)

    fun touched(screen: Screen, time: Long) {
        lastInput[screen] = time
    }

    fun pressed(controllerOn: Screen?, time: Long) {
        (controllerOn?.let(::listOf) ?: Screen.entries).forEach { lastInput[it] = time }
    }

    /** Both screens start over (the screens came on). */
    fun restart(time: Long) {
        Screen.entries.forEach { lastInput[it] = time }
    }

    /**
     * The screens to protect at [now]: chosen, and without input for the chosen minutes. While media plays and
     * [OledChoices.notWhileMedia] is on, the screen with the controller (both while that isn't known) is left alone.
     */
    fun idle(choices: OledChoices, now: Long, controllerOn: Screen?, mediaPlaying: Boolean): Set<Screen> {
        if (!choices.idle) return emptySet()
        val after = choices.idleMinutes * MINUTE_MS
        return choices.idleScreens.screens.filter { screen ->
            val watching = choices.notWhileMedia && mediaPlaying && (controllerOn == null || controllerOn == screen)
            now - lastInput.getValue(screen) >= after && !watching
        }.toSet()
    }

    companion object {
        const val MINUTE_MS = 60_000L
    }
}
