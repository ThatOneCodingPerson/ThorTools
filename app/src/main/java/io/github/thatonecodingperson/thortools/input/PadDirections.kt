package io.github.thatonecodingperson.thortools.input

import io.github.thatonecodingperson.thortools.hotkeys.PadButton
import kotlin.math.abs
import kotlin.math.max

/** One joystick reading: the D-pad hat and both sticks, each -1..1, up negative (as Android reports it). */
data class PadSample(
    val hatX: Float = 0f,
    val hatY: Float = 0f,
    val leftX: Float = 0f,
    val leftY: Float = 0f,
    val rightX: Float = 0f,
    val rightY: Float = 0f,
    val leftTrigger: Float = 0f,
    val rightTrigger: Float = 0f,
)

/**
 * Turns joystick readings into presses and releases of the D-pad directions and stick flicks. Pure.
 * A stick is pressed once it passes [PRESS] in its main direction and released below [RELEASE], so a resting or
 * slightly off-centre stick never counts. A stick already pushed in the first reading after [reset] counts only after
 * it has come back, so one held (say, to run) before the combo's first button never fires. After [reset] the D-pad has
 * no such rule: Android only reports changes, so the catcher's first reading is always a new press. [start] knows the
 * whole pad (the root helper reads it raw), so a D-pad direction held then waits for its release as well.
 */
class PadDirections {
    data class Change(val button: PadButton, val down: Boolean)

    private val hatX = Hat(PadButton.DPAD_LEFT, PadButton.DPAD_RIGHT)
    private val hatY = Hat(PadButton.DPAD_UP, PadButton.DPAD_DOWN)
    private val left = Stick(PadButton.LSTICK_UP, PadButton.LSTICK_DOWN, PadButton.LSTICK_LEFT, PadButton.LSTICK_RIGHT)
    private val right = Stick(PadButton.RSTICK_UP, PadButton.RSTICK_DOWN, PadButton.RSTICK_LEFT, PadButton.RSTICK_RIGHT)
    private var first = true

    fun update(sample: PadSample): List<Change> {
        val changes = mutableListOf<Change>()
        hatX.update(sample.hatX, changes)
        hatY.update(sample.hatY, changes)
        left.update(sample.leftX, sample.leftY, first, changes)
        right.update(sample.rightX, sample.rightY, first, changes)
        first = false
        return changes
    }

    fun reset() {
        listOf(hatX, hatY).forEach { it.clear() }
        listOf(left, right).forEach { it.clear() }
        first = true
    }

    /**
     * Starts watching from [sample], the pad as it is right now (the root helper knows it): a stick or D-pad direction
     * already pushed counts only after it has come back, so one held before the combo's first button never fires.
     */
    fun start(sample: PadSample) {
        reset()
        hatX.hold(sample.hatX)
        hatY.hold(sample.hatY)
        val none = mutableListOf<Change>()
        left.update(sample.leftX, sample.leftY, first = true, changes = none)
        right.update(sample.rightX, sample.rightY, first = true, changes = none)
        first = false
    }

    /** One D-pad axis: each axis on its own, so a diagonal presses both directions. */
    private class Hat(private val negative: PadButton, private val positive: PadButton) {
        private var pressed: PadButton? = null

        /** False while a direction already held when watching started is still held. */
        private var armed = true

        fun update(value: Float, changes: MutableList<Change>) {
            val now = direction(value)
            if (!armed) {
                if (now == null) armed = true
                return
            }
            if (now == pressed) return
            pressed?.let { changes += Change(it, down = false) }
            now?.let { changes += Change(it, down = true) }
            pressed = now
        }

        /** A direction held when watching starts counts only once it has been let go. */
        fun hold(value: Float) {
            armed = direction(value) == null
        }

        private fun direction(value: Float): PadButton? = when {
            value < -HAT_ON -> negative
            value > HAT_ON -> positive
            else -> null
        }

        fun clear() {
            pressed = null
            armed = true
        }
    }

    private class Stick(private val up: PadButton, private val down: PadButton, private val left: PadButton, private val right: PadButton) {
        private var pressed: PadButton? = null
        private var armed = true

        fun update(x: Float, y: Float, first: Boolean, changes: MutableList<Change>) {
            val reach = max(abs(x), abs(y))
            if (first && reach >= RELEASE) armed = false
            if (!armed) {
                if (reach < RELEASE) armed = true
                return
            }
            val current = pressed
            when {
                current == null && reach >= PRESS -> direction(x, y).let {
                    pressed = it
                    changes += Change(it, down = true)
                }
                current != null && reach < RELEASE -> {
                    pressed = null
                    changes += Change(current, down = false)
                }
                // A new main direction counts only when it clearly leads, so a diagonal doesn't flicker.
                current != null && reach >= PRESS && abs(abs(x) - abs(y)) > SWITCH_MARGIN -> direction(x, y).takeIf { it != current }?.let {
                    changes += Change(current, down = false)
                    pressed = it
                    changes += Change(it, down = true)
                }
            }
        }

        private fun direction(x: Float, y: Float): PadButton = if (abs(x) > abs(y)) {
            if (x > 0) right else left
        } else {
            if (y > 0) down else up
        }

        fun clear() {
            pressed = null
            armed = true
        }
    }

    companion object {
        const val PRESS = 0.6f
        const val RELEASE = 0.4f
        const val SWITCH_MARGIN = 0.2f
        private const val HAT_ON = 0.5f
    }
}
