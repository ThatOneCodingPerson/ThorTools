package io.github.thatonecodingperson.thortools.desktop

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PointerMotionTest {
    private val layout = DesktopLayout.DEFAULT

    @Test
    fun `inside the dead zone the pointer stays put`() {
        assertEquals(0f to 0f, PointerMotion.velocity(0.05f, -0.05f, layout))
    }

    @Test
    fun `full tilt is top speed, scaled by the speed setting and the slow-down button`() {
        val (vx, vy) = PointerMotion.velocity(1f, 0f, layout)
        assertEquals(PointerMotion.TOP_SPEED, vx, 0.01f)
        assertEquals(0f, vy, 0.01f)
        val (fast, _) = PointerMotion.velocity(1f, 0f, layout.copy(speed = 2f))
        assertEquals(PointerMotion.TOP_SPEED * 2f, fast, 0.01f)
        val (slow, _) = PointerMotion.velocity(1f, 0f, layout, precise = true)
        assertEquals(PointerMotion.TOP_SPEED * PointerMotion.PRECISION, slow, 0.01f)
    }

    @Test
    fun `acceleration makes a half push slower, never a full one`() {
        val none = PointerMotion.velocity(0.5f, 0f, layout.copy(acceleration = Acceleration.NONE)).first
        val strong = PointerMotion.velocity(0.5f, 0f, layout.copy(acceleration = Acceleration.STRONG)).first
        assertTrue(strong < none)
        assertEquals(
            PointerMotion.velocity(1f, 0f, layout.copy(acceleration = Acceleration.NONE)).first,
            PointerMotion.velocity(1f, 0f, layout.copy(acceleration = Acceleration.STRONG)).first,
            0.01f,
        )
    }

    @Test
    fun `up on the stick is up on the screen, unless inverted`() {
        assertTrue(PointerMotion.velocity(0f, -1f, layout).second < 0f)
        assertTrue(PointerMotion.velocity(0f, -1f, layout.copy(invertY = true)).second > 0f)
    }

    @Test
    fun `pushing up scrolls up, natural scrolling turns it round, sideways can be off`() {
        assertTrue(PointerMotion.scroll(0f, -1f, layout).first > 0f)
        assertTrue(PointerMotion.scroll(0f, -1f, layout.copy(naturalScroll = true)).first < 0f)
        assertTrue(PointerMotion.scroll(1f, 0f, layout).second > 0f)
        assertEquals(0f, PointerMotion.scroll(1f, 0f, layout.copy(horizontalScroll = false)).second)
    }

    @Test
    fun `a slow stick still moves, parts of a step are carried over`() {
        val carry = StepCarry()
        val steps = (1..10).sumOf { stepsFor(30f, 8, carry) }
        assertEquals(2, steps)
        carry.reset()
        assertEquals(0, stepsFor(0f, 8, carry))
    }

    @Test
    fun `a short press is a tap, a long one a hold that fires once`() {
        val start = TapOrHold(800)
        start.press(0)
        assertFalse(start.held(500))
        assertTrue(start.release())
        start.press(1_000)
        assertTrue(start.held(1_800))
        assertFalse(start.held(1_900))
        assertFalse(start.release())
    }

    @Test
    fun `a stick or trigger still pushed after a hotkey waits until it is let go`() {
        val wait = ReleaseWait()
        assertTrue(wait.passes(active = true))
        wait.arm()
        assertFalse(wait.passes(active = true))
        assertFalse(wait.passes(active = true))
        assertTrue(wait.passes(active = false))
        assertTrue(wait.passes(active = true))
        // Armed while already let go, it lets the next push through at once.
        wait.arm()
        assertTrue(wait.passes(active = false))
        assertTrue(wait.passes(active = true))
    }

    @Test
    fun `a trigger counts as pulled down to a little below its point`() {
        assertTrue(TriggerButton.pulled(0.3f, 0.35f))
        assertFalse(TriggerButton.pulled(0.2f, 0.35f))
    }

    @Test
    fun `a trigger presses past its point and lets go a little below it`() {
        val trigger = TriggerButton()
        assertFalse(trigger.update(0.3f, 0.35f))
        assertTrue(trigger.update(0.4f, 0.35f))
        assertTrue(trigger.down)
        assertFalse(trigger.update(0.3f, 0.35f))
        assertTrue(trigger.update(0.2f, 0.35f))
        assertFalse(trigger.down)
    }
}
