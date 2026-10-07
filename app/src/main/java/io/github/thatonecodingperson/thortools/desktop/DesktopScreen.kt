package io.github.thatonecodingperson.thortools.desktop

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.Gamepad
import androidx.compose.material.icons.rounded.Mouse
import androidx.compose.material.icons.rounded.SportsEsports
import androidx.compose.material.icons.rounded.SwapVert
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.UnfoldMore
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import io.github.thatonecodingperson.thortools.R
import io.github.thatonecodingperson.thortools.hotkeys.AppPicker
import io.github.thatonecodingperson.thortools.hotkeys.Hotkey
import io.github.thatonecodingperson.thortools.hotkeys.KeyCap
import io.github.thatonecodingperson.thortools.hotkeys.NoteCard
import io.github.thatonecodingperson.thortools.hotkeys.triggerText
import io.github.thatonecodingperson.thortools.ui.composables.CardColumns
import io.github.thatonecodingperson.thortools.ui.composables.CardHeader
import io.github.thatonecodingperson.thortools.ui.composables.CardRow
import io.github.thatonecodingperson.thortools.ui.composables.CardRowBox
import io.github.thatonecodingperson.thortools.ui.composables.CardSwitchRow
import io.github.thatonecodingperson.thortools.ui.composables.SettingsCard
import io.github.thatonecodingperson.thortools.ui.composables.SubTopAppBar
import io.github.thatonecodingperson.thortools.ui.composables.SwitchCard
import io.github.thatonecodingperson.thortools.ui.composables.cardChipColors
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Desktop controls: the controller as a mouse and keys in apps, like a Steam Controller in desktop mode, switched on and
 * off by the app on the controller's screen. Every button, stick and speed can be changed.
 */
@Composable
fun DesktopScreen(viewModel: DesktopViewModel = hiltViewModel(), onBack: () -> Unit) {
    val state by viewModel.uiState.collectAsState()
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.refresh() }
    val side = state.picker
    if (side != null) {
        val other = if (side == DesktopApps.Side.ONLY_IN) DesktopApps.Side.NEVER_IN else DesktopApps.Side.ONLY_IN
        val otherReason = stringResource(
            if (other == DesktopApps.Side.NEVER_IN) R.string.desktopOnNeverList else R.string.desktopOnOnlyList,
        )
        AppPicker(
            title = stringResource(if (side == DesktopApps.Side.ONLY_IN) R.string.desktopOnlyIn else R.string.desktopNeverIn),
            info = stringResource(if (side == DesktopApps.Side.ONLY_IN) R.string.desktopOnlyInInfo else R.string.desktopNeverInInfo),
            apps = state.appList,
            loaded = state.appsLoaded,
            checked = state.apps.list(side),
            blocked = state.apps.list(other).associateWith { otherReason },
            onPick = viewModel::toggleApp,
            onClose = viewModel::closePicker,
        )
        return
    }
    Scaffold(topBar = { SubTopAppBar(title = R.string.desktopTitle, onBack = onBack) }) { padding ->
        CardColumns(
            padding = padding,
            left = {
                SwitchCard(
                    icon = Icons.Rounded.Mouse,
                    title = R.string.desktopTitle,
                    info = R.string.desktopSwitchInfo,
                    checked = state.enabled,
                    onChange = viewModel::setEnabled,
                )
                if (state.aynMouseOn) {
                    NoteCard(
                        stringResource(R.string.desktopAynMouseOn),
                        caution = true,
                        actionLabel = stringResource(R.string.desktopAynMouseOff),
                        onAction = viewModel::turnOffAynMouse,
                    )
                }
                ButtonsCard(state.layout, state.selected, viewModel::select)
                JobCard(state.layout, state.selected, state.hotkeys, viewModel)
                WhereCard(state, viewModel)
                Text(
                    stringResource(R.string.desktopRule),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            },
            right = {
                PresetsCard(state.layout, viewModel)
                SticksCard(state.layout, viewModel)
                PointerCard(state.layout, state.androidSpeed, viewModel)
                ScrollCard(state.layout, viewModel)
                TriggersCard(state.layout, viewModel)
            },
        )
    }
}

