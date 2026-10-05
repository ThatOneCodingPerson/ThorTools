package io.github.thatonecodingperson.thortools.display

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.thatonecodingperson.thortools.coexist.OdinToolsDetector
import io.github.thatonecodingperson.thortools.data.SharedPrefsRepo
import io.github.thatonecodingperson.thortools.tools.DeviceType
import io.github.thatonecodingperson.thortools.tools.DeviceUtils
import io.github.thatonecodingperson.thortools.tools.SettingsRepo
import io.github.thatonecodingperson.thortools.ui.theme.ThorPalette
import io.github.thatonecodingperson.thortools.ui.theme.ThorThemes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

data class DisplayUiModel(
    val deviceType: DeviceType = DeviceType.THOR,
    val odinToolsInstalled: Boolean = false,
    val showSaturationDialog: Boolean = false,
    val currentSaturation: Float = 1.0f,
    val vibrationEnabled: Boolean = false,
    val showVibrationDialog: Boolean = false,
    val currentVibration: Int = 0,
    val themeId: String = ThorThemes.default.id,
    val themes: List<ThorPalette> = ThorThemes.builtIn,
)

@HiltViewModel
class DisplayViewModel @Inject constructor(
    deviceUtils: DeviceUtils,
    private val settings: SettingsRepo,
    private val prefs: SharedPrefsRepo,
    private val odinTools: OdinToolsDetector,
) : ViewModel() {

    private val _uiState = MutableStateFlow(DisplayUiModel())
    val uiState: StateFlow<DisplayUiModel> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val deviceType = withContext(Dispatchers.IO) { deviceUtils.getDeviceType() }
            _uiState.update { it.copy(deviceType = deviceType) }
        }
    }

    fun refresh() {
        _uiState.update {
            it.copy(
                odinToolsInstalled = odinTools.isInstalled(),
                vibrationEnabled = settings.vibrationEnabled,
                themeId = prefs.themeId,
                themes = ThorThemes.builtIn + prefs.customPalettes,
            )
        }
    }

    fun saturationClicked() {
        _uiState.update { it.copy(showSaturationDialog = true, currentSaturation = prefs.saturationOverride) }
    }

    fun saturationDialogDismissed() {
        _uiState.update { it.copy(showSaturationDialog = false) }
    }

    // Saturation and vibration are written (and the strength read) through PServer, so off the main thread.
    fun saveSaturation(newValue: Float) {
        prefs.saturationOverride = newValue
        viewModelScope.launch(Dispatchers.IO) { settings.setSfSaturation(newValue) }
        _uiState.update { it.copy(showSaturationDialog = false) }
    }

    fun updateVibrationPreference(newValue: Boolean) {
        viewModelScope.launch(Dispatchers.IO) { settings.vibrationEnabled = newValue }
        _uiState.update { it.copy(vibrationEnabled = newValue) }
    }

    fun vibrationClicked() {
        viewModelScope.launch {
            val strength = withContext(Dispatchers.IO) { settings.vibrationStrength }
            _uiState.update { it.copy(showVibrationDialog = true, currentVibration = strength) }
        }
    }

    fun vibrationDialogDismissed() {
        _uiState.update { it.copy(showVibrationDialog = false) }
    }

    fun saveVibration(newValue: Int) {
        prefs.vibrationStrength = newValue
        viewModelScope.launch(Dispatchers.IO) { settings.vibrationStrength = newValue }
        _uiState.update { it.copy(showVibrationDialog = false, currentVibration = newValue) }
    }
}
