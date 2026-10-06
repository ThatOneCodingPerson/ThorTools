package io.github.thatonecodingperson.thortools.oled

import android.accessibilityservice.AccessibilityService
import android.graphics.Point
import android.hardware.display.DisplayManager
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.Display
import io.github.thatonecodingperson.thortools.R
import io.github.thatonecodingperson.thortools.actions.CloseTarget
import io.github.thatonecodingperson.thortools.data.SharedPrefsRepo
import io.github.thatonecodingperson.thortools.input.Screen
import io.github.thatonecodingperson.thortools.tools.ShellExecutor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * OLED Safety in the running service: follows input on each screen, covers a screen that sits idle (dim or black, as
 * chosen) until it is touched or a button is pressed, runs the refresher, and hands the root helper's engine its work
 * (the pixel shifter, watching for still pictures, still areas). Touches come from the root helper, so without it
 * nothing is covered. Which screen has the controller is read in the background at every check. Main thread.
 */
class OledGuard(
    private val service: AccessibilityService,
    private val executor: ShellExecutor,
    private val prefs: SharedPrefsRepo,
    private val scope: CoroutineScope,
    private val bottomDisplay: () -> Int?,
    private val helperRunning: () -> Boolean,
    /** A command for the root helper and what to do with its answer (main thread); false when it couldn't be sent. */
    private val toHelper: (name: String, args: List<String>, onResult: (Boolean, String) -> Unit) -> Boolean,
) {
    private val mainHandler = Handler(Looper.getMainLooper())
    private val cover = ScreenCover(service)
    private val watch = IdleWatch(now())
    private var choices = OledChoices()
    private var controllerOn: Screen? = null
    private var screenOn = true
    private var following: Job? = null
    private val check = Runnable { checkNow() }

    /** What the engine was last told; null makes the next send go out whatever it is. */
    private var engineSent: String? = null

    /** Since when each display's picture has stayed still, as the engine reports it. */
    private val stillSince = mutableMapOf<Int, Long>()

    fun start() {
        following = scope.launch {
            prefs.oledChoicesChanges().collect { latest ->
                mainHandler.post {
                    // Switched on: the waiting starts now, not at the last input before.
                    if (latest.idle && !choices.idle) watch.restart(now())
                    choices = latest
                    if (!latest.idle) coverIdle(emptySet())
                    sendEngine()
                    schedule(0)
                }
            }
        }
    }

    fun stop() {
        following?.cancel()
        mainHandler.removeCallbacks(check)
        cover.removeAll()
        toHelper("oledstop", emptyList()) { _, _ -> }
    }

    /** A new helper starts from nothing, and the one before may have been killed with a screen moved. */
    fun onHelperConnected() {
        engineSent = null
        sendEngine()
    }

    /** The engine's report: display [displayId]'s picture went still, or moves again. */
    fun onPicture(displayId: Int, still: Boolean) {
        if (still) stillSince[displayId] = now() else stillSince.remove(displayId)
    }

    private fun sendEngine() {
        val config = EngineConfig(
            shiftDisplays = if (choices.shift) choices.shiftScreens.screens.mapNotNull(::displayOf).toSet() else emptySet(),
            radius = choices.shiftRadius,
            everyMs = choices.shiftEverySeconds * 1_000L,
            whenStill = choices.shiftMode == ShiftMode.STILL,
            stillMs = choices.shiftStillSeconds * 1_000L,
            center = choices.shiftCenter,
            watchDisplays = if (choices.refreshAuto) choices.refreshScreens.screens.mapNotNull(::displayOf).toSet() else emptySet(),
            areaDisplays = if (choices.areas) choices.areaScreens.screens.mapNotNull(::displayOf).toSet() else emptySet(),
            areaStillMs = choices.areaStillSeconds * 1_000L,
            areaDim = if (choices.areaAction.dim) choices.areaDimPercent else 0,
            areaShift = if (choices.areaAction.shift) choices.areaShiftPixels else 0,
            areaEveryMs = choices.areaEverySeconds * 1_000L,
            areaExperimental = choices.areaExperimental,
        )
        // Stopping also puts every screen back where the display manager has it.
        val (name, args) = if (config.idle) "oledstop" to emptyList() else "oledconfig" to listOf(config.encode())
        val text = "$name ${args.joinToString(" ")}"
        if (text == engineSent) return
        stillSince.keys.retainAll(config.watching)
        if (toHelper(name, args) { _, _ -> }) engineSent = text
    }

    fun onTouch(screen: Screen) {
        watch.touched(screen, now())
        wake(screen)
    }

    /** Any button: it counts for the screen with the controller, or for both while that isn't known yet. */
    fun onButton() {
        watch.pressed(controllerOn, now())
        controllerOn?.let(::wake) ?: Screen.entries.forEach(::wake)
    }

    fun onScreen(on: Boolean) {
        screenOn = on
        if (on) {
            watch.restart(now())
            schedule(CHECK_MS)
        } else {
            mainHandler.removeCallbacks(check)
            cover.removeAll()
            stillSince.clear()
        }
    }

    /** The refresher on the chosen screens, with the chosen pattern and length. */
    fun refreshNow() {
        choices.refreshScreens.screens.forEach { screen -> displayOf(screen)?.let(::refresh) }
    }

    /**
     * The refresher on display [displayId]. The inverse picture is copied first (by the root helper, before anything of
     * ours covers the screen); without it the sweep runs instead.
     */
    private fun refresh(displayId: Int) {
        val current = choices
        val label = { left: Int -> service.getString(R.string.oledRefreshing, left) }
        val start = { pattern: RefreshPattern, inverse: Inverse? ->
            cover.refresh(displayId, current.refreshSeconds, pattern, inverse, label) { stillSince.remove(displayId) }
        }
        if (current.refreshPattern != RefreshPattern.INVERSE) return start(current.refreshPattern, null)
        val display = service.getSystemService(DisplayManager::class.java).getDisplay(displayId) ?: return
        val size = Point().also {
            @Suppress("DEPRECATION")
            display.getRealSize(it)
        }
        val args = listOf(displayId.toString(), (size.x / INVERSE_SCALE).toString(), (size.y / INVERSE_SCALE).toString())
        val sent = toHelper("oledshot", args) { ok, text ->
            val inverse = if (ok) RefreshPattern.negative(text) else null
            if (inverse != null) start(RefreshPattern.INVERSE, inverse) else start(RefreshPattern.SWEEP, null)
        }
        if (!sent) start(RefreshPattern.SWEEP, null)
    }

    /** The refresher by itself on each chosen screen whose picture has stayed still long enough and isn't covered. */
    private fun refreshStill() {
        val after = choices.refreshAfterMinutes * IdleWatch.MINUTE_MS
        choices.refreshScreens.screens.forEach { screen ->
            val displayId = displayOf(screen) ?: return@forEach
            val since = stillSince[displayId] ?: return@forEach
            if (now() - since < after || cover.kindOn(displayId) != null) return@forEach
            stillSince[displayId] = now()
            refresh(displayId)
        }
    }

    private fun wake(screen: Screen) {
        val displayId = displayOf(screen) ?: return
        val kind = cover.kindOn(displayId)
        if (kind == ScreenCover.Kind.DIM || kind == ScreenCover.Kind.BLACK) cover.remove(displayId)
    }

    private fun schedule(delayMs: Long) {
        mainHandler.removeCallbacks(check)
        if ((choices.idle || choices.refreshAuto) && screenOn) mainHandler.postDelayed(check, delayMs)
    }

    private fun checkNow() {
        scope.launch {
            val focused = CloseTarget.parseFocusedDisplay(executor.executeAsRoot(FOCUSED_DISPLAY).getOrNull())
            val media = runCatching { service.getSystemService(AudioManager::class.java).isMusicActive }.getOrDefault(false)
            mainHandler.post {
                controllerOn = focused?.let(::screenOf)
                // The choices as they are now: they may have changed while the focus was read.
                if (screenOn && choices.idle && helperRunning()) {
                    coverIdle(watch.idle(choices, now(), controllerOn, media))
                } else {
                    coverIdle(emptySet())
                }
                if (screenOn && choices.refreshAuto) refreshStill()
                schedule(CHECK_MS)
            }
        }
    }

    /** Covers exactly the [idle] screens; a running sweep is left alone. */
    private fun coverIdle(idle: Set<Screen>) {
        Screen.entries.forEach { screen ->
            val displayId = displayOf(screen) ?: return@forEach
            val kind = cover.kindOn(displayId)
            when {
                kind == ScreenCover.Kind.REFRESH -> Unit
                screen !in idle -> if (kind != null) cover.remove(displayId)
                choices.look == IdleLook.DIM -> cover.dim(displayId, choices.dimPercent)
                else -> cover.black(displayId) { onTouch(screen) }
            }
        }
    }

    private fun displayOf(screen: Screen): Int? = if (screen == Screen.TOP) Display.DEFAULT_DISPLAY else bottomDisplay()

    private fun screenOf(displayId: Int): Screen? = when (displayId) {
        Display.DEFAULT_DISPLAY -> Screen.TOP
        bottomDisplay() -> Screen.BOTTOM
        else -> null
    }

    private fun now(): Long = SystemClock.elapsedRealtime()

    private companion object {
        const val CHECK_MS = 15_000L
        const val INVERSE_SCALE = 8
        const val FOCUSED_DISPLAY = "dumpsys input | grep -m1 FocusedDisplayId"
    }
}
