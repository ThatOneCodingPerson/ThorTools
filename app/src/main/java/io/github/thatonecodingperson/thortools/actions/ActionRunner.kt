package io.github.thatonecodingperson.thortools.actions

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.app.ActivityOptions
import android.content.Intent
import android.graphics.Path
import android.hardware.display.DisplayManager
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.DisplayMetrics
import android.view.Display
import androidx.annotation.StringRes
import io.github.thatonecodingperson.thortools.R
import io.github.thatonecodingperson.thortools.data.SharedPrefsRepo
import io.github.thatonecodingperson.thortools.hotkeys.AppLaunch
import io.github.thatonecodingperson.thortools.hotkeys.CloseAppArg
import io.github.thatonecodingperson.thortools.hotkeys.LaunchScreen
import io.github.thatonecodingperson.thortools.input.AppMove
import io.github.thatonecodingperson.thortools.input.BackgroundTasks
import io.github.thatonecodingperson.thortools.input.LockPolicy
import io.github.thatonecodingperson.thortools.input.Screen
import io.github.thatonecodingperson.thortools.input.ScreenSwap
import io.github.thatonecodingperson.thortools.input.SwapResult
import io.github.thatonecodingperson.thortools.models.ControllerStyle
import io.github.thatonecodingperson.thortools.models.FanMode
import io.github.thatonecodingperson.thortools.models.L2R2Style
import io.github.thatonecodingperson.thortools.models.PerfMode
import io.github.thatonecodingperson.thortools.models.RefreshRate
import io.github.thatonecodingperson.thortools.panel.StatsParser
import io.github.thatonecodingperson.thortools.tools.AynHooks
import io.github.thatonecodingperson.thortools.tools.ShellExecutor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File

/** What [ActionRunner] needs from the accessibility service. */
interface ActionHost {
    val foregroundPackage: String?

    /** The app showing on each display (display id to package), from the window list. Launchers don't count. */
    fun appsOnScreens(): Map<Int, String>

    fun bottomDisplayId(): Int?

    fun controllerDisplayId(): Int

    /** Home on [displayId], or on the screen that has the controller when null. */
    fun goHome(displayId: Int?)

    /** Home on a screen an app just moved away from, when that can't disturb the other screen. */
    fun homeAfterMove(displayId: Int)

    /** Home on both screens, the bottom one first. */
    fun goHomeBoth(bottomDisplayId: Int)

    fun togglePanel()

    fun moveController(displayId: Int, onDone: (Boolean) -> Unit)

    /** Where the controller is locked, if anywhere. */
    val lockedTo: Screen?

    /** Locks to [target] or unlocks with null; [onDone] gets the lock in place afterwards. */
    fun setLock(target: Screen?, onDone: (Screen?) -> Unit)

    fun toggleStayAwake(): Boolean

    /** Back for the app (Android's Back). */
    fun pressBack()

    /** The display the controller was on when the open quick panel opened; null while it is closed. */
    fun panelOpenedFrom(): Int?

    /**
     * Packages "Close background apps" leaves alone: the launchers, Thor Tools itself (the service runs in its process),
     * the keyboard, system and AYN apps, and the apps on the screens.
     */
    fun keepWhenClosingBackground(): Set<String>

    /** Runs a root input helper command; false when the helper isn't connected. */
    fun helperCommand(name: String, vararg args: String, onResult: (Boolean, String) -> Unit): Boolean

    /** OLED Safety's refresh sweep on its chosen screens. */
    fun refreshScreens()
}

