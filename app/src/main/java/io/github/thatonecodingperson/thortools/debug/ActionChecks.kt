package io.github.thatonecodingperson.thortools.debug

import io.github.thatonecodingperson.thortools.actions.ThorAction
import io.github.thatonecodingperson.thortools.hotkeys.AppLaunch
import io.github.thatonecodingperson.thortools.hotkeys.LaunchScreen

/** AUTO checks itself and puts everything back; WATCH needs a "did it open?" answer; RISKY runs only when picked. */
enum class CheckKind { AUTO, WATCH, RISKY }

/** What a check reads before and after the action. Every reading is text, so the verdicts stay simple and testable. */
enum class Probe {
    CONTROLLER_STYLE,
    L2R2,
    PERFORMANCE,
    FAN,

    /** `min/peak`, both refresh rate settings. */
    REFRESH,
    BOTTOM_SCREEN,
    AYN_MOUSE,

    /** `top` or `bottom`: the screen with the controller. */
    CONTROLLER,

    /** `top`, `bottom` or `none`. */
    LOCK,
    STAY_AWAKE,

    /** `home=on|off,back=on|off`: which of Android's navigation swipes work, as read from the system. */
    GESTURES,
    DESKTOP,
    PANEL,

    /** `top,bottom` brightness, 0 to 1. */
    BRIGHTNESS,
    VOLUME,

    /** Where the test app shows (`top`, `bottom`, `top+bottom` or `none`) and whether it runs: `bottom|running`. */
    TEST_APP,

    /** `top=<package>;bottom=<package>` for the apps on the screens; empty when none. */
    SCREENS,

    /** The newest screenshot's file name. */
    SCREENSHOT,

    /** `on` or `off`. */
    SCREEN,

    /** The direction the gesture pad saw. */
    SWIPE,

    /** The titles of the system windows showing (for the watch checks' notes). */
    WINDOWS,
}

/** How a check makes sure the action has something to do, before it runs. */
enum class Setup {
    NONE,

    /** A level at its limit first moves to the middle, so a step either way shows. */
    LEVEL_MIDDLE,

    /** The controller first goes to the other screen than the one the move aims at. */
    CONTROLLER_ELSEWHERE,
    APP_ON_TOP,
    APP_ON_BOTTOM,
    APP_ON_CONTROLLER_SCREEN,
    APP_ON_OTHER_SCREEN,

    /** The test app opened and sent to the background (Home on its screen). */
    APP_IN_BACKGROUND,
    GESTURE_PAD,
}

/** How a check puts things back afterwards. */
enum class Restore { NONE, VALUE, CLOSE_APP, SWAP_BACK, CLOSE_PANEL, LEAVE_SYSTEM_VIEW, DELETE_SCREENSHOT }

/** A screen named in an expectation; resolved when the check runs ([ActionChecks.resolve]). */
enum class Where { TOP, BOTTOM, CONTROLLER, OTHER }

sealed interface Expect {
    data object Changed : Expect

    data class Is(val value: String) : Expect

    /** The numbers at [parts] (comma separated in the reading) all went up. */
    data class Rises(val parts: List<Int>) : Expect

    data class Falls(val parts: List<Int>) : Expect

    /** Both refresh settings equal, and not the peak from before. */
    data object RefreshToggled : Expect

    data class AppOn(val screen: Where) : Expect

    data class AppNotOn(val screen: Where) : Expect

    /** On no screen and no longer running. */
    data object AppGone : Expect

    /** Now on the other screen than before. */
    data object AppSwapped : Expect

    /** Neither screen shows an app. */
    data object NoApps : Expect

    /** Nothing to decide on its own: the user says whether it happened. */
    data object Asked : Expect
}

data class ActionCheck(
    val action: ThorAction,
    val probe: Probe,
    val expect: Expect,
    val kind: CheckKind = CheckKind.AUTO,
    val setup: Setup = Setup.NONE,
    val restore: Restore = Restore.VALUE,
    val arg: String? = null,
)

