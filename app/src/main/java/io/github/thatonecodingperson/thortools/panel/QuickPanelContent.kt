package io.github.thatonecodingperson.thortools.panel

import androidx.annotation.StringRes
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.BatteryChargingFull
import androidx.compose.material.icons.rounded.BatteryFull
import androidx.compose.material.icons.rounded.BrightnessHigh
import androidx.compose.material.icons.rounded.BrightnessLow
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import io.github.thatonecodingperson.thortools.R
import io.github.thatonecodingperson.thortools.actions.ThorAction
import io.github.thatonecodingperson.thortools.actions.icon
import io.github.thatonecodingperson.thortools.hotkeys.LaunchScreen
import io.github.thatonecodingperson.thortools.input.Screen
import io.github.thatonecodingperson.thortools.ui.theme.LocalThorPalette
import kotlinx.coroutines.delay
import java.text.DateFormat
import java.util.Date
import kotlin.math.roundToInt

/** False in previews (the panel editor, the theme editor): nothing there can be clicked or focused. */
val LocalPanelInteractive = staticCompositionLocalOf { true }

/** Everything the panel's content can ask its host to do. */
class PanelCallbacks(
    val onTile: (String) -> Unit = {},
    val onRefreshRate: () -> Unit = {},
    val onVolume: (Float) -> Unit = {},
    val onTopBrightness: (Float) -> Unit = {},
    val onBottomBrightness: (Float) -> Unit = {},
    val onClose: () -> Unit = {},
    /** Opens Thor Tools on the panel editor. */
    val onEdit: () -> Unit = {},
    val onMedia: (MediaCommand) -> Unit = {},
    val onAllowMedia: () -> Unit = {},
    val onApp: (String, LaunchScreen) -> Unit = { _, _ -> },
    val onNoteDrawn: (String, List<NoteStroke>) -> Unit = { _, _ -> },
    val onTimer: (TimerCommand) -> Unit = {},
    val onToggle: (QuickToggle) -> Unit = {},
    val onScreenshot: (Screenshot, LaunchScreen) -> Unit = { _, _ -> },
    val onAllowScreenshots: () -> Unit = {},
)

/** The panel: a header with page dots, then the pages, each a grid of widgets ([WidgetGrid]). */
@Composable
fun QuickPanelContent(state: PanelUiState, layout: PanelLayout, pagerState: PagerState, callbacks: PanelCallbacks) {
    val palette = LocalThorPalette.current
    val interactive = LocalPanelInteractive.current
    val pageFocus = remember { FocusRequester() }
    LaunchedEffect(pagerState.currentPage) {
        if (!interactive) return@LaunchedEffect
        // The window needs a moment to receive input focus before a request sticks.
        delay(FIRST_FOCUS_DELAY_MS)
        runCatching { pageFocus.requestFocus() }
    }

    // There is no Surface in an overlay window, so text and icons would otherwise fall back to black.
    CompositionLocalProvider(LocalContentColor provides Color(palette.onSurface)) {
        PanelPages(state, layout, pagerState, callbacks, pageFocus)
    }
}

@Composable
private fun PanelPages(
    state: PanelUiState,
    layout: PanelLayout,
    pagerState: PagerState,
    callbacks: PanelCallbacks,
    pageFocus: FocusRequester,
) {
    val palette = LocalThorPalette.current
    val interactive = LocalPanelInteractive.current
    BoxWithConstraints(Modifier.fillMaxSize().background(Color(palette.background))) {
        // Too short for everything to share the height: fixed sizes and a scroll instead.
        val fits = maxHeight >= MIN_FIT_HEIGHT
        Column(
            verticalArrangement = Arrangement.spacedBy(GAP),
            modifier = Modifier
                .fillMaxSize()
                .padding(PADDING),
        ) {
            Header(
                state = state,
                pages = layout.pages.size,
                current = pagerState.currentPage,
                onEdit = callbacks.onEdit,
                onClose = callbacks.onClose,
            )
            HorizontalPager(
                state = pagerState,
                beyondViewportPageCount = 0,
                pageSpacing = GAP,
                userScrollEnabled = interactive,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
            ) { index ->
                val page = layout.pages.getOrNull(index) ?: return@HorizontalPager
                PageContent(page, state, callbacks, fits, focus = pageFocus.takeIf { index == pagerState.currentPage })
            }
        }
    }
}

