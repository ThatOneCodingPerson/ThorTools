package io.github.thatonecodingperson.thortools.oled

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BlurLinear
import androidx.compose.material.icons.rounded.BlurOn
import androidx.compose.material.icons.rounded.Brightness4
import androidx.compose.material.icons.rounded.FilterCenterFocus
import androidx.compose.material.icons.rounded.OpenWith
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import io.github.thatonecodingperson.thortools.R
import io.github.thatonecodingperson.thortools.display.SliderRow
import io.github.thatonecodingperson.thortools.hotkeys.NoteCard
import io.github.thatonecodingperson.thortools.input.Screen
import io.github.thatonecodingperson.thortools.ui.composables.CardColumns
import io.github.thatonecodingperson.thortools.ui.composables.CardHeader
import io.github.thatonecodingperson.thortools.ui.composables.CardRow
import io.github.thatonecodingperson.thortools.ui.composables.CardRowBox
import io.github.thatonecodingperson.thortools.ui.composables.CardSection
import io.github.thatonecodingperson.thortools.ui.composables.CardSwitchRow
import io.github.thatonecodingperson.thortools.ui.composables.LinkCard
import io.github.thatonecodingperson.thortools.ui.composables.SettingsCard
import io.github.thatonecodingperson.thortools.ui.composables.SubTopAppBar
import io.github.thatonecodingperson.thortools.ui.composables.SwitchCard
import io.github.thatonecodingperson.thortools.ui.composables.cardChipColors
import kotlin.math.roundToInt

/**
 * OLED Safety (Beta): Thor Tools' own protection (pixel shifter, still areas, refresher, idle screens), and a link to
 * AYN's own ([AynScreen]).
 */
@Composable
fun OledScreen(viewModel: OledViewModel = hiltViewModel(), onAyn: () -> Unit, onBack: () -> Unit) {
    val state by viewModel.uiState.collectAsState()
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.refresh() }
    val choices = state.choices
    Scaffold(topBar = { SubTopAppBar(title = R.string.oledTitle, onBack = onBack) }) { padding ->
        CardColumns(
            padding = padding,
            left = {
                CardSection(R.string.oledOwn, R.string.oledOwnIntro) {
                    val needsHelper = choices.idle || choices.shift || choices.refreshAuto || choices.areas
                    if (!state.helperRunning && needsHelper) NoteCard(stringResource(R.string.oledNeedsHelper), warning = true)
                    ShifterCard(choices, viewModel::change)
                    StillAreasCard(choices, state.areasNow, viewModel::change)
                }
            },
            right = {
                CardSection(R.string.oledRest) {
                    RefresherCard(choices, viewModel)
                    IdleCard(choices, viewModel::change)
                }
                CardSection(R.string.oledAyn) {
                    LinkCard(
                        icon = Icons.Rounded.OpenWith,
                        title = stringResource(R.string.oledAynLink),
                        info = stringResource(R.string.oledAynLinkInfo),
                        value = aynValue(state),
                        onClick = onAyn,
                    )
                }
            },
        )
    }
}

/** AYN's own shifter and refresher, set as on AYN's page; handed over while Thor Tools' own run. */
@Composable
fun AynScreen(viewModel: OledViewModel = hiltViewModel(), onBack: () -> Unit) {
    val state by viewModel.uiState.collectAsState()
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.refresh() }
    Scaffold(topBar = { SubTopAppBar(title = R.string.oledAyn, onBack = onBack) }) { padding ->
        CardColumns(
            padding = padding,
            left = { AynCards(state, viewModel) },
            right = { NoteCard(stringResource(R.string.oledAynLimits)) },
        )
    }
}

@Composable
private fun aynValue(state: OledUiModel): String? {
    val ayn = state.ayn ?: return null
    val choices = state.choices
    return when {
        choices.shift && choices.refreshAuto -> stringResource(R.string.oledAynHandedOver)
        ayn.applied(choices.shift, choices.refreshAuto).let { it.shifter || it.refresher } -> stringResource(R.string.oledOn)
        else -> stringResource(R.string.oledOff)
    }
}

