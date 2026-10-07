package io.github.thatonecodingperson.thortools.input

import android.accessibilityservice.AccessibilityService
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import android.util.Log
import android.view.Display
import android.view.accessibility.AccessibilityNodeInfo
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
    private val sendBack = Runnable { returnToBottom(firstLook = true) }
    private val sendBackLater = Runnable { returnToBottom(firstLook = false) }

    @Volatile
    var lockedTo: Screen? = null
        private set

    /** Told where the lock moved the controller, so the service knows without reading Android again. */
    var onMoved: ((Int) -> Unit)? = null

    /** Desktop controls keep the controller on the bottom screen, the way the bottom lock does, without being a lock. */
    var desktopHoldsBottom: () -> Boolean = { false }

    /** Where the controller belongs: the locked screen, or the bottom screen while desktop controls keep it there. */
    val belongsTo: Screen? get() = lockedTo ?: if (desktopHoldsBottom()) Screen.BOTTOM else null

    /** A finger is on the top screen: a return to the bottom waits for it to lift. */
    private var fingerOnTop = false

    /** A move back to the top screen is under way. */
    private var pulling = false

    /** When the recent moves back happened, so a controller that won't stay is let be instead of fought over. */
    private val pulls = ArrayDeque<Long>()

    /** What Thor Tools last wrote to AYN's lock (null: nothing yet), and how many of its writes are still under way. */
    private var written: Int? = null
    private var writing = 0

    private val checkAynLock = Runnable { readAynLock() }
    private val aynLockObserver = object : ContentObserver(mainHandler) {
        override fun onChange(selfChange: Boolean) {
            mainHandler.removeCallbacks(checkAynLock)
            mainHandler.postDelayed(checkAynLock, AYN_LOCK_SETTLE_MS)
        }
    }

    /** AYN's lock may still be on from before this service started. */
    fun start() {
        runCatching {
            service.contentResolver.registerContentObserver(Settings.System.getUriFor(KEY_FOCUS_LOCK), false, aynLockObserver)
        }
        scope.launch {
            val value = systemInt(KEY_FOCUS_LOCK)
            mainHandler.post {
                if (written == null) written = value
                if (value == 1 && lockedTo == null) lockedTo = Screen.TOP
            }
        }
    }

    fun stop() {
        runCatching { service.contentResolver.unregisterContentObserver(aynLockObserver) }
        mainHandler.removeCallbacks(checkAynLock)
        cancelReturn()
    }

    /** Locks to [target], or unlocks with null. [onDone] gets the lock in place afterwards (null if moving failed). */
    fun set(target: Screen?, onDone: (Screen?) -> Unit = {}) {
        val previous = lockedTo
        lockedTo = target
        cancelReturn()
        // AYN's lock must be off before the controller can move to the bottom screen.
        if (previous == Screen.TOP && target != Screen.TOP) {
            setAynLock(false) { apply(target, onDone) }
        } else {
            mainHandler.post { apply(target, onDone) }
        }
    }

    /**
     * Writes AYN's lock; every write of it in Thor Tools goes through here, so a change made elsewhere (AYN, another app)
     * can be told apart. [then] runs on the main thread once it is written.
     */
    fun setAynLock(on: Boolean, then: (() -> Unit)? = null) {
        val value = if (on) 1 else 0
        written = value
        writing++
        scope.launch {
            try {
                executor.executeAsRoot("settings put system $KEY_FOCUS_LOCK $value")
            } finally {
                mainHandler.post {
                    writing--
                    then?.invoke()
                }
            }
        }
    }

    /** AYN's lock changed. Thor Tools' own writes are known; a change made elsewhere is followed, both ways. */
    private fun readAynLock() {
        if (writing > 0) {
            mainHandler.postDelayed(checkAynLock, AYN_LOCK_SETTLE_MS)
            return
        }
        scope.launch {
            val value = systemInt(KEY_FOCUS_LOCK)
            mainHandler.post {
                // A write of Thor Tools' own started meanwhile: its own change notice looks again afterwards.
                if (writing > 0 || value == written) return@post
                written = value
                when {
                    value == 0 && lockedTo == Screen.TOP -> lockedTo = null
                    value == 1 && lockedTo != Screen.TOP -> {
                        lockedTo = Screen.TOP
                        cancelReturn()
                    }
                    else -> return@post
                }
                Log.d(TAG, "AYN's controller lock set to $value elsewhere; the lock follows: ${lockedTo ?: "off"}")
            }
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
                if (onTop) onMoved?.invoke(Display.DEFAULT_DISPLAY)
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
                    if (onBottom) onMoved?.invoke(bottom)
                    onDone(lockedTo)
                }
            }
        }
    }

    /**
     * Android's focused display read as [displayId]. Locked to the top, the controller belongs there: an app starting on
     * the bottom screen takes it even with AYN's lock on, so it is brought back (not while the quick panel holds it, nor
     * while a keyboard is up, which types into the app that has it).
     */
    fun onFocusedDisplay(displayId: Int) {
        if (displayId == Display.DEFAULT_DISPLAY && belongsTo == Screen.BOTTOM) return keepBottom()
        if (lockedTo != Screen.TOP || displayId == Display.DEFAULT_DISPLAY) return
        val now = SystemClock.uptimeMillis()
        while (pulls.isNotEmpty() && now - pulls.first() > PULL_WINDOW_MS) pulls.removeFirst()
        val waits = when {
            pulling -> "a move is under way"
            paused() -> "the quick panel holds it"
            keyboardShown() -> "a keyboard is up"
            pulls.size >= MAX_PULLS -> "it was moved back ${pulls.size} times just now"
            else -> null
        }
        if (waits != null) {
            Log.d(TAG, "controller on display $displayId while locked to the top; left there: $waits")
            return
        }
        pulls.addLast(now)
        pulling = true
        focusMover.moveTo(Display.DEFAULT_DISPLAY) { onTop ->
            pulling = false
            Log.d(TAG, "controller on display $displayId while locked to the top; moved back: $onTop")
            if (!onTop || lockedTo != Screen.TOP) return@moveTo
            setAynLock(true)
            onMoved?.invoke(Display.DEFAULT_DISPLAY)
        }
    }

    /** A new finger on [screen]: a pending return waits for that finger too. */
    fun onTouch(screen: Screen) {
        if (screen != Screen.TOP) return
        fingerOnTop = true
        cancelReturn()
    }

    /** The last finger left [screen]. */
    fun onLift(screen: Screen) {
        if (screen == Screen.TOP) fingerOnTop = false
        if (!LockPolicy.returnsAfterLift(belongsTo, screen, paused())) return
        cancelReturn()
        mainHandler.postDelayed(sendBack, RETURN_DELAY_MS)
    }

    /**
     * The controller may have gone up while it belongs on the bottom screen (a click on the top screen, or a read that
     * found it there): it goes back a moment later, as after a touch, and not while a finger is still on the top screen.
     */
    fun keepBottom() {
        if (belongsTo != Screen.BOTTOM || paused() || fingerOnTop) return
        cancelReturn()
        mainHandler.postDelayed(sendBack, RETURN_DELAY_MS)
    }

    private fun cancelReturn() {
        mainHandler.removeCallbacks(sendBack)
        mainHandler.removeCallbacks(sendBackLater)
    }

    private fun returnToBottom(firstLook: Boolean) {
        if (belongsTo != Screen.BOTTOM || paused()) return
        if (LockPolicy.waitsForTyping(keyboardShown(), editableFocused(), firstLook)) {
            mainHandler.postDelayed(sendBackLater, TYPING_CHECK_MS)
            return
        }
        scope.launch {
            // While AYN has the bottom screen switched off there is nothing to return to.
            if (systemInt(KEY_SCREEN_MODE) == 1) return@launch
            val bottom = screenFocus.bottomDisplayId() ?: return@launch
            mainHandler.post {
                if (belongsTo == Screen.BOTTOM && !paused()) focusMover.moveTo(bottom) { moved -> if (moved) onMoved?.invoke(bottom) }
            }
        }
    }

    /** A keyboard window on either screen (AYN can show it on the other screen than the app's). */
    private fun keyboardShown(): Boolean = runCatching {
        val windows = service.windowsOnAllDisplays
        (0 until windows.size()).any { index -> windows.valueAt(index).any { it.type == AccessibilityWindowInfo.TYPE_INPUT_METHOD } }
    }.getOrDefault(false)

    private fun editableFocused(): Boolean = runCatching {
        service.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)?.isEditable == true
    }.getOrDefault(false)

    private fun systemInt(key: String): Int = runCatching { Settings.System.getInt(service.contentResolver, key, 0) }
        .getOrElse { executor.getIntSystemSetting(key, 0) }

    private companion object {
        const val KEY_FOCUS_LOCK = "screen_focus_lock"
        const val TAG = "ThorToolsKeys"
        const val PULL_WINDOW_MS = 10_000L
        const val MAX_PULLS = 3
        const val KEY_SCREEN_MODE = "dual_screen_display_mode"

        // Android applies the touch's own focus change a moment after the lift; going back sooner gets undone.
        const val RETURN_DELAY_MS = 300L

        // Change notices come in bursts; AYN's lock is read once they settle.
        const val AYN_LOCK_SETTLE_MS = 300L

        // While typing, the return looks again this often and goes once the keyboard is closed.
        const val TYPING_CHECK_MS = 500L
    }
}
