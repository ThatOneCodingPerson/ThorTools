package io.github.thatonecodingperson.thortools.roms

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Icon
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.thatonecodingperson.thortools.R
import io.github.thatonecodingperson.thortools.main.MainActivity
import javax.inject.Inject
import javax.inject.Singleton

/** The one notification ROM compression shows while jobs run, with Pause or Resume and Stop. */
@Singleton
class RomJobNotice @Inject constructor(@ApplicationContext private val context: Context) {

    private val manager = context.getSystemService(NotificationManager::class.java)

    /** [job] is game [position] of [total]. */
    fun show(job: RomJob, phase: String, progress: ChdProgress?, position: Int, total: Int, paused: Boolean) {
        if (context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        ensureChannel()
        val step = context.getString(
            when {
                paused -> R.string.romPaused
                phase == "verify" -> R.string.romPhaseVerify
                phase == "extract" -> R.string.romPhaseExtract
                phase == "unpack" -> R.string.romPhaseUnpack
                phase == "finish" -> R.string.romPhaseFinish
                phase.isEmpty() -> R.string.romPhaseStarting
                else -> R.string.romPhaseCompress
            },
        )
        val percent = progress?.percent?.toInt()?.coerceIn(0, 100)
        val text = listOfNotNull(
            step + (percent?.let { " $it %" } ?: ""),
            context.getString(R.string.romGameOf, position, total).takeIf { total > 1 },
        ).joinToString(" · ")
        val open = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                .putExtra(MainActivity.EXTRA_OPEN, MainActivity.OPEN_ROMS),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val icon = Icon.createWithResource(context, R.drawable.ic_file_save)
        val toggle = if (paused) RomJobActions.ACTION_RESUME else RomJobActions.ACTION_PAUSE
        val notification = Notification.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_file_save)
            .setContentTitle(job.title)
            .setContentText(text)
            .setProgress(100, percent ?: 0, percent == null && !paused)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(Notification.CATEGORY_PROGRESS)
            .setContentIntent(open)
            .addAction(
                Notification.Action.Builder(
                    icon,
                    context.getString(if (paused) R.string.romResume else R.string.romPause),
                    action(toggle),
                ).build(),
            )
            .addAction(Notification.Action.Builder(icon, context.getString(R.string.romStop), action(RomJobActions.ACTION_STOP)).build())
            .build()
        manager.notify(NOTIFICATION_ID, notification)
    }

    fun clear() = manager.cancel(NOTIFICATION_ID)

    private fun action(name: String): PendingIntent = PendingIntent.getBroadcast(
        context,
        name.hashCode(),
        Intent(context, RomJobActions::class.java).setAction(name),
        PendingIntent.FLAG_IMMUTABLE,
    )

    private fun ensureChannel() {
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        val channel = NotificationChannel(CHANNEL_ID, context.getString(R.string.romChannel), NotificationManager.IMPORTANCE_LOW)
        channel.description = context.getString(R.string.romChannelDescription)
        manager.createNotificationChannel(channel)
    }

    companion object {
        const val CHANNEL_ID = "thortools_roms"
        const val NOTIFICATION_ID = 4103
    }
}

/** The notification's Pause, Resume and Stop. */
@AndroidEntryPoint
class RomJobActions : BroadcastReceiver() {

    @Inject
    lateinit var runner: RomJobRunner

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            ACTION_PAUSE -> runner.pause()
            ACTION_RESUME -> runner.resumeRun()
            ACTION_STOP -> runner.stopAll()
        }
    }

    companion object {
        const val ACTION_PAUSE = "thortools.action.PAUSE_ROM_JOBS"
        const val ACTION_RESUME = "thortools.action.RESUME_ROM_JOBS"
        const val ACTION_STOP = "thortools.action.STOP_ROM_JOBS"
    }
}
