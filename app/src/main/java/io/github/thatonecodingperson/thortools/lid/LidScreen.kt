package io.github.thatonecodingperson.thortools.lid

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AirplanemodeActive
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.BluetoothDisabled
import androidx.compose.material.icons.rounded.CleaningServices
import androidx.compose.material.icons.rounded.EnergySavingsLeaf
import androidx.compose.material.icons.rounded.Laptop
import androidx.compose.material.icons.rounded.PauseCircle
import androidx.compose.material.icons.rounded.SmartButton
import androidx.compose.material.icons.rounded.SportsEsports
import androidx.compose.material.icons.rounded.TouchApp
import androidx.compose.material.icons.rounded.WifiOff
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import io.github.thatonecodingperson.thortools.R
import io.github.thatonecodingperson.thortools.hotkeys.NoteCard
import io.github.thatonecodingperson.thortools.ui.composables.CardColumns
import io.github.thatonecodingperson.thortools.ui.composables.CardSection
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

    val top: @Composable () -> Unit = {
        LidSwitchCard(MASTER, choices, change, master = true)
        NoteCard(LidText.result(LocalContext.current, state.last), warning = state.last?.notRestored?.isNotEmpty() == true)
    }
    val savePower: @Composable () -> Unit = {
        CardSection(R.string.lidSavePower, R.string.lidSavePowerIntro) {
            SAVE_POWER.forEach { LidSwitchCard(it, choices, change) }
        }
    }
    val stayAsleep: @Composable () -> Unit = {
        CardSection(R.string.lidStayAsleep, R.string.lidStayAsleepIntro) {
            if (choices.enabled && choices.mutes.isNotEmpty() && !state.helperRunning) {
                NoteCard(stringResource(R.string.lidNeedsHelper), warning = true)
            }
            STAY_ASLEEP.forEach { LidSwitchCard(it, choices, change) }
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
