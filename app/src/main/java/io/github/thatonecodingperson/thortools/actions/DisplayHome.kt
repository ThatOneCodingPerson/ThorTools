package io.github.thatonecodingperson.thortools.actions

import android.accessibilityservice.AccessibilityService
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Handler
import android.os.Looper
import android.view.Display
import android.view.accessibility.AccessibilityEvent
import io.github.thatonecodingperson.thortools.input.ScreenFocus
import io.github.thatonecodingperson.thortools.tools.ShellExecutor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap

/**
 * Home on the screen that has the controller. Android's global Home only acts on the top screen, and starting the
 * top screen's launcher on the bottom screen brings it up on the top screen instead, so the bottom screen re-opens its
 * own launcher activity: the one seen there, else the one Android lists there, else the secondary home.
 */
class DisplayHome(
    private val service: AccessibilityService,
    private val executor: ShellExecutor,
    private val screenFocus: ScreenFocus,
    private val scope: CoroutineScope,
    /** A Home key for one display from the root helper (false without it); [onFailed] if the helper couldn't. */
    private val homeKey: (displayId: Int, onFailed: () -> Unit) -> Boolean,
) {
    private val mainHandler = Handler(Looper.getMainLooper())
    private val launcherByDisplay = ConcurrentHashMap<Int, ComponentName>()

    /** Called for window-state events of launcher packages; remembers which launcher activity each screen shows. */
    fun onLauncherEvent(event: AccessibilityEvent) {
        val className = event.className?.toString() ?: return
        val packageName = event.packageName?.toString() ?: return
        val component = ComponentName(packageName, className)
        if (runCatching { service.packageManager.getActivityInfo(component, 0) }.isFailure) return
        val windowId = event.windowId
        val windows = service.windowsOnAllDisplays
        for (index in 0 until windows.size()) {
            if (windows.valueAt(index).any { it.id == windowId }) {
                launcherByDisplay[windows.keyAt(index)] = component
                return
            }
        }
    }

    /**
     * Home on [displayId], or on the screen that has the controller when null. First choice: a Home key for that
     * display, which Android answers with that screen's own home. Without the root helper: Android's global Home for
     * the top screen, the bottom screen's launcher activity for the bottom one.
     */
    fun goHome(displayId: Int? = null) {
        scope.launch {
            val display = displayId ?: focusedDisplay() ?: screenFocus.displayId()
            mainHandler.post { homeOn(display) }
        }
    }

    /**
     * Home as the Home button itself does it, with no key sent: on [displayId], or on the screen with the controller
     * (Android's focused display) when null. The top screen gets Android's own Home while it has the
     * controller, else its home started by name (Android's Home acts on the focused screen); another screen re-opens its
     * own launcher.
     */
    fun systemHome(displayId: Int? = null) {
        scope.launch {
            val focused = focusedDisplay()
            when (val display = displayId ?: focused ?: screenFocus.displayId()) {
                Display.DEFAULT_DISPLAY -> if (focused == null || focused == display) topHome() else startTopHome()
                else -> secondHome(display, fallback = true)
            }
        }
    }

    /**
     * Both screens: the bottom first, then the top, each named. The global Home only reaches the screen Android
     * considers focused, which starting the bottom screen's home moves to the bottom.
     */
    fun goHomeBoth(bottomDisplayId: Int) {
        mainHandler.post {
            homeOn(bottomDisplayId)
            mainHandler.postDelayed({ homeOn(Display.DEFAULT_DISPLAY, explicitTop = true) }, BOTH_GAP_MS)
        }
    }

    /** Main thread. [explicitTop]: the top screen's home started on display 0 instead of the global Home. */
    private fun homeOn(display: Int, explicitTop: Boolean = false) {
        val fallback = {
            scope.launch {
                when {
                    display != Display.DEFAULT_DISPLAY -> secondHome(display, fallback = true)
                    explicitTop -> startTopHome()
                    else -> topHome()
                }
            }
            Unit
        }
        if (!homeKey(display, fallback)) fallback()
    }

    /** The top screen's home, named: a HOME intent started on display 0 always lands on the top screen's home. */
    private fun startTopHome() {
        if (!start(Display.DEFAULT_DISPLAY, "-a android.intent.action.MAIN -c android.intent.category.HOME -f $HOME_FLAGS")) topHome()
    }

    /**
     * Home on a screen an app just left. The top screen uses Android's Home; the bottom screen only its own launcher
     * activity, never a guess, because a wrong start brings a launcher up over the app that just moved to the top.
     * Otherwise the screen is left showing what lies beneath.
     */
    fun homeAfterMove(displayId: Int) {
        if (displayId == Display.DEFAULT_DISPLAY) return topHome()
        scope.launch { secondHome(displayId, fallback = false) }
    }

    private fun topHome() {
        mainHandler.post { service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_HOME) }
    }

    /** Runs root calls: never on the main thread. [fallback] lets Android pick a second-screen home when we can't. */
    private fun secondHome(display: Int, fallback: Boolean) {
        // Starting the top screen's launcher here would bring it up on the top screen instead.
        val unsafe = setOfNotNull(topHomeActivity(), launcherByDisplay[Display.DEFAULT_DISPLAY])
        val component = sequenceOf(
            { launcherByDisplay[display] },
            { listedLauncher(display)?.also { launcherByDisplay[display] = it } },
            { secondaryHome() },
        ).mapNotNull { it() }.firstOrNull { it !in unsafe }
        if (component != null && start(display, "-n ${component.flattenToShortString()} -f $HOME_FLAGS")) return
        if (fallback) start(display, "-a android.intent.action.MAIN -c android.intent.category.SECONDARY_HOME")
    }

    /** `am start` on [display]; false when Android answered with an error. */
    private fun start(display: Int, target: String): Boolean {
        val output = executor.capture("am start --display $display $target").getOrNull() ?: return false
        return output.lineSequence().none { it.startsWith("Error") }
    }

    /** The launcher Android lists on [display], for a screen whose home hasn't been seen since the service started. */
    private fun listedLauncher(display: Int): ComponentName? {
        val dump = executor.capture("dumpsys activity activities").getOrNull() ?: return null
        val (packageName, className) = LauncherLookup.onDisplay(dump, display, launcherPackages()) ?: return null
        return ComponentName(packageName, className)
    }

    private fun launcherPackages(): Set<String> = listOf(Intent.CATEGORY_HOME, Intent.CATEGORY_SECONDARY_HOME).flatMap { category ->
        service.packageManager.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(category), 0).map {
            it.activityInfo.packageName
        }
    }.toSet()

    private fun topHomeActivity(): ComponentName? = service.packageManager
        .resolveActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME), PackageManager.MATCH_DEFAULT_ONLY)
        ?.activityInfo
        ?.let { ComponentName(it.packageName, it.name) }

    /** The display Android routes the controller to, from `dumpsys input`. Runs a root call: never on the main thread. */
    private fun focusedDisplay(): Int? = executor.executeAsRoot("dumpsys input | grep -m1 FocusedDisplayId")
        .getOrNull()
        ?.substringAfter(':', "")
        ?.trim()
        ?.toIntOrNull()

    private fun secondaryHome(): ComponentName? {
        val candidates = service.packageManager.queryIntentActivities(
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_SECONDARY_HOME),
            PackageManager.MATCH_DEFAULT_ONLY,
        )
        val defaultLauncher = topHomeActivity()?.packageName
        val chosen = candidates.firstOrNull { it.activityInfo.packageName == defaultLauncher } ?: candidates.firstOrNull()
        return chosen?.activityInfo?.let { ComponentName(it.packageName, it.name) }
    }

    private companion object {
        /** FLAG_ACTIVITY_NEW_TASK | FLAG_ACTIVITY_REORDER_TO_FRONT */
        const val HOME_FLAGS = "0x10020000"

        // Lets the bottom screen's home take focus before the top screen's Home is pressed.
        const val BOTH_GAP_MS = 150L
    }
}
