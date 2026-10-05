package io.github.thatonecodingperson.thortools.leds

import android.os.SystemClock
import java.io.File

/**
 * Writes the stick lights' nodes, and runs an animated look on a thread of its own. Runs in the root input helper, so it
 * never waits on PServer; the thread ends with the helper (which ends when Thor Tools disconnects).
 *
 * AYN's settings app switches the rings off while the screen is off and redraws its own look half a second after it
 * comes on, so an animation pauses with the screen and writes its frame again at least every [REFRESH_MS].
 */
internal object LightWriter {
    private const val LEFT = "/sys/class/sn3112l/led/brightness"
    private const val RIGHT = "/sys/class/sn3112r/led/brightness"
    private const val LEFT_ENABLE = "/sys/class/sn3112l/led/enable"
    private const val RIGHT_ENABLE = "/sys/class/sn3112r/led/enable"
    private const val FRAME_MS = 33L
    private const val REFRESH_MS = 500L
    private const val JOIN_MS = 200L

    private class Effect(val look: LedLook, val battery: Int, val charging: Boolean, val started: Long)

    @Volatile
    private var effect: Effect? = null

    @Volatile
    private var screenOn = true

    @Volatile
    private var loop: Thread? = null

    /** One fixed frame; stops an animation first. Not while the screen is off: AYN draws its look when it comes on. */
    @Synchronized
    fun once(frame: LedFrame): String {
        stop()
        if (!screenOn) return "ok"
        enable()
        write(frame)
        return "ok"
    }

    /** Runs [look] until [stop], drawing a frame about 30 times a second while the screen is on. */
    @Synchronized
    fun start(look: LedLook, battery: Int, charging: Boolean): String {
        stop()
        effect = Effect(look, battery, charging, SystemClock.uptimeMillis())
        if (screenOn) startLoop()
        return "ok"
    }

    @Synchronized
    fun stop(): String {
        effect = null
        stopLoop()
        return "ok"
    }

    /** The rings are dark while the screen is off, so the animation rests until it is on again. */
    @Synchronized
    fun screen(on: Boolean) {
        screenOn = on
        if (!on) {
            stopLoop()
        } else if (effect != null && loop == null) {
            startLoop()
        }
    }

    private fun startLoop() {
        val running = effect ?: return
        loop = Thread({
            var last: LedFrame? = null
            var lastWrite = 0L
            runCatching { enable() }
            while (!Thread.currentThread().isInterrupted) {
                val now = SystemClock.uptimeMillis()
                val frame = LedPlan.frame(running.look, now - running.started, running.battery, running.charging)
                if (frame != null && (frame != last || now - lastWrite >= REFRESH_MS)) {
                    if (runCatching { write(frame) }.isSuccess) {
                        last = frame
                        lastWrite = now
                    }
                }
                try {
                    Thread.sleep(FRAME_MS)
                } catch (_: InterruptedException) {
                    break
                }
            }
        }, "lights").apply {
            isDaemon = true
            start()
        }
    }

    /** Waits a moment for the loop to end, so its last frame can't land after a fixed one. */
    private fun stopLoop() {
        loop?.let { thread ->
            thread.interrupt()
            runCatching { thread.join(JOIN_MS) }
        }
        loop = null
    }

    private fun enable() {
        File(LEFT_ENABLE).writeText("1")
        File(RIGHT_ENABLE).writeText("1")
    }

    private fun write(frame: LedFrame) {
        File(LEFT).writeText(LedPlan.nodeLine(frame.left, frame.brightness))
        File(RIGHT).writeText(LedPlan.nodeLine(frame.right, frame.brightness))
    }
}
