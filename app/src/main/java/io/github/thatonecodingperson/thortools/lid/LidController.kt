package io.github.thatonecodingperson.thortools.lid

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioManager
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import android.provider.Settings
import android.view.KeyEvent
import io.github.thatonecodingperson.thortools.data.SharedPrefsRepo
import io.github.thatonecodingperson.thortools.models.FanMode
import io.github.thatonecodingperson.thortools.models.PerfMode
import io.github.thatonecodingperson.thortools.panel.MediaListener
import io.github.thatonecodingperson.thortools.tools.ShellExecutor
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger

/**
 * Carries out the lid sandbox: what its switches ask for when the lid closes, and putting all of it back when the lid
 * opens, checked twice. The root helper reports the lid (`hall_switch`); the screen turning on or off makes this read
 * Android's own lid state too, in case a report was missed. Everything runs on one background thread under a short wake
 * lock, so the work is done before the Thor sleeps. Events come in on the main thread. A Save power part that runs later
 * (a delay, or media playing) is an exact alarm that wakes the Thor; opening the lid first cancels it.
 */
class LidController(
    private val context: Context,
    private val executor: ShellExecutor,
    private val prefs: SharedPrefsRepo,
    private val helper: (name: String, args: List<String>, onResult: (Boolean, String) -> Unit) -> Boolean,
    private val closeBackground: () -> Unit,
    private val sleepNow: () -> Unit,
    private val notRestored: (List<LidItem>) -> Unit,
    /** The app in front, for the apps that keep the buttons on. */
    private val frontApp: () -> String?,
) {
    private val worker = Executors.newSingleThreadExecutor { Thread(it, "lid").apply { isDaemon = true } }
    private val mainHandler = Handler(Looper.getMainLooper())
    private val wakeLock = context.getSystemService(PowerManager::class.java)
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

    /** What the helper last said (main thread): a wake right after it said "open" is the lid opening. */
    private var helperSaysClosed: Boolean? = null
    private val guard = WakeGuard()

    /** Reads the lid, and puts back what a closing before a crash or a reboot left changed. */
    fun start() {
        context.registerReceiver(savingReceiver, IntentFilter(ACTION_SAVING), Context.RECEIVER_NOT_EXPORTED)
        work { lidIs(readLid()) }
    }

    fun stop() {
        alarms.cancel(savingAlarm)
        runCatching { context.unregisterReceiver(savingReceiver) }
        worker.shutdownNow()
        if (wakeLock.isHeld) wakeLock.release()
    }

    fun onLid(closed: Boolean) {
        helperSaysClosed = closed
        if (!closed) guard.reset()
        work { lidIs(closed) }
    }

    fun onScreen(on: Boolean) = work {
        val closed = readLid()
        lidIs(closed)
        // Only on Android's own word that the lid is closed: a missed reading must never send an opening back to sleep.
        if (on && closed == true) backToSleep()
    }

    private fun lidIs(closed: Boolean?) {
        if (closed == null || closed == lidClosed) {
            // An earlier session can still be waiting with the lid open: the app stopped before it opened.
            if (closed == false && prefs.lidSession != null) opened()
            return
        }
        lidClosed = closed
        if (closed) closed() else opened()
    }

    private fun closed() {
        val choices = prefs.lidChoices
        prefs.lidSession?.let { saved ->
            // The app started again with the lid still closed before the Save power part ran.
            if (saved.pending) schedule(LidPlan.savingDueAt(saved, choices))
            return
        }
        if (!choices.active) return
        val muted = LidPlan.mutes(choices, setOfNotNull(frontApp(), playingApp()))
        val later = LidPlan.savingLater(choices, musicActive())
        val session = LidPlan.session(choices, if (later) LidReadings() else read(), System.currentTimeMillis(), muted, pending = later)
        // Saved before anything changes, so whatever happens next, opening the lid knows what to put back.
        prefs.lidSession = session
        if (session.muted.isNotEmpty()) helper("mute", listOf(InputGroup.encode(session.muted))) { _, _ -> }
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
        prefs.lidLastResult = LidResult(session.closedAt, System.currentTimeMillis(), missing)
        if (missing.isNotEmpty()) mainHandler.post { notRestored(missing) }
    }

    /** Airplane mode first, so Wi-Fi and Bluetooth come back to what they were and not to what it remembers. */
    private fun restore(session: LidSession, items: List<LidItem>) {
        val wanted = session.restore
        if (LidItem.INPUTS in items && session.muted.isNotEmpty()) unmute()
        if (LidItem.AIRPLANE in items) wanted.airplane?.let(::setAirplane)
        if (LidItem.WIFI in items) wanted.wifi?.let(::setWifi)
        if (LidItem.BLUETOOTH in items) wanted.bluetooth?.let(::setBluetooth)
        if (LidItem.PERFORMANCE in items) wanted.performance?.let(::setPerformance)
        if (LidItem.FAN in items) wanted.fan?.let(::setFan)
    }

    private fun check(session: LidSession, waitMs: Long): List<LidItem> {
        Thread.sleep(waitMs)
        val muted = if (session.muted.isEmpty()) 0 else mutedInputs()
        return LidPlan.notRestored(session, read(), muted)
    }

    /** The helper unmutes on its own when the lid opens; this also covers a helper that isn't running. */
    private fun unmute() {
        helper("unmute", emptyList()) { _, _ -> }
        if (mutedInputs() != 0) executor.executeAsRoot("for f in \$($MUTED_FILES); do echo 0 > \$f; done")
    }

    private fun mutedInputs(): Int? = executor.executeAsRoot("$MUTED_FILES | wc -l").getOrNull()?.trim()?.toIntOrNull()

    /** On the main thread: a wake with the lid still closed goes back to sleep, unless the lid opened meanwhile. */
    private fun backToSleep() {
        val choices = prefs.lidChoices
        if (!choices.enabled || !choices.backToSleep) return
        mainHandler.post {
            if (helperSaysClosed != false && guard.onWake(SystemClock.uptimeMillis())) sleepNow()
        }
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

    /** The app whose media is playing; only known while Thor Tools may see media sessions (the Now playing widget's access). */
    private fun playingApp(): String? = runCatching {
        context.getSystemService(MediaSessionManager::class.java)
            .getActiveSessions(ComponentName(context, MediaListener::class.java))
            .firstOrNull { it.playbackState?.state == PlaybackState.STATE_PLAYING }
            ?.packageName
    }.getOrNull()

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
