package io.github.thatonecodingperson.thortools.lid

import androidx.activity.compose.BackHandler
import androidx.annotation.StringRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.AirplanemodeActive
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.BluetoothDisabled
import androidx.compose.material.icons.rounded.CleaningServices
import androidx.compose.material.icons.rounded.EnergySavingsLeaf
import androidx.compose.material.icons.rounded.Laptop
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.PauseCircle
import androidx.compose.material.icons.rounded.SmartButton
import androidx.compose.material.icons.rounded.SportsEsports
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.TouchApp
import androidx.compose.material.icons.rounded.WifiOff
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import io.github.thatonecodingperson.thortools.R
import io.github.thatonecodingperson.thortools.hotkeys.AppPicker
import io.github.thatonecodingperson.thortools.hotkeys.AppPickerMode
import io.github.thatonecodingperson.thortools.hotkeys.NoteCard
import io.github.thatonecodingperson.thortools.ui.composables.CardColumns
import io.github.thatonecodingperson.thortools.ui.composables.CardSection
import io.github.thatonecodingperson.thortools.ui.composables.SettingsCard
import io.github.thatonecodingperson.thortools.ui.composables.SubTopAppBar
import io.github.thatonecodingperson.thortools.ui.composables.SwitchCard

/** One switch of the sandbox: how it shows, and which choice it reads and sets. */
private class LidSwitch(
    val icon: ImageVector,
    @StringRes val title: Int,
    @StringRes val info: Int,
    val isOn: (LidChoices) -> Boolean,
    val turn: LidChoices.(Boolean) -> LidChoices,
)

private val SAVE_POWER = listOf(
    LidSwitch(Icons.Rounded.EnergySavingsLeaf, R.string.lidPowerSaving, R.string.lidPowerSavingInfo, { it.powerSaving }) {
        copy(powerSaving = it)
    },
    LidSwitch(Icons.Rounded.CleaningServices, R.string.lidCloseBackground, R.string.lidCloseBackgroundInfo, { it.closeBackground }) {
        copy(closeBackground = it)
    },
    LidSwitch(Icons.Rounded.PauseCircle, R.string.lidPauseMedia, R.string.lidPauseMediaInfo, { it.pauseMedia }) {
        copy(pauseMedia = it)
    },
    LidSwitch(Icons.Rounded.WifiOff, R.string.lidWifi, R.string.lidWifiInfo, { it.wifiOff }) {
        copy(wifiOff = it)
    },
    LidSwitch(Icons.Rounded.BluetoothDisabled, R.string.lidBluetooth, R.string.lidBluetoothInfo, { it.bluetoothOff }) {
        copy(bluetoothOff = it)
    },
    LidSwitch(Icons.Rounded.AirplanemodeActive, R.string.lidAirplane, R.string.lidAirplaneInfo, { it.airplane }) {
        copy(airplane = it)
    },
)

private val STAY_ASLEEP = listOf(
    LidSwitch(Icons.Rounded.SmartButton, R.string.lidMuteButtons, R.string.lidMuteButtonsInfo, { it.muteButtons }) {
        copy(muteButtons = it)
    },
    LidSwitch(Icons.Rounded.SportsEsports, R.string.lidMuteController, R.string.lidMuteControllerInfo, { it.muteController }) {
        copy(muteController = it)
    },
    LidSwitch(Icons.Rounded.TouchApp, R.string.lidMuteTouch, R.string.lidMuteTouchInfo, { it.muteTouch }) {
        copy(muteTouch = it)
    },
    LidSwitch(Icons.Rounded.Bedtime, R.string.lidBackToSleep, R.string.lidBackToSleepInfo, { it.backToSleep }) {
        copy(backToSleep = it)
    },
)

private val NOT_WHILE_MEDIA =
    LidSwitch(Icons.Rounded.MusicNote, R.string.lidNotWhileMedia, R.string.lidNotWhileMediaInfo, { it.notWhileMedia }) {
        copy(notWhileMedia = it)
    }

private val MASTER = LidSwitch(Icons.Rounded.Laptop, R.string.lidMaster, R.string.lidMasterInfo, { it.enabled }) {
    copy(enabled = it)
}

