package io.github.thatonecodingperson.thortools.actions

import androidx.annotation.StringRes
import io.github.thatonecodingperson.thortools.R
import io.github.thatonecodingperson.thortools.hotkeys.LaunchScreen

/** Which app the quick panel's "Close the current app" tile closes. [id] is stored, so it must never change. */
enum class PanelCloseTarget(val id: String, @StringRes val label: Int, @StringRes val info: Int) {
    OPENED_FROM("opened_from", R.string.panelCloseOpenedFrom, R.string.panelCloseOpenedFromInfo),
    TOP("top", R.string.panelCloseTop, R.string.panelCloseTopInfo),
    BOTTOM("bottom", R.string.panelCloseBottom, R.string.panelCloseBottomInfo),
    ;

    companion object {
        fun byId(id: String?): PanelCloseTarget? = entries.find { it.id == id }
    }
}

/** Finding the app "Close the current app" acts on, one screen at a time. Pure. */
object CloseTarget {
    /**
     * The screen the panel's tile closes on. Never "the screen with the controller": while the panel closes, the
     * controller is still on the panel's screen.
     */
    fun screenFor(target: PanelCloseTarget, openedFromTop: Boolean): LaunchScreen = when (target) {
        PanelCloseTarget.OPENED_FROM -> if (openedFromTop) LaunchScreen.TOP else LaunchScreen.BOTTOM
        PanelCloseTarget.TOP -> LaunchScreen.TOP
        PanelCloseTarget.BOTTOM -> LaunchScreen.BOTTOM
    }

    /** The app showing on [display], if it may be closed: never Thor Tools itself, never a malformed name. */
    fun appToClose(apps: Map<Int, String>, display: Int, ownPackage: String, valid: (String) -> Boolean): String? =
        apps[display]?.takeIf { it != ownPackage && valid(it) }

    /** The display id in a `dumpsys input | grep -m1 FocusedDisplayId` line, e.g. `FocusedDisplayId: 2`. */
    fun parseFocusedDisplay(line: String?): Int? = line?.substringAfter(':', "")?.trim()?.toIntOrNull()
}
