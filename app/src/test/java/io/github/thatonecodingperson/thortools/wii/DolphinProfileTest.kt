package io.github.thatonecodingperson.thortools.wii

import io.github.thatonecodingperson.thortools.hotkeys.PadButton
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DolphinProfileTest {
    private val device = DolphinProfile.device(xboxStyle = true)

    private fun lines(mapping: WiiMapping) = DolphinProfile.ini(mapping, device).lines()

    private fun value(mapping: WiiMapping, key: String) = lines(mapping).first { it.startsWith("$key = ") }.substringAfter(" = ")

    @Test
    fun `a profile starts with the section and the pad, and ends with the extension`() {
        val lines = lines(WiiMapping(WiiSetup.NUNCHUK))
        assertEquals("[Profile]", lines[0])
        assertEquals("Device = Android/1/Xbox Wireless Controller", lines[1])
        assertTrue("Extension = Nunchuk" in lines)
        assertTrue("Extension = Classic" in lines(WiiMapping(WiiSetup.CLASSIC)))
        assertTrue("Extension = None" in lines(WiiMapping(WiiSetup.POINTING)))
    }

    @Test
    fun `a face button names both pads, the Xbox style by position`() {
        assertEquals(
            "`Android/1/Xbox Wireless Controller:Button B` | `Android/1/Odin Controller:Button A`",
            value(WiiMapping(WiiSetup.NUNCHUK), "Buttons/A"),
        )
        assertEquals(
            "`Android/1/Xbox Wireless Controller:Button Y` | `Android/1/Odin Controller:Button X`",
            value(WiiMapping(WiiSetup.NUNCHUK), "Buttons/1"),
        )
    }

    @Test
    fun `the triggers work in both of AYN's trigger modes`() {
        assertEquals(
            "`Android/1/Xbox Wireless Controller:Button R2` | `Android/1/Xbox Wireless Controller:Axis 18+` | " +
                "`Android/1/Xbox Wireless Controller:Axis 22+` | `Android/1/Odin Controller:Button R2` | " +
                "`Android/1/Odin Controller:Axis 18+` | `Android/1/Odin Controller:Axis 22+`",
            value(WiiMapping(WiiSetup.NUNCHUK), "Shake/X"),
        )
    }

    @Test
    fun `a hotkey is Dolphin's own, held then pressed, for every code in both styles, and a broken one isn't written`() {
        val xbox = "Android/1/Xbox Wireless Controller"
        val odin = "Android/1/Odin Controller"
        assertEquals(
            "@(`$xbox:Select`+`$xbox:Button L2`) | @(`$xbox:Select`+`$xbox:Axis 17+`) | @(`$xbox:Select`+`$xbox:Axis 23+`) | " +
                "@(`$odin:Select`+`$odin:Button L2`) | @(`$odin:Select`+`$odin:Axis 17+`) | @(`$odin:Select`+`$odin:Axis 23+`)",
            value(WiiMapping(WiiSetup.NUNCHUK), "Hotkeys/Sideways Toggle"),
        )
        assertTrue(value(WiiMapping(WiiSetup.NUNCHUK), "Hotkeys/Upright Toggle").contains("@(`$xbox:Select`+`$xbox:Axis 22+`)"))
        val broken = WiiMapping(WiiSetup.NUNCHUK).withCombo(WiiControl.UPRIGHT_TOGGLE, PadButton.R2, null)
        assertFalse(lines(broken).any { it.startsWith("Hotkeys/Upright Toggle") })
        assertFalse(lines(WiiMapping(WiiSetup.CLASSIC)).any { it.startsWith("Hotkeys/") })
    }

    @Test
    fun `a stick writes its four directions, each source joined`() {
        val mapping = WiiMapping(WiiSetup.SIDEWAYS)
        assertEquals(
            "`Android/1/Xbox Wireless Controller:Axis 16-` | `Android/1/Xbox Wireless Controller:Axis 1-` | " +
                "`Android/1/Odin Controller:Axis 16-` | `Android/1/Odin Controller:Axis 1-`",
            value(mapping, "D-Pad/Up"),
        )
        assertTrue(value(mapping, "D-Pad/Right").contains("Axis 15+"))
        assertTrue(value(mapping, "Tilt/Forward").contains("Axis 14-"))
        assertTrue(value(mapping, "Tilt/Left").contains("Axis 11-"))
        assertTrue(value(WiiMapping(WiiSetup.NUNCHUK), "Nunchuk/Stick/Down").contains("Axis 1+"))
    }

    @Test
    fun `a shake moves on all three axes`() {
        val mapping = WiiMapping(WiiSetup.POINTING)
        val shake = value(mapping, "Shake/X")
        assertEquals(shake, value(mapping, "Shake/Y"))
        assertEquals(shake, value(mapping, "Shake/Z"))
    }

    @Test
    fun `options follow the tab and the switches`() {
        assertTrue("Options/Sideways Wiimote = True" in lines(WiiMapping(WiiSetup.SIDEWAYS)))
        assertFalse(lines(WiiMapping(WiiSetup.NUNCHUK)).any { it.startsWith("Options/Sideways") })
        assertTrue("IR/Relative Input = True" in lines(WiiMapping(WiiSetup.POINTING)))
        assertFalse(lines(WiiMapping(WiiSetup.POINTING, relativePointer = false)).any { it.startsWith("IR/Relative") })
        assertTrue("Rumble/Motor = `Android/0/Device Sensors:Motor 0`" in lines(WiiMapping(WiiSetup.CLASSIC)))
        assertFalse(lines(WiiMapping(WiiSetup.CLASSIC, rumble = false)).any { it.startsWith("Rumble") })
        assertFalse(DolphinProfile.ini(WiiMapping(WiiSetup.CLASSIC), device, vibrates = false).contains("Rumble"))
    }

    @Test
    fun `a Classic trigger is pressed and fully pulled at once, a control left out isn't written`() {
        val mapping = WiiMapping(WiiSetup.CLASSIC).withButton(WiiControl.CLASSIC_HOME, null)
        assertEquals(value(mapping, "Classic/Triggers/L"), value(mapping, "Classic/Triggers/L-Analog"))
        assertFalse(lines(mapping).any { it.startsWith("Classic/Buttons/Home") })
        assertTrue(value(mapping, "Classic/Buttons/A").contains("Odin Controller:Button A"))
    }

    @Test
    fun `every Thor button the builder offers has a code in both styles`() {
        WII_BUTTONS.forEach { button ->
            assertTrue(button.name, DolphinProfile.codes(button, xboxStyle = true).isNotEmpty())
            assertTrue(button.name, DolphinProfile.codes(button, xboxStyle = false).isNotEmpty())
        }
        assertTrue(DolphinProfile.codes(PadButton.HOME, xboxStyle = true).isEmpty())
    }

    @Test
    fun `the pad's device is read from Dolphin's first Wii Remote`() {
        val ini = "[Wiimote1]\nSource = 1\nDevice = Android/2/Odin Controller\n[Wiimote2]\nDevice = Android/5/Xbox Wireless Controller\n"
        assertEquals(DolphinProfile.Device(2, DolphinProfile.STANDARD_PAD), DolphinProfile.deviceIn(ini))
        assertNull(DolphinProfile.deviceIn("[Wiimote1]\nDevice = Android/0/Device Sensors\n"))
        assertNull(DolphinProfile.deviceIn(null))
    }

    @Test
    fun `profile names keep to safe characters`() {
        assertEquals("ThorNunchuk", DolphinProfile.fileName(" Thor/Nunchuk?", "x"))
        assertEquals("Thor Nunchuk", DolphinProfile.fileName("Thor Nunchuk", "x"))
        assertEquals("fallback", DolphinProfile.fileName("../..", "fallback"))
        assertEquals("Mario-Galaxy_2", DolphinProfile.fileName("Mario-Galaxy_2", "x"))
    }
}
