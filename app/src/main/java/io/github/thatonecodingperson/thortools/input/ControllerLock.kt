package io.github.thatonecodingperson.thortools.input

import android.accessibilityservice.AccessibilityService
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.Display
import android.view.accessibility.AccessibilityWindowInfo
import io.github.thatonecodingperson.thortools.panel.FocusMover
import io.github.thatonecodingperson.thortools.tools.ShellExecutor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Keeps the controller on one screen. Top: AYN's own `screen_focus_lock`, armed only once the controller is verified
 * on the top screen. Bottom: AYN has no such lock, so after the last finger leaves the top screen the controller is sent
 * back (needs the root input helper's touch reports). Rules in [LockPolicy].
 */
class ControllerLock(
    private val service: AccessibilityService,
    private val executor: ShellExecutor,
    private val focusMover: FocusMover,
    private val screenFocus: ScreenFocus,
    private val scope: CoroutineScope,
    /** The quick panel holds the controller while it is open; the lock waits meanwhile. */
    private val paused: () -> Boolean,
) {
    private val mainHandler = Handler(Looper.getMainLooper())
    private val sendBack = Runnable { returnToBottom() }

    @Volatile
    var lockedTo: Screen? = null
        private set

    /** AYN's lock may still be on from before this service started. */
    fun start() {
        scope.launch {
            if (systemInt(KEY_FOCUS_LOCK) == 1) mainHandler.post { if (lockedTo == null) lockedTo = Screen.TOP }
        }
    }

    /** Locks to [target], or unlocks with null. [onDone] gets the lock in place afterwards (null if moving failed). */
    fun set(target: Screen?, onDone: (Screen?) -> Unit = {}) {
        val previous = lockedTo
        lockedTo = target
        mainHandler.removeCallbacks(sendBack)
        scope.launch {
            // AYN's lock must be off before the controller can move to the bottom screen.
            if (previous == Screen.TOP && target != Screen.TOP) executor.executeAsRoot("settings put system $KEY_FOCUS_LOCK 0")
            mainHandler.post { apply(target, onDone) }
        }
    }

    private fun apply(target: Screen?, onDone: (Screen?) -> Unit) {
        if (lockedTo != target) return onDone(lockedTo)
        when (target) {
            null -> onDone(null)
            Screen.TOP -> focusMover.moveTo(Display.DEFAULT_DISPLAY) { onTop ->
                // Armed with the controller on the bottom, AYN's lock freezes the top screen's input for seconds.
                if (lockedTo == Screen.TOP) {
                    if (onTop) setAynLock(true) else lockedTo = null
                }
                onDone(lockedTo)
            }
            Screen.BOTTOM -> {
                val bottom = screenFocus.bottomDisplayId()
                if (bottom == null) {
                    lockedTo = null
                    return onDone(null)
                }
                focusMover.moveTo(bottom) { onBottom ->
                    if (!onBottom && lockedTo == Screen.BOTTOM) lockedTo = null
                    onDone(lockedTo)
                }
            }
        }
    }

    /** A new finger on [screen]: a pending return waits for that finger too. */
    fun onTouch(screen: Screen) {
        if (screen == Screen.TOP) mainHandler.removeCallbacks(sendBack)
    }

    /** The last finger left [screen]. */
    fun onLift(screen: Screen) {
        if (!LockPolicy.returnsAfterLift(lockedTo, screen, keyboardOnTop(), paused())) return
        mainHandler.removeCallbacks(sendBack)
        mainHandler.postDelayed(sendBack, RETURN_DELAY_MS)
    }

    private fun returnToBottom() {
        if (lockedTo != Screen.BOTTOM || paused()) return
        scope.launch {
            // While AYN has the bottom screen switched off there is nothing to return to.
            if (systemInt(KEY_SCREEN_MODE) == 1) return@launch
            val bottom = screenFocus.bottomDisplayId() ?: return@launch
            mainHandler.post { if (lockedTo == Screen.BOTTOM && !paused()) focusMover.moveTo(bottom) }
        }
    }

    private fun keyboardOnTop(): Boolean = runCatching {
        service.windowsOnAllDisplays.get(Display.DEFAULT_DISPLAY)?.any { it.type == AccessibilityWindowInfo.TYPE_INPUT_METHOD } == true
    }.getOrDefault(false)

    private fun setAynLock(on: Boolean) {
        scope.launch { executor.executeAsRoot("settings put system $KEY_FOCUS_LOCK ${if (on) 1 else 0}") }
    }

    private fun systemInt(key: String): Int = runCatching { Settings.System.getInt(service.contentResolver, key, 0) }
        .getOrElse { executor.getIntSystemSetting(key, 0) }

    private companion object {
        const val KEY_FOCUS_LOCK = "screen_focus_lock"
        const val KEY_SCREEN_MODE = "dual_screen_display_mode"

        // Android applies the touch's own focus change a moment after the lift; going back sooner gets undone.
        const val RETURN_DELAY_MS = 300L
    }
}