/** The Thor's buttons as keys, each with its job under it; a tap chooses the button whose job is changed below. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ButtonsCard(layout: DesktopLayout, selected: DesktopControl, onSelect: (DesktopControl) -> Unit) {
    SettingsCard(contentPadding = PaddingValues()) {
        CardHeader(Icons.Rounded.SportsEsports, stringResource(R.string.desktopButtons), stringResource(R.string.desktopButtonsInfo))
        CardRowBox {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                BUTTON_GROUPS.forEach { (label, controls) ->
                    Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh) {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(8.dp)) {
                            Text(
                                stringResource(label),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                controls.forEach { control ->
                                    ButtonKey(layout, control, control == selected) { onSelect(control) }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private val BUTTON_GROUPS = listOf(
    R.string.buttonGroupFace to listOf(DesktopControl.A, DesktopControl.B, DesktopControl.X, DesktopControl.Y),
    R.string.buttonGroupShoulders to listOf(DesktopControl.L1, DesktopControl.R1, DesktopControl.L2, DesktopControl.R2),
    R.string.buttonGroupSticks to listOf(DesktopControl.L3, DesktopControl.R3),
    R.string.buttonGroupMenu to listOf(DesktopControl.SELECT, DesktopControl.START),
)

/** One button: the key, and what it does under it ("Slow" for the slow-down button with no job). */
@Composable
private fun ButtonKey(layout: DesktopLayout, control: DesktopControl, selected: Boolean, onClick: () -> Unit) {
    val job = layout.job(control)
    val text = when {
        job != DesktopJob.NONE -> stringResource(job.label)
        control == layout.precision -> stringResource(R.string.desktopPrecisionShort)
        else -> stringResource(R.string.desktopJobNoneShort)
    }
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.widthIn(min = KEY_WIDTH)) {
        KeyCap(control.pad, selected = selected, onClick = onClick)
        Text(
            text,
            style = MaterialTheme.typography.labelSmall,
            color = if (control in layout.taken) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            maxLines = 2,
            modifier = Modifier.padding(top = 2.dp),
        )
    }
}

private val KEY_WIDTH = 64.dp

/** The chosen button's job, picked from the jobs in their groups, and whether it slows the pointer down. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun JobCard(layout: DesktopLayout, control: DesktopControl, hotkeys: List<Hotkey>, viewModel: DesktopViewModel) {
    val current = layout.job(control)
    val starts = hotkeys.filter { it.second != null && it.button == control.pad }
    val after = hotkeys.filter { it.second == control.pad }
    val buttonName = stringResource(control.pad.label)
    SettingsCard(contentPadding = PaddingValues()) {
        CardHeader(
            Icons.Rounded.Gamepad,
            stringResource(R.string.desktopJobFor, stringResource(control.pad.label)),
            stringResource(R.string.desktopJobInfo),
        )
        CardRowBox {
            DesktopJob.Group.entries.forEach { group ->
                Text(
                    stringResource(group.label),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 6.dp, bottom = 2.dp),
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    DesktopJob.entries.filter { it.group == group }.forEach { job ->
                        FilterChip(
                            selected = job == current,
                            onClick = { viewModel.setJob(job) },
                            label = { Text(stringResource(job.label)) },
                            colors = cardChipColors(),
                        )
                    }
                }
            }
            val note = when {
                control == DesktopControl.L2 || control == DesktopControl.R2 -> R.string.desktopTriggerNote
                control == DesktopControl.START && layout.holdStartSwitch -> R.string.desktopStartNote
                current == DesktopJob.KEYBOARD -> R.string.desktopKeyboardNote
                else -> null
            }
            if (note != null) {
                Text(
                    stringResource(note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
            if (starts.isNotEmpty()) {
                val combos = starts.take(SHOWN_HOTKEYS).map { triggerText(it.button, it.second, it.press) }.joinToString("; ")
                HotkeyLine(stringResource(R.string.desktopStartsHotkeys, buttonName, combos))
            }
            if (after.isNotEmpty()) {
                val combos = after.take(SHOWN_HOTKEYS).map { triggerText(it.button, it.second, it.press) }.joinToString("; ")
                HotkeyLine(stringResource(R.string.desktopInHotkeys, buttonName, combos))
            }
        }
        // Slowing down needs the button held, which a button that starts hotkeys or switches with a hold can't give.
        val canSlow = starts.isEmpty() && !(control == DesktopControl.START && layout.holdStartSwitch)
        CardSwitchRow(
            title = stringResource(R.string.desktopPrecision),
            info = stringResource(if (canSlow) R.string.desktopPrecisionInfo else R.string.desktopPrecisionNotHere),
            checked = layout.precision == control,
            enabled = canSlow || layout.precision == control,
            onChange = viewModel::setPrecision,
        )
    }
}

/** Where a hotkey and this button meet: the hotkey comes first. */
@Composable
private fun HotkeyLine(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 8.dp),
    )
}

