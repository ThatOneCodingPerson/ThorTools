package io.github.thatonecodingperson.thortools.navigation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class GestureNavUiModel(
    val choice: GestureChoice = GestureChoice(),
    val nav: GestureNavState = GestureNavState(),
    /** The bar switch just tapped shows its new place until the next reading. */
    val topBarTapped: Boolean? = null,
) {
    val topBarHidden: Boolean get() = topBarTapped ?: nav.reading?.topBarHidden ?: false
}

@HiltViewModel
class GestureNavViewModel @Inject constructor(private val gestureNav: GestureNav) : ViewModel() {

    private val _uiState = MutableStateFlow(GestureNavUiModel(gestureNav.state.value.wish.choice, gestureNav.state.value))
    val uiState: StateFlow<GestureNavUiModel> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            gestureNav.state.collect { nav ->
                _uiState.update { it.copy(choice = nav.wish.choice, nav = nav, topBarTapped = null) }
            }
        }
    }

    fun refresh() = gestureNav.refresh()

    fun setOn(on: Boolean) = setChoice(_uiState.value.choice.copy(on = on))

    fun setStopHome(stop: Boolean) = setChoice(_uiState.value.choice.withStopHome(stop))

    fun setStopBack(stop: Boolean) = setChoice(_uiState.value.choice.withStopBack(stop))

    fun setOffWithDesktop(off: Boolean) = setChoice(_uiState.value.choice.copy(offWithDesktop = off))

    fun setTopBarHidden(hidden: Boolean) {
        _uiState.update { it.copy(topBarTapped = hidden) }
        gestureNav.setTopBarHidden(hidden)
    }

    private fun setChoice(choice: GestureChoice) {
        _uiState.update { it.copy(choice = choice) }
        gestureNav.setChoice(choice)
    }
}
