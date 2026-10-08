package io.github.thatonecodingperson.thortools.main

import androidx.annotation.StringRes
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import io.github.thatonecodingperson.thortools.coexist.OdinToolsState
import io.github.thatonecodingperson.thortools.extras.KeyboardPlace
import io.github.thatonecodingperson.thortools.leds.LedMode
import io.github.thatonecodingperson.thortools.tools.DeviceType
import io.github.thatonecodingperson.thortools.tools.DeviceType.THOR
import io.github.thatonecodingperson.thortools.ui.theme.ThorPalette

data class MainUiModel(
    val deviceType: DeviceType = THOR,
    val deviceVersion: String = "",
    val showIncompatibleDeviceDialog: Boolean = false,
    val showPServerNotAvailableDialog: Boolean = false,

    val odinTools: OdinToolsState = OdinToolsState.Absent,
    val accessNeedsAttention: Boolean = false,
    val serviceRunning: Boolean = true,
    val helperRunning: Boolean = true,
    /** Hotkeys on Home that AYN's double-press Home blocks (single-press Home is off); 0 when nothing clashes. */
    val homeBlocked: Int = 0,
    val summary: HomeSummary = HomeSummary(),
)

/** What the main menu's cards say about each section; null where it isn't known yet. */
data class HomeSummary(
    @StringRes val controllerStyle: Int? = null,
    @StringRes val l2r2: Int? = null,
    val hotkeys: Int = 0,
    val panelPages: Int = 0,
    val panelWidgets: Int = 0,
    val profiles: Int? = null,
    val lidActions: Int = 0,
    val chargeAlert: Boolean = false,
    /** The charge limit automation's levels; null while it is off. */
    val chargeLimit: IntRange? = null,
    /** The theme's name, or null for Android's own colours. */
    val theme: ThorPalette? = null,
    val ledMode: LedMode = LedMode.AYN,
    /** Where the keyboard appears; null until read. */
    val keyboard: KeyboardPlace? = null,
)

/** [supporting]: a line under the title, e.g. what a mode does. */
class CheckboxPreferenceUiModel(
    val key: String,
    @StringRes val text: Int,
    initialChecked: Boolean = false,
    @StringRes val supporting: Int? = null,
) {
    var checked by mutableStateOf(initialChecked)
}
