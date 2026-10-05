package io.github.thatonecodingperson.thortools.appsettings

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.thatonecodingperson.thortools.coexist.CoexistencePolicy
import io.github.thatonecodingperson.thortools.coexist.OdinToolsDetector
import io.github.thatonecodingperson.thortools.coexist.Overlap
import io.github.thatonecodingperson.thortools.data.SharedPrefsRepo
import io.github.thatonecodingperson.thortools.models.ControllerStyle
import io.github.thatonecodingperson.thortools.models.L2R2Style
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject

data class ProfilesUiModel(
    val odinToolsInstalled: Boolean = false,
    val perAppControlsEnabled: Boolean = true,
    val appOverridesEnabled: Boolean = true,
    val overrideDelayEnabled: Boolean = false,
    val videoOutputOverrideEnabled: Boolean = false,
    val showVideoOutputOverrideDialog: Boolean = false,
    val videoOutputControllerStyle: ControllerStyle = ControllerStyle.Unknown,
    val videoOutputL2R2Style: L2R2Style = L2R2Style.Unknown,
    val pendingOverlap: Overlap? = null,
)

@HiltViewModel
class ProfilesViewModel @Inject constructor(private val prefs: SharedPrefsRepo, private val odinTools: OdinToolsDetector) : ViewModel() {

    private val _uiState = MutableStateFlow(ProfilesUiModel())
    val uiState: StateFlow<ProfilesUiModel> = _uiState.asStateFlow()

    fun refresh() {
        _uiState.update {
            it.copy(
                odinToolsInstalled = odinTools.isInstalled(),
                perAppControlsEnabled = prefs.isEnabled(Overlap.PER_APP_CONTROLS),
                appOverridesEnabled = prefs.appOverridesEnabled,
                overrideDelayEnabled = prefs.overrideDelay,
                videoOutputOverrideEnabled = prefs.videoOutputOverrideEnabled,
                videoOutputControllerStyle = ControllerStyle.getById(prefs.videoOutputControllerStyle),
                videoOutputL2R2Style = L2R2Style.getById(prefs.videoOutputL2R2Style),
            )
        }
    }

    fun appOverridesEnabled(newValue: Boolean) {
        prefs.appOverridesEnabled = newValue
        _uiState.update { it.copy(appOverridesEnabled = newValue) }
    }

    fun overrideDelayEnabled(newValue: Boolean) {
        prefs.overrideDelay = newValue
        _uiState.update { it.copy(overrideDelayEnabled = newValue) }
    }

    fun updateVideoOutputOverridePreference(newValue: Boolean) {
        if (CoexistencePolicy.needsConfirmation(newValue, _uiState.value.odinToolsInstalled)) {
            _uiState.update { it.copy(pendingOverlap = Overlap.EXTERNAL_DISPLAY_STYLE) }
            return
        }
        prefs.videoOutputOverrideEnabled = newValue
        _uiState.update { it.copy(videoOutputOverrideEnabled = newValue) }
    }

    fun confirmOverlap() {
        prefs.videoOutputOverrideEnabled = true
        _uiState.update { it.copy(pendingOverlap = null, videoOutputOverrideEnabled = true) }
    }

    fun dismissOverlap() {
        _uiState.update { it.copy(pendingOverlap = null) }
    }

    fun videoOutputOverrideClicked() {
        _uiState.update {
            it.copy(
                showVideoOutputOverrideDialog = true,
                videoOutputControllerStyle = ControllerStyle.getById(prefs.videoOutputControllerStyle),
                videoOutputL2R2Style = L2R2Style.getById(prefs.videoOutputL2R2Style),
            )
        }
    }

    fun videoOutputOverrideDialogDismissed() {
        _uiState.update { it.copy(showVideoOutputOverrideDialog = false) }
    }

    fun saveVideoOutputOverride(newControllerStyle: ControllerStyle, newL2R2Style: L2R2Style) {
        prefs.videoOutputControllerStyle = newControllerStyle.id
        prefs.videoOutputL2R2Style = newL2R2Style.id
        _uiState.update {
            it.copy(
                showVideoOutputOverrideDialog = false,
                videoOutputControllerStyle = newControllerStyle,
                videoOutputL2R2Style = newL2R2Style,
            )
        }
    }
}