/** Performs [ThorAction]s for the accessibility service. Root calls run on [scope], never on the key event thread. */
class ActionRunner(
    private val service: AccessibilityService,
    private val executor: ShellExecutor,
    private val prefs: SharedPrefsRepo,
    private val scope: CoroutineScope,
    private val host: ActionHost,
) {
    private val mainHandler = Handler(Looper.getMainLooper())
    private val audio = service.getSystemService(AudioManager::class.java)
    private val cue = FeedbackCue(service, prefs)

    /** Says what an action did: [done] only when the call asks for a message, [problem] always. Any thread. */
    private inner class Say(private val enabled: Boolean) {
        fun done(text: String, displayId: Int? = null) {
            if (enabled) cue.show(text, displayId ?: host.controllerDisplayId())
        }

        fun done(@StringRes text: Int, displayId: Int? = null) = done(service.getString(text), displayId)

        fun problem(text: String) = cue.show(text, host.controllerDisplayId())

        fun problem(@StringRes text: Int) = problem(service.getString(text))
    }

    fun run(call: ActionCall) {
        val say = Say(call.feedback)
        val action = call.action
        when (action) {
            ThorAction.BACK -> host.pressBack().also { say.done(action.label) }
            ThorAction.HOME -> host.goHome(null).also { say.done(action.label) }
            ThorAction.HOME_TOP -> host.goHome(Display.DEFAULT_DISPLAY).also { say.done(action.label, Display.DEFAULT_DISPLAY) }
            ThorAction.HOME_BOTTOM -> withBottom(say) { bottom ->
                host.goHome(bottom)
                say.done(action.label, bottom)
            }
            ThorAction.HOME_BOTH -> withBottom(say) { bottom ->
                host.goHomeBoth(bottom)
                say.done(action.label)
            }
            ThorAction.OPEN_QUICK_PANEL -> host.togglePanel()
            ThorAction.RECENTS -> global(AccessibilityService.GLOBAL_ACTION_RECENTS, action, say)
            ThorAction.NOTIFICATIONS -> global(AccessibilityService.GLOBAL_ACTION_NOTIFICATIONS, action, say)
            ThorAction.QUICK_SETTINGS -> global(AccessibilityService.GLOBAL_ACTION_QUICK_SETTINGS, action, say)
            ThorAction.SCREENSHOT -> global(AccessibilityService.GLOBAL_ACTION_TAKE_SCREENSHOT, action, say)
            ThorAction.LOCK_SCREEN -> service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_LOCK_SCREEN)
            ThorAction.LAUNCH_APP -> launch(call.arg, say)
            ThorAction.SWAP_SCREENS -> swapScreens(follow = false, say)
            ThorAction.CLOSE_OTHER_SCREEN_APP -> closeOtherScreenApp(say)
            ThorAction.CONTROLLER_TO_TOP -> moveController(Screen.TOP, alsoLock = call.alsoLock, say)
            ThorAction.CONTROLLER_TO_BOTTOM -> moveController(Screen.BOTTOM, alsoLock = call.alsoLock, say)
            ThorAction.TOGGLE_CONTROLLER_LOCK -> lock(LockPolicy.toggled(host.lockedTo, Screen.TOP), say)
            ThorAction.LOCK_CONTROLLER_BOTTOM -> withBottom(say) { lock(LockPolicy.toggled(host.lockedTo, Screen.BOTTOM), say) }
            ThorAction.LOCK_CONTROLLER_HERE -> {
                val here = if (host.controllerDisplayId() == Display.DEFAULT_DISPLAY) Screen.TOP else Screen.BOTTOM
                lock(LockPolicy.toggledHere(host.lockedTo, here), say)
            }
            ThorAction.TOGGLE_STAY_AWAKE -> say.done(
                if (host.toggleStayAwake()) R.string.actionResultStayAwakeOn else R.string.actionResultStayAwakeOff,
            )
            ThorAction.SWIPE_UP -> swipe(0f, -1f, action, say)
            ThorAction.SWIPE_DOWN -> swipe(0f, 1f, action, say)
            ThorAction.SWIPE_LEFT -> swipe(-1f, 0f, action, say)
            ThorAction.SWIPE_RIGHT -> swipe(1f, 0f, action, say)
            ThorAction.BRIGHTER -> brightness(both = true, bottomOnly = false, up = true, say = say)
            ThorAction.DIMMER -> brightness(both = true, bottomOnly = false, up = false, say = say)
            ThorAction.TOP_BRIGHTER -> brightness(both = false, bottomOnly = false, up = true, say = say)
            ThorAction.TOP_DIMMER -> brightness(both = false, bottomOnly = false, up = false, say = say)
            ThorAction.BOTTOM_BRIGHTER -> brightness(both = false, bottomOnly = true, up = true, say = say)
            ThorAction.BOTTOM_DIMMER -> brightness(both = false, bottomOnly = true, up = false, say = say)
            ThorAction.LOUDER -> volume(AudioManager.ADJUST_RAISE, action, say)
            ThorAction.QUIETER -> volume(AudioManager.ADJUST_LOWER, action, say)
            ThorAction.CLOSE_APP -> CloseAppArg.decode(call.arg)?.let { closeAppOn(it, say) } ?: scope.launch { runPrivileged(call, say) }
            ThorAction.CLEAR_BACKGROUND -> closeBackground(call.alsoCleanMemory, say)
            ThorAction.REFRESH_SCREENS -> host.refreshScreens()
            else -> scope.launch { runPrivileged(call, say) }
        }
    }

    /**
     * Closes the app showing on [screen] with `am force-stop`, found from the window list, off the main thread. "Here"
     * is the screen the open panel was opened from, else the screen Android routes the controller to.
     */
    private fun closeAppOn(screen: LaunchScreen, say: Say) {
        val fixed = when (screen) {
            LaunchScreen.TOP -> Display.DEFAULT_DISPLAY
            LaunchScreen.BOTTOM -> host.bottomDisplayId() ?: return say.problem(R.string.actionResultNoSecondScreen)
            LaunchScreen.HERE -> host.panelOpenedFrom()
        }
        scope.launch {
            val display = fixed ?: focusedDisplay() ?: host.controllerDisplayId()
            val app = CloseTarget.appToClose(host.appsOnScreens(), display, service.packageName) { it.matches(PACKAGE_NAME) }
                ?: return@launch say.problem(R.string.actionResultNothingToCloseHere)
            executor.executeAsRoot("am force-stop $app")
            say.done(service.getString(R.string.actionResultClosed, appLabel(app)), display)
        }
    }

    /** The display Android routes the controller to. A root call: never on the main thread. */
    private fun focusedDisplay(): Int? =
        CloseTarget.parseFocusedDisplay(executor.executeAsRoot("dumpsys input | grep -m1 FocusedDisplayId").getOrNull())

    private fun global(globalAction: Int, action: ThorAction, say: Say) {
        service.performGlobalAction(globalAction)
        say.done(action.label)
    }

    private fun volume(direction: Int, action: ThorAction, say: Say) {
        audio.adjustStreamVolume(AudioManager.STREAM_MUSIC, direction, AudioManager.FLAG_SHOW_UI)
        say.done(action.label)
    }

    /** [arg] is an encoded [AppLaunch]: the app, and the screen it opens on. */
    private fun launch(arg: String?, say: Say) {
        val target = AppLaunch.decode(arg) ?: return
        val intent = service.packageManager.getLaunchIntentForPackage(target.packageName)
            ?: return say.problem(R.string.actionResultFailed)
        val displayId = when (target.screen) {
            LaunchScreen.HERE -> host.controllerDisplayId()
            LaunchScreen.TOP -> Display.DEFAULT_DISPLAY
            LaunchScreen.BOTTOM -> host.bottomDisplayId() ?: return say.problem(R.string.actionResultNoSecondScreen)
        }
        val options = ActivityOptions.makeBasic().setLaunchDisplayId(displayId).toBundle()
        service.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK), options)
        say.done(appLabel(target.packageName).toString(), displayId)
    }

    private fun withBottom(say: Say, block: (Int) -> Unit) {
        host.bottomDisplayId()?.let(block) ?: say.problem(R.string.actionResultNoSecondScreen)
    }

    /** Sends the controller to [target]; see [LockPolicy.afterMove] for how a lock follows. */
    private fun moveController(target: Screen, alsoLock: Boolean, say: Say) {
        val display = if (target == Screen.TOP) Display.DEFAULT_DISPLAY else host.bottomDisplayId()
        if (display == null) return say.problem(R.string.actionResultNoSecondScreen)
        val lockAfter = LockPolicy.afterMove(host.lockedTo, target, alsoLock)
        if (alsoLock) return lock(lockAfter, say)
        val move = {
            host.moveController(display) { moved ->
                if (moved) {
                    say.done(if (target == Screen.TOP) R.string.feedbackControllerTop else R.string.feedbackControllerBottom, display)
                } else {
                    say.problem(R.string.actionResultFailed)
                }
            }
        }
        // A lock on the other screen would pull the controller straight back, so it goes first.
        if (lockAfter != host.lockedTo) host.setLock(lockAfter) { move() } else move()
    }

    private fun lock(target: Screen?, say: Say) {
        host.setLock(target) { result ->
            when {
                target == null -> say.done(R.string.feedbackUnlocked)
                result == null -> say.problem(R.string.feedbackLockFailed)
                result == Screen.TOP -> say.done(R.string.feedbackLockedTop, Display.DEFAULT_DISPLAY)
                else -> say.done(R.string.feedbackLockedBottom, host.bottomDisplayId())
            }
        }
    }

    private fun swapScreens(follow: Boolean, say: Say) = withBottom(say) { bottom ->
        val apps = host.appsOnScreens().filterValues { it.matches(PACKAGE_NAME) }
        val (result, moves) = ScreenSwap.plan(apps[Display.DEFAULT_DISPLAY], apps[bottom], Display.DEFAULT_DISPLAY, bottom)
        when (result) {
            SwapResult.NOTHING -> return@withBottom say.problem(R.string.actionResultNothingToSwap)
            SwapResult.SAME_APP -> return@withBottom say.problem(R.string.actionResultSameApp)
            else -> Unit
        }
        val args = moves.flatMap { listOf(it.packageName, it.fromDisplay.toString(), it.toDisplay.toString()) }
        val sent = host.helperCommand("move", *args.toTypedArray()) { ok, _ ->
            if (!ok) return@helperCommand say.problem(R.string.actionResultFailed)
            // A screen left empty would show whatever lies beneath in its back stack; Home is cleaner where it's safe.
            if (moves.size == 1) host.homeAfterMove(moves.single().fromDisplay)
            watchMoved(moves)
            if (follow && moves.size == 1) {
                // A moment later, so the app is on its new screen when the controller gets there.
                val target = if (moves.single().toDisplay == Display.DEFAULT_DISPLAY) Screen.TOP else Screen.BOTTOM
                mainHandler.postDelayed({ moveController(target, alsoLock = false, say = Say(false)) }, FOLLOW_DELAY_MS)
            }
            say.done(
                when (result) {
                    SwapResult.SWAPPED -> R.string.actionResultSwapped
                    SwapResult.MOVED_DOWN -> R.string.actionResultMovedDown
                    else -> R.string.actionResultMovedUp
                },
                moves.last().toDisplay,
            )
        }
        if (!sent) say.problem(R.string.actionResultNeedsHelper)
    }

    /**
     * A launcher can answer its screen changing by bringing itself forward over the app that just moved there. For a
     * few seconds, such an app is brought back to the front the way Recent apps would (no restart).
     */
    private fun watchMoved(moves: List<AppMove>) {
        MOVE_WATCH_MS.forEach { delay ->
            mainHandler.postDelayed({
                val apps = host.appsOnScreens()
                moves.filter { apps[it.toDisplay] == null }.forEach { move ->
                    host.helperCommand("front", move.packageName, move.toDisplay.toString()) { _, _ -> }
                }
            }, delay)
        }
    }

    private fun closeOtherScreenApp(say: Say) = withBottom(say) { bottom ->
        val other = if (host.controllerDisplayId() == Display.DEFAULT_DISPLAY) bottom else Display.DEFAULT_DISPLAY
        val app = host.appsOnScreens()[other]?.takeIf { it.matches(PACKAGE_NAME) }
            ?: return@withBottom say.problem(R.string.actionResultNothingToClose)
        val sent = host.helperCommand("close", app, other.toString()) { ok, text ->
            when {
                !ok -> say.problem(R.string.actionResultFailed)
                text == "none" -> say.problem(R.string.actionResultNothingToClose)
                else -> say.done(service.getString(R.string.actionResultClosed, appLabel(app)), other)
            }
        }
        if (!sent) say.problem(R.string.actionResultNeedsHelper)
    }

    private fun brightness(both: Boolean, bottomOnly: Boolean, up: Boolean, say: Say) {
        val bottom = host.bottomDisplayId()
        val displays = when {
            both -> listOfNotNull(Display.DEFAULT_DISPLAY, bottom)
            bottomOnly -> listOfNotNull(bottom)
            else -> listOf(Display.DEFAULT_DISPLAY)
        }
        if (displays.isEmpty()) return say.problem(R.string.actionResultNoSecondScreen)
        val sent = host.helperCommand("brightness", displays.joinToString(","), if (up) "up" else "down") { ok, text ->
            if (ok) {
                say.done(service.getString(R.string.actionResultBrightness, text.toIntOrNull() ?: 0), displays.first())
            } else {
                say.problem(R.string.actionResultFailed)
            }
        }
        if (!sent) say.problem(R.string.actionResultNeedsHelper)
    }

    /** A swipe through the middle of the screen that has the controller, as if a finger did it. */
    private fun swipe(dx: Float, dy: Float, action: ThorAction, say: Say) {
        val displayId = host.controllerDisplayId()
        val display = service.getSystemService(DisplayManager::class.java).getDisplay(displayId) ?: return
        val metrics = DisplayMetrics().also {
            @Suppress("DEPRECATION")
            display.getRealMetrics(it)
        }
        val centreX = metrics.widthPixels / 2f
        val centreY = metrics.heightPixels / 2f
        val reach = minOf(metrics.widthPixels, metrics.heightPixels) * SWIPE_REACH
        val path = Path().apply {
            moveTo(centreX - dx * reach, centreY - dy * reach)
            lineTo(centreX + dx * reach, centreY + dy * reach)
        }
        val gesture = GestureDescription.Builder()
            .setDisplayId(displayId)
            .addStroke(GestureDescription.StrokeDescription(path, 0, SWIPE_MS))
            .build()
        service.dispatchGesture(gesture, null, null)
        say.done(action.label, displayId)
    }

    private suspend fun runPrivileged(call: ActionCall, say: Say) {
        val action = call.action
        val result = when (action) {
            ThorAction.TOGGLE_LAYOUT -> {
                val style = ControllerStyle.toggledLayout(ControllerStyle.getStyle(executor)).also { it.enable(executor) }
                service.getString(R.string.actionResultController, service.getString(style.textRes))
            }
            ThorAction.CYCLE_CONTROLLER_STYLE -> {
                val disabled = prefs.disabledControllerStyle?.let(ControllerStyle::getById)
                val style = ControllerStyle.next(ControllerStyle.getStyle(executor), disabled).also { it.enable(executor) }
                service.getString(R.string.actionResultController, service.getString(style.textRes))
            }
            ThorAction.CYCLE_L2R2 -> {
                val disabled = prefs.disabledL2r2Style?.let(L2R2Style::getById)
                val style = L2R2Style.next(L2R2Style.getStyle(executor), disabled).also { it.enable(executor) }
                service.getString(R.string.actionResultL2r2, service.getString(style.textRes))
            }
            ThorAction.CYCLE_PERFORMANCE -> {
                val mode = PerfMode.next(PerfMode.getMode(executor)).also { it.enable(executor) }
                service.getString(R.string.actionResultPerformance, service.getString(mode.textRes))
            }
            ThorAction.CYCLE_FAN -> {
                val mode = FanMode.next(FanMode.getMode(executor)).also { it.enable(executor) }
                service.getString(R.string.actionResultFan, service.getString(mode.textRes))
            }
            ThorAction.TOGGLE_REFRESH_RATE -> {
                val hz = RefreshRate.toggled(RefreshRate.peak(executor)).also { RefreshRate.apply(executor, it) }
                service.getString(R.string.actionResultRefreshRate, hz)
            }
            ThorAction.TOGGLE_BOTTOM_SCREEN -> {
                // AYN's own long press of the AYN button flips the same setting: 0 both screens on, 1 bottom off.
                val bottomOff = readSystemInt(KEY_SCREEN_MODE) == 1
                executor.executeAsRoot("settings put system $KEY_SCREEN_MODE ${if (bottomOff) 0 else 1}")
                service.getString(if (bottomOff) R.string.actionResultBottomScreenOn else R.string.actionResultBottomScreenOff)
            }
            ThorAction.TOGGLE_AYN_MOUSE -> {
                val on = readSystemInt(KEY_AYN_MOUSE) == 1
                executor.executeAsRoot("settings put system $KEY_AYN_MOUSE ${if (on) 0 else 1}")
                service.getString(if (on) R.string.actionResultMouseOff else R.string.actionResultMouseOn)
            }
            ThorAction.CLOSE_APP -> closeForegroundApp() ?: return say.problem(R.string.actionResultNothingToCloseHere)
            ThorAction.AYN_DRAWER -> {
                AynHooks.openDrawer(executor)
                service.getString(action.label)
            }
            else -> return
        }
        say.done(result)
    }

    /**
     * Closes every app that isn't on a screen, as swiping it away in Recent apps does (the root helper's `cleanup`), then
     * lets Android drop the processes it keeps cached. `am kill-all` alone only stops cached processes: the apps
     * stayed open. Without the helper only that last step is possible, and the message says so.
     */
    private fun closeBackground(alsoCleanMemory: Boolean, say: Say) {
        val finish = { closed: Int? ->
            scope.launch {
                executor.executeAsRoot("am kill-all")
                val apps = when (closed) {
                    null -> service.getString(R.string.actionResultBackgroundStopped)
                    0 -> service.getString(R.string.actionResultNoBackgroundApps)
                    else -> service.resources.getQuantityString(R.plurals.actionResultBackgroundClosed, closed, closed)
                }
                say.done(if (alsoCleanMemory) cleanMemory(apps) else apps)
            }
            Unit
        }
        val keep = BackgroundTasks.encodeKeep(host.keepWhenClosingBackground())
        val sent = host.helperCommand("cleanup", keep) { ok, text -> finish(if (ok) text.trim().toIntOrNull() else null) }
        if (!sent) finish(null)
    }

    /**
     * Drops the kernel's file caches and compacts memory as root, then adds how much memory is free to [apps]. The
     * `echo ok` tells whether the writes went through; SELinux can refuse them, and then the message says so.
     */
    private suspend fun cleanMemory(apps: String): String {
        val cleaned = executor.capture(CLEAN_MEMORY).getOrNull()?.lines()?.lastOrNull()?.trim() == "ok"
        if (!cleaned) return service.getString(R.string.actionResultMemoryRefused, apps)
        delay(CLEAN_SETTLE_MS)
        val availableKb = StatsParser.availableKb(runCatching { File("/proc/meminfo").readText() }.getOrDefault(""))
            ?: StatsParser.availableKb(executor.executeAsRoot("grep MemAvailable /proc/meminfo").getOrNull().orEmpty())
            ?: return apps
        return service.getString(R.string.actionResultMemoryCleaned, apps, StatsParser.gb(availableKb))
    }

    private fun readSystemInt(key: String): Int = runCatching { Settings.System.getInt(service.contentResolver, key, 0) }
        .getOrElse { executor.getIntSystemSetting(key, 0) }

    private fun closeForegroundApp(): String? {
        val packageName = host.foregroundPackage?.takeIf { it.matches(PACKAGE_NAME) && it != service.packageName } ?: return null
        executor.executeAsRoot("am force-stop $packageName")
        return service.getString(R.string.actionResultClosed, appLabel(packageName))
    }

    private fun appLabel(packageName: String): CharSequence = runCatching {
        service.packageManager.getApplicationLabel(service.packageManager.getApplicationInfo(packageName, 0))
    }.getOrDefault(packageName)

    private companion object {
        val PACKAGE_NAME = Regex("[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z0-9_]+)+")
        const val KEY_SCREEN_MODE = "dual_screen_display_mode"
        const val KEY_AYN_MOUSE = "global_gamepad_to_mouse_mode"
        const val SWIPE_REACH = 0.3f
        const val SWIPE_MS = 250L
        val MOVE_WATCH_MS = listOf(600L, 1500L, 3000L)
        const val FOLLOW_DELAY_MS = 300L
        const val CLEAN_MEMORY = "sync; echo 3 > /proc/sys/vm/drop_caches && echo 1 > /proc/sys/vm/compact_memory && echo ok"
        const val CLEAN_SETTLE_MS = 800L
    }
}
