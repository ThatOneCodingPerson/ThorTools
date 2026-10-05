package io.github.thatonecodingperson.thortools.charging

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.thatonecodingperson.thortools.coexist.CoexistencePolicy
import io.github.thatonecodingperson.thortools.coexist.OdinToolsDetector
import io.github.thatonecodingperson.thortools.coexist.Overlap
import io.github.thatonecodingperson.thortools.data.SharedPrefsRepo
import io.github.thatonecodingperson.thortools.tools.DeviceType
import io.github.thatonecodingperson.thortools.tools.DeviceUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

data class ChargingUiModel(
    val deviceType: DeviceType = DeviceType.THOR,
    val odinToolsInstalled: Boolean = false,
    val chargeAlertEnabled: Boolean = true,
    val chargeAlertSensitivity: Sensitivity = Sensitivity.MEDIUM,
    val showSensitivityDialog: Boolean = false,
    val chargeLimitEnabled: Boolean = false,
    val showChargeLimitDialog: Boolean = false,
    val currentChargeLimit: ClosedRange<Int> = 20..80,
    val pendingOverlap: Overlap? = null,
)

@HiltViewModel
class ChargingViewModel @Inject constructor(
    deviceUtils: DeviceUtils,
    private val prefs: SharedPrefsRepo,
    private val odinTools: OdinToolsDetector,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ChargingUiModel())
    val uiState: StateFlow<ChargingUiModel> = _uiState.asStateFlow()

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
                chargeAlertEnabled = prefs.chargeAlertEnabled,
                chargeAlertSensitivity = prefs.chargeAlertSensitivity,
                chargeLimitEnabled = prefs.chargeLimitEnabled,
            )
        }
    }

    fun updateChargeAlert(enabled: Boolean) {
        prefs.chargeAlertEnabled = enabled
        _uiState.update { it.copy(chargeAlertEnabled = enabled) }
    }

    fun chargeAlertClicked() {
        _uiState.update { it.copy(showSensitivityDialog = true) }
    }

    fun sensitivityDialogDismissed() {
        _uiState.update { it.copy(showSensitivityDialog = false) }
    }

    fun saveSensitivity(sensitivity: Sensitivity) {
        prefs.chargeAlertSensitivity = sensitivity
        _uiState.update { it.copy(chargeAlertSensitivity = sensitivity, showSensitivityDialog = false) }
    }

    fun updateChargeLimitPreference(newValue: Boolean) {
        if (CoexistencePolicy.needsConfirmation(newValue, _uiState.value.odinToolsInstalled)) {
            _uiState.update { it.copy(pendingOverlap = Overlap.CHARGE_AUTOMATION) }
            return
        }
        prefs.chargeLimitEnabled = newValue
        _uiState.update { it.copy(chargeLimitEnabled = newValue) }
    }

    fun confirmOverlap() {
        prefs.chargeLimitEnabled = true
        _uiState.update { it.copy(pendingOverlap = null, chargeLimitEnabled = true) }
    }

    fun dismissOverlap() {
        _uiState.update { it.copy(pendingOverlap = null) }
    }

    fun chargeLimitClicked() {
        _uiState.update {
            it.copy(showChargeLimitDialog = true, currentChargeLimit = prefs.minBatteryLevel..prefs.maxBatteryLevel)
        }
    }

    fun chargeLimitDialogDismissed() {
        _uiState.update { it.copy(showChargeLimitDialog = false) }
    }

    fun saveChargeLimit(newValue: ClosedRange<Int>) {
        prefs.minBatteryLevel = newValue.start
        prefs.maxBatteryLevel = newValue.endInclusive
        _uiState.update { it.copy(showChargeLimitDialog = false, currentChargeLimit = newValue) }
    }
}
