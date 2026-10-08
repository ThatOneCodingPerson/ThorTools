package io.github.thatonecodingperson.thortools.tools

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dagger.hilt.android.AndroidEntryPoint
import io.github.thatonecodingperson.thortools.coexist.Overlap
import io.github.thatonecodingperson.thortools.data.SharedPrefsRepo
import io.github.thatonecodingperson.thortools.navigation.GestureNav
import io.github.thatonecodingperson.thortools.service.ServiceWatchJob
import javax.inject.Inject

@AndroidEntryPoint
class BootReceiver : BroadcastReceiver() {

    @Inject
    lateinit var settings: SettingsRepo

    @Inject
    lateinit var prefs: SharedPrefsRepo

    @Inject
    lateinit var gestureNav: GestureNav

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) {
            return
        }
        ServiceWatchJob.schedule(context, ServiceWatchJob.AFTER_BOOT_MS, afterBoot = true)
        // PServer writes; the accessibility service may already answer key events on the main thread.
        val pending = goAsync()
        Thread {
            try {
                val saturation = prefs.saturationOverride
                if (saturation != 1.0f && prefs.isEnabled(Overlap.SATURATION_AT_BOOT)) {
                    settings.setSfSaturation(saturation)
                }
                val vibrationStrength = prefs.vibrationStrength
                if (vibrationStrength != 0 && prefs.isEnabled(Overlap.VIBRATION_AT_BOOT)) {
                    settings.vibrationStrength = vibrationStrength
                }
                // The status bar flags behind the swipe up don't survive a restart.
                gestureNav.reapplyAndWait()
            } finally {
                pending.finish()
            }
        }.start()
    }
}
