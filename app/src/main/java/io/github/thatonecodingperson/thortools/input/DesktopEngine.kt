package io.github.thatonecodingperson.thortools.input

import android.os.SystemClock
import android.view.InputDevice
import android.view.KeyCharacterMap
import android.view.KeyEvent
import io.github.thatonecodingperson.thortools.desktop.DesktopControl
import io.github.thatonecodingperson.thortools.desktop.DesktopJob
import io.github.thatonecodingperson.thortools.desktop.DesktopLayout
import io.github.thatonecodingperson.thortools.desktop.PointerMotion
import io.github.thatonecodingperson.thortools.desktop.ReleaseWait
import io.github.thatonecodingperson.thortools.desktop.StepCarry
import io.github.thatonecodingperson.thortools.desktop.StickRole
import io.github.thatonecodingperson.thortools.desktop.TapOrHold
import io.github.thatonecodingperson.thortools.desktop.TriggerButton
import io.github.thatonecodingperson.thortools.desktop.stepsFor
import java.io.File
import java.io.Writer
import kotlin.math.hypot
import kotlin.math.max

/**
 * Desktop controls inside the root helper. Android's own `uinput` tool gives Thor Tools a mouse and a keyboard of its
 * own (the keyboard has no letters, so Android's on-screen keyboard still opens); while the app has desktop controls on,
 * the pad's sticks and triggers, read raw, become pointer moves, wheel notches and clicks, and the buttons the app hands
 * over (`key`, `tap`: what its hotkeys passed on) become clicks and keys. The pad itself is never grabbed. While the app
 * says a hotkey may be under way (`hold`), the sticks and triggers stay still, and one still pushed afterwards waits
 * until it is let go. The devices go away with the tool, which ends when its input closes, so a helper that dies leaves
 * nothing behind. While the app keeps the controller on the bottom screen (`split`), keys are sent to the top screen
 * itself instead (Thor Tools' keyboard would type where the controller is), and every click is told to the app, which
 * then brings the controller back down.
 */
object DesktopEngine {
    private const val MOUSE = 1
    private const val KEYS = 2
    private const val EV_SYN = 0
    private const val EV_KEY = 1
    private const val EV_REL = 2
    private const val REL_X = 0
    private const val REL_Y = 1
    private const val REL_HWHEEL = 6
    private const val REL_WHEEL = 8
    private const val UI_SET_EVBIT = 100
    private const val UI_SET_KEYBIT = 101
    private const val UI_SET_RELBIT = 102
    private const val KEY_L2 = 312
    private const val KEY_R2 = 313
    private const val TICK_MS = 8L
    private const val HOLD_MS = 800L
    private const val TOOL = "/system/bin/uinput"

    /** Thor Tools' own devices, so the app can tell their keys from the Thor's buttons. */
    const val VENDOR = 0x1209
    const val MOUSE_PRODUCT = 0x7454
    const val KEYS_PRODUCT = 0x7455
    const val MOUSE_NAME = "Thor Tools desktop mouse"
    const val KEYS_NAME = "Thor Tools desktop keys"

    private val lock = Any()
    private var process: Process? = null
    private var tool: Writer? = null
    private var ticker: Thread? = null
    private var layout = DesktopLayout.DEFAULT
    private var on = false
    private var paused = false
    private var holding = false
    private var split = false
    private var startsHotkeys = emptySet<DesktopControl>()

    // Written by the pad reader, which must never wait for the engine: the hotkeys' D-pad and stick combos come from it.
    @Volatile
    private var l2Key = false

    @Volatile
    private var r2Key = false

    /** The buttons the app says are down, and what Thor Tools' devices have down because of them. */
    private val down = mutableSetOf<DesktopControl>()
    private val sentDown = mutableMapOf<DesktopControl, DesktopJob>()
    private val startButton = TapOrHold(HOLD_MS)
    private val leftTrigger = TriggerButton()
    private val rightTrigger = TriggerButton()
    private val leftTriggerWait = ReleaseWait()
    private val rightTriggerWait = ReleaseWait()
    private val leftStickWait = ReleaseWait()
    private val rightStickWait = ReleaseWait()
    private val moveX = StepCarry()
    private val moveY = StepCarry()
    private val wheel = StepCarry()
    private val hwheel = StepCarry()
    private val mouseEvents = mutableListOf<Int>()
    private val keyEvents = mutableListOf<Int>()

