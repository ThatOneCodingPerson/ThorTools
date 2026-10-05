package io.github.thatonecodingperson.thortools.panel

import io.github.thatonecodingperson.thortools.actions.ThorAction
import io.github.thatonecodingperson.thortools.hotkeys.LaunchScreen

/** A widget's span on the page grid ([WidgetGrid]): [columns] of 4 across, [rows] down. */
data class WidgetSize(val columns: Int, val rows: Int) {
    /** Wider than tall: sliders lie down, for instance. */
    val wide: Boolean get() = columns > rows

    fun encode(): String = "${columns}x$rows"

    companion object {
        fun decode(text: String?): WidgetSize? {
            val (columns, rows) = text?.split('x')?.takeIf { it.size == 2 }?.map { it.toIntOrNull() ?: return null } ?: return null
            return WidgetSize(columns, rows)
        }
    }
}

private fun size(columns: Int, rows: Int) = WidgetSize(columns, rows)

/** "3x3 2x2" as sizes. */
private fun sizes(list: String): List<WidgetSize> = list.split(' ').map { checkNotNull(WidgetSize.decode(it)) }

/**
 * What a page can hold, each at most once, with the sizes it comes in (the first one is where a new one starts).
 * [id] is stored, so it must never change.
 */
enum class WidgetType(val id: String, val sizes: List<WidgetSize>) {
    /** The page's icons ([PanelPage.tiles]). */
    TILES("tiles", sizes("3x3 2x2 3x2 4x2 2x3 4x3 2x4 3x4 4x4 4x1 2x1 3x1")),

    /** Volume and the brightness of both screens. */
    LEVELS("levels", sizes("1x3 1x2 1x4 2x2 2x1 3x1 4x1 2x3 2x4 3x2 4x2")),

    /** Refresh rate, CPU, GPU, power, memory and temperatures. */
    DEVICE("device", sizes("4x1 4x2 2x2 2x1 3x1 3x2")),
    MEDIA("media", sizes("4x1 2x1 2x2 4x2 1x1 3x1 3x2")),
    BATTERY("battery", sizes("2x1 1x1 2x2 1x2 3x1 4x1")),
    GRAPH("graph", sizes("4x1 2x1 2x2 4x2 3x1 3x2 4x3")),
    APPS("apps", sizes("4x1 1x1 2x1 2x2 4x2 3x1 1x2 3x2 4x3")),
    NOTES("notes", sizes("2x2 4x2 2x3 4x3 4x4 3x2 3x3 2x4")),

    /** How long the app in front has been there, with a break reminder. */
    PLAY_TIMER("playtime", sizes("2x1 1x1 3x1 4x1 2x2")),

    /** The apps opened last, one tap back in. */
    RECENT("recent", sizes("4x1 2x1 3x1 2x2 4x2")),

    /** Layout, L2/R2, the lock and the screen the controller is on. */
    CONTROLLER("controller", sizes("2x2 2x1 3x1 4x1")),

    /** The time and date, with a stopwatch and a timer. */
    CLOCK("clock", sizes("2x2 2x1 1x1 4x1 4x2 3x2")),
    SCREENSHOTS("screenshots", sizes("4x1 2x1 2x2 4x2 3x1")),
    STORAGE("storage", sizes("2x1 1x1 2x2 4x1")),
    NETWORK("network", sizes("2x1 1x1 2x2 4x1")),

    /** Wi-Fi, Bluetooth, airplane mode and Do not disturb. */
    TOGGLES("toggles", sizes("4x1 2x1 2x2 3x1")),
    ;

    val defaultSize: WidgetSize get() = sizes.first()

    companion object {
        fun byId(id: String): WidgetType? = entries.find { it.id == id }
    }
}

/** The sliders a [WidgetType.LEVELS] widget can show. [id] is stored. */
enum class LevelSlider(val id: String) {
    VOLUME("volume"),
    TOP("top"),
    BOTTOM("bottom"),
}

/**
 * One widget on a page. The options only matter to their type: [sliders] to the sliders, [apps] to App shortcuts,
 * [appsOn] (where an app or a picture opens) to App shortcuts, Recent apps and Screenshots, [note] (the note's file
 * name) to Notes, [remind] (minutes between break reminders, 0 for none) to the play timer.
 */
data class PanelWidget(
    val type: WidgetType,
    val size: WidgetSize = type.defaultSize,
    val sliders: Set<LevelSlider> = LevelSlider.entries.toSet(),
    val apps: List<String> = emptyList(),
    val appsOn: LaunchScreen = LaunchScreen.TOP,
    val note: String = "",
    val remind: Int = 0,
)

/**
 * One page: its widgets in order, packed into the grid by [WidgetGrid.place]. [tiles] ([PanelTiles] ids) and [columns]
 * (icons per row) belong to its icons widget.
 */
