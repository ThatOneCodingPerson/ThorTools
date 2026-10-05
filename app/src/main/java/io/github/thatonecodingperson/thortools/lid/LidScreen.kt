package io.github.thatonecodingperson.thortools.lid

import android.content.Intent
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.BluetoothDisabled
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.NightsStay
import androidx.compose.material.icons.rounded.WifiOff
import androidx.compose.material3.FilterChip
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
import io.github.thatonecodingperson.thortools.ui.composables.CardRow
import io.github.thatonecodingperson.thortools.ui.composables.CardRowBox
import io.github.thatonecodingperson.thortools.ui.composables.CardSection
import io.github.thatonecodingperson.thortools.ui.composables.SettingsCard
import io.github.thatonecodingperson.thortools.ui.composables.SubTopAppBar
import io.github.thatonecodingperson.thortools.ui.composables.SwitchCard
import io.github.thatonecodingperson.thortools.ui.composables.cardChipColors
import kotlin.math.roundToInt

/** One switch of the lid screen: how it shows, and which choice it reads and sets. */
private class LidSwitch(
    val icon: ImageVector,
    @StringRes val title: Int,
    @StringRes val info: Int,
    val isOn: (LidChoices) -> Boolean,
    val turn: LidChoices.(Boolean) -> LidChoices,
)

private val WIFI = LidSwitch(Icons.Rounded.WifiOff, R.string.lidWifi, R.string.lidWifiInfo, { it.wifiOff }) { copy(wifiOff = it) }

private val BLUETOOTH = LidSwitch(Icons.Rounded.BluetoothDisabled, R.string.lidBluetooth, R.string.lidBluetoothInfo, { it.bluetoothOff }) {
    copy(bluetoothOff = it)
}

private val BACK_TO_SLEEP = LidSwitch(Icons.Rounded.Bedtime, R.string.lidBackToSleep, R.string.lidBackToSleepInfo, { it.backToSleep }) {
    copy(backToSleep = it)
}

/** What happens when the lid closes: Wi-Fi off, Bluetooth off and Back to sleep, each its own switch, all off at first. */
@Composable
fun LidScreen(viewModel: LidViewModel = hiltViewModel(), onBack: () -> Unit) {
    val state by viewModel.uiState.collectAsState()
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.refresh() }
    val choices = state.choices
    val change = viewModel::change

    // The pointer to SleepManager sits under the radios, where it shows without scrolling.
    val left: @Composable () -> Unit = {
        NoteCard(LidText.result(LocalContext.current, state.last), warning = state.last?.notBack?.isNotEmpty() == true)
        CardSection(R.string.lidRadios) {
            LidSwitchCard(WIFI, choices, change)
            LidSwitchCard(BLUETOOTH, choices, change)
        }
        CardSection(R.string.lidMore) { SleepManagerCard(state) }
    }
    val right: @Composable () -> Unit = {
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
    }

    Scaffold(topBar = { SubTopAppBar(title = R.string.lidTitle, onBack = onBack) }) { padding ->
        CardColumns(padding = padding, left = left, right = right)
    }
}

@Composable
private fun LidSwitchCard(
    switch: LidSwitch,
    choices: LidChoices,
    change: ((LidChoices) -> LidChoices) -> Unit,
    rows: (@Composable ColumnScope.() -> Unit)? = null,
) = SwitchCard(switch.icon, switch.title, switch.info, switch.isOn(choices), rows = rows) { value ->
    change { switch.turn(it, value) }
}

/** Back to sleep's wait: seconds or minutes, and how many. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SleepWaitRows(choices: LidChoices, change: ((LidChoices) -> LidChoices) -> Unit) {
    val unit = choices.sleepUnit
    val amount = unit.clamp(choices.sleepWait)
    CardRowBox {
        Text(stringResource(R.string.lidSleepWait), style = MaterialTheme.typography.bodyMedium)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 4.dp)) {
            WaitUnit.entries.forEach { option ->
                FilterChip(
                    selected = option == unit,
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
                modifier = Modifier.weight(1f),
            )
            Text(
                stringResource(if (unit == WaitUnit.SECONDS) R.string.lidSeconds else R.string.lidMinutes, amount),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
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
        if (state.sleepManagerInstalled) {
            CardRow(
                title = stringResource(R.string.lidMoreOpen),
                info = stringResource(R.string.lidMoreInstalledInfo),
                icon = Icons.Rounded.NightsStay,
                divider = false,
            ) { runCatching { context.startActivity(SleepManagerApp.launchIntent().addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } }
        } else {
            CardRow(
                title = stringResource(R.string.lidMoreGithub),
                info = stringResource(R.string.lidMoreInfo),
                icon = Icons.Rounded.NightsStay,
                divider = false,
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
