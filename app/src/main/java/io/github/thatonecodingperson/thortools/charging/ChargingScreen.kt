package io.github.thatonecodingperson.thortools.charging

import android.Manifest
import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material.icons.rounded.BatteryChargingFull
import androidx.compose.material.icons.rounded.ElectricalServices
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Laptop
import androidx.compose.material.icons.rounded.RestartAlt
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import io.github.thatonecodingperson.thortools.R
import io.github.thatonecodingperson.thortools.hotkeys.NoteCard
import io.github.thatonecodingperson.thortools.ui.composables.CardColumns
import io.github.thatonecodingperson.thortools.ui.composables.CardRow
import io.github.thatonecodingperson.thortools.ui.composables.CardRowBox
import io.github.thatonecodingperson.thortools.ui.composables.CardSection
import io.github.thatonecodingperson.thortools.ui.composables.ChargeLimitPreferenceDialog
import io.github.thatonecodingperson.thortools.ui.composables.LinkCard
import io.github.thatonecodingperson.thortools.ui.composables.OverlapConfirmDialog
import io.github.thatonecodingperson.thortools.ui.composables.SettingsCard
import io.github.thatonecodingperson.thortools.ui.composables.SubTopAppBar
import io.github.thatonecodingperson.thortools.ui.composables.SwitchCard
import io.github.thatonecodingperson.thortools.ui.composables.cardChipColors

/**
 * Power management: the charging stability alert and what it is for, the charge limit automation, what the charger is
 * doing right now, and the way to the lid sandbox.
 */
@Composable
fun ChargingScreen(viewModel: ChargingViewModel = hiltViewModel(), onLid: () -> Unit, onBack: () -> Unit) {
    val state by viewModel.uiState.collectAsState()
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(Unit) { viewModel.refresh() }
    LaunchedEffect(Unit) { lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) { viewModel.watch() } }

    val context = LocalContext.current
    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (!granted) Toast.makeText(context, R.string.notificationsDenied, Toast.LENGTH_LONG).show()
    }
    val restart = { context.startActivity(Intent(context, RestartActivity::class.java)) }

    state.pendingOverlap?.let {
        OverlapConfirmDialog(overlap = it, onConfirm = viewModel::confirmOverlap, onDismiss = viewModel::dismissOverlap)
    }
    if (state.showChargeLimitDialog) {
        ChargeLimitPreferenceDialog(state.currentChargeLimit, viewModel::chargeLimitDialogDismissed, viewModel::saveChargeLimit)
    }

    Scaffold(topBar = { SubTopAppBar(title = R.string.powerManagement, onBack = onBack) }) { padding ->
        CardColumns(
            padding = padding,
            left = {
                CardSection(R.string.powerStability) {
                    SwitchCard(
                        icon = Icons.Rounded.ElectricalServices,
                        title = R.string.chargeAlert,
                        info = R.string.chargeAlertDescription,
                        checked = state.chargeAlertEnabled,
                        rows = { SensitivityRow(state.chargeAlertSensitivity, state.chargeAlertEnabled, viewModel::saveSensitivity) },
                    ) { enabled ->
                        viewModel.updateChargeAlert(enabled)
                        if (enabled) notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                    AboutStabilityCard()
                }
                CardSection(R.string.chargeLimit) {
                    SwitchCard(
                        icon = Icons.Rounded.BatteryChargingFull,
                        title = R.string.overlapChargeAutomation,
                        info = R.string.chargeLimitDescription,
                        checked = state.chargeLimitEnabled,
                        rows = {
                            CardRow(
                                title = stringResource(R.string.chargeLimitLevels),
                                value = stringResource(
                                    R.string.chargeLimitLevelsValue,
                                    state.currentChargeLimit.start,
                                    state.currentChargeLimit.endInclusive,
                                ),
                                onClick = viewModel::chargeLimitClicked,
                            )
                        },
                        onChange = viewModel::updateChargeLimitPreference,
                    )
                    val top = state.currentChargeLimit.endInclusive
                    if (ChargeStatus.limitUnreachable(state.now?.aynLimit == true, top)) {
                        NoteCard(stringResource(R.string.chargeLimitUnreachable, top), warning = true)
                    }
                    if (state.odinToolsInstalled) NoteCard(stringResource(R.string.powerOdinToolsNote))
                    NoteCard(stringResource(R.string.chargeLimitNote))
                    // A restart after a charge setting changed is what owners report brings charging back.
                    LinkCard(
                        icon = Icons.Rounded.RestartAlt,
                        title = stringResource(R.string.powerRestartTitle),
                        info = stringResource(if (state.restartSuggested) R.string.powerRestartChanged else R.string.powerRestartInfo),
                        highlighted = state.restartSuggested,
                        onClick = restart,
                    )
                }
                CardSection(R.string.lidHeader) {
                    LinkCard(
                        icon = Icons.Rounded.Laptop,
                        title = stringResource(R.string.lidTitle),
                        info = stringResource(R.string.lidRowSummary),
                        onClick = onLid,
                    )
                }
            },
            right = {
                CardSection(R.string.powerNow, R.string.powerNowIntro) {
                    ChargingNowCard(state.now, state.switches, state.chargeAlertEnabled, state.chargeAlertSensitivity)
                }
            },
        )
    }
}