data class PanelPage(
    val tiles: List<String> = emptyList(),
    val columns: Int = DEFAULT_COLUMNS,
    val widgets: List<PanelWidget> = emptyList(),
) {
    /** Nothing to show: no widgets, or only an icons widget without icons. */
    val isEmpty: Boolean get() = widgets.none { it.type != WidgetType.TILES || tiles.isNotEmpty() }

    fun widget(type: WidgetType): PanelWidget? = widgets.find { it.type == type }

    fun withColumns(count: Int) = copy(columns = count.coerceIn(MIN_COLUMNS, MAX_COLUMNS))

    /** Adds [type] at the end in its first size; [note] names a new Notes widget's file. */
    fun addWidget(type: WidgetType, note: String = ""): PanelPage {
        if (widget(type) != null) return this
        return copy(widgets = widgets + PanelWidget(type, note = note.takeIf { type == WidgetType.NOTES }.orEmpty()))
    }

    fun removeWidget(type: WidgetType) = copy(widgets = widgets.filter { it.type != type })

    /** Moves a widget [by] places (negative = earlier), stopping at either end. */
    fun moveWidget(type: WidgetType, by: Int): PanelPage {
        val from = widgets.indexOfFirst { it.type == type }.takeIf { it >= 0 } ?: return this
        val to = (from + by).coerceIn(0, widgets.lastIndex)
        return copy(widgets = widgets.toMutableList().apply { add(to, removeAt(from)) })
    }

    fun resizeWidget(type: WidgetType, size: WidgetSize) = if (size !in type.sizes) this else updateWidget(type) { it.copy(size = size) }

    fun updateWidget(type: WidgetType, change: (PanelWidget) -> PanelWidget) =
        copy(widgets = widgets.map { if (it.type == type) change(it).copy(type = type) else it })

    /** Adding an icon to a page without an icons widget adds one. */
    fun addTile(id: String) = if (id in tiles) this else copy(tiles = tiles + id).addWidget(WidgetType.TILES)

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

/** A widget's place on the grid: its top-left cell. */
data class Placement(val widget: PanelWidget, val column: Int, val row: Int)

/**
 * The page grid: 4 columns, and 4 rows fit the bottom screen (the sliders, icons and stats of the first page take 1x3,
 * 3x3 and 4x1). Pure.
 */
object WidgetGrid {
    const val COLUMNS = 4
    const val ROWS = 4

    /** Each widget in order goes into the first free spot, row by row; the grid grows downwards when it is full. */
    fun place(widgets: List<PanelWidget>): List<Placement> {
        val taken = mutableListOf<BooleanArray>()
        fun free(column: Int, row: Int, size: WidgetSize): Boolean {
            for (r in row until row + size.rows) {
                val cells = taken.getOrNull(r) ?: continue
                for (c in column until column + size.columns) if (cells[c]) return false
            }
            return true
        }
        return widgets.map { widget ->
            val size = widget.size.copy(columns = widget.size.columns.coerceIn(1, COLUMNS), rows = widget.size.rows.coerceAtLeast(1))
            // Rows below the grid are always free, so a spot is always found.
            val (row, column) = generateSequence(0) { it + 1 }.firstNotNullOf { row ->
                (0..COLUMNS - size.columns).firstOrNull { free(it, row, size) }?.let { row to it }
            }
            while (taken.size < row + size.rows) taken += BooleanArray(COLUMNS)
            for (r in row until row + size.rows) for (c in column until column + size.columns) taken[r][c] = true
            Placement(widget.copy(size = size), column, row)
        }
    }

    /** How many rows [placements] use. */
    fun rows(placements: List<Placement>): Int = placements.maxOfOrNull { it.row + it.widget.size.rows } ?: 0
}

/** The quick panel's pages, stored as text in the `panel_layout` preference. */
data class PanelLayout(val pages: List<PanelPage>) {

    fun addPage() = if (pages.size >= MAX_PAGES) this else copy(pages = pages + PanelPage())

    fun removePage(index: Int) =
        if (pages.size <= 1 || index !in pages.indices) this else copy(pages = pages.filterIndexed { i, _ -> i != index })

    fun updatePage(index: Int, change: (PanelPage) -> PanelPage) =
        if (index !in pages.indices) this else copy(pages = pages.mapIndexed { i, page -> if (i == index) change(page) else page })

    /**
     * Pages separated by `|`, each like `columns=4;tiles=a,b;w=levels:1x3,tiles:3x3;sliders=volume,top;apps=x,y;
     * appson=top;note=n1`. Version 1 (before widgets) had `widgets=levels,device` instead of `w=`.
     */
    fun encode(): String = pages.joinToString(PAGE_SEPARATOR) { page ->
        buildList {
            add("columns=${page.columns}")
            add("tiles=${page.tiles.joinToString(",")}")
            add("w=${page.widgets.joinToString(",") { "${it.type.id}:${it.size.encode()}" }}")
            page.widget(WidgetType.LEVELS)?.let { levels ->
                add("sliders=${levels.sliders.sortedBy(LevelSlider::ordinal).joinToString(",") { it.id }}")
            }
            page.widget(WidgetType.APPS)?.let { add("apps=${it.apps.joinToString(",")}") }
            page.widgets.filter { it.type in OPENS_SOMETHING }.forEach { add("${it.type.id}on=${it.appsOn.id}") }
            page.widget(WidgetType.NOTES)?.let { add("note=${it.note}") }
            page.widget(WidgetType.PLAY_TIMER)?.let { add("remind=${it.remind}") }
        }.joinToString(";")
    }

    companion object {
        const val MAX_PAGES = 5
        private const val PAGE_SEPARATOR = "|"

        /** The Notes widget of the default layout writes to this file. */
        const val DEFAULT_NOTE = "main"

        /** The play timer's break reminders, in minutes (0: none). */
        val REMINDERS = listOf(0, 30, 60, 90, 120)

        /** Widgets that open an app or a picture on a screen of their choice. */
        private val OPENS_SOMETHING = setOf(WidgetType.APPS, WidgetType.RECENT, WidgetType.SCREENSHOTS)

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
                    widgets = listOf(
                        PanelWidget(WidgetType.LEVELS, size(1, 3)),
                        PanelWidget(WidgetType.TILES, size(3, 3)),
                        PanelWidget(WidgetType.DEVICE, size(4, 1)),
                    ),
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
                    widgets = listOf(PanelWidget(WidgetType.TILES, size(4, 4))),
                ),
                PanelPage(
                    widgets = listOf(
                        PanelWidget(WidgetType.MEDIA, size(4, 1)),
                        PanelWidget(WidgetType.BATTERY, size(2, 1)),
                        PanelWidget(WidgetType.GRAPH, size(2, 1)),
                        PanelWidget(WidgetType.NOTES, size(4, 2), note = DEFAULT_NOTE),
                    ),
                ),
            ),
        )

        /** Unknown tiles, widgets and sizes are dropped; anything unreadable gives [DEFAULT]. */
        fun decode(text: String?, knownTiles: Set<String> = PanelTiles.ids): PanelLayout {
            if (text.isNullOrBlank()) return DEFAULT
            val pages = text.split(PAGE_SEPARATOR).take(MAX_PAGES).map { pageText ->
                val values = pageText.split(';').associate { it.substringBefore('=') to it.substringAfter('=', "") }
                val tiles = values["tiles"].orEmpty().split(',').filter { it in knownTiles }.distinct()
                PanelPage(
                    tiles = tiles,
                    columns = (values["columns"]?.toIntOrNull() ?: PanelPage.DEFAULT_COLUMNS)
                        .coerceIn(PanelPage.MIN_COLUMNS, PanelPage.MAX_COLUMNS),
                    widgets = if ("w" in values) widgets(values) else fromVersion1(values["widgets"], hasTiles = tiles.isNotEmpty()),
                )
            }
            return if (pages.all { it.isEmpty }) DEFAULT else PanelLayout(pages)
        }

        private fun widgets(values: Map<String, String>): List<PanelWidget> {
            val sliders = values["sliders"]?.split(',')?.mapNotNull { id -> LevelSlider.entries.find { it.id == id } }?.toSet()
            return values["w"].orEmpty().split(',').mapNotNull { item ->
                val type = WidgetType.byId(item.substringBefore(':')) ?: return@mapNotNull null
                val size = WidgetSize.decode(item.substringAfter(':', ""))?.takeIf { it in type.sizes } ?: type.defaultSize
                val opensOn = LaunchScreen.entries.find { it.id == values["${type.id}on"] } ?: LaunchScreen.TOP
                when (type) {
                    WidgetType.LEVELS -> PanelWidget(type, size, sliders = sliders ?: LevelSlider.entries.toSet())
                    WidgetType.APPS -> PanelWidget(
                        type = type,
                        size = size,
                        apps = values["apps"].orEmpty().split(',').filter { it.isNotBlank() }.distinct(),
                        appsOn = opensOn,
                    )
                    WidgetType.RECENT, WidgetType.SCREENSHOTS -> PanelWidget(type, size, appsOn = opensOn)
                    WidgetType.NOTES -> PanelWidget(type, size, note = values["note"].orEmpty())
                    WidgetType.PLAY_TIMER -> PanelWidget(
                        type,
                        size,
                        remind =
                        values["remind"]?.toIntOrNull()?.takeIf { it in REMINDERS } ?: 0,
                    )
                    else -> PanelWidget(type, size)
                }
            }.distinctBy { it.type }
        }

        /** Version 1 had one fixed template: sliders on the left, the icons, the stats strip below. Same look on the grid. */
        private fun fromVersion1(widgets: String?, hasTiles: Boolean): List<PanelWidget> {
            val ids = widgets.orEmpty().split(',')
            val levels = "levels" in ids
            val device = "device" in ids
            val height = if (device) 3 else 4
            return listOfNotNull(
                PanelWidget(WidgetType.LEVELS, size(1, height)).takeIf { levels },
                PanelWidget(WidgetType.TILES, size(if (levels) 3 else 4, height)).takeIf { hasTiles },
                PanelWidget(WidgetType.DEVICE, size(4, 1)).takeIf { device },
            )
        }
    }
}
