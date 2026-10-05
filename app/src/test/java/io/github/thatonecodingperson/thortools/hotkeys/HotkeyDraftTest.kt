package io.github.thatonecodingperson.thortools.hotkeys

import io.github.thatonecodingperson.thortools.actions.ThorAction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HotkeyDraftTest {
    @Test
    fun `editing a single press and saving unchanged gives the same hotkey`() {
        PressKind.entries.forEach { press ->
            val hotkey = Hotkey(PadButton.BACK, null, press, ThorAction.RECENTS, showText = false)
            assertEquals(hotkey, HotkeyDraft.of(hotkey).toHotkey())
        }
    }

    @Test
    fun `editing a combo and saving unchanged gives the same hotkey`() {
        PressKind.entries.forEach { press ->
            val hotkey = Hotkey(PadButton.HOME, PadButton.L1, press, ThorAction.CONTROLLER_TO_TOP, lock = true)
            val draft = HotkeyDraft.of(hotkey)
            assertTrue(draft.combo)
            assertEquals(press, draft.secondPress)
            assertEquals(hotkey, draft.toHotkey())
        }
        val app = Hotkey(PadButton.SELECT, PadButton.R1, PressKind.TAP, ThorAction.LAUNCH_APP, arg = "org.example.game@bottom")
        assertEquals(app, HotkeyDraft.of(app).toHotkey())
    }

    @Test
    fun `a combo needs its second button`() {
        val draft = HotkeyDraft(editing = null, action = ThorAction.SCREENSHOT, button = PadButton.HOME).withChoice(PressChoice.COMBO)
        assertFalse(draft.allowed)
        assertNull(draft.toHotkey())
        assertEquals(DraftSlot.SECOND, draft.slot)
    }

    @Test
    fun `switching to a single press drops the second button from the hotkey but keeps it in the draft`() {
        val combo = HotkeyDraft.of(Hotkey(PadButton.HOME, PadButton.X, PressKind.DOUBLE, ThorAction.SCREENSHOT))
        val single = combo.withChoice(PressChoice.HOLD)
        assertEquals(Hotkey(PadButton.HOME, null, PressKind.HOLD, ThorAction.SCREENSHOT), single.toHotkey())
        assertEquals(PadButton.X, single.second)
        assertEquals(DraftSlot.FIRST, single.slot)
        val back = single.withChoice(PressChoice.COMBO).toHotkey()
        assertEquals(Hotkey(PadButton.HOME, PadButton.X, PressKind.DOUBLE, ThorAction.SCREENSHOT), back)
    }

    @Test
    fun `the press of a combo is the second button's`() {
        val draft = HotkeyDraft(
            editing = null,
            action = ThorAction.SCREENSHOT,
            button = PadButton.BACK,
            second = PadButton.R1,
            press = PressKind.HOLD,
            combo = true,
            secondPress = PressKind.TRIPLE,
        )
        assertEquals(Hotkey(PadButton.BACK, PadButton.R1, PressKind.TRIPLE, ThorAction.SCREENSHOT), draft.toHotkey())
        assertEquals(PressChoice.COMBO, draft.choice)
    }

    @Test
    fun `a recording with two buttons is a combo, with one a single press`() {
        val draft = HotkeyDraft(editing = null, action = ThorAction.SCREENSHOT)
        assertTrue(draft.withRecorded(PadButton.HOME, PadButton.A).combo)
        val single = draft.withRecorded(PadButton.BACK, null)
        assertFalse(single.combo)
        assertEquals(Hotkey(PadButton.BACK, null, PressKind.TAP, ThorAction.SCREENSHOT), single.toHotkey())
    }

    @Test
    fun `Home and Back together are still not allowed`() {
        val draft = HotkeyDraft(editing = null, action = ThorAction.SCREENSHOT, button = PadButton.HOME, second = PadButton.BACK)
        assertFalse(draft.withChoice(PressChoice.COMBO).allowed)
    }

    @Test
    fun `switches only stick to actions that have them`() {
        val draft = HotkeyDraft(editing = null, action = ThorAction.SCREENSHOT, button = PadButton.BACK, lock = true, cleanMemory = true)
        assertEquals(Hotkey(PadButton.BACK, null, PressKind.TAP, ThorAction.SCREENSHOT), draft.toHotkey())
        val clean = draft.copy(action = ThorAction.CLEAR_BACKGROUND).toHotkey()
        assertEquals(true, clean?.cleanMemory)
        assertEquals(false, clean?.lock)
    }
}