    /** Keys for the top screen while [split], sent at the next flush, and when each one went down. */
    private val topKeys = mutableListOf<Pair<DesktopJob, Boolean>>()
    private val topKeysDownAt = mutableMapOf<DesktopJob, Long>()

    /** Messages for the app, sent once [lock] is let go: sending may end the helper, which needs the lock to stop. */
    private val outbox = mutableListOf<HelperMessage>()
    private val problemsTold = mutableSetOf<String>()

    // Messages leave in the order they were made, whichever thread delivers them.
    private val deliverLock = Any()

    /** When the last tick ran, so moves follow the time that really passed. */
    private var lastTick = 0L

    /** The pad as it is now, given by the helper. */
    var sample: () -> PadSample = { PadSample() }

    /** Tells the app something: ready or not, paused by holding Start, the keyboard asked for, a problem. */
    var report: (HelperMessage) -> Unit = {}

    /**
     * From the app: `config <layout>`, `on`, `off`, `stop`, `key <control> 1|0`, `tap <control>`, `hold 1|0`,
     * `starts <control,...>` and `split 1|0`.
     */
    fun command(line: String) {
        try {
            synchronized(lock) { run(line.trim().split(' ')) }
        } catch (e: Throwable) {
            synchronized(lock) { problem("command", e) }
        }
        deliver()
    }

    private fun run(words: List<String>) {
        when (words[0]) {
            "config" -> {
                releaseAll()
                layout = DesktopLayout.decode(words.getOrNull(1))
                startButton.release()
                // Without switching by Start, a pause could never end.
                if (paused && !layout.holdStartSwitch) {
                    paused = false
                    outbox += HelperMessage.DesktopPaused(false)
                }
                quietAll()
                flush()
            }
            "on" -> {
                if (!ensureTool()) {
                    outbox += HelperMessage.DesktopReady(false)
                    return
                }
                if (!on) {
                    on = true
                    quietAll()
                }
                startTicker()
                outbox += HelperMessage.DesktopReady(true)
            }
            "off" -> {
                switchOff()
                flush()
                outbox += HelperMessage.DesktopReady(false)
            }
            "stop" -> {
                stopLocked()
                outbox += HelperMessage.DesktopReady(false)
            }
            "key" -> {
                val control = DesktopControl.byId(words.getOrNull(1)) ?: return
                key(control, words.getOrNull(2) == "1")
                flush()
            }
            "tap" -> {
                val control = DesktopControl.byId(words.getOrNull(1)) ?: return
                if (on && !paused) tap(control)
                flush()
            }
            "hold" -> {
                setHold(words.getOrNull(1) == "1")
                flush()
            }
            "split" -> {
                // Whatever is down goes up the way it went down.
                releaseAll()
                flush()
                split = words.getOrNull(1) == "1"
            }
            "starts" -> {
                val starts = words.getOrNull(1).orEmpty().split(',').mapNotNull(DesktopControl::byId).toSet()
                // A trigger handed to the app while its click is down lets go now.
                (starts - startsHotkeys).forEach { control ->
                    val button = triggerButton(control) ?: return@forEach
                    release(control)
                    button.update(0f, layout.triggerThreshold)
                }
                startsHotkeys = starts
                flush()
            }
        }
    }

    /** Ends desktop controls and the tool, so Thor Tools' devices are gone. */
    fun stop() {
        synchronized(lock) { stopLocked() }
    }

    private fun stopLocked() {
        switchOff()
        paused = false
        holding = false
        split = false
        flush()
        closeTool()
    }

    private fun switchOff() {
        on = false
        releaseAll()
        // Their releases won't come once the app stops handing buttons over.
        down.clear()
        // A Start held when desktop controls went off must not count as held when they come back.
        startButton.release()
    }

    /** A key of AYN's pad as it leaves the pad: only L2 and R2 matter here, as triggers; the app hands over the rest. */
    fun onPadKey(code: Int, pressed: Boolean) {
        when (code) {
            KEY_L2 -> l2Key = pressed
            KEY_R2 -> r2Key = pressed
        }
    }

    /** The pad was opened again (AYN re-made it): a trigger held on the old one is never released. */
    fun onPadReset() {
        l2Key = false
        r2Key = false
    }

