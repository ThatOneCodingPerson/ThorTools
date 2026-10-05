package io.github.thatonecodingperson.thortools.charging

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Icon
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.thatonecodingperson.thortools.R
import io.github.thatonecodingperson.thortools.main.MainActivity
import javax.inject.Inject

class ChargeAlertNotifier @Inject constructor(@ApplicationContext private val context: Context) {

    private val manager = context.getSystemService(NotificationManager::class.java)

    fun canPost(): Boolean = context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    fun show(changes: Int, context: ChargeAlertContext) {
        if (!canPost()) return
        ensureChannel()
        manager.notify(NOTIFICATION_ID, build(changes, context))
    }

    fun clear() = manager.cancel(NOTIFICATION_ID)

    private fun ensureChannel() {
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        val channel = NotificationChannel(CHANNEL_ID, context.getString(R.string.chargeAlertChannel), NotificationManager.IMPORTANCE_LOW)
        channel.description = context.getString(R.string.chargeAlertChannelDescription)
        manager.createNotificationChannel(channel)
    }

    private fun build(changes: Int, alert: ChargeAlertContext): Notification {
        val notes = ChargeStatus.notes(alert).map { note ->
            context.getString(
                when (note) {
                    AlertNote.AYN_LIMIT -> R.string.chargeAlertNoteAynLimit
                    AlertNote.SEPARATION -> R.string.chargeAlertNoteSeparation
                    AlertNote.AUTOMATION -> R.string.chargeAlertNoteAutomation
                    AlertNote.WEAK_CHARGER -> R.string.chargeAlertNoteWeak
                },
            )
        }
        val text = (listOf(context.getString(R.string.chargeAlertText, changes)) + notes + context.getString(R.string.chargeAlertAdvice))
            .joinToString(" ")
        val open = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE,
        )
        val snooze = PendingIntent.getBroadcast(
            context,
            0,
            Intent(context, ChargeAlertActionReceiver::class.java).setAction(ChargeAlertActionReceiver.ACTION_SNOOZE),
            PendingIntent.FLAG_IMMUTABLE,
        )
        val restart = PendingIntent.getActivity(
            context,
            0,
            Intent(context, RestartActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE,
        )
        val icon = Icon.createWithResource(context, R.drawable.ic_charge_unstable)
        return Notification.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_charge_unstable)
            .setContentTitle(context.getString(R.string.chargeAlertTitle))
            .setContentText(text)
            .setStyle(Notification.BigTextStyle().bigText(text))
            .setCategory(Notification.CATEGORY_STATUS)
            .setOnlyAlertOnce(true)
            .setContentIntent(open)
            .addAction(Notification.Action.Builder(icon, context.getString(R.string.chargeAlertSnooze), snooze).build())
            .addAction(Notification.Action.Builder(icon, context.getString(R.string.chargeAlertRestart), restart).build())
            .build()
    }

    companion object {
        const val CHANNEL_ID = "thortools_charging_stability"
        const val NOTIFICATION_ID = 4101
    }
}
