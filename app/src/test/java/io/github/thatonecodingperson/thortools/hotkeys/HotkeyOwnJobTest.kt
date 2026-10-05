package io.github.thatonecodingperson.thortools.hotkeys

import io.github.thatonecodingperson.thortools.actions.ThorAction
import io.github.thatonecodingperson.thortools.hotkeys.HotkeyRecognizer.Effect.GiveBack
import io.github.thatonecodingperson.thortools.hotkeys.HotkeyRecognizer.Effect.Run
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** A single click does the button's own job; game buttons are only part of combos. */
class HotkeyOwnJobTest {
    private val homeTap = Hotkey(PadButton.HOME, null, PressKind.TAP, ThorAction.HOME)
    private val backTap = Hotkey(PadButton.BACK, null, PressKind.TAP, ThorAction.BACK)

    @Test
    fun `a tap doing what the button does anyway is its own job`() {
        assertTrue(homeTap.isOwnJob)
        assertTrue(backTap.isOwnJob)
        assertTrue(Hotkey(PadButton.AYN, null, PressKind.TAP, ThorAction.AYN_DRAWER).isOwnJob)
        assertTrue(Hotkey(PadButton.VOLUME_UP, null, PressKind.TAP, ThorAction.LOUDER).isOwnJob)
        assertFalse(Hotkey(PadButton.HOME, null, PressKind.TAP, ThorAction.RECENTS).isOwnJob)
        assertFalse(Hotkey(PadButton.HOME, null, PressKind.DOUBLE, ThorAction.HOME).isOwnJob)
        assertFalse(Hotkey(PadButton.HOME, PadButton.X, PressKind.TAP, ThorAction.HOME).isOwnJob)
        assertFalse(Hotkey(PadButton.BACK, null, PressKind.TAP, ThorAction.HOME).isOwnJob)
    }

    @Test
    fun `Home and Back with only their own job are left alone`() {
        val keys = HotkeyRecognizer(listOf(homeTap, backTap))
        assertFalse(keys.takes(PadButton.HOME))
        assertFalse(keys.takes(PadButton.BACK))
        assertFalse(keys.onKey(PadButton.HOME, down = true, repeat = false, time = 0).consume)
        assertFalse(keys.onKey(PadButton.HOME, down = false, repeat = false, time = 80).consume)
        assertFalse(keys.onKey(PadButton.BACK, down = true, repeat = false, time = 200).consume)
        assertEquals(HotkeyRecognizer.Step(consume = false), keys.onKey(PadButton.BACK, down = false, repeat = false, time = 260))
    }

    @Test
    fun `with a double tap on Home, a single Home does Home's own job after the tap gap`() {
        val double = Hotkey(PadButton.HOME, null, PressKind.DOUBLE, ThorAction.HOME_BOTH)
        val keys = HotkeyRecognizer(listOf(homeTap, double))
        assertTrue(keys.onKey(PadButton.HOME, down = true, repeat = false, time = 0).consume)
        assertTrue(keys.onKey(PadButton.HOME, down = false, repeat = false, time = 80).consume)
        assertEquals(listOf(GiveBack(PadButton.HOME, 1)), keys.onTimer(400).effects)
        keys.onKey(PadButton.HOME, down = true, repeat = false, time = 1000)
        keys.onKey(PadButton.HOME, down = false, repeat = false, time = 1050)
        keys.onKey(PadButton.HOME, down = true, repeat = false, time = 1150)
        assertEquals(listOf(Run(double)), keys.onKey(PadButton.HOME, down = false, repeat = false, time = 1200).effects)
    }

    @Test
    fun `a tap bound to something else runs it instead of the button's job`() {
        val recents = Hotkey(PadButton.BACK, null, PressKind.TAP, ThorAction.RECENTS)
        val keys = HotkeyRecognizer(listOf(recents))
        assertTrue(keys.takes(PadButton.BACK))
        keys.onKey(PadButton.BACK, down = true, repeat = false, time = 0)
        assertEquals(listOf(Run(recents)), keys.onKey(PadButton.BACK, down = false, repeat = false, time = 80).effects)
    }

    @Test
    fun `Home's own job alone doesn't hold Home back`() {
        assertFalse(homeTap.usesHome)
        assertTrue(Hotkey(PadButton.HOME, null, PressKind.DOUBLE, ThorAction.HOME_BOTH).usesHome)
        assertTrue(Hotkey(PadButton.HOME, PadButton.R1, PressKind.TAP, ThorAction.CONTROLLER_TO_BOTTOM).usesHome)
    }

    @Test
    fun `game buttons only work in combos`() {
        listOf(PadButton.A, PadButton.B, PadButton.X, PadButton.L1, PadButton.R2, PadButton.START, PadButton.R3).forEach {
            assertFalse(it.id, Hotkey.allowed(it, null))
            assertTrue(it.id, Hotkey.allowed(PadButton.HOME, it))
        }
        assertTrue(Hotkey.allowed(PadButton.SELECT, PadButton.R1))
        listOf(PadButton.HOME, PadButton.BACK, PadButton.AYN, PadButton.VOLUME_UP, PadButton.VOLUME_DOWN).forEach {
            assertTrue(it.id, Hotkey.allowed(it, null))
        }
    }

    @Test
    fun `a game button's own press saved earlier is dropped when the list is read`() {
        val text = listOf("x;;double;system_screenshot;;text", "home;r1;tap;screens_swap;;text").joinToString("\n")
        assertEquals(
            listOf(Hotkey(PadButton.HOME, PadButton.R1, PressKind.TAP, ThorAction.SWAP_SCREENS)),
            HotkeyList.decode(text),
        )
    }

    @Test
    fun `the editor can't save a game button on its own, only in a combo`() {
        val single = HotkeyDraft(editing = null, action = ThorAction.SCREENSHOT, button = PadButton.X)
        assertFalse(single.allowed)
        assertEquals(null, single.toHotkey())
        val combo = single.copy(button = PadButton.HOME, second = PadButton.X, combo = true)
        assertTrue(combo.allowed)
    }

    @Test
    fun `the defaults list Home's and Back's own tap, without text`() {
        assertTrue(HotkeyList.defaults.containsAll(HotkeyList.ownPresses))
        assertTrue(HotkeyList.ownPresses.all { it.isOwnJob && !it.showText })
        assertEquals(HotkeyList.defaults, HotkeyList.decode(null))
    }

    @Test
    fun `own presses are added only where that tap is free`() {
        val recents = Hotkey(PadButton.BACK, null, PressKind.TAP, ThorAction.RECENTS)
        val list = listOf(HotkeyList.aynPanel, recents)
        val added = HotkeyList.withOwnPresses(list)
        assertEquals(list + Hotkey(PadButton.HOME, null, PressKind.TAP, ThorAction.HOME, showText = false), added)
        assertEquals(added, HotkeyList.withOwnPresses(added))
    }
}
