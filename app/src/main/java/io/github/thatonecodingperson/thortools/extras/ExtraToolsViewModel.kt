package io.github.thatonecodingperson.thortools.extras

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.thatonecodingperson.thortools.data.SharedPrefsRepo
import io.github.thatonecodingperson.thortools.leds.LedMode
import io.github.thatonecodingperson.thortools.navigation.GestureNav
import io.github.thatonecodingperson.thortools.navigation.GestureWish
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
    /** What the gestures should be now, whoever decides (the page, a profile, desktop controls, the action). */
    val gestures: GestureWish = GestureWish(),
)

@HiltViewModel
class ExtraToolsViewModel @Inject constructor(
    private val prefs: SharedPrefsRepo,
    private val executor: ShellExecutor,
    private val gestureNav: GestureNav,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ExtraToolsUiModel())
    val uiState: StateFlow<ExtraToolsUiModel> = _uiState.asStateFlow()

    init {
        viewModelScope.launch { gestureNav.state.collect { nav -> _uiState.update { it.copy(gestures = nav.wish) } } }
    }

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
