package io.github.thatonecodingperson.thortools.panel

import androidx.annotation.StringRes
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Backspace
import androidx.compose.material.icons.rounded.BatteryChargingFull
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.Draw
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.google.accompanist.drawablepainter.rememberDrawablePainter
import io.github.thatonecodingperson.thortools.R
import io.github.thatonecodingperson.thortools.hotkeys.LaunchScreen
import io.github.thatonecodingperson.thortools.ui.theme.LocalThorPalette
import kotlinx.coroutines.delay
import kotlin.math.max
import kotlin.math.roundToInt

/** A short message in the middle of a widget, with an optional button. */
@Composable
private fun WidgetMessage(@StringRes text: Int, @StringRes button: Int? = null, focus: FocusRequester? = null, onClick: () -> Unit = {}) {
    val palette = LocalThorPalette.current
    val interactive = LocalPanelInteractive.current
    var focused by remember { mutableStateOf(false) }
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterVertically),
        modifier = Modifier.fillMaxSize(),
    ) {
        Text(
            text = stringResource(text),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
        )
        if (button != null) {
            val shape = RoundedCornerShape(50)
            Text(
                text = stringResource(button),
                style = MaterialTheme.typography.labelLarge,
                color = Color(palette.onPrimary),
                modifier = Modifier
                    .clip(shape)
                    .background(Color(palette.primary))
                    .border(FOCUS_BORDER, if (focused) Color(palette.onSurface) else Color.Transparent, shape)
                    .focusFrom(focus.takeIf { interactive })
                    .tap(interactive, { focused = it }, onClick)
                    .padding(horizontal = 14.dp, vertical = 6.dp),
            )
        }
    }
}

/** A round button for the widgets (media controls, the note's tools). */
@Composable
private fun RoundButton(icon: ImageVector, size: Dp, focus: FocusRequester? = null, filled: Boolean = false, onClick: () -> Unit) {
    val palette = LocalThorPalette.current
    val interactive = LocalPanelInteractive.current
    var focused by remember { mutableStateOf(false) }
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(Color(if (filled) palette.primary else palette.tileOff))
            .border(
                FOCUS_BORDER,
                if (focused) Color(if (filled) palette.onSurface else palette.primary) else Color.Transparent,
                CircleShape,
            )
            .focusFrom(focus.takeIf { interactive })
            .tap(interactive, { focused = it }, onClick),
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = Color(if (filled) palette.onPrimary else palette.onSurface),
            modifier = Modifier.size(
                size * 0.55f,
            ),
        )
    }
}

// ---- Now playing ----

@Composable
internal fun MediaCard(
    media: NowPlaying,
    size: WidgetSize,
    onMedia: (MediaCommand) -> Unit,
    onAllow: () -> Unit,
    focus: FocusRequester?,
    modifier: Modifier,
) {
    PanelCard(modifier) {
        when {
            !media.access -> WidgetMessage(R.string.panelMediaNoAccess, R.string.panelMediaAllow, focus, onAllow)
            media.title == null -> WidgetMessage(R.string.panelMediaNothing)
            size.rows >= 2 && size.columns <= 2 -> Column(
                verticalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxSize(),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    AlbumArt(media, Modifier.size(64.dp))
                    MediaTitles(media, Modifier.weight(1f).padding(start = 10.dp))
                }
                MediaControls(media, onMedia, focus, Modifier.align(Alignment.CenterHorizontally), compact = false)
            }
            else -> Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxSize()) {
                if (size.columns >= 4 || size.rows >= 2) AlbumArt(media, Modifier.fillMaxHeight().aspectRatio(1f))
                Column(Modifier.weight(1f).padding(horizontal = 10.dp)) {
                    MediaTitles(media, Modifier)
                    if (size.rows >= 2) {
                        Spacer(Modifier.height(10.dp))
                        MediaControls(media, onMedia, focus, Modifier, compact = false)
                    }
                }
                if (size.rows < 2) MediaControls(media, onMedia, focus, Modifier, compact = size.columns <= 2)
            }
        }
    }
}