    private fun key(control: DesktopControl, pressed: Boolean) {
        if (pressed) down += control else down -= control
        // The triggers click from their axes on each tick, unless they start hotkeys (then the app sends a tap).
        if (control == DesktopControl.L2 || control == DesktopControl.R2) {
            if (!pressed && control in startsHotkeys) release(control)
            return
        }
        if (control == DesktopControl.START && layout.holdStartSwitch) {
            if (pressed) {
                if (on) startButton.press(now())
            } else {
                // A hold the ticker hasn't seen yet is still a hold, not a tap.
                if (on && startButton.held(now())) togglePause()
                if (startButton.release() && on && !paused) tap(control)
            }
            return
        }
        if (!pressed) return release(control)
        if (on && !paused) press(control)
    }

    private fun setHold(hold: Boolean) {
        if (hold == holding) return
        holding = hold
        if (hold) {
            // A trigger's click ends here; a held button's job stays until that button is let go.
            listOf(DesktopControl.L2, DesktopControl.R2).forEach(::release)
            leftTrigger.update(0f, layout.triggerThreshold)
            rightTrigger.update(0f, layout.triggerThreshold)
            listOf(moveX, moveY, wheel, hwheel).forEach(StepCarry::reset)
        } else {
            quietAll()
        }
    }

    /** Sticks and triggers pushed right now wait until they are let go. */
    private fun quietAll() {
        listOf(leftTriggerWait, rightTriggerWait, leftStickWait, rightStickWait).forEach(ReleaseWait::arm)
        listOf(moveX, moveY, wheel, hwheel).forEach(StepCarry::reset)
    }

    private fun ensureTool(): Boolean {
        if (process?.isAlive == true && tool != null) return true
        closeTool()
        var started: Process? = null
        return try {
            started = ProcessBuilder(TOOL, "-")
                .redirectErrorStream(true)
                .redirectOutput(File("/dev/null"))
                .start()
            val writer = started.outputStream.bufferedWriter()
            val rel = listOf(REL_X, REL_Y, REL_HWHEEL, REL_WHEEL)
            writer.write(register(MOUSE, MOUSE_NAME, MOUSE_PRODUCT, listOf(EV_KEY, EV_REL), DesktopJob.mouseButtons, rel))
            writer.write(register(KEYS, KEYS_NAME, KEYS_PRODUCT, listOf(EV_KEY), DesktopJob.keys, emptyList()))
            writer.flush()
            process = started
            tool = writer
            true
        } catch (e: Exception) {
            runCatching { started?.destroy() }
            problem("uinput", e)
            false
        }
    }

    private fun closeTool() {
        runCatching { tool?.close() }
        runCatching { process?.destroy() }
        tool = null
        process = null
    }

    private fun register(id: Int, name: String, product: Int, events: List<Int>, keys: List<Int>, rel: List<Int>): String {
        val config = buildList {
            add("{\"type\":$UI_SET_EVBIT,\"data\":${events.joinToString(",", "[", "]")}}")
            add("{\"type\":$UI_SET_KEYBIT,\"data\":${keys.joinToString(",", "[", "]")}}")
            if (rel.isNotEmpty()) add("{\"type\":$UI_SET_RELBIT,\"data\":${rel.joinToString(",", "[", "]")}}")
        }.joinToString(",")
        return "{\"id\":$id,\"command\":\"register\",\"name\":\"$name\",\"vid\":$VENDOR,\"pid\":$product,\"bus\":\"usb\"," +
            "\"configuration\":[$config]}\n"
    }

    /** One ticker while on; it clears [ticker] itself, under the lock, when it ends, so a new one is never missed. */
    private fun startTicker() {
        if (ticker != null) return
        val thread = Thread {
            try {
                while (true) {
                    synchronized(lock) {
                        if (process == null || !on) {
                            ticker = null
                            return@Thread
                        }
                        try {
                            tick(now())
                        } catch (e: Exception) {
                            problem("tick", e)
                        }
                    }
                    deliver()
                    Thread.sleep(TICK_MS)
                }
            } catch (e: Throwable) {
                synchronized(lock) {
                    ticker = null
                    problem("ticker", e)
                    // Nothing moves the pointer now: let go of everything and tell the app, which switches on again.
                    runCatching {
                        releaseAll()
                        flush()
                    }
                    on = false
                    outbox += HelperMessage.DesktopReady(false)
                }
                deliver()
            }
        }
        thread.isDaemon = true
        thread.name = "desktop-controls"
        ticker = thread
        lastTick = now()
        try {
            thread.start()
        } catch (e: Throwable) {
            ticker = null
            problem("ticker", e)
        }
    }