/** The lid sandbox: what happens when the lid closes, one card with one switch per thing, all off at first. */
@Composable
fun LidScreen(viewModel: LidViewModel = hiltViewModel(), onBack: () -> Unit) {
    val state by viewModel.uiState.collectAsState()
    LaunchedEffect(Unit) { viewModel.refresh() }
    val choices = state.choices
    val change = viewModel::change
    BackHandler(enabled = state.pickerOpen) { viewModel.closePicker() }
    if (state.pickerOpen) {
        return AppPicker(
            mode = AppPickerMode.LID_KEEP_BUTTONS,
            apps = state.apps,
            loaded = state.appsLoaded,
            checked = choices.keepButtonsFor,
            onPick = viewModel::pickApp,
            onClose = viewModel::closePicker,
        )
    }

    val top: @Composable () -> Unit = {
        LidSwitchCard(MASTER, choices, change, master = true)
        NoteCard(LidText.result(LocalContext.current, state.last), warning = state.last?.notRestored?.isNotEmpty() == true)
    }
    val savePower: @Composable () -> Unit = {
        CardSection(R.string.lidSavePower, R.string.lidSavePowerIntro) {
            SAVE_POWER.forEach { LidSwitchCard(it, choices, change) }
            DelayCard(choices, change)
            LidSwitchCard(NOT_WHILE_MEDIA, choices, change)
        }
    }
    val stayAsleep: @Composable () -> Unit = {
        CardSection(R.string.lidStayAsleep, R.string.lidStayAsleepIntro) {
            if (choices.enabled && choices.mutes.isNotEmpty() && !state.helperRunning) {
                NoteCard(stringResource(R.string.lidNeedsHelper), warning = true)
            }
            STAY_ASLEEP.forEach { switch ->
                LidSwitchCard(switch, choices, change)
                if (switch.title == R.string.lidMuteButtons &&
                    choices.muteButtons
                ) {
                    KeepButtonsCard(choices, state.keepButtonsNames, viewModel::openPicker)
                }
            }
            Text(
                text = stringResource(R.string.lidFootnote),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }

    Scaffold(topBar = { SubTopAppBar(title = R.string.lidTitle, onBack = onBack) }) { padding ->
        CardColumns(
            padding = padding,
            left = {
                top()
                savePower()
            },
            right = stayAsleep,
        )
    }
}

@Composable
private fun LidSwitchCard(switch: LidSwitch, choices: LidChoices, change: ((LidChoices) -> LidChoices) -> Unit, master: Boolean = false) =
    SwitchCard(switch.icon, switch.title, switch.info, switch.isOn(choices), enabled = master || choices.enabled) { value ->
        change { switch.turn(it, value) }
    }

/** How long after closing the Save power part runs. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DelayCard(choices: LidChoices, change: ((LidChoices) -> LidChoices) -> Unit) {
    val enabled = choices.enabled
    SettingsCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Rounded.Timer,
                contentDescription = null,
                tint = if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(28.dp),
            )
            Column(Modifier.weight(1f).padding(start = 12.dp)) {
                Text(stringResource(R.string.lidDelay), style = MaterialTheme.typography.titleMedium)
                Text(
                    stringResource(R.string.lidDelayInfo),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(start = 40.dp, top = 4.dp)) {
            LidPlan.DELAYS.forEach { minutes ->
                FilterChip(
                    selected = minutes == choices.delayMinutes,
                    enabled = enabled,
                    onClick = { change { it.copy(delayMinutes = minutes) } },
                    label = {
                        Text(if (minutes == 0) stringResource(R.string.lidDelayNone) else stringResource(R.string.lidDelayMinutes, minutes))
                    },
                )
            }
        }
    }
}

/** The apps that keep the AYN and volume buttons on; a tap opens the app list. */
@Composable
private fun KeepButtonsCard(choices: LidChoices, names: List<String>, onOpen: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Surface(
        onClick = onOpen,
        enabled = choices.enabled,
        shape = RoundedCornerShape(16.dp),
        color = colors.surfaceContainer,
        border = BorderStroke(1.dp, colors.outline),
        modifier = Modifier.fillMaxWidth().padding(start = 24.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
            Icon(Icons.Rounded.MusicNote, contentDescription = null, tint = colors.primary, modifier = Modifier.size(24.dp))
            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                Text(stringResource(R.string.lidKeepButtons), style = MaterialTheme.typography.titleSmall)
                Text(
                    text = if (names.isEmpty()) stringResource(R.string.lidKeepButtonsNone) else names.joinToString(", "),
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurfaceVariant,
                )
                Text(
                    stringResource(R.string.lidKeepButtonsInfo),
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurfaceVariant,
                )
            }
            Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = null, tint = colors.onSurfaceVariant)
        }
    }
}
