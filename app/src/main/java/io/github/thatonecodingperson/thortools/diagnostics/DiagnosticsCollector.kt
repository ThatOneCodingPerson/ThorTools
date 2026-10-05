package io.github.thatonecodingperson.thortools.diagnostics

import android.content.Context
import android.hardware.display.DisplayManager
import android.hardware.input.InputManager
import android.os.Build
import android.provider.Settings
import android.view.Display
import android.view.InputDevice
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.thatonecodingperson.thortools.BuildConfig
import io.github.thatonecodingperson.thortools.coexist.OdinToolsDetector
import io.github.thatonecodingperson.thortools.data.SharedPrefsRepo
import io.github.thatonecodingperson.thortools.service.ServiceStatus
import io.github.thatonecodingperson.thortools.setup.AccessChecker
import io.github.thatonecodingperson.thortools.tools.DeviceUtils
import io.github.thatonecodingperson.thortools.tools.SettingsRepo
import io.github.thatonecodingperson.thortools.tools.SharedList
import io.github.thatonecodingperson.thortools.tools.ShellExecutor
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

/** Gathers the device facts Thor Tools relies on into a plain-text report. */
class DiagnosticsCollector @Inject constructor(
    @ApplicationContext private val context: Context,
    private val executor: ShellExecutor,
    private val deviceUtils: DeviceUtils,
    private val odinTools: OdinToolsDetector,
    private val serviceKeys: ServiceKeyLog,
    private val serviceStatus: ServiceStatus,
    private val prefs: SharedPrefsRepo,
    private val settings: SettingsRepo,
    private val access: AccessChecker,
) {

    fun collect(): String = buildString {
        val stamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
        appendLine("Thor Tools ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE}) diagnostics, $stamp")
        section("Device") {
            appendLine("manufacturer=${Build.MANUFACTURER} brand=${Build.BRAND} model=${Build.MODEL}")
            appendLine("device=${Build.DEVICE} product=${Build.PRODUCT} board=${Build.BOARD} hardware=${Build.HARDWARE}")
            appendLine("android=${Build.VERSION.RELEASE} (sdk ${Build.VERSION.SDK_INT}) display=${Build.DISPLAY}")
            appendLine("detected=${deviceUtils.getDeviceType()}")
        }
        section("Root") {
            appendLine("pserver_binder=${executor.pServerAvailable}")
            appendLine("pserver_uid0=${executor.probe()}")
        }
        section("OdinTools") {
            val state = odinTools.state()
            appendLine("installed=${state.installed} version=${state.versionName ?: "-"} service=${state.serviceEnabled}")
            appendLine("leftover_thortools=${state.leftoverThorTools} self=${BuildConfig.APPLICATION_ID}")
        }
        section("Permissions and access") {
            access.full().states.forEach { (check, state) -> appendLine("${check.name.lowercase()}=${state.name.lowercase()}") }
        }
        section("Accessibility service") {
            val services = Settings.Secure.getString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
            appendLine(
                "enabled_in_settings=${SharedList.containsPackage(
                    services,
                    ':',
                    BuildConfig.APPLICATION_ID,
                )} connected=${serviceStatus.connected}",
            )
            appendLine("keys_seen=${serviceStatus.keysSeen} prevent_press_home_accidentally=${settings.preventPressHome}")
            appendLine("battery_exempt=${settings.isBatteryExempt()}")
        }
        section("Root input helper") {
            val helper = serviceStatus.rawInput
            if (helper == null) {
                appendLine("not running (the service is off)")
            } else {
                appendLine("state=${helper.state}")
                helper.devices.forEach(::appendLine)
            }
        }
        section("Keys seen by the service, oldest first") {
            serviceKeys.snapshot().forEach { appendLine(it.toString()) }
        }
        section("Displays") { displays().forEach(::appendLine) }
        section("Input devices") { inputDevices().forEach(::appendLine) }
        section("Readable without root") { SYSFS.forEach { appendLine("$it=${appReadable(it)}") } }
        if (executor.pServerAvailable) {
            appendLine()
            append(executor.capture(rootScript()).getOrElse { "root script failed: ${it.message}" })
            appendLine()
        }
    }

    private inline fun StringBuilder.section(title: String, body: StringBuilder.() -> Unit) {
        appendLine()
        appendLine("[$title]")
        body()
    }

    private fun displays(): List<String> {
        val manager = context.getSystemService(DisplayManager::class.java)
        return manager.displays.map { display ->
            val mode = display.mode
            val type = runCatching { Display::class.java.getMethod("getType").invoke(display) }.getOrNull()
            "id=${display.displayId} name=\"${display.name}\" ${mode.physicalWidth}x${mode.physicalHeight}" +
                " @${mode.refreshRate}Hz flags=0x${Integer.toHexString(display.flags)} type=$type state=${display.state}"
        }
    }

    private fun inputDevices(): List<String> {
        val manager = context.getSystemService(InputManager::class.java)
        return manager.inputDeviceIds.asList().mapNotNull(manager::getInputDevice).map { device ->
            "id=${device.id} name=\"${device.name}\" sources=0x${Integer.toHexString(device.sources)}" +
                " vendor=${device.vendorId} product=${device.productId} external=${device.isExternal}" +
                " gamepad=${device.supportsSource(InputDevice.SOURCE_GAMEPAD)}"
        }
    }

    private fun appReadable(path: String): String {
        val file = File(path)
        return when {
            !file.exists() -> "missing"
            !file.canRead() -> "no"
            else -> runCatching { "yes (${file.readText().trim().take(40)})" }.getOrDefault("denied")
        }
    }

    private fun rootScript(): String = buildString {
        appendLine("echo '[SELinux]'; getenforce")
        appendLine("echo; echo '[System settings]'")
        appendLine("for k in ${SYSTEM_KEYS.joinToString(" ")}; do echo \"\$k=\$(settings get system \$k)\"; done")
        appendLine("echo \"device_provisioned=\$(settings get global device_provisioned)\"")
        appendLine("echo \"user_setup_complete=\$(settings get secure user_setup_complete)\"")
        appendLine("echo; echo '[Thor-looking system settings]'")
        appendLine("settings list system | grep -iE 'abxy|button|trigger|perf|fan|refresh|charg|limit|home|ayn|thor|screen_mode|second'")
        appendLine("echo; echo '[Properties]'")
        appendLine("for p in ${PROPERTIES.joinToString(" ")}; do echo \"\$p=\$(getprop \$p)\"; done")
        appendLine("getprop | grep -iE 'retro|ayn|thor|odin' | head -n 40")
        appendLine("echo; echo '[sysfs as root]'")
        appendLine(
            "for f in ${SYSFS.joinToString(" ")}; do " +
                "if [ -e \$f ]; then echo \"\$f=\$(head -c 80 \$f)\"; else echo \"\$f=missing\"; fi; done",
        )
        appendLine("ls /sys/class/qcom-battery/ 2>&1 | tr '\\n' ' '; echo")
        appendLine("echo; echo '[Backlights]'")
        appendLine(
            "for b in /sys/class/backlight/*; do " +
                "echo \"\$b brightness=\$(cat \$b/brightness) max=\$(cat \$b/max_brightness) power=\$(cat \$b/bl_power 2>/dev/null)\"; done",
        )
        appendLine("echo; echo '[Memory cleanup]'")
        appendLine("echo \"context=\$(id -Z 2>&1)\"")
        appendLine(
            "for f in /proc/sys/vm/drop_caches /proc/sys/vm/compact_memory; do " +
                "[ -w \$f ] && echo \"\$f writable\" || echo \"\$f not writable\"; done",
        )
        appendLine("echo; echo '[Tasks]'")
        appendLine("am stack list 2>&1 | head -n 30")
        appendLine("dumpsys activity activities | grep -E 'Display #|Task\\{|mResumedActivity' | head -n 40")
        appendLine("echo; echo '[AYN and handheld packages]'")
        appendLine("pm list packages | grep -iE 'ayn|odin|gameassist|retro|thor|launcher' | sort")
    }

    private companion object {
        val SYSTEM_KEYS = listOf(
            "temp_abxy_layout_mode",
            "flip_button_layout",
            "no_create_gamepad_button_layout",
            "trigger_input_mode",
            "performance_mode",
            "fan_mode",
            "min_refresh_rate",
            "peak_refresh_rate",
            "prevent_press_home_accidentally",
            "is_charging_separation",
            "percent_80_charge_limit",
            "charging_limit_greater_than_80",
            "charging_limit_less_than_10",
            "vibrate_on",
            "app_whiteList",
        )
        val PROPERTIES = listOf(
            "ro.vendor.retro.name",
            "ro.build.odin2.ota.version",
            "persist.sys.sf.color_saturation",
            "ro.product.model",
            "ro.build.version.incremental",
        )
        val SYSFS = listOf(
            "/sys/class/qcom-battery/restrict_chg",
            "/sys/class/qcom-battery/restrict_cur",
            "/sys/class/qcom-battery/limit_capacity_charge",
            "/sys/class/qcom-battery/usb_charge_now",
            "/sys/class/power_supply/battery/current_now",
            "/sys/class/power_supply/battery/voltage_now",
            "/sys/class/power_supply/battery/cycle_count",
            "/sys/class/power_supply/battery/temp",
            "/sys/class/power_supply/usb/current_now",
            "/sys/class/power_supply/usb/voltage_now",
            "/sys/class/power_supply/usb/usb_type",
            "/sys/class/power_supply/usb/online",
        )
    }
}
