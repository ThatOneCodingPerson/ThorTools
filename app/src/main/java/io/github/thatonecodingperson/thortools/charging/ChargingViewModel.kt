package io.github.thatonecodingperson.thortools.charging

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.thatonecodingperson.thortools.coexist.CoexistencePolicy
import io.github.thatonecodingperson.thortools.coexist.OdinToolsDetector
import io.github.thatonecodingperson.thortools.coexist.Overlap
import io.github.thatonecodingperson.thortools.data.SharedPrefsRepo
import io.github.thatonecodingperson.thortools.service.ServiceStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import javax.inject.Inject

data class ChargingUiModel(
    val odinToolsInstalled: Boolean = false,
    val chargeAlertEnabled: Boolean = true,
    val chargeAlertSensitivity: Sensitivity = Sensitivity.MEDIUM,
    val chargeLimitEnabled: Boolean = false,
    val showChargeLimitDialog: Boolean = false,
    val currentChargeLimit: ClosedRange<Int> = 20..80,
    val pendingOverlap: Overlap? = null,
    /** The live reading while Power management is open; null before the first one. */
    val now: ChargeNow? = null,
    /** How often charging switched in the alert's window; null while the alert isn't watching. */
    val switches: Int? = null,
    /** A charge setting changed on this screen, so the restart card stands out. */
    val restartSuggested: Boolean = false,
)

@HiltViewModel
class ChargingViewModel @Inject constructor(
    private val prefs: SharedPrefsRepo,
    private val odinTools: OdinToolsDetector,
    private val reader: ChargeReader,
    private val status: ServiceStatus,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ChargingUiModel())
    val uiState: StateFlow<ChargingUiModel> = _uiState.asStateFlow()

    fun refresh() {
        _uiState.update {
            it.copy(
                odinToolsInstalled = odinTools.isInstalled(),
                chargeAlertEnabled = prefs.chargeAlertEnabled,
                chargeAlertSensitivity = prefs.chargeAlertSensitivity,
                chargeLimitEnabled = prefs.chargeLimitEnabled,
                currentChargeLimit = prefs.minBatteryLevel..prefs.maxBatteryLevel,
            )
        }
    }

    /** Reads the charger every few seconds for as long as the caller runs it (while the screen is shown). */
    suspend fun watch() {
        while (true) {
            val now = withContext(Dispatchers.IO) { reader.read() }
            _uiState.update { it.copy(now = now, switches = status.chargeSwitches) }
            delay(LIVE_MS)
        }
    }

    fun updateChargeAlert(enabled: Boolean) {
        prefs.chargeAlertEnabled = enabled
        _uiState.update { it.copy(chargeAlertEnabled = enabled) }
    }

    fun saveSensitivity(sensitivity: Sensitivity) {
        prefs.chargeAlertSensitivity = sensitivity
        _uiState.update { it.copy(chargeAlertSensitivity = sensitivity) }
    }

    fun updateChargeLimitPreference(newValue: Boolean) {
        if (CoexistencePolicy.needsConfirmation(newValue, _uiState.value.odinToolsInstalled)) {
            _uiState.update { it.copy(pendingOverlap = Overlap.CHARGE_AUTOMATION) }
            return
        }
        prefs.chargeLimitEnabled = newValue
        _uiState.update { it.copy(chargeLimitEnabled = newValue, restartSuggested = true) }
    }

    fun confirmOverlap() {
        prefs.chargeLimitEnabled = true
        _uiState.update { it.copy(pendingOverlap = null, chargeLimitEnabled = true, restartSuggested = true) }
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

    private companion object {
        const val LIVE_MS = 3_000L
    }
}
