package io.github.thatonecodingperson.thortools.models

import androidx.annotation.StringRes
import io.github.thatonecodingperson.thortools.R
import io.github.thatonecodingperson.thortools.tools.ShellExecutor

sealed class ControllerStyle(
    val id: String,
    private val tempAbxyLayout: Int,
    private val noCreateGamepadLayout: Int,
    private val flipButtonLayout: Int,
    @StringRes val textRes: Int,
    @StringRes val infoRes: Int,
) {
    data object Xbox : ControllerStyle("xbox", 0, 0, 1, R.string.xbox, R.string.xboxInfo)
    data object Odin : ControllerStyle("odin", 1, 0, 0, R.string.odin, R.string.odinInfo)
    data object Disconnect : ControllerStyle("disconnect", 2, 1, 0, R.string.disconnect, R.string.disconnectInfo)
    data object Unknown : ControllerStyle("unknown", -1, -1, -1, R.string.unknown, R.string.modeUnknownInfo)

    fun enable(executor: ShellExecutor) {
        if (this != Unknown) {
            executor.setIntSystemSetting(TEMP_ABXY_LAYOUT_MODE, tempAbxyLayout)
            executor.setIntSystemSetting(NO_CREATE_GAMEPAD_BUTTON_LAYOUT, noCreateGamepadLayout)
            executor.setIntSystemSetting(FLIP_BUTTON_LAYOUT, flipButtonLayout)
        }
    }

    companion object {
        private const val TEMP_ABXY_LAYOUT_MODE = "temp_abxy_layout_mode"
        private const val NO_CREATE_GAMEPAD_BUTTON_LAYOUT = "no_create_gamepad_button_layout"
        private const val FLIP_BUTTON_LAYOUT = "flip_button_layout"

        fun getStyle(executor: ShellExecutor): ControllerStyle =
            if (executor.getIntSystemSetting(NO_CREATE_GAMEPAD_BUTTON_LAYOUT, 0) == 1) {
                Disconnect
            } else if (executor.getIntSystemSetting(FLIP_BUTTON_LAYOUT, 0) == 1) {
                Xbox
            } else {
                Odin
            }

        /** The style in use for showing it: Unknown when PServer can't be reached, where [getStyle] says Standard. */
        fun readOrUnknown(executor: ShellExecutor): ControllerStyle = if (executor.pServerAvailable) getStyle(executor) else Unknown

        /** The style after [current] in the Xbox, Standard, disconnect cycle, skipping [disabled]. */
        fun next(current: ControllerStyle, disabled: ControllerStyle?): ControllerStyle = when (current) {
            Xbox -> if (disabled == Odin) Disconnect else Odin
            Odin -> if (disabled == Disconnect) Xbox else Disconnect
            Disconnect, Unknown -> if (disabled == Xbox) Odin else Xbox
        }

        fun toggledLayout(current: ControllerStyle): ControllerStyle = if (current == Xbox) Odin else Xbox

        /** The styles a user can pick, in the order the cycle steps through them. */
        val choices: List<ControllerStyle> get() = listOf(Xbox, Odin, Disconnect)

        fun getById(id: String?) = when (id) {
            Xbox.id -> Xbox
            Odin.id -> Odin
            Disconnect.id -> Disconnect
            else -> Unknown
        }
    }
}