/** Whether [widget] has something the controller can land on, so a new page can start there. */
private fun takesFocus(widget: PanelWidget, page: PanelPage, state: PanelUiState): Boolean = when (widget.type) {
    WidgetType.TILES -> page.tiles.isNotEmpty()
    WidgetType.LEVELS -> widget.sliders.isNotEmpty()
    WidgetType.DEVICE, WidgetType.NOTES, WidgetType.CONTROLLER, WidgetType.TOGGLES -> true
    WidgetType.CLOCK -> widget.size.columns > 1 && widget.size.rows > 1 || widget.size.columns >= 3
    WidgetType.MEDIA -> !state.media.access || state.media.title != null
    WidgetType.APPS -> widget.apps.isNotEmpty()
    WidgetType.RECENT -> state.recent.isNotEmpty()
    WidgetType.SCREENSHOTS -> !state.screenshotsAllowed || state.screenshots.isNotEmpty()
    WidgetType.BATTERY, WidgetType.GRAPH, WidgetType.PLAY_TIMER, WidgetType.STORAGE, WidgetType.NETWORK -> false
}

@Composable
private fun PageContent(page: PanelPage, state: PanelUiState, callbacks: PanelCallbacks, fits: Boolean, focus: FocusRequester?) {
    // An icons widget without icons has nothing to show.
    val placements = remember(page) { WidgetGrid.place(page.widgets.filter { it.type != WidgetType.TILES || page.tiles.isNotEmpty() }) }
    if (placements.isEmpty()) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
            Text(
                text = stringResource(R.string.panelPageEmpty),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
        return
    }
    // The first thing the controller lands on when the page shows: the icons when there are any, as always.
    val focusOn = placements.map { it.widget }.filter { takesFocus(it, page, state) }.let { widgets ->
        widgets.find { it.type == WidgetType.TILES } ?: widgets.firstOrNull()
    }?.type
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val rows = WidgetGrid.rows(placements)
        val cellWidth = (maxWidth - GAP * (WidgetGrid.COLUMNS - 1)) / WidgetGrid.COLUMNS
        // Too short for the grid to share the height: fixed rows and a scroll instead.
        val cellHeight = if (fits) (maxHeight - GAP * (WidgetGrid.ROWS - 1)) / WidgetGrid.ROWS else FIXED_CELL_HEIGHT
        val height = cellHeight * rows + GAP * (rows - 1)
        Box(Modifier.fillMaxSize().then(if (height > maxHeight) Modifier.verticalScroll(rememberScrollState()) else Modifier)) {
            Box(Modifier.fillMaxWidth().height(height)) {
                placements.forEach { placement ->
                    val size = placement.widget.size
                    WidgetContent(
                        widget = placement.widget,
                        page = page,
                        state = state,
                        callbacks = callbacks,
                        focus = focus.takeIf { placement.widget.type == focusOn },
                        modifier = Modifier
                            .offset(x = (cellWidth + GAP) * placement.column, y = (cellHeight + GAP) * placement.row)
                            .size(cellWidth * size.columns + GAP * (size.columns - 1), cellHeight * size.rows + GAP * (size.rows - 1)),
                    )
                }
            }
        }
    }
}

