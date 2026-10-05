package io.github.thatonecodingperson.thortools.panel

import android.os.SystemClock
import androidx.annotation.StringRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AirplanemodeActive
import androidx.compose.material.icons.rounded.Bluetooth
import androidx.compose.material.icons.rounded.DoNotDisturbOn
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material.icons.rounded.SignalCellularAlt
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material.icons.rounded.WifiOff
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.google.accompanist.drawablepainter.rememberDrawablePainter
import io.github.thatonecodingperson.thortools.R
import io.github.thatonecodingperson.thortools.actions.ThorAction
import io.github.thatonecodingperson.thortools.input.Screen
import io.github.thatonecodingperson.thortools.ui.theme.LocalThorPalette
import kotlinx.coroutines.delay
import java.text.DateFormat
import java.util.Date
import kotlin.math.max

/** Elapsed real time, once a second, for the widgets that count. */
@Composable
private fun ticking(): Long {
    val now by produceState(SystemClock.elapsedRealtime()) {
        while (true) {
            delay(TICK_MS)
            value = SystemClock.elapsedRealtime()
        }
    }
    return now
}

@Composable
private fun Small(text: String, modifier: Modifier = Modifier, color: Color = MaterialTheme.colorScheme.onSurfaceVariant) {
    Text(
        text,
        style = MaterialTheme.typography.labelSmall,
        color = color,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier,
    )
}

// ---- Play timer ----

@Composable
internal fun PlayTimeCard(front: FrontApp?, app: PanelApp?, size: WidgetSize, remind: Int, modifier: Modifier) {
    val now = ticking()
    PanelCard(modifier) {
        if (front == null) return@PanelCard WidgetMessage(R.string.panelPlayNone)
        val played = PlayTime.format(now - front.since)
        val name = app?.name ?: front.packageName.substringAfterLast('.')
        val reminder = if (remind > 0) stringResource(R.string.panelPlayBreak, remind) else null
        when {
            size.columns == 1 -> Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier.fillMaxSize(),
            ) {
                Text(played, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, maxLines = 1)
                Small(name)
            }
            size.rows == 1 -> Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxSize()) {
                AppIcon(app, front.packageName, Modifier.size(44.dp))
                Column(Modifier.weight(1f).padding(start = 12.dp)) {
                    Text(name, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(played, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, maxLines = 1)
                    if (reminder != null && size.columns >= 3) Small(reminder)
                }
            }
            else -> Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterVertically),
                modifier = Modifier.fillMaxSize(),
            ) {
                AppIcon(app, front.packageName, Modifier.size(52.dp))
                Text(name, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(played, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, maxLines = 1)
                if (reminder != null) Small(reminder)
            }
        }
    }
}

@Composable
private fun AppIcon(app: PanelApp?, packageName: String, modifier: Modifier) {
    val palette = LocalThorPalette.current
    if (app != null) {
        Image(rememberDrawablePainter(app.icon), contentDescription = null, modifier = modifier)
    } else {
        Box(contentAlignment = Alignment.Center, modifier = modifier.clip(RoundedCornerShape(50)).background(Color(palette.tileOff))) {
            Text(packageName.substringAfterLast('.').take(1).uppercase(), style = MaterialTheme.typography.titleMedium)
        }
    }
}

// ---- Controller status ----

/** One line per fact; a tap on a line runs the matching action (the screen line only says where the controller is). */
@Composable
internal fun ControllerStatusCard(
    state: PanelUiState,
    size: WidgetSize,
    onTile: (String) -> Unit,
    focus: FocusRequester?,
    modifier: Modifier,
) {
    val none = stringResource(R.string.panelNoValue)
    val lines = listOf(
        StatusLine(R.string.panelCtrlLayout, state.controllerStyle?.let { stringResource(it) } ?: none, ThorAction.TOGGLE_LAYOUT.id),
        StatusLine(R.string.panelCtrlTriggers, state.l2r2?.let { stringResource(it) } ?: none, ThorAction.CYCLE_L2R2.id),
        StatusLine(
            R.string.panelCtrlLock,
            stringResource(
                when (state.lockedTo) {
                    Screen.TOP -> R.string.panelLockTop
                    Screen.BOTTOM -> R.string.panelLockBottom
                    null -> R.string.panelLockOff
                },
            ),
            ThorAction.LOCK_CONTROLLER_HERE.id,
        ),
        StatusLine(
            R.string.panelCtrlScreen,
            when (state.controllerOnTop) {
                true -> stringResource(R.string.panelScreenTop)
                false -> stringResource(R.string.panelScreenBottom)
                null -> none
            },
            null,
        ),
    )
    PanelCard(modifier) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val fit = max(1, (maxHeight / LINE_HEIGHT).toInt())
            val columns = if (size.columns >= 4 && size.rows == 1) 2 else 1
            val shown = lines.take(fit * columns)
            Column(verticalArrangement = Arrangement.SpaceEvenly, modifier = Modifier.fillMaxSize()) {
                shown.chunked(columns).forEachIndexed { rowIndex, row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        row.forEachIndexed { index, line ->
                            StatusRow(line, focus.takeIf { rowIndex == 0 && index == 0 }, Modifier.weight(1f)) { line.action?.let(onTile) }
                        }
                    }
                }
            }
        }
    }
}