    private fun togglePause() {
        paused = !paused
        releaseAll()
        if (!paused) quietAll()
        outbox += HelperMessage.DesktopPaused(paused)
    }

    private fun tick(now: Long) {
        val dt = (now - lastTick).coerceIn(1L, MAX_TICK_GAP_MS)
        lastTick = now
        if (layout.holdStartSwitch && startButton.held(now)) togglePause()
        if (paused || holding) return flush()
        val pad = sample()
        trigger(DesktopControl.L2, leftTrigger, leftTriggerWait, max(pad.leftTrigger, if (l2Key) 1f else 0f))
        trigger(DesktopControl.R2, rightTrigger, rightTriggerWait, max(pad.rightTrigger, if (r2Key) 1f else 0f))
        // With AYN's mouse mode moving the pointer, both sticks are AYN's.
        if (!layout.aynPointer) {
            val (left, right) = layout.sticksFor(split, scrollHeld = DesktopJob.SCROLL_HOLD in sentDown.values)
            stick(right, pad.rightX, pad.rightY, rightStickWait, dt)
            stick(left, pad.leftX, pad.leftY, leftStickWait, dt)
        }
        flush()
    }

    private fun triggerButton(control: DesktopControl): TriggerButton? = when (control) {
        DesktopControl.L2 -> leftTrigger
        DesktopControl.R2 -> rightTrigger
        else -> null
    }

    private fun trigger(control: DesktopControl, button: TriggerButton, wait: ReleaseWait, value: Float) {
        if (control in startsHotkeys) return
        if (!wait.passes(TriggerButton.pulled(value, layout.triggerThreshold))) return
        if (!button.update(value, layout.triggerThreshold)) return
        if (button.down) press(control) else release(control)
    }

    private fun stick(role: StickRole, x: Float, y: Float, wait: ReleaseWait, dt: Long) {
        if (role == StickRole.NONE) return
        if (!wait.passes(hypot(x, y) > layout.deadZone)) return
        when (role) {
            StickRole.POINTER -> {
                val (vx, vy) = PointerMotion.velocity(x, y, layout, precise())
                val dx = stepsFor(vx, dt, moveX)
                val dy = stepsFor(vy, dt, moveY)
                if (dx != 0) mouseEvents += listOf(EV_REL, REL_X, dx)
                if (dy != 0) mouseEvents += listOf(EV_REL, REL_Y, dy)
            }
            StickRole.SCROLL -> {
                val (vertical, horizontal) = PointerMotion.scroll(x, y, layout)
                val notches = stepsFor(vertical, dt, wheel)
                val sideways = stepsFor(horizontal, dt, hwheel)
                if (notches != 0) mouseEvents += listOf(EV_REL, REL_WHEEL, notches)
                if (sideways != 0) mouseEvents += listOf(EV_REL, REL_HWHEEL, sideways)
            }
            StickRole.NONE -> Unit
        }
    }

    /** The slow-down button is held; a trigger counts once pulled past its point. */
    private fun precise(): Boolean = when (val control = layout.precision) {
        null -> false
        DesktopControl.L2 -> leftTrigger.down
        DesktopControl.R2 -> rightTrigger.down
        else -> control in down
    }

    /** A tap of a button whose hold has a job of its own, or that waited for a combo: its press and release at once. */
    private fun tap(control: DesktopControl) {
        press(control)
        flush()
        release(control)
    }

    private fun press(control: DesktopControl) {
        val job = layout.job(control)
        if (job.kind != DesktopJob.Kind.NONE && job in sentDown.values) {
            sentDown[control] = job
            return
        }
        when (job.kind) {
            DesktopJob.Kind.MOUSE -> mouseEvents += listOf(EV_KEY, job.code, 1)
            DesktopJob.Kind.KEY -> if (split) topKeys += job to true else keyEvents += listOf(EV_KEY, job.code, 1)
            DesktopJob.Kind.WHEEL -> mouseEvents += listOf(EV_REL, REL_WHEEL, job.code)
            DesktopJob.Kind.APP -> outbox += HelperMessage.DesktopKeyboard
            DesktopJob.Kind.HOLD -> Unit
            DesktopJob.Kind.NONE -> return
        }
        sentDown[control] = job
    }