@Composable
private fun WidgetContent(
    widget: PanelWidget,
    page: PanelPage,
    state: PanelUiState,
    callbacks: PanelCallbacks,
    focus: FocusRequester?,
    modifier: Modifier,
) {
    when (widget.type) {
        WidgetType.TILES -> ControlsCard(page, state, focus, callbacks.onTile, modifier)
        WidgetType.LEVELS -> LevelsCard(state, callbacks, widget, focus, modifier)
        WidgetType.DEVICE -> DeviceCard(state, callbacks.onRefreshRate, focus, widget.size, modifier)
        WidgetType.MEDIA -> MediaCard(state.media, widget.size, callbacks.onMedia, callbacks.onAllowMedia, focus, modifier)
        WidgetType.BATTERY -> BatteryCard(state, widget.size, modifier)
        WidgetType.GRAPH -> GraphCard(state, modifier)
        WidgetType.APPS -> AppsCard(widget, state.apps, callbacks.onApp, focus, modifier)
        WidgetType.NOTES -> NotesCard(widget.note, state.notes[widget.note], callbacks.onNoteDrawn, focus, modifier)
        WidgetType.PLAY_TIMER -> PlayTimeCard(
            state.front,
            state.front?.let {
                state.apps[it.packageName]
            },
            widget.size,
            widget.remind,
            modifier,
        )
        WidgetType.RECENT -> AppsCard(
            widget.copy(apps = state.recent),
            state.apps,
            callbacks.onApp,
            focus,
            modifier,
            empty = R.string.panelRecentEmpty,
        )
        WidgetType.CONTROLLER -> ControllerStatusCard(state, widget.size, callbacks.onTile, focus, modifier)
        WidgetType.CLOCK -> ClockCard(state.timer, widget.size, callbacks.onTimer, focus, modifier)
        WidgetType.SCREENSHOTS -> ScreenshotsCard(state, {
            callbacks.onScreenshot(it, widget.appsOn)
        }, callbacks.onAllowScreenshots, focus, modifier)
        WidgetType.STORAGE -> StorageCard(state, widget.size, modifier)
        WidgetType.NETWORK -> NetworkCard(state.network, widget.size, modifier)
        WidgetType.TOGGLES -> TogglesCard(state.toggles, widget.size, callbacks.onToggle, focus, modifier)
    }
}

/** Click and focus handling that previews leave out. */
internal fun Modifier.tap(interactive: Boolean, onFocus: (Boolean) -> Unit, onClick: () -> Unit): Modifier =
    if (interactive) onFocusChanged { onFocus(it.isFocused) }.clickable(onClick = onClick) else this

internal fun Modifier.focusFrom(focus: FocusRequester?): Modifier = if (focus != null) focusRequester(focus) else this

@Composable
private fun Header(state: PanelUiState, pages: Int, current: Int, onEdit: () -> Unit, onClose: () -> Unit) {
    val palette = LocalThorPalette.current
    var now by remember { mutableStateOf(Date()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(CLOCK_TICK_MS)
            now = Date()
        }
    }
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Text(
            text = DateFormat.getTimeInstance(DateFormat.SHORT).format(now),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.width(16.dp))
        if (state.battery >= 0) {
            Icon(
                imageVector = if (state.charging) Icons.Rounded.BatteryChargingFull else Icons.Rounded.BatteryFull,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(4.dp))
            Text(
                text = stringResource(if (state.charging) R.string.panelBatteryCharging else R.string.panelBattery, state.battery),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.weight(1f))
        if (pages > 1) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(end = 12.dp)) {
                repeat(pages) { index ->
                    Box(
                        Modifier
                            .size(DOT_SIZE)
                            .clip(CircleShape)
                            .background(Color(if (index == current) palette.primary else palette.tileOff)),
                    )
                }
            }
        }
        if (LocalPanelInteractive.current) {
            IconButton(onClick = onEdit, modifier = Modifier.size(36.dp)) {
                Icon(Icons.Rounded.Edit, contentDescription = stringResource(R.string.editPanel))
            }
            IconButton(onClick = onClose, modifier = Modifier.size(36.dp)) {
                Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.panelClose))
            }
        } else {
            Icon(Icons.Rounded.Edit, contentDescription = null, modifier = Modifier.size(36.dp).padding(6.dp))
            Icon(Icons.Rounded.Close, contentDescription = null, modifier = Modifier.size(36.dp).padding(6.dp))
        }
    }
}

