package io.github.thatonecodingperson.thortools.panel

import io.github.thatonecodingperson.thortools.actions.ThorAction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PanelLayoutTest {

    @Test
    fun `layouts survive a text round trip`() {
        assertEquals(PanelLayout.DEFAULT, PanelLayout.decode(PanelLayout.DEFAULT.encode()))
        val odd = PanelLayout(listOf(PanelPage(), PanelPage(listOf(PanelTiles.THOR_TOOLS), 6, setOf(PanelWidget.DEVICE))))
        assertEquals(odd, PanelLayout.decode(odd.encode()))
    }

    @Test
    fun `missing, broken or empty text gives the default`() {
        assertEquals(PanelLayout.DEFAULT, PanelLayout.decode(null))
        assertEquals(PanelLayout.DEFAULT, PanelLayout.decode(""))
        assertEquals(PanelLayout.DEFAULT, PanelLayout.decode("nonsense"))
        assertEquals(PanelLayout.DEFAULT, PanelLayout.decode("columns=4;widgets=;tiles=|columns=3;widgets=;tiles="))
    }

    @Test
    fun `unknown tiles and widgets are dropped, columns stay in range, duplicates go`() {
        val layout = PanelLayout.decode("columns=9;widgets=levels,clock;tiles=gone,system_back,system_back")
        assertEquals(PanelPage(listOf(ThorAction.BACK.id), PanelPage.MAX_COLUMNS, setOf(PanelWidget.LEVELS)), layout.pages.single())
    }

    @Test
    fun `pages stay between one and the maximum`() {
        var layout = PanelLayout.DEFAULT
        repeat(10) { layout = layout.addPage() }
        assertEquals(PanelLayout.MAX_PAGES, layout.pages.size)
        repeat(10) { layout = layout.removePage(0) }
        assertEquals(1, layout.pages.size)
        assertEquals(layout, layout.removePage(5))
    }

    @Test
    fun `tiles can be added once, moved and removed`() {
        val page = PanelPage(listOf("a", "b", "c"))
        assertEquals(page, page.addTile("b"))
        assertEquals(listOf("b", "a", "c"), page.moveTile("b", -1).tiles)
        assertEquals(listOf("b", "c", "a"), page.moveTile("a", 9).tiles)
        assertEquals(page, page.moveTile("x", 1))
        assertEquals(listOf("a", "c"), page.removeTile("b").tiles)
        assertEquals(PanelPage.MIN_COLUMNS, page.withColumns(1).columns)
        assertTrue(PanelWidget.LEVELS in page.toggle(PanelWidget.LEVELS).widgets)
        assertEquals(page, page.toggle(PanelWidget.LEVELS).toggle(PanelWidget.LEVELS))
    }

    @Test
    fun `the panel offers every action except app launch and itself, plus Thor Tools`() {
        assertTrue(PanelTiles.THOR_TOOLS in PanelTiles.ids)
        assertTrue(ThorAction.LAUNCH_APP.id !in PanelTiles.ids)
        assertTrue(ThorAction.OPEN_QUICK_PANEL.id !in PanelTiles.ids)
        assertTrue(PanelLayout.DEFAULT.pages.flatMap { it.tiles }.all { it in PanelTiles.ids })
    }

    @Test
    fun `tiles that act on the screens close the panel first, toggles keep it open`() {
        assertEquals(450L, PanelTiles.leaveDelayMs(ThorAction.SCREENSHOT.id))
        assertEquals(250L, PanelTiles.leaveDelayMs(ThorAction.SWAP_SCREENS.id))
        assertEquals(150L, PanelTiles.leaveDelayMs(PanelTiles.THOR_TOOLS))
        assertNull(PanelTiles.leaveDelayMs(ThorAction.CYCLE_FAN.id))
        assertNull(PanelTiles.leaveDelayMs(ThorAction.LOUDER.id))
        assertEquals(PanelTiles.Look.MODE, PanelTiles.look(ThorAction.TOGGLE_REFRESH_RATE.id))
        assertEquals(PanelTiles.Look.SWITCH, PanelTiles.look(ThorAction.TOGGLE_STAY_AWAKE.id))
        assertEquals(PanelTiles.Look.ACTION, PanelTiles.look(PanelTiles.THOR_TOOLS))
    }
}
