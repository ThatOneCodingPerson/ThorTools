package io.github.thatonecodingperson.thortools.main

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.thatonecodingperson.thortools.coexist.OdinToolsDetector
import io.github.thatonecodingperson.thortools.data.AppOverrideDao
import io.github.thatonecodingperson.thortools.data.SharedPrefsRepo
import io.github.thatonecodingperson.thortools.extras.KeyboardPlace
import io.github.thatonecodingperson.thortools.models.ControllerStyle
import io.github.thatonecodingperson.thortools.models.L2R2Style
import io.github.thatonecodingperson.thortools.service.ServiceStatus
import io.github.thatonecodingperson.thortools.service.ServiceWatchJob
import io.github.thatonecodingperson.thortools.setup.AccessChecker
import io.github.thatonecodingperson.thortools.tools.DeviceUtils
import io.github.thatonecodingperson.thortools.tools.SettingsRepo
import io.github.thatonecodingperson.thortools.tools.ShellExecutor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@HiltViewModel
class MainViewModel @Inject constructor(
    @ApplicationContext context: Context,
    deviceUtils: DeviceUtils,
    settings: SettingsRepo,
    private val odinTools: OdinToolsDetector,
    private val access: AccessChecker,
    private val prefs: SharedPrefsRepo,
    private val status: ServiceStatus,
    private val executor: ShellExecutor,
    private val appOverrides: AppOverrideDao,
) : ViewModel() {

    private val _uiState = MutableStateFlow(MainUiModel())
    val uiState: StateFlow<MainUiModel> = _uiState.asStateFlow()

    init {
        viewModelScope.launch(Dispatchers.IO) { settings.applyRequiredSettings() }
        ServiceWatchJob.schedule(context, ServiceWatchJob.AFTER_OPEN_MS, afterBoot = false)

        // Device facts come through PServer, which never runs on the main thread (key events wait there).
        viewModelScope.launch {
            val (deviceType, version) = withContext(Dispatchers.IO) { deviceUtils.getDeviceType() to deviceUtils.getDeviceVersion() }
            _uiState.update {
                it.copy(
                    deviceType = deviceType,
                    deviceVersion = version,
                    showIncompatibleDeviceDialog = !deviceType.isSupported,
                    showPServerNotAvailableDialog = !executor.pServerAvailable,
                )
            }
        }
    }

    /** Again on every visit: the cards show what was changed in the sections meanwhile. */
    fun refresh() {
        val layout = prefs.panelLayout
        _uiState.update {
            it.copy(
                odinTools = odinTools.state(),
                accessNeedsAttention = access.quick().needsAttention,
                serviceRunning = status.connected,
                helperRunning = status.rawInput?.connected == true,
                summary = it.summary.copy(
                    hotkeys = prefs.hotkeys.size,
                    panelPages = layout.pages.size,
                    panelWidgets = layout.pages.sumOf { page -> page.widgets.size },
                    lidActions = prefs.lidChoices.switchedOn,
                    chargeAlert = prefs.chargeAlertEnabled,
                    chargeLimit = if (prefs.chargeLimitEnabled) prefs.minBatteryLevel..prefs.maxBatteryLevel else null,
                    theme = prefs.palette(),
                    ledMode = prefs.ledLook.mode,
                ),
            )
        }
        // The modes come through PServer and the profiles from the database: never on the main thread.
        viewModelScope.launch {
            val (style, l2r2, profiles) = withContext(Dispatchers.IO) {
                Triple(
                    ControllerStyle.getStyle(executor).takeIf { it != ControllerStyle.Unknown }?.textRes,
                    L2R2Style.getStyle(executor).takeIf { it != L2R2Style.Unknown }?.textRes,
                    appOverrides.getAll().first().size,
                )
            }
            val keyboard = withContext(Dispatchers.IO) { KeyboardPlace.read(executor) }
            _uiState.update {
                it.copy(summary = it.summary.copy(controllerStyle = style, l2r2 = l2r2, profiles = profiles, keyboard = keyboard))
            }
        }
    }

    fun openPanel() {
        status.toggleQuickPanel?.invoke()
    }

    fun pServerDialogDismissed() {
        _uiState.update { it.copy(showPServerNotAvailableDialog = false) }
    }

    fun incompatibleDeviceDialogDismissed() {
        _uiState.update { it.copy(showIncompatibleDeviceDialog = false) }
    }
}