@Composable
internal fun PanelCard(modifier: Modifier, content: @Composable () -> Unit) {
    val palette = LocalThorPalette.current
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(palette.cornerDp.dp))
            .background(Color(palette.surface))
            .padding(CARD_PADDING),
    ) { content() }
}

/** One slider of the levels widget. Null [value]: it can't be set now. */
private class Level(val value: Float?, val icon: ImageVector, @StringRes val label: Int, val onChange: (Float) -> Unit)

/** The chosen sliders: standing side by side, or lying one above the other when the widget is wider than tall. */
@Composable
private fun LevelsCard(state: PanelUiState, callbacks: PanelCallbacks, widget: PanelWidget, focus: FocusRequester?, modifier: Modifier) {
    val levels = listOfNotNull(
        Level(state.volume, Icons.AutoMirrored.Rounded.VolumeUp, R.string.panelVolume, callbacks.onVolume)
            .takeIf { LevelSlider.VOLUME in widget.sliders },
        Level(state.topBrightness, Icons.Rounded.BrightnessHigh, R.string.panelLevelTop, callbacks.onTopBrightness)
            .takeIf { LevelSlider.TOP in widget.sliders },
        Level(state.bottomBrightness, Icons.Rounded.BrightnessLow, R.string.panelLevelBottom, callbacks.onBottomBrightness)
            .takeIf { LevelSlider.BOTTOM in widget.sliders },
    )
    val focusIndex = levels.indexOfFirst { it.value != null }
    PanelCard(modifier) {
        if (widget.size.wide) {
            Column(verticalArrangement = Arrangement.spacedBy(LEVEL_GAP_LYING), modifier = Modifier.fillMaxSize()) {
                levels.forEachIndexed { index, level ->
                    HorizontalLevel(level, focus.takeIf { index == focusIndex }, Modifier.weight(1f).fillMaxWidth())
                }
            }
        } else {
            BoxWithConstraints(Modifier.fillMaxSize()) {
                val count = levels.size.coerceAtLeast(1)
                // Every slider gets the same slot; a slot too narrow for the words shows the icons only.
                val slot = min(LEVEL_SLOT_MAX, (maxWidth - LEVEL_GAP * (count - 1)) / count)
                Row(
                    horizontalArrangement = Arrangement.spacedBy(LEVEL_GAP, Alignment.CenterHorizontally),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    levels.forEachIndexed { index, level ->
                        VerticalLevel(
                            value = level.value,
                            icon = level.icon,
                            label = stringResource(level.label).takeIf { slot >= LEVEL_LABEL_MIN_SLOT },
                            slot = slot,
                            focus = focus.takeIf { index == focusIndex },
                            onChange = level.onChange,
                        )
                    }
                }
            }
        }
    }
}

/** A lying slider: the icon, then the bar. Touch sets the level where the finger is; with focus, D-pad left and right step it. */
@Composable
private fun HorizontalLevel(level: Level, focus: FocusRequester?, modifier: Modifier) {
    val palette = LocalThorPalette.current
    val interactive = LocalPanelInteractive.current
    val value = level.value
    val current by rememberUpdatedState(value ?: 0f)
    val change by rememberUpdatedState(level.onChange)
    var focused by remember { mutableStateOf(false) }
    val muted = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = if (value == null) DISABLED_ALPHA else 1f)
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = modifier) {
        Icon(level.icon, contentDescription = stringResource(level.label), tint = muted, modifier = Modifier.size(18.dp))
        BoxWithConstraints(contentAlignment = Alignment.CenterStart, modifier = Modifier.weight(1f).fillMaxHeight()) {
            val thickness = min(maxHeight, LEVEL_WIDTH)
            val shape = RoundedCornerShape(thickness / 2)
            Box(
                contentAlignment = Alignment.CenterStart,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(thickness)
                    .clip(shape)
                    .background(Color(palette.tileOff))
                    .border(FOCUS_BORDER, if (focused) Color(palette.primary) else Color.Transparent, shape)
                    .then(
                        if (value == null || !interactive) {
                            Modifier
                        } else {
                            Modifier
                                .focusFrom(focus)
                                .onFocusChanged { focused = it.isFocused }
                                .onKeyEvent { event ->
                                    val step = when (event.key) {
                                        Key.DirectionRight -> LEVEL_STEP
                                        Key.DirectionLeft -> -LEVEL_STEP
                                        else -> return@onKeyEvent false
                                    }
                                    if (event.type == KeyEventType.KeyDown) change((current + step).coerceIn(0f, 1f))
                                    true
                                }
                                .focusable()
                                .pointerInput(Unit) {
                                    fun levelAt(position: Offset) = (position.x / size.width).coerceIn(0f, 1f)
                                    awaitEachGesture {
                                        val down = awaitFirstDown()
                                        change(levelAt(down.position))
                                        drag(down.id) { moved ->
                                            change(levelAt(moved.position))
                                            moved.consume()
                                        }
                                    }
                                }
                        },
                    ),
            ) {
                if (value != null) Box(Modifier.fillMaxHeight().fillMaxWidth(value.coerceIn(0f, 1f)).background(Color(palette.primary)))
            }
        }
    }
}