/** How many switches within 10 minutes bring the alert, as a row of the alert's card. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SensitivityRow(selected: Sensitivity, enabled: Boolean, onSelect: (Sensitivity) -> Unit) {
    CardRowBox {
        Text(stringResource(R.string.sensitivity), style = MaterialTheme.typography.bodyLarge)
        Text(
            stringResource(R.string.powerSensitivityInfo),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 4.dp)) {
            Sensitivity.entries.forEach { option ->
                FilterChip(
                    selected = option == selected,
                    enabled = enabled,
                    onClick = { onSelect(option) },
                    label = { Text(stringResource(option.label, option.changesToAlert)) },
                    colors = cardChipColors(),
                )
            }
        }
    }
}

@get:StringRes
private val Sensitivity.label: Int
    get() = when (this) {
        Sensitivity.LOW -> R.string.sensitivityLow
        Sensitivity.MEDIUM -> R.string.sensitivityMedium
        Sensitivity.HIGH -> R.string.sensitivityHigh
    }

/** What the known issue looks like, what helps and what is normal; folded until tapped. */
@Composable
private fun AboutStabilityCard() {
    var open by rememberSaveable { mutableStateOf(false) }
    val colors = MaterialTheme.colorScheme
    Surface(
        onClick = { open = !open },
        shape = RoundedCornerShape(16.dp),
        color = colors.surfaceContainer,
        border = BorderStroke(1.dp, colors.outline),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Info, contentDescription = null, tint = colors.primary, modifier = Modifier.size(24.dp))
                Text(
                    stringResource(R.string.powerAboutTitle),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f).padding(horizontal = 12.dp),
                )
                Icon(
                    if (open) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                    contentDescription = null,
                    tint = colors.onSurfaceVariant,
                )
            }
            if (open) {
                listOf(R.string.powerAboutIssue, R.string.powerAboutHelps, R.string.powerAboutNormal).forEach { text ->
                    Text(stringResource(text), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 10.dp))
                }
            }
        }
    }
}

@Composable
private fun ChargingNowCard(now: ChargeNow?, switches: Int?, alertOn: Boolean, sensitivity: Sensitivity) {
    val none = stringResource(R.string.panelNoValue)
    SettingsCard {
        if (now == null) {
            Text(stringResource(R.string.powerNowReading), style = MaterialTheme.typography.bodyMedium)
            return@SettingsCard
        }
        val plugged = now.chargeClass != ChargeClass.UNPLUGGED
        NowRow(R.string.powerNowStatus, stringResource(now.chargeClass.label), strong = true)
        if (plugged) {
            NowRow(R.string.powerNowCharger, now.charger.label?.let { stringResource(it) } ?: now.chargerType ?: none)
            NowRow(R.string.powerNowOffered, now.offeredWatts?.let { stringResource(R.string.powerWatts, it) } ?: none)
            NowRow(
                R.string.powerNowIncoming,
                when {
                    now.inputWatts != null && now.inputVolts != null -> stringResource(
                        R.string.powerWattsAtVolts,
                        now.inputWatts,
                        now.inputVolts,
                    )
                    now.inputWatts != null -> stringResource(R.string.powerWatts, now.inputWatts)
                    else -> none
                },
            )
        }
        NowRow(
            R.string.powerNowBattery,
            when {
                now.level != null && now.temperatureC != null -> stringResource(R.string.powerBatteryLevelTemp, now.level, now.temperatureC)
                now.level != null -> stringResource(R.string.powerPercent, now.level)
                else -> none
            },
        )
        NowRow(R.string.powerNowHealth, now.healthPercent?.let { stringResource(R.string.powerPercent, it) } ?: none)
        NowRow(R.string.powerNowAynLimit, onOff(now.aynLimit))
        NowRow(R.string.powerNowSeparation, onOff(now.separation))
        if (now.fiveVoltCap) NowRow(R.string.powerNowFiveVolt, stringResource(R.string.powerOn))
        NowRow(
            R.string.powerNowSwitches,
            when {
                !alertOn -> stringResource(R.string.powerNowAlertOff)
                switches == null -> none
                else -> stringResource(R.string.powerNowSwitchesValue, switches, sensitivity.changesToAlert)
            },
        )
        Text(
            stringResource(R.string.powerNowFootnote),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 6.dp),
        )
    }
}

@Composable
private fun onOff(value: Boolean?): String = when (value) {
    true -> stringResource(R.string.powerOn)
    false -> stringResource(R.string.powerOff)
    null -> stringResource(R.string.panelNoValue)
}

@Composable
private fun NowRow(@StringRes label: Int, value: String, strong: Boolean = false) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
        Text(
            stringResource(label),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (strong) FontWeight.SemiBold else FontWeight.Normal,
            color = if (strong) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
        )
    }
}

@get:StringRes
private val ChargeClass.label: Int
    get() = when (this) {
        ChargeClass.UNPLUGGED -> R.string.chargeNowUnplugged
        ChargeClass.NOT_CHARGING -> R.string.chargeNowNotCharging
        ChargeClass.SLOW -> R.string.chargeNowSlow
        ChargeClass.NORMAL -> R.string.chargeNowNormal
        ChargeClass.FAST -> R.string.chargeNowFast
        ChargeClass.HELD -> R.string.chargeNowHeld
    }

/** Null for a type without a name of its own: the raw type is shown instead. */
private val ChargerKind.label: Int?
    get() = when (this) {
        ChargerKind.NONE -> null
        ChargerKind.COMPUTER -> R.string.chargerComputer
        ChargerKind.COMPUTER_CHARGING -> R.string.chargerComputerCharging
        ChargerKind.WALL -> R.string.chargerWall
        ChargerKind.QUICK_CHARGE -> R.string.chargerQuick
        ChargerKind.PD -> R.string.chargerPd
        ChargerKind.OTHER -> null
    }
