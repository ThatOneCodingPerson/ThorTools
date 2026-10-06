package io.github.thatonecodingperson.thortools.oled

import android.view.Display
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.thatonecodingperson.thortools.data.SharedPrefsRepo
import io.github.thatonecodingperson.thortools.input.Screen
import io.github.thatonecodingperson.thortools.service.ServiceStatus
import io.github.thatonecodingperson.thortools.tools.ShellExecutor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

data class OledUiModel(
    val choices: OledChoices = OledChoices(),
    /** AYN's values as the user chose them (while Thor Tools' own run, AYN's are off); null until read. */
    val ayn: AynProtection? = null,
    /** AYN's values couldn't be read (no root). */
    val aynUnreadable: Boolean = false,
    /** The last change reached AYN's settings but not the system yet (the root helper isn't running). */
    val aynPending: Boolean = false,
    val helperRunning: Boolean = true,
    /** Still areas protected now: the share of each screen, in percent. */
    val areasNow: Map<Screen, Int> = emptyMap(),
)

@HiltViewModel
class OledViewModel @Inject constructor(
    private val prefs: SharedPrefsRepo,
    private val executor: ShellExecutor,
    private val status: ServiceStatus,
) : ViewModel() {

    private val _uiState = MutableStateFlow(OledUiModel(choices = prefs.oledChoices))
    val uiState: StateFlow<OledUiModel> = _uiState.asStateFlow()
    private var applying: Job? = null

    init {
        viewModelScope.launch {
            status.oledAreas.collect { byDisplay ->
                val now = byDisplay.mapNotNull { (displayId, percent) ->
                    val screen = if (displayId == Display.DEFAULT_DISPLAY) Screen.TOP else Screen.BOTTOM
                    (screen to percent).takeIf { percent > 0 }
                }.toMap()
                _uiState.update { it.copy(areasNow = now) }
            }
        }
    }

    fun refresh() {
        _uiState.update { it.copy(helperRunning = status.rawInput?.connected == true) }
        viewModelScope.launch {
            val text = withContext(Dispatchers.IO) { executor.executeAsRoot("settings get system ${AynProtection.SETTING}") }
            val saved = prefs.oledAynSaved?.let(AynProtection::decode)
            _uiState.update { state ->
                text.fold(
                    onSuccess = { state.copy(ayn = saved ?: AynProtection.fromSetting(it), aynUnreadable = false) },
                    onFailure = { state.copy(aynUnreadable = true) },
                )
            }
        }
    }

    fun change(update: (OledChoices) -> OledChoices) {
        val before = _uiState.value.choices
        val choices = update(before)
        prefs.oledChoices = choices
        _uiState.update { it.copy(choices = choices) }
        if (choices.shift != before.shift || choices.refreshAuto != before.refreshAuto) takeOver(choices)
    }

    /** Shown at once; handed to AYN's settings and the system a moment after the last change, so a slider sends once. */
    fun changeAyn(update: (AynProtection) -> AynProtection) {
        val current = _uiState.value.ayn ?: return
        val next = update(current)
        _uiState.update { it.copy(ayn = next) }
        if (prefs.oledAynSaved != null) prefs.oledAynSaved = next.encode()
        val choices = _uiState.value.choices
        applyAyn(next.applied(choices.shift, choices.refreshAuto), APPLY_DELAY_MS)
    }

    /**
     * Thor Tools' shifter or automatic refresher switched on: AYN's own is switched off (two would fight) and the user's
     * AYN values are kept; once neither runs, they all go back.
     */
    private fun takeOver(own: OledChoices) {
        val chosen = _uiState.value.ayn ?: return
        prefs.oledAynSaved = if (own.shift || own.refreshAuto) chosen.encode() else null
        applyAyn(chosen.applied(own.shift, own.refreshAuto), 0)
    }

    private fun applyAyn(values: AynProtection, delayMs: Long) {
        applying?.cancel()
        applying = viewModelScope.launch {
            delay(delayMs)
            // Kept in AYN's own setting too, so AYN's page shows it and AYN's app sends the same after a restart.
            withContext(Dispatchers.IO) { executor.executeAsRoot("settings put system ${AynProtection.SETTING} ${values.encode()}") }
            val sent = status.rawInput?.command("aynprotect", *values.values().map(Int::toString).toTypedArray()) { _, _ -> } == true
            _uiState.update { it.copy(aynPending = !sent) }
        }
    }

    fun aynRefreshNow() {
        status.rawInput?.command("aynrefresh") { _, _ -> }
    }

    fun refreshNow() {
        status.refreshScreens?.invoke()
    }

    private companion object {
        const val APPLY_DELAY_MS = 500L
    }
}
