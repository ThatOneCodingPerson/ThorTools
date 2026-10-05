package io.github.thatonecodingperson.thortools.leds

import androidx.annotation.StringRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.BatteryChargingFull
import androidx.compose.material.icons.rounded.BrightnessMedium
import androidx.compose.material.icons.rounded.Colorize
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.WbIridescent
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import io.github.thatonecodingperson.thortools.R
import io.github.thatonecodingperson.thortools.display.ColorPickerDialog
import io.github.thatonecodingperson.thortools.hotkeys.NoteCard
import io.github.thatonecodingperson.thortools.ui.composables.CardColumns
import io.github.thatonecodingperson.thortools.ui.composables.CardHeader
import io.github.thatonecodingperson.thortools.ui.composables.CardRowBox
import io.github.thatonecodingperson.thortools.ui.composables.CardSwitchRow
import io.github.thatonecodingperson.thortools.ui.composables.ChoiceCard
import io.github.thatonecodingperson.thortools.ui.composables.LinkCard
import io.github.thatonecodingperson.thortools.ui.composables.SettingsCard
import io.github.thatonecodingperson.thortools.ui.composables.SubTopAppBar
import io.github.thatonecodingperson.thortools.ui.composables.SwitchCard
import io.github.thatonecodingperson.thortools.ui.composables.cardChipColors
import kotlin.math.roundToInt

/** The order of the look chips. */
private val MODES = listOf(
    LedMode.COLOUR,
    LedMode.BREATHE,
    LedMode.PULSE,
    LedMode.RAINBOW,
    LedMode.BATTERY,
    LedMode.OFF,
    LedMode.AYN,
)