/** The action check's table and its verdicts. Pure. */
object ActionChecks {
    /** Android's own Settings: opened and closed by the checks, so none of the user's apps are touched. */
    const val TEST_APP = "com.android.settings"

    const val TOP = "top"
    const val BOTTOM = "bottom"
    const val NONE = "none"
    const val RUNNING = "running"

    val ALL: List<ActionCheck> = listOf(
        ActionCheck(ThorAction.TOGGLE_LAYOUT, Probe.CONTROLLER_STYLE, Expect.Changed),
        ActionCheck(ThorAction.CYCLE_CONTROLLER_STYLE, Probe.CONTROLLER_STYLE, Expect.Changed),
        ActionCheck(ThorAction.CYCLE_L2R2, Probe.L2R2, Expect.Changed),
        ActionCheck(ThorAction.TOGGLE_AYN_MOUSE, Probe.AYN_MOUSE, Expect.Changed),
        ActionCheck(ThorAction.TOGGLE_DESKTOP, Probe.DESKTOP, Expect.Changed),
        ActionCheck(ThorAction.CONTROLLER_TO_TOP, Probe.CONTROLLER, Expect.Is(TOP), setup = Setup.CONTROLLER_ELSEWHERE),
        ActionCheck(ThorAction.CONTROLLER_TO_BOTTOM, Probe.CONTROLLER, Expect.Is(BOTTOM), setup = Setup.CONTROLLER_ELSEWHERE),
        ActionCheck(ThorAction.TOGGLE_CONTROLLER_LOCK, Probe.LOCK, Expect.Changed),
        ActionCheck(ThorAction.LOCK_CONTROLLER_BOTTOM, Probe.LOCK, Expect.Changed),
        ActionCheck(ThorAction.LOCK_CONTROLLER_HERE, Probe.LOCK, Expect.Changed),
        ActionCheck(ThorAction.SWAP_SCREENS, Probe.TEST_APP, Expect.AppSwapped, setup = Setup.APP_ON_BOTTOM, restore = Restore.SWAP_BACK),
        ActionCheck(ThorAction.TOGGLE_BOTTOM_SCREEN, Probe.BOTTOM_SCREEN, Expect.Changed, kind = CheckKind.RISKY),
        ActionCheck(
            ThorAction.CLOSE_OTHER_SCREEN_APP,
            Probe.TEST_APP,
            Expect.AppGone,
            setup = Setup.APP_ON_OTHER_SCREEN,
            restore = Restore.CLOSE_APP,
        ),
        ActionCheck(ThorAction.HOME_TOP, Probe.TEST_APP, Expect.AppNotOn(Where.TOP), setup = Setup.APP_ON_TOP, restore = Restore.CLOSE_APP),
        ActionCheck(
            ThorAction.HOME_BOTTOM,
            Probe.TEST_APP,
            Expect.AppNotOn(Where.BOTTOM),
            setup = Setup.APP_ON_BOTTOM,
            restore = Restore.CLOSE_APP,
        ),
        ActionCheck(ThorAction.HOME_BOTH, Probe.SCREENS, Expect.NoApps, setup = Setup.APP_ON_BOTTOM, restore = Restore.CLOSE_APP),
        ActionCheck(
            ThorAction.BACK,
            Probe.TEST_APP,
            Expect.AppNotOn(Where.CONTROLLER),
            setup = Setup.APP_ON_CONTROLLER_SCREEN,
            restore = Restore.CLOSE_APP,
        ),
        ActionCheck(
            ThorAction.HOME,
            Probe.TEST_APP,
            Expect.AppNotOn(Where.CONTROLLER),
            setup = Setup.APP_ON_CONTROLLER_SCREEN,
            restore = Restore.CLOSE_APP,
        ),
        ActionCheck(ThorAction.RECENTS, Probe.WINDOWS, Expect.Asked, kind = CheckKind.WATCH, restore = Restore.LEAVE_SYSTEM_VIEW),
        ActionCheck(ThorAction.NOTIFICATIONS, Probe.WINDOWS, Expect.Asked, kind = CheckKind.WATCH, restore = Restore.LEAVE_SYSTEM_VIEW),
        ActionCheck(ThorAction.QUICK_SETTINGS, Probe.WINDOWS, Expect.Asked, kind = CheckKind.WATCH, restore = Restore.LEAVE_SYSTEM_VIEW),
        ActionCheck(ThorAction.SCREENSHOT, Probe.SCREENSHOT, Expect.Changed, restore = Restore.DELETE_SCREENSHOT),
        ActionCheck(ThorAction.REFRESH_SCREENS, Probe.WINDOWS, Expect.Asked, kind = CheckKind.WATCH, restore = Restore.NONE),
        ActionCheck(ThorAction.LOCK_SCREEN, Probe.SCREEN, Expect.Is("off"), kind = CheckKind.RISKY, restore = Restore.NONE),
        ActionCheck(
            ThorAction.CLOSE_APP,
            Probe.TEST_APP,
            Expect.AppGone,
            setup = Setup.APP_ON_CONTROLLER_SCREEN,
            restore = Restore.CLOSE_APP,
        ),
        ActionCheck(
            ThorAction.CLEAR_BACKGROUND,
            Probe.TEST_APP,
            Expect.AppGone,
            kind = CheckKind.RISKY,
            setup = Setup.APP_IN_BACKGROUND,
            restore = Restore.CLOSE_APP,
        ),
        ActionCheck(ThorAction.TOGGLE_STAY_AWAKE, Probe.STAY_AWAKE, Expect.Changed),
        ActionCheck(ThorAction.TOGGLE_GESTURES, Probe.GESTURES, Expect.Changed),
        ActionCheck(ThorAction.SWIPE_UP, Probe.SWIPE, Expect.Is("up"), setup = Setup.GESTURE_PAD, restore = Restore.NONE),
        ActionCheck(ThorAction.SWIPE_DOWN, Probe.SWIPE, Expect.Is("down"), setup = Setup.GESTURE_PAD, restore = Restore.NONE),
        ActionCheck(ThorAction.SWIPE_LEFT, Probe.SWIPE, Expect.Is("left"), setup = Setup.GESTURE_PAD, restore = Restore.NONE),
        ActionCheck(ThorAction.SWIPE_RIGHT, Probe.SWIPE, Expect.Is("right"), setup = Setup.GESTURE_PAD, restore = Restore.NONE),
        ActionCheck(ThorAction.AYN_DRAWER, Probe.WINDOWS, Expect.Asked, kind = CheckKind.WATCH, restore = Restore.LEAVE_SYSTEM_VIEW),
        ActionCheck(ThorAction.OPEN_QUICK_PANEL, Probe.PANEL, Expect.Is("open"), restore = Restore.CLOSE_PANEL),
        ActionCheck(ThorAction.BRIGHTER, Probe.BRIGHTNESS, Expect.Rises(listOf(0, 1)), setup = Setup.LEVEL_MIDDLE),
        ActionCheck(ThorAction.DIMMER, Probe.BRIGHTNESS, Expect.Falls(listOf(0, 1)), setup = Setup.LEVEL_MIDDLE),
        ActionCheck(ThorAction.TOP_BRIGHTER, Probe.BRIGHTNESS, Expect.Rises(listOf(0)), setup = Setup.LEVEL_MIDDLE),
        ActionCheck(ThorAction.TOP_DIMMER, Probe.BRIGHTNESS, Expect.Falls(listOf(0)), setup = Setup.LEVEL_MIDDLE),
        ActionCheck(ThorAction.BOTTOM_BRIGHTER, Probe.BRIGHTNESS, Expect.Rises(listOf(1)), setup = Setup.LEVEL_MIDDLE),
        ActionCheck(ThorAction.BOTTOM_DIMMER, Probe.BRIGHTNESS, Expect.Falls(listOf(1)), setup = Setup.LEVEL_MIDDLE),
        ActionCheck(ThorAction.LOUDER, Probe.VOLUME, Expect.Rises(listOf(0)), setup = Setup.LEVEL_MIDDLE),
        ActionCheck(ThorAction.QUIETER, Probe.VOLUME, Expect.Falls(listOf(0)), setup = Setup.LEVEL_MIDDLE),
        ActionCheck(ThorAction.CYCLE_PERFORMANCE, Probe.PERFORMANCE, Expect.Changed),
        ActionCheck(ThorAction.CYCLE_FAN, Probe.FAN, Expect.Changed),
        ActionCheck(ThorAction.TOGGLE_REFRESH_RATE, Probe.REFRESH, Expect.RefreshToggled),
        ActionCheck(
            ThorAction.LAUNCH_APP,
            Probe.TEST_APP,
            Expect.AppOn(Where.BOTTOM),
            restore = Restore.CLOSE_APP,
            arg = AppLaunch(TEST_APP, LaunchScreen.BOTTOM).encode(),
        ),
    )