@Composable
private fun AlbumArt(media: NowPlaying, modifier: Modifier) {
    val palette = LocalThorPalette.current
    val shape = RoundedCornerShape(10.dp)
    val art = media.art
    if (art != null) {
        Image(art.asImageBitmap(), contentDescription = null, contentScale = ContentScale.Crop, modifier = modifier.clip(shape))
    } else {
        Box(contentAlignment = Alignment.Center, modifier = modifier.clip(shape).background(Color(palette.tileOff))) {
            Icon(Icons.Rounded.MusicNote, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun MediaTitles(media: NowPlaying, modifier: Modifier) {
    Column(modifier) {
        Text(
            media.title.orEmpty(),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        val second = listOfNotNull(media.artist, media.app).distinct().joinToString(" · ")
        Text(
            second,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun MediaControls(
    media: NowPlaying,
    onMedia: (MediaCommand) -> Unit,
    focus: FocusRequester?,
    modifier: Modifier,
    compact: Boolean,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically, modifier = modifier) {
        if (!compact) RoundButton(Icons.Rounded.SkipPrevious, MEDIA_BUTTON) { onMedia(MediaCommand.PREVIOUS) }
        RoundButton(if (media.playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, MEDIA_BUTTON, focus, filled = true) {
            onMedia(MediaCommand.PLAY_PAUSE)
        }
        if (!compact) RoundButton(Icons.Rounded.SkipNext, MEDIA_BUTTON) { onMedia(MediaCommand.NEXT) }
    }
}

// ---- Battery and charging ----

@Composable
internal fun BatteryCard(state: PanelUiState, size: WidgetSize, modifier: Modifier) {
    val palette = LocalThorPalette.current
    val level = state.battery.takeIf { it >= 0 }
    val percent = level?.let { stringResource(R.string.panelPercent, it) } ?: stringResource(R.string.panelNoValue)
    val watts = state.powerW?.let { stringResource(R.string.panelWatts, it) }
    val status = stringResource(if (state.charging) R.string.panelBatteryStatusCharging else R.string.panelBatteryStatusBattery)
    val time = state.toFullMinutes?.let { stringResource(R.string.panelBatteryToFull, duration(it)) }
        ?: state.leftMinutes?.let { stringResource(R.string.panelBatteryLeft, duration(it)) }
    val color = if (state.charging) palette.chart[2] else palette.primary
    PanelCard(modifier) {
        when {
            size.columns == 1 -> Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier.fillMaxSize(),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (state.charging) Icon(Icons.Rounded.BatteryChargingFull, contentDescription = null, modifier = Modifier.size(18.dp))
                    Text(percent, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                }
                Text(
                    watts ?: status,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
            size.rows == 1 -> Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxSize()) {
                Ring(level?.div(100f), color, Modifier.fillMaxHeight().aspectRatio(1f)) {
                    Text(percent, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                }
                BatteryLines(status, watts, time, null, Modifier.weight(1f).padding(start = 10.dp))
            }
            else -> Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxSize()) {
                Ring(level?.div(100f), color, Modifier.weight(1f).aspectRatio(1f, matchHeightConstraintsFirst = true)) {
                    Text(percent, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                }
                val temp = state.batteryTemp?.let { stringResource(R.string.panelDegrees, it.roundToInt()) }
                BatteryLines(status, watts, time, temp, Modifier.padding(top = 6.dp), center = true)
            }
        }
    }
}

@Composable
private fun BatteryLines(status: String, watts: String?, time: String?, temp: String?, modifier: Modifier, center: Boolean = false) {
    Column(horizontalAlignment = if (center) Alignment.CenterHorizontally else Alignment.Start, modifier = modifier) {
        Text(
            listOfNotNull(status, watts).joinToString(" · "),
            style = MaterialTheme.typography.labelLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        listOfNotNull(time, temp).forEach {
            Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
        }
    }
}

@Composable
private fun duration(minutes: Int): String = if (minutes >=
    60
) {
    stringResource(R.string.panelDurationHours, minutes / 60, minutes % 60)
} else {
    stringResource(R.string.panelDurationMinutes, minutes)
}

/** A gauge ring like the stats strip's, filled to [fraction], with [content] in the middle. */
@Composable
private fun Ring(fraction: Float?, color: Long, modifier: Modifier, content: @Composable () -> Unit) {
    val track = Color(LocalThorPalette.current.tileOff)
    Box(contentAlignment = Alignment.Center, modifier = modifier) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = (size.minDimension * RING_STROKE_FRACTION).coerceAtLeast(4.dp.toPx())
            val arc = Size(size.minDimension - stroke, size.minDimension - stroke)
            val topLeft = Offset((size.width - arc.width) / 2, (size.height - arc.height) / 2)
            drawArc(
                track,
                RING_START,
                RING_SWEEP,
                useCenter = false,
                topLeft = topLeft,
                size = arc,
                style = Stroke(stroke, cap = StrokeCap.Round),
            )
            val filled = (fraction ?: 0f).coerceIn(0f, 1f) * RING_SWEEP
            if (filled > 0f) {
                drawArc(
                    Color(color),
                    RING_START,
                    filled,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arc,
                    style = Stroke(stroke, cap = StrokeCap.Round),
                )
            }
        }
        content()
    }
}

// ---- Performance graph ----

@Composable
internal fun GraphCard(state: PanelUiState, modifier: Modifier) {
    val palette = LocalThorPalette.current
    val points = state.history
    val last = points.lastOrNull()
    val none = stringResource(R.string.panelNoValue)
    PanelCard(modifier) {
        Column(Modifier.fillMaxSize()) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Legend(palette.chart[0], last?.cpuLoad?.let { stringResource(R.string.panelGraphCpu, (it * 100).roundToInt()) } ?: none)
                Legend(palette.chart[1], last?.gpuLoad?.let { stringResource(R.string.panelGraphGpu, (it * 100).roundToInt()) } ?: none)
                Legend(palette.chart[2], last?.cpuTemp?.let { stringResource(R.string.panelDegrees, it.roundToInt()) } ?: none)
            }
            Canvas(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(top = 6.dp),
            ) {
                drawLine(Color(palette.tileOff), Offset(0f, size.height), Offset(size.width, size.height), strokeWidth = 1.dp.toPx())
                if (points.size >= 2) {
                    val end = points.last().time
                    line(points, end, Color(palette.chart[0])) { it.cpuLoad }
                    line(points, end, Color(palette.chart[1])) { it.gpuLoad }
                    line(points, end, Color(palette.chart[2])) { point -> point.cpuTemp?.let(StatsHistory::tempFraction) }
                }
            }
        }
    }
}

@Composable
private fun Legend(color: Long, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(Color(color)))
        Text(text, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(start = 4.dp), maxLines = 1)
    }
}

/** One line of the graph over the last minute up to [end]; a missing value breaks the line. */
private fun DrawScope.line(points: List<GraphPoint>, end: Long, color: Color, value: (GraphPoint) -> Float?) {
    val start = end - StatsHistory.WINDOW_MS
    val path = Path()
    var drawing = false
    points.forEach { point ->
        val fraction = value(point)?.coerceIn(0f, 1f)
        if (fraction == null) {
            drawing = false
            return@forEach
        }
        val x = (point.time - start).toFloat() / StatsHistory.WINDOW_MS * size.width
        val y = size.height * (1f - fraction)
        if (drawing) path.lineTo(x, y) else path.moveTo(x, y)
        drawing = true
    }
    drawPath(path, color, style = Stroke(2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
}

// ---- App shortcuts ----

@Composable
internal fun AppsCard(
    widget: PanelWidget,
    apps: Map<String, PanelApp>,
    onApp: (String, LaunchScreen) -> Unit,
    focus: FocusRequester?,
    modifier: Modifier,
) {
    PanelCard(modifier) {
        if (widget.apps.isEmpty()) return@PanelCard WidgetMessage(R.string.panelAppsEmpty)
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val perRow = max(1, (maxWidth / APP_SLOT_WIDTH).toInt())
            val rows = max(1, (maxHeight / APP_SLOT_HEIGHT).toInt())
            val labels = maxHeight / rows >= APP_SLOT_LABELLED_HEIGHT
            Column(verticalArrangement = Arrangement.SpaceEvenly, modifier = Modifier.fillMaxSize()) {
                widget.apps.take(perRow * rows).chunked(perRow).forEachIndexed { rowIndex, row ->
                    Row(horizontalArrangement = Arrangement.SpaceEvenly, modifier = Modifier.fillMaxWidth()) {
                        row.forEachIndexed { index, name ->
                            AppSlot(name, apps[name], labels, focus.takeIf { rowIndex == 0 && index == 0 }) { onApp(name, widget.appsOn) }
                        }
                        repeat(perRow - row.size) { Spacer(Modifier.width(APP_SLOT_WIDTH)) }
                    }
                }
            }
        }
    }
}

@Composable
private fun AppSlot(name: String, app: PanelApp?, label: Boolean, focus: FocusRequester?, onClick: () -> Unit) {
    val palette = LocalThorPalette.current
    val interactive = LocalPanelInteractive.current
    var focused by remember { mutableStateOf(false) }
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .width(APP_SLOT_WIDTH)
            .clip(RoundedCornerShape(10.dp))
            .focusFrom(focus.takeIf { interactive })
            .tap(interactive, { focused = it }, onClick)
            .padding(vertical = 2.dp),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(APP_ICON)
                .clip(CircleShape)
                .border(FOCUS_BORDER, if (focused) Color(palette.primary) else Color.Transparent, CircleShape),
        ) {
            if (app != null) {
                Image(rememberDrawablePainter(app.icon), contentDescription = null, modifier = Modifier.fillMaxSize().padding(2.dp))
            } else {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize().background(Color(palette.tileOff))) {
                    Text(name.substringAfterLast('.').take(1).uppercase(), style = MaterialTheme.typography.titleMedium)
                }
            }
        }
        if (label) {
            Text(
                text = app?.name ?: name.substringAfterLast('.'),
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
    }
}

// ---- Notes ----

/**
 * A note: the text typed in Thor Tools, and a drawing made right on the widget with a finger. The tools in the corner
 * pick the pen's colour, switch between pen and eraser (which takes away whole strokes) and clear the drawing (a second
 * tap within 3 s confirms).
 */
@Composable
internal fun NotesCard(
    id: String,
    note: PanelNote?,
    onDrawn: (String, List<NoteStroke>) -> Unit,
    focus: FocusRequester?,
    modifier: Modifier,
) {
    val palette = LocalThorPalette.current
    val interactive = LocalPanelInteractive.current
    val colors = listOf(palette.onSurface) + palette.chart
    var strokes by remember(id, note?.strokes) { mutableStateOf(note?.strokes.orEmpty()) }
    var drawing by remember { mutableStateOf(emptyList<NotePoint>()) }
    var color by remember { mutableIntStateOf(1) }
    var erasing by remember { mutableStateOf(false) }
    var confirmClear by remember { mutableStateOf(false) }
    val drawn by rememberUpdatedState(onDrawn)
    LaunchedEffect(confirmClear) {
        if (!confirmClear) return@LaunchedEffect
        delay(CLEAR_CONFIRM_MS)
        confirmClear = false
    }
    PanelCard(modifier) {
        Box(Modifier.fillMaxSize()) {
            val text = note?.text.orEmpty()
            if (text.isNotBlank()) {
                Text(
                    text,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.align(Alignment.TopStart),
                    overflow = TextOverflow.Ellipsis,
                )
            } else if (strokes.isEmpty() && drawing.isEmpty()) {
                WidgetMessage(R.string.panelNotesHint)
            }
            Canvas(
                Modifier
                    .fillMaxSize()
                    .then(
                        if (!interactive) {
                            Modifier
                        } else {
                            Modifier.pointerInput(id) {
                                fun at(position: Offset) = NotePoint(
                                    (position.x / size.width).coerceIn(0f, 1f),
                                    (position.y / size.height).coerceIn(0f, 1f),
                                )
                                awaitEachGesture {
                                    val down = awaitFirstDown()
                                    down.consume()
                                    if (erasing) {
                                        fun erase(position: Offset) {
                                            val point = at(position)
                                            strokes = NoteStrokes.erase(strokes, point.x, point.y, ERASER_RADIUS)
                                        }
                                        erase(down.position)
                                        drag(down.id) { change ->
                                            erase(change.position)
                                            change.consume()
                                        }
                                    } else {
                                        val points = mutableListOf(at(down.position))
                                        drawing = points.toList()
                                        drag(down.id) { change ->
                                            points += at(change.position)
                                            drawing = points.toList()
                                            change.consume()
                                        }
                                        strokes = NoteStrokes.add(strokes, NoteStroke(color, points.toList()))
                                        drawing = emptyList()
                                    }
                                    drawn(id, strokes)
                                }
                            }
                        },
                    ),
            ) {
                strokes.forEach { stroke(it.points, Color(colors[it.color.coerceIn(0, colors.lastIndex)])) }
                if (drawing.isNotEmpty()) stroke(drawing, Color(colors[color]))
            }
            if (interactive) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.align(Alignment.BottomEnd)) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.size(NOTE_TOOL).clip(CircleShape).background(Color(palette.tileOff)),
                    ) {
                        RoundButton(Icons.Rounded.Draw, NOTE_TOOL, focus, filled = !erasing) {
                            if (!erasing) color = (color + 1) % colors.size
                            erasing = false
                        }
                        Box(Modifier.align(Alignment.BottomEnd).size(10.dp).clip(CircleShape).background(Color(colors[color])))
                    }
                    RoundButton(Icons.AutoMirrored.Rounded.Backspace, NOTE_TOOL, filled = erasing) { erasing = true }
                    RoundButton(Icons.Rounded.DeleteSweep, NOTE_TOOL, filled = confirmClear) {
                        if (confirmClear) {
                            strokes = emptyList()
                            drawn(id, strokes)
                        }
                        confirmClear = !confirmClear
                    }
                }
            }
        }
    }
}

private fun DrawScope.stroke(points: List<NotePoint>, color: Color) {
    val width = NOTE_PEN.toPx()
    if (points.size == 1) {
        drawCircle(color, width / 2, Offset(points[0].x * size.width, points[0].y * size.height))
        return
    }
    val path = Path()
    points.forEachIndexed { index, point ->
        val x = point.x * size.width
        val y = point.y * size.height
        if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
    }
    drawPath(path, color, style = Stroke(width, cap = StrokeCap.Round, join = StrokeJoin.Round))
}

private const val RING_STROKE_FRACTION = 0.1f
private const val RING_START = 135f
private const val RING_SWEEP = 270f
private const val CLEAR_CONFIRM_MS = 3000L
private const val ERASER_RADIUS = 0.04f
private val MEDIA_BUTTON = 40.dp
private val APP_SLOT_WIDTH = 50.dp
private val APP_SLOT_HEIGHT = 48.dp
private val APP_SLOT_LABELLED_HEIGHT = 62.dp
private val APP_ICON = 40.dp
private val NOTE_TOOL = 34.dp
private val NOTE_PEN = 3.dp
