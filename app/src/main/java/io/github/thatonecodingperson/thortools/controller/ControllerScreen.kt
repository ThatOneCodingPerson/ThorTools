package io.github.thatonecodingperson.thortools.controller

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import io.github.thatonecodingperson.thortools.R
import io.github.thatonecodingperson.thortools.tools.DeviceType
import io.github.thatonecodingperson.thortools.tools.SettingsRepo
import io.github.thatonecodingperson.thortools.ui.composables.RemapButtonDialog
import io.github.thatonecodingperson.thortools.ui.composables.SettingsHeader
import io.github.thatonecodingperson.thortools.ui.composables.SubScreen
import io.github.thatonecodingperson.thortools.ui.composables.SwitchPreference
import io.github.thatonecodingperson.thortools.ui.composables.TriggerPreference

@Composable
fun ControllerScreen(
    viewModel: ControllerViewModel = hiltViewModel(),
    onModes: () -> Unit,
    onHotkeys: () -> Unit,
    onQuickPanel: () -> Unit,
    onBack: () -> Unit,
) {
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(Unit) { viewModel.refresh() }

    uiState.remapSetting?.let { setting ->
        RemapButtonDialog(
            initialValue = uiState.remapKeyCode,
            onCancel = viewModel::remapDismissed,
            onReset = { viewModel.remapReset(setting) },
            onSave = { viewModel.remapSave(setting, it) },
        )
    }

    SubScreen(title = R.string.controllerAndButtons, onBack = onBack) {
        SettingsHeader(R.string.controllerHeader)
        TriggerPreference(
            icon = R.drawable.ic_face_buttons,
            title = stringResource(R.string.controllerModes),
            description = stringResource(
                R.string.controllerModesNow,
                stringResource(uiState.controllerStyleNow.textRes),
                stringResource(uiState.l2r2StyleNow.textRes),
            ),
            onClick = onModes,
        )

        SettingsHeader(R.string.buttons)
        TriggerPreference(
            icon = R.drawable.ic_gamepad,
            title = R.string.hotkeys,
            description = R.string.hotkeysDescription,
            onClick = onHotkeys,
        )
        TriggerPreference(
            icon = R.drawable.ic_sliders,
            title = R.string.quickPanel,
            description = R.string.quickPanelDescription,
            onClick = onQuickPanel,
        )
        SwitchPreference(
            icon = R.drawable.ic_home,
            title = R.string.singlePressHome,
            description = R.string.singlePressHomeDescription,
            state = uiState.singlePressHomeEnabled,
            tag = R.string.tagOdinTools.takeIf { uiState.odinToolsInstalled },
            onChange = viewModel::updateSinglePressHomePreference,
        )

        if (uiState.deviceType == DeviceType.ODIN2) {
            SettingsHeader(R.string.backButtons)
            TriggerPreference(icon = R.drawable.ic_gamepad, title = R.string.m1Button, description = R.string.remapButtonDescription) {
                viewModel.remapClicked(SettingsRepo.KEY_CUSTOM_M1_VALUE)
            }
            TriggerPreference(icon = R.drawable.ic_gamepad, title = R.string.m2Button, description = R.string.remapButtonDescription) {
                viewModel.remapClicked(SettingsRepo.KEY_CUSTOM_M2_VALUE)
            }
        }
    }
}
