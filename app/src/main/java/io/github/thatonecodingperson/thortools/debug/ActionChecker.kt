package io.github.thatonecodingperson.thortools.debug

import android.accessibilityservice.AccessibilityService
import android.app.ActivityOptions
import android.content.Intent
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.view.Display
import android.view.accessibility.AccessibilityWindowInfo
import io.github.thatonecodingperson.thortools.actions.ActionCall
import io.github.thatonecodingperson.thortools.actions.ActionHost
import io.github.thatonecodingperson.thortools.actions.ActionRunner
import io.github.thatonecodingperson.thortools.actions.ThorAction
import io.github.thatonecodingperson.thortools.input.Screen
import io.github.thatonecodingperson.thortools.main.MainActivity
import io.github.thatonecodingperson.thortools.models.ControllerStyle
import io.github.thatonecodingperson.thortools.models.L2R2Style
import io.github.thatonecodingperson.thortools.models.RefreshRate
import io.github.thatonecodingperson.thortools.navigation.GestureNav
import io.github.thatonecodingperson.thortools.navigation.GestureShell
import io.github.thatonecodingperson.thortools.tools.ShellExecutor
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

/**
 * Runs the action check for the accessibility service: for each [ActionCheck] it prepares, reads, runs the action the
 * way a hotkey would, waits for the expected change, reads again and puts things back. Everything runs on [scope] (IO);
 * only the action itself and the window and view calls go to the main thread.
 */