@Composable
private fun AynCards(state: OledUiModel, viewModel: OledViewModel) {
    CardSection(R.string.oledAyn, R.string.oledAynIntro) {
        val ayn = state.ayn
        when {
            state.aynUnreadable -> NoteCard(stringResource(R.string.oledAynUnreadable), warning = true)
            ayn == null -> Unit
            else -> {
                val ownShifter = state.choices.shift
                val aynShifter = ayn.shifter && !ownShifter
                SwitchCard(
                    Icons.Rounded.OpenWith,
                    R.string.oledShifter,
                    R.string.oledShifterInfo,
                    aynShifter,
                    enabled = !ownShifter,
                    rows = {
                        if (ownShifter) {
                            CardRowBox {
                                Text(stringResource(R.string.oledAynShifterHandedOver), style = MaterialTheme.typography.bodyMedium)
                            }
                        } else if (ayn.shifter) {
                            CardRowBox {
                                SliderRow(
                                    label = R.string.oledRadius,
                                    value = ayn.radius.toFloat(),
                                    range = AynProtection.RADIUS.first.toFloat()..AynProtection.RADIUS.last.toFloat(),
                                    valueText = stringResource(R.string.oledPixels, ayn.radius),
                                    steps = AynProtection.RADIUS.last - AynProtection.RADIUS.first - 1,
                                ) { value -> viewModel.changeAyn { it.copy(radius = value.roundToInt()) } }
                                SecondsRow(ayn.shiftAfterMs, AynProtection.SHIFT_AFTER_MS, SHIFT_STEP_MS) { ms ->
                                    viewModel.changeAyn { it.copy(shiftAfterMs = ms) }
                                }
                            }
                        }
                    },
                ) { on -> viewModel.changeAyn { it.copy(shifter = on) } }
                val ownRefresher = state.choices.refreshAuto
                SwitchCard(
                    Icons.Rounded.Brightness4,
                    R.string.oledRefresher,
                    R.string.oledRefresherInfo,
                    ayn.refresher && !ownRefresher,
                    enabled = !ownRefresher,
                    rows = {
                        if (ownRefresher) {
                            CardRowBox {
                                Text(stringResource(R.string.oledAynRefresherHandedOver), style = MaterialTheme.typography.bodyMedium)
                            }
                        } else if (ayn.refresher) {
                            CardRowBox {
                                SecondsRow(ayn.refreshAfterMs, AynProtection.REFRESH_AFTER_MS, REFRESH_STEP_MS) { ms ->
                                    viewModel.changeAyn { it.copy(refreshAfterMs = ms) }
                                }
                            }
                        }
                        CardRow(
                            title = stringResource(R.string.oledAynRefreshNow),
                            icon = Icons.Rounded.PlayArrow,
                            enabled = state.helperRunning,
                            onClick = viewModel::aynRefreshNow,
                        )
                    },
                ) { on -> viewModel.changeAyn { it.copy(refresher = on) } }
                if (state.aynPending) NoteCard(stringResource(R.string.oledAynPending), warning = true)
            }
        }
    }
}

