package io.github.thatonecodingperson.thortools.charging

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dagger.hilt.android.AndroidEntryPoint
import io.github.thatonecodingperson.thortools.data.SharedPrefsRepo
import javax.inject.Inject

@AndroidEntryPoint
class ChargeAlertActionReceiver : BroadcastReceiver() {

    @Inject
    lateinit var prefs: SharedPrefsRepo

    @Inject
    lateinit var notifier: ChargeAlertNotifier

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_SNOOZE) return
        prefs.chargeAlertSnoozedUntil = System.currentTimeMillis() + SNOOZE_MS
        notifier.clear()
    }

    companion object {
        const val ACTION_SNOOZE = "thortools.action.SNOOZE_CHARGE_ALERT"
        private const val SNOOZE_MS = 60 * 60 * 1000L
    }
}
