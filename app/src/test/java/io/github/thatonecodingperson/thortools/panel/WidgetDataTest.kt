package io.github.thatonecodingperson.thortools.panel

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WidgetDataTest {

    private fun widget(type: WidgetType, columns: Int, rows: Int) = PanelWidget(type, WidgetSize(columns, rows))

    @Test
    fun `widgets fill the grid row by row, small ones going into gaps`() {
        val placed = WidgetGrid.place(
            listOf(
                widget(WidgetType.MEDIA, 4, 1),
                widget(WidgetType.BATTERY, 1, 1),
                widget(WidgetType.NOTES, 2, 2),
                widget(WidgetType.APPS, 1, 1),
                widget(WidgetType.GRAPH, 4, 1),
            ),
        ).map { Triple(it.widget.type, it.column, it.row) }
        assertEquals(
            listOf(
                Triple(WidgetType.MEDIA, 0, 0),
                Triple(WidgetType.BATTERY, 0, 1),
                Triple(WidgetType.NOTES, 1, 1),
                Triple(WidgetType.APPS, 3, 1),
                Triple(WidgetType.GRAPH, 0, 3),
            ),
            placed,
        )
    }

    @Test
    fun `a full grid grows downwards and nothing is lost`() {
        val placed = WidgetGrid.place(listOf(widget(WidgetType.TILES, 4, 4), widget(WidgetType.NOTES, 4, 2), widget(WidgetType.APPS, 9, 0)))
        assertEquals(listOf(0, 4, 6), placed.map { it.row })
        assertEquals(WidgetSize(4, 1), placed.last().widget.size)
        assertEquals(7, WidgetGrid.rows(placed))
        assertEquals(0, WidgetGrid.rows(emptyList()))
    }

    @Test
    fun `drawn notes survive being stored, broken parts are skipped`() {
        val strokes = listOf(
            NoteStroke(0, listOf(NotePoint(0.1f, 0.2f), NotePoint(0.3333f, 0.5f))),
            NoteStroke(4, listOf(NotePoint(1f, 0f))),
        )
        val back = NoteStrokes.decode(NoteStrokes.encode(strokes))
        assertEquals(listOf(NoteStroke(0, listOf(NotePoint(0.1f, 0.2f), NotePoint(0.333f, 0.5f))), strokes[1]), back)
        assertEquals(listOf(NoteStroke(1, listOf(NotePoint(1f, 0.5f)))), NoteStrokes.decode("9:0.1,0.1\n1:2,0.5 x,1 0.1\nnonsense"))
        assertEquals(emptyList<NoteStroke>(), NoteStrokes.decode(null))
    }

    @Test
    fun `the eraser takes whole strokes it touches, and there is a cap`() {
        val a = NoteStroke(1, listOf(NotePoint(0.1f, 0.1f), NotePoint(0.2f, 0.1f)))
        val b = NoteStroke(2, listOf(NotePoint(0.8f, 0.8f)))
        assertEquals(listOf(b), NoteStrokes.erase(listOf(a, b), 0.21f, 0.11f, radius = 0.03f))
        assertEquals(listOf(a, b), NoteStrokes.erase(listOf(a, b), 0.5f, 0.5f, radius = 0.03f))
        var many = emptyList<NoteStroke>()
        repeat(NoteStrokes.MAX_STROKES + 5) { many = NoteStrokes.add(many, b) }
        assertEquals(NoteStrokes.MAX_STROKES, many.size)
        assertEquals(many, NoteStrokes.add(many, NoteStroke(0, emptyList())))
    }

    @Test
    fun `the graph keeps the last minute`() {
        val history = StatsHistory()
        history.add(GraphPoint(0, 0.1f, null, 40f))
        history.add(GraphPoint(30_000, 0.2f, 0.3f, 41f))
        assertEquals(listOf(30_000L, 70_000L), history.add(GraphPoint(70_000, 0.3f, 0.3f, 42f)).map { it.time })
        assertEquals(0f, StatsHistory.tempFraction(20f))
        assertEquals(0.5f, StatsHistory.tempFraction(60f))
        assertEquals(1f, StatsHistory.tempFraction(120f))
    }

    @Test
    fun `battery estimates handle missing values and either unit`() {
        assertEquals(90, BatteryEstimate.toFullMinutes(5_400_000))
        assertNull(BatteryEstimate.toFullMinutes(-1))
        assertEquals(120, BatteryEstimate.leftMinutes(chargeCounter = 3_000_000, current = -1_500_000))
        assertEquals(120, BatteryEstimate.leftMinutes(chargeCounter = 3_000_000, current = 1_500))
        assertNull(BatteryEstimate.leftMinutes(chargeCounter = 0, current = -1_500_000))
        assertNull(BatteryEstimate.leftMinutes(chargeCounter = 3_000_000, current = 0))
    }
}