@Composable
private fun ShifterCard(choices: OledChoices, change: ((OledChoices) -> OledChoices) -> Unit) {
    SwitchCard(Icons.Rounded.BlurOn, R.string.oledOwnShifter, R.string.oledOwnShifterInfo, choices.shift, rows = {
        if (choices.shift) {
            CardRowBox {
                Chips(ScreenChoice.entries, choices.shiftScreens, ::screensLabel) { screens -> change { it.copy(shiftScreens = screens) } }
                Chips(ShiftMode.entries, choices.shiftMode, ::modeLabel) { mode -> change { it.copy(shiftMode = mode) } }
                SliderRow(
                    label = R.string.oledRadius,
                    value = choices.shiftRadius.toFloat(),
                    range = OledChoices.SHIFT_RADIUS.first.toFloat()..OledChoices.SHIFT_RADIUS.last.toFloat(),
                    valueText = stringResource(R.string.oledPixels, choices.shiftRadius),
                    steps = OledChoices.SHIFT_RADIUS.last - OledChoices.SHIFT_RADIUS.first - 1,
                ) { value -> change { it.copy(shiftRadius = value.roundToInt()) } }
                PresetRow(R.string.oledMoveEvery, OledChoices.SHIFT_EVERY, choices.shiftEverySeconds) { seconds ->
                    change { it.copy(shiftEverySeconds = seconds) }
                }
                if (choices.shiftMode == ShiftMode.STILL) {
                    PresetRow(R.string.oledStillFor, OledChoices.SHIFT_STILL, choices.shiftStillSeconds) { seconds ->
                        change { it.copy(shiftStillSeconds = seconds) }
                    }
                }
            }
            if (choices.shiftMode == ShiftMode.STILL) {
                CardSwitchRow(
                    title = stringResource(R.string.oledShiftCenter),
                    info = stringResource(R.string.oledShiftCenterInfo),
                    checked = choices.shiftCenter,
                ) { on -> change { it.copy(shiftCenter = on) } }
            }
        }
    }) { on -> change { it.copy(shift = on) } }
}

