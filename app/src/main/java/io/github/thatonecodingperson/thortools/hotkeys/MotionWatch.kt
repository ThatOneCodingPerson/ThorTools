package io.github.thatonecodingperson.thortools.hotkeys

import io.github.thatonecodingperson.thortools.actions.ActionCategory
import io.github.thatonecodingperson.thortools.actions.ThorAction

/** Rules for the joystick catcher (`JoystickCatcher`). Pure. */
object MotionWatch {
    private val endingSystemActions = setOf(
        ThorAction.BACK,
        ThorAction.HOME,
        ThorAction.RECENTS,
        ThorAction.NOTIFICATIONS,
        ThorAction.QUICK_SETTINGS,
        ThorAction.LOCK_SCREEN,
        ThorAction.CLOSE_APP,
        ThorAction.AYN_DRAWER,
        ThorAction.OPEN_QUICK_PANEL,
    )

    /**
     * Whether the catcher goes away for the rest of the press after [action] ran: it moves the controller, the windows or
     * the screens, or AYN re-creates its pad. Levels, performance, screenshots and swipes keep it, so a stick can be
     * flicked again and again while the first button stays held.
     */
    fun endsWatch(action: ThorAction): Boolean = (action.category == ActionCategory.CONTROLLER && action != ThorAction.TOGGLE_DESKTOP) ||
        action.category == ActionCategory.SCREENS ||
        action.category == ActionCategory.APPS ||
        action in endingSystemActions
}