private class StatusLine(@StringRes val label: Int, val value: String, val action: String?)

@Composable
private fun StatusRow(line: StatusLine, focus: FocusRequester?, modifier: Modifier, onClick: () -> Unit) {
    val palette = LocalThorPalette.current
    val interactive = LocalPanelInteractive.current
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(8.dp)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .clip(shape)
            .border(FOCUS_BORDER, if (focused) Color(palette.primary) else Color.Transparent, shape)
            .then(
                if (line.action !=
                    null
                ) {
                    Modifier.focusFrom(focus.takeIf { interactive }).tap(interactive, { focused = it }, onClick)
                } else {
                    Modifier
                },
            )
            .padding(horizontal = 6.dp, vertical = 2.dp),
    ) {
        Small(stringResource(line.label), Modifier.weight(1f))
        Text(
            line.value,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary,
            maxLines = 1,
        )
    }
}

// ---- Clock, stopwatch and timer ----

@Composable
internal fun ClockCard(timer: PanelTimer, size: WidgetSize, onTimer: (TimerCommand) -> Unit, focus: FocusRequester?, modifier: Modifier) {
    val now = ticking()
    val clock = DateFormat.getTimeInstance(DateFormat.SHORT).format(Date())
    val date = DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date())
    val shown = timer.remaining(now) ?: timer.elapsed(now)
    PanelCard(modifier) {
        when {
            size.columns == 1 || (size.rows == 1 && size.columns <= 2) -> Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier.fillMaxSize(),
            ) {
                Text(clock, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, maxLines = 1)
                Small(date)
            }
            size.rows == 1 -> Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxSize()) {
                Column(Modifier.weight(1f)) {
                    Text(clock, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, maxLines = 1)
                    Small(date)
                }
                Text(
                    PanelTimer.format(shown),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(end = 10.dp),
                )
                TimerButtons(timer, onTimer, focus)
            }
            else -> Column(verticalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxSize()) {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(clock, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, maxLines = 1)
                    Small(date, Modifier.padding(start = 8.dp, bottom = 4.dp))
                }
                TimerChoices(timer, onTimer)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        PanelTimer.format(shown),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f),
                    )
                    TimerButtons(timer, onTimer, focus)
                }
            }
        }
    }
}

@Composable
private fun TimerButtons(timer: PanelTimer, onTimer: (TimerCommand) -> Unit, focus: FocusRequester?) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        RoundButton(if (timer.running) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, TIMER_BUTTON, focus, filled = true) {
            onTimer(if (timer.running) TimerCommand.Pause else TimerCommand.Start)
        }
        RoundButton(Icons.Rounded.RestartAlt, TIMER_BUTTON) { onTimer(TimerCommand.Reset) }
    }
}

/** Stopwatch or a timer of 5, 10, 15 or 30 minutes. */
@Composable
private fun TimerChoices(timer: PanelTimer, onTimer: (TimerCommand) -> Unit) {
    val palette = LocalThorPalette.current
    val interactive = LocalPanelInteractive.current
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        (listOf<Int?>(null) + TIMER_MINUTES).forEach { minutes ->
            val selected = timer.countdownMs == minutes?.let { it * 60_000L }
            var focused by remember { mutableStateOf(false) }
            val shape = RoundedCornerShape(50)
            Text(
                text = minutes?.let { stringResource(R.string.panelTimerMinutes, it) } ?: stringResource(R.string.panelStopwatch),
                style = MaterialTheme.typography.labelMedium,
                color = Color(if (selected) palette.onPrimary else palette.onSurface),
                maxLines = 1,
                modifier = Modifier
                    .clip(shape)
                    .background(Color(if (selected) palette.primary else palette.tileOff))
                    .border(FOCUS_BORDER, if (focused) Color(palette.primary) else Color.Transparent, shape)
                    .tap(interactive, { focused = it }) { onTimer(TimerCommand.Set(minutes)) }
                    .padding(horizontal = 10.dp, vertical = 4.dp),
            )
        }
    }
}

// ---- Screenshots ----

