package io.github.thatonecodingperson.thortools.panel

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PanelClosingTest {
    @Test
    fun `with only AYN closing it, AYN and the close button close the panel and nothing else a user does`() {
        val closing = CloseReason.entries.filter { PanelClosing.closes(it, onlyAynCloses = true) }.toSet()
        val always = setOf(
            CloseReason.AYN,
            CloseReason.CLOSE_BUTTON,
            CloseReason.OPEN_THOR_TOOLS,
            CloseReason.SCREEN_OFF,
            CloseReason.SERVICE_STOPPED,
        )
        assertEquals(always, closing)
        listOf(CloseReason.BACK, CloseReason.TOUCH_OUTSIDE, CloseReason.TILE, CloseReason.HOTKEY, CloseReason.HOME, CloseReason.OTHER_APP)
            .forEach { assertFalse(it.name, PanelClosing.closes(it, onlyAynCloses = true)) }
    }

    @Test
    fun `with the setting off, every reason closes the panel as before`() {
        CloseReason.entries.forEach { assertTrue(it.name, PanelClosing.closes(it, onlyAynCloses = false)) }
    }
}
