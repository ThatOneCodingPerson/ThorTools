package io.github.thatonecodingperson.thortools.charging

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Handler
import android.os.HandlerThread
import android.os.SystemClock
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.thatonecodingperson.thortools.data.SharedPrefsRepo
import io.github.thatonecodingperson.thortools.tools.SettingsRepo
import io.github.thatonecodingperson.thortools.tools.ShellExecutor
import java.io.File
import javax.inject.Inject

/**
 * Watches battery broadcasts for the charging stability alert. Runs on its own thread because reading AYN's
 * charge settings goes through PServer.
 */
class ChargeMonitor @Inject constructor(
    @ApplicationContext private val context: Context,
    private val prefs: SharedPrefsRepo,
    private val executor: ShellExecutor,
    private val notifier: ChargeAlertNotifier,
) {
    private val detector = FlapDetector()

    /** SELinux keeps the app out of the voltage file on the Thor; once a direct read failed, PServer reads it. */
    private var directReadDenied = false
    private var thread: HandlerThread? = null
    private var handler: Handler? = null

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) = onBatteryChanged(intent)
    }

    private val ticker = object : Runnable {
        override fun run() {
            publish(detector.tick(SystemClock.elapsedRealtime()))
            handler?.postDelayed(this, TICK_MS)
        }
    }

    fun start() {
        if (thread != null) return
        detector.sensitivity = prefs.chargeAlertSensitivity
        val newThread = HandlerThread("ChargeMonitor").apply { start() }
        val newHandler = Handler(newThread.looper)
        thread = newThread
        handler = newHandler
        context.registerReceiver(receiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED), null, newHandler, Context.RECEIVER_NOT_EXPORTED)
        newHandler.postDelayed(ticker, TICK_MS)
    }

    fun stop() {
        val current = thread ?: return
        context.unregisterReceiver(receiver)
        handler?.removeCallbacksAndMessages(null)
        current.quitSafely()
        thread = null
        handler = null
        notifier.clear()
    }

    fun setSensitivity(sensitivity: Sensitivity) {
        handler?.post { detector.sensitivity = sensitivity }
    }

    private fun onBatteryChanged(intent: Intent) {
        val plugged = intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) != 0
        val sample = ChargeSample(
            timeMs = SystemClock.elapsedRealtime(),
            plugged = plugged,
            status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, BatteryManager.BATTERY_STATUS_UNKNOWN),
            level = batteryPercent(intent),
            maxCurrentMicroAmp = intent.getIntExtra(EXTRA_MAX_CHARGING_CURRENT, -1),
            maxVoltageMicroVolt = intent.getIntExtra(EXTRA_MAX_CHARGING_VOLTAGE, -1),
            inputMicroVolt = if (plugged) readInputVoltage() else null,
            chargeLimitOn = plugged && executor.getBooleanSystemSetting(SettingsRepo.KEY_PERCENT_80_LIMIT, false),
            directPowerOn = plugged && executor.getBooleanSystemSetting(SettingsRepo.KEY_CHARGING_SEPARATION, false),
        )
        publish(detector.onSample(sample.timeMs, ChargeClassifier.classify(sample), ChargeClassifier.inputBand(sample.inputMicroVolt)))
    }

    private fun publish(verdict: FlapVerdict) {
        when {
            !verdict.alerting -> if (verdict.alertChanged) notifier.clear()
            System.currentTimeMillis() < prefs.chargeAlertSnoozedUntil -> Unit
            else -> notifier.show(verdict.changesInWindow)
        }
    }

    private fun batteryPercent(intent: Intent): Int {
        val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, 100)
        return if (level < 0 || scale <= 0) -1 else level * 100 / scale
    }

    private fun readInputVoltage(): Long? {
        val direct = if (directReadDenied) null else runCatching { File(USB_VOLTAGE_NODE).readText() }.getOrNull()
        if (direct == null) directReadDenied = true
        val raw = direct ?: executor.getStringValue(USB_VOLTAGE_NODE, "")
        return raw.trim().toLongOrNull()
    }

    private companion object {
        // Hidden BatteryManager extras that SystemUI uses for its "rapidly / slowly" label.
        const val EXTRA_MAX_CHARGING_CURRENT = "max_charging_current"
        const val EXTRA_MAX_CHARGING_VOLTAGE = "max_charging_voltage"
        const val USB_VOLTAGE_NODE = "/sys/class/power_supply/usb/voltage_now"
        const val TICK_MS = 60_000L
    }
}
