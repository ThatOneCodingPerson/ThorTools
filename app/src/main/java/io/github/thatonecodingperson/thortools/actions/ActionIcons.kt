package io.github.thatonecodingperson.thortools.actions

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.VolumeDown
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.Air
import androidx.compose.material.icons.rounded.Animation
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.BlurLinear
import androidx.compose.material.icons.rounded.BrightnessHigh
import androidx.compose.material.icons.rounded.BrightnessLow
import androidx.compose.material.icons.rounded.Cancel
import androidx.compose.material.icons.rounded.CancelPresentation
import androidx.compose.material.icons.rounded.CleaningServices
import androidx.compose.material.icons.rounded.Dashboard
import androidx.compose.material.icons.rounded.DesktopWindows
import androidx.compose.material.icons.rounded.East
import androidx.compose.material.icons.rounded.Gamepad
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Mouse
import androidx.compose.material.icons.rounded.North
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material.icons.rounded.Screenshot
import androidx.compose.material.icons.rounded.South
import androidx.compose.material.icons.rounded.SpaceDashboard
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.Splitscreen
import androidx.compose.material.icons.rounded.SportsEsports
import androidx.compose.material.icons.rounded.SwapVert
import androidx.compose.material.icons.rounded.Swipe
import androidx.compose.material.icons.rounded.ToggleOn
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.VerticalAlignBottom
import androidx.compose.material.icons.rounded.VerticalAlignTop
import androidx.compose.material.icons.rounded.ViewCarousel
import androidx.compose.material.icons.rounded.West
import androidx.compose.ui.graphics.vector.ImageVector

/** The icon for each action, for the quick panel and its editor. */
val ThorAction.icon: ImageVector
    get() = when (this) {
        ThorAction.TOGGLE_LAYOUT -> Icons.Rounded.SportsEsports
        ThorAction.CYCLE_CONTROLLER_STYLE -> Icons.Rounded.Gamepad
        ThorAction.CYCLE_L2R2 -> Icons.Rounded.Tune
        ThorAction.TOGGLE_AYN_MOUSE -> Icons.Rounded.Mouse
        ThorAction.TOGGLE_DESKTOP -> Icons.Rounded.DesktopWindows
        ThorAction.CONTROLLER_TO_TOP -> Icons.Rounded.VerticalAlignTop
        ThorAction.CONTROLLER_TO_BOTTOM -> Icons.Rounded.VerticalAlignBottom
        ThorAction.TOGGLE_CONTROLLER_LOCK, ThorAction.LOCK_CONTROLLER_BOTTOM -> Icons.Rounded.Lock
        ThorAction.LOCK_CONTROLLER_HERE -> Icons.Rounded.PushPin
        ThorAction.SWAP_SCREENS -> Icons.Rounded.SwapVert
        ThorAction.REFRESH_SCREENS -> Icons.Rounded.BlurLinear
        ThorAction.TOGGLE_BOTTOM_SCREEN -> Icons.Rounded.Splitscreen
        ThorAction.CLOSE_OTHER_SCREEN_APP -> Icons.Rounded.CancelPresentation
        ThorAction.HOME_TOP, ThorAction.HOME_BOTTOM, ThorAction.HOME_BOTH, ThorAction.HOME -> Icons.Rounded.Home
        ThorAction.BACK -> Icons.AutoMirrored.Rounded.ArrowBack
        ThorAction.RECENTS -> Icons.Rounded.ViewCarousel
        ThorAction.NOTIFICATIONS -> Icons.Rounded.Notifications
        ThorAction.QUICK_SETTINGS -> Icons.Rounded.ToggleOn
        ThorAction.SCREENSHOT -> Icons.Rounded.Screenshot
        ThorAction.LOCK_SCREEN -> Icons.Rounded.Bedtime
        ThorAction.CLOSE_APP -> Icons.Rounded.Cancel
        ThorAction.CLEAR_BACKGROUND -> Icons.Rounded.CleaningServices
        ThorAction.TOGGLE_STAY_AWAKE -> Icons.Rounded.LightMode
        ThorAction.TOGGLE_GESTURES -> Icons.Rounded.Swipe
        ThorAction.SWIPE_UP -> Icons.Rounded.North
        ThorAction.SWIPE_DOWN -> Icons.Rounded.South
        ThorAction.SWIPE_LEFT -> Icons.Rounded.West
        ThorAction.SWIPE_RIGHT -> Icons.Rounded.East
        ThorAction.AYN_DRAWER -> Icons.Rounded.Dashboard
        ThorAction.OPEN_QUICK_PANEL -> Icons.Rounded.SpaceDashboard
        ThorAction.BRIGHTER, ThorAction.TOP_BRIGHTER, ThorAction.BOTTOM_BRIGHTER -> Icons.Rounded.BrightnessHigh
        ThorAction.DIMMER, ThorAction.TOP_DIMMER, ThorAction.BOTTOM_DIMMER -> Icons.Rounded.BrightnessLow
        ThorAction.LOUDER -> Icons.AutoMirrored.Rounded.VolumeUp
        ThorAction.QUIETER -> Icons.AutoMirrored.Rounded.VolumeDown
        ThorAction.CYCLE_PERFORMANCE -> Icons.Rounded.Speed
        ThorAction.CYCLE_FAN -> Icons.Rounded.Air
        ThorAction.TOGGLE_REFRESH_RATE -> Icons.Rounded.Animation
        ThorAction.LAUNCH_APP -> Icons.Rounded.Apps
    }
