package io.github.thatonecodingperson.thortools.debug

import io.github.thatonecodingperson.thortools.actions.ThorAction
import io.github.thatonecodingperson.thortools.hotkeys.Hotkey
import io.github.thatonecodingperson.thortools.hotkeys.PadButton
import io.github.thatonecodingperson.thortools.hotkeys.PressKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HotkeyCheckTest {
    private val homeTap = Hotkey(PadButton.HOME, null, PressKind.TAP, ThorAction.HOME)
    private val homeDouble = Hotkey(PadButton.HOME, null, PressKind.DOUBLE, ThorAction.SWAP_SCREENS)
    private val backHold = Hotkey(PadButton.BACK, null, PressKind.HOLD, ThorAction.SCREENSHOT)
    private val homeR1 = Hotkey(PadButton.HOME, PadButton.R1, PressKind.TAP, ThorAction.CONTROLLER_TO_BOTTOM)
    private val homeStick = Hotkey(PadButton.HOME, PadButton.RSTICK_UP, PressKind.TAP, ThorAction.CONTROLLER_TO_TOP)
    private val inGame = Hotkey(PadButton.BACK, null, PressKind.DOUBLE, ThorAction.CLOSE_APP, apps = setOf("game"))
    private val all = listOf(homeTap, homeDouble, backHold, homeR1, homeStick, inGame)

    @Test
    fun `each type picks its own hotkeys`() {
        assertEquals(listOf(homeTap), HotkeyCheck.rows(HotkeyType.TAPS, all).map { it.hotkey })
        assertEquals(listOf(homeDouble, inGame), HotkeyCheck.rows(HotkeyType.DOUBLES, all).map { it.hotkey })
        assertEquals(listOf(backHold), HotkeyCheck.rows(HotkeyType.HOLDS, all).map { it.hotkey })
        assertEquals(listOf(homeR1), HotkeyCheck.rows(HotkeyType.COMBOS, all).map { it.hotkey })
        assertEquals(listOf(homeStick), HotkeyCheck.rows(HotkeyType.DIRECTIONS, all).map { it.hotkey })
        assertEquals(all, HotkeyCheck.rows(HotkeyType.ALL, all).map { it.hotkey })
        assertEquals(emptyList<HotkeyRow>(), HotkeyCheck.rows(HotkeyType.TRIPLES, all))
    }

    @Test
    fun `a recognised hotkey ticks its row, other presses tick nothing`() {
        var rows = HotkeyCheck.rows(HotkeyType.DOUBLES, all)
        rows = HotkeyCheck.record(rows, HotkeyEvent.Matched(homeDouble, ran = false, time = 5))
        assertEquals(5L, rows.first().seenAt)
        rows = HotkeyCheck.record(rows, HotkeyEvent.Matched(backHold, ran = false, time = 6))
        rows = HotkeyCheck.record(rows, HotkeyEvent.Unmatched(PadButton.VOLUME_UP, time = 7))
        assertEquals(listOf(5L, null), rows.map { it.seenAt })
    }

    @Test
    fun `a button's own job ticks when one press is given back`() {
        val rows = HotkeyCheck.rows(HotkeyType.TAPS, all)
        assertEquals(9L, HotkeyCheck.record(rows, HotkeyEvent.GivenBack(PadButton.HOME, presses = 1, time = 9)).single().seenAt)
        assertNull(HotkeyCheck.record(rows, HotkeyEvent.GivenBack(PadButton.HOME, presses = 2, time = 9)).single().seenAt)
    }

    @Test
    fun `hotkeys for chosen apps don't count, as they don't work on the debug screen`() {
        val rows = HotkeyCheck.record(HotkeyCheck.rows(HotkeyType.DOUBLES, all), HotkeyEvent.Matched(homeDouble, ran = false, time = 1))
        assertEquals(1 to 1, HotkeyCheck.summary(rows))
    }

    @Test
    fun `presses read as ids in the results file`() {
        assertEquals("double home", HotkeyCheck.describe(homeDouble))
        assertEquals("hold home + tap r1", HotkeyCheck.describe(homeR1))
        assertEquals("double home -> screens_swap (not run)", HotkeyCheck.describe(HotkeyEvent.Matched(homeDouble, ran = false, time = 1)))
        assertEquals("volume_up -> no hotkey", HotkeyCheck.describe(HotkeyEvent.Unmatched(PadButton.VOLUME_UP, time = 1)))
    }
}
