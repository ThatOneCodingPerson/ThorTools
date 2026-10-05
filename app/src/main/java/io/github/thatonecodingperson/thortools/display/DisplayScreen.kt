package io.github.thatonecodingperson.thortools.display

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import io.github.thatonecodingperson.thortools.R
import io.github.thatonecodingperson.thortools.tools.DeviceType.ODIN2
import io.github.thatonecodingperson.thortools.ui.composables.SaturationPreferenceDialog
import io.github.thatonecodingperson.thortools.ui.composables.SettingsHeader
import io.github.thatonecodingperson.thortools.ui.composables.SubScreen
import io.github.thatonecodingperson.thortools.ui.composables.SwitchableTriggerPreference
import io.github.thatonecodingperson.thortools.ui.composables.TriggerPreference
import io.github.thatonecodingperson.thortools.ui.composables.VibrationPreferenceDialog
import io.github.thatonecodingperson.thortools.ui.theme.ThorThemes

@Composable
fun DisplayScreen(viewModel: DisplayViewModel = hiltViewModel(), onThemes: () -> Unit, onBack: () -> Unit) {
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(Unit) { viewModel.refresh() }

    if (uiState.showSaturationDialog) {
        SaturationPreferenceDialog(
            initialValue = uiState.currentSaturation,
            onCancel = viewModel::saturationDialogDismissed,
            onSave = viewModel::saveSaturation,
        )
    }

    if (uiState.showVibrationDialog) {
        VibrationPreferenceDialog(
            initialValue = uiState.currentVibration,
            onCancel = viewModel::vibrationDialogDismissed,
            onSave = viewModel::saveVibration,
        )
    }

    SubScreen(title = R.string.display, onBack = onBack) {
        val current = uiState.themes.find { it.id == uiState.themeId }
        TriggerPreference(
            icon = R.drawable.ic_palette,
            title = stringResource(R.string.theme),
            description = when {
                uiState.themeId == ThorThemes.SYSTEM -> stringResource(R.string.themeSystem)
                current?.label != null -> stringResource(current.label)
                else -> current?.name ?: ThorThemes.default.name
            },
            onClick = onThemes,
        )
        TriggerPreference(
            icon = R.drawable.ic_palette,
            title = R.string.saturation,
            description = R.string.saturationDescription,
            tag = R.string.tagOdinTools.takeIf { uiState.odinToolsInstalled },
            onClick = viewModel::saturationClicked,
        )
        if (uiState.deviceType == ODIN2) {
            SettingsHeader(R.string.haptics)
            SwitchableTriggerPreference(
                icon = R.drawable.ic_vibration,
                title = R.string.vibrationStrength,
                description = R.string.vibrationStrengthDescription,
                state = uiState.vibrationEnabled,
                onClick = viewModel::vibrationClicked,
                tag = R.string.tagOdinTools.takeIf { uiState.odinToolsInstalled },
                onChange = viewModel::updateVibrationPreference,
            )
        }
    }
}
