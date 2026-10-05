package io.github.thatonecodingperson.thortools.hotkeys

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.InputDevice
import android.view.KeyCharacterMap
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import io.github.thatonecodingperson.thortools.input.PadDirections
import io.github.thatonecodingperson.thortools.input.PadSample

/**
 * While a held button has D-pad or stick combos, a 1x1 focusable accessibility overlay on the controller's screen takes
 * the joystick focus: the D-pad and stick motion comes here instead of the game, and becomes direction presses for the
 * hotkeys ([onChange]). The accessibility key filter can't hold back motion on Android 13.
 * Main thread only.
 */
class JoystickCatcher(private val service: AccessibilityService, private val onChange: (PadButton, Boolean, Long) -> Unit) {
    private val handler = Handler(Looper.getMainLooper())
    private var view: View? = null
    private var manager: WindowManager? = null
    private var press: Long? = null
    private var finishedPress: Long? = null
    private val directions = mutableMapOf<Int, PadDirections>()
    private val cap = Runnable { finishPress() }

    /** Puts the catcher up for the press that started at [pressToken], unless it is up already or that press is done. */
    fun start(displayId: Int, pressToken: Long) {
        if (press == pressToken && (view != null || finishedPress == pressToken)) return
        stop()
        press = pressToken
        val display = service.getSystemService(DisplayManager::class.java).getDisplay(displayId) ?: return
        val context = service.createDisplayContext(display)
        val windowManager = context.getSystemService(WindowManager::class.java)
        val catcher = CatcherView(context) { event ->
            directions.getOrPut(event.deviceId, ::PadDirections).update(event.toPadSample()).forEach {
                onChange(it.button, it.down, event.eventTime)
            }
        }
        val params = WindowManager.LayoutParams(
            1,
            1,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM,
            PixelFormat.TRANSPARENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            title = "ThorToolsJoystickCatcher"
        }
        if (runCatching { windowManager.addView(catcher, params) }.isFailure) return
        view = catcher
        manager = windowManager
        catcher.requestFocus()
        handler.postDelayed(cap, CAP_MS)
    }

    /** Takes the catcher down and keeps it down for the rest of this press (after an action that moves focus or windows). */
    fun finishPress() {
        finishedPress = press
        stop()
    }

    fun stop() {
        handler.removeCallbacks(cap)
        directions.clear()
        val catcher = view ?: return
        view = null
        // Not only when attached: a quick tap can take it down before it ever attached.
        runCatching { manager?.removeViewImmediate(catcher) }
        manager = null
    }

    /**
     * The catcher's 1x1 view: joystick motion goes to [onMotion]. Pad buttons pressed meanwhile end here too, so Android
     * makes no fallback keys from them.
     */
    class CatcherView(context: Context, private val onMotion: (MotionEvent) -> Unit) : View(context) {
        init {
            isFocusable = true
            isFocusableInTouchMode = true
        }

        override fun onGenericMotionEvent(event: MotionEvent): Boolean {
            if (!event.isFromSource(InputDevice.SOURCE_JOYSTICK)) return super.onGenericMotionEvent(event)
            if (event.actionMasked == MotionEvent.ACTION_MOVE) onMotion(event)
            return true
        }

        override fun dispatchKeyEvent(event: KeyEvent): Boolean {
            if (event.deviceId == KeyCharacterMap.VIRTUAL_KEYBOARD) return super.dispatchKeyEvent(event)
            return KeyEvent.isGamepadButton(event.keyCode) || event.keyCode == KeyEvent.KEYCODE_BACK || super.dispatchKeyEvent(event)
        }
    }

    private companion object {
        /** The game loses window focus while the catcher is up, so it never stays long. */
        const val CAP_MS = 5000L
    }
}

/** The D-pad and both sticks of a joystick event. The right stick is Z / RZ on the Thor's pad, RX / RY on some others. */
fun MotionEvent.toPadSample() = PadSample(
    hatX = getAxisValue(MotionEvent.AXIS_HAT_X),
    hatY = getAxisValue(MotionEvent.AXIS_HAT_Y),
    leftX = getAxisValue(MotionEvent.AXIS_X),
    leftY = getAxisValue(MotionEvent.AXIS_Y),
    rightX = getAxisValue(MotionEvent.AXIS_Z).takeIf { it != 0f } ?: getAxisValue(MotionEvent.AXIS_RX),
    rightY = getAxisValue(MotionEvent.AXIS_RZ).takeIf { it != 0f } ?: getAxisValue(MotionEvent.AXIS_RY),
)