/** A vertical slider. Touch sets the level where the finger is; with focus, D-pad up and down step it. Null is disabled. */
@Composable
private fun VerticalLevel(value: Float?, icon: ImageVector, label: String?, slot: Dp, focus: FocusRequester?, onChange: (Float) -> Unit) {
    val palette = LocalThorPalette.current
    val interactive = LocalPanelInteractive.current
    val width = min(LEVEL_WIDTH, slot)
    val shape = RoundedCornerShape(width / 2)
    val current by rememberUpdatedState(value ?: 0f)
    val change by rememberUpdatedState(onChange)
    var focused by remember { mutableStateOf(false) }
    val muted = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = if (value == null) DISABLED_ALPHA else 1f)
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier
            .fillMaxHeight()
            .width(slot),
    ) {
        Box(
            contentAlignment = Alignment.BottomCenter,
            modifier = Modifier
                .weight(1f)
                .width(width)
                .clip(shape)
                .background(Color(palette.tileOff))
                .border(FOCUS_BORDER, if (focused) Color(palette.primary) else Color.Transparent, shape)
                .then(
                    if (value == null || !interactive) {
                        Modifier
                    } else {
                        Modifier
                            .focusFrom(focus)
                            .onFocusChanged { focused = it.isFocused }
                            .onKeyEvent { event ->
                                val step = when (event.key) {
                                    Key.DirectionUp -> LEVEL_STEP
                                    Key.DirectionDown -> -LEVEL_STEP
                                    else -> return@onKeyEvent false
                                }
                                if (event.type == KeyEventType.KeyDown) change((current + step).coerceIn(0f, 1f))
                                true
                            }
                            .focusable()
                            .pointerInput(Unit) {
                                fun levelAt(position: Offset) = (1f - position.y / size.height).coerceIn(0f, 1f)
                                awaitEachGesture {
                                    val down = awaitFirstDown()
                                    change(levelAt(down.position))
                                    drag(down.id) { moved ->
                                        change(levelAt(moved.position))
                                        moved.consume()
                                    }
                                }
                            }
                    },
                ),
        ) {
            if (value != null) {
                Box(Modifier.fillMaxWidth().fillMaxHeight(value.coerceIn(0f, 1f)).background(Color(palette.primary)))
            }
        }
        Icon(icon, contentDescription = label, tint = muted, modifier = Modifier.size(18.dp))
        if (label != null) Text(text = label, style = MaterialTheme.typography.labelSmall, color = muted, maxLines = 1)
    }
}

