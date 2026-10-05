package io.github.thatonecodingperson.thortools.hotkeys

import android.view.InputDevice
import android.view.MotionEvent
import io.github.thatonecodingperson.thortools.input.PadDirections

/**
 * Joystick motion that reaches the open quick panel. The panel has the controller, so the D-pad and sticks already come
 * to it and no joystick catcher goes up; while a held button has D-pad or stick combos, the motion becomes direction
 * presses for the hotkeys ([onChange]) instead of moving the panel's focus. Fresh readings for every press, as the
 * catcher's. Main thread.
 */
class PanelMotion(private val onChange: (PadButton, Boolean, Long) -> Unit) {
    private var press: Long? = null
    private val directions = mutableMapOf<Int, PadDirections>()

    /** Whether [event] belongs to the hotkeys: [heldSince] is the held press with such combos, null when there is none. */
    fun take(event: MotionEvent, heldSince: Long?): Boolean {
        if (heldSince == null || !event.isFromSource(InputDevice.SOURCE_JOYSTICK)) return false
        if (heldSince != press) {
            press = heldSince
            directions.clear()
        }
        if (event.actionMasked == MotionEvent.ACTION_MOVE) {
            directions.getOrPut(event.deviceId, ::PadDirections).update(event.toPadSample()).forEach {
                onChange(it.button, it.down, event.eventTime)
            }
        }
        return true
    }
}
