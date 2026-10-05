package io.github.thatonecodingperson.thortools.lid

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.thatonecodingperson.thortools.data.SharedPrefsRepo
import io.github.thatonecodingperson.thortools.hotkeys.LaunchableApp
import io.github.thatonecodingperson.thortools.hotkeys.LaunchableApps
import io.github.thatonecodingperson.thortools.service.ServiceStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

data class LidUiModel(
    val choices: LidChoices = LidChoices(),
    val last: LidResult? = null,
    val helperRunning: Boolean = true,
    /** The names of the apps that keep the buttons on, for the card. */
    val keepButtonsNames: List<String> = emptyList(),
    val pickerOpen: Boolean = false,
    val apps: List<LaunchableApp> = emptyList(),
    val appsLoaded: Boolean = false,
)

@HiltViewModel
class LidViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val prefs: SharedPrefsRepo,
    private val status: ServiceStatus,
    private val launchableApps: LaunchableApps,
) : ViewModel() {

    private val _uiState = MutableStateFlow(LidUiModel(choices = prefs.lidChoices))
    val uiState: StateFlow<LidUiModel> = _uiState.asStateFlow()

    init {
        viewModelScope.launch { prefs.lidResultChanges().collect { last -> _uiState.update { it.copy(last = last) } } }
        loadNames()
    }

    fun refresh() = _uiState.update { it.copy(helperRunning = status.rawInput?.connected == true) }

    fun change(update: (LidChoices) -> LidChoices) {
        val choices = update(_uiState.value.choices)
        prefs.lidChoices = choices
        _uiState.update { it.copy(choices = choices) }
    }

    fun openPicker() {
        _uiState.update { it.copy(pickerOpen = true) }
        if (_uiState.value.appsLoaded) return
        viewModelScope.launch {
            val apps = withContext(Dispatchers.IO) { launchableApps.load() }
            _uiState.update { it.copy(apps = apps, appsLoaded = true) }
        }
    }

    fun closePicker() {
        _uiState.update { it.copy(pickerOpen = false) }
        loadNames()
    }

    /** Ticks or unticks an app that keeps the buttons on. */
    fun pickApp(packageName: String) = change { choices ->
        val apps = choices.keepButtonsFor
        choices.copy(keepButtonsFor = if (packageName in apps) apps - packageName else apps + packageName)
    }

    private fun loadNames() {
        val packages = _uiState.value.choices.keepButtonsFor
        viewModelScope.launch {
            val names = withContext(Dispatchers.IO) {
                val packageManager = context.packageManager
                packages.map { name ->
                    runCatching {
                        packageManager.getApplicationLabel(packageManager.getApplicationInfo(name, 0)).toString()
                    }.getOrDefault(name)
                }.sortedBy { it.lowercase() }
            }
            _uiState.update { it.copy(keepButtonsNames = names) }
        }
    }
}
