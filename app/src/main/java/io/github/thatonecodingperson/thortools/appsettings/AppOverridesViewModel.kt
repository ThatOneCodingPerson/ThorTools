package io.github.thatonecodingperson.thortools.appsettings

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.thatonecodingperson.thortools.coexist.OdinToolsDetector
import io.github.thatonecodingperson.thortools.coexist.Overlap
import io.github.thatonecodingperson.thortools.data.AppOverrideDao
import io.github.thatonecodingperson.thortools.data.AppOverrideEntity
import io.github.thatonecodingperson.thortools.data.SharedPrefsRepo
import io.github.thatonecodingperson.thortools.leds.LedPreset
import io.github.thatonecodingperson.thortools.models.AppRefreshRate
import io.github.thatonecodingperson.thortools.models.AppVibration
import io.github.thatonecodingperson.thortools.models.BottomScreenRule
import io.github.thatonecodingperson.thortools.models.ControllerStyle
import io.github.thatonecodingperson.thortools.models.FanMode
import io.github.thatonecodingperson.thortools.models.FanMode.Companion.getDisabledFanModes
import io.github.thatonecodingperson.thortools.models.L2R2Style
import io.github.thatonecodingperson.thortools.models.NoChange
import io.github.thatonecodingperson.thortools.models.PerfMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@HiltViewModel
class AppOverridesViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val appOverrideDao: AppOverrideDao,
    private val appOverrideMapper: AppOverrideMapper,
    private val prefs: SharedPrefsRepo,
    private val odinTools: OdinToolsDetector,
) : ViewModel() {

    private val _uiState = MutableStateFlow(AppOverridesUiModel())
    val uiState: StateFlow<AppOverridesUiModel> = _uiState.asStateFlow()

    private val packageName = checkNotNull(savedStateHandle.get<String>("packageName"))
    private var initialControllerStyle = NoChange.KEY
    private var initialL2R2Style = NoChange.KEY
    private var initialPerfMode = NoChange.KEY
    private var initialFanMode = NoChange.KEY
    private var initialRefreshRate = NoChange.KEY
    private var initialBottomScreen = NoChange.KEY
    private var initialVibration = NoChange.KEY
    private var initialLeds = NoChange.KEY

    init {
        viewModelScope.launch {
            val app = withContext(Dispatchers.IO) {
                appOverrideDao.getForPackage(packageName)
            }

            val uiModel = if (app == null) {
                appOverrideMapper.mapEmptyOverride(packageName)
            } else {
                initialControllerStyle = app.controllerStyle ?: NoChange.KEY
                initialL2R2Style = app.l2R2Style ?: NoChange.KEY
                initialPerfMode = app.perfMode ?: NoChange.KEY
                initialFanMode = app.fanMode ?: NoChange.KEY
                initialRefreshRate = app.refreshRate ?: NoChange.KEY
                initialBottomScreen = app.bottomScreen ?: NoChange.KEY
                initialVibration = app.vibration ?: NoChange.KEY
                initialLeds = LedPreset.of(app.leds)?.id ?: NoChange.KEY

                appOverrideMapper.mapAppOverride(app)
            }

            _uiState.update {
                it.copy(
                    app = uiModel,
                    isNewApp = app == null,
                    disabledFanModeKeys = getDisabledFanModes(initialPerfMode),
                    leftToOdinTools = odinTools.isInstalled() && !prefs.isEnabled(Overlap.PER_APP_CONTROLS),
                )
            }
        }
    }

    fun saveClicked() {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                appOverrideDao.save(
                    AppOverrideEntity(
                        packageName = packageName,
                        controllerStyle = _uiState.value.app?.controllerStyle?.id,
                        l2R2Style = _uiState.value.app?.l2r2Style?.id,
                        perfMode = _uiState.value.app?.perfMode?.id,
                        fanMode = _uiState.value.app?.fanMode?.id,
                        refreshRate = _uiState.value.app?.refreshRate?.id,
                        bottomScreen = _uiState.value.app?.bottomScreen?.id,
                        vibration = _uiState.value.app?.vibration?.id,
                        leds = _uiState.value.app?.leds?.look?.encode(),
                    ),
                )
            }
        }
        _uiState.update {
            it.copy(navigateBack = true)
        }
    }

    fun deleteClicked() {
        _uiState.update {
            it.copy(showDeleteConfirmDialog = true)
        }
    }

    fun deleteDismissed() {
        _uiState.update {
            it.copy(showDeleteConfirmDialog = false)
        }
    }

    fun deleteConfirmed() {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                appOverrideDao.deleteByPackageName(packageName)
            }
        }
        _uiState.update {
            it.copy(showDeleteConfirmDialog = false, navigateBack = true)
        }
    }

    fun controllerStyleSelected(key: String) {
        _uiState.update {
            it.copy(
                app = it.app?.copy(controllerStyle = ControllerStyle.getById(key)),
                hasUnsavedChanges = hasUnsavedChanges(controllerStyle = key),
            )
        }
    }

    fun l2R2StyleSelected(key: String) {
        _uiState.update {
            it.copy(
                app = it.app?.copy(l2r2Style = L2R2Style.getById(key)),
                hasUnsavedChanges = hasUnsavedChanges(l2R2Style = key),
            )
        }
    }

    fun perfModeSelected(key: String) {
        val perfMode = PerfMode.getById(key)
        val disabledFanModes = getDisabledFanModes(key)

        val currentFanMode = if (key == NoChange.KEY) {
            null
        } else {
            _uiState.value.app?.fanMode
        }

        val fixedFanMode = if (currentFanMode == null || currentFanMode.id in disabledFanModes) {
            systemFanPerfModeMapping[perfMode]
        } else {
            currentFanMode
        }

        _uiState.update {
            it.copy(
                app = it.app?.copy(perfMode = perfMode, fanMode = fixedFanMode),
                hasUnsavedChanges = hasUnsavedChanges(perfMode = key, fanMode = fixedFanMode?.id),
                disabledFanModeKeys = disabledFanModes,
            )
        }
    }

    fun fanModeSelected(key: String) {
        _uiState.update {
            it.copy(
                app = it.app?.copy(fanMode = FanMode.getById(key)),
                hasUnsavedChanges = hasUnsavedChanges(fanMode = key),
            )
        }
    }

    fun refreshRateSelected(key: String) {
        _uiState.update {
            it.copy(
                app = it.app?.copy(refreshRate = AppRefreshRate.byId(key)),
                hasUnsavedChanges = hasUnsavedChanges(refreshRate = key),
            )
        }
    }

    fun ledsSelected(key: String) {
        _uiState.update {
            it.copy(
                app = it.app?.copy(leds = LedPreset.byId(key)),
                hasUnsavedChanges = hasUnsavedChanges(leds = key),
            )
        }
    }

    fun vibrationSelected(key: String) {
        _uiState.update {
            it.copy(
                app = it.app?.copy(vibration = AppVibration.byId(key)),
                hasUnsavedChanges = hasUnsavedChanges(vibration = key),
            )
        }
    }

    fun bottomScreenSelected(key: String) {
        _uiState.update {
            it.copy(
                app = it.app?.copy(bottomScreen = BottomScreenRule.byId(key)),
                hasUnsavedChanges = hasUnsavedChanges(bottomScreen = key),
            )
        }
    }

    private fun hasUnsavedChanges(
        controllerStyle: String? = null,
        l2R2Style: String? = null,
        perfMode: String? = null,
        fanMode: String? = null,
        refreshRate: String? = null,
        bottomScreen: String? = null,
        vibration: String? = null,
        leds: String? = null,
    ): Boolean = listOf(
        (controllerStyle ?: _uiState.value.app?.controllerStyle?.id) != initialControllerStyle,
        (l2R2Style ?: _uiState.value.app?.l2r2Style?.id) != initialL2R2Style,
        (perfMode ?: _uiState.value.app?.perfMode?.id) != initialPerfMode,
        (fanMode ?: _uiState.value.app?.fanMode?.id) != initialFanMode,
        (refreshRate ?: _uiState.value.app?.refreshRate?.id ?: NoChange.KEY) != initialRefreshRate,
        (bottomScreen ?: _uiState.value.app?.bottomScreen?.id ?: NoChange.KEY) != initialBottomScreen,
        (vibration ?: _uiState.value.app?.vibration?.id ?: NoChange.KEY) != initialVibration,
        (leds ?: _uiState.value.app?.leds?.id ?: NoChange.KEY) != initialLeds,
    ).any { it }

    companion object {
        private val systemFanPerfModeMapping = mapOf(
            PerfMode.Standard to FanMode.Off,
            PerfMode.Performance to FanMode.Quiet,
            PerfMode.HighPerformance to FanMode.Sport,
        )
    }
}
