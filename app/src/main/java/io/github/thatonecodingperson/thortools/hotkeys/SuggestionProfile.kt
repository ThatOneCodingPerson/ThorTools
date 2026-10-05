package io.github.thatonecodingperson.thortools.hotkeys

import androidx.annotation.StringRes
import io.github.thatonecodingperson.thortools.R
import io.github.thatonecodingperson.thortools.actions.ThorAction

private val closeHere = CloseAppArg.encode(LaunchScreen.HERE)

private fun hotkey(
    button: PadButton,
    second: PadButton?,
    press: PressKind,
    action: ThorAction,
    arg: String? = null,
    lock: Boolean = false,
) = Hotkey(button, second, press, action, arg, lock = lock)

/**
 * Ready-made sets of hotkeys, shown as tabs under Suggestions; any single hotkey can be added. Users can't make their own
 * profiles. [id] must never change. [credit]: the tab shows where the idea came from. Every
 * profile starts with the AYN button's tap opening the Thor Tools quick panel, so it can be added back from any tab.
 */
enum class SuggestionProfile(
    val id: String,
    @StringRes val title: Int,
    @StringRes val info: Int,
    val hotkeys: List<Hotkey>,
    val credit: Boolean = false,
) {
    /** The set the Thor Profile tab offers. */
    THOR(
        "thor",
        R.string.suggestionProfileThor,
        R.string.suggestionProfileThorInfo,
        listOf(
            HotkeyList.aynPanel,
            hotkey(PadButton.AYN, null, PressKind.HOLD, ThorAction.AYN_DRAWER),
            hotkey(PadButton.HOME, null, PressKind.DOUBLE, ThorAction.HOME_BOTH),
            hotkey(PadButton.HOME, PadButton.RSTICK_DOWN, PressKind.TAP, ThorAction.CONTROLLER_TO_BOTTOM, lock = true),
            hotkey(PadButton.HOME, PadButton.RSTICK_UP, PressKind.TAP, ThorAction.CONTROLLER_TO_TOP, lock = true),
            hotkey(PadButton.HOME, PadButton.R3, PressKind.DOUBLE, ThorAction.SWAP_SCREENS),
            hotkey(PadButton.HOME, PadButton.R3, PressKind.TAP, ThorAction.RECENTS),
            hotkey(PadButton.HOME, PadButton.R3, PressKind.TRIPLE, ThorAction.TOGGLE_REFRESH_RATE),
            hotkey(PadButton.HOME, null, PressKind.TRIPLE, ThorAction.CLEAR_BACKGROUND),
            hotkey(PadButton.AYN, null, PressKind.DOUBLE, ThorAction.TOGGLE_LAYOUT),
        ),
    ),

    /** Only combos on Home: Back, AYN and every game button stay as they are (emulators that use Back). */
    HOME_ONLY(
        "home_only",
        R.string.suggestionProfileHomeOnly,
        R.string.suggestionProfileHomeOnlyInfo,
        listOf(
            HotkeyList.aynPanel,
            hotkey(PadButton.HOME, PadButton.R3, PressKind.TAP, ThorAction.SWAP_SCREENS),
            hotkey(PadButton.HOME, PadButton.L1, PressKind.TAP, ThorAction.CONTROLLER_TO_TOP),
            hotkey(PadButton.HOME, PadButton.R1, PressKind.TAP, ThorAction.CONTROLLER_TO_BOTTOM),
            hotkey(PadButton.HOME, PadButton.R2, PressKind.TAP, ThorAction.BRIGHTER),
            hotkey(PadButton.HOME, PadButton.L2, PressKind.TAP, ThorAction.DIMMER),
            hotkey(PadButton.HOME, PadButton.X, PressKind.TAP, ThorAction.SCREENSHOT),
            hotkey(PadButton.HOME, PadButton.SELECT, PressKind.TAP, ThorAction.RECENTS),
            hotkey(PadButton.HOME, PadButton.START, PressKind.TAP, ThorAction.CYCLE_PERFORMANCE),
            hotkey(PadButton.HOME, PadButton.L3, PressKind.TAP, ThorAction.HOME_BOTH),
            hotkey(PadButton.HOME, PadButton.B, PressKind.HOLD, ThorAction.CLOSE_APP, closeHere),
        ),
    ),

    /** A small start. */
    ESSENTIALS(
        "essentials",
        R.string.suggestionProfileEssentials,
        R.string.suggestionProfileEssentialsInfo,
        listOf(
            HotkeyList.aynPanel,
            hotkey(PadButton.HOME, null, PressKind.DOUBLE, ThorAction.HOME_BOTH),
            hotkey(PadButton.HOME, PadButton.R3, PressKind.TAP, ThorAction.SWAP_SCREENS),
            hotkey(PadButton.HOME, PadButton.L1, PressKind.TAP, ThorAction.CONTROLLER_TO_TOP),
            hotkey(PadButton.HOME, PadButton.R1, PressKind.TAP, ThorAction.CONTROLLER_TO_BOTTOM),
        ),
    ),

    /** A game on one screen, a guide or video on the other. */
    TWO_APPS(
        "two_apps",
        R.string.suggestionProfileTwoApps,
        R.string.suggestionProfileTwoAppsInfo,
        listOf(
            HotkeyList.aynPanel,
            hotkey(PadButton.HOME, PadButton.R3, PressKind.TAP, ThorAction.SWAP_SCREENS),
            hotkey(PadButton.HOME, PadButton.L1, PressKind.TAP, ThorAction.CONTROLLER_TO_TOP),
            hotkey(PadButton.HOME, PadButton.R1, PressKind.TAP, ThorAction.CONTROLLER_TO_BOTTOM),
            hotkey(PadButton.HOME, PadButton.L3, PressKind.TAP, ThorAction.LOCK_CONTROLLER_HERE),
            hotkey(PadButton.HOME, PadButton.B, PressKind.HOLD, ThorAction.CLOSE_OTHER_SCREEN_APP),
            hotkey(PadButton.HOME, PadButton.SELECT, PressKind.TAP, ThorAction.TOGGLE_BOTTOM_SCREEN),
            hotkey(PadButton.HOME, PadButton.R2, PressKind.TAP, ThorAction.BOTTOM_BRIGHTER),
            hotkey(PadButton.HOME, PadButton.L2, PressKind.TAP, ThorAction.BOTTOM_DIMMER),
        ),
    ),

    /** The 0.14.0 and 0.15.0 suggestions, in another order. */
    WAYFINDER_LIKE(
        "wayfinder_like",
        R.string.suggestionProfileWayfinder,
        R.string.suggestionProfileWayfinderInfo,
        listOf(
            HotkeyList.aynPanel,
            hotkey(PadButton.HOME, PadButton.Y, PressKind.TAP, ThorAction.SCREENSHOT),
            hotkey(PadButton.BACK, null, PressKind.DOUBLE, ThorAction.RECENTS),
            hotkey(PadButton.HOME, PadButton.R1, PressKind.TAP, ThorAction.CONTROLLER_TO_BOTTOM),
            hotkey(PadButton.HOME, PadButton.START, PressKind.TAP, ThorAction.CYCLE_PERFORMANCE),
            hotkey(PadButton.BACK, null, PressKind.HOLD, ThorAction.SWAP_SCREENS),
            hotkey(PadButton.HOME, PadButton.L2, PressKind.TAP, ThorAction.DIMMER),
            hotkey(PadButton.HOME, PadButton.B, PressKind.HOLD, ThorAction.CLOSE_APP, closeHere),
            hotkey(PadButton.HOME, null, PressKind.DOUBLE, ThorAction.HOME_BOTH),
            hotkey(PadButton.HOME, PadButton.SELECT, PressKind.TAP, ThorAction.TOGGLE_CONTROLLER_LOCK),
            hotkey(PadButton.BACK, null, PressKind.TRIPLE, ThorAction.CLEAR_BACKGROUND),
            hotkey(PadButton.HOME, PadButton.L1, PressKind.TAP, ThorAction.CONTROLLER_TO_TOP),
            hotkey(PadButton.HOME, PadButton.R2, PressKind.TAP, ThorAction.BRIGHTER),
        ),
        credit = true,
    ),
    ;

    companion object {
        const val WAYFINDER_URL = "https://github.com/Thor-Wayfinder/thor-wayfinder"
    }
}
