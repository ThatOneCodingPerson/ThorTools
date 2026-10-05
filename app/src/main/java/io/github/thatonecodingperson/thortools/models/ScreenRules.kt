package io.github.thatonecodingperson.thortools.models

import androidx.annotation.StringRes
import io.github.thatonecodingperson.thortools.R

/** A per-app profile's refresh rate. [id] is stored. */
enum class AppRefreshRate(val id: String, val hz: Int, @StringRes val textRes: Int) {
    LOW("60", RefreshRate.LOW, R.string.refreshRate60),
    HIGH("120", RefreshRate.HIGH, R.string.refreshRate120),
    ;

    companion object {
        fun byId(id: String?): AppRefreshRate? = entries.find { it.id == id }
    }
}

/** A per-app profile's rule for the bottom screen. [id] is stored. */
enum class BottomScreenRule(val id: String, @StringRes val textRes: Int, @StringRes val infoRes: Int) {
    OFF("off", R.string.bottomScreenRuleOff, R.string.bottomScreenRuleOffInfo),
    ON("on", R.string.bottomScreenRuleOn, R.string.bottomScreenRuleOnInfo),
    ;

    companion object {
        fun byId(id: String?): BottomScreenRule? = entries.find { it.id == id }
    }
}

/** AYN's dual-screen setting (`dual_screen_display_mode`): 0 both screens on, 1 the bottom screen off. */
object ScreenMode {
    const val KEY = "dual_screen_display_mode"
    const val BOTH_ON = 0
    const val BOTTOM_OFF = 1

    /**
     * The mode a profile's [rule] asks for, or null to leave it as it was. The bottom screen only goes off while the
     * app is on the top screen ([appOnTop]); off with the app on the bottom screen would hide the app itself.
     */
    fun forRule(rule: BottomScreenRule?, appOnTop: Boolean): Int? = when (rule) {
        BottomScreenRule.OFF -> BOTTOM_OFF.takeIf { appOnTop }
        BottomScreenRule.ON -> BOTH_ON
        null -> null
    }
}