@Composable
internal fun ScreenshotsCard(
    state: PanelUiState,
    onOpen: (Screenshot) -> Unit,
    onAllow: () -> Unit,
    focus: FocusRequester?,
    modifier: Modifier,
) {
    PanelCard(modifier) {
        when {
            !state.screenshotsAllowed -> WidgetMessage(R.string.panelShotsNoAccess, R.string.panelMediaAllow, focus, onAllow)
            state.screenshots.isEmpty() -> WidgetMessage(R.string.panelShotsNone)
            else -> BoxWithConstraints(Modifier.fillMaxSize()) {
                val rows = max(1, (maxHeight / SHOT_MIN_HEIGHT).toInt())
                val height = (maxHeight - SHOT_GAP * (rows - 1)) / rows
                val perRow = max(1, ((maxWidth + SHOT_GAP) / (height * SHOT_RATIO + SHOT_GAP)).toInt())
                Column(verticalArrangement = Arrangement.spacedBy(SHOT_GAP)) {
                    state.screenshots.take(perRow * rows).chunked(perRow).forEachIndexed { rowIndex, row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(SHOT_GAP)) {
                            row.forEachIndexed { index, shot ->
                                Thumbnail(
                                    shot,
                                    focus.takeIf {
                                        rowIndex == 0 && index == 0
                                    },
                                    Modifier.height(height).aspectRatio(SHOT_RATIO),
                                ) {
                                    onOpen(shot)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Thumbnail(shot: Screenshot, focus: FocusRequester?, modifier: Modifier, onClick: () -> Unit) {
    val palette = LocalThorPalette.current
    val interactive = LocalPanelInteractive.current
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(8.dp)
    Box(
        modifier = modifier
            .clip(shape)
            .background(Color(palette.tileOff))
            .border(FOCUS_BORDER, if (focused) Color(palette.primary) else Color.Transparent, shape)
            .focusFrom(focus.takeIf { interactive })
            .tap(interactive, { focused = it }, onClick),
    ) {
        shot.thumbnail?.let {
            Image(it.asImageBitmap(), contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        }
    }
}

// ---- Storage and memory ----

@Composable
internal fun StorageCard(state: PanelUiState, size: WidgetSize, modifier: Modifier) {
    val palette = LocalThorPalette.current
    val storage = state.storage
    val ramUsed = state.stats.ramUsedGb
    val ramTotal = state.stats.ramTotalGb
    PanelCard(modifier) {
        val bars = listOfNotNull(
            storage?.let { Bar(R.string.panelStorage, it.usedGb, it.totalGb, palette.chart[0]) },
            if (ramUsed != null && ramTotal != null) Bar(R.string.panelMemory, ramUsed, ramTotal, palette.chart[3]) else null,
        )
        if (bars.isEmpty()) return@PanelCard WidgetMessage(R.string.panelNoValue)
        if (size.columns == 1) {
            Column(verticalArrangement = Arrangement.SpaceEvenly, modifier = Modifier.fillMaxSize()) {
                bars.forEach { bar ->
                    Small(stringResource(bar.label))
                    Text(
                        "${(bar.used / bar.total * 100).toInt()} %",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        } else {
            val wide = size.columns >= 4 && size.rows == 1
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp), modifier = Modifier.fillMaxSize()) {
                if (wide) {
                    bars.forEach { BarView(it, Modifier.weight(1f).fillMaxHeight()) }
                } else {
                    Column(verticalArrangement = Arrangement.SpaceEvenly, modifier = Modifier.fillMaxSize()) {
                        bars.forEach { BarView(it, Modifier.fillMaxWidth()) }
                    }
                }
            }
        }
    }
}

private class Bar(@StringRes val label: Int, val used: Float, val total: Float, val color: Long)

@Composable
private fun BarView(bar: Bar, modifier: Modifier) {
    val palette = LocalThorPalette.current
    val fraction = if (bar.total > 0) (bar.used / bar.total).coerceIn(0f, 1f) else 0f
    Column(verticalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterVertically), modifier = modifier) {
        Row {
            Small(stringResource(bar.label), Modifier.weight(1f))
            Small(stringResource(R.string.panelUsedOf, bar.used, bar.total), color = MaterialTheme.colorScheme.onSurface)
        }
        Box(Modifier.fillMaxWidth().height(BAR_HEIGHT).clip(RoundedCornerShape(50)).background(Color(palette.tileOff))) {
            Box(Modifier.fillMaxWidth(fraction).fillMaxHeight().background(Color(bar.color)))
        }
    }
}

// ---- Network ----

@Composable
internal fun NetworkCard(network: NetworkReading, size: WidgetSize, modifier: Modifier) {
    val palette = LocalThorPalette.current
    val icon = when (network.type) {
        NetworkType.WIFI -> Icons.Rounded.Wifi
        NetworkType.CELLULAR, NetworkType.ETHERNET -> Icons.Rounded.SignalCellularAlt
        NetworkType.NONE -> Icons.Rounded.WifiOff
    }
    val name = stringResource(
        when (network.type) {
            NetworkType.WIFI -> R.string.panelNetWifi
            NetworkType.CELLULAR -> R.string.panelNetMobile
            NetworkType.ETHERNET -> R.string.panelNetEthernet
            NetworkType.NONE -> R.string.panelNetNone
        },
    )
    val ping = network.pingMs?.let { stringResource(R.string.panelPing, it) } ?: stringResource(R.string.panelPingNone)
    val details = listOfNotNull(
        network.rssi?.let { stringResource(R.string.panelDbm, it) },
        network.linkMbps?.let { stringResource(R.string.panelMbps, it) },
    ).joinToString(" · ")
    PanelCard(modifier) {
        val content: @Composable () -> Unit = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, contentDescription = null, tint = Color(palette.primary), modifier = Modifier.size(22.dp))
                Text(name, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(start = 6.dp), maxLines = 1)
                network.signalLevel?.let { SignalBars(it, Modifier.padding(start = 8.dp)) }
            }
            if (size.columns > 1 && details.isNotEmpty()) Small(details)
            Text(
                ping,
                style = if (size.rows > 1) MaterialTheme.typography.titleLarge else MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
            )
        }
        Column(verticalArrangement = Arrangement.SpaceEvenly, modifier = Modifier.fillMaxSize()) { content() }
    }
}

@Composable
private fun SignalBars(level: Int, modifier: Modifier) {
    val palette = LocalThorPalette.current
    Row(horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.Bottom, modifier = modifier.height(14.dp)) {
        (1..4).forEach { bar ->
            Box(
                Modifier
                    .width(4.dp)
                    .fillMaxHeight(bar / 4f)
                    .background(Color(if (bar <= level) palette.primary else palette.tileOff), RoundedCornerShape(1.dp)),
            )
        }
    }
}

// ---- Quick toggles ----

@Composable
internal fun TogglesCard(
    toggles: Map<QuickToggle, Boolean>,
    size: WidgetSize,
    onToggle: (QuickToggle) -> Unit,
    focus: FocusRequester?,
    modifier: Modifier,
) {
    val items = listOf(
        Triple(QuickToggle.WIFI, Icons.Rounded.Wifi, R.string.panelToggleWifi),
        Triple(QuickToggle.BLUETOOTH, Icons.Rounded.Bluetooth, R.string.panelToggleBluetooth),
        Triple(QuickToggle.AIRPLANE, Icons.Rounded.AirplanemodeActive, R.string.panelToggleAirplane),
        Triple(QuickToggle.DND, Icons.Rounded.DoNotDisturbOn, R.string.panelToggleDnd),
    )
    val perRow = if (size.rows >= 2 && size.columns <= 2) 2 else 4
    val labels = size.columns >= 3 || size.rows >= 2
    PanelCard(modifier) {
        Column(verticalArrangement = Arrangement.SpaceEvenly, modifier = Modifier.fillMaxSize()) {
            items.chunked(perRow).forEachIndexed { rowIndex, row ->
                Row(horizontalArrangement = Arrangement.SpaceEvenly, modifier = Modifier.fillMaxWidth()) {
                    row.forEachIndexed { index, (toggle, icon, label) ->
                        ToggleButton(
                            icon,
                            label,
                            toggles[toggle] == true,
                            labels,
                            focus.takeIf {
                                rowIndex == 0 && index == 0
                            },
                        ) { onToggle(toggle) }
                    }
                }
            }
        }
    }
}

@Composable
private fun ToggleButton(
    icon: ImageVector,
    @StringRes label: Int,
    on: Boolean,
    showLabel: Boolean,
    focus: FocusRequester?,
    onClick: () -> Unit,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        RoundButton(icon, TOGGLE_BUTTON, focus, filled = on, onClick = onClick)
        if (showLabel) {
            Spacer(Modifier.height(2.dp))
            Text(
                stringResource(label),
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1,
                textAlign = TextAlign.Center,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

private const val TICK_MS = 1000L
private val TIMER_MINUTES = listOf(5, 10, 15, 30)
private val LINE_HEIGHT = 22.dp
private val TIMER_BUTTON = 36.dp
private val TOGGLE_BUTTON = 40.dp
private val SHOT_MIN_HEIGHT = 56.dp
private val SHOT_GAP = 6.dp
private const val SHOT_RATIO = 16f / 9f
private val BAR_HEIGHT = 8.dp