@Composable
private fun ControlsCard(page: PanelPage, state: PanelUiState, focus: FocusRequester?, onTile: (String) -> Unit, modifier: Modifier) {
    PanelCard(modifier) {
        BoxWithConstraints {
            val rows = page.tiles.chunked(page.columns)
            val available = maxHeight / rows.size.coerceAtLeast(1)
            // Too many rows to fit: keep the circles readable and let the grid scroll.
            val scrolls = available - TILE_TEXT_HEIGHT < MIN_CIRCLE
            val rowHeight = if (scrolls) MIN_CIRCLE + TILE_TEXT_HEIGHT else available
            val circle = min((rowHeight - TILE_TEXT_HEIGHT).coerceIn(MIN_CIRCLE, MAX_CIRCLE), maxWidth / page.columns - TILE_SIDE_ROOM)
            Column(
                Modifier
                    .fillMaxSize()
                    .then(if (scrolls) Modifier.verticalScroll(rememberScrollState()) else Modifier),
            ) {
                rows.forEachIndexed { rowIndex, row ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .height(rowHeight),
                    ) {
                        row.forEachIndexed { index, id ->
                            ToggleTile(
                                id = id,
                                state = state,
                                circle = circle,
                                focus = focus.takeIf { rowIndex == 0 && index == 0 },
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight(),
                            ) { onTile(id) }
                        }
                        repeat(page.columns - row.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }
        }
    }
}

@Composable
private fun modeValue(action: ThorAction?, state: PanelUiState): String? = when (action) {
    ThorAction.TOGGLE_LAYOUT, ThorAction.CYCLE_CONTROLLER_STYLE -> state.controllerStyle?.let { stringResource(it) }
    ThorAction.CYCLE_L2R2 -> state.l2r2?.let { stringResource(it) }
    ThorAction.CYCLE_PERFORMANCE -> state.performance?.let { stringResource(it) }
    ThorAction.CYCLE_FAN -> state.fan?.let { stringResource(it) }
    ThorAction.TOGGLE_REFRESH_RATE -> state.refreshHz?.let { stringResource(R.string.actionResultRefreshRate, it) }
    else -> null
}

private fun switchOn(action: ThorAction?, state: PanelUiState): Boolean = when (action) {
    ThorAction.TOGGLE_BOTTOM_SCREEN -> state.bottomScreenOn
    ThorAction.TOGGLE_STAY_AWAKE -> state.stayAwake
    ThorAction.TOGGLE_AYN_MOUSE -> state.aynMouse
    ThorAction.TOGGLE_CONTROLLER_LOCK -> state.lockedTo == Screen.TOP
    ThorAction.LOCK_CONTROLLER_BOTTOM -> state.lockedTo == Screen.BOTTOM
    ThorAction.LOCK_CONTROLLER_HERE -> state.lockedTo != null
    else -> false
}

@Composable
private fun ToggleTile(id: String, state: PanelUiState, circle: Dp, focus: FocusRequester?, modifier: Modifier, onClick: () -> Unit) {
    val palette = LocalThorPalette.current
    val interactive = LocalPanelInteractive.current
    var focused by remember { mutableStateOf(false) }
    val action = PanelTiles.action(id)
    val look = PanelTiles.look(id)
    val on = look == PanelTiles.Look.SWITCH && switchOn(action, state)
    val background = when {
        on -> palette.primary
        look == PanelTiles.Look.MODE -> palette.tileOn
        else -> palette.tileOff
    }
    val value = when (look) {
        PanelTiles.Look.MODE -> modeValue(action, state)
        PanelTiles.Look.SWITCH -> stringResource(if (on) R.string.panelOn else R.string.panelOff)
        PanelTiles.Look.ACTION -> null
    }
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .focusFrom(focus.takeIf { interactive })
            .tap(interactive, { focused = it }, onClick)
            .padding(horizontal = 2.dp, vertical = 2.dp),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(circle)
                .clip(CircleShape)
                .background(Color(background))
                .border(
                    FOCUS_BORDER,
                    if (focused) Color(if (on) palette.onSurface else palette.primary) else Color.Transparent,
                    CircleShape,
                ),
        ) {
            Icon(
                imageVector = action?.icon ?: Icons.Rounded.Settings,
                contentDescription = null,
                tint = Color(if (on) palette.onPrimary else palette.onSurface),
                modifier = Modifier.size(circle * ICON_SCALE),
            )
        }
        Spacer(Modifier.height(4.dp))
        Text(
            text = stringResource(PanelTiles.label(id)),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = if (focused) FontWeight.Bold else FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = value.orEmpty(),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** Refresh rate, the four gauges and the temperatures: one strip (4x1, or bigger rings at 4x2), or two rows at 2x2. */
@Composable
private fun DeviceCard(state: PanelUiState, onRefreshRate: () -> Unit, focus: FocusRequester?, size: WidgetSize, modifier: Modifier) {
    val palette = LocalThorPalette.current
    val stats = state.stats
    val none = stringResource(R.string.panelNoValue)
    val ring = if (size.rows >= 2 && size.columns >= WidgetGrid.COLUMNS) RING_SIZE_LARGE else RING_SIZE
    val gauges: @Composable (Modifier) -> Unit = { each ->
        Gauge(stats.cpuGhz?.let { "%.1f".format(it) } ?: none, stats.cpuLoad, palette.chart[0], R.string.panelStatCpu, ring, each)
        Gauge(stats.gpuMhz?.toString() ?: none, stats.gpuLoad, palette.chart[1], R.string.panelStatGpu, ring, each)
        Gauge(
            state.powerW?.let { "%.1f".format(it) } ?: none,
            state.powerW?.let { it / POWER_FULL_SCALE_W },
            palette.chart[2],
            R.string.panelStatPower,
            ring,
            each,
        )
        Gauge(
            stats.ramUsedGb?.let { "%.1f".format(it) } ?: none,
            stats.ramUsedGb?.let { used -> stats.ramTotalGb?.let { used / it } },
            palette.chart[3],
            R.string.panelStatRam,
            ring,
            each,
        )
    }
    PanelCard(modifier) {
        if (size.rows == 1 && size.columns < WidgetGrid.COLUMNS) {
            // 2x1: the refresh rate and the temperatures; 3x1 adds the CPU and GPU gauges.
            Row(
                horizontalArrangement = Arrangement.spacedBy(GAP),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxSize(),
            ) {
                RefreshRate(state.refreshHz, focus, onRefreshRate)
                if (size.columns >= 3) {
                    Gauge(
                        stats.cpuGhz?.let {
                            "%.1f".format(it)
                        } ?: none,
                        stats.cpuLoad,
                        palette.chart[0],
                        R.string.panelStatCpu,
                        ring,
                        Modifier.weight(1f),
                    )
                    Gauge(
                        stats.gpuMhz?.toString() ?: none,
                        stats.gpuLoad,
                        palette.chart[1],
                        R.string.panelStatGpu,
                        ring,
                        Modifier.weight(1f),
                    )
                } else {
                    Spacer(Modifier.weight(1f))
                }
                Temperatures(stats)
            }
        } else if (size.columns < WidgetGrid.COLUMNS) {
            Column(verticalArrangement = Arrangement.spacedBy(GAP), modifier = Modifier.fillMaxSize()) {
                Row(horizontalArrangement = Arrangement.spacedBy(GAP), modifier = Modifier.weight(1f).fillMaxWidth()) {
                    RefreshRate(state.refreshHz, focus, onRefreshRate)
                    Temperatures(stats)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.weight(1f).fillMaxWidth()) {
                    gauges(Modifier.weight(1f))
                }
            }
        } else {
            Row(
                horizontalArrangement = Arrangement.spacedBy(GAP),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxSize(),
            ) {
                RefreshRate(state.refreshHz, focus, onRefreshRate)
                gauges(Modifier.weight(1f))
                Temperatures(stats)
            }
        }
    }
}

/** The current peak refresh rate in big numbers; A or a tap switches between 60 and 120 Hz. */
@Composable
private fun RefreshRate(hz: Int?, focus: FocusRequester?, onClick: () -> Unit) {
    val palette = LocalThorPalette.current
    val interactive = LocalPanelInteractive.current
    val shape = RoundedCornerShape(12.dp)
    var focused by remember { mutableStateOf(false) }
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier
            .fillMaxHeight()
            .width(REFRESH_WIDTH)
            .clip(shape)
            .background(Color(palette.tileOn))
            .border(FOCUS_BORDER, if (focused) Color(palette.primary) else Color.Transparent, shape)
            .focusFrom(focus.takeIf { interactive })
            .tap(interactive, { focused = it }, onClick),
    ) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                text = hz?.toString() ?: stringResource(R.string.panelNoValue),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = stringResource(R.string.panelHz),
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.padding(start = 2.dp, bottom = 5.dp),
            )
        }
        Text(text = stringResource(R.string.panelTileRefresh), style = MaterialTheme.typography.labelSmall, maxLines = 1)
    }
}

