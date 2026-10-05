package io.github.thatonecodingperson.thortools.panel

import io.github.thatonecodingperson.thortools.actions.ThorAction
import io.github.thatonecodingperson.thortools.hotkeys.LaunchScreen
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PanelLayoutTest {

    @Test
    fun `layouts survive a text round trip`() {
        assertEquals(PanelLayout.DEFAULT, PanelLayout.decode(PanelLayout.DEFAULT.encode()))
        val odd = PanelLayout(
            listOf(
                PanelPage(widgets = listOf(PanelWidget(WidgetType.TILES))),
                PanelPage(
                    tiles = listOf(PanelTiles.THOR_TOOLS),
                    columns = 6,
                    widgets = listOf(
                        PanelWidget(WidgetType.DEVICE, WidgetSize(2, 2)),
                        PanelWidget(WidgetType.LEVELS, WidgetSize(4, 1), sliders = setOf(LevelSlider.VOLUME)),
                        PanelWidget(WidgetType.APPS, apps = listOf("com.a", "com.b"), appsOn = LaunchScreen.BOTTOM),
                        PanelWidget(WidgetType.NOTES, note = "n7"),
                        PanelWidget(WidgetType.TILES),
                    ),
                ),
            ),
        )
        assertEquals(odd, PanelLayout.decode(odd.encode()))
    }

    @Test
    fun `missing, broken or empty text gives the default`() {
        assertEquals(PanelLayout.DEFAULT, PanelLayout.decode(null))
        assertEquals(PanelLayout.DEFAULT, PanelLayout.decode(""))
        assertEquals(PanelLayout.DEFAULT, PanelLayout.decode("nonsense"))
        assertEquals(PanelLayout.DEFAULT, PanelLayout.decode("columns=4;widgets=;tiles=|columns=3;widgets=;tiles="))
        assertEquals(PanelLayout.DEFAULT, PanelLayout.decode("columns=4;tiles=;w=tiles:3x3"))
    }

    @Test
    fun `a panel saved before widgets keeps its look on the grid`() {
        val back = ThorAction.BACK.id
        val home = ThorAction.HOME.id
        val layout = PanelLayout.decode(
            "columns=4;widgets=levels,device;tiles=$back,$home|columns=3;widgets=;tiles=$home|columns=4;widgets=device;tiles=$back|" +
                "columns=4;widgets=levels;tiles=",
        )
        val first =
            listOf(
                PanelWidget(WidgetType.LEVELS, WidgetSize(1, 3)),
                PanelWidget(WidgetType.TILES, WidgetSize(3, 3)),
                PanelWidget(WidgetType.DEVICE),
            )
        assertEquals(first, layout.pages[0].widgets)
        assertEquals(listOf(back, home), layout.pages[0].tiles)
        assertEquals(listOf(PanelWidget(WidgetType.TILES, WidgetSize(4, 4))), layout.pages[1].widgets)
        assertEquals(listOf(PanelWidget(WidgetType.TILES, WidgetSize(4, 3)), PanelWidget(WidgetType.DEVICE)), layout.pages[2].widgets)
        assertEquals(listOf(PanelWidget(WidgetType.LEVELS, WidgetSize(1, 4))), layout.pages[3].widgets)
        val grid = WidgetGrid.place(layout.pages[0].widgets).map { it.column to it.row }
        assertEquals(listOf(0 to 0, 1 to 0, 0 to 3), grid)
    }

    @Test
    fun `unknown tiles, widgets and sizes are dropped, columns stay in range, duplicates go`() {
        val text = "columns=9;tiles=gone,system_back,system_back;w=levels:1x3,radar:2x2,device:3x3,levels:1x2;sliders=top,x"
        val layout = PanelLayout.decode(text)
        val page = layout.pages.single()
        assertEquals(listOf(ThorAction.BACK.id), page.tiles)
        assertEquals(PanelPage.MAX_COLUMNS, page.columns)
        assertEquals(
            listOf(PanelWidget(WidgetType.LEVELS, WidgetSize(1, 3), sliders = setOf(LevelSlider.TOP)), PanelWidget(WidgetType.DEVICE)),
            page.widgets,
        )
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
    fun `tiles can be added once, moved and removed, and bring their widget`() {
        val page = PanelPage(listOf("a", "b", "c"), widgets = listOf(PanelWidget(WidgetType.TILES)))
        assertEquals(page, page.addTile("b"))
        assertEquals(listOf("b", "a", "c"), page.moveTile("b", -1).tiles)
        assertEquals(listOf("b", "c", "a"), page.moveTile("a", 9).tiles)
        assertEquals(page, page.moveTile("x", 1))
        assertEquals(listOf("a", "c"), page.removeTile("b").tiles)
        assertEquals(PanelPage.MIN_COLUMNS, page.withColumns(1).columns)
        assertEquals(listOf(PanelWidget(WidgetType.TILES)), PanelPage().addTile("a").widgets)
    }

    @Test
    fun `widgets are added once, moved, resized to their own sizes only, and removed`() {
        var page = PanelPage().addWidget(WidgetType.MEDIA).addWidget(WidgetType.NOTES, note = "n1").addWidget(WidgetType.MEDIA)
        assertEquals(listOf(WidgetType.MEDIA, WidgetType.NOTES), page.widgets.map { it.type })
        assertEquals("n1", page.widget(WidgetType.NOTES)?.note)
        assertEquals("", PanelPage().addWidget(WidgetType.MEDIA, note = "x").widget(WidgetType.MEDIA)?.note)
        page = page.moveWidget(WidgetType.NOTES, -5)
        assertEquals(listOf(WidgetType.NOTES, WidgetType.MEDIA), page.widgets.map { it.type })
        assertEquals(WidgetSize(4, 4), page.resizeWidget(WidgetType.NOTES, WidgetSize(4, 4)).widget(WidgetType.NOTES)?.size)
        assertEquals(page, page.resizeWidget(WidgetType.NOTES, WidgetSize(1, 1)))
        assertEquals(listOf(WidgetType.MEDIA), page.removeWidget(WidgetType.NOTES).widgets.map { it.type })
        assertTrue(PanelPage(tiles = listOf("a"), widgets = listOf(PanelWidget(WidgetType.TILES))).isEmpty.not())
        assertTrue(PanelPage(widgets = listOf(PanelWidget(WidgetType.TILES))).isEmpty)
    }

    @Test
    fun `every widget starts in one of its own sizes, all within the grid's width`() {
        WidgetType.entries.forEach { type ->
            assertTrue(type.defaultSize in type.sizes)
            assertTrue(type.sizes.all { it.columns in 1..WidgetGrid.COLUMNS && it.rows in 1..WidgetGrid.ROWS })
        }
        assertEquals(WidgetSize(3, 2), WidgetSize.decode("3x2"))
        assertNull(WidgetSize.decode("3x"))
        assertNull(WidgetSize.decode(null))
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
