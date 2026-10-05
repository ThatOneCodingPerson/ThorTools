package io.github.thatonecodingperson.thortools.panel

import io.github.thatonecodingperson.thortools.actions.ThorAction

/** The optional parts of a panel page besides its icon grid. [id] is stored, so it must never change. */
enum class PanelWidget(val id: String) {
    LEVELS("levels"),
    DEVICE("device"),
}

/**
 * One page, always laid out the same way: sliders on the left, the icon grid, the stats strip below. Every part is
 * optional. [tiles] are [PanelTiles] ids.
 */
data class PanelPage(
    val tiles: List<String> = emptyList(),
    val columns: Int = DEFAULT_COLUMNS,
    val widgets: Set<PanelWidget> = emptySet(),
) {
    val isEmpty: Boolean get() = tiles.isEmpty() && widgets.isEmpty()

    fun withColumns(count: Int) = copy(columns = count.coerceIn(MIN_COLUMNS, MAX_COLUMNS))

    fun toggle(widget: PanelWidget) = copy(widgets = if (widget in widgets) widgets - widget else widgets + widget)

    fun addTile(id: String) = if (id in tiles) this else copy(tiles = tiles + id)

    fun removeTile(id: String) = copy(tiles = tiles - id)

    /** Moves a tile [by] places (negative = earlier), stopping at either end. */
    fun moveTile(id: String, by: Int): PanelPage {
        val from = tiles.indexOf(id).takeIf { it >= 0 } ?: return this
        val to = (from + by).coerceIn(0, tiles.lastIndex)
        return copy(tiles = tiles.toMutableList().apply { add(to, removeAt(from)) })
    }

    companion object {
        const val MIN_COLUMNS = 3
        const val MAX_COLUMNS = 6
        const val DEFAULT_COLUMNS = 4
    }
}

/** The quick panel's pages, stored as text in the `panel_layout` preference. */
data class PanelLayout(val pages: List<PanelPage>) {

    fun addPage() = if (pages.size >= MAX_PAGES) this else copy(pages = pages + PanelPage())

    fun removePage(index: Int) = if (pages.size <= 1 ||
        index !in pages.indices
    ) {
        this
    } else {
        copy(pages = pages.filterIndexed { i, _ -> i != index })
    }

    fun updatePage(index: Int, change: (PanelPage) -> PanelPage) =
        if (index !in pages.indices) this else copy(pages = pages.mapIndexed { i, page -> if (i == index) change(page) else page })

    /** Pages separated by `|`, each `columns=4;widgets=levels,device;tiles=a,b`. */
    fun encode(): String = pages.joinToString(PAGE_SEPARATOR) { page ->
        "columns=${page.columns};widgets=${page.widgets.joinToString(",") { it.id }};tiles=${page.tiles.joinToString(",")}"
    }

    companion object {
        const val MAX_PAGES = 5
        private const val PAGE_SEPARATOR = "|"

        val DEFAULT = PanelLayout(
            listOf(
                PanelPage(
                    tiles = listOf(
                        ThorAction.TOGGLE_LAYOUT,
                        ThorAction.CYCLE_L2R2,
                        ThorAction.CYCLE_PERFORMANCE,
                        ThorAction.CYCLE_FAN,
                        ThorAction.TOGGLE_BOTTOM_SCREEN,
                        ThorAction.SWAP_SCREENS,
                        ThorAction.TOGGLE_STAY_AWAKE,
                        ThorAction.SCREENSHOT,
                        ThorAction.RECENTS,
                        ThorAction.CLOSE_APP,
                        ThorAction.AYN_DRAWER,
                    ).map { it.id } + PanelTiles.THOR_TOOLS,
                    columns = 4,
                    widgets = setOf(PanelWidget.LEVELS, PanelWidget.DEVICE),
                ),
                PanelPage(
                    tiles = listOf(
                        ThorAction.CONTROLLER_TO_TOP,
                        ThorAction.CONTROLLER_TO_BOTTOM,
                        ThorAction.HOME_BOTTOM,
                        ThorAction.CLOSE_OTHER_SCREEN_APP,
                        ThorAction.TOGGLE_AYN_MOUSE,
                        ThorAction.CLEAR_BACKGROUND,
                    ).map { it.id },
                    columns = 3,
                ),
            ),
        )

        /** Unknown tiles and widgets are dropped; anything unreadable gives [DEFAULT]. */
        fun decode(text: String?, knownTiles: Set<String> = PanelTiles.ids): PanelLayout {
            if (text.isNullOrBlank()) return DEFAULT
            val pages = text.split(PAGE_SEPARATOR).take(MAX_PAGES).map { pageText ->
                val values = pageText.split(';').associate { it.substringBefore('=') to it.substringAfter('=', "") }
                PanelPage(
                    tiles = values["tiles"].orEmpty().split(',').filter { it in knownTiles }.distinct(),
                    columns = (values["columns"]?.toIntOrNull() ?: PanelPage.DEFAULT_COLUMNS).coerceIn(
                        PanelPage.MIN_COLUMNS,
                        PanelPage.MAX_COLUMNS,
                    ),
                    widgets = values["widgets"].orEmpty().split(',').mapNotNull { id -> PanelWidget.entries.find { it.id == id } }.toSet(),
                )
            }
            return if (pages.all { it.isEmpty }) DEFAULT else PanelLayout(pages)
        }
    }
}
