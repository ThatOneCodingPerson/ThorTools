package io.github.thatonecodingperson.thortools.coexist

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.thatonecodingperson.thortools.data.SharedPrefsRepo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject

data class CoexistenceUiModel(
    val odinTools: OdinToolsState = OdinToolsState.Absent,
    val enabled: Map<Overlap, Boolean> = emptyMap(),
    val pendingConfirmation: Overlap? = null,
)

@HiltViewModel
class CoexistenceViewModel @Inject constructor(private val detector: OdinToolsDetector, private val prefs: SharedPrefsRepo) : ViewModel() {

    private val _uiState = MutableStateFlow(CoexistenceUiModel())
    val uiState: StateFlow<CoexistenceUiModel> = _uiState.asStateFlow()

    fun refresh() {
        _uiState.update {
            it.copy(
                odinTools = detector.state(),
                enabled = Overlap.entries.associateWith(prefs::isEnabled),
            )
        }
    }

    fun toggle(overlap: Overlap, enabled: Boolean) {
        if (CoexistencePolicy.needsConfirmation(enabled, _uiState.value.odinTools.installed)) {
            _uiState.update { it.copy(pendingConfirmation = overlap) }
        } else {
            store(overlap, enabled)
        }
    }

    fun confirm() {
        _uiState.value.pendingConfirmation?.let { store(it, true) }
        dismissConfirmation()
    }

    fun dismissConfirmation() {
        _uiState.update { it.copy(pendingConfirmation = null) }
    }

    private fun store(overlap: Overlap, enabled: Boolean) {
        prefs.setEnabled(overlap, enabled)
        refresh()
    }
}