    private fun release(control: DesktopControl) {
        val job = sentDown.remove(control) ?: return
        if (job in sentDown.values) return
        when (job.kind) {
            DesktopJob.Kind.MOUSE -> {
                mouseEvents += listOf(EV_KEY, job.code, 0)
                if (split) outbox += HelperMessage.DesktopClicked
            }
            DesktopJob.Kind.KEY -> if (split) topKeys += job to false else keyEvents += listOf(EV_KEY, job.code, 0)
            else -> Unit
        }
    }

    /** Lets go of every click and key Thor Tools' devices hold, so nothing stays down. */
    private fun releaseAll() {
        sentDown.keys.toList().forEach(::release)
        leftTrigger.update(0f, layout.triggerThreshold)
        rightTrigger.update(0f, layout.triggerThreshold)
        listOf(moveX, moveY, wheel, hwheel).forEach(StepCarry::reset)
    }

    private fun flush() {
        sendTopKeys()
        val writer = tool ?: return
        val lines = buildString {
            if (mouseEvents.isNotEmpty()) append(inject(MOUSE, mouseEvents))
            if (keyEvents.isNotEmpty()) append(inject(KEYS, keyEvents))
        }
        mouseEvents.clear()
        keyEvents.clear()
        if (lines.isEmpty()) return
        try {
            writer.write(lines)
            writer.flush()
        } catch (e: Exception) {
            // The tool is gone, and its devices with it: Android let go of whatever they held.
            on = false
            sentDown.clear()
            down.clear()
            closeTool()
            problem("write", e)
            outbox += HelperMessage.DesktopReady(false)
        }
    }

    /** Keys for the top screen's focused window, with Ctrl, Alt and Shift as they are held now. */
    private fun sendTopKeys() {
        if (topKeys.isEmpty()) return
        val keys = topKeys.toList()
        topKeys.clear()
        keys.forEach { (job, down) ->
            val now = SystemClock.uptimeMillis()
            val downAt = if (down) now.also { topKeysDownAt[job] = it } else topKeysDownAt.remove(job) ?: now
            val action = if (down) KeyEvent.ACTION_DOWN else KeyEvent.ACTION_UP
            val event = KeyEvent(
                downAt,
                now,
                action,
                job.keyCode,
                0,
                metaState(),
                KeyCharacterMap.VIRTUAL_KEYBOARD,
                0,
                0,
                InputDevice.SOURCE_KEYBOARD,
            )
            try {
                InputInject.inject(event, TOP_DISPLAY)
            } catch (e: Exception) {
                problem("key", e)
            }
        }
    }

    private fun metaState(): Int = sentDown.values.fold(0) { meta, job ->
        meta or when (job) {
            DesktopJob.CTRL -> KeyEvent.META_CTRL_ON or KeyEvent.META_CTRL_LEFT_ON
            DesktopJob.ALT -> KeyEvent.META_ALT_ON or KeyEvent.META_ALT_LEFT_ON
            DesktopJob.SHIFT -> KeyEvent.META_SHIFT_ON or KeyEvent.META_SHIFT_LEFT_ON
            else -> 0
        }
    }

    private fun inject(id: Int, events: List<Int>): String =
        "{\"id\":$id,\"command\":\"inject\",\"events\":${(events + listOf(EV_SYN, 0, 0)).joinToString(",", "[", "]")}}\n"

    /** Told to the app's log once per kind, so a fault is seen without flooding the socket. */
    private fun problem(where: String, e: Throwable) {
        val text = "$where: ${e.javaClass.simpleName}: ${e.message.orEmpty().take(MAX_PROBLEM)}"
        if (problemsTold.size < MAX_PROBLEMS && problemsTold.add(text)) outbox += HelperMessage.DesktopProblem(text)
    }

    private fun deliver() = synchronized(deliverLock) {
        val messages = synchronized(lock) { outbox.toList().also { outbox.clear() } }
        messages.forEach { message -> runCatching { report(message) } }
    }

    private fun now(): Long = System.nanoTime() / NANOS_PER_MS

    private const val NANOS_PER_MS = 1_000_000L
    private const val TOP_DISPLAY = 0
    private const val MAX_PROBLEM = 120
    private const val MAX_PROBLEMS = 32
    private const val MAX_TICK_GAP_MS = 50L
}
