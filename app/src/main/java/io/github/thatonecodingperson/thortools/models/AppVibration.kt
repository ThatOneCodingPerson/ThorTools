package io.github.thatonecodingperson.thortools.models

import androidx.annotation.StringRes
import io.github.thatonecodingperson.thortools.R

/** What a profile does to the vibration while its app is in front. [strengthMv] null switches vibration off. */
enum class AppVibration(val id: String, val strengthMv: Int?, @StringRes val textRes: Int) {
    OFF("off", null, R.string.appVibrationOff),
    LIGHT("light", 2000, R.string.appVibrationLight),
    NORMAL("normal", 3200, R.string.appVibrationNormal),
    STRONG("strong", 4500, R.string.appVibrationStrong),
    MAX("max", 5800, R.string.appVibrationMax),
    ;

    companion object {
        /** The haptics driver's range (`user_vmax_mv`). */
        val RANGE_MV = 1000..5800

        fun byId(id: String?): AppVibration? = entries.find { it.id == id }
    }
}
