package io.github.thatonecodingperson.thortools.coexist

import androidx.annotation.StringRes
import io.github.thatonecodingperson.thortools.R

/**
 * Features that write state OdinTools also writes on its own. While OdinTools is installed each of these starts
 * switched off; an explicit choice by the user always wins. [guardOnly]: not a setting of its own, only who handles a
 * feature while OdinTools is there, so without OdinTools it is always on.
 */
enum class Overlap(
    val prefKey: String,
    val defaultEnabled: Boolean,
    @StringRes val title: Int,
    @StringRes val detail: Int,
    val guardOnly: Boolean = false,
) {
    PER_APP_CONTROLS(
        prefKey = "coexist_per_app_controls",
        defaultEnabled = true,
        title = R.string.overlapPerAppControls,
        detail = R.string.overlapPerAppControlsDetail,
        guardOnly = true,
    ),
    EXTERNAL_DISPLAY_STYLE(
        prefKey = "video_output_override_enabled",
        defaultEnabled = false,
        title = R.string.overlapExternalDisplay,
        detail = R.string.overlapExternalDisplayDetail,
    ),
    CHARGE_AUTOMATION(
        prefKey = "charge_limit_enabled",
        defaultEnabled = false,
        title = R.string.overlapChargeAutomation,
        detail = R.string.overlapChargeAutomationDetail,
    ),
    SATURATION_AT_BOOT(
        prefKey = "coexist_saturation_at_boot",
        defaultEnabled = true,
        title = R.string.overlapSaturation,
        detail = R.string.overlapSaturationDetail,
    ),
    VIBRATION_AT_BOOT(
        prefKey = "coexist_vibration_at_boot",
        defaultEnabled = true,
        title = R.string.overlapVibration,
        detail = R.string.overlapVibrationDetail,
    ),
}
