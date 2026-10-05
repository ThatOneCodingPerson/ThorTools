package io.github.thatonecodingperson.thortools.display

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.thatonecodingperson.thortools.coexist.OdinToolsDetector
import io.github.thatonecodingperson.thortools.coexist.Overlap
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
    /** Without OdinTools these live here; with it, on the OdinTools coexistence page. */
    val saturationAtBoot: Boolean = true,
    val vibrationAtBoot: Boolean = true,
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
                saturationAtBoot = prefs.isEnabled(Overlap.SATURATION_AT_BOOT),
                vibrationAtBoot = prefs.isEnabled(Overlap.VIBRATION_AT_BOOT),
                themeId = prefs.themeId,
                themes = ThorThemes.builtIn + prefs.customPalettes,
                currentSaturation = prefs.saturationOverride,
            )
        }
        // Read through PServer when Android hides the key: never on the main thread.
        readVibration()
    }

    private fun readVibration() {
        viewModelScope.launch {
            val (enabled, strength) = withContext(Dispatchers.IO) { settings.vibrationEnabled to settings.vibrationStrength }
            _uiState.update { it.copy(vibrationEnabled = enabled, currentVibration = strength) }
        }
    }

    fun setSaturationAtBoot(on: Boolean) {
        prefs.setEnabled(Overlap.SATURATION_AT_BOOT, on)
        _uiState.update { it.copy(saturationAtBoot = on) }
    }

    fun setVibrationAtBoot(on: Boolean) {
        prefs.setEnabled(Overlap.VIBRATION_AT_BOOT, on)
        _uiState.update { it.copy(vibrationAtBoot = on) }
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
        _uiState.update { it.copy(showSaturationDialog = false, currentSaturation = newValue) }
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
