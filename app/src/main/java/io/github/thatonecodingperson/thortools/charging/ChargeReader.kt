package io.github.thatonecodingperson.thortools.charging

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.SystemClock
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.thatonecodingperson.thortools.tools.SettingsRepo
import io.github.thatonecodingperson.thortools.tools.ShellExecutor
import java.io.File
import javax.inject.Inject

/**
 * Reads "Charging right now" for Power management: Android's battery broadcast, AYN's charge settings and the charger
 * nodes in sysfs. Touches the disk and PServer, so never on the main thread.
 */
class ChargeReader @Inject constructor(@ApplicationContext private val context: Context, private val executor: ShellExecutor) {
    /** SELinux may keep the app out of the nodes; once a direct read failed, PServer reads them all in one call. */
    @Volatile
    private var directReadDenied = false

    fun read(): ChargeNow {
        val battery = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED)) ?: return ChargeNow()
        val plugged = battery.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) != 0
        val rawLevel = battery.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = battery.getIntExtra(BatteryManager.EXTRA_SCALE, 100)
        val level = if (rawLevel < 0 || scale <= 0) -1 else rawLevel * 100 / scale
        val maxCurrent = battery.getIntExtra(EXTRA_MAX_CHARGING_CURRENT, -1)
        val maxVoltage = battery.getIntExtra(EXTRA_MAX_CHARGING_VOLTAGE, -1)
        val aynLimit = systemFlag(SettingsRepo.KEY_PERCENT_80_LIMIT)
        val separation = systemFlag(SettingsRepo.KEY_CHARGING_SEPARATION)
        val sample = ChargeSample(
            timeMs = SystemClock.elapsedRealtime(),
            plugged = plugged,
            status = battery.getIntExtra(BatteryManager.EXTRA_STATUS, BatteryManager.BATTERY_STATUS_UNKNOWN),
            level = level,
            maxCurrentMicroAmp = maxCurrent,
            maxVoltageMicroVolt = maxVoltage,
            chargeLimitOn = aynLimit == true,
            directPowerOn = separation == true,
        )
        val (usbType, health, fiveVolt, microVolt, microAmp) = nodes()
        val inputMicroVolt = microVolt?.toLongOrNull()
        return ChargeNow(
            chargeClass = ChargeClassifier.classify(sample),
            level = level.takeIf { it >= 0 },
            temperatureC = battery.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, Int.MIN_VALUE).takeIf { it != Int.MIN_VALUE }?.div(10f),
            charger = if (plugged) ChargerKind.from(usbType) else ChargerKind.NONE,
            chargerType = usbType.takeIf { plugged },
            offeredWatts = if (plugged) ChargeStatus.offeredWatts(maxCurrent, maxVoltage) else null,
            inputWatts = if (plugged) ChargeStatus.inputWatts(inputMicroVolt, microAmp?.toLongOrNull()) else null,
            inputVolts = if (plugged) inputMicroVolt?.takeIf { it > 0 }?.div(MICRO) else null,
            healthPercent = health?.toIntOrNull()?.takeIf { it in 1..100 },
            aynLimit = aynLimit,
            separation = separation,
            fiveVoltCap = fiveVolt == "1",
        )
    }

    private fun systemFlag(key: String): Boolean? = when (executor.getStringSystemSetting(key, "")) {
        "1" -> true
        "0" -> false
        else -> null
    }

    private fun nodes(): List<String?> {
        if (!directReadDenied) {
            val direct = NODES.map { runCatching { File(it).readText().trim() }.getOrNull() }
            if (direct.all { it != null }) return direct
            directReadDenied = true
        }
        // One short line: PServer drops commands over about 300 characters and returns only the first line.
        val command = "cd /sys/class; echo \"" + NODES.joinToString("|") { "\$(cat ${it.removePrefix(SYS_CLASS)} 2>/dev/null)" } + "\""
        return ChargeStatus.fields(executor.executeAsRoot(command).getOrNull(), NODES.size)
    }

    private companion object {
        // Hidden BatteryManager extras that SystemUI uses for its "rapidly / slowly" label.
        const val EXTRA_MAX_CHARGING_CURRENT = "max_charging_current"
        const val EXTRA_MAX_CHARGING_VOLTAGE = "max_charging_voltage"
        const val MICRO = 1_000_000f
        const val SYS_CLASS = "/sys/class/"

        val NODES = listOf(
            "/sys/class/qcom-battery/usb_real_type",
            "/sys/class/qcom-battery/soh",
            "/sys/class/qcom-battery/force_5v",
            "/sys/class/power_supply/usb/voltage_now",
            "/sys/class/power_supply/usb/current_now",
        )
    }
}
