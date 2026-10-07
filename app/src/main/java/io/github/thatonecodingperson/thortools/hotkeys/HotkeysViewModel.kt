package io.github.thatonecodingperson.thortools.hotkeys

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.thatonecodingperson.thortools.actions.ActionCategory
import io.github.thatonecodingperson.thortools.actions.ThorAction
import io.github.thatonecodingperson.thortools.data.SharedPrefsRepo
import io.github.thatonecodingperson.thortools.service.ServiceStatus
import io.github.thatonecodingperson.thortools.tools.SettingsRepo
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

/** Which list the Hotkeys screen shows. */
sealed interface HotkeyTab {
    data object Yours : HotkeyTab

    data object Suggested : HotkeyTab

    data class Actions(val category: ActionCategory) : HotkeyTab

    data object Options : HotkeyTab
}

enum class AppPickerMode { OPEN_APP, HOTKEYS_OFF, HOTKEY_APPS }

data class HotkeysUiModel(
    val hotkeys: List<Hotkey> = emptyList(),
    /** Only this action's hotkey is edited, for the screen that owns it; closing the editor leaves. */
    val onlyAction: ThorAction? = null,
    val tab: HotkeyTab = HotkeyTab.Yours,
    val singlePressHome: Boolean = true,
    val offApps: Set<String> = emptySet(),
    val apps: List<LaunchableApp> = emptyList(),
    val appsLoaded: Boolean = false,
    val serviceRunning: Boolean = true,
    val draft: HotkeyDraft? = null,
    val recording: Boolean = false,
    val picker: AppPickerMode? = null,
    val confirmReset: Boolean = false,
    val suggestionProfile: SuggestionProfile = SuggestionProfile.THOR,
) {
    /** Some hotkey uses Home while AYN holds back the first Home press. */
    val homeHeldBack: Boolean get() = !singlePressHome && hotkeys.any { it.usesHome }

    fun app(packageName: String): LaunchableApp? = apps.find { it.packageName == packageName }

    /** The other hotkey the draft [Hotkey.clashes] with, which saving would replace. */
    fun draftConflict(): Hotkey? {
        val wanted = draft?.toHotkey() ?: return null
        return hotkeys.find { it != draft.editing && it.clashes(wanted) }
    }

    /** The hotkey for every app that the draft, limited to some apps, takes the place of there. */
    fun draftShadows(): Hotkey? {
        val wanted = draft?.toHotkey()?.takeIf { it.apps.isNotEmpty() } ?: return null
        return hotkeys.find { it != draft.editing && it.apps.isEmpty() && it.sameTrigger(wanted) }
    }
}

