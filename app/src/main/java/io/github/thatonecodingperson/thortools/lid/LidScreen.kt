package io.github.thatonecodingperson.thortools.lid

import android.content.Intent
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.AirplanemodeActive
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.BluetoothDisabled
import androidx.compose.material.icons.rounded.CleaningServices
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.EnergySavingsLeaf
import androidx.compose.material.icons.rounded.Laptop
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.NightsStay
import androidx.compose.material.icons.rounded.PauseCircle
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.WifiOff
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import io.github.thatonecodingperson.thortools.R
import io.github.thatonecodingperson.thortools.hotkeys.NoteCard
import io.github.thatonecodingperson.thortools.ui.composables.CardColumns
import io.github.thatonecodingperson.thortools.ui.composables.CardHeader
import io.github.thatonecodingperson.thortools.ui.composables.CardRow
import io.github.thatonecodingperson.thortools.ui.composables.CardRowBox
import io.github.thatonecodingperson.thortools.ui.composables.CardSection
import io.github.thatonecodingperson.thortools.ui.composables.SettingsCard
import io.github.thatonecodingperson.thortools.ui.composables.SubTopAppBar
import io.github.thatonecodingperson.thortools.ui.composables.SwitchCard
import io.github.thatonecodingperson.thortools.ui.composables.cardChipColors
import kotlin.math.roundToInt

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

private val BACK_TO_SLEEP =
    LidSwitch(Icons.Rounded.Bedtime, R.string.lidBackToSleep, R.string.lidBackToSleepInfo, { it.backToSleep }) {
        copy(backToSleep = it)
    }

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
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.refresh() }
    val choices = state.choices
    val change = viewModel::change

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
            // How long a wake lasts belongs to the switch's card, shown while it is on.
            val rows: (@Composable ColumnScope.() -> Unit)? = if (choices.backToSleep) {
                { SleepWaitRows(choices, change) }
            } else {
                null
            }
            LidSwitchCard(BACK_TO_SLEEP, choices, change, rows = rows)
            Text(
                text = stringResource(R.string.lidFootnote),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        CardSection(R.string.lidMore) { SleepManagerCard(state) }
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
private fun LidSwitchCard(
    switch: LidSwitch,
    choices: LidChoices,
    change: ((LidChoices) -> LidChoices) -> Unit,
    master: Boolean = false,
    rows: (@Composable ColumnScope.() -> Unit)? = null,
) = SwitchCard(switch.icon, switch.title, switch.info, switch.isOn(choices), enabled = master || choices.enabled, rows = rows) { value ->
    change { switch.turn(it, value) }
}

/** Back to sleep's wait: seconds or minutes, and how many. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SleepWaitRows(choices: LidChoices, change: ((LidChoices) -> LidChoices) -> Unit) {
    val enabled = choices.enabled
    val unit = choices.sleepUnit
    val amount = unit.clamp(choices.sleepWait)
    CardRowBox {
        Text(stringResource(R.string.lidSleepWait), style = MaterialTheme.typography.bodyMedium)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 4.dp)) {
            WaitUnit.entries.forEach { option ->
                FilterChip(
                    selected = option == unit,
                    enabled = enabled,
                    onClick = { change { LidPlan.withUnit(it, option) } },
                    label = { Text(stringResource(if (option == WaitUnit.SECONDS) R.string.lidUnitSeconds else R.string.lidUnitMinutes)) },
                    colors = cardChipColors(),
                )
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Slider(
                value = amount.toFloat(),
                onValueChange = { value -> change { it.copy(sleepWait = unit.clamp(value.roundToInt())) } },
                valueRange = unit.range.first.toFloat()..unit.range.last.toFloat(),
                steps = (unit.range.last - unit.range.first) / unit.step - 1,
                enabled = enabled,
                modifier = Modifier.weight(1f),
            )
            Text(
                stringResource(if (unit == WaitUnit.SECONDS) R.string.lidSeconds else R.string.lidDelayMinutes, amount),
                style = MaterialTheme.typography.labelLarge,
                color = if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 12.dp),
            )
        }
    }
}

/** Points to SleepManager for more than this basic lid handling: open it, or get it from GitHub or through Obtainium. */
@Composable
private fun SleepManagerCard(state: LidUiModel) {
    val context = LocalContext.current
    val links = LocalUriHandler.current
    SettingsCard(contentPadding = PaddingValues()) {
        CardHeader(Icons.Rounded.NightsStay, stringResource(R.string.lidMoreTitle), stringResource(R.string.lidMoreInfo))
        if (state.sleepManagerInstalled) {
            CardRow(
                title = stringResource(R.string.lidMoreOpen),
                info = stringResource(R.string.lidMoreInstalledInfo),
                icon = Icons.AutoMirrored.Rounded.OpenInNew,
            ) { runCatching { context.startActivity(SleepManagerApp.launchIntent().addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } }
        } else {
            CardRow(
                title = stringResource(R.string.lidMoreGithub),
                info = stringResource(R.string.lidMoreGithubInfo),
                icon = Icons.AutoMirrored.Rounded.OpenInNew,
            ) { runCatching { links.openUri(SleepManagerApp.PAGE) } }
            if (state.obtainiumInstalled) {
                CardRow(
                    title = stringResource(R.string.lidMoreObtainium),
                    info = stringResource(R.string.lidMoreObtainiumInfo),
                    icon = Icons.Rounded.Download,
                ) { runCatching { links.openUri(SleepManagerApp.OBTAINIUM_ADD) } }
            }
        }
    }
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
                    colors = cardChipColors(),
                )
            }
        }
    }
}
