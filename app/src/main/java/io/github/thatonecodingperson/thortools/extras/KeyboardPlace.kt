package io.github.thatonecodingperson.thortools.extras

import androidx.annotation.StringRes
import io.github.thatonecodingperson.thortools.R
import io.github.thatonecodingperson.thortools.tools.ShellExecutor

/**
 * Where the on-screen keyboard appears: AYN's own setting, two system settings that Android reads each time the
 * keyboard opens. Not fixed means it opens on the screen being typed on.
 */
enum class KeyboardPlace(@StringRes val textRes: Int, @StringRes val infoRes: Int) {
    SAME(R.string.keyboardSame, R.string.keyboardSameInfo),
    TOP(R.string.keyboardTop, R.string.keyboardTopInfo),
    BOTTOM(R.string.keyboardBottom, R.string.keyboardBottomInfo),
    ;

    /** The values for [KEY_FIXED] and [KEY_ON_SECOND]; null leaves the second as it is (not read while not fixed). */
    fun settings(): Pair<Int, Int?> = when (this) {
        SAME -> 0 to null
        TOP -> 1 to 0
        BOTTOM -> 1 to 1
    }

    companion object {
        const val KEY_FIXED = "ime_fixed"
        const val KEY_ON_SECOND = "ime_show_on_second"

        fun from(fixed: Int?, onSecond: Int?): KeyboardPlace = when {
            fixed != 1 -> SAME
            onSecond == 1 -> BOTTOM
            else -> TOP
        }

        /** Through PServer: never on the main thread. */
        fun read(executor: ShellExecutor): KeyboardPlace =
            from(executor.getIntSystemSetting(KEY_FIXED, 0), executor.getIntSystemSetting(KEY_ON_SECOND, 0))

        /** Through PServer: never on the main thread. Returns the place as read back afterwards. */
        fun write(executor: ShellExecutor, place: KeyboardPlace): KeyboardPlace {
            val (fixed, onSecond) = place.settings()
            if (onSecond != null) executor.setIntSystemSetting(KEY_ON_SECOND, onSecond)
            executor.setIntSystemSetting(KEY_FIXED, fixed)
            return read(executor)
        }
    }
}