/** The stick lights: a live picture of both rings, the look, its colours, brightness and speed. */
@Composable
fun LedScreen(viewModel: LedViewModel = hiltViewModel(), onProfiles: () -> Unit, onBack: () -> Unit) {
    val state by viewModel.uiState.collectAsState()
    LaunchedEffect(Unit) { viewModel.refresh() }
    // Sliders show their value at once and save when let go.
    var brightness by remember(state.look.brightness) { mutableFloatStateOf(state.look.brightness) }
    var speed by remember(state.look.speed) { mutableFloatStateOf(state.look.speed) }
    val look = state.look.copy(brightness = brightness, speed = speed)
    val mode = look.mode
    val usesColour = mode == LedMode.COLOUR || mode == LedMode.BREATHE || mode == LedMode.PULSE
    val animated = LedPlan.animated(look, state.charging, state.battery)

    Scaffold(topBar = { SubTopAppBar(title = R.string.ledTitle, onBack = onBack) }) { padding ->
        CardColumns(
            padding = padding,
            left = {
                if (!state.sticksFound) NoteCard(stringResource(R.string.ledNoSticks), warning = true)
                if (!state.serviceRunning) NoteCard(stringResource(R.string.ledServiceOff), warning = true)
                if (animated && state.serviceRunning && !state.helperRunning) {
                    NoteCard(stringResource(R.string.ledNeedsHelper), warning = true)
                }
                PreviewCard(state, look, onPick = if (usesColour && !state.same) viewModel::edit else null)
                LookCard(mode, viewModel::setMode)
                Text(
                    text = stringResource(R.string.ledHowItWorks),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            },
            right = {
                ColourCard(
                    state,
                    enabled = usesColour,
                    onPick = viewModel::pickColour,
                    onSame = viewModel::setSame,
                    onEdit = viewModel::edit,
                )
                SliderCard(
                    icon = Icons.Rounded.BrightnessMedium,
                    title = R.string.ledBrightness,
                    info = R.string.ledBrightnessInfo,
                    value = brightness,
                    enabled = mode != LedMode.AYN && mode != LedMode.OFF,
                    label = stringResource(R.string.ledPercent, (brightness * 100).roundToInt()),
                    onChange = { brightness = it },
                    onDone = { viewModel.setBrightness(brightness) },
                )
                SliderCard(
                    icon = Icons.Rounded.Speed,
                    title = R.string.ledSpeed,
                    info = R.string.ledSpeedInfo,
                    value = speed,
                    enabled = mode == LedMode.BREATHE || mode == LedMode.PULSE || mode == LedMode.RAINBOW || look.chargingBreathe,
                    label = stringResource(
                        when {
                            speed < SLOW_BELOW -> R.string.ledSpeedSlow
                            speed > FAST_ABOVE -> R.string.ledSpeedFast
                            else -> R.string.ledSpeedMedium
                        },
                    ),
                    onChange = { speed = it },
                    onDone = { viewModel.setSpeed(speed) },
                )
                SwitchCard(
                    icon = Icons.Rounded.BatteryChargingFull,
                    title = R.string.ledCharging,
                    info = R.string.ledChargingInfo,
                    checked = look.chargingBreathe,
                    enabled = mode != LedMode.AYN,
                    onChange = viewModel::setChargingBreathe,
                )
                LinkCard(
                    icon = Icons.Rounded.Apps,
                    title = stringResource(R.string.ledPerApp),
                    info = stringResource(R.string.ledPerAppInfo),
                    onClick = onProfiles,
                )
            },
        )
    }
}

/** Both sticks as seen from above, lit as the look lights them, moving when it moves. */
@Composable
private fun PreviewCard(state: LedUiModel, look: LedLook, onPick: ((LedSide) -> Unit)?) {
    val animated = LedPlan.animated(look, state.charging, state.battery)
    var time by remember { mutableLongStateOf(0L) }
    if (animated) {
        LaunchedEffect(look, state.charging, state.battery) {
            val start = withFrameMillis { it }
            while (true) withFrameMillis { time = it - start }
        }
    }
    val frame = LedPlan.frame(look, if (animated) time else 0L, state.battery, state.charging)
    SettingsCard {
        Row(
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        ) {
            StickRing(
                colour = frame?.left,
                brightness = frame?.brightness ?: 0,
                label = stringResource(R.string.ledLeftStick),
                selected = onPick != null && state.editing == LedSide.LEFT,
                onClick = onPick?.let { pick -> { pick(LedSide.LEFT) } },
            )
            StickRing(
                colour = frame?.right,
                brightness = frame?.brightness ?: 0,
                label = stringResource(R.string.ledRightStick),
                selected = onPick != null && state.editing == LedSide.RIGHT,
                onClick = onPick?.let { pick -> { pick(LedSide.RIGHT) } },
            )
        }
        val note = when {
            look.mode == LedMode.AYN -> R.string.ledPreviewAyn
            onPick != null -> R.string.ledPreviewPick
            else -> null
        }
        if (note != null) {
            Text(
                text = stringResource(note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.align(Alignment.CenterHorizontally),
            )
        }
    }
}

/** One stick: its cap and the light ring around it; null [colour] for a ring Thor Tools doesn't draw. */
@Composable
private fun StickRing(colour: Int?, brightness: Int, label: String, selected: Boolean, onClick: (() -> Unit)?) {
    val colors = MaterialTheme.colorScheme
    val lit = colour != null && colour != 0 && brightness > 0
    val ring = if (lit) rgb(colour).copy(alpha = DIM_ALPHA + (1 - DIM_ALPHA) * brightness / 255f) else colors.outlineVariant
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(8.dp),
    ) {
        Canvas(Modifier.size(RING_SIZE)) {
            val outer = size.minDimension / 2
            val stroke = outer * RING_WIDTH
            if (selected) drawCircle(colors.primary, radius = outer, style = Stroke(SELECT_WIDTH.toPx()))
            drawCircle(ring, radius = outer - SELECT_GAP.toPx() - stroke / 2, style = Stroke(stroke))
            drawCircle(colors.surfaceContainerHighest, radius = outer - SELECT_GAP.toPx() - stroke - CAP_GAP.toPx())
            drawCircle(colors.outlineVariant, radius = (outer - SELECT_GAP.toPx() - stroke) * CAP_DIMPLE, style = Stroke(1.dp.toPx()))
        }
        Text(
            label,
            style = MaterialTheme.typography.labelLarge,
            color = if (selected) colors.primary else colors.onSurfaceVariant,
            modifier = Modifier.padding(top = 6.dp),
        )
    }
}

@Composable
private fun LookCard(mode: LedMode, onMode: (LedMode) -> Unit) {
    ChoiceCard(
        icon = Icons.Rounded.WbIridescent,
        title = stringResource(R.string.ledLook),
        info = stringResource(modeInfo(mode)),
        options = MODES.map { it to stringResource(modeName(it)) },
        selected = mode,
        onSelect = onMode,
    )
}

/** The colour swatches, your own colour, and whether the sticks share it. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ColourCard(state: LedUiModel, enabled: Boolean, onPick: (Int) -> Unit, onSame: (Boolean) -> Unit, onEdit: (LedSide) -> Unit) {
    var picking by remember { mutableStateOf(false) }
    val current = if (state.same || state.editing == LedSide.LEFT) state.look.left else state.look.right
    val own = LedPreset.colours.none { it.look.left == current }
    SettingsCard(contentPadding = PaddingValues()) {
        CardHeader(
            Icons.Rounded.Palette,
            stringResource(R.string.ledColour),
            stringResource(if (enabled) R.string.ledColourInfo else R.string.ledColourNotUsed),
            enabled,
        )
        CardSwitchRow(title = stringResource(R.string.ledSame), checked = state.same, enabled = enabled, onChange = onSame)
        CardRowBox {
            if (!state.same) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(bottom = 8.dp)) {
                    LedSide.entries.forEach { side ->
                        FilterChip(
                            selected = side == state.editing,
                            enabled = enabled,
                            onClick = { onEdit(side) },
                            label = { Text(stringResource(if (side == LedSide.LEFT) R.string.ledLeftStick else R.string.ledRightStick)) },
                            colors = cardChipColors(),
                        )
                    }
                }
            }
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.alpha(if (enabled) 1f else DISABLED_ALPHA),
            ) {
                LedPreset.colours.forEach { preset ->
                    Swatch(
                        colour = rgb(preset.look.left),
                        selected = preset.look.left == current,
                        enabled = enabled,
                        label = stringResource(preset.textRes),
                        onClick = { onPick(preset.look.left) },
                    )
                }
                Swatch(
                    colour = if (own) rgb(current) else null,
                    selected = own,
                    enabled = enabled,
                    label = stringResource(R.string.ledCustom),
                    onClick = { picking = true },
                )
            }
        }
    }
    if (picking) {
        ColorPickerDialog(
            title = stringResource(R.string.ledCustom),
            initial = OPAQUE or current.toLong(),
            onPick = {
                onPick((it and RGB_MASK).toInt())
                picking = false
            },
            onDismiss = { picking = false },
        )
    }
}

/** A round colour to tap; without a [colour] it is the button for your own. */
@Composable
private fun Swatch(colour: Color?, selected: Boolean, enabled: Boolean, label: String, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(SWATCH_SIZE)
            .border(BorderStroke(SELECT_WIDTH, if (selected) colors.primary else Color.Transparent), CircleShape)
            .padding(SELECT_GAP)
            .background(colour ?: colors.surfaceContainerHighest, CircleShape)
            .border(BorderStroke(1.dp, colors.outline), CircleShape)
            .clickable(enabled = enabled, onClick = onClick)
            .semantics { contentDescription = label },
    ) {
        if (colour ==
            null
        ) {
            Icon(Icons.Rounded.Colorize, contentDescription = null, tint = colors.onSurfaceVariant, modifier = Modifier.size(18.dp))
        }
    }
}