@HiltViewModel
class HotkeysViewModel @Inject constructor(
    private val prefs: SharedPrefsRepo,
    private val settings: SettingsRepo,
    private val status: ServiceStatus,
    private val launchableApps: LaunchableApps,
) : ViewModel() {

    private val _uiState = MutableStateFlow(HotkeysUiModel())
    val uiState: StateFlow<HotkeysUiModel> = _uiState.asStateFlow()

    private var recordTimeout: Job? = null

    init {
        viewModelScope.launch { prefs.hotkeyChanges().collect { list -> _uiState.update { it.copy(hotkeys = list) } } }
        viewModelScope.launch { prefs.hotkeyOffAppsChanges().collect { apps -> _uiState.update { it.copy(offApps = apps) } } }
        viewModelScope.launch {
            val apps = withContext(Dispatchers.IO) { launchableApps.load() }
            _uiState.update { it.copy(apps = apps, appsLoaded = true) }
        }
    }

    /** AYN's double-press Home setting is read through PServer, so off the main thread. */
    fun refresh() {
        _uiState.update { it.copy(serviceRunning = status.connected) }
        viewModelScope.launch {
            val prevent = withContext(Dispatchers.IO) { settings.preventPressHome }
            _uiState.update { it.copy(singlePressHome = !prevent) }
        }
    }

    fun selectTab(tab: HotkeyTab) = _uiState.update { it.copy(tab = tab) }

    /** Opens [action]'s hotkey (its first one, or a new one) for the screen it is set on. */
    fun editOnly(action: ThorAction) {
        if (_uiState.value.onlyAction == action) return
        val existing = prefs.hotkeys.firstOrNull { it.action == action }
        _uiState.update {
            it.copy(onlyAction = action, draft = existing?.let(HotkeyDraft::of) ?: HotkeyDraft(editing = null, action = action))
        }
    }

    /** Back closes the innermost thing that is open; false when nothing is. */
    fun back(): Boolean {
        val state = _uiState.value
        when {
            state.recording -> stopRecording()
            state.picker != null -> closePicker()
            state.draft != null -> closeDraft()
            state.confirmReset -> dismissReset()
            else -> return false
        }
        return true
    }

    /** A new Close the current app hotkey acts on the screen with the controller unless told otherwise. */
    fun add(action: ThorAction, arg: String? = null) = _uiState.update {
        val start = arg ?: CloseAppArg.encode(LaunchScreen.HERE).takeIf { action == ThorAction.CLOSE_APP }
        it.copy(draft = HotkeyDraft(editing = null, action = action, arg = start))
    }

    fun edit(hotkey: Hotkey) = _uiState.update { it.copy(draft = HotkeyDraft.of(hotkey)) }

    /** The second slot only exists in a combo. */
    fun selectSlot(slot: DraftSlot) = changeDraft { if (slot == DraftSlot.FIRST || it.combo) it.copy(slot = slot) else it }

    /** Fills the selected slot; the combo button can be cleared with null. */
    fun pickButton(button: PadButton?) = changeDraft { draft ->
        when (draft.slot) {
            DraftSlot.FIRST -> if (button == null || button.secondOnly) {
                draft
            } else {
                draft.copy(button = button, second = draft.second?.takeIf { Hotkey.allowed(button, it) })
            }
            DraftSlot.SECOND -> draft.copy(
                second = button?.takeIf { candidate -> draft.button?.let { held -> Hotkey.allowed(held, candidate) } == true },
            )
        }
    }

    fun pickChoice(choice: PressChoice) = changeDraft { it.withChoice(choice) }

    fun pickSecondPress(press: PressKind) = changeDraft { it.copy(secondPress = press) }

    /** Null keeps the app that came up last (hotkeys saved before 0.16.0). */
    fun pickCloseScreen(screen: LaunchScreen?) = changeDraft {
        if (it.action == ThorAction.CLOSE_APP) it.copy(arg = CloseAppArg.encode(screen)) else it
    }

    fun setDraftText(on: Boolean) = changeDraft { it.copy(showText = on) }

    fun setDraftLock(on: Boolean) = changeDraft { it.copy(lock = on) }

    fun setDraftCleanMemory(on: Boolean) = changeDraft { it.copy(cleanMemory = on) }

    /** Back to every app; the apps chosen before are forgotten. */
    fun setDraftEverywhere() = changeDraft { it.copy(apps = emptySet()) }

    fun pickScreen(screen: LaunchScreen) = changeDraft { draft ->
        AppLaunch.decode(draft.arg)?.let { draft.copy(arg = it.copy(screen = screen).encode()) } ?: draft
    }

    fun saveDraft() {
        val draft = _uiState.value.draft ?: return
        val hotkey = draft.toHotkey() ?: return
        prefs.hotkeys = HotkeyList.put(_uiState.value.hotkeys, hotkey, replacing = draft.editing)
        closeDraft()
    }

    fun deleteDraft() {
        val editing = _uiState.value.draft?.editing ?: return
        prefs.hotkeys = _uiState.value.hotkeys - editing
        closeDraft()
    }

    fun closeDraft() {
        stopRecording()
        _uiState.update { it.copy(draft = null) }
    }

    /** The next buttons pressed on the Thor fill the draft (through the service; see `ServiceStatus.buttonRecorder`). */
    fun startRecording() {
        if (!status.connected) return _uiState.update { it.copy(serviceRunning = false) }
        val recorder = ButtonRecorder()
        status.buttonRecorder = { button, pressed ->
            recorder.onKey(button, pressed)?.let { recorded ->
                stopRecording()
                changeDraft { it.withRecorded(recorded.button, recorded.second) }
            }
        }
        _uiState.update { it.copy(recording = true) }
        recordTimeout?.cancel()
        recordTimeout = viewModelScope.launch {
            delay(RECORD_TIMEOUT_MS)
            stopRecording()
        }
    }

    fun stopRecording() {
        status.buttonRecorder = null
        recordTimeout?.cancel()
        _uiState.update { it.copy(recording = false) }
    }

    fun turnOnSinglePressHome() {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { settings.preventPressHome = false }
            refresh()
        }
    }

    fun setSinglePressHome(on: Boolean) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { settings.preventPressHome = !on }
            refresh()
        }
    }

    /** The text switch of every hotkey at once. */
    fun setTextForAll(on: Boolean) {
        prefs.hotkeys = _uiState.value.hotkeys.map { it.copy(showText = on) }
    }

    fun openPicker(mode: AppPickerMode) = _uiState.update { it.copy(picker = mode) }

    fun closePicker() = _uiState.update { it.copy(picker = null) }

    /** A tap on an app in the picker: open it with a new hotkey, or switch hotkeys off or on in it. */
    fun pickApp(packageName: String) {
        when (_uiState.value.picker) {
            AppPickerMode.OPEN_APP -> {
                closePicker()
                add(ThorAction.LAUNCH_APP, AppLaunch(packageName).encode())
            }
            AppPickerMode.HOTKEYS_OFF -> {
                val off = _uiState.value.offApps
                prefs.hotkeyOffApps = if (packageName in off) off - packageName else off + packageName
            }
            AppPickerMode.HOTKEY_APPS -> changeDraft { draft ->
                draft.copy(apps = if (packageName in draft.apps) draft.apps - packageName else draft.apps + packageName)
            }
            null -> Unit
        }
    }

    fun addSuggestion(hotkey: Hotkey) {
        prefs.hotkeys = HotkeyList.put(_uiState.value.hotkeys, hotkey)
    }

    fun selectProfile(profile: SuggestionProfile) = _uiState.update { it.copy(suggestionProfile = profile) }

    fun addFreeSuggestions() {
        val state = _uiState.value
        prefs.hotkeys = HotkeyList.addFreeSuggestions(state.hotkeys, state.suggestionProfile.hotkeys)
    }

    fun askReset() = _uiState.update { it.copy(confirmReset = true) }

    fun dismissReset() = _uiState.update { it.copy(confirmReset = false) }

    fun reset() {
        prefs.resetHotkeys()
        dismissReset()
    }

    private fun changeDraft(change: (HotkeyDraft) -> HotkeyDraft) = _uiState.update { state ->
        state.copy(draft = state.draft?.let(change))
    }

    override fun onCleared() {
        status.buttonRecorder = null
    }

    private companion object {
        const val RECORD_TIMEOUT_MS = 15_000L
    }
}
