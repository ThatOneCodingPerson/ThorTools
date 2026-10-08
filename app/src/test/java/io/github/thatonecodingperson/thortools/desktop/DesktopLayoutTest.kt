package io.github.thatonecodingperson.thortools.desktop

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DesktopLayoutTest {
    @Test
    fun `the default uses only the shoulders and triggers and leaves every other button to the app`() {
        val layout = DesktopLayout.DEFAULT
        assertEquals(DesktopPreset.SHOULDERS, layout.preset)
        assertEquals(StickRole.POINTER, layout.rightStick)
        assertEquals(StickRole.SCROLL, layout.leftStick)
        assertEquals(DesktopJob.LEFT_CLICK, layout.job(DesktopControl.R2))
        assertEquals(DesktopJob.RIGHT_CLICK, layout.job(DesktopControl.L2))
        assertEquals(DesktopJob.MOUSE_BACK, layout.job(DesktopControl.L1))
        assertEquals(DesktopJob.MOUSE_FORWARD, layout.job(DesktopControl.R1))
        assertEquals(setOf(DesktopControl.L1, DesktopControl.R1, DesktopControl.L2, DesktopControl.R2), layout.taken)
        assertFalse(layout.holdStartSwitch)
    }

    @Test
    fun `the Steam Controller preset is a Steam Controller's desktop layout`() {
        val layout = DesktopPreset.STEAM_CONTROLLER.layout
        assertEquals(StickRole.POINTER, layout.rightStick)
        assertEquals(StickRole.SCROLL, layout.leftStick)
        assertEquals(DesktopJob.LEFT_CLICK, layout.job(DesktopControl.R2))
        assertEquals(DesktopJob.RIGHT_CLICK, layout.job(DesktopControl.L2))
        assertEquals(DesktopJob.ENTER, layout.job(DesktopControl.A))
        assertEquals(DesktopJob.ESCAPE, layout.job(DesktopControl.B))
        assertEquals(DesktopJob.KEYBOARD, layout.job(DesktopControl.X))
        assertEquals(DesktopJob.SPACE, layout.job(DesktopControl.Y))
        assertEquals(DesktopJob.TAB, layout.job(DesktopControl.SELECT))
        // Switching is the on/off hotkey's; holding Start pauses only when chosen.
        assertFalse(layout.holdStartSwitch)
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
            .copy(precision = DesktopControl.R1, holdStartSwitch = true)
        assertTrue(DesktopControl.START in layout.taken)
        assertFalse(DesktopControl.L1 in layout.taken)
        assertTrue(DesktopControl.R1 in layout.taken)
        assertFalse(DesktopControl.START in layout.copy(holdStartSwitch = false).taken)
    }

    @Test
    fun `the pointer and both-screens choices come back from their stored text`() {
        val layout = DesktopLayout.DEFAULT.copy(
            aynPointer = true,
            bottomScreen = true,
            bottomButtons = setOf(DesktopControl.X, DesktopControl.A),
        )
        assertEquals(layout, DesktopLayout.decode(layout.encode()))
        // No buttons ticked: only the D-pad works the bottom screen.
        val dpadOnly = DesktopLayout.DEFAULT.copy(bottomButtons = emptySet())
        assertEquals(dpadOnly, DesktopLayout.decode(dpadOnly.encode()))
        // Older settings keep A and B for the bottom screen.
        assertEquals(DesktopLayout.DEFAULT_BOTTOM_BUTTONS, DesktopLayout.decode("a=enter").bottomButtons)
    }

    @Test
    fun `with the controller on the bottom screen its buttons stay the app's`() {
        // Only the D-pad until buttons are chosen for the bottom screen.
        assertEquals(DesktopLayout.DEFAULT.taken, DesktopLayout.DEFAULT.takenFor(split = true))
        val layout = DesktopPreset.STEAM_CONTROLLER.layout.copy(bottomButtons = setOf(DesktopControl.A, DesktopControl.B))
        assertEquals(layout.taken, layout.takenFor(split = false))
        val split = layout.takenFor(split = true)
        assertFalse(DesktopControl.A in split)
        assertFalse(DesktopControl.B in split)
        assertTrue(DesktopControl.R2 in split)
        assertTrue(DesktopControl.SELECT in split)
    }

    @Test
    fun `with the controller kept below the right stick points and the left one is the bottom screen's`() {
        val layout = DesktopLayout.DEFAULT.copy(leftStick = StickRole.POINTER, rightStick = StickRole.SCROLL)
        assertEquals(StickRole.POINTER to StickRole.SCROLL, layout.sticksFor(split = false))
        assertEquals(StickRole.NONE to StickRole.POINTER, layout.sticksFor(split = true))
    }

    @Test
    fun `while Scroll while held is down the pointer stick scrolls`() {
        val layout = DesktopLayout.DEFAULT.copy(leftStick = StickRole.POINTER, rightStick = StickRole.SCROLL)
        assertEquals(StickRole.SCROLL to StickRole.SCROLL, layout.sticksFor(split = false, scrollHeld = true))
        assertEquals(StickRole.NONE to StickRole.SCROLL, layout.sticksFor(split = true, scrollHeld = true))
        assertTrue(DesktopControl.R1 in layout.with(DesktopControl.R1, DesktopJob.SCROLL_HOLD).taken)
    }

    @Test
    fun `holding Start no longer pauses unless chosen, and older stored choices start over`() {
        assertFalse(DesktopLayout.DEFAULT.holdStartSwitch)
        // "hold=1" and "bb=a,b" were the old keys: the new defaults apply.
        val old = DesktopLayout.decode("hold=1;bb=a,b")
        assertFalse(old.holdStartSwitch)
        assertEquals(emptySet<DesktopControl>(), old.bottomButtons)
        val chosen = DesktopLayout.DEFAULT.copy(holdStartSwitch = true, bottomButtons = setOf(DesktopControl.X))
        assertEquals(chosen, DesktopLayout.decode(chosen.encode()))
    }

    @Test
    fun `with AYN's pointer the slow-down button stays the app's`() {
        val layout = DesktopLayout.DEFAULT.with(DesktopControl.R1, DesktopJob.NONE).copy(precision = DesktopControl.R1)
        assertTrue(DesktopControl.R1 in layout.taken)
        assertFalse(DesktopControl.R1 in layout.copy(aynPointer = true).taken)
    }

    @Test
    fun `every key job has its Android key code, for keys sent to the top screen`() {
        assertTrue(DesktopJob.entries.filter { it.kind == DesktopJob.Kind.KEY }.all { it.keyCode > 0 })
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

    @Test
    fun `the sticks that point and scroll are kept from apps, a stick left to the app is not`() {
        val layout = DesktopLayout.DEFAULT
        assertEquals(true to true, layout.sticksHidden(split = false))
        assertEquals(false to true, layout.copy(leftStick = StickRole.NONE).sticksHidden(split = false))
        // With the controller kept below, the left stick is the bottom screen's app's.
        assertEquals(false to true, layout.sticksHidden(split = true))
    }

    @Test
    fun `with AYN's pointer or the choice off, apps keep both sticks, and the choice comes back from its stored text`() {
        val layout = DesktopLayout.DEFAULT
        assertEquals(false to false, layout.copy(aynPointer = true).sticksHidden(split = false))
        assertEquals(false to false, layout.copy(hideSticks = false).sticksHidden(split = false))
        assertEquals(false, DesktopLayout.decode(layout.copy(hideSticks = false).encode()).hideSticks)
        assertEquals(true, DesktopLayout.decode("ls=pointer").hideSticks)
    }
}