    fun of(action: ThorAction): ActionCheck? = ALL.find { it.action == action }

    /** The checks "Run all" runs: everything but the risky ones. */
    val runAll: List<ActionCheck> get() = ALL.filter { it.kind != CheckKind.RISKY }

    /** [where] as `top` or `bottom`, from where the controller is. */
    fun resolve(where: Where, controllerOnTop: Boolean): String = when (where) {
        Where.TOP -> TOP
        Where.BOTTOM -> BOTTOM
        Where.CONTROLLER -> if (controllerOnTop) TOP else BOTTOM
        Where.OTHER -> if (controllerOnTop) BOTTOM else TOP
    }

    /** Whether [after] shows the action did its job, given [before]; [controllerOnTop] resolves the screens. */
    fun passes(expect: Expect, before: String?, after: String?, controllerOnTop: Boolean = true): Boolean {
        if (after == null) return false
        return when (expect) {
            Expect.Changed -> after != before
            is Expect.Is -> after == expect.value
            is Expect.Rises -> compare(before, after, expect.parts) { b, a -> a > b }
            is Expect.Falls -> compare(before, after, expect.parts) { b, a -> a < b }
            Expect.RefreshToggled -> {
                val (min, peak) = after.split('/').map { it.toFloatOrNull() }.let { it.getOrNull(0) to it.getOrNull(1) }
                val peakBefore = before?.split('/')?.getOrNull(1)?.toFloatOrNull()
                min != null && min == peak && peak != peakBefore
            }
            is Expect.AppOn -> resolve(expect.screen, controllerOnTop) in appScreens(after)
            is Expect.AppNotOn -> resolve(expect.screen, controllerOnTop) !in appScreens(after)
            Expect.AppGone -> appScreens(after).isEmpty() && !appRunning(after)
            Expect.AppSwapped -> {
                val was = appScreens(before)
                val now = appScreens(after)
                was.size == 1 && now.size == 1 && was != now
            }
            Expect.NoApps -> after.isEmpty()
            Expect.Asked -> false
        }
    }

    /** The [Probe.TEST_APP] reading for the screens it shows on and whether it runs. */
    fun testAppReading(screens: Set<String>, running: Boolean): String =
        listOf(TOP, BOTTOM).filter { it in screens }.joinToString("+").ifEmpty { NONE } + "|" + if (running) RUNNING else "stopped"

    fun appScreens(reading: String?): Set<String> =
        reading?.substringBefore('|')?.split('+')?.filter { it == TOP || it == BOTTOM }?.toSet().orEmpty()

    fun appRunning(reading: String?): Boolean = reading?.substringAfter('|', "") == RUNNING

    /** A level that sits at a limit ([low] or [high]) goes to the middle first, so a step either way shows. */
    fun atLimit(value: Float, low: Float, high: Float): Boolean = value <= low || value >= high

    private fun compare(before: String?, after: String, parts: List<Int>, test: (Float, Float) -> Boolean): Boolean {
        val was = before?.split(',')?.map { it.toFloatOrNull() } ?: return false
        val now = after.split(',').map { it.toFloatOrNull() }
        return parts.all { index ->
            val b = was.getOrNull(index)
            val a = now.getOrNull(index)
            b != null && a != null && test(b, a)
        }
    }
}
