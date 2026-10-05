package io.github.thatonecodingperson.thortools.input

import io.github.thatonecodingperson.thortools.hotkeys.PadButton
import io.github.thatonecodingperson.thortools.input.PadDirections.Change
import org.junit.Assert.assertEquals
import org.junit.Test

class PadDirectionsTest {
    private fun watching() = PadDirections().also { it.update(PadSample()) }

    @Test
    fun `each D-pad direction presses and releases`() {
        val pad = watching()
        assertEquals(listOf(Change(PadButton.DPAD_UP, true)), pad.update(PadSample(hatY = -1f)))
        assertEquals(emptyList<Change>(), pad.update(PadSample(hatY = -1f)))
        assertEquals(listOf(Change(PadButton.DPAD_UP, false)), pad.update(PadSample()))
        assertEquals(listOf(Change(PadButton.DPAD_RIGHT, true)), pad.update(PadSample(hatX = 1f)))
    }

    @Test
    fun `a D-pad side switch without the centre ends the old direction`() {
        val pad = watching()
        pad.update(PadSample(hatX = -1f))
        assertEquals(
            listOf(Change(PadButton.DPAD_LEFT, false), Change(PadButton.DPAD_RIGHT, true)),
            pad.update(PadSample(hatX = 1f)),
        )
    }

    @Test
    fun `a stick flick fires once past the press line and again only after coming back`() {
        val pad = watching()
        assertEquals(emptyList<Change>(), pad.update(PadSample(rightY = -0.5f)))
        assertEquals(listOf(Change(PadButton.RSTICK_UP, true)), pad.update(PadSample(rightY = -0.7f)))
        assertEquals(emptyList<Change>(), pad.update(PadSample(rightY = -0.5f)))
        assertEquals(listOf(Change(PadButton.RSTICK_UP, false)), pad.update(PadSample(rightY = -0.3f)))
        assertEquals(listOf(Change(PadButton.RSTICK_UP, true)), pad.update(PadSample(rightY = -0.9f)))
    }

    @Test
    fun `a resting or drifting stick never fires`() {
        val pad = watching()
        listOf(0.05f, 0.2f, 0.35f, 0.55f, 0.1f).forEach {
            assertEquals(emptyList<Change>(), pad.update(PadSample(leftX = it, leftY = -it)))
        }
    }

    @Test
    fun `a stick already pushed when watching starts waits until it comes back`() {
        val pad = PadDirections()
        assertEquals(emptyList<Change>(), pad.update(PadSample(leftY = -0.9f)))
        assertEquals(emptyList<Change>(), pad.update(PadSample(leftY = -0.8f)))
        pad.update(PadSample())
        assertEquals(listOf(Change(PadButton.LSTICK_UP, true)), pad.update(PadSample(leftY = -0.9f)))
    }

    @Test
    fun `the first D-pad reading after watching starts is a new press`() {
        // Android only reports D-pad changes: the first reading the catcher gets is the user's own press.
        val pad = PadDirections()
        assertEquals(listOf(Change(PadButton.DPAD_UP, true)), pad.update(PadSample(hatY = -1f)))
        assertEquals(listOf(Change(PadButton.DPAD_UP, false)), pad.update(PadSample()))
    }

    @Test
    fun `the main direction decides, and a clear change switches it`() {
        val pad = watching()
        assertEquals(listOf(Change(PadButton.LSTICK_RIGHT, true)), pad.update(PadSample(leftX = 0.8f, leftY = 0.3f)))
        assertEquals(emptyList<Change>(), pad.update(PadSample(leftX = 0.7f, leftY = 0.65f)))
        assertEquals(
            listOf(Change(PadButton.LSTICK_RIGHT, false), Change(PadButton.LSTICK_DOWN, true)),
            pad.update(PadSample(leftX = 0.2f, leftY = 0.9f)),
        )
    }

    @Test
    fun `the two sticks are separate`() {
        val pad = watching()
        assertEquals(
            listOf(Change(PadButton.LSTICK_LEFT, true), Change(PadButton.RSTICK_DOWN, true)),
            pad.update(PadSample(leftX = -0.9f, rightY = 0.9f)),
        )
    }

    @Test
    fun `reset forgets what was pressed`() {
        val pad = watching()
        pad.update(PadSample(hatY = 1f))
        pad.reset()
        assertEquals(emptyList<Change>(), pad.update(PadSample()))
        assertEquals(listOf(Change(PadButton.DPAD_DOWN, true)), pad.update(PadSample(hatY = 1f)))
    }
}
