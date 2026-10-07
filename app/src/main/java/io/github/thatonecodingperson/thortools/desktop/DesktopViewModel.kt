package io.github.thatonecodingperson.thortools.desktop

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.thatonecodingperson.thortools.R
import io.github.thatonecodingperson.thortools.data.SharedPrefsRepo
import io.github.thatonecodingperson.thortools.hotkeys.Hotkey
import io.github.thatonecodingperson.thortools.hotkeys.LaunchableApp
import io.github.thatonecodingperson.thortools.hotkeys.LaunchableApps
import io.github.thatonecodingperson.thortools.tools.ShellExecutor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

/** The Desktop controls page's tabs. */
enum class DesktopTab(@StringRes val title: Int) {
    GENERAL(R.string.desktopTabGeneral),
    BUTTONS(R.string.desktopTabButtons),
    STICKS(R.string.desktopTabSticks),
}

data class DesktopUiModel(
    val enabled: Boolean = false,
    val tab: DesktopTab = DesktopTab.GENERAL,
    /** Off while the top screen shows a home screen or a game front end. */
    val offOnFrontEnds: Boolean = true,
    val apps: DesktopApps = DesktopApps(),
    val layout: DesktopLayout = DesktopLayout.DEFAULT,
    /** The button whose job is being picked. */
    val selected: DesktopControl = DesktopControl.R2,
    /** AYN's own mouse mode is on, which fights Thor Tools' pointer. */
    val aynMouseOn: Boolean = false,
    /** Android's own mouse speed (-7..7), which scales every mouse, this pointer too; null until read. */
    val androidSpeed: Int? = null,
    val appList: List<LaunchableApp> = emptyList(),
    val appsLoaded: Boolean = false,
    /** The list being edited in the app picker. */
    val picker: DesktopApps.Side? = null,
    /** The hotkeys, which come first where they use the same buttons. */
    val hotkeys: List<Hotkey> = emptyList(),
)

@HiltViewModel
class DesktopViewModel @Inject constructor(
    private val prefs: SharedPrefsRepo,
    private val executor: ShellExecutor,
    private val launchableApps: LaunchableApps,
) : ViewModel() {
    private val _uiState = MutableStateFlow(
        DesktopUiModel(
            enabled = prefs.desktopEnabled,
            offOnFrontEnds = prefs.desktopOffOnFrontEnds,
            apps = prefs.desktopApps,
            layout = prefs.desktopLayout,
        ),
    )
    val uiState: StateFlow<DesktopUiModel> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val apps = withContext(Dispatchers.IO) { launchableApps.load() }
            _uiState.update { it.copy(appList = apps, appsLoaded = true) }
        }
        viewModelScope.launch {
            prefs.hotkeyChanges().collect { list -> _uiState.update { it.copy(hotkeys = list) } }
        }
        viewModelScope.launch {
            prefs.desktopChanges().collect { settings -> _uiState.update { it.copy(enabled = settings.enabled) } }
        }
    }

    /** Reads AYN's mouse mode and Android's mouse speed again, e.g. after coming back from their settings. */
    fun refresh() {
        viewModelScope.launch {
            val (aynOn, speed) = withContext(Dispatchers.IO) {
                (executor.getIntSystemSetting(AYN_MOUSE, 0) == 1) to executor.getIntSystemSetting(ANDROID_SPEED, 0)
            }
            _uiState.update { it.copy(aynMouseOn = aynOn, androidSpeed = speed) }
        }
    }

    fun resetAndroidSpeed() {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { executor.setIntSystemSetting(ANDROID_SPEED, 0) }
            refresh()
        }
    }

    fun setEnabled(on: Boolean) {
        prefs.desktopEnabled = on
        _uiState.update { it.copy(enabled = on) }
    }

    fun turnOffAynMouse() {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { executor.setIntSystemSetting(AYN_MOUSE, 0) }
            refresh()
        }
    }

    fun select(control: DesktopControl) = _uiState.update { it.copy(selected = control) }

    fun selectTab(tab: DesktopTab) = _uiState.update { it.copy(tab = tab) }

    fun setOffOnFrontEnds(on: Boolean) {
        prefs.desktopOffOnFrontEnds = on
        _uiState.update { it.copy(offOnFrontEnds = on) }
    }

    fun setJob(job: DesktopJob) = change { it.with(_uiState.value.selected, job) }

    fun usePreset(preset: DesktopPreset) = change { it.withButtonsOf(preset) }

    fun resetAll() = change { DesktopLayout.DEFAULT }

    fun setLeftStick(role: StickRole) = change { it.copy(leftStick = role) }

    fun setRightStick(role: StickRole) = change { it.copy(rightStick = role) }

    fun setAcceleration(acceleration: Acceleration) = change { it.copy(acceleration = acceleration) }

    fun setInvertY(on: Boolean) = change { it.copy(invertY = on) }

    fun setNaturalScroll(on: Boolean) = change { it.copy(naturalScroll = on) }

    fun setHorizontalScroll(on: Boolean) = change { it.copy(horizontalScroll = on) }

    fun setHoldStart(on: Boolean) = change { it.copy(holdStartSwitch = on) }

    fun setAynPointer(on: Boolean) = change { it.copy(aynPointer = on) }

    fun setBottomScreen(on: Boolean) = change { it.copy(bottomScreen = on) }

    /** [control] given to the bottom screen's app while the controller stays there, or back to its job. */
    fun toggleBottomButton(control: DesktopControl) = change { layout ->
        val buttons = layout.bottomButtons
        layout.copy(bottomButtons = if (control in buttons) buttons - control else buttons + control)
    }

    /** The chosen button as the slow-down button, or no slow-down button. One button at most has the job. */
    fun setPrecision(on: Boolean) = change { layout ->
        val selected = _uiState.value.selected
        when {
            on -> layout.copy(precision = selected)
            layout.precision == selected -> layout.copy(precision = null)
            else -> layout
        }
    }

    /** A slider while it moves: shown at once, kept when it is let go ([commit]). */
    fun moveSpeed(value: Float) = preview { it.copy(speed = value) }

    fun moveScrollSpeed(value: Float) = preview { it.copy(scrollSpeed = value) }

    fun moveDeadZone(value: Float) = preview { it.copy(deadZone = value) }

    fun moveThreshold(value: Float) = preview { it.copy(triggerThreshold = value) }

    fun commit() {
        prefs.desktopLayout = _uiState.value.layout
    }

    fun openPicker(side: DesktopApps.Side) = _uiState.update { it.copy(picker = side) }

    fun closePicker() = _uiState.update { it.copy(picker = null) }

    /** A tap on an app in the picker: on or off the list being edited, unless it is on the other list. */
    fun toggleApp(packageName: String) {
        val side = _uiState.value.picker ?: return
        val change = _uiState.value.apps.toggle(side, packageName) as? DesktopApps.Change.Done ?: return
        prefs.desktopApps = change.apps
        _uiState.update { it.copy(apps = change.apps) }
    }

    private fun preview(update: (DesktopLayout) -> DesktopLayout) = _uiState.update { it.copy(layout = update(it.layout)) }

    private fun change(update: (DesktopLayout) -> DesktopLayout) {
        val layout = update(_uiState.value.layout)
        prefs.desktopLayout = layout
        _uiState.update { it.copy(layout = layout) }
    }

    private companion object {
        const val AYN_MOUSE = "global_gamepad_to_mouse_mode"
        const val ANDROID_SPEED = "pointer_speed"
    }
}
