package io.github.thatonecodingperson.thortools.hotkeys

import io.github.thatonecodingperson.thortools.actions.ThorAction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SuggestionProfileTest {
    private fun triggers(list: List<Hotkey>) = list.map { Triple(it.button, it.second, it.press) }

    @Test
    fun `every profile has unique triggers and only allowed hotkeys`() {
        SuggestionProfile.entries.forEach { profile ->
            profile.hotkeys.forEach { assertTrue("$profile $it", Hotkey.allowed(it.button, it.second)) }
            assertEquals(profile.toString(), profile.hotkeys.size, triggers(profile.hotkeys).toSet().size)
        }
    }

    @Test
    fun `profile ids never change`() {
        assertEquals(
            listOf("thor", "home_only", "essentials", "two_apps", "wayfinder_like"),
            SuggestionProfile.entries.map { it.id },
        )
    }

    @Test
    fun `the Thor Profile is its fixed list`() {
        val list = setOf(
            Hotkey(PadButton.HOME, PadButton.R3, PressKind.TAP, ThorAction.SWAP_SCREENS),
            Hotkey(PadButton.BACK, null, PressKind.DOUBLE, ThorAction.RECENTS),
            Hotkey(PadButton.HOME, null, PressKind.TRIPLE, ThorAction.CLEAR_BACKGROUND),
            Hotkey(PadButton.HOME, null, PressKind.DOUBLE, ThorAction.HOME_BOTH),
            Hotkey(PadButton.BACK, PadButton.LSTICK_UP, PressKind.TAP, ThorAction.BRIGHTER),
            Hotkey(PadButton.BACK, PadButton.LSTICK_DOWN, PressKind.TAP, ThorAction.DIMMER),
            Hotkey(PadButton.HOME, PadButton.RSTICK_UP, PressKind.TAP, ThorAction.CONTROLLER_TO_TOP),
            Hotkey(PadButton.HOME, PadButton.RSTICK_DOWN, PressKind.TAP, ThorAction.CONTROLLER_TO_BOTTOM),
            Hotkey(PadButton.HOME, PadButton.X, PressKind.TAP, ThorAction.SCREENSHOT),
            Hotkey(PadButton.HOME, PadButton.START, PressKind.TAP, ThorAction.TOGGLE_CONTROLLER_LOCK),
            Hotkey(PadButton.BACK, PadButton.L3, PressKind.TAP, ThorAction.CYCLE_PERFORMANCE),
        )
        assertEquals(list + HotkeyList.aynPanel, SuggestionProfile.THOR.hotkeys.toSet())
    }

    @Test
    fun `Wayfinder-Like keeps the earlier suggestions in another order, with the credit`() {
        val earlier = listOf(
            Triple(PadButton.BACK, null, PressKind.HOLD),
            Triple(PadButton.BACK, null, PressKind.DOUBLE),
            Triple(PadButton.BACK, null, PressKind.TRIPLE),
            Triple(PadButton.HOME, null, PressKind.DOUBLE),
            Triple(PadButton.HOME, PadButton.R2, PressKind.TAP),
            Triple(PadButton.HOME, PadButton.L2, PressKind.TAP),
            Triple(PadButton.HOME, PadButton.R1, PressKind.TAP),
            Triple(PadButton.HOME, PadButton.L1, PressKind.TAP),
            Triple(PadButton.HOME, PadButton.Y, PressKind.TAP),
            Triple(PadButton.HOME, PadButton.B, PressKind.HOLD),
            Triple(PadButton.HOME, PadButton.SELECT, PressKind.TAP),
            Triple(PadButton.HOME, PadButton.START, PressKind.TAP),
        )
        val now = triggers(SuggestionProfile.WAYFINDER_LIKE.hotkeys.drop(1))
        assertEquals(earlier.toSet(), now.toSet())
        assertNotEquals(earlier, now)
        assertTrue(SuggestionProfile.WAYFINDER_LIKE.credit)
        assertEquals(listOf(SuggestionProfile.WAYFINDER_LIKE), SuggestionProfile.entries.filter { it.credit })
    }

    @Test
    fun `every profile starts with the AYN button opening the Thor Tools quick panel`() {
        SuggestionProfile.entries.forEach { assertEquals(it.toString(), HotkeyList.aynPanel, it.hotkeys.first()) }
    }

    @Test
    fun `adding a profile's free ones never replaces a hotkey and adds only that profile`() {
        val mine = Hotkey(PadButton.HOME, null, PressKind.DOUBLE, ThorAction.SCREENSHOT)
        val result = HotkeyList.addFreeSuggestions(listOf(mine), SuggestionProfile.ESSENTIALS.hotkeys)
        assertEquals(mine, result.first())
        assertEquals(1 + SuggestionProfile.ESSENTIALS.hotkeys.size - 1, result.size)
        assertTrue(result.drop(1).all { it in SuggestionProfile.ESSENTIALS.hotkeys })
    }

    @Test
    fun `Close the current app suggestions act on the screen with the controller`() {
        SuggestionProfile.entries.flatMap { it.hotkeys }.filter { it.action == ThorAction.CLOSE_APP }.forEach {
            assertEquals(LaunchScreen.HERE, CloseAppArg.decode(it.arg))
        }
    }
}
