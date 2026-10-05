package io.github.thatonecodingperson.thortools.models

import io.github.thatonecodingperson.thortools.tools.ShellExecutor

object RefreshRate {
    const val LOW = 60
    const val HIGH = 120

    private const val MIN_REFRESH_RATE = "min_refresh_rate"
    private const val PEAK_REFRESH_RATE = "peak_refresh_rate"

    fun toggled(peak: Float): Int = if (peak > (LOW + HIGH) / 2) LOW else HIGH

    fun peak(executor: ShellExecutor): Float = executor.getStringSystemSetting(PEAK_REFRESH_RATE, "$LOW").toFloatOrNull() ?: LOW.toFloat()

    /** Pins the panel to [hz] by setting the minimum and the peak rate to the same value. */
    fun apply(executor: ShellExecutor, hz: Int) {
        executor.setStringSystemSetting(MIN_REFRESH_RATE, "$hz.0")
        executor.setStringSystemSetting(PEAK_REFRESH_RATE, "$hz.0")
    }

    /** Both settings as they were, to put back exactly (the minimum and the peak can differ); null: couldn't be read. */
    data class Saved(val min: String?, val peak: String?)

    fun save(executor: ShellExecutor) = Saved(
        min = executor.getStringSystemSetting(MIN_REFRESH_RATE, "").ifEmpty { null },
        peak = executor.getStringSystemSetting(PEAK_REFRESH_RATE, "").ifEmpty { null },
    )

    /** A value that couldn't be read is left as it is now rather than guessed. */
    fun restore(executor: ShellExecutor, saved: Saved) {
        saved.min?.let { executor.setStringSystemSetting(MIN_REFRESH_RATE, it) }
        saved.peak?.let { executor.setStringSystemSetting(PEAK_REFRESH_RATE, it) }
    }
}
