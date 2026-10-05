package io.github.thatonecodingperson.thortools.models

import androidx.annotation.StringRes
import io.github.thatonecodingperson.thortools.R
import io.github.thatonecodingperson.thortools.tools.ShellExecutor

/** AYN's trigger mode. [infoRes] says in plain words what the triggers send to games in this mode. */
sealed class L2R2Style(val id: String, val settingsValue: Int, @StringRes val textRes: Int, @StringRes val infoRes: Int) {
    data object Analog : L2R2Style("analog", 0, R.string.analog, R.string.l2r2AnalogInfo)
    data object Digital : L2R2Style("digital", 1, R.string.digital, R.string.l2r2DigitalInfo)
    data object Both : L2R2Style("both", 2, R.string.both, R.string.l2r2BothInfo)
    data object Unknown : L2R2Style("unknown", -1, R.string.unknown, R.string.modeUnknownInfo)

    fun enable(executor: ShellExecutor) {
        if (this != Unknown) {
            executor.setIntSystemSetting(TRIGGER_INPUT_MODE, settingsValue)
        }
    }

    companion object {
        private const val TRIGGER_INPUT_MODE = "trigger_input_mode"

        fun getStyle(executor: ShellExecutor) = fromSettingsValue(executor.getIntSystemSetting(TRIGGER_INPUT_MODE, Analog.settingsValue))

        /** The mode in use for showing it; unlike [getStyle], an unreadable setting is Unknown rather than Analog. */
        fun readOrUnknown(executor: ShellExecutor) =
            fromSettingsValue(executor.getIntSystemSetting(TRIGGER_INPUT_MODE, Unknown.settingsValue))

        fun fromSettingsValue(value: Int) = when (value) {
            Analog.settingsValue -> Analog
            Digital.settingsValue -> Digital
            Both.settingsValue -> Both
            else -> Unknown
        }

        /** The modes a user can pick, in the order the cycle steps through them. */
        val choices: List<L2R2Style> get() = listOf(Analog, Digital, Both)

        /** The mode after [current] in the analog, digital, both cycle, skipping [disabled]. */
        fun next(current: L2R2Style, disabled: L2R2Style?): L2R2Style = when (current) {
            Analog -> if (disabled == Digital) Both else Digital
            Digital -> if (disabled == Both) Analog else Both
            Both, Unknown -> if (disabled == Analog) Digital else Analog
        }

        fun getById(id: String?) = when (id) {
            Analog.id -> Analog
            Digital.id -> Digital
            Both.id -> Both
            else -> Unknown
        }
    }
}
