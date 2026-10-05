package io.github.thatonecodingperson.thortools.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.database.ContentObserver
import android.hardware.display.DisplayManager
import android.hardware.input.InputManager
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import android.view.Display
import android.view.InputDevice
import android.view.KeyCharacterMap
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityWindowInfo
import dagger.hilt.android.AndroidEntryPoint
import io.github.thatonecodingperson.thortools.R
import io.github.thatonecodingperson.thortools.actions.ActionCall
import io.github.thatonecodingperson.thortools.actions.ActionHost
import io.github.thatonecodingperson.thortools.actions.ActionRunner
import io.github.thatonecodingperson.thortools.actions.CloseTarget
import io.github.thatonecodingperson.thortools.actions.DisplayHome
import io.github.thatonecodingperson.thortools.actions.FeedbackCue
import io.github.thatonecodingperson.thortools.actions.StayAwake
import io.github.thatonecodingperson.thortools.actions.ThorAction
import io.github.thatonecodingperson.thortools.charging.ChargeMonitor
import io.github.thatonecodingperson.thortools.coexist.OdinToolsDetector
import io.github.thatonecodingperson.thortools.coexist.Overlap
import io.github.thatonecodingperson.thortools.data.AppOverrideDao
import io.github.thatonecodingperson.thortools.data.AppOverrideEntity
import io.github.thatonecodingperson.thortools.data.SharedPrefsRepo
import io.github.thatonecodingperson.thortools.diagnostics.KeyLogEntry
import io.github.thatonecodingperson.thortools.diagnostics.ServiceKeyLog
import io.github.thatonecodingperson.thortools.hotkeys.Hotkey
import io.github.thatonecodingperson.thortools.hotkeys.HotkeyList
import io.github.thatonecodingperson.thortools.hotkeys.HotkeyRecognizer
import io.github.thatonecodingperson.thortools.hotkeys.JoystickCatcher
import io.github.thatonecodingperson.thortools.hotkeys.MotionWatch
import io.github.thatonecodingperson.thortools.hotkeys.PadButton
import io.github.thatonecodingperson.thortools.hotkeys.PanelKeys
import io.github.thatonecodingperson.thortools.hotkeys.PanelMotion
import io.github.thatonecodingperson.thortools.hotkeys.SystemPress
import io.github.thatonecodingperson.thortools.input.ControllerLock
import io.github.thatonecodingperson.thortools.input.RawInputClient
import io.github.thatonecodingperson.thortools.input.Screen
import io.github.thatonecodingperson.thortools.input.ScreenApps
import io.github.thatonecodingperson.thortools.input.ScreenFocus
import io.github.thatonecodingperson.thortools.input.WindowSnapshot
import io.github.thatonecodingperson.thortools.lid.LidController
import io.github.thatonecodingperson.thortools.lid.LidText
import io.github.thatonecodingperson.thortools.main.MainActivity
import io.github.thatonecodingperson.thortools.models.AppRefreshRate
import io.github.thatonecodingperson.thortools.models.BottomScreenRule
import io.github.thatonecodingperson.thortools.models.ControllerStyle
import io.github.thatonecodingperson.thortools.models.ControllerStyle.Unknown
import io.github.thatonecodingperson.thortools.models.FanMode
import io.github.thatonecodingperson.thortools.models.L2R2Style
import io.github.thatonecodingperson.thortools.models.PerfMode
import io.github.thatonecodingperson.thortools.models.RefreshRate
import io.github.thatonecodingperson.thortools.models.ScreenMode
import io.github.thatonecodingperson.thortools.panel.CloseReason
import io.github.thatonecodingperson.thortools.panel.FocusMover
import io.github.thatonecodingperson.thortools.panel.FrontApp
import io.github.thatonecodingperson.thortools.panel.PlayTime
import io.github.thatonecodingperson.thortools.panel.QuickPanel
import io.github.thatonecodingperson.thortools.panel.RecentApps
import io.github.thatonecodingperson.thortools.panel.WidgetType
import io.github.thatonecodingperson.thortools.tools.AynHooks
import io.github.thatonecodingperson.thortools.tools.BatteryLevelReceiver
import io.github.thatonecodingperson.thortools.tools.ShellExecutor
import io.github.thatonecodingperson.thortools.tools.VideoOutputReceiver
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject

@AndroidEntryPoint
class ForegroundAppWatcherService @Inject constructor() : AccessibilityService() {

    @Inject
    lateinit var appOverrideDao: AppOverrideDao

    @Inject
    lateinit var executor: ShellExecutor

    @Inject
    lateinit var prefs: SharedPrefsRepo

    @Inject
    lateinit var chargeMonitor: ChargeMonitor

    @Inject
    lateinit var serviceKeyLog: ServiceKeyLog

    @Inject
    lateinit var status: ServiceStatus

    private var batteryLevelReceiver: BatteryLevelReceiver = BatteryLevelReceiver()
    private var videoOutputReceiver: VideoOutputReceiver = VideoOutputReceiver()

    private val job = SupervisorJob()
    private val scope = CoroutineScope(Dispatchers.IO + job)

    private var currentForegroundPackage = ""

