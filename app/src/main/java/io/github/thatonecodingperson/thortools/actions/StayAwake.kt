package io.github.thatonecodingperson.thortools.actions

import android.accessibilityservice.AccessibilityService
import android.graphics.PixelFormat
import android.view.View
import android.view.WindowManager

/** Keeps the screens on with an invisible 1x1 overlay that carries `FLAG_KEEP_SCREEN_ON`; no wake lock needed. */
class StayAwake(private val service: AccessibilityService) {
    private var view: View? = null

    val isOn: Boolean get() = view != null

    /** Returns whether staying awake is now on. */
    fun toggle(): Boolean {
        if (view != null) {
            off()
            return false
        }
        val keeper = View(service)
        val params = WindowManager.LayoutParams(
            1,
            1,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
            PixelFormat.TRANSLUCENT,
        )
        return runCatching { service.getSystemService(WindowManager::class.java).addView(keeper, params) }
            .onSuccess { view = keeper }
            .isSuccess
    }

    fun off() {
        val keeper = view ?: return
        view = null
        runCatching { service.getSystemService(WindowManager::class.java).removeViewImmediate(keeper) }
    }
}
