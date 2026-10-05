package io.github.thatonecodingperson.thortools.actions

import androidx.annotation.StringRes
import io.github.thatonecodingperson.thortools.R

enum class ActionCategory(@StringRes val label: Int) {
    CONTROLLER(R.string.actionCategoryController),
    SCREENS(R.string.actionCategoryScreens),
    SYSTEM(R.string.actionCategorySystem),
    LEVELS(R.string.actionCategoryLevels),
    PERFORMANCE(R.string.actionCategoryPerformance),
    APPS(R.string.actionCategoryApps),
}

/**
 * Something a panel tile can do. [id] is stored in the panel layout, so it must never change. [needsHelper] actions run
 * inside the root input helper.
 */
enum class ThorAction(
    val id: String,
    @StringRes val label: Int,
    @StringRes val description: Int,
    val category: ActionCategory,
    val needsApp: Boolean = false,
    val needsHelper: Boolean = false,
) {
    TOGGLE_LAYOUT("controller_toggle_layout", R.string.actionToggleLayout, R.string.actionToggleLayoutInfo, ActionCategory.CONTROLLER),
    CYCLE_CONTROLLER_STYLE(
        "controller_cycle_style",
        R.string.actionCycleControllerStyle,
        R.string.actionCycleControllerStyleInfo,
        ActionCategory.CONTROLLER,
    ),
    CYCLE_L2R2("controller_cycle_l2r2", R.string.actionCycleL2r2, R.string.actionCycleL2r2Info, ActionCategory.CONTROLLER),
    TOGGLE_AYN_MOUSE("controller_ayn_mouse", R.string.actionAynMouse, R.string.actionAynMouseInfo, ActionCategory.CONTROLLER),
    CONTROLLER_TO_TOP("focus_top", R.string.actionControllerToTop, R.string.actionControllerToTopInfo, ActionCategory.CONTROLLER),
    CONTROLLER_TO_BOTTOM(
        "focus_bottom",
        R.string.actionControllerToBottom,
        R.string.actionControllerToBottomInfo,
        ActionCategory.CONTROLLER,
    ),
    TOGGLE_CONTROLLER_LOCK(
        "focus_lock_toggle",
        R.string.actionControllerLock,
        R.string.actionControllerLockInfo,
        ActionCategory.CONTROLLER,
    ),
    LOCK_CONTROLLER_BOTTOM(
        "focus_lock_bottom",
        R.string.actionControllerLockBottom,
        R.string.actionControllerLockBottomInfo,
        ActionCategory.CONTROLLER,
        needsHelper = true,
    ),
    LOCK_CONTROLLER_HERE(
        "focus_lock_here",
        R.string.actionControllerLockHere,
        R.string.actionControllerLockHereInfo,
        ActionCategory.CONTROLLER,
        needsHelper = true,
    ),
    SWAP_SCREENS("screens_swap", R.string.actionSwapScreens, R.string.actionSwapScreensInfo, ActionCategory.SCREENS, needsHelper = true),
    TOGGLE_BOTTOM_SCREEN(
        "screens_bottom_toggle",
        R.string.actionToggleBottomScreen,
        R.string.actionToggleBottomScreenInfo,
        ActionCategory.SCREENS,
    ),
    CLOSE_OTHER_SCREEN_APP(
        "screens_close_other",
        R.string.actionCloseOtherScreenApp,
        R.string.actionCloseOtherScreenAppInfo,
        ActionCategory.SCREENS,
        needsHelper = true,
    ),
    HOME_TOP("home_top", R.string.actionHomeTop, R.string.actionHomeTopInfo, ActionCategory.SCREENS),
    HOME_BOTTOM("home_bottom", R.string.actionHomeBottom, R.string.actionHomeBottomInfo, ActionCategory.SCREENS),
    HOME_BOTH("home_both", R.string.actionHomeBoth, R.string.actionHomeBothInfo, ActionCategory.SCREENS),
    BACK("system_back", R.string.actionBack, R.string.actionBackInfo, ActionCategory.SYSTEM),
    HOME("system_home", R.string.actionHome, R.string.actionHomeInfo, ActionCategory.SYSTEM),
    RECENTS("system_recents", R.string.actionRecents, R.string.actionRecentsInfo, ActionCategory.SYSTEM),
    NOTIFICATIONS("system_notifications", R.string.actionNotifications, R.string.actionNotificationsInfo, ActionCategory.SYSTEM),
    QUICK_SETTINGS("system_quick_settings", R.string.actionQuickSettings, R.string.actionQuickSettingsInfo, ActionCategory.SYSTEM),
    SCREENSHOT("system_screenshot", R.string.actionScreenshot, R.string.actionScreenshotInfo, ActionCategory.SYSTEM),
    LOCK_SCREEN("system_lock", R.string.actionLockScreen, R.string.actionLockScreenInfo, ActionCategory.SYSTEM),
    CLOSE_APP("system_close_app", R.string.actionCloseApp, R.string.actionCloseAppInfo, ActionCategory.SYSTEM),
    CLEAR_BACKGROUND("system_clear_background", R.string.actionClearBackground, R.string.actionClearBackgroundInfo, ActionCategory.SYSTEM),
    TOGGLE_STAY_AWAKE("system_stay_awake", R.string.actionStayAwake, R.string.actionStayAwakeInfo, ActionCategory.SYSTEM),
    SWIPE_UP("gesture_swipe_up", R.string.actionSwipeUp, R.string.actionSwipeUpInfo, ActionCategory.SYSTEM),
    SWIPE_DOWN("gesture_swipe_down", R.string.actionSwipeDown, R.string.actionSwipeDownInfo, ActionCategory.SYSTEM),
    SWIPE_LEFT("gesture_swipe_left", R.string.actionSwipeLeft, R.string.actionSwipeLeftInfo, ActionCategory.SYSTEM),
    SWIPE_RIGHT("gesture_swipe_right", R.string.actionSwipeRight, R.string.actionSwipeRightInfo, ActionCategory.SYSTEM),
    AYN_DRAWER("ayn_drawer", R.string.actionAynDrawer, R.string.actionAynDrawerInfo, ActionCategory.SYSTEM),
    OPEN_QUICK_PANEL("open_quick_panel", R.string.actionOpenQuickPanel, R.string.actionOpenQuickPanelInfo, ActionCategory.SYSTEM),
    BRIGHTER("brightness_up", R.string.actionBrighter, R.string.actionBrighterInfo, ActionCategory.LEVELS, needsHelper = true),
    DIMMER("brightness_down", R.string.actionDimmer, R.string.actionDimmerInfo, ActionCategory.LEVELS, needsHelper = true),
    TOP_BRIGHTER(
        "brightness_top_up",
        R.string.actionTopBrighter,
        R.string.actionTopBrighterInfo,
        ActionCategory.LEVELS,
        needsHelper = true,
    ),
    TOP_DIMMER("brightness_top_down", R.string.actionTopDimmer, R.string.actionTopDimmerInfo, ActionCategory.LEVELS, needsHelper = true),
    BOTTOM_BRIGHTER(
        "brightness_bottom_up",
        R.string.actionBottomBrighter,
        R.string.actionBottomBrighterInfo,
        ActionCategory.LEVELS,
        needsHelper = true,
    ),
    BOTTOM_DIMMER(
        "brightness_bottom_down",
        R.string.actionBottomDimmer,
        R.string.actionBottomDimmerInfo,
        ActionCategory.LEVELS,
        needsHelper = true,
    ),
    LOUDER("volume_up", R.string.actionLouder, R.string.actionLouderInfo, ActionCategory.LEVELS),
    QUIETER("volume_down", R.string.actionQuieter, R.string.actionQuieterInfo, ActionCategory.LEVELS),
    CYCLE_PERFORMANCE("perf_cycle_mode", R.string.actionCyclePerformance, R.string.actionCyclePerformanceInfo, ActionCategory.PERFORMANCE),
    CYCLE_FAN("perf_cycle_fan", R.string.actionCycleFan, R.string.actionCycleFanInfo, ActionCategory.PERFORMANCE),
    TOGGLE_REFRESH_RATE(
        "display_toggle_refresh_rate",
        R.string.actionToggleRefreshRate,
        R.string.actionToggleRefreshRateInfo,
        ActionCategory.PERFORMANCE,
    ),
    LAUNCH_APP("launch_app", R.string.actionLaunchApp, R.string.actionLaunchAppInfo, ActionCategory.APPS, needsApp = true),
    ;

    companion object {
        fun byId(id: String?): ThorAction? = entries.find { it.id == id }
    }
}

/**
 * An action plus its argument, e.g. the package for [ThorAction.LAUNCH_APP]. [feedback]: show a short message with the
 * result. [alsoLock]: a controller move also locks the controller to that screen (again: unlocks). [alsoCleanMemory]:
 * closing background apps also cleans memory.
 */
data class ActionCall(
    val action: ThorAction,
    val arg: String? = null,
    val feedback: Boolean = false,
    val alsoLock: Boolean = false,
    val alsoCleanMemory: Boolean = false,
)
