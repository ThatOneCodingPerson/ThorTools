package io.github.thatonecodingperson.thortools.panel

import androidx.annotation.StringRes
import io.github.thatonecodingperson.thortools.R
import io.github.thatonecodingperson.thortools.actions.ThorAction

/** What the panel can show as a round tile: almost every action, plus opening Thor Tools. Ids are stored. */
object PanelTiles {
    const val THOR_TOOLS = "thor_tools"

    /** How a tile shows its state: a mode it cycles through, a switch, or a plain action. */
    enum class Look { MODE, SWITCH, ACTION }

    /** Opening an app needs a choice the panel can't offer; opening the panel from the panel makes no sense. */
    val actions: List<ThorAction> = ThorAction.entries.filter { !it.needsApp && it != ThorAction.OPEN_QUICK_PANEL }

    val ids: Set<String> = actions.map { it.id }.toSet() + THOR_TOOLS

    fun action(id: String): ThorAction? = ThorAction.byId(id)?.takeIf { it in actions }

    fun look(id: String): Look = when (action(id)) {
        ThorAction.TOGGLE_LAYOUT,
        ThorAction.CYCLE_CONTROLLER_STYLE,
        ThorAction.CYCLE_L2R2,
        ThorAction.CYCLE_PERFORMANCE,
        ThorAction.CYCLE_FAN,
        ThorAction.TOGGLE_REFRESH_RATE,
        -> Look.MODE
        ThorAction.TOGGLE_BOTTOM_SCREEN,
        ThorAction.TOGGLE_STAY_AWAKE,
        ThorAction.TOGGLE_AYN_MOUSE,
        ThorAction.TOGGLE_CONTROLLER_LOCK,
        ThorAction.LOCK_CONTROLLER_BOTTOM,
        ThorAction.LOCK_CONTROLLER_HERE,
        -> Look.SWITCH
        else -> Look.ACTION
    }

    /** How long after the panel closes most actions that leave it run (hotkeys pressed with the panel open too). */
    const val LEAVE_MS = 250L

    /**
     * Null: the action runs with the panel open. Otherwise the panel closes first and the action runs this much later,
     * so it lands on the screen below (and a screenshot doesn't show the panel). The Thor Tools tile doesn't wait: see
     * `QuickPanel.openThorTools`.
     */
    fun leaveDelayMs(id: String): Long? = when (action(id)) {
        null -> if (id == THOR_TOOLS) OPEN_APP_MS else null
        ThorAction.SCREENSHOT -> SCREENSHOT_MS
        ThorAction.TOGGLE_BOTTOM_SCREEN -> SCREEN_MODE_MS
        ThorAction.SWAP_SCREENS,
        ThorAction.CLOSE_OTHER_SCREEN_APP,
        ThorAction.CLOSE_APP,
        ThorAction.RECENTS,
        ThorAction.AYN_DRAWER,
        ThorAction.HOME,
        ThorAction.HOME_TOP,
        ThorAction.HOME_BOTTOM,
        ThorAction.HOME_BOTH,
        ThorAction.BACK,
        ThorAction.NOTIFICATIONS,
        ThorAction.QUICK_SETTINGS,
        ThorAction.LOCK_SCREEN,
        ThorAction.SWIPE_UP,
        ThorAction.SWIPE_DOWN,
        ThorAction.SWIPE_LEFT,
        ThorAction.SWIPE_RIGHT,
        ThorAction.CONTROLLER_TO_TOP,
        ThorAction.CONTROLLER_TO_BOTTOM,
        // The panel has the controller while it is open; a lock only makes sense after closing.
        ThorAction.TOGGLE_CONTROLLER_LOCK,
        ThorAction.LOCK_CONTROLLER_BOTTOM,
        ThorAction.LOCK_CONTROLLER_HERE,
        -> LEAVE_MS
        else -> null
    }