private const val SHOWN_HOTKEYS = 3

@Composable
private fun WhereCard(state: DesktopUiModel, viewModel: DesktopViewModel) {
    val onlyIn = state.apps.onlyIn
    val neverIn = state.apps.neverIn
    SettingsCard(contentPadding = PaddingValues()) {
        CardHeader(
            Icons.Rounded.Apps,
            stringResource(R.string.desktopWhere),
            stringResource(if (onlyIn.isEmpty()) R.string.desktopWhereEverywhere else R.string.desktopWhereOnly),
        )
        CardRow(
            title = stringResource(R.string.desktopOnlyIn),
            value = if (onlyIn.isEmpty()) stringResource(R.string.desktopEveryApp) else appCount(onlyIn.size),
            onClick = { viewModel.openPicker(DesktopApps.Side.ONLY_IN) },
        )
        CardRow(
            title = stringResource(R.string.desktopNeverIn),
            info = stringResource(R.string.desktopNeverInHint),
            value = if (neverIn.isEmpty()) stringResource(R.string.desktopNone) else appCount(neverIn.size),
            onClick = { viewModel.openPicker(DesktopApps.Side.NEVER_IN) },
        )
        CardSwitchRow(
            title = stringResource(R.string.desktopHoldStart),
            info = stringResource(R.string.desktopHoldStartInfo),
            checked = state.layout.holdStartSwitch,
            onChange = viewModel::setHoldStart,
        )
    }
}

@Composable
private fun appCount(count: Int): String =
    if (count == 1) stringResource(R.string.desktopOneApp) else stringResource(R.string.desktopApps, count)

