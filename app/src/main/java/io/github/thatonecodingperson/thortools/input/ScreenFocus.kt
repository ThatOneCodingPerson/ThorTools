package io.github.thatonecodingperson.thortools.input

import android.hardware.display.DisplayManager
import android.view.Display

/** Which screen has the controller, judged by the last touched screen (reported by the root input helper). */
class ScreenFocus(private val displayManager: DisplayManager) {
    @Volatile
    var lastScreen: Screen = Screen.TOP

    /** The Thor's second built-in screen: the first display that isn't the default one and isn't private. */
    fun bottomDisplayId(): Int? = displayManager.displays
        .firstOrNull { it.displayId != Display.DEFAULT_DISPLAY && it.flags and Display.FLAG_PRIVATE == 0 }
        ?.displayId

    fun displayId(screen: Screen = lastScreen): Int =
        if (screen == Screen.BOTTOM) bottomDisplayId() ?: Display.DEFAULT_DISPLAY else Display.DEFAULT_DISPLAY

    fun screenOf(displayId: Int): Screen = if (displayId == Display.DEFAULT_DISPLAY) Screen.TOP else Screen.BOTTOM
}
