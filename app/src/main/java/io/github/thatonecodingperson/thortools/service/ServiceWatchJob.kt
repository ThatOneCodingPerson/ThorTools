package io.github.thatonecodingperson.thortools.service

import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.content.ComponentName
import android.content.Context
import android.os.PersistableBundle
import android.os.SystemClock
import dagger.hilt.android.AndroidEntryPoint
import io.github.thatonecodingperson.thortools.tools.SettingsRepo
import javax.inject.Inject

/**
 * Brings the accessibility service back when Android stopped it. During boot the memory killer can stop the app,
 * after which Android treats the service as crashed and won't reconnect it until it is toggled off and on.
 */
@AndroidEntryPoint
class ServiceWatchJob : JobService() {

    @Inject
    lateinit var settings: SettingsRepo

    @Inject
    lateinit var status: ServiceStatus

    override fun onStartJob(params: JobParameters): Boolean {
        Thread {
            if (settings.accessibilityServiceListed() && !waitForService()) {
                settings.reviveAccessibilityService()
                waitForService()
            }
            if (params.extras.getBoolean(EXTRA_AFTER_BOOT) && SystemClock.elapsedRealtime() < BOOT_WINDOW_MS) {
                schedule(this, LATER_CHECK_MS, afterBoot = true)
            }
            jobFinished(params, false)
        }.start()
        return true
    }

    override fun onStopJob(params: JobParameters): Boolean = false

    private fun waitForService(): Boolean {
        repeat(VERIFY_TRIES) {
            if (status.connected) return true
            Thread.sleep(VERIFY_STEP_MS)
        }
        return status.connected
    }

    companion object {
        private const val JOB_ID = 4103
        private const val EXTRA_AFTER_BOOT = "after_boot"
        private const val VERIFY_TRIES = 12
        private const val VERIFY_STEP_MS = 500L
        private const val BOOT_WINDOW_MS = 5 * 60_000L
        private const val LATER_CHECK_MS = 120_000L
        const val AFTER_BOOT_MS = 60_000L
        const val AFTER_OPEN_MS = 15_000L

        fun schedule(context: Context, delayMs: Long, afterBoot: Boolean) {
            val extras = PersistableBundle().apply { putBoolean(EXTRA_AFTER_BOOT, afterBoot) }
            val job = JobInfo.Builder(JOB_ID, ComponentName(context, ServiceWatchJob::class.java))
                .setMinimumLatency(delayMs)
                .setOverrideDeadline(delayMs + 30_000L)
                .setExtras(extras)
                .build()
            context.getSystemService(JobScheduler::class.java).schedule(job)
        }
    }
}
