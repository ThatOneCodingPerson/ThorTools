package io.github.thatonecodingperson.thortools.navigation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GestureWishTest {
    private val offBoth = GestureChoice(on = false)

    @Test
    fun `on means every swipe works, off stops only the chosen parts`() {
        assertEquals(GestureParts(), GestureChoice().parts())
        assertEquals(GestureParts(homeSwipe = false, backSwipe = false), offBoth.parts())
        assertEquals(GestureParts(homeSwipe = false, backSwipe = true), offBoth.copy(stopBack = false).parts())
        assertEquals(GestureParts(homeSwipe = true, backSwipe = false), offBoth.copy(stopHome = false).parts())
        assertTrue(offBoth.copy(stopHome = false, stopBack = false).parts().allWork)
    }

    @Test
    fun `the last ticked part can't be unticked, so off always stops something`() {
        val homeOnly = GestureChoice().withStopBack(false)
        assertEquals(GestureChoice(stopBack = false), homeOnly)
        assertEquals(homeOnly, homeOnly.withStopHome(false))
        assertEquals(GestureChoice(stopHome = false), GestureChoice().withStopHome(false))
        assertEquals(GestureChoice(stopHome = false), GestureChoice(stopHome = false).withStopBack(false))
        assertEquals(GestureChoice(), homeOnly.withStopBack(true))
    }

    @Test
    fun `nothing ticked as stored reads as on with both ticked, keeping the desktop switch`() {
        assertEquals(
            GestureChoice(on = true, offWithDesktop = true),
            GestureChoice.stored(on = false, stopHome = false, stopBack = false, offWithDesktop = true),
        )
        assertEquals(
            GestureChoice(on = false, stopBack = false),
            GestureChoice.stored(on = false, stopHome = true, stopBack = false, offWithDesktop = false),
        )
    }

    @Test
    fun `the page decides until a profile does, and the profile only while its app is in front`() {
        val page = GestureWish(offBoth)
        assertEquals(GestureSource.PAGE, page.source)
        assertFalse(page.on)
        val game = page.forApp(true)
        assertEquals(GestureSource.APP, game.source)
        assertTrue(game.on)
        val left = game.forApp(null)
        assertEquals(GestureSource.PAGE, left.source)
        assertFalse(left.on)
    }

    @Test
    fun `an app's profile off uses the page's parts`() {
        val wish = GestureWish(GestureChoice(on = true, stopHome = true, stopBack = false)).forApp(false)
        assertEquals(GestureParts(homeSwipe = false, backSwipe = true), wish.parts)
    }

    @Test
    fun `the action flips the page's switch when no profile decides`() {
        val toggled = GestureWish(GestureChoice()).toggled()
        assertEquals(GestureSource.PAGE, toggled.source)
        assertFalse(toggled.choice.on)
        assertFalse(toggled.on)
        assertTrue(toggled.toggled().choice.on)
    }

    @Test
    fun `in a profiled app the action lasts until the app changes, and the page keeps its switch`() {
        val inGame = GestureWish(GestureChoice()).forApp(false)
        val pressed = inGame.toggled()
        assertEquals(GestureSource.ACTION, pressed.source)
        assertTrue(pressed.on)
        assertTrue(pressed.choice.on)
        val pressedAgain = pressed.toggled()
        assertFalse(pressedAgain.on)
        assertEquals(GestureSource.ACTION, pressedAgain.source)
        val nextApp = pressed.forApp(null)
        assertEquals(GestureSource.PAGE, nextApp.source)
        assertTrue(nextApp.on)
    }

    @Test
    fun `desktop controls stop the swipes only when the page asks for it`() {
        val asked = GestureWish(GestureChoice(offWithDesktop = true))
        assertTrue(asked.on)
        assertFalse(asked.withDesktop(true).on)
        assertEquals(GestureSource.DESKTOP, asked.withDesktop(true).source)
        assertTrue(asked.withDesktop(true).withDesktop(false).on)
        assertTrue(GestureWish(GestureChoice()).withDesktop(true).on)
    }

    @Test
    fun `when desktop controls stop, the page and the profile decide again`() {
        val pageOff = GestureWish(GestureChoice(on = false, offWithDesktop = true)).withDesktop(true)
        assertFalse(pageOff.withDesktop(false).on)
        val profileOff = GestureWish(GestureChoice(offWithDesktop = true)).forApp(false).withDesktop(true)
        assertFalse(profileOff.withDesktop(false).on)
    }

    @Test
    fun `desktop controls win over a profile's on, the action wins over both`() {
        val wish = GestureWish(GestureChoice(offWithDesktop = true)).forApp(true).withDesktop(true)
        assertFalse(wish.on)
        val pressed = wish.toggled()
        assertEquals(GestureSource.ACTION, pressed.source)
        assertTrue(pressed.on)
        assertTrue(pressed.choice.on)
        assertTrue(pressed.withDesktop(false).withDesktop(true).on)
    }

    @Test
    fun `desktop controls use the page's parts`() {
        val wish = GestureWish(GestureChoice(stopBack = false, offWithDesktop = true)).withDesktop(true)
        assertEquals(GestureParts(homeSwipe = false, backSwipe = true), wish.parts)
    }

    @Test
    fun `changing the page ends an action's change`() {
        val pressed = GestureWish(GestureChoice()).forApp(false).toggled()
        val changed = pressed.withChoice(GestureChoice(stopBack = false))
        assertEquals(GestureSource.APP, changed.source)
        assertEquals(GestureParts(homeSwipe = false, backSwipe = true), changed.parts)
    }
}
