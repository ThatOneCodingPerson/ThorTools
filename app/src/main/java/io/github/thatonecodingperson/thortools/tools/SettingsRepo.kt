package io.github.thatonecodingperson.thortools.tools

import android.Manifest
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.PowerManager
import android.provider.Settings
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.thatonecodingperson.thortools.BuildConfig
import io.github.thatonecodingperson.thortools.service.ForegroundAppWatcherService
import javax.inject.Inject

class SettingsRepo @Inject constructor(@ApplicationContext private val context: Context, private val executor: ShellExecutor) {

    fun applyRequiredSettings() {
        enableA11yService()
        grantAllAppsPermission()
        exemptFromBatteryOptimisation()
        // Don't add to whitelist on debug builds, otherwise even Android Studio can't kill the app
        if (!BuildConfig.DEBUG) {
            addSelfToWhitelist()
        }
    }

    private fun enableA11yService() {
        // A failed read must not turn into a write that drops the other apps' services.
        val currentServices = runCatching { Settings.Secure.getString(context.contentResolver, KEY_ACCESSIBILITY_SERVICES) }
            .getOrElse { return }
        if (SharedList.containsPackage(currentServices, ':', PACKAGE)) return

        val services = SharedList.withEntry(currentServices, ':', A11Y_SERVICE)
        executor.script("settings put secure $KEY_ACCESSIBILITY_SERVICES '$services'")
    }

    fun accessibilityServiceListed(): Boolean = SharedList.containsPackage(
        runCatching { Settings.Secure.getString(context.contentResolver, KEY_ACCESSIBILITY_SERVICES) }.getOrNull(),
        ':',
        PACKAGE,
    )

    /** Takes only our entry out of the list and puts it back, which makes Android bind a "crashed" service again. */
    fun reviveAccessibilityService() {
        val current = runCatching { Settings.Secure.getString(context.contentResolver, KEY_ACCESSIBILITY_SERVICES) }
            .getOrElse { return }
        val without = SharedList.withoutPackage(current, ':', PACKAGE)
        val with = SharedList.withEntry(without, ':', A11Y_SERVICE)
        executor.script(
            "settings put secure $KEY_ACCESSIBILITY_SERVICES '$without'\n" +
                "sleep 1\n" +
                "settings put secure $KEY_ACCESSIBILITY_SERVICES '$with'\n" +
                "settings put secure accessibility_enabled 1\n",
        )
    }

    fun isBatteryExempt(): Boolean = context.getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(PACKAGE)

    fun exemptFromBatteryOptimisation() {
        if (!isBatteryExempt()) executor.executeAsRoot("cmd deviceidle whitelist +$PACKAGE")
    }

    /** QUERY_ALL_PACKAGES is granted at install; notifications are a runtime permission, granted here without a prompt. */
    private fun grantAllAppsPermission() {
        if (!notificationPermissionGranted()) grantNotifications()
    }

    fun notificationPermissionGranted(): Boolean =
        context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    /** The permission alone isn't enough: notifications can also be switched off for the app in Android's settings. */
    fun notificationsAllowed(): Boolean =
        notificationPermissionGranted() && context.getSystemService(NotificationManager::class.java).areNotificationsEnabled()

    fun grantNotifications(): Boolean {
        executor.executeAsRoot("pm grant $PACKAGE ${Manifest.permission.POST_NOTIFICATIONS}")
        return notificationsAllowed()
    }

    fun inBackgroundList(): Boolean = SharedList.containsPackage(
        runCatching { Settings.System.getString(context.contentResolver, KEY_APP_WHITELIST) }
            .recoverCatching { executor.executeAsRoot("settings get system $KEY_APP_WHITELIST").getOrThrow() }
            .getOrNull(),
        ',',
        PACKAGE,
    )

    fun addSelfToWhitelist() {
        val currentWhitelist = runCatching { Settings.System.getString(context.contentResolver, KEY_APP_WHITELIST) }
            .recoverCatching { executor.executeAsRoot("settings get system $KEY_APP_WHITELIST").getOrThrow() }
            .getOrElse { return }
        if (SharedList.containsPackage(currentWhitelist, ',', PACKAGE)) return

        val whitelist = SharedList.withEntry(currentWhitelist, ',', PACKAGE)
        executor.script("settings put system $KEY_APP_WHITELIST '$whitelist'")
    }