    /** Short names that fit under a round tile; the full action names are used everywhere else. */
    @StringRes
    fun label(id: String): Int = when (action(id)) {
        null -> R.string.panelTileThorTools
        ThorAction.TOGGLE_LAYOUT -> R.string.panelTileLayout
        ThorAction.CYCLE_CONTROLLER_STYLE -> R.string.panelTileStyle
        ThorAction.CYCLE_L2R2 -> R.string.panelTileL2r2
        ThorAction.TOGGLE_AYN_MOUSE -> R.string.panelTileMouse
        ThorAction.CONTROLLER_TO_TOP -> R.string.panelTileToTop
        ThorAction.CONTROLLER_TO_BOTTOM -> R.string.panelTileToBottom
        ThorAction.TOGGLE_CONTROLLER_LOCK -> R.string.panelTileLock
        ThorAction.LOCK_CONTROLLER_BOTTOM -> R.string.panelTileLockBottom
        ThorAction.LOCK_CONTROLLER_HERE -> R.string.panelTileLockHere
        ThorAction.SWAP_SCREENS -> R.string.panelTileSwap
        ThorAction.TOGGLE_BOTTOM_SCREEN -> R.string.panelTileBottomScreen
        ThorAction.CLOSE_OTHER_SCREEN_APP -> R.string.panelTileCloseOther
        ThorAction.HOME_TOP -> R.string.panelTileHomeTop
        ThorAction.HOME_BOTTOM -> R.string.panelTileHomeBottom
        ThorAction.HOME_BOTH -> R.string.panelTileHomeBoth
        ThorAction.BACK -> R.string.actionBack
        ThorAction.HOME -> R.string.actionHome
        ThorAction.RECENTS -> R.string.panelTileRecents
        ThorAction.NOTIFICATIONS -> R.string.actionNotifications
        ThorAction.QUICK_SETTINGS -> R.string.actionQuickSettings
        ThorAction.SCREENSHOT -> R.string.panelTileScreenshot
        ThorAction.LOCK_SCREEN -> R.string.actionLockScreen
        ThorAction.CLOSE_APP -> R.string.panelTileCloseApp
        ThorAction.CLEAR_BACKGROUND -> R.string.panelTileCleanUp
        ThorAction.TOGGLE_STAY_AWAKE -> R.string.panelTileStayAwake
        ThorAction.SWIPE_UP -> R.string.actionSwipeUp
        ThorAction.SWIPE_DOWN -> R.string.actionSwipeDown
        ThorAction.SWIPE_LEFT -> R.string.actionSwipeLeft
        ThorAction.SWIPE_RIGHT -> R.string.actionSwipeRight
        ThorAction.AYN_DRAWER -> R.string.panelTileAynDrawer
        ThorAction.OPEN_QUICK_PANEL -> R.string.actionOpenQuickPanel
        ThorAction.BRIGHTER -> R.string.panelTileBrighter
        ThorAction.DIMMER -> R.string.panelTileDimmer
        ThorAction.TOP_BRIGHTER -> R.string.panelTileTopBrighter
        ThorAction.TOP_DIMMER -> R.string.panelTileTopDimmer
        ThorAction.BOTTOM_BRIGHTER -> R.string.panelTileBottomBrighter
        ThorAction.BOTTOM_DIMMER -> R.string.panelTileBottomDimmer
        ThorAction.LOUDER -> R.string.panelTileLouder
        ThorAction.QUIETER -> R.string.panelTileQuieter
        ThorAction.CYCLE_PERFORMANCE -> R.string.panelTilePerformance
        ThorAction.CYCLE_FAN -> R.string.panelTileFan
        ThorAction.TOGGLE_REFRESH_RATE -> R.string.panelTileRefresh
        ThorAction.LAUNCH_APP -> R.string.actionLaunchApp
    }

    private const val SCREENSHOT_MS = 450L
    private const val SCREEN_MODE_MS = 300L
    private const val OPEN_APP_MS = 150L
}
