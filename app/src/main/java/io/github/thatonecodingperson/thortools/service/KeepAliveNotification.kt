package io.github.thatonecodingperson.thortools.service

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import io.github.thatonecodingperson.thortools.R
import io.github.thatonecodingperson.thortools.main.MainActivity

/** A silent ongoing notification that makes Android less eager to stop the accessibility service. */
object KeepAliveNotification {
    private const val CHANNEL_ID = "thortools_service"
    private const val NOTIFICATION_ID = 4102

    fun show(context: Context) {
        if (context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        val manager = context.getSystemService(NotificationManager::class.java)
        if (manager.getNotificationChannel(CHANNEL_ID) == null) {
            val channel = NotificationChannel(CHANNEL_ID, context.getString(R.string.keepAliveChannel), NotificationManager.IMPORTANCE_MIN)
            channel.description = context.getString(R.string.keepAliveChannelDescription)
            channel.setShowBadge(false)
            manager.createNotificationChannel(channel)
        }
        val open = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = Notification.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_gamepad)
            .setContentTitle(context.getString(R.string.keepAliveTitle))
            .setContentText(context.getString(R.string.keepAliveText))
            .setOngoing(true)
            .setShowWhen(false)
            .setCategory(Notification.CATEGORY_SERVICE)
            .setContentIntent(open)
            .build()
        manager.notify(NOTIFICATION_ID, notification)
    }

    fun hide(context: Context) {
        context.getSystemService(NotificationManager::class.java).cancel(NOTIFICATION_ID)
    }
}
