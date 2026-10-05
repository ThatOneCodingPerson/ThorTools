package io.github.thatonecodingperson.thortools.tools

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Context.BATTERY_SERVICE
import android.content.Intent
import android.os.BatteryManager
import dagger.hilt.android.AndroidEntryPoint
import io.github.thatonecodingperson.thortools.data.SharedPrefsRepo
import java.util.concurrent.Executors
import javax.inject.Inject

@AndroidEntryPoint
class BatteryLevelReceiver : BroadcastReceiver() {

    @Inject
    lateinit var settings: SettingsRepo

    @Inject
    lateinit var prefs: SharedPrefsRepo

    override fun onReceive(context: Context, intent: Intent) {
        if (!prefs.chargeLimitEnabled || intent.action !in ALLOWED_INTENTS) {
            return
        }

        val batteryManager = context.getSystemService(BATTERY_SERVICE) as BatteryManager
        val batteryLevel = batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)

        // Registered by the accessibility service, so this runs on its main thread; the sysfs reads go through PServer.
        worker.execute {
            if (batteryLevel >= prefs.maxBatteryLevel && !settings.chargingSeparationEnabled()) {
                settings.enableChargingSeparation()
            } else if (batteryLevel <= prefs.minBatteryLevel && settings.chargingSeparationEnabled()) {
                settings.disableChargingSeparation()
            }
        }
    }

    companion object {
        private val worker = Executors.newSingleThreadExecutor()

        val ALLOWED_INTENTS = listOf(
            Intent.ACTION_BATTERY_CHANGED,
            Intent.ACTION_POWER_CONNECTED,
            Intent.ACTION_POWER_DISCONNECTED,
            Intent.ACTION_SCREEN_OFF,
            Intent.ACTION_SCREEN_ON,
        )
    }
}