/** Ready-made buttons and sticks; the speeds stay. Reset puts everything back. */
@Composable
private fun PresetsCard(layout: DesktopLayout, viewModel: DesktopViewModel) {
    val current = layout.preset
    SettingsCard(contentPadding = PaddingValues()) {
        CardHeader(
            Icons.Rounded.Tune,
            stringResource(R.string.desktopPresets),
            stringResource(
                R.string.desktopPresetsInfo,
                current?.let { stringResource(it.label) } ?: stringResource(R.string.desktopPresetCustom),
            ),
        )
        DesktopPreset.entries.forEach { preset ->
            CardRow(
                title = stringResource(preset.label),
                info = stringResource(preset.info),
                value = if (preset == current) stringResource(R.string.desktopPresetInUse) else null,
                onClick = { viewModel.usePreset(preset) },
            )
        }
        CardRow(
            title = stringResource(R.string.desktopResetAll),
            info = stringResource(R.string.desktopResetAllInfo),
            danger = true,
            enabled = layout != DesktopLayout.DEFAULT,
            onClick = viewModel::resetAll,
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SticksCard(layout: DesktopLayout, viewModel: DesktopViewModel) {
    SettingsCard(contentPadding = PaddingValues()) {
        CardHeader(Icons.Rounded.UnfoldMore, stringResource(R.string.desktopSticks), stringResource(R.string.desktopSticksInfo))
        CardRowBox {
            StickChoice(R.string.desktopLeftStick, layout.leftStick, viewModel::setLeftStick)
            StickChoice(R.string.desktopRightStick, layout.rightStick, viewModel::setRightStick)
            Text(
                stringResource(R.string.desktopDpadNote),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun StickChoice(title: Int, role: StickRole, onPick: (StickRole) -> Unit) {
    Text(
        stringResource(title),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 6.dp, bottom = 2.dp),
    )
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        StickRole.entries.forEach { option ->
            FilterChip(
                selected = option == role,
                onClick = { onPick(option) },
                label = { Text(stringResource(option.label)) },
                colors = cardChipColors(),
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PointerCard(layout: DesktopLayout, androidSpeed: Int?, viewModel: DesktopViewModel) {
    SettingsCard(contentPadding = PaddingValues()) {
        CardHeader(Icons.Rounded.Mouse, stringResource(R.string.desktopPointer), stringResource(R.string.desktopPointerInfo))
        CardRowBox {
            SliderLine(
                title = stringResource(R.string.desktopSpeed),
                value = layout.speed,
                text = times(layout.speed),
                range = DesktopLayout.MIN_SPEED..DesktopLayout.MAX_SPEED,
                onMove = viewModel::moveSpeed,
                onDone = viewModel::commit,
            )
            Text(
                stringResource(R.string.desktopAcceleration),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(top = 6.dp, bottom = 2.dp),
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Acceleration.entries.forEach { option ->
                    FilterChip(
                        selected = option == layout.acceleration,
                        onClick = { viewModel.setAcceleration(option) },
                        label = { Text(stringResource(option.label)) },
                        colors = cardChipColors(),
                    )
                }
            }
            SliderLine(
                title = stringResource(R.string.desktopDeadZone),
                value = layout.deadZone,
                text = percent(layout.deadZone),
                range = DesktopLayout.MIN_DEAD_ZONE..DesktopLayout.MAX_DEAD_ZONE,
                onMove = viewModel::moveDeadZone,
                onDone = viewModel::commit,
            )
        }
        CardSwitchRow(
            title = stringResource(R.string.desktopInvertY),
            checked = layout.invertY,
            onChange = viewModel::setInvertY,
        )
        CardRow(
            title = stringResource(R.string.desktopPrecisionRow),
            info = stringResource(R.string.desktopPrecisionRowInfo),
            value = layout.precision?.let { stringResource(it.pad.label) } ?: stringResource(R.string.desktopNone),
            enabled = layout.precision != null,
            onClick = { layout.precision?.let(viewModel::select) },
        )
        if (androidSpeed != null && androidSpeed != 0) {
            CardRow(
                title = stringResource(R.string.desktopAndroidSpeed),
                info = stringResource(if (androidSpeed < 0) R.string.desktopAndroidSlower else R.string.desktopAndroidFaster),
                value = if (androidSpeed > 0) "+$androidSpeed" else "−${-androidSpeed}",
                onClick = viewModel::resetAndroidSpeed,
            )
        }
    }
}

@Composable
private fun ScrollCard(layout: DesktopLayout, viewModel: DesktopViewModel) {
    val scrolls = layout.leftStick == StickRole.SCROLL || layout.rightStick == StickRole.SCROLL
    SettingsCard(contentPadding = PaddingValues()) {
        CardHeader(
            Icons.Rounded.SwapVert,
            stringResource(R.string.desktopScroll),
            stringResource(if (scrolls) R.string.desktopScrollInfo else R.string.desktopScrollNoStick),
        )
        CardRowBox {
            SliderLine(
                title = stringResource(R.string.desktopScrollSpeed),
                value = layout.scrollSpeed,
                text = times(layout.scrollSpeed),
                range = DesktopLayout.MIN_SPEED..DesktopLayout.MAX_SPEED,
                onMove = viewModel::moveScrollSpeed,
                onDone = viewModel::commit,
            )
        }
        CardSwitchRow(
            title = stringResource(R.string.desktopNatural),
            info = stringResource(R.string.desktopNaturalInfo),
            checked = layout.naturalScroll,
            onChange = viewModel::setNaturalScroll,
        )
        CardSwitchRow(
            title = stringResource(R.string.desktopHorizontal),
            info = stringResource(R.string.desktopHorizontalInfo),
            checked = layout.horizontalScroll,
            onChange = viewModel::setHorizontalScroll,
        )
    }
}

@Composable
private fun TriggersCard(layout: DesktopLayout, viewModel: DesktopViewModel) {
    SettingsCard(contentPadding = PaddingValues()) {
        CardHeader(Icons.Rounded.Gamepad, stringResource(R.string.desktopTriggers), stringResource(R.string.desktopTriggersInfo))
        CardRowBox {
            SliderLine(
                title = stringResource(R.string.desktopThreshold),
                value = layout.triggerThreshold,
                text = percent(layout.triggerThreshold),
                range = DesktopLayout.MIN_THRESHOLD..DesktopLayout.MAX_THRESHOLD,
                onMove = viewModel::moveThreshold,
                onDone = viewModel::commit,
            )
        }
    }
}

/** A slider with its name and value above it; [onDone] when it is let go. */
@Composable
private fun SliderLine(
    title: String,
    value: Float,
    text: String,
    range: ClosedFloatingPointRange<Float>,
    onMove: (Float) -> Unit,
    onDone: () -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 6.dp)) {
        Text(
            title,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.weight(1f),
        )
        Text(text, style = MaterialTheme.typography.labelLarge)
    }
    Slider(value = value, onValueChange = onMove, onValueChangeFinished = onDone, valueRange = range)
}

private fun times(value: Float): String = String.format(Locale.getDefault(), "%.2f×", value)

private fun percent(value: Float): String = "${(value * 100).roundToInt()}%"
