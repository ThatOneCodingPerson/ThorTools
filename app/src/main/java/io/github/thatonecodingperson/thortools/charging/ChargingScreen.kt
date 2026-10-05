package io.github.thatonecodingperson.thortools.charging

import android.Manifest
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import io.github.thatonecodingperson.thortools.R
import io.github.thatonecodingperson.thortools.tools.DeviceType.ODIN2
import io.github.thatonecodingperson.thortools.ui.composables.ChargeLimitPreferenceDialog
import io.github.thatonecodingperson.thortools.ui.composables.OverlapConfirmDialog
import io.github.thatonecodingperson.thortools.ui.composables.SettingsHeader
import io.github.thatonecodingperson.thortools.ui.composables.SubScreen
import io.github.thatonecodingperson.thortools.ui.composables.SwitchableTriggerPreference

@Composable
fun ChargingScreen(viewModel: ChargingViewModel = hiltViewModel(), onBack: () -> Unit) {
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(Unit) { viewModel.refresh() }

    val context = LocalContext.current
    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (!granted) Toast.makeText(context, R.string.notificationsDenied, Toast.LENGTH_LONG).show()
    }

    uiState.pendingOverlap?.let { overlap ->
        OverlapConfirmDialog(
            overlap = overlap,
            onConfirm = viewModel::confirmOverlap,
            onDismiss = viewModel::dismissOverlap,
        )
    }

    if (uiState.showSensitivityDialog) {
        SensitivityDialog(
            initial = uiState.chargeAlertSensitivity,
            onCancel = viewModel::sensitivityDialogDismissed,
            onSave = viewModel::saveSensitivity,
        )
    }

    if (uiState.showChargeLimitDialog) {
        ChargeLimitPreferenceDialog(
            initialValue = uiState.currentChargeLimit,
            onCancel = viewModel::chargeLimitDialogDismissed,
            onSave = viewModel::saveChargeLimit,
        )
    }

    SubScreen(title = R.string.charging, onBack = onBack) {
        SwitchableTriggerPreference(
            icon = R.drawable.ic_electrical_services,
            title = R.string.chargeAlert,
            description = R.string.chargeAlertDescription,
            state = uiState.chargeAlertEnabled,
            onClick = viewModel::chargeAlertClicked,
        ) { enabled ->
            viewModel.updateChargeAlert(enabled)
            if (enabled) notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        if (uiState.deviceType == ODIN2) {
            SettingsHeader(R.string.battery)
            SwitchableTriggerPreference(
                icon = R.drawable.ic_electrical_services,
                title = R.string.chargeLimit,
                description = R.string.chargeLimitDescription,
                state = uiState.chargeLimitEnabled,
                onClick = viewModel::chargeLimitClicked,
                tag = R.string.tagOdinTools.takeIf { uiState.odinToolsInstalled },
                onChange = viewModel::updateChargeLimitPreference,
            )
        }
    }
}
