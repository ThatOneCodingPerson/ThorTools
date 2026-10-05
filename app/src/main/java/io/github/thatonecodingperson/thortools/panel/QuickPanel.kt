package io.github.thatonecodingperson.thortools.panel

import android.accessibilityservice.AccessibilityService
import android.annotation.SuppressLint
import android.app.ActivityOptions
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import android.util.DisplayMetrics
import android.view.Display
import android.view.Gravity
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.ComposeView
import io.github.thatonecodingperson.thortools.actions.ActionCall
import io.github.thatonecodingperson.thortools.actions.CloseTarget
import io.github.thatonecodingperson.thortools.actions.ThorAction
import io.github.thatonecodingperson.thortools.data.SharedPrefsRepo
import io.github.thatonecodingperson.thortools.hotkeys.CloseAppArg
import io.github.thatonecodingperson.thortools.input.Screen
import io.github.thatonecodingperson.thortools.input.ScreenFocus
import io.github.thatonecodingperson.thortools.main.MainActivity
import io.github.thatonecodingperson.thortools.tools.ShellExecutor
import io.github.thatonecodingperson.thortools.ui.theme.ThorToolsTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * The AYN-button quick panel: an accessibility overlay (so the game underneath is never paused), on the bottom
 * screen when it's on, else as a side sheet on the top screen.
 */
