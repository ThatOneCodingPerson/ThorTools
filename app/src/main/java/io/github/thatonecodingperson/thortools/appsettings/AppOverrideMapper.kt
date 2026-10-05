package io.github.thatonecodingperson.thortools.appsettings

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.thatonecodingperson.thortools.R
import io.github.thatonecodingperson.thortools.data.AppOverrideEntity
import io.github.thatonecodingperson.thortools.leds.LedPreset
import io.github.thatonecodingperson.thortools.models.AppRefreshRate
import io.github.thatonecodingperson.thortools.models.AppVibration
import io.github.thatonecodingperson.thortools.models.BottomScreenRule
import io.github.thatonecodingperson.thortools.models.ControllerStyle
import io.github.thatonecodingperson.thortools.models.FanMode
import io.github.thatonecodingperson.thortools.models.L2R2Style
import io.github.thatonecodingperson.thortools.models.PerfMode
import javax.inject.Inject

class AppOverrideMapper @Inject constructor(@ApplicationContext private val context: Context) {

    fun mapOverrideCandidates(existingOverrides: List<AppOverrideEntity>): List<AppUiModel> =
        context.packageManager.getInstalledApplications(PackageManager.GET_META_DATA).filter {
            it.flags and ApplicationInfo.FLAG_SYSTEM == 0 && it.enabled
        }.filterNot { appInfo ->
            existingOverrides.any { appInfo.packageName == it.packageName }
        }.map {
            val icon = context.packageManager.getApplicationIcon(it.packageName)
            val label = context.packageManager.getApplicationLabel(it).toString()
            AppUiModel(it.packageName, label, icon)
        }.sortedBy {
            it.appName
        }

    fun mapAppOverrides(overrides: List<AppOverrideEntity>): List<AppUiModel> = overrides.mapNotNull(::mapAppOverride)

    fun mapAppOverride(app: AppOverrideEntity): AppUiModel? {
        val appInfo = runCatching {
            context.packageManager.getApplicationInfo(app.packageName, PackageManager.GET_META_DATA)
        }.onFailure { return null }.getOrNull() ?: return null
        // TODO do DB cleanup on uninstalled packages?

        val controllerStyle = ControllerStyle.getById(app.controllerStyle)
        val l2R2Style = L2R2Style.getById(app.l2R2Style)
        val perfMode = PerfMode.getById(app.perfMode)
        val fanMode = FanMode.getById(app.fanMode)
        val refreshRate = AppRefreshRate.byId(app.refreshRate)
        val bottomScreen = BottomScreenRule.byId(app.bottomScreen)
        val vibration = AppVibration.byId(app.vibration)
        val leds = LedPreset.of(app.leds)

        return AppUiModel(
            packageName = app.packageName,
            appName = context.packageManager.getApplicationLabel(appInfo).toString(),
            appIcon = context.packageManager.getApplicationIcon(appInfo),
            subtitle = getSubtitle(controllerStyle, l2R2Style, perfMode, fanMode, refreshRate, bottomScreen, vibration, leds),
            controllerStyle = controllerStyle,
            l2r2Style = l2R2Style,
            perfMode = perfMode,
            fanMode = fanMode,
            refreshRate = refreshRate,
            bottomScreen = bottomScreen,
            vibration = vibration,
            leds = leds,
        )
    }

    fun mapEmptyOverride(packageName: String): AppUiModel {
        // If this crashes then something is fishy...
        val appInfo = context.packageManager.getApplicationInfo(packageName, PackageManager.GET_META_DATA)

        return AppUiModel(
            packageName = packageName,
            appName = context.packageManager.getApplicationLabel(appInfo).toString(),
            appIcon = context.packageManager.getApplicationIcon(appInfo),
        )
    }

    private fun getSubtitle(
        controllerStyle: ControllerStyle,
        l2R2Style: L2R2Style,
        perfMode: PerfMode,
        fanMode: FanMode,
        refreshRate: AppRefreshRate?,
        bottomScreen: BottomScreenRule?,
        vibration: AppVibration?,
        leds: LedPreset?,
    ): String? = buildString {
        if (controllerStyle != ControllerStyle.Unknown) {
            append(context.getString(R.string.controllerStyle))
            append(": ")
            append(context.getString(controllerStyle.textRes))
            append(" | ")
        }
        if (l2R2Style != L2R2Style.Unknown) {
            append(context.getString(R.string.l2r2mode))
            append(": ")
            append(context.getString(l2R2Style.textRes))
            append(" | ")
        }
        if (perfMode != PerfMode.Unknown) {
            append(context.getString(R.string.perfMode))
            append(": ")
            append(context.getString(perfMode.textRes))
            append(" | ")
        }
        if (fanMode != FanMode.Unknown) {
            append(context.getString(R.string.fanMode))
            append(": ")
            append(context.getString(fanMode.textRes))
            append(" | ")
        }
        if (refreshRate != null) {
            append(context.getString(R.string.profileRefreshRate))
            append(": ")
            append(context.getString(refreshRate.textRes))
            append(" | ")
        }
        if (bottomScreen != null) {
            append(context.getString(R.string.profileBottomScreen))
            append(": ")
            append(context.getString(bottomScreen.textRes))
            append(" | ")
        }
        if (vibration != null) {
            append(context.getString(R.string.profileVibration))
            append(": ")
            append(context.getString(vibration.textRes))
            append(" | ")
        }
        if (leds != null) {
            append(context.getString(R.string.profileLeds))
            append(": ")
            append(context.getString(leds.textRes))
            append(" | ")
        }
    }.trimEnd(' ', '|').ifEmpty { null }
}