    @Volatile
    private lateinit var overrides: List<AppOverrideEntity>
    private var overridesEnabled = true
    private var overridesDelay = false
    private var perAppControlsEnabled = true

    // The four categories OdinTools also manages, applied only while Thor Tools has them (see Overlap.PER_APP_CONTROLS).
    private var hasSetOverride = false
    private var savedControllerStyle: ControllerStyle? = null
    private var savedL2R2Style: L2R2Style? = null
    private var savedPerfMode: PerfMode? = null
    private var savedFanMode: FanMode? = null

    // Thor Tools' own categories; OdinTools doesn't touch them, so they apply either way. Null: not changed now.
    private var savedRefreshRate: RefreshRate.Saved? = null
    private var savedScreenMode: Int? = null

    @Volatile
    private var currentIme = ""
    private val imeObserver = object : ContentObserver(Handler(Looper.getMainLooper())) {
        override fun onChange(selfChange: Boolean) {
            currentIme = Settings.Secure.getString(contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD).orEmpty()
        }
    }

    private var chargeLimitEnabled: Boolean = false
    private var videoOutputOverrideEnabled: Boolean = false

    private val mainHandler = Handler(Looper.getMainLooper())

    // Per-app overrides write through PServer, which is slow; key events share the main thread with us.
    private val overrideExecutor = Executors.newSingleThreadExecutor()
    private lateinit var actionRunner: ActionRunner

    // Main thread only, like the key events it reads; replaced whenever the hotkey list or the app in front changes.
    private var allHotkeys = HotkeyList.defaults
    private var scopeWaiting = false
    private var hotkeyList = HotkeyList.defaults
    private var hotkeys = HotkeyRecognizer(hotkeyList)
    private val hotkeyTimer = Runnable {
        carryOut(hotkeys.onTimer(SystemClock.uptimeMillis()))
        watchJoystick()
        useWaitingHotkeys()
    }
    private lateinit var joystickCatcher: JoystickCatcher

    /** A Home press the open panel is answering is down (its release must be swallowed too). */
    private var homeForPanel = false

    /**
     * The root helper reads the pad's D-pad and sticks raw for the button held now; the catcher and the panel then only
     * keep that motion from the game and the panel's focus. Main thread.
     */
    private var padWatching = false

    /** The input device the held button was pressed on: if it goes away, its release never comes. */
    private var heldFrom: Int? = null
    private val panelMotion = PanelMotion { button, down, time -> if (!padWatching) onDirection(button, down, time) }
    private val catcherDue = Runnable { watchJoystick() }
    private val focusReadAgain = Runnable { refreshFocusedDisplay() }

    /**
     * The display Android routes the controller to, for the joystick catcher: the last touched screen alone misses an
     * app opening on the other screen. Kept up to date only while some hotkey has D-pad or stick combos.
     */
    @Volatile
    private var focusedDisplay: Int? = null
    private val readingFocus = AtomicBoolean(false)
    private var lastFocusRead = 0L
    private lateinit var systemPress: SystemPress

    @Volatile
    private var hotkeysOffIn = emptySet<String>()
    private lateinit var screenFocus: ScreenFocus
    private lateinit var rawInput: RawInputClient
    private lateinit var displayHome: DisplayHome
    private lateinit var quickPanel: QuickPanel
    private lateinit var focusMover: FocusMover
    private lateinit var stayAwake: StayAwake
    private lateinit var controllerLock: ControllerLock
    private lateinit var lidController: LidController
    private val actionHost = object : ActionHost {
        override val foregroundPackage: String? get() = lastAppPackage

        override fun appsOnScreens(): Map<Int, String> {
            val windows = windowsOnAllDisplays
            val snapshots = (0 until windows.size()).flatMap { index ->
                windows.valueAt(index).sortedByDescending { it.layer }.map { window ->
                    WindowSnapshot(
                        displayId = windows.keyAt(index),
                        isApplication = window.type == AccessibilityWindowInfo.TYPE_APPLICATION,
                        packageName = window.root?.packageName?.toString(),
                    )
                }
            }
            val excluded = homePackages + ignoredPackages + currentIme.substringBefore('/')
            return ScreenApps.onScreens(snapshots, excluded, dialogPackages)
        }

        override fun bottomDisplayId(): Int? = screenFocus.bottomDisplayId()

        override fun controllerDisplayId(): Int = screenFocus.displayId()

        override fun goHome(displayId: Int?) {
            quickPanel.close(CloseReason.HOME, returnFocus = false)
            displayHome.goHome(displayId)
        }

        override fun homeAfterMove(displayId: Int) = displayHome.homeAfterMove(displayId)

        override fun goHomeBoth(bottomDisplayId: Int) {
            quickPanel.close(CloseReason.HOME, returnFocus = false)
            displayHome.goHomeBoth(bottomDisplayId)
        }

        override fun togglePanel() = quickPanel.toggle(lastAppPackage)

        override fun moveController(displayId: Int, onDone: (Boolean) -> Unit) {
            screenFocus.lastScreen = screenFocus.screenOf(displayId)
            focusMover.moveTo(displayId) { moved ->
                if (moved) {
                    focusedDisplay = displayId
                    quickPanel.controllerOn(displayId)
                }
                onDone(moved)
            }
        }

        override val lockedTo: Screen? get() = controllerLock.lockedTo

        override fun setLock(target: Screen?, onDone: (Screen?) -> Unit) = controllerLock.set(target) { lockedTo ->
            // Locking moves the controller to that screen, away from an open panel elsewhere.
            lockedTo?.let { quickPanel.controllerOn(screenFocus.displayId(it)) }
            onDone(lockedTo)
        }

        override fun toggleStayAwake(): Boolean = stayAwake.toggle()

        override fun pressBack() = this@ForegroundAppWatcherService.pressBack()

        override fun panelOpenedFrom(): Int? = quickPanel.takeIf { it.isOpen }?.openedFrom

        override fun keepWhenClosingBackground(): Set<String> =
            homePackages + packageName + ignoredPackages + currentIme.substringBefore('/') + appsOnScreens().values

        override fun helperCommand(name: String, vararg args: String, onResult: (Boolean, String) -> Unit): Boolean =
            rawInput.command(name, *args, onResult = onResult)
    }
    private val rawInputListener = object : RawInputClient.Listener {
        override fun onTouch(screen: Screen) {
            screenFocus.lastScreen = screen
            // A finger on a screen gives that screen the controller, an open panel's or not.
            if (hotkeys.usesMotion || quickPanel.isOpen) focusedDisplay = screenFocus.displayId(screen)
            quickPanel.controllerOn(screenFocus.displayId(screen))
            controllerLock.onTouch(screen)
        }

        override fun onLift(screen: Screen) = controllerLock.onLift(screen)

        override fun onDirection(button: PadButton, down: Boolean) {
            if (padWatching) onPadDirection(button, down)
        }

        override fun onLid(closed: Boolean) = lidController.onLid(closed)
    }

