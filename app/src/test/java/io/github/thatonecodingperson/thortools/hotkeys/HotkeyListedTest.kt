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

    @Test
    fun `hotkeys on Home need single-press Home, Desktop controls' one too, a tap that only goes Home doesn't`() {
        val hotkeys = listOf(
            Hotkey(PadButton.HOME, null, PressKind.TAP, ThorAction.HOME),
            Hotkey(PadButton.HOME, null, PressKind.DOUBLE, ThorAction.HOME_BOTH),
            Hotkey(PadButton.HOME, PadButton.R1, PressKind.TAP, ThorAction.TOGGLE_DESKTOP),
            Hotkey(PadButton.HOME, PadButton.X, PressKind.TAP, ThorAction.SCREENSHOT),
            Hotkey(PadButton.AYN, null, PressKind.TAP, ThorAction.OPEN_QUICK_PANEL),
        )
        assertEquals(3, HotkeyList.usingHome(hotkeys))
        assertEquals(0, HotkeyList.usingHome(HotkeyList.defaults))
    }
}
