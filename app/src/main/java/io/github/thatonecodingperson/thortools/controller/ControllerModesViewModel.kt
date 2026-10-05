package io.github.thatonecodingperson.thortools.controller

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.thatonecodingperson.thortools.data.SharedPrefsRepo
import io.github.thatonecodingperson.thortools.models.ControllerStyle
import io.github.thatonecodingperson.thortools.models.CycleChoice
import io.github.thatonecodingperson.thortools.models.L2R2Style
import io.github.thatonecodingperson.thortools.tools.ShellExecutor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * The modes in use (read through PServer; Unknown until read or when unreadable) and the one each switching skips
 * (stored ids, null for none).
 */
data class ControllerModesUiModel(
    val style: ControllerStyle = ControllerStyle.Unknown,
    val l2r2: L2R2Style = L2R2Style.Unknown,
    val loaded: Boolean = false,
    val skippedStyle: String? = null,
    val skippedL2r2: String? = null,
)

@HiltViewModel
class ControllerModesViewModel @Inject constructor(private val prefs: SharedPrefsRepo, private val executor: ShellExecutor) :
    ViewModel() {

    private val _uiState = MutableStateFlow(
        ControllerModesUiModel(skippedStyle = prefs.disabledControllerStyle, skippedL2r2 = prefs.disabledL2r2Style),
    )
    val uiState: StateFlow<ControllerModesUiModel> = _uiState.asStateFlow()

    fun refresh() {
        viewModelScope.launch {
            val (style, l2r2) = withContext(Dispatchers.IO) { ControllerStyle.readOrUnknown(executor) to L2R2Style.readOrUnknown(executor) }
            _uiState.update { it.copy(style = style, l2r2 = l2r2, loaded = true) }
        }
    }

    /** Switches to [style] now, the same way the quick settings tile does (PServer, off the main thread). */
    fun useStyle(style: ControllerStyle) {
        if (style == ControllerStyle.Unknown) return
        _uiState.update { it.copy(style = style) }
        viewModelScope.launch {
            withContext(Dispatchers.IO) { style.enable(executor) }
            refresh()
        }
    }

    fun useL2r2(style: L2R2Style) {
        if (style == L2R2Style.Unknown) return
        _uiState.update { it.copy(l2r2 = style) }
        viewModelScope.launch {
            withContext(Dispatchers.IO) { style.enable(executor) }
            refresh()
        }
    }

    fun setStyleInCycle(id: String, include: Boolean) {
        val skipped = CycleChoice.toggled(_uiState.value.skippedStyle, id, include)
        prefs.disabledControllerStyle = skipped
        _uiState.update { it.copy(skippedStyle = skipped) }
    }

    fun setL2r2InCycle(id: String, include: Boolean) {
        val skipped = CycleChoice.toggled(_uiState.value.skippedL2r2, id, include)
        prefs.disabledL2r2Style = skipped
        _uiState.update { it.copy(skippedL2r2 = skipped) }
    }
}
