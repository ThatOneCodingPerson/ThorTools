package io.github.thatonecodingperson.thortools.models

import androidx.annotation.StringRes
import io.github.thatonecodingperson.thortools.R
import io.github.thatonecodingperson.thortools.tools.ShellExecutor

sealed class FanMode(val id: String, val settingsValue: Int, @StringRes val textRes: Int) {
    data object Off : FanMode("fanOff", 0, R.string.fanOff)
    data object Quiet : FanMode("quiet", 1, R.string.quiet)
    data object Smart : FanMode("smart", 4, R.string.smart)
    data object Sport : FanMode("sport", 5, R.string.sport)
    data object Unknown : FanMode("unknown", -1, R.string.unknown)

    fun enable(executor: ShellExecutor) {
        if (this != Unknown) {
            executor.setIntSystemSetting(FAN_MODE, settingsValue)
        }
    }

    companion object {
        private const val FAN_MODE = "fan_mode"

        fun getMode(executor: ShellExecutor) = when (executor.getIntSystemSetting(FAN_MODE, Quiet.settingsValue)) {
            Quiet.settingsValue -> Quiet
            Smart.settingsValue -> Smart
            Sport.settingsValue -> Sport
            Off.settingsValue -> Off
            else -> Unknown
        }

        /** Cycles quiet, smart and sport. Off is left out so cycling can't stop the fan under load. */
        fun next(current: FanMode): FanMode = when (current) {
            Quiet -> Smart
            Smart -> Sport
            Sport, Off, Unknown -> Quiet
        }

        fun getById(id: String?) = when (id) {
            Quiet.id -> Quiet
            Smart.id -> Smart
            Sport.id -> Sport
            Off.id -> Off
            else -> Unknown
        }

        fun getDisabledFanModes(perfModeKey: String?): List<String> = when (perfModeKey) {
            PerfMode.Standard.id -> listOf(NoChange.KEY, Unknown.id)
            PerfMode.Performance.id -> listOf(NoChange.KEY, Unknown.id, Off.id)
            PerfMode.HighPerformance.id -> listOf(NoChange.KEY, Unknown.id, Off.id, Quiet.id)
            else -> emptyList()
        }
    }
}
