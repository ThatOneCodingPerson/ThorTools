package io.github.thatonecodingperson.thortools.hotkeys

import io.github.thatonecodingperson.thortools.actions.ThorAction
import io.github.thatonecodingperson.thortools.hotkeys.HotkeyRecognizer.Effect.GiveBack
import io.github.thatonecodingperson.thortools.hotkeys.HotkeyRecognizer.Effect.Run
import io.github.thatonecodingperson.thortools.hotkeys.HotkeyRecognizer.Step
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HotkeyRecognizerTest {
    private val panel = HotkeyList.defaults[0]
    private val drawer = HotkeyList.defaults[1]

    private fun hotkey(button: PadButton, press: PressKind, second: PadButton? = null, action: ThorAction = ThorAction.SCREENSHOT) =
        Hotkey(button, second, press, action)

    private fun HotkeyRecognizer.down(button: PadButton, time: Long) = onKey(button, down = true, repeat = false, time = time)

    private fun HotkeyRecognizer.up(button: PadButton, time: Long, canceled: Boolean = false) =
        onKey(button, down = false, repeat = false, time = time, canceled = canceled)

    @Test
    fun `AYN tap opens the panel on release`() {
        val keys = HotkeyRecognizer(HotkeyList.defaults)
        assertEquals(Step(consume = true, timerAt = 600), keys.down(PadButton.AYN, 0))
        assertEquals(Step(consume = true, timerAt = 600), keys.onKey(PadButton.AYN, down = true, repeat = true, time = 400))
        assertEquals(Step(consume = true, effects = listOf(Run(panel))), keys.up(PadButton.AYN, 450))
        assertEquals(Step(consume = false), keys.onTimer(600))
    }

    @Test
    fun `AYN hold opens the drawer and the release does nothing`() {
        val keys = HotkeyRecognizer(HotkeyList.defaults)
        keys.down(PadButton.AYN, 0)
        assertEquals(Step(consume = false, timerAt = 600), keys.onTimer(300))
        assertEquals(Step(consume = false, effects = listOf(Run(drawer))), keys.onTimer(600))
        assertEquals(Step(consume = true), keys.up(PadButton.AYN, 900))
    }

    @Test
    fun `a canceled release does nothing`() {
        val keys = HotkeyRecognizer(HotkeyList.defaults)
        keys.down(PadButton.AYN, 0)
        assertEquals(Step(consume = true), keys.up(PadButton.AYN, 100, canceled = true))
    }

    @Test
    fun `a press whose release went missing starts over`() {
        val keys = HotkeyRecognizer(HotkeyList.defaults)
        keys.down(PadButton.AYN, 0)
        assertEquals(Step(consume = true, timerAt = 5600), keys.down(PadButton.AYN, 5000))
        assertEquals(Step(consume = true, effects = listOf(Run(panel))), keys.up(PadButton.AYN, 5100))
    }

    @Test
    fun `a button nothing uses is never touched`() {
        val keys = HotkeyRecognizer(HotkeyList.defaults)
        assertEquals(Step(consume = false), keys.down(PadButton.BACK, 0))
        assertEquals(Step(consume = false), keys.onKey(PadButton.BACK, down = true, repeat = true, time = 500))
        assertEquals(Step(consume = false), keys.up(PadButton.BACK, 600))
    }

    @Test
    fun `a single tap of a double tap button is given back after the gap`() {
        val keys = HotkeyRecognizer(listOf(hotkey(PadButton.HOME, PressKind.DOUBLE)))
        assertEquals(Step(consume = true), keys.down(PadButton.HOME, 0))
        assertEquals(Step(consume = true, timerAt = 400), keys.up(PadButton.HOME, 100))
        assertEquals(Step(consume = false, effects = listOf(GiveBack(PadButton.HOME, 1))), keys.onTimer(400))
    }

    @Test
    fun `a double tap runs as soon as the highest count is reached`() {
        val double = hotkey(PadButton.HOME, PressKind.DOUBLE)
        val keys = HotkeyRecognizer(listOf(double))
        keys.down(PadButton.HOME, 0)
        keys.up(PadButton.HOME, 100)
        assertEquals(Step(consume = true), keys.down(PadButton.HOME, 250))
        assertEquals(Step(consume = true, effects = listOf(Run(double))), keys.up(PadButton.HOME, 300))
    }

    @Test
    fun `taps nobody bound are all given back`() {
        val keys = HotkeyRecognizer(listOf(hotkey(PadButton.BACK, PressKind.TRIPLE)))
        keys.down(PadButton.BACK, 0)
        keys.up(PadButton.BACK, 50)
        keys.down(PadButton.BACK, 150)
        assertEquals(Step(consume = true, timerAt = 500), keys.up(PadButton.BACK, 200))
        assertEquals(Step(consume = false, effects = listOf(GiveBack(PadButton.BACK, 2))), keys.onTimer(500))
    }

    @Test
    fun `the next button resolves pending taps first`() {
        val keys = HotkeyRecognizer(listOf(hotkey(PadButton.HOME, PressKind.DOUBLE)))
        keys.down(PadButton.HOME, 0)
        keys.up(PadButton.HOME, 100)
        assertEquals(Step(consume = false, effects = listOf(GiveBack(PadButton.HOME, 1))), keys.down(PadButton.A, 200))
    }

    @Test
    fun `a combo with only a tap runs on the press and swallows both buttons`() {
        val combo = hotkey(PadButton.HOME, PressKind.TAP, second = PadButton.A)
        val keys = HotkeyRecognizer(listOf(combo))
        assertEquals(Step(consume = true), keys.down(PadButton.HOME, 0))
        assertEquals(Step(consume = true, effects = listOf(Run(combo))), keys.down(PadButton.A, 100))
        assertEquals(Step(consume = true), keys.up(PadButton.A, 150))
        assertEquals(Step(consume = true), keys.up(PadButton.HOME, 200))
    }

    @Test
    fun `a combo double tap counts the second button`() {
        val tap = hotkey(PadButton.BACK, PressKind.TAP, second = PadButton.R1)
        val double = hotkey(PadButton.BACK, PressKind.DOUBLE, second = PadButton.R1, action = ThorAction.SWAP_SCREENS)
        val keys = HotkeyRecognizer(listOf(tap, double))
        keys.down(PadButton.BACK, 0)
        keys.down(PadButton.R1, 100)
        assertEquals(Step(consume = true, timerAt = 450), keys.up(PadButton.R1, 150))
        keys.down(PadButton.R1, 250)
        assertEquals(Step(consume = true, effects = listOf(Run(double))), keys.up(PadButton.R1, 300))
        assertEquals(Step(consume = true), keys.up(PadButton.BACK, 400))
    }

    @Test
    fun `a combo tap waiting for more runs when the first button is let go`() {
        val tap = hotkey(PadButton.BACK, PressKind.TAP, second = PadButton.R1)
        val double = hotkey(PadButton.BACK, PressKind.DOUBLE, second = PadButton.R1, action = ThorAction.SWAP_SCREENS)
        val keys = HotkeyRecognizer(listOf(tap, double))
        keys.down(PadButton.BACK, 0)
        keys.down(PadButton.R1, 100)
        keys.up(PadButton.R1, 150)
        assertEquals(Step(consume = true, effects = listOf(Run(tap))), keys.up(PadButton.BACK, 200))
    }

    @Test
    fun `a combo hold runs while both are held`() {
        val hold = hotkey(PadButton.HOME, PressKind.HOLD, second = PadButton.X)
        val keys = HotkeyRecognizer(listOf(hold))
        keys.down(PadButton.HOME, 0)
        assertEquals(Step(consume = true, timerAt = 1100), keys.down(PadButton.X, 100))
        assertEquals(Step(consume = false, effects = listOf(Run(hold))), keys.onTimer(1100))
        assertEquals(Step(consume = true), keys.up(PadButton.X, 1300))
        assertEquals(Step(consume = true), keys.up(PadButton.HOME, 1400))
    }

    @Test
    fun `a game button that only starts combos still reaches the game`() {
        val combo = hotkey(PadButton.SELECT, PressKind.TAP, second = PadButton.R1)
        val keys = HotkeyRecognizer(listOf(combo))
        assertEquals(Step(consume = false), keys.down(PadButton.SELECT, 0))
        assertEquals(Step(consume = true, effects = listOf(Run(combo))), keys.down(PadButton.R1, 100))
        assertEquals(Step(consume = true), keys.up(PadButton.R1, 150))
        assertEquals(Step(consume = false), keys.up(PadButton.SELECT, 200))
    }

    @Test
    fun `another button used while holding means letting go is not a press`() {
        val keys = HotkeyRecognizer(listOf(hotkey(PadButton.HOME, PressKind.TAP)))
        keys.down(PadButton.HOME, 0)
        assertEquals(Step(consume = false), keys.down(PadButton.A, 100))
        assertEquals(Step(consume = false), keys.up(PadButton.A, 150))
        assertEquals(Step(consume = true), keys.up(PadButton.HOME, 200))
    }

    @Test
    fun `Home and Back together are never a hotkey`() {
        val keys = HotkeyRecognizer(listOf(hotkey(PadButton.HOME, PressKind.TAP), hotkey(PadButton.BACK, PressKind.TAP)))
        keys.down(PadButton.HOME, 0)
        assertEquals(Step(consume = true), keys.down(PadButton.BACK, 50))
        assertEquals(Step(consume = true), keys.up(PadButton.BACK, 100))
        assertEquals(Step(consume = true), keys.up(PadButton.HOME, 150))
    }

    @Test
    fun `Back during a game's own combo goes to the game`() {
        val keys = HotkeyRecognizer(listOf(hotkey(PadButton.BACK, PressKind.DOUBLE)))
        assertEquals(Step(consume = false), keys.down(PadButton.SELECT, 0))
        assertEquals(Step(consume = false), keys.down(PadButton.BACK, 100))
        assertEquals(Step(consume = false), keys.up(PadButton.BACK, 150))
        assertEquals(Step(consume = false), keys.up(PadButton.SELECT, 200))
    }

    @Test
    fun `a game's button whose release was lost with its pad no longer gives Back to the game`() {
        val keys = HotkeyRecognizer(listOf(hotkey(PadButton.BACK, PressKind.TAP)))
        keys.down(PadButton.SELECT, 0)
        keys.forgetHeld()
        assertTrue(keys.down(PadButton.BACK, 100).consume)
    }

    @Test
    fun `holding a combo button and letting go without a combo does nothing`() {
        val keys = HotkeyRecognizer(listOf(hotkey(PadButton.HOME, PressKind.TAP, second = PadButton.A)))
        keys.down(PadButton.HOME, 0)
        assertEquals(Step(consume = true), keys.up(PadButton.HOME, 1200))
        keys.down(PadButton.HOME, 2000)
        assertEquals(Step(consume = true, effects = listOf(GiveBack(PadButton.HOME, 1))), keys.up(PadButton.HOME, 2100))
    }

    @Test
    fun `it knows while a button that starts combos is held`() {
        val keys = HotkeyRecognizer(listOf(hotkey(PadButton.HOME, PressKind.TAP, second = PadButton.A)))
        assertEquals(false, keys.holding)
        keys.down(PadButton.A, 0)
        assertEquals(false, keys.holding)
        keys.up(PadButton.A, 50)
        keys.down(PadButton.HOME, 100)
        assertEquals(true, keys.holding)
        keys.up(PadButton.HOME, 200)
        assertEquals(false, keys.holding)
    }

    @Test
    fun `a stick flick while Home is held runs its combo`() {
        val combo = hotkey(PadButton.HOME, PressKind.TAP, second = PadButton.RSTICK_UP)
        val keys = HotkeyRecognizer(listOf(combo))
        keys.down(PadButton.HOME, 0)
        assertEquals(true, keys.wantsMotion)
        assertEquals(Step(consume = false, effects = listOf(Run(combo))), keys.onMotion(PadButton.RSTICK_UP, down = true, time = 100))
        assertEquals(Step(consume = false), keys.onMotion(PadButton.RSTICK_UP, down = false, time = 150))
        assertEquals(Step(consume = true), keys.up(PadButton.HOME, 200))
        assertEquals(false, keys.wantsMotion)
    }

    @Test
    fun `a double flick counts like double taps`() {
        val double = hotkey(PadButton.BACK, PressKind.DOUBLE, second = PadButton.LSTICK_UP)
        val keys = HotkeyRecognizer(listOf(double, hotkey(PadButton.BACK, PressKind.TAP, second = PadButton.LSTICK_UP)))
        keys.down(PadButton.BACK, 0)
        keys.onMotion(PadButton.LSTICK_UP, down = true, time = 100)
        assertEquals(Step(consume = false, timerAt = 450), keys.onMotion(PadButton.LSTICK_UP, down = false, time = 150))
        keys.onMotion(PadButton.LSTICK_UP, down = true, time = 250)
        assertEquals(Step(consume = false, effects = listOf(Run(double))), keys.onMotion(PadButton.LSTICK_UP, down = false, time = 300))
    }

    @Test
    fun `a direction without a combo leaves the held button's own press alone`() {
        val tap = hotkey(PadButton.HOME, PressKind.TAP)
        val keys = HotkeyRecognizer(listOf(tap, hotkey(PadButton.HOME, PressKind.TAP, second = PadButton.DPAD_UP)))
        keys.down(PadButton.HOME, 0)
        assertEquals(Step(consume = false), keys.onMotion(PadButton.DPAD_LEFT, down = true, time = 50))
        keys.onMotion(PadButton.DPAD_LEFT, down = false, time = 80)
        assertEquals(Step(consume = true, effects = listOf(Run(tap))), keys.up(PadButton.HOME, 120))
    }

    @Test
    fun `directions mean nothing when no button is held`() {
        val keys = HotkeyRecognizer(
            listOf(hotkey(PadButton.HOME, PressKind.DOUBLE), hotkey(PadButton.HOME, PressKind.TAP, second = PadButton.DPAD_UP)),
        )
        keys.down(PadButton.HOME, 0)
        keys.up(PadButton.HOME, 50)
        assertEquals(Step(consume = false, timerAt = 350), keys.onMotion(PadButton.DPAD_UP, down = true, time = 100))
        assertEquals(false, keys.wantsMotion)
    }

    @Test
    fun `only buttons with direction combos want the joystick`() {
        val keys = HotkeyRecognizer(listOf(hotkey(PadButton.HOME, PressKind.TAP, second = PadButton.A)))
        keys.down(PadButton.HOME, 0)
        assertEquals(false, keys.wantsMotion)
    }

    @Test
    fun `AYN waits longer before its hold when it starts combos`() {
        val keys = HotkeyRecognizer(HotkeyList.defaults + hotkey(PadButton.AYN, PressKind.TAP, second = PadButton.B))
        assertEquals(Step(consume = true, timerAt = 1000), keys.down(PadButton.AYN, 0))
    }

    @Test
    fun `it is busy while a press is still being counted, taps waiting for the next one too`() {
        val keys = HotkeyRecognizer(listOf(hotkey(PadButton.BACK, PressKind.DOUBLE)))
        assertFalse(keys.busy)
        keys.down(PadButton.BACK, 0)
        assertTrue(keys.busy)
        keys.up(PadButton.BACK, 100)
        assertFalse(keys.holding)
        assertTrue(keys.busy)
        assertEquals(listOf(GiveBack(PadButton.BACK, 1)), keys.onTimer(10_000).effects)
        assertFalse(keys.busy)
    }

    @Test
    fun `combo first buttons are known, and holding one shows while it is down`() {
        val homeR3 = hotkey(PadButton.HOME, PressKind.DOUBLE, second = PadButton.R3, action = ThorAction.SWAP_SCREENS)
        val keys = HotkeyRecognizer(listOf(homeR3, hotkey(PadButton.L1, PressKind.TAP, second = PadButton.R1)))
        assertTrue(keys.startsCombos(PadButton.HOME))
        assertTrue(keys.startsCombos(PadButton.L1))
        assertFalse(keys.startsCombos(PadButton.R3))
        assertFalse(keys.startsCombos(PadButton.A))
        assertFalse(keys.holding)
        keys.down(PadButton.HOME, 0)
        assertTrue(keys.holding)
        // A combo's second button is the hotkeys' alone: swallowed, so desktop controls never get it.
        val steps =
            listOf(keys.down(PadButton.R3, 100), keys.up(PadButton.R3, 150), keys.down(PadButton.R3, 250), keys.up(PadButton.R3, 300))
        assertTrue(steps.all { it.consume })
        assertEquals(listOf(Run(homeR3)), steps.flatMap { it.effects })
        keys.up(PadButton.HOME, 400)
        assertFalse(keys.holding)
        // A game button that starts a combo goes on to the app (and so to desktop controls), held all the same.
        assertFalse(keys.down(PadButton.L1, 500).consume)
        assertTrue(keys.holding)
    }
}
