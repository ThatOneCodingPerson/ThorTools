package io.github.thatonecodingperson.thortools.models

import androidx.annotation.StringRes
import io.github.thatonecodingperson.thortools.R

/** What a profile does to Android's navigation swipes while its app is in front (which parts go: Extra tools). */
enum class AppGestures(val id: String, val on: Boolean, @StringRes val textRes: Int) {
    OFF("off", false, R.string.appGesturesOff),
    ON("on", true, R.string.appGesturesOn),
    ;

    companion object {
        fun byId(id: String?): AppGestures? = entries.find { it.id == id }
    }
}
