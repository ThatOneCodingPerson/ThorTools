package io.github.thatonecodingperson.thortools.lid

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.display.DisplayManager
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import android.provider.Settings
import android.view.Display
import android.view.KeyEvent
import io.github.thatonecodingperson.thortools.data.SharedPrefsRepo
import io.github.thatonecodingperson.thortools.models.FanMode
import io.github.thatonecodingperson.thortools.models.PerfMode
import io.github.thatonecodingperson.thortools.tools.ShellExecutor
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * Carries out the lid sandbox: what its switches ask for when the lid closes, and putting all of it back when the lid
 * opens, checked twice. The root helper reports the lid (`hall_switch`); the screen turning on or off makes this read
 * Android's own lid state too, in case a report was missed. Everything runs on one background thread under a short wake
 * lock, so the work is done before the Thor sleeps. Events come in on the main thread. A Save power part that runs later
 * (a delay, or media playing) is an exact alarm that wakes the Thor; opening the lid first cancels it. Input devices are
 * never muted: a wake with the lid closed lasts the chosen wait and then goes back to sleep if the lid is still closed.
 */
class LidController(
    private val context: Context,
    private val executor: ShellExecutor,
    private val prefs: SharedPrefsRepo,
    private val closeBackground: () -> Unit,
    private val sleepNow: () -> Unit,
    private val notRestored: (List<LidItem>) -> Unit,
) {
    private val worker = Executors.newSingleThreadScheduledExecutor { Thread(it, "lid").apply { isDaemon = true } }
    private val mainHandler = Handler(Looper.getMainLooper())
    private val power = context.getSystemService(PowerManager::class.java)
    private val displays = context.getSystemService(DisplayManager::class.java)
    private val wakeLock = power
        .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "ThorTools:lid")
        .apply { setReferenceCounted(false) }
    private val queued = AtomicInteger()
    private val alarms = context.getSystemService(AlarmManager::class.java)
    private val savingAlarm = PendingIntent.getBroadcast(
        context,
        0,
        Intent(ACTION_SAVING).setPackage(context.packageName),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )
    private val savingReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) = work(::savingDue)
    }

    /** The lid as this thread last knew it; null before the first reading. Worker thread only. */
    private var lidClosed: Boolean? = null

    /** What the helper last said: a wake right after it said "open" is the lid opening. */
    @Volatile
    private var helperSaysClosed: Boolean? = null

    // Worker thread only: the wake that is waiting to go back to sleep, and how often this closing sent it back.
    private val guard = WakeGuard()
    private var sleepWait: ScheduledFuture<*>? = null
    private var sentBack = 0

    /** Reads the lid, and puts back what a closing before a crash or a reboot left changed. */
    fun start() {
        context.registerReceiver(savingReceiver, IntentFilter(ACTION_SAVING), Context.RECEIVER_NOT_EXPORTED)
        work {
            clearMutedInputs()
            lidIs(readLid())
        }
    }

    fun stop() {
        alarms.cancel(savingAlarm)
        runCatching { context.unregisterReceiver(savingReceiver) }
        worker.shutdownNow()
        if (wakeLock.isHeld) wakeLock.release()
    }

    fun onLid(closed: Boolean) {
        helperSaysClosed = closed
        work {
            guard.reset()
            lidIs(closed)
        }
    }

    fun onScreen(on: Boolean) = work {
        val closed = readLid()
        lidIs(closed)
        when {
            !on -> cancelSleepWait()
            // Only on Android's own word that the lid is closed: a missed reading must never send an opening back to sleep.
            closed == true && backToSleepOn() && guard.onWake(SystemClock.elapsedRealtime()) -> waitThenSleep()
        }
    }

    private fun lidIs(closed: Boolean?) {
        if (closed == null || closed == lidClosed) {
            // An earlier session can still be waiting with the lid open: the app stopped before it opened.
            if (closed == false && prefs.lidSession != null) opened()
            return
        }
        lidClosed = closed
        if (closed) {
            closed()
            // Closing the lid puts the Thor to sleep; if something keeps the screen on, the same wait applies.
            if (backToSleepOn() && power.isInteractive) waitThenSleep()
        } else {
            cancelSleepWait()
            opened()
        }
    }

    private fun closed() {
        val choices = prefs.lidChoices
        prefs.lidSession?.let { saved ->
            // The app started again with the lid still closed before the Save power part ran.
            if (saved.pending) schedule(LidPlan.savingDueAt(saved, choices))
            return
        }
        if (!choices.active) return
        sentBack = 0
        val later = LidPlan.savingLater(choices, musicActive())
        val session = LidPlan.session(choices, if (later) LidReadings() else read(), System.currentTimeMillis(), pending = later)
        // Saved before anything changes, so whatever happens next, opening the lid knows what to put back.
        prefs.lidSession = session
        if (later) schedule(LidPlan.savingDueAt(session, choices)) else savePower(choices, session)
    }

    /** The alarm for a later Save power part: it runs now, unless media still plays and it should wait. */
    private fun savingDue() {
        val session = prefs.lidSession?.takeIf { it.pending } ?: return
        if (lidClosed == false) return
        val choices = prefs.lidChoices
        if (!choices.enabled) {
            prefs.lidSession = session.copy(pending = false)
            return
        }
        if (LidPlan.savingWaits(choices, musicActive())) return schedule(System.currentTimeMillis() + LidPlan.MINUTE_MS)
        val done = LidPlan.savingDone(session, choices, read())
        prefs.lidSession = done
        savePower(choices, done)
    }

    private fun savePower(choices: LidChoices, session: LidSession) {
        if (choices.pauseMedia) pauseMedia()
        if (choices.closeBackground) mainHandler.post(closeBackground)
        val target = LidPlan.closedTargets(choices)
        with(session.restore) {
            if (performance != null) target.performance?.let(::setPerformance)
            if (fan != null) target.fan?.let(::setFan)
            if (wifi != null) target.wifi?.let(::setWifi)
            if (bluetooth != null) target.bluetooth?.let(::setBluetooth)
            if (airplane != null) target.airplane?.let(::setAirplane)
        }
    }

    /** Puts everything back, reads it all again and retries what isn't back once, then records how it went. */
    private fun opened() {
        alarms.cancel(savingAlarm)
        val session = prefs.lidSession ?: return
        restore(session, LidItem.entries)
        var missing = check(session, WAIT_FIRST_MS)
        if (missing.isNotEmpty()) {
            restore(session, missing)
            missing = check(session, WAIT_AGAIN_MS)
        }
        prefs.lidSession = null
        prefs.lidLastResult = LidResult(session.closedAt, System.currentTimeMillis(), missing, sentBack)
        sentBack = 0
        if (missing.isNotEmpty()) mainHandler.post { notRestored(missing) }
    }

    /** Airplane mode first, so Wi-Fi and Bluetooth come back to what they were and not to what it remembers. */
    private fun restore(session: LidSession, items: List<LidItem>) {
        val wanted = session.restore
        if (LidItem.AIRPLANE in items) wanted.airplane?.let(::setAirplane)
        if (LidItem.WIFI in items) wanted.wifi?.let(::setWifi)
        if (LidItem.BLUETOOTH in items) wanted.bluetooth?.let(::setBluetooth)
        if (LidItem.PERFORMANCE in items) wanted.performance?.let(::setPerformance)
        if (LidItem.FAN in items) wanted.fan?.let(::setFan)
    }

    private fun check(session: LidSession, waitMs: Long): List<LidItem> {
        Thread.sleep(waitMs)
        return LidPlan.notRestored(session, read())
    }

    private fun backToSleepOn(): Boolean = prefs.lidChoices.let { it.enabled && it.backToSleep }

    /** Starts the chosen wait; when it is over, the Thor goes back to sleep if the lid is still closed. */
    private fun waitThenSleep() {
        cancelSleepWait()
        sleepWait = worker.schedule({ work(::waitOver) }, LidPlan.sleepWaitMs(prefs.lidChoices), TimeUnit.MILLISECONDS)
    }

    private fun cancelSleepWait() {
        sleepWait?.cancel(false)
        sleepWait = null
    }

    private fun waitOver() {
        sleepWait = null
        if (!backToSleepOn()) return
        val closed = readLid()
        lidIs(closed)
        if (!LidPlan.backToSleep(closed, helperSaysClosed == false, power.isInteractive, docked())) return
        guard.sentBack(SystemClock.elapsedRealtime())
        sentBack++
        // The helper's word that the lid opened can arrive while this ran.
        mainHandler.post { if (helperSaysClosed != false) sleepNow() }
    }

    private fun docked(): Boolean = LidPlan.docked(
        displays.displays.count { it.displayId != Display.DEFAULT_DISPLAY && it.flags and Display.FLAG_PRIVATE == 0 },
    )

    /**
     * Unmutes, once, every input device still muted through the kernel's `inhibited` switch: nothing in the app mutes
     * one, and a mute outlives the process that set it until the next reboot.
     */
    private fun clearMutedInputs() {
        if (prefs.lidMutesCleared) return
        val left = executor.executeAsRoot("for f in \$($MUTED_FILES); do echo 0 > \$f; done; $MUTED_FILES | wc -l").getOrNull()
        if (left?.trim() == "0") prefs.lidMutesCleared = true
    }

    private fun read(): LidReadings {
        val resolver = context.contentResolver
        fun global(key: String): Int? = runCatching { Settings.Global.getInt(resolver, key) }.getOrNull()
        return LidReadings(
            performance = PerfMode.getMode(executor).takeIf { it != PerfMode.Unknown }?.settingsValue,
            fan = FanMode.getMode(executor).takeIf { it != FanMode.Unknown }?.settingsValue,
            wifi = LidPlan.radioOn(global(Settings.Global.WIFI_ON)),
            bluetooth = LidPlan.radioOn(global(Settings.Global.BLUETOOTH_ON)),
            airplane = global(Settings.Global.AIRPLANE_MODE_ON)?.let { it == 1 },
        )
    }

    /** Exact, so a delay of a minute is a minute even while the Thor sleeps; inexact if exact alarms aren't allowed. */
    private fun schedule(at: Long) {
        if (alarms.canScheduleExactAlarms()) {
            alarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, savingAlarm)
        } else {
            alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, savingAlarm)
        }
    }

    private fun musicActive(): Boolean = runCatching {
        context.getSystemService(AudioManager::class.java).isMusicActive
    }.getOrDefault(false)

    private fun readLid(): Boolean? = LidPlan.lidClosed(executor.executeAsRoot(LID_STATE).getOrNull())

    private fun setPerformance(value: Int) {
        PERF_MODES.find { it.settingsValue == value }?.enable(executor)
    }

    private fun setFan(value: Int) {
        FAN_MODES.find { it.settingsValue == value }?.enable(executor)
    }

    private fun setWifi(on: Boolean) {
        executor.executeAsRoot("cmd wifi set-wifi-enabled ${if (on) "enabled" else "disabled"}")
    }

    private fun setBluetooth(on: Boolean) {
        executor.executeAsRoot("cmd bluetooth_manager ${if (on) "enable" else "disable"}")
    }

    private fun setAirplane(on: Boolean) {
        executor.executeAsRoot("cmd connectivity airplane-mode ${if (on) "enable" else "disable"}")
    }

    private fun pauseMedia() {
        val audio = context.getSystemService(AudioManager::class.java)
        listOf(KeyEvent.ACTION_DOWN, KeyEvent.ACTION_UP).forEach { action ->
            audio.dispatchMediaKeyEvent(KeyEvent(action, KeyEvent.KEYCODE_MEDIA_PAUSE))
        }
    }

    /** One job at a time on the lid thread, with the wake lock held until the last queued one is done. */
    private fun work(job: () -> Unit) {
        queued.incrementAndGet()
        wakeLock.acquire(WAKE_LOCK_MS)
        runCatching {
            worker.execute {
                try {
                    runCatching(job)
                } finally {
                    if (queued.decrementAndGet() == 0 && wakeLock.isHeld) wakeLock.release()
                }
            }
        }.onFailure { queued.decrementAndGet() }
    }

    private companion object {
        const val WAKE_LOCK_MS = 15_000L
        const val WAIT_FIRST_MS = 1_500L
        const val WAIT_AGAIN_MS = 3_000L
        const val ACTION_SAVING = "io.github.thatonecodingperson.thortools.LID_SAVE_POWER"

        // dumpsys prints LID_ABSENT for another policy first; only the first line reaches the app.
        const val LID_STATE = "dumpsys window | grep -m1 -o -E 'mLidState=LID_(OPEN|CLOSED)'"
        const val MUTED_FILES = "grep -l '^1' /sys/class/input/event*/device/inhibited 2>/dev/null"

        val PERF_MODES = listOf(PerfMode.Standard, PerfMode.Performance, PerfMode.HighPerformance)
        val FAN_MODES = listOf(FanMode.Off, FanMode.Quiet, FanMode.Smart, FanMode.Sport)
    }
}
