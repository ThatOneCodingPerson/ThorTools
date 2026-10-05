package io.github.thatonecodingperson.thortools.hotkeys

import io.github.thatonecodingperson.thortools.hotkeys.ButtonRecorder.Recorded
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ButtonRecorderTest {
    @Test
    fun `one button is recorded when it comes up`() {
        val recorder = ButtonRecorder()
        assertNull(recorder.onKey(PadButton.BACK, pressed = true))
        assertEquals(Recorded(PadButton.BACK, null), recorder.onKey(PadButton.BACK, pressed = false))
    }

    @Test
    fun `a button pressed while the first is held makes a combo`() {
        val recorder = ButtonRecorder()
        recorder.onKey(PadButton.HOME, pressed = true)
        recorder.onKey(PadButton.A, pressed = true)
        assertNull(recorder.onKey(PadButton.A, pressed = false))
        assertEquals(Recorded(PadButton.HOME, PadButton.A), recorder.onKey(PadButton.HOME, pressed = false))
    }

    @Test
    fun `the combo ends when the last button comes up, whichever it is`() {
        val recorder = ButtonRecorder()
        recorder.onKey(PadButton.SELECT, pressed = true)
        recorder.onKey(PadButton.R1, pressed = true)
        assertNull(recorder.onKey(PadButton.SELECT, pressed = false))
        assertEquals(Recorded(PadButton.SELECT, PadButton.R1), recorder.onKey(PadButton.R1, pressed = false))
    }

    @Test
    fun `a release from before recording started is ignored`() {
        val recorder = ButtonRecorder()
        assertNull(recorder.onKey(PadButton.A, pressed = false))
        recorder.onKey(PadButton.B, pressed = true)
        assertEquals(Recorded(PadButton.B, null), recorder.onKey(PadButton.B, pressed = false))
    }

    @Test
    fun `a direction can't start a recording but can finish a combo`() {
        val recorder = ButtonRecorder()
        assertNull(recorder.onKey(PadButton.RSTICK_UP, pressed = true))
        assertNull(recorder.onKey(PadButton.RSTICK_UP, pressed = false))
        recorder.onKey(PadButton.HOME, pressed = true)
        recorder.onKey(PadButton.RSTICK_UP, pressed = true)
        recorder.onKey(PadButton.RSTICK_UP, pressed = false)
        assertEquals(Recorded(PadButton.HOME, PadButton.RSTICK_UP), recorder.onKey(PadButton.HOME, pressed = false))
    }

    @Test
    fun `a third button doesn't change the combo`() {
        val recorder = ButtonRecorder()
        recorder.onKey(PadButton.HOME, pressed = true)
        recorder.onKey(PadButton.X, pressed = true)
        recorder.onKey(PadButton.Y, pressed = true)
        recorder.onKey(PadButton.Y, pressed = false)
        recorder.onKey(PadButton.X, pressed = false)
        assertEquals(Recorded(PadButton.HOME, PadButton.X), recorder.onKey(PadButton.HOME, pressed = false))
    }
}
