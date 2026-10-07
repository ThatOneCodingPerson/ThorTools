package io.github.thatonecodingperson.thortools.hotkeys

import io.github.thatonecodingperson.thortools.actions.ThorAction
import org.junit.Assert.assertEquals
import org.junit.Test

class HotkeyListedTest {
    @Test
    fun `the Hotkeys menu lists every hotkey but Desktop controls' on and off, which is set there`() {
        val screenshot = Hotkey(PadButton.HOME, PadButton.A, PressKind.TAP, ThorAction.SCREENSHOT)
        val desktop = Hotkey(PadButton.HOME, PadButton.START, PressKind.TAP, ThorAction.TOGGLE_DESKTOP)
        assertEquals(listOf(screenshot), HotkeyList.listed(listOf(screenshot, desktop)))
    }
}