class QuickPanel(
    private val service: AccessibilityService,
    private val executor: ShellExecutor,
    private val prefs: SharedPrefsRepo,
    private val screenFocus: ScreenFocus,
    private val scope: CoroutineScope,
    private val helper: HelperCommand,
    stayAwakeOn: () -> Boolean,
    lockedTo: () -> Screen?,
    /** The display Thor Tools' own screen is showing on, null when it isn't showing. */
    private val thorToolsShownOn: () -> Int?,
    /** Joystick motion reaching the panel; true when the hotkeys took it (a held button's D-pad or stick combos). */
    private val onMotion: (MotionEvent) -> Boolean,
    private val runAction: (ActionCall) -> Unit,
) {
    private val mainHandler = Handler(Looper.getMainLooper())
    private val focusMover = FocusMover(service, executor, scope)
    private val model = QuickPanelModel(
        context = service,
        executor = executor,
        scope = scope,
        helper = helper,
        stayAwakeOn = stayAwakeOn,
        bottomDisplay = { screenFocus.bottomDisplayId() },
        lockedTo = lockedTo,
    )

    // L1 / R1 page steps, from the root view to the pager inside Compose.
    private val pageSteps = MutableSharedFlow<Int>(extraBufferCapacity = PAGE_STEP_BUFFER)

    private var root: View? = null
    private var windowManager: WindowManager? = null
    private var owner: OverlayOwner? = null
    private var returnDisplay = Display.DEFAULT_DISPLAY
    private var tookController = false
    private var liftedFocusLock = false
    private var openedAt = 0L

    /** Counts panel openings, so work queued for one panel never acts on a later one. Main thread. */
    private var panelSession = 0

    /** The display the controller was on when the panel opened (Android's focused display when it can be read). */
    @Volatile
    var openedFrom: Int = Display.DEFAULT_DISPLAY
        private set

    /** The app in front when the panel opened; another app coming up closes it. */
    var openedOver: String? = null
        private set

    val isOpen: Boolean get() = root != null

    /** Open and holding the controller (Panel takes the controller), so the pad's buttons work the panel. */
    val hasController: Boolean get() = isOpen && tookController

    /** The panel's Back, as a Back press inside it would do: back in the panel, or close it. */
    fun back() {
        owner?.let(::onBack)
    }

    fun toggle(foregroundPackage: String?) = if (isOpen) close() else open(foregroundPackage)

    /** After a hotkey changed something the panel shows, while it is open. */
    fun refreshSoon() {
        if (isOpen) model.refreshSoon()
    }

    fun open(foregroundPackage: String?) {
        if (isOpen) return
        openedFrom = screenFocus.displayId()
        val displays = service.getSystemService(DisplayManager::class.java)
        // AYN's "bottom screen off" may leave the display itself reporting on.
        val bottomOff = runCatching { Settings.System.getInt(service.contentResolver, KEY_SCREEN_MODE, 0) }.getOrDefault(0) == 1
        val bottom = screenFocus.bottomDisplayId()?.let(displays::getDisplay)?.takeIf { it.state == Display.STATE_ON && !bottomOff }
        val opened = (bottom?.let(::show) ?: false) || show(displays.getDisplay(Display.DEFAULT_DISPLAY))
        if (!opened) return
        openedOver = foregroundPackage
        openedAt = SystemClock.uptimeMillis()
        model.refresh()
        model.start()
    }

    /** [returnFocus] false when something else is about to decide where the controller goes (Home, screen off). */
    fun close(returnFocus: Boolean = true) {
        val closed = removeWindow() ?: return
        if (!closed.tookController) return
        if (!returnFocus) {
            if (closed.relock) setFocusLock(true)
            return
        }
        // "Here" for the controller is where it goes back to, even after a touch on the panel moved it to the panel's screen.
        screenFocus.lastScreen = screenFocus.screenOf(returnDisplay)
        mainHandler.postDelayed({ handBack(closed) }, RETURN_FOCUS_DELAY_MS)
    }

    /** What the panel had done when it closed: taken the controller, and lifted AYN's lock to do so. */
    private data class Closed(val tookController: Boolean, val relock: Boolean)

    /** Removes the panel's window; null when it wasn't open. */
    private fun removeWindow(): Closed? {
        val view = root ?: return null
        root = null
        model.stop()
        runCatching { windowManager?.removeViewImmediate(view) }
        windowManager = null
        owner?.destroy()
        owner = null
        openedOver = null
        val closed = Closed(tookController, tookController && liftedFocusLock)
        tookController = false
        liftedFocusLock = false
        return closed
    }

    /** Gives the controller back to the screen it came from. */
    private fun handBack(closed: Closed) {
        focusMover.moveTo(returnDisplay) { onTop ->
            // AYN's lock pins the controller to the top screen; arming it with focus elsewhere freezes input.
            if (closed.relock && onTop && returnDisplay == Display.DEFAULT_DISPLAY) setFocusLock(true)
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun show(display: Display?): Boolean {
        display ?: return false
        val context = service.createDisplayContext(display)
        val manager = context.getSystemService(WindowManager::class.java)
        val metrics = DisplayMetrics().also {
            @Suppress("DEPRECATION")
            display.getRealMetrics(it)
        }
        // Display contexts can report the top screen's size, so the real metrics decide the form.
        val sideSheet = metrics.widthPixels / metrics.density > SIDE_SHEET_MIN_WIDTH_DP
        val takes = prefs.panelTakesController
        val palette = prefs.palette()
        val panelOwner = OverlayOwner()
        val panelRoot = PanelRoot(context, onBack = { onBack(panelOwner) }, onPage = { pageSteps.tryEmit(it) }, onMotion = onMotion)
        panelRoot.setOnTouchListener { _, event ->
            if (event.actionMasked == MotionEvent.ACTION_OUTSIDE && SystemClock.uptimeMillis() - openedAt > OUTSIDE_TOUCH_GRACE_MS) close()
            false
        }
        panelOwner.attach(panelRoot)
        val layout = prefs.panelLayout
        val callbacks = PanelCallbacks(
            onTile = ::onTile,
            onRefreshRate = {
                runAction(ActionCall(ThorAction.TOGGLE_REFRESH_RATE))
                model.refreshSoon()
            },
            onVolume = model::setVolume,
            onTopBrightness = model::setTopBrightness,
            onBottomBrightness = model::setBottomBrightness,
            onClose = { close() },
        )
        panelRoot.addView(
            ComposeView(context).apply {
                setContent {
                    ThorToolsTheme(palette) {
                        val state by model.state.collectAsState()
                        val pagerState = rememberPagerState { layout.pages.size }
                        LaunchedEffect(Unit) {
                            // Instant, like turning a page; the swipe is the animated way.
                            pageSteps.collect { step ->
                                pagerState.scrollToPage((pagerState.currentPage + step).coerceIn(0, layout.pages.lastIndex))
                            }
                        }
                        QuickPanelContent(state = state, layout = layout, pagerState = pagerState, callbacks = callbacks)
                    }
                }
            },
        )

        var flags = WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
            WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED or
            WindowManager.LayoutParams.FLAG_DIM_BEHIND or
            WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH
        if (!takes) flags = flags or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
        val params = WindowManager.LayoutParams(
            if (sideSheet) (SIDE_SHEET_WIDTH_DP * metrics.density).toInt() else WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            flags,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.END or Gravity.TOP
            dimAmount = palette?.dimAmount ?: DEFAULT_DIM
            title = "ThorToolsQuickPanel"
        }
        if (runCatching { manager.addView(panelRoot, params) }.isFailure) {
            panelOwner.destroy()
            return false
        }
        root = panelRoot
        windowManager = manager
        owner = panelOwner
        val session = ++panelSession
        if (takes) {
            takeController(display.displayId, session)
        } else {
            scope.launch { readFocusedDisplay()?.let { openedFrom = it } }
        }
        return true
    }

    private fun takeController(displayId: Int, session: Int) {
        tookController = true
        returnDisplay = screenFocus.displayId()
        scope.launch {
            // Before the controller moves to the panel, Android still says where it was. The PServer call can't be cut
            // short, so it runs on its own and an answer later than the wait is dropped (it would name the panel's screen).
            val read = async { readFocusedDisplay() }
            withTimeoutOrNull(OPENED_FROM_WAIT_MS) { read.await() }?.let { openedFrom = it }
            // AYN's lock would keep the controller on the top screen while the panel is open.
            val aynLock = runCatching { Settings.System.getInt(service.contentResolver, KEY_FOCUS_LOCK, 0) }.getOrDefault(0) == 1
            mainHandler.post {
                // The panel may have closed (or a new one opened) meanwhile: then the lock and the controller stay put.
                if (!isOpen || session != panelSession) return@post
                if (aynLock) {
                    liftedFocusLock = true
                    setFocusLock(false)
                }
                focusMover.moveTo(displayId)
            }
        }
    }

    /** Root call: never on the main thread. */
    private fun readFocusedDisplay(): Int? =
        CloseTarget.parseFocusedDisplay(executor.executeAsRoot("dumpsys input | grep -m1 FocusedDisplayId").getOrNull())

    private fun setFocusLock(on: Boolean) {
        scope.launch { executor.executeAsRoot("settings put system $KEY_FOCUS_LOCK ${if (on) 1 else 0}") }
    }

    private fun onBack(panelOwner: OverlayOwner) {
        if (panelOwner.onBackPressedDispatcher.hasEnabledCallbacks()) panelOwner.onBackPressedDispatcher.onBackPressed() else close()
    }

    private fun onTile(id: String) {
        if (id == PanelTiles.THOR_TOOLS) return openThorTools()
        val action = PanelTiles.action(id)
        val delay = PanelTiles.leaveDelayMs(id)
        if (delay == null) {
            // The panel shows the new state itself, so no message on top of it.
            action?.let { runAction(ActionCall(it, alsoCleanMemory = it == ThorAction.CLEAR_BACKGROUND && prefs.panelCleanMemory)) }
            model.refreshSoon()
            return
        }
        // Decided before closing: afterwards the controller is on its way back and "here" would be the panel's screen.
        val call = when (action) {
            null -> null
            ThorAction.CLOSE_APP -> ActionCall(action, closeAppArg(), feedback = true)
            else -> ActionCall(action)
        }
        close()
        mainHandler.postDelayed({
            if (call != null) {
                runAction(call)
            } else {
                startOn(Intent(service, MainActivity::class.java), Display.DEFAULT_DISPLAY)
            }
        }, delay)
    }

    /**
     * The Thor Tools tile. The app starts while the panel still shows, and the panel then closes without handing the
     * controller back: that hand-back taps the screen the controller came from, which could bring the game back over
     * Thor Tools. The controller follows Thor Tools to the top screen; [checkLaunch] makes sure it came up.
     */
    private fun openThorTools() {
        startThorTools()
        val closed = removeWindow() ?: return
        mainHandler.postDelayed({ checkLaunch(attempt = 1, closed = closed) }, LAUNCH_CHECK_MS)
    }

    private fun startThorTools() =
        startOn(Intent(service, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT), Display.DEFAULT_DISPLAY)

    private fun checkLaunch(attempt: Int, closed: Closed) {
        // A panel opened since then has the controller now; the tile's follow-ups would undo that.
        if (isOpen) return
        val shownOn = thorToolsShownOn()
        val again = { mainHandler.postDelayed({ checkLaunch(attempt + 1, closed) }, LAUNCH_CHECK_MS) }
        when (LaunchCheck.next(attempt, shownOn, Display.DEFAULT_DISPLAY)) {
            LaunchCheck.Next.DONE -> if (closed.relock) relockWhenOnTop()
            LaunchCheck.Next.MOVE -> {
                val args = listOf(service.packageName, shownOn.toString(), Display.DEFAULT_DISPLAY.toString())
                if (!helper.send("move", args) { _, _ -> }) startThorTools()
                again()
            }
            LaunchCheck.Next.START_AGAIN -> {
                startThorTools()
                again()
            }
            LaunchCheck.Next.GIVE_UP -> if (closed.tookController) handBack(closed)
        }
    }

    /**
     * AYN's lock only goes back on once the controller is really on the top screen; elsewhere it would freeze input. It
     * checks again rather than moving the controller: that move taps the screen Thor Tools is now on.
     */
    private fun relockWhenOnTop(attempt: Int = 1) {
        scope.launch {
            val focused = CloseTarget.parseFocusedDisplay(executor.executeAsRoot("dumpsys input | grep -m1 FocusedDisplayId").getOrNull())
            mainHandler.post {
                if (isOpen) return@post
                when {
                    focused == Display.DEFAULT_DISPLAY -> setFocusLock(true)
                    attempt < RELOCK_CHECKS -> mainHandler.postDelayed({ relockWhenOnTop(attempt + 1) }, RELOCK_RETRY_MS)
                }
            }
        }
    }

    private fun closeAppArg(): String? =
        CloseAppArg.encode(CloseTarget.screenFor(prefs.panelCloseAppTarget, openedFromTop = openedFrom == Display.DEFAULT_DISPLAY))

    private fun startOn(intent: Intent, displayId: Int) {
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val options = ActivityOptions.makeBasic().setLaunchDisplayId(displayId).toBundle()
        runCatching { service.startActivity(intent, options) }.onFailure { service.startActivity(intent) }
    }

    /**
     * The panel's root view: B, Back and Escape go back, L1 and R1 turn pages; other pad buttons are dropped so Android
     * makes no fallback keys. Joystick motion the hotkeys take never moves the panel's focus.
     */
    private class PanelRoot(
        context: Context,
        private val onBack: () -> Unit,
        private val onPage: (Int) -> Unit,
        private val onMotion: (MotionEvent) -> Boolean,
    ) : FrameLayout(context) {
        override fun dispatchGenericMotionEvent(event: MotionEvent): Boolean = onMotion(event) || super.dispatchGenericMotionEvent(event)

        override fun dispatchKeyEvent(event: KeyEvent): Boolean {
            when (event.keyCode) {
                KeyEvent.KEYCODE_BACK, KeyEvent.KEYCODE_BUTTON_B, KeyEvent.KEYCODE_ESCAPE -> {
                    if (event.action == KeyEvent.ACTION_UP && !event.isCanceled) onBack()
                    return true
                }
                KeyEvent.KEYCODE_BUTTON_L1, KeyEvent.KEYCODE_BUTTON_R1 -> {
                    if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) {
                        onPage(if (event.keyCode == KeyEvent.KEYCODE_BUTTON_L1) -1 else 1)
                    }
                    return true
                }
                // A is left unhandled on purpose: Android turns it into D-pad centre, which clicks the focused tile.
                KeyEvent.KEYCODE_BUTTON_A -> return super.dispatchKeyEvent(event)
            }
            if (KeyEvent.isGamepadButton(event.keyCode)) return true
            return super.dispatchKeyEvent(event)
        }
    }

    private companion object {
        const val KEY_FOCUS_LOCK = "screen_focus_lock"
        const val KEY_SCREEN_MODE = "dual_screen_display_mode"
        const val SIDE_SHEET_MIN_WIDTH_DP = 700f
        const val SIDE_SHEET_WIDTH_DP = 560
        const val OUTSIDE_TOUCH_GRACE_MS = 800L
        const val RETURN_FOCUS_DELAY_MS = 150L
        const val DEFAULT_DIM = 0.5f
        const val LAUNCH_CHECK_MS = 700L
        const val OPENED_FROM_WAIT_MS = 250L
        const val RELOCK_CHECKS = 5
        const val RELOCK_RETRY_MS = 400L
        const val PAGE_STEP_BUFFER = 4
    }
}