    // AYN re-creates its pad on a layout switch (also per game) and on sleep; a key held on the old one is never released.
    private val inputDeviceListener = object : InputManager.InputDeviceListener {
        override fun onInputDeviceAdded(deviceId: Int) = Unit

        override fun onInputDeviceChanged(deviceId: Int) = Unit

        override fun onInputDeviceRemoved(deviceId: Int) {
            if (deviceId == heldFrom) forgetHeld()
        }
    }
    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val on = intent.action == Intent.ACTION_SCREEN_ON
            rawInput.setScreenOn(on)
            lidController.onScreen(on)
            if (!on) {
                quickPanel.close(CloseReason.SCREEN_OFF, returnFocus = false)
                forgetHeld()
                joystickCatcher.stop()
            }
        }
    }

    @Volatile
    private var homePackages = emptySet<String>()

    @Volatile
    private var lastAppPackage: String? = null

    /** When [lastAppPackage] came to the front (elapsed ms), for the play timer. */
    @Volatile
    private var frontSince = SystemClock.elapsedRealtime()

    /** Break reminders already shown for the app in front. Main thread. */
    private var breaksShown = 0
    private lateinit var breakNote: FeedbackCue

    /** Once a minute: a break reminder when the play timer's interval has passed for the app in front. */
    private val breakCheck = object : Runnable {
        override fun run() {
            mainHandler.postDelayed(this, BREAK_CHECK_MS)
            val app = lastAppPackage ?: return
            val remind = prefs.panelLayout.pages.firstNotNullOfOrNull { it.widget(WidgetType.PLAY_TIMER) }?.remind ?: return
            val played = SystemClock.elapsedRealtime() - frontSince
            val due = PlayTime.reminderDue(played, remind, breaksShown) ?: return
            breaksShown = due
            val name = runCatching {
                packageManager.getApplicationLabel(packageManager.getApplicationInfo(app, 0)).toString()
            }.getOrDefault(app)
            breakNote.show(getString(R.string.panelBreak, name, PlayTime.format(played)), screenFocus.displayId())
        }
    }

    // OdinTools appearing or disappearing changes the defaults of every overlapping feature.
    private val odinToolsPackageReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.data?.schemeSpecificPart == OdinToolsDetector.PACKAGE) refreshCoexistence()
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        // The event is recycled once this returns, so only its package name may outlive the call.
        val packageName = event.packageName?.toString() ?: return
        // Our own overlays (joystick catcher, quick panel, notes) say nothing about the app in front; they would reset the
        // per-app overrides. Only Thor Tools' own screen counts as an app (it reports its activity's class).
        if (packageName == this.packageName && event.className?.toString() != MainActivity::class.java.name) return
        refreshFocusedDisplay()
        if (packageName in homePackages && ::displayHome.isInitialized) displayHome.onLauncherEvent(event)
        if (::quickPanel.isInitialized) closePanelForNewApp(packageName)
        trackApp(packageName)
        if (shouldIgnore(packageName)) return

        if (overridesDelay) {
            mainHandler.postDelayed({ handleApp(packageName) }, OVERRIDE_DELAY)
        } else {
            handleApp(packageName)
        }
    }

    private fun handleApp(packageName: String) {
        currentForegroundPackage = packageName
        val override = overrides.find { it.packageName == packageName }
        val odinCategories = perAppControlsEnabled
        val appOnTop = override?.bottomScreen != null && actionHost.appsOnScreens()[Display.DEFAULT_DISPLAY] == packageName
        overrideExecutor.execute {
            if (override != null) {
                if (odinCategories) applyOverride(override) else resetOverrides()
                applyScreenRules(override, appOnTop)
            } else {
                resetOverrides()
                resetScreenRules()
            }
        }
    }

    private fun shouldIgnore(packageName: String): Boolean {
        if (overridesEnabled.not()) return true // User disabled overrides globally
        if (::overrides.isInitialized.not()) return true // Got an event before the DB was returning data
        if (ignoredPackages.contains(packageName)) return true // Ignore some system packages
        if (packageName == currentForegroundPackage) return true // No action on duplicate events
        if (currentIme.contains(packageName)) return true // Ignore keyboards popping up

        return false // All good, process event
    }

    /** Another app coming to the front means the panel is no longer about what's on screen. */
    private fun closePanelForNewApp(packageName: String) {
        val over = quickPanel.openedOver ?: return
        if (packageName == over || packageName == this.packageName || ignoredPackages.contains(packageName)) return
        if (currentIme.contains(packageName)) return
        quickPanel.close(CloseReason.OTHER_APP)
    }

    /** The app "Close the current app" acts on. A launcher in front means there is none. */
    private fun trackApp(packageName: String) {
        val app = when {
            packageName in homePackages -> null
            packageName == this.packageName || ignoredPackages.contains(packageName) || currentIme.contains(packageName) -> return
            else -> packageName
        }
        if (app != lastAppPackage) {
            frontSince = SystemClock.elapsedRealtime()
            breaksShown = 0
            app?.let { prefs.recentApps = RecentApps.opened(prefs.recentApps, it) }
        }
        lastAppPackage = app
        useHotkeysForApp()
    }

    override fun onKeyEvent(event: KeyEvent): Boolean {
        status.keysSeen++
        val (consume, note) = decideKey(event)
        serviceKeyLog.add(KeyLogEntry.of(event, note))
        return consume
    }

    /** Whether to swallow [event], and why in a few words (for the Diagnostics key log). */
    private fun decideKey(event: KeyEvent): Pair<Boolean, String> {
        if (!::systemPress.isInitialized) return false to "passed"
        // Keys sent by software (Android's Back action, the root helper's Home key for one screen) come from the virtual
        // keyboard: never a press of the Thor's own buttons.
        if (event.deviceId == KeyCharacterMap.VIRTUAL_KEYBOARD) return false to "passed (injected)"
        if (event.action != KeyEvent.ACTION_DOWN && event.action != KeyEvent.ACTION_UP) return false to "passed"
        val button = PadButton.of(event.keyCode, event.scanCode, xboxLayout(event)) ?: return false to "passed"
        val down = event.action == KeyEvent.ACTION_DOWN
        status.buttonRecorder?.let { record ->
            if (event.repeatCount == 0) record(button, down)
            return true to "recorded (hotkey editor)"
        }
        val newPress = down && event.repeatCount == 0
        // With no hotkey on Home, the open panel answers Home itself: the press would otherwise go Home behind the panel.
        // Each new press decides; its repeats and release are swallowed with it.
        val hotkeysOff = lastAppPackage in hotkeysOffIn
        if (button == PadButton.HOME && newPress) {
            homeForPanel = PanelKeys.answersHome(button, quickPanel.hasController, hotkeysTakeHome = !hotkeysOff && hotkeys.takes(button))
        }
        if (button == PadButton.HOME && homeForPanel) {
            if (!down) {
                homeForPanel = false
                if (!event.isCanceled) panelHome()
            }
            return true to "swallowed (panel Home)"
        }
        if (newPress && button != PadButton.AYN && hotkeysOff) return false to "passed (hotkeys off in this app)"
        val step = hotkeys.onKey(button, down, repeat = event.repeatCount > 0, time = event.eventTime, canceled = event.isCanceled)
        if (newPress && hotkeys.heldSince == event.eventTime) heldFrom = event.deviceId
        carryOut(step)
        // Answer the key first; the catcher's window is a WindowManager call.
        mainHandler.post {
            watchJoystick()
            useWaitingHotkeys()
        }
        return step.consume to describe(step, if (step.consume) "swallowed" else "passed")
    }

    private fun describe(step: HotkeyRecognizer.Step, verb: String): String {
        val ran = step.effects.filterIsInstance<HotkeyRecognizer.Effect.Run>().joinToString { it.hotkey.action.id }
        return when {
            ran.isNotEmpty() -> "$verb (hotkey: $ran)"
            step.consume -> "$verb (hotkey)"
            else -> verb
        }
    }

    /**
     * Reads Android's focused display on the IO scope after a window change: one read at a time, at most every 500 ms,
     * and once more at the end of a burst of changes, so the last one is never missed. Needed while direction combos
     * exist, and while the quick panel is open (an app starting elsewhere takes the controller from it).
     */
    private fun refreshFocusedDisplay() {
        if (!hotkeys.usesMotion && !quickPanel.isOpen) return
        mainHandler.removeCallbacks(focusReadAgain)
        val now = SystemClock.uptimeMillis()
        if (now - lastFocusRead < FOCUS_READ_GAP_MS || !readingFocus.compareAndSet(false, true)) {
            // Never sooner than a moment from now: a read still under way must not make this spin.
            mainHandler.postAtTime(focusReadAgain, maxOf(lastFocusRead + FOCUS_READ_GAP_MS, now + FOCUS_READ_RETRY_MS))
            return
        }
        lastFocusRead = now
        scope.launch {
            try {
                CloseTarget.parseFocusedDisplay(executor.executeAsRoot("dumpsys input | grep -m1 FocusedDisplayId").getOrNull())?.let {
                    focusedDisplay = it
                    mainHandler.post { quickPanel.controllerOn(it) }
                }
            } finally {
                readingFocus.set(false)
            }
        }
    }

    /**
     * A D-pad direction or stick flick while a button with such combos is held: [note] "read" when the root helper read
     * it raw, "caught" when the joystick catcher or the panel did.
     */
    private fun onDirection(button: PadButton, down: Boolean, time: Long, note: String = "caught") {
        if (!hotkeys.wantsMotion) return
        val step = hotkeys.onMotion(button, down, time)
        carryOut(step)
        serviceKeyLog.add(KeyLogEntry.motion(button.id, down, describe(step, note)))
        watchJoystick()
    }

    private fun onPadDirection(button: PadButton, down: Boolean) = onDirection(button, down, SystemClock.uptimeMillis(), note = "read")

    /** The held button's release won't come (its device went away, the screen went off): nothing counts as held now. */
    private fun forgetHeld() {
        heldFrom = null
        if (!hotkeys.holding) return
        hotkeys.forgetHeld()
        watchJoystick()
        useWaitingHotkeys()
    }

    /**
     * While a held button has D-pad or stick combos, the root helper reads the pad raw from the press on: the joystick
     * catcher only gets the controller a frame or two after it goes up, too late for a quick flick. The catcher goes up
     * 150 ms after the press to keep that motion from the game, and reads it itself only without the helper's pad. Never
     * while the quick panel has the controller (the motion comes to the panel then, see [PanelMotion]) or the hotkey
     * editor records.
     */
    private fun watchJoystick() {
        mainHandler.removeCallbacks(catcherDue)
        val since = hotkeys.heldSince
        val wanted = since != null && hotkeys.wantsMotion && status.buttonRecorder == null
        val raw = wanted && rawInput.padReady
        if (raw != padWatching) {
            padWatching = raw
            rawInput.watchPad(raw)
        }
        if (since != null && wanted && !quickPanel.hasController) {
            // A plain tap of the button never takes window focus from the game.
            val due = since + CATCH_AFTER_MS
            if (SystemClock.uptimeMillis() < due) {
                mainHandler.postAtTime(catcherDue, due)
                return
            }
            val display = controllerLock.lockedTo?.let(screenFocus::displayId) ?: focusedDisplay ?: screenFocus.displayId()
            joystickCatcher.start(display, since)
        } else {
            joystickCatcher.stop()
        }
    }

    /**
     * Home from the open panel: close it, handing the controller back, and go Home the way the Home button does on the
     * screen it was opened from (where the user was).
     */
    private fun panelHome() {
        val target = quickPanel.openedFrom
        quickPanel.close(CloseReason.HOME)
        displayHome.systemHome(target)
    }

    /** Joystick motion reaching the open panel: the hotkeys' while a held button has D-pad or stick combos. */
    private fun onPanelMotion(event: MotionEvent): Boolean = panelMotion.take(event, hotkeys.heldSince?.takeIf { hotkeys.wantsMotion })

    /** AYN's own pad in its Xbox layout reports face buttons by position. */
    private fun xboxLayout(event: KeyEvent): Boolean =
        event.device?.let { it.vendorId == AYN_VENDOR && it.productId == AYN_XBOX_PRODUCT } == true

    /** The hotkeys' effects, routed around the open quick panel ([PanelKeys.route]). */
    private fun carryOut(step: HotkeyRecognizer.Step) {
        val panel = quickPanel.takeIf { it.isOpen }?.let {
            PanelKeys.Panel(hasController = it.hasController, openedFromTop = it.openedFrom == Display.DEFAULT_DISPLAY)
        }
        PanelKeys.route(step.effects, panel).forEach { out ->
            when (out) {
                PanelKeys.Out.PanelBack -> quickPanel.back()
                PanelKeys.Out.PanelHome -> panelHome()
                is PanelKeys.Out.Hotkeys -> {
                    carryOut(out.effect)
                    quickPanel.refreshSoon()
                }
                is PanelKeys.Out.AfterClosing -> if (quickPanel.closesFor(CloseReason.HOTKEY)) {
                    if (out.effect is HotkeyRecognizer.Effect.Run) joystickCatcher.finishPress()
                    quickPanel.close(CloseReason.HOTKEY)
                    mainHandler.postDelayed({ carryOut(out.effect) }, out.delayMs)
                } else {
                    // The panel stays open (only AYN closes it): the action runs now, "here" already pinned to where
                    // the panel was opened from.
                    carryOut(out.effect)
                    quickPanel.refreshSoon()
                }
            }
        }
        mainHandler.removeCallbacks(hotkeyTimer)
        step.timerAt?.let { mainHandler.postAtTime(hotkeyTimer, it) }
    }

    private fun carryOut(effect: HotkeyRecognizer.Effect) {
        when (effect) {
            is HotkeyRecognizer.Effect.Run -> with(effect.hotkey) {
                if (MotionWatch.endsWatch(action)) joystickCatcher.finishPress()
                actionRunner.run(ActionCall(action, arg, feedback = showText, alsoLock = lock, alsoCleanMemory = cleanMemory))
            }
            is HotkeyRecognizer.Effect.GiveBack -> systemPress.press(effect.button, effect.presses)
        }
    }

    /**
     * The hotkeys for the app in front ([HotkeyList.forApp]). While a button is held the change waits for its release, so
     * a press never ends on another list than it started on.
     */
    private fun useHotkeysForApp() {
        scopeWaiting = hotkeys.holding
        if (!scopeWaiting) useHotkeys(HotkeyList.forApp(allHotkeys, lastAppPackage))
    }

    /** After a key, a timer or a lost release: the app's list once nothing is held any more. */
    private fun useWaitingHotkeys() {
        if (scopeWaiting) useHotkeysForApp()
    }

    /** A new list builds a new recognizer; releases the old one still had to swallow carry over (a held Home's above all). */
    private fun useHotkeys(list: List<Hotkey>) {
        if (list == hotkeyList) return
        hotkeyList = list
        mainHandler.removeCallbacks(hotkeyTimer)
        joystickCatcher.stop()
        val pending = hotkeys.releasesToSwallow()
        hotkeys = HotkeyRecognizer(list).also { it.swallowReleases(pending) }
        watchJoystick()
    }

    /** Back for the app (Android's Back). */
    private fun pressBack() {
        performGlobalAction(GLOBAL_ACTION_BACK)
    }

    /** Home on [displayId] the way Android does it for that screen: a Home key for that display, from the root helper. */
    private fun homeKey(displayId: Int, onFailed: () -> Unit): Boolean {
        val args = listOf(KeyCharacterMap.VIRTUAL_KEYBOARD, KeyEvent.KEYCODE_HOME, 0, InputDevice.SOURCE_KEYBOARD, displayId, HOME_PRESS_MS)
        return rawInput.command("key", *args.map(Int::toString).toTypedArray()) { ok, _ -> if (!ok) onFailed() }
    }

    private fun applyOverride(override: AppOverrideEntity) {
        // This check makes sure that we don't override the "defaults" when switching between apps with overrides
        if (!hasSetOverride) {
            savedControllerStyle = ControllerStyle.getStyle(executor)
            savedL2R2Style = L2R2Style.getStyle(executor)
            savedPerfMode = PerfMode.getMode(executor)
            savedFanMode = FanMode.getMode(executor)
        }

        // Avoid conflicts with Video Output Override
        if (!videoOutputOverrideEnabled || !videoOutputReceiver.overrideEnabled) {
            ControllerStyle.getById(override.controllerStyle).takeIf {
                it != Unknown
            }?.enable(executor) ?: run {
                // Reset to default if we switch between override and NoChange app
                savedControllerStyle?.enable(executor)
            }

            L2R2Style.getById(override.l2R2Style).takeIf {
                it != L2R2Style.Unknown
            }?.enable(executor) ?: run {
                // Reset to default if we switch between override and NoChange app
                savedL2R2Style?.enable(executor)
            }
        }

        PerfMode.getById(override.perfMode).takeIf {
            it != PerfMode.Unknown
        }?.enable(executor) ?: run {
            // Reset to default if we switch between override and NoChange app
            savedPerfMode?.enable(executor)
        }

        FanMode.getById(override.fanMode).takeIf {
            it != FanMode.Unknown
        }?.enable(executor) ?: run {
            // Reset to default if we switch between override and NoChange app
            savedFanMode?.enable(executor)
        }

        hasSetOverride = true
    }

    private fun resetOverrides() {
        if (!hasSetOverride) return

        savedControllerStyle?.enable(executor)
        savedControllerStyle = null

        savedL2R2Style?.enable(executor)
        savedL2R2Style = null

        savedPerfMode?.enable(executor)
        savedPerfMode = null

        savedFanMode?.enable(executor)
        savedFanMode = null

        hasSetOverride = false
    }

    /** Refresh rate and the bottom screen, saved before the first change and put back exactly when no profile asks. */
    private fun applyScreenRules(override: AppOverrideEntity, appOnTop: Boolean) {
        val hz = AppRefreshRate.byId(override.refreshRate)?.hz
        if (hz != null) {
            if (savedRefreshRate == null) savedRefreshRate = RefreshRate.save(executor)
            RefreshRate.apply(executor, hz)
        } else {
            restoreRefreshRate()
        }
        val mode = ScreenMode.forRule(BottomScreenRule.byId(override.bottomScreen), appOnTop)
        if (mode != null) {
            if (savedScreenMode == null) savedScreenMode = screenMode()
            executor.executeAsRoot("settings put system ${ScreenMode.KEY} $mode")
        } else {
            restoreScreenMode()
        }
    }

    private fun resetScreenRules() {
        restoreRefreshRate()
        restoreScreenMode()
    }

    private fun restoreRefreshRate() {
        savedRefreshRate?.let { RefreshRate.restore(executor, it) }
        savedRefreshRate = null
    }

    private fun restoreScreenMode() {
        savedScreenMode?.let { executor.executeAsRoot("settings put system ${ScreenMode.KEY} $it") }
        savedScreenMode = null
    }

    private fun screenMode(): Int = runCatching { Settings.System.getInt(contentResolver, ScreenMode.KEY, ScreenMode.BOTH_ON) }
        .getOrDefault(ScreenMode.BOTH_ON)

    private fun applyChargeLimit(newValue: Boolean) {
        if (newValue && !chargeLimitEnabled) {
            val intentFilter = IntentFilter().apply {
                BatteryLevelReceiver.ALLOWED_INTENTS.forEach { action ->
                    addAction(action)
                }
            }
            registerReceiver(batteryLevelReceiver, intentFilter, RECEIVER_NOT_EXPORTED)
        } else if (!newValue && chargeLimitEnabled) {
            unregisterReceiver(batteryLevelReceiver)
        }
        chargeLimitEnabled = newValue
    }

    private fun applyVideoOutputOverride(newValue: Boolean) {
        if (newValue && !videoOutputOverrideEnabled) {
            val intentFilter = IntentFilter().apply {
                VideoOutputReceiver.ALLOWED_INTENTS.forEach { action ->
                    addAction(action)
                }
            }
            registerReceiver(videoOutputReceiver, intentFilter, RECEIVER_EXPORTED)
        } else if (!newValue && videoOutputOverrideEnabled) {
            unregisterReceiver(videoOutputReceiver)
        }
        videoOutputOverrideEnabled = newValue
    }

    private fun applyPerAppControls(newValue: Boolean) {
        if (!newValue) {
            overrideExecutor.execute { resetOverrides() }
            currentForegroundPackage = ""
        }
        perAppControlsEnabled = newValue
    }

    private fun refreshCoexistence() {
        applyPerAppControls(prefs.isEnabled(Overlap.PER_APP_CONTROLS))
        applyChargeLimit(prefs.chargeLimitEnabled)
        applyVideoOutputOverride(prefs.videoOutputOverrideEnabled)
    }

    override fun onInterrupt() {
        // Nothing here
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        // The XML asks for key events too, but some firmware only delivers them once the flag is set at runtime.
        serviceInfo = serviceInfo.apply {
            flags =
                flags or AccessibilityServiceInfo.FLAG_REQUEST_FILTER_KEY_EVENTS or
                AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
        }
        status.attach(this)
        KeepAliveNotification.show(this)
        // The bottom screen runs a secondary home, which can be another package than the main launcher.
        homePackages = listOf(Intent.CATEGORY_HOME, Intent.CATEGORY_SECONDARY_HOME).flatMap { category ->
            packageManager.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(category), 0).map { it.activityInfo.packageName }
        }.toSet()

        imeObserver.onChange(false)
        contentResolver.registerContentObserver(
            Settings.Secure.getUriFor(Settings.Secure.DEFAULT_INPUT_METHOD),
            false,
            imeObserver,
        )

        overridesEnabled = prefs.appOverridesEnabled
        prefs.observeAppOverrideEnabledState(
            { overridesEnabled = it },
            { overridesDelay = it },
        )

        applyChargeLimit(prefs.chargeLimitEnabled)
        prefs.observeChargeLimitEnabledState {
            applyChargeLimit(it)
        }

        applyVideoOutputOverride(prefs.videoOutputOverrideEnabled)
        prefs.observeVideoOutputOverrideEnabledState {
            applyVideoOutputOverride(it)
        }

        applyPerAppControls(prefs.isEnabled(Overlap.PER_APP_CONTROLS))
        prefs.observeOverlaps(this) { overlap ->
            if (overlap == Overlap.PER_APP_CONTROLS) applyPerAppControls(prefs.isEnabled(overlap))
        }

        screenFocus = ScreenFocus(getSystemService(DisplayManager::class.java))
        displayHome = DisplayHome(this, executor, screenFocus, scope, homeKey = ::homeKey)
        focusMover = FocusMover(this, executor, scope)
        joystickCatcher = JoystickCatcher(this) { button, down, time -> if (!padWatching) onDirection(button, down, time) }
        stayAwake = StayAwake(this)
        controllerLock = ControllerLock(this, executor, focusMover, screenFocus, scope, paused = { quickPanel.isOpen })
        controllerLock.start()
        actionRunner = ActionRunner(this, executor, prefs, scope, actionHost)
        quickPanel = QuickPanel(
            service = this,
            executor = executor,
            prefs = prefs,
            screenFocus = screenFocus,
            scope = scope,
            helper = { name, args, onResult -> rawInput.command(name, *args.toTypedArray(), onResult = onResult) },
            stayAwakeOn = { stayAwake.isOn },
            lockedTo = { controllerLock.lockedTo },
            thorToolsShownOn = { status.appShownOn },
            onMotion = ::onPanelMotion,
            frontApp = { lastAppPackage?.let { FrontApp(it, frontSince) } },
        ) { call -> actionRunner.run(call) }
        status.toggleQuickPanel = { mainHandler.post { quickPanel.toggle(lastAppPackage) } }
        breakNote = FeedbackCue(this, prefs, hideAfterMs = BREAK_NOTE_MS, windowTitle = "ThorToolsBreakNote")
        mainHandler.postDelayed(breakCheck, BREAK_CHECK_MS)
        status.onAccessChanged = { mainHandler.post { KeepAliveNotification.show(this) } }
        rawInput = RawInputClient(this, executor, rawInputListener)
        status.rawInput = rawInput
        val lidNote = FeedbackCue(this, prefs, hideAfterMs = LID_NOTE_MS, windowTitle = "ThorToolsLidNote")
        lidController = LidController(
            context = this,
            executor = executor,
            prefs = prefs,
            helper = { name, args, onResult -> rawInput.command(name, *args.toTypedArray(), onResult = onResult) },
            closeBackground = { actionRunner.run(ActionCall(ThorAction.CLEAR_BACKGROUND)) },
            sleepNow = { performGlobalAction(GLOBAL_ACTION_LOCK_SCREEN) },
            notRestored = { items -> lidNote.show(LidText.notRestored(this, items), Display.DEFAULT_DISPLAY) },
            frontApp = { lastAppPackage },
        )
        systemPress = SystemPress(
            service = this,
            goHome = { displayHome.systemHome() },
            openAynDrawer = { scope.launch { AynHooks.openDrawer(executor) } },
        )
        getSystemService(InputManager::class.java).registerInputDeviceListener(inputDeviceListener, mainHandler)
        scope.launch {
            prefs.hotkeyChanges().collect { list ->
                mainHandler.post {
                    allHotkeys = list
                    useHotkeysForApp()
                }
            }
        }
        scope.launch { prefs.hotkeyOffAppsChanges().collect { apps -> hotkeysOffIn = apps } }
        registerReceiver(
            screenReceiver,
            IntentFilter().apply {
                addAction(Intent.ACTION_SCREEN_ON)
                addAction(Intent.ACTION_SCREEN_OFF)
            },
            RECEIVER_NOT_EXPORTED,
        )
        // Swap screens, brightness, Home per screen and the controller lock need the helper whenever the service runs.
        rawInput.start()
        lidController.start()
        val packageFilter = IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addAction(Intent.ACTION_PACKAGE_FULLY_REMOVED)
            addDataScheme("package")
        }
        registerReceiver(odinToolsPackageReceiver, packageFilter, RECEIVER_NOT_EXPORTED)

        if (prefs.chargeAlertEnabled) chargeMonitor.start()
        prefs.observeChargeAlert(
            onEnabled = { if (it) chargeMonitor.start() else chargeMonitor.stop() },
            onSensitivity = chargeMonitor::setSensitivity,
        )

        scope.launch {
            appOverrideDao.getAll()
                .flowOn(Dispatchers.IO)
                .collect { overrides ->
                    this@ForegroundAppWatcherService.overrides = overrides
                }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        status.detach(this)
        if (!status.connected) KeepAliveNotification.hide(this)
        overrideExecutor.shutdown()
        // Android can destroy a service that never got as far as onServiceConnected.
        if (::rawInput.isInitialized) {
            quickPanel.close(CloseReason.SERVICE_STOPPED, returnFocus = false)
            stayAwake.off()
            systemPress.stop()
            joystickCatcher.stop()
            getSystemService(InputManager::class.java).unregisterInputDeviceListener(inputDeviceListener)
            rawInput.stop()
            lidController.stop()
            unregisterReceiver(screenReceiver)
            unregisterReceiver(odinToolsPackageReceiver)
        }
        applyChargeLimit(false)
        applyVideoOutputOverride(false)
        job.cancel()
        contentResolver.unregisterContentObserver(imeObserver)
        prefs.removeAppOverrideEnabledObserver()
        prefs.removeChargeLimitEnabledObserver()
        prefs.removeVideoOutputOverrideEnabledObserver()
        prefs.removeOverlapObserver(this)
        prefs.removeChargeAlertObserver()
        mainHandler.removeCallbacksAndMessages(null)
        chargeMonitor.stop()
    }

    companion object {
        private const val HOME_PRESS_MS = 30
        private const val AYN_VENDOR = 0x2020
        private const val AYN_XBOX_PRODUCT = 0x0112
        private const val FOCUS_READ_GAP_MS = 500L
        private const val FOCUS_READ_RETRY_MS = 100L
        private const val CATCH_AFTER_MS = 150L
        private const val LID_NOTE_MS = 8_000L
        private const val BREAK_NOTE_MS = 10_000L
        private const val BREAK_CHECK_MS = 60_000L

        private val ignoredPackages = listOf(
            "com.android.launcher3",
            "com.odin2.gameassistant",
            "com.odin.gameassistant",
            "com.odin.dualscreen.assistant",
            "com.android.systemui",
            "android",
        )

        /** Dialogs shown inside another app's task: the app underneath is still the one on that screen. */
        private val dialogPackages = setOf("com.android.permissioncontroller", "com.google.android.permissioncontroller", "android")

        const val OVERRIDE_DELAY = 500L
    }
}
