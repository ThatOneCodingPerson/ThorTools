package io.github.thatonecodingperson.thortools.main

import androidx.annotation.StringRes
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import io.github.thatonecodingperson.thortools.coexist.OdinToolsState
import io.github.thatonecodingperson.thortools.tools.DeviceType
import io.github.thatonecodingperson.thortools.tools.DeviceType.THOR

data class MainUiModel(
    val deviceType: DeviceType = THOR,
    val deviceVersion: String = "",
    val showIncompatibleDeviceDialog: Boolean = false,
    val showPServerNotAvailableDialog: Boolean = false,

    val odinTools: OdinToolsState = OdinToolsState.Absent,
    val accessNeedsAttention: Boolean = false,
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
