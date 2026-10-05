package io.github.thatonecodingperson.thortools.panel

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.os.Handler
import android.os.Looper
import android.util.DisplayMetrics
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import io.github.thatonecodingperson.thortools.tools.ShellExecutor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Moves the controller (Android's focused display) to a screen. Only a touch moves it, so a tiny focusable overlay
 * is tapped with an accessibility gesture and removed right after; whatever focusable window is on top of that
 * screen then has the controller. The result is checked with `dumpsys input` and retried a few times. The newest move,
 * from any FocusMover, wins: an older one still under way neither taps nor retries, and reports false.
 */
class FocusMover(private val service: AccessibilityService, private val executor: ShellExecutor, private val scope: CoroutineScope) {
    private val mainHandler = Handler(Looper.getMainLooper())

    fun moveTo(displayId: Int, onDone: (Boolean) -> Unit = {}) = tryMove(displayId, ++latestMove, 1, onDone)

    private fun tryMove(displayId: Int, move: Int, attempt: Int, onDone: (Boolean) -> Unit) {
        if (move != latestMove) return onDone(false)
        val display = service.getSystemService(DisplayManager::class.java).getDisplay(displayId) ?: return onDone(false)
        val context = service.createDisplayContext(display)
        val manager = context.getSystemService(WindowManager::class.java)
        val metrics = DisplayMetrics().also {
            @Suppress("DEPRECATION")
            display.getRealMetrics(it)
        }
        val probe = View(context).apply { isFocusable = true }
        val params = WindowManager.LayoutParams(
            PROBE_PX,
            PROBE_PX,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
            PixelFormat.TRANSLUCENT,
        ).apply { gravity = Gravity.START or Gravity.CENTER_VERTICAL }
        if (runCatching { manager.addView(probe, params) }.isFailure) return onDone(false)

        var finished = false
        val finish = Runnable {
            if (finished) return@Runnable
            finished = true
            runCatching { manager.removeViewImmediate(probe) }
            verify(displayId, move, attempt, onDone)
        }
        // The gesture callback can be lost; the probe must never stay on screen.
        mainHandler.postDelayed(finish, SAFETY_MS)

        val tap = Path().apply { moveTo(PROBE_PX / 2f, metrics.heightPixels / 2f) }
        val gesture = GestureDescription.Builder()
            .setDisplayId(displayId)
            .addStroke(GestureDescription.StrokeDescription(tap, 0, TAP_MS))
            .build()
        val callback = object : AccessibilityService.GestureResultCallback() {
            override fun onCompleted(gestureDescription: GestureDescription) {
                mainHandler.postDelayed(finish, REMOVE_AFTER_TAP_MS)
            }

            override fun onCancelled(gestureDescription: GestureDescription) {
                mainHandler.post(finish)
            }
        }
        // A tap before the probe is on screen lands on the app underneath (closing the quick panel tapped something on
        // the other screen). So the tap waits for the probe's first frame, plus a moment for input to learn about the
        // window. Should that report never come, the tap goes at TAP_LATEST_MS, long after the probe
        // is up.
        var tapped = false
        val tapWhenShown = Runnable {
            if (finished || tapped) return@Runnable
            tapped = true
            // Overtaken by a newer move (the panel handing the controller back while a hotkey moves it): no tap.
            if (move != latestMove || !service.dispatchGesture(gesture, callback, mainHandler)) mainHandler.post(finish)
        }
        probe.viewTreeObserver.registerFrameCommitCallback { mainHandler.postDelayed(tapWhenShown, INPUT_READY_MS) }
        mainHandler.postDelayed(tapWhenShown, TAP_LATEST_MS)
    }

    private fun verify(displayId: Int, move: Int, attempt: Int, onDone: (Boolean) -> Unit) {
        scope.launch {
            val focused = executor.executeAsRoot("dumpsys input | grep -m1 FocusedDisplayId").getOrNull()
                ?.substringAfter(':', "")?.trim()?.toIntOrNull()
            mainHandler.post {
                when {
                    move != latestMove -> onDone(false)
                    focused == null || focused == displayId -> onDone(true)
                    attempt < MAX_ATTEMPTS -> mainHandler.postDelayed({ tryMove(displayId, move, attempt + 1, onDone) }, RETRY_MS)
                    else -> onDone(false)
                }
            }
        }
    }

    private companion object {
        /** The newest move of all FocusMovers (the panel, the lock and the actions each have one). Main thread. */
        var latestMove = 0

        const val PROBE_PX = 6
        const val TAP_MS = 20L

        /** After the probe's first frame is shown, until input routes touches to it (a few frames). */
        const val INPUT_READY_MS = 50L
        const val TAP_LATEST_MS = 250L
        const val REMOVE_AFTER_TAP_MS = 40L
        const val SAFETY_MS = 800L
        const val RETRY_MS = 200L
        const val MAX_ATTEMPTS = 4
    }
}
