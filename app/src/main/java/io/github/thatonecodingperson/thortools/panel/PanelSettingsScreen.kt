package io.github.thatonecodingperson.thortools.panel

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.thatonecodingperson.thortools.R
import io.github.thatonecodingperson.thortools.actions.PanelCloseTarget
import io.github.thatonecodingperson.thortools.data.SharedPrefsRepo
import io.github.thatonecodingperson.thortools.service.ServiceStatus
import io.github.thatonecodingperson.thortools.ui.composables.ChoiceDialogPreference
import io.github.thatonecodingperson.thortools.ui.composables.ChoiceOption
import io.github.thatonecodingperson.thortools.ui.composables.SubScreen
import io.github.thatonecodingperson.thortools.ui.composables.SwitchPreference
import io.github.thatonecodingperson.thortools.ui.composables.TriggerPreference
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject

data class PanelSettingsUiModel(
    val takesController: Boolean = true,
    val serviceRunning: Boolean = true,
    val closeAppTarget: PanelCloseTarget = PanelCloseTarget.OPENED_FROM,
    val cleanMemory: Boolean = false,
    val pickingCloseAppTarget: Boolean = false,
)

@HiltViewModel
class PanelSettingsViewModel @Inject constructor(private val prefs: SharedPrefsRepo, private val status: ServiceStatus) : ViewModel() {

    private val _uiState =
        MutableStateFlow(
            PanelSettingsUiModel(prefs.panelTakesController, status.connected, prefs.panelCloseAppTarget, prefs.panelCleanMemory),
        )
    val uiState: StateFlow<PanelSettingsUiModel> = _uiState.asStateFlow()

    fun setTakesController(enabled: Boolean) {
        prefs.panelTakesController = enabled
        _uiState.update { it.copy(takesController = enabled) }
    }

    fun setCleanMemory(on: Boolean) {
        prefs.panelCleanMemory = on
        _uiState.update { it.copy(cleanMemory = on) }
    }

    fun pickCloseAppTarget(show: Boolean) = _uiState.update { it.copy(pickingCloseAppTarget = show) }

    fun setCloseAppTarget(target: PanelCloseTarget) {
        prefs.panelCloseAppTarget = target
        _uiState.update { it.copy(closeAppTarget = target, pickingCloseAppTarget = false) }
    }

    fun openPanel() {
        val toggle = status.toggleQuickPanel
        _uiState.update { it.copy(serviceRunning = toggle != null) }
        toggle?.invoke()
    }
}

@Composable
fun PanelSettingsScreen(viewModel: PanelSettingsViewModel = hiltViewModel(), onEditPanel: () -> Unit, onBack: () -> Unit) {
    val uiState by viewModel.uiState.collectAsState()

    if (uiState.pickingCloseAppTarget) {
        ChoiceDialogPreference(
            title = R.string.panelCloseTitle,
            message = R.string.panelClosePickInfo,
            options = PanelCloseTarget.entries.map { ChoiceOption(it.id, stringResource(it.label), stringResource(it.info)) },
            selectedKey = uiState.closeAppTarget.id,
            onSelect = { key -> PanelCloseTarget.byId(key)?.let(viewModel::setCloseAppTarget) },
            onDismiss = { viewModel.pickCloseAppTarget(false) },
        )
    }

    SubScreen(title = R.string.quickPanel, onBack = onBack) {
        TriggerPreference(
            icon = R.drawable.ic_palette,
            title = R.string.editPanel,
            description = R.string.editPanelDescription,
            onClick = onEditPanel,
        )
        SwitchPreference(
            icon = R.drawable.ic_gamepad,
            title = R.string.panelTakesController,
            description = R.string.panelTakesControllerDescription,
            state = uiState.takesController,
            onChange = viewModel::setTakesController,
        )
        TriggerPreference(
            icon = R.drawable.ic_app_settings,
            title = stringResource(R.string.panelCloseTitle),
            description = stringResource(uiState.closeAppTarget.label),
        ) { viewModel.pickCloseAppTarget(true) }
        SwitchPreference(
            icon = R.drawable.ic_more_time,
            title = R.string.panelCleanMemory,
            description = R.string.panelCleanMemoryDescription,
            state = uiState.cleanMemory,
            onChange = viewModel::setCleanMemory,
        )
        TriggerPreference(
            icon = R.drawable.ic_sliders,
            title = R.string.openQuickPanelNow,
            description = if (uiState.serviceRunning) R.string.openQuickPanelNowDescription else R.string.serviceOffDescription,
            onClick = viewModel::openPanel,
        )
        Text(
            text = stringResource(R.string.quickPanelFootnote),
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )
    }
}