/** A ring that fills with [fraction] (null leaves it empty), the value in the middle and the unit below. */
@Composable
private fun Gauge(value: String, fraction: Float?, color: Long, label: Int, ring: Dp, modifier: Modifier) {
    val track = Color(LocalThorPalette.current.tileOff)
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = modifier.fillMaxHeight(),
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.weight(1f, fill = false).size(ring)) {
            Canvas(Modifier.fillMaxSize()) {
                val stroke = RING_STROKE.toPx()
                val inset = stroke / 2
                val arcSize = Size(size.width - stroke, size.height - stroke)
                drawArc(
                    track,
                    RING_START,
                    RING_SWEEP,
                    useCenter = false,
                    topLeft = Offset(inset, inset),
                    size = arcSize,
                    style = Stroke(stroke, cap = StrokeCap.Round),
                )
                val filled = (fraction ?: 0f).coerceIn(0f, 1f) * RING_SWEEP
                if (filled > 0f) {
                    drawArc(
                        Color(color),
                        RING_START,
                        filled,
                        useCenter = false,
                        topLeft = Offset(inset, inset),
                        size = arcSize,
                        style = Stroke(stroke, cap = StrokeCap.Round),
                    )
                }
            }
            Text(text = value, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold, maxLines = 1)
        }
        Text(
            text = stringResource(label),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun Temperatures(stats: StatsReading) {
    val none = stringResource(R.string.panelNoValue)
    Column(verticalArrangement = Arrangement.Center, modifier = Modifier.fillMaxHeight().width(TEMP_WIDTH)) {
        Text(
            text = stats.cpuTemp?.let { stringResource(R.string.panelDegrees, it.roundToInt()) } ?: none,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = stringResource(R.string.panelTempCpu),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = stringResource(
                R.string.panelTempGpu,
                stats.gpuTemp?.let { stringResource(R.string.panelDegrees, it.roundToInt()) } ?: none,
            ),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
        )
    }
}

private const val FIRST_FOCUS_DELAY_MS = 150L
private const val CLOCK_TICK_MS = 15_000L
private const val LEVEL_STEP = 1f / 16
private const val DISABLED_ALPHA = 0.38f
private const val ICON_SCALE = 0.5f

// Power above this fills the ring; the Thor draws roughly 5 to 15 W in games.
private const val POWER_FULL_SCALE_W = 20f

// The ring is open at the bottom, like a gauge.
private const val RING_START = 135f
private const val RING_SWEEP = 270f
private val MIN_FIT_HEIGHT = 380.dp
private val FIXED_CELL_HEIGHT = 90.dp
private val PADDING = 14.dp
private val GAP = 10.dp
private val CARD_PADDING = 10.dp
private val LEVEL_WIDTH = 36.dp
private val LEVEL_GAP = 6.dp
private val LEVEL_SLOT_MAX = 56.dp
private val LEVEL_LABEL_MIN_SLOT = 44.dp
private val LEVEL_GAP_LYING = 4.dp
private val TILE_TEXT_HEIGHT = 36.dp
private val TILE_SIDE_ROOM = 8.dp
private val MIN_CIRCLE = 30.dp
private val MAX_CIRCLE = 56.dp
internal val FOCUS_BORDER = 2.dp
private val DOT_SIZE = 7.dp
private val REFRESH_WIDTH = 84.dp
private val TEMP_WIDTH = 64.dp
private val RING_SIZE = 56.dp
private val RING_SIZE_LARGE = 84.dp
private val RING_STROKE = 6.dp