class ActionChecker(
    private val service: AccessibilityService,
    private val executor: ShellExecutor,
    private val scope: CoroutineScope,
    private val host: ActionHost,
    private val runner: ActionRunner,
    private val session: DebugSession,
    private val panelOpen: () -> Boolean,
    private val closePanel: () -> Unit,
    private val stayAwakeOn: () -> Boolean,
    private val desktopOn: () -> Boolean,
    private val setDesktopOn: (Boolean) -> Unit,
    private val gestures: GestureNav,
    private val helperConnected: () -> Boolean,
) : CheckRunner {
    private val mainHandler = Handler(Looper.getMainLooper())
    private val audio = service.getSystemService(AudioManager::class.java)
    private var job: Job? = null

    override fun start(checks: List<ActionCheck>) {
        if (job?.isActive == true) return
        job = scope.launch {
            session.setBusy(true)
            try {
                checks.forEach { check -> session.put(runOne(check)) }
            } finally {
                withContext(NonCancellable) {
                    session.showPad(false)
                    session.setBusy(false)
                    bringBack()
                    val results = session.results.value.values.sortedBy { it.action.ordinal }
                    session.writeSection(DebugReport.ACTIONS, DebugReport.actionSection(results, session.now()))
                }
            }
        }
    }

    override fun stop() {
        job?.cancel()
    }

    private suspend fun runOne(check: ActionCheck): CheckResult {
        session.put(CheckResult(check.action, CheckState.RUNNING))
        return try {
            check(check)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            CheckResult(check.action, CheckState.FAIL, detail = "error: ${e.javaClass.simpleName} ${e.message.orEmpty()}")
        }
    }

    private suspend fun check(check: ActionCheck): CheckResult {
        skipReason(check)?.let { return CheckResult(check.action, CheckState.SKIPPED, detail = it) }
        val original = read(check.probe)
        // The controller's screen before anything moved it, for the checks that move it to reach the right screen.
        val controllerBefore = host.controllerDisplayId()
        try {
            prepare(check)?.let { return CheckResult(check.action, CheckState.SKIPPED, original, detail = it) }
            val before = read(check.probe)
            val controllerOnTop = host.controllerDisplayId() == Display.DEFAULT_DISPLAY
            onMain { runner.run(ActionCall(check.action, check.arg)) }
            if (check.expect == Expect.Asked) return watch(check, before)
            val after = awaitResult(check, before, controllerOnTop)
            val passed = ActionChecks.passes(check.expect, before, after, controllerOnTop)
            val restored = withContext(NonCancellable) { restore(check, original, before, after) }
            return CheckResult(check.action, if (passed) CheckState.PASS else CheckState.FAIL, before, after, restored)
        } finally {
            withContext(NonCancellable) {
                if (check.setup == Setup.GESTURE_PAD) session.showPad(false)
                if (host.controllerDisplayId() != controllerBefore && check.probe != Probe.CONTROLLER) moveController(controllerBefore)
            }
        }
    }

    private fun skipReason(check: ActionCheck): String? {
        val needsBottom = check.setup in setOf(Setup.APP_ON_BOTTOM, Setup.APP_ON_OTHER_SCREEN, Setup.CONTROLLER_ELSEWHERE) ||
            check.action in
            setOf(ThorAction.LAUNCH_APP, ThorAction.LOCK_CONTROLLER_BOTTOM, ThorAction.BOTTOM_BRIGHTER, ThorAction.BOTTOM_DIMMER)
        return when {
            needsBottom && host.bottomDisplayId() == null -> "no second screen"
            (check.action.needsHelper || check.probe == Probe.BRIGHTNESS) && !helperConnected() -> "needs the root input helper"
            check.probe in ROOT_PROBES && !executor.pServerAvailable -> "needs AYN system access (PServer)"
            else -> null
        }
    }

    /** Gets the check's situation ready; a reason when it couldn't (the check is then skipped). */
    private suspend fun prepare(check: ActionCheck): String? {
        if (check.probe == Probe.TEST_APP || check.probe == Probe.SCREENS) closeTestApp()
        return when (check.setup) {
            Setup.NONE -> null
            Setup.LEVEL_MIDDLE -> {
                levelToMiddle(check.probe)
                null
            }
            Setup.CONTROLLER_ELSEWHERE -> {
                val target = if (check.action == ThorAction.CONTROLLER_TO_TOP) host.bottomDisplayId() else Display.DEFAULT_DISPLAY
                if (target != null && host.controllerDisplayId() != target) moveController(target)
                null
            }
            Setup.APP_ON_TOP -> openTestApp(Display.DEFAULT_DISPLAY)
            Setup.APP_ON_BOTTOM -> openTestApp(host.bottomDisplayId())
            Setup.APP_ON_CONTROLLER_SCREEN -> openTestApp(host.controllerDisplayId())?.also { return it }
                ?: awaitFront()
            Setup.APP_ON_OTHER_SCREEN -> openTestApp(otherDisplay())
            Setup.APP_IN_BACKGROUND -> openTestApp(Display.DEFAULT_DISPLAY) ?: run {
                onMain { host.goHome(Display.DEFAULT_DISPLAY) }
                val hidden = poll(OPEN_WAIT_MS) { ActionChecks.TOP !in ActionChecks.appScreens(read(Probe.TEST_APP)) }
                if (hidden) null else "the test app didn't go to the background"
            }
            Setup.GESTURE_PAD -> {
                if (host.controllerDisplayId() != Display.DEFAULT_DISPLAY) moveController(Display.DEFAULT_DISPLAY)
                bringBack()
                session.lastSwipe = null
                session.showPad(true)
                delay(PAD_SETTLE_MS)
                null
            }
        }
    }

    private suspend fun watch(check: ActionCheck, before: String?): CheckResult {
        delay(WATCH_MS)
        val windows = read(Probe.WINDOWS)
        if (check.action == ThorAction.NOTIFICATIONS || check.action == ThorAction.QUICK_SETTINGS) {
            onMain { service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_DISMISS_NOTIFICATION_SHADE) }
        }
        bringBack()
        val yes = session.ask(check.action)
        val state = when (yes) {
            true -> CheckState.PASS
            false -> CheckState.FAIL
            null -> CheckState.ASK
        }
        return CheckResult(
            check.action,
            state,
            before,
            windows,
            detail = if (yes ==
                null
            ) {
                "no answer"
            } else {
                "answered ${if (yes) "yes" else "no"}"
            },
        )
    }

    /** Reads until the expectation holds or time runs out; the last reading either way. */
    private suspend fun awaitResult(check: ActionCheck, before: String?, controllerOnTop: Boolean): String? {
        var after: String? = null
        poll(if (check.probe == Probe.SCREENSHOT) SCREENSHOT_WAIT_MS else RESULT_WAIT_MS) {
            after = read(check.probe)
            ActionChecks.passes(check.expect, before, after, controllerOnTop)
        }
        return after
    }

    /** Puts back what the check changed; true when it is back, null when there was nothing to put back. */
    private suspend fun restore(check: ActionCheck, original: String?, before: String?, after: String?): Boolean? = when (check.restore) {
        Restore.NONE -> null
        Restore.VALUE -> {
            if (original != null) write(check.probe, original)
            original?.let { wanted -> poll(RESULT_WAIT_MS) { read(check.probe) == wanted } }
        }
        Restore.CLOSE_APP -> {
            closeTestApp()
            bringBack()
            null
        }
        Restore.SWAP_BACK -> {
            onMain { runner.run(ActionCall(ThorAction.SWAP_SCREENS)) }
            val back = poll(RESULT_WAIT_MS) { ActionChecks.appScreens(read(Probe.TEST_APP)) == ActionChecks.appScreens(before) }
            closeTestApp()
            bringBack()
            back
        }
        Restore.CLOSE_PANEL -> {
            onMain { closePanel() }
            poll(RESULT_WAIT_MS) { !onMainResult { panelOpen() } }
        }
        Restore.LEAVE_SYSTEM_VIEW -> null
        Restore.DELETE_SCREENSHOT -> {
            val name = after?.takeIf { it != before && it.matches(SAFE_NAME) }
            if (name == null) {
                null
            } else {
                executor.executeAsRoot(
                    "rm -f '$SCREENSHOTS/$name'; content delete --uri content://media/external/images/media --where \"_display_name='$name'\"",
                )
                read(Probe.SCREENSHOT) != name
            }
        }
    }

    private suspend fun read(probe: Probe): String? = when (probe) {
        Probe.CONTROLLER_STYLE -> ControllerStyle.getStyle(executor).id
        Probe.L2R2 -> L2R2Style.getStyle(executor).id
        Probe.PERFORMANCE -> systemInt(KEY_PERFORMANCE)
        Probe.FAN -> systemInt(KEY_FAN)
        Probe.REFRESH -> RefreshRate.save(executor).let { "${it.min}/${it.peak}" }
        Probe.BOTTOM_SCREEN -> systemInt(KEY_SCREEN_MODE)
        Probe.AYN_MOUSE -> systemInt(KEY_AYN_MOUSE)
        Probe.CONTROLLER -> if (host.controllerDisplayId() == Display.DEFAULT_DISPLAY) ActionChecks.TOP else ActionChecks.BOTTOM
        Probe.LOCK -> host.lockedTo?.name?.lowercase() ?: ActionChecks.NONE
        Probe.STAY_AWAKE -> if (onMainResult { stayAwakeOn() }) "on" else "off"
        Probe.GESTURES -> GestureShell.probe(gestures.readNow())
        Probe.DESKTOP -> if (onMainResult { desktopOn() }) "on" else "off"
        Probe.PANEL -> if (onMainResult { panelOpen() }) "open" else "closed"
        Probe.BRIGHTNESS -> helper("getbrightness", displays().joinToString(","))
        Probe.VOLUME -> audio.getStreamVolume(AudioManager.STREAM_MUSIC).toString()
        Probe.TEST_APP -> {
            val screens = screensOf(ActionChecks.TEST_APP)
            val running = !executor.executeAsRoot("pidof ${ActionChecks.TEST_APP}").getOrNull().isNullOrBlank()
            ActionChecks.testAppReading(screens, running)
        }
        Probe.SCREENS -> host.appsOnScreens().entries.sortedBy { it.key }.joinToString(";") { "${screenName(it.key)}=${it.value}" }
        Probe.SCREENSHOT -> executor.executeAsRoot("ls -t $SCREENSHOTS 2>/dev/null | head -n 1").getOrNull()?.trim()?.takeIf {
            it.isNotEmpty()
        }
        Probe.SCREEN -> if (service.getSystemService(PowerManager::class.java).isInteractive) "on" else "off"
        Probe.SWIPE -> session.lastSwipe
        Probe.WINDOWS -> onMainResult { systemWindows() }
    }

    private suspend fun write(probe: Probe, value: String) {
        when (probe) {
            Probe.CONTROLLER_STYLE -> ControllerStyle.getById(value).enable(executor)
            Probe.L2R2 -> L2R2Style.getById(value).enable(executor)
            Probe.PERFORMANCE -> value.toIntOrNull()?.let { executor.setIntSystemSetting(KEY_PERFORMANCE, it) }
            Probe.FAN -> value.toIntOrNull()?.let { executor.setIntSystemSetting(KEY_FAN, it) }
            Probe.REFRESH -> value.split('/').let { RefreshRate.restore(executor, RefreshRate.Saved(it.getOrNull(0), it.getOrNull(1))) }
            Probe.BOTTOM_SCREEN -> value.toIntOrNull()?.let { executor.setIntSystemSetting(KEY_SCREEN_MODE, it) }
            Probe.AYN_MOUSE -> value.toIntOrNull()?.let { executor.setIntSystemSetting(KEY_AYN_MOUSE, it) }
            Probe.CONTROLLER -> (
                if (value ==
                    ActionChecks.TOP
                ) {
                    Display.DEFAULT_DISPLAY
                } else {
                    host.bottomDisplayId()
                }
                )?.let { moveController(it) }
            Probe.LOCK -> setLock(Screen.entries.find { it.name.lowercase() == value })
            Probe.STAY_AWAKE -> if (read(Probe.STAY_AWAKE) != value) onMain { host.toggleStayAwake() }
            Probe.GESTURES -> if (read(Probe.GESTURES) != value) gestures.toggle()
            Probe.DESKTOP -> onMain { setDesktopOn(value == "on") }
            Probe.BRIGHTNESS -> displays().zip(value.split(',')).forEach { (display, level) -> helper("setbrightness", "$display", level) }
            Probe.VOLUME -> value.toIntOrNull()?.let { audio.setStreamVolume(AudioManager.STREAM_MUSIC, it, 0) }
            else -> Unit
        }
    }

    /** Brightness or volume at a limit moves to the middle first, so a step either way shows. */
    private suspend fun levelToMiddle(probe: Probe) {
        when (probe) {
            Probe.BRIGHTNESS -> {
                val levels = read(probe)?.split(',')?.map { it.toFloatOrNull() ?: MIDDLE } ?: return
                displays().zip(levels).forEach { (display, level) ->
                    if (ActionChecks.atLimit(level, LOW_LEVEL, HIGH_LEVEL)) helper("setbrightness", "$display", "$MIDDLE")
                }
            }
            Probe.VOLUME -> {
                val max = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
                val now = audio.getStreamVolume(AudioManager.STREAM_MUSIC)
                if (now <= 0 || now >= max) audio.setStreamVolume(AudioManager.STREAM_MUSIC, max / 2, 0)
            }
            else -> Unit
        }
    }

    /** Opens the test app on [displayId] and waits for it there; a reason when it didn't come up. */
    private suspend fun openTestApp(displayId: Int?): String? {
        displayId ?: return "no second screen"
        val intent = service.packageManager.getLaunchIntentForPackage(ActionChecks.TEST_APP)
            ?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            ?: return "the test app (Android's Settings) isn't there"
        val options = ActivityOptions.makeBasic().setLaunchDisplayId(displayId).toBundle()
        onMain { service.startActivity(intent, options) }
        val screen = screenName(displayId)
        val shown = poll(OPEN_WAIT_MS) { screen in ActionChecks.appScreens(read(Probe.TEST_APP)) }
        return if (shown) null else "the test app didn't open on the $screen screen"
    }

    /** Close the current app acts on the app in front, which the service learns from the window events a moment later. */
    private suspend fun awaitFront(): String? =
        if (poll(OPEN_WAIT_MS) { host.foregroundPackage == ActionChecks.TEST_APP }) null else "the test app didn't come to the front"

    private fun closeTestApp() {
        executor.executeAsRoot("am force-stop ${ActionChecks.TEST_APP}")
    }

    /** Brings the debug screen back to the top screen, unless it is already there. */
    private suspend fun bringBack() {
        if (host.appsOnScreens()[Display.DEFAULT_DISPLAY] == service.packageName) return
        val intent = Intent(service, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
            .putExtra(MainActivity.EXTRA_OPEN, MainActivity.OPEN_DEBUG)
        val options = ActivityOptions.makeBasic().setLaunchDisplayId(Display.DEFAULT_DISPLAY).toBundle()
        onMain { service.startActivity(intent, options) }
        poll(OPEN_WAIT_MS) { host.appsOnScreens()[Display.DEFAULT_DISPLAY] == service.packageName }
    }

    private suspend fun moveController(displayId: Int) {
        withTimeoutOrNull(MOVE_WAIT_MS) {
            suspendCancellableCoroutine { done ->
                mainHandler.post { host.moveController(displayId) { if (done.isActive) done.resume(Unit) } }
            }
        }
    }

    private suspend fun setLock(target: Screen?) {
        withTimeoutOrNull(MOVE_WAIT_MS) {
            suspendCancellableCoroutine { done ->
                mainHandler.post { host.setLock(target) { if (done.isActive) done.resume(Unit) } }
            }
        }
    }

    /** A root input helper command's answer; null when it failed or isn't connected. */
    private suspend fun helper(name: String, vararg args: String): String? = withTimeoutOrNull(HELPER_WAIT_MS) {
        suspendCancellableCoroutine { done ->
            val sent = host.helperCommand(name, *args) { ok, text -> if (done.isActive) done.resume(text.takeIf { ok }) }
            if (!sent && done.isActive) done.resume(null)
        }
    }

    private fun displays(): List<Int> = listOfNotNull(Display.DEFAULT_DISPLAY, host.bottomDisplayId())

    private fun otherDisplay(): Int? = if (host.controllerDisplayId() ==
        Display.DEFAULT_DISPLAY
    ) {
        host.bottomDisplayId()
    } else {
        Display.DEFAULT_DISPLAY
    }

    private fun screenName(displayId: Int): String = if (displayId == Display.DEFAULT_DISPLAY) ActionChecks.TOP else ActionChecks.BOTTOM

    private fun screensOf(packageName: String): Set<String> =
        host.appsOnScreens().filterValues { it == packageName }.keys.map(::screenName).toSet()

    private fun systemInt(key: String): String = executor.getIntSystemSetting(key, -1).toString()

    /** The system windows showing (the notification shade, Recent apps, AYN's drawer), by title or package. */
    private fun systemWindows(): String {
        val all = service.windowsOnAllDisplays
        return (0 until all.size()).flatMap { all.valueAt(it) }
            .filter { it.type != AccessibilityWindowInfo.TYPE_APPLICATION }
            .mapNotNull { window -> window.title?.toString()?.takeIf { it.isNotBlank() } ?: window.root?.packageName?.toString() }
            .distinct()
            .joinToString(", ")
            .ifEmpty { ActionChecks.NONE }
    }

    private suspend fun poll(timeoutMs: Long, condition: suspend () -> Boolean): Boolean {
        val end = System.currentTimeMillis() + timeoutMs
        while (true) {
            if (condition()) return true
            if (System.currentTimeMillis() >= end) return false
            delay(POLL_MS)
        }
    }

    private suspend fun onMain(block: () -> Unit) = onMainResult(block)

    private suspend fun <T> onMainResult(block: () -> T): T = suspendCancellableCoroutine { done ->
        mainHandler.post { if (done.isActive) done.resume(block()) }
    }

    private companion object {
        const val KEY_PERFORMANCE = "performance_mode"
        const val KEY_FAN = "fan_mode"
        const val KEY_SCREEN_MODE = "dual_screen_display_mode"
        const val KEY_AYN_MOUSE = "global_gamepad_to_mouse_mode"
        const val SCREENSHOTS = "/sdcard/Pictures/Screenshots"
        val SAFE_NAME = Regex("[A-Za-z0-9._-]+")
        val ROOT_PROBES = setOf(Probe.TEST_APP, Probe.SCREENSHOT)
        const val RESULT_WAIT_MS = 4_000L
        const val SCREENSHOT_WAIT_MS = 6_000L
        const val OPEN_WAIT_MS = 5_000L
        const val MOVE_WAIT_MS = 3_000L
        const val HELPER_WAIT_MS = 3_000L
        const val WATCH_MS = 2_500L
        const val PAD_SETTLE_MS = 700L
        const val POLL_MS = 200L
        const val MIDDLE = 0.5f
        const val LOW_LEVEL = 0.05f
        const val HIGH_LEVEL = 0.95f
    }
}
