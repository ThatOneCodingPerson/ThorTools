package io.github.thatonecodingperson.thortools.input

import io.github.thatonecodingperson.thortools.hotkeys.PadButton
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HelperMessagesTest {

    @Test
    fun `helper lines round trip and junk is rejected`() {
        val messages = listOf(
            HelperMessage.Touch(Screen.BOTTOM),
            HelperMessage.Lift(Screen.TOP),
            HelperMessage.Status("top /dev/input/event2 fts_ts"),
            HelperMessage.Result(7, ok = true, text = "swapped"),
            HelperMessage.Result(8, ok = false, text = "moveRootTaskToDisplay failed: no such method"),
            HelperMessage.Direction(PadButton.RSTICK_UP, down = true),
            HelperMessage.Direction(PadButton.DPAD_LEFT, down = false),
            HelperMessage.Lid(closed = true),
            HelperMessage.Lid(closed = false),
        )
        messages.forEach { assertEquals(it, HelperMessage.parse(HelperMessage.format(it))) }
        assertNull(HelperMessage.parse("b HOME 1"))
        assertNull(HelperMessage.parse("t left"))
        assertNull(HelperMessage.parse("x 1 2 3"))
        assertNull(HelperMessage.parse(""))
        assertNull(HelperMessage.parse("r x 1 ok"))
        assertNull(HelperMessage.parse("r 3 2 ok"))
        assertNull(HelperMessage.parse("d rstick_up 2"))
        assertNull(HelperMessage.parse("d nothing 1"))
        assertNull(HelperMessage.parse("d home 1"))
        assertNull(HelperMessage.parse("l 2"))
    }
}
