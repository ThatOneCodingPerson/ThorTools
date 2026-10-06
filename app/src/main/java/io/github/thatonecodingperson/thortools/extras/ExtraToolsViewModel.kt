package io.github.thatonecodingperson.thortools.extras

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.thatonecodingperson.thortools.data.SharedPrefsRepo
import io.github.thatonecodingperson.thortools.leds.LedMode
import io.github.thatonecodingperson.thortools.oled.OledChoices
import io.github.thatonecodingperson.thortools.tools.ShellExecutor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

data class ExtraToolsUiModel(
    val ledMode: LedMode = LedMode.AYN,
    val oled: OledChoices = OledChoices(),
    /** Null until read. */
    val keyboard: KeyboardPlace? = null,
    /** The last change didn't take (PServer refused it). */
    val keyboardFailed: Boolean = false,
)

@HiltViewModel
class ExtraToolsViewModel @Inject constructor(private val prefs: SharedPrefsRepo, private val executor: ShellExecutor) : ViewModel() {

    private val _uiState = MutableStateFlow(ExtraToolsUiModel())
    val uiState: StateFlow<ExtraToolsUiModel> = _uiState.asStateFlow()

    fun refresh() {
        _uiState.update { it.copy(ledMode = prefs.ledLook.mode, oled = prefs.oledChoices) }
        viewModelScope.launch {
            val place = withContext(Dispatchers.IO) { KeyboardPlace.read(executor) }
            _uiState.update { it.copy(keyboard = place) }
        }
    }

    fun setKeyboard(place: KeyboardPlace) {
        _uiState.update { it.copy(keyboard = place, keyboardFailed = false) }
        viewModelScope.launch {
            val now = withContext(Dispatchers.IO) { KeyboardPlace.write(executor, place) }
            _uiState.update { it.copy(keyboard = now, keyboardFailed = now != place) }
        }
    }
}
