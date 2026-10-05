package io.github.thatonecodingperson.thortools.lid

import android.content.Context
import android.hardware.display.DisplayManager
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import android.provider.Settings
import android.view.Display
import io.github.thatonecodingperson.thortools.data.SharedPrefsRepo
import io.github.thatonecodingperson.thortools.models.FanMode
import io.github.thatonecodingperson.thortools.models.PerfMode
import io.github.thatonecodingperson.thortools.tools.ShellExecutor
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * The lid: Wi-Fi and Bluetooth off while it is closed and back on when it opens, and a Thor that wakes with its lid
 * closed back to sleep after the chosen wait. Whether the lid is closed always comes from the kernel's switch state
 * ([LidPlan.LID_STATE]); the helper's reports of the sensor and the screen turning on or off only make this read it
 * again. Everything runs on one background thread under a short wake lock; events come in on the main thread.
 */
class LidController(
    private val context: Context,
    private val executor: ShellExecutor,
    private val prefs: SharedPrefsRepo,
    private val sleepNow: () -> Unit,
    private val notBack: (List<LidItem>) -> Unit,
) {
    private val worker = Executors.newSingleThreadScheduledExecutor { Thread(it, "lid").apply { isDaemon = true } }
    private val mainHandler = Handler(Looper.getMainLooper())
    private val power = context.getSystemService(PowerManager::class.java)
    private val displays = context.getSystemService(DisplayManager::class.java)
    private val wakeLock = power
        .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "ThorTools:lid")
        .apply { setReferenceCounted(false) }
    private val queued = AtomicInteger()

    /** What the helper last said: a wake right after it said "open" is the lid opening. */
    @Volatile
    private var helperSaysClosed: Boolean? = null

    // Worker thread only: the lid as the kernel last said (null before the first reading), the wake waiting to go back
    // to sleep, and how often this closing sent it back.
    private var lidClosed: Boolean? = null
    private val guard = WakeGuard()
    private var sleepWait: ScheduledFuture<*>? = null
    private var sentBack = 0

    /** Reads the lid, and turns back on what a closing before a crash or a reboot left off. */
    fun start() = work {
        prefs.forgetOldLidSwitches()
        putBackOldChanges()
        clearMutedInputs()
        readAndAct()
    }

    fun stop() {
        worker.shutdownNow()
        if (wakeLock.isHeld) wakeLock.release()
    }

    fun onLid(closed: Boolean) {
        helperSaysClosed = closed
        work {
            guard.reset()
            readAndAct()
        }
    }

    fun onScreen(on: Boolean) = work {
        val closed = readAndAct()
        when {
            !on -> cancelSleepWait()
            closed == true && prefs.lidChoices.backToSleep && guard.onWake(SystemClock.elapsedRealtime()) -> waitThenSleep()
        }
    }

    /** Reads the lid and acts when it changed, or when a closing is still to be undone; returns the reading. */
    private fun readAndAct(): Boolean? {
        val closed = readLid() ?: return null
        when {
            closed != lidClosed -> {
                lidClosed = closed
                if (closed) closed() else opened()
            }
            !closed && prefs.lidSession != null -> opened()
        }
        return closed
    }

    private fun closed() {
        val choices = prefs.lidChoices
        if (prefs.lidSession == null && choices.switchedOn > 0) {
            val off = LidPlan.toTurnOff(choices, radios())
            sentBack = 0
            // Saved before anything is turned off, so opening the lid knows what to turn back on whatever happens next.
            prefs.lidSession = LidSession(System.currentTimeMillis(), off)
            if (LidItem.WIFI in off) setWifi(false)
            if (LidItem.BLUETOOTH in off) setBluetooth(false)
        }
        // Closing the lid puts the Thor to sleep; if something keeps the screen on, the same wait applies.
        if (choices.backToSleep && power.isInteractive) waitThenSleep()
    }

    /** Turns back on what the closing turned off, and tries again while something isn't back; then records how it went. */
    private fun opened() {
        cancelSleepWait()
        val session = prefs.lidSession ?: return
        var missing = LidPlan.notBack(session, radios())
        var tries = 0
        while (missing.isNotEmpty() && tries < TRIES) {
            if (LidItem.WIFI in missing) setWifi(true)
            if (LidItem.BLUETOOTH in missing) setBluetooth(true)
            Thread.sleep(SETTLE_MS)
            missing = LidPlan.notBack(session, radios())
            tries++
        }
        prefs.lidSession = null
        prefs.lidLastResult = LidResult(session.closedAt, System.currentTimeMillis(), missing, sentBack)
        sentBack = 0
        if (missing.isNotEmpty()) mainHandler.post { notBack(missing) }
    }

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
        if (!prefs.lidChoices.backToSleep) return
        val closed = readAndAct()
        if (!LidPlan.backToSleep(closed, helperSaysClosed == false, power.isInteractive, docked())) return
        guard.sentBack(SystemClock.elapsedRealtime())
        sentBack++
        // The helper's word that the lid opened can arrive while this ran.
        mainHandler.post { if (helperSaysClosed != false) sleepNow() }
    }

    private fun docked(): Boolean = LidPlan.docked(
        displays.displays.count { it.displayId != Display.DEFAULT_DISPLAY && it.flags and Display.FLAG_PRIVATE == 0 },
    )

    private fun readLid(): Boolean? = LidPlan.lidClosed(executor.executeAsRoot(LidPlan.LID_STATE).getOrNull())

    private fun radios(): Radios {
        fun global(key: String): Int? = runCatching { Settings.Global.getInt(context.contentResolver, key) }.getOrNull()
        return Radios(
            wifi = LidPlan.radioOn(global(Settings.Global.WIFI_ON)),
            bluetooth = LidPlan.radioOn(global(Settings.Global.BLUETOOTH_ON)),
        )
    }

    private fun setWifi(on: Boolean) {
        executor.executeAsRoot("cmd wifi set-wifi-enabled ${if (on) "enabled" else "disabled"}")
    }

    private fun setBluetooth(on: Boolean) {
        executor.executeAsRoot("cmd bluetooth_manager ${if (on) "enable" else "disable"}")
    }

    /** Performance, fan and airplane mode still waiting in a stored session are put back once; the lid doesn't change them. */
    private fun putBackOldChanges() {
        val old = prefs.oldLidChanges ?: return
        old.airplane?.let { on -> executor.executeAsRoot("cmd connectivity airplane-mode ${if (on) "enable" else "disable"}") }
        old.performance?.let { value -> PERF_MODES.find { it.settingsValue == value }?.enable(executor) }
        old.fan?.let { value -> FAN_MODES.find { it.settingsValue == value }?.enable(executor) }
        prefs.lidSession = prefs.lidSession
    }

    /**
     * Unmutes, once, every input device still muted through the kernel's `inhibited` switch: nothing in the app mutes
     * one, and a mute outlives the process that set it until the next reboot.
     */
    private fun clearMutedInputs() {
        if (prefs.lidMutesCleared) return
        val left = executor.executeAsRoot("for f in \$($MUTED_FILES); do echo 0 > \$f; done; $MUTED_FILES | wc -l").getOrNull()
        if (left?.trim() == "0") prefs.lidMutesCleared = true
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

        // Bluetooth takes a few seconds to come on; Wi-Fi less.
        const val SETTLE_MS = 3_000L
        const val TRIES = 3
        const val MUTED_FILES = "grep -l '^1' /sys/class/input/event*/device/inhibited 2>/dev/null"

        val PERF_MODES = listOf(PerfMode.Standard, PerfMode.Performance, PerfMode.HighPerformance)
        val FAN_MODES = listOf(FanMode.Off, FanMode.Quiet, FanMode.Smart, FanMode.Sport)
    }
}
