package io.github.thatonecodingperson.thortools.hotkeys

import io.github.thatonecodingperson.thortools.actions.ThorAction
import io.github.thatonecodingperson.thortools.hotkeys.HotkeyRecognizer.Effect.Run
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** A held button whose release never comes (AYN re-created its pad), and a new hotkey list mid-press. */
class HeldButtonsTest {
    private val screenshot = Hotkey(PadButton.HOME, PadButton.X, PressKind.TAP, ThorAction.SCREENSHOT)
    private val toTop = Hotkey(PadButton.HOME, PadButton.RSTICK_UP, PressKind.TAP, ThorAction.CONTROLLER_TO_TOP)
    private val homeBoth = Hotkey(PadButton.HOME, null, PressKind.DOUBLE, ThorAction.HOME_BOTH)

    private fun HotkeyRecognizer.down(button: PadButton, time: Long) = onKey(button, down = true, repeat = false, time = time)

    private fun HotkeyRecognizer.up(button: PadButton, time: Long) = onKey(button, down = false, repeat = false, time = time)

    @Test
    fun `the first stick flick while Home is held runs a tap combo at once`() {
        val keys = HotkeyRecognizer(listOf(toTop, homeBoth))
        keys.down(PadButton.HOME, 0)
        assertTrue(keys.wantsMotion)
        assertEquals(listOf(Run(toTop)), keys.onMotion(PadButton.RSTICK_UP, down = true, time = 60).effects)
    }

    @Test
    fun `a lost Home release no longer turns X alone into Home plus X`() {
        val keys = HotkeyRecognizer(listOf(screenshot, toTop))
        keys.down(PadButton.HOME, 0)
        keys.forgetHeld()
        assertFalse(keys.holding)
        assertFalse(keys.wantsMotion)
        val x = keys.down(PadButton.X, 2000)
        assertFalse(x.consume)
        assertEquals(emptyList<HotkeyRecognizer.Effect>(), x.effects)
        assertEquals(emptyList<HotkeyRecognizer.Effect>(), keys.onMotion(PadButton.RSTICK_UP, down = true, time = 2100).effects)
    }

    @Test
    fun `a forgotten Home's late release is still swallowed and does nothing`() {
        val keys = HotkeyRecognizer(listOf(screenshot, homeBoth))
        keys.down(PadButton.HOME, 0)
        keys.forgetHeld()
        val release = keys.up(PadButton.HOME, 500)
        assertTrue(release.consume)
        assertEquals(emptyList<HotkeyRecognizer.Effect>(), release.effects)
        assertEquals(emptyList<HotkeyRecognizer.Effect>(), keys.onTimer(900).effects)
    }

    @Test
    fun `Home pressed again after a forgotten press works as usual`() {
        val keys = HotkeyRecognizer(listOf(screenshot))
        keys.down(PadButton.HOME, 0)
        keys.forgetHeld()
        assertTrue(keys.down(PadButton.HOME, 3000).consume)
        assertEquals(listOf(Run(screenshot)), keys.down(PadButton.X, 3100).effects)
    }

    @Test
    fun `nothing held means nothing to forget`() {
        val keys = HotkeyRecognizer(listOf(screenshot))
        keys.forgetHeld()
        assertEquals(emptySet<PadButton>(), keys.releasesToSwallow())
    }

    @Test
    fun `a new list takes over the release of a held Home`() {
        val old = HotkeyRecognizer(listOf(screenshot))
        old.down(PadButton.HOME, 0)
        assertEquals(setOf(PadButton.HOME), old.releasesToSwallow())
        val new = HotkeyRecognizer(listOf(homeBoth)).also { it.swallowReleases(old.releasesToSwallow()) }
        assertTrue(new.up(PadButton.HOME, 400).consume)
    }

    @Test
    fun `a game button held as a combo's first button passes its release`() {
        val keys = HotkeyRecognizer(listOf(Hotkey(PadButton.SELECT, PadButton.R1, PressKind.TAP, ThorAction.SCREENSHOT)))
        keys.down(PadButton.SELECT, 0)
        assertEquals(emptySet<PadButton>(), keys.releasesToSwallow())
        keys.forgetHeld()
        assertFalse(keys.up(PadButton.SELECT, 300).consume)
    }
}
