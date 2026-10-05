package io.github.thatonecodingperson.thortools.appsettings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import io.github.thatonecodingperson.thortools.R
import io.github.thatonecodingperson.thortools.ui.composables.OverlapConfirmDialog
import io.github.thatonecodingperson.thortools.ui.composables.SubScreen
import io.github.thatonecodingperson.thortools.ui.composables.SwitchPreference
import io.github.thatonecodingperson.thortools.ui.composables.SwitchableTriggerPreference
import io.github.thatonecodingperson.thortools.ui.composables.VideoOutputOverridePreferenceDialog

@Composable
fun ProfilesScreen(viewModel: ProfilesViewModel = hiltViewModel(), onAppOverrides: () -> Unit, onBack: () -> Unit) {
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(Unit) { viewModel.refresh() }

    uiState.pendingOverlap?.let { overlap ->
        OverlapConfirmDialog(
            overlap = overlap,
            onConfirm = viewModel::confirmOverlap,
            onDismiss = viewModel::dismissOverlap,
        )
    }

    if (uiState.showVideoOutputOverrideDialog) {
        VideoOutputOverridePreferenceDialog(
            initialControllerStyle = uiState.videoOutputControllerStyle,
            initialL2R2Style = uiState.videoOutputL2R2Style,
            onCancel = viewModel::videoOutputOverrideDialogDismissed,
            onSave = viewModel::saveVideoOutputOverride,
        )
    }

    SubScreen(title = R.string.appProfiles, onBack = onBack) {
        SwitchableTriggerPreference(
            icon = R.drawable.ic_app_settings,
            title = R.string.appOverrides,
            description = R.string.appOverridesDescription,
            state = uiState.appOverridesEnabled,
            onClick = onAppOverrides,
            tag = when {
                !uiState.odinToolsInstalled -> null
                uiState.perAppControlsEnabled -> R.string.tagOdinTools
                else -> R.string.tagLeftToOdinTools
            },
            onChange = viewModel::appOverridesEnabled,
        )
        SwitchableTriggerPreference(
            icon = R.drawable.ic_gamepad_docked,
            title = R.string.videoOutputOverride,
            description = R.string.videoOutputOverrideDescription,
            state = uiState.videoOutputOverrideEnabled,
            onClick = viewModel::videoOutputOverrideClicked,
            tag = R.string.tagOdinTools.takeIf { uiState.odinToolsInstalled },
            onChange = viewModel::updateVideoOutputOverridePreference,
        )
        SwitchPreference(
            icon = R.drawable.ic_more_time,
            title = R.string.overrideDelay,
            description = R.string.overrideDelayDescription,
            state = uiState.overrideDelayEnabled,
            onChange = viewModel::overrideDelayEnabled,
        )
    }
}
