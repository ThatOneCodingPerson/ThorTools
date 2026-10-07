package io.github.thatonecodingperson.thortools.desktop

import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.pow

/** A stick's push turned into pointer speed and wheel notches, as [DesktopLayout] sets them. Pure. */
object PointerMotion {
    /** Pixels a second at full tilt and speed 1, before Android's own pointer speed. */
    const val TOP_SPEED = 1400f

    /** Wheel notches a second at full tilt and scroll speed 1. */
    const val TOP_SCROLL = 14f

    /** How much the precision button slows the pointer. */
    const val PRECISION = 0.3f

    /**
     * The stick's travel past the dead zone, 0..1, shaped by the acceleration curve; the direction comes from [x] and
     * [y] (each -1..1, up negative).
     */
    fun push(x: Float, y: Float, deadZone: Float, exponent: Float): Float {
        val travel = hypot(x, y).coerceAtMost(1f)
        if (travel <= deadZone) return 0f
        return ((travel - deadZone) / (1f - deadZone)).coerceIn(0f, 1f).pow(exponent)
    }

    /** Pointer speed in pixels a second for each axis. */
    fun velocity(x: Float, y: Float, layout: DesktopLayout, precise: Boolean = false): Pair<Float, Float> {
        val push = push(x, y, layout.deadZone, layout.acceleration.exponent)
        if (push == 0f) return 0f to 0f
        val travel = hypot(x, y)
        val speed = push * TOP_SPEED * layout.speed * (if (precise) PRECISION else 1f)
        val vx = x / travel * speed
        val vy = y / travel * speed * (if (layout.invertY) -1f else 1f)
        return vx to vy
    }

    /**
     * Wheel notches a second, vertical and horizontal. Pushing up scrolls up (a positive notch), unless
     * [DesktopLayout.naturalScroll] turns it round, as on a touchpad.
     */
    fun scroll(x: Float, y: Float, layout: DesktopLayout): Pair<Float, Float> {
        val push = push(x, y, layout.deadZone, Acceleration.LIGHT.exponent)
        if (push == 0f) return 0f to 0f
        val travel = hypot(x, y)
        val rate = push * TOP_SCROLL * layout.scrollSpeed
        val sign = if (layout.naturalScroll) 1f else -1f
        val vertical = sign * y / travel * rate
        val horizontal = if (layout.horizontalScroll) -sign * x / travel * rate else 0f
        return vertical to horizontal
    }
}

/** Fractions of a step carried over, so a slow stick still moves: whole steps out, the rest kept. */
class StepCarry {
    private var rest = 0f

    fun take(amount: Float): Int {
        rest += amount
        val whole = rest.toInt()
        rest -= whole
        return whole
    }

    fun reset() {
        rest = 0f
    }
}

/**
 * A button that does one thing on a tap and another when held: [press] at its going down, [release] at its going up
 * (true when it was a tap), [held] while it is down (true once, when it has been down [holdMs]).
 */
class TapOrHold(private val holdMs: Long) {
    private var downAt: Long? = null
    private var holdDone = false

    fun press(now: Long) {
        downAt = now
        holdDone = false
    }

    fun held(now: Long): Boolean {
        val since = downAt ?: return false
        if (holdDone || now - since < holdMs) return false
        holdDone = true
        return true
    }

    fun release(): Boolean {
        val wasTap = downAt != null && !holdDone
        downAt = null
        holdDone = false
        return wasTap
    }

    val isDown: Boolean get() = downAt != null
}

/** An analog trigger as a button: down past [threshold], up again a little below it so it doesn't flicker. */
class TriggerButton {
    var down = false
        private set

    /** The new state for a trigger at [value] (0..1); true when it changed. */
    fun update(value: Float, threshold: Float): Boolean {
        val next = if (down) value > threshold - HYSTERESIS else value > threshold
        if (next == down) return false
        down = next
        return true
    }

    companion object {
        const val HYSTERESIS = 0.08f

        /** A trigger at [value] is still pulled, for [ReleaseWait]: above where a click would let go. */
        fun pulled(value: Float, threshold: Float): Boolean = value > threshold - HYSTERESIS
    }
}

/**
 * Keeps a stick or trigger quiet once [arm]ed until it has been let go: a stick still pushed when a hotkey ends, or when
 * desktop controls come on, must not move the pointer or click.
 */
class ReleaseWait {
    private var armed = false

    fun arm() {
        armed = true
    }

    /** Whether input that is [active] now may act. */
    fun passes(active: Boolean): Boolean {
        if (armed && !active) armed = false
        return !armed
    }
}

/** Rounds pixel speeds to whole steps for one tick of [dtMs]; a tiny move is never lost. */
fun stepsFor(perSecond: Float, dtMs: Long, carry: StepCarry): Int = if (abs(perSecond) < 0.001f) {
    carry.take(0f)
} else {
    carry.take(perSecond * dtMs / 1000f)
}
