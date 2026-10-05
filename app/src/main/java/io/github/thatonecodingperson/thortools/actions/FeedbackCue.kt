package io.github.thatonecodingperson.thortools.actions

import android.accessibilityservice.AccessibilityService
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.hardware.display.DisplayManager
import android.os.Handler
import android.os.Looper
import android.util.TypedValue
import android.view.Display
import android.view.Gravity
import android.view.WindowManager
import android.widget.TextView
import io.github.thatonecodingperson.thortools.data.SharedPrefsRepo
import io.github.thatonecodingperson.thortools.ui.theme.ThorThemes

/**
 * A short message near the bottom of a screen, in the theme's colours, gone after [hideAfterMs] (null: until [remove]).
 * Used instead of toasts, which only show on the top screen, so "Controller on the bottom screen" appears where it
 * happened.
 */
class FeedbackCue(
    private val service: AccessibilityService,
    private val prefs: SharedPrefsRepo,
    private val hideAfterMs: Long? = SHOW_MS,
    private val bottomOffsetDp: Int = BOTTOM_OFFSET_DP,
    private val windowTitle: String = "ThorToolsFeedback",
) {
    private val mainHandler = Handler(Looper.getMainLooper())
    private var view: TextView? = null
    private var manager: WindowManager? = null
    private var shownOn = Display.INVALID_DISPLAY
    private val hide = Runnable { remove() }

    /** Any thread. A new message replaces the one showing. */
    fun show(text: String, displayId: Int) {
        mainHandler.post { showNow(text, displayId) }
    }

    fun remove() {
        mainHandler.removeCallbacks(hide)
        val current = view ?: return
        view = null
        runCatching { manager?.removeViewImmediate(current) }
        manager = null
        shownOn = Display.INVALID_DISPLAY
    }

    private fun showNow(text: String, displayId: Int) {
        val displays = service.getSystemService(DisplayManager::class.java)
        val display = displays.getDisplay(displayId) ?: displays.getDisplay(Display.DEFAULT_DISPLAY) ?: return
        val current = view
        if (current != null && shownOn == display.displayId) {
            current.text = text
        } else {
            remove()
            add(text, display)
        }
        mainHandler.removeCallbacks(hide)
        hideAfterMs?.let { mainHandler.postDelayed(hide, it) }
    }

    /** Any thread. */
    fun hide() {
        mainHandler.post { remove() }
    }

    private fun add(text: String, display: Display) {
        val context = service.createDisplayContext(display)
        val density = context.resources.displayMetrics.density
        // The system theme has no fixed colours outside Compose; the default palette stands in for it.
        val palette = prefs.palette() ?: ThorThemes.default
        val label = TextView(context).apply {
            this.text = text
            setTextColor(palette.onSurface.toInt())
            setTextSize(TypedValue.COMPLEX_UNIT_SP, TEXT_SP)
            val horizontal = (PADDING_H_DP * density).toInt()
            val vertical = (PADDING_V_DP * density).toInt()
            setPadding(horizontal, vertical, horizontal, vertical)
            background = GradientDrawable().apply {
                cornerRadius = CORNER_DP * density
                setColor(palette.surface.toInt())
                setStroke(density.toInt().coerceAtLeast(1), palette.tileOff.toInt())
            }
        }
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
            y = (bottomOffsetDp * density).toInt()
            windowAnimations = android.R.style.Animation_Toast
            title = windowTitle
        }
        val windowManager = context.getSystemService(WindowManager::class.java)
        if (runCatching { windowManager.addView(label, params) }.isFailure) return
        view = label
        manager = windowManager
        shownOn = display.displayId
    }

    private companion object {
        const val SHOW_MS = 2500L
        const val TEXT_SP = 15f
        const val PADDING_H_DP = 18
        const val PADDING_V_DP = 10
        const val CORNER_DP = 20f
        const val BOTTOM_OFFSET_DP = 56
    }
}
