package io.github.thatonecodingperson.thortools.wii

import io.github.thatonecodingperson.thortools.hotkeys.PadButton
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WiiMappingTest {
    @Test
    fun `every suggestion only maps controls its tab has, and no two share a Thor button`() {
        WiiSetup.entries.forEach { setup ->
            val mapping = WiiMapping(setup)
            assertTrue(setup.name, setup.controls.containsAll(setup.suggestedButtons.keys + setup.suggestedSticks.keys))
            setup.controls.forEach { control -> assertEquals("$setup $control", emptyList<WiiControl>(), mapping.sharing(control)) }
            assertTrue(setup.suggestedButtons.values.all { it in WII_BUTTONS })
        }
    }

    @Test
    fun `button controls get buttons and stick controls get sticks`() {
        WiiSetup.entries.forEach { setup ->
            assertTrue(setup.suggestedButtons.keys.all { it.kind == WiiKind.BUTTON })
            assertTrue(setup.suggestedSticks.keys.all { it.kind == WiiKind.STICK })
            assertTrue(setup.suggestedCombos.keys.all { it.kind == WiiKind.COMBO && it in setup.controls })
        }
        WiiControl.entries.filter { it.kind == WiiKind.STICK }.forEach { assertEquals(it.name, 4, it.keys.size) }
    }

    @Test
    fun `a mapping survives being stored, and nothing stored is the suggestion`() {
        val edited = WiiMapping(WiiSetup.NUNCHUK)
            .withButton(WiiControl.REMOTE_B, PadButton.R1)
            .withButton(WiiControl.REMOTE_HOME, null)
            .withSticks(WiiControl.TILT, setOf(StickSource.RIGHT, StickSource.DPAD))
            .copy(rumble = false, relativePointer = false)
        assertEquals(edited, WiiMapping.decode(WiiSetup.NUNCHUK, edited.encode()))
        assertEquals(WiiMapping(WiiSetup.CLASSIC), WiiMapping.decode(WiiSetup.CLASSIC, null))
    }

    @Test
    fun `broken or foreign parts are left out`() {
        val mapping = WiiMapping.decode(WiiSetup.SIDEWAYS, "1=b;2=volume_up;dpad=left+moon;n_c=l1;rumble=0")
        assertEquals(mapOf(WiiControl.REMOTE_1 to PadButton.B), mapping.buttons)
        assertEquals(mapOf(WiiControl.REMOTE_DPAD to setOf(StickSource.LEFT)), mapping.sticks)
        assertFalse(mapping.rumble)
    }

    @Test
    fun `sharing a Thor button is noticed both ways, a stick counting as its directions`() {
        val mapping = WiiMapping(WiiSetup.NUNCHUK)
            .withButton(WiiControl.REMOTE_1, PadButton.A)
            .withButton(WiiControl.REMOTE_2, PadButton.RSTICK_UP)
        assertEquals(listOf(WiiControl.REMOTE_1), mapping.sharing(WiiControl.REMOTE_A))
        assertEquals(listOf(WiiControl.REMOTE_A), mapping.sharing(WiiControl.REMOTE_1))
        assertEquals(listOf(WiiControl.POINTER), mapping.sharing(WiiControl.REMOTE_2))
    }

    @Test
    fun `the Wii Remote tabs suggest letters to letters, the shake on R2 and the Nunchuk on the left`() {
        val nunchuk = WiiMapping(WiiSetup.NUNCHUK)
        assertEquals(PadButton.A, nunchuk.buttons[WiiControl.REMOTE_A])
        assertEquals(PadButton.B, nunchuk.buttons[WiiControl.REMOTE_B])
        assertEquals(PadButton.X, nunchuk.buttons[WiiControl.REMOTE_1])
        assertEquals(PadButton.Y, nunchuk.buttons[WiiControl.REMOTE_2])
        assertEquals(PadButton.R2, nunchuk.buttons[WiiControl.SHAKE])
        assertEquals(PadButton.R3, nunchuk.buttons[WiiControl.RECENTER])
        assertEquals(PadButton.L1, nunchuk.buttons[WiiControl.NUNCHUK_C])
        assertEquals(PadButton.L2, nunchuk.buttons[WiiControl.NUNCHUK_Z])
        assertEquals(PadButton.L3, nunchuk.buttons[WiiControl.NUNCHUK_SHAKE])
        assertEquals(PadButton.SELECT to PadButton.L2, nunchuk.combos[WiiControl.SIDEWAYS_TOGGLE])
        assertEquals(PadButton.SELECT to PadButton.R2, nunchuk.combos[WiiControl.UPRIGHT_TOGGLE])
        val classic = WiiMapping(WiiSetup.CLASSIC)
        assertEquals(PadButton.L1, classic.buttons[WiiControl.CLASSIC_ZL])
        assertEquals(PadButton.R1, classic.buttons[WiiControl.CLASSIC_ZR])
        assertEquals(PadButton.L2, classic.buttons[WiiControl.CLASSIC_L])
        assertEquals(PadButton.R2, classic.buttons[WiiControl.CLASSIC_R])
    }

    @Test
    fun `nothing the suggestion maps is left out by default, and a cleared control is named`() {
        WiiSetup.entries.forEach { setup -> assertEquals(setup.name, emptyList<WiiControl>(), WiiMapping(setup).leftOut()) }
        val mapping = WiiMapping(WiiSetup.NUNCHUK)
            .withButton(WiiControl.SHAKE, null)
            .withCombo(WiiControl.SIDEWAYS_TOGGLE, null, null)
            .withCombo(WiiControl.UPRIGHT_TOGGLE, PadButton.R2, null)
        assertEquals(listOf(WiiControl.SHAKE, WiiControl.SIDEWAYS_TOGGLE, WiiControl.UPRIGHT_TOGGLE), mapping.leftOut())
    }

    @Test
    fun `a hotkey changes one part at a time, is stored, and is flagged when both parts are one button`() {
        val mapping = WiiMapping(WiiSetup.POINTING)
            .withCombo(WiiControl.SIDEWAYS_TOGGLE, null, PadButton.R1)
            .withCombo(WiiControl.UPRIGHT_TOGGLE, PadButton.START, null)
        assertEquals(PadButton.SELECT to PadButton.R1, mapping.combos[WiiControl.SIDEWAYS_TOGGLE])
        assertEquals(PadButton.START to PadButton.R2, mapping.combos[WiiControl.UPRIGHT_TOGGLE])
        assertEquals(mapping, WiiMapping.decode(WiiSetup.POINTING, mapping.encode()))
        val left = mapping.withCombo(WiiControl.SIDEWAYS_TOGGLE, null, null)
        assertFalse(WiiControl.SIDEWAYS_TOGGLE in left.combos)
        assertEquals(left, WiiMapping.decode(WiiSetup.POINTING, left.encode()))
        val same = mapping.withCombo(WiiControl.UPRIGHT_TOGGLE, null, PadButton.START)
        assertTrue(same.brokenCombo(WiiControl.UPRIGHT_TOGGLE))
        // A hotkey is made of buttons that do something else alone: that isn't sharing.
        assertEquals(emptyList<WiiControl>(), mapping.sharing(WiiControl.REMOTE_MINUS))
        assertEquals(emptyList<WiiControl>(), mapping.sharing(WiiControl.SIDEWAYS_TOGGLE))
    }

    @Test
    fun `suggested puts one control back`() {
        val mapping = WiiMapping(WiiSetup.POINTING).withButton(WiiControl.REMOTE_A, PadButton.Y)
        assertFalse(mapping.isSuggested(WiiControl.REMOTE_A))
        assertTrue(mapping.suggested(WiiControl.REMOTE_A).isSuggested(WiiControl.REMOTE_A))
        assertEquals(WiiMapping(WiiSetup.POINTING), mapping.suggested(WiiControl.REMOTE_A))
    }
}
