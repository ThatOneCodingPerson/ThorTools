package io.github.thatonecodingperson.thortools.controller

import android.view.KeyEvent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.thatonecodingperson.thortools.coexist.OdinToolsDetector
import io.github.thatonecodingperson.thortools.data.SharedPrefsRepo
import io.github.thatonecodingperson.thortools.hotkeys.HotkeyList
import io.github.thatonecodingperson.thortools.models.ControllerStyle
import io.github.thatonecodingperson.thortools.models.L2R2Style
import io.github.thatonecodingperson.thortools.tools.DeviceType
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

data class ControllerUiModel(
    val odinToolsInstalled: Boolean = false,
    val singlePressHomeEnabled: Boolean = false,
    val deviceType: DeviceType = DeviceType.THOR,
    /** The modes in use now (read through PServer); Unknown until read or when unreadable. */
    val controllerStyleNow: ControllerStyle = ControllerStyle.Unknown,
    val l2r2StyleNow: L2R2Style = L2R2Style.Unknown,
    /** The Odin 2 back button being remapped, as AYN's setting key. */
    val remapSetting: String? = null,
    val remapKeyCode: Int = 0,
    /** Hotkeys on Home that turning single-press Home off would block; asked about first while above 0. */
    val confirmHomeOff: Int = 0,
)

@HiltViewModel
class ControllerViewModel @Inject constructor(
    private val settings: SettingsRepo,
    private val prefs: SharedPrefsRepo,
    private val odinTools: OdinToolsDetector,
    private val executor: ShellExecutor,
    deviceUtils: DeviceUtils,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ControllerUiModel())
    val uiState: StateFlow<ControllerUiModel> = _uiState.asStateFlow()

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
                singlePressHomeEnabled = !settings.preventPressHome,
            )
        }
        readModes()
    }

    private fun readModes() {
        viewModelScope.launch {
            val (style, l2r2) = withContext(Dispatchers.IO) { ControllerStyle.readOrUnknown(executor) to L2R2Style.readOrUnknown(executor) }
            _uiState.update { it.copy(controllerStyleNow = style, l2r2StyleNow = l2r2) }
        }
    }

    /** The Odin 2's M1/M2 back buttons are remapped by AYN itself; Thor Tools only edits AYN's setting. */
    fun remapClicked(setting: String) {
        viewModelScope.launch {
            val keyCode = withContext(Dispatchers.IO) { executor.getIntSystemSetting(setting, 0) }
            _uiState.update { it.copy(remapSetting = setting, remapKeyCode = keyCode) }
        }
    }

    fun remapDismissed() {
        _uiState.update { it.copy(remapSetting = null) }
    }

    fun remapReset(setting: String) {
        val default = when (setting) {
            SettingsRepo.KEY_CUSTOM_M1_VALUE -> KeyEvent.KEYCODE_BUTTON_C
            SettingsRepo.KEY_CUSTOM_M2_VALUE -> KeyEvent.KEYCODE_BUTTON_Z
            else -> KeyEvent.KEYCODE_UNKNOWN
        }
        remapSave(setting, default)
    }

    fun remapSave(setting: String, keyCode: Int) {
        _uiState.update { it.copy(remapSetting = null) }
        viewModelScope.launch(Dispatchers.IO) { executor.setIntSystemSetting(setting, keyCode) }
    }

    fun updateSinglePressHomePreference(newValue: Boolean) {
        val blocked = HotkeyList.usingHome(prefs.hotkeys)
        if (!newValue && blocked > 0) {
            _uiState.update { it.copy(confirmHomeOff = blocked) }
            return
        }
        writeSinglePressHome(newValue)
    }

    fun homeOffConfirmed() {
        _uiState.update { it.copy(confirmHomeOff = 0) }
        writeSinglePressHome(false)
    }

    fun homeOffDismissed() = _uiState.update { it.copy(confirmHomeOff = 0) }

    private fun writeSinglePressHome(on: Boolean) {
        // Invert here as prevent == double press; written through PServer, so off the main thread.
        viewModelScope.launch(Dispatchers.IO) { settings.preventPressHome = !on }
        _uiState.update { it.copy(singlePressHomeEnabled = on) }
    }
}