    /** SystemUI silently refuses Recents while either setup flag is 0 (Android's crash recovery resets them). */
    fun recentsBlocked(): Boolean {
        val provisioned = runCatching { Settings.Global.getInt(context.contentResolver, Settings.Global.DEVICE_PROVISIONED, 1) }
            .getOrDefault(1)
        val setupComplete = executor.executeAsRoot("settings get secure user_setup_complete").getOrNull()?.toIntOrNull() ?: 1
        return provisioned == 0 || setupComplete == 0
    }

    fun fixRecents() {
        executor.executeAsRoot("settings put global device_provisioned 1; settings put secure user_setup_complete 1")
    }

    fun setSfSaturation(value: Float) {
        executor.executeAsRoot("service call SurfaceFlinger 1022 f ${String.format("%.1f", value)}")
    }

    fun enableChargingSeparation() {
        isChargingSeparation = true
        restrictCurrent = 1000
        restrictCharge = true
    }

    fun disableChargingSeparation() {
        isChargingSeparation = false
        restrictCurrent = 1000000
        restrictCharge = false
    }

    fun chargingSeparationEnabled(): Boolean = isChargingSeparation && restrictCharge && restrictCurrent == 1000

    var preventPressHome: Boolean
        get() = executor.getBooleanSystemSetting(KEY_PREVENT_PRESS_HOME, true)
        set(value) = executor.setBooleanSystemSetting(KEY_PREVENT_PRESS_HOME, value)

    var vibrationEnabled: Boolean
        get() = executor.getBooleanSystemSetting(KEY_VIBRATE_ON, false)
        set(value) = executor.setBooleanSystemSetting(KEY_VIBRATE_ON, value)

    var vibrationStrength: Int
        get() = executor.getIntValue(KEY_VIBRATION_STRENGTH, 0)
        set(value) = executor.setIntValue(KEY_VIBRATION_STRENGTH, value)

    var isChargingSeparation: Boolean
        get() = executor.getBooleanSystemSetting(KEY_CHARGING_SEPARATION, false)
        set(value) = executor.setBooleanSystemSetting(KEY_CHARGING_SEPARATION, value)

    var restrictCharge: Boolean
        get() = executor.getBooleanValue(KEY_RESTRICT_CHARGE, false)
        set(value) = executor.setBooleanValue(KEY_RESTRICT_CHARGE, value)

    var restrictCurrent: Int
        get() = executor.getIntValue(KEY_RESTRICT_CURRENT, 0)
        set(value) = executor.setIntValue(KEY_RESTRICT_CURRENT, value)

    var chargingLimit80Enabled: Boolean
        get() = executor.getBooleanSystemSetting(KEY_CHARGING_LIMIT_80, false)
        set(value) = executor.setBooleanSystemSetting(KEY_CHARGING_LIMIT_80, value)

    var chargingLimit10Enabled: Boolean
        get() = executor.getBooleanSystemSetting(KEY_CHARGING_LIMIT_10, false)
        set(value) = executor.setBooleanSystemSetting(KEY_CHARGING_LIMIT_10, value)

    companion object {
        private const val PACKAGE = BuildConfig.APPLICATION_ID
        private val A11Y_SERVICE = "$PACKAGE/${ForegroundAppWatcherService::class.java.name}"
        const val KEY_VENDOR_NAME = "ro.vendor.retro.name"
        const val KEY_BUILD_VERSION = "ro.build.odin2.ota.version"
        const val KEY_SATURATION = "persist.sys.sf.color_saturation"
        const val KEY_ACCESSIBILITY_SERVICES = "enabled_accessibility_services"
        const val KEY_APP_WHITELIST = "app_whiteList"
        const val KEY_PREVENT_PRESS_HOME = "prevent_press_home_accidentally"
        const val KEY_VIBRATE_ON = "vibrate_on"
        const val KEY_CUSTOM_M1_VALUE = "remap_custom_to_m1_value"
        const val KEY_CUSTOM_M2_VALUE = "remap_custom_to_m2_value"
        const val KEY_VIBRATION_STRENGTH = "/d/haptics/user_vmax_mv"
        const val KEY_CHARGING_SEPARATION = "is_charging_separation"
        const val KEY_PERCENT_80_LIMIT = "percent_80_charge_limit"
        const val KEY_CHARGING_LIMIT_80 = "charging_limit_greater_than_80"
        const val KEY_CHARGING_LIMIT_10 = "charging_limit_less_than_10"
        const val KEY_RESTRICT_CHARGE = "/sys/class/qcom-battery/restrict_chg"
        const val KEY_RESTRICT_CURRENT = "/sys/class/qcom-battery/restrict_cur"
    }
}
