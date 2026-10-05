package io.github.thatonecodingperson.thortools.debug

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.thatonecodingperson.thortools.actions.ActionCategory
import io.github.thatonecodingperson.thortools.actions.ThorAction
import io.github.thatonecodingperson.thortools.data.SharedPrefsRepo
import io.github.thatonecodingperson.thortools.service.ServiceStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

enum class DebugTab { ACTIONS, HOTKEYS }

data class DebugUiModel(
    val tab: DebugTab = DebugTab.ACTIONS,
    val serviceRunning: Boolean = true,
    /** A risky check waiting for the user's OK. */
    val confirm: ThorAction? = null,
    val hotkeyType: HotkeyType = HotkeyType.TAPS,
    val rows: List<HotkeyRow> = emptyList(),
    val runActions: Boolean = false,
    val reportFile: Boolean = true,
    /** The results file's text while it is shown; null when hidden. */
    val reportText: String? = null,
)

@HiltViewModel
class DebugViewModel @Inject constructor(
    private val session: DebugSession,
    private val prefs: SharedPrefsRepo,
    private val status: ServiceStatus,
) : ViewModel() {

    private val _uiState = MutableStateFlow(DebugUiModel(reportFile = prefs.debugReportFile))
    val uiState: StateFlow<DebugUiModel> = _uiState.asStateFlow()

    val results = session.results
    val busy = session.busy
    val asking = session.asking
    val padShown = session.padShown
    val events = session.events
    val probe = session.probe

    init {
        viewModelScope.launch { session.events.collect { updateRows() } }
    }

    fun refresh() {
        _uiState.update { it.copy(serviceRunning = status.connected && session.runner != null) }
        updateRows()
    }

    fun selectTab(tab: DebugTab) = _uiState.update { it.copy(tab = tab) }

    fun runAll() = start(ActionChecks.runAll.filter { it.kind == CheckKind.AUTO }, clear = true)

    fun runWatch() = start(ActionChecks.ALL.filter { it.kind == CheckKind.WATCH })

    fun runGroup(category: ActionCategory) = start(ActionChecks.ALL.filter { it.action.category == category && it.kind != CheckKind.RISKY })

    fun runFailed() {
        val failed = results.value.filterValues { it.state == CheckState.FAIL }.keys
        start(ActionChecks.ALL.filter { it.action in failed })
    }

    /** A tap on a row: risky checks ask first. */
    fun runOne(action: ThorAction) {
        val check = ActionChecks.of(action) ?: return
        if (check.kind == CheckKind.RISKY) _uiState.update { it.copy(confirm = action) } else start(listOf(check))
    }

    fun confirmRisky() {
        val action = _uiState.value.confirm ?: return
        _uiState.update { it.copy(confirm = null) }
        ActionChecks.of(action)?.let { start(listOf(it)) }
    }

    fun dismissRisky() = _uiState.update { it.copy(confirm = null) }

    fun stop() {
        session.runner?.stop()
    }

    fun answer(yes: Boolean) = session.answer(yes)

    /** The gesture pad saw a swipe. */
    fun swiped(direction: String) {
        session.lastSwipe = direction
    }

    fun startHotkeyCheck() = session.startProbe(_uiState.value.runActions)

    /** Ends the hotkey check (the hotkeys work as usual again) and writes what it saw to the results file. */
    fun stopHotkeyCheck() {
        if (session.probe.value == null) return
        session.stopProbe()
        val state = _uiState.value
        if (session.events.value.isEmpty()) return
        session.writeSectionSoon(
            DebugReport.HOTKEYS,
            DebugReport.hotkeySection(state.hotkeyType, state.rows, session.events.value, state.runActions, session.now()),
        )
    }

    fun pickType(type: HotkeyType) {
        _uiState.update { it.copy(hotkeyType = type) }
        updateRows()
    }

    fun setRunActions(on: Boolean) {
        _uiState.update { it.copy(runActions = on) }
        if (session.probe.value != null) session.startProbe(on)
    }

    fun setReportFile(on: Boolean) {
        prefs.debugReportFile = on
        _uiState.update { it.copy(reportFile = on) }
    }

    fun showReport() {
        viewModelScope.launch {
            val text = withContext(Dispatchers.IO) { session.reportText() }
            _uiState.update { it.copy(reportText = text.orEmpty()) }
        }
    }

    fun hideReport() = _uiState.update { it.copy(reportText = null) }

    val reportPath: String? get() = session.reportFile?.absolutePath

    private fun start(checks: List<ActionCheck>, clear: Boolean = false) {
        val runner = session.runner ?: return refresh()
        if (checks.isEmpty() || session.busy.value) return
        if (clear) session.clearResults()
        runner.start(checks)
    }

    /** The rows of the chosen type, ticked by every press seen so far. */
    private fun updateRows() {
        val type = _uiState.value.hotkeyType
        val rows = session.events.value.fold(HotkeyCheck.rows(type, prefs.hotkeys)) { rows, event -> HotkeyCheck.record(rows, event) }
        _uiState.update { it.copy(rows = rows) }
    }

    override fun onCleared() {
        stopHotkeyCheck()
    }
}
