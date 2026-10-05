package io.github.thatonecodingperson.thortools.leds

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LedPlanTest {
    @Test
    fun `a look survives being stored, and broken text falls back to the defaults`() {
        val look = LedLook(LedMode.RAINBOW, 0x00ff00, 0x0000ff, 0.25f, 0.75f, chargingBreathe = true)
        assertEquals(look, LedLook.decode(look.encode()))
        assertEquals(LedLook(), LedLook.decode("mode=disco;left=zz;bright=x"))
        assertEquals(LedLook(), LedLook.decode(null))
    }

    @Test
    fun `the node line is zone 1 with the colour scaled by the brightness, as the driver has none of its own`() {
        assertEquals("1-255:23:52", LedPlan.nodeLine(0xFF1734, 255))
        assertEquals("1-153:13:31", LedPlan.nodeLine(0xFF1734, 153))
        assertEquals("1-0:0:0", LedPlan.nodeLine(0xFF1734, 0))
        assertEquals("1-255:255:255", LedPlan.nodeLine(0xFFFFFF, 300))
    }

    @Test
    fun `without the helper an animated look shows its colours steadily`() {
        val breathe = LedLook(LedMode.BREATHE, 0x112233, 0x445566, brightness = 1f)
        assertEquals(LedFrame(0x112233, 0x445566, 255), LedPlan.still(breathe, 50, charging = false))
        assertEquals(LedFrame(0x112233, 0x445566, 255), LedPlan.still(breathe.copy(mode = LedMode.PULSE), 50, charging = false))
        assertEquals(LedFrame(0xFF0000, 0x00FFFF, 255), LedPlan.still(LedLook(LedMode.RAINBOW, brightness = 1f), 50, charging = false))
        val charging = breathe.copy(chargingBreathe = true)
        assertEquals(LedFrame(LedPlan.GREEN, LedPlan.GREEN, 255), LedPlan.still(charging, 50, charging = true))
        assertNull(LedPlan.still(LedLook(LedMode.AYN), 50, charging = false))
    }

    @Test
    fun `AYN's colour setting reads and writes both sticks`() {
        assertEquals("#ffff1734,#ff0000ff", LedPlan.aynColours(0xFF1734, 0x0000FF))
        assertEquals(0xFF1734 to 0x0000FF, LedPlan.parseAynColours("#ffff1734,#ff0000ff"))
        assertNull(LedPlan.parseAynColours("#ffff1734"))
        assertNull(LedPlan.parseAynColours(null))
    }

    @Test
    fun `plain colours, off and AYN's own`() {
        val colour = LedLook(LedMode.COLOUR, 0x112233, 0x445566, brightness = 1f)
        assertEquals(LedFrame(0x112233, 0x445566, 255), LedPlan.frame(colour, 0, 50, charging = false))
        assertEquals(LedFrame(0, 0, 0), LedPlan.frame(LedLook(LedMode.OFF), 0, 50, charging = false))
        assertNull(LedPlan.frame(LedLook(LedMode.AYN), 0, 50, charging = false))
        assertFalse(LedPlan.drawnByThorTools(colour, charging = false))
        assertTrue(LedPlan.drawnByThorTools(LedLook(LedMode.RAINBOW), charging = false))
    }

    @Test
    fun `breathing rises and falls but never goes dark`() {
        val look = LedLook(LedMode.BREATHE, brightness = 1f, speed = 0f)
        val period = LedPlan.periodMs(0f)
        val frames = (0 until 20).map { LedPlan.frame(look, period * it / 20, 50, charging = false)!!.brightness }
        assertTrue(frames.min() > 0)
        assertTrue(frames.max() >= 252)
        assertTrue(LedPlan.animated(look, charging = false, battery = 50))
    }

    @Test
    fun `the rainbow keeps the sticks on opposite colours`() {
        val look = LedLook(LedMode.RAINBOW, speed = 1f)
        val frame = LedPlan.frame(look, 0, 50, charging = false)!!
        assertEquals(0xFF0000, frame.left)
        assertEquals(0x00FFFF, frame.right)
    }

    @Test
    fun `battery colours go from red through amber to green`() {
        assertEquals(0xFF0000, LedPlan.batteryColour(5))
        assertEquals(0xFF0000, LedPlan.batteryColour(20))
        assertEquals(0xFFA000, LedPlan.batteryColour(50))
        assertEquals(LedPlan.GREEN, LedPlan.batteryColour(100))
        assertEquals(LedPlan.GREEN, LedPlan.batteryColour(130))
    }

    @Test
    fun `while charging the lights breathe green, and stay green once full`() {
        val look = LedLook(LedMode.COLOUR, 0x123456, 0x123456, brightness = 1f, chargingBreathe = true)
        assertEquals(LedPlan.GREEN, LedPlan.frame(look, 0, 60, charging = true)!!.left)
        assertTrue(LedPlan.animated(look, charging = true, battery = 60))
        assertEquals(LedFrame(LedPlan.GREEN, LedPlan.GREEN, 255), LedPlan.frame(look, 0, 100, charging = true))
        assertFalse(LedPlan.animated(look, charging = true, battery = 100))
        assertTrue(LedPlan.drawnByThorTools(look, charging = true))
        // AYN's own look is never drawn over, not even while charging.
        assertNull(LedPlan.frame(LedLook(LedMode.AYN, chargingBreathe = true), 0, 60, charging = true))
    }

    @Test
    fun `speed sets how long a cycle takes`() {
        assertEquals(6000L, LedPlan.periodMs(0f))
        assertEquals(1200L, LedPlan.periodMs(1f))
    }

    @Test
    fun `AYN's three settings make a look, a stick switched off stays dark`() {
        assertEquals(LedFrame(0xFF1734, 0xFF1734, 128), LedPlan.aynFrame("1,1", "#ffff1734,#ffff1734", "0.5"))
        assertEquals(LedFrame(0xFF1734, 0, 0), LedPlan.aynFrame("1,0", "#ffff1734,#ffff1734", "0.0"))
        assertNull(LedPlan.aynFrame("1,1", "broken", "0.5"))
    }
}
