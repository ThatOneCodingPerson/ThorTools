package io.github.thatonecodingperson.thortools.appsettings

import android.graphics.drawable.Drawable
import io.github.thatonecodingperson.thortools.models.ControllerStyle
import io.github.thatonecodingperson.thortools.models.FanMode
import io.github.thatonecodingperson.thortools.models.L2R2Style
import io.github.thatonecodingperson.thortools.models.PerfMode

data class AppOverrideListUiModel(
    val showAppSelectDialog: Boolean = false,
    val overrideList: List<AppUiModel> = emptyList(),
    val overrideCandidates: List<AppUiModel> = emptyList(),
)

data class AppOverridesUiModel(
    val app: AppUiModel? = null,

    val hasUnsavedChanges: Boolean = false,
    val showDeleteConfirmDialog: Boolean = false,
    val navigateBack: Boolean = false,
    val isNewApp: Boolean = false,
    val disabledFanModeKeys: List<String> = emptyList(),
)

data class AppUiModel(
    val packageName: String,
    val appName: String,
    val appIcon: Drawable,
    val subtitle: String? = null,
    val controllerStyle: ControllerStyle? = null,
    val l2r2Style: L2R2Style? = null,
    val fanMode: FanMode? = null,
    val perfMode: PerfMode? = null,
)