@Composable
private fun StillAreasCard(choices: OledChoices, now: Map<Screen, Int>, change: ((OledChoices) -> OledChoices) -> Unit) {
    SwitchCard(Icons.Rounded.FilterCenterFocus, R.string.oledAreas, R.string.oledAreasInfo, choices.areas, rows = {
        CardRowBox { NoteCard(stringResource(R.string.oledAreasPreBeta), caution = true) }
        if (choices.areas) {
            CardRowBox {
                Text(areasNowText(now), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
            }
            CardRowBox {
                Chips(ScreenChoice.entries, choices.areaScreens, ::screensLabel) { screens -> change { it.copy(areaScreens = screens) } }
                Chips(AreaAction.entries, choices.areaAction, ::areaActionLabel) { action -> change { it.copy(areaAction = action) } }
                PresetRow(R.string.oledStillFor, OledChoices.AREA_STILL, choices.areaStillSeconds) { seconds ->
                    change { it.copy(areaStillSeconds = seconds) }
                }
                if (choices.areaAction.dim) {
                    SliderRow(
                        label = R.string.oledDimLabel,
                        value = choices.areaDimPercent.toFloat(),
                        range = OledChoices.AREA_DIM.first.toFloat()..OledChoices.AREA_DIM.last.toFloat(),
                        valueText = stringResource(R.string.oledPercent, choices.areaDimPercent),
                        steps = (OledChoices.AREA_DIM.last - OledChoices.AREA_DIM.first) / OledChoices.DIM_STEP - 1,
                    ) { value -> change { it.copy(areaDimPercent = (value / OledChoices.DIM_STEP).roundToInt() * OledChoices.DIM_STEP) } }
                }
                if (choices.areaAction.shift) {
                    SliderRow(
                        label = R.string.oledMoveBy,
                        value = choices.areaShiftPixels.toFloat(),
                        range = OledChoices.AREA_SHIFT.first.toFloat()..OledChoices.AREA_SHIFT.last.toFloat(),
                        valueText = stringResource(R.string.oledPixels, choices.areaShiftPixels),
                        steps = OledChoices.AREA_SHIFT.last - OledChoices.AREA_SHIFT.first - 1,
                    ) { value -> change { it.copy(areaShiftPixels = value.roundToInt()) } }
                    PresetRow(R.string.oledMoveEvery, OledChoices.AREA_EVERY, choices.areaEverySeconds) { seconds ->
                        change { it.copy(areaEverySeconds = seconds) }
                    }
                }
            }
            CardSwitchRow(
                title = stringResource(R.string.oledAreasExperimental),
                info = stringResource(R.string.oledAreasExperimentalInfo),
                checked = choices.areaExperimental,
            ) { on -> change { it.copy(areaExperimental = on) } }
        }
    }) { on -> change { it.copy(areas = on) } }
}

@Composable
private fun RefresherCard(choices: OledChoices, viewModel: OledViewModel) {
    val change = viewModel::change
    SettingsCard(contentPadding = PaddingValues()) {
        CardHeader(Icons.Rounded.BlurLinear, stringResource(R.string.oledRefresh), stringResource(R.string.oledRefreshInfo))
        CardRowBox {
            Chips(ScreenChoice.entries, choices.refreshScreens, ::screensLabel) { screens -> change { it.copy(refreshScreens = screens) } }
            Chips(RefreshPattern.entries, choices.refreshPattern, ::patternLabel) { pattern ->
                change { it.copy(refreshPattern = pattern) }
            }
            SliderRow(
                label = R.string.oledLength,
                value = choices.refreshSeconds.toFloat(),
                range = OledChoices.REFRESH_SECONDS.first.toFloat()..OledChoices.REFRESH_SECONDS.last.toFloat(),
                valueText = stringResource(R.string.oledSeconds, choices.refreshSeconds),
                steps = (OledChoices.REFRESH_SECONDS.last - OledChoices.REFRESH_SECONDS.first) / OledChoices.REFRESH_STEP - 1,
            ) { value ->
                change { it.copy(refreshSeconds = (value / OledChoices.REFRESH_STEP).roundToInt() * OledChoices.REFRESH_STEP) }
            }
        }
        CardSwitchRow(
            title = stringResource(R.string.oledRefreshAuto),
            info = stringResource(R.string.oledRefreshAutoInfo),
            checked = choices.refreshAuto,
        ) { on -> change { it.copy(refreshAuto = on) } }
        if (choices.refreshAuto) {
            CardRowBox {
                SliderRow(
                    label = R.string.oledAfter,
                    value = choices.refreshAfterMinutes.toFloat(),
                    range = OledChoices.MINUTES.first.toFloat()..OledChoices.MINUTES.last.toFloat(),
                    valueText = stringResource(R.string.oledMinutes, choices.refreshAfterMinutes),
                    steps = OledChoices.MINUTES.last - OledChoices.MINUTES.first - 1,
                ) { value -> change { it.copy(refreshAfterMinutes = value.roundToInt()) } }
            }
        }
        CardRow(title = stringResource(R.string.oledRefreshNow), icon = Icons.Rounded.Refresh, onClick = viewModel::refreshNow)
    }
}

@Composable
private fun IdleCard(choices: OledChoices, change: ((OledChoices) -> OledChoices) -> Unit) {
    SwitchCard(Icons.Rounded.Brightness4, R.string.oledIdle, R.string.oledIdleInfo, choices.idle, rows = {
        if (choices.idle) {
            CardRowBox {
                Chips(IdleLook.entries, choices.look, { if (it == IdleLook.DIM) R.string.oledDim else R.string.oledBlack }) { look ->
                    change { it.copy(look = look) }
                }
                Chips(ScreenChoice.entries, choices.idleScreens, ::screensLabel) { screens -> change { it.copy(idleScreens = screens) } }
                SliderRow(
                    label = R.string.oledAfter,
                    value = choices.idleMinutes.toFloat(),
                    range = OledChoices.MINUTES.first.toFloat()..OledChoices.MINUTES.last.toFloat(),
                    valueText = stringResource(R.string.oledMinutes, choices.idleMinutes),
                    steps = OledChoices.MINUTES.last - OledChoices.MINUTES.first - 1,
                ) { value -> change { it.copy(idleMinutes = value.roundToInt()) } }
                if (choices.look == IdleLook.DIM) {
                    SliderRow(
                        label = R.string.oledDimLabel,
                        value = choices.dimPercent.toFloat(),
                        range = OledChoices.DIM_PERCENT.first.toFloat()..OledChoices.DIM_PERCENT.last.toFloat(),
                        valueText = stringResource(R.string.oledPercent, choices.dimPercent),
                        steps = (OledChoices.DIM_PERCENT.last - OledChoices.DIM_PERCENT.first) / OledChoices.DIM_STEP - 1,
                    ) { value -> change { it.copy(dimPercent = (value / OledChoices.DIM_STEP).roundToInt() * OledChoices.DIM_STEP) } }
                }
            }
            CardSwitchRow(
                title = stringResource(R.string.oledNotWhileMedia),
                info = stringResource(R.string.oledNotWhileMediaInfo),
                checked = choices.notWhileMedia,
            ) { on -> change { it.copy(notWhileMedia = on) } }
        }
    }) { on -> change { it.copy(idle = on) } }
}

/** What still areas protect right now, per screen. */
@Composable
private fun areasNowText(now: Map<Screen, Int>): String {
    val top = now[Screen.TOP]
    val bottom = now[Screen.BOTTOM]
    return when {
        top != null && bottom != null -> stringResource(R.string.oledAreasNowBoth, top, bottom)
        top != null -> stringResource(R.string.oledAreasNowTop, top)
        bottom != null -> stringResource(R.string.oledAreasNowBottom, bottom)
        else -> stringResource(R.string.oledAreasNowNone)
    }
}

/** A slider over a list of second counts ([presets]), shown as seconds or minutes. */
@Composable
private fun PresetRow(@StringRes label: Int, presets: List<Int>, seconds: Int, onChange: (Int) -> Unit) {
    SliderRow(
        label = label,
        value = presets.indexOf(seconds).coerceAtLeast(0).toFloat(),
        range = 0f..(presets.size - 1).toFloat(),
        valueText = secondsText(seconds),
        steps = presets.size - 2,
    ) { value -> onChange(presets[value.roundToInt().coerceIn(presets.indices)]) }
}

/** A wait in seconds on a slider of [step] ms steps, shown as seconds or minutes and seconds. */
@Composable
private fun SecondsRow(ms: Int, range: IntRange, step: Int, onChange: (Int) -> Unit) {
    SliderRow(
        label = R.string.oledAfter,
        value = ms.toFloat(),
        range = range.first.toFloat()..range.last.toFloat(),
        valueText = secondsText(ms / 1_000),
        steps = (range.last - range.first) / step - 1,
    ) { value -> onChange((value / step).roundToInt() * step) }
}

@Composable
private fun secondsText(seconds: Int): String = when {
    seconds < 60 -> stringResource(R.string.oledSeconds, seconds)
    seconds % 60 == 0 -> stringResource(R.string.oledMinutes, seconds / 60)
    else -> stringResource(R.string.oledMinutesSeconds, seconds / 60, seconds % 60)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun <T> Chips(options: List<T>, selected: T, label: (T) -> Int, onSelect: (T) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { option ->
            FilterChip(
                selected = option == selected,
                onClick = { onSelect(option) },
                label = { Text(stringResource(label(option))) },
                colors = cardChipColors(),
            )
        }
    }
}

@StringRes
private fun areaActionLabel(action: AreaAction): Int = when (action) {
    AreaAction.DIM -> R.string.oledDim
    AreaAction.SHIFT -> R.string.oledAreaMove
    AreaAction.BOTH -> R.string.oledAreaDimAndMove
}

@StringRes
private fun modeLabel(mode: ShiftMode): Int = if (mode == ShiftMode.ALWAYS) R.string.oledShiftAlways else R.string.oledShiftWhenStill

@StringRes
private fun patternLabel(pattern: RefreshPattern): Int = when (pattern) {
    RefreshPattern.SWEEP -> R.string.oledPatternSweep
    RefreshPattern.NOISE -> R.string.oledPatternNoise
    RefreshPattern.COLOURS -> R.string.oledPatternColours
    RefreshPattern.INVERSE -> R.string.oledPatternInverse
}

@StringRes
private fun screensLabel(choice: ScreenChoice): Int = when (choice) {
    ScreenChoice.TOP -> R.string.oledTop
    ScreenChoice.BOTTOM -> R.string.oledBottom
    ScreenChoice.BOTH -> R.string.oledBoth
}

private const val SHIFT_STEP_MS = 1_000
private const val REFRESH_STEP_MS = 10_000
