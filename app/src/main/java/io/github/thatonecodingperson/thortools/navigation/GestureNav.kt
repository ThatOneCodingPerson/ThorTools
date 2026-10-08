package io.github.thatonecodingperson.thortools.navigation

import android.util.Log
import io.github.thatonecodingperson.thortools.data.SharedPrefsRepo
import io.github.thatonecodingperson.thortools.tools.ShellExecutor
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

data class GestureNavState(
    val wish: GestureWish = GestureWish(),
    /** The app in front as the service last said; null without the service. */
    val app: String? = null,
    /** As read back; null until read, or when the read failed. */
    val reading: GestureReading? = null,
    /** What should work isn't what the system says (no PServer, or Android refused the change). */
    val failed: Boolean = false,
)

/**
 * Switches Android's navigation swipes off and on as root: the page's choice, an app profile's wish while its app is in
 * front, and the action. Only what changed is written, on one background thread; calls may come from any thread. The
 * back swipe's settings as they were are saved before the first change and put back when it returns.
 */
@Singleton
class GestureNav @Inject constructor(private val executor: ShellExecutor, private val prefs: SharedPrefsRepo) {
    private val worker = Executors.newSingleThreadScheduledExecutor { Thread(it, "gestures").apply { isDaemon = true } }

    private var app: String? = null
    private var wish = GestureWish(prefs.gestureChoice)

    /** Desktop controls stopped a moment ago; the swipes come back when this runs, unless [desktopStamp] moved on. */
    private var desktopEnding: ScheduledFuture<*>? = null
    private var desktopStamp = 0

    /** What was last written, so an app switch that changes nothing writes nothing. Worker thread only. */
    private var written: GestureParts? = null

    private val _state = MutableStateFlow(GestureNavState(wish))
    val state: StateFlow<GestureNavState> = _state.asStateFlow()

    /** Whether the gestures are meant to be on now, before the background thread has written it. */
    val on: Boolean get() = synchronized(this) { wish.on }

    /** The page's choice changed. */
    fun setChoice(choice: GestureChoice) {
        prefs.gestureChoice = choice
        synchronized(this) { wish = wish.withChoice(choice) }
        apply()
    }

    /** The app in front and its profile's wish (null when none asks), on every app event. */
    fun setForApp(packageName: String?, on: Boolean?) {
        synchronized(this) {
            if (packageName == app && on == wish.forApp) return
            app = packageName
            wish = wish.forApp(on)
        }
        apply()
    }

    /**
     * Desktop controls came into use or stopped. Stopping waits a moment: they pause while the quick panel is open or
     * the controller visits the bottom screen, which shouldn't flip the swipes each time.
     */
    fun setDesktop(inUse: Boolean) {
        synchronized(this) {
            if (!inUse) {
                // Called again while already ending: the wait isn't started over.
                if (!wish.desktop || desktopEnding != null) return
                val stamp = desktopStamp
                desktopEnding = runCatching {
                    worker.schedule({ endDesktop(stamp) }, DESKTOP_END_MS, TimeUnit.MILLISECONDS)
                }.getOrNull()
                return
            }
            desktopStamp++
            desktopEnding?.cancel(false)
            desktopEnding = null
            if (wish.desktop) return
            wish = wish.withDesktop(true)
        }
        apply()
    }

    private fun endDesktop(stamp: Int) {
        synchronized(this) {
            if (stamp != desktopStamp) return
            desktopEnding = null
            wish = wish.withDesktop(false)
        }
        runCatching { write(force = false) }
    }

    /** No app decides any more (the service stopped, or profiles were switched off). */
    fun forgetApp() = setForApp(null, null)

    /** The action; returns whether the swipes work afterwards. */
    fun toggle(): Boolean {
        val next = synchronized(this) { wish.toggled().also { wish = it } }
        if (next.choice != prefs.gestureChoice) prefs.gestureChoice = next.choice
        apply()
        return next.on
    }

    /** AYN's own switch for the top screen's home bar. */
    fun setTopBarHidden(hidden: Boolean) = post {
        executor.executeAsRoot(GestureShell.topBar(hidden))
        publish(read())
    }

    fun refresh() = post { publish(read()) }

    /** Everything written again: a restart clears the status bar flags. */
    fun reapply() = post { write(force = true) }

    /** As [reapply], waiting for it: the boot receiver's process may go once it returns. */
    fun reapplyAndWait() {
        runCatching { worker.submit { runCatching { write(force = true) } }.get(BOOT_WAIT_S, TimeUnit.SECONDS) }
    }

    /** A fresh reading on the caller's thread (never the main one), for the debug toolkit. */
    fun readNow(): GestureReading? = read()

    private fun apply() = post { write(force = false) }

    private fun post(work: () -> Unit) {
        runCatching { worker.execute { runCatching(work) } }
    }

    private fun write(force: Boolean) {
        val (now, front) = synchronized(this) { wish to app }
        val parts = now.parts
        if (!force && parts == written) {
            _state.update { it.copy(wish = now, app = front) }
            return
        }
        val reached = executor.executeAsRoot(GestureShell.home(parts.homeSwipe)).isSuccess
        if (parts.backSwipe) restoreBack() else stopBack()
        written = parts.takeIf { reached }
        val reading = read()
        Log.d(TAG, "wanted $parts by ${now.source} (desktop ${now.desktop}, app $front), read ${reading?.parts}")
        publish(reading, now)
    }

    private fun stopBack() {
        if (prefs.gestureBackSaved == null) {
            // Nothing is written blind: without a reading there would be nothing to put back.
            val now = read()?.back ?: return
            prefs.gestureBackSaved = if (now.ours) BackValues.DEFAULT else now
        }
        executor.script(GestureShell.BACK_OFF)
    }

    /** Only what Thor Tools changed goes back; values it never saved belong to someone else. */
    private fun restoreBack() {
        val saved = prefs.gestureBackSaved ?: return
        val now = read()?.back ?: return
        val script = GestureShell.backRestore(saved, now)
        if (script.isNotEmpty()) executor.script(script)
        prefs.gestureBackSaved = null
    }

    private fun read(): GestureReading? = executor.capture(GestureShell.READ).getOrNull()?.let(GestureShell::parse)

    private fun publish(reading: GestureReading?, now: GestureWish = synchronized(this) { wish }) {
        _state.value = GestureNavState(
            wish = now,
            app = synchronized(this) { app },
            reading = reading,
            failed = reading == null || reading.parts != now.parts,
        )
    }

    private companion object {
        const val TAG = "ThorToolsGestures"
        const val BOOT_WAIT_S = 20L
        const val DESKTOP_END_MS = 2000L
    }
}
