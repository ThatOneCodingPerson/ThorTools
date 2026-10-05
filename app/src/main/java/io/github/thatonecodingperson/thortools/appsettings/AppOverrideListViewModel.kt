package io.github.thatonecodingperson.thortools.appsettings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.thatonecodingperson.thortools.data.AppOverrideDao
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AppOverrideListViewModel @Inject constructor(
    private val appOverrideDao: AppOverrideDao,
    private val appOverrideMapper: AppOverrideMapper,
) : ViewModel() {

    private val _uiState = MutableStateFlow(AppOverrideListUiModel())
    val uiState: StateFlow<AppOverrideListUiModel> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            appOverrideDao.getAll()
                // Loading every app's label and icon is slow, so it stays off the main thread.
                .map { overrides -> appOverrideMapper.mapAppOverrides(overrides) to appOverrideMapper.mapOverrideCandidates(overrides) }
                .flowOn(Dispatchers.IO)
                .collect { (overrideList, candidates) ->
                    _uiState.update { it.copy(overrideList = overrideList, overrideCandidates = candidates) }
                }
        }
    }

    fun addClicked() {
        _uiState.update {
            it.copy(showAppSelectDialog = true)
        }
    }

    fun dismissAppSelectDialog() {
        _uiState.update {
            it.copy(showAppSelectDialog = false)
        }
    }

    /**
     * System settings:
     * performance_mode: standard 0, performance 1, high performance 2
     * fan_mode: disabled 0, quiet 1, (balance 2), (performance 3), smart 4, sport 5, custom 6
     */
}
