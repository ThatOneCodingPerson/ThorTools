package io.github.thatonecodingperson.thortools.main

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.thatonecodingperson.thortools.coexist.OdinToolsDetector
import io.github.thatonecodingperson.thortools.service.ServiceWatchJob
import io.github.thatonecodingperson.thortools.setup.AccessChecker
import io.github.thatonecodingperson.thortools.tools.DeviceUtils
import io.github.thatonecodingperson.thortools.tools.SettingsRepo
import io.github.thatonecodingperson.thortools.tools.ShellExecutor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@HiltViewModel
class MainViewModel @Inject constructor(
    @ApplicationContext context: Context,
    deviceUtils: DeviceUtils,
    executor: ShellExecutor,
    settings: SettingsRepo,
    private val odinTools: OdinToolsDetector,
    private val access: AccessChecker,
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

    fun refreshCoexistence() {
        _uiState.update { it.copy(odinTools = odinTools.state(), accessNeedsAttention = access.quick().needsAttention) }
    }

    fun pServerDialogDismissed() {
        _uiState.update { it.copy(showPServerNotAvailableDialog = false) }
    }

    fun incompatibleDeviceDialogDismissed() {
        _uiState.update { it.copy(showIncompatibleDeviceDialog = false) }
    }
}
