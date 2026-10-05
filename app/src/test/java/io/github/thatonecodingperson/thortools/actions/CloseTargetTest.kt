package io.github.thatonecodingperson.thortools.actions

import io.github.thatonecodingperson.thortools.hotkeys.CloseAppArg
import io.github.thatonecodingperson.thortools.hotkeys.LaunchScreen
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CloseTargetTest {
    private val valid: (String) -> Boolean = { it.contains('.') }

    @Test
    fun `the panel closes on the screen it was opened from, or the chosen one`() {
        assertEquals(LaunchScreen.TOP, CloseTarget.screenFor(PanelCloseTarget.OPENED_FROM, openedFromTop = true))
        assertEquals(LaunchScreen.BOTTOM, CloseTarget.screenFor(PanelCloseTarget.OPENED_FROM, openedFromTop = false))
        assertEquals(LaunchScreen.TOP, CloseTarget.screenFor(PanelCloseTarget.TOP, openedFromTop = false))
        assertEquals(LaunchScreen.BOTTOM, CloseTarget.screenFor(PanelCloseTarget.BOTTOM, openedFromTop = true))
    }

    @Test
    fun `the app on that screen is closed, never Thor Tools itself`() {
        val apps = mapOf(0 to "org.example.game", 2 to "io.github.me.thortools")
        assertEquals("org.example.game", CloseTarget.appToClose(apps, 0, "io.github.me.thortools", valid))
        assertNull(CloseTarget.appToClose(apps, 2, "io.github.me.thortools", valid))
        assertNull(CloseTarget.appToClose(apps, 5, "io.github.me.thortools", valid))
        assertNull(CloseTarget.appToClose(mapOf(0 to "bad name"), 0, "io.github.me.thortools", valid))
    }

    @Test
    fun `the focused display is read from dumpsys input`() {
        assertEquals(2, CloseTarget.parseFocusedDisplay("  FocusedDisplayId: 2"))
        assertEquals(0, CloseTarget.parseFocusedDisplay("FocusedDisplayId: 0\n"))
        assertNull(CloseTarget.parseFocusedDisplay(""))
        assertNull(CloseTarget.parseFocusedDisplay(null))
    }

    @Test
    fun `stored panel choices and hotkey screens read back`() {
        PanelCloseTarget.entries.forEach { assertEquals(it, PanelCloseTarget.byId(it.id)) }
        assertNull(PanelCloseTarget.byId("sideways"))
        LaunchScreen.entries.forEach { assertEquals(it, CloseAppArg.decode(CloseAppArg.encode(it))) }
        assertNull(CloseAppArg.decode(null))
        assertNull(CloseAppArg.decode("sideways"))
    }
}
