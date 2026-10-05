package io.github.thatonecodingperson.thortools.lid

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.thatonecodingperson.thortools.data.SharedPrefsRepo
import io.github.thatonecodingperson.thortools.service.ServiceStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class LidUiModel(val choices: LidChoices = LidChoices(), val last: LidResult? = null, val helperRunning: Boolean = true)

@HiltViewModel
class LidViewModel @Inject constructor(private val prefs: SharedPrefsRepo, private val status: ServiceStatus) : ViewModel() {

    private val _uiState = MutableStateFlow(LidUiModel(choices = prefs.lidChoices))
    val uiState: StateFlow<LidUiModel> = _uiState.asStateFlow()

    init {
        viewModelScope.launch { prefs.lidResultChanges().collect { last -> _uiState.update { it.copy(last = last) } } }
    }

    fun refresh() = _uiState.update { it.copy(helperRunning = status.rawInput?.connected == true) }

    fun change(update: (LidChoices) -> LidChoices) {
        val choices = update(_uiState.value.choices)
        prefs.lidChoices = choices
        _uiState.update { it.copy(choices = choices) }
    }
}