@Composable
private fun SliderCard(
    icon: ImageVector,
    @StringRes title: Int,
    @StringRes info: Int,
    value: Float,
    enabled: Boolean,
    label: String,
    onChange: (Float) -> Unit,
    onDone: () -> Unit,
) {
    SettingsCard(contentPadding = PaddingValues()) {
        CardHeader(icon, stringResource(title), stringResource(info), enabled)
        CardRowBox {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Slider(
                    value = value,
                    onValueChange = onChange,
                    onValueChangeFinished = onDone,
                    enabled = enabled,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    label,
                    style = MaterialTheme.typography.labelLarge,
                    color = if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 12.dp),
                )
            }
        }
    }
}

@StringRes
fun modeName(mode: LedMode): Int = when (mode) {
    LedMode.AYN -> R.string.ledModeAyn
    LedMode.OFF -> R.string.ledModeOff
    LedMode.COLOUR -> R.string.ledModeColour
    LedMode.BREATHE -> R.string.ledModeBreathe
    LedMode.PULSE -> R.string.ledModePulse
    LedMode.RAINBOW -> R.string.ledModeRainbow
    LedMode.BATTERY -> R.string.ledModeBattery
}

@StringRes
private fun modeInfo(mode: LedMode): Int = when (mode) {
    LedMode.AYN -> R.string.ledModeAynInfo
    LedMode.OFF -> R.string.ledModeOffInfo
    LedMode.COLOUR -> R.string.ledModeColourInfo
    LedMode.BREATHE -> R.string.ledModeBreatheInfo
    LedMode.PULSE -> R.string.ledModePulseInfo
    LedMode.RAINBOW -> R.string.ledModeRainbowInfo
    LedMode.BATTERY -> R.string.ledModeBatteryInfo
}

private fun rgb(colour: Int) = Color(OPAQUE_INT or (colour and RGB_MASK.toInt()))

private val RING_SIZE = 112.dp
private val SWATCH_SIZE = 44.dp
private val SELECT_WIDTH = 3.dp
private val SELECT_GAP = 5.dp
private val CAP_GAP = 4.dp
private const val RING_WIDTH = 0.2f
private const val CAP_DIMPLE = 0.55f
private const val DIM_ALPHA = 0.3f
private const val DISABLED_ALPHA = 0.38f
private const val SLOW_BELOW = 0.34f
private const val FAST_ABOVE = 0.66f
private const val OPAQUE = 0xFF000000L
private const val OPAQUE_INT = -0x1000000
private const val RGB_MASK = 0xFFFFFFL
