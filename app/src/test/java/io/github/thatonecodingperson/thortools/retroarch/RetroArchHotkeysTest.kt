package io.github.thatonecodingperson.thortools.retroarch

import io.github.thatonecodingperson.thortools.actions.ThorAction
import io.github.thatonecodingperson.thortools.hotkeys.Hotkey
import io.github.thatonecodingperson.thortools.hotkeys.PadButton
import io.github.thatonecodingperson.thortools.hotkeys.PressKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RetroArchHotkeysTest {
    private val empty = RetroArchConfig("input_enable_hotkey_btn = \"nul\"\ninput_enable_hotkey_axis = \"nul\"\n")

    @Test
    fun `face buttons are written as the key code the controller style sends`() {
        // Xbox style: the printed buttons arrive by position (printed X, on top, is BUTTON_Y).
        assertEquals("100", RetroArchBinds.changes("input_menu_toggle", PadButton.X, xbox = true)["input_menu_toggle_btn"])
        assertEquals("97", RetroArchBinds.changes("input_reset", PadButton.A, xbox = true)["input_reset_btn"])
        assertEquals("96", RetroArchBinds.changes("input_pause_toggle", PadButton.B, xbox = true)["input_pause_toggle_btn"])
        assertEquals("99", RetroArchBinds.changes("input_screenshot", PadButton.Y, xbox = true)["input_screenshot_btn"])
        // Standard style: by the printed letter.
        assertEquals("99", RetroArchBinds.changes("input_menu_toggle", PadButton.X, xbox = false)["input_menu_toggle_btn"])
        assertEquals("96", RetroArchBinds.changes("input_reset", PadButton.A, xbox = false)["input_reset_btn"])
        assertEquals("108", RetroArchBinds.changes("input_exit_emulator", PadButton.START, xbox = true)["input_exit_emulator_btn"])
    }

    @Test
    fun `the D-pad is hat 0 and the triggers are a key and an axis`() {
        assertEquals(
            mapOf("input_state_slot_increase_btn" to "h0up", "input_state_slot_increase_axis" to "nul"),
            RetroArchBinds.changes("input_state_slot_increase", PadButton.DPAD_UP, xbox = true),
        )
        assertEquals(
            mapOf("input_hold_slowmotion_btn" to "104", "input_hold_slowmotion_axis" to "+6"),
            RetroArchBinds.changes("input_hold_slowmotion", PadButton.L2, xbox = true),
        )
        assertEquals(
            mapOf("input_hold_fast_forward_btn" to "105", "input_hold_fast_forward_axis" to "+7"),
            RetroArchBinds.changes("input_hold_fast_forward", PadButton.R2, xbox = false),
        )
        assertEquals(
            mapOf("input_reset_btn" to "nul", "input_reset_axis" to "nul"),
            RetroArchBinds.changes("input_reset", null, xbox = true),
        )
    }

    @Test
    fun `every usable button reads back as itself in both styles`() {
        listOf(true, false).forEach { xbox ->
            RetroArchBinds.usable.forEach { button ->
                val config = empty.with(RetroArchBinds.changes("input_reset", button, xbox))
                assertEquals("$button, xbox $xbox", RaBinding.Thor(button), RetroArchBinds.read(config, "input_reset", xbox))
            }
        }
    }

    @Test
    fun `bindings made in RetroArch read as the Thor's buttons where they are, else as they stand`() {
        val config = RetroArchConfig(
            """
            input_hold_fast_forward_btn = "nul"
            input_hold_fast_forward_axis = "+9"
            input_hold_slowmotion_btn = "nul"
            input_hold_slowmotion_axis = "+8"
            input_reset_btn = "123"
            input_reset_axis = "nul"
            input_pause_toggle_btn = "nul"
            input_pause_toggle_axis = "nul"
            """.trimIndent(),
        )
        assertEquals(RaBinding.Thor(PadButton.R2), RetroArchBinds.read(config, "input_hold_fast_forward", xbox = true))
        assertEquals(RaBinding.Thor(PadButton.L2), RetroArchBinds.read(config, "input_hold_slowmotion", xbox = true))
        assertEquals(RaBinding.Other("123"), RetroArchBinds.read(config, "input_reset", xbox = true))
        assertEquals(RaBinding.None, RetroArchBinds.read(config, "input_pause_toggle", xbox = true))
        assertEquals(RaBinding.None, RetroArchBinds.read(config, "input_screenshot", xbox = true))
    }

    @Test
    fun `the suggestion holds Select and gives every hotkey its own button`() {
        val suggestion = RetroArchBinds.suggestion(xbox = true)
        assertEquals("109", suggestion["input_enable_hotkey_btn"])
        val config = empty.with(suggestion)
        assertTrue(RetroArchBinds.isSuggestion(config, xbox = true))
        assertFalse(RetroArchBinds.isSuggestion(config, xbox = false))
        assertEquals(RaBinding.Thor(PadButton.SELECT), RetroArchBinds.read(config, RetroArchBinds.ENABLE, xbox = true))
        RaHotkey.entries.forEach { hotkey ->
            assertEquals(hotkey.name, RaBinding.Thor(hotkey.suggested), RetroArchBinds.read(config, hotkey.key, xbox = true))
        }
        val expected = mapOf(
            RaHotkey.EXIT to PadButton.START,
            RaHotkey.MENU to PadButton.X,
            RaHotkey.RESET to PadButton.A,
            RaHotkey.PAUSE to PadButton.B,
            RaHotkey.SCREENSHOT to PadButton.Y,
            RaHotkey.SLOT_UP to PadButton.DPAD_UP,
            RaHotkey.SLOT_DOWN to PadButton.DPAD_DOWN,
            RaHotkey.LOAD_STATE to PadButton.L1,
            RaHotkey.SAVE_STATE to PadButton.R1,
            RaHotkey.SLOW_MOTION to PadButton.L2,
            RaHotkey.FAST_FORWARD to PadButton.R2,
        )
        assertEquals(expected, RaHotkey.entries.associateWith { it.suggested })
        assertEquals(RaHotkey.entries.size, RaHotkey.entries.map { it.suggested }.toSet().size)
    }

    @Test
    fun `a Thor Tools hotkey on the same two buttons takes a RetroArch hotkey's press`() {
        val selectR1 = Hotkey(PadButton.SELECT, PadButton.R1, PressKind.TAP, ThorAction.TOGGLE_LAYOUT)
        val homeR1 = Hotkey(PadButton.HOME, PadButton.R1, PressKind.TAP, ThorAction.TOGGLE_LAYOUT)
        val binds = mapOf(RaHotkey.SAVE_STATE to PadButton.R1, RaHotkey.LOAD_STATE to PadButton.L1)
        assertEquals(listOf(RaHotkey.SAVE_STATE to selectR1), RetroArchBinds.clashes(PadButton.SELECT, binds, listOf(selectR1, homeR1)))
        assertTrue(RetroArchBinds.clashes(null, binds, listOf(selectR1)).isEmpty())
        assertTrue(RetroArchBinds.clashes(PadButton.START, binds, listOf(selectR1)).isEmpty())
    }
}
