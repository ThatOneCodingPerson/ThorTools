package io.github.thatonecodingperson.thortools.desktop

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DesktopLayoutTest {
    @Test
    fun `the default is a Steam Controller's desktop layout`() {
        val layout = DesktopLayout.DEFAULT
        assertEquals(StickRole.POINTER, layout.rightStick)
        assertEquals(StickRole.SCROLL, layout.leftStick)
        assertEquals(DesktopJob.LEFT_CLICK, layout.job(DesktopControl.R2))
        assertEquals(DesktopJob.RIGHT_CLICK, layout.job(DesktopControl.L2))
        assertEquals(DesktopJob.ENTER, layout.job(DesktopControl.A))
        assertEquals(DesktopJob.ESCAPE, layout.job(DesktopControl.B))
        assertEquals(DesktopJob.KEYBOARD, layout.job(DesktopControl.X))
        assertEquals(DesktopJob.SPACE, layout.job(DesktopControl.Y))
        assertEquals(DesktopJob.TAB, layout.job(DesktopControl.SELECT))
        assertTrue(layout.holdStartSwitch)
        assertEquals(DesktopPreset.STEAM_CONTROLLER, layout.preset)
    }

    @Test
    fun `every preset comes back the same from its stored text`() {
        DesktopPreset.entries.forEach { preset ->
            assertEquals(preset.layout, DesktopLayout.decode(preset.layout.encode()))
        }
    }

    @Test
    fun `a changed layout comes back the same from its stored text`() {
        val layout = DesktopLayout.DEFAULT
            .with(DesktopControl.A, DesktopJob.PAGE_DOWN)
            .copy(
                leftStick = StickRole.NONE,
                speed = 1.75f,
                acceleration = Acceleration.STRONG,
                deadZone = 0.2f,
                invertY = true,
                scrollSpeed = 0.5f,
                naturalScroll = true,
                horizontalScroll = false,
                triggerThreshold = 0.6f,
                holdStartSwitch = false,
                precision = DesktopControl.L1,
            )
        assertEquals(layout, DesktopLayout.decode(layout.encode()))
        assertNull(layout.preset)
    }

    @Test
    fun `missing, unknown and out-of-range values fall back or are held in range`() {
        assertEquals(DesktopLayout.DEFAULT, DesktopLayout.decode(null))
        assertEquals(DesktopLayout.DEFAULT, DesktopLayout.decode("a=fly;ls=sideways;nonsense"))
        val clamped = DesktopLayout.decode("sp=40;dz=0;tt=2")
        assertEquals(DesktopLayout.MAX_SPEED, clamped.speed)
        assertEquals(DesktopLayout.MIN_DEAD_ZONE, clamped.deadZone)
        assertEquals(DesktopLayout.MAX_THRESHOLD, clamped.triggerThreshold)
        assertNull(DesktopLayout.decode("pr=none").precision)
    }

    @Test
    fun `a preset changes the buttons and sticks but keeps the speeds`() {
        val tuned = DesktopLayout.DEFAULT.copy(speed = 2f, invertY = true)
        val android = tuned.withButtonsOf(DesktopPreset.ANDROID)
        assertEquals(DesktopJob.BACK, android.job(DesktopControl.B))
        assertEquals(2f, android.speed)
        assertTrue(android.invertY)
        assertEquals(DesktopPreset.ANDROID, android.preset)
    }

    @Test
    fun `taken buttons are those with a job, the slow-down button and Start when it switches`() {
        val layout = DesktopLayout.DEFAULT
            .with(DesktopControl.START, DesktopJob.NONE)
            .with(DesktopControl.L1, DesktopJob.NONE)
            .with(DesktopControl.R1, DesktopJob.NONE)
            .copy(precision = DesktopControl.R1)
        assertTrue(DesktopControl.START in layout.taken)
        assertFalse(DesktopControl.L1 in layout.taken)
        assertTrue(DesktopControl.R1 in layout.taken)
        assertFalse(DesktopControl.START in layout.copy(holdStartSwitch = false).taken)
    }

    @Test
    fun `in the Xbox style the face buttons arrive by position`() {
        assertEquals(DesktopControl.A, DesktopControl.ofRaw(304, xbox = false))
        assertEquals(DesktopControl.B, DesktopControl.ofRaw(304, xbox = true))
        assertEquals(DesktopControl.Y, DesktopControl.ofRaw(307, xbox = true))
        assertEquals(DesktopControl.R2, DesktopControl.ofRaw(313, xbox = true))
        assertNull(DesktopControl.ofRaw(316, xbox = false))
    }

    @Test
    fun `Thor Tools' keyboard has no letters, so Android's on-screen keyboard still opens`() {
        val letters = (16..25) + (30..38) + (44..50)
        assertTrue(DesktopJob.keys.none { it in letters })
    }
}
